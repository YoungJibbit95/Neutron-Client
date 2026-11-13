package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.FakeLag;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.DemiseSliderValue;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.Assert.*;

public class FakeLagDemiseConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Object[][] DEFAULTS = {
            {"Mode", "Pulse"}, {"Smart", true}, {"HurtTime to register attack", 3D}, {"Early release", false},
            {"Attack range", 4D}, {"Always spoof", false}, {"Search range", 6D}, {"Recoil time (ms)", 50D},
            {"Delay (min ms)", 100D}, {"Delay (max ms)", 100D}, {"Delay on opponent attack", 200D},
            {"Keep range mode", "None"}, {"Timer", .75D}, {"Timer ticks", 1D},
            {"Experimental dynamic hurt time", false}, {"HurtTime to stop (>)", 0D},
            {"Pause on backtrack", false}, {"Force first hit", false}, {"Display real pos", true},
            {"Render mode", "FakePlayer"}, {"Only onGround", false}, {"Only on killAura", false}, {"Team check", false}
    };
    private FakeLag module() { return new FakeLag(() -> 0, null, (a, b) -> a); }
    private Map<String, Object> values(FakeLag module) {
        Map<String, Object> values = new LinkedHashMap<>(); for (Value<?> value : module.getValues()) values.put(value.getName(), value.getValue()); return values;
    }

    @Test public void allTwentyThreeOriginalSettingsAndOnlyTwoHostControlsHaveExactDefaultsAndOrder() {
        FakeLag module = module(); Map<String, Object> expected = new LinkedHashMap<>();
        for (Object[] entry : DEFAULTS) expected.put((String) entry[0], entry[1]);
        expected.put("Stop on Damage", false); expected.put("Exclusive Timing", false);
        assertEquals(expected, values(module)); assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(values(module).keySet()));
        assertEquals(25, module.getValues().size());
        assertArrayEquals(new String[]{"Pulse", "Spoof"}, module.getModeSetting("Mode").getOptions());
        assertArrayEquals(new String[]{"None", "WTap", "Timer down"}, module.getModeSetting("Keep range mode").getOptions());
        assertArrayEquals(new String[]{"Box", "FakePlayer", "Line"}, module.getModeSetting("Render mode").getOptions());
        Object[][] sliders = {
                {"HurtTime to register attack", 0D, 10D, 1F}, {"Attack range", 0D, 15D, .1F},
                {"Search range", 1D, 15D, .1F}, {"Recoil time (ms)", 0D, 1000D, 1F},
                {"Delay (min ms)", 0D, 1000D, 1F}, {"Delay (max ms)", 0D, 1000D, 1F},
                {"Delay on opponent attack", 0D, 500D, 1F}, {"Timer", .01D, 1D, .01F},
                {"Timer ticks", 1D, 10D, 1F}, {"HurtTime to stop (>)", 0D, 10D, 1F}
        };
        for (Object[] row : sliders) {
            DemiseSliderValue slider = (DemiseSliderValue) module.getNumberSetting((String) row[0]);
            assertEquals((Double) row[1], slider.getMin(), 0); assertEquals((Double) row[2], slider.getMax(), 0);
            assertEquals((Float) row[3], slider.getIncrement(), 0);
        }
    }

    @Test public void visibilityRetainsHiddenValuesAndTheSourceTimerVisibilityRule() {
        FakeLag m = module(); assertFalse(m.getNumberSetting("Attack range").isVisible());
        assertTrue(m.getBooleanSetting("Early release").isVisible()); assertFalse(m.getNumberSetting("Delay on opponent attack").isVisible());
        m.getModeSetting("Mode").setValue("Spoof"); assertTrue(m.getNumberSetting("Delay on opponent attack").isVisible());
        m.getModeSetting("Keep range mode").setValue("Timer down"); m.getBooleanSetting("Smart").setValue(false);
        assertTrue("source depends on Keep range only", m.getNumberSetting("Timer").isVisible());
        assertTrue(m.getNumberSetting("Timer ticks").isVisible()); assertFalse(m.getModeSetting("Keep range mode").isVisible());
        assertTrue(m.getNumberSetting("Attack range").isVisible());
        assertFalse(m.getNumberSetting("HurtTime to register attack").isVisible());
        assertFalse(m.getBooleanSetting("Experimental dynamic hurt time").isVisible());
        m.getBooleanSetting("Always spoof").setValue(true); assertFalse(m.getNumberSetting("Search range").isVisible());
        m.getBooleanSetting("Display real pos").setValue(false); assertFalse(m.getModeSetting("Render mode").isVisible());
        assertEquals("FakePlayer", m.getModeSetting("Render mode").getValue());
    }

    @Test public void allSettingsRoundTripIncludingHiddenFloatsZeroFalseAndCrossedDelayBounds() throws Exception {
        FakeLag module = module();
        ConfigManager config = new ConfigManager(temporary.newFolder().toPath(), Collections.<Module>singletonList(module), () -> false, message -> { });
        for (Value<?> value : module.getValues()) {
            if (value instanceof CheckBoxValue) ((CheckBoxValue) value).setValue(!(Boolean) value.getDefaultValue());
            else if (value instanceof ComboValue) { ComboValue mode = (ComboValue) value; mode.setValue(mode.getOptions()[mode.getOptions().length - 1]); }
            else if (value instanceof SliderValue) ((SliderValue) value).setValue(((SliderValue) value).getMin());
        }
        module.getNumberSetting("Delay (min ms)").setValue(875.125D); module.getNumberSetting("Delay (max ms)").setValue(31.5D);
        module.getNumberSetting("Timer").setValue(.123456789D); module.getNumberSetting("Search range").setValue(12.3456789D);
        Map<String, Object> expected = values(module);
        assertTrue(config.getLastError(), config.saveConfig("FakeLag")); String serialized = config.serialize();
        for (Value<?> value : module.getValues()) value.reset();
        assertTrue(config.getLastError(), config.loadConfig("FakeLag")); assertEquals(expected, values(module));
        assertEquals(serialized, config.serialize()); assertEquals((double) (float) .123456789, module.getNumberSetting("Timer").getValue(), 0);
    }

    @Test public void pinnedSourceBlobsAreExactAndEnumerateEveryOriginalControl() throws Exception {
        String source = source("FakeLag.java", "e6f6a226499b23baedb8fb900706d6687f70bad7");
        int declarations = 0;
        for (String line : source.split("\\n")) if (line.contains("private final") && (line.contains("new ModeValue") || line.contains("new BoolValue") || line.contains("new SliderValue"))) declarations++;
        assertEquals(23, declarations);
        for (Object[] entry : DEFAULTS) assertTrue((String) entry[0], source.contains("\"" + entry[0] + "\""));
        source("BlinkComponent.java", "290593f09e33f8c8aa7d0eb3dc9f362854250d00");
        source("LagUtils.java", "5f5fe6bf613b2cbaddbac6e5424fb2e7c9982e42");
    }
    private String source(String name, String sha) throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/demise-reference/fakelag/" + name + ".txt")) {
            assertNotNull(name, in); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size); bytes = out.toByteArray();
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-1"); digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest(bytes); StringBuilder actual = new StringBuilder(); for (byte b : hash) actual.append(String.format("%02x", b & 255));
        assertEquals(name, sha, actual.toString()); return new String(bytes, StandardCharsets.UTF_8);
    }
}
