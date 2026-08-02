package dev.quirky;

import dev.quirky.equip_swap.EquipSwapServer;
import dev.quirky.harvest.HarvestHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuirkyMod implements ModInitializer {
	public static final String MOD_ID = "quirky";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		HarvestHandler.init();
		EquipSwapServer.init();
		LOGGER.info("Quirky loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
