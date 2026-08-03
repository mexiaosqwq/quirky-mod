package dev.quirky;

import dev.quirky.config.QuirkyConfig;
import dev.quirky.config.QuirkyConfigHolder;
import dev.quirky.config.QuirkyReloadCommand;
import dev.quirky.deathcam.DeathCamServer;
import dev.quirky.equip_swap.EquipSwapServer;
import dev.quirky.harvest.HarvestHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuirkyMod implements ModInitializer {
	public static final String MOD_ID = "quirky";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// AutoConfig 在双端进程都会执行；服务端进程读写服务端 config 目录，客户端进程读写客户端 config 目录
		AutoConfig.register(QuirkyConfig.class, JanksonConfigSerializer::new);
		QuirkyConfigHolder.set(AutoConfig.getConfigHolder(QuirkyConfig.class).getConfig());
		ModBlocks.register();
		ModBlockEntityTypes.register();
		ModParticles.register();
		ModItems.register();
		ModEntities.register();
		HarvestHandler.init();
		EquipSwapServer.init();
		DeathCamServer.init();
		QuirkyReloadCommand.init();
		LOGGER.info("Quirky loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
