package com.example.voxychunkycompat.network;

import com.example.voxychunkycompat.client.LodHudState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent server → client once per second during LOD delivery.
 * Setting total = 0 clears the HUD.
 */
public class LodProgressMessage {

    public final int processed;
    public final int total;

    public LodProgressMessage(int processed, int total) {
        this.processed = processed;
        this.total = total;
    }

    public static void encode(LodProgressMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.processed);
        buf.writeVarInt(msg.total);
    }

    public static LodProgressMessage decode(FriendlyByteBuf buf) {
        return new LodProgressMessage(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (total <= 0 || processed >= total) {
                LodHudState.clear();
            } else {
                LodHudState.update(processed, total);
            }
        });
        ctx.setPacketHandled(true);
    }
}
