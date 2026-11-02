package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.*;
import static org.lwjgl.opengl.GL11.*;

@ModuleInfo(name="ChinaHat",description="A theme-colored conical hat in third person",category=Category.Render)
public final class ChinaHat extends Module {
    private final SliderValue radius=new SliderValue("Radius",this,.65,.3,1.2),opacity=new SliderValue("Opacity",this,.38,.1,.9);
    @Subscribe private final Listener<Event3D> render=new Listener<>(e->{if(mc.thePlayer==null||mc.gameSettings.thirdPersonView==0||mc.thePlayer.isInvisible())return;float p=e.getPartialTicks();double x=mc.thePlayer.lastTickPosX+(mc.thePlayer.posX-mc.thePlayer.lastTickPosX)*p,y=mc.thePlayer.lastTickPosY+(mc.thePlayer.posY-mc.thePlayer.lastTickPosY)*p+mc.thePlayer.height+.08-(mc.thePlayer.isSneaking()?.2:0),z=mc.thePlayer.lastTickPosZ+(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)*p;try(RenderState state=WorldVisuals.begin()){glBegin(GL_TRIANGLE_FAN);WorldVisuals.color(.5f,opacity.getValue().floatValue());WorldVisuals.vertex(x,y+.3,z);for(int i=0;i<=WorldVisuals.SEGMENTS;i++){WorldVisuals.color(i/(float)WorldVisuals.SEGMENTS,opacity.getValue().floatValue());WorldVisuals.vertex(x+WorldVisuals.COS[i]*radius.getValue(),y,z+WorldVisuals.SIN[i]*radius.getValue());}glEnd();WorldVisuals.ring(x,y,z,radius.getValue(),.8f);}});
}
