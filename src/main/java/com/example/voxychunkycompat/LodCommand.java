package com.example.voxychunkycompat;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

public class LodCommand {

    private static final List<String> VALID_MODES = List.of("disk_only", "generate");

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("voxylod").requires(src -> src.hasPermission(2))
                .then(Commands.literal("enable").executes(ctx -> {
                    VoxyConfig.lodEnabled = true;
                    ctx.getSource().sendSuccess(() -> Component.literal("[VoxyAutoLOD] LOD delivery enabled"), true);
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    VoxyConfig.lodEnabled = false;
                    ctx.getSource().sendSuccess(() -> Component.literal("[VoxyAutoLOD] LOD delivery disabled"), true);
                    return 1;
                }))
                .then(Commands.literal("mode")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(VALID_MODES, b))
                        .executes(ctx -> {
                            String val = StringArgumentType.getString(ctx, "value");
                            if (!VALID_MODES.contains(val)) {
                                ctx.getSource().sendFailure(Component.literal(
                                    "[VoxyAutoLOD] Invalid mode '" + val + "'. Valid: " + String.join(", ", VALID_MODES)));
                                return 0;
                            }
                            VoxyConfig.generationMode = val;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "[VoxyAutoLOD] Generation mode → " + val +
                                (val.equals("generate") ? " (may cause TPS lag)" : " (disk only)")), true);
                            return 1;
                        })))
                .then(Commands.literal("sendonjoin")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "enabled");
                            VoxyConfig.lodSendOnJoin = val;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "[VoxyAutoLOD] Send-on-join → " + val), true);
                            return 1;
                        })))
                .then(Commands.literal("status").executes(ctx -> {
                    boolean enabled = VoxyConfig.lodEnabled;
                    String mode = VoxyConfig.generationMode;
                    boolean soj = VoxyConfig.lodSendOnJoin;
                    int radius = VoxyConfig.LOD_RADIUS.get();
                    int radiusMax = VoxyConfig.LOD_RADIUS_MAX.get();
                    int cpt = VoxyConfig.CHUNKS_PER_TICK.get();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "[VoxyAutoLOD] Status:\n" +
                        "  enabled        = " + enabled + "\n" +
                        "  mode           = " + mode + "\n" +
                        "  send_on_join   = " + soj + "\n" +
                        "  lod_radius     = " + radius + " chunks\n" +
                        "  lod_radius_max = " + radiusMax + " chunks\n" +
                        "  chunks_per_tick= " + cpt), false);
                    return 1;
                }))
        );
    }
}
