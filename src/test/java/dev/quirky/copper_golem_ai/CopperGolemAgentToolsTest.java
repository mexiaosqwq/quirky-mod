package dev.quirky.copper_golem_ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemAgentToolsTest {

	@Test
	void toolsJsonIsValidWithTwelveFunctions() {
		JsonArray tools = JsonParser.parseString(CopperGolemAgentTools.TOOLS_JSON).getAsJsonArray();
		assertEquals(12, tools.size());
		Set<String> names = new java.util.HashSet<>();
		for (var el : tools) {
			JsonObject fn = el.getAsJsonObject().getAsJsonObject("function");
			assertEquals("function", el.getAsJsonObject().get("type").getAsString());
			names.add(fn.get("name").getAsString());
		}
		assertEquals(Set.of("look_containers", "get_player_status", "get_world_info", "get_self_status", "scan_mobs",
			"move_to", "follow_player", "approach_entity", "stop", "collect_dropped_items", "transport", "tell_golem"), names);
	}

	@Test
	void periodOfDayBoundaries() {
		assertEquals("白天", CopperGolemAgentTools.periodOfDay(6000));
		assertEquals("白天", CopperGolemAgentTools.periodOfDay(0));
		assertEquals("黄昏", CopperGolemAgentTools.periodOfDay(12000));
		assertEquals("夜晚", CopperGolemAgentTools.periodOfDay(13000));
		assertEquals("夜晚", CopperGolemAgentTools.periodOfDay(18000));
		assertEquals("黎明", CopperGolemAgentTools.periodOfDay(23000));
		assertEquals("白天", CopperGolemAgentTools.periodOfDay(24000));
	}

	@Test
	void formatContainersTruncates() {
		List<CopperGolemAgentTools.ContainerInfo> list = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			list.add(new CopperGolemAgentTools.ContainerInfo("chest", "100," + i + ",0", List.of("a×1", "b×2"), 2));
		}
		String out = CopperGolemAgentTools.formatContainers(list, 20, 10);
		assertTrue(out.contains("还有 5 个容器未列出"));

		List<CopperGolemAgentTools.ContainerInfo> one = List.of(
			new CopperGolemAgentTools.ContainerInfo("chest", "1,64,1", List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"), 12));
		String out2 = CopperGolemAgentTools.formatContainers(one, 20, 10);
		assertTrue(out2.contains("还有 2 种物品未列出"));
	}

	@Test
	void unknownToolReturnsError() {
		String out = CopperGolemAgentTools.execute("fly_away", "{}", null);
		assertTrue(out.contains("error"));
		assertTrue(out.contains("fly_away"));
	}

	@Test
	void rangeArgumentDefaultsToSixteen() {
		JsonObject args = new JsonObject();
		assertEquals(16, CopperGolemAgentTools.rangeOf(args, 16, 64));
		args.addProperty("range", 8);
		assertEquals(8, CopperGolemAgentTools.rangeOf(args, 16, 64));
		args.addProperty("range", "abc");
		assertEquals(16, CopperGolemAgentTools.rangeOf(args, 16, 64));
		// AI 参数不可信：越界 clamp 到 max、非正用默认（防超大 range 卡死服务端线程）
		args.addProperty("range", 100000);
		assertEquals(64, CopperGolemAgentTools.rangeOf(args, 16, 64));
		args.addProperty("range", -5);
		assertEquals(16, CopperGolemAgentTools.rangeOf(args, 16, 64));
		args.addProperty("range", 0);
		assertEquals(16, CopperGolemAgentTools.rangeOf(args, 16, 64));
	}
}
