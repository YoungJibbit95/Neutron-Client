package me.youngjibbit.core.world;

import net.minecraft.util.MathHelper;
import static java.lang.Math.*;

/** Independent oracle extracted from the pinned original, not from DemiseRotationEngine.
 * RotationHandler.java git blob 29555b7cfca3b1e5cccad94e4d5ed06902784a31 (AGPL-3.0).
 * The six method bodies below retain source arithmetic and control flow verbatim.
 * Only static ownership and System.nanoTime's clock dependency are adapted.
 * Minecraft/events/timers below are deterministic boundary stubs. The complete original
 * is retained as a hashed test resource so this oracle's provenance can be checked.
 */
final class DemiseOriginalRotation {
    enum SmoothMode { Linear, Relative }
    enum MovementCorrectionMode { Silent, Strict, None }
    static final class Clock { long millis = 1000, nanos = 1_000_000_000L; }
    static final class Player {
        float rotationYaw, rotationPitch;
        float[] getRotation() { return new float[]{rotationYaw, rotationPitch}; }
    }
    static final class Settings { float mouseSensitivity; }
    static final class Client { final Player thePlayer = new Player(); final Settings gameSettings = new Settings(); }
    static final class MouseMoveEvent {
        enum State { PRE, UPDATE }
        final State state;
        MouseMoveEvent(State state) { this.state = state; }
        State getState() { return state; }
    }
    final class TimerUtils {
        long lastMS = clock.millis;
        void reset() { lastMS = clock.millis; }
        boolean hasTimeElapsed(long time) { return clock.millis - lastMS > time; }
    }
    static final class RotationUtils {
        static float getRotationDifference(float[] a, float[] b) {
            return (float) hypot(abs(getAngleDifference(a[0], b[0])), abs(a[1] - b[1]));
        }
    }
    static float getAngleDifference(float a, float b) { return MathHelper.wrapAngleTo180_float(a - b); }
    static float getRotationDifference(float[] a, float[] b) { return RotationUtils.getRotationDifference(a, b); }
    final Clock clock;
    final Client mc = new Client();
    final TimerUtils tickTimer, tickTimer1, activeTimer;
    float[] currentRotation = new float[]{0, 0}, serverRotation, previousRotation = new float[]{0, 0}, targetRotation;
    float lastDelta, rotDiffBuildUp;
    boolean enabled, silent = true, reset = true, accel;
    float hSpeed = 180, vSpeed = 180, accelFactorYaw, accelFactorPitch, smoothingFactor = 1, timeScale;
    SmoothMode smoothMode = SmoothMode.Linear;
    MovementCorrectionMode correction = MovementCorrectionMode.None;
    long lastFrameTime;
    int previousDeltaYaw, previousDeltaPitch, interpolatedAccelDeltaYaw, interpolatedAccelDeltaPitch;
    DemiseOriginalRotation(Clock clock) {
        this.clock = clock;
        tickTimer = new TimerUtils(); tickTimer1 = new TimerUtils(); activeTimer = new TimerUtils();
        lastFrameTime = clock.nanos;
    }
public void setRotation(float[] rotation, MovementCorrectionMode correction, float[] speed, boolean accel, float[] accelFactor, SmoothMode smoothMode, boolean silent, float smoothingFactor) {
        if (tickTimer.hasTimeElapsed(50)) {
            lastDelta = getRotationDifference(currentRotation, previousRotation);

            tickTimer.reset();
        }

        this.targetRotation = rotation;
        this.silent = silent;
        this.hSpeed = speed[0];
        this.vSpeed = speed[1];
        this.smoothMode = smoothMode;
        this.correction = correction;
        this.accel = accel;
        this.accelFactorYaw = accelFactor[0];
        this.accelFactorPitch = accelFactor[1];
        this.smoothingFactor = smoothingFactor;
        enabled = true;
        activeTimer.reset();
    }

public void onMouseMove(MouseMoveEvent e) {
        if (currentRotation == null) {
            currentRotation = mc.thePlayer.getRotation();
        }

        if (e.getState() == MouseMoveEvent.State.PRE) {
            if (lastFrameTime == 0L) {
                lastFrameTime = clock.nanos;
            }

            long currentTime = clock.nanos;
            float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f;
            lastFrameTime = currentTime;

            deltaTime = Math.min(deltaTime, 1.0f / 90);
            timeScale = deltaTime * 120;
            return;
        }

        // expire enabled state if no rotation updates received for 150 milliseconds
        if (activeTimer.hasTimeElapsed(150)) {
            enabled = false;
        }

        if (enabled && targetRotation != null) {
            handleRotation(e, targetRotation);
        } else {
            if (abs(RotationUtils.getRotationDifference(currentRotation, mc.thePlayer.getRotation())) < 1 || reset) {
                currentRotation = mc.thePlayer.getRotation();
                targetRotation = null;
                reset = true;
                previousDeltaYaw = previousDeltaPitch = 0;
            } else {
                handleRotation(e, mc.thePlayer.getRotation());
            }
        }
    }

public boolean shouldRotate() {
        return currentRotation != null && targetRotation != null;
    }

private void handleRotation(MouseMoveEvent e, float[] target) {
        float[] delta = toFloats(limitRotations(target));

        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;

        float yawStep = delta[0] * timeScale * f1;
        float pitchStep = delta[1] * timeScale * f1;

        if (!silent) {
            // preserve manual mouse delta so physical mouse input is not frozen by aim assist
            mc.thePlayer.rotationYaw += yawStep * smoothingFactor;
            mc.thePlayer.rotationPitch -= pitchStep * smoothingFactor;
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch, -90f, 90f);
            currentRotation = mc.thePlayer.getRotation();
        } else {
            currentRotation[0] += yawStep * smoothingFactor;
            currentRotation[1] -= pitchStep * smoothingFactor;
            currentRotation[1] = MathHelper.clamp_float(currentRotation[1], -90f, 90f);
        }

        reset = false;
    }

private int[] limitRotations(float[] target) {
        float yawDifference = getAngleDifference(target[0], currentRotation[0]);
        float pitchDifference = getAngleDifference(target[1], currentRotation[1]);

        double rotationDifference = hypot(abs(yawDifference), abs(pitchDifference));

        // guard against division by zero which produces nan values
        if (rotationDifference == 0) {
            return new int[]{0, 0};
        }

        float straightLineYaw = (float) (abs(yawDifference / rotationDifference) * hSpeed);
        float straightLinePitch = (float) (abs(pitchDifference / rotationDifference) * vSpeed);

        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;

        int[] finalTargetRotation = new int[]{
                (int) (max(-straightLineYaw, min(straightLineYaw, yawDifference)) / f1),
                (int) -(max(-straightLinePitch, min(straightLinePitch, pitchDifference)) / f1)
        };

        int[] delta;

        if (smoothMode.equals(SmoothMode.Relative)) {
            // direct proportional easing toward target chest center with negated pitch step to match subtraction in handlerotation
            float targetYawStep = (float) max(-hSpeed, min(hSpeed, yawDifference * 0.25f));
            float targetPitchStep = (float) max(-vSpeed, min(vSpeed, pitchDifference * 0.25f));

            delta = new int[]{
                    (int) (targetYawStep / f1),
                    (int) -(targetPitchStep / f1)
            };
        } else {
            delta = finalTargetRotation;
        }

        if (accel) {
            float[] factors = new float[]{accelFactorYaw, accelFactorPitch};

            // 16.67 is roughly the frame delay of 60 fps
            if (tickTimer1.hasTimeElapsed((long) 16.67)) {
                interpolatedAccelDeltaYaw = (int) (delta[0] * (1 - factors[0]) + previousDeltaYaw * factors[0]);
                interpolatedAccelDeltaPitch = (int) (delta[1] * (1 - factors[1]) + previousDeltaPitch * factors[1]);
                tickTimer1.reset();
            } else if (interpolatedAccelDeltaYaw == 0 && interpolatedAccelDeltaPitch == 0) {
                // initialize acceleration interpolation state to avoid initial zero delta delay
                interpolatedAccelDeltaYaw = delta[0];
                interpolatedAccelDeltaPitch = delta[1];
            }

            delta[0] = interpolatedAccelDeltaYaw;
            delta[1] = interpolatedAccelDeltaPitch;
        }

        previousDeltaYaw = delta[0];
        previousDeltaPitch = delta[1];

        return delta;
    }

private float[] toFloats(int[] ints) {
        return new float[]{ints[0], ints[1]};
    }
}
