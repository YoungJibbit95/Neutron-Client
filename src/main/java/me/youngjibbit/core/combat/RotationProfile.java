package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;

/** Per-call settings snapshot; speeds are degrees per 50 ms, offsets degrees, radius blocks, frequency Hz. */
public final class RotationProfile {
    public enum Mode { BASIC, SMOOTH, ADVANCED }
    public enum ArsenicMode { OFF, LAZY, HEURISTICS }
    public ArsenicMode arsenic=ArsenicMode.OFF;
    /** Aura-only optional approach; false keeps every historical profile and default unchanged. */
    public boolean adaptive;
    /** Optional Aura-only neural candidate; no implicit model or old-mode fallback. */
    public boolean aiLearned;
    /** Used only when adaptive=true; all old modes ignore these three values. */
    public AdaptivePresetMotion.Speed adaptiveSpeed = AdaptivePresetMotion.Speed.BALANCED;
    public AdaptivePresetMotion.Hitpoint adaptiveHitpoint = AdaptivePresetMotion.Hitpoint.STABLE;
    public float adaptiveVariation = .5f;
    public AdaptivePresetMotion.AimPoint adaptiveAimPoint = AdaptivePresetMotion.AimPoint.STRAIGHT;
    public boolean adaptiveKeepAim = true;
    public boolean softArsenicMotion;
    public enum HitpointNoise {
        CALM(.45, .25), BALANCED(.85, .48), DYNAMIC(1.4, .8);
        final double rate, extent;
        HitpointNoise(double rate, double extent) { this.rate = rate; this.extent = extent; }
    }
    public HitpointNoise hitpointNoise = HitpointNoise.CALM;
    public float hitpointSpeed = 1, hitpointStrength;

    public float predictionTicks=1, missChance=.05f;
    public enum SpeedCurve { OFF, BEZIER }
    /** Independent opt-in; legacy randomization presets never enable or override it. */
    public SpeedCurve speedCurve=SpeedCurve.OFF;
    public float bezierStrength=.5f, bezierDurationMs=500;
    /** Angular perturbation only; hit-point and speed variation keep their own controls. */
    public enum Pattern { NOISE, BASIC, ONLY_ROTATION, DOUBLED }
    public Pattern pattern=Pattern.NOISE;
    public Mode mode=Mode.BASIC;
    public float yawSpeed=28, pitchSpeed=28, minSpeed=30, maxSpeed=40, interpolation=5;
    public boolean randomization=true, noiseEnabled=true, randomHitVec;
    public boolean smartAim,stopOnTarget,throughWalls;
    public boolean heuristics,interpolateRotation,advancedPitch;
    public float reach=6;
    public float speedVariation;
    public float strength=1, jitter=.35f, yawOffset, pitchOffset, radius=.5f, frequency=1;
    public FastNoiseLite.NoiseType noise=FastNoiseLite.NoiseType.OpenSimplex2;
    public FastNoiseLite.FractalType fractal=FastNoiseLite.FractalType.None;
    public FastNoiseLite.CellularDistanceFunction cellularDistance=FastNoiseLite.CellularDistanceFunction.EuclideanSq;
    public FastNoiseLite.CellularReturnType cellularReturn=FastNoiseLite.CellularReturnType.Distance;
    public float cellularJitter=1, gain=.5f, lacunarity=2, pingPong=2, weighted=0, warpAmplitude=1;
    public int octaves=3;
    public FastNoiseLite.DomainWarpType warp=FastNoiseLite.DomainWarpType.OpenSimplex2;
    public void preset(String name) {
        if("Benutzerdefiniert".equals(name)) return;
        if("Aus".equals(name)) { randomization=false;return; }
        randomization=true;noiseEnabled=true;minSpeed=30;maxSpeed=40;interpolation=5;gain=.5f;lacunarity=2;octaves=3;weighted=0;pingPong=2;
        yawOffset=pitchOffset=0;cellularJitter=1;
        if("Sanft".equals(name)) {
            noise=FastNoiseLite.NoiseType.OpenSimplex2;fractal=FastNoiseLite.FractalType.None;
            frequency=.6f;jitter=.15f;randomHitVec=false;radius=.08f;speedVariation=.12f;
        } else if("Organisch".equals(name)) {
            noise=FastNoiseLite.NoiseType.OpenSimplex2S;fractal=FastNoiseLite.FractalType.FBm;
            frequency=.9f;jitter=.3f;randomHitVec=true;radius=.12f;speedVariation=.3f;
        } else if("Dynamisch".equals(name)) {
            noise=FastNoiseLite.NoiseType.Perlin;fractal=FastNoiseLite.FractalType.DomainWarpProgressive;
            warp=FastNoiseLite.DomainWarpType.OpenSimplex2;warpAmplitude=.8f;
            frequency=1.4f;jitter=.6f;randomHitVec=true;radius=.22f;speedVariation=.55f;
        } else throw new IllegalArgumentException("Unknown rotation randomization profile: "+name);
    }
    public boolean randomized() { return randomization && noiseEnabled && strength>0; }
    public boolean angularRandomized() { return randomization && strength>0 && (noiseEnabled || pattern!=Pattern.NOISE); }
}
