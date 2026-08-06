package dev.quirky.copper_golem_ai;

import dev.quirky.copper_golem_ai.CopperGolemRename.RenameState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CopperGolemRenameTest {

	@Test
	void ownerMatchesOnlyRequester() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		RenameState state = new RenameState(a, 600, net.minecraft.resources.Identifier.parse("minecraft:overworld"));
		assertTrue(RenameState.isOwner(state, a));
		assertFalse(RenameState.isOwner(state, b));
	}

	@Test
	void dimensionMustMatch() {
		RenameState state = new RenameState(UUID.randomUUID(), 600, net.minecraft.resources.Identifier.parse("minecraft:overworld"));
		assertTrue(RenameState.isSameDimension(state, net.minecraft.resources.Identifier.parse("minecraft:overworld")));
		assertFalse(RenameState.isSameDimension(state, net.minecraft.resources.Identifier.parse("minecraft:the_nether")));
	}

	@Test
	void expiryBoundaries() {
		RenameState state = new RenameState(UUID.randomUUID(), 600, net.minecraft.resources.Identifier.parse("minecraft:overworld"));
		assertFalse(RenameState.isExpired(state, 600)); // 正好到期不失效
		assertFalse(RenameState.isExpired(state, 599));
		assertTrue(RenameState.isExpired(state, 601));
	}

	@Test
	void nameTruncation() {
		assertEquals("短", CopperGolemRename.truncate("短"));
		assertEquals(50, CopperGolemRename.truncate("长".repeat(100)).length());
	}
}
