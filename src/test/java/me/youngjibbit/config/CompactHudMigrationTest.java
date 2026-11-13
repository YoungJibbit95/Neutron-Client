package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.impl.render.TargetHUD;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real config parser, transactional import and canonical save; no Minecraft or native paint. */
public class CompactHudMigrationTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void everyHistoricNamedTargetModeLoadsAndResavesAsOneOfFiveDesigns()throws Exception {
        String[][] aliases={{"Neutron Default","Basic"},{"Neutron White","Modern"},{"Neutron Outline","Modern"},
                {"Rise","Rue"},{"Rue (Rise)","Rue"},{"Test","Basic"},{"Neutron 2.0","Modern"},
                {"Rue","Rue"},{"Tenacity","Tenacity"},{"Basic","Basic"},{"Modern","Modern"},{"Creative","Creative"}};
        for(String[] alias:aliases){
            Path dir=temporary.newFolder().toPath();TargetHUD hud=new TargetHUD();
            java.util.List<String> messages=new java.util.ArrayList<>();
            ConfigManager manager=new ConfigManager(dir,Collections.singletonList(hud),()->false,messages::add);
            String content="{\"version\":2,\"modules\":{\"targethud\":{\"settings\":{\"mode\":\""+alias[0]+"\",\"position_x\":83,\"position_y\":97}}}}";
            Files.write(dir.resolve("old.json"),content.getBytes(StandardCharsets.UTF_8));
            assertTrue(alias[0],manager.loadConfig("old"));assertEquals(alias[1],hud.getModeSetting("Mode").getValue());
            String report=String.join(" ",messages);
            assertEquals(alias[0],!alias[0].equals(alias[1]),report.contains("TargetHUD design migrated"));
            assertFalse("HUD-only migration must not claim an AimAssist change",report.contains("Smooth mapped to Legit"));
            assertEquals(83,hud.getNumberSetting("Position X").getValue(),0);
            assertEquals(97,hud.getNumberSetting("Position Y").getValue(),0);
            assertTrue(manager.saveConfig("canonical"));
            JsonObject saved=JsonParser.parseString(new String(Files.readAllBytes(dir.resolve("canonical.json")),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(alias[1],saved.getAsJsonObject("modules").getAsJsonObject("targethud").getAsJsonObject("settings").get("mode").getAsString());
            // Imported source belongs to the user and must remain byte-for-byte intact.
            assertEquals(content,new String(Files.readAllBytes(dir.resolve("old.json")),StandardCharsets.UTF_8));
        }
    }
    @Test public void legacyNamedSettingArraysAndWrappedValuesMigrate()throws Exception{
        String[] samples={"{\"TargetHUD\":{\"settings\":[{\"name\":\"Mode\",\"value\":\"Neutron White\"}]}}",
                "{\"TargetHUD\":{\"Mode\":{\"value\":\"Neutron Outline\"}}}",
                "{\"version\":2,\"modules\":{\"targethud\":{\"settings\":{\"mode\":{\"value\":\"Neutron 2.0\"}}}}}"};
        for(String content:samples){
            Path dir=temporary.newFolder().toPath();TargetHUD hud=new TargetHUD();
            ConfigManager manager=new ConfigManager(dir,Collections.singletonList(hud),()->false,message->{});
            Files.write(dir.resolve("old.json"),content.getBytes(StandardCharsets.UTF_8));
            assertTrue(manager.loadConfig("old"));assertEquals("Modern",hud.getModeSetting("Mode").getValue());
        }
    }
    @Test public void unknownAndNumericChoicesUseTheDeclaredDesignAndKeepCompatiblePositions()throws Exception{
        // The historic parser/serializer stores choice names. Numeric indices have no unambiguous version.
        for(String value:new String[]{"\"Missing design\"","7","{\"value\":7}"}){
            Path dir=temporary.newFolder().toPath();TargetHUD hud=new TargetHUD();
            hud.getModeSetting("Mode").setValue("Tenacity");hud.getNumberSetting("Position X").setValue(61d);
            java.util.List<String> messages=new java.util.ArrayList<>();
            ConfigManager manager=new ConfigManager(dir,Collections.singletonList(hud),()->false,messages::add);
            Files.write(dir.resolve("invalid.json"),("{\"version\":2,\"modules\":{\"targethud\":{\"settings\":{\"position_x\":99,\"mode\":"+value+"}}}}").getBytes(StandardCharsets.UTF_8));
            assertTrue(manager.loadConfig("invalid"));assertEquals(hud.getModeSetting("Mode").getDefaultValue(),hud.getModeSetting("Mode").getValue());
            assertEquals(99,hud.getNumberSetting("Position X").getValue(),0);
            assertTrue(messages.toString().contains("targethud.mode"));assertTrue(messages.toString().contains("declared default"));
        }
    }
}
