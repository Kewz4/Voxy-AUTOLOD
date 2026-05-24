package com.example.voxychunkycompat.client;

import com.example.voxychunkycompat.VoxyServerLODMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class LodHudOverlay implements IGuiOverlay {

    public static final ResourceLocation ID =
            new ResourceLocation(VoxyServerLODMod.MOD_ID, "lod_hud");
    public static final LodHudOverlay INSTANCE = new LodHudOverlay();

    // Layout constants
    private static final int X = 4;
    private static final int Y = 4;
    private static final int BAR_W = 160;
    private static final int BAR_H = 11; // tall enough to show inline % (font.lineHeight = 9)

    // Colors (ARGB)
    private static final int BG     = 0x99000000;
    private static final int TRACK  = 0xFF555555;
    private static final int FILL   = 0xFF44CC55;
    private static final int TEXT   = 0xFFFFFFFF;

    @Override
    public void render(ForgeGui gui, GuiGraphics gfx,
                       float partialTick, int screenW, int screenH) {

        if (!LodHudState.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug) return; // hide while F3 is open

        int processed = LodHudState.processed;
        int total     = LodHudState.total;
        float fraction = total > 0 ? Math.min(1f, (float) processed / total) : 0f;

        String etaStr = formatEta(LodHudState.etaSeconds());
        String labelLine = String.format("LOD  %,d / %,d  •  ETA %s", processed, total, etaStr);

        int textW  = mc.font.width(labelLine);
        int boxW   = Math.max(BAR_W, textW) + 8;
        int boxH   = BAR_H + mc.font.lineHeight + 10;

        int bx = X, by = Y;

        // Background panel
        gfx.fill(bx, by, bx + boxW, by + boxH, BG);

        // Progress track
        int trackX = bx + 4, trackY = by + 4;
        int trackW  = boxW - 8;
        gfx.fill(trackX, trackY, trackX + trackW, trackY + BAR_H, TRACK);

        // Filled portion
        int filled = (int) (trackW * fraction);
        if (filled > 0) {
            gfx.fill(trackX, trackY, trackX + filled, trackY + BAR_H, FILL);
        }

        // Percentage tick inside bar
        String pct = (int)(fraction * 100) + "%";
        int pctX = trackX + (trackW - mc.font.width(pct)) / 2;
        int pctY = trackY - 1 + (BAR_H - mc.font.lineHeight) / 2;
        if (BAR_H >= mc.font.lineHeight) {
            gfx.drawString(mc.font, pct, pctX, pctY, TEXT, false);
        }

        // Label below bar
        int labelX = bx + (boxW - mc.font.width(labelLine)) / 2;
        int labelY = trackY + BAR_H + 3;
        gfx.drawString(mc.font, labelLine, labelX, labelY, TEXT, false);
    }

    private static String formatEta(long seconds) {
        if (seconds < 0) return "...";
        if (seconds == 0) return "almost done";
        if (seconds >= 3600) return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60)   return String.format("%dm %ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }
}
