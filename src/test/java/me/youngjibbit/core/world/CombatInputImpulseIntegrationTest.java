package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.ghost.WTap2;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

/** Synthetic floor and keys, real S12, Aura controller, input and local physics; no server. */
@RunWith(Parameterized.class)
public class CombatInputImpulseIntegrationTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {{"acceptedStapMovesBack"}, {"rejectedStapKeepsForward"},
                {"acceptedLegitKeepsImpulseWithoutAcceleration"}, {"laterVelocityReplacesAttackImpulse"}});
    }
    private final String method;
    public CombatInputImpulseIntegrationTest(String method) { this.method = method; }
    @Test public void freshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    public static class Checks {
    @Test public void acceptedStapMovesBack() throws Exception { check("STap", false, -1, false); }
    @Test public void rejectedStapKeepsForward() throws Exception { check("STap", true, 1, false); }
    @Test public void acceptedLegitKeepsImpulseWithoutAcceleration() throws Exception { check("Legit", false, 0, false); }
    @Test public void laterVelocityReplacesAttackImpulse() throws Exception { check("STap", false, -1, true); }

    private void check(String mode, boolean cancel, int expectedForward, boolean laterVelocity) throws Exception {
        try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Legacy Strict", CombatFollowupTest.PhysicsPlayer.class)) {
            r.remoteTarget();
            CombatFollowupTest.prepareNetwork(r);
            Entity target = r.f.run.scene.getEntityByID(19);
            target.setPosition(-.5, 64, 2);
            target.setEntityBoundingBox(new AxisAlignedBB(-.8, 64, 1.7, -.2, 65.8, 2.3));
            GameSettings settings = r.f.run.fixture.mc.gameSettings;
            WTap2 tap = CombatFollowupTest.tap(r);
            tap.getModeSetting("Mode").setValue(mode);
            settings.keyBindBack = new KeyBinding("integration.back", 31, "integration");
            settings.keyBindLeft = new KeyBinding("integration.left", 30, "integration");
            settings.keyBindRight = new KeyBinding("integration.right", 32, "integration");
            settings.keyBindJump = new KeyBinding("integration.jump", 57, "integration");
            settings.keyBindSneak = new KeyBinding("integration.sneak", 42, "integration");
            r.player.movementInput = new MovementInputFromOptions(settings);
            r.player.onGround = true;
            r.player.sprint = true;
            r.player.jumpMovementFactor = .02f;
            CombatFollowupTest.velocity(r, .4, .3, -.2);
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> { if (cancel && e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity) e.cancel(); };
            Pulsar.BUS.subscribe(hooks);
            try {
                long attacks = r.f.walk().stream().filter(p -> p instanceof C02PacketUseEntity
                        && ((C02PacketUseEntity) p).getAction() == C02PacketUseEntity.Action.ATTACK).count();
                assertEquals(cancel ? 0 : 1, attacks);
                CombatFollowupTest.impulse(r.player, cancel ? .4 : .24, .3, cancel ? -.2 : -.12);
                assertEquals(cancel, r.player.sprint);
                if (laterVelocity) CombatFollowupTest.velocity(r, -.2, .25, .4);
                double initialX = laterVelocity ? -.2 : cancel ? .4 : .24;
                double initialY = laterVelocity ? .25 : .3;
                double initialZ = laterVelocity ? .4 : cancel ? -.2 : -.12;
                if ("STap".equals(mode) && !cancel)
                    ScaffoldOwnerContinuityTest.field(WTap2.class, "releaseAt").setLong(tap, Long.MAX_VALUE);
                Pulsar.BUS.post(new TickEvent());
                r.f.scheduler.beginTick(); r.f.clock.addAndGet(50); r.f.run.scene.motionTime.addAndGet(50_000_000L);
                RuntimeObservation observation = r.f.run.fixture.runtime.getObservation();
                observation.beginOuterTick();
                try {
                    observation.observePlayerStep(r.player, () -> {
                        try { r.player.onLivingUpdate(); fail("fixture must stop before unrelated world physics"); }
                        catch (AuraMovementConsistencyTest.BeforePhysics expected) { }
                        assertEquals(expectedForward, MovementInput.moveForward, 0);
                        assertEquals(0, MovementInput.moveStrafe, 0);
                        if (expectedForward <= 0) assertFalse(r.player.isSprinting());
                        CombatFollowupTest.impulse(r.player, initialX, initialY, initialZ);
                        r.player.moveEntityWithHeading(MovementInput.moveStrafe * .98f, MovementInput.moveForward * .98f);
                        assertNotNull(r.move);
                        r.player.onUpdateWalkingPlayer();
                        float yaw = r.f.run.fixture.runtime.getRotationManager().getEffectiveYaw();
                        assertTrue("silent rotation positive control", Math.abs(yaw - r.player.rotationYaw) > 5);
                        assertEquals("physics and accepted movement share yaw", yaw, r.move.getYaw(), .001);
                        double radians = Math.toRadians(yaw);
                        double friction = .6f * .91f;
                        double acceleration = expectedForward * .98f * r.move.getFriction();
                        assertEquals((initialX - Math.sin(radians) * acceleration) * friction, r.player.motionX, 3e-5);
                        assertEquals((initialZ + Math.cos(radians) * acceleration) * friction, r.player.motionZ, 3e-5);
                        return true;
                    }, step -> step.getAsBoolean());
                } finally { observation.endOuterTick(); }
                boolean movementSent = false;
                float effectiveYaw = r.f.run.fixture.runtime.getRotationManager().getEffectiveYaw();
                for (Object packet : r.f.run.drain()) if (packet instanceof C03PacketPlayer) {
                    C03PacketPlayer movement = (C03PacketPlayer) packet;
                    movementSent = true;
                    if (movement.getRotating()) assertEquals(effectiveYaw, movement.getYaw(), .001);
                }
                assertTrue("movement reached local transport", movementSent);
                if ("Legit".equals(mode)) {
                    // The same held forward key is sampled again after the one-step tap.
                    r.f.scheduler.beginTick(); r.f.clock.addAndGet(50); r.f.run.scene.motionTime.addAndGet(50_000_000L);
                    observation.beginOuterTick();
                    try {
                        observation.observePlayerStep(r.player, () -> {
                            try { r.player.onLivingUpdate(); fail("fixture must stop before unrelated world physics"); }
                            catch (AuraMovementConsistencyTest.BeforePhysics expected) { }
                            assertEquals("Legit consumes exactly one input sample", 1, MovementInput.moveForward, 0);
                            return true;
                        }, step -> step.getAsBoolean());
                    } finally { observation.endOuterTick(); }
                }
            } finally { Pulsar.BUS.unsubscribe(hooks); CombatFollowupTest.detach(tap); }
        }
    }
    }
}
