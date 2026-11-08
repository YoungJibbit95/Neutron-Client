package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** Same real config codec; the motion layer neither persists nor rewrites Values. */
public class SettingsMenuMotionConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void interruptedUiAnimationsDoNotAffectPersistentSettingsRoundTrip() throws Exception {
        Path folder=temporary.newFolder().toPath();
        ClientSettings values=new ClientSettings();
        ConfigManager manager=new ConfigManager(folder,Collections.singletonList(values),
                ()->false, ignored->{});
        manager.initialize(Collections.emptyList());

        values.roundness.setValue(12.5);
        values.surface.setValue("Liquid Glass");
        values.motionIntensity.setValue("Expressive");
        values.blur.setValue(false);
        values.reducedMotion.setValue(true);

        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();
        motion.category(30,0,values);
        for(int i=0;i<30;i++){
            motion.category(i%2==0?180:30,.016f,values);
            motion.popup(i%2==0);
            motion.popup(.016f,values);
            motion.cursor(i%2==0?90:20,.016f,values);
        }
        motion.reset();
        assertEquals(12.5,values.roundness.getValue(),0);
        assertEquals("Liquid Glass",values.surface.getValue());
        assertEquals("Expressive",values.motionIntensity.getValue());
        assertFalse(values.blur.getValue());
        assertTrue(values.reducedMotion.getValue());

        assertTrue(manager.saveDefaultConfig());
        manager.shutdown();

        ClientSettings restored=new ClientSettings();
        ConfigManager reloaded=new ConfigManager(folder,Collections.singletonList(restored),
                ()->false,ignored->{});
        reloaded.initialize(Collections.emptyList());
        assertEquals(12.5,restored.roundness.getValue(),0);
        assertEquals("Liquid Glass",restored.surface.getValue());
        assertEquals("Expressive",restored.motionIntensity.getValue());
        assertFalse(restored.blur.getValue());
        assertTrue(restored.reducedMotion.getValue());
        reloaded.shutdown();
    }

    @Test public void legacyDefaultSettingsAreNotAlteredByMotionInitialization() {
        ClientSettings values=new ClientSettings();
        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();
        motion.popup(true);
        motion.popup(.1f,values);
        motion.category(160,.1f,values);
        assertEquals(5.0,values.roundness.getValue(),0);
        assertEquals("Blur",values.surface.getValue());
        assertEquals("Balanced",values.motionIntensity.getValue());
        assertTrue(values.animations.getValue());
        assertFalse(values.reducedMotion.getValue());
    }
}
