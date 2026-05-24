package com.example.voxychunkycompat;

import com.example.voxychunkycompat.network.LodChunkMessage;
import com.example.voxychunkycompat.network.VoxyNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;

public class VoxyIntegration {

    public static void tryIngest(ServerLevel level, LevelChunk chunk) {
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("voxy")) {
            boolean result = VoxyCompat.tryAutoIngestChunk(chunk);
            VoxyServerLODMod.LOGGER.debug("[VoxyAutoLOD] Ingested chunk ({}, {}) → {}",
                    chunk.getPos().x, chunk.getPos().z, result);
        }
    }

    private static LodChunkMessage buildMessage(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        ResourceLocation dimension = level.dimension().location();
        ArrayList<LodChunkMessage.SectionEntry> entries = new ArrayList<>();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) continue;

            int sectionY = level.getSectionYFromSectionIndex(i);

            FriendlyByteBuf blockBuf = new FriendlyByteBuf(Unpooled.buffer(12288));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        blockBuf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x, y, z)));

            byte[] blockData = new byte[blockBuf.writerIndex()];
            blockBuf.getBytes(0, blockData);
            blockBuf.release();

            SectionPos sp = SectionPos.of(pos, sectionY);
            DataLayer skyLayer = level.getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(sp);
            DataLayer blkLayer = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(sp);

            byte[] skyData = skyLayer != null ? skyLayer.getData().clone() : new byte[2048];
            byte[] blkData = blkLayer != null ? blkLayer.getData().clone() : new byte[2048];

            entries.add(new LodChunkMessage.SectionEntry(sectionY, blockData, skyData, blkData));
        }

        return new LodChunkMessage(dimension, pos.x, pos.z, entries);
    }

    public static void sendToWatchers(ServerLevel level, LevelChunk chunk) {
        LodChunkMessage msg = buildMessage(level, chunk);
        if (!msg.sections().isEmpty()) {
            VoxyNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
        }
    }

    public static void sendToPlayer(ServerLevel level, LevelChunk chunk, ServerPlayer player) {
        LodChunkMessage msg = buildMessage(level, chunk);
        if (!msg.sections().isEmpty()) {
            VoxyNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        }
    }
}
