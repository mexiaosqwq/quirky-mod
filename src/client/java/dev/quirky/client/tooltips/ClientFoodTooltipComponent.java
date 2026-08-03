package dev.quirky.client.tooltips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.quirky.tooltips.FoodTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import org.jspecify.annotations.Nullable;

/**
 * 食物 tooltip 的客户端绘制组件：基础「鸡腿 + 饱和度」行 + 条件食用信息行。
 * 度量与属性行统一（{@link TooltipRowMetrics}）：图标 9x9、16px 行高、垂直居中、间距 2/4。
 *
 * 条件行仅在有信息时出现：可满饥食用、非默认食用时长、状态效果（含概率）、
 * 移除/清除效果、随机传送。默认时长与空效果段不打印，声音/粒子细节省略。
 */
public class ClientFoodTooltipComponent implements ClientTooltipComponent {
	private static final Identifier DRUMSTICK_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier SATURATION_SPRITE = Identifier.withDefaultNamespace("mob_effect/saturation");
	private static final int ICON_SIZE = 9;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	/** 有益效果绿色（贴近原版属性正向色）。 */
	private static final int POSITIVE_COLOR = 0xFF55FF55;
	/** 有害效果红色。 */
	private static final int NEGATIVE_COLOR = 0xFFFF5555;

	private final FoodTooltipComponent component;

	public ClientFoodTooltipComponent(FoodTooltipComponent component) {
		this.component = component;
	}

	/** 一行条件信息：可选图标（效果 sprite），文本与颜色。 */
	public record DetailLine(@Nullable Identifier icon, Component text, int color) {
	}

	/**
	 * 收集条件食用信息行。tickRate 用于时长格式化：单元测试传 20，真机由
	 * {@link #tickRate()} 取当前 level 的 tickrate。
	 */
	static List<DetailLine> detailLines(FoodTooltipComponent component, float tickRate) {
		List<DetailLine> lines = new ArrayList<>();
		FoodProperties food = component.food();
		if (food.canAlwaysEat()) {
			lines.add(new DetailLine(null, Component.translatable("tooltip.quirky.food.always_edible"), TEXT_COLOR));
		}
		Consumable consumable = component.consumable();
		if (consumable != null) {
			if (consumable.consumeSeconds() != Consumable.DEFAULT_CONSUME_SECONDS) {
				String seconds = String.format(Locale.ROOT, "%.1f", consumable.consumeSeconds());
				String key = consumable.animation() == ItemUseAnimation.DRINK
					? "tooltip.quirky.food.drink_time"
					: "tooltip.quirky.food.eat_time";
				lines.add(new DetailLine(null, Component.translatable(key, seconds), TEXT_COLOR));
			}
			for (ConsumeEffect effect : consumable.onConsumeEffects()) {
				addEffectLines(effect, lines, tickRate);
			}
		}
		return lines;
	}

	private static void addEffectLines(ConsumeEffect effect, List<DetailLine> lines, float tickRate) {
		if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
			for (MobEffectInstance instance : apply.effects()) {
				Holder<MobEffect> holder = instance.getEffect();
				Component name = holder.value().getDisplayName();
				if (instance.getAmplifier() > 0) {
					name = Component.translatable(
						"potion.withAmplifier",
						name,
						Component.translatable("potion.potency." + instance.getAmplifier())
					);
				}
				Component duration = MobEffectUtil.formatDuration(instance, 1.0F, tickRate);
				Component text = Component.translatable("tooltip.quirky.food.effect", name, duration);
				if (apply.probability() < 1.0F) {
					int percent = Math.round(apply.probability() * 100.0F);
					text = Component.translatable("tooltip.quirky.food.effect_probability", text, percent);
				}
				lines.add(new DetailLine(
					Hud.getMobEffectSprite(holder),
					text,
					holder.value().isBeneficial() ? POSITIVE_COLOR : NEGATIVE_COLOR
				));
			}
		} else if (effect instanceof RemoveStatusEffectsConsumeEffect remove) {
			for (Holder<MobEffect> holder : remove.effects()) {
				lines.add(new DetailLine(
					null,
					Component.translatable("tooltip.quirky.food.remove_effect", holder.value().getDisplayName()),
					TEXT_COLOR
				));
			}
		} else if (effect instanceof ClearAllStatusEffectsConsumeEffect) {
			lines.add(new DetailLine(null, Component.translatable("tooltip.quirky.food.clear_all_effects"), TEXT_COLOR));
		} else if (effect instanceof TeleportRandomlyConsumeEffect) {
			lines.add(new DetailLine(null, Component.translatable("tooltip.quirky.food.random_teleport"), TEXT_COLOR));
		}
		// PlaySoundConsumeEffect 及未知效果：视觉摘要省略，避免暴露实现细节
	}

	@Override
	public int getWidth(Font font) {
		int width = baseRowWidth(font);
		for (DetailLine line : detailLines(component, tickRate())) {
			width = Math.max(width, lineWidth(font, line));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return TooltipRowMetrics.LINE_HEIGHT * (1 + detailLines(component, tickRate()).size());
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		drawBaseRow(font, x, y, graphics);
		List<DetailLine> lines = detailLines(component, tickRate());
		for (int i = 0; i < lines.size(); i++) {
			DetailLine line = lines.get(i);
			int rowY = y + (i + 1) * TooltipRowMetrics.LINE_HEIGHT;
			int yIcon = TooltipRowMetrics.iconY(rowY, ICON_SIZE);
			int yText = TooltipRowMetrics.textY(rowY);
			int cursor = x;
			if (line.icon() != null) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, line.icon(), cursor, yIcon, ICON_SIZE, ICON_SIZE);
				cursor += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
			}
			graphics.text(font, line.text(), cursor, yText, line.color());
		}
	}

	private int baseRowWidth(Font font) {
		return ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(nutritionText())
			+ TooltipRowMetrics.CELL_GAP + ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP + font.width(saturationText());
	}

	private static int lineWidth(Font font, DetailLine line) {
		int width = font.width(line.text());
		if (line.icon() != null) {
			width += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
		}
		return width;
	}

	private void drawBaseRow(Font font, int x, int y, GuiGraphicsExtractor graphics) {
		int yIcon = TooltipRowMetrics.iconY(y, ICON_SIZE);
		int yText = TooltipRowMetrics.textY(y);
		int cursor = x;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DRUMSTICK_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
		graphics.text(font, nutritionText(), cursor, yText, TEXT_COLOR);
		cursor += font.width(nutritionText()) + TooltipRowMetrics.CELL_GAP;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SATURATION_SPRITE, cursor, yIcon, ICON_SIZE, ICON_SIZE);
		cursor += ICON_SIZE + TooltipRowMetrics.ICON_TEXT_GAP;
		graphics.text(font, saturationText(), cursor, yText, TEXT_COLOR);
	}

	private String nutritionText() {
		return "+" + component.food().nutrition();
	}

	private String saturationText() {
		float saturation = component.food().saturation();
		// 整数值不带小数（+6），否则保留一位小数（+9.6）
		if (saturation == (float) (int) saturation) {
			return "+" + (int) saturation;
		}
		return "+" + String.format(Locale.ROOT, "%.1f", saturation);
	}

	/** 真机取当前 level 的 tickrate；无 level（单元测试）时按 20。 */
	private static float tickRate() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return 20.0F;
		}
		return minecraft.level.tickRateManager().tickrate();
	}
}
