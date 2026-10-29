package me.youngjibbit.module.impl.client;

import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.config.ConfigManager;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;

public class ClientSettingsVis02Test {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    // Captured from actual JDK8 baseline ClientSettings at 7f79895f4033ba7e2e58af95bec2a3775d082ab0.
    private static final String[] BEFORE={
        "roundness|Roundness|5.0|",
        "surface_style|Surface Style|Blur|Standard,Clear,Gradient,Blur,Liquid Glass",
        "surface_opacity|Surface Opacity|1.0|",
        "gradient_fill_strength|Gradient Fill Strength|0.28|",
        "global_border|Global Border|true|",
        "border_color_source|Border Color Source|Gradient|Gradient,Accent",
        "border_opacity|Border Opacity|0.8|",
        "global_blur|Global Blur|true|",
        "blur_method|Blur Method|Gaussian|Gaussian,Kawase",
        "global_blur_strength|Global Blur Strength|5.0|",
        "kawase_quality|Kawase Quality|2.0|",
        "glass_appearance|Glass Appearance|Regular|Regular,Clear",
        "glass_refraction|Glass Refraction|0.35|",
        "glass_tint|Glass Tint|0.18|",
        "glass_edge_light|Glass Edge Light|0.3|",
        "global_glow|Global Glow|true|",
        "glow_intensity|Glow Intensity|1.0|",
        "glow_extent|Glow Extent|9.0|",
        "shared_gradients|Shared Gradients|true|",
        "synchronize_text_border_glow|Synchronize Text Border Glow|true|",
        "gradient_direction|Gradient Direction|Theme|Theme,Vertical,Horizontal,Up,Left",
        "gradient_motion|Gradient Motion|Theme|Theme,Static,Animated,Solid",
        "gradient_speed|Gradient Speed|0.18|",
        "text_direction|Text Direction|Theme|Theme,Vertical,Horizontal,Up,Left",
        "border_direction|Border Direction|Theme|Theme,Vertical,Horizontal,Up,Left",
        "glow_direction|Glow Direction|Theme|Theme,Vertical,Horizontal,Up,Left",
        "text_motion|Text Motion|Theme|Theme,Static,Animated,Solid",
        "border_motion|Border Motion|Theme|Theme,Static,Animated,Solid",
        "glow_motion|Glow Motion|Theme|Theme,Static,Animated,Solid",
        "text_speed|Text Speed|0.18|",
        "border_speed|Border Speed|0.18|",
        "glow_speed|Glow Speed|0.18|",
        "ui_animations|UI Animations|true|",
        "reduced_motion|Reduced Motion|false|",
        "ui_motion_intensity|UI Motion Intensity|Balanced|Calm,Balanced,Expressive",
        "notifications|Notifications|true|",
        "notification_design|Notification Design|Global|Dark,Outline,Liquid Glass,Global",
        "notification_position|Notification Position|Bottom Right|Bottom Right,Top Right,Bottom Left,Top Left",
        "notification_duration|Notification Duration|5.0|",
        "notification_width|Notification Width|260.0|",
        "visible_notifications|Visible Notifications|3.0|",
        "queued_notifications|Queued Notifications|32.0|",
        "info_notifications|Info Notifications|true|",
        "success_notifications|Success Notifications|true|",
        "warning_notifications|Warning Notifications|true|",
        "error_notifications|Error Notifications|true|"
    };
    @Test public void everyOldIdNameDefaultAndEnumIndexIsUnchanged() {
        ClientSettings c=new ClientSettings();List<String> current=new ArrayList<>();
        for(Value<?> v:c.getValues())if(!Arrays.asList("gradient_method","ambient_motion","semantic_color_priority","notification_theme").contains(v.getId()))
            current.add(v.getId()+"|"+v.getName()+"|"+v.getDefaultValue()+"|"+(v instanceof ComboValue?String.join(",",((ComboValue)v).getOptions()):""));
        assertArrayEquals(BEFORE,current.toArray(new String[0]));assertEquals(BEFORE.length+3+1,c.getValues().size());
        assertEquals("Balanced",c.motionIntensity.getValue());
    }
    @Test public void threeNewDefaultsBelongToExactlyOneExistingGroup() {
        ClientSettings c=new ClientSettings();
        assertEquals("gradient_method",c.gradientMethod.getId());assertEquals("Flow",c.gradientMethod.getValue());assertEquals(0,c.gradientMethod.getSelectedIndex());
        assertArrayEquals(new String[]{"Flow","Ping-Pong","Pulse"},c.gradientMethod.getOptions());
        assertEquals("ambient_motion",c.ambientMotion.getId());assertTrue(c.ambientMotion.getValue());
        assertEquals("semantic_color_priority",c.semanticColorPriority.getId());assertTrue(c.semanticColorPriority.getValue());
        Set<Value<?>> seen=Collections.newSetFromMap(new IdentityHashMap<Value<?>,Boolean>());
        for(List<Value<?>> group:c.groups().values())for(Value<?> v:group)assertTrue(seen.add(v));
        assertEquals(c.getValues().size(),seen.size());
    }
    @Test public void legacyConfigWithoutNewKeysUsesDefaultsAndRetainsOldOverrides() throws Exception {
        ClientSettings c=new ClientSettings();ConfigManager codec=codec(c);
        JsonObject root=new JsonParser().parse(codec.serialize()).getAsJsonObject();
        JsonObject values=root.getAsJsonObject("modules").getAsJsonObject("client_settings").getAsJsonObject("settings");
        values.remove("gradient_method");values.remove("ambient_motion");values.remove("semantic_color_priority");
        values.addProperty("synchronize_text_border_glow",false);values.addProperty("glow_direction","Up");values.addProperty("text_motion","Animated");values.addProperty("text_speed",.37);
        assertTrue(codec.saveConfig("old",root.toString()));assertTrue(codec.loadConfig("old"));
        assertEquals("Flow",c.gradientMethod.getValue());assertTrue(c.ambientMotion.getValue());assertTrue(c.semanticColorPriority.getValue());
        assertFalse(c.synchronize.getValue());assertEquals("Up",c.glowDirection.getValue());assertEquals("Animated",c.textMotion.getValue());assertEquals(.37,c.textSpeed.getValue(),0);
    }
    @Test public void allMethodsAndSwitchesRoundTripWithTheRealCodec() throws Exception {
        for(String method:new String[]{"Flow","Ping-Pong","Pulse"})for(boolean flag:new boolean[]{false,true}) {
            ClientSettings c=new ClientSettings();ConfigManager codec=codec(c);
            c.gradientMethod.setValue(method);c.ambientMotion.setValue(flag);c.semanticColorPriority.setValue(!flag);
            assertTrue(codec.saveConfig("vis02",codec.serialize()));
            c.gradientMethod.setValue("Flow");c.ambientMotion.setValue(!flag);c.semanticColorPriority.setValue(flag);
            assertTrue(codec.loadConfig("vis02"));assertEquals(method,c.gradientMethod.getValue());assertEquals(flag,c.ambientMotion.getValue());assertEquals(!flag,c.semanticColorPriority.getValue());
        }
    }
    private ConfigManager codec(ClientSettings c) throws Exception {return new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(c),()->false,s->{});}
}
