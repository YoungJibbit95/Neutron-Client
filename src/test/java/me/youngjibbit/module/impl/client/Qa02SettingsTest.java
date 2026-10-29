package me.youngjibbit.module.impl.client;

import com.google.gson.*;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;

public class Qa02SettingsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private ComboValue theme(ClientSettings c) {
        Value<?> value=c.getValueByName("notification_theme");
        assertNotNull("Q02-13: persistent Notification Theme is missing",value);
        assertTrue(value instanceof ComboValue);return (ComboValue)value;
    }
    @Test public void additionalSelectorHasStableOptionsDefaultAndAdjacentGroupPosition() {
        ClientSettings c=new ClientSettings();ComboValue v=theme(c);
        assertEquals("Notification Theme",v.getName());assertEquals("Follow Global",v.getDefaultValue());assertEquals(0,v.getSelectedIndex());
        assertArrayEquals(new String[]{"Follow Global","Obsidian","Aurora","Neon Edge","Minimal"},v.getOptions());
        List<Value<?>> group=c.groups().get("Notifications");assertSame(v,group.get(group.indexOf(c.notificationDesign)+1));
        int count=0;for(List<Value<?>> values:c.groups().values())for(Value<?> each:values)if(each==v)count++;
        assertEquals(1,count);assertTrue(v.isVisible());assertEquals(50,c.getValues().size());
        assertSame(v,c.getValues().get(49)); // no old registration index moves
    }
    @Test public void allTwentySelectorPairsRoundTripThroughRealCodec() throws Exception {
        ClientSettings c=new ClientSettings();ComboValue v=theme(c);ConfigManager codec=codec(c);
        for(String selected:v.getOptions())for(String design:c.notificationDesign.getOptions()) {
            v.setValue(selected);c.notificationDesign.setValue(design);assertTrue(codec.saveConfig("qa02",codec.serialize()));
            v.reset();c.notificationDesign.reset();assertTrue(codec.loadConfig("qa02"));
            assertEquals(selected,v.getValue());assertEquals(design,c.notificationDesign.getValue());
        }
    }
    @Test public void legacyMissingKeyLoadsFollowGlobalOnFreshSettingsAndKeepsDesignOverride() throws Exception {
        ClientSettings c=new ClientSettings();theme(c);java.nio.file.Path path=folder.newFolder().toPath();ConfigManager codec=new ConfigManager(path,Collections.singletonList(c),()->false,s->{});
        JsonObject json=new JsonParser().parse(codec.serialize()).getAsJsonObject();
        JsonObject values=json.getAsJsonObject("modules").getAsJsonObject("client_settings").getAsJsonObject("settings");
        values.remove("notification_theme");values.addProperty("notification_design","Outline");
        assertTrue(codec.saveConfig("legacy",json.toString()));ClientSettings restored=new ClientSettings();
        ConfigManager reader=new ConfigManager(path,Collections.singletonList(restored),()->false,s->{});assertTrue(reader.loadConfig("legacy"));
        assertEquals("Follow Global",theme(restored).getValue());assertEquals("Outline",restored.notificationDesign.getValue());
    }
    @Test public void oldDesignIdDefaultOptionIndicesStayExact() {
        ClientSettings c=new ClientSettings();assertEquals("notification_design",c.notificationDesign.getId());
        assertEquals("Global",c.notificationDesign.getDefaultValue());assertEquals(3,c.notificationDesign.getSelectedIndex());
        assertArrayEquals(new String[]{"Dark","Outline","Liquid Glass","Global"},c.notificationDesign.getOptions());
    }
    @Test public void legacyMissingKeyAlsoUsesFollowGlobalOnWarmProfileSwitch() throws Exception {
        ClientSettings c=new ClientSettings();ComboValue v=theme(c);ConfigManager codec=codec(c);
        JsonObject json=new JsonParser().parse(codec.serialize()).getAsJsonObject();
        JsonObject values=json.getAsJsonObject("modules").getAsJsonObject("client_settings").getAsJsonObject("settings");
        values.remove("notification_theme");values.addProperty("notification_design","Outline");values.addProperty("roundness",13.75);
        assertTrue(codec.saveConfig("legacy-warm",json.toString()));v.setValue("Neon Edge");int[] calls={0,0};
        c.roundness.setOnChange(ignore->calls[0]++);v.setOnChange(ignore->calls[1]++);assertTrue(codec.loadConfig("legacy-warm"));
        assertEquals("Follow Global",v.getValue());assertEquals("Outline",c.notificationDesign.getValue());assertEquals(13.75,c.roundness.getValue(),0);
        assertArrayEquals(new int[]{1,1},calls);
    }
    @Test public void allFortyNineOldValuesRetainExactOrderNamesDefaultsAndOptions() throws Exception {
        java.lang.reflect.Field before=ClientSettingsVis02Test.class.getDeclaredField("BEFORE");before.setAccessible(true);
        List<String> expected=new ArrayList<>(Arrays.asList((String[])before.get(null)));
        int i=expected.indexOf("notifications|Notifications|true|");
        expected.addAll(i,Arrays.asList("gradient_method|Gradient Method|Flow|Flow,Ping-Pong,Pulse","ambient_motion|Ambient Motion|true|","semantic_color_priority|Semantic Color Priority|true|"));
        List<String> actual=new ArrayList<>();for(Value<?> v:new ClientSettings().getValues())if(!v.getId().equals("notification_theme"))
            actual.add(v.getId()+"|"+v.getName()+"|"+v.getDefaultValue()+"|"+(v instanceof ComboValue?String.join(",",((ComboValue)v).getOptions()):""));
        assertEquals(49,expected.size());assertEquals(expected,actual);
    }
    private ConfigManager codec(ClientSettings c)throws Exception{return new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(c),()->false,s->{});}
}
