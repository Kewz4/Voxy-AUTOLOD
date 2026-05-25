package me.cortex.voxy.addon.compat;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("voxy_compat_addon")
public class VoxyCompatAddonForgeMod {
    public static final String MOD_ID = "voxy_compat_addon";
    static final Logger LOGGER = LoggerFactory.getLogger("Voxy Compatibility Addon");

    public VoxyCompatAddonForgeMod() {
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AddonRegistrar::register);
    }
}
