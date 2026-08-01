package dev.quirky.client.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class ClientMapTooltipComponent implements ClientTooltipComponent {
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final int TOTAL_SIZE = 71;
	private final MapId mapId;
	private final MapRenderState renderState = new MapRenderState();

	public ClientMapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	@Override
	public int getWidth(Font font) {
		return TOTAL_SIZE;
	}

	@Override
	public int getHeight(Font font) {
		return TOTAL_SIZE;
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, -7, -7, 0.0F, 0.0F, 142, 142, 64, 64, 64, 64);
		graphics.map(renderState);
		graphics.pose().popMatrix();
	}
}
