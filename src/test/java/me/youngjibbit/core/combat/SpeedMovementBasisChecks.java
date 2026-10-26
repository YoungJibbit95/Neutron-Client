package me.youngjibbit.core.combat;

import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.utils.Eierlecken;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.Timer;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Real Speed consumers, disposable client, no native window or server. */
public class SpeedMovementBasisChecks {
    private sun.misc.Unsafe unsafe;
    private Field utilityClient;
    private Object oldUtilityClient;

    @Before public void saveLegacyUtilityClient() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true); unsafe = (sun.misc.Unsafe) field.get(null);
        utilityClient = Eierlecken.class.getDeclaredField("mc");
        oldUtilityClient = utilityClient.get(null);
    }

    @After public void restoreLegacyUtilityClient() {
        unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), oldUtilityClient);
    }

    private void prepare(RotationBehaviorHookTest.Fixture f) throws Exception {
        unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), f.mc);
        f.mc.timer = RotationBehaviorHookTest.bare(Timer.class);
        f.mc.gameSettings = RotationBehaviorHookTest.bare(GameSettings.class);
        f.mc.gameSettings.keyBindJump = RotationBehaviorHookTest.bare(KeyBinding.class);
        f.player.rotationYaw = 180;
        f.player.sprint = true;
    }

    private static void run(Speed speed, String mode) throws Exception {
        Method method = Speed.class.getDeclaredMethod("handle" + mode);
        method.setAccessible(true);
        method.invoke(speed);
    }

    private static void direction(RotationBehaviorHookTest.Fixture f, double degrees) {
        double magnitude = Math.hypot(f.player.motionX, f.player.motionZ);
        assertTrue("Speed must write nonzero motion", magnitude > .01);
        assertEquals(-Math.sin(Math.toRadians(degrees)), f.player.motionX / magnitude, 2e-5);
        assertEquals(Math.cos(Math.toRadians(degrees)), f.player.motionZ / magnitude, 2e-5);
    }

    @Test public void everyGroundAndFirstAirWriterUsesAcceptedBasisForBothOwnersAndAllKeys() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f);
            for (String owner : new String[]{"aura", "scaffold"})
                for (String mode : new String[]{"Intave", "Bmc", "Polar", "Vulcan", "Vulcan2"})
                    for (float forward : new float[]{-1, 0, 1}) for (float strafe : new float[]{-1, 0, 1}) {
                        if (forward == 0 && strafe == 0) continue;
                        Speed speed = new Speed();
                        speed.getBooleanSetting("Air Speed").setValue(true);
                        f.player.moveForward = forward; f.player.moveStrafing = strafe;
                        f.player.motionX = f.player.motionZ = 0; f.player.onGround = true;
                        f.accept(owner, 90);
                        // Reproduce the audit's real moveFlying -> Speed chain.
                        f.player.moveFlying(strafe, forward, .1f);
                        run(speed, mode);
                        direction(f, MoveUtil.directionYaw(90, forward, strafe));
                        assertEquals(180, f.player.rotationYaw, 0);
                        assertTrue("Jump impulse retained", mode.equals("Bmc") || f.player.motionY > .4);
                        if (mode.equals("Polar") || mode.equals("Vulcan") || mode.equals("Vulcan2")) {
                            f.player.onGround = false;
                            // Poison the incoming direction so the first-air assignment must correct it.
                            f.player.motionX = 0; f.player.motionZ = -.5;
                            run(speed, mode);
                            direction(f, MoveUtil.directionYaw(90, forward, strafe));
                        }
                    }
        }
    }

    @Test public void modesKeepCameraIntentOrAcceptedAxesWithoutDoubleTransform() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f);
            for (String owner : new String[]{"aura", "scaffold"})
                for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
                    f.aura.getModeSetting("movefix").setValue(mode.label);
                    f.scaffold.getModeSetting("movefix").setValue(mode.label);
                    f.player.moveForward = .2f; f.player.moveStrafing = .2f;
                    f.player.onGround = true; f.accept(owner, 73);
                    f.player.moveFlying(.2f, .2f, .1f);
                    run(new Speed(), "Bmc");
                    double expected = mode == MoveFixController.Mode.ROTATION_BOUND ? 28
                            : mode == MoveFixController.Mode.SIMPLE ? 118 : 135;
                    direction(f, expected);
                    assertEquals(.2f, f.player.moveForward, 0);
                    assertEquals(.2f, f.player.moveStrafing, 0);
                    assertEquals(180, f.player.rotationYaw, 0);
                    assertEquals(.2873 * 1.2 * 1.08, MoveUtil.horizontalSpeed(f.player), 1e-9);
                }
        }
    }

    @Test public void jumpAndLaterAirScalingKeepDirectionAndVerticalMechanics() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.player.moveForward = .3f;
            for (String mode : new String[]{"Intave", "Bmc", "Polar", "Vulcan", "Vulcan2"}) {
                Speed speed = new Speed(); speed.getBooleanSetting("Air Speed").setValue(true);
                f.player.moveStrafing = 0; f.player.onGround = true;
                f.player.motionX = f.player.motionZ = 0; f.accept("scaffold", 90);
                run(speed, mode);
                if (mode.equals("Bmc")) run(speed, mode); // even ground tick includes its jump
                direction(f, 90); assertTrue(f.player.motionY > .4);
                f.player.onGround = false;
                for (int tick = 1; tick <= 6; tick++) {
                    run(speed, mode); direction(f, 90);
                    assertEquals(180, f.player.rotationYaw, 0);
                }
                if (mode.equals("Vulcan2")) assertEquals(-.13, f.player.motionY, 0);
            }
        }
    }

    @Test public void pendingAndLostOwnersAndManualCameraUseCurrentCamera() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.player.moveForward = 1; f.player.onGround = true;
            f.accept("aura", 90);
            RotationManager r = f.runtime.getRotationManager();
            r.beginMotion(f.player, f.world, 180, 0); r.request("scaffold", 30, -90, 0);
            run(new Speed(), "Bmc"); direction(f, 90); // requested is not accepted
            r.reset("aura"); f.player.rotationYaw = 27;
            run(new Speed(), "Bmc"); direction(f, 27);
            f.accept("scaffold", 90); r.reset("scaffold"); f.player.rotationYaw = -35;
            run(new Speed(), "Bmc"); direction(f, -35);
            f.accept("aimassist", 90);
            run(new Speed(), "Bmc"); direction(f, -35);
            f.accept("aura", 90); f.mc.inGameHasFocus = false;
            run(new Speed(), "Bmc"); direction(f, -35);
        }
    }

    @Test public void rawInputSetterRetainsItsCameraContract() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); f.accept("aura", 90);
            MoveUtil.setSpeedFromInput(f.player, .4, 1, 0); direction(f, 180);
            MoveUtil.setSpeedFromInput(f.player, .4, 0, 0);
            assertEquals(0, MoveUtil.horizontalSpeed(f.player), 0);
        }
    }
}
