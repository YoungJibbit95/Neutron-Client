package me.youngjibbit.core.world;

import net.minecraft.util.MathHelper;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import static java.lang.Math.*;

/**
 * Instance-local port of DemisePrivate e219a97 RotationManager/RotationHandler.
 * Source: wtf/demise/utils/player/rotation (AGPL-3.0). Arithmetic, integer mouse counts,
 * frame timing and the 150-ms expiry/return are retained by the default engine. BedBreaker opts
 * into bounded continuous acquisition/return without changing that source-parity path.
 * Scene guards belong to the adapter.
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
    private final boolean continuous;
    private boolean returning;
    private float[] continuousWireBase;
    /** Continuous owners never publish an accumulated whole-angle catch-up after a rejected C03. */
    public static final float MAX_CONTINUOUS_PACKET_TURN = 45;
    public static final float MAX_CONTINUOUS_FRAME_TURN = 12;
    private static final float CONTINUOUS_EPSILON = .0001f;
    private float[] currentRotation = {0, 0}, serverRotation, previousRotation, targetRotation;
    private float lastDelta, rotDiffBuildUp, randYawSpeed, randPitchSpeed;
    private boolean enabled, silent = true, reset = true, accel;
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
        this(millis, nanos, random, false);
    }

    private DemiseRotationEngine(LongSupplier millis, LongSupplier nanos, FloatRandom random, boolean continuous) {
        if (millis == null || nanos == null || random == null) throw new IllegalArgumentException("rotation clock/random");
        this.millis = millis; this.nanos = nanos; this.random = random;
        this.continuous = continuous;
        tickTime = accelTime = activeTime = shortStopTime = millis.getAsLong();
        lastFrameTime = nanos.getAsLong();
    }

    /** BedBreaker opts into continuous acquisition/tracking/return; the source-parity default is unchanged. */
    public static DemiseRotationEngine continuous() {
        return continuous(System::currentTimeMillis, System::nanoTime, DemiseRotationEngine::randomizeFloat);
    }

    public static DemiseRotationEngine continuous(LongSupplier millis, LongSupplier nanos, FloatRandom random) {
        return new DemiseRotationEngine(millis, nanos, random, true);
    }

    /** The source's randomizeDouble upper bound is max+1 unless both bounds are below one. */
    public static float randomizeFloat(float min, float max) {
        if (min == max) return min;
        if (min < 1 && max < 1) return (float) (Math.random() * (max - min) + min);
        return (float) ThreadLocalRandom.current().nextDouble(min, max + 1D);
    }

    /** Original UpdateEvent: sample once; setRotation reuses these speeds across frames. */
    public void updateSpeeds(DemiseRotationSettings.Snapshot settings) {
        if (continuous) {
            // Independent sliders may cross while editing; the continuous owner keeps a valid interval.
            randYawSpeed = random.sample(Math.min(settings.yawRotationSpeedMin, settings.yawRotationSpeedMax),
                    Math.max(settings.yawRotationSpeedMin, settings.yawRotationSpeedMax));
            randPitchSpeed = random.sample(Math.min(settings.pitchRotationSpeedMin, settings.pitchRotationSpeedMax),
                    Math.max(settings.pitchRotationSpeedMin, settings.pitchRotationSpeedMax));
        } else {
            randYawSpeed = random.sample(settings.yawRotationSpeedMin, settings.yawRotationSpeedMax);
            randPitchSpeed = random.sample(settings.pitchRotationSpeedMin, settings.pitchRotationSpeedMax);
        }
    }

    /** Original Clutch only overwrites the sampled speeds; all other settings still apply. */
    public void forceSpeeds(float yaw, float pitch) { randYawSpeed = yaw; randPitchSpeed = pitch; }

    public void setRotation(float[] target, DemiseRotationSettings.Snapshot settings) {
        if (target == null || target.length < 2 || !Float.isFinite(target[0]) || !Float.isFinite(target[1])) return;
        if (continuous && !shouldRotate()) {
            if (serverRotation != null) currentRotation = serverRotation.clone();
            continuousWireBase = currentRotation.clone();
        }
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
        targetRotation = continuous ? target.clone() : target;
        returning = false;
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
        if (continuous) return continuousUpdate(cameraYaw, cameraPitch, sensitivity);
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
        return new Aim(currentRotation[0], currentRotation[1]);
    }

    /** Stop task refreshes immediately, while preserving a rotation-only tail and its movement settings. */
    public void beginReturn() {
        enabled = false;
        returning = shouldRotate();
    }

    public boolean returning() { return returning && shouldRotate(); }

    /** Changing Silent while tracking must not copy a distant server pose straight into the camera. */
    public Aim cameraAim(float cameraYaw, float cameraPitch) {
        if (!continuous) return currentAim();
        if (!shouldRotate() || !Float.isFinite(cameraYaw) || !Float.isFinite(cameraPitch)
                || !Float.isFinite(timeScale) || timeScale <= 0) return new Aim(cameraYaw, cameraPitch);
        float yaw = angleDifference(currentRotation[0], cameraYaw) * .5f;
        float pitch = (currentRotation[1] - cameraPitch) * .5f;
        double length = Math.hypot(yaw, pitch);
        double maximum = MAX_CONTINUOUS_FRAME_TURN * Math.min(timeScale, 4F / 3F) / (4D / 3D);
        if (length > maximum) {
            yaw = (float) (yaw * (maximum / length));
            pitch = (float) (pitch * (maximum / length));
        }
        return new Aim(cameraYaw + yaw, MathHelper.clamp_float(cameraPitch + pitch, -90, 90));
    }

    /** A different accepted owner is the starting pose at a handoff, never an old invisible trajectory. */
    public void resumeFromServer() {
        if (!continuous || !shouldRotate() || serverRotation == null) return;
        currentRotation = serverRotation.clone();
        continuousWireBase = serverRotation.clone();
        previousDeltaYaw = previousDeltaPitch = interpolatedAccelDeltaYaw = interpolatedAccelDeltaPitch = 0;
    }

    private Aim continuousUpdate(float cameraYaw, float cameraPitch, float sensitivity) {
        if (!Float.isFinite(cameraYaw) || !Float.isFinite(cameraPitch) || !Float.isFinite(sensitivity))
            return new Aim(currentRotation[0], currentRotation[1]);
        cameraPitch = MathHelper.clamp_float(cameraPitch, -90, 90);
        if (enabled && millis.getAsLong() - activeTime > 150) beginReturn();
        if (!shouldRotate()) {
            currentRotation = new float[]{cameraYaw, cameraPitch};
            return new Aim(cameraYaw, cameraPitch);
        }
        float[] target = enabled ? targetRotation : new float[]{cameraYaw, cameraPitch};
        returning = !enabled;
        float yawDifference = angleDifference(target[0], currentRotation[0]);
        float pitchDifference = MathHelper.clamp_float(target[1], -90, 90) - currentRotation[1];
        // A sub-pixel residual replaces the source's visible <1-degree snap/dead zone.
        float epsilon = Math.max(CONTINUOUS_EPSILON, Math.max(Math.ulp(currentRotation[0]), Math.ulp(target[0])));
        if (returning && Math.hypot(yawDifference, pitchDifference) <= epsilon) {
            currentRotation = new float[]{cameraYaw, cameraPitch};
            targetRotation = null; returning = false; reset = true;
            previousDeltaYaw = previousDeltaPitch = interpolatedAccelDeltaYaw = interpolatedAccelDeltaPitch = 0;
            return new Aim(cameraYaw, cameraPitch);
        }
        if (!Float.isFinite(timeScale) || timeScale <= 0) return new Aim(currentRotation[0], currentRotation[1]);
        float savedH = hSpeed, savedV = vSpeed;
        float factor = MathHelper.clamp_float(smoothingFactor, .01f, 1);
        if (returning) {
            // A short stop or a deliberately tiny tracking speed must not strand a disabled owner.
            hSpeed = Math.max(15, randYawSpeed); vSpeed = Math.max(15, randPitchSpeed);
            factor = Math.max(.15f, factor);
        }
        int[] delta;
        try { delta = limitRotations(target, MathHelper.clamp_float(sensitivity, 0, 1)); }
        finally { hSpeed = savedH; vSpeed = savedV; }
        float f = MathHelper.clamp_float(sensitivity, 0, 1) * .6F + .2F;
        float count = f * f * f * 8;
        float yawStep = delta[0] * timeScale * count * factor;
        float pitchStep = -delta[1] * timeScale * count * factor;
        float horizontal = returning ? Math.max(15, randYawSpeed) : hSpeed;
        float vertical = returning ? Math.max(15, randPitchSpeed) : vSpeed;
        yawStep = continuousStep(yawStep, yawDifference, horizontal, factor, currentRotation[0]);
        pitchStep = continuousStep(pitchStep, pitchDifference, vertical, factor, currentRotation[1]);
        double distance = Math.hypot(yawStep, pitchStep);
        double frameMaximum = MAX_CONTINUOUS_FRAME_TURN * Math.min(timeScale, 4F / 3F) / (4D / 3D);
        if (distance > frameMaximum) {
            yawStep = (float) (yawStep * (frameMaximum / distance));
            pitchStep = (float) (pitchStep * (frameMaximum / distance));
        }
        float nextYaw = currentRotation[0] + yawStep, nextPitch = MathHelper.clamp_float(currentRotation[1] + pitchStep, -90, 90);
        float[] wire = serverRotation != null ? serverRotation : continuousWireBase;
        if (wire != null) {
            double fromWire = Math.hypot(angleDifference(nextYaw, wire[0]), nextPitch - wire[1]);
            double previousFromWire = Math.hypot(angleDifference(currentRotation[0], wire[0]), currentRotation[1] - wire[1]);
            // Never clamp a handoff by teleporting the visible trajectory. The adapter rebases that handoff.
            if (fromWire > MAX_CONTINUOUS_PACKET_TURN && previousFromWire <= MAX_CONTINUOUS_PACKET_TURN + .001) {
                double low = 0, high = 1;
                for (int i = 0; i < 20; i++) {
                    double part = (low + high) * .5;
                    double length = Math.hypot(angleDifference(currentRotation[0] + (float) (yawStep * part), wire[0]),
                            currentRotation[1] + pitchStep * part - wire[1]);
                    if (length <= MAX_CONTINUOUS_PACKET_TURN) low = part; else high = part;
                }
                nextYaw = currentRotation[0] + (float) (yawStep * low);
                nextPitch = currentRotation[1] + (float) (pitchStep * low);
            }
        }
        currentRotation = new float[]{nextYaw, nextPitch}; reset = false;
        return new Aim(nextYaw, nextPitch);
    }

    private float continuousStep(float proposed, float difference, float speed, float factor, float from) {
        if (difference == 0 || speed <= 0) return 0;
        float remaining = Math.abs(difference);
        if (proposed == 0) {
            float fraction = Math.min(.5f, timeScale * factor * (smoothMode == Smoothing.Relative ? .25f : 1));
            proposed = Math.copySign(Math.min(remaining * fraction, speed * timeScale * factor), difference);
        }
        if (Math.signum(proposed) == Math.signum(difference)) proposed = Math.copySign(Math.min(Math.abs(proposed), remaining * .5f), proposed);
        if (from + proposed == from) proposed = Math.copySign(Math.min(remaining, Math.ulp(from)), difference);
        return proposed;
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
        if (continuous && (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90)) return;
        if (serverRotation != null && shouldRotate()) rotDiffBuildUp += angleDifference(yaw, serverRotation[0]);
        serverRotation = new float[]{yaw, pitch};
        if (continuous) continuousWireBase = serverRotation.clone();
    }

    /** Original PlayerSP aliases currentRotation while active (it does not clone this array). */
    public void observeMotion(float yaw, float pitch) {
        previousRotation = shouldRotate() ? currentRotation : new float[]{yaw, pitch};
    }
    public boolean shouldRotate() { return currentRotation != null && targetRotation != null; }
    public boolean enabled() { return enabled; }
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
        enabled = false; reset = true;
        returning = false;
        continuousWireBase = null;
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
