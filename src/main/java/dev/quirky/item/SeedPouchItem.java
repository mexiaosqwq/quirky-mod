package dev.quirky.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.seedpouch.SeedFilter;
import dev.quirky.seedpouch.SeedPouchPlanter;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanEntry;
import dev.quirky.seedpouch.SeedPouchPlanter.PlanResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import dev.quirky.tooltips.TooltipShiftState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 播种袋 v2：收纳袋式种子容器 + 批量播种。
 *
 * <p>存储用原版 {@link DataComponents#BUNDLE_CONTENTS}（BundleContents），自带
 * tooltip 网格 / 装满度条 / selected。袋子只装真种子（{@link SeedFilter} 白名单），
 * 右键耕地从袋内取种种下。修掉 v1 误把灯笼当种子的 bug。
 *
 * <ul>
 *   <li>右键耕地 → 从袋内按列表顺序找能存活的种子批量播种（canSurvive 泛化）；</li>
 *   <li>光标拿种子左键点袋子 → 放入（过白名单，受重量上限）；</li>
 *   <li>光标拿袋子左键点种子堆 → 收纳（过白名单）；</li>
 *   <li>光标拿袋子右键点格子 → 取出（空格放下/同种补充/异种跳过）；</li>
 *   <li>光标空手右键点袋子 → 取出一组到光标（原版 removeOne）。</li>
 * </ul>
 */
public class SeedPouchItem extends BundleItem {
	public SeedPouchItem(Properties properties) {
		super(properties);
	}

	/** 26.2 原版允许 bundle 套娃（canFitInsideContainerItems 默认 true，BundleItem 不覆写）；
	 * 播种袋作为专用种子容器显式禁止进入其他容器物品（收纳袋/潜影盒），与潜影盒不进潜影盒同理。 */
	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/** 右键空气时不触发原版 Bundle 的 startUsingItem+dropContent（连续丢物品）机制；
	 * 种地走 useOn，容器交互走 override，use 在此静默 PASS。 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		QuirkyConfig config = QuirkyConfigHolder.get();
		ItemStack pouch = context.getItemInHand();
		BundleContents contents = pouch.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		// 袋内 items 拷贝（plan 只读，消耗在服务端用 consumeOne 重建）
		List<ItemStack> pouchItems = contents.itemCopyStream().toList();
		int radius = context.isSecondaryUseActive() ? 0 : config.seedPouchRadius;
		boolean creative = player.hasInfiniteMaterials();
		PlanResult result = SeedPouchPlanter.plan(
			level, SeedPouchPlanter.scan(level, context.getClickedPos(), radius), pouchItems, creative
		);
		if (result.isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel serverLevel = (ServerLevel) level;
		// 记录每格要消耗的袋内 index，服务端一次性用 Mutable 重建（Mutable 内部存 ItemStack，toImmutable 转 ItemStackTemplate）
		java.util.List<Integer> toConsume = new java.util.ArrayList<>();
		for (PlanEntry entry : result.entries()) {
			BlockPos cropPos = entry.pos().above();
			serverLevel.setBlock(cropPos, entry.cropState(), 3);
			serverLevel.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				cropPos.getX() + 0.5, cropPos.getY() + 0.25, cropPos.getZ() + 0.5,
				2, 0.25, 0.15, 0.25, 0.0
			);
			if (!creative) {
				toConsume.add(entry.pouchIndex());
			}
		}
		if (!creative && !toConsume.isEmpty()) {
			// 按计划纯逻辑算出消耗后列表，用 Mutable 重建 BundleContents
			List<ItemStack> updatedItems = pouchItems;
			for (int idx : toConsume) {
				updatedItems = SeedPouchPlanter.consumeOne(updatedItems, idx);
			}
			BundleContents.Mutable mutable = new BundleContents.Mutable(BundleContents.EMPTY);
			mutable.clearItems();
			for (ItemStack s : updatedItems) {
				mutable.tryInsert(s);
			}
			pouch.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
		}
		int count = result.entries().size();
		boolean allNetherWart = result.entries().stream().allMatch(e -> e.cropState().is(Blocks.NETHER_WART));
		serverLevel.playSound(
			null, context.getClickedPos(),
			allNetherWart ? SoundEvents.NETHER_WART_PLANTED : SoundEvents.CROP_PLANTED,
			SoundSource.BLOCKS, 1.0F, 1.0F - Math.min(count * 0.02F, 0.2F)
		);
		return InteractionResult.SUCCESS;
	}

	/** 光标拿种子点袋子：放入（左键，过白名单 + 重量上限）。 */
	@Override
	public boolean overrideOtherStackedOnMe(
		ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem
	) {
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!SeedFilter.isSeed(other)) {
				return false; // 非种子拒绝放入（修灯笼 bug 关键）
			}
			BundleContents initial = self.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
			BundleContents.Mutable mutable = new BundleContents.Mutable(initial);
			int inserted = mutable.tryInsert(other);
			if (inserted > 0) {
				self.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
				player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
			}
			return inserted > 0;
		}
		// 右键 + 空手：原版 removeOne 取一组到光标
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			BundleContents initial = self.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
			BundleContents.Mutable mutable = new BundleContents.Mutable(initial);
			ItemStack removed = mutable.removeOne();
			if (removed != null) {
				self.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
				carriedItem.set(removed);
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
				return true;
			}
		}
		return false;
	}

	/** 光标拿袋子点格子：左键=收纳该格种子，右键=取出/补充。 */
	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
		BundleContents initial = self.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		ItemStack other = slot.getItem();
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty() && SeedFilter.isSeed(other)) {
			// 左键点种子堆：收纳（tryTransfer 原版语义）
			BundleContents.Mutable mutable = new BundleContents.Mutable(initial);
			int transferred = mutable.tryTransfer(slot, player);
			if (transferred > 0) {
				self.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
				player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
			}
			return transferred > 0;
		}
		if (clickAction == ClickAction.SECONDARY) {
			// 右键点格子：取出袋内第一项一组（空格放下/同种补充/异种 safeInsert 拒绝则退回袋子）
			BundleContents.Mutable mutable = new BundleContents.Mutable(initial);
			ItemStack removed = mutable.removeOne();
			if (removed == null) {
				return false;
			}
			ItemStack remainder = slot.safeInsert(removed);
			if (!remainder.isEmpty()) {
				mutable.tryInsert(remainder);
			} else {
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
			}
			self.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
			return true;
		}
		return false;
	}

	@Override
	public void appendHoverText(
		ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
	) {
		// 高级 tooltip 模式：未按 Shift 时由 TooltipShiftGateMixin 隐藏并追加提示行
		if (TooltipShiftState.isSuppressing()) {
			return;
		}
		builder.accept(Component.translatable("tooltip.quirky.seed_pouch").withStyle(ChatFormatting.GRAY));
		// 与箭袋一致：空袋标"空"
		BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		if (contents.isEmpty()) {
			builder.accept(Component.translatable("tooltip.quirky.n").withStyle(ChatFormatting.GRAY));
		}
	}
}
