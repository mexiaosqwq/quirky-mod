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
	private static final Map<UUID, Long> LAST_HEARTBEAT_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_CHATTER_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MOOD_SCORES = new ConcurrentHashMap<>(); // golemId → 心情分数
	private static final Map<UUID, CopperGolemRename.RenameState> RENAMES = new ConcurrentHashMap<>(); // golemId → 待命名
	private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>(); // golemId → 被劈 tick
	private static final Map<UUID, ActiveTransport> ACTIVE_TRANSPORTS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> ACTIVE_FOLLOWS = new ConcurrentHashMap<>(); // golemId → playerId
	private static final Map<UUID, UUID> ACTIVE_APPROACHES = new ConcurrentHashMap<>(); // golemId → entityId
	private static final Map<UUID, CollectTask> ACTIVE_COLLECTS = new ConcurrentHashMap<>(); // golemId → 掉落物
	private static final ConcurrentLinkedQueue<Runnable> PENDING_TASKS = new ConcurrentLinkedQueue<>();
	private static final Map<UUID, Boolean> PENDING_REPLIES = new ConcurrentHashMap<>(); // golemId → 对话请求在途（心跳 busy 检查用）
	private static final Map<UUID, UUID> CHAT_PLAYERS = new ConcurrentHashMap<>(); // golemId → 对话玩家（在途时傀儡转头看着他）

	/** 傀儡间留言（tell_golem）：A 传给 B 的话，B 下次心跳/对话时注入并消费。 */
	private record GolemMessage(String fromName, String text, long expireTick) {
	}

	private static final Map<UUID, List<GolemMessage>> GOLEM_MESSAGES = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> GOLEM_MESSAGE_REPLYING = ConcurrentHashMap.newKeySet(); // 本轮回应对同伴留言，限流放行
	private static final int GOLEM_MESSAGE_TTL_TICKS = 6000; // 5 分钟：心跳 30s，两轮内必被看到
	private static final int MAX_TRANSPORT_DISTANCE = 64;
	private static final int TARGETED_RAYCAST_DISTANCE = 6;
	private static volatile boolean initialized = false;

	/** 进行中的搬运任务（每傀儡一个）。givePlayerId=give 目标玩家（非 give 为 null）；openPos=当前打开的容器；collectQueue=collect 批量链的剩余目标（非 collect 为 null）。 */
	private record ActiveTransport(
		CopperGolemAiIntent.TransportRequest request,
		CopperGolemTransportTask.State state,
		BlockPos source,
		@Nullable BlockPos destination,
		String itemId,
		@Nullable UUID givePlayerId,
		@Nullable BlockPos openPos,
		@Nullable List<UUID> collectQueue,
		@Nullable UUID enderOwner // 末影箱归属玩家（对话发起者）；非末影箱任务为 null
	) {
		ActiveTransport withState(CopperGolemTransportTask.State s) {
			return new ActiveTransport(request, s, source, destination, itemId, givePlayerId, openPos, collectQueue, enderOwner);
		}

		ActiveTransport withOpenPos(BlockPos p) {
			return new ActiveTransport(request, state, source, destination, itemId, givePlayerId, p, collectQueue, enderOwner);
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
			// 心跳：附近有玩家的铜傀儡自主行动
			try {
				tickHeartbeats(server);
			} catch (RuntimeException e) {
				LOGGER.error("Copper golem heartbeat failed", e);
			}
			// 低频清理：无对话超 20 分钟的傀儡会话（spec：死亡/卸载后清空，防内存增长）
			if ((server.getTickCount() & 1199) == 0) {
				long cutoff = server.getTickCount() - 24000L;
				LAST_REPLY_TICK.entrySet().removeIf(e -> e.getValue() < cutoff);
				SESSIONS.keySet().removeIf(id -> !LAST_REPLY_TICK.containsKey(id));
				// 注意：LAST_HEARTBEAT_TICK 不清理——删除会让无会话傀儡的心跳状态丢失 → 立即重触发（心跳风暴）
				GOLEM_MESSAGES.entrySet().removeIf(e -> e.getValue().stream().allMatch(m -> m.expireTick() < server.getTickCount()));
				RENAMES.entrySet().removeIf(e -> CopperGolemRename.RenameState.isExpired(e.getValue(), server.getTickCount()));
			}
		});
	}

	private static void tickHeartbeats(net.minecraft.server.MinecraftServer server) {
		int interval = QuirkyConfigHolder.get().heartbeatIntervalSeconds;
		if (interval <= 0) {
			return;
		}
		long nowTick = server.getTickCount();
		for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
			for (CopperGolem golem : level.getEntities(EntityTypeTest.forClass(CopperGolem.class), e -> !e.isRemoved())) {
				UUID golemId = golem.getUUID();
				Long last = LAST_HEARTBEAT_TICK.get(golemId);
				// 便宜判断前置：间隔未到（map 命中）直接跳过，避免每 tick 空扫实体
				if (last != null && nowTick - last < interval * 20L) {
					continue;
				}
				// busy 只挡"正在干活"的任务（搬运/捡取中）+ 对话在途；跟随/接近是自主行动，不挡心跳
				boolean busy = ACTIVE_TRANSPORTS.containsKey(golemId) || ACTIVE_COLLECTS.containsKey(golemId)
					|| PENDING_REPLIES.containsKey(golemId);
				boolean playerNearby = !level.getEntities(EntityTypeTest.forClass(net.minecraft.server.level.ServerPlayer.class),
					new AABB(golem.blockPosition()).inflate(CopperGolemHeartbeat.HEARTBEAT_PLAYER_RANGE), e -> !e.isRemoved()).isEmpty();
				if (!CopperGolemHeartbeat.shouldHeartbeat(interval, nowTick, last == null ? 0 : last, playerNearby, busy)) {
					continue;
				}
				LAST_HEARTBEAT_TICK.put(golemId, nowTick);
				LOGGER.info("heartbeat fired for golem {}", golemId);
				fireHeartbeat(golem, level, nowTick);
			}
		}
	}

	/** 发起一次心跳：独立上下文（不进长期历史），AI 自主决策；无事静默，有内容搭话（限流）。 */
	/** system prompt：{NAME} 占位符替换为玩家命名的名字（未命名用占位默认）+ 实时感知注入 + 心情 + 天线引导。 */
	private static String buildSystemPrompt(CopperGolem golem, @Nullable ServerPlayer chatPlayer) {
		Component customName = golem.getCustomName();
		String name = customName == null ? "无名的小铜傀儡" : customName.getString();
		ItemStack antenna = golem.getItemBySlot(EquipmentSlot.SADDLE);
		String antennaLine = antenna.isEmpty() ? ""
			: "你头顶戴着" + antenna.getHoverName().getString() + "，可以自然地炫耀或回应关于它的提问。";
		CopperGolemAgentMood.Mood mood = CopperGolemAgentMood.moodFor(MOOD_SCORES.getOrDefault(golem.getUUID(), 0));
		String chatter = chatPlayer == null ? ""
			: "[玩家]" + chatPlayer.getName().getString() + " 正在跟你说话——直接回应它，称它" + chatPlayer.getName().getString() + "。";
		return CopperGolemAiHttp.SYSTEM_PROMPT.replace("{NAME}", name) + "\n"
			+ chatter + realtimeContext(golem) + consumeGolemMessages(golem) + antennaLine + CopperGolemAgentMood.toPrompt(mood);
	}

	/** 实时感知注入：附近玩家（最近 2 个：名字/距离/手持）+ 天气时间 + 自身手持。一行内，AI 无需调工具即有临场感。 */
	private static String realtimeContext(CopperGolem golem) {
		try {
			ServerLevel level = (ServerLevel) golem.level();
			StringBuilder sb = new StringBuilder("[此刻]");
			List<ServerPlayer> nearby = level.getEntities(EntityTypeTest.forClass(ServerPlayer.class),
				new AABB(golem.blockPosition()).inflate(CopperGolemHeartbeat.HEARTBEAT_PLAYER_RANGE), e -> !e.isRemoved()).stream()
				.sorted(Comparator.comparingDouble(p -> p.distanceToSqr(golem)))
				.limit(2)
				.toList();
			if (nearby.isEmpty()) {
				sb.append("附近没有玩家");
			} else {
				sb.append("玩家 ");
				for (int i = 0; i < nearby.size(); i++) {
					ServerPlayer p = nearby.get(i);
					ItemStack held = p.getMainHandItem();
					sb.append(p.getName().getString()).append("(")
						.append((int) Math.round(Math.sqrt(p.distanceToSqr(golem)))).append("格")
						.append("，手持").append(held.isEmpty() ? "空手" : held.getHoverName().getString()).append(")");
					if (i < nearby.size() - 1) {
						sb.append("、");
					}
				}
			}
			ItemStack held = golem.getMainHandItem();
			sb.append("；天气").append(level.isThundering() ? "雷雨" : level.isRaining() ? "下雨" : "晴")
				.append("；你手里").append(held.isEmpty() ? "空" : held.getHoverName().getString());
			// 同伴傀儡（名字+坐标）：AI 认识同类、知道去哪找它们
			List<CopperGolem> pals = level.getEntities(EntityTypeTest.forClass(CopperGolem.class),
					new AABB(golem.blockPosition()).inflate(CopperGolemHeartbeat.HEARTBEAT_PLAYER_RANGE),
					e -> !e.isRemoved() && e != golem).stream()
				.sorted(Comparator.comparingDouble(p -> p.distanceToSqr(golem)))
				.limit(2)
				.toList();
			if (!pals.isEmpty()) {
				sb.append("；附近的同伴傀儡 ");
				for (int i = 0; i < pals.size(); i++) {
					CopperGolem p = pals.get(i);
					sb.append(p.getDisplayName().getString()).append("(")
						.append((int) p.getX()).append(",").append((int) p.getY()).append(",").append((int) p.getZ()).append(")");
					if (i < pals.size() - 1) {
						sb.append("、");
					}
				}
			}
			return sb.append("。").toString();
		} catch (Exception e) {
			return ""; // 注入失败不影响主流程
		}
	}

	/** 心跳里心情衰减（每心跳 -1 向平静回归）。 */
	private static void decayMood(CopperGolem golem) {
		MOOD_SCORES.compute(golem.getUUID(), (id, score) -> CopperGolemAgentMood.decay(score == null ? 0 : score));
	}

	private static void fireHeartbeat(CopperGolem golem, ServerLevel level, long nowTick) {
		decayMood(golem);
		String systemPrompt = buildSystemPrompt(golem, null)
			+ "\n现在是自主行动时间：至少做一件事——查看周围（look_containers/get_player_status/get_world_info），"
			+ "做点有用的事（捡掉落物/搬东西/跟着玩家/去看看生物）。"
			+ "玩家在旁边时主动打个招呼或汇报你在干什么（比如'我刚捡了 X'），看到有趣的事（玩家戴了新帽子、箱子里有奇怪的东西）可以说出来，"
			+ "但不要编造没看到的东西；实在没什么可做的才回复：无事。";
		CopperGolemAgentLoop loop = new CopperGolemAgentLoop(QuirkyConfigHolder.get(), systemPrompt, List.of(), "[自主行动时间]");
		CopperGolemAgentTools.ToolContext ctx = new CopperGolemAgentTools.ToolContext(golem, level, null, new java.util.HashSet<>());
		IO.submit(() -> {
			try {
				String reply = loop.run(
					body -> post(CopperGolemAiHttp.endpoint(QuirkyConfigHolder.get()), QuirkyConfigHolder.get().aiApiKey, body),
					(name, args) -> executeOnServerThread(null, golem, ctx, name, args));
				PENDING_TASKS.add(() -> {
					if (golem.isRemoved()) {
						return; // 傀儡已死/消失：不广播
					}
					boolean replyingToGolem = GOLEM_MESSAGE_REPLYING.remove(golem.getUUID()); // 标记只服务这一次回复（先取后判）
					if (reply == null || reply.isBlank() || reply.equals("无事") || reply.contains("无事")) {
						LOGGER.info("heartbeat golem {} silent: {}", golem.getUUID(), reply == null ? "null" : reply);
						return; // 静默
					}
					Long lastChatter = LAST_CHATTER_TICK.get(golem.getUUID());
					if (lastChatter != null && nowTick - lastChatter < CopperGolemHeartbeat.CHATTER_COOLDOWN_TICKS
						&& !replyingToGolem) {
						LOGGER.info("heartbeat golem {} chatter throttled: {}", golem.getUUID(), reply);
						return; // 搭话限流（同伴留言的回应放行）
					}
					LAST_CHATTER_TICK.put(golem.getUUID(), nowTick);
					LOGGER.info("heartbeat golem {} chatters: {}", golem.getUUID(), reply);
					Component message = Component.literal("[" + golem.getDisplayName().getString() + "] " + reply)
						.withStyle(ChatFormatting.DARK_AQUA);
					for (net.minecraft.server.level.ServerPlayer p : level.getEntities(EntityTypeTest.forClass(net.minecraft.server.level.ServerPlayer.class),
						new AABB(golem.blockPosition()).inflate(CopperGolemHeartbeat.HEARTBEAT_PLAYER_RANGE), e -> !e.isRemoved())) {
						p.sendSystemMessage(message);
					}
				});
			} catch (Exception e) {
				GOLEM_MESSAGE_REPLYING.remove(golem.getUUID());
				LOGGER.info("Copper golem heartbeat failed, skipped: {}", e.toString());
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
		// 待命名通道：发起者下一条消息 = 名字（不进 AI 对话）
		if (consumeRename(player, level, text)) {
			return;
		}
		// 名字分拣：消息含某傀儡名字（大小写不敏感）→ 全部触发；否则最近者
		List<CopperGolem> targets = findTargetGolems(level, player, config.aiListenRange, text);
		if (targets.isEmpty()) {
			return;
		}
		for (CopperGolem golem : targets) {
			handleGolemMessage(level, player, config, text, golem, gameTime);
		}
	}

	/** 单只傀儡的消息处理：冷却 → 会话 → 命令/压缩/对话（每只独立，多名字可同时触发）。 */
	private static void handleGolemMessage(ServerLevel level, ServerPlayer player, QuirkyConfig config, String text, CopperGolem golem, long gameTime) {
		UUID golemId = golem.getUUID();
		Long last = LAST_REPLY_TICK.get(golemId);
		if (last != null && gameTime - last < config.aiCooldownTicks) {
			return;
		}
		LAST_REPLY_TICK.put(golemId, gameTime);

		CopperGolemAiHistory.GolemSession session = SESSIONS.computeIfAbsent(golemId, id -> new CopperGolemAiHistory.GolemSession());
		// 会话管理命令（隔离记忆/提前压缩）：不进 AI 对话
		if (CopperGolemAiHistory.isResetCommand(text)) {
			session.clear();
			reply(player, golem, "好，之前的都忘了，重新认识一下！");
			return;
		}
		if (CopperGolemAiHistory.isCompressCommand(text)) {
			startCompression(player, golem, session, config, null); // null=手动压缩：命令不进历史、压缩完不重发
			return;
		}
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

	/** 名字分拣：范围内（默认 8 格）名字含于消息的傀儡（大小写不敏感）全部返回；无名字匹配 → 最近者。 */
	private static List<CopperGolem> findTargetGolems(ServerLevel level, ServerPlayer player, int range, String text) {
		AABB box = new AABB(player.blockPosition()).inflate(range);
		List<CopperGolem> golems = level.getEntities(EntityTypeTest.forClass(CopperGolem.class), box, e -> !e.isRemoved());
		String lower = text.toLowerCase();
		List<CopperGolem> byName = golems.stream()
			.filter(g -> g.getCustomName() != null && lower.contains(g.getName().getString().toLowerCase()))
			.toList();
		if (!byName.isEmpty()) {
			return byName;
		}
		CopperGolem nearest = golems.stream()
			.min(Comparator.comparingDouble(g -> g.distanceToSqr(player)))
			.orElse(null);
		return nearest == null ? List.of() : List.of(nearest);
	}

	// ===== 右键改名（聊天栏输入式）=====

	/** 右键进入待命名（mixin 调用）：仅限未命名的傀儡（首次命名）；已命名只能用命名牌改。
	 * 同一玩家同时只能对一个傀儡改名。 */
	public static boolean tryEnterRename(net.minecraft.world.entity.player.Player player, CopperGolem golem) {
		if (!(player instanceof ServerPlayer sp)) {
			return false; // 仅服务端
		}
		if (golem.getCustomName() != null) {
			return false; // 已有名字：改名走命名牌（原版），右键不改
		}
		RENAMES.entrySet().removeIf(e -> CopperGolemRename.RenameState.isOwner(e.getValue(), player.getUUID()));
		long expireTick = player.level().getGameTime() + CopperGolemRename.RENAME_TIMEOUT_TICKS;
		RENAMES.put(golem.getUUID(), new CopperGolemRename.RenameState(player.getUUID(), expireTick, golem.level().dimension().identifier()));
		sp.sendSystemMessage(Component.literal("[" + golem.getDisplayName().getString() + "] 你想给我起什么名字？直接输入，30 秒内有效")
			.withStyle(ChatFormatting.DARK_AQUA));
		player.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_SPIN, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** 待命名通道消费：发起者的消息设为名字；空白取消；过期/跨维度忽略。返回 true=消息被命名通道消费。 */
	private static boolean consumeRename(ServerPlayer player, ServerLevel level, String text) {
		UUID golemId = null;
		for (var entry : RENAMES.entrySet()) {
			CopperGolemRename.RenameState state = entry.getValue();
			if (CopperGolemRename.RenameState.isOwner(state, player.getUUID())
				&& CopperGolemRename.RenameState.isSameDimension(state, level.dimension().identifier())
				&& !CopperGolemRename.RenameState.isExpired(state, level.getGameTime())) {
				golemId = entry.getKey();
				break;
			}
		}
		if (golemId == null) {
			return false;
		}
		CopperGolemRename.RenameState state = RENAMES.remove(golemId);
		CopperGolem golem = (CopperGolem) level.getEntity(golemId);
		if (golem == null || golem.isRemoved()) {
			return true;
		}
		if (text.isBlank()) {
			player.sendSystemMessage(Component.literal("[" + golem.getDisplayName().getString() + "] 改名取消了"));
			return true;
		}
		golem.setCustomName(Component.literal(CopperGolemRename.truncate(text)));
		triggerSpin(golem); // 命名成功 → 转圈庆祝
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.literal("[" + golem.getDisplayName().getString() + "] 记住了，以后叫我 " + CopperGolemRename.truncate(text) + "！")
			.withStyle(ChatFormatting.DARK_AQUA));
		return true;
	}

	/** 工具执行 dispatch 到服务端 tick 线程：副作用（Brain 记忆/entityData/容器）必须在游戏线程；IO 线程阻塞等待结果。
	 *  ctx 由 loop 级创建（一次对话/心跳共享）：knownItems 在工具间累积（look → transport 同一轮有效）。 */
	private static String executeOnServerThread(@Nullable ServerPlayer player, CopperGolem golem,
		CopperGolemAgentTools.ToolContext ctx, String name, String args) {
		if (!(golem.level() instanceof ServerLevel level)) {
			return "{\"error\":\"no server level\"}";
		}
		java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
		level.getServer().execute(() -> {
			try {
				future.complete(CopperGolemAgentTools.execute(name, args, ctx));
			} catch (Throwable t) {
				future.complete("{\"error\":\"" + t.getClass().getSimpleName() + "\"}");
			}
		});
		try {
			return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			return "{\"error\":\"tool timeout\"}";
		}
	}

	/** tell_golem：给 32 格内名字匹配的同伴傀儡留言（大小写不敏感）。返回 ok 或 error。 */
	public static String tellGolem(CopperGolem from, ServerLevel level, String targetName, String message) {
		if (targetName == null || targetName.isBlank()) {
			return "{\"error\":\"需要目标名字\"}";
		}
		if (message == null || message.isBlank()) {
			return "{\"error\":\"消息不能为空\"}";
		}
		AABB box = new AABB(from.blockPosition()).inflate(32);
		String lower = targetName.toLowerCase();
		CopperGolem target = level.getEntities(EntityTypeTest.forClass(CopperGolem.class), box, e -> !e.isRemoved() && e != from).stream()
			.filter(g -> g.getCustomName() != null && g.getName().getString().toLowerCase().equals(lower))
			.min(Comparator.comparingDouble(g -> g.distanceToSqr(from)))
			.orElse(null);
		if (target == null) {
			return "{\"error\":\"32 格内没有叫 " + targetName + " 的同伴傀儡\"}";
		}
		String fromName = from.getCustomName() == null ? "无名铜傀儡" : from.getCustomName().getString();
		GOLEM_MESSAGES.computeIfAbsent(target.getUUID(), k -> new java.util.ArrayList<>())
			.add(new GolemMessage(fromName, message, level.getGameTime() + GOLEM_MESSAGE_TTL_TICKS));
		LOGGER.info("golem tell {} -> {}: {}", fromName, target.getName().getString(), message);
		return "{\"ok\":\"留言已捎给 " + target.getName().getString() + "，它看到会回应\"}";
	}

	/** 消费留言：注入 system prompt 的文本（最多 2 条，注入后删除），无留言返回空串。 */
	private static String consumeGolemMessages(CopperGolem golem) {
		List<GolemMessage> list = GOLEM_MESSAGES.get(golem.getUUID());
		if (list == null || list.isEmpty()) {
			return "";
		}
		long now = golem.level().getGameTime();
		List<GolemMessage> fresh = list.stream().filter(m -> m.expireTick() >= now).toList();
		// 只消费注入的部分（最多 2 条）+ 过期条；第 3+ 条保留到下轮（防丢失）
		int take = Math.min(fresh.size(), 2);
		List<GolemMessage> expired = list.stream().filter(m -> m.expireTick() < now).toList();
		if (take == 0) {
			if (!expired.isEmpty()) {
				GOLEM_MESSAGES.remove(golem.getUUID());
			}
			return "";
		}
		GOLEM_MESSAGES.compute(golem.getUUID(), (id, old) -> {
			if (old == null) {
				return null;
			}
			List<GolemMessage> kept = old.stream().filter(m -> m.expireTick() >= now).skip(take).toList();
			return kept.isEmpty() ? null : new java.util.ArrayList<>(kept);
		});
		GOLEM_MESSAGE_REPLYING.add(golem.getUUID()); // 本轮回应对同伴留言 → 心跳回复不限流
		StringBuilder sb = new StringBuilder("[同伴留言]");
		for (int i = 0; i < take; i++) {
			if (i > 0) {
				sb.append("；");
			}
			GolemMessage m = fresh.get(i);
			sb.append(m.fromName()).append(" 对你说：").append(m.text());
		}
		LOGGER.info("golem {} consumes {} golem message(s)", golem.getUUID(), take);
		return sb.append("。同伴叫你时你要回应它或行动。").toString();
	}

	private static void requestReply(ServerPlayer player, CopperGolem golem, CopperGolemAiHistory.GolemSession session, QuirkyConfig config, String text) {
		int delta = CopperGolemAgentMood.processWord(text);
		if (delta != 0) {
			MOOD_SCORES.merge(golem.getUUID(), delta, Integer::sum);
			if (delta > 0) {
				triggerSpin(golem); // 被夸 → 开心转圈
			}
		}
		String systemPrompt = buildSystemPrompt(golem, player);
		CopperGolemAgentLoop loop = new CopperGolemAgentLoop(config, systemPrompt, session.messages(), text);
		CopperGolemAgentTools.ToolContext ctx = new CopperGolemAgentTools.ToolContext(golem,
			(ServerLevel) player.level(), player, new java.util.HashSet<>());
		PENDING_REPLIES.put(golem.getUUID(), true); // 对话在途（心跳 busy 检查）
		CHAT_PLAYERS.put(golem.getUUID(), player.getUUID()); // 转头看向说话者
		IO.submit(() -> {
			try {
				String reply = loop.run(
					body -> post(CopperGolemAiHttp.endpoint(config), config.aiApiKey, body),
					(name, args) -> executeOnServerThread(player, golem, ctx, name, args));
				PENDING_TASKS.add(() -> {
					PENDING_REPLIES.remove(golem.getUUID());
					CHAT_PLAYERS.remove(golem.getUUID());
					GOLEM_MESSAGE_REPLYING.remove(golem.getUUID()); // 对话可能消费过留言，防下次心跳误放行
					LOGGER.info("golem reply: {}", reply);
					session.addGolemReply(reply);
					reply(player, golem, reply);
				});
			} catch (Exception e) {
				PENDING_REPLIES.remove(golem.getUUID());
				CHAT_PLAYERS.remove(golem.getUUID());
				GOLEM_MESSAGE_REPLYING.remove(golem.getUUID());
				LOGGER.warn("Copper golem AI request failed: {}", e.toString());
			}
		});
	}

	private static void startCompression(ServerPlayer player, CopperGolem golem, CopperGolemAiHistory.GolemSession session, QuirkyConfig config, String currentText) {
		// 自动压缩：当前消息已进历史 → 摘出，压缩完成后重发；手动压缩（currentText==null）：命令未进历史，不摘不重发
		if (currentText != null) {
			session.removeLastPlayerMessage();
		}
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
					// 当前消息作为新消息正常回复（手动压缩则无当前消息）
					if (currentText != null) {
						requestReply(player, golem, session, config, currentText);
					}
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
					if (currentText != null) {
						requestReply(player, golem, session, config, currentText);
					}
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

	// ===== 工具查询 =====

	/** 最近被闪电劈的时间（tick）。 */
	public static void recordLightning(CopperGolem golem) {
		LAST_LIGHTNING.put(golem.getUUID(), golem.level().getGameTime());
		triggerSpin(golem); // 被雷劈（除锈）→ 兴奋转圈
	}

	/** 闪电感知描述（get_self_status 用）。 */
	public static String lightningInfo(CopperGolem golem) {
		Long tick = LAST_LIGHTNING.get(golem.getUUID());
		if (tick == null) {
			return "从未被劈过";
		}
		long seconds = (golem.level().getGameTime() - tick) / 20L;
		return seconds < 60 ? seconds + " 秒前被闪电劈过" : (seconds / 60) + " 分钟前被闪电劈过";
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
		ACTIVE_MOVES.clear();
		GOLEM_MESSAGES.clear();
		GOLEM_MESSAGE_REPLYING.clear();
		PENDING_REPLIES.clear();
		CHAT_PLAYERS.clear();
		SPIN_TICKS.clear();
		LAST_HEARTBEAT_TICK.clear();
		LAST_CHATTER_TICK.clear();
		MOOD_SCORES.clear();
		LAST_LIGHTNING.clear();
		RENAMES.clear();
	}

	// ===== V1 搬运执行 =====

	/** 处理 transport 意图：白名单 + 准心射线 + 启动搬运；任一不满足 → 提示并放弃。 */
	/** transport 工具执行：格式校验 + 定位 + 注册任务；返回工具结果 JSON（给 AI）。 */
	public static String handleTransportRequest(CopperGolem golem, ServerLevel level, @Nullable ServerPlayer player,
		java.util.Set<String> knownItems, CopperGolemAiIntent.TransportRequest req) {
		String itemId = CopperGolemAiIntent.normalizeItem(req.item());
		if (!CopperGolemAiIntent.isPlausibleItem(itemId)) {
			return "{\"error\":\"物品名不合法\"}";
		}
		// 放宽白名单：AI 可能凭心跳/之前对话的感知搬物品（knownItems 只收当次 look），找不到会如实报"没找到"
		if (!CopperGolemAiIntent.isKnownItem(itemId, knownItems)) {
			LOGGER.info("golem transport item {} not seen this turn, attempting anyway", itemId);
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
		UUID enderOwner = enderOwnerOf(level, source, player);
		if (containerAt(level, source, enderOwner) == null) {
			return "{\"error\":\"取货位置不是容器（末影箱需玩家在场）\"}";
		}
		// 距离校验：源/目标超过 64 格不接受（防 AI 幻觉/过期坐标导致无限远行）
		if (source.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > MAX_TRANSPORT_DISTANCE * MAX_TRANSPORT_DISTANCE) {
			return "{\"error\":\"取货位置太远了（超过 " + MAX_TRANSPORT_DISTANCE + " 格）\"}";
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
			if (enderOwner == null) {
				enderOwner = enderOwnerOf(level, destination, player);
			}
			if (containerAt(level, destination, enderOwner) == null) {
				return "{\"error\":\"放货位置不是容器（末影箱需玩家在场）\"}";
			}
			if (destination.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > MAX_TRANSPORT_DISTANCE * MAX_TRANSPORT_DISTANCE) {
				return "{\"error\":\"放货位置太远了（超过 " + MAX_TRANSPORT_DISTANCE + " 格）\"}";
			}
		}
		// 未感知物品预检：源容器实际有货才搬（防 AI 幻觉 ID 白跑；凭记忆搬真实存在的货放行）
		if (!"any".equals(itemId)) {
			Container src = containerAt(level, source, enderOwner);
			if (src != null && !hasItem(src, itemId)) {
				return "{\"error\":\"" + itemId + " 不在这个容器里，先 look_containers 确认\"}";
			}
		}
		// 暂停原版运输 AI（调度枢纽：有 cooldown memory 则原版运输行为不启动）
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
		clearOtherTasks(golem, "transport");
		TRANSPORT_START_TICK.put(golem.getUUID(), level.getGameTime());
		ACTIVE_TRANSPORTS.put(golem.getUUID(),
			new ActiveTransport(req, CopperGolemTransportTask.State.WALK_SOURCE, source, destination, itemId,
				toPlayer ? player.getUUID() : null, null, null, enderOwner));
		return "{\"ok\":\"开始搬运 " + itemId + "\"}";
	}

	/** 该位置是否为末影箱方块 → 归属玩家 UUID（无玩家上下文 → null）。 */
	private static @Nullable UUID enderOwnerOf(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
		if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
			return player == null ? null : player.getUUID();
		}
		return null;
	}

	/** 玩家准心射线（≤6 格）命中且为容器的方块位置；未命中/非容器 → null。 */
	private static @Nullable BlockPos findTargetedContainer(ServerPlayer player, ServerLevel level) {
		HitResult hit = player.pick(TARGETED_RAYCAST_DISTANCE, 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = ((BlockHitResult) hit).getBlockPos();
		return containerAt(level, pos, null) != null ? pos : null;
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
	/** 拾取任务：pos=当前目标位置；itemEntityId=当前目标实体；itemId=当前物品；queue=剩余批量目标（前 5 个，按距离排序）。 */
	private record CollectTask(BlockPos pos, UUID itemEntityId, String itemId, List<UUID> queue) {
	}

	/** 开始捡掉落物：找 range 内最近 ItemEntity → 注册 COLLECT 任务。 */
	/** 清扫掉落物：找附近全部掉落物（最多 64 个安全上限），逐个捡并自动放铜箱；队列自动延续到捡完或被打断。 */
	public static String startCollect(CopperGolem golem, ServerLevel level, int range) {
		AABB box = new AABB(golem.blockPosition()).inflate(range);
		List<net.minecraft.world.entity.item.ItemEntity> all = level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class),
				box, e -> !e.isRemoved()).stream()
			.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(golem)))
			.toList();
		if (all.isEmpty()) {
			return "{\"ok\":\"附近没有掉落物\"}";
		}
		clearOtherTasks(golem, "collect"); // 确认有活干才顶掉其他任务（防空操作误杀）
		boolean truncated = all.size() > 64;
		List<net.minecraft.world.entity.item.ItemEntity> items = all.subList(0, Math.min(all.size(), 64));
		net.minecraft.world.entity.item.ItemEntity first = items.get(0);
		List<UUID> queue = items.subList(1, items.size()).stream().map(net.minecraft.world.entity.Entity::getUUID).toList();
		ACTIVE_COLLECTS.put(golem.getUUID(), new CollectTask(first.blockPosition(), first.getUUID(),
			BuiltInRegistries.ITEM.getKey(first.getItem().getItem()).toString(), queue));
		return "{\"ok\":\"发现 " + all.size() + " 个掉落物" + (truncated ? "（超过 64 个，先捡前 64 个）" : "，开始逐个捡") + "\"}";
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
			continueCollect(golem, task.queue()); // 目标消失：跳过它，批量链继续
			return;
		}
		// 主手非空（搬运中/放不下残留/铜箱缺失滞留）：先把手里的放回铜箱，再回来捡当前目标（防 setItemSlot 静默销毁旧物品）
		if (!golem.getMainHandItem().isEmpty()) {
			BlockPos copper = findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE);
			if (copper == null) {
				return; // 无铜箱可放：保留手里物品，放弃本次捡取（不销毁任何东西）
			}
			List<UUID> newQueue = new java.util.ArrayList<>();
			newQueue.add(task.itemEntityId()); // 队列头 = 当前没捡成的目标
			newQueue.addAll(task.queue());
			golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
			CopperGolemAiIntent.TransportRequest req = new CopperGolemAiIntent.TransportRequest(
				BuiltInRegistries.ITEM.getKey(golem.getMainHandItem().getItem()).toString(), "copper",
				copper.getX() + "," + copper.getY() + "," + copper.getZ());
			ACTIVE_TRANSPORTS.put(golem.getUUID(),
				new ActiveTransport(req, CopperGolemTransportTask.State.WALK_DEST, golem.blockPosition(), copper, req.item(), null, null, newQueue, null));
			return;
		}
		ItemStack stack = item.getItem();
		item.discard();
		golem.setItemSlot(EquipmentSlot.MAINHAND, stack);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		golem.setState(CopperGolemState.GETTING_ITEM);
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_ITEM_GET, SoundSource.PLAYERS, 1.0F, 1.0F);
		// 转入 TRANSPORT：带回最近铜箱（跳过取货，直接放货）；批量链剩余目标随任务传递
		BlockPos copper = findNearestCopperChest(golem, level, MAX_TRANSPORT_DISTANCE);
		if (copper == null) {
			return;
		}
		golem.getBrain().setMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, 6000);
		CopperGolemAiIntent.TransportRequest req = new CopperGolemAiIntent.TransportRequest(
			BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "copper", copper.getX() + "," + copper.getY() + "," + copper.getZ());
		ACTIVE_TRANSPORTS.put(golem.getUUID(),
			new ActiveTransport(req, CopperGolemTransportTask.State.WALK_DEST, task.pos(), copper, req.item(), null, null, task.queue(), null));
	}

	/** 移动中任务（move_to）：tick 推进，到达/卡住/超时收尾。 */
	private record ActiveMove(BlockPos target, BlockPos lastPos, int stuckTicks, int totalTicks, boolean retried) {
	}

	private static final Map<UUID, ActiveMove> ACTIVE_MOVES = new ConcurrentHashMap<>();

	/** 开始移动：注册 MOVING 任务（tick 每帧重设目标，到达≤1.5格完成；卡住重试一次后中止；60 秒超时中止）。 */
	public static String startMove(CopperGolem golem, BlockPos target) {
		ACTIVE_MOVES.put(golem.getUUID(), new ActiveMove(target, golem.blockPosition(), 0, 0, false));
		return "{\"ok\":\"正在前往 " + target.getX() + "," + target.getY() + "," + target.getZ() + "\"}";
	}

	private static void tickMove(CopperGolem golem, ServerLevel level) {
		ActiveMove m = ACTIVE_MOVES.get(golem.getUUID());
		if (m == null) {
			return;
		}
		if (golem.isRemoved()) {
			ACTIVE_MOVES.remove(golem.getUUID());
			return;
		}
		double dist = golem.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(m.target()));
		if (dist <= 1.5) {
			ACTIVE_MOVES.remove(golem.getUUID());
			golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return; // 到达：静默完成（AI 可自行感知位置）
		}
		if (m.totalTicks() >= 1200) { // 60 秒超时
			ACTIVE_MOVES.remove(golem.getUUID());
			golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			broadcastMoveAbort(golem, level, "太远了，我走不过去，先歇了");
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, m.target(), 1.0F, 0);
		int stuck = golem.blockPosition().equals(m.lastPos()) ? m.stuckTicks() + 1 : 0;
		if (stuck >= 40) { // 2 秒没换格 = 卡住
			if (!m.retried()) {
				ACTIVE_MOVES.put(golem.getUUID(), new ActiveMove(m.target(), golem.blockPosition(), 0, m.totalTicks() + 1, true));
				return; // 重试一次（重设目标）
			}
			ACTIVE_MOVES.remove(golem.getUUID());
			golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			broadcastMoveAbort(golem, level, "路被挡住了，我过不去");
			return;
		}
		ACTIVE_MOVES.put(golem.getUUID(), new ActiveMove(m.target(), golem.blockPosition(), stuck, m.totalTicks() + 1, m.retried()));
	}

	private static void broadcastMoveAbort(CopperGolem golem, ServerLevel level, String text) {
		Component msg = Component.literal("[" + golem.getDisplayName().getString() + "] " + text)
			.withStyle(ChatFormatting.DARK_AQUA);
		for (ServerPlayer p : level.getEntities(EntityTypeTest.forClass(ServerPlayer.class),
			new AABB(golem.blockPosition()).inflate(CopperGolemHeartbeat.HEARTBEAT_PLAYER_RANGE), e -> !e.isRemoved())) {
			p.sendSystemMessage(msg);
		}
	}

	/** 开始跟随玩家：注册 FOLLOW 任务（tick 持续刷新目标）。 */
	public static String startFollow(CopperGolem golem, ServerLevel level, String playerName) {
		clearOtherTasks(golem, "follow");
		ServerPlayer target = level.players().stream()
			.filter(p -> p.getName().getString().equals(playerName))
			.findFirst().orElse(null);
		if (target == null) {
			return "{\"error\":\"找不到玩家 " + playerName + "\"}";
		}
		ACTIVE_FOLLOWS.put(golem.getUUID(), target.getUUID());
		return "{\"ok\":\"开始跟着 " + playerName + "\"}";
	}

	public static String startApproach(CopperGolem golem, ServerLevel level, String entityTypeId) {
		clearOtherTasks(golem, "approach");
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
		if (found.get().distanceToSqr(golem) > MAX_TRANSPORT_DISTANCE * MAX_TRANSPORT_DISTANCE) {
			return "{\"error\":\"目标太远了（超过 " + MAX_TRANSPORT_DISTANCE + " 格）\"}";
		}
		ACTIVE_APPROACHES.put(golem.getUUID(), found.get().getUUID());
		return "{\"ok\":\"去看 " + entityTypeId + "\"}";
	}

	/** 新任务互斥：注册新任务时清掉其他任务（collect 顶 follow、follow 顶 collect……）；except 当前类型保留。 */
	static void clearOtherTasks(CopperGolem golem, String keep) {
		UUID golemId = golem.getUUID();
		if (!"move".equals(keep)) {
			ACTIVE_MOVES.remove(golemId);
		}
		if (!"follow".equals(keep)) {
			ACTIVE_FOLLOWS.remove(golemId);
		}
		if (!"approach".equals(keep)) {
			ACTIVE_APPROACHES.remove(golemId);
		}
		if (!"collect".equals(keep)) {
			ACTIVE_COLLECTS.remove(golemId);
		}
		if (!"transport".equals(keep)) {
			TRANSPORT_START_TICK.remove(golemId);
			ActiveTransport t = ACTIVE_TRANSPORTS.remove(golemId);
			if (t != null && t.openPos() != null) {
				// 打开中的箱子关闭（放回物品由 doPut 已处理；仅关动画；末影箱经 containerAt 走 enderOwner）
				if (golem.level() instanceof ServerLevel sl && containerAt(sl, t.openPos(), t.enderOwner()) instanceof Container c) {
					c.stopOpen(golem);
				}
			}
		}
	}
	/** 停止全部行动（移动/跟随/接近/搬运/捡取），恢复待机。 */
	public static String stopAll(CopperGolem golem) {
		ACTIVE_FOLLOWS.remove(golem.getUUID());
		ACTIVE_APPROACHES.remove(golem.getUUID());
		ACTIVE_COLLECTS.remove(golem.getUUID());
		ACTIVE_MOVES.remove(golem.getUUID());
		ActiveTransport t = ACTIVE_TRANSPORTS.remove(golem.getUUID());
		TRANSPORT_START_TICK.remove(golem.getUUID());
		if (t != null && t.openPos() != null) {
			if (golem.level() instanceof ServerLevel sl && containerAt(sl, t.openPos(), t.enderOwner()) instanceof Container c) {
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
		if (ACTIVE_MOVES.containsKey(id)) {
			return "正在前往某处";
		}
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
			ActiveTransport gone = ACTIVE_TRANSPORTS.remove(golem.getUUID());
			TRANSPORT_START_TICK.remove(golem.getUUID());
			if (gone != null && gone.openPos() != null
				&& containerAt(level, gone.openPos(), gone.enderOwner()) instanceof Container c) {
				c.stopOpen(golem); // 死亡也归还 openers 计数，防永久开盖
			}
			ACTIVE_FOLLOWS.remove(golem.getUUID());
			ACTIVE_APPROACHES.remove(golem.getUUID());
			ACTIVE_COLLECTS.remove(golem.getUUID());
			LAST_HEARTBEAT_TICK.remove(golem.getUUID()); // 实体已消失：回收心跳记录（不会复现风暴）
			return;
		}
		tickFollow(golem, level);
		tickApproach(golem, level);
		tickCollect(golem, level);
		tickTransportTask(golem, level);
		tickMove(golem, level);
		tickChatLook(golem, level);
		tickSpin(golem, level);
	}

	/** 对话在途：傀儡转头看向说话玩家（原版 LookAtTargetSink 消费 LOOK_TARGET）。 */
	private static void tickChatLook(CopperGolem golem, ServerLevel level) {
		UUID playerId = CHAT_PLAYERS.get(golem.getUUID());
		if (playerId == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
		if (player == null || !player.isAlive() || player.level() != level) {
			CHAT_PLAYERS.remove(golem.getUUID());
			return;
		}
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
			new net.minecraft.world.entity.ai.behavior.EntityTracker(player, true));
	}

	/** 情绪转圈：yRot 服务端推进 360°/2 秒（40 tick × 9°）+ 开始播 COPPER_GOLEM_SPIN。 */
	private static final int SPIN_TICKS_TOTAL = 40;
	private static final Map<UUID, Integer> SPIN_TICKS = new ConcurrentHashMap<>(); // golemId → 剩余 tick

	/** 触发转圈（幂等：转圈中不重置）。 */
	public static void triggerSpin(CopperGolem golem) {
		if (SPIN_TICKS.containsKey(golem.getUUID())) {
			return;
		}
		SPIN_TICKS.put(golem.getUUID(), SPIN_TICKS_TOTAL);
		if (golem.level() instanceof ServerLevel level) {
			level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.COPPER_GOLEM_SPIN,
				SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	private static void tickSpin(CopperGolem golem, ServerLevel level) {
		Integer left = SPIN_TICKS.get(golem.getUUID());
		if (left == null) {
			return;
		}
		if (left <= 0) {
			SPIN_TICKS.remove(golem.getUUID());
			return;
		}
		golem.setYRot(golem.getYRot() + 9.0F);
		SPIN_TICKS.put(golem.getUUID(), left - 1);
	}

	private static void tickFollow(CopperGolem golem, ServerLevel level) {
		UUID playerId = ACTIVE_FOLLOWS.get(golem.getUUID());
		if (playerId == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
		if (player == null || !player.isAlive() || player.level() != level) {
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

	private static final Map<UUID, Long> TRANSPORT_START_TICK = new ConcurrentHashMap<>(); // golemId → 任务起始 tick（超时中止用）

	private static void tickTransportTask(CopperGolem golem, ServerLevel level) {
		ActiveTransport t = ACTIVE_TRANSPORTS.get(golem.getUUID());
		if (t == null) {
			TRANSPORT_START_TICK.remove(golem.getUUID()); // 任务已清（stop/互斥/超时）：回收起始记录
			return;
		}
		if (t.state() == CopperGolemTransportTask.State.DONE || t.state() == CopperGolemTransportTask.State.FAIL) {
			TRANSPORT_START_TICK.remove(golem.getUUID());
			return;
		}
		if (golem.isRemoved()) {
			ACTIVE_TRANSPORTS.remove(golem.getUUID());
			TRANSPORT_START_TICK.remove(golem.getUUID());
			return;
		}
		// 超时中止：60 秒搬不完（路径被堵/目标消失）→ 关箱 + 广播，防无限游荡
		Long started = TRANSPORT_START_TICK.get(golem.getUUID());
		if (started != null && level.getGameTime() - started > 1200) {
			TRANSPORT_START_TICK.remove(golem.getUUID());
			ACTIVE_TRANSPORTS.remove(golem.getUUID());
			if (t.openPos() != null && containerAt(level, t.openPos(), t.enderOwner()) instanceof Container c) {
				c.stopOpen(golem);
			}
			golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
			golem.setState(CopperGolemState.IDLE);
			broadcastMoveAbort(golem, level, "搬太久了，我先歇了（箱子或路可能有问题）");
			return;
		}
		if (t.state() == CopperGolemTransportTask.State.WALK_SOURCE
			|| t.state() == CopperGolemTransportTask.State.WALK_DEST) {
			BlockPos target = t.state() == CopperGolemTransportTask.State.WALK_SOURCE
				? t.source()
				: (t.givePlayerId() != null ? giveTarget(level, t) : t.destination());
			if (target == null) {
				// give 且玩家不在/跨维度 → 中止；手里物品放回源容器（含末影箱：containerAt 走 enderOwner，防滞留后塞错箱子）
				if (t.givePlayerId() != null && !golem.getMainHandItem().isEmpty() && t.source() != null) {
					Container c = containerAt(level, t.source(), t.enderOwner());
					if (c != null && addToContainer(c, golem.getMainHandItem()).isEmpty()) {
						golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					}
				}
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
		return player != null && player.isAlive() && player.level() == level ? player.blockPosition() : null;
	}

	/** 递物品给玩家：走到面前 → 面朝玩家 → 掉落物交付 + 音效。 */
	private static void doGive(CopperGolem golem, ServerLevel level, ActiveTransport t) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.givePlayerId());
		if (player == null || !player.isAlive() || player.level() != level) {
			// 放回源容器成功才清空主手（防容器缺失/满时 setItemSlot 静默销毁物品）
			ItemStack held = golem.getMainHandItem();
			if (t.source() != null && !held.isEmpty()) {
				Container c = containerAt(level, t.source(), t.enderOwner());
				if (c != null && addToContainer(c, held).isEmpty()) {
					golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					replyTo(golem, level, t, "你不在，东西我先放回箱子了");
					return;
				}
			}
			replyTo(golem, level, t, "你不在，箱子也放不回去了，东西我帮你拿着");
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
		Container container = containerAt(level, t.source(), t.enderOwner());
		if (container == null) {
			replyTo(golem, level, t, "取货的箱子不见了，我搬不了");
			return false;
		}
		// 手里若已有物品（原版搬运残留/上一单没放完）：先放回源容器——放不回去就保留（绝不覆盖销毁）
		ItemStack held = golem.getMainHandItem();
		if (!held.isEmpty()) {
			ItemStack left = addToContainer(container, held);
			if (left.isEmpty()) {
				golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			} else {
				replyTo(golem, level, t, "我手里还有东西放不下（" + left.getHoverName().getString() + "×" + left.getCount() + "），先搬不了新的");
				return false;
			}
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
		Container container = containerAt(level, t.destination(), t.enderOwner());
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
			return;
		}
		triggerSpin(golem); // 搬完 → 小转圈
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

	/** 容器内是否有该物品（只读，不取出）。 */
	private static boolean hasItem(Container container, String itemId) {
		boolean any = itemId.equals("any");
		Item target = any ? null : BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && (any || stack.getItem() == target)) {
				return true;
			}
		}
		return false;
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

	/** 方块位置 → Container；末影箱 → 归属玩家的末影箱数据（PlayerEnderChestContainer）；非容器 → null。 */
	private static @Nullable Container containerAt(ServerLevel level, BlockPos pos, @Nullable UUID enderOwner) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
			if (enderOwner == null) {
				return null; // 末影箱需要玩家上下文
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(enderOwner);
			return owner == null ? null : owner.getEnderChestInventory();
		}
		if (be instanceof Container c) {
			return c;
		}
		if (level.getBlockState(pos).getBlock() instanceof ChestBlock cb) {
			return ChestBlock.getContainer(cb, level.getBlockState(pos), level, pos, false);
		}
		return null;
	}

	/** 结束任务：恢复原版状态（清 cooldown memory → 原版运输 AI 恢复），并关掉打开的容器（末影箱经 enderOwner）。 */
	private static void finishTransport(CopperGolem golem, ActiveTransport t) {
		golem.setState(CopperGolemState.IDLE);
		if (t.openPos() != null) {
			if (golem.level() instanceof ServerLevel sl && containerAt(sl, t.openPos(), t.enderOwner()) instanceof Container c) {
				c.stopOpen(golem);
			}
		}
		golem.clearOpenedChestPos();
		golem.getBrain().eraseMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS);
		ACTIVE_TRANSPORTS.remove(golem.getUUID());
		TRANSPORT_START_TICK.remove(golem.getUUID());
		// collect 批量链：剩余目标 → 自动继续捡
		if (t.collectQueue() != null && !t.collectQueue().isEmpty()) {
			continueCollect(golem, t.collectQueue());
		}
	}

	/** collect 批量链延续：取队列下一个目标实体 → 注册新 COLLECT。 */
	private static void continueCollect(CopperGolem golem, List<UUID> queue) {
		if (queue.isEmpty()) {
			return;
		}
		ServerLevel level = (ServerLevel) golem.level();
		UUID nextId = queue.get(0);
		List<UUID> rest = queue.size() > 1 ? queue.subList(1, queue.size()) : List.of();
		if (!(level.getEntity(nextId) instanceof net.minecraft.world.entity.item.ItemEntity item) || item.isRemoved()) {
			continueCollect(golem, rest); // 目标消失，跳过继续
			return;
		}
		ACTIVE_COLLECTS.put(golem.getUUID(), new CollectTask(item.blockPosition(), item.getUUID(),
			BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(), rest));
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
