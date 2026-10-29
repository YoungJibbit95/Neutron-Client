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
 @Test public void localAnimatedThemeUsesItsOwnSpeedAndThePausedUiClock(){
  Theme local=Theme.custom("custom:00000000-0000-0000-0000-000000000022","Local clock",java.util.Arrays.asList(java.awt.Color.RED,java.awt.Color.BLUE),false,true,.3f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.GREEN,.75f,9,.8f,1);
  settings.gradientMotion.setValue("Theme");advanceTo(1000000000L);assertEquals(.3f,UiEffects.gradient(local,UiEffects.Channel.TEXT).phase,.00001);
  settings.reducedMotion.setValue(true);clock.set(5000000000L);UiEffects.beginFrame();assertEquals(.3f,local.phase(),.00001);
 }

}
