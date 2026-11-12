package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.AutomationEligibility;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.player.MovementReadyEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.module.impl.world.NScaffold;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Real shared input, Entity movement, walking transport and model hooks with isolated synthetic scenes. */
public class BedBreakerDemiseHookChecks {
    private static Field field(Class<?> type,String name) throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static final class Run implements AutoCloseable {
        final float oldForward=MovementInput.moveForward, oldStrafe=MovementInput.moveStrafe;
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        final ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.5);
        final DemiseOriginalRotation.Clock clock=new DemiseOriginalRotation.Clock();
        final BedBreaker bed=new BedBreaker();
        final DemiseRotationEngine engine=DemiseRotationEngine.continuous(() -> clock.millis,() -> clock.nanos,(a,b) -> a);
        final RotationManager rotation=f.runtime.getRotationManager();
        final Map<Class<? extends Module>,Module> modules;
        NScaffold scaffold;
        @SuppressWarnings("unchecked") Run() throws Exception {
            field(Module.class,"toggled").setBoolean(f.aura,false);
            field(Module.class,"toggled").setBoolean(f.scaffold,false);
            modules=(Map<Class<? extends Module>,Module>)field(ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
            modules.put(BedBreaker.class,bed);
            GameSettings keys=f.mc.gameSettings;
            keys.keyBindForward=new KeyBinding("bed.demise.forward",17,"test");
            keys.keyBindBack=new KeyBinding("bed.demise.back",31,"test");
            keys.keyBindLeft=new KeyBinding("bed.demise.left",30,"test");
            keys.keyBindRight=new KeyBinding("bed.demise.right",32,"test");
            keys.keyBindJump=new KeyBinding("bed.demise.jump",57,"test");
            keys.keyBindSneak=new KeyBinding("bed.demise.sneak",42,"test");
            keys.keyBindSprint=new KeyBinding("bed.demise.sprint",29,"test");
            keys.keyBindUseItem=new KeyBinding("bed.demise.use",-99,"test");
            keys.keyBindAttack=new KeyBinding("bed.demise.attack",-100,"test");
            f.player.inventoryContainer=new ContainerPlayer(f.player.inventory,false,f.player);
            f.player.openContainer=f.player.inventoryContainer;
            f.player.movementInput=new MovementInputFromOptions(keys);
            MovementInput.moveForward=MovementInput.moveStrafe=0;
            field(BedBreaker.class,"rotationEngine").set(bed,engine);
            activate(bed);
        }
        void activate(Module module) throws Exception {
            f.runtime.getObservation().beginOuterTick();
            try { field(Module.class,"toggled").setBoolean(module,true); module.onEnable(); }
            finally { f.runtime.getObservation().endOuterTick(); }
        }
        void disable() throws Exception { field(Module.class,"toggled").setBoolean(bed,false); bed.onDisable(); }
        void aim(float yaw,float pitch,String correction) {
            bed.rotationSettings.moveCorrection.setValue(correction);
            engine.updateSpeeds(bed.rotationSettings.snapshot());
            engine.setRotation(new float[]{yaw,pitch},bed.rotationSettings.snapshot());
        }
        void frame() {
            clock.millis+=17; clock.nanos+=17_000_000L;
            DemiseRotationHooks.mousePre(); DemiseRotationHooks.mouseUpdate();
        }
        void inactiveFrame() {
            clock.millis+=17; clock.nanos+=17_000_000L;
            DemiseRotationHooks.mousePre(); DemiseRotationHooks.mouseInactive();
        }
        void warm() {
            for (int i=0;i<12;i++) {
                aim(140,70,"Silent"); frame(); if(i%3==0) step(f.player::onUpdateWalkingPlayer);
            }
            step(f.player::onUpdateWalkingPlayer); drain();
        }
        void step(Runnable action) {
            RuntimeObservation observation=f.runtime.getObservation();
            scene.motionTime.addAndGet(50_000_000L); observation.beginOuterTick();
            try { observation.observePlayerStep(f.player,() -> { action.run(); return true; },s -> s.getAsBoolean()); }
            finally { observation.endOuterTick(); }
            f.player.ticksExisted++;
        }
        List<Object> drain() {
            List<Object> packets=new ArrayList<>(); Object packet;
            while((packet=f.channel.readOutbound())!=null) packets.add(packet);
            return packets;
        }
        DemiseRotationEngine installScaffold() throws Exception {
            scaffold=new NScaffold(); modules.put(NScaffold.class,scaffold);
            DemiseRotationEngine other=new DemiseRotationEngine(() -> clock.millis,() -> clock.nanos,(a,b) -> a);
            field(NScaffold.class,"rotationEngine").set(scaffold,other); activate(scaffold);
            other.updateSpeeds(scaffold.rotationSettings.snapshot());
            other.setRotation(new float[]{-110,50},scaffold.rotationSettings.snapshot());
            return other;
        }
        @Override public void close() throws Exception {
            if(bed.isToggled()) disable();
            engine.clear(f.player.rotationYaw,f.player.rotationPitch);
            if(scaffold!=null && scaffold.isToggled()) {
                field(Module.class,"toggled").setBoolean(scaffold,false); scaffold.onDisable();
                scaffold.rotationEngine.clear(f.player.rotationYaw,f.player.rotationPitch);
            }
            f.close(); MovementInput.moveForward=oldForward; MovementInput.moveStrafe=oldStrafe;
        }
    }
    private static final class OtherOwner implements Subscriber {
        @Subscribe final Listener<EventRotation> request;
        OtherOwner(RotationManager rotation) { request=new Listener<>(event -> rotation.request("aura",100,-70,20)); }
    }
    private static final class RejectMovement implements Subscriber {
        @Subscribe final Listener<EventPacket> reject=new Listener<>(event -> {
            if(event.getPacket() instanceof C03PacketPlayer) event.cancel();
        });
    }
    private static final class SuspendedCallbacks implements Subscriber {
        int rotations, readiness, physics, motions;
        @Subscribe final Listener<EventRotation> rotation;
        @Subscribe final Listener<MovementReadyEvent> ready=new Listener<>(event -> readiness++);
        @Subscribe final Listener<EventMotion> motion;
        SuspendedCallbacks(RotationManager manager,DemiseRotationEngine engine) {
            rotation=new Listener<>(event -> {
                rotations++; event.beforePhysics(() -> physics++);
                manager.request("aura",100,-140,70);
            });
            motion=new Listener<>(event -> {
                if(event.isPre() && engine.shouldRotate()) { motions++; manager.request("aura",100,-140,70); }
            });
        }
    }

    @Test public void disabledTailUsesOnePoseForWireModelLookAndManualCamera() throws Exception {
        try(Run r=new Run()) {
            r.warm(); float[] before=r.engine.rotation(); r.disable();
            assertTrue(r.engine.returning()); assertArrayEquals(before,r.engine.rotation(),0);
            r.f.player.setAngles(40,20);
            assertEquals(6,r.f.player.rotationYaw,0); assertEquals(-3,r.f.player.rotationPitch,0);
            r.frame(); assertTrue(distance(before,r.engine.rotation())<=12.0001);
            float[] aim=r.engine.rotation(); r.step(r.f.player::onUpdateWalkingPlayer);
            assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
            assertEquals(aim[0],r.rotation.getEffectiveYaw(),0); assertEquals(aim[1],r.rotation.getEffectivePitch(),0);
            assertEquals(aim[0],r.f.player.rotationYawHead,0); assertEquals(aim[1],r.f.player.rotationPitchHead,0);
            assertEquals(aim[0],DemiseRotationHooks.modelYaw(r.f.player,0),0);
            assertNull(RotationCorrections.renderPose(r.f.player,0));
            Vec3 expected=Entity.getVectorForRotation(aim[1],aim[0]),look=r.f.player.getLook(1);
            assertEquals(expected.xCoord,look.xCoord,0); assertEquals(expected.yCoord,look.yCoord,0); assertEquals(expected.zCoord,look.zCoord,0);
            r.f.player.prevRotationPitchHead=0;
            assertEquals(aim[1]*.5f,DemiseRotationHooks.renderPitch(r.f.player,.5f,-3),0);
            boolean sent=false;
            for(Object packet:r.drain()) {
                assertFalse("a disabled tail must never mine",packet instanceof C07PacketPlayerDigging);
                if(packet instanceof C03PacketPlayer) {
                    C03PacketPlayer c03=(C03PacketPlayer)packet; sent=true;
                    assertTrue(c03.getRotating()); assertEquals(aim[0],c03.getYaw(),0); assertEquals(aim[1],c03.getPitch(),0);
                }
            }
            assertTrue(sent);
            for(int i=0;i<500 && r.engine.shouldRotate();i++) { r.frame(); if(i%3==0) r.step(r.f.player::onUpdateWalkingPlayer); }
            assertFalse("the release must finish without physical camera help",r.engine.shouldRotate());
            r.step(r.f.player::onUpdateWalkingPlayer);
            assertNull(r.rotation.getConfirmedOwner()); assertEquals(6,r.rotation.getEffectiveYaw(),0); assertEquals(-3,r.rotation.getEffectivePitch(),0);
            assertEquals(6,r.f.player.rotationYaw,0); assertEquals(-3,r.f.player.rotationPitch,0);
        }
    }

    @Test public void allCorrectionModesUseRawKeysBeforeSneakAndOnePhysicsTransform() throws Exception {
        try(Run r=new Run()) {
            r.f.player.rotationYaw=120; r.engine.clear(120,0);
            r.aim(-90,65,"Silent"); r.frame(); r.disable();
            DemiseOriginalMoveFix oracle=new DemiseOriginalMoveFix();
            for(String correction:new String[]{"Silent","Strict","None"}) for(boolean sneak:new boolean[]{false,true}) {
                r.aim(-90,65,correction); r.engine.beginReturn();
                r.f.mc.gameSettings.keyBindForward.pressed=true; r.f.mc.gameSettings.keyBindLeft.pressed=true;
                r.f.mc.gameSettings.keyBindSneak.pressed=sneak;
                r.step(() -> {
                    r.rotation.prepareMovement(r.f.player);
                    assertNotNull(r.rotation.getPreparedMovement());
                    assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getPreparedMovement().source);
                    float yaw=r.rotation.getPreparedMovement().yaw;
                    DemiseOriginalMoveFix.MoveInputEvent expected=new DemiseOriginalMoveFix.MoveInputEvent(1,1);
                    if("Silent".equals(correction)) oracle.fixMovement(expected,yaw,r.f.player.rotationYaw);
                    float scale=sneak?.3f:1,strafe=expected.strafe*scale,forward=expected.forward*scale;
                    r.f.player.movementInput.updatePlayerMoveState();
                    assertEquals(strafe,MovementInput.moveStrafe,0); assertEquals(forward,MovementInput.moveForward,0);
                    assertEquals(forward,RotationCorrections.sprintForward(r.f.player.rotationYaw,strafe,forward),0);
                    r.f.player.motionX=r.f.player.motionZ=0;
                    r.f.player.moveFlying(MovementInput.moveStrafe,MovementInput.moveForward,.1f);
                    float physicsYaw="None".equals(correction)?r.f.player.rotationYaw:yaw;
                    float factor=.1f/Math.max(1,MathHelper.sqrt_float(strafe*strafe+forward*forward));
                    float sine=MathHelper.sin(physicsYaw*(float)Math.PI/180),cosine=MathHelper.cos(physicsYaw*(float)Math.PI/180);
                    assertEquals(strafe*factor*cosine-forward*factor*sine,r.f.player.motionX,2e-6);
                    assertEquals(forward*factor*cosine+strafe*factor*sine,r.f.player.motionZ,2e-6);
                    r.f.player.motionX=r.f.player.motionZ=0; r.f.player.sprint=true; r.f.player.jump();
                    assertEquals(-MathHelper.sin(physicsYaw*.017453292f)*.2f,r.f.player.motionX,2e-6);
                    assertEquals(MathHelper.cos(physicsYaw*.017453292f)*.2f,r.f.player.motionZ,2e-6);
                    r.f.player.onUpdateWalkingPlayer();
                });
                assertEquals(120,r.f.player.rotationYaw,0); assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
            }
        }
    }

    @Test public void scaffoldAndForeignOwnersDoNotShareTargetsOrPresentation() throws Exception {
        try(Run r=new Run()) {
            r.warm(); float[] original=r.engine.rotation();
            DemiseRotationEngine other=r.installScaffold();
            assertArrayEquals("binding NScaffold cannot clear BedBreaker's trajectory",original,r.engine.rotation(),0);
            r.frame(); r.step(r.f.player::onUpdateWalkingPlayer);
            assertEquals(NScaffold.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
            assertEquals(other.currentAim().yaw,DemiseRotationHooks.modelYaw(r.f.player,0),0);
            r.disable(); assertTrue(r.engine.returning()); assertTrue(other.shouldRotate());
            OtherOwner foreign=new OtherOwner(r.rotation); Pulsar.BUS.subscribe(foreign);
            try { r.step(() -> { r.rotation.prepareMovement(r.f.player); r.f.player.onUpdateWalkingPlayer(); }); }
            finally { Pulsar.BUS.unsubscribe(foreign); }
            assertEquals("aura",r.rotation.getConfirmedOwner());
            assertEquals(12,DemiseRotationHooks.modelYaw(r.f.player,12),0);
            assertEquals(7,DemiseRotationHooks.renderPitch(r.f.player,.5f,7),0);
            assertNull(DemiseRotationHooks.lookVector(r.f.player));
            other.clear(0,0); assertTrue("invalidating NScaffold leaves the independent Bed tail",r.engine.shouldRotate());
            r.step(r.f.player::onUpdateWalkingPlayer);
            assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
            assertEquals("handoff starts at the pose that really reached the wire",-70,r.rotation.getEffectiveYaw(),0);
            assertEquals(20,r.rotation.getEffectivePitch(),0);
            r.frame(); assertTrue(distance(new float[]{-70,20},r.engine.rotation())<=12.0001);
        }
    }

    @Test public void rejectedC03KeepsAnimationContinuousAndRecoveryBounded() throws Exception {
        try(Run r=new Run()) {
            r.warm(); float[] wire={r.rotation.getEffectiveYaw(),r.rotation.getEffectivePitch()};
            RejectMovement cancel=new RejectMovement(); Pulsar.BUS.subscribe(cancel);
            try {
                for(int i=0;i<12;i++) {
                    r.aim(-80,-65,"Silent"); r.frame(); float[] before=r.engine.rotation();
                    r.step(r.f.player::onUpdateWalkingPlayer);
                    assertArrayEquals("rejected transport must not rebase a visible animation backward",before,r.engine.rotation(),0);
                    assertTrue(distance(wire,r.engine.rotation())<=45.0001);
                    for(Object packet:r.drain()) assertFalse(packet instanceof C03PacketPlayer);
                }
            } finally { Pulsar.BUS.unsubscribe(cancel); }
            r.step(r.f.player::onUpdateWalkingPlayer);
            assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
            assertTrue(distance(wire,new float[]{r.rotation.getEffectiveYaw(),r.rotation.getEffectivePitch()})<=45.0001);
            assertEquals(0,r.f.player.rotationYaw,0); assertEquals(0,r.f.player.rotationPitch,0);
        }
    }

    @Test public void suspensionKeepsReturnButSceneEpochCannotReviveIt() throws Exception {
        try(Run r=new Run()) {
            r.warm(); float[] before=r.engine.rotation();
            r.f.mc.inGameHasFocus=false; DemiseRotationHooks.mousePre();
            assertTrue(r.engine.returning()); assertArrayEquals(before,r.engine.rotation(),0);
            assertNotNull("presentation follows the same-scene return",DemiseRotationHooks.lookVector(r.f.player));
            r.frame(); assertTrue(r.engine.shouldRotate()); assertTrue(distance(before,r.engine.rotation())<=12.0001);
            float[] returning=r.engine.rotation();
            r.f.mc.inGameHasFocus=true; DemiseRotationHooks.bindBed();
            assertTrue("same-scene focus return retains the release",r.engine.shouldRotate());
            assertArrayEquals("focus restoration cannot rewind to an older C03",returning,r.engine.rotation(),0);
            r.f.network.getClientPackets().worldChanged(); DemiseRotationHooks.mousePre();
            assertFalse(r.engine.shouldRotate()); assertFalse(r.engine.hasServerRotation());
            assertFalse(r.bed.correctionEnabled());
        }
    }

    @Test public void visibleSettingIsBoundedAndCannotTakeAnotherOwnersCamera() throws Exception {
        try(Run r=new Run()) {
            r.warm();
            assertTrue("the silent model must already be far from the free camera",distance(new float[]{0,0},r.engine.rotation())>60);
            r.bed.rotationSettings.silent.setValue(false);
            r.bed.rotationSettings.extraSmoothFactor.setValue(1D);
            r.aim(170,80,"Silent"); r.frame();
            assertTrue(distance(new float[]{0,0},new float[]{r.f.player.rotationYaw,r.f.player.rotationPitch})<=12.0001);
            assertTrue(Math.abs(r.f.player.rotationYaw)>0);
            assertTrue("visible mode catches the old silent pose over multiple frames",
                    distance(r.engine.rotation(),new float[]{r.f.player.rotationYaw,r.f.player.rotationPitch})>30);
            OtherOwner foreign=new OtherOwner(r.rotation); Pulsar.BUS.subscribe(foreign);
            try { r.step(() -> { r.rotation.prepareMovement(r.f.player); r.f.player.onUpdateWalkingPlayer(); }); }
            finally { Pulsar.BUS.unsubscribe(foreign); }
            float yaw=r.f.player.rotationYaw,pitch=r.f.player.rotationPitch;
            r.frame();
            assertEquals(yaw,r.f.player.rotationYaw,0); assertEquals(pitch,r.f.player.rotationPitch,0);
        }
    }

    @Test public void focusReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks() throws Exception {
        suspendedWireReturn(false);
    }

    @Test public void guiReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks() throws Exception {
        suspendedWireReturn(true);
    }

    private static void suspendedWireReturn(boolean gui) throws Exception {
        try(Run r=new Run()) {
            r.warm();
            r.f.player.rotationYaw=-95; r.f.player.rotationPitch=-35;
            float[] wire={r.rotation.getEffectiveYaw(),r.rotation.getEffectivePitch()};
            assertTrue("start with a visible camera/server separation",distance(wire,new float[]{-95,-35})>90);
            if(gui) r.f.mc.currentScreen=RotationBehaviorHookTest.bare(GuiIngameMenu.class);
            else r.f.mc.inGameHasFocus=false;
            r.f.mc.gameSettings.keyBindForward.pressed=true;
            SuspendedCallbacks callbacks=new SuspendedCallbacks(r.rotation,r.engine); Pulsar.BUS.subscribe(callbacks);
            int packets=0;
            try {
                for(int tick=0;tick<240;tick++) {
                    for(int frame=0;frame<3;frame++) {
                        float[] before=r.engine.rotation(); r.inactiveFrame();
                        assertTrue("inactive renders advance once with the same bound",distance(before,r.engine.rotation())<=12.0001);
                    }
                    final boolean returning=r.engine.shouldRotate();
                    final float[] pose=r.engine.rotation();
                    r.step(() -> {
                        assertFalse(AutomatedActionAdmission.mayAttempt(AutomationEligibility.ModuleAction.ATTACK));
                        r.bed.onDemiseUpdate();
                        r.rotation.prepareMovement(r.f.player);
                        if(returning) {
                            assertNotNull(r.rotation.getPreparedMovement());
                            assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getPreparedMovement().source);
                            float yaw=r.rotation.getPreparedMovement().yaw;
                            assertEquals(pose[0],yaw,0);
                            r.rotation.beforeMovementPhysics();
                            // A real menu suppresses held keys before the input hook; a rotation tail cannot reopen them.
                            DemiseMoveFix.Input expected=DemiseMoveFix.correct(0,gui?0:1,yaw,r.f.player.rotationYaw);
                            r.f.player.movementInput.updatePlayerMoveState();
                            assertEquals(expected.strafe,MovementInput.moveStrafe,0);
                            assertEquals(expected.forward,MovementInput.moveForward,0);
                            r.f.player.motionX=r.f.player.motionZ=0;
                            r.f.player.moveFlying(MovementInput.moveStrafe,MovementInput.moveForward,.1f);
                            float strength=.1f/Math.max(1,MathHelper.sqrt_float(expected.strafe*expected.strafe+expected.forward*expected.forward));
                            float sine=MathHelper.sin(yaw*(float)Math.PI/180),cosine=MathHelper.cos(yaw*(float)Math.PI/180);
                            assertEquals((expected.strafe*cosine-expected.forward*sine)*strength,r.f.player.motionX,2e-6);
                            assertEquals((expected.forward*cosine+expected.strafe*sine)*strength,r.f.player.motionZ,2e-6);
                        }
                        r.f.player.onUpdateWalkingPlayer();
                        assertFalse("a rotation return cannot grant action admission",AutomatedActionAdmission.mayAttempt(AutomationEligibility.ModuleAction.ATTACK));
                    });
                    int movements=0;
                    for(Object packet:r.drain()) {
                        assertFalse("suspension never continues mining",packet instanceof C07PacketPlayerDigging);
                        if(packet instanceof C03PacketPlayer) {
                            C03PacketPlayer c03=(C03PacketPlayer)packet; movements++; packets++;
                            float[] next=c03.getRotating()?new float[]{c03.getYaw(),c03.getPitch()}:wire;
                            assertTrue("the first suspended C03 must not reset to camera",distance(wire,next)<=45.0001);
                            wire=next;
                        }
                    }
                    assertEquals("one real locally accepted C03 per suspended step",1,movements);
                    if(returning) {
                        assertEquals(BedBreaker.ROTATION_SOURCE,r.rotation.getConfirmedOwner());
                        assertEquals(pose[0],r.rotation.getEffectiveYaw(),0); assertEquals(pose[1],r.rotation.getEffectivePitch(),0);
                        assertEquals(pose[0],DemiseRotationHooks.modelYaw(r.f.player,-95),0);
                        assertNotNull(DemiseRotationHooks.lookVector(r.f.player));
                    } else break;
                }
            } finally { Pulsar.BUS.unsubscribe(callbacks); }
            assertTrue(packets>2); assertFalse("suspended rendering must finish the return",r.engine.shouldRotate());
            assertEquals(0,callbacks.rotations); assertEquals(0,callbacks.readiness); assertEquals(0,callbacks.physics);
            assertTrue("ordinary Motion callbacks existed but could not steal the suspended return",callbacks.motions>0);
            assertEquals(-95,r.rotation.getEffectiveYaw(),0); assertEquals(-35,r.rotation.getEffectivePitch(),0);
            assertEquals(-95,r.f.player.rotationYaw,0); assertEquals(-35,r.f.player.rotationPitch,0);
        }
    }

    private static double distance(float[] a,float[] b) {
        return Math.hypot(MathHelper.wrapAngleTo180_float(a[0]-b[0]),a[1]-b[1]);
    }
}
