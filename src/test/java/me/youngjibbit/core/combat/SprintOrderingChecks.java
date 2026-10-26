package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.core.world.TowerController;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.impl.movement.Sprint;
import me.zero.alpine.listener.*;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Native Scaffold PRE, real sprint module, real jump and walking packet consumers. */
public class SprintOrderingChecks {
    private static void prepare(RotationBehaviorHookTest.Fixture f) throws Exception {
        ScaffoldClientFlowTest.prepare(f, .5, .48);
        f.player.movementInput = new MovementInput();
        MovementInput.moveForward = 1; MovementInput.moveStrafe = 0;
        f.player.moveForward = 1; f.player.rotationYaw = 180;
        f.scaffold.getBooleanSetting("Rotations").setValue(false);
    }
    private static List<C0BPacketEntityAction.Action> actions(RotationBehaviorHookTest.Fixture f) {
        List<C0BPacketEntityAction.Action> result = new ArrayList<>(); Object packet;
        while ((packet = f.channel.readOutbound()) != null)
            if (packet instanceof C0BPacketEntityAction) result.add(((C0BPacketEntityAction) packet).getAction());
        return result;
    }
    @Test public void heldSprintAndSprintModuleCannotRestartWhileValidPlanForbidsIt() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.player.sprint = true; f.player.onUpdateWalkingPlayer();
            assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f));
            f.scaffold.onEnable(); Sprint sprint = new Sprint(); sprint.onEnable();
            try {
                for (int tick = 0; tick < 4; tick++) {
                    f.player.sprint = true; // held key's vanilla decision
                    Pulsar.BUS.post(new EventUpdate()); // real Sprint module requests it too
                    assertTrue(f.player.isSprinting());
                    f.runtime.getScaffoldPlacementService().beginTick(); f.player.onUpdateWalkingPlayer();
                    assertFalse(f.player.isSprinting()); assertFalse(f.player.isServerSprintState());
                    assertEquals(tick == 0 ? Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING)
                            : Collections.emptyList(), actions(f));
                }
            } finally { sprint.onDisable(); f.scaffold.onDisable(); }
        }
    }
    @Test public void autoJumpConsumesFinalSprintAndRetainsAcceptedJumpBasisWhenAllowed() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.scaffold.onEnable();
            try {
                for (boolean jump : new boolean[]{false, true}) for (boolean allowSprint : new boolean[]{false, true}) {
                    f.scaffold.getBooleanSetting("Auto Jump").setValue(jump);
                    f.scaffold.getBooleanSetting("Sprint").setValue(allowSprint);
                    f.player.sprint = true; f.player.onGround = true;
                    f.player.motionX = f.player.motionY = f.player.motionZ = 0; f.accept("aura", 90);
                    f.runtime.getScaffoldPlacementService().beginTick(); f.player.onUpdateWalkingPlayer();
                    assertEquals(allowSprint, f.player.isSprinting()); assertEquals(allowSprint, f.player.isServerSprintState());
                    assertEquals(jump ? .42 : 0, f.player.motionY, 1e-6);
                    assertEquals(jump && allowSprint ? -.2 : 0, f.player.motionX, 3e-5);
                    assertEquals(0, f.player.motionZ, 3e-5); assertEquals(180, f.player.rotationYaw, 0);
                    actions(f);
                }
            } finally { f.scaffold.onDisable(); }
        }
    }
    @Test public void missingPlanTargetLossAndDisableReleaseSprintSuppression() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.scaffold.onEnable();
            try {
                f.player.sprint = true; f.player.onUpdateWalkingPlayer(); assertFalse(f.player.isSprinting()); actions(f);
                f.player.inventory.mainInventory[3] = null; f.player.sprint = true;
                f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isSprinting()); assertTrue(f.player.isServerSprintState());
                assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f));
                f.scaffold.onDisable(); f.player.sprint = false; f.player.onUpdateWalkingPlayer();
                assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING), actions(f));
                f.player.sprint = true; f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isServerSprintState());
                assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f));
            } finally { f.scaffold.onDisable(); }
        }
    }
    public static final class CancelMotion implements Subscriber {
        @Subscribe final Listener<EventMotion> listener = new Listener<>(event -> { if (event.isPre()) event.cancel(); });
    }
    @Test public void finalSprintActionStillSynchronizesWhenMotionPreIsCancelled() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.player.sprint = true; f.player.onUpdateWalkingPlayer(); actions(f);
            f.scaffold.onEnable(); CancelMotion cancel = new CancelMotion(); Pulsar.BUS.subscribe(cancel);
            try {
                f.player.sprint = true; f.player.onUpdateWalkingPlayer();
                assertFalse(f.player.isSprinting()); assertFalse(f.player.isServerSprintState());
                assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING), actions(f));
                assertFalse(f.runtime.getRotationManager().isMotionAccepted());
            } finally { Pulsar.BUS.unsubscribe(cancel); f.scaffold.onDisable(); }
        }
    }
    public static final class ChangeContext implements Subscriber {
        Runnable change;
        @Subscribe final Listener<EventMotion> listener = new Listener<>(event -> { if (event.isPre()) change.run(); });
    }
    @Test public void motionPreContextChangesCannotEmitOldPlayerActions() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.accept("aura", 90); f.player.sprint = true;
            ChangeContext callback = new ChangeContext();
            if (scenario == 0) callback.change = () -> f.mc.theWorld = f.world;
            if (scenario == 1) callback.change = () -> f.mc.thePlayer = null;
            if (scenario == 2) callback.change = () -> f.channel.close();
            if (scenario == 3) callback.change = () -> f.network.getClientPackets().worldChanged();
            Pulsar.BUS.subscribe(callback);
            try {
                f.player.onUpdateWalkingPlayer(); assertTrue(actions(f).isEmpty()); assertNull(f.channel.readOutbound());
                assertFalse(f.player.isServerSprintState()); assertNull(f.runtime.getRotationManager().getConfirmedOwner());
                assertFalse(f.runtime.getRotationManager().isMotionAccepted());
            } finally { Pulsar.BUS.unsubscribe(callback); }
        }
    }
    @Test public void abortingOldWalkingFramePreservesANewerNestedFrame() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.player.sprint = true; RotationManager rotations = f.runtime.getRotationManager();
            ChangeContext callback = new ChangeContext(); callback.change = () -> {
                f.mc.thePlayer = null; rotations.beginMotion(new Object(), f.world, 0, 0);
                rotations.request("aura", 20, 45, 0); rotations.finishMotion(45, 0, true);
            };
            Pulsar.BUS.subscribe(callback);
            try {
                f.player.onUpdateWalkingPlayer(); assertTrue(actions(f).isEmpty()); assertNull(f.channel.readOutbound());
                assertEquals("aura", rotations.getConfirmedOwner()); assertTrue(rotations.isMotionAccepted());
            } finally { Pulsar.BUS.unsubscribe(callback); }
        }
    }
    public static final class FilterAction implements Subscriber {
        boolean disconnect;
        Minecraft client;
        @Subscribe final Listener<EventPacket> listener = new Listener<>(event -> {
            if (event.isOutbound() && event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                if (disconnect) client.thePlayer = null; else event.cancel();
            }
        });
    }
    @Test public void cancelledOrContextChangedActionCannotAdvanceSprintCache() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); FilterAction filter = new FilterAction(); filter.client = f.mc;
            f.player.sprint = true; Pulsar.BUS.subscribe(filter);
            try { f.player.onUpdateWalkingPlayer(); assertFalse(f.player.isServerSprintState()); assertTrue(actions(f).isEmpty()); }
            finally { Pulsar.BUS.unsubscribe(filter); }
            f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isServerSprintState());
            assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f));
            f.player.sprint = false; Pulsar.BUS.subscribe(filter);
            try { f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isServerSprintState()); assertTrue(actions(f).isEmpty()); }
            finally { Pulsar.BUS.unsubscribe(filter); }
            f.player.onUpdateWalkingPlayer(); assertFalse(f.player.isServerSprintState());
            assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING), actions(f));
            f.player.sprint = true; filter.disconnect = true; Pulsar.BUS.subscribe(filter);
            try { f.player.onUpdateWalkingPlayer(); assertFalse(f.player.isServerSprintState()); assertTrue(actions(f).isEmpty()); assertNull(f.channel.readOutbound()); }
            finally { Pulsar.BUS.unsubscribe(filter); }
            assertNull(f.runtime.getRotationManager().getConfirmedOwner());
        }
    }
    @Test public void towerPlanLossKeepsInitialSuppressionForOnlyTheCurrentStep() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); MovementInput.moveForward = 0; f.player.moveForward = 0;
            TowerController tower = new TowerController(new TowerController.Access() {
                public boolean ready() { return true; }
                public boolean onGround() { return true; }
                public double motionY() { return f.player.motionY; }
                public void motionY(double value) { f.player.motionY = value; f.player.inventory.mainInventory[3] = null; }
                public Vec3 position() { return new Vec3(f.player.posX, f.player.posY, f.player.posZ); }
                public boolean move(Vec3 position) { return false; }
            });
            java.lang.reflect.Field field = Scaffold.class.getDeclaredField("towerController"); field.setAccessible(true); field.set(f.scaffold, tower);
            f.scaffold.getBooleanSetting("Tower").setValue(true); f.scaffold.getModeSetting("Tower Mode").setValue("NCP");
            f.mc.gameSettings.keyBindJump.pressed = true; f.scaffold.onEnable();
            try {
                f.player.sprint = true; f.player.onUpdateWalkingPlayer();
                assertEquals(.42, f.player.motionY, 0); assertNull(f.player.inventory.mainInventory[3]);
                assertFalse(f.player.isSprinting()); assertFalse(f.player.isServerSprintState()); assertTrue(actions(f).isEmpty());
                f.player.sprint = true; f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isSprinting());
                assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f));
            } finally { f.scaffold.onDisable(); }
        }
    }
    @Test public void nonViewPlayerStillSynchronizesVanillaSprintActions() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); java.lang.reflect.Field view = Minecraft.class.getDeclaredField("renderViewEntity"); view.setAccessible(true); view.set(f.mc, null);
            f.player.sprint = true; f.player.onUpdateWalkingPlayer(); assertTrue(f.player.isServerSprintState());
            assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions(f)); assertNull(f.channel.readOutbound());
        }
    }
}
