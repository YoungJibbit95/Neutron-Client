package me.youngjibbit.utils;

import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** World-space geometry, including degree/radian precision and sign-based sneak input. */
public class MovementHelperContractTest {
    private static final float[] YAWS = {0, 90, -90, 720.125f, -1080.125f};
    private static final float[] INPUTS = {-1, -.3f, 0, .3f, 1};

    private static EntityPlayerSP player() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        EntityPlayerSP player = (EntityPlayerSP) ((sun.misc.Unsafe) f.get(null)).allocateInstance(EntityPlayerSP.class);
        player.movementInput = new MovementInput();
        return player;
    }

    @Test public void directionPreservesTheWorldVectorForEveryDigitalAndSneakDirection() {
        for (float yaw : YAWS) for (float forward : INPUTS) for (float strafe : INPUTS) {
            if (forward == 0 && strafe == 0) continue;
            double angle = MoveUtil.directionRadians(yaw, forward, strafe), camera = Math.toRadians(yaw);
            double s = Math.signum(strafe), f = Math.signum(forward), length = Math.hypot(s, f);
            double x = (s * Math.cos(camera) - f * Math.sin(camera)) / length;
            double z = (f * Math.cos(camera) + s * Math.sin(camera)) / length;
            assertEquals(x, -Math.sin(angle), 1e-12);
            assertEquals(z, Math.cos(angle), 1e-12);
        }
    }

    @Test public void noInputKeepsTheCameraBasisWithoutNarrowingRadiansToFloat() {
        assertEquals(Math.toRadians(720.125f), MoveUtil.directionRadians(720.125f, 0, 0), 0);
    }

    @Test public void strafePreservesSpeedVerticalMotionAndAnglesWithoutTouchingAnotherPlayer() throws Exception {
        EntityPlayerSP player = player(), other = player();
        other.motionX = 7; other.motionZ = -8;
        for (float yaw : YAWS) for (float forward : INPUTS) for (float strafe : INPUTS) {
            player.rotationYaw = yaw; player.rotationPitch = 23;
            player.moveForward = forward; player.moveStrafing = strafe;
            player.motionX = 2; player.motionZ = -3; player.motionY = .42;
            MoveUtil.strafe(player, .2873);
            if (forward == 0 && strafe == 0) {
                assertFalse(MoveUtil.hasMovementInput(player));
                assertEquals(2, player.motionX, 0); assertEquals(-3, player.motionZ, 0);
            } else {
                assertTrue(MoveUtil.hasMovementInput(player));
                assertEquals(.2873, MoveUtil.horizontalSpeed(player), 1e-15);
                double angle = Math.toRadians(yaw);
                double inputLength = Math.hypot(Math.signum(strafe), Math.signum(forward));
                assertEquals(.2873 * (Math.signum(strafe) * Math.cos(angle) - Math.signum(forward) * Math.sin(angle)) / inputLength, player.motionX, 1e-12);
                assertEquals(.2873 * (Math.signum(forward) * Math.cos(angle) + Math.signum(strafe) * Math.sin(angle)) / inputLength, player.motionZ, 1e-12);
            }
            assertEquals(.42, player.motionY, 0);
            assertEquals(yaw, player.rotationYaw, 0); assertEquals(23, player.rotationPitch, 0);
            assertEquals(7, other.motionX, 0); assertEquals(-8, other.motionZ, 0);
        }
    }

    @Test public void rawInputAssignmentUsesRawFieldsAndStopsWithoutInput() throws Exception {
        EntityPlayerSP player = player();
        player.moveForward = -1; player.moveStrafing = 1; // processed fields deliberately differ
        player.motionY = -.2;
        for (float yaw : YAWS) for (float forward : INPUTS) for (float strafe : INPUTS) {
            player.rotationYaw = yaw;
            MoveUtil.setSpeedFromInput(player, .7, forward, strafe);
            double s = Math.signum(strafe), f = Math.signum(forward), length = Math.hypot(s, f);
            double camera = Math.toRadians(yaw);
            assertEquals(length == 0 ? 0 : .7 * (s * Math.cos(camera) - f * Math.sin(camera)) / length, player.motionX, 1e-12);
            assertEquals(length == 0 ? 0 : .7 * (f * Math.cos(camera) + s * Math.sin(camera)) / length, player.motionZ, 1e-12);
            assertEquals(-.2, player.motionY, 0);
        }
    }

    @Test public void actualMovementEventKeepsRawGroundInputAndAirFrictionSeparate() throws Exception {
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        float previousForward = MovementInput.moveForward, previousStrafe = MovementInput.moveStrafe;
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Minecraft client = (Minecraft) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Minecraft.class);
        EntityPlayerSP player = player(); client.thePlayer = player;
        try {
            singleton.set(null, client);
            player.moveForward = -1; player.moveStrafing = 1;
            MovementInput.moveForward = 1; MovementInput.moveStrafe = 0;
            player.rotationYaw = 90; player.rotationPitch = 11; player.motionY = .42;
            PlayerMoveUpdateEvent event = new PlayerMoveUpdateEvent(1, -1, .1f, -90, 20);
            player.onGround = true;
            event.applyMotion(.5, .2f);
            assertEquals(-.5, player.motionX, 1e-15); assertEquals(0, player.motionZ, 1e-15);
            assertEquals(.1f, event.getFriction(), 0);
            player.onGround = false;
            event.applyMotion(.5, .2f);
            assertEquals(-.5 * (float) (.2f * .91), player.motionX, 1e-15);
            assertEquals(.5f * (1 - .2f), event.getFriction(), 0);
            assertEquals(.42, player.motionY, 0);
            assertEquals(90, player.rotationYaw, 0); assertEquals(11, player.rotationPitch, 0);
            assertEquals(-90, event.getYaw(), 0); assertEquals(20, event.getPitch(), 0);
            player.onGround = true;
            MovementInput.moveForward = 0; MovementInput.moveStrafe = 0;
            event.applyMotion(.5, .2f);
            assertEquals(0, player.motionX, 0); assertEquals(0, player.motionZ, 0);
        } finally {
            singleton.set(null, previous);
            MovementInput.moveForward = previousForward; MovementInput.moveStrafe = previousStrafe;
        }
    }

}
