package com.example.voxychunkycompat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChunkEventHandler {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!VoxyConfig.lodEnabled) return;
        if (event.getLevel().isClientSide()) return;
        ChunkAccess ca = event.getChunk();
        if (!(ca instanceof LevelChunk levelChunk)) return;
        VoxyIntegration.tryIngest((ServerLevel) event.getLevel(), levelChunk);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!VoxyConfig.lodEnabled) return;
        ServerLevel level = event.getLevel();
        if (!level.getServer().isDedicatedServer()) return;
        ChunkPos pos = event.getPos();
        ServerPlayer player = event.getPlayer();
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        VoxyIntegration.sendToPlayer(level, chunk, player);
    }
}
