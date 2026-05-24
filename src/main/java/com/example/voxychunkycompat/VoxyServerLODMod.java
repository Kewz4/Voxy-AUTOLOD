package com.example.voxychunkycompat;

import com.example.voxychunkycompat.network.VoxyNetworking;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("voxy_server_lod")
public class VoxyServerLODMod {

    public static final String MOD_ID = "voxy_server_lod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VoxyServerLODMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VoxyConfig.SPEC);
        VoxyNetworking.register();

        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getModId().equals(MOD_ID)) VoxyConfig.initMirrors();
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getModId().equals(MOD_ID)) {
                VoxyConfig.initMirrors();
                if (FMLEnvironment.dist.isClient()) ClientSetup.onConfigReload();
            }
        });

        if (ModList.get().isLoaded("chunky")) {
            LOGGER.info("[VoxyAutoLOD] Chunky detected — registering chunk ingestion listener");
            MinecraftForge.EVENT_BUS.register(ChunkEventHandler.class);
            MinecraftForge.EVENT_BUS.register(LodDeliveryQueue.class);
        } else {
            LOGGER.info("[VoxyAutoLOD] Chunky not present — mod is dormant");
        }

        MinecraftForge.EVENT_BUS.addListener(LodCommand::register);

        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init(modBus);
        }
    }
}
