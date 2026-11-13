package me.youngjibbit.core.combat;

import me.youngjibbit.core.world.DemiseRotationEngine;
import me.youngjibbit.core.world.DemiseRotationSettings;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.DemiseSliderValue;
import me.youngjibbit.value.impl.SliderValue;

import java.util.function.BooleanSupplier;

/**
 * Aura smoothing controls from DemisePrivate e219a97 RotationManager (AGPL-3.0).
 * Camera/MoveFix ownership stays with Aura and Neutron's existing rotation manager.
 */
public final class AdaptiveDemiseSettings {
    public final ComboValue smoothMode;
    public final CheckBoxValue accelerate, imperfectCorrelation, distanceSpeed, shortStop;
    public final SliderValue smoothing, yawAcceleration, pitchAcceleration;
    public final SliderValue minYaw, maxYaw, minPitch, maxPitch;
    public final SliderValue minRange, maxRange, decrement;
    public final SliderValue stopDuration, stopDifference, stopAttempts;

    public AdaptiveDemiseSettings(Module owner, BooleanSupplier visible) {
        smoothMode = new ComboValue("Demise Smooth Mode", owner, new String[]{"Linear", "Relative"}, 0);
        smoothing = slider("Demise Extra Smoothing", owner, .15, .01, 1, .01);
        accelerate = new CheckBoxValue("Demise Accelerate", owner, false);
        yawAcceleration = slider("Demise Yaw Acceleration", owner, .25, .01, .9, .01);
        pitchAcceleration = slider("Demise Pitch Acceleration", owner, .25, .01, .9, .01);
        imperfectCorrelation = new CheckBoxValue("Demise Imperfect Correlation", owner, false);
        minYaw = slider("Demise Min Yaw Speed", owner, 180, .01, 180, .01);
        maxYaw = slider("Demise Max Yaw Speed", owner, 180, .01, 180, .01);
        minPitch = slider("Demise Min Pitch Speed", owner, 180, .01, 180, .01);
        maxPitch = slider("Demise Max Pitch Speed", owner, 180, .01, 180, .01);
        distanceSpeed = new CheckBoxValue("Demise Distance Speed", owner, false);
        minRange = slider("Demise Min Range", owner, 0, 0, 8, .1);
        maxRange = slider("Demise Max Range", owner, 8, 0, 8, .1);
        decrement = slider("Demise Speed Decrement", owner, .5, .1, 2, .1);
        shortStop = new CheckBoxValue("Demise Short Stop", owner, false);
        stopDuration = slider("Demise Stop Duration", owner, 50, 25, 1000, 25);
        stopDifference = slider("Demise Stop Difference", owner, 180, 50, 720, 1);
        stopAttempts = slider("Demise Stop Attempts", owner, 1, 0, 5, 1);
        for (Value<?> value : values()) value.setVisibility(m -> visible.getAsBoolean());
        yawAcceleration.setVisibility(m -> visible.getAsBoolean() && accelerate.getValue());
        pitchAcceleration.setVisibility(m -> visible.getAsBoolean() && accelerate.getValue());
        for (Value<?> value : new Value<?>[]{minRange, maxRange, decrement})
            value.setVisibility(m -> visible.getAsBoolean() && distanceSpeed.getValue());
        for (Value<?> value : new Value<?>[]{stopDuration, stopDifference, stopAttempts})
            value.setVisibility(m -> visible.getAsBoolean() && shortStop.getValue());
    }

    private static SliderValue slider(String name, Module owner, double initial, double min, double max, double step) {
        return new DemiseSliderValue(name, owner, initial, min, max, step);
    }

    public void reset() { for (Value<?> value : values()) value.reset(); }

    private Value<?>[] values() {
        return new Value<?>[]{smoothMode, smoothing, accelerate, yawAcceleration, pitchAcceleration,
                imperfectCorrelation, minYaw, maxYaw, minPitch, maxPitch, distanceSpeed,
                minRange, maxRange, decrement, shortStop, stopDuration, stopDifference, stopAttempts};
    }

    public Configuration snapshot() {
        float yawLow = finite(minYaw, .01f, 180, 180), yawHigh = finite(maxYaw, .01f, 180, 180);
        float pitchLow = finite(minPitch, .01f, 180, 180), pitchHigh = finite(maxPitch, .01f, 180, 180);
        float near = finite(minRange, 0, 8, 0), far = finite(maxRange, 0, 8, 8);
        return new Configuration(new DemiseRotationSettings.Snapshot(true,
                finite(smoothing, .01f, 1, .15f), DemiseRotationEngine.Smoothing.valueOf(smoothMode.getValue()),
                accelerate.getValue(), finite(yawAcceleration, .01f, .9f, .25f),
                finite(pitchAcceleration, .01f, .9f, .25f), imperfectCorrelation.getValue(),
                Math.min(yawLow, yawHigh), Math.max(yawLow, yawHigh),
                Math.min(pitchLow, pitchHigh), Math.max(pitchLow, pitchHigh),
                DemiseRotationEngine.Correction.None, shortStop.getValue(),
                finite(stopDuration, 25, 1000, 50), finite(stopDifference, 50, 720, 180),
                finite(stopAttempts, 0, 5, 1)), distanceSpeed.getValue(), Math.min(near, far),
                Math.max(near, far), finite(decrement, .1f, 2, .5f));
    }

    private static float finite(SliderValue value, float min, float max, float fallback) {
        float number = value.getValue().floatValue();
        return Float.isFinite(number) ? Math.max(min, Math.min(max, number)) : fallback;
    }

    public static final class Configuration {
        public final DemiseRotationSettings.Snapshot rotation;
        public final boolean distanceSpeed;
        public final float minRange, maxRange, decrement;

        public Configuration(DemiseRotationSettings.Snapshot rotation, boolean distanceSpeed,
                             float minRange, float maxRange, float decrement) {
            if (rotation == null || !Float.isFinite(minRange) || !Float.isFinite(maxRange)
                    || !Float.isFinite(decrement) || minRange < 0 || minRange > maxRange || maxRange > 8
                    || decrement < .1f || decrement > 2) throw new IllegalArgumentException("Demise distance controls");
            this.rotation = rotation; this.distanceSpeed = distanceSpeed;
            this.minRange = minRange; this.maxRange = maxRange; this.decrement = decrement;
        }

        /** Source formula: decrement once per .01 block, inside the configured interval. */
        public float reduction(double distance) {
            float d = (float) distance;
            return distanceSpeed && Float.isFinite(d) && d >= minRange && d <= maxRange
                    ? ((d - minRange) / .01f) * decrement : 0;
        }

        public boolean same(Configuration other) {
            if (other == null) return false;
            DemiseRotationSettings.Snapshot a = rotation, b = other.rotation;
            return distanceSpeed == other.distanceSpeed && minRange == other.minRange && maxRange == other.maxRange
                    && decrement == other.decrement && a.smoothMode == b.smoothMode && a.accel == b.accel
                    && a.extraSmoothFactor == b.extraSmoothFactor && a.yawAccelFactor == b.yawAccelFactor
                    && a.pitchAccelFactor == b.pitchAccelFactor && a.imperfectCorrelation == b.imperfectCorrelation
                    && a.yawRotationSpeedMin == b.yawRotationSpeedMin && a.yawRotationSpeedMax == b.yawRotationSpeedMax
                    && a.pitchRotationSpeedMin == b.pitchRotationSpeedMin && a.pitchRotationSpeedMax == b.pitchRotationSpeedMax
                    && a.shortStop == b.shortStop && a.shortStopDuration == b.shortStopDuration
                    && a.rotationDiffBuildUpToStop == b.rotationDiffBuildUpToStop
                    && a.maxThresholdAttemptsToStop == b.maxThresholdAttemptsToStop;
        }
    }
}
