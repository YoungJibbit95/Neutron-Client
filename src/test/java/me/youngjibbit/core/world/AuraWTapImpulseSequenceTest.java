package me.youngjibbit.core.world;

import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.module.impl.ghost.WTap2;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MovementInput;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AuraWTapImpulseSequenceTest {
    @Test public void beforeAttack() throws Exception { Wave2IsolatedCase.run(Checks.class, "beforeAttack"); }
    @Test public void pendingTap() throws Exception { Wave2IsolatedCase.run(Checks.class, "pendingTap"); }
    @Test public void afterRelease() throws Exception { Wave2IsolatedCase.run(Checks.class, "afterRelease"); }

    public static class Checks {
        enum Arrival { BEFORE_ATTACK, PENDING_TAP, AFTER_RELEASE }

        @Test public void beforeAttack() throws Exception { sequence(Arrival.BEFORE_ATTACK); }
        @Test public void pendingTap() throws Exception { sequence(Arrival.PENDING_TAP); }
        @Test public void afterRelease() throws Exception { sequence(Arrival.AFTER_RELEASE); }

        private static void sequence(Arrival arrival) throws Exception {
            try (AuraMovementConsistencyTest.Run run = new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                run.remoteTarget();
                CombatFollowupTest.prepareNetwork(run);
                WTap2 tap = CombatFollowupTest.tap(run);
                try {
                    CombatProfileTest.tapClock(tap);
                    run.f.run.fixture.aura.getBooleanSetting("Click Randomization Enabled").setValue(false);
                    run.player.onGround = true;
                    run.player.sprint = true;
                    if (arrival == Arrival.BEFORE_ATTACK) sendImpulse(run);
                    assertEquals(1, attacks(run.f.walk()));
                    assertFalse(run.player.isSprinting());
                    if (arrival == Arrival.PENDING_TAP) sendImpulse(run);
                    double expectedX = arrival == Arrival.BEFORE_ATTACK ? .24 : arrival == Arrival.PENDING_TAP ? .4 : 0;
                    double expectedZ = -expectedX / 2;
                    double expectedY = arrival == Arrival.AFTER_RELEASE ? 0 : .3;
                    // At 10 CPS the first 50 ms sample consumes WTap but cannot attack again.
                    assertEquals(0, step(run, false, false));
                    CombatFollowupTest.impulse(run.player, expectedX, expectedY, expectedZ);
                    // The next 50 ms sample resumes held W/sprint and reaches the next eligible attack.
                    assertEquals(1, step(run, true, arrival == Arrival.AFTER_RELEASE));
                    if (arrival == Arrival.AFTER_RELEASE) {
                        expectedX = .4;
                        expectedY = .3;
                        expectedZ = -.2;
                    }
                    CombatFollowupTest.impulse(run.player, expectedX * .6, expectedY, expectedZ * .6);
                    assertFalse(run.player.isSprinting());
                } finally {
                    CombatFollowupTest.detach(tap);
                }
            }
        }

        private static long step(AuraMovementConsistencyTest.Run run, boolean forward, boolean impulseAfterInput) {
            run.f.scheduler.beginTick();
            run.f.clock.addAndGet(50);
            run.f.run.scene.motionTime.addAndGet(50_000_000L);
            RuntimeObservation observation = run.f.run.fixture.runtime.getObservation();
            observation.beginOuterTick();
            try {
                observation.observePlayerStep(run.player, () -> {
                    try {
                        run.player.onLivingUpdate();
                        fail("Fixture must stop at the boundary before world physics");
                    } catch (AuraMovementConsistencyTest.BeforePhysics expected) { }
                    assertEquals(forward ? 1 : 0, MovementInput.moveForward, 0);
                    assertEquals(forward, run.player.isSprinting());
                    assertTrue(run.f.run.fixture.mc.gameSettings.keyBindForward.pressed);
                    if (impulseAfterInput) sendImpulse(run);
                    run.player.onUpdateWalkingPlayer();
                    return true;
                }, step -> step.getAsBoolean());
            } finally {
                observation.endOuterTick();
            }
            return attacks(run.f.run.drain());
        }

        private static void sendImpulse(AuraMovementConsistencyTest.Run run) {
            CombatFollowupTest.velocity(run, .4, .3, -.2);
        }

        private static long attacks(List<Object> packets) {
            return packets.stream().filter(C02PacketUseEntity.class::isInstance)
                    .map(C02PacketUseEntity.class::cast)
                    .filter(p -> p.getAction() == C02PacketUseEntity.Action.ATTACK).count();
        }
    }
}
