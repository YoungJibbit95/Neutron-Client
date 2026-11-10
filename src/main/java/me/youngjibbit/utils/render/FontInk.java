package me.youngjibbit.utils.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

/**
 * One glyph-atlas shader for sharp text and its optional halo.
 * No framebuffer, deferred draw, per-character program switch or additional blur pass.
 */
public final class FontInk {
    private static ShaderUtil shader;
    private static boolean directActive;
    private static boolean plainActive;
    private FontInk() {}

    public static boolean enabled() {
        ClientSettings c=ClientSettings.current();
        return c!=null && "On".equals(c.textGlow.getValue()) && c.glow.getValue()
                && c.textGlowStrength.getValue()>0 && c.glowIntensity.getValue()>0;
    }

    public static void themed(Theme requested,Runnable glyphs) {
        if(!OpenGlHelper.shadersSupported){glyphs.run();return;}
        boolean previous=directActive;
        try(RenderState state=new RenderState()){
            directActive=true;
            bind(Theme.resolve(requested),true);
            glyphs.run();
        }finally{directActive=previous;}
    }

    /** Semantic detail text keeps its supplied ink, including when global text glow follows the palette. */
    public static void plain(Runnable glyphs) {
        if (!OpenGlHelper.shadersSupported) { glyphs.run(); return; }
        boolean previous = plainActive;
        try (RenderState state = new RenderState()) {
            plainActive = true;
            GL20.glUseProgram(0);
            // A fixed-function glyph must sample only its atlas. Inherited
            // lightmap/effect texture units otherwise modulate the supplied ink.
            for (int slot = 1; slot <= 4; slot++) {
                int unit = slot == 4 ? 16 : slot;
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            }
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            org.lwjgl.opengl.GL11.glTexEnvi(org.lwjgl.opengl.GL11.GL_TEXTURE_ENV,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_ENV_MODE, org.lwjgl.opengl.GL11.GL_MODULATE);
            glyphs.run();
        } finally { plainActive = previous; }
    }

    private static void bind(Theme theme,boolean gradient){
        if(shader==null)shader=new ShaderUtil("Flauxy/Shaders/fontInk.frag","Flauxy/Shaders/fontInk.vsh");
        shader.init();
        shader.setUniformi("glyph",0);
        shader.setUniformi("themed",gradient?1:0);
        shader.setUniformf("haloStrength",0);
        UiEffects.upload(shader,theme);
        color("first",theme.getPrimaryGradientRGB());
        color("second",theme.getSecondaryGradientRGB());
    }

    /** Called outside glBegin; unknown caller programs remain untouched. */
    public static int begin(boolean eligible,boolean shadow) {
        if(!OpenGlHelper.shadersSupported || plainActive)return -1;
        boolean glow=eligible && !shadow && !TextEffects.isCapturing() && enabled();
        if(!glow&&!directActive)return -1;
        int previous=org.lwjgl.opengl.GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean own=shader!=null && previous==shader.getProgramId();
        if(previous==0 && glow){bind(Theme.resolve(Theme.getDefaultTheme()),false);own=true;}
        if(own){
            ClientSettings c=ClientSettings.current();
            shader.setUniformf("haloStrength",glow?Math.min(1,c.textGlowStrength.getValue().floatValue()*c.glowIntensity.getValue().floatValue()):0);
            shader.setUniformf("haloRadius",glow?c.textGlowRadius.getValue().floatValue()*2:0);
            shader.setUniformi("haloTheme",glow&&"Theme".equals(c.textGlowColor.getValue())?1:0);
        }
        return own?previous:-1;
    }

    public static void end(int previous){
        if(previous<0)return;
        shader.setUniformf("haloStrength",0);
        GL20.glUseProgram(previous);
    }

    private static void color(String name,int rgba){
        shader.setUniformf(name,(rgba>>16&255)/255f,(rgba>>8&255)/255f,(rgba&255)/255f,(rgba>>>24)/255f);
    }

    public static void releaseResources(){
        if(shader!=null){shader.close();shader=null;}
        directActive=false;
        plainActive=false;
    }
}
