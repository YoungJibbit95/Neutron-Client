package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.ghost.JumpReset;
import me.youngjibbit.utils.Eierlecken;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Draft regression: an accepted native placement earns the interval, then a real
 * incoming S12 goes through NetHandlerPlayClient before the next fresh physical
 * input. The existing fixture executes EntityPlayerSP's input/sprint/rotation
 * consumers and stops before full living physics. No native game/server result
 * is claimed, and no pending jump, pulse, budget or rotation authority is set.
 */
public class VelocityScaffoldInputCompositionTest {
    @Test public void neutronEarnedSneakCannotRetainAnAutomaticImpulseJump() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "neutronEarnedSneakCannotRetainAnAutomaticImpulseJump");
    }

    @Test public void arsenicEarnedSneakRetainsTheRealSprintAdmissionGate() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "arsenicEarnedSneakRetainsTheRealSprintAdmissionGate");
    }

    @Test public void neutronPhysicalJumpRemainsPlayerOwnedDuringThePulse() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "neutronPhysicalJumpRemainsPlayerOwnedDuringThePulse");
    }

    @Test public void preCancelledS12CannotAuthorizeAnAutomaticJumpDuringThePulse() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "preCancelledS12CannotAuthorizeAnAutomaticJumpDuringThePulse");
    }

    @Test public void neutronWithoutAnIntervalStillConsumesTheAppliedImpulseOnce() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "neutronWithoutAnIntervalStillConsumesTheAppliedImpulseOnce");
    }

    public static final class Checks {
        @Test public void neutronEarnedSneakCannotRetainAnAutomaticImpulseJump() throws Exception {
            exercise("Neutron", true, false, false, false);
        }

        @Test public void arsenicEarnedSneakRetainsTheRealSprintAdmissionGate() throws Exception {
            exercise("Arsenic", true, false, false, false);
        }

        @Test public void neutronPhysicalJumpRemainsPlayerOwnedDuringThePulse() throws Exception {
            exercise("Neutron", true, true, false, true);
        }

        @Test public void preCancelledS12CannotAuthorizeAnAutomaticJumpDuringThePulse() throws Exception {
            exercise("Neutron", true, false, true, false);
        }

        @Test public void neutronWithoutAnIntervalStillConsumesTheAppliedImpulseOnce() throws Exception {
            exercise("Neutron", false, false, false, true);
        }
    }

    private static void exercise(String approach, boolean interval, boolean physicalJump,
                                 boolean cancelImpulse, boolean expectedJump) throws Exception {
        try (AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run();
             UtilityClientBinding ignored = new UtilityClientBinding(r.f.mc)) {
            // Existing fixture dependencies only; no module action state is injected.
            ScaffoldClientFlowTest.set(Minecraft.class, r.f.mc, "mcThread", Thread.currentThread());
            r.player.setEntityId(1);
            r.scene.loadedEntityList.add(r.player);
            r.player.sendQueue.clientWorldController = r.scene;
            r.f.aura.setToggled(false);
            r.f.scaffold.setToggled(false);
            r.f.scaffold.getModeSetting("Scaffold Approach").setValue(approach);
            r.f.scaffold.getBooleanSetting("Eagle").setValue(false);
            r.f.scaffold.getBooleanSetting("Sprint").setValue(true);
            r.f.scaffold.getBooleanSetting("Auto Jump").setValue(false);
            r.f.scaffold.getBooleanSetting("Tower").setValue(false);
            r.f.scaffold.getNumberSetting("Sneak After Blocks").setValue(interval ? 1D : 0D);
            r.position(.25, .5);
            r.player.rotationYaw = 0;
            r.settings.keyBindForward.pressed = true;
            r.settings.keyBindSprint.pressed = true;
            assertFalse(r.settings.keyBindSneak.isKeyDown());
            assertFalse(r.settings.keyBindJump.isKeyDown());
            assertFalse("a separate JumpReset owner would invalidate the scenario", JumpReset.isHandlingJumps());
            assertFalse("the destination starts empty", r.scene.blocks.containsKey(new BlockPos(0, 63, 0)));
            assertFalse("the player has real block support at the initial edge",
                    r.scene.getCollidingBoundingBoxes(r.player,
                            r.player.getEntityBoundingBox().offset(0, -.05, 0)).isEmpty());

            Velocity velocity = new Velocity();
            velocity.getModeSetting("Mode").setValue("Impulse Guard");
            velocity.setToggled(true);
            r.f.scaffold.setToggled(true);
            try {
                int placements = 0;
                for (int tick = 0; tick < 60 && placements == 0; tick++) {
                    r.step(true, () -> { });
                    for (Object packet : r.drain()) {
                        if (packet instanceof C08PacketPlayerBlockPlacement) placements++;
                        assertFalse("Impulse Guard never adds reduction attacks", packet instanceof C02PacketUseEntity);
                    }
                }
                assertEquals("the real controller must earn exactly one placement", 1, placements);
                assertEquals(Blocks.stone, r.scene.getBlockState(new BlockPos(0, 63, 0)).getBlock());
                assertEquals("successful ItemBlock consumption earns the pulse", 63,
                        r.player.inventory.mainInventory[3].stackSize);
                // Arsenic's hard edge guard can already sneak even with Eagle disabled.
                // Only Neutron isolates the interval as the sole sneak producer here.
                if ("Neutron".equals(approach))
                    assertFalse("no later fresh input has consumed the earned pulse yet", r.player.movementInput.sneak);
                assertTrue(r.player.onGround);
                // Arsenic suppresses sprint in ordinary input even when the old Sprint value is true.
                assertEquals("sprint is established only by physical input and the real module gate",
                        "Neutron".equals(approach), r.player.isSprinting());

                final int[] phases = new int[2], inputCount = new int[1];
                final long[] appliedAt = new long[1];
                final MovementInputEvent[] observedInput = new MovementInputEvent[1];
                Listener<KnockbackEvent> impulse = new Listener<>(event -> {
                    if (event.isPre()) {
                        phases[0]++;
                        if (cancelImpulse) event.cancel();
                    }
                    if (event.isPost()) {
                        phases[1]++;
                        appliedAt[0] = System.nanoTime();
                    }
                });
                Listener<MovementInputEvent> input = new Listener<>(event -> {
                    inputCount[0]++;
                    observedInput[0] = event;
                });
                Pulsar.BUS.subscribe(impulse);
                Pulsar.BUS.subscribe(input);
                try {
                    double oldX = r.player.motionX, oldY = r.player.motionY, oldZ = r.player.motionZ;
                    r.f.channel.writeInbound(new S12PacketEntityVelocity(1, .3, .2, 0));
                    assertEquals("the real local S12 handler emitted PRE", 1, phases[0]);
                    assertEquals("POST exists only after actual setVelocity", cancelImpulse ? 0 : 1, phases[1]);
                    assertEquals(cancelImpulse ? oldX : .3, r.player.motionX, 1e-4);
                    assertEquals(cancelImpulse ? oldY : .2, r.player.motionY, 1e-4);
                    assertEquals(cancelImpulse ? oldZ : 0, r.player.motionZ, 1e-4);
                    r.settings.keyBindJump.pressed = physicalJump;
                    r.step(false, () -> {
                        if (!cancelImpulse) {
                            long elapsed = System.nanoTime() - appliedAt[0];
                            assertTrue("setup must exercise the real 250ms window; no expired false pass",
                                    elapsed >= 0 && elapsed < 200_000_000L);
                        }
                        assertEquals("the next real fresh input executes once", 1, inputCount[0]);
                        assertNotNull(observedInput[0]);
                        // Read after BUS dispatch, independent of listener registration/priority.
                        if ("Neutron".equals(approach))
                            assertFalse("Neutron has no earlier sneak before the late interval hook",
                                    observedInput[0].isSneak());
                        assertEquals("the next final input matches the earned-interval scenario", interval,
                                r.player.movementInput.sneak);
                        assertFalse("the physical sneak key remains untouched", r.settings.keyBindSneak.isKeyDown());
                        assertEquals(physicalJump, r.settings.keyBindJump.isKeyDown());
                        System.out.println("composition approach=" + approach + " interval=" + interval
                                + " physicalJump=" + physicalJump + " cancelledS12=" + cancelImpulse
                                + " eventJump=" + observedInput[0].isJump() + " finalSneak=" + r.player.movementInput.sneak
                                + " finalJump=" + r.player.movementInput.jump);
                        assertEquals("a final earned sneak must reject only an automatic jump", expectedJump,
                                r.player.movementInput.jump);
                    });
                    if (!interval && !physicalJump && !cancelImpulse) {
                        r.input();
                        assertFalse("an applied impulse cannot authorize repeated automatic jumps",
                                r.player.movementInput.jump);
                    }
                    assertEquals(63, r.player.inventory.mainInventory[3].stackSize);
                    List<Object> packets = r.drain();
                    for (Object packet : packets) {
                        assertFalse("the input-only step performs no second placement",
                                packet instanceof C08PacketPlayerBlockPlacement);
                        assertFalse("Impulse Guard performs no reduction attack", packet instanceof C02PacketUseEntity);
                    }
                } finally {
                    Pulsar.BUS.unsubscribe(input);
                    Pulsar.BUS.unsubscribe(impulse);
                }
            } finally {
                velocity.setToggled(false);
                r.f.scaffold.setToggled(false);
            }
        }
    }

    /** Mirrors the existing ExploitHookFixture utility-client binding, restored after each case. */
    private static final class UtilityClientBinding implements AutoCloseable {
        private final sun.misc.Unsafe unsafe;
        private final Object base, previous;
        private final long offset;

        UtilityClientBinding(Minecraft client) throws Exception {
            Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            u.setAccessible(true);
            unsafe = (sun.misc.Unsafe) u.get(null);
            Field field = Eierlecken.class.getDeclaredField("mc");
            field.setAccessible(true);
            field.get(null); // Initialize the declaring interface before rebinding its dependency.
            base = unsafe.staticFieldBase(field);
            offset = unsafe.staticFieldOffset(field);
            previous = unsafe.getObject(base, offset);
            unsafe.putObject(base, offset, client);
        }

        @Override public void close() { unsafe.putObject(base, offset, previous); }
    }
}
