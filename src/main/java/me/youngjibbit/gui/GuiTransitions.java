package me.youngjibbit.gui;

import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

/** A single timeline moves both pages; no capture or render target work in steady frames. */
public final class GuiTransitions {
    private static final long SLIDE_NANOS=220_000_000L;
    private static int texture, incomingTexture, width, height, direction=1;
    private static long started;
    private static boolean active, slide;
    private static final List<WeakReference<GuiScreen>> history=new ArrayList<>();
    private static WeakReference<GuiScreen> destination=new WeakReference<>(null);
    private GuiTransitions(){}
    private static boolean client(GuiScreen screen){return screen!=null&&screen.getClass().getName().startsWith("me.youngjibbit.");}
    public static void beforeChange(GuiScreen from,GuiScreen to){
        active=false;
        if(from==to)return;
        slide=client(from)&&client(to);
        direction=1;
        if(!slide||destination.get()!=from)history.clear();
        if(slide){
            int previous=-1;
            for(int i=history.size()-1;i>=0;i--)if(history.get(i).get()==to){previous=i;break;}
            if(previous>=0){direction=-1;while(history.size()>previous)history.remove(history.size()-1);}
            else{if(history.size()==16)history.remove(0);history.add(new WeakReference<>(from));}
        }
        destination=new WeakReference<>(to);
        if(!UiEffects.motion()||!client(from)&&!client(to))return;
        Minecraft mc=Minecraft.getMinecraft();
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
            // Start at the first painted destination, not before potentially expensive initGui.
            started=0;active=true;
        }
    }
    public static boolean blocksPointerInput(GuiScreen screen){
        Minecraft mc=Minecraft.getMinecraft();
        return active&&slide&&destination.get()==screen&&UiEffects.motion()&&mc!=null
                &&width==mc.displayWidth&&height==mc.displayHeight
                &&(started==0||System.nanoTime()-started<SLIDE_NANOS);
    }
    public static void draw(){
        drawAt(System.nanoTime());
    }
    private static void drawAt(long now){
        if(!active)return;Minecraft mc=Minecraft.getMinecraft();
        boolean first=started==0;
        if(first)started=now;
        float progress=Math.max(0,Math.min(1,(now-started)/(float)(slide?SLIDE_NANOS:180_000_000L)));
        if(progress>=1||!UiEffects.motion()||width!=mc.displayWidth||height!=mc.displayHeight){active=false;return;}
        try(RenderState saved=new RenderState()){
            mc.getFramebuffer().bindFramebuffer(true);
            saved.screenSpace();glDisable(GL_CULL_FACE);glDisable(GL_FOG);glColorMask(true,true,true,true);org.lwjgl.opengl.GL20.glUseProgram(0);
            for(int unit=2;unit>0;unit--){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);glDisable(GL_TEXTURE_2D);}
            GL13.glActiveTexture(GL13.GL_TEXTURE0);glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
            glEnable(GL_TEXTURE_2D);org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
            ScaledResolution sr=new ScaledResolution(mc);float w=(float)sr.getScaledWidth_double(),h=(float)sr.getScaledHeight_double();
            if(slide){
                if(incomingTexture==0)incomingTexture=glGenTextures();
                glBindTexture(GL_TEXTURE_2D,incomingTexture);
                if(first){
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                    glCopyTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,0,0,width,height,0);
                }else glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,width,height);
                float eased=progress*progress*(3-2*progress);
                glDisable(GL_BLEND);glColor4f(1,1,1,1);
                page(texture,-direction*eased*w,w,h);
                page(incomingTexture,direction*(1-eased)*w,w,h);
            }else{
                // Opening/closing the UI over gameplay retains the short crossfade.
                glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                glColor4f(1,1,1,(1-progress)*(1-progress));page(texture,0,w,h);
            }
        }
    }
    private static void page(int image,float x,float w,float h){
        glBindTexture(GL_TEXTURE_2D,image);
        glBegin(GL_QUADS);glTexCoord2f(0,1);glVertex2f(x,0);glTexCoord2f(0,0);glVertex2f(x,h);glTexCoord2f(1,0);glVertex2f(x+w,h);glTexCoord2f(1,1);glVertex2f(x+w,0);glEnd();
    }
    public static void releaseResources(){active=false;history.clear();destination.clear();if(texture!=0)GlStateManager.deleteTexture(texture);if(incomingTexture!=0)GlStateManager.deleteTexture(incomingTexture);texture=incomingTexture=width=height=0;}
}
