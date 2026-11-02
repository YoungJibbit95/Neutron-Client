package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.*;

@ModuleInfo(name="JumpCircle",description="Expanding theme rings at takeoff",category=Category.Render)
public final class JumpCircle extends Module {
    private final SliderValue radius=new SliderValue("Radius",this,1.5,.3,3),duration=new SliderValue("Duration",this,1.1,.3,3);
    private final VisualTrail rings=new VisualTrail(16);
    private Object lastWorld;private boolean grounded;private double groundX,groundY,groundZ;
    @Subscribe private final Listener<EventUpdate> update=new Listener<>(e->{
        if(mc.thePlayer==null){reset();return;}
        if(lastWorld!=mc.theWorld){reset();lastWorld=mc.theWorld;}
        if(mc.thePlayer.onGround){groundX=mc.thePlayer.posX;groundY=mc.thePlayer.posY;groundZ=mc.thePlayer.posZ;}
        if(grounded&&!mc.thePlayer.onGround&&mc.thePlayer.motionY>0)rings.add(mc.theWorld,groundX,groundY+.02,groundZ,System.nanoTime());
        grounded=mc.thePlayer.onGround;
    });
    @Subscribe private final Listener<Event3D> render=new Listener<>(e->{rings.world(mc.theWorld);long now=System.nanoTime(),life=(long)(duration.getValue()*1e9);rings.expire(now,life);if(rings.size()==0)return;try(RenderState state=WorldVisuals.begin()){for(int i=0;i<rings.size();i++){float p=Math.min(1,(now-rings.time(i))/(float)life);double r=radius.getValue()*(UiEffects.motion()?1-(1-p)*(1-p):1);WorldVisuals.ring(rings.x(i),rings.y(i),rings.z(i),r,(1-p)*(1-p));}}});
    private void reset(){rings.clear();lastWorld=null;grounded=false;}
    @Override public void onDisable(){reset();super.onDisable();}
}
