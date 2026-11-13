package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.BodyRotationController;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual Aura/Scaffold values and real config parser; only temporary synthetic config files. */
public class MoveFixConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final Aura aura=new Aura();
    private final Scaffold scaffold=new Scaffold();
    private Path dir;
    private ConfigManager configs;
    @Before public void setup() throws Exception {
        dir=temporary.newFolder().toPath();
        configs=new ConfigManager(dir,Arrays.<Module>asList(aura,scaffold),()->false,message->{});
    }
    private void write(String content) throws Exception { Files.write(dir.resolve("Synthetic.json"),content.getBytes(StandardCharsets.UTF_8)); }
    private JsonObject root() { return JsonParser.parseString(configs.serialize()).getAsJsonObject(); }
    private JsonObject settings(JsonObject root,String module) { return root.getAsJsonObject("modules").getAsJsonObject(module).getAsJsonObject("settings"); }

    @Test public void idsAndDefaultsAreStableAndBothModulesHaveIndependentSelectors() {
        assertEquals("movefix",aura.getValueByName("Movefix").getId());
        assertEquals(MoveFixController.Mode.OFF,aura.moveFixMode());assertEquals(MoveFixController.Mode.OFF,scaffold.moveFixMode());
        assertEquals(BodyRotationController.Mode.VANILLA,aura.bodyRotationMode());assertEquals(BodyRotationController.Mode.VANILLA,scaffold.bodyRotationMode());
        aura.getModeSetting("movefix").setValue("Präzise");aura.getModeSetting("body_rotation").setValue("Natürlich");
        assertEquals(MoveFixController.Mode.OFF,scaffold.moveFixMode());assertEquals(BodyRotationController.Mode.VANILLA,scaffold.bodyRotationMode());
    }

    @Test public void oldAuraBooleansInCurrentFormatMapToOffAndSimpleOnlyForAura() throws Exception {
        for(boolean value:new boolean[]{true,false}) {
            JsonObject root=root();settings(root,"aura").addProperty("movefix",value);write(root.toString());
            assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));
            assertEquals(value?MoveFixController.Mode.SIMPLE:MoveFixController.Mode.OFF,aura.moveFixMode());
            assertEquals(BodyRotationController.Mode.VANILLA,aura.bodyRotationMode());
        }
        JsonObject root=root();settings(root,"scaffold").addProperty("movefix",true);write(root.toString());
        assertTrue(configs.loadConfig("Synthetic"));
        assertEquals(MoveFixController.Mode.OFF, scaffold.moveFixMode());
    }

    @Test public void legacyNamedArrayFlatAndWrappedBooleanFormatsMigrateWithoutChangingIds() throws Exception {
        for(String legacy:Arrays.asList(
                "{\"Aura\":{\"Movefix\":true}}",
                "{\"Aura\":{\"settings\":[{\"name\":\"Movefix\",\"value\":true}]}}",
                "{\"version\":2,\"modules\":{\"Aura\":{\"settings\":{\"Movefix\":{\"value\":true}}}}}")) {
            aura.getModeSetting("movefix").setValue("Aus");write(legacy);assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));
            assertEquals(MoveFixController.Mode.SIMPLE,aura.moveFixMode());
        }
    }

    @Test public void allNewMovementAndBodyChoicesRoundTripWithIndependentModuleValues() throws Exception {
        for(MoveFixController.Mode movement:MoveFixController.Mode.values()) for(BodyRotationController.Mode body:BodyRotationController.Mode.values()) {
            aura.getModeSetting("movefix").setValue(movement.label);aura.getModeSetting("body_rotation").setValue(body.label);
            scaffold.getModeSetting("movefix").setValue("Präzise");scaffold.getModeSetting("body_rotation").setValue("Direkt");
            assertTrue(configs.saveConfig("Synthetic"));
            aura.getModeSetting("movefix").setValue("Aus");aura.getModeSetting("body_rotation").setValue("Vanilla");
            scaffold.getModeSetting("movefix").setValue("Aus");scaffold.getModeSetting("body_rotation").setValue("Vanilla");
            assertTrue(configs.loadConfig("Synthetic"));
            assertEquals(movement,aura.moveFixMode());assertEquals(body,aura.bodyRotationMode());
            assertEquals(MoveFixController.Mode.PRECISE,scaffold.moveFixMode());assertEquals(BodyRotationController.Mode.DIRECT,scaffold.bodyRotationMode());
            assertTrue(settings(root(),"aura").get("movefix").getAsJsonPrimitive().isString());
        }
    }

    @Test public void nextAutomaticSaveWritesCanonicalStringsAndKeepsSeparateBodyDefaults() throws Exception {
        JsonObject root=root();settings(root,"aura").addProperty("movefix",true);
        Files.write(configs.defaultConfig.toPath(),root.toString().getBytes(StandardCharsets.UTF_8));
        configs.initialize(Collections.emptyList());
        assertEquals(MoveFixController.Mode.SIMPLE,aura.moveFixMode());
        aura.setKey(aura.getKey()+1); // Trigger a normal save; loading alone must not rewrite the file.
        configs.shutdown();
        JsonObject saved=JsonParser.parseString(new String(Files.readAllBytes(configs.defaultConfig.toPath()),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Einfach",settings(saved,"aura").get("movefix").getAsString());
        assertEquals("Vanilla",settings(saved,"aura").get("body_rotation").getAsString());
        assertEquals("Aus",settings(saved,"scaffold").get("movefix").getAsString());
    }

    @Test public void invalidNewChoicesUseTheDeclaredDefaultAndKeepCompatibleBodyValues() throws Exception {
        for(JsonElement invalid:Arrays.asList(new JsonPrimitive("unexpected"),new JsonPrimitive(1),JsonNull.INSTANCE)) {
            JsonObject root=root();settings(root,"aura").addProperty("body_rotation","Direkt");settings(root,"aura").add("movefix",invalid);
            write(root.toString());assertTrue(configs.loadConfig("Synthetic"));
            assertEquals(MoveFixController.Mode.OFF,aura.moveFixMode());
            assertEquals(BodyRotationController.Mode.DIRECT,aura.bodyRotationMode());
        }
    }

    /** W09-T1b: layman presets map 1:1 onto the reviewed legacy Movefix values. */
    @Test public void friendlyMovementCorrectionPresetsMapOntoVerifiedLegacyValues() {
        assertEquals(MoveFixController.Mode.OFF,scaffold.moveFixMode());
        assertEquals("Off",scaffold.getModeSetting("movement_correction").getValue());
        scaffold.getModeSetting("movement_correction").setValue("Balanced");
        assertEquals(MoveFixController.Mode.SIMPLE,scaffold.moveFixMode());
        scaffold.getModeSetting("movement_correction").setValue("Precise");
        assertEquals(MoveFixController.Mode.PRECISE,scaffold.moveFixMode());
        scaffold.getModeSetting("movement_correction").setValue("Off");
        assertEquals(MoveFixController.Mode.OFF,scaffold.moveFixMode());
    }

    /** Custom exposes all legacy options and never rewrites a chosen legacy value. */
    @Test public void customPresetKeepsEveryLegacyMovefixValueWithoutRewritingIt() {
        for(MoveFixController.Mode legacy:Arrays.asList(MoveFixController.Mode.ROTATION_BOUND,
                MoveFixController.Mode.LEGACY_STRICT,MoveFixController.Mode.LEGACY_ROUNDED)) {
            scaffold.getModeSetting("movefix").setValue(legacy.label);
            assertEquals("Custom",scaffold.getModeSetting("movement_correction").getValue());
            assertEquals(legacy,scaffold.moveFixMode());
        }
        // A preset choice rebinds the legacy combo from the Custom state.
        scaffold.getModeSetting("movement_correction").setValue("Balanced");
        assertEquals(MoveFixController.Mode.SIMPLE,scaffold.moveFixMode());
        assertEquals("Balanced",scaffold.getModeSetting("movement_correction").getValue());
    }

    /** Real saves/loads: legacy writes land on Custom, presets rebind the combo, nothing is lost. */
    @Test public void configRoundTripPreservesFriendlyAndLegacyMovefixTogether() throws Exception {
        scaffold.getModeSetting("movefix").setValue(MoveFixController.Mode.LEGACY_ROUNDED.label);
        assertTrue(configs.saveConfig("Synthetic"));
        scaffold.getModeSetting("movefix").setValue("Aus");
        assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));
        assertEquals(MoveFixController.Mode.LEGACY_ROUNDED,scaffold.moveFixMode());
        assertEquals("Custom",scaffold.getModeSetting("movement_correction").getValue());
        JsonObject saved=root();
        assertEquals("Legacy Rounded",settings(saved,"scaffold").get("movefix").getAsString());
        assertEquals("Custom",settings(saved,"scaffold").get("movement_correction").getAsString());
        // A legacy config without the friendly key still lands on Custom, never on Off.
        JsonObject legacy=root();settings(legacy,"scaffold").remove("movement_correction");
        write(legacy.toString());
        assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));
        assertEquals(MoveFixController.Mode.LEGACY_ROUNDED,scaffold.moveFixMode());
        assertEquals("Custom",scaffold.getModeSetting("movement_correction").getValue());
    }

    /** A preset flips the underlying combo in the saved truth and rebinds on load. */
    @Test public void friendlyPresetFlipsTheLegacyComboThroughTheRoundTrip() throws Exception {
        scaffold.getModeSetting("movement_correction").setValue("Precise");
        JsonObject saved=root();
        assertEquals("Präzise",settings(saved,"scaffold").get("movefix").getAsString());
        assertEquals("Precise",settings(saved,"scaffold").get("movement_correction").getAsString());
        assertTrue(configs.saveConfig("Synthetic"));
        scaffold.getModeSetting("movement_correction").setValue("Off");
        assertEquals(MoveFixController.Mode.OFF,scaffold.moveFixMode());
        assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));
        assertEquals(MoveFixController.Mode.PRECISE,scaffold.moveFixMode());
        assertEquals("Precise",scaffold.getModeSetting("movement_correction").getValue());
    }
    /** Partial historical profiles must not turn an unrelated movement correction off. */
    @Test public void partialLegacyConfigsPreserveEveryUnspecifiedMovefixValue() throws Exception {
        for (String partial : Arrays.asList(
                "{\"Scaffold\":{\"key\":42}}",
                "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{\"place_delay\":4}}}}")) {
            for (MoveFixController.Mode movement : MoveFixController.Mode.values()) {
                scaffold.getModeSetting("movefix").setValue(movement.label);
                write(partial);
                assertTrue(configs.getLastError(), configs.loadConfig("Synthetic"));
                assertEquals("omitted movement keys must preserve " + movement.label,
                        movement, scaffold.moveFixMode());
                assertEquals(movement.label, scaffold.getModeSetting("movefix").getValue());
            }
        }
    }

}
