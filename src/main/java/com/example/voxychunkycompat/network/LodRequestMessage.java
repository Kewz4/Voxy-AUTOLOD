package com.example.voxychunkycompat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LodRequestMessage {

    private final int radius;

    public LodRequestMessage(int radius) {
        this.radius = radius;
    }

    public int radius() { return radius; }

    public static void encode(LodRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.radius);
    }

    public static LodRequestMessage decode(FriendlyByteBuf buf) {
        return new LodRequestMessage(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> ServerLodRequestHandler.handle(this, sender));
        ctx.setPacketHandled(true);
    }
}
