package dev.quirky.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.seedpouch.SeedPouchPlanter;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanEntry;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 播种袋：右键耕地（或沙/灵魂沙等）批量播种。纯交互物品，无 mixin。
 * 客户端只做只读判定（预测挥臂），世界修改与背包消耗由服务端权威执行。
 */
public class SeedPouchItem extends Item {
	public SeedPouchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		QuirkyConfig config = QuirkyConfigHolder.get();
		if (!config.seedPouchEnabled) {
			return InteractionResult.PASS;
		}
		// 潜行右键 = 精准模式（只种点击格）；否则按配置半径扫描
		int radius = context.isSecondaryUseActive() ? 0 : config.seedPouchRadius;
		// 背包主栏 + 快捷栏，不含盔甲槽（Inventory.getContainerSize() 会含盔甲）
		List<ItemStack> inventory = new ArrayList<>();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			inventory.add(player.getInventory().getItem(i));
		}
		boolean creative = player.hasInfiniteMaterials();
		PlanResult result = SeedPouchPlanter.plan(
			level, SeedPouchPlanter.scan(level, context.getClickedPos(), radius), inventory, creative
		);
		if (result.isEmpty()) {
			// 无种子/无可种位置：不播放声音、不挥臂（原版 use 失败的手感）
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			// 客户端只做预测：返回 SUCCESS 触发挥臂，真正种植由服务端完成
			return InteractionResult.SUCCESS;
		}
		ServerLevel serverLevel = (ServerLevel) level;
		for (PlanEntry entry : result.entries()) {
			BlockPos cropPos = entry.pos().above();
			serverLevel.setBlock(cropPos, entry.cropState(), 3);
			serverLevel.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				cropPos.getX() + 0.5, cropPos.getY() + 0.25, cropPos.getZ() + 0.5,
				2, 0.25, 0.15, 0.25, 0.0
			);
		}
		if (!creative) {
			for (PlanEntry entry : result.entries()) {
				player.getInventory().getItem(entry.inventorySlot()).shrink(1);
			}
		}
		int count = result.entries().size();
		boolean allNetherWart = result.entries().stream().allMatch(e -> e.cropState().is(Blocks.NETHER_WART));
		// 参照 HarvestFx：地狱疣用 NETHER_WART_PLANTED，其余用 CROP_PLANTED；
		// 音高随播种数量微调（种得多音高略低，手感更扎实）
		serverLevel.playSound(
			null, context.getClickedPos(),
			allNetherWart ? SoundEvents.NETHER_WART_PLANTED : SoundEvents.CROP_PLANTED,
			SoundSource.BLOCKS, 1.0F, 1.0F - Math.min(count * 0.02F, 0.2F)
		);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(
		ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
	) {
		builder.accept(Component.translatable("tooltip.quirky.seed_pouch").withStyle(ChatFormatting.GRAY));
	}
}
