package me.youngjibbit.module.impl.client;

import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.CheckBoxValue;
import org.junit.Test;
import me.youngjibbit.module.impl.client.island.IslandLayout;
import me.youngjibbit.module.impl.client.island.IslandStateMachine;
import me.youngjibbit.module.impl.client.island.IslandWidget;
import me.youngjibbit.module.impl.client.island.IslandWidgetLayout;
import me.youngjibbit.utils.render.HudDrag;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class WatermarkIslandModeTest {
    @Test public void sanitizedEmptyTargetNeverEntersTheNotificationRenderer() throws Exception {
        me.youngjibbit.module.impl.client.island.IslandStateMachine model=new me.youngjibbit.module.impl.client.island.IslandStateMachine();
        me.youngjibbit.module.impl.client.island.IslandPresentation target=
                me.youngjibbit.module.impl.client.island.IslandPresentation.from(model.resolve(1,1,
                        new me.youngjibbit.module.impl.client.island.IslandStateMachine.Target(2,"§a",10,20,0),
                        null,true,false),"Neutron","Dev","",true,true,false);
        assertNull(me.youngjibbit.module.impl.client.island.IslandTargetComposition.capture(target,0,false));
        assertNull(target.notification);
        java.lang.reflect.Method render=WatermarkHUDModule.class.getDeclaredMethod("paintIslandNotification",
                me.youngjibbit.module.impl.client.island.IslandPresentation.class,
                me.youngjibbit.module.impl.client.island.IslandLayout.Frame.class,
                me.youngjibbit.utils.font.TTFFontRenderer.class,me.youngjibbit.utils.Theme.class,float.class);
        render.setAccessible(true);
        // No frame, font, theme or GL context exists: a non-notification must return before using them.
        render.invoke(new WatermarkHUDModule(),target,null,null,null,1f);
    }
    @Test public void appendsModernAndCreativeAfterLegacyChoicesAndRetainsGlassDefault() throws Exception {
        WatermarkHUDModule module = new WatermarkHUDModule();
        Field mode = WatermarkHUDModule.class.getDeclaredField("mode");
        mode.setAccessible(true);
        ComboValue value = (ComboValue) mode.get(module);
        assertArrayEquals(new String[]{
                "Simple","Glass","Outline","Rise","iOS","Logo 1",
                "Logo 2","Wordmark","Status","Dynamic Island","Modern","Creative"}, value.getOptions());
        assertEquals("Glass", value.getValue());
        assertEquals(1, value.getSelectedIndex());
        for (int i = 0; i < value.getOptions().length; i++) {
            value.setSelectedIndex(i);
            assertEquals(value.getOptions()[i], value.getValue());
        }
        value.setValue("Modern");
        assertEquals(10, value.getSelectedIndex());
        value.setValue("Creative");
        assertEquals(11, value.getSelectedIndex());
    }

    @Test public void optionalWidgetsShareOnePlacementChoiceAndOnlyExposeRelevantControls(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        ComboValue mode=(ComboValue)(Object)module.getValueByName("Mode");
        ComboValue placement=(ComboValue)(Object)module.getValueByName("Island Widget Placement");
        ComboValue clockStyle=(ComboValue)(Object)module.getValueByName("Island Clock Style");
        CheckBoxValue cps=(CheckBoxValue)(Object)module.getValueByName("Island CPS Widget");
        CheckBoxValue clock=(CheckBoxValue)(Object)module.getValueByName("Island Clock");
        assertArrayEquals(new String[]{"Inside","Outside"},placement.getOptions());
        assertArrayEquals(new String[]{"Text","Modern"},clockStyle.getOptions());
        assertFalse(cps.getValue());assertEquals("Inside",placement.getValue());assertEquals("Text",clockStyle.getValue());
        mode.setValue("Simple");assertFalse(cps.isVisible());assertFalse(placement.isVisible());
        assertTrue(module.getValueByName("FPS").isVisible());
        mode.setValue("Modern");assertTrue(cps.isVisible());assertFalse(placement.isVisible());
        assertFalse(module.getValueByName("FPS").isVisible());assertTrue(clockStyle.isVisible());
        cps.setValue(true);assertTrue(placement.isVisible());
        clock.setValue(false);assertFalse(clockStyle.isVisible());
        assertTrue(module.getValues().indexOf(clockStyle)>module.getValues().indexOf(module.getValueByName("Island Local Warnings")));
    }

    @Test public void fixedDockSettingsAppendWithoutMovingExistingIdsAndExplicitMovesSwap(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        ComboValue cps=dock(module,"CPS"),fps=dock(module,"FPS"),exp=dock(module,"EXP");
        assertArrayEquals(IslandWidgetLayout.DOCK_NAMES,cps.getOptions());
        int oldLast=module.getValues().indexOf(module.getValueByName("Island Widget Placement"));
        assertEquals(oldLast+1,module.getValues().indexOf(cps));
        assertEquals(oldLast+2,module.getValues().indexOf(fps));
        assertEquals(oldLast+3,module.getValues().indexOf(exp));
        assertDocks(module,0,3,4);
        module.applyWidgetDock(IslandWidget.Kind.CPS,3);assertDocks(module,3,0,4);
        module.applyWidgetDock(IslandWidget.Kind.FPS,4);assertDocks(module,3,4,0);
        module.applyWidgetDock(IslandWidget.Kind.CPS,-1);assertDocks(module,3,4,0);
        module.resetWidgetPositions();assertDocks(module,0,3,4);
        ComboValue mode=(ComboValue)(Object)module.getValueByName("Mode");
        ComboValue placement=(ComboValue)(Object)module.getValueByName("Island Widget Placement");
        CheckBoxValue enabled=(CheckBoxValue)(Object)module.getValueByName("Island CPS Widget");
        mode.setValue("Modern");enabled.setValue(true);
        assertFalse(cps.isVisible());placement.setValue("Outside");assertTrue(cps.isVisible());
        enabled.setValue(false);assertFalse(cps.isVisible());
    }

    @Test public void dockGestureRejectsHeldGameplayClicksAndCommitsOnlyOnRelease(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        IslandWidget[] widgets=widgets(IslandWidget.Kind.CPS,IslandWidget.Kind.FPS,IslandWidget.Kind.EXPLOIT);
        IslandWidgetLayout layout=layout(854,new int[]{0,3,4});
        float[] x=positions(layout);float y=layout.y+layout.diameter/2;
        float left=layout.circleX(0)+layout.diameter/2,right=layout.dockCircleX(3)+layout.diameter/2;
        try{
            HudDrag.context(false,true);
            module.interactWidgetDocks(false,true,left,y,layout,widgets,x,854,480);
            HudDrag.context(true,true);
            module.interactWidgetDocks(true,true,left,y,layout,widgets,x,854,480);
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,right,y,layout,widgets,x,854,480);
            assertDocks(module,0,3,4);
            HudDrag.context(true,true);
            module.interactWidgetDocks(true,true,left,y,layout,widgets,x,854,480);
            module.interactWidgetDocks(true,true,right,y,layout,widgets,x,854,480);
            assertDocks(module,0,3,4);
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,right,y,layout,widgets,x,854,480);
            assertDocks(module,3,0,4);
        }finally{HudDrag.context(false,false);}
    }

    @Test public void dockHitTestingUsesRenderedPositionsInsteadOfDestinationSlots(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        IslandWidget[] widgets=widgets(IslandWidget.Kind.CPS,IslandWidget.Kind.FPS,IslandWidget.Kind.EXPLOIT);
        IslandWidgetLayout layout=layout(854,new int[]{0,3,4});
        float[] x=positions(layout);x[0]-=50;
        float y=layout.y+layout.diameter/2;
        float resting=layout.circleX(0)+layout.diameter/2,actual=x[0]+layout.diameter/2;
        float destination=layout.dockCircleX(3)+layout.diameter/2;
        try{
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,resting,y,layout,widgets,x,854,480);
            HudDrag.context(true,true);
            module.interactWidgetDocks(true,true,resting,y,layout,widgets,x,854,480);
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,destination,y,layout,widgets,x,854,480);
            assertDocks(module,0,3,4);
            HudDrag.context(true,true);
            module.interactWidgetDocks(true,true,actual,y,layout,widgets,x,854,480);
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,destination,y,layout,widgets,x,854,480);
            assertDocks(module,3,0,4);
        }finally{HudDrag.context(false,false);}
    }

    @Test public void duplicateConfigDropSwapsTheVisibleOccupantsAndPersistsUniqueSlots(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        dock(module,"CPS").setSelectedIndex(0);dock(module,"FPS").setSelectedIndex(0);dock(module,"EXP").setSelectedIndex(0);
        IslandWidgetLayout layout=layout(320,new int[]{0,0,0});
        assertArrayEquals(new int[]{0,1,2},new int[]{layout.dock(0),layout.dock(1),layout.dock(2)});
        assertDocks(module,0,0,0); // Resolving a render layout is strictly read-only.
        drop(module,layout,widgets(IslandWidget.Kind.CPS,IslandWidget.Kind.FPS,IslandWidget.Kind.EXPLOIT),2,1);
        assertDocks(module,0,2,1);
        assertArrayEquals(new int[]{0,2,1},IslandWidgetLayout.resolveDocks(3,new int[]{
                dock(module,"CPS").getSelectedIndex(),dock(module,"FPS").getSelectedIndex(),dock(module,"EXP").getSelectedIndex()}));
    }

    @Test public void hiddenDuplicateConfigYieldsToTheVisibleOccupantsOnExplicitDrop(){
        WatermarkHUDModule module=new WatermarkHUDModule();
        dock(module,"CPS").setSelectedIndex(0);dock(module,"FPS").setSelectedIndex(0);dock(module,"EXP").setSelectedIndex(0);
        IslandWidgetLayout layout=layout(427,new int[]{0,0});
        assertDocks(module,0,0,0);
        assertEquals(0,layout.dock(0));assertEquals(1,layout.dock(1));
        drop(module,layout,widgets(IslandWidget.Kind.FPS,IslandWidget.Kind.EXPLOIT),1,0);
        assertDocks(module,2,1,0);
    }

    private static ComboValue dock(WatermarkHUDModule module,String name){
        return (ComboValue)(Object)module.getValueByName("Island "+name+" Dock");
    }
    private static void assertDocks(WatermarkHUDModule module,int cps,int fps,int exp){
        assertEquals(cps,dock(module,"CPS").getSelectedIndex());
        assertEquals(fps,dock(module,"FPS").getSelectedIndex());
        assertEquals(exp,dock(module,"EXP").getSelectedIndex());
    }
    private static IslandWidget[] widgets(IslandWidget.Kind... kinds){
        IslandWidget[] result=new IslandWidget[kinds.length];
        for(int i=0;i<kinds.length;i++)result[i]=new IslandWidget(kinds[i],kinds[i].name(),"1","live",true,0,false);
        return result;
    }
    private static IslandWidgetLayout layout(int width,int[] slots){
        return IslandWidgetLayout.capture(IslandLayout.forState(IslandStateMachine.State.IDLE,width,480,true),
                slots.length,true,null,slots,true);
    }
    private static float[] positions(IslandWidgetLayout layout){
        float[] x=new float[layout.count];for(int i=0;i<x.length;i++)x[i]=layout.x(i);return x;
    }
    private static void drop(WatermarkHUDModule module,IslandWidgetLayout layout,IslandWidget[] widgets,int index,int slot){
        float[] x=positions(layout);float y=layout.y+layout.diameter/2;
        float from=layout.circleX(index)+layout.diameter/2,to=layout.dockCircleX(slot)+layout.diameter/2;
        try{
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,from,y,layout,widgets,x,layout.frame.viewportWidth,480);
            HudDrag.context(true,true);
            module.interactWidgetDocks(true,true,from,y,layout,widgets,x,layout.frame.viewportWidth,480);
            module.interactWidgetDocks(true,true,to,y,layout,widgets,x,layout.frame.viewportWidth,480);
            HudDrag.context(true,false);
            module.interactWidgetDocks(true,false,to,y,layout,widgets,x,layout.frame.viewportWidth,480);
        }finally{HudDrag.context(false,false);}
    }
}
