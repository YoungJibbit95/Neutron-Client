package me.youngjibbit.module.impl.client;

import com.google.gson.*;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;

public class ClientSettingsMotionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void oneVisiblePersistentValueWithBalancedDefault() {
        ClientSettings c=new ClientSettings(); ComboValue motion=c.motionIntensity;
        assertEquals("ui_motion_intensity",motion.getId()); assertEquals("UI Motion Intensity",motion.getName());
        assertEquals("Balanced",motion.getValue()); assertEquals("Balanced",motion.getDefaultValue());
        assertArrayEquals(new String[]{"Calm","Balanced","Expressive"},motion.getOptions()); assertTrue(motion.isVisible());
        int count=0;
        for(List<Value<?>> group:c.groups().values())for(Value<?> value:group)if(value==motion)count++;
        assertEquals(1,count); assertTrue(c.groups().get("Gradients / Motion").contains(motion));
        assertSame(motion,c.getValueByName("ui_motion_intensity"));
        motion.setValue("Expressive");motion.reset();assertEquals("Balanced",motion.getValue());
    }
    @Test public void allPresetsRoundTripThroughExistingConfigCodec() throws Exception {
        for(String preset:new String[]{"Calm","Balanced","Expressive"}) {
            ClientSettings c=new ClientSettings();ConfigManager first=manager(c);first.initialize(Collections.emptyList());
            c.motionIntensity.setValue(preset);c.animations.setValue(false);c.reducedMotion.setValue(true);c.blur.setValue(false);c.glow.setValue(false);
            assertTrue(first.saveConfig("motion",first.serialize()));
            c.motionIntensity.setValue("Balanced");c.animations.setValue(true);c.reducedMotion.setValue(false);
            assertTrue(first.loadConfig("motion"));assertEquals(preset,c.motionIntensity.getValue());
            assertFalse(c.animations.getValue());assertTrue(c.reducedMotion.getValue());assertFalse(c.blur.getValue());assertFalse(c.glow.getValue());
        }
    }
    @Test public void oldConfigKeepsLegacyValuesAndNewDefault() throws Exception {
        ClientSettings c=new ClientSettings();ConfigManager codec=manager(c);
        JsonObject json=new JsonParser().parse(codec.serialize()).getAsJsonObject();
        JsonObject values=json.getAsJsonObject("modules").getAsJsonObject("client_settings").getAsJsonObject("settings");
        values.remove("ui_motion_intensity"); values.addProperty("ui_animations",false);values.addProperty("reduced_motion",true);
        values.addProperty("global_blur",false);values.addProperty("global_glow",false);
        assertTrue(codec.saveConfig("legacy",json.toString()));assertTrue(codec.loadConfig("legacy"));
        assertEquals("Balanced",c.motionIntensity.getValue()); assertEquals("ui_animations",c.animations.getId());
        assertEquals("reduced_motion",c.reducedMotion.getId());assertFalse(c.animations.getValue());assertTrue(c.reducedMotion.getValue());
        assertFalse(c.blur.getValue());assertFalse(c.glow.getValue());
    }
    private ConfigManager manager(ClientSettings c) throws Exception {
        return new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(c),()->false,s->{});
    }
}
