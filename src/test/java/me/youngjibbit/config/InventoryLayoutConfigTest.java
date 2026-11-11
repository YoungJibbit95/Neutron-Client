package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.impl.player.InventoryManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.Assert.*;

/** New inventories start organized; old profiles retain the layout they were built around. */
public class InventoryLayoutConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    private ConfigManager codec(Path directory,InventoryManager inventory) {
        return new ConfigManager(directory,Collections.singletonList(inventory),()->false,message->{});
    }
    private static String v2(String layout) {
        JsonObject settings=new JsonObject();settings.addProperty("maximum_block_stacks",4);
        if(layout!=null)settings.addProperty("layout",layout);
        JsonObject module=new JsonObject();module.add("settings",settings);
        JsonObject modules=new JsonObject();modules.add("invmanager",module);
        JsonObject root=new JsonObject();root.addProperty("version",2);root.add("modules",modules);return root.toString();
    }
    private static void write(Path directory,String name,String content)throws Exception {
        Files.write(directory.resolve(name+".json"),content.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void newModuleAndNewlySerializedProfileUseOrganizedLayout()throws Exception {
        InventoryManager inventory=new InventoryManager();
        assertEquals("Organized",inventory.layout.getValue());
        ConfigManager config=codec(temporary.newFolder().toPath(),inventory);
        JsonObject saved=new JsonParser().parse(config.serialize()).getAsJsonObject().getAsJsonObject("modules")
                .getAsJsonObject("invmanager").getAsJsonObject("settings");
        assertEquals("Organized",saved.get("layout").getAsString());
    }
    @Test public void olderVersionTwoAndLegacyShapesWithoutLayoutRetainLegacyBehavior()throws Exception {
        InventoryManager inventory=new InventoryManager();Path directory=temporary.newFolder().toPath();ConfigManager config=codec(directory,inventory);
        String[] old={v2(null),
                "{\"InvManager\":{\"Maximum Block Stacks\":4}}",
                "{\"InvManager\":{\"settings\":[{\"name\":\"Maximum Block Stacks\",\"value\":4}]}}"};
        for(int i=0;i<old.length;i++) {
            inventory.layout.setValue("Organized");inventory.maxBlocks.setValue(1D);
            write(directory,"Old"+i,old[i]);
            assertTrue(config.getLastError(),config.loadConfig("Old"+i));
            assertEquals("Legacy",inventory.layout.getValue());assertEquals(4,inventory.maxBlocks.getValue(),0);
        }
    }
    @Test public void explicitLayoutsSurviveLoadAndRoundTripWithoutLegacyOverride()throws Exception {
        InventoryManager inventory=new InventoryManager();Path directory=temporary.newFolder().toPath();ConfigManager config=codec(directory,inventory);
        for(String layout:inventory.layout.getOptions()) {
            write(directory,"Explicit",v2(layout));
            assertTrue(config.getLastError(),config.loadConfig("Explicit"));assertEquals(layout,inventory.layout.getValue());
            assertTrue(config.getLastError(),config.saveConfig("RoundTrip"));
            inventory.layout.setValue("Legacy".equals(layout)?"Custom":"Legacy");
            assertTrue(config.getLastError(),config.loadConfig("RoundTrip"));assertEquals(layout,inventory.layout.getValue());
            write(directory,"LegacyExplicit","{\"InvManager\":{\"Layout\":\""+layout+"\"}}");
            inventory.layout.setValue("Legacy".equals(layout)?"Organized":"Legacy");
            assertTrue(config.getLastError(),config.loadConfig("LegacyExplicit"));assertEquals(layout,inventory.layout.getValue());
        }
    }
    @Test public void invalidLayoutRejectsTheWholeProfileWithoutPartialChanges()throws Exception {
        InventoryManager inventory=new InventoryManager();Path directory=temporary.newFolder().toPath();ConfigManager config=codec(directory,inventory);
        inventory.layout.setValue("Custom");inventory.maxBlocks.setValue(2D);
        write(directory,"Invalid",v2("Unknown Layout"));
        assertFalse(config.loadConfig("Invalid"));
        assertEquals("Custom",inventory.layout.getValue());assertEquals(2,inventory.maxBlocks.getValue(),0);
        assertFalse(config.saveConfig("InvalidSave",v2("Unknown Layout")));
        assertFalse(Files.exists(directory.resolve("InvalidSave.json")));
    }
}
