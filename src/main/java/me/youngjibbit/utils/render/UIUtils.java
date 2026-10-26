package me.youngjibbit.utils.render;

import me.youngjibbit.utils.ColorUtil;
import me.youngjibbit.utils.GLUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;

public class UIUtils
{

    public static ShaderUtil roundedShader = new ShaderUtil("roundedRect");
    public static ShaderUtil roundedOutlineShader = new ShaderUtil("roundRectOutline");
    private static ShaderUtil roundedTexturedShader = new ShaderUtil("roundRectTexture");
    private static ShaderUtil roundedGradientShader = new ShaderUtil("roundedRectGradient");


    private static void ensureShaders(){if(roundedShader==null)roundedShader=new ShaderUtil("roundedRect");if(roundedOutlineShader==null)roundedOutlineShader=new ShaderUtil("roundRectOutline");if(roundedTexturedShader==null)roundedTexturedShader=new ShaderUtil("roundRectTexture");if(roundedGradientShader==null)roundedGradientShader=new ShaderUtil("roundedRectGradient");}
    public static void releaseResources(){if(roundedShader!=null){roundedShader.close();roundedShader=null;}if(roundedOutlineShader!=null){roundedOutlineShader.close();roundedOutlineShader=null;}if(roundedTexturedShader!=null){roundedTexturedShader.close();roundedTexturedShader=null;}if(roundedGradientShader!=null){roundedGradientShader.close();roundedGradientShader=null;}}
    public static void drawRound(float x, float y, float width, float height, float radius, Color color)
    {
        drawRound(x, y, width, height, radius, false, color);
    }

    public static void drawGradientHorizontal(float x, float y, float width, float height, float radius, Color left, Color right)
    {
        drawGradientRound(x, y, width, height, radius, left, left, right, right);
    }

    public static void drawGradientVertical(float x, float y, float width, float height, float radius, Color top, Color bottom)
    {
        drawGradientRound(x, y, width, height, radius, bottom, top, bottom, top);
    }

    public static void drawGradientCornerLR(float x, float y, float width, float height, float radius, Color topLeft, Color bottomRight)
    {
        Color mixedColor = ColorUtil.interpolateColorC(topLeft, bottomRight, .5f);
        drawGradientRound(x, y, width, height, radius, mixedColor, topLeft, bottomRight, mixedColor);
    }

    public static void drawGradientCornerRL(float x, float y, float width, float height, float radius, Color bottomLeft, Color topRight)
    {
        Color mixedColor = ColorUtil.interpolateColorC(topRight, bottomLeft, .5f);
        drawGradientRound(x, y, width, height, radius, bottomLeft, mixedColor, mixedColor, topRight);
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight)
    {
        ensureShaders();
        try(RenderState state=new RenderState()){
        RenderUiUtils.setAlphaLimit(0);
        RenderUiUtils.resetColor();
        GLUtil.startBlend();
        roundedGradientShader.init();
        UiEffects.uploadMaterial(roundedGradientShader,material(width,height,radius,topLeft));
        Color gradientEnd=topLeft.equals(topRight)?bottomLeft:topRight;
        roundedGradientShader.setUniformf("uiFirst",topLeft.getRed()/255f,topLeft.getGreen()/255f,topLeft.getBlue()/255f,topLeft.getAlpha()/255f);
        roundedGradientShader.setUniformf("uiSecond",gradientEnd.getRed()/255f,gradientEnd.getGreen()/255f,gradientEnd.getBlue()/255f,gradientEnd.getAlpha()/255f);
        UiEffects.upload(roundedGradientShader,UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme()));
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader);
        //Top left
        roundedGradientShader.setUniformf("color1", topLeft.getRed() / 255f, topLeft.getGreen() / 255f, topLeft.getBlue() / 255f, topLeft.getAlpha() / 255f);
        // Bottom Left
        roundedGradientShader.setUniformf("color2", bottomLeft.getRed() / 255f, bottomLeft.getGreen() / 255f, bottomLeft.getBlue() / 255f, bottomLeft.getAlpha() / 255f);
        //Top Right
        roundedGradientShader.setUniformf("color3", topRight.getRed() / 255f, topRight.getGreen() / 255f, topRight.getBlue() / 255f, topRight.getAlpha() / 255f);
        //Bottom Right
        roundedGradientShader.setUniformf("color4", bottomRight.getRed() / 255f, bottomRight.getGreen() / 255f, bottomRight.getBlue() / 255f, bottomRight.getAlpha() / 255f);
        ShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedGradientShader.unload();
        GLUtil.endBlend();
            }
    }


    public static void drawRound(float x, float y, float width, float height, float radius, boolean blur, Color color)
    {
        ensureShaders();
        try(RenderState state=new RenderState()){
        RenderUiUtils.resetColor();
        GLUtil.startBlend();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        RenderUiUtils.setAlphaLimit(0);
        roundedShader.init();UiEffects.upload(roundedShader,UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme()));
        UiEffects.uploadMaterial(roundedShader,material(width,height,radius,color));

        setupRoundedRectUniforms(x, y, width, height, radius, roundedShader);
        roundedShader.setUniformi("blur", blur ? 1 : 0);
        roundedShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, (Blur.isCapturing()?1f:color.getAlpha() / 255f));

        ShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedShader.unload();
        GLUtil.endBlend();
            }
    }


    public static void drawRoundOutline(float x, float y, float width, float height, float radius, float outlineThickness, Color color, Color outlineColor)
    {
        drawRoundOutline(x,y,width,height,radius,outlineThickness,color,outlineColor,1);
    }
    public static void drawRoundOutline(float x,float y,float width,float height,float radius,float outlineThickness,Color color,Color outlineColor,float themeAccent){
        ensureShaders();
        try(RenderState state=new RenderState()){
        RenderUiUtils.resetColor();
        GLUtil.startBlend();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        RenderUiUtils.setAlphaLimit(0);
        roundedOutlineShader.init();UiEffects.uploadMaterial(roundedOutlineShader,material(width,height,radius,color));

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        setupRoundedRectUniforms(x, y, width, height, radius, roundedOutlineShader);
        roundedOutlineShader.setUniformf("uiAccent",Math.max(0,Math.min(1,themeAccent)));
        roundedOutlineShader.setUniformf("outlineThickness", outlineThickness * sr.getScaleFactor());
        roundedOutlineShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, (Blur.isCapturing()?1f:color.getAlpha() / 255f));
        UiEffects.upload(roundedOutlineShader,UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme()));
        java.awt.Color f=UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme()).getPrimaryGradientColor(),g=UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme()).getSecondaryGradientColor();
        roundedOutlineShader.setUniformf("uiFirst",f.getRed()/255f,f.getGreen()/255f,f.getBlue()/255f,f.getAlpha()/255f);roundedOutlineShader.setUniformf("uiSecond",g.getRed()/255f,g.getGreen()/255f,g.getBlue()/255f,g.getAlpha()/255f);
        roundedOutlineShader.setUniformf("outlineColor", outlineColor.getRed() / 255f, outlineColor.getGreen() / 255f, outlineColor.getBlue() / 255f, (UiEffects.options().border?outlineColor.getAlpha()/255f*UiEffects.options().borderOpacity:0));


        ShaderUtil.drawQuads(x - (2 + outlineThickness), y - (2 + outlineThickness), width + (4 + outlineThickness * 2), height + (4 + outlineThickness * 2));
        roundedOutlineShader.unload();
        GLUtil.endBlend();
            }
    }


    public static void drawRoundTextured(float x, float y, float width, float height, float radius, float alpha)
    {
        ensureShaders();
        try(RenderState state=new RenderState()){
        RenderUiUtils.resetColor();
        RenderUiUtils.setAlphaLimit(0);
        GLUtil.startBlend();
        roundedTexturedShader.init();
        roundedTexturedShader.setUniformi("textureIn", 0);
        setupRoundedRectUniforms(x, y, width, height, radius, roundedTexturedShader);
        roundedTexturedShader.setUniformf("alpha", alpha);
        ShaderUtil.drawQuads(x - 1, y - 1, width + 2, height + 2);
        roundedTexturedShader.unload();
        GLUtil.endBlend();
            }
    }

    private static int decorations,sceneMasks,exactGeometry;
    /** Literal color swatches, identity tiles and semantic badges keep their color while sharing rounded geometry. */
    public static void drawDecorationRound(float x,float y,float width,float height,float radius,Color color){decorations++;try{drawRound(x,y,width,height,radius,color);}finally{decorations--;}}
    public static void drawWorldMaskRound(float x,float y,float width,float height,float radius,Color color){decorations++;exactGeometry++;try{drawRound(x,y,width,height,radius,color);}finally{exactGeometry--;decorations--;}}
    public static void drawSceneRound(float x,float y,float width,float height,float radius){sceneMasks++;try{drawRound(x,y,width,height,radius,Color.WHITE);}finally{sceneMasks--;}}
    private static boolean material(float width,float height,float radius,Color fill){return sceneMasks>0||decorations==0&&width>8&&height>8&&radius<Math.min(width,height)/2&&fill.getAlpha()>64;}
    private static void setupRoundedRectUniforms(float x, float y, float width, float height, float radius, ShaderUtil roundedTexturedShader)
    {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        radius=exactGeometry==0?UiEffects.radius(radius,width,height):Math.max(0,Math.min(radius,Math.min(width,height)/2));
        float[] rect=ScreenRect.project(x,y,width,height);
        float scale=sr.getScaleFactor();
        roundedTexturedShader.setUniformf("location",rect[0]*scale,Minecraft.getMinecraft().displayHeight-(rect[1]+rect[3])*scale);
        roundedTexturedShader.setUniformf("rectSize",rect[2]*scale,rect[3]*scale);
        roundedTexturedShader.setUniformf("radius",radius*scale*Math.min(rect[2]/Math.max(0.001f,width),rect[3]/Math.max(0.001f,height)));

    }


    public static class ColorUtils
    {
        static
        {
            for (int i = 0; i < 18; i++)
            {
                //animation.add(new AnimationUtils(0.0F));
            }
        }

        private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)Ã‚Â§[0-9A-FK-OR]");
        private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)Ã‚Â§([0-9A-FK-OR])");

        public static Color getBackgroundColor(int id)
        {
            switch (id)
            {
                case 1:
                    return new Color(16, 15, 69);
                case 2:
                    return new Color(19, 19, 128);
                case 3:
                    return new Color(255, 255, 255);
                case 4:
                    return new Color(0, 0, 0);
                default:
                    return new Color(255, 0, 255);
            }
        }

        public static Color getFontColor(int id, int alpha)
        {
            Color rawColor = getRawFontColor(id);
            int speed = 12;

            /*if (id == 1)
            {
                animation.get(12).setAnimation(rawColor.getRed(), speed);
                animation.get(13).setAnimation(rawColor.getGreen(), speed);
                animation.get(14).setAnimation(rawColor.getBlue(), speed);

                return new Color((int) animation.get(12).getValue(), (int) animation.get(13).getValue(), (int) animation.get(14).getValue(), alpha);
            }

            if (id == 2)
            {
                animation.get(15).setAnimation(rawColor.getRed(), speed);
                animation.get(16).setAnimation(rawColor.getGreen(), speed);
                animation.get(17).setAnimation(rawColor.getBlue(), speed);

                return new Color((int) animation.get(15).getValue(), (int) animation.get(16).getValue(), (int) animation.get(17).getValue(), alpha);
            }*/

            return rawColor;
        }

        private static Color getRawFontColor(int id)
        {
            switch (id)
            {
                case 1:
                    return new Color(255, 255, 255);
                case 2:
                    return new Color(173, 173, 173);
                default:
                    return new Color(255, 0, 0);
            }
        }

        public static Color getFontColor(int id)
        {
            return getFontColor(id, 255);
        }

        public static void setColor(int color, double alpha)
        {
            float r = (float) (color >> 16 & 255) / 255.0F;
            float g = (float) (color >> 8 & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;
            GlStateManager.color(r, g, b, (float) alpha);
        }

        public static void setColor(int color)
        {
            setColor(color, (color >> 24 & 255) / 255.0F);
        }

        public static void resetColor()
        {
            GlStateManager.color(1, 1, 1, 1);
        }

        public static String stripColor(String input)
        {
            return COLOR_PATTERN.matcher(input).replaceAll("");
        }

        public static Color getColorFromCode(String input)
        {
            Matcher matcher = COLOR_CODE_PATTERN.matcher(input);
            if (matcher.find())
            {
                char code = matcher.group(1).charAt(0);
                switch (code)
                {
                    case '0':
                        return new Color(0, 0, 0);
                    case '1':
                        return new Color(0, 0, 170);
                    case '2':
                        return new Color(0, 170, 0);
                    case '3':
                        return new Color(0, 170, 170);
                    case '4':
                        return new Color(170, 0, 0);
                    case '5':
                        return new Color(170, 0, 170);
                    case '6':
                        return new Color(255, 170, 0);
                    case '7':
                        return new Color(170, 170, 170);
                    case '8':
                        return new Color(85, 85, 85);
                    case '9':
                        return new Color(85, 85, 255);
                    case 'a':
                        return new Color(85, 255, 85);
                    case 'b':
                        return new Color(85, 255, 255);
                    case 'c':
                        return new Color(255, 85, 85);
                    case 'd':
                        return new Color(255, 85, 255);
                    case 'e':
                        return new Color(255, 255, 85);
                    case 'f':
                        return new Color(255, 255, 255);
                    default:
                        return new Color(255, 255, 255);
                }
            }
            return new Color(255, 255, 255);
        }

        @Contract("_ -> new")
        public static @NotNull Color colorFromInt(int color)
        {
            Color c = new Color(color);
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
        }

        @Contract("_, _ -> new")
        public static @NotNull Color brighter(final @NotNull Color color, final float FACTOR)
        {
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();
            final int alpha = color.getAlpha();
            final int i = (int) (1.0 / (1.0 - FACTOR));
            if (r == 0 && g == 0 && b == 0)
            {
                return new Color(i, i, i, alpha);
            }
            if (r > 0 && r < i)
            {
                r = i;
            }
            if (g > 0 && g < i)
            {
                g = i;
            }
            if (b > 0 && b < i)
            {
                b = i;
            }
            return new Color(Math.min((int) (r / FACTOR), 255), Math.min((int) (g / FACTOR), 255), Math.min((int) (b / FACTOR), 255), alpha);
        }

        @Contract("_, _, _ -> new")
        public static @NotNull Color interpolateColorC(final @NotNull Color color1, final @NotNull Color color2, float amount)
        {
            amount = Math.min(1.0f, Math.max(0.0f, amount));
            return new Color(interpolateInt(color1.getRed(), color2.getRed(), amount), interpolateInt(color1.getGreen(), color2.getGreen(), amount), interpolateInt(color1.getBlue(), color2.getBlue(), amount), interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
        }

        public static int interpolateInt(final int oldValue, final int newValue, final double interpolationValue)
        {
            return ((Double) ((double) oldValue + ((double) newValue - (double) oldValue) * (double) (float) interpolationValue)).intValue();
        }

        @Contract("_, _, _ -> new")
        public static @NotNull Color blend(@NotNull Color color1, @NotNull Color color2, double ratio)
        {
            float r = (float) ratio;
            float ir = 1.0f - r;
            float[] rgb1 = new float[3];
            float[] rgb2 = new float[3];
            color1.getColorComponents(rgb1);
            color2.getColorComponents(rgb2);
            return new Color(rgb1[0] * r + rgb2[0] * ir, rgb1[1] * r + rgb2[1] * ir, rgb1[2] * r + rgb2[2] * ir);
        }

        @Contract("_, _ -> new")
        public static @NotNull Color blend(Color color1, Color color2)
        {
            return blend(color1, color2, 0.5);
        }

        @Contract("_, _ -> new")
        public static @NotNull Color blend(int color1, int color2)
        {
            return blend(colorFromInt(color1), colorFromInt(color2), 0.5);
        }

        public static @NotNull Color reAlpha(@NotNull Color color, float alpha)
        {
            float r = 0.003921569f * (float) color.getRed();
            float g = 0.003921569f * (float) color.getGreen();
            float b = 0.003921569f * (float) color.getBlue();
            return new Color(r, g, b, alpha);
        }

        public static float calculateGaussianValue(final float x, final float sigma)
        {
            final double output = 1.0 / Math.sqrt(6.283185307179586 * (sigma * sigma));
            return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
        }
    }
}
