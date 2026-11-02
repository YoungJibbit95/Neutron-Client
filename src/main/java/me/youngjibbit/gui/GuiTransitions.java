package me.youngjibbit.gui;

import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import static org.lwjgl.opengl.GL11.*;

/** One GPU snapshot per navigation, no extra render target or work in steady frames. */
public final class GuiTransitions {
    private static int texture, width, height;
    private static long started;
    private static boolean active;
    private GuiTransitions(){}
    private static boolean client(GuiScreen screen){return screen!=null&&screen.getClass().getName().startsWith("me.youngjibbit.");}
    public static void beforeChange(GuiScreen from,GuiScreen to){
        if(from==to||!UiEffects.motion()||!client(from)&&!client(to)){active=false;return;}
        Minecraft mc=Minecraft.getMinecraft();
        active=false;
        if(mc==null||mc.getFramebuffer()==null||mc.displayWidth<=0||mc.displayHeight<=0)return;
        // Unit tests and early bootstrap may navigate without a current GL context.
        try{org.lwjgl.opengl.GLContext.getCapabilities();}catch(RuntimeException unavailable){return;}
        try(RenderState saved=new RenderState()){
            mc.getFramebuffer().bindFramebuffer(false);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if(texture==0)texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            if(width!=mc.displayWidth||height!=mc.displayHeight){width=mc.displayWidth;height=mc.displayHeight;glCopyTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,0,0,width,height,0);}
            else glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,width,height);
            started=System.nanoTime();active=true;
        }
    }
    public static void draw(){
        drawAt(System.nanoTime());
    }
    private static void drawAt(long now){
        if(!active)return;Minecraft mc=Minecraft.getMinecraft();
        float progress=Math.max(0,Math.min(1,(now-started)/180000000f));
        if(progress>=1||!UiEffects.motion()||width!=mc.displayWidth||height!=mc.displayHeight){active=false;return;}
        float alpha=(1-progress)*(1-progress);
        try(RenderState saved=new RenderState()){
            saved.screenSpace();glDisable(GL_CULL_FACE);glDisable(GL_FOG);glColorMask(true,true,true,true);org.lwjgl.opengl.GL20.glUseProgram(0);
            for(int unit=2;unit>0;unit--){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);glDisable(GL_TEXTURE_2D);}
            GL13.glActiveTexture(GL13.GL_TEXTURE0);glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
            glEnable(GL_TEXTURE_2D);glEnable(GL_BLEND);org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glColor4f(1,1,1,alpha);glBindTexture(GL_TEXTURE_2D,texture);
            ScaledResolution sr=new ScaledResolution(mc);float w=sr.getScaledWidth(),h=sr.getScaledHeight();
            glBegin(GL_QUADS);glTexCoord2f(0,1);glVertex2f(0,0);glTexCoord2f(0,0);glVertex2f(0,h);glTexCoord2f(1,0);glVertex2f(w,h);glTexCoord2f(1,1);glVertex2f(w,0);glEnd();
        }
    }
    public static void releaseResources(){active=false;if(texture!=0)GlStateManager.deleteTexture(texture);texture=width=height=0;}
}
