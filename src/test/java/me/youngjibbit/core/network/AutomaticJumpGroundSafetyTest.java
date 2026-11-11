package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.movement.GroundJumpGuard;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.ghost.JumpReset;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/** Normal embedded S12 delivery and input consumption; no outgoing attack or packet changes. */
public class AutomaticJumpGroundSafetyTest {
    private static void setup(ExploitHookFixture f) {
        f.supportPlayerOnFloor(); f.world.entities.put(1, f.player); f.player.sprint = true;
    }

    private static void impulse(ExploitHookFixture f) {
        f.receive(new S12PacketEntityVelocity(1, .3, .2, 0));
    }

    private static MovementInputEvent input(boolean manual) {
        MovementInputEvent event = new MovementInputEvent(0, 1, manual, false);
        Pulsar.BUS.post(event); return event;
    }

    private static void evidence(ExploitHookFixture f, ExploitHookFixture.Enemy enemy) {
        Pulsar.BUS.post(new PacketReceiveEvent(new S0BPacketAnimation(enemy, 0), f.pipe()));
        Pulsar.BUS.post(new PacketReceiveEvent(new S19PacketEntityStatus(f.player, (byte) 2), f.pipe()));
    }

    private static Velocity velocity(String mode) {
        Velocity module = new Velocity(); module.getModeSetting("Mode").setValue(mode); module.setToggled(true); return module;
    }

    @Test public void unsupportedOnGroundFlagCannotAuthorizeEitherAutomaticJump() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f); f.world.floor.clear();
            assertTrue(f.player.onGround); assertFalse(GroundJumpGuard.supported(f.client));
            JumpReset reset = new JumpReset(() -> 0, () -> 0); reset.setToggled(true);
            try {
                evidence(f, f.enemy(7, 3)); impulse(f);
                assertFalse(f.player.isAirBorne); assertEquals(.2, f.player.motionY, 1e-9);
            } finally { reset.setToggled(false); }
            for (String mode : new String[]{"Legit", "Legit Jumpreset", "Impulse Guard"}) {
                Velocity module = velocity(mode);
                try { impulse(f); assertFalse(mode, input(false).isJump()); }
                finally { module.setToggled(false); }
            }
        }
    }

    @Test public void leavingSupportAfterArmingConsumesAndRejectsThePendingJump() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f); Velocity module = velocity("Impulse Guard");
            try {
                impulse(f); f.world.floor.clear();
                assertFalse(input(false).isJump());
                f.supportPlayerOnFloor();
                assertFalse("unsupported input must not queue a later jump", input(false).isJump());
                impulse(f); assertTrue(input(false).isJump());
            } finally { module.setToggled(false); }
        }
    }

    @Test public void aSecondAppliedImpulseCannotRequestAnotherJumpInTheSameTick() throws Exception {
        for (String mode : new String[]{"Legit", "Legit Jumpreset", "Impulse Guard"})
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                setup(f); Velocity module = velocity(mode);
                try {
                    impulse(f); assertTrue(mode, input(false).isJump());
                    impulse(f); assertFalse("same-tick repeat in " + mode, input(false).isJump());
                    assertTrue("manual input is untouched by the automatic guard", input(true).isJump());
                    assertEquals(.3, f.player.motionX, 1e-9); assertEquals(.2, f.player.motionY, 1e-9);
                } finally { module.setToggled(false); }
            }
    }

    @Test public void freshHurtEvidenceDoesNotBypassTheStandaloneSameTickClaim() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f); JumpReset module = new JumpReset(() -> 0, () -> 0); module.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                evidence(f, enemy); impulse(f); assertTrue(f.player.isAirBorne);
                // Observe a second write independently of the first one.
                f.player.isAirBorne = false;
                evidence(f, enemy); impulse(f); assertFalse(f.player.isAirBorne);
                assertEquals(.2, f.player.motionY, 1e-9);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void aClaimRemainsSharedWhenTheJumpOwnerIsToggledWithinOneTick() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f); JumpReset reset = new JumpReset(() -> 0, () -> 0); reset.setToggled(true);
            try { evidence(f, f.enemy(7, 3)); impulse(f); assertTrue(f.player.isAirBorne); }
            finally { reset.setToggled(false); }
            Velocity module = velocity("Impulse Guard");
            try { impulse(f); assertFalse(input(false).isJump()); }
            finally { module.setToggled(false); }
        }
    }

    @Test public void airborneUpdatesRejectAndARealLaterLandingReadmits() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f); Velocity module = velocity("Impulse Guard");
            try {
                impulse(f); assertTrue(input(false).isJump());
                f.player.ticksExisted++; f.player.onGround = false;
                f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(0, .42, 0));
                impulse(f); assertFalse(input(false).isJump());
                f.player.ticksExisted++; f.player.onGround = true;
                assertFalse("stale onGround while feet are above the floor", GroundJumpGuard.supported(f.client));
                f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(0, -.42, 0));
                f.player.fallDistance = 0;
                impulse(f); assertTrue("a later physically supported landing is eligible", input(false).isJump());
            } finally { module.setToggled(false); }
        }
    }

    @Test public void aWallOrFallingStateCannotMasqueradeAsGroundSupport() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f);
            f.world.floor = Collections.singletonList(new AxisAlignedBB(-.4, -1, -.4, .4, 2, .4));
            assertFalse("a side collision extends above the feet", GroundJumpGuard.supported(f.client));
            f.supportPlayerOnFloor(); f.player.fallDistance = 1;
            assertFalse(GroundJumpGuard.supported(f.client));
            f.player.fallDistance = Float.NaN; assertFalse(GroundJumpGuard.supported(f.client));
            f.player.fallDistance = 0; f.player.noClip = true; assertFalse(GroundJumpGuard.supported(f.client));
        }
    }

    @Test public void collisionCallbacksCannotAuthorizeAStaleWorldOrDisabledOwner() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            setup(f);
            WorldClient other = NetworkTestSupport.bare(WorldClient.class);
            f.world.collisionCallback = () -> f.client.theWorld = other;
            assertFalse(GroundJumpGuard.tryClaim(f.client, () -> true));
            f.client.theWorld = f.world; f.world.collisionCallback = null;
            Velocity module = velocity("Impulse Guard");
            try {
                impulse(f);
                f.world.collisionCallback = () -> module.setToggled(false);
                assertFalse(input(false).isJump()); assertFalse(module.isToggled());
            } finally { module.setToggled(false); }
        }
    }
}
