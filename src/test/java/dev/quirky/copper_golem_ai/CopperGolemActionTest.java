package dev.quirky.copper_golem_ai;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemActionTest {

	@Test
	void followStopsBeyondMaxDistance() {
		assertTrue(CopperGolemAgentTools.shouldStopFollow(5000, 64)); // 70^2 > 64^2
		assertFalse(CopperGolemAgentTools.shouldStopFollow(100, 64));
		assertFalse(CopperGolemAgentTools.shouldStopFollow(4096, 64)); // 正好 64 格不停止
	}

	@Test
	void entityTypeParsing() {
		assertEquals("minecraft:sheep", CopperGolemAgentTools.parseEntityType("sheep"));
		assertEquals("minecraft:zombie", CopperGolemAgentTools.parseEntityType("minecraft:zombie"));
		assertNull(CopperGolemAgentTools.parseEntityType(""));
		assertNull(CopperGolemAgentTools.parseEntityType("sheep/zombie"));
	}

	@Test
	void coordParsing() {
		BlockPos pos = CopperGolemAgentTools.parseCoords("12,64,-8");
		assertNotNull(pos);
		assertEquals(12, pos.getX());
		assertEquals(64, pos.getY());
		assertEquals(-8, pos.getZ());
		assertNull(CopperGolemAgentTools.parseCoords("12,64"));
		assertNull(CopperGolemAgentTools.parseCoords("a,b,c"));
		assertNull(CopperGolemAgentTools.parseCoords(""));
	}
}
