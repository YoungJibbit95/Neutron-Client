package me.youngjibbit.core.world;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.youngjibbit.value.impl.DemiseSliderValue;

/** Common Scaffold/BedBreaker settings from DemisePrivate e219a97, RotationManager.java (AGPL-3.0).
 * The requested host default is Silent movement correction; Demise defaults to None.
 * KillAura-only distance controls do not apply to these owners.
 */
public final class DemiseRotationSettings {
    public final CheckBoxValue silent, accel, imperfectCorrelation, shortStop;
    public final ComboValue smoothMode, moveCorrection;
    public final SliderValue extraSmoothFactor, yawAccelFactor, pitchAccelFactor;
    public final SliderValue yawRotationSpeedMin, yawRotationSpeedMax, pitchRotationSpeedMin, pitchRotationSpeedMax;
    public final SliderValue shortStopDuration, rotationDiffBuildUpToStop, maxThresholdAttemptsToStop;

    public DemiseRotationSettings(Module parent) {
        silent = new CheckBoxValue("Silent", parent, true);
        extraSmoothFactor = new DemiseSliderValue("Extra smoothing factor", parent, .15D, .01, 1, .01);
        smoothMode = new ComboValue("Smooth mode", parent, new String[]{"Linear", "Relative"}, 0);
        accel = new CheckBoxValue("Accelerate", parent, false);
        yawAccelFactor = new DemiseSliderValue("Yaw accel factor", parent, .25D, .01, .9, .01);
        pitchAccelFactor = new DemiseSliderValue("Pitch accel factor", parent, .25D, .01, .9, .01);
        yawAccelFactor.setVisibility(m -> accel.getValue());
        pitchAccelFactor.setVisibility(m -> accel.getValue());
        imperfectCorrelation = new CheckBoxValue("Imperfect correlation", parent, false);
        yawRotationSpeedMin = new DemiseSliderValue("Yaw rotation speed (min)", parent, 180D, .01, 180, .01);
        yawRotationSpeedMax = new DemiseSliderValue("Yaw rotation speed (max)", parent, 180D, .01, 180, .01);
        pitchRotationSpeedMin = new DemiseSliderValue("Pitch rotation speed (min)", parent, 180D, .01, 180, .01);
        pitchRotationSpeedMax = new DemiseSliderValue("Pitch rotation speed (max)", parent, 180D, .01, 180, .01);
        moveCorrection = new ComboValue("Movement correction", parent, new String[]{"Silent", "Strict", "None"}, 0);
        shortStop = new CheckBoxValue("Short stop", parent, false);
        shortStopDuration = new DemiseSliderValue("Duration", parent, 50D, 25, 1000, 25);
        rotationDiffBuildUpToStop = new DemiseSliderValue("Rotation diff buildup to stop", parent, 180D, 50, 720, 1);
        maxThresholdAttemptsToStop = new DemiseSliderValue("Max threshold attempts to stop", parent, 1D, 0, 5, 1);
        shortStopDuration.setVisibility(m -> shortStop.getValue());
        rotationDiffBuildUpToStop.setVisibility(m -> shortStop.getValue());
        maxThresholdAttemptsToStop.setVisibility(m -> shortStop.getValue());
    }

    public Snapshot snapshot() {
        return new Snapshot(silent.getValue(), extraSmoothFactor.getValue().floatValue(),
                DemiseRotationEngine.Smoothing.valueOf(smoothMode.getValue()), accel.getValue(),
                yawAccelFactor.getValue().floatValue(), pitchAccelFactor.getValue().floatValue(),
                imperfectCorrelation.getValue(), yawRotationSpeedMin.getValue().floatValue(),
                yawRotationSpeedMax.getValue().floatValue(), pitchRotationSpeedMin.getValue().floatValue(),
                pitchRotationSpeedMax.getValue().floatValue(),
                DemiseRotationEngine.Correction.valueOf(moveCorrection.getValue()), shortStop.getValue(),
                shortStopDuration.getValue().floatValue(), rotationDiffBuildUpToStop.getValue().floatValue(),
                maxThresholdAttemptsToStop.getValue().floatValue());
    }

    /** Float values are retained as in Demise; no reordered ranges or extra angle caps. */
    public static final class Snapshot {
        public final boolean silent, accel, imperfectCorrelation, shortStop;
        public final float extraSmoothFactor, yawAccelFactor, pitchAccelFactor;
        public final float yawRotationSpeedMin, yawRotationSpeedMax, pitchRotationSpeedMin, pitchRotationSpeedMax;
        public final float shortStopDuration, rotationDiffBuildUpToStop, maxThresholdAttemptsToStop;
        public final DemiseRotationEngine.Smoothing smoothMode;
        public final DemiseRotationEngine.Correction moveCorrection;

        public Snapshot(boolean silent, float extraSmoothFactor, DemiseRotationEngine.Smoothing smoothMode,
                        boolean accel, float yawAccelFactor, float pitchAccelFactor, boolean imperfectCorrelation,
                        float yawRotationSpeedMin, float yawRotationSpeedMax, float pitchRotationSpeedMin,
                        float pitchRotationSpeedMax, DemiseRotationEngine.Correction moveCorrection,
                        boolean shortStop, float shortStopDuration, float rotationDiffBuildUpToStop,
                        float maxThresholdAttemptsToStop) {
            this.silent = silent; this.extraSmoothFactor = extraSmoothFactor; this.smoothMode = smoothMode;
            this.accel = accel; this.yawAccelFactor = yawAccelFactor; this.pitchAccelFactor = pitchAccelFactor;
            this.imperfectCorrelation = imperfectCorrelation;
            this.yawRotationSpeedMin = yawRotationSpeedMin; this.yawRotationSpeedMax = yawRotationSpeedMax;
            this.pitchRotationSpeedMin = pitchRotationSpeedMin; this.pitchRotationSpeedMax = pitchRotationSpeedMax;
            this.moveCorrection = moveCorrection; this.shortStop = shortStop;
            this.shortStopDuration = shortStopDuration; this.rotationDiffBuildUpToStop = rotationDiffBuildUpToStop;
            this.maxThresholdAttemptsToStop = maxThresholdAttemptsToStop;
        }

        public static Snapshot defaults(DemiseRotationEngine.Correction correction) {
            return new Snapshot(true, .15f, DemiseRotationEngine.Smoothing.Linear, false, .25f, .25f,
                    false, 180, 180, 180, 180, correction, false, 50, 180, 1);
        }
    }
}
