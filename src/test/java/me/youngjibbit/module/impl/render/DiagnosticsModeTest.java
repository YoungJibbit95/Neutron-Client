package me.youngjibbit.module.impl.render;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DiagnosticsModeTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void modeRoundTripsThroughConfigAndSharesPositionAndVisibility() throws Exception {
        Diagnostics original = new Diagnostics();
        ComboValue mode = original.getModeSetting("Display Mode");
        assertNotNull("Diagnostics mode must be selectable in normal module settings", mode);
        assertEquals("Text", mode.getValue());
        Path directory = folder.newFolder().toPath();
        ConfigManager configs = new ConfigManager(directory, Collections.singletonList(original), () -> false, s -> {});
        configs.initialize(Collections.emptyList());
        original.getNumberSetting("Network X").setValue(87.5);
        original.getBooleanSetting("Flag Widget").setValue(false);
        mode.setValue("Simple");
        assertTrue(configs.saveConfig("simple-diagnostics"));
        configs.shutdown();
        Diagnostics restored = new Diagnostics();
        ConfigManager reload = new ConfigManager(directory, Collections.singletonList(restored), () -> false, s -> {});
        try {
            reload.initialize(Collections.emptyList());
            assertEquals("Simple", restored.getModeSetting("Display Mode").getValue());
            assertEquals(87.5, restored.widgets[2].x, 0);
            assertFalse(restored.getBooleanSetting("Flag Widget").getValue());
        } finally {
            reload.shutdown();
        }
    }

    @Test public void editorTogglesTheHudModeAndAllFourWidgetsFitWithoutSamples() throws Exception {
        try (RotationBehaviorHookTest.Fixture fixture = new RotationBehaviorHookTest.Fixture()) {
            Diagnostics diagnostics = new Diagnostics();
            ComboValue mode = diagnostics.getModeSetting("Display Mode");
            assertNotNull("Missing editor/HUD display mode", mode);
            DiagnosticsEditor editor = new DiagnosticsEditor(diagnostics);
            fixture.mc.currentScreen = editor;
            List<Diagnostics.Panel> text = diagnostics.panels();
            assertEquals(4, text.size());
            assertFalse(text.get(0).lines.isEmpty());
            net.minecraft.client.gui.GuiButton toggle = new net.minecraft.client.gui.GuiButton(2, 0, 0, "Mode");
            editor.actionPerformed(toggle);
            assertEquals("Simple", mode.getValue());
            assertTrue(toggle.displayString.contains("Simple"));
            List<Diagnostics.Panel> simple = diagnostics.panels();
            assertSame(simple, diagnostics.panels());
            assertEquals(4, simple.size());
            for (int i = 0; i < simple.size(); i++) {
                Diagnostics.Panel panel = simple.get(i);
                assertSame(text.get(i).widget, panel.widget);
                assertTrue(panel.lines.isEmpty());
                diagnostics.layout(panel, 320, 200);
                assertTrue(panel.widget.x + panel.widget.width <= 320);
                assertTrue(panel.widget.y + panel.widget.height <= 200 - DiagnosticsEditor.CONTROL_STRIP);
                assertEquals(0, panel.widget.maxScroll);
            }
            editor.actionPerformed(toggle);
            assertEquals("Text", mode.getValue());
            assertFalse(diagnostics.panels().get(0).lines.isEmpty());
        }
    }
}
