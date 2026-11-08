package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.NeutronTwoGui;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.impl.ComboValue;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Real ClickGUI Module, Value and ConfigManager; no native display or framebuffer. */
public class ClickGuiNeutronTwoModeTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private ModuleManager previous;
    private String previousTheme;
    private ClientSettings settings;
    private ClickGUI owner;

    @Before public void setup() throws Exception {
        previous=Pulsar.INSTANCE.getMm();
        previousTheme=ThemeMode.selected();
        settings=new ClientSettings();
        owner=new ClickGUI();
        Field mm=Pulsar.class.getDeclaredField("mm");
        mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(settings,owner)));
        ThemeMode.select(ThemeMode.LEGACY);
    }
    @After public void restore() throws Exception {
        Field mm=Pulsar.class.getDeclaredField("mm");
        mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,previous);
        ThemeMode.select(previousTheme);
    }

    private static GuiScreen route(ClickGUI owner,String value) throws Exception {
        Method method=ClickGUI.class.getDeclaredMethod("resolveScreen",String.class);
        method.setAccessible(true);
        return (GuiScreen)method.invoke(owner,value);
    }

    @Test public void onlyPanelAndDropdownAreAvailableInLegacy() throws Exception {
        ComboValue mode=owner.getModeSetting("Mode");
        assertArrayEquals(new String[]{"Panel","Dropdown"},mode.getOptions());
        assertEquals("Panel",mode.getValue());
        assertEquals(0,mode.getSelectedIndex());
        mode.setValue("Dropdown");
        assertEquals(1,mode.getSelectedIndex());
        assertSame(Pulsar.INSTANCE.getDropdown(),route(owner,mode.getValue()));
        mode.setValue("iOS");
        assertEquals("Panel",mode.getValue());
        assertEquals(0,mode.getSelectedIndex());
        assertSame(Pulsar.INSTANCE.getPanel(),route(owner,"iOS"));
        assertFalse(route(owner,"iOS") instanceof NeutronTwoGui);
    }

    @Test public void themeTwoRoutesToItsOwnCachedScreenWithoutChangingLegacySetting() throws Exception {
        ComboValue mode=owner.getModeSetting("Mode");
        mode.setValue("Dropdown");
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        GuiScreen first=route(owner,mode.getValue());
        GuiScreen again=route(owner,"Panel");
        assertTrue(first instanceof NeutronTwoGui);
        assertSame(first,again);
        assertEquals("Dropdown",mode.getValue());
        ThemeMode.select(ThemeMode.LEGACY);
        assertSame(Pulsar.INSTANCE.getDropdown(),route(owner,mode.getValue()));
        assertEquals("Dropdown",mode.getValue());
    }

    @Test public void realConfigRoundTripPreservesDropdownNameAndDefaults() throws Exception {
        Path folder=temp.newFolder().toPath();
        ConfigManager writer=new ConfigManager(folder,Arrays.asList(settings,owner),
                ()->false,ignored->{});
        writer.initialize(Collections.emptyList());
        owner.getModeSetting("Mode").setValue("Dropdown");
        assertTrue(writer.saveDefaultConfig());
        writer.shutdown();

        ClientSettings reloadedSettings=new ClientSettings();
        ClickGUI reloadedGui=new ClickGUI();
        ConfigManager reader=new ConfigManager(folder,
                Arrays.asList(reloadedSettings,reloadedGui),()->false,ignored->{});
        reader.initialize(Collections.emptyList());
        assertArrayEquals(new String[]{"Panel","Dropdown"},
                reloadedGui.getModeSetting("Mode").getOptions());
        assertEquals("Dropdown",reloadedGui.getModeSetting("Mode").getValue());
        assertEquals(1,reloadedGui.getModeSetting("Mode").getSelectedIndex());
        assertEquals("Blur",reloadedSettings.surface.getValue());
        assertEquals("Balanced",reloadedSettings.motionIntensity.getValue());
        reader.shutdown();
    }
}
