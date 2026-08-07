package dev.quirky.copper_golem_ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.quirky.config.QuirkyConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemLoopTest {

	private static final String TOOL_RESPONSE = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
		+ "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_world_info\",\"arguments\":\"{}\"}},"
		+ "{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"get_self_status\",\"arguments\":\"{}\"}}"
		+ "]}}]}";

	private static final String TOOL_RESPONSE_SINGLE = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":["
		+ "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_world_info\",\"arguments\":\"{}\"}}"
		+ "]}}]}";

	private static final String REPLY_RESPONSE = "{\"choices\":[{\"message\":{\"content\":\"好了，我去看看\"}}]}";

	@Test
	void singleRoundReplyFinishesImmediately() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "附近有什么");
		String reply = loop.run(body -> REPLY_RESPONSE, (name, args) -> "{}");
		assertEquals("好了，我去看看", reply);
		assertEquals(0, loop.rounds());
	}

	@Test
	void toolRoundThenReplyAppendsToolMessages() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "附近有什么");
		String reply = loop.run(
			body -> {
				// 第二次请求应包含 assistant tool_calls + tool 结果消息
				JsonObject req = JsonParser.parseString(body).getAsJsonObject();
				JsonArray messages = req.getAsJsonArray("messages");
				int size = messages.size();
				if (size > 3) {
					JsonObject assistant = null;
					for (JsonElement el : messages) {
						JsonObject m = el.getAsJsonObject();
						if ("assistant".equals(m.get("role").getAsString()) && m.has("tool_calls")) {
							assistant = m;
							break;
						}
					}
					assertNotNull(assistant, "缺少 assistant tool_calls 消息");
					assertEquals(2, assistant.getAsJsonArray("tool_calls").size());
					JsonObject tool = messages.get(size - 1).getAsJsonObject();
					assertEquals("tool", tool.get("role").getAsString());
					assertEquals("call_2", tool.get("tool_call_id").getAsString());
				}
				return size > 3 ? REPLY_RESPONSE : TOOL_RESPONSE;
			},
			(name, args) -> "{\"time\":\"day\"}"
		);
		assertEquals("好了，我去看看", reply);
		assertEquals(1, loop.rounds());
	}

	@Test
	void twentyRoundsOfToolsHitsSafetyLimit() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "一直查");
		String reply = loop.run(body -> TOOL_RESPONSE, (name, args) -> "{}");
		assertTrue(reply.contains("走神"));
		assertEquals(20, loop.rounds());
	}

	@Test
	void executorExceptionBecomesErrorResultAndLoopContinues() throws Exception {
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "查一下");
		String reply = loop.run(
			body -> {
				JsonArray messages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
				if (messages.size() > 3) {
					// tool 结果应为 error JSON，循环继续
					JsonObject tool = messages.get(messages.size() - 1).getAsJsonObject();
					assertTrue(tool.get("content").getAsString().contains("error"));
					return REPLY_RESPONSE;
				}
				return TOOL_RESPONSE;
			},
			(name, args) -> { throw new RuntimeException("箱子被拆了"); }
		);
		assertEquals("好了，我去看看", reply);
		assertEquals(1, loop.rounds());
	}

	@Test
	void sameToolFailingThreeTimesBreaksLoop() throws Exception {
		// 满箱/目标消失等场景：AI 反复调同一工具失败 → 连续 3 次中断，不再烧 20 轮刷屏
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "搬一下");
		String reply = loop.run(body -> TOOL_RESPONSE_SINGLE, (name, args) -> "{\"error\":\"目标太远了\"}");
		assertTrue(reply.contains("反复试了几次"), "应返回中断说明，实际: " + reply);
		assertEquals(2, loop.rounds()); // 第 3 次失败在第三轮中断（rounds 未 ++）
	}

	@Test
	void toolSuccessResetsFailureCounter() throws Exception {
		// 失败→成功→失败→失败：成功重置计数，第三次失败才中断
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger apiCalls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "看看周围");
		String reply = loop.run(
			body -> apiCalls.getAndIncrement() == 0 ? TOOL_RESPONSE_SINGLE : REPLY_RESPONSE,
			(name, args) -> calls.getAndIncrement() == 0 ? "{\"error\":\"目标太远了\"}" : "{}"
		);
		assertEquals("好了，我去看看", reply);
		assertEquals(1, loop.rounds()); // 成功路径未被误中断
	}

	@Test
	void emptyReplyRetriesOnceThenFallsBack() throws Exception {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "说话");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			return "{\"choices\":[{\"message\":{\"content\":null}}]}";
		}, (name, args) -> "{}");
		// 空响应（无 content 无 tool_calls）重试一次仍空 → 兜底文案
		assertEquals("我有点走神了", reply);
		assertEquals(2, calls.get());
	}
	@Test
	void actionIntentWithNoToolsForcesRetryWithToolHint() throws Exception {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "把黑曜石搬到末影箱");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			if (calls.get() == 1) {
				return "{\"choices\":[{\"message\":{\"content\":\"好，这就去搬！\"}}]}";
			}
			JsonObject req = JsonParser.parseString(body).getAsJsonObject();
			JsonArray messages = req.getAsJsonArray("messages");
			String all = messages.toString();
			assertTrue(all.contains("光说不做"), "第二次请求应含硬校验提示: " + all);
			assertTrue(all.contains("transport"), "提示应点名 transport 工具: " + all);
			return "{\"choices\":[{\"message\":{\"content\":\"搬好了\"}}]}";
		}, (name, args) -> "{}");
		assertEquals("搬好了", reply);
		assertEquals(2, calls.get());
	}

	@Test
	void actionIntentRetriesAtMostTwice() throws Exception {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "去那边看看");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			return "{\"choices\":[{\"message\":{\"content\":\"好\"}}]}";
		}, (name, args) -> "{}");
		assertEquals("好", reply);
		assertEquals(3, calls.get());
	}

	@Test
	void doneStatementSkipsRetry() throws Exception {
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "把东西搬过去");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			return "{\"choices\":[{\"message\":{\"content\":\"已经搬完了\"}}]}";
		}, (name, args) -> "{}");
		assertEquals("已经搬完了", reply);
		assertEquals(1, calls.get());
	}

	@Test
	void toolsCalledEarlierSkipsHardVerification() throws Exception {
		// 第 1 轮已调 transport（已动手，含失败也算），第 2 轮纯文本无完成语 → 不再指控"光说不做"（deep-fix 条款）
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "把黑曜石搬到末影箱");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			if (calls.get() == 1) {
				return "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"transport\","
					+ "\"arguments\":\"{\\\"item\\\":\\\"minecraft:obsidian\\\",\\\"source\\\":\\\"copper\\\",\\\"destination\\\":\\\"copper\\\"}\"}}]}}]}";
			}
			String all = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages").toString();
			assertFalse(all.contains("光说不做"), "调过工具后不应再硬校验: " + all);
			return "{\"choices\":[{\"message\":{\"content\":\"东西放好了\"}}]}";
		}, (name, args) -> "{\"ok\":true}");
		assertEquals("东西放好了", reply);
		assertEquals(2, calls.get());
	}

	@Test
	void perceptionCallsBeyondLimitAreBlocked() throws Exception {
		// AI 无限循环调 look_containers：前 MAX_PERCEPTION_CALLS 次执行，之后被拦截（不调执行器，回传提示）
		java.util.concurrent.atomic.AtomicInteger executed = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "看看周围");
		String reply = loop.run(body ->
			"{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"p\",\"function\":{\"name\":\"look_containers\",\"arguments\":\"{}\"}}]}}]}",
			(name, args) -> {
				executed.incrementAndGet();
				return "{\"ok\":true}";
			});
		assertEquals(CopperGolemAgentLoop.MAX_PERCEPTION_CALLS, executed.get()); // 只执行 4 次，第 5 次起拦截
		assertFalse(reply.isBlank());
	}

	@Test
	void perceptionOnlyStillForcesActionRetry() throws Exception {
		// 只调过感知工具（look_containers）不算"已动手"——说"搬"却只 look 不 transport 仍触发硬校验
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		var loop = new CopperGolemAgentLoop(new QuirkyConfig(), "你是铜傀儡", List.of(), "把黑曜石搬到末影箱");
		String reply = loop.run(body -> {
			calls.incrementAndGet();
			if (calls.get() == 1) {
				return "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"look_containers\","
					+ "\"arguments\":\"{\\\"range\\\":32}\"}}]}}]}";
			}
			String all = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages").toString();
			if (calls.get() == 3) {
				assertTrue(all.contains("光说不做"), "只调感知工具后纯文本仍应硬校验: " + all);
			}
			return "{\"choices\":[{\"message\":{\"content\":\"好\"}}]}";
		}, (name, args) -> "{\"ok\":true}");
		assertEquals("好", reply);
		assertEquals(4, calls.get()); // 1 轮感知 + 2 次硬校验重试 + 1 次最终
	}
}