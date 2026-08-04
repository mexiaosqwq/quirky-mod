package dev.quirky.item;

import java.util.function.Consumer;

import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.quiver.QuiverContents;
import dev.quirky.quiver.QuiverLogic;
import net.minecraft.ChatFormatting;
import dev.quirky.tooltips.TooltipShiftState;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * 箭袋：可染色的弹药容器，装取对齐播种袋（光标点击）+ 弓/弩射击自动抽箭。
 *
 * <ul>
 *   <li>光标拿弹药左键点箭袋 / 箭袋左键点弹药堆 → 装入（过白名单，受组上限截断，余量退回光标）；</li>
 *   <li>光标右键点箭袋 → 取出一组到光标（原版 bundle 式）；</li>
 *   <li>弓/弩射击时，背包无散装弹药则自动从箭袋抽箭（散装优先，箭袋作备用），
 *       详见 {@link dev.quirky.mixin.PlayerQuiverAmmoMixin}；</li>
 *   <li>染色走原版 DYED_COLOR 组件（炼药锅洗色自动生效）。</li>
 * </ul>
 *
 * <p>容量按"组"计（默认 4 组，每组按物品自身 maxStackSize，箭类 64 → 默认 256 支）。
 * 禁止进入其他容器物品（套娃防护）。</p>
 */
public class QuiverItem extends Item {

	public QuiverItem(Properties properties) {
		super(properties);
	}

	/** 禁止箭袋进入收纳袋/潜影盒/另一个箭袋（套娃防护，同播种袋）。 */
	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/** 世界右键无作用（装取都在物品栏光标点击；射击自动抽箭不需要手动操作）。 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	/** 光标拿弹药点箭袋：左键装入，右键取出一组到光标。 */
	@Override
	public boolean overrideOtherStackedOnMe(
		ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem
	) {
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
			if (!QuiverLogic.isAmmo(other)) {
				return false; // 非弹药拒绝
			}
			ItemContainerContents initial = self.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
			int capacity = QuirkyConfigHolder.get().quiverCapacity;
			QuiverLogic.InsertResult result = QuiverLogic.insert(initial, other.copy(), capacity, QuiverLogic::isAmmo);
			if (result.inserted() > 0) {
				self.set(QuiverContents.TYPE, result.contents());
				other.shrink(result.inserted()); // 退回余量到光标（other 是活引用）
				player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
			}
			return result.inserted() > 0;
		}
		// 右键 + 空手：取出一组到光标
		if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
			ItemContainerContents initial = self.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
			QuiverLogic.ExtractResult result = QuiverLogic.extractOne(initial);
			if (!result.extracted().isEmpty()) {
				self.set(QuiverContents.TYPE, result.contents());
				carriedItem.set(result.extracted());
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
				return true;
			}
		}
		return false;
	}

	/** 光标拿箭袋点格子：左键=收纳该格弹药，右键=取出一组到该格。 */
	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
		ItemStack other = slot.getItem();
		if (clickAction == ClickAction.PRIMARY && !other.isEmpty() && QuiverLogic.isAmmo(other)) {
			ItemContainerContents initial = self.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
			int capacity = QuirkyConfigHolder.get().quiverCapacity;
			QuiverLogic.InsertResult result = QuiverLogic.insert(initial, other.copy(), capacity, QuiverLogic::isAmmo);
			if (result.inserted() > 0) {
				self.set(QuiverContents.TYPE, result.contents());
				other.shrink(result.inserted());
				player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
			}
			return result.inserted() > 0;
		}
		if (clickAction == ClickAction.SECONDARY) {
			ItemContainerContents initial = self.getOrDefault(QuiverContents.TYPE, ItemContainerContents.EMPTY);
			QuiverLogic.ExtractResult result = QuiverLogic.extractOne(initial);
			if (!result.extracted().isEmpty()) {
				ItemStack remainder = slot.safeInsert(result.extracted());
				if (remainder.isEmpty()) {
					self.set(QuiverContents.TYPE, result.contents());
					player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(
		ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
	) {
		// 高级 tooltip 模式：未按 Shift 时由 TooltipShiftGateMixin 隐藏并追加提示行
		if (TooltipShiftState.isSuppressing()) {
			return;
		}
		builder.accept(Component.translatable("tooltip.quirky.quiver.usage").withStyle(ChatFormatting.GRAY));
	}
}
