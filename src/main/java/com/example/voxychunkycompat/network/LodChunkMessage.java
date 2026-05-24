package com.example.voxychunkycompat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LodChunkMessage {

    private final ResourceLocation dimension;
    private final int chunkX;
    private final int chunkZ;
    private final List<SectionEntry> sections;

    public LodChunkMessage(ResourceLocation dimension, int chunkX, int chunkZ, List<SectionEntry> sections) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = sections;
    }

    public ResourceLocation dimension() { return dimension; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public List<SectionEntry> sections() { return sections; }

    public static void encode(LodChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeVarInt(msg.chunkX);
        buf.writeVarInt(msg.chunkZ);
        buf.writeVarInt(msg.sections.size());
        for (SectionEntry s : msg.sections) {
            buf.writeVarInt(s.sectionY());
            buf.writeByteArray(s.blockData());
            buf.writeByteArray(s.skyLightData());
            buf.writeByteArray(s.blockLightData());
        }
    }

    public static LodChunkMessage decode(FriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        int cx = buf.readVarInt();
        int cz = buf.readVarInt();
        int count = buf.readVarInt();
        List<SectionEntry> sections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int sy = buf.readVarInt();
            byte[] block = buf.readByteArray();
            byte[] sky = buf.readByteArray();
            byte[] blk = buf.readByteArray();
            sections.add(new SectionEntry(sy, block, sky, blk));
        }
        return new LodChunkMessage(dim, cx, cz, sections);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientLodHandler.handle(this));
        ctx.setPacketHandled(true);
    }

    public record SectionEntry(int sectionY, byte[] blockData, byte[] skyLightData, byte[] blockLightData) {}
}
