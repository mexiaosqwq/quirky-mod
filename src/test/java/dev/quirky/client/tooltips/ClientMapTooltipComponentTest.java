package dev.quirky.client.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import dev.quirky.TestBootstrap;
import net.minecraft.client.gui.Font;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientMapTooltipComponentTest {
	@BeforeAll
	static void bootStrap() {
		TestBootstrap.boot();
	}

	@Test
	void keepsVanillaParchmentBorderAroundMap() {
		ClientMapTooltipComponent component = new ClientMapTooltipComponent(new MapId(1));
		Font font = mock(Font.class);

		assertEquals(71, component.getWidth(font));
		assertEquals(71, component.getHeight(font));
	}
}
