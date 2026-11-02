package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import java.awt.Color;

@ModuleInfo(name="Crosshair",description="A smooth, configurable theme crosshair",category=Category.Render)
public final class Crosshair extends Module {
    private final SliderValue length=new SliderValue("Length",this,5.0,1,15),gap=new SliderValue("Gap",this,3.0,0,12),thickness=new SliderValue("Thickness",this,1.5,.5,4);
    private final CheckBoxValue dynamic=new CheckBoxValue("Dynamic",this,true),dot=new CheckBoxValue("Center Dot",this,false);
    private float spread;private long last;
    public static boolean replacesVanilla(){Module module=Pulsar.INSTANCE.getMm()==null?null:Pulsar.INSTANCE.getMm().getModule(Crosshair.class);return module!=null&&module.isToggled();}
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{if(mc.thePlayer==null||mc.gameSettings.thirdPersonView!=0||mc.gameSettings.showDebugInfo||!me.youngjibbit.module.impl.client.HUD.visible())return;long now=System.nanoTime();float dt=last==0?0:Math.min(.1f,(now-last)/1e9f);last=now;float target=dynamic.getValue()&&mc.thePlayer.isSprinting()?3:0;spread+=(target-spread)*UiEffects.response(dt,18);float x=e.getSr().getScaledWidth()/2f,y=e.getSr().getScaledHeight()/2f,g=gap.getValue().floatValue()+spread,l=length.getValue().floatValue(),t=thickness.getValue().floatValue();Color ink=new Color(UiEffects.gradientAt(Theme.getDefaultTheme(),UiEffects.Channel.TEXT,x,y),true);try(RenderState state=new RenderState()){state.screenSpace();UIUtils.drawDecorationRound(x-g-l,y-t/2,l,t,t/2,ink);UIUtils.drawDecorationRound(x+g,y-t/2,l,t,t/2,ink);UIUtils.drawDecorationRound(x-t/2,y-g-l,t,l,t/2,ink);UIUtils.drawDecorationRound(x-t/2,y+g,t,l,t/2,ink);if(dot.getValue())UIUtils.drawDecorationRound(x-t/2,y-t/2,t,t,t/2,ink);}});
    @Override public void onDisable(){last=0;spread=0;super.onDisable();}
}
