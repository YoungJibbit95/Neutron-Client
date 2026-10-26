package me.youngjibbit.module.impl.render;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
/** Exercises actual start/end + deferred batch APIs, with deterministic creation/draw counters. */
public final class EffectConsistencyCheck {
    static void run() throws Exception {
        Minecraft mc=SessionInfoRenderCheck.mc;mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);
        SessionInfoRenderCheck.backdrop();BufferedImage before=SessionInfoRenderCheck.capture();
        Blur.startBlur();UIUtils.drawRound(24,28,140,80,8,new Color(0,0,0,1));Blur.endBlur(8,1);
        BufferedImage after=SessionInfoRenderCheck.capture();int changes=0;
        for(int y=0;y<640;y++)for(int x=0;x<960;x++){boolean outside=x<44||x>332||y<52||y>220;if(outside)SessionInfoRenderCheck.require(before.getRGB(x,y)==after.getRGB(x,y),"Legacy blur escaped mask");else if(before.getRGB(x,y)!=after.getRGB(x,y))changes++;}
        SessionInfoRenderCheck.require(changes>100,"Legacy blur has no visible effect");
        SessionInfoRenderCheck.sheet("04-common-effects.png",Arrays.asList("Before: structured scene","After: low-alpha legacy mask"),Arrays.asList(before,after));
        ArrayListSurface panel=new ArrayListSurface();
        RenderDiagnostics.enabled=true;RenderDiagnostics.reset();frame(panel);System.out.println("first mixed frame (shared cache warm): "+RenderDiagnostics.snapshot());
        RenderDiagnostics.reset();for(int i=0;i<8;i++)frame(panel);
        SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.shaderCompiles==0,"Steady rendering creates GPU resources");
        System.out.println("8 stable mixed-HUD frames: "+RenderDiagnostics.snapshot());
        RenderDiagnostics.reset();for(int i=0;i<4;i++){mc.displayWidth=i%2==0?1101:960;mc.displayHeight=i%2==0?733:640;mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);frame(panel);}
        System.out.println("4 alternating resizes: "+RenderDiagnostics.snapshot());panel.close();RenderDiagnostics.enabled=false;
        // Exercise the real alternate-screen entry point, including its opening scale and a resize.
        SessionInfoRenderCheck.set(mc,"renderEngine",new net.minecraft.client.renderer.texture.TextureManager(mc.getResourceManager()));
        mc.fontRendererObj=new net.minecraft.client.gui.FontRenderer(mc.gameSettings,new net.minecraft.util.ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);
        mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
        SessionInfoRenderCheck.set(me.youngjibbit.Pulsar.INSTANCE,"mm",new me.youngjibbit.module.ModuleManager());
        me.youngjibbit.gui.iosUi.iOS ios=new me.youngjibbit.gui.iosUi.iOS();
        SessionInfoRenderCheck.set(me.youngjibbit.Pulsar.INSTANCE,"iOS",ios);
        for(int i=0;i<2;i++){mc.displayWidth=i==0?960:640;mc.displayHeight=i==0?640:480;mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);net.minecraft.client.gui.ScaledResolution sr=new net.minecraft.client.gui.ScaledResolution(mc);ios.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());for(int j=0;j<25;j++){SessionInfoRenderCheck.backdrop();ios.drawScreen(-1,-1,0);Thread.sleep(12);}}
        SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Common effect GL error");System.out.println("PASS: legacy blur mask, transformed/nested bloom, mixed panels, stable resource counts, repeated resize");
    }
    static void frame(ArrayListSurface panel){
        SessionInfoRenderCheck.backdrop();GuiShaderBloom.beginBatch();GL11.glPushMatrix();GL11.glTranslatef(60,35,0);GL11.glScalef(0.8f,0.8f,1);GuiShaderBloom.queueRoundedRect(10,10,110,40,5,0x80FF00FF);GL11.glPopMatrix();GuiShaderBloom.beginBatch();GuiShaderBloom.queueRoundedRect(220,30,60,30,5,0x8000FFFF);GuiShaderBloom.flushBatch(2,2);
        GuiShaderBloom.afterBatch(()->panel.panel(30,100,140,70,7,Theme.SynthWave,true,true,true,4,0.5f,0.8f,()->{}));GuiShaderBloom.flushBatch(2,2);
        Blur.startBlur();Blur.startBlur();UIUtils.drawRound(210,110,130,60,5,Color.WHITE);Blur.endBlur(5,1);Blur.endBlur(5,1);
    }
}
