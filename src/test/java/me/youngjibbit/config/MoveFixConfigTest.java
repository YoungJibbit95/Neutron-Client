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
        assertFalse(configs.loadConfig("Synthetic"));
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

    @Test public void invalidNewChoicesRejectTheWholeLoadInsteadOfSilentlySelectingOff() throws Exception {
        for(JsonElement invalid:Arrays.asList(new JsonPrimitive("unexpected"),new JsonPrimitive(1),JsonNull.INSTANCE)) {
            JsonObject root=root();settings(root,"aura").addProperty("body_rotation","Direkt");settings(root,"aura").add("movefix",invalid);
            String before=configs.serialize();write(root.toString());assertFalse(configs.loadConfig("Synthetic"));assertEquals(before,configs.serialize());
        }
    }
}
