package me.youngjibbit.module.impl.client;

import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.After;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Actual Watermark and ArrayList consumers; never invoke a native render pass. */
public class Wave07CompactHudModeTest {
    @ModuleInfo(name="Visual Test", category=Category.Movement)
    public static final class Dummy extends Module {}
    @After public void restore(){ThemeMode.select(ThemeMode.LEGACY);}

    @Test public void modernThemeShowsRealIslandThroughTheHistoricalGlassDefault() {
        WatermarkHUDModule hud=new WatermarkHUDModule();
        ComboValue mode=hud.getModeSetting("Mode");
        assertEquals("Glass",mode.getValue());assertEquals(1,mode.getSelectedIndex());
        assertFalse(hud.islandSelected());
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertTrue(hud.islandSelected());
        mode.setValue("Dynamic Island");
        assertTrue(hud.islandSelected());
        ThemeMode.select(ThemeMode.LEGACY);
        assertTrue(hud.islandSelected());
        mode.setValue("Simple");
        assertFalse(hud.islandSelected());
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertFalse("Explicit legacy Simple must remain Simple",hud.islandSelected());
    }

    @Test public void nineHistoricalWatermarkChoicesAndValueIdsArePreserved(){
        WatermarkHUDModule hud=new WatermarkHUDModule();
        ComboValue mode=hud.getModeSetting("Mode");
        assertArrayEquals(new String[]{"Simple","Glass","Outline","Rise",
                "iOS","Logo 1","Logo 2","Wordmark","Status","Dynamic Island"},
                mode.getOptions());
        assertEquals(1,mode.getSelectedIndex());
        assertEquals("Glass",mode.getDefaultValue());
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("Glass",mode.getValue());
        assertEquals(1,mode.getSelectedIndex());
        assertNotNull(hud.getBooleanSetting("Island Target"));
        assertNotNull(hud.getBooleanSetting("Island Clock"));
    }

    @Test public void slimArrayListChangesOnlyPresentationNotSavedSettings() throws Exception {
        ArrayListHUDModule hud=new ArrayListHUDModule();
        Dummy module=new Dummy();
        ComboValue mode=hud.getModeSetting("Mode");
        Method getLabel=ArrayListHUDModule.class.getDeclaredMethod("getLabel",Module.class);
        getLabel.setAccessible(true);
        Method pad=ArrayListHUDModule.class.getDeclaredMethod("pad",SliderValue.class);
        pad.setAccessible(true);
        SliderValue spacing=hud.getNumberSetting("Row Spacing");
        SliderValue left=hud.getNumberSetting("Container Padding Left");
        assertEquals("| Visual Test",getLabel.invoke(hud,module));
        assertEquals(8f,(Float)pad.invoke(hud,spacing),0);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("Visual Test",getLabel.invoke(hud,module));
        assertEquals(5f,(Float)pad.invoke(hud,spacing),0);
        assertEquals(10f,(Float)pad.invoke(hud,left),0);
        assertEquals("Glass",mode.getValue());
        assertEquals(0,mode.getSelectedIndex());
        assertEquals(1d,hud.getNumberSetting("Scale").getValue(),0);
        ThemeMode.select(ThemeMode.LEGACY);
        assertEquals("| Visual Test",getLabel.invoke(hud,module));
        assertEquals(8f,(Float)pad.invoke(hud,spacing),0);
    }

    @Test public void explicitLocalPaddingKeepsItsFunctionalOverride() throws Exception {
        ArrayListHUDModule hud=new ArrayListHUDModule();
        Method pad=ArrayListHUDModule.class.getDeclaredMethod("pad",SliderValue.class);
        pad.setAccessible(true);
        SliderValue spacing=hud.getNumberSetting("Row Spacing");
        hud.getBooleanSetting("Custom Container Padding").setValue(true);
        spacing.setValue(13d);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals(13f,(Float)pad.invoke(hud,spacing),0);
        ThemeMode.select(ThemeMode.LEGACY);
        assertEquals(13f,(Float)pad.invoke(hud,spacing),0);
        assertEquals(13d,spacing.getValue(),0);
    }

    @Test public void noneModeStillHidesTheArrayList() {
        ArrayListHUDModule hud=new ArrayListHUDModule();
        ComboValue mode=hud.getModeSetting("Mode");
        mode.setValue("None");
        assertEquals(5,mode.getSelectedIndex());
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("None",mode.getValue());
    }
}
