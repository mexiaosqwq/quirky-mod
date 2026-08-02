package dev.quirky.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class QuirkyReloadCommand {
	private QuirkyReloadCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("quirky")
				.then(literal("reload")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> {
						ConfigHolder<QuirkyConfig> holder = AutoConfig.getConfigHolder(QuirkyConfig.class);
						QuirkyConfig previous = QuirkyConfigHolder.get();
						boolean ok = holder.load();
						if (ok) {
							// AutoConfig 的 load() 用新反序列化的实例替换内部引用，必须重新注入静态 holder
							QuirkyConfigHolder.set(holder.getConfig());
							ctx.getSource().sendSuccess(
								() -> Component.literal("Quirky config reloaded"), true);
							return 1;
						}
						// load() 失败时内部重置为默认实例——恢复旧实例，保证行为不变
						QuirkyConfigHolder.set(previous);
						ctx.getSource().sendFailure(
							Component.literal("Quirky config reload failed, keeping previous values")
								.withStyle(ChatFormatting.RED));
						return 0;
					}))));
	}
}
