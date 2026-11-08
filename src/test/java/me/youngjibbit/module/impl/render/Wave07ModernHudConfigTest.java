package me.youngjibbit.module.impl.render;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Actual ConfigManager save/reload: no new cosmetic rendering changes stored modes. */
public class Wave07ModernHudConfigTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private final String mode=ThemeMode.selected();
    @After public void restore(){ThemeMode.select(mode);}

    @Test public void targetArmorAndKeystrokesPositionsAndFunctionalValuesRoundTrip()
            throws Exception {
        Path home=folder.newFolder().toPath();
        TargetHUD target=new TargetHUD();
        ArmorHUD armor=new ArmorHUD();
        Keystrokes keys=new Keystrokes();
        ClientSettings settings=new ClientSettings();
        ConfigManager writer=new ConfigManager(home,
                Arrays.asList(settings,target,armor,keys),()->false,msg->{});
        writer.initialize(Collections.emptyList());
        target.getModeSetting("Mode").setValue("Tenacity");
        target.getNumberSetting("Position X").setValue(152d);
        target.getNumberSetting("Position Y").setValue(200d);
        armor.getModeSetting("Layout").setValue("Horizontal");
        armor.getModeSetting("Durability").setValue("Both");
        armor.getNumberSetting("Position X").setValue(204d);
        armor.getNumberSetting("Position Y").setValue(176d);
        keys.getNumberSetting("Position X").setValue(28d);
        keys.getNumberSetting("Position Y").setValue(39d);
        keys.getBooleanSetting("Mouse Buttons").setValue(false);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertTrue(writer.saveDefaultConfig());writer.shutdown();

        TargetHUD loadedTarget=new TargetHUD();
        ArmorHUD loadedArmor=new ArmorHUD();
        Keystrokes loadedKeys=new Keystrokes();
        ConfigManager reader=new ConfigManager(home,Arrays.asList(
                new ClientSettings(),loadedTarget,loadedArmor,loadedKeys),()->false,msg->{});
        reader.initialize(Collections.emptyList());
        assertEquals("Tenacity",loadedTarget.getModeSetting("Mode").getValue());
        assertEquals(152d,loadedTarget.getNumberSetting("Position X").getValue(),0);
        assertEquals(200d,loadedTarget.getNumberSetting("Position Y").getValue(),0);
        assertEquals("Horizontal",loadedArmor.getModeSetting("Layout").getValue());
        assertEquals("Both",loadedArmor.getModeSetting("Durability").getValue());
        assertEquals(204d,loadedArmor.getNumberSetting("Position X").getValue(),0);
        assertEquals(176d,loadedArmor.getNumberSetting("Position Y").getValue(),0);
        assertEquals(28d,loadedKeys.getNumberSetting("Position X").getValue(),0);
        assertEquals(39d,loadedKeys.getNumberSetting("Position Y").getValue(),0);
        assertFalse(loadedKeys.getBooleanSetting("Mouse Buttons").getValue());
        reader.shutdown();
    }

    @Test public void originalDefaultsRemainAcrossModernThemeSelection(){
        TargetHUD target=new TargetHUD();
        ArmorHUD armor=new ArmorHUD();
        Keystrokes keys=new Keystrokes();
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("Neutron White",target.getModeSetting("Mode").getValue());
        assertEquals(1,target.getModeSetting("Mode").getSelectedIndex());
        assertEquals("Vertical",armor.getModeSetting("Layout").getValue());
        assertEquals("Percent",armor.getModeSetting("Durability").getValue());
        assertTrue(keys.getBooleanSetting("Mouse Buttons").getValue());
        assertEquals(16d,keys.getNumberSetting("Position X").getValue(),0);
        assertEquals(80d,keys.getNumberSetting("Position Y").getValue(),0);
    }
}
