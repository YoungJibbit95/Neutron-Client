package me.youngjibbit.utils.render;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;
/** Whole-string glyph masks. Owns three reusable targets; no fonts, glyph textures or per-letter passes. */
public final class TextEffects implements AutoCloseable {
    private static boolean capturing;
    public static boolean isCapturing(){return capturing;}
    private static final java.util.Set<TextEffects> owners=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<TextEffects,Boolean>());
    private static ShaderUtil effectShader,directShader;
    public TextEffects(){owners.add(this);}
    public static void releaseResources(){for(TextEffects owner:new java.util.ArrayList<>(owners))owner.close();if(effectShader!=null){effectShader.close();effectShader=null;}if(directShader!=null){directShader.close();directShader=null;}}
    private final Framebuffer[] buffers=new Framebuffer[3];
    private int width,height;
    private float localPhase,lastLocalTime;
    public void draw(TTFFontRenderer font,String text,float x,float y,int first,int second,int style,boolean animate,float speed,boolean glow,float strength,float radius,boolean shadow){
        if(text==null||text.isEmpty())return;
        float[] bounds=ScreenRect.project(x,y,font.getWidth(text),font.getHeight(text));
        draw(bounds,first,second,style,animate,speed,glow,strength,radius,false,false,()->{if(shadow)font.drawStringWithShadow(text,x,y,-1);else font.drawString(text,x,y,-1);});
    }
    public void drawLocal(TTFFontRenderer font,String text,float x,float y,int first,int second,int style,boolean animate,float speed,boolean glow,float strength,float radius,boolean localGradient,boolean localRadius,boolean shadow){
        if(text==null||text.isEmpty())return;
        draw(ScreenRect.project(x,y,font.getWidth(text),font.getHeight(text)),first,second,style,animate,speed,glow,strength,radius,localGradient,localRadius,()->{if(shadow)font.drawStringWithShadow(text,x,y,-1);else font.drawString(text,x,y,-1);});
    }
    public void themed(me.youngjibbit.utils.Theme theme,Runnable glyphs){direct(theme,glyphs);}
    /** Sharp theme text shades the existing glyph draws directly: no framebuffer or per-row effect pass. */
    public static void direct(me.youngjibbit.utils.Theme theme,Runnable glyphs){
        theme=UiEffects.theme(theme);
        if(!net.minecraft.client.renderer.OpenGlHelper.shadersSupported){glyphs.run();return;}
        try(RenderState state=new RenderState()){
            texture(0,0);if(directShader==null)directShader=new ShaderUtil("Flauxy/Shaders/textDirect.frag");ShaderUtil s=directShader;s.init();s.setUniformi("glyph",0);UiEffects.upload(s,theme);color(s,"first",theme.getPrimaryGradientRGB());color(s,"second",theme.getSecondaryGradientRGB());glyphs.run();s.unload();
        }
    }
    private void draw(float[] bounds,int first,int second,int style,boolean animate,float speed,boolean glow,float strength,float radius,boolean localGradient,boolean localRadius,Runnable glyphs){
        if(!net.minecraft.client.renderer.OpenGlHelper.shadersSupported||!net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled()){glyphs.run();return;}
        glow=glow&&UiEffects.glowEnabled()&&strength>0;strength=UiEffects.glowAlpha(strength);radius=localRadius?radius:UiEffects.glowRadius(radius);
        float time=UiEffects.time();if(animate&&UiEffects.motion())localPhase=(localPhase+Math.max(0,time-lastLocalTime)*speed)%1;lastLocalTime=time;
        if(UiEffects.options().configured&&!UiEffects.options().gradients)style=0;
        Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);
        try(RenderState state=new RenderState()){
            ensure(mc.displayWidth,mc.displayHeight,glow);target(buffers[0]);
            // Retain the caller's model/projection for glyph capture, suppress only its clip and depth.
            capturing=true;
            try{glyphs.run();}finally{capturing=false;}
            if(RenderDiagnostics.enabled)RenderDiagnostics.maskPasses++;
            state.screenSpace();
            if(glow){int r=Math.max(1,Math.min(120,Math.round(radius*sr.getScaleFactor())));GaussianBlur.renderPass(buffers[1],buffers[0].framebufferTexture,1,0,r,true,1);GaussianBlur.renderPass(buffers[2],buffers[1].framebufferTexture,0,1,r,true,1);}
            state.bindTarget();state.restoreClip();GlStateManager.enableBlend();GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            texture(0,buffers[0].framebufferTexture);texture(1,glow?buffers[2].framebufferTexture:buffers[0].framebufferTexture);
            if(effectShader==null)effectShader=new ShaderUtil("Flauxy/Shaders/textEffect.frag");ShaderUtil s=effectShader;s.init();s.setUniformi("glyphs",0);s.setUniformi("halo",1);s.setUniformf("resolution",width,height);
            s.setUniformf("origin",bounds[0]*sr.getScaleFactor(),bounds[1]*sr.getScaleFactor());s.setUniformf("extent",bounds[2]*sr.getScaleFactor(),bounds[3]*sr.getScaleFactor());
            UiEffects.upload(s,me.youngjibbit.utils.Theme.getDefaultTheme());s.setUniformi("useShared",style!=0&&!localGradient?1:0);color(s,"first",first);color(s,"second",second);s.setUniformi("style",style);s.setUniformf("phase",animate?localPhase:-1);s.setUniformf("strength",glow?strength:0);
            ShaderUtil.drawQuads();s.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.composites++;
        }
    }
    private void ensure(int w,int h,boolean glow){
        if(width!=w||height!=h){close();width=w;height=h;}
        for(int i=0;i<(glow?3:1);i++)if(buffers[i]==null){buffers[i]=new Framebuffer(w,h,false);buffers[i].setFramebufferFilter(GL_LINEAR);texture(0,buffers[i].framebufferTexture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);}
    }
    private static void target(Framebuffer f){f.bindFramebuffer(true);GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();glDisable(GL_STENCIL_TEST);glDisable(GL_SCISSOR_TEST);GlStateManager.colorMask(true,true,true,true);GlStateManager.clearColor(0,0,0,0);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);}
    private static void texture(int unit,int id){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GlStateManager.bindTexture(id);glBindTexture(GL_TEXTURE_2D,id);}
    private static void color(ShaderUtil s,String name,int rgb){s.setUniformf(name,(rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,(rgb>>>24)/255f);}
    @Override public void close(){try(RenderState state=new RenderState()){for(int i=0;i<3;i++)if(buffers[i]!=null){buffers[i].deleteFramebuffer();buffers[i]=null;}}}
}
