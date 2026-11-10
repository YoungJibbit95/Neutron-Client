package me.youngjibbit.module.impl.client;

import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.CheckBoxValue;
import org.junit.Test;
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
}
