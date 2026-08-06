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
	private static final ConcurrentLinkedQueue<Runnable> PENDING_TASKS = new ConcurrentLinkedQueue<>();
	private static final int MAX_TRANSPORT_DISTANCE = 64;
	private static final int TARGETED_RAYCAST_DISTANCE = 6;
	private static volatile boolean initialized = false;

	/** 进行中的搬运任务（每傀儡一个）。 */
	private record ActiveTransport(
		CopperGolemAiIntent.TransportRequest request,
		CopperGolemTransportTask.State state,
		BlockPos source,
		BlockPos destination,
		String itemId
	) {
		ActiveTransport withState(CopperGolemTransportTask.State s) {
			return new ActiveTransport(request, s, source, destination, itemId);
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
		String body = CopperGolemAiHttp.buildChatRequest(config, session.messages(), text);
		IO.submit(() -> {
			try {
				String response = post(CopperGolemAiHttp.endpoint(config), config.aiApiKey, body);
				String reply = CopperGolemAiHttp.parseReply(response);
				CopperGolemAiIntent.TransportRequest toolCall = CopperGolemAiHttp.parseToolCall(response);
				PENDING_TASKS.add(() -> {
					if (toolCall != null) {
						handleTransportIntent(player, golem, session, config, toolCall);
					}
					if (reply != null) {
						session.addGolemReply(reply);
						reply(player, golem, reply);
					}
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

	/** 测试辅助：清空会话状态（防测试间污染）。 */
	public static void resetForTest() {
		SESSIONS.clear();
		LAST_REPLY_TICK.clear();
		PENDING_TASKS.clear();
		ACTIVE_TRANSPORTS.clear();
	}

	// ===== V1 搬运执行 =====

	/** 处理 transport 意图：白名单 + 准心射线 + 启动搬运；任一不满足 → 提示并放弃。 */
	private static void handleTransportIntent(ServerPlayer player, CopperGolem golem,
		CopperGolemAiHistory.GolemSession session, QuirkyConfig config, CopperGolemAiIntent.TransportRequest req) {
		if (!CopperGolemAiIntent.isPlausibleItem(req.item())) {
			reply(player, golem, "我没听懂要搬什么，请再说一次");
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		boolean needsTargeted = req.source() == CopperGolemAiIntent.Target.TARGETED
			|| req.destination() == CopperGolemAiIntent.Target.TARGETED;
		BlockPos targeted = needsTargeted ? findTargetedContainer(player, level) : null;
		if (needsTargeted && targeted == null) {
			reply(player, golem, "没有对准箱子，请重新告诉我放进哪");
			return;
		}
		boolean needsCopper = req.source() == CopperGolemAiIntent.Target.COPPER
			|| req.destination() == CopperGolemAiIntent.Target.COPPER;
		BlockPos copper = needsCopper ? findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE) : null;
		if (needsCopper && copper == null) {
			reply(player, golem, "附近没有铜箱子，我搬不了");
			return;
		}
		BlockPos source = req.source() == CopperGolemAiIntent.Target.TARGETED ? targeted : copper;
		BlockPos destination = req.destination() == CopperGolemAiIntent.Target.TARGETED ? targeted : copper;
		if (source == null || destination == null || source.equals(destination)) {
			reply(player, golem, "搬运目标不明确，请重新对准箱子告诉我");
			return;
		}
		// 暂停原版运输 AI（调度枢纽：有 cooldown memory 则原版运输行为不启动）
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
		ACTIVE_TRANSPORTS.put(golem.getUUID(),
			new ActiveTransport(req, CopperGolemTransportTask.State.WALK_SOURCE, source, destination, req.item()));
		reply(player, golem, "好嘞，这就去搬" + (req.item().equals("any") ? "点东西" : " " + req.item()));
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

	/** mixin 每 tick 调用：推进搬运状态机；无活跃任务立即返回。 */
	public static void tickTransport(CopperGolem golem, ServerLevel level) {
		ActiveTransport t = ACTIVE_TRANSPORTS.get(golem.getUUID());
		if (t == null || CopperGolemTransportTask.isTerminal(t.state())) {
			return;
		}
		BlockPos target = t.state() == CopperGolemTransportTask.State.WALK_SOURCE
			|| t.state() == CopperGolemTransportTask.State.TAKE ? t.source() : t.destination();
		boolean atTarget = golem.blockPosition().distSqr(target) <= 4.0;
		var decision = CopperGolemTransportTask.decide(t.state(), atTarget, false, false);
		switch (decision) {
			case WALK_TO -> BehaviorUtils.setWalkAndLookTargetMemories(golem, target, 1.0F, 0);
			case INTERACT -> {
				CopperGolemTransportTask.State current = t.state();
				CopperGolemTransportTask.State next = CopperGolemTransportTask.nextState(current, decision);
				ACTIVE_TRANSPORTS.put(golem.getUUID(), t.withState(next));
				if (current == CopperGolemTransportTask.State.WALK_SOURCE) {
					doTake(golem, level, t);
				} else if (current == CopperGolemTransportTask.State.WALK_DEST) {
					doPut(golem, level, t);
				}
			}
			case FINISH, ABORT -> finishTransport(golem);
		}
	}

	private static void doTake(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		Container container = containerAt(level, t.source());
		if (container == null) {
			replyNearby(golem, level, "取货的箱子不见了，我搬不了");
			finishTransport(golem);
			return;
		}
		ItemStack picked = pickupItem(container, t.itemId());
		if (picked.isEmpty()) {
			replyNearby(golem, level, "没找到" + t.itemId() + "，我搬不了");
			finishTransport(golem);
			return;
		}
		golem.setItemSlot(EquipmentSlot.MAINHAND, picked);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		golem.setState(CopperGolemState.GETTING_ITEM);
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static void doPut(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		Container container = containerAt(level, t.destination());
		if (container == null) {
			replyNearby(golem, level, "放货的箱子不见了，我搬不了");
			finishTransport(golem);
			return;
		}
		ItemStack held = golem.getMainHandItem();
		ItemStack left = addToContainer(container, held);
		golem.setItemSlot(EquipmentSlot.MAINHAND, left);
		golem.setState(left.isEmpty() ? CopperGolemState.DROPPING_ITEM : CopperGolemState.DROPPING_NO_ITEM);
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (left.isEmpty()) {
			finishTransport(golem);
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

	/** 结束任务：恢复原版状态（清 cooldown memory → 原版运输 AI 恢复）。 */
	private static void finishTransport(CopperGolem golem) {
		golem.setState(CopperGolemState.IDLE);
		golem.clearOpenedChestPos();
		golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
		ACTIVE_TRANSPORTS.remove(golem.getUUID());
	}

	/** 傀儡自己向世界播报（玩家可能在附近，系统消息可见）。 */
	private static void replyNearby(CopperGolem golem, ServerLevel level, String text) {
		level.getServer().getPlayerList().broadcastSystemMessage(
			Component.literal("[" + golem.getDisplayName().getString() + "] " + text).withStyle(ChatFormatting.DARK_AQUA), false);
	}
}
