package me.youngjibbit.utils.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.awt.*;

public class RenderUtil
{
    public static void drawRect(double x, double y, double width, double height, int color)
    {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    public static void drawString(FontRenderer font, String text, float x, float y, int color)
    {
        font.drawString(text, x,  y, color);
    }

    public static void drawCenteredString(FontRenderer font, String text, float x, float y, int color)
    {
        font.drawString(text, (x - (float) font.getStringWidth(text) / 2), y, color);
    }
}
