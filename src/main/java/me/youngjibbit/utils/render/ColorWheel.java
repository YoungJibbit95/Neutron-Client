package me.youngjibbit.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Legacy color entry points. Color conversions and interpolation are owned by ColorUtil. */
@Deprecated
public class ColorWheel {
    public static Color tripleColor(int rgbValue) { return ColorUtil.tripleColor(rgbValue); }
    public static Color tripleColor(int rgbValue, float alpha) { return ColorUtil.tripleColor(rgbValue, alpha); }
    public static Color blendColors(float[] fractions, Color[] colors, float progress) {
        int startPoint = 0;
        int[] indices = new int[2];

        while (startPoint < fractions.length && fractions[startPoint] <= progress) startPoint++;
        if (startPoint >= fractions.length) {
            startPoint = fractions.length - 1;
        }

        indices[0] = startPoint - 1;
        indices[1] = startPoint;

        float[] range = new float[]{fractions[indices[0]], fractions[indices[1]]};
        Color[] colorRange = new Color[]{colors[indices[0]], colors[indices[1]]};

        float max = range[1] - range[0];
        float value = progress - range[0];
        float weight = value / max;

        float r = 1.0f - weight;
        float ir = 1.0f - r;

        float[] rgb1 = new float[3];
        float[] rgb2 = new float[3];

        colorRange[0].getColorComponents(rgb1);
        colorRange[1].getColorComponents(rgb2);

        float red = rgb1[0] * r + rgb2[0] * ir;
        float green = rgb1[1] * r + rgb2[1] * ir;
        float blue = rgb1[2] * r + rgb2[2] * ir;

        if (red < 0.0f) {
            red = 0.0f;
        } else if (red > 255.0f) {
            red = 255.0f;
        }

        if (green < 0.0f) {
            green = 0.0f;
        } else if (green > 255.0f) {
            green = 255.0f;
        }

        if (blue < 0.0f) {
            blue = 0.0f;
        } else if (blue > 255.0f) {
            blue = 255.0f;
        }

        return new Color(red, green, blue);
    }
    public static Color[] getAnalogousColor(Color color) { return ColorUtil.getAnalogousColor(color); }
    public static Color getRandomColor() { return ColorUtil.getRandomColor(); }
    public static Color hslToRGB(float[] hsl) { return ColorUtil.hslToRGB(hsl); }
    public static float hueToRGB(float p, float q, float t) { return ColorUtil.hueToRGB(p, q, t); }
    public static float[] rgbToHSL(Color rgb) { return ColorUtil.rgbToHSL(rgb); }
    public static Color imitateTransparency(Color backgroundColor, Color accentColor, float percentage) { return ColorUtil.imitateTransparency(backgroundColor, accentColor, percentage); }
    public static int applyOpacity(int color, float opacity) { return ColorUtil.applyOpacity(color, opacity); }
    public static Color applyOpacity(Color color, float opacity) { return ColorUtil.applyOpacity(color, opacity); }
    public static Color darker(Color color, float FACTOR) { return ColorUtil.darker(color, FACTOR); }
    public static Color brighter(Color color, float FACTOR) { return ColorUtil.brighter(color, FACTOR); }
    public static Color averageColor(BufferedImage bi, int width, int height, int pixelStep) { return ColorUtil.averageColor(bi, width, height, pixelStep); }
    public static Color rainbow(int speed, int index, float saturation, float brightness, float opacity) { return ColorUtil.rainbow(speed, index, saturation, brightness, opacity); }
    public static Color interpolateColorsBackAndForth(int speed, int index, Color start, Color end, boolean trueColor) { return ColorUtil.interpolateColorsBackAndForth(speed, index, start, end, trueColor); }
    public static int interpolateColor(Color color1, Color color2, float amount) { return ColorUtil.interpolateColor(color1, color2, amount); }
    public static int interpolateColor(int color1, int color2, float amount) { return ColorUtil.interpolateColor(color1, color2, amount); }
    public static Color interpolateColorC(Color color1, Color color2, float amount) { return ColorUtil.interpolateColorC(color1, color2, amount); }
    public static Color interpolateColorHue(Color color1, Color color2, float amount) { return ColorUtil.interpolateColorHue(color1, color2, amount); }
    public static Color fade(int speed, int index, Color color, float alpha) { return ColorUtil.fade(speed, index, color, alpha); }
    public static int[] createColorArray(int color) { return ColorUtil.createColorArray(color); }
    public static int getOppositeColor(int color) { return ColorUtil.getOppositeColor(color); }
    public static Color getOppositeColor(Color color) { return ColorUtil.getOppositeColor(color); }
}
