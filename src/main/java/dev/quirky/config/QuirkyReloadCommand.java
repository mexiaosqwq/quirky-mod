package dev.quirky.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
						try {
							holder.load();
							// AutoConfig 的 load() 用新反序列化的实例替换内部引用，必须重新注入静态 holder
							QuirkyConfigHolder.set(holder.getConfig());
							ctx.getSource().sendSuccess(
								() -> Component.literal("Quirky config reloaded"), true);
							return 1;
						} catch (Exception e) {
							ctx.getSource().sendFailure(
								Component.literal("Quirky config reload failed, keeping old values")
									.withStyle(ChatFormatting.RED));
							return 0;
						}
					}))));
	}
}
