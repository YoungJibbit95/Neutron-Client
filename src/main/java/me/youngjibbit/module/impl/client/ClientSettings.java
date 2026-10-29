package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import java.util.*;

/** One persistent source of UI defaults; opening the screen is a one-shot module action. */
@ModuleInfo(name="Client Settings",description="Global UI effects and notifications",category=Category.Client)
public class ClientSettings extends Module {
    public final SliderValue roundness=new SliderValue("Roundness",this,5.0,0,18);
    public final ComboValue surface=new ComboValue("Surface Style",this,new String[]{"Standard","Clear","Gradient","Blur","Liquid Glass"},3);
    public final SliderValue surfaceOpacity=new SliderValue("Surface Opacity",this,1.0,0,1);
    public final SliderValue gradientFillStrength=new SliderValue("Gradient Fill Strength",this,.28,0,1);
    public final CheckBoxValue border=new CheckBoxValue("Global Border",this,true);
    public final ComboValue borderColor=new ComboValue("Border Color Source",this,new String[]{"Gradient","Accent"},0);
    public final SliderValue borderOpacity=new SliderValue("Border Opacity",this,.8,0,1);
    public final CheckBoxValue blur=new CheckBoxValue("Global Blur",this,true);
    public final ComboValue blurMethod=new ComboValue("Blur Method",this,new String[]{"Gaussian","Kawase"},0);
    public final SliderValue blurStrength=new SliderValue("Global Blur Strength",this,5.0,1,18);
    public final SliderValue blurQuality=new SliderValue("Kawase Quality",this,2.0,1,4,true);
    public final ComboValue glassAppearance=new ComboValue("Glass Appearance",this,new String[]{"Regular","Clear"},0);
    public final SliderValue glassRefraction=new SliderValue("Glass Refraction",this,.35,0,1);
    public final SliderValue glassTint=new SliderValue("Glass Tint",this,.18,0,.6);
    public final SliderValue glassLight=new SliderValue("Glass Edge Light",this,.3,0,1);
    public final CheckBoxValue glow=new CheckBoxValue("Global Glow",this,true);
    public final SliderValue glowIntensity=new SliderValue("Glow Intensity",this,1.0,0,2);
    public final SliderValue glowRadius=new SliderValue("Glow Extent",this,9.0,1,24);
    public final CheckBoxValue gradients=new CheckBoxValue("Shared Gradients",this,true);
    public final CheckBoxValue synchronize=new CheckBoxValue("Synchronize Text Border Glow",this,true);
    public final ComboValue direction=direction("Gradient Direction");
    public final ComboValue gradientMotion=motion("Gradient Motion");
    public final SliderValue gradientSpeed=speed("Gradient Speed");
    public final ComboValue textDirection=direction("Text Direction"),borderDirection=direction("Border Direction"),glowDirection=direction("Glow Direction");
    public final ComboValue textMotion=motion("Text Motion"),borderMotion=motion("Border Motion"),glowMotion=motion("Glow Motion");
    public final SliderValue textSpeed=speed("Text Speed"),borderSpeed=speed("Border Speed"),glowSpeed=speed("Glow Speed");
    public final CheckBoxValue animations=new CheckBoxValue("UI Animations",this,true);
    public final CheckBoxValue reducedMotion=new CheckBoxValue("Reduced Motion",this,false);
    public final ComboValue motionIntensity=new ComboValue("UI Motion Intensity",this,new String[]{"Calm","Balanced","Expressive"},1);
    public final ComboValue gradientMethod=new ComboValue("Gradient Method",this,new String[]{"Flow","Ping-Pong","Pulse"},0);
    public final CheckBoxValue ambientMotion=new CheckBoxValue("Ambient Motion",this,true);
    public final CheckBoxValue semanticColorPriority=new CheckBoxValue("Semantic Color Priority",this,true);
    public final CheckBoxValue notifications=new CheckBoxValue("Notifications",this,true);
    public final ComboValue notificationDesign=new ComboValue("Notification Design",this,new String[]{"Dark","Outline","Liquid Glass","Global"},3);
    public final ComboValue notificationPosition=new ComboValue("Notification Position",this,new String[]{"Bottom Right","Top Right","Bottom Left","Top Left"},0);
    public final SliderValue notificationDuration=new SliderValue("Notification Duration",this,5.0,1,15);
    public final SliderValue notificationWidth=new SliderValue("Notification Width",this,260.0,160,360);
    public final SliderValue notificationVisible=new SliderValue("Visible Notifications",this,3.0,1,5,true);
    public final SliderValue notificationQueue=new SliderValue("Queued Notifications",this,32.0,4,64,true);
    public final CheckBoxValue info=new CheckBoxValue("Info Notifications",this,true),success=new CheckBoxValue("Success Notifications",this,true),warnings=new CheckBoxValue("Warning Notifications",this,true),errors=new CheckBoxValue("Error Notifications",this,true);
    // Append registration, preserving every existing Value index. Group display is adjacent to Design.
    public final ComboValue notificationTheme=new ComboValue("Notification Theme",this,new String[]{"Follow Global","Obsidian","Aurora","Neon Edge","Minimal"},0);

    private ComboValue direction(String name){return new ComboValue(name,this,new String[]{"Theme","Vertical","Horizontal","Up","Left"},0);}
    private ComboValue motion(String name){return new ComboValue(name,this,new String[]{"Theme","Static","Animated","Solid"},0);}
    private SliderValue speed(String name){return new SliderValue(name,this,.18,.02,1);}
    public static ClientSettings current(){return Pulsar.INSTANCE.getMm()==null?null:(ClientSettings)Pulsar.INSTANCE.getMm().getModule(ClientSettings.class);}
    public Map<String,List<Value<?>>> groups(){
        Map<String,List<Value<?>>> groups=new LinkedHashMap<>();
        groups.put("Appearance",Arrays.asList(roundness,surface,surfaceOpacity,gradientFillStrength,border,borderColor,borderOpacity));
        groups.put("Blur / Liquid Glass",Arrays.asList(blur,blurMethod,blurStrength,blurQuality,glassAppearance,glassRefraction,glassTint,glassLight));
        groups.put("Glow",Arrays.asList(glow,glowIntensity,glowRadius));
        groups.put("Gradients / Motion",Arrays.asList(gradients,synchronize,direction,gradientMotion,gradientMethod,gradientSpeed,textDirection,textMotion,textSpeed,borderDirection,borderMotion,borderSpeed,glowDirection,glowMotion,glowSpeed,animations,reducedMotion,motionIntensity,ambientMotion,semanticColorPriority));
        groups.put("Notifications",Arrays.asList(notifications,notificationDesign,notificationTheme,notificationPosition,notificationDuration,notificationWidth,notificationVisible,notificationQueue,info,success,warnings,errors));
        return groups;
    }
    public ClientSettings(){
        for(Value<?> v:Arrays.asList(textDirection,textMotion,textSpeed,borderDirection,borderMotion,borderSpeed,glowDirection,glowMotion,glowSpeed))v.setVisibility(m->!synchronize.getValue());
        direction.setVisibility(m->synchronize.getValue());
        gradientMotion.setVisibility(m->synchronize.getValue());
        gradientSpeed.setVisibility(m->synchronize.getValue()&&gradientMotion.getValue().equals("Animated"));
        textSpeed.setVisibility(m->!synchronize.getValue()&&textMotion.getValue().equals("Animated"));
        borderSpeed.setVisibility(m->!synchronize.getValue()&&borderMotion.getValue().equals("Animated"));
        glowSpeed.setVisibility(m->!synchronize.getValue()&&glowMotion.getValue().equals("Animated"));
        blurQuality.setVisibility(m->blurMethod.getValue().equals("Kawase"));
    }
    @Override public void beginConfigUpdate(){
        super.beginConfigUpdate();
        // Only the new appearance key has this migration default. Imported keys still win,
        // and callbacks/persistence retain the existing config batch owner.
        notificationTheme.reset();
    }
    @Override public void onValueChange(){me.youngjibbit.utils.render.UiEffects.invalidate();}
    @Override public void onEnable(){
        super.onEnable();
        if(mc!=null)mc.displayGuiScreen(new SettingsMenu(mc.currentScreen));
        setToggled(false);
    }
}
