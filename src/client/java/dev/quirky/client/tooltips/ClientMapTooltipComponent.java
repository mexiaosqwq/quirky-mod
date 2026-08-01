package dev.quirky.client.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class ClientMapTooltipComponent implements ClientTooltipComponent {
	private static final int SIZE = 64;
	private final MapId mapId;
	private final MapRenderState renderState = new MapRenderState();

	public ClientMapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	@Override
	public int getWidth(Font font) {
		return SIZE;
	}

	@Override
	public int getHeight(Font font) {
		return SIZE;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		MapItemSavedData data = mc.level.getMapData(mapId);
		if (data == null) {
			return;
		}
		mc.getMapRenderer().extractRenderState(mapId, data, renderState);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.5F, 0.5F);
		graphics.map(renderState);
		graphics.pose().popMatrix();
	}
}
