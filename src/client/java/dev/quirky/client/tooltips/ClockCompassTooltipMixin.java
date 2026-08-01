package dev.quirky.client.tooltips;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ClockCompassTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void quirky$appendTooltip(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack stack = (ItemStack) (Object) this;
		if (player == null) {
			return;
		}
		if (stack.is(Items.CLOCK)) {
			cir.getReturnValue().add(
				Component.translatable("tooltip.quirky.clock", formatTime(player.level()))
					.withStyle(ChatFormatting.GRAY)
			);
		} else if (stack.is(Items.COMPASS)) {
			LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
			if (tracker != null && tracker.target().isPresent()) {
				GlobalPos target = tracker.target().get();
				BlockPos targetPos = target.pos();
				cir.getReturnValue().add(
					Component.translatable(
						"tooltip.quirky.lodestone",
						targetPos.getX(),
						targetPos.getZ(),
						target.dimension().identifier().toString()
					).withStyle(ChatFormatting.GRAY)
				);
			} else {
				BlockPos spawnPos = player.level().getRespawnData().globalPos().pos();
				cir.getReturnValue().add(
					Component.translatable(
						"tooltip.quirky.compass",
						player.getDirection().getSerializedName(),
						spawnPos.getX(),
						spawnPos.getZ()
					).withStyle(ChatFormatting.GRAY)
				);
			}
		}
	}

	private static String formatTime(Level level) {
		long dayTime = level.getDefaultClockTime();
		long day = dayTime / 24000L + 1L;
		int ticks = (int) (dayTime % 24000L);
		int hours = (ticks / 1000 + 6) % 24;
		int minutes = (ticks % 1000) * 60 / 1000;
		return day + " " + String.format("%02d:%02d", hours, minutes);
	}
}
