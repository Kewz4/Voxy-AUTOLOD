package com.example.voxychunkycompat;

import com.example.voxychunkycompat.client.LodHudCommand;
import com.example.voxychunkycompat.client.LodHudOverlay;
import com.example.voxychunkycompat.network.ClientLodHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public class ClientSetup {

    static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientLogin);
        MinecraftForge.EVENT_BUS.register(LodHudCommand.class);
        modBus.addListener(ClientSetup::registerOverlays);
    }

    static void onConfigReload() {
        ClientLodHandler.sendLodRequest();
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientLodHandler.sendLodRequest();
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(LodHudOverlay.ID.toString(), LodHudOverlay.INSTANCE);
    }
}
