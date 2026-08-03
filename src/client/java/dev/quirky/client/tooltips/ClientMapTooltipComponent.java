package dev.quirky.client.tooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * 地图 tooltip 的客户端绘制组件：71x71 实时预览（羊皮纸边框保留）。
 * 有地图数据时在下方追加一行「比例 1:N（已锁定）」元信息，宽度/高度随之扩展，
 * 确保绘制始终落在报告的边界内；无数据时保持基础 71x71。
 */
public class ClientMapTooltipComponent implements ClientTooltipComponent {
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final int TOTAL_SIZE = 71;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private final MapId mapId;
	private final MapRenderState renderState = new MapRenderState();

	public ClientMapTooltipComponent(MapId mapId) {
		this.mapId = mapId;
	}

	/** 当前客户端地图数据；无客户端或数据缺失返回 null（保持基础尺寸）。 */
	@Nullable
	private MapItemSavedData mapData() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return null;
		}
		return mc.level.getMapData(mapId);
	}

	/** 比例 1:2^scale，锁定地图追加「已锁定」标记。 */
	static Component metadataText(byte scale, boolean locked) {
		Component text = Component.translatable("tooltip.quirky.map.scale", 1 << scale);
		if (locked) {
			text = Component.translatable("tooltip.quirky.map.scale_locked", text);
		}
		return text;
	}

	@Override
	public int getWidth(Font font) {
		MapItemSavedData data = mapData();
		if (data == null) {
			return TOTAL_SIZE;
		}
		return Math.max(TOTAL_SIZE, font.width(metadataText(data.scale, data.locked)));
	}

	@Override
	public int getHeight(Font font) {
		return mapData() == null ? TOTAL_SIZE : TOTAL_SIZE + TooltipRowMetrics.LINE_HEIGHT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		MapItemSavedData data = mapData();
		if (data == null) {
			return;
		}
		mc().getMapRenderer().extractRenderState(mapId, data, renderState);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.5F, 0.5F);
		graphics.pose().translate(7, 7);
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, -7, -7, 0.0F, 0.0F, 142, 142, 64, 64, 64, 64);
		graphics.map(renderState);
		graphics.pose().popMatrix();
		// 元信息脚注行：位于地图下方一行，始终在报告的宽度/高度内
		Component metadata = metadataText(data.scale, data.locked);
		int textY = y + TOTAL_SIZE + TooltipRowMetrics.textY(0);
		graphics.text(font, metadata, x, textY, TEXT_COLOR);
	}

	private Minecraft mc() {
		return Minecraft.getInstance();
	}
}
