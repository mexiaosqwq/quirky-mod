package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import dev.quirky.TestBootstrap;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientMapTooltipComponentTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void metadataTextFormatsScaleAndLock() {
		Component scaled = ClientMapTooltipComponent.metadataText((byte) 1, false);
		assertEquals("tooltip.quirky.map.scale", ((TranslatableContents) scaled.getContents()).getKey());
		assertEquals(2, ((TranslatableContents) scaled.getContents()).getArgs()[0]);

		Component locked = ClientMapTooltipComponent.metadataText((byte) 2, true);
		TranslatableContents lockedContents = (TranslatableContents) locked.getContents();
		assertEquals("tooltip.quirky.map.scale_locked", lockedContents.getKey());
		Component inner = (Component) lockedContents.getArgs()[0];
		TranslatableContents innerContents = (TranslatableContents) inner.getContents();
		assertEquals("tooltip.quirky.map.scale", innerContents.getKey());
		assertEquals(4, innerContents.getArgs()[0]);
	}

	@Test
	void keepsVanillaParchmentBorderAroundMap() {
		ClientMapTooltipComponent component = new ClientMapTooltipComponent(new MapId(1));
		Font font = mock(Font.class);

		assertEquals(71, component.getWidth(font));
		assertEquals(71, component.getHeight(font));
	}
}
