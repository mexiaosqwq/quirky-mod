package dev.quirky.copper_golem_ai;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
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
	private static final ConcurrentLinkedQueue<Runnable> PENDING_TASKS = new ConcurrentLinkedQueue<>();
	private static volatile boolean initialized = false;

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
				PENDING_TASKS.add(() -> {
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
	}
}
