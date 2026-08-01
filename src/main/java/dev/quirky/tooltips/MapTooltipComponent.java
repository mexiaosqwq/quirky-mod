package dev.quirky.tooltips;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapId;

public record MapTooltipComponent(MapId mapId) implements TooltipComponent {
}
