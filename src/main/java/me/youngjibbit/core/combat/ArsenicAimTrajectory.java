package me.youngjibbit.core.combat;

import me.youngjibbit.core.arsenic.aim.AimCore;
import java.lang.ref.WeakReference;
import java.util.Random;

/** One upstream controller per owner/acquisition, advanced only by distinct motion samples. */
final class ArsenicAimTrajectory {
    private final long seed;
    private long generation, lastSequence=Long.MIN_VALUE, lastNanos;
    private WeakReference<Object> target=new WeakReference<>(null);
    private AimCore core;
    private RotationProfile.ArsenicMode mode;
    private float[] cached;

    ArsenicAimTrajectory(long seed) { this.seed=seed; }

    void reset() {
        core=null;target.clear();cached=null;mode=null;
        lastSequence=Long.MIN_VALUE;
    }

    float[] advance(Object identity,AimCore.Input in,RotationProfile profile,long sequence,long nanos) {
        if(sequence==lastSequence) {
            return identity==target.get() && cached!=null?cached.clone():new float[]{in.curYaw,in.curPitch};
        }
        if(core==null || target.get()!=identity || mode!=profile.arsenic || nanos<lastNanos || nanos-lastNanos>250_000_000L) {
            core=new AimCore(AimCore.Tuning.best(),new Random(seed ^ ++generation*0x54ab183dL));
            target=new WeakReference<>(identity);mode=profile.arsenic;
        }
        lastSequence=sequence;lastNanos=nanos;
        core.tun.predictionTicks=bound(profile.predictionTicks,0,3);
        core.tun.pitchSpeedCap=bound(profile.pitchSpeed,0,8);
        core.tun.maxPitchStep=core.tun.pitchSpeedCap;
        // Discontinuous entity positions must not project the aim point across the world.
        core.tun.maxLead=1.5f;
        core.missChance=bound(profile.missChance,0,.5f);
        in.maxSpeed=bound(profile.yawSpeed,0,180);
        in.budgetTicks=2;
        core.updateDrift(in);core.observe(in);
        float[] next=mode==RotationProfile.ArsenicMode.HEURISTICS?core.heuristicStep(in):core.lazyStep(in,core.aimRotations(in));
        float yaw=Float.isFinite(next[0])?next[0]:in.curYaw;
        float pitch=Float.isFinite(next[1])?next[1]:in.curPitch;
        float yawDelta=net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-in.curYaw);
        float pitchCap=core.tun.pitchSpeedCap;
        cached=new float[]{in.curYaw+bound(yawDelta,-in.maxSpeed,in.maxSpeed),
                bound(in.curPitch+bound(pitch-in.curPitch,-pitchCap,pitchCap),-90,90)};
        return cached.clone();
    }
    private static float bound(float v,float lo,float hi) { return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo; }
}
