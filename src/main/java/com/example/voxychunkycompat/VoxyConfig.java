package com.example.voxychunkycompat;

import net.minecraftforge.common.ForgeConfigSpec;

public class VoxyConfig {

    public static volatile boolean lodEnabled = true;
    public static volatile String generationMode = "disk_only";
    public static volatile boolean lodSendOnJoin = true;

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LOD_RADIUS;
    public static final ForgeConfigSpec.IntValue LOD_RADIUS_MAX;
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<String> LOD_GENERATION_MODE;
    public static final ForgeConfigSpec.BooleanValue LOD_SEND_ON_JOIN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Chunk radius the client requests on world join. The server caps this at lod_radius_max.");
        LOD_RADIUS = b.defineInRange("lod_radius", 256, 1, 4096);

        b.comment("Maximum radius the server will honour. Prevents a client from overloading the server.");
        LOD_RADIUS_MAX = b.defineInRange("lod_radius_max", 512, 1, 4096);

        b.comment("Chunks processed per server tick during LOD delivery. Lower = less TPS impact.");
        CHUNKS_PER_TICK = b.defineInRange("lod_chunks_per_tick", 4, 1, 64);

        b.comment(
            "How the delivery queue handles chunks in the requested radius:",
            "  disk_only (default) — delivers chunks already on disk; skips unpregenned chunks.",
            "  generate            — delivers disk chunks first, then generates any missing chunks."
        );
        LOD_GENERATION_MODE = b.define("lod_generation_mode", "disk_only");

        b.comment("If true (default), the client requests extended LOD delivery on world join.");
        LOD_SEND_ON_JOIN = b.define("lod_send_on_join", true);

        SPEC = b.build();
    }

    public static void initMirrors() {
        generationMode = LOD_GENERATION_MODE.get();
        lodSendOnJoin = LOD_SEND_ON_JOIN.get();
    }
}
