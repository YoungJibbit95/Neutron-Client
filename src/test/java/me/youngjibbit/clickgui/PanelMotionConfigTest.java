package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Uses the real configuration codec; no animation State is stored as a Value. */
public class PanelMotionConfigTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();

    @Test public void interruptedPanelMotionDoesNotChangeSavedModuleValues() throws Exception {
        Path path=folder.newFolder().toPath();
        ClientSettings settings=new ClientSettings();
        PanelControlMotionTest.Dummy dummy=new PanelControlMotionTest.Dummy();
        ConfigManager writer=new ConfigManager(path,Arrays.asList(settings,dummy),()->false,message->{});
        writer.initialize(Collections.emptyList());
        settings.motionIntensity.setValue("Expressive");
        settings.surface.setValue("Liquid Glass");
        settings.roundness.setValue(10.5);
        dummy.check.setValue(true);
        dummy.combo.setValue("Three");
        dummy.slider.setValue(7.25);
        int changes=dummy.changes;

        ClickModule row=ClickModule.forPanel(dummy,238);
        row.setOpen(true);
        for(int i=0;i<60;i++){
            row.updatePanelAnimation(.016f);
            for(me.youngjibbit.clickgui.component.Component.Sub<?> value:row.presentationValues()) {
                value.advanceMotion(.016f,-1,-1,false,false,true);
            }
        }
        for(int i=0;i<50;i++){
            row.setOpen(i%2==0);
            row.updatePanelAnimation(.008f);
        }
        row.resetPanelMotion();
        assertEquals(changes,dummy.changes);
        assertTrue(dummy.check.getValue());
        assertEquals("Three",dummy.combo.getValue());
        assertEquals(7.25,dummy.slider.getValue(),0);
        assertTrue(writer.saveDefaultConfig());
        writer.shutdown();

        ClientSettings restoredSettings=new ClientSettings();
        PanelControlMotionTest.Dummy restored=new PanelControlMotionTest.Dummy();
        ConfigManager reader=new ConfigManager(path,Arrays.asList(restoredSettings,restored),
                ()->false,message->{});
        reader.initialize(Collections.emptyList());
        assertTrue(restored.check.getValue());
        assertEquals("Three",restored.combo.getValue());
        assertEquals(7.25,restored.slider.getValue(),0);
        assertEquals("Expressive",restoredSettings.motionIntensity.getValue());
        assertEquals("Liquid Glass",restoredSettings.surface.getValue());
        assertEquals(10.5,restoredSettings.roundness.getValue(),0);
        reader.shutdown();
    }

    @Test public void legacyDefaultsAreIndependentOfPanelSpringInitialization() {
        ClientSettings settings=new ClientSettings();
        PanelMotion motion=new PanelMotion();motion.reset(0);
        motion.category(100,.03f,settings);
        motion.entrance(.03f,settings);
        assertEquals(5d,settings.roundness.getValue(),0);
        assertEquals("Blur",settings.surface.getValue());
        assertTrue(settings.animations.getValue());
        assertFalse(settings.reducedMotion.getValue());
        assertEquals("Balanced",settings.motionIntensity.getValue());
        assertArrayEquals(new String[]{"Calm","Balanced","Expressive"},
                settings.motionIntensity.getOptions());
        assertEquals(me.youngjibbit.theme.ThemeMode.LEGACY,
                me.youngjibbit.theme.ThemeMode.LEGACY);
    }
}
