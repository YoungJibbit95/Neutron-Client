package me.youngjibbit.utils;

import java.awt.Color;

public class ColorUtils {

    public static Color gradientEffect(long offset, float fade) {
        // Calculate the sine wave for a smooth transition between cyan and magenta
        float wave = (float) ((Math.sin((System.currentTimeMillis() + offset) / 1000.0) + 1) / 2);

        // Define cyan and magenta colors
        Color cyan = new Color(0, 255, 255);
        Color magenta = new Color(255, 0, 255);

        // Interpolate between cyan and magenta based on the wave value
        int red = (int) ((magenta.getRed() - cyan.getRed()) * wave + cyan.getRed());
        int green = (int) ((magenta.getGreen() - cyan.getGreen()) * wave + cyan.getGreen());
        int blue = (int) ((magenta.getBlue() - cyan.getBlue()) * wave + cyan.getBlue());

        return new Color(red, green, blue);
    }

    public static int mixColors(int color1, int color2, float ratio) {
        int r = (int) ((getRed(color2) - getRed(color1)) * ratio + getRed(color1));
        int g = (int) ((getGreen(color2) - getGreen(color1)) * ratio + getGreen(color1));
        int b = (int) ((getBlue(color2) - getBlue(color1)) * ratio + getBlue(color1));
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    public static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int getBlue(int color) {
        return color & 0xFF;
    }

    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }
}
