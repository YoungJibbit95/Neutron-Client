package me.youngjibbit.core.combat;

import java.util.Random;

/**
 * Pure Java 8 Adaptive-only settings mathematics. These values never own
 * rotation packets, attacks, hit permissions or an entity/scene lifecycle.
 */
public final class AdaptivePresetMotion {
    private AdaptivePresetMotion() { }

    public enum Speed {
        CALM(.68, .72), BALANCED(1, 1), SWIFT(1.24, 1.22);
        final double speed, acceleration;
        Speed(double speed, double acceleration) {
            this.speed = speed; this.acceleration = acceleration;
        }
    }

    public enum Hitpoint {
        STABLE(0, 0), ORGANIC(.09, .33), DYNAMIC(.20, .72);
        final double extent, cyclesPerSecond;
        Hitpoint(double extent, double cyclesPerSecond) {
            this.extent = extent; this.cyclesPerSecond = cyclesPerSecond;
        }
    }

    public enum AimPoint { STRAIGHT, NEAREST, LEGACY, HEAD, TORSO, LEGS, NEUTRON_NOISE }
    public enum Noise {
        OFF(0, 1), SOFT(.55, .65), ORGANIC(1, 1.15);
        final double amount, tempo;
        Noise(double amount, double tempo) { this.amount=amount; this.tempo=tempo; }
    }

    /** Independent Aura controls; null on historical profiles and other consumers. */
    public static final class Tuning {
        public final double curveStrength, pathAmount, pathTempo, initialRadius, hitpointAmount, hitpointTempo;
        public final AdaptiveHitpointMotion.Configuration legacyHitpoint;

        public Tuning(double curveStrength, double pathAmount, double pathTempo, double initialRadius,
                      double hitpointAmount, double hitpointTempo,
                      AdaptiveHitpointMotion.Configuration legacyHitpoint) {
            if (!unit(curveStrength) || !unit(pathAmount) || !positive(pathTempo)
                    || !unit(initialRadius) || !unit(hitpointAmount) || !positive(hitpointTempo)
                    || hitpointTempo > 3 || legacyHitpoint == null)
                throw new IllegalArgumentException("invalid Adaptive independent controls");
            this.curveStrength = curveStrength; this.pathAmount = pathAmount;
            this.pathTempo = pathTempo; this.initialRadius = initialRadius;
            this.hitpointAmount = hitpointAmount; this.hitpointTempo = hitpointTempo;
            this.legacyHitpoint = legacyHitpoint;
        }

        public boolean same(Tuning other) {
            return other != null && Double.compare(curveStrength, other.curveStrength) == 0
                    && Double.compare(pathAmount, other.pathAmount) == 0
                    && Double.compare(pathTempo, other.pathTempo) == 0
                    && Double.compare(initialRadius, other.initialRadius) == 0
                    && Double.compare(hitpointAmount, other.hitpointAmount) == 0
                    && Double.compare(hitpointTempo, other.hitpointTempo) == 0
                    && legacyHitpoint.same(other.legacyHitpoint);
        }
    }

    /**
     * Demise's Straight anchor keeps horizontal box centre and a useful torso-to-eye
     * height. Nearest minimizes the correction distance. Neither expands the hitbox.
     */
    public static double[] aimPoint(AimPoint style,double eyeX,double eyeY,double eyeZ,
            double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        if(style==null || style==AimPoint.LEGACY || style==AimPoint.NEUTRON_NOISE || !Double.isFinite(eyeX) || !Double.isFinite(eyeY)
                || !Double.isFinite(eyeZ) || !finiteBox(minX,maxX) || !finiteBox(minY,maxY)
                || !finiteBox(minZ,maxZ))return null;
        double ix=(maxX-minX)*.001,iy=(maxY-minY)*.001,iz=(maxZ-minZ)*.001;
        if(style==AimPoint.STRAIGHT)return new double[]{minX+(maxX-minX)*.5,
                clamp(eyeY,minY+(maxY-minY)*.45,maxY-iy),minZ+(maxZ-minZ)*.5};
        if(style==AimPoint.HEAD || style==AimPoint.TORSO || style==AimPoint.LEGS) {
            double height=style==AimPoint.HEAD?.90:style==AimPoint.TORSO?.75:.45;
            return new double[]{minX+(maxX-minX)*.5,
                    clamp(minY+(maxY-minY)*height,minY+iy,maxY-iy),minZ+(maxZ-minZ)*.5};
        }
        return new double[]{clamp(eyeX,minX+ix,maxX-ix),clamp(eyeY,minY+iy,maxY-iy),
                clamp(eyeZ,minZ+iz,maxZ-iz)};
    }

    /** A snapshot; changing it requires a fresh rotation acquisition. */
    public static final class Configuration {
        public final Speed speed;
        public final Hitpoint hitpoint;
        public final double variation, yawTicks, pitchTicks;
        public final boolean explicitControls, keepAim;
        public final double minAimSpeed, maxAimSpeed, yawScale, pitchScale, noiseTempo;
        public final AdaptiveMotionController.Curve curve;
        public final Noise noise;
        public final AimPoint aimPoint;
        public final Tuning tuning;

        /** One conversion point for RotationManager; no legacy setting leaks into explicit Aura tuning. */
        public static Configuration fromProfile(RotationProfile profile) {
            if (profile == null) throw new IllegalArgumentException("Adaptive profile required");
            return profile.adaptiveExplicitControls ? new Configuration(profile.adaptiveSpeed,
                    profile.adaptiveHitpoint, profile.adaptiveVariation, profile.adaptiveMinAimSpeed,
                    profile.adaptiveMaxAimSpeed, profile.adaptiveYawScale, profile.adaptivePitchScale,
                    profile.adaptiveCurve, profile.adaptiveNoise, profile.adaptiveNoiseTempo,
                    profile.adaptiveAimPoint, profile.adaptiveKeepAim, profile.adaptiveTuning)
                    : new Configuration(profile.adaptiveSpeed, profile.adaptiveHitpoint,
                    profile.adaptiveVariation, profile.yawSpeed, profile.pitchSpeed);
        }

        public Configuration(Speed speed, Hitpoint hitpoint, double variation,
                             double yawTicks, double pitchTicks) {
            this(speed,hitpoint,variation,yawTicks,pitchTicks,false,0,0,1,1,
                    AdaptiveMotionController.Curve.ADAPTIVE,Noise.SOFT,1,AimPoint.STRAIGHT,true,null);
        }

        public Configuration(Speed speed, Hitpoint hitpoint, double variation,
                             double minAimSpeed, double maxAimSpeed, double pitchScale,
                             AdaptiveMotionController.Curve curve, Noise noise, double noiseTempo,
                             AimPoint aimPoint, boolean keepAim) {
            this(speed,hitpoint,variation,minAimSpeed,maxAimSpeed,1,pitchScale,curve,noise,noiseTempo,aimPoint,keepAim);
        }
        public Configuration(Speed speed, Hitpoint hitpoint, double variation,
                             double minAimSpeed, double maxAimSpeed, double yawScale, double pitchScale,
                             AdaptiveMotionController.Curve curve, Noise noise, double noiseTempo,
                             AimPoint aimPoint, boolean keepAim) {
            this(speed,hitpoint,variation,minAimSpeed,maxAimSpeed,yawScale,pitchScale,curve,noise,
                    noiseTempo,aimPoint,keepAim,null);
        }

        public Configuration(Speed speed, Hitpoint hitpoint, double variation,
                             double minAimSpeed, double maxAimSpeed, double yawScale, double pitchScale,
                             AdaptiveMotionController.Curve curve, Noise noise, double noiseTempo,
                             AimPoint aimPoint, boolean keepAim, Tuning tuning) {
            this(speed,hitpoint,variation,maxAimSpeed*yawScale/20,maxAimSpeed*pitchScale/20,true,
                    minAimSpeed,maxAimSpeed,yawScale,pitchScale,curve,noise,noiseTempo,aimPoint,keepAim,tuning);
        }

        private Configuration(Speed speed, Hitpoint hitpoint, double variation,
                              double yawTicks, double pitchTicks, boolean explicitControls,
                              double minAimSpeed, double maxAimSpeed, double yawScale, double pitchScale,
                              AdaptiveMotionController.Curve curve, Noise noise, double noiseTempo,
                              AimPoint aimPoint, boolean keepAim, Tuning tuning) {
            if (speed == null || hitpoint == null || !Double.isFinite(variation)
                    || variation < 0 || variation > 1 || !positive(yawTicks)
                    || !positive(pitchTicks)) {
                throw new IllegalArgumentException("invalid Adaptive preset");
            }
            if (explicitControls && (!positive(minAimSpeed) || !positive(maxAimSpeed)
                    || minAimSpeed>maxAimSpeed || !positive(yawScale) || yawScale>1 || !positive(pitchScale) || pitchScale>1
                    || !positive(noiseTempo) || curve==null || noise==null || aimPoint==null))
                throw new IllegalArgumentException("invalid Adaptive aim controls");
            this.speed = speed; this.hitpoint = hitpoint;
            this.variation = variation;
            this.yawTicks = yawTicks; this.pitchTicks = pitchTicks;
            this.explicitControls=explicitControls;
            this.minAimSpeed=minAimSpeed;this.maxAimSpeed=maxAimSpeed;this.yawScale=yawScale;this.pitchScale=pitchScale;
            this.curve=curve;this.noise=noise;this.noiseTempo=noiseTempo;
            this.aimPoint=aimPoint;this.keepAim=keepAim;
            this.tuning=tuning;
        }

        public boolean same(Configuration other) {
            return other != null && speed == other.speed && hitpoint == other.hitpoint
                    && Double.compare(variation, other.variation) == 0
                    && Double.compare(yawTicks, other.yawTicks) == 0
                    && Double.compare(pitchTicks, other.pitchTicks) == 0
                    && explicitControls==other.explicitControls && keepAim==other.keepAim && aimPoint==other.aimPoint
                    && curve==other.curve && noise==other.noise
                    && Double.compare(minAimSpeed,other.minAimSpeed)==0
                    && Double.compare(maxAimSpeed,other.maxAimSpeed)==0
                    && Double.compare(yawScale,other.yawScale)==0
                    && Double.compare(pitchScale,other.pitchScale)==0
                    && Double.compare(noiseTempo,other.noiseTempo)==0
                    && (tuning==null?other.tuning==null:tuning.same(other.tuning));
        }

        /** Stable rotation style can still use an independently selected initial or Legacy point. */
        public boolean changesHitpoint() {
            if (tuning == null) return hitpoint != Hitpoint.STABLE;
            return tuning.initialRadius > 0 || (legacyPoint()
                    ? tuning.legacyHitpoint.radius > 0 : hitpoint != Hitpoint.STABLE && tuning.hitpointAmount > 0);
        }

        private AdaptiveHitpointMotion.Configuration pointConfiguration() {
            if (legacyPoint()) return tuning.legacyHitpoint;
            return new AdaptiveHitpointMotion.Configuration(AdaptiveHitpointMotion.Profile.CUSTOM,
                    AdaptiveHitpointMotion.Algorithm.OPEN_SIMPLEX_2S, hitpoint.extent * 2 * tuning.hitpointAmount,
                    hitpoint == Hitpoint.STABLE ? 1 : hitpoint.cyclesPerSecond * tuning.hitpointTempo,
                    hitpoint == Hitpoint.DYNAMIC ? .3 : .15);
        }

        private boolean legacyPoint() { return aimPoint == AimPoint.LEGACY || aimPoint == AimPoint.NEUTRON_NOISE; }

        public AdaptiveMotionController.Limits limits() {
            if (explicitControls) {
                double yaw=maxAimSpeed*yawScale,pitch=maxAimSpeed*pitchScale;
                double accel=clamp(Math.max(yaw,pitch)*6*speed.acceleration,120,18000);
                AdaptiveMotionController.Control control = tuning == null
                        ? new AdaptiveMotionController.Control(minAimSpeed/maxAimSpeed,curve,
                        variation*noise.amount,noiseTempo*noise.tempo,maxAimSpeed)
                        : new AdaptiveMotionController.Control(minAimSpeed/maxAimSpeed,curve,
                        variation*noise.amount,noiseTempo*noise.tempo,maxAimSpeed,
                        tuning.curveStrength,tuning.pathAmount,tuning.pathTempo);
                return new AdaptiveMotionController.Limits(yaw,pitch,accel,
                        clamp(accel*20,2400,360000),0,.20,control);
            }
            // Original sliders express degrees/50ms. Same engine for all presets.
            double yaw = clamp(yawTicks * 20 * speed.speed, 20, 720);
            double pitch = clamp(pitchTicks * 20 * speed.speed, 20, 720);
            double accel = clamp(Math.max(yaw, pitch) * 6 * speed.acceleration, 300, 4200);
            // At Balanced + strength .5, keep Phase 2's exact variation/curvature.
            return new AdaptiveMotionController.Limits(yaw, pitch, accel,
                    clamp(accel * 20, 9000, 84000), .28 * variation, .24 * variation);
        }
    }

    /**
     * Acquisition-local sampling at motion time, never render count. Explicit
     * Aura controls use FastNoiseLite; the old constructor retains its sine phases.
     * New acquisition starts at its unchanged base point (fade-in = 0).
     */
    public static final class HitpointTrajectory {
        private final Hitpoint style;
        private final double[] phases = new double[6];
        private final boolean coherent;
        private final AdaptiveAimNoise noise;
        private final double amount;
        private final AdaptiveHitpointMotion inBox;

        public HitpointTrajectory(long seed, Hitpoint style) {
            this(seed,style,false,1,1,null);
        }

        public HitpointTrajectory(long seed, Configuration settings) {
            this(seed,settings.hitpoint,settings.explicitControls,
                    settings.variation*settings.noise.amount,settings.noiseTempo*settings.noise.tempo,
                    settings.tuning == null ? null : new AdaptiveHitpointMotion(seed,
                            settings.pointConfiguration(),settings.tuning.initialRadius));
        }

        private HitpointTrajectory(long seed, Hitpoint style, boolean coherent, double amount, double tempo,
                                   AdaptiveHitpointMotion inBox) {
            if (style == null) throw new IllegalArgumentException("hitpoint style");
            this.style = style;
            this.coherent=coherent;
            this.amount=amount;
            this.inBox=inBox;
            this.noise=inBox==null && coherent && amount>0 ? new AdaptiveAimNoise(seed,tempo) : null;
            if(inBox==null) {
                Random random = new Random(seed);
                for (int i = 0; i < phases.length; i++)
                    phases[i] = random.nextDouble() * Math.PI * 2;
            }
        }

        /**
         * @return valid in-box point, or null when input bounds/time/point is invalid.
         * Position coordinates may change with a moving box; the noise is continuous
         * in normalized local coordinates and does not draw per-frame randomness.
         */
        public double[] point(double baseX, double baseY, double baseZ,
                              double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ, double seconds) {
            if(inBox!=null)return inBox.point(baseX,baseY,baseZ,minX,minY,minZ,maxX,maxY,maxZ,seconds);
            if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(baseX)
                    || !Double.isFinite(baseY) || !Double.isFinite(baseZ)
                    || !finiteBox(minX, maxX) || !finiteBox(minY, maxY)
                    || !finiteBox(minZ, maxZ)) return null;
            double fade = 1 - Math.exp(-seconds * 4);
            return new double[] {
                axis(baseX, minX, maxX, seconds, fade, 0),
                axis(baseY, minY, maxY, seconds, fade, 1),
                axis(baseZ, minZ, maxZ, seconds, fade, 2)
            };
        }

        private double axis(double base, double min, double max, double time, double fade, int i) {
            double width = max - min;
            double angle = time * style.cyclesPerSecond * 2 * Math.PI;
            double primary = Math.sin(angle + phases[i]) - Math.sin(phases[i]);
            double secondary = Math.sin(angle * 1.71 + phases[i + 3]) - Math.sin(phases[i + 3]);
            double offset = (.6 * primary + .4 * secondary) * .5 * style.extent * fade;
            if(coherent)offset=noise==null?0:
                    (noise.sample(time,i+1)-noise.sample(0,i+1))*.5*style.extent*amount*fade;
            double inset = width * 0.001;
            return clamp(base + width * offset, min + inset, max - inset);
        }
    }

    private static boolean positive(double d) { return Double.isFinite(d) && d > 0; }
    private static boolean unit(double d) { return Double.isFinite(d) && d >= 0 && d <= 1; }
    private static boolean finiteBox(double lo, double hi) {
        return Double.isFinite(lo) && Double.isFinite(hi)
                && hi > lo && Double.isFinite(hi - lo);
    }
    private static double clamp(double d, double lo, double hi) {
        return Math.max(lo, Math.min(hi, d));
    }
}
