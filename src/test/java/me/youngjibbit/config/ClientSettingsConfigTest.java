package me.youngjibbit.config;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;
/** Real Values and the existing config codec; no GUI, renderer or alternate persistence. */
public class ClientSettingsConfigTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 @Test public void globalAndHudValuesRoundTripOnRestart()throws Exception{
  Path path=folder.newFolder().toPath();ClientSettings settings=new ClientSettings();HUD hud=new HUD();
  ConfigManager first=new ConfigManager(path,Arrays.asList(settings,hud),()->false,s->{});first.initialize(Collections.emptyList());
  settings.roundness.setValue(14.5);settings.blurMethod.setValue("Kawase");settings.surface.setValue("Liquid Glass");settings.glassAppearance.setValue("Clear");settings.synchronize.setValue(false);settings.glowDirection.setValue("Up");settings.textMotion.setValue("Animated");settings.textSpeed.setValue(.37);settings.notificationDesign.setValue("Outline");settings.notifications.setValue(false);hud.snap.setValue(8.0);hud.editInChat.setValue(false);first.shutdown();
  ClientSettings restored=new ClientSettings();HUD restoredHud=new HUD();ConfigManager second=new ConfigManager(path,Arrays.asList(restored,restoredHud),()->false,s->{});second.initialize(Collections.emptyList());
  assertEquals(14.5,restored.roundness.getValue(),0);assertEquals("Kawase",restored.blurMethod.getValue());assertEquals("Liquid Glass",restored.surface.getValue());assertEquals("Clear",restored.glassAppearance.getValue());assertFalse(restored.synchronize.getValue());assertEquals("Up",restored.glowDirection.getValue());assertEquals("Animated",restored.textMotion.getValue());assertEquals(.37,restored.textSpeed.getValue(),0);assertFalse(restored.notifications.getValue());assertEquals(8,restoredHud.snap.getValue(),0);assertFalse(restoredHud.editInChat.getValue());
 }
 @Test public void groupRowsAreRealUniqueModuleValues(){ClientSettings c=new ClientSettings();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());for(java.util.List<me.youngjibbit.value.Value<?>> values:c.groups().values())for(me.youngjibbit.value.Value<?> value:values){assertTrue(seen.add(value));assertTrue(c.getValues().contains(value));}assertEquals(c.getValues().size(),seen.size());c.synchronize.setValue(false);assertTrue(c.glowDirection.isVisible());c.synchronize.setValue(true);assertFalse(c.glowDirection.isVisible());}
 @Test public void resettingUsesPublicValueDefaults(){ClientSettings c=new ClientSettings();c.roundness.setValue(18.0);c.glow.setValue(false);c.surface.setValue("Liquid Glass");for(me.youngjibbit.value.Value<?> value:c.getValues())value.reset();assertEquals(5,c.roundness.getValue(),0);assertTrue(c.glow.getValue());assertEquals("Blur",c.surface.getValue());}
 @Test public void oldHudEffectChoicesBecomeExplicitLocalOverrides()throws Exception{
  me.youngjibbit.module.impl.client.ArrayListHUDModule hud=new me.youngjibbit.module.impl.client.ArrayListHUDModule();ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(hud),()->false,s->{});
  com.google.gson.JsonObject json=new com.google.gson.JsonParser().parse(manager.serialize()).getAsJsonObject();com.google.gson.JsonObject values=json.getAsJsonObject("modules").getAsJsonObject("arraylist_hud").getAsJsonObject("settings");values.remove("effects_source");values.remove("local_rectangle_substance");values.addProperty("background",false);values.addProperty("blur",true);
  assertTrue(manager.saveConfig("legacy-effects",json.toString()));assertTrue(manager.loadConfig("legacy-effects"));assertEquals("Local",hud.getModeSetting("Effects Source").getValue());assertFalse(hud.getBooleanSetting("Background").getValue());assertTrue(hud.getBooleanSetting("Blur").getValue());
  hud.getModeSetting("Effects Source").setValue("Liquid Glass");assertTrue(manager.saveConfig("direct-substance"));hud.getModeSetting("Effects Source").setValue("Global");assertTrue(manager.loadConfig("direct-substance"));assertEquals("Liquid Glass",hud.getModeSetting("Effects Source").getValue());
 }
 @Test public void oldWatermarkTextGlowExtentRemainsAnExplicitChoice()throws Exception{
  me.youngjibbit.module.impl.client.WatermarkHUDModule hud=new me.youngjibbit.module.impl.client.WatermarkHUDModule();ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(hud),()->false,s->{});
  com.google.gson.JsonObject json=new com.google.gson.JsonParser().parse(manager.serialize()).getAsJsonObject();com.google.gson.JsonObject values=json.getAsJsonObject("modules").entrySet().iterator().next().getValue().getAsJsonObject().getAsJsonObject("settings");values.remove("local_text_glow_extent");values.addProperty("text_glow_radius",9.0);
  assertTrue(manager.saveConfig("old-text-radius",json.toString()));assertTrue(manager.loadConfig("old-text-radius"));assertTrue(hud.getBooleanSetting("Local Text Glow Extent").getValue());assertEquals(9,hud.getNumberSetting("Text Glow Radius").getValue(),0);
 }

}
