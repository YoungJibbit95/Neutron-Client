package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** Production settings and ConfigManager persistence for the independent pre-aim controls. */
public class AuraPreAimConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ConfigManager manager(Path directory, Aura aura) {
        return new ConfigManager(directory, Collections.<Module>singletonList(aura), () -> false, message -> { });
    }

    private static SliderValue range(Aura aura) { return aura.getNumberSetting("pre_aim_range"); }
    private static SliderValue prediction(Aura aura) { return aura.getNumberSetting("pre_aim_prediction"); }

    private static String modern(String settings) {
        return "{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{" + settings + "}}}}";
    }

    private static void write(Path directory, String content) throws Exception {
        Files.write(directory.resolve("Fixture.json"), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void defaultsUnitsBoundsAndVisibilityDescribeIndependentPreparation() {
        Aura aura = new Aura();
        assertNotNull(range(aura));
        assertNotNull(prediction(aura));
        assertEquals("pre_aim_range", range(aura).getId());
        assertEquals("pre_aim_prediction", prediction(aura).getId());
        assertSame(range(aura), aura.getNumberSetting("Pre Aim Range"));
        assertSame(range(aura), aura.getNumberSetting("Pre Aim Range (blocks)"));
        assertSame(prediction(aura), aura.getNumberSetting("Pre Aim Prediction"));
        assertSame(prediction(aura), aura.getNumberSetting("Pre Aim Prediction (ticks)"));
        assertEquals("Pre Aim Range (blocks)", range(aura).getName());
        assertEquals("Pre Aim Prediction (ticks)", prediction(aura).getName());
        assertEquals(3, range(aura).getValue(), 0);
        assertEquals(0, prediction(aura).getValue(), 0);
        assertEquals(1, range(aura).getMin(), 0);
        assertEquals(9, range(aura).getMax(), 0);
        assertEquals(0, prediction(aura).getMin(), 0);
        assertEquals(3, prediction(aura).getMax(), 0);
        assertFalse(range(aura).isInteger());
        assertFalse(prediction(aura).isInteger());
        assertTrue(range(aura).isVisible());
        assertFalse("no preparation band exists at the default attack range", prediction(aura).isVisible());
        assertFalse("the old margin remains an import alias, not a competing slider",
                aura.getValueByName("Aim Range Margin").isVisible());

        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        range(aura).setValue(5.4);
        prediction(aura).setValue(1.7);
        assertTrue(prediction(aura).isVisible());
        assertEquals(5.4, range(aura).getValue(), 0);
        assertEquals(1.7, prediction(aura).getValue(), 0);
        aura.getBooleanSetting("Rotations").setValue(false);
        assertFalse(range(aura).isVisible());
        assertFalse(prediction(aura).isVisible());
        aura.getBooleanSetting("Rotations").setValue(true);
        aura.getNumberSetting("Reach").setValue(6D);
        assertTrue(range(aura).isVisible());
        assertFalse(prediction(aura).isVisible());
        aura.getNumberSetting("Reach").setValue(3D);
        aura.getModeSetting("Rot Mode").setValue("AI Learned");
        assertTrue(range(aura).isVisible());
        assertFalse("the learned-model path does not consume ordinary pre-aim prediction", prediction(aura).isVisible());
    }

    @Test public void bothValuesRoundTripWithAdaptiveAndEverySelectedPointFamily() throws Exception {
        for (String aimPoint : new String[]{"Straight", "Head", "Legacy", "Neutron Noise"}) {
            Aura aura = new Aura();
            Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(directory, aura);
            aura.getModeSetting("Rot Mode").setValue("Adaptive");
            aura.getModeSetting("Adaptive Aim Point").setValue(aimPoint);
            aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
            aura.getModeSetting("Adaptive Legacy Hitpoint Algorithm").setValue("ValueCubic");
            range(aura).setValue(5.4);
            prediction(aura).setValue(1.7);
            assertTrue(config.getLastError(), config.saveConfig("Saved"));
            JsonObject settings = new JsonParser().parse(config.serialize()).getAsJsonObject()
                    .getAsJsonObject("modules").getAsJsonObject("aura").getAsJsonObject("settings");
            assertEquals(5.4, settings.get("pre_aim_range").getAsDouble(), 0);
            assertEquals(1.7, settings.get("pre_aim_prediction").getAsDouble(), 0);
            range(aura).setValue(3D);
            prediction(aura).setValue(0D);
            aura.getModeSetting("Adaptive Aim Point").setValue("Torso");
            assertTrue(config.getLastError(), config.loadConfig("Saved"));
            assertEquals(5.4, range(aura).getValue(), 0);
            assertEquals(1.7, prediction(aura).getValue(), 0);
            assertEquals("Adaptive", aura.getModeSetting("Rot Mode").getValue());
            assertEquals(aimPoint, aura.getModeSetting("Adaptive Aim Point").getValue());
            assertEquals("Custom", aura.getModeSetting("Adaptive Legacy Hitpoint Profile").getValue());
            assertEquals("ValueCubic", aura.getModeSetting("Adaptive Legacy Hitpoint Algorithm").getValue());
        }
    }

    @Test public void missingNewKeysMigrateFinalLegacyRangeRegardlessOfOrderAndWarmState() throws Exception {
        String[] inputs = {
                modern("\"reach\":3.6,\"aim_range_margin\":1.1"),
                modern("\"aim_range_margin\":1.1,\"reach\":3.6"),
                "{\"Aura\":{\"Reach\":3.6,\"Aim Range Margin\":1.1}}",
                "{\"Aura\":{\"Aim Range Margin\":1.1,\"Reach\":3.6}}"
        };
        for (String json : inputs) {
            Aura aura = new Aura();
            aura.getNumberSetting("Reach").setValue(6D);
            aura.getNumberSetting("Aim Range Margin").setValue(3D);
            range(aura).setValue(8D);
            prediction(aura).setValue(3D);
            Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(directory, aura);
            write(directory, json);
            assertTrue(config.getLastError(), config.loadConfig("Fixture"));
            assertEquals(json, 3.6, aura.getNumberSetting("Reach").getValue(), 1e-10);
            assertEquals(json, 4.7, range(aura).getValue(), 1e-10);
            assertEquals(json, 0, prediction(aura).getValue(), 0);
            assertEquals(json, new String(Files.readAllBytes(directory.resolve("Fixture.json")), StandardCharsets.UTF_8));
        }
    }

    @Test public void explicitPreAimRangeWinsOverLegacyMarginInEitherMemberOrder() throws Exception {
        for (String entries : new String[]{
                "\"pre_aim_range\":5.2,\"pre_aim_prediction\":1.4,\"aim_range_margin\":1.8,\"reach\":3.8",
                "\"reach\":3.8,\"aim_range_margin\":1.8,\"pre_aim_prediction\":1.4,\"pre_aim_range\":5.2"}) {
            Aura aura = new Aura();
            Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(directory, aura);
            write(directory, modern(entries));
            assertTrue(config.getLastError(), config.loadConfig("Fixture"));
            assertEquals(3.8, aura.getNumberSetting("Reach").getValue(), 0);
            assertEquals("the explicit control must not be replaced with Reach + Margin", 5.2, range(aura).getValue(), 0);
            assertEquals(1.4, prediction(aura).getValue(), 0);
        }
    }

    @Test public void omittedPredictionAndMarginDoNotInheritThePreviousProfile() throws Exception {
        Aura aura = new Aura();
        Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(directory, aura);
        aura.getNumberSetting("Aim Range Margin").setValue(3D);
        prediction(aura).setValue(2.7);
        write(directory, modern("\"reach\":4.2"));
        assertTrue(config.getLastError(), config.loadConfig("Fixture"));
        assertEquals(4.2, range(aura).getValue(), 0);
        assertEquals(0, prediction(aura).getValue(), 0);
        prediction(aura).setValue(2.7);
        write(directory, modern("\"reach\":3,\"pre_aim_range\":6.3"));
        assertTrue(config.getLastError(), config.loadConfig("Fixture"));
        assertEquals(6.3, range(aura).getValue(), 0);
        assertEquals(0, prediction(aura).getValue(), 0);
    }

    @Test public void directLegacyMarginUpdatesPreparationWithoutChangingAttackReach() {
        Aura aura = new Aura();
        aura.getNumberSetting("Reach").setValue(4.4);
        aura.getNumberSetting("Aim Range Margin").setValue(1.5);
        assertEquals(5.9, range(aura).getValue(), 1e-10);
        range(aura).setValue(7D);
        aura.getNumberSetting("Aim Range Margin").setValue(.8);
        assertEquals(5.2, range(aura).getValue(), 1e-10);
        assertEquals(4.4, aura.getNumberSetting("Reach").getValue(), 0);
    }

    @Test public void invalidNewValuesCannotReplaceASavedConfigOrChangeTheLiveValues() throws Exception {
        Aura aura = new Aura();
        Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(directory, aura);
        range(aura).setValue(5.5);
        prediction(aura).setValue(1.2);
        String before = config.serialize();
        assertTrue(config.getLastError(), config.saveConfig("Fixture"));
        String saved = new String(Files.readAllBytes(directory.resolve("Fixture.json")), StandardCharsets.UTF_8);
        for (String invalid : new String[]{"\"pre_aim_range\":0.9", "\"pre_aim_range\":9.1",
                "\"pre_aim_prediction\":-0.1", "\"pre_aim_prediction\":3.1"}) {
            String json = modern("\"reach\":4," + invalid);
            assertFalse(config.saveConfig("Fixture", json));
            assertEquals(before, config.serialize());
            assertEquals(saved, new String(Files.readAllBytes(directory.resolve("Fixture.json")), StandardCharsets.UTF_8));
        }
    }
}
