package me.youngjibbit.module.impl.client;

import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class WatermarkIslandModeTest {
    @Test public void appendsOnlyOneModeAfterNineLegacyChoicesAndRetainsGlassDefault() throws Exception {
        WatermarkHUDModule module = new WatermarkHUDModule();
        Field mode = WatermarkHUDModule.class.getDeclaredField("mode");
        mode.setAccessible(true);
        ComboValue value = (ComboValue) mode.get(module);
        assertArrayEquals(new String[]{
                "Simple","Glass","Outline","Rise","iOS","Logo 1",
                "Logo 2","Wordmark","Status","Dynamic Island"}, value.getOptions());
        assertEquals("Glass", value.getValue());
        assertEquals(1, value.getSelectedIndex());
        for (int i = 0; i < 9; i++) {
            value.setSelectedIndex(i);
            assertEquals(value.getOptions()[i], value.getValue());
        }
        value.setValue("Dynamic Island");
        assertEquals(9, value.getSelectedIndex());
    }
}
