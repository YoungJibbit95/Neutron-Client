package me.youngjibbit.core.world;

import net.minecraft.util.MathHelper;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import static java.lang.Math.*;

/**
 * NScaffold's instance-local port of DemisePrivate e219a97 RotationManager/RotationHandler.
 * Source: wtf/demise/utils/player/rotation (AGPL-3.0). Arithmetic, integer mouse counts,
 * frame timing and the 150-ms expiry/return are retained. Scene guards belong to the adapter.
 */
public final class DemiseRotationEngine {
    public enum Smoothing { Linear, Relative }
    public enum Correction { Silent, Strict, None }
    public interface FloatRandom { float sample(float minimum, float maximum); }
    public static final class Aim {
        public final float yaw, pitch;
        private Aim(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        public float[] array() { return new float[]{yaw, pitch}; }
    }
    public static Aim aim(float yaw, float pitch) { return new Aim(yaw, pitch); }

    private final LongSupplier millis, nanos;
    private final FloatRandom random;
    private float[] currentRotation = {0, 0}, serverRotation, previousRotation, targetRotation;
    private float lastDelta, rotDiffBuildUp, randYawSpeed, randPitchSpeed;
    private boolean enabled, silent = true, reset = true, accel;
    private boolean lastFrameStationary;
    private Smoothing smoothMode = Smoothing.Linear;
    private Correction correction = Correction.None;
    private float hSpeed = 180, vSpeed = 180, accelFactorYaw, accelFactorPitch, smoothingFactor = 1;
    private long tickTime, accelTime, activeTime, shortStopTime, lastFrameTime;
    private float timeScale;
    private int previousDeltaYaw, previousDeltaPitch, interpolatedAccelDeltaYaw, interpolatedAccelDeltaPitch;
    private int maxThresholdReachAttempts;

    public DemiseRotationEngine() {
        this(System::currentTimeMillis, System::nanoTime, DemiseRotationEngine::randomizeFloat);
    }

    public DemiseRotationEngine(LongSupplier millis, LongSupplier nanos, FloatRandom random) {
        if (millis == null || nanos == null || random == null) throw new IllegalArgumentException("rotation clock/random");
        this.millis = millis; this.nanos = nanos; this.random = random;
        tickTime = accelTime = activeTime = shortStopTime = millis.getAsLong();
        lastFrameTime = nanos.getAsLong();
    }

    /** The source's randomizeDouble upper bound is max+1 unless both bounds are below one. */
    public static float randomizeFloat(float min, float max) {
        if (min == max) return min;
        if (min < 1 && max < 1) return (float) (Math.random() * (max - min) + min);
        return (float) ThreadLocalRandom.current().nextDouble(min, max + 1D);
    }

    /** Original UpdateEvent: sample once; setRotation reuses these speeds across frames. */
    public void updateSpeeds(DemiseRotationSettings.Snapshot settings) {
        randYawSpeed = random.sample(settings.yawRotationSpeedMin, settings.yawRotationSpeedMax);
        randPitchSpeed = random.sample(settings.pitchRotationSpeedMin, settings.pitchRotationSpeedMax);
    }

    /** Original Clutch only overwrites the sampled speeds; all other settings still apply. */
    public void forceSpeeds(float yaw, float pitch) { randYawSpeed = yaw; randPitchSpeed = pitch; }

    public void setRotation(float[] target, DemiseRotationSettings.Snapshot settings) {
        if (target == null || target.length < 2 || !Float.isFinite(target[0]) || !Float.isFinite(target[1])) return;
        float horizontal = randYawSpeed, vertical = randPitchSpeed;
        if (settings.shortStop && shouldShortStop(settings)) {
            horizontal = random.sample(0, .1f);
            vertical = random.sample(0, .1f);
        }
        if (settings.imperfectCorrelation) {
            horizontal *= random.sample(.9f, 1.1f);
            vertical *= random.sample(.9f, 1.1f);
        }
        horizontal = MathHelper.clamp_float(horizontal, 0, 180);
        vertical = MathHelper.clamp_float(vertical, 0, 180);
        long now = millis.getAsLong();
        if (now - tickTime > 50) {
            // The first scene can precede Demise's walking-frame previousRotation assignment.
            lastDelta = previousRotation == null ? 0 : rotationDifference(currentRotation, previousRotation);
            tickTime = now;
        }
        targetRotation = target;
        silent = settings.silent;
        hSpeed = horizontal; vSpeed = vertical;
        smoothMode = settings.smoothMode;
        correction = settings.moveCorrection;
        accel = settings.accel;
        accelFactorYaw = settings.yawAccelFactor; accelFactorPitch = settings.pitchAccelFactor;
        smoothingFactor = settings.extraSmoothFactor;
        enabled = true;
        activeTime = now;
    }

    private boolean shouldShortStop(DemiseRotationSettings.Snapshot settings) {
        long now = millis.getAsLong();
        if (now - shortStopTime <= (long) settings.shortStopDuration) return true;
        if (Math.abs(rotDiffBuildUp) < settings.rotationDiffBuildUpToStop) return false;
        if (maxThresholdReachAttempts < settings.maxThresholdAttemptsToStop) {
            maxThresholdReachAttempts++;
            return false;
        }
        shortStopTime = now;
        return true;
    }

    /** MouseMoveEvent.PRE, including frames without physical mouse delta. */
    public void mousePre() {
        if (lastFrameTime == 0L) lastFrameTime = nanos.getAsLong();
        long currentTime = nanos.getAsLong();
        float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = currentTime;
        deltaTime = Math.min(deltaTime, 1.0f / 90);
        timeScale = deltaTime * 120;
    }

    /** MouseMoveEvent.UPDATE precedes Entity.setAngles/AngleEvent and physical mouse integration.
     * For Silent=false the adapter applies this camera pose before vanilla setAngles.
     */
    public Aim mouseUpdate(float cameraYaw, float cameraPitch, float sensitivity) {
        if (currentRotation == null) currentRotation = new float[]{cameraYaw, cameraPitch};
        float beforeYaw = currentRotation[0], beforePitch = currentRotation[1];
        boolean hadResidual = targetRotation != null
                && (angleDifference(targetRotation[0], beforeYaw) != 0 || angleDifference(targetRotation[1], beforePitch) != 0);
        if (millis.getAsLong() - activeTime > 150) enabled = false;
        if (enabled && targetRotation != null) {
            handleRotation(targetRotation, cameraYaw, cameraPitch, sensitivity);
        } else if (abs(rotationDifference(currentRotation, new float[]{cameraYaw, cameraPitch})) < 1 || reset) {
            currentRotation = new float[]{cameraYaw, cameraPitch};
            targetRotation = null;
            reset = true;
            previousDeltaYaw = previousDeltaPitch = 0;
        } else {
            handleRotation(new float[]{cameraYaw, cameraPitch}, cameraYaw, cameraPitch, sensitivity);
        }
        // Observation only: the adapter may choose a new target after a real stalled frame.
        lastFrameStationary = enabled && timeScale > 0 && hadResidual && targetRotation != null
                && currentRotation[0] == beforeYaw && currentRotation[1] == beforePitch;
        return new Aim(currentRotation[0], currentRotation[1]);
    }

    private void handleRotation(float[] target, float cameraYaw, float cameraPitch, float sensitivity) {
        int[] delta = limitRotations(target, sensitivity);
        float f = sensitivity * .6F + .2F;
        float f1 = f * f * f * 8.0F;
        float yawStep = delta[0] * timeScale * f1;
        float pitchStep = delta[1] * timeScale * f1;
        if (!silent) {
            cameraYaw += yawStep * smoothingFactor;
            cameraPitch -= pitchStep * smoothingFactor;
            cameraPitch = MathHelper.clamp_float(cameraPitch, -90f, 90f);
            currentRotation = new float[]{cameraYaw, cameraPitch};
        } else {
            currentRotation[0] += yawStep * smoothingFactor;
            currentRotation[1] -= pitchStep * smoothingFactor;
            currentRotation[1] = MathHelper.clamp_float(currentRotation[1], -90f, 90f);
        }
        reset = false;
    }

    private int[] limitRotations(float[] target, float sensitivity) {
        float yawDifference = angleDifference(target[0], currentRotation[0]);
        float pitchDifference = angleDifference(target[1], currentRotation[1]);
        double rotationDifference = hypot(abs(yawDifference), abs(pitchDifference));
        if (rotationDifference == 0) return new int[]{0, 0};
        float straightLineYaw = (float) (abs(yawDifference / rotationDifference) * hSpeed);
        float straightLinePitch = (float) (abs(pitchDifference / rotationDifference) * vSpeed);
        float f = sensitivity * .6F + .2F;
        float f1 = f * f * f * 8.0F;
        int[] delta;
        if (smoothMode == Smoothing.Relative) {
            float targetYawStep = max(-hSpeed, min(hSpeed, yawDifference * .25f));
            float targetPitchStep = max(-vSpeed, min(vSpeed, pitchDifference * .25f));
            delta = new int[]{(int) (targetYawStep / f1), (int) -(targetPitchStep / f1)};
        } else {
            delta = new int[]{(int) (max(-straightLineYaw, min(straightLineYaw, yawDifference)) / f1),
                    (int) -(max(-straightLinePitch, min(straightLinePitch, pitchDifference)) / f1)};
        }
        if (accel) {
            long now = millis.getAsLong();
            if (now - accelTime > (long) 16.67) {
                interpolatedAccelDeltaYaw = (int) (delta[0] * (1 - accelFactorYaw) + previousDeltaYaw * accelFactorYaw);
                interpolatedAccelDeltaPitch = (int) (delta[1] * (1 - accelFactorPitch) + previousDeltaPitch * accelFactorPitch);
                accelTime = now;
            } else if (interpolatedAccelDeltaYaw == 0 && interpolatedAccelDeltaPitch == 0) {
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

    /** The shared transport supplies only an accepted C03 from this scene. */
    public void observeAcceptedPacket(boolean rotating, float yaw, float pitch) {
        if (!rotating) { rotDiffBuildUp = 0; return; }
        if (serverRotation != null && shouldRotate()) rotDiffBuildUp += angleDifference(yaw, serverRotation[0]);
        serverRotation = new float[]{yaw, pitch};
    }

    /** Original PlayerSP aliases currentRotation while active (it does not clone this array). */
    public void observeMotion(float yaw, float pitch) {
        previousRotation = shouldRotate() ? currentRotation : new float[]{yaw, pitch};
    }
    public boolean shouldRotate() { return currentRotation != null && targetRotation != null; }
    public boolean enabled() { return enabled; }
    public boolean wasLastFrameStationary() { return lastFrameStationary; }
    public void resetFrameObservation() { lastFrameStationary = false; }
    public boolean silent() { return silent; }
    public Correction correction() { return correction; }
    public Aim currentAim() { return shouldRotate() ? new Aim(currentRotation[0], currentRotation[1]) : null; }
    public float[] rotation() { return currentRotation == null ? null : currentRotation.clone(); }
    public boolean hasServerRotation() { return serverRotation != null; }
    public float[] serverRotation() { return serverRotation == null ? null : serverRotation.clone(); }
    public float lastDelta() { return lastDelta; }
    public float rotationDiffBuildUp() { return rotDiffBuildUp; }

    /** Immediate scene/lifecycle invalidation. Normal disable instead stops target refreshes. */
    public void clear(float cameraYaw, float cameraPitch) {
        currentRotation = new float[]{cameraYaw, cameraPitch};
        targetRotation = previousRotation = serverRotation = null;
        enabled = false; reset = true; lastFrameStationary = false;
        correction = Correction.None;
        previousDeltaYaw = previousDeltaPitch = interpolatedAccelDeltaYaw = interpolatedAccelDeltaPitch = 0;
        rotDiffBuildUp = lastDelta = 0;
        maxThresholdReachAttempts = 0;
        tickTime = accelTime = activeTime = shortStopTime = millis.getAsLong();
        lastFrameTime = nanos.getAsLong();
        timeScale = 0;
    }

    private static float angleDifference(float a, float b) { return MathHelper.wrapAngleTo180_float(a - b); }
    private static float rotationDifference(float[] a, float[] b) {
        return (float) hypot(abs(angleDifference(a[0], b[0])), abs(a[1] - b[1]));
    }
}
