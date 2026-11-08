package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.component.impl.DropdownModule;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class DropdownMotionConfigTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();

    @Test public void togglesAndReversalsCannotPersistAnimationState() throws Exception {
        String prior=ThemeMode.selected();
        try {
            ThemeMode.select(ThemeMode.APPROACH_TWO);
            Path path=folder.newFolder().toPath();
            ClientSettings settings=new ClientSettings();
            DropdownControlMotionTest.Dummy owner=new DropdownControlMotionTest.Dummy();
            ConfigManager writer=new ConfigManager(path,Arrays.asList(settings,owner),
                    ()->false,ignored->{});
            writer.initialize(Collections.emptyList());
            settings.motionIntensity.setValue("Expressive");
            settings.surface.setValue("Liquid Glass");
            owner.check.setValue(true);
            owner.combo.setValue("C");
            owner.slider.setValue(6.75);
            int callbacks=owner.changes;
            DropdownModule row=new DropdownModule(owner);
            DropdownMotion column=new DropdownMotion();
            row.setOpen(true);
            for(int i=0;i<90;i++){
                row.setOpen(i%2==0);
                row.updateCompactMotion(.009f);
                column.step(i%2==0,.009f,settings);
            }
            row.cancelInputs();row.resetCompactMotion();column.reset(true);
            assertEquals(callbacks,owner.changes);
            assertTrue(owner.check.getValue());
            assertEquals("C",owner.combo.getValue());
            assertEquals(6.75,owner.slider.getValue(),0);
            assertTrue(writer.saveDefaultConfig());
            writer.shutdown();

            ClientSettings restoredSettings=new ClientSettings();
            DropdownControlMotionTest.Dummy restored=new DropdownControlMotionTest.Dummy();
            ConfigManager reader=new ConfigManager(path,Arrays.asList(restoredSettings,restored),
                    ()->false,ignored->{});
            reader.initialize(Collections.emptyList());
            assertTrue(restored.check.getValue());
            assertEquals("C",restored.combo.getValue());
            assertEquals(6.75,restored.slider.getValue(),0);
            assertEquals("Expressive",restoredSettings.motionIntensity.getValue());
            assertEquals("Liquid Glass",restoredSettings.surface.getValue());
            reader.shutdown();
        }finally{ThemeMode.select(prior);}
    }

    @Test public void originalDefaultsRemainAndCompactSpringIsNotARegisteredValue() {
        ClientSettings settings=new ClientSettings();
        DropdownControlMotionTest.Dummy owner=new DropdownControlMotionTest.Dummy();
        int count=owner.getValues().size();
        DropdownModule row=new DropdownModule(owner);
        row.updateCompactMotion(.05f);
        assertEquals(count,owner.getValues().size());
        assertEquals(5,settings.roundness.getValue(),0);
        assertEquals("Blur",settings.surface.getValue());
        assertEquals("Balanced",settings.motionIntensity.getValue());
        assertTrue(settings.animations.getValue());
        assertFalse(settings.reducedMotion.getValue());
        assertEquals("A",owner.combo.getValue());
        assertEquals(5,owner.slider.getValue(),0);
        assertFalse(owner.check.getValue());
    }
}
