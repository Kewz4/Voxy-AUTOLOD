package com.example.voxychunkycompat.network;

import com.example.voxychunkycompat.LodDeliveryQueue;
import com.example.voxychunkycompat.VoxyConfig;
import com.example.voxychunkycompat.VoxyServerLODMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class ServerLodRequestHandler {

    public static void handle(LodRequestMessage msg, ServerPlayer player) {
        if (player == null) return;
        if (!VoxyConfig.lodEnabled || !VoxyConfig.lodSendOnJoin) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int cx = player.chunkPosition().x;
        int cz = player.chunkPosition().z;
        int radius = Math.min(Math.max(1, msg.radius()), VoxyConfig.LOD_RADIUS_MAX.get());

        VoxyServerLODMod.LOGGER.info(
                "[VoxyAutoLOD] Enqueueing LOD delivery for {} at chunk ({},{}) radius={}",
                player.getName().getString(), cx, cz, radius);

        LodDeliveryQueue.removePlayer(player.getUUID());
        LodDeliveryQueue.enqueue(level, player, cx, cz, radius);
    }
}
