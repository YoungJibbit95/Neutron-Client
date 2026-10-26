package me.youngjibbit.config;
import com.google.gson.*;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Existing config/module/value persistence with disposable theme definitions; never a game session. */
public class ThemeConfigIntegrationTest {
 @Rule public TemporaryFolder temporary=new TemporaryFolder();
 private ThemeCatalog previous,catalog;private Path data,configs,library;private ThemeModule module;
 @Before public void before()throws Exception{previous=ThemeCatalog.global();data=temporary.newFolder().toPath();configs=data.resolve("configs");library=data.resolve("pulsar/theme/themes.json");ThemeRuntime.initialize(data.toFile());catalog=ThemeCatalog.global();module=new ThemeModule();}
 @After public void after(){ThemeCatalog.install(previous);}
 private ConfigManager manager(ThemeModule m){return new ConfigManager(configs,Collections.singletonList(m),()->false,message->{});}
 private String read(Path p)throws Exception{return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
 private void write(Path p,String bytes)throws Exception{Files.createDirectories(p.getParent());Files.write(p,bytes.getBytes(StandardCharsets.UTF_8));}
 private String reference(ConfigManager c){return JsonParser.parseString(c.serialize()).getAsJsonObject().getAsJsonObject("modules").getAsJsonObject("theme").getAsJsonObject("settings").get("theme").getAsString();}
 @Test public void customSelectionSurvivesConfigRestart()throws Exception{
  ConfigManager cfg=manager(module);cfg.initialize(Collections.emptyList());String id;
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.duplicate();id=d.reference();d.title.setValue("Restart theme");d.first.setColor(new Color(1,2,3,4));assertEquals("PinkLemonade",reference(cfg));assertFalse(Files.exists(library));d.save();}
  assertEquals(id,reference(cfg));assertTrue(cfg.saveDefaultConfig());cfg.shutdown();
  ThemeRuntime.initialize(data.toFile());ThemeModule restarted=new ThemeModule();ConfigManager loaded=manager(restarted);loaded.initialize(Collections.emptyList());assertEquals(id,restarted.selectedTheme.getValue());assertEquals("Restart theme",Theme.getDefaultTheme().getDisplayName());assertEquals(4,Theme.getDefaultTheme().getPrimaryGradientColor().getAlpha());assertFalse(Files.exists(data.resolve("pulsar/theme/theme_config.txt")));
 }
 @Test public void previewIsAbsentEvenFromExplicitConfigSave()throws Exception{
  ConfigManager cfg=manager(module);cfg.initialize(Collections.emptyList());assertTrue(cfg.saveDefaultConfig());String bytes=read(cfg.defaultConfig.toPath());
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.duplicate();d.title.setValue("Never saved");d.speed.setValue(1.4);assertTrue(cfg.saveDefaultConfig());assertEquals(bytes,read(cfg.defaultConfig.toPath()));assertFalse(Files.exists(library));d.cancel();}
  assertSame(Theme.PinkLemonade,Theme.getDefaultTheme());assertEquals("PinkLemonade",module.selectedTheme.getValue());
 }
 @Test public void oldPresetStringsAndChoiceObjectsRestoreThroughOneCallback()throws Exception{
  ConfigManager cfg=manager(module);AtomicInteger calls=new AtomicInteger();module.selectedTheme.setOnChange(value->calls.incrementAndGet());
  write(configs.resolve("Legacy.json"),"{\"version\":2,\"modules\":{\"theme\":{\"settings\":{\"theme\":{\"value\":\"SynthWave\"}}}}}");assertTrue(cfg.loadConfig("Legacy"));assertSame(Theme.SynthWave,Theme.getDefaultTheme());assertEquals(1,calls.get());
  write(configs.resolve("Legacy.json"),"{\"version\":2,\"modules\":{\"theme\":{\"settings\":{\"theme\":\"neon\"}}}}");assertTrue(cfg.loadConfig("Legacy"));assertEquals("Neon",module.selectedTheme.getValue());assertEquals(2,calls.get());
 }
 @Test public void legacyFileIsReadOnlyAndLastStateTakesPrecedence()throws Exception{
  Path legacy=data.resolve("pulsar/theme/theme_config.txt");write(legacy,"Neon\n");ThemeRuntime.initialize(data.toFile());ThemeModule old=new ThemeModule();assertEquals("Neon",old.selectedTheme.getValue());ConfigManager cfg=manager(old);cfg.initialize(Collections.emptyList());old.selectedTheme.setValue("SynthWave");assertTrue(cfg.saveDefaultConfig());assertEquals("Neon\n",read(legacy));
  ThemeRuntime.initialize(data.toFile());ThemeModule restarted=new ThemeModule();manager(restarted).initialize(Collections.emptyList());assertEquals("SynthWave",restarted.selectedTheme.getValue());assertSame(Theme.SynthWave,Theme.getDefaultTheme());assertEquals("Neon\n",read(legacy));
 }
 @Test public void newerLibraryKeepsFileAndUnavailableSelectionReference()throws Exception{
  String id="custom:"+UUID.randomUUID(),future="{\"version\":99,\"themes\":[]}";write(library,future);ThemeRuntime.initialize(data.toFile());ThemeModule m=new ThemeModule();ConfigManager cfg=manager(m);
  write(cfg.defaultConfig.toPath(),"{\"version\":2,\"modules\":{\"theme\":{\"settings\":{\"theme\":\""+id+"\"}}}}");cfg.initialize(Collections.emptyList());assertEquals(id,m.selectedTheme.getValue());assertSame(Theme.PinkLemonade,Theme.getDefaultTheme());assertTrue(ThemeCatalog.global().isReadOnly());assertTrue(cfg.saveDefaultConfig());assertEquals(id,reference(cfg));assertEquals(future,read(library));
 }
 @Test public void renameKeepsReferencesAndDeleteUsesPublicValueCallbackOnce()throws Exception{
  ConfigManager cfg=manager(module);cfg.initialize(Collections.emptyList());AtomicInteger calls=new AtomicInteger();module.selectedTheme.setOnChange(v->calls.incrementAndGet());String id;
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.duplicate();id=d.reference();d.save();}assertEquals(1,calls.get());assertTrue(cfg.saveConfig("BeforeRename"));
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.title.setValue("Renamed");d.save();}assertEquals(id,module.selectedTheme.getValue());assertEquals(1,calls.get());assertTrue(cfg.loadConfig("BeforeRename"));assertEquals("Renamed",Theme.getDefaultTheme().getDisplayName());
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.delete();}assertEquals(2,calls.get());assertEquals("PinkLemonade",reference(cfg));assertSame(Theme.PinkLemonade,Theme.getDefaultTheme());
 }
 @Test public void invalidThemeIsRejectedBeforeAnyConfigMutation()throws Exception{
  ConfigManagerTest.Sample sample=new ConfigManagerTest.Sample();ConfigManager cfg=new ConfigManager(configs,Arrays.asList(sample,module),()->false,message->{});
  write(configs.resolve("Invalid.json"),"{\"version\":2,\"modules\":{\"synthetic\":{\"settings\":{\"flag\":true}},\"theme\":{\"settings\":{\"theme\":\"not-a-theme\"}}}}");assertFalse(cfg.loadConfig("Invalid"));assertFalse(sample.flag.getValue());assertEquals("PinkLemonade",module.selectedTheme.getValue());
 }
 @Test public void liveChoiceListUsesIdsAndPublicIndexSetter()throws Exception{
  assertEquals(19,module.selectedTheme.getOptions().length);String id;
  try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.duplicate();id=d.reference();d.title.setValue("Friendly label");d.save();}assertEquals(20,module.selectedTheme.getOptions().length);assertEquals("Friendly label",ThemeSelectionValue.label(id));module.selectedTheme.setSelectedIndex(0);assertEquals("BlackLight",module.selectedTheme.getValue());module.selectedTheme.setSelectedIndex(19);assertEquals(id,module.selectedTheme.getValue());assertEquals(19,module.selectedTheme.getSelectedIndex());
 }
 @Test public void customThemeAndIndependentMovefixModesSurviveOneConfigAndRestart()throws Exception{
  me.youngjibbit.module.impl.combat.Aura aura=new me.youngjibbit.module.impl.combat.Aura();
  me.youngjibbit.module.impl.world.Scaffold scaffold=new me.youngjibbit.module.impl.world.Scaffold();
  ConfigManager cfg=new ConfigManager(configs,Arrays.asList(aura,scaffold,module),()->false,message->{});cfg.initialize(Collections.emptyList());
  String id;try(ThemeDraft d=new ThemeDraft(catalog,module.selectedTheme::setValue)){d.duplicate();id=d.reference();d.title.setValue("Combined integration");d.save();}
  aura.getModeSetting("movefix").setValue("Präzise");scaffold.getModeSetting("movefix").setValue("Einfach");assertTrue(cfg.saveConfig("Combined"));
  aura.getModeSetting("movefix").setValue("Aus");scaffold.getModeSetting("movefix").setValue("Aus");module.selectedTheme.setValue("Neon");
  assertTrue(cfg.getLastError(),cfg.loadConfig("Combined"));assertEquals(id,module.selectedTheme.getValue());
  assertEquals(me.youngjibbit.core.combat.MoveFixController.Mode.PRECISE,aura.moveFixMode());assertEquals(me.youngjibbit.core.combat.MoveFixController.Mode.SIMPLE,scaffold.moveFixMode());
  assertTrue(cfg.saveDefaultConfig());cfg.shutdown();ThemeRuntime.initialize(data.toFile());
  me.youngjibbit.module.impl.combat.Aura nextAura=new me.youngjibbit.module.impl.combat.Aura();
  me.youngjibbit.module.impl.world.Scaffold nextScaffold=new me.youngjibbit.module.impl.world.Scaffold();ThemeModule nextTheme=new ThemeModule();
  ConfigManager restarted=new ConfigManager(configs,Arrays.asList(nextAura,nextScaffold,nextTheme),()->false,message->{});restarted.initialize(Collections.emptyList());
  assertEquals(id,nextTheme.selectedTheme.getValue());assertEquals("Combined integration",Theme.getDefaultTheme().getDisplayName());
  assertEquals(me.youngjibbit.core.combat.MoveFixController.Mode.PRECISE,nextAura.moveFixMode());assertEquals(me.youngjibbit.core.combat.MoveFixController.Mode.SIMPLE,nextScaffold.moveFixMode());
  for(boolean enabled:new boolean[]{true,false}){
   write(configs.resolve("LegacyCombined.json"),"{\"version\":2,\"modules\":{\"aura\":{\"settings\":{\"movefix\":"+enabled+"}},\"scaffold\":{\"settings\":{\"movefix\":\"Präzise\"}},\"theme\":{\"settings\":{\"theme\":{\"value\":\"SynthWave\"}}}}}");
   assertTrue(restarted.getLastError(),restarted.loadConfig("LegacyCombined"));assertSame(Theme.SynthWave,Theme.getDefaultTheme());
   assertEquals(enabled?me.youngjibbit.core.combat.MoveFixController.Mode.SIMPLE:me.youngjibbit.core.combat.MoveFixController.Mode.OFF,nextAura.moveFixMode());
   assertEquals(me.youngjibbit.core.combat.MoveFixController.Mode.PRECISE,nextScaffold.moveFixMode());
  }restarted.shutdown();
 }
}
