package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.*;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.module.impl.exploit.*;
import me.youngjibbit.module.impl.ghost.AutoClicker2;
import me.youngjibbit.module.impl.ghost.JumpReset;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.module.impl.player.NoSlow;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class RotationPresetBundleTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void intaveCommandAppliesBundledSettingsIndividuallyAndTogether() throws Exception {
        Aura aura = new Aura();
        Velocity velocity = new Velocity();
        List<Module> modules = Arrays.asList(aura, velocity, new Scaffold(), new Backtrack(), new LagRange(),
                new Tickbase(), new me.youngjibbit.module.impl.ghost.WTap2(), new Speed(), new NoSlow());
        ConfigManager manager = new ConfigManager(temp.newFolder().toPath(), modules, () -> false, m -> {});
        me.youngjibbit.command.impl.LoadCommand command = new me.youngjibbit.command.impl.LoadCommand(manager);
        aura.getBooleanSetting("Preset Timing").setValue(true);
        command.execute("intave", "Autoblock");
        assertEquals("", manager.getLastError());
        assertTrue(aura.getBooleanSetting("Autoblock").getValue());
        assertTrue(aura.getBooleanSetting("Preset Timing").getValue());
        assertEquals("Legit", velocity.mode.getValue());
        assertFalse(manager.applyIntavePreset("absent"));
        command.execute("intave");
        assertEquals("", manager.getLastError());
        assertEquals("Intave Reduce", velocity.mode.getValue());
        assertEquals("Pre", modules.get(2).getModeSetting("Place Timing").getValue());
        for (Module module : modules) assertFalse(module.isToggled());
    }
    private static final String[] NAMES={"Intave-Arsenic-Combat","Intave-Arsenic-Assist","Polar-Arsenic-Organic","Polar-Noise-Organic"};
    @Test public void everyBundledSettingIsKnownAndLoadsExactlyAcrossProfileSwitches() throws Exception {
        List<Module> modules=Arrays.asList(new Aura(),new AimAssist(),new Scaffold(),new Backtrack(),new LagRange(),
                new Tickbase(),new Velocity(),new JumpReset(),new AutoClicker2(),new Speed(),new NoSlow());
        Map<String,Module> byId=new HashMap<>();for(Module m:modules)byId.put(m.getId(),m);
        Path dir=temp.newFolder().toPath();
        ConfigManager manager=new ConfigManager(dir,modules,()->false,message->{});
        // Reuse the same modules through both directions: a previous profile must not change explicit values.
        for(String name:new String[]{NAMES[0],NAMES[1],NAMES[2],NAMES[3],NAMES[0],NAMES[2]}) {
            String text;
            try(InputStream stream=getClass().getResourceAsStream("/neutron/configs/"+name+".json")) {
                assertNotNull(name,stream); ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
                while((n=stream.read(buf))!=-1)data.write(buf,0,n);text=new String(data.toByteArray(),StandardCharsets.UTF_8);
            }
            JsonObject expected=new JsonParser().parse(text).getAsJsonObject().getAsJsonObject("modules");
            for(Map.Entry<String,JsonElement> entry:expected.entrySet()) {
                Module module=byId.get(entry.getKey());assertNotNull("unknown module "+entry.getKey(),module);
                JsonObject settings=entry.getValue().getAsJsonObject().getAsJsonObject("settings");
                if(settings!=null)for(Map.Entry<String,JsonElement> value:settings.entrySet())
                    assertNotNull(name+": unknown setting "+module.getId()+"."+value.getKey(),module.getValueByName(value.getKey()));
            }
            assertTrue(manager.getLastError(),manager.saveConfig(name,text));
            assertTrue(manager.getLastError(),manager.loadConfig(name));
            JsonObject actual=new JsonParser().parse(manager.serialize()).getAsJsonObject().getAsJsonObject("modules");
            for(Map.Entry<String,JsonElement> entry:expected.entrySet()) {
                JsonObject want=entry.getValue().getAsJsonObject(),got=actual.getAsJsonObject(entry.getKey());
                assertEquals(name+": activation "+entry.getKey(),want.get("enabled"),got.get("enabled"));
                JsonObject settings=want.getAsJsonObject("settings");
                if(settings!=null)for(Map.Entry<String,JsonElement> value:settings.entrySet())
                    assertEquals(name+": "+entry.getKey()+"."+value.getKey(),value.getValue(),got.getAsJsonObject("settings").get(value.getKey()));
            }
            assertFalse(actual.getAsJsonObject("scaffold").get("enabled").getAsBoolean());
            assertNotEquals(actual.getAsJsonObject("aura").get("enabled"),actual.getAsJsonObject("aimassist").get("enabled"));
        }
    }

    @Test public void smoothStopDefaultsAndVisibilityRoundTripForBothScaffoldApproaches() throws Exception {
        Aura aura=new Aura();Scaffold scaffold=new Scaffold();
        ConfigManager config=new ConfigManager(temp.newFolder().toPath(),Arrays.asList(aura,scaffold),()->false,m->{});
        assertTrue(aura.getBooleanSetting("Smooth Rotation Stop").getValue());
        assertEquals(6,aura.getNumberSetting("Return Ticks").getValue(),0);
        scaffold.getModeSetting("Scaffold Approach").setValue("Arsenic");
        assertTrue(scaffold.getValueByName("Smooth Rotation Stop").isVisible());
        assertTrue(scaffold.getValueByName("Return Ticks").isVisible());
        aura.getNumberSetting("Return Ticks").setValue(9D);scaffold.getNumberSetting("Return Ticks").setValue(8D);
        assertTrue(config.saveConfig("return"));
        aura.getNumberSetting("Return Ticks").setValue(3D);scaffold.getNumberSetting("Return Ticks").setValue(3D);
        assertTrue(config.loadConfig("return"));
        assertEquals(9,aura.getNumberSetting("Return Ticks").getValue(),0);
        assertEquals(8,scaffold.getNumberSetting("Return Ticks").getValue(),0);
        scaffold.getBooleanSetting("Smooth Rotation Stop").setValue(false);
        assertFalse(scaffold.getValueByName("Return Ticks").isVisible());
    }
}
