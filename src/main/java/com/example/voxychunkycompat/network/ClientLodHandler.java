package com.example.voxychunkycompat.network;

import com.example.voxychunkycompat.VoxyCompat;
import com.example.voxychunkycompat.VoxyConfig;
import com.example.voxychunkycompat.VoxyServerLODMod;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraftforge.fml.ModList;

public class ClientLodHandler {

    public static void sendLodRequest() {
        int radius = VoxyConfig.LOD_RADIUS.get();
        VoxyNetworking.CHANNEL.sendToServer(new LodRequestMessage(radius));
        VoxyServerLODMod.LOGGER.info("[VoxyAutoLOD] Requested extended LOD delivery, radius={} chunks", radius);
    }

    public static void handle(LodChunkMessage msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !ModList.get().isLoaded("voxy")) return;
        if (!VoxyCompat.isAvailable()) return;

        var biomeReg = level.registryAccess().registryOrThrow(Registries.BIOME);
        ChunkPos chunkPos = new ChunkPos(msg.chunkX(), msg.chunkZ());
        int sectionCount = level.getSectionsCount();
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) sections[i] = new LevelChunkSection(biomeReg);

        for (LodChunkMessage.SectionEntry se : msg.sections()) {
            int idx = level.getSectionIndexFromSectionY(se.sectionY());
            if (idx < 0 || idx >= sectionCount) continue;

            LevelChunkSection section = new LevelChunkSection(biomeReg);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(se.blockData()));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt());
                        if (state != null) section.setBlockState(x, y, z, state, false);
                    }
                }
            }
            sections[idx] = section;
        }

        LevelLightEngine lightEngine = level.getLightEngine();
        lightEngine.setLightEnabled(chunkPos, true);

        for (LodChunkMessage.SectionEntry se : msg.sections()) {
            SectionPos sp = SectionPos.of(chunkPos, se.sectionY());
            DataLayer sky = se.skyLightData().length == 2048
                    ? new DataLayer(se.skyLightData()) : new DataLayer(new byte[2048]);
            DataLayer blk = se.blockLightData().length == 2048
                    ? new DataLayer(se.blockLightData()) : new DataLayer(new byte[2048]);
            lightEngine.queueSectionData(LightLayer.SKY, sp, sky);
            lightEngine.queueSectionData(LightLayer.BLOCK, sp, blk);
            lightEngine.updateSectionStatus(sp, false);
        }
        lightEngine.runLightUpdates();

        LevelChunk chunk = new LevelChunk(level, chunkPos, UpgradeData.EMPTY,
                new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, sections, null, null);

        boolean ingested = VoxyCompat.tryIngestChunk(level, chunk);
        if (ingested) {
            VoxyServerLODMod.LOGGER.debug("[VoxyAutoLOD] tryIngestChunk succeeded at ({}, {})",
                    msg.chunkX(), msg.chunkZ());
            return;
        }

        VoxyServerLODMod.LOGGER.debug(
                "[VoxyAutoLOD] tryIngestChunk returned false at ({}, {}), falling back to rawIngest",
                msg.chunkX(), msg.chunkZ());

        for (LodChunkMessage.SectionEntry se : msg.sections()) {
            int idx = level.getSectionIndexFromSectionY(se.sectionY());
            if (idx < 0 || idx >= sectionCount) continue;
            DataLayer sky = se.skyLightData().length == 2048
                    ? new DataLayer(se.skyLightData()) : new DataLayer(new byte[2048]);
            DataLayer blk = se.blockLightData().length == 2048
                    ? new DataLayer(se.blockLightData()) : new DataLayer(new byte[2048]);
            VoxyCompat.rawIngest(level, sections[idx], msg.chunkX(), se.sectionY(), msg.chunkZ(), sky, blk);
        }
    }
}
