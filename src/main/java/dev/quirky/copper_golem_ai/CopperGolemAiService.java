package dev.quirky.copper_golem_ai;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 铜傀儡 AI 对话服务端集成：聊天监听 → 异步 LLM 调用 → 回复 + 音效；
 * 每傀儡独立会话（历史/冷却/压缩）。异步结果经队列在服务端 tick 消费，不阻塞主线程。
 */
public final class CopperGolemAiService {
	private static final Logger LOGGER = LoggerFactory.getLogger("quirky-copper-golem-ai");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ExecutorService IO = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "quirky-ai-io");
		t.setDaemon(true);
		return t;
	});
	private static final Map<UUID, CopperGolemAiHistory.GolemSession> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_REPLY_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveTransport> ACTIVE_TRANSPORTS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> ACTIVE_FOLLOWS = new ConcurrentHashMap<>(); // golemId → playerId
	private static final Map<UUID, UUID> ACTIVE_APPROACHES = new ConcurrentHashMap<>(); // golemId → entityId
	private static final Map<UUID, CollectTask> ACTIVE_COLLECTS = new ConcurrentHashMap<>(); // golemId → 掉落物
	private static final ConcurrentLinkedQueue<Runnable> PENDING_TASKS = new ConcurrentLinkedQueue<>();
	private static final int MAX_TRANSPORT_DISTANCE = 64;
	private static final int TARGETED_RAYCAST_DISTANCE = 6;
	private static volatile boolean initialized = false;

	/** 进行中的搬运任务（每傀儡一个）。givePlayerId=give 目标玩家（非 give 为 null）；openPos=当前打开的容器。 */
	private record ActiveTransport(
		CopperGolemAiIntent.TransportRequest request,
		CopperGolemTransportTask.State state,
		BlockPos source,
		@Nullable BlockPos destination,
		String itemId,
		@Nullable UUID givePlayerId,
		@Nullable BlockPos openPos
	) {
		ActiveTransport withState(CopperGolemTransportTask.State s) {
			return new ActiveTransport(request, s, source, destination, itemId, givePlayerId, openPos);
		}

		ActiveTransport withOpenPos(BlockPos p) {
			return new ActiveTransport(request, state, source, destination, itemId, givePlayerId, p);
		}
	}

	private CopperGolemAiService() {
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerMessageEvents.CHAT_MESSAGE.register(CopperGolemAiService::onChatMessage);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Runnable task;
			while ((task = PENDING_TASKS.poll()) != null) {
				try {
					task.run();
				} catch (RuntimeException e) {
					LOGGER.error("Copper golem AI task failed", e);
				}
			}
			// 低频清理：无对话超 20 分钟的傀儡会话（spec：死亡/卸载后清空，防内存增长）
			if ((server.getTickCount() & 1199) == 0) {
				long cutoff = server.getTickCount() - 24000L;
				LAST_REPLY_TICK.entrySet().removeIf(e -> e.getValue() < cutoff);
				SESSIONS.keySet().removeIf(id -> !LAST_REPLY_TICK.containsKey(id));
			}
		});
	}

	private static void onChatMessage(net.minecraft.network.chat.PlayerChatMessage message, ServerPlayer player, net.minecraft.network.chat.ChatType.Bound params) {
		if (player.level().isClientSide()) {
			return;
		}
		QuirkyConfig config = QuirkyConfigHolder.get();
		if (!CopperGolemAiConfig.enabled(config)) {
			return;
		}
		String text = message.signedContent();
		if (text == null || text.isBlank()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level(); // 26.2 ServerPlayer 无 serverLevel()，level() 运行时即 ServerLevel
		long gameTime = level.getGameTime();
		CopperGolem golem = nearestGolem(level, player, config.aiListenRange);
		if (golem == null) {
			return;
		}
		UUID golemId = golem.getUUID();
		Long last = LAST_REPLY_TICK.get(golemId);
		if (last != null && gameTime - last < config.aiCooldownTicks) {
			return;
		}
		LAST_REPLY_TICK.put(golemId, gameTime);

		CopperGolemAiHistory.GolemSession session = SESSIONS.computeIfAbsent(golemId, id -> new CopperGolemAiHistory.GolemSession());
		var result = session.addPlayerMessage(text);
		switch (result) {
			case FORGET_ALL -> {
				reply(player, golem, "好，我把刚才的都忘了");
				return;
			}
			case FORGET_LAST -> {
				reply(player, golem, "好，上一条忘了");
				return;
			}
			case COMPRESSING -> {
				reply(player, golem, "正在整理记忆，稍等一下…");
				return;
			}
			case NORMAL -> { /* fallthrough */ }
		}

		if (CopperGolemAiHistory.shouldCompress(session, config)) {
			startCompression(player, golem, session, config, text);
			return;
		}

		requestReply(player, golem, session, config, text);
	}

	private static CopperGolem nearestGolem(ServerLevel level, ServerPlayer player, int range) {
		AABB box = new AABB(player.blockPosition()).inflate(range);
		List<CopperGolem> golems = level.getEntities(EntityTypeTest.forClass(CopperGolem.class), box, e -> !e.isRemoved());
		return golems.stream()
			.min(Comparator.comparingDouble(g -> g.distanceToSqr(player)))
			.orElse(null);
	}

	private static void requestReply(ServerPlayer player, CopperGolem golem, CopperGolemAiHistory.GolemSession session, QuirkyConfig config, String text) {
		CopperGolemAgentLoop loop = new CopperGolemAgentLoop(config, CopperGolemAiHttp.SYSTEM_PROMPT, session.messages(), text);
		CopperGolemAgentTools.ToolContext ctx = new CopperGolemAgentTools.ToolContext(golem, (ServerLevel) player.level(), player, new java.util.HashSet<>());
		IO.submit(() -> {
			try {
				String reply = loop.run(
					body -> post(CopperGolemAiHttp.endpoint(config), config.aiApiKey, body),
					(name, args) -> CopperGolemAgentTools.execute(name, args, ctx));
				PENDING_TASKS.add(() -> {
					session.addGolemReply(reply);
					reply(player, golem, reply);
				});
			} catch (Exception e) {
				LOGGER.warn("Copper golem AI request failed: {}", e.toString());
			}
		});
	}

	private static void startCompression(ServerPlayer player, CopperGolem golem, CopperGolemAiHistory.GolemSession session, QuirkyConfig config, String currentText) {
		// 当前消息已通过 addPlayerMessage 进历史：先摘出，压缩完成后作为新消息重新发送（防摘要重建后重复）
		session.removeLastPlayerMessage();
		session.markCompressing(true);
		reply(player, golem, "⌛ 上下文压缩中…");
		List<String> history = session.messages();
		String body = CopperGolemAiHttp.buildSummaryRequest(config, history);
		IO.submit(() -> {
			try {
				String response = post(CopperGolemAiHttp.endpoint(config), config.aiApiKey, body);
				String summary = CopperGolemAiHttp.parseReply(response);
				PENDING_TASKS.add(() -> {
					List<String> replacement = new ArrayList<>();
					if (summary != null) {
						replacement.add("system: " + summary);
					}
					List<String> all = session.messages();
					int keep = Math.min(4, all.size());
					replacement.addAll(all.subList(all.size() - keep, all.size()));
					session.setSummarized(replacement);
					// 处理压缩期间排队的消息（逐条走对话）
					String queued;
					while ((queued = session.pollPending()) != null) {
						session.addPlayerMessage(queued);
						requestReply(player, golem, session, config, queued);
					}
					// 当前消息作为新消息正常回复
					requestReply(player, golem, session, config, currentText);
				});
			} catch (Exception e) {
				LOGGER.warn("Copper golem AI summary failed: {}", e.toString());
				PENDING_TASKS.add(() -> {
					session.dropToTail(4);
					String queued;
					while ((queued = session.pollPending()) != null) {
						session.addPlayerMessage(queued);
						requestReply(player, golem, session, config, queued);
					}
					requestReply(player, golem, session, config, currentText);
				});
			}
		});
	}

	private static void reply(ServerPlayer player, CopperGolem golem, String text) {
		String name = golem.getDisplayName().getString();
		player.sendSystemMessage(Component.literal("[" + name + "] " + text).withStyle(ChatFormatting.DARK_AQUA));
		player.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_SPIN, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static String post(String endpoint, String apiKey, String bodyJson) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
			.timeout(Duration.ofSeconds(30))
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer " + apiKey)
			.POST(HttpRequest.BodyPublishers.ofString(bodyJson));
		return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
	}

	// ===== 工具查询（Task 8 替换 lightningInfo）=====

	/** 最近被闪电劈的时间描述（Task 8 接 mixin 后返回真实值）。 */
	public static String lightningInfo(CopperGolem golem) {
		return "从未被劈过";
	}

	/** 测试辅助：清空会话状态（防测试间污染）。 */
	public static void resetForTest() {
		SESSIONS.clear();
		LAST_REPLY_TICK.clear();
		PENDING_TASKS.clear();
		ACTIVE_TRANSPORTS.clear();
		ACTIVE_FOLLOWS.clear();
		ACTIVE_APPROACHES.clear();
		ACTIVE_COLLECTS.clear();
	}

	// ===== V1 搬运执行 =====

	/** 处理 transport 意图：白名单 + 准心射线 + 启动搬运；任一不满足 → 提示并放弃。 */
	/** transport 工具执行：白名单 + knownItems 校验 + 定位 + 注册任务；返回工具结果 JSON（给 AI）。 */
	public static String handleTransportRequest(CopperGolem golem, ServerLevel level, @Nullable ServerPlayer player,
		java.util.Set<String> knownItems, CopperGolemAiIntent.TransportRequest req) {
		if (!CopperGolemAiIntent.isPlausibleItem(req.item())) {
			return "{\"error\":\"物品名不合法\"}";
		}
		if (!CopperGolemAiIntent.isKnownItem(req.item(), knownItems)) {
			return "{\"error\":\"我看不到这个物品（" + req.item() + "），请先 look_containers 查看容器内容\"}";
		}
		if (!CopperGolemAiIntent.isPlausibleTarget(req.source()) || !CopperGolemAiIntent.isPlausibleTarget(req.destination())) {
			return "{\"error\":\"搬运目标不合法\"}";
		}
		boolean toPlayer = "give".equals(req.destination());
		if (player == null && toPlayer) {
			return "{\"error\":\"玩家不在线，没法递给他\"}";
		}
		BlockPos source = "copper".equals(req.source())
			? findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE)
			: CopperGolemAgentTools.parseCoords(req.source());
		if (source == null) {
			return "{\"error\":\"附近没有铜箱子\"}";
		}
		if (containerAt(level, source) == null) {
			return "{\"error\":\"取货位置不是容器\"}";
		}
		BlockPos destination = null;
		if (!toPlayer) {
			destination = "copper".equals(req.destination())
				? findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE)
				: CopperGolemAgentTools.parseCoords(req.destination());
			if (destination == null) {
				return "{\"error\":\"目标位置无效\"}";
			}
			if (destination.equals(source)) {
				return "{\"error\":\"来源和目标相同\"}";
			}
			if (containerAt(level, destination) == null) {
				return "{\"error\":\"放货位置不是容器\"}";
			}
		}
		// 暂停原版运输 AI（调度枢纽：有 cooldown memory 则原版运输行为不启动）
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
		ACTIVE_TRANSPORTS.put(golem.getUUID(),
			new ActiveTransport(req, CopperGolemTransportTask.State.WALK_SOURCE, source, destination, req.item(),
				toPlayer ? player.getUUID() : null, null));
		return "{\"ok\":\"开始搬运 " + req.item() + "\"}";
	}

	/** 玩家准心射线（≤6 格）命中且为容器的方块位置；未命中/非容器 → null。 */
	private static @Nullable BlockPos findTargetedContainer(ServerPlayer player, ServerLevel level) {
		HitResult hit = player.pick(TARGETED_RAYCAST_DISTANCE, 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = ((BlockHitResult) hit).getBlockPos();
		return containerAt(level, pos) != null ? pos : null;
	}

	/** 64 格内最近的铜箱子（BlockTags.COPPER_CHESTS）；无 → null。 */
	private static @Nullable BlockPos findNearestCopperChest(CopperGolem golem, ServerLevel level, int maxDist) {
		BlockPos golemPos = golem.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -maxDist; dx <= maxDist; dx += 16) {
			for (int dz = -maxDist; dz <= maxDist; dz += 16) {
				ChunkPos cp = ChunkPos.containing(golemPos.offset(dx, 0, dz));
				LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof ChestBlockEntity
						&& level.getBlockState(be.getBlockPos()).is(BlockTags.COPPER_CHESTS)) {
						double d = be.getBlockPos().distToCenterSqr(golem.getX(), golem.getY(), golem.getZ());
						if (d < bestDist) {
							bestDist = d;
							best = be.getBlockPos();
						}
					}
				}
			}
		}
		return best != null && bestDist <= (double) maxDist * maxDist ? best : null;
	}

	/** 捡掉落物任务：走到掉落物旁 → 捡起 → 转入 TRANSPORT（带回最近铜箱）。 */
	private record CollectTask(BlockPos pos, UUID itemEntityId, String itemId) {
	}

	/** 开始捡掉落物：找 range 内最近 ItemEntity → 注册 COLLECT 任务。 */
	public static String startCollect(CopperGolem golem, ServerLevel level, int range) {
		AABB box = new AABB(golem.blockPosition()).inflate(range);
		var found = level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class), box, e -> !e.isRemoved())
			.stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(golem)));
		if (found.isEmpty()) {
			return "{\"ok\":\"附近没有掉落物\"}";
		}
		net.minecraft.world.entity.item.ItemEntity item = found.get();
		ACTIVE_COLLECTS.put(golem.getUUID(), new CollectTask(item.blockPosition(), item.getUUID(),
			BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString()));
		return "{\"ok\":\"去捡 " + ACTIVE_COLLECTS.get(golem.getUUID()).itemId() + "\"}";
	}

	private static void tickCollect(CopperGolem golem, ServerLevel level) {
		CollectTask task = ACTIVE_COLLECTS.get(golem.getUUID());
		if (task == null) {
			return;
		}
		if (golem.blockPosition().distSqr(task.pos()) > 4.0) {
			BehaviorUtils.setWalkAndLookTargetMemories(golem, task.pos(), 1.0F, 1);
			return;
		}
		ACTIVE_COLLECTS.remove(golem.getUUID());
		var entity = level.getEntity(task.itemEntityId());
		if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item) || item.isRemoved()) {
			return;
		}
		ItemStack stack = item.getItem();
		item.discard();
		golem.setItemSlot(EquipmentSlot.MAINHAND, stack);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		golem.setState(CopperGolemState.GETTING_ITEM);
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 1.0F, 1.0F);
		// 转入 TRANSPORT：带回最近铜箱（跳过取货，直接放货）
		BlockPos copper = findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE);
		if (copper == null) {
			return;
		}
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
		CopperGolemAiIntent.TransportRequest req = new CopperGolemAiIntent.TransportRequest(
			BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "copper", copper.getX() + "," + copper.getY() + "," + copper.getZ());
		ACTIVE_TRANSPORTS.put(golem.getUUID(),
			new ActiveTransport(req, CopperGolemTransportTask.State.WALK_DEST, task.pos(), copper, req.item(), null, null));
	}

	/** 开始移动：设 WALK_TARGET（一次性）。返回确认文本。 */
	public static String startMove(CopperGolem golem, BlockPos target) {
		BehaviorUtils.setWalkAndLookTargetMemories(golem, target, 1.0F, 1);
		return "{\"ok\":\"正在前往 " + target.getX() + "," + target.getY() + "," + target.getZ() + "\"}";
	}

	/** 开始跟随玩家：注册 FOLLOW 任务（tick 持续刷新目标）。 */
	public static String startFollow(CopperGolem golem, ServerLevel level, String playerName) {
		ServerPlayer target = level.players().stream()
			.filter(p -> p.getName().getString().equals(playerName))
			.findFirst().orElse(null);
		if (target == null) {
			return "{\"error\":\"找不到玩家 " + playerName + "\"}";
		}
		ACTIVE_FOLLOWS.put(golem.getUUID(), target.getUUID());
		return "{\"ok\":\"开始跟着 " + playerName + "\"}";
	}

	/** 开始接近指定类型生物：找最近同类 → 注册 APPROACH 任务。 */
	public static String startApproach(CopperGolem golem, ServerLevel level, String entityTypeId) {
		var holder = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(entityTypeId));
		if (holder == null) {
			return "{\"error\":\"未知生物类型 " + entityTypeId + "\"}";
		}
		AABB box = new AABB(golem.blockPosition()).inflate(32);
		var found = level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class), box, e -> !e.isRemoved()).stream()
			.filter(e -> e.getType() == holder)
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(golem)));
		if (found.isEmpty()) {
			return "{\"error\":\"附近没有 " + entityTypeId + "\"}";
		}
		ACTIVE_APPROACHES.put(golem.getUUID(), found.get().getUUID());
		return "{\"ok\":\"去看 " + entityTypeId + "\"}";
	}

	/** 停止全部行动（移动/跟随/接近/搬运），恢复待机。 */
	public static String stopAll(CopperGolem golem) {
		ACTIVE_FOLLOWS.remove(golem.getUUID());
		ACTIVE_APPROACHES.remove(golem.getUUID());
		ActiveTransport t = ACTIVE_TRANSPORTS.remove(golem.getUUID());
		if (t != null && t.openPos() != null) {
			BlockEntity be = golem.level().getBlockEntity(t.openPos());
			if (be instanceof Container c) {
				c.stopOpen(golem);
			}
		}
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
		golem.setState(CopperGolemState.IDLE);
		golem.clearOpenedChestPos();
		return "{\"ok\":\"好，我停下了\"}";
	}

	/** 傀儡当前任务描述（供 get_self_status）。 */
	public static String currentTaskDescription(CopperGolem golem) {
		UUID id = golem.getUUID();
		if (ACTIVE_FOLLOWS.containsKey(id)) {
			return "正在跟随玩家";
		}
		if (ACTIVE_APPROACHES.containsKey(id)) {
			return "正在去看某个生物";
		}
		ActiveTransport t = ACTIVE_TRANSPORTS.get(id);
		if (t != null) {
			return "正在搬运物品";
		}
		return "无";
	}

	/** mixin 每 tick 调用：推进全部任务（搬运/跟随/接近）。 */
	public static void tickTransport(CopperGolem golem, ServerLevel level) {
		if (golem.isRemoved()) {
			ACTIVE_TRANSPORTS.remove(golem.getUUID());
			ACTIVE_FOLLOWS.remove(golem.getUUID());
			ACTIVE_APPROACHES.remove(golem.getUUID());
			ACTIVE_COLLECTS.remove(golem.getUUID());
			return;
		}
		tickFollow(golem, level);
		tickApproach(golem, level);
		tickCollect(golem, level);
		tickTransportTask(golem, level);
	}

	private static void tickFollow(CopperGolem golem, ServerLevel level) {
		UUID playerId = ACTIVE_FOLLOWS.get(golem.getUUID());
		if (playerId == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
		if (player == null || !player.isAlive()) {
			ACTIVE_FOLLOWS.remove(golem.getUUID());
			return;
		}
		double distSqr = player.distanceToSqr(golem);
		if (CopperGolemAgentTools.shouldStopFollow(distSqr, 64)) {
			ACTIVE_FOLLOWS.remove(golem.getUUID());
			return;
		}
		// 保持 2-3 格距离：目标 = 玩家位置，closeEnough=2（每 tick 刷新，到达后仍贴着）
		BehaviorUtils.setWalkAndLookTargetMemories(golem, player.blockPosition(), 1.0F, 2);
	}

	private static void tickApproach(CopperGolem golem, ServerLevel level) {
		UUID entityId = ACTIVE_APPROACHES.get(golem.getUUID());
		if (entityId == null) {
			return;
		}
		var entity = level.getEntity(entityId);
		if (entity == null || !entity.isAlive()) {
			ACTIVE_APPROACHES.remove(golem.getUUID());
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, entity.blockPosition(), 1.0F, 2);
	}

	private static void tickTransportTask(CopperGolem golem, ServerLevel level) {
		ActiveTransport t = ACTIVE_TRANSPORTS.get(golem.getUUID());
		if (t == null || t.state() == CopperGolemTransportTask.State.DONE || t.state() == CopperGolemTransportTask.State.FAIL) {
			return;
		}
		if (golem.isRemoved()) {
			ACTIVE_TRANSPORTS.remove(golem.getUUID());
			return;
		}
		if (t.state() == CopperGolemTransportTask.State.WALK_SOURCE
			|| t.state() == CopperGolemTransportTask.State.WALK_DEST) {
			BlockPos target = t.state() == CopperGolemTransportTask.State.WALK_SOURCE
				? t.source()
				: (t.givePlayerId() != null ? giveTarget(level, t) : t.destination());
			if (target == null) {
				// give 且玩家不在 → 中止
				finishTransport(golem, t);
				return;
			}
			boolean atTarget = golem.blockPosition().distSqr(target) <= 4.0;
			if (!atTarget) {
				BehaviorUtils.setWalkAndLookTargetMemories(golem, target, 1.0F, 0);
				return;
			}
			if (t.state() == CopperGolemTransportTask.State.WALK_SOURCE) {
				if (doTake(golem, level, t)) {
					ACTIVE_TRANSPORTS.put(golem.getUUID(), t.withState(CopperGolemTransportTask.State.WALK_DEST));
				} else {
					finishTransport(golem, t);
				}
			} else {
				if (t.givePlayerId() != null) {
					doGive(golem, level, t);
				} else {
					doPut(golem, level, t);
				}
				finishTransport(golem, t);
			}
		}
	}

	/** give 目标位置：玩家当前位置；玩家不在 → null。 */
	private static @Nullable BlockPos giveTarget(ServerLevel level, ActiveTransport t) {
		if (t.givePlayerId() == null) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.givePlayerId());
		return player != null && player.isAlive() ? player.blockPosition() : null;
	}

	/** 递物品给玩家：走到面前 → 面朝玩家 → 掉落物交付 + 音效。 */
	private static void doGive(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.givePlayerId());
		if (player == null || !player.isAlive()) {
			replyTo(golem, level, t, "你不在，东西我先放回箱子了");
			if (t.source() != null && containerAt(level, t.source()) != null) {
				addToContainer(containerAt(level, t.source()), golem.getMainHandItem());
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return;
		}
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
			new net.minecraft.world.entity.ai.behavior.EntityTracker(player, true));
		ItemStack held = golem.getMainHandItem();
		golem.spawnAtLocation(level, held);
		golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		golem.setState(CopperGolemState.DROPPING_ITEM);
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.PLAYERS, 1.0F, 1.0F);
		replyTo(golem, level, t, "给你，接好咯");
	}

	/** 取货：成功返回 true（推进 WALK_DEST）；失败返回 false（任务中止，提示已发）。 */
	private static boolean doTake(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		Container container = containerAt(level, t.source());
		if (container == null) {
			replyTo(golem, level, t, "取货的箱子不见了，我搬不了");
			return false;
		}
		// 手里若已有物品（原版搬运残留）：先放回源容器，避免覆盖丢失
		ItemStack held = golem.getMainHandItem();
		if (!held.isEmpty()) {
			addToContainer(container, held);
			golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		ItemStack picked = pickupItem(container, t.itemId());
		if (picked.isEmpty()) {
			replyTo(golem, level, t, "没找到" + t.itemId() + "，我搬不了");
			return false;
		}
		golem.setItemSlot(EquipmentSlot.MAINHAND, picked);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		golem.setState(CopperGolemState.GETTING_ITEM);
		container.startOpen(golem);
		ACTIVE_TRANSPORTS.put(golem.getUUID(), t.withOpenPos(t.source()));
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** 放货：放完或放不下都结束任务；放不下提示玩家并让物品留在手里（不会消失）。 */
	private static void doPut(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		Container container = containerAt(level, t.destination());
		if (container == null) {
			replyTo(golem, level, t, "放货的箱子不见了，我搬不了");
			return;
		}
		ItemStack held = golem.getMainHandItem();
		ItemStack left = addToContainer(container, held);
		golem.setItemSlot(EquipmentSlot.MAINHAND, left);
		golem.setState(left.isEmpty() ? CopperGolemState.DROPPING_ITEM : CopperGolemState.DROPPING_NO_ITEM);
		container.startOpen(golem);
		ACTIVE_TRANSPORTS.put(golem.getUUID(), t.withOpenPos(t.destination()));
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (!left.isEmpty()) {
			replyTo(golem, level, t, "箱子放不下了，剩下的我先拿着");
		}
	}

	/** 取指定物品（≤16）："any" 取第一个非空槽，否则按注册表解析的 Item 匹配。 */
	private static ItemStack pickupItem(Container container, String itemId) {
		boolean any = itemId.equals("any");
		Item target = any ? null : BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (any || stack.getItem() == target) {
				int count = Math.min(stack.getCount(), 16);
				ItemStack out = container.removeItem(slot, count);
				container.setChanged();
				return out;
			}
		}
		return ItemStack.EMPTY;
	}

	/** 放入容器：找空槽或同物品合并；返回剩余（空=放完）。 */
	private static ItemStack addToContainer(Container container, ItemStack stack) {
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
			ItemStack inSlot = container.getItem(slot);
			if (inSlot.isEmpty()) {
				container.setItem(slot, remaining.copy());
				container.setChanged();
				return ItemStack.EMPTY;
			}
			if (ItemStack.isSameItemSameComponents(inSlot, remaining) && inSlot.getCount() < inSlot.getMaxStackSize()) {
				int room = inSlot.getMaxStackSize() - inSlot.getCount();
				int add = Math.min(room, remaining.getCount());
				inSlot.grow(add);
				remaining.shrink(add);
				container.setChanged();
			}
		}
		return remaining;
	}

	/** 方块位置 → Container（箱子经 ChestBlock.getContainer，其他 Container 方块实体直接取）；非容器 → null。 */
	private static @Nullable Container containerAt(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c) {
			return c;
		}
		if (level.getBlockState(pos).getBlock() instanceof ChestBlock cb) {
			return ChestBlock.getContainer(cb, level.getBlockState(pos), level, pos, false);
		}
		return null;
	}

	/** 结束任务：恢复原版状态（清 cooldown memory → 原版运输 AI 恢复），并关掉打开的容器。 */
	private static void finishTransport(CopperGolem golem, ActiveTransport t) {
		golem.setState(CopperGolemState.IDLE);
		if (t.openPos() != null) {
			BlockEntity be = golem.level().getBlockEntity(t.openPos());
			if (be instanceof Container c) {
				c.stopOpen(golem);
			}
		}
		golem.clearOpenedChestPos();
		golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
		ACTIVE_TRANSPORTS.remove(golem.getUUID());
	}

	/** 任务提示只发给发起者（不走全局广播，避免泄露对话/刷屏）。 */
	private static void replyTo(CopperGolem golem, ServerLevel level, ActiveTransport t, String text) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.givePlayerId());
		if (player != null) {
			player.sendSystemMessage(
				Component.literal("[" + golem.getDisplayName().getString() + "] " + text).withStyle(ChatFormatting.DARK_AQUA));
		}
	}
}
