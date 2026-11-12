package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.world.NScaffold;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Real keyboard, Entity physics, walking transport and model-field consumers; synthetic world/input only. */
public class DemiseRotationHookChecks {
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static final class Run implements AutoCloseable {
        final float oldForward = MovementInput.moveForward, oldStrafe = MovementInput.moveStrafe;
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final ScaffoldClientFlowTest.Scene scene = ScaffoldClientFlowTest.prepare(f,.5,.5);
        final DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
        final NScaffold module = new NScaffold();
        final DemiseRotationEngine engine = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos, (a,b) -> a);
        final RotationManager rotation = f.runtime.getRotationManager();
        @SuppressWarnings("unchecked") Run() throws Exception {
            field(Module.class,"toggled").setBoolean(f.aura,false);
            field(Module.class,"toggled").setBoolean(f.scaffold,false);
            ((Map<Class<? extends Module>,Module>) field(ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm()))
                    .put(NScaffold.class,module);
            GameSettings keys = f.mc.gameSettings;
            keys.keyBindForward = new KeyBinding("demise.forward",17,"test");
            keys.keyBindBack = new KeyBinding("demise.back",31,"test");
            keys.keyBindLeft = new KeyBinding("demise.left",30,"test");
            keys.keyBindRight = new KeyBinding("demise.right",32,"test");
            keys.keyBindJump = new KeyBinding("demise.jump",57,"test");
            keys.keyBindSneak = new KeyBinding("demise.sneak",42,"test");
            keys.keyBindSprint = new KeyBinding("demise.sprint",29,"test");
            keys.keyBindUseItem = new KeyBinding("demise.use",-99,"test");
            keys.keyBindAttack = new KeyBinding("demise.attack",-100,"test");
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory,false,f.player);
            f.player.openContainer = f.player.inventoryContainer;
            f.player.movementInput = new MovementInputFromOptions(keys);
            MovementInput.moveForward = MovementInput.moveStrafe = 0;
            field(NScaffold.class,"rotationEngine").set(module,engine);
            enable();
        }
        void enable() throws Exception {
            // Production activation runs after Minecraft.runTick has started the observer.
            // Keep this real admission boundary; an unstarted observer is intentionally invalid.
            f.runtime.getObservation().beginOuterTick();
            try {
                field(Module.class,"toggled").setBoolean(module,true);
                module.onEnable();
            } finally { f.runtime.getObservation().endOuterTick(); }
        }
        void aim(float yaw, float pitch, String correction) {
            module.rotationSettings.moveCorrection.setValue(correction);
            engine.updateSpeeds(module.rotationSettings.snapshot());
            engine.setRotation(new float[]{yaw,pitch},module.rotationSettings.snapshot());
        }
        void frame(long millis) {
            clock.millis += millis; clock.nanos += millis * 1_000_000L;
            DemiseRotationHooks.mousePre(); DemiseRotationHooks.mouseUpdate();
        }
        void disable() throws Exception {
            field(Module.class,"toggled").setBoolean(module,false); module.onDisable();
        }
        void step(Runnable action) {
            RuntimeObservation observation = f.runtime.getObservation();
            scene.motionTime.addAndGet(50_000_000L); observation.beginOuterTick();
            try { observation.observePlayerStep(f.player,() -> { action.run(); return true; }, s -> s.getAsBoolean()); }
            finally { observation.endOuterTick(); }
        }
        List<Object> drain() {
            List<Object> result = new ArrayList<>(); Object packet;
            while ((packet = f.channel.readOutbound()) != null) result.add(packet);
            return result;
        }
        @Override public void close() throws Exception {
            if (module.isToggled()) disable();
            engine.clear(f.player.rotationYaw,f.player.rotationPitch);
            f.close(); MovementInput.moveForward = oldForward; MovementInput.moveStrafe = oldStrafe;
        }
    }

    @Test public void originalAngleProducerPreservesManualCamera() throws Exception {
        try (Run run = new Run()) {
            run.f.player.rotationYaw = 60;
            run.engine.clear(60,0);
            run.module.getModeSetting("Rotations").setValue("Reverse");
            run.module.getBooleanSetting("Static-ify").setValue(true);
            run.engine.updateSpeeds(run.module.rotationSettings.snapshot());
            assertFalse(run.engine.shouldRotate());
            run.f.player.setAngles(20,10); // Actual Entity Angle producer; no physical mouse/native context needed.
            assertTrue("the real setAngles hook must create the Demise target",run.engine.shouldRotate());
            assertEquals(63,run.f.player.rotationYaw,0); assertEquals(-1.5,run.f.player.rotationPitch,0);
            float before = run.engine.rotation()[0]; run.frame(17);
            assertTrue("zero physical delta still advances the frame rotation",run.engine.rotation()[0] != before);
            assertEquals(63,run.f.player.rotationYaw,0); assertEquals(-1.5,run.f.player.rotationPitch,0);
            net.minecraft.util.Vec3 expected=Entity.getVectorForRotation(run.engine.currentAim().pitch,run.engine.currentAim().yaw);
            net.minecraft.util.Vec3 look=run.f.player.getLook(1);
            assertEquals(expected.xCoord,look.xCoord,0); assertEquals(expected.yCoord,look.yCoord,0); assertEquals(expected.zCoord,look.zCoord,0);
            assertEquals(3,run.f.player.inventory.currentItem);
        }
    }

    @Test public void rayPlanCanAimEarlyWithoutInventingAcceptedRotationHistory() throws Exception {
        try (Run run = new Run()) {
            assertEquals("Normal",run.module.getModeSetting("Rotations").getValue());
            assertNull(run.engine.serverRotation());
            run.f.player.setAngles(0,0);
            assertEquals(NScaffold.Status.ALIGNING,run.module.getStatus());
            assertTrue("geometry may prepare the first aim before a walking packet",run.engine.shouldRotate());
            assertNull("an aiming preference is not factual C03 history",run.engine.serverRotation());
            assertFalse(run.rotation.canUseRotation(NScaffold.ROTATION_SOURCE,false));
            run.step(() -> DemiseRotationHooks.update(run.f.player));
            for (Object packet : run.drain()) assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
            run.f.player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(75,18,true));
            assertArrayEquals(new float[]{75,18},run.engine.serverRotation(),0);
            assertTrue("history must come from actual accepted C03 transport",run.drain().get(0) instanceof C03PacketPlayer.C05PacketPlayerLook);
            run.f.player.setAngles(0,0);
            assertTrue("the accepted packet keeps the prepared Normal aim active",run.engine.shouldRotate());
            assertEquals(NScaffold.Status.ALIGNING,run.module.getStatus());
            assertEquals(0,run.f.player.rotationYaw,0); assertEquals(0,run.f.player.rotationPitch,0);
        }
    }

    @Test public void acceptedHistorySurvivesFocusButNeverAnotherEpoch() throws Exception {
        try (Run run = new Run()) {
            run.disable(); run.f.mc.inGameHasFocus=false;
            run.f.player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(83,21,true));
            assertFalse("focus loss clears the active frame engine",run.engine.hasServerRotation());
            assertTrue(run.drain().get(0) instanceof C03PacketPlayer.C05PacketPlayerLook);
            run.f.mc.inGameHasFocus=true; run.enable();
            assertArrayEquals(new float[]{83,21},run.engine.serverRotation(),0);
            run.engine.updateSpeeds(run.module.rotationSettings.snapshot());
            run.f.player.setAngles(0,0); assertTrue(run.engine.shouldRotate());
            run.f.mc.inGameHasFocus=false; DemiseRotationHooks.mousePre();
            assertFalse(run.engine.shouldRotate());
            run.f.mc.inGameHasFocus=true; DemiseRotationHooks.bind();
            assertArrayEquals("same-scene focus return keeps factual packet history",new float[]{83,21},run.engine.serverRotation(),0);
            assertFalse("restoring a server sample must not revive an old target",run.engine.shouldRotate());
            run.f.network.getClientPackets().worldChanged(); DemiseRotationHooks.mousePre();
            assertNull("another epoch cannot inherit the old scene's C03",run.engine.serverRotation());
            run.disable(); run.enable();
            run.f.player.setAngles(0,0);
            assertEquals(NScaffold.Status.ALIGNING,run.module.getStatus()); assertTrue(run.engine.shouldRotate());
            assertNull("fresh geometry cannot restore the preceding epoch's history",run.engine.serverRotation());
            run.f.player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(-31,7,true));
            assertArrayEquals(new float[]{-31,7},run.engine.serverRotation(),0);
            run.f.player.setAngles(0,0); assertTrue(run.engine.shouldRotate());
        }
    }

    @Test public void rawMoveFixPrecedesSneakAndPhysicsConsumesItOnce() throws Exception {
        try (Run run = new Run()) {
            run.f.player.rotationYaw = 120; run.engine.clear(120,0);
            run.aim(-90,65,"Silent"); run.frame(17); run.disable();
            DemiseOriginalMoveFix oracle = new DemiseOriginalMoveFix();
            for (String correction : new String[]{"Silent","Strict","None"}) for (boolean sneak : new boolean[]{false,true}) {
                run.aim(-90,65,correction);
                run.f.mc.gameSettings.keyBindForward.pressed = true;
                run.f.mc.gameSettings.keyBindLeft.pressed = true;
                run.f.mc.gameSettings.keyBindSneak.pressed = sneak;
                run.step(() -> {
                    run.rotation.prepareMovement(run.f.player);
                    assertEquals(NScaffold.ROTATION_SOURCE,run.rotation.getPreparedMovement().source);
                    float yaw = run.rotation.getPreparedMovement().yaw;
                    DemiseOriginalMoveFix.MoveInputEvent expected = new DemiseOriginalMoveFix.MoveInputEvent(1,1);
                    if ("Silent".equals(correction)) oracle.fixMovement(expected,yaw,run.f.player.rotationYaw);
                    float scale = sneak ? .3f : 1;
                    float strafe = expected.strafe * scale, forward = expected.forward * scale;
                    run.f.player.movementInput.updatePlayerMoveState();
                    assertEquals(strafe,MovementInput.moveStrafe,0); assertEquals(forward,MovementInput.moveForward,0);
                    assertEquals(forward,RotationCorrections.sprintForward(run.f.player.rotationYaw,strafe,forward),0);
                    run.f.player.motionX = run.f.player.motionZ = 0;
                    run.f.player.moveFlying(MovementInput.moveStrafe,MovementInput.moveForward,.1f);
                    float physicsYaw = "None".equals(correction) ? run.f.player.rotationYaw : yaw;
                    float length = MathHelper.sqrt_float(strafe*strafe+forward*forward);
                    float factor = .1f / Math.max(1,length);
                    float sine = MathHelper.sin(physicsYaw * (float) Math.PI / 180), cosine = MathHelper.cos(physicsYaw * (float) Math.PI / 180);
                    assertEquals(strafe*factor*cosine-forward*factor*sine,run.f.player.motionX,2e-6);
                    assertEquals(forward*factor*cosine+strafe*factor*sine,run.f.player.motionZ,2e-6);
                    run.f.player.motionX = run.f.player.motionZ = 0; run.f.player.sprint = true;
                    run.f.player.jump();
                    assertEquals(-MathHelper.sin(physicsYaw*.017453292f)*.2f,run.f.player.motionX,2e-6);
                    assertEquals(MathHelper.cos(physicsYaw*.017453292f)*.2f,run.f.player.motionZ,2e-6);
                    run.f.player.onUpdateWalkingPlayer();
                });
                assertEquals(120,run.f.player.rotationYaw,0);
                assertEquals(NScaffold.ROTATION_SOURCE,run.rotation.getConfirmedOwner());
            }
        }
    }

    @Test public void acceptedWireAndModelPoseSurviveFreeCameraAndDisableTail() throws Exception {
        try (Run run = new Run()) {
            run.aim(130,80,"Silent"); run.frame(17);
            DemiseRotationEngine.Aim aim = run.engine.currentAim(); run.disable();
            run.f.player.setAngles(40,20);
            assertEquals(6,run.f.player.rotationYaw,0); assertEquals(-3,run.f.player.rotationPitch,0);
            run.step(run.f.player::onUpdateWalkingPlayer);
            assertEquals(NScaffold.ROTATION_SOURCE,run.rotation.getConfirmedOwner());
            assertEquals(aim.yaw,run.rotation.getEffectiveYaw(),0); assertEquals(aim.pitch,run.rotation.getEffectivePitch(),0);
            assertEquals(aim.yaw,run.f.player.rotationYawHead,0); assertEquals(aim.pitch,run.f.player.rotationPitchHead,0);
            assertEquals(6,run.f.player.rotationYaw,0); assertEquals(-3,run.f.player.rotationPitch,0);
            assertNull("original model fields must not be replaced by a shared cosmetic controller",RotationCorrections.renderPose(run.f.player,0));
            Method body = EntityLivingBase.class.getDeclaredMethod("func_110146_f",float.class,float.class); body.setAccessible(true);
            run.f.player.renderYawOffset = 0; body.invoke(run.f.player,45f,1f);
            assertEquals(originalBodyYaw(aim.yaw,45,0),run.f.player.renderYawOffset,0);
            run.f.player.prevRotationPitchHead = 0;
            assertEquals(aim.pitch*.5f,DemiseRotationHooks.renderPitch(run.f.player,.5f,-3),0);
            int movementPackets = 0;
            for (Object packet : run.drain()) {
                if (packet instanceof C03PacketPlayer) {
                    movementPackets++; C03PacketPlayer c03=(C03PacketPlayer)packet;
                    assertTrue(c03.getRotating()); assertEquals(aim.yaw,c03.getYaw(),0); assertEquals(aim.pitch,c03.getPitch(),0);
                }
                assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
                assertFalse(packet instanceof C09PacketHeldItemChange);
                assertFalse(packet instanceof C0APacketAnimation);
            }
            assertEquals(1,movementPackets);
            run.frame(134); // Last target refresh was at t=1000; expiry occurs at151ms, not150.
            assertFalse(run.engine.enabled()); assertTrue(run.engine.shouldRotate());
            for (int i=0;i<2000 && run.engine.shouldRotate();i++) run.frame(17);
            assertEquals(6,run.f.player.rotationYaw,0); assertEquals(-3,run.f.player.rotationPitch,0);
            // Original integer counts can leave a diagonal sub-degree dead zone.
            // Manual camera movement closes it; no replacement reset threshold is added.
            run.f.player.rotationYaw=run.engine.rotation()[0]; run.f.player.rotationPitch=run.engine.rotation()[1];
            run.frame(17);
            assertFalse(run.engine.shouldRotate());
            run.step(run.f.player::onUpdateWalkingPlayer);
            assertNull(run.rotation.getConfirmedOwner());
            assertEquals(run.f.player.rotationYaw,run.rotation.getEffectiveYaw(),0);
            assertEquals(run.f.player.rotationPitch,run.rotation.getEffectivePitch(),0);
        }
    }

    @Test public void anotherOwnerAndInvalidScenesDoNotInheritDemisePose() throws Exception {
        try (Run run = new Run()) {
            run.aim(130,80,"Silent"); run.frame(17); run.disable();
            assertTrue("the bound tail must be live before testing another owner",run.module.correctionEnabled());
            run.rotation.bindConnection(run.f.network.getClientPackets());
            run.rotation.beginMotion(run.f.player,run.f.mc.theWorld,0,0);
            run.rotation.request("aura",20,-70,20); run.rotation.finishMotion(-70,20,true);
            assertEquals(12,DemiseRotationHooks.modelYaw(run.f.player,12),0);
            assertEquals(7,DemiseRotationHooks.renderPitch(run.f.player,.5f,7),0);
            assertNull("another owner's getLook must not inherit NScaffold",DemiseRotationHooks.lookVector(run.f.player));
            run.f.mc.inGameHasFocus = false; DemiseRotationHooks.mousePre();
            assertFalse(run.engine.shouldRotate());
            run.f.mc.inGameHasFocus = true; DemiseRotationHooks.bind();
            run.aim(130,80,"Silent"); run.frame(17);
            run.f.network.getClientPackets().worldChanged(); DemiseRotationHooks.mousePre();
            assertFalse("a pipeline epoch change invalidates the disabled tail",run.engine.shouldRotate());
        }
    }

    @Test public void noneCorrectionStopsOppositeSprintOnlyDuringDisableTail() throws Exception {
        try (Run run = new Run()) {
            run.aim(0,0,"None"); run.frame(17); run.f.player.rotationYaw=180;
            run.f.mc.gameSettings.keyBindForward.pressed=true;
            run.f.mc.gameSettings.keyBindSprint=new KeyBinding("demise.tail.sprint",29,"test");
            run.f.mc.gameSettings.keyBindSprint.pressed=true; run.f.player.sprint=true;
            run.step(() -> {
                run.rotation.prepareMovement(run.f.player); run.f.player.movementInput.updatePlayerMoveState();
                assertTrue("active Scaffold is exempt in the original handler",run.f.player.sprint);
                assertTrue(run.f.mc.gameSettings.keyBindSprint.pressed); run.f.player.onUpdateWalkingPlayer();
            });
            run.disable();
            run.step(() -> {
                run.rotation.prepareMovement(run.f.player); run.f.player.movementInput.updatePlayerMoveState();
                assertFalse(run.f.player.sprint); assertFalse(run.f.mc.gameSettings.keyBindSprint.pressed);
                run.f.player.onUpdateWalkingPlayer();
            });
            run.aim(0,0,"Strict"); run.f.player.sprint=true; run.f.mc.gameSettings.keyBindSprint.pressed=true;
            run.step(() -> {
                run.rotation.prepareMovement(run.f.player); run.f.player.movementInput.updatePlayerMoveState();
                assertTrue("Strict does not execute the None-only stop",run.f.player.sprint);
                assertTrue(run.f.mc.gameSettings.keyBindSprint.pressed); run.f.player.onUpdateWalkingPlayer();
            });
        }
    }

    public static class CounterEntity extends Entity {
        int seenGround, seenAir;
        public CounterEntity() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound compound) { }
        @Override protected void writeEntityToNBT(NBTTagCompound compound) { }
        @Override public void onEntityUpdate() { seenGround=onGroundTicks; seenAir=offGroundTicks; }
    }
    @Test public void consecutiveGroundCountersRunBeforeEntityUpdate() throws Exception {
        CounterEntity entity=RotationBehaviorHookTest.bare(CounterEntity.class);
        entity.onGround=true; entity.onUpdate(); assertEquals(1,entity.seenGround); assertEquals(0,entity.seenAir);
        entity.onUpdate(); assertEquals(2,entity.seenGround);
        entity.onGround=false; entity.onUpdate(); assertEquals(0,entity.seenGround); assertEquals(1,entity.seenAir);
        entity.onUpdate(); assertEquals(2,entity.seenAir);
        entity.onGround=true; entity.onUpdate(); assertEquals(1,entity.seenGround); assertEquals(0,entity.seenAir);
    }

    /** Original EntityLivingBase.updateDistance arithmetic, with the original realistic/body defaults. */
    private static float originalBodyYaw(float rotationYaw,float target,float renderYawOffset) {
        float f=MathHelper.wrapAngleTo180_float(target-renderYawOffset); renderYawOffset+=f*.3f;
        float f1=MathHelper.wrapAngleTo180_float(rotationYaw-renderYawOffset);
        if(f1 < -75)f1=-75; if(f1 >= 75)f1=75;
        renderYawOffset=rotationYaw-f1; if(f1*f1>2500)renderYawOffset+=f1*.2f;
        return renderYawOffset;
    }
}
