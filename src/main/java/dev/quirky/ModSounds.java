package dev.quirky;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	/**
	 * 自定义环境音：定义音量 1.0（不受原版音效定义音量折减影响）、衰减距离 64 格。
	 * 实际声音文件引用原版 amethyst chime（见 assets/quirky/sounds.json）。
	 */
	public static final SoundEvent TOTEM_CHIME = SoundEvent.createVariableRangeEvent(QuirkyMod.id("totem_chime"));

	private ModSounds() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.SOUND_EVENT, QuirkyMod.id("totem_chime"), TOTEM_CHIME);
	}
}
