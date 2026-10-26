package me.youngjibbit.gui;

import lombok.SneakyThrows;
import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Eierlecken;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.ShaderUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public class MenuShader implements Eierlecken
{
    private static final ShaderUtil[] backgrounds=new ShaderUtil[6];
    private static Framebuffer fb;
    private static ShaderUtil shader(int mode){if(backgrounds[mode-1]==null)backgrounds[mode-1]=new ShaderUtil("Flauxy/Shaders/menuBackground"+mode+".frag","Tenacity/shaders/vertex.vsh");return backgrounds[mode-1];}
    private static int texture(){if(fb==null)try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){fb=new Framebuffer(1,1,false);}return fb.framebufferTexture;}
    public static void releaseResources(){for(int i=0;i<backgrounds.length;i++)if(backgrounds[i]!=null){backgrounds[i].close();backgrounds[i]=null;}if(fb!=null)try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){fb.deleteFramebuffer();fb=null;}}

    // Customizable blend factors (adjust as needed)
    private static final float blendFactor1 = 0.5f;
    private static final float blendFactor2 = 0.7f;
    private static final float blendFactor3 = 1.2f;

    public static Theme applyTheme() {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    private static int[] getStaticOutlineGradientColors(Theme theme)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        return new int[]{resolvedTheme.getPrimaryGradientRGB(), resolvedTheme.getSecondaryGradientRGB()};
    }

    @SneakyThrows
    public MenuShader() throws IOException
    {

    }

    public static void mm1(long initTime)
    {
        ScaledResolution sr = new ScaledResolution(mc);
        ShaderUtil mainmenu=shader(1);
        mainmenu.init();
        mainmenu.setUniformf("TIME", me.youngjibbit.utils.render.UiEffects.time());
        mainmenu.setUniformf("RESOLUTION", (float) ((double) sr.getScaledWidth() * sr.getScaleFactor()), (float) ((double) sr.getScaledHeight() * sr.getScaleFactor()));
        ShaderUtil.drawFixedQuads();
        mainmenu.unload();

    }

    public static void mm2(long initTime)
    {
        ScaledResolution sr = new ScaledResolution(mc);
        ShaderUtil mainmenu2=shader(2);
        mainmenu2.init();

        // Set u_time uniform (time elapsed since shader initialization)
        mainmenu2.setUniformf("u_time", me.youngjibbit.utils.render.UiEffects.time());

        // Set u_resolution uniform (screen resolution adjusted for scale factor)
        mainmenu2.setUniformf("u_resolution",
                (float) ((double) sr.getScaledWidth() * sr.getScaleFactor()),
                (float) ((double) sr.getScaledHeight() * sr.getScaleFactor())
        );

        // Set u_mouse uniform (normalized mouse position)
        mainmenu2.setUniformf("u_mouse",
                Mouse.getEventX() / (float) sr.getScaledWidth(),
                Mouse.getEventY() / (float) sr.getScaledHeight(),
                0.0f, 0.0f
        );
        // The texture ID from stencilFramebuffer
        mainmenu2.setUniformi("u_textures[16]", 0);  // Bind to texture unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);  // Activate texture unit 0
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture());

        // Draw the shader quad
        ShaderUtil.drawFixedQuads();

        // Unload the mainmenu2 shader
        mainmenu2.unload();
    }

    public static void mm3(long initTime)
    {
        ScaledResolution sr = new ScaledResolution(mc);
        ShaderUtil mainmenu3=shader(3);
        mainmenu3.init();
        mainmenu3.setUniformf("iResolution", (float) ((double) sr.getScaledWidth() * sr.getScaleFactor()), (float) ((double) sr.getScaledHeight() * sr.getScaleFactor()));
        mainmenu3.setUniformi("iChannel0", 0);  // Bind to texture unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);  // Activate texture unit 0
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture());
        ShaderUtil.drawFixedQuads();
        mainmenu3.unload();
    }

    // Function to get RGB components from an integer
    private static float[] getRGBComponents(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    private static float calculateBlendFactor(float[] color) {
        // A simple example: the blend factor could be based on the color's brightness (average of RGB values)
        return (color[0] + color[1] + color[2]) / 3.0f;
    }

    // Another variation: blend factor between two colors
    private static float calculateBlendFactor(float[] color1, float[] color2) {
        // A simple example: the blend factor could be the average brightness of two colors
        return ((color1[0] + color1[1] + color1[2]) + (color2[0] + color2[1] + color2[2])) / 6.0f;
    }

    public static void mm4(long initTime) {
        ScaledResolution sr = new ScaledResolution(mc);

        ShaderUtil mainmenu4=shader(4);
        mainmenu4.init();
        mainmenu4.setUniformf("iResolution", sr.getScaledWidth() * sr.getScaleFactor(), sr.getScaledHeight() * sr.getScaleFactor());
        mainmenu4.setUniformf("iMouse",
                Mouse.getEventX() / (float) sr.getScaledWidth(),
                Mouse.getEventY() / (float) sr.getScaledHeight(),
                0.0f, 0.0f);
        mainmenu4.setUniformf("iTime", me.youngjibbit.utils.render.UiEffects.time());
        ShaderUtil.drawFixedQuads();
        mainmenu4.unload();
    }

    public static void mm5(long initTime)
    {
        ScaledResolution sr = new ScaledResolution(mc);
        Theme currentTheme = applyTheme();
        int[] outlineColors = getStaticOutlineGradientColors(currentTheme);
        float[] bgColor = getRGBComponents(outlineColors[0]);
        float[] accentColor = getRGBComponents(outlineColors[1]);

        ShaderUtil mainmenu5=shader(5);
        mainmenu5.init();
        mainmenu5.setUniformf("iResolution", 550, 500);
        mainmenu5.setUniformf("iTime", me.youngjibbit.utils.render.UiEffects.time());
        mainmenu5.setUniformf("u_bgColor", bgColor[0], bgColor[1], bgColor[2]);
        mainmenu5.setUniformf("u_accentColor", accentColor[0], accentColor[1], accentColor[2]);
        ShaderUtil.drawFixedQuads();
        mainmenu5.unload();
    }

    public static void mm6(long initTime) {
        ScaledResolution sr = new ScaledResolution(mc);
        int textureId0 = texture();
        int textureId3 = texture();

        // Initialize the shader program
        ShaderUtil mainmenu6=shader(6);
        mainmenu6.init();

        // Set resolution dynamically
        mainmenu6.setUniformf("iResolution", (float) ((double) sr.getScaledWidth()), (float) ((double) sr.getScaledHeight()));

        // Set time
        mainmenu6.setUniformf("iTime", me.youngjibbit.utils.render.UiEffects.time());

        // Set iChannel0 (first texture)
        mainmenu6.setUniformi("iChannel0", 0);  // Bind to texture unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);  // Activate texture unit 0
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId0);

        mainmenu6.setUniformi("iChannel3", 3);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId3);

        // Draw the shader
        ShaderUtil.drawFixedQuads();

        // Clean up the shader program
        mainmenu6.unload();
    }
}
