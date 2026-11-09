package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import java.awt.Color;

@ModuleInfo(name="Crosshair",description="A smooth, configurable theme crosshair",category=Category.Render)
public final class Crosshair extends Module {
    private final SliderValue length=new SliderValue("Length",this,5.0,1,15),gap=new SliderValue("Gap",this,3.0,0,12),thickness=new SliderValue("Thickness",this,1.5,.5,4);
    private final CheckBoxValue dynamic=new CheckBoxValue("Dynamic",this,true),dot=new CheckBoxValue("Center Dot",this,false);
    // Append the cosmetic choice after every pre-existing Value registration.
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Basic","Modern","Creative"},0);
    private float spread;private long last;
    public static boolean replacesVanilla(){Module module=Pulsar.INSTANCE.getMm()==null?null:Pulsar.INSTANCE.getMm().getModule(Crosshair.class);return module!=null&&module.isToggled();}
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{
        if(mc.thePlayer==null||mc.gameSettings.thirdPersonView!=0||mc.gameSettings.showDebugInfo||!me.youngjibbit.module.impl.client.HUD.visible())return;
        long now=System.nanoTime();float dt=last==0?0:Math.min(.1f,(now-last)/1e9f);last=now;
        float target=dynamic.getValue()&&mc.thePlayer.isSprinting()?3:0;
        spread+=(target-spread)*UiEffects.response(dt,18);
        float x=e.getSr().getScaledWidth()/2f,y=e.getSr().getScaledHeight()/2f;
        float g=gap.getValue().floatValue()+spread,l=length.getValue().floatValue(),t=thickness.getValue().floatValue();
        String design=mode.getValue();
        boolean modern="Modern".equals(design);
        boolean creative="Creative".equals(design);
        boolean twoDotwo=modern||creative;
        Theme theme=twoDotwo?Theme.resolve(ThemeCatalog.global().current()):Theme.getDefaultTheme();
        float pulse=creative?creativePulse(UiEffects.time(),UiEffects.motion()):1f;
        try(RenderState state=new RenderState()){
            state.screenSpace();
            if(twoDotwo){
                Color primary=new Color(theme.getPrimaryGradientColor().getRed(),
                        theme.getPrimaryGradientColor().getGreen(),
                        theme.getPrimaryGradientColor().getBlue(),Math.round(255*.9f));
                Color secondary=new Color(theme.getSecondaryGradientColor().getRed(),
                        theme.getSecondaryGradientColor().getGreen(),
                        theme.getSecondaryGradientColor().getBlue(),Math.round(255*.75f));
                float ll=creative?l*(.85f+.35f*pulse):l;
                float tt=creative?t*(.8f+.45f*pulse):t;
                float gg=creative?g*(.9f+.25f*pulse):g;
                if(modern){
                    GuiShaderBloom.beginBatch();
                    float glowRad=UiEffects.glowRadius(t);
                    float glowA=UiEffects.glowAlpha(.4f);
                    int primaryRGB=theme.getPrimaryGradientRGB();
                    UiEffects.drawGlow(x-gg-ll,y-tt/2,ll,tt,(int)glowRad,primaryRGB,glowA,2,2);
                    UiEffects.drawGlow(x+gg,y-tt/2,ll,tt,(int)glowRad,primaryRGB,glowA,2,2);
                    UiEffects.drawGlow(x-tt/2,y-gg-ll,tt,ll,(int)glowRad,primaryRGB,glowA,2,2);
                    UiEffects.drawGlow(x-tt/2,y+gg,tt,ll,(int)glowRad,primaryRGB,glowA,2,2);
                    GuiShaderBloom.flushBatch(2,2);
                }
                Color hInk=modern?primary:new Color(theme.getPrimaryGradientColor().getRed(),
                        theme.getPrimaryGradientColor().getGreen(),
                        theme.getPrimaryGradientColor().getBlue(),Math.round(255*(.8f+.2f*pulse)));
                Color vInk=creative?secondary:hInk;
                UIUtils.drawDecorationRound(x-gg-ll,y-tt/2,ll,tt,tt/2,hInk);
                UIUtils.drawDecorationRound(x+gg,y-tt/2,ll,tt,tt/2,hInk);
                UIUtils.drawDecorationRound(x-tt/2,y-gg-ll,tt,ll,tt/2,vInk);
                UIUtils.drawDecorationRound(x-tt/2,y+gg,tt,ll,tt/2,vInk);
                if(dot.getValue()){
                    float ds=creative?t*(.9f+.5f*pulse):t;
                    UIUtils.drawDecorationRound(x-ds/2,y-ds/2,ds,ds,ds/2,hInk);
                }
            }else{
                Color ink=new Color(UiEffects.gradientAt(Theme.getDefaultTheme(),UiEffects.Channel.TEXT,x,y),true);
                UIUtils.drawDecorationRound(x-g-l,y-t/2,l,t,t/2,ink);
                UIUtils.drawDecorationRound(x+g,y-t/2,l,t,t/2,ink);
                UIUtils.drawDecorationRound(x-t/2,y-g-l,t,l,t/2,ink);
                UIUtils.drawDecorationRound(x-t/2,y+g,t,l,t/2,ink);
                if(dot.getValue())UIUtils.drawDecorationRound(x-t/2,y-t/2,t,t,t/2,ink);
            }
        }
    });
    static float creativePulse(float seconds,boolean motion){return motion?.5f+.5f*(float)Math.sin(seconds*3f):1f;}
    @Override public void onDisable(){last=0;spread=0;super.onDisable();}
}
