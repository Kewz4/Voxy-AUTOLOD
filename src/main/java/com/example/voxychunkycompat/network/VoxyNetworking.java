package com.example.voxychunkycompat.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class VoxyNetworking {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("voxy_server_lod", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                LodChunkMessage.class,
                LodChunkMessage::encode,
                LodChunkMessage::decode,
                LodChunkMessage::handle);
        CHANNEL.registerMessage(id++,
                LodRequestMessage.class,
                LodRequestMessage::encode,
                LodRequestMessage::decode,
                LodRequestMessage::handle);
        CHANNEL.registerMessage(id++,
                LodProgressMessage.class,
                LodProgressMessage::encode,
                LodProgressMessage::decode,
                LodProgressMessage::handle);
    }
}
