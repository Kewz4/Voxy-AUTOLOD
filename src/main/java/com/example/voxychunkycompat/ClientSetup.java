package com.example.voxychunkycompat;

import com.example.voxychunkycompat.network.ClientLodHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientSetup {

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientLogin);
    }

    static void onConfigReload() {
        ClientLodHandler.sendLodRequest();
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientLodHandler.sendLodRequest();
    }
}
