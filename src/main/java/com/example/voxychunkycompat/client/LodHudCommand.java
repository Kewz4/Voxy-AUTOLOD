package com.example.voxychunkycompat.client;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class LodHudCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("voxyhud")
                .executes(ctx -> {
                    LodHudState.hudEnabled = !LodHudState.hudEnabled;
                    sendFeedback(ctx.getSource(), LodHudState.hudEnabled);
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> {
                    LodHudState.hudEnabled = true;
                    sendFeedback(ctx.getSource(), true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    LodHudState.hudEnabled = false;
                    sendFeedback(ctx.getSource(), false);
                    return 1;
                }))
        );
    }

    private static void sendFeedback(net.minecraft.commands.CommandSourceStack source, boolean enabled) {
        source.sendSuccess(
            () -> Component.literal("[VoxyAutoLOD] LOD HUD " + (enabled ? "§aenabled" : "§cdisabled")),
            false
        );
    }
}
