package me.youngjibbit.utils.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Render-thread snapshot of Minecraft's immutable GUI dimensions. Input changes are checked on
 * every access, including a scale/language change made between two draws in the same frame.
 */
public final class GuiResolution {
    private static Minecraft owner;
    private static ScaledResolution cached;
    private static int width, height, configuredScale;
    private static boolean unicode;

    private GuiResolution() { }

    public static ScaledResolution current() {
        return current(Minecraft.getMinecraft());
    }

    static ScaledResolution current(Minecraft client) {
        int displayWidth = client.displayWidth, displayHeight = client.displayHeight;
        int scale = client.gameSettings.guiScale;
        boolean useUnicode = client.isUnicode();
        if (cached == null || owner != client || width != displayWidth || height != displayHeight
                || configuredScale != scale || unicode != useUnicode) {
            cached = new ScaledResolution(client);
            owner = client;
            width = displayWidth;
            height = displayHeight;
            configuredScale = scale;
            unicode = useUnicode;
        }
        return cached;
    }
}
