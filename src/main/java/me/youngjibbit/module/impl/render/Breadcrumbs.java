package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.*;
import static org.lwjgl.opengl.GL11.*;

@ModuleInfo(name="Breadcrumbs",description="A fading theme-colored trail of your movement",category=Category.Render)
public final class Breadcrumbs extends Module {
    private final SliderValue duration=new SliderValue("Duration",this,3.0,.5,10);
    private final SliderValue width=new SliderValue("Width",this,2.0,1,5);
    private final VisualTrail trail=new VisualTrail(256);
    @Subscribe private final Listener<EventUpdate> update=new Listener<>(e->{if(mc.thePlayer==null){trail.clear();return;}trail.world(mc.theWorld);int n=trail.size();double x=mc.thePlayer.posX,y=mc.thePlayer.posY+.04,z=mc.thePlayer.posZ;if(n==0||Math.abs(x-trail.x(n-1))+Math.abs(y-trail.y(n-1))+Math.abs(z-trail.z(n-1))>.025)trail.add(mc.theWorld,x,y,z,System.nanoTime());});
    @Subscribe private final Listener<Event3D> render=new Listener<>(e->{trail.world(mc.theWorld);long now=System.nanoTime(),life=(long)(duration.getValue()*1e9);trail.expire(now,life);if(trail.size()<2)return;try(RenderState state=WorldVisuals.begin()){glLineWidth(width.getValue().floatValue());glBegin(GL_LINE_STRIP);for(int i=0;i<trail.size();i++){WorldVisuals.color(i/(float)trail.size(),1-(now-trail.time(i))/(float)life);WorldVisuals.vertex(trail.x(i),trail.y(i),trail.z(i));}glEnd();}});
    @Override public void onDisable(){trail.clear();super.onDisable();}
}
