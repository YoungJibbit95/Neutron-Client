package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.render.*;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.*;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Actual cosmetic update/render listeners in the existing disconnected world fixture. */
final class CosmeticRenderCheck {
    @SuppressWarnings("unchecked") static <T> void invoke(Module module,String field,T event)throws Exception{
        ((Listener<T>)SessionInfoRenderCheck.get(module,field)).accept(event);
    }
    static void run()throws Exception{
        Minecraft mc=IngameHudRenderCheck.mc;
        Breadcrumbs trail=new Breadcrumbs();JumpCircle jump=new JumpCircle();ChinaHat hat=new ChinaHat();Crosshair crosshair=new Crosshair();Keystrokes keys=new Keystrokes();
        boolean oldMotion=ClientSettings.current().animations.getValue();int third=mc.gameSettings.thirdPersonView,key=mc.gameSettings.keyBindForward.getKeyCode();
        double px=mc.thePlayer.posX,py=mc.thePlayer.posY,pz=mc.thePlayer.posZ;boolean ground=mc.thePlayer.onGround;
        try(RenderState saved=new RenderState()){
            ClientSettings.current().animations.setValue(false);
            mc.thePlayer.setPosition(0,0,0);mc.thePlayer.onGround=true;invoke(jump,"update",new EventUpdate());
            mc.thePlayer.onGround=false;mc.thePlayer.motionY=.42;mc.thePlayer.setPosition(0,.42,0);invoke(jump,"update",new EventUpdate());
            VisualTrail rings=(VisualTrail)SessionInfoRenderCheck.get(jump,"rings");
            SessionInfoRenderCheck.require(rings.size()==1&&Math.abs(rings.y(0)-.02)<.00001,"Jump ring followed airborne Y instead of takeoff surface");
            for(int i=0;i<20;i++){mc.thePlayer.setPosition(-1+i*.1,0,-.4);invoke(trail,"update",new EventUpdate());}
            mc.thePlayer.setPosition(0,0,0);mc.thePlayer.lastTickPosX=mc.thePlayer.lastTickPosY=mc.thePlayer.lastTickPosZ=0;
            mc.getRenderManager().viewerPosX=mc.getRenderManager().viewerPosY=mc.getRenderManager().viewerPosZ=0;
            mc.gameSettings.thirdPersonView=1;
            glDisable(GL_SCISSOR_TEST);glDepthMask(true);glClearColor(.025f,.035f,.06f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(-4,4,-3,3,-10,10);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslatef(0,-.6f,0);glRotatef(28,1,0,0);
            BufferedImage before=SessionInfoRenderCheck.capture();
            glShadeModel(GL_FLAT);glEnable(GL_ALPHA_TEST);
            invoke(trail,"render",new Event3D(1));invoke(jump,"render",new Event3D(1));invoke(hat,"render",new Event3D(1));
            SessionInfoRenderCheck.require(glGetBoolean(GL_DEPTH_WRITEMASK)&&glGetInteger(GL_SHADE_MODEL)==GL_FLAT&&glIsEnabled(GL_ALPHA_TEST),"World cosmetics leaked depth/shading/alpha state");
            BufferedImage world=SessionInfoRenderCheck.capture();int changed=0;
            for(int y=0;y<world.getHeight();y++)for(int x=0;x<world.getWidth();x++)if(world.getRGB(x,y)!=before.getRGB(x,y))changed++;
            SessionInfoRenderCheck.require(changed>100,"World cosmetics produced no visible geometry");
            ImageIO.write(world,"png",new File(IngameHudRenderCheck.OUT,"neutron-world-cosmetics.png"));
            mc.gameSettings.thirdPersonView=0;mc.gameSettings.keyBindForward.setKeyCode(-100);
            invoke(crosshair,"render",new Event2D(0,new ScaledResolution(mc)));invoke(keys,"render",new Event2D(0,new ScaledResolution(mc)));
            SessionInfoRenderCheck.require("LMB".equals(Keystrokes.keyLabel(-100))&&"RMB".equals(Keystrokes.keyLabel(-99)),"Mouse-remapped movement key label");
            ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(IngameHudRenderCheck.OUT,"neutron-cosmetic-hud.png"));
            trail.onDisable();jump.onDisable();keys.onDisable();crosshair.onDisable();
            SessionInfoRenderCheck.require(((VisualTrail)SessionInfoRenderCheck.get(trail,"trail")).size()==0&&rings.size()==0,"Disabled cosmetics retained world samples");
            SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Cosmetic render GL error");
            System.out.println("PASS: takeoff ground coordinates, real world trail/ring/hat pixels, depth/alpha/shading restoration, remapped-key HUD and disable cleanup");
        }finally{ClientSettings.current().animations.setValue(oldMotion);mc.gameSettings.thirdPersonView=third;mc.gameSettings.keyBindForward.setKeyCode(key);mc.thePlayer.setPosition(px,py,pz);mc.thePlayer.onGround=ground;keys.onDisable();}
    }
}
