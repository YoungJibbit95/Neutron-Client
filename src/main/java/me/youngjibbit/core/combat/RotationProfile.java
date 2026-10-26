package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;

/** Per-call settings snapshot; speeds are degrees per 50 ms, offsets degrees, radius blocks, frequency Hz. */
public final class RotationProfile {
    public enum Mode { BASIC, SMOOTH, ADVANCED }
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
