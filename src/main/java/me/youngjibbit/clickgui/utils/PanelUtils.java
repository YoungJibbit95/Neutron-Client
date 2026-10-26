package me.youngjibbit.clickgui.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class PanelUtils
{

    public PanelUtils()
    {
    }



    public int getColorFromTheme(Theme theme, float offset)
    {
        Theme resolved = Theme.resolve(theme);
        return resolved.getGradientRGB(offset);
    }



    public void drawClientName(String text, int x, int y, Theme theme)
    {
        me.youngjibbit.utils.render.TextEffects.direct(Theme.resolve(theme),()->{
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;

        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = -1;


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28").getWidth(tmpStr) -1.5;
        }
            });
    }

    public void dropdownModuleString(String text, int x, int y, Theme theme)
    {
        me.youngjibbit.utils.render.TextEffects.direct(Theme.resolve(theme),()->{
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;

        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = -1;


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 15").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 15").getWidth(tmpStr) -1.5;
        }
            });
    }

    public void icon1(String text, int x, int y, Theme theme)
    {
        me.youngjibbit.utils.render.TextEffects.direct(Theme.resolve(theme),()->{
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;

        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = -1;


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("icon 23").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("icon 23").getWidth(tmpStr) * 0.78;
        }
            });
    }

    public void categoryString(String text, int x, int y, Theme theme) {
        me.youngjibbit.utils.render.TextEffects.direct(Theme.resolve(theme),()->{

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;

        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = -1;


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").getWidth(tmpStr) -1.5;
        }
            });
    }

    public void icon2(String text, int x, int y, Theme theme)
    {
        me.youngjibbit.utils.render.TextEffects.direct(Theme.resolve(theme),()->{
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;

        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = -1;


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("icon1 23").getWidth(tmpStr) * 0.78;
        }
            });
    }
}
