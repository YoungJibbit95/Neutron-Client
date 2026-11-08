package me.youngjibbit.module.impl.client;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.theme.ThemeMode;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real saved Mode names and functional HUD controls, independent of presentation springs. */
public class Wave07CompactHudConfigTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @After public void legacy(){ThemeMode.select(ThemeMode.LEGACY);}

    @Test public void legacySavedModesAndPositionsSurviveThemeSwitchAndRoundtrip()throws Exception{
        Path home=folder.newFolder().toPath();
        ClientSettings originalSettings=new ClientSettings();
        WatermarkHUDModule watermark=new WatermarkHUDModule();
        ArrayListHUDModule list=new ArrayListHUDModule();
        ConfigManager writer=new ConfigManager(home,Arrays.asList(originalSettings,watermark,list),
                ()->false,ignored->{});
        writer.initialize(Collections.emptyList());
        watermark.getModeSetting("Mode").setValue("Status");
        list.getModeSetting("Mode").setValue("Compact");
        list.getNumberSetting("Position X").setValue(87d);
        list.getNumberSetting("Position Y").setValue(140d);
        list.getBooleanSetting("Custom Container Padding").setValue(true);
        list.getNumberSetting("Row Spacing").setValue(13d);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("Status",watermark.getModeSetting("Mode").getValue());
        assertEquals("Compact",list.getModeSetting("Mode").getValue());
        assertTrue(writer.saveDefaultConfig());writer.shutdown();

        WatermarkHUDModule restoredWatermark=new WatermarkHUDModule();
        ArrayListHUDModule restoredList=new ArrayListHUDModule();
        ConfigManager reader=new ConfigManager(home,Arrays.asList(
                new ClientSettings(),restoredWatermark,restoredList),()->false,ignored->{});
        reader.initialize(Collections.emptyList());
        assertEquals("Status",restoredWatermark.getModeSetting("Mode").getValue());
        assertEquals("Compact",restoredList.getModeSetting("Mode").getValue());
        assertEquals(87d,restoredList.getNumberSetting("Position X").getValue(),0);
        assertEquals(140d,restoredList.getNumberSetting("Position Y").getValue(),0);
        assertTrue(restoredList.getBooleanSetting("Custom Container Padding").getValue());
        assertEquals(13d,restoredList.getNumberSetting("Row Spacing").getValue(),0);
        reader.shutdown();
    }
    @Test public void originalHudDefaultsRemainStable(){
        WatermarkHUDModule watermark=new WatermarkHUDModule();
        ArrayListHUDModule list=new ArrayListHUDModule();
        assertEquals("Glass",watermark.getModeSetting("Mode").getValue());
        assertEquals(1,watermark.getModeSetting("Mode").getSelectedIndex());
        assertEquals("Glass",list.getModeSetting("Mode").getValue());
        assertEquals(0,list.getModeSetting("Mode").getSelectedIndex());
        assertEquals(10d,list.getNumberSetting("Position X").getValue(),0);
        assertEquals(20d,list.getNumberSetting("Position Y").getValue(),0);
    }
}
