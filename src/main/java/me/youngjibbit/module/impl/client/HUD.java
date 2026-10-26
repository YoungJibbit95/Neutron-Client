package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.render.*;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;

/** Toggle shows alignment guides in chat. The persistent layout controls apply independently. */
@ModuleInfo(id="hud",name="HUD",description="Chat alignment guides and shared HUD layout controls",category=Category.Client)
public class HUD extends Module {
    public final CheckBoxValue visible=new CheckBoxValue("HUD Visible",this,true);
    public final CheckBoxValue editInChat=new CheckBoxValue("Edit HUD In Chat",this,true);
    public final CheckBoxValue screenBounds=new CheckBoxValue("Keep HUD On Screen",this,true);
    public final SliderValue snap=new SliderValue("HUD Grid Size",this,0.0,0,16,true);
    public static HUD current(){return Pulsar.INSTANCE.getMm()==null?null:(HUD)Pulsar.INSTANCE.getMm().getModule(HUD.class);}
    public static boolean visible(){HUD h=current();return h==null||h.visible.getValue();}
    public static boolean editable(){HUD h=current();return h==null||h.editInChat.getValue();}
    public static boolean bounded(){HUD h=current();return h==null||h.screenBounds.getValue();}
    public static float snap(float p){HUD h=current();float step=h==null?0:h.snap.getValue().floatValue();return step>0?Math.round(p/step)*step:p;}
    public static void resetPositions(){
        if(Pulsar.INSTANCE.getMm()==null)return;
        for(Module m:Pulsar.INSTANCE.getMm().getAllModules()){
            if(m instanceof ArrayListHUDModule)((ArrayListHUDModule)m).resetPosition();
            if(m instanceof ArmorHUD)((ArmorHUD)m).resetPosition();
            if(m instanceof SessionInfo)((SessionInfo)m).resetPosition();
        }
    }
    @Subscribe private final Listener<Event2D> guides=new Listener<>(e->{
        if(!(mc.currentScreen instanceof GuiChat)||!editInChat.getValue()||!visible.getValue())return;
        ScaledResolution sr=new ScaledResolution(mc);
        try(RenderState state=new RenderState()){
            state.screenSpace();glDisable(GL_TEXTURE_2D);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glColor4f(.65f,.75f,.9f,.12f);glLineWidth(1);
            float step=Math.max(8,snap.getValue().floatValue());glBegin(GL_LINES);
            for(float x=step;x<sr.getScaledWidth();x+=step){glVertex2f(x,0);glVertex2f(x,sr.getScaledHeight());}
            for(float y=step;y<sr.getScaledHeight();y+=step){glVertex2f(0,y);glVertex2f(sr.getScaledWidth(),y);}glEnd();
        }
    });
}
