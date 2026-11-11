package me.youngjibbit.utils.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import org.junit.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
public class UiEffectsTest {
 private ModuleManager before;private Field field;private ClientSettings settings;private AtomicLong clock;
 @Before public void setup()throws Exception{field=Pulsar.class.getDeclaredField("mm");field.setAccessible(true);before=Pulsar.INSTANCE.getMm();Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);ModuleManager manager=(ModuleManager)((sun.misc.Unsafe)u.get(null)).allocateInstance(ModuleManager.class);settings=new ClientSettings();Field index=ModuleManager.class.getDeclaredField("modulesByClass");index.setAccessible(true);java.util.LinkedHashMap<Class<?>,Object> modules=new java.util.LinkedHashMap<>();modules.put(ClientSettings.class,settings);index.set(manager,modules);field.set(Pulsar.INSTANCE,manager);clock=new AtomicLong(0);UiEffects.timeSource(clock::get);UiEffects.invalidate();settings.direction.setValue("Vertical");settings.gradientMotion.setValue("Animated");settings.gradientSpeed.setValue(.25);UiEffects.beginFrame();}
 private void advanceTo(long target){while(clock.get()<target){clock.set(Math.min(target,clock.get()+100000000L));UiEffects.beginFrame();}}
 @After public void restore()throws Exception{field.set(Pulsar.INSTANCE,before);UiEffects.invalidate();UiEffects.timeSource(System::nanoTime);}
 @Test public void waveTravelsDownAndWrapsWithoutJump(){advanceTo(1000000000L);UiEffects.Gradient g=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);assertEquals(.25,g.phase,1e-6);assertEquals(0,g.position(0,25,100,100),1e-5);assertEquals(1,g.position(0,75,100,100),1e-5);advanceTo(4000000000L);assertEquals(0,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,1e-5);assertEquals(g.position(0,25,100,100),g.position(0,125,100,100),1e-5);}
 @Test public void channelsSharePhaseUntilExplicitlySeparated(){advanceTo(1000000000L);assertEquals(UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,0);settings.synchronize.setValue(false);settings.glowMotion.setValue("Animated");settings.glowSpeed.setValue(.5);UiEffects.beginFrame();clock.set(2000000000L);UiEffects.beginFrame();assertNotEquals(UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,1e-5);}
 @Test public void reducedMotionFreezesPhaseAndSnapsResponse(){advanceTo(1000000000L);float phase=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase;settings.reducedMotion.setValue(true);clock.set(5000000000L);UiEffects.beginFrame();assertEquals(phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,0);assertEquals(1,UiEffects.response(.016f,20),0);settings.reducedMotion.setValue(false);settings.animations.setValue(false);clock.set(9000000000L);UiEffects.beginFrame();assertEquals(phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,0);}
 @Test public void substanceModesAndIndependentMastersResolveInOnePlace(){
  for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){settings.surface.setValue(mode);UiEffects.SurfacePolicy p=UiEffects.surface(null,Theme.SynthWave,true,true,true,1,-1);assertEquals(UiEffects.style(mode),p.style);assertEquals(mode.equals("Blur")||mode.equals("Liquid Glass"),p.blur);assertTrue(p.glow);}
  settings.blur.setValue(false);settings.glow.setValue(false);settings.border.setValue(false);UiEffects.SurfacePolicy p=UiEffects.surface(null,Theme.SynthWave,true,true,true,1,-1);assertFalse(p.blur);assertFalse(p.glow);assertEquals(0,p.outline,0);
 }
 @Test public void actualArraylistExposesCommonSubstancesWithoutLosingLegacyOverrides()throws Exception{
  me.youngjibbit.module.impl.client.ArrayListHUDModule owner=new me.youngjibbit.module.impl.client.ArrayListHUDModule();Field surface=owner.getClass().getDeclaredField("surface");surface.setAccessible(true);Field localField=ArrayListSurface.class.getDeclaredField("local");localField.setAccessible(true);UiEffects.LocalSurface local=(UiEffects.LocalSurface)localField.get(surface.get(owner));
  settings.surface.setValue("Gradient");assertEquals(1,UiEffects.surface(local,Theme.SynthWave,false,false,false,0,-1).style);assertFalse(owner.getModeSetting("Mode").isVisible());
  for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){local.source.setValue(mode);UiEffects.SurfacePolicy p=UiEffects.surface(local,Theme.SynthWave,false,false,false,0,-1);assertEquals(UiEffects.style(mode),p.style);assertTrue(p.background);assertTrue(p.glow);assertEquals(UiEffects.style(mode)>=2,p.blur);}
  local.source.setValue("Local");assertTrue(owner.getModeSetting("Mode").isVisible());assertTrue(local.substance.isVisible());assertEquals(-1,UiEffects.surface(local,Theme.SynthWave,false,false,false,0,-1).style);assertFalse(UiEffects.surface(local,Theme.SynthWave,false,false,false,0,-1).background);
  local.source.setValue("Global");assertEquals(1,UiEffects.surface(local,Theme.SynthWave,false,false,false,0,-1).style);
 }

 @Test public void sharedControlsOnlyAppearWhenTheyCanAffectTheSelectedChannels(){
  settings.synchronize.setValue(false);assertFalse(settings.direction.isVisible());assertFalse(settings.gradientMotion.isVisible());assertFalse(settings.gradientSpeed.isVisible());assertTrue(settings.textDirection.isVisible());
  settings.textMotion.setValue("Static");assertFalse(settings.textSpeed.isVisible());settings.textMotion.setValue("Animated");assertTrue(settings.textSpeed.isVisible());
  settings.synchronize.setValue(true);settings.gradientMotion.setValue("Theme");assertTrue(settings.direction.isVisible());assertFalse(settings.gradientSpeed.isVisible());settings.gradientMotion.setValue("Animated");assertTrue(settings.gradientSpeed.isVisible());
 }
 @Test public void geometricGlowControlsDoNotRetunePaletteSpeedOrPhase(){
  advanceTo(1000000000L);
  UiEffects.Gradient baseline=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW);
  for(String mode:new String[]{"Flame","Neon","Standard"}){
   settings.uiGlowMode.setValue(mode);settings.neonGlow.setValue(2D);settings.flameSpeed.setValue(1.2);
   settings.lineSegments.setValue(16D);settings.lineSpacing.setValue(7D);
   UiEffects.Gradient gradient=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW);
   assertEquals(baseline.axis,gradient.axis);assertEquals(baseline.mode,gradient.mode);
   assertEquals(baseline.speed,gradient.speed,0);assertEquals(baseline.phase,gradient.phase,0);
   assertEquals(baseline.position(40,60,120,140),gradient.position(40,60,120,140),0);
  }
 }
 @Test public void localAnimatedThemeUsesItsOwnSpeedAndThePausedUiClock(){
  Theme local=Theme.custom("custom:00000000-0000-0000-0000-000000000022","Local clock",java.util.Arrays.asList(java.awt.Color.RED,java.awt.Color.BLUE),false,true,.3f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.GREEN,.75f,9,.8f,1);
  settings.gradientMotion.setValue("Theme");advanceTo(1000000000L);assertEquals(.3f,UiEffects.gradient(local,UiEffects.Channel.TEXT).phase,.00001);
  settings.reducedMotion.setValue(true);clock.set(5000000000L);UiEffects.beginFrame();assertEquals(.3f,local.phase(),.00001);
 }

 @Test public void warmedGradientIsSharedUntilItsActualInputsChange(){
  UiEffects.Gradient first=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
  assertSame(first,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT));
  assertSame(first,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER));
  assertSame(first,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW));
  settings.direction.setValue("Up");
  UiEffects.Gradient changed=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
  assertNotSame(first,changed);assertEquals(-2,changed.axis);
  advanceTo(100000000L);
  UiEffects.Gradient next=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
  assertNotSame(changed,next);assertEquals(.025f,next.phase,.000001f);
  settings.reducedMotion.setValue(true);
  UiEffects.Gradient frozen=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
  advanceTo(1000000000L);
  assertSame(frozen,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT));
 }

 @Test public void cachedLocalThemeSwitchesToGlobalPhaseImmediatelyDuringPreview(){
  me.youngjibbit.theme.ThemeCatalog previousCatalog=me.youngjibbit.theme.ThemeCatalog.global();
  me.youngjibbit.theme.ThemeCatalog catalog=new me.youngjibbit.theme.ThemeCatalog(null);
  me.youngjibbit.theme.ThemeCatalog.install(catalog);
  try{
   Theme local=Theme.custom("custom:00000000-0000-0000-0000-000000000023","Preview clock",java.util.Arrays.asList(java.awt.Color.RED,java.awt.Color.BLUE),false,true,.3f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.GREEN,.75f,9,.8f,1);
   settings.gradientMotion.setValue("Theme");advanceTo(1000000000L);
   UiEffects.Gradient inherited=UiEffects.gradient(local,UiEffects.Channel.TEXT);
   assertEquals(.3f,inherited.phase,.00001);
   catalog.preview(local);
   UiEffects.Gradient preview=UiEffects.gradient(local,UiEffects.Channel.TEXT);
   assertNotSame(inherited,preview);assertEquals(0,preview.phase,0);
   catalog.cancelPreview();
   assertEquals(.3f,UiEffects.gradient(local,UiEffects.Channel.TEXT).phase,.00001);
  }finally{me.youngjibbit.theme.ThemeCatalog.install(previousCatalog);}
 }

 @Test public void separateChannelsAndSameIdThemeReplacementsKeepTheirOwnValues()throws Exception{
  settings.synchronize.setValue(false);settings.textMotion.setValue("Solid");settings.glowMotion.setValue("Animated");settings.glowSpeed.setValue(.5);
  assertEquals(0,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).mode);
  advanceTo(1000000000L);
  assertEquals(.5f,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,.00001);
  settings.synchronize.setValue(true);settings.gradientMotion.setValue("Theme");
  String id="custom:00000000-0000-0000-0000-000000000024";
  Theme first=Theme.custom(id,"First",java.util.Arrays.asList(java.awt.Color.RED,java.awt.Color.BLUE),false,true,.3f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.GREEN,.75f,9,.8f,1);
  Theme replacement=Theme.custom(id,"Replacement",java.util.Arrays.asList(java.awt.Color.RED,java.awt.Color.BLUE),true,true,.7f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.GREEN,.75f,9,.8f,1);
  assertEquals(.3f,UiEffects.gradient(first,UiEffects.Channel.TEXT).phase,.00001);
  assertEquals(.7f,UiEffects.gradient(replacement,UiEffects.Channel.TEXT).phase,.00001);
  settings.direction.setValue("Theme");
  assertEquals(1,UiEffects.gradient(first,UiEffects.Channel.TEXT).axis);
  assertEquals(2,UiEffects.gradient(replacement,UiEffects.Channel.TEXT).axis);
  for(Theme theme:Theme.values())UiEffects.gradient(theme,UiEffects.Channel.TEXT);
  Field cache=UiEffects.class.getDeclaredField("gradientCache");cache.setAccessible(true);
  assertTrue(((java.util.Map<?,?>)cache.get(null)).size()<=16);
 }


 @Test public void categoryCoordinateDomainIsSharedAndRestoresAfterClose(){
  settings.direction.setValue("Vertical");settings.gradientMotion.setValue("Static");
  UiEffects.Gradient border=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER);
  assertEquals(.25,UiEffects.gradientPosition(border,10,25,100,100),1e-5);
  try(UiEffects.GradientSpace column=UiEffects.gradientSpace(10,20,80,200)){
   assertEquals(.5,UiEffects.gradientPosition(border,50,120,100,100),1e-5);
   UiEffects.Gradient glow=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW);
   assertEquals(UiEffects.gradientPosition(border,50,120,100,100),UiEffects.gradientPosition(glow,50,120,100,100),0);
   try(UiEffects.GradientSpace nested=UiEffects.gradientSpace(0,0,40,40)){
    assertEquals(.5,UiEffects.gradientPosition(border,10,20,100,100),1e-5);
   }
   assertEquals(.5,UiEffects.gradientPosition(border,50,120,100,100),1e-5);
  }
  assertEquals(.25,UiEffects.gradientPosition(border,10,25,100,100),1e-5);
 }
 @Test public void categoryMappingKeepsExplicitDirectionModesAndFrozenClock(){
  try(UiEffects.GradientSpace column=UiEffects.gradientSpace(20,30,100,200)){
   settings.direction.setValue("Horizontal");settings.gradientMotion.setValue("Static");
   UiEffects.Gradient horizontal=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER);
   assertEquals(.75,UiEffects.gradientPosition(horizontal,95,80,500,500),1e-5);
   settings.direction.setValue("Up");
   assertEquals(.75,UiEffects.gradientPosition(UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER),95,80,500,500),1e-5);
   settings.gradientMotion.setValue("Solid");
   assertEquals(0,UiEffects.gradientPosition(UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER),95,80,500,500),0);
   settings.gradientMotion.setValue("Animated");advanceTo(1000000000L);
   float phase=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase;
   settings.ambientMotion.setValue(false);advanceTo(2000000000L);
   assertEquals(phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,0);
  }
 }

}
