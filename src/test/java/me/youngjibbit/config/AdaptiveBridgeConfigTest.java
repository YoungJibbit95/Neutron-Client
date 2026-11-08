package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;

public class AdaptiveBridgeConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void appendedApproachPreservesOldIndicesAndDefault() {
        Scaffold s=new Scaffold();
        assertArrayEquals(new String[]{"Neutron","Arsenic","Adaptive Bridge"},
                s.getModeSetting("Scaffold Approach").getOptions());
        assertEquals("Neutron",s.getModeSetting("Scaffold Approach").getValue());
        assertEquals(0,s.getModeSetting("Scaffold Approach").getSelectedIndex());
        s.getModeSetting("Scaffold Approach").setSelectedIndex(1);
        assertEquals("Arsenic",s.getModeSetting("Scaffold Approach").getValue());
        s.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        assertEquals(2,s.getModeSetting("Scaffold Approach").getSelectedIndex());
        assertFalse(s.getValueByName("Tower").isVisible());
    }

    @Test public void optInRoundTripsUsingOldConfigId() throws Exception {
        Scaffold s=new Scaffold();Path dir=temp.newFolder().toPath();
        ConfigManager c=new ConfigManager(dir,Collections.<Module>singletonList(s),
                ()->false,message->{});
        s.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        assertTrue(c.getLastError(),c.saveConfig("Bridge"));
        String original=c.serialize();
        assertEquals("Adaptive Bridge",new com.google.gson.JsonParser().parse(original)
                .getAsJsonObject().getAsJsonObject("modules")
                .getAsJsonObject("scaffold").getAsJsonObject("settings")
                .get("scaffold_approach").getAsString());
        s.getModeSetting("Scaffold Approach").setValue("Neutron");
        assertTrue(c.getLastError(),c.loadConfig("Bridge"));
        assertEquals("Adaptive Bridge",s.getModeSetting("Scaffold Approach").getValue());
        assertEquals(original,c.serialize());
    }

    @Test public void missingKeyRevertsToNeutronWithoutChangingSourceFile() throws Exception {
        Scaffold s=new Scaffold();Path dir=temp.newFolder().toPath();
        ConfigManager c=new ConfigManager(dir,Collections.<Module>singletonList(s),
                ()->false,message->{});
        s.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        String original="{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{}}}}";
        Files.write(dir.resolve("Legacy.json"),original.getBytes(StandardCharsets.UTF_8));
        assertTrue(c.getLastError(),c.loadConfig("Legacy"));
        assertEquals("Neutron",s.getModeSetting("Scaffold Approach").getValue());
        assertEquals(original,new String(Files.readAllBytes(dir.resolve("Legacy.json")),StandardCharsets.UTF_8));
    }

    @Test public void invalidApproachIsAtomic() throws Exception {
        Scaffold s=new Scaffold();Path dir=temp.newFolder().toPath();
        ConfigManager c=new ConfigManager(dir,Collections.<Module>singletonList(s),
                ()->false,message->{});
        s.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        String before=c.serialize();
        String bad="{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":"
                +"{\"scaffold_approach\":\"Adaptive Bridge Unsafe\"}}}}";
        Files.write(dir.resolve("Bad.json"),bad.getBytes(StandardCharsets.UTF_8));
        assertFalse(c.loadConfig("Bad"));
        assertEquals(before,c.serialize());
    }
}
