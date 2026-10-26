package me.youngjibbit.theme;

import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Editor-only values, deliberately outside module discovery and without a persistence listener. */
@ModuleInfo(name="Theme Preview",category=Category.Client)
public final class ThemeDraft extends Module implements AutoCloseable {
    public final Value<String> title=new Value<>("Name",this,"Custom theme");
    public final ColorValue first=new ColorValue("Stop 1",this,Color.CYAN),second=new ColorValue("Stop 2",this,Color.MAGENTA),background=new ColorValue("Background",this,new Color(5,5,7,116)),text=new ColorValue("Text",this,Color.WHITE),accent=new ColorValue("Accent",this,Color.CYAN);
    public final ComboValue direction=new ComboValue("Direction",this,new String[]{"Horizontal","Vertical"},0);
    public final CheckBoxValue animated=new CheckBoxValue("Animated",this,false);
    public final SliderValue speed=new SliderValue("Speed",this,.18,.02,2),glowStrength=new SliderValue("Glow strength",this,.75,0,1.5),glowRadius=new SliderValue("Glow radius",this,9.0,1,16),outlineStrength=new SliderValue("Outline strength",this,.8,0,1),outlineWidth=new SliderValue("Outline width",this,1.0,.4,2.5);
    private final ThemeCatalog catalog;
    private final Consumer<String> selection;
    private String id;
    private Theme baseline;
    private boolean editing,loading,finished;
    public ThemeDraft(ThemeCatalog catalog,Consumer<String> selection){this.catalog=catalog;this.selection=selection;Theme initial=catalog.find(catalog.selectedReference());load(initial==null?Theme.PinkLemonade:initial);}
    public boolean isEditable(){return editing;}
    public boolean canDelete(){return editing&&catalog.find(id)!=null;}
    public String reference(){return id;}
    public void choose(Theme theme){load(theme);}
    public void duplicate(){Theme theme=snapshot();id="custom:"+UUID.randomUUID();editing=true;title.setValue(shortName(theme.getDisplayName()+" copy"));baseline=snapshot();preview();}
    private static String shortName(String name){return name.length()>48?name.substring(0,48):name;}
    private void load(Theme theme){
        loading=true;id=theme.name();baseline=theme;editing=theme.isCustom();
        try{
            title.setValue(theme.getDisplayName());first.setColor(theme.getPrimaryGradientColor());second.setColor(theme.getSecondaryGradientColor());background.setColor(theme.getBackground());text.setColor(theme.getTextColor());accent.setColor(theme.getAccentColor());direction.setValue(theme.isVertical()?"Vertical":"Horizontal");animated.setValue(theme.isAnimated());speed.setValue((double)theme.getAnimationSpeed());glowStrength.setValue((double)theme.getGlowStrength());glowRadius.setValue((double)theme.getGlowRadius());outlineStrength.setValue((double)theme.getOutlineStrength());outlineWidth.setValue((double)theme.getOutlineWidth());
        }finally{loading=false;}preview();
    }
    public Theme snapshot(){if(!editing)return baseline;return Theme.custom(id,title.getValue(),Arrays.asList(first.getValue(),second.getValue()),direction.getSelectedIndex()==1,animated.getValue(),speed.getValue().floatValue(),background.getValue(),text.getValue(),accent.getValue(),glowStrength.getValue().floatValue(),glowRadius.getValue().floatValue(),outlineStrength.getValue().floatValue(),outlineWidth.getValue().floatValue());}
    @Override public void onValueChange(){if(!loading&&catalog!=null)preview();}
    private void preview(){if(!finished)try{catalog.preview(snapshot());}catch(IllegalArgumentException ignored){/* Incomplete name stays local until it is valid. */}}
    public void reset(){load(baseline);}
    public void save()throws IOException{
        Theme theme=snapshot();if(editing)catalog.save(theme);
        catalog.cancelPreview();selection.accept(theme.name());finished=true;
    }
    public void delete()throws IOException{
        String previous=catalog.selectedReference();catalog.delete(id);if(!catalog.selectedReference().equals(previous))selection.accept(catalog.selectedReference());finished=true;
    }
    public void cancel(){catalog.cancelPreview();finished=true;}
    @Override public void close(){if(!finished)cancel();}
}
