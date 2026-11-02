package me.youngjibbit.core.world;

import com.google.gson.*;
import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.*;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.ghost.*;
import me.youngjibbit.module.impl.movement.Sprint;
import me.zero.alpine.listener.Listener;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CombatProfileTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"settingsRoundTrip"},{"legacySettingsReset"},{"wTapLifecycle"},
                {"wTapCooldownRangeAndRawInput"},{"cancelledAttackEvent"},{"sideTapCannotSprint"},
                {"savedProfile"},{"withoutWTap"},{"withoutJumpReset"},{"withoutBoth"},{"withVelocity"},
                {"allClickModesThroughAura"},{"clickerCallbacks"},{"clickerDeniedExtraRetainsSample"}});
    }
    private final String method;
    public CombatProfileTest(String method) { this.method=method; }
    @Test public void freshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }
    public static class DebugPlayer extends CombatFollowupTest.PhysicsPlayer {
        int messages;
        @Override public void addChatComponentMessage(net.minecraft.util.IChatComponent message) { messages++; }
    }
    static ClickTimingSettings timing(Module module) throws Exception {
        return (ClickTimingSettings)ScaffoldOwnerContinuityTest.field(module.getClass(),"clickTiming").get(module);
    }
    static void seedClicks(Module module,AtomicLong clock) throws Exception {
        ScaffoldOwnerContinuityTest.field(ClickTimingSettings.class,"randomizer").set(timing(module),
                new ClickRandomizer(new Random(17),new Random(19),clock::get));
    }
    static Path temporary() throws Exception {
        Path base=Paths.get(System.getProperty("user.dir")).resolve("combat-profile-cases");
        Files.createDirectories(base);return Files.createTempDirectory(base,"case-");
    }
    static ConfigManager config(Path path,Module... modules) {
        return new ConfigManager(path,Arrays.asList(modules),()->true,s->{});
    }
    static void save(Path dir,String json) throws Exception {
        Files.write(dir.resolve("case.json"),json.getBytes(StandardCharsets.UTF_8));
    }
    static AtomicLong tapClock(WTap2 tap) throws Exception {
        AtomicLong now=new AtomicLong(1000);
        ScaffoldOwnerContinuityTest.field(WTap2.class,"legitClock").set(tap,(LongSupplier)now::get);return now;
    }
    static void arm(AuraMovementConsistencyTest.Run r) {
        r.player.sprint=true;
        assertEquals("positive control: controller and C02 accepted",1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
    }
    public static class Checks {
        @Test public void settingsRoundTrip() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                Module aura=r.f.run.fixture.aura;AutoClicker2 clicker=new AutoClicker2();
                aura.setToggled(false);Path dir=temporary();ConfigManager config=config(dir,aura,clicker);
                for(String mode:ClickRandomizer.modes()) {
                    ClickTimingSettings a=timing(aura),c=timing(clicker);
                    a.mode.setValue(mode);c.mode.setValue(mode);a.enabled.setValue(false);c.enabled.setValue(true);
                    a.amount.setValue(4.5);c.amount.setValue(2.5);a.debugging.setValue(true);c.debugging.setValue(false);
                    aura.getBooleanSetting("randomization").setValue(true);
                    aura.getNumberSetting("Max CPS").setValue(9D);aura.getNumberSetting("Min CPS").setValue(12D);
                    aura.getBooleanSetting("Custom Block Frequency").setValue(true);aura.getNumberSetting("Block Frequency").setValue(10D);
                    assertFalse("hidden values must still persist",a.amount.isVisible());
                    assertTrue(config.saveConfig("case"));a.mode.setValue("Basic");c.mode.setValue("Basic");a.amount.setValue(.5);
                    assertTrue(config.loadConfig("case"));
                    assertEquals(mode,a.mode.getValue());assertEquals(mode,c.mode.getValue());
                    assertEquals(4.5,a.amount.getValue(),0);assertEquals(2.5,c.amount.getValue(),0);
                    assertFalse(a.enabled.getValue());assertTrue(c.enabled.getValue());assertTrue(a.debugging.getValue());assertFalse(c.debugging.getValue());
                    assertEquals(9,a.snapshot().min,0);assertEquals(12,a.snapshot().max,0);
                    assertEquals(10,aura.getNumberSetting("Block Frequency").getValue(),0);
                    assertTrue(aura.getBooleanSetting("Custom Block Frequency").getValue());
                    assertTrue("rotation randomization retains its boolean type and value",aura.getBooleanSetting("randomization").getValue());
                    assertNotSame(a.mode,c.mode);assertSame(aura,a.mode.getParent());assertSame(clicker,c.mode.getParent());
                    Set<String> ids=new HashSet<>();for(me.youngjibbit.value.Value<?> v:aura.getValues())assertTrue("unique setting ID "+v.getId(),ids.add(v.getId()));
                }
            }
        }
        @Test public void legacySettingsReset() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                Module aura=r.f.run.fixture.aura;aura.setToggled(false);ClickTimingSettings a=timing(aura);
                Path dir=temporary();ConfigManager config=config(dir,aura);
                for(String settings:new String[]{"\"min_cps\":12,\"max_cps\":9","\"max_cps\":9,\"min_cps\":12"}) {
                    a.mode.setValue("Pi");a.enabled.setValue(true);a.debugging.setValue(true);a.amount.setValue(5D);
                    save(dir,"{\"version\":2,\"modules\":{\"aura\":{\"enabled\":false,\"rotationVersion\":2,\"settings\":{"+settings+",\"randomization\":false}}}}");
                    assertTrue(config.loadConfig("case"));assertEquals("Basic",a.mode.getValue());assertFalse(a.enabled.getValue());assertFalse(a.debugging.getValue());
                    assertEquals(3,a.amount.getValue(),0);assertEquals(9,a.snapshot().min,0);assertEquals(12,a.snapshot().max,0);
                    assertFalse(aura.getBooleanSetting("randomization").getValue());assertFalse(aura.getBooleanSetting("Custom Block Frequency").getValue());
                }
            }
        }
        @Test public void cancelledAttackEvent() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);
                Listener<AttackEvent> cancel=new Listener<>(AttackEvent.class,e->e.cancel(),me.zero.alpine.event.EventPriority.LOWEST);
                Pulsar.BUS.subscribe(cancel);r.player.sprint=true;
                try {
                    assertEquals(0,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                    r.physical(false);assertEquals(1,MovementInput.moveForward,0);
                    assertTrue(r.f.run.fixture.mc.gameSettings.keyBindForward.pressed);
                } finally { Pulsar.BUS.unsubscribe(cancel);tap.setToggled(false); }
            }
        }
        @Test public void wTapLifecycle() throws Exception {
            for(int transition=0;transition<5;transition++)
                try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                    r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);arm(r);
                    try {
                        if(transition==0) { tap.setToggled(false);tap.setToggled(true); }
                        if(transition==1) { tap.getModeSetting("Mode").setValue("Spoof");tap.getModeSetting("Mode").setValue("Legit"); }
                        if(transition==2) { r.f.run.fixture.mc.inGameHasFocus=false;Pulsar.BUS.post(new TickEvent());r.f.run.fixture.mc.inGameHasFocus=true; }
                        if(transition==3) Pulsar.BUS.post(new WorldEvent.Unload(r.f.run.scene));
                        if(transition==4) r.f.run.fixture.network.getClientPackets().worldChanged();
                        r.physical(false);assertEquals("transition "+transition+" drops pending input",1,MovementInput.moveForward,0);
                        assertTrue(r.f.run.fixture.mc.gameSettings.keyBindForward.pressed);
                        assertTrue(r.f.run.fixture.mc.gameSettings.keyBindSprint.pressed);
                    } finally { tap.setToggled(false); }
                }
        }
        @Test public void wTapCooldownRangeAndRawInput() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);AtomicLong now=tapClock(tap);
                try {
                    arm(r);Pulsar.BUS.post(new TickEvent());
                    MovementInputEvent first=new MovementInputEvent(1,1,false,false);Pulsar.BUS.post(first);assertEquals(0,first.getForward(),0);assertEquals(1,first.getStrafe(),0);
                    MovementInputEvent extra=new MovementInputEvent(1,1,false,false);Pulsar.BUS.post(extra);assertEquals("extra player step in same outer tick cannot repeat tap",1,extra.getForward(),0);
                    now.set(1427);arm(r);MovementInputEvent cooldown=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(cooldown);assertEquals(1,cooldown.getForward(),0);
                    now.set(1428);arm(r);MovementInputEvent boundary=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(boundary);assertEquals(0,boundary.getForward(),0);
                    now.set(1856);arm(r);MovementInputEvent released=new MovementInputEvent(-1,-1,false,false);Pulsar.BUS.post(released);assertEquals("new backward input is not a held W key",-1,released.getForward(),0);
                    tap.getNumberSetting("Min Distance").setValue(3D);arm(r);
                    MovementInputEvent distance=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(distance);assertEquals(1,distance.getForward(),0);
                } finally { tap.setToggled(false); }
            }
        }
        @Test public void sideTapCannotSprint() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Rounded")) {
                r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);Sprint sprint=new Sprint();sprint.setToggled(true);
                try {
                    arm(r);r.input.forward=1;r.input.strafe=1;r.player.rotationYaw=-90;
                    Pulsar.BUS.post(new EventUpdate());r.physical(false);
                    assertEquals(0,MovementInput.moveForward,0);assertEquals(1,MovementInput.moveStrafe,0);
                    assertFalse("corrected side key plus Sprint must not re-enable sprint for tapped physics",r.physicsSprint);
                    assertFalse(r.player.isServerSprintState());
                } finally { tap.setToggled(false);sprint.setToggled(false); }
            }
        }
        @Test public void savedProfile() throws Exception { profile(true,true,false); }
        @Test public void withoutWTap() throws Exception { profile(false,true,false); }
        @Test public void withoutJumpReset() throws Exception { profile(true,false,false); }
        @Test public void withoutBoth() throws Exception { profile(false,false,false); }
        @Test public void withVelocity() throws Exception { profile(false,false,true); }
        @SuppressWarnings("unchecked") private void profile(boolean useTap,boolean useJump,boolean useVelocity) throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict",CombatFollowupTest.PhysicsPlayer.class)) {
                r.remoteTarget();CombatFollowupTest.prepareNetwork(r);r.player.jumpMovementFactor=.02f;
                ScaffoldOwnerContinuityTest.field(Entity.class,"rand").set(r.player,new Random(7));
                ScaffoldOwnerContinuityTest.field(net.minecraft.entity.EntityLivingBase.class,"activePotionsMap")
                        .set(r.f.run.scene.getEntityByID(19),new HashMap<>());
                WTap2 tap=CombatFollowupTest.tap(r);tap.setToggled(false);AtomicLong wTapTime=tapClock(tap);
                JumpReset jump=new JumpReset(r.f.run.scene.motionTime::get,()->.25);Sprint sprint=new Sprint();
                HitDelayFix hit=new HitDelayFix();Velocity velocity=new Velocity();AutoClicker2 clicker=new AutoClicker2();
                List<Module> modules=Arrays.asList(r.f.run.fixture.aura,tap,jump,sprint,hit,velocity,clicker);
                Map<Class<? extends Module>,Module> registry=(Map<Class<? extends Module>,Module>)ScaffoldOwnerContinuityTest.field(ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
                for(Module module:modules)registry.put(module.getClass(),module);
                Path dir=temporary();
                try(java.io.InputStream resource=CombatProfileTest.class.getResourceAsStream("/combat/mineblaze1v1-modules.json")) {
                    assertNotNull(resource);Files.copy(resource,dir.resolve("case.json"));
                }
                ConfigManager config=config(dir,modules.toArray(new Module[0]));assertTrue(config.loadConfig("case"));
                try {
                    assertFalse("saved Aura-off is a negative control, not the reported runtime state",r.f.run.fixture.aura.isToggled());
                    assertEquals(0,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                    assertTrue(tap.isToggled());assertTrue(jump.isToggled());assertTrue(sprint.isToggled());assertTrue(hit.isToggled());assertFalse(velocity.isToggled());
                    assertEquals("Advanced",r.f.run.fixture.aura.getModeSetting("rot_mode").getValue());
                    assertEquals("Bezier",r.f.run.fixture.aura.getModeSetting("speed_curve").getValue());
                    assertEquals(1.38,r.f.run.fixture.aura.getNumberSetting("hit_vec_radius").getValue(),0);
                    // Documented runtime override and individual ablations; never toggle shared key 11.
                    tap.setToggled(useTap);jump.setToggled(useJump);velocity.setToggled(useVelocity);r.f.run.fixture.aura.setToggled(true);
                    seedClicks(r.f.run.fixture.aura,r.f.clock);int attacks=0;
                    for(int step=0;step<24;step++) {
                        r.input.jump=false;
                        if(step%8==0) {
                            // Three equal eight-step impulses. Let jump/air motion evolve between them.
                            r.player.setPosition(.5,64,.5);r.player.onGround=true;r.player.sprint=true;
                            r.f.run.fixture.channel.writeInbound(new S0BPacketAnimation(r.f.run.scene.getEntityByID(19),0));
                            r.f.run.fixture.channel.writeInbound(new S19PacketEntityStatus(r.player,(byte)2));
                            CombatFollowupTest.velocity(r,.24,.15,-.16);
                        }
                        final double incomingX=r.player.motionX,incomingZ=r.player.motionZ;
                        r.f.scheduler.beginTick();r.f.clock.addAndGet(50);r.f.run.scene.motionTime.addAndGet(50_000_000L);
                        wTapTime.addAndGet(50);
                        Pulsar.BUS.post(new TickEvent());Pulsar.BUS.post(new EventUpdate());
                        me.youngjibbit.core.observation.RuntimeObservation observation=r.f.run.fixture.runtime.getObservation();
                        observation.beginOuterTick();
                        try { observation.observePlayerStep(r.player,()->{
                            try { r.player.onLivingUpdate();fail(); }catch(AuraMovementConsistencyTest.BeforePhysics expected) { }
                            // Pending JumpReset/Velocity may request a normal jump; neither rescales S12 X/Z.
                            assertEquals(incomingX,r.player.motionX,1e-9);assertEquals(incomingZ,r.player.motionZ,1e-9);
                            if(r.input.jump)r.player.jump();
                            r.player.moveEntityWithHeading(MovementInput.moveStrafe*.98f,MovementInput.moveForward*.98f);
                            double x=r.player.motionX,y=r.player.motionY,z=r.player.motionZ;boolean sprintBefore=r.player.isSprinting();
                            r.player.onUpdateWalkingPlayer();
                            boolean attacked=r.f.scheduler.hasAttackThisTick();double factor=attacked&&sprintBefore?.6:1;
                            CombatFollowupTest.impulse(r.player,x*factor,y,z*factor);
                            assertEquals(r.move.getYaw(),r.f.run.fixture.runtime.getRotationManager().getEffectiveYaw(),.001);
                            return true;
                        },s->s.getAsBoolean()); }finally { observation.endOuterTick(); }
                        long count=r.f.run.drain().stream().filter(C02PacketUseEntity.class::isInstance).count();assertTrue(count<=1);attacks+=count;
                    }
                    assertTrue("profile must reach real Aura/controller attacks",attacks>0);
                } finally { for(Module module:modules)module.setToggled(false); }
            }
        }
        @Test public void allClickModesThroughAura() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                ClickTimingSettings settings=timing(r.f.run.fixture.aura);seedClicks(r.f.run.fixture.aura,r.f.clock);
                for(String mode:ClickRandomizer.modes()) {
                    settings.mode.setValue(mode);settings.enabled.setValue(true);
                    r.f.run.fixture.aura.getNumberSetting("Min CPS").setValue(9D);r.f.run.fixture.aura.getNumberSetting("Max CPS").setValue(12D);
                    r.f.scheduler.reset();long last=Long.MIN_VALUE;int count=0;
                    for(int tick=0;tick<30;tick++) {
                        r.f.clock.addAndGet(-100); // SelectionRun.walk advances 150 ms: net 50 ms.
                        List<Object> packets=r.f.walk();long attacks=packets.stream().filter(C02PacketUseEntity.class::isInstance).count();assertTrue(attacks<=1);
                        if(attacks==1) {
                            if(last!=Long.MIN_VALUE)assertTrue(mode+" respects sampled interval",r.f.clock.get()-last>=83);
                            last=r.f.clock.get();count++;
                        }
                    }
                    assertTrue(mode+" produces bounded attacks",count>=5&&count<=16);
                }
            }
        }
        @Test public void clickerCallbacks() throws Exception {
            for(boolean disable:new boolean[]{false,true})
                try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict",DebugPlayer.class)) {
                    r.f.run.fixture.aura.setToggled(false);
                    AutoClicker2 clicker=new AutoClicker2();clicker.getBooleanSetting("Debugging").setValue(true);
                    clicker.getBooleanSetting("AutoBlock").setValue(true);clicker.getNumberSetting("Block Frequency").setValue(10D);
                    clicker.setToggled(true);r.f.run.fixture.mc.gameSettings.keyBindAttack.pressed=true;
                    r.f.run.fixture.mc.objectMouseOver=new net.minecraft.util.MovingObjectPosition(r.f.first);
                    ((me.youngjibbit.utils.TimerUtil)ScaffoldOwnerContinuityTest.field(AutoClicker2.class,"clickTimer").get(clicker)).lastMS=0;
                    java.lang.reflect.Field client=ScaffoldOwnerContinuityTest.field(Pulsar.class,"mc");Object previous=client.get(Pulsar.INSTANCE);client.set(Pulsar.INSTANCE,r.f.run.fixture.mc);
                    Listener<AttackEvent> callback=new Listener<>(AttackEvent.class,e->{
                        if(disable)clicker.setToggled(false);else clicker.getNumberSetting("Randomization").setValue(4D);
                    });Pulsar.BUS.subscribe(callback);
                    me.youngjibbit.core.observation.RuntimeObservation observation=r.f.run.fixture.runtime.getObservation();
                    observation.beginOuterTick();
                    try {
                        observation.observePlayerStep(r.player,()->{Pulsar.BUS.post(new EventUpdate());return true;},s->s.getAsBoolean());
                        assertEquals(1,r.f.run.drain().stream().filter(C02PacketUseEntity.class::isInstance).count());
                        assertEquals(disable?0:1,((DebugPlayer)r.player).messages);
                        assertEquals("disabled callback cannot resume use input",!disable,r.f.run.fixture.mc.gameSettings.keyBindUseItem.pressed);
                    } finally { observation.endOuterTick();Pulsar.BUS.unsubscribe(callback);clicker.setToggled(false);client.set(Pulsar.INSTANCE,previous); }
                }
        }
        @Test public void clickerDeniedExtraRetainsSample() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.f.run.fixture.aura.setToggled(false);AutoClicker2 clicker=new AutoClicker2();clicker.setToggled(true);
                r.f.run.fixture.mc.gameSettings.keyBindAttack.pressed=true;
                ((me.youngjibbit.utils.TimerUtil)ScaffoldOwnerContinuityTest.field(AutoClicker2.class,"clickTimer").get(clicker)).lastMS=System.currentTimeMillis()+1000;
                java.lang.reflect.Field pending=ScaffoldOwnerContinuityTest.field(AutoClicker2.class,"pendingClick");
                Object[] sample={null};AtomicInteger calls=new AtomicInteger();
                me.youngjibbit.core.observation.RuntimeObservation observation=r.f.run.fixture.runtime.getObservation();
                observation.beginOuterTick();
                try {
                    observation.observePlayerStep(r.player,()->{
                        Pulsar.BUS.post(new EventUpdate());
                        try {
                            if(calls.getAndIncrement()==0) { sample[0]=pending.get(clicker);assertNotNull(sample[0]); }
                            else assertSame("denied extra step cannot resample a pending interval",sample[0],pending.get(clicker));
                        }catch(IllegalAccessException failure) { throw new AssertionError(failure); }
                        return true;
                    },s->{s.getAsBoolean();s.getAsBoolean();});
                    assertEquals(2,calls.get());assertEquals(0,r.f.run.drain().stream().filter(C02PacketUseEntity.class::isInstance).count());
                } finally { observation.endOuterTick();clicker.setToggled(false); }
            }
        }
    }
}
