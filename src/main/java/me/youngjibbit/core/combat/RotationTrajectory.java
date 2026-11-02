package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;

/** One owner's target-scoped trajectory. Samples elapsed time, never render-call count. */
public final class RotationTrajectory {
    private final LongSupplier clock;
    private final int seed;
    private final FastNoiseLite noise=new FastNoiseLite(), warp=new FastNoiseLite();
    private WeakReference<Object> target=new WeakReference<>(null);
    private long epoch, lastStep=Long.MIN_VALUE;
    private int generation;
    private int boundSeed;
    private BezierSpeedEnvelope speedEnvelope;
    private double curveSeconds,curveStrength,curveDuration;
    private long curveAcquisition,curveMotion=Long.MIN_VALUE;
    private WeakReference<Object> curveMotionTarget=new WeakReference<>(null);
    private float curveYaw,curvePitch;
    private boolean curvePaused;
    public RotationTrajectory(LongSupplier clock,int seed) { this.clock=clock;this.seed=seed; }
    public void pause(Object current) {
        if(speedEnvelope!=null)resetSpeedCurve();
        bind(current);lastStep=clock.getAsLong();
    }

    private void bind(Object current) {
        if (target.get()!=current) {
            if(speedEnvelope!=null)resetSpeedCurve();
            target=new WeakReference<>(current);epoch=clock.getAsLong();lastStep=Long.MIN_VALUE;
            int targetSeed=seed+ ++generation*0x9e3779b9;
            boundSeed=targetSeed;
            noise.SetSeed(targetSeed);warp.SetSeed(targetSeed^0x54ab183d);
        }
    }
    private double seconds() { return Math.max(0,(clock.getAsLong()-epoch)/1_000_000_000.0); }
    private void configure(RotationProfile p) {
        noise.SetFrequency(1);warp.SetFrequency(1);
        noise.SetNoiseType(p.noise);
        noise.SetCellularDistanceFunction(p.cellularDistance);noise.SetCellularReturnType(p.cellularReturn);
        noise.SetCellularJitter(p.cellularJitter);
        boolean domain=p.fractal==FastNoiseLite.FractalType.DomainWarpProgressive || p.fractal==FastNoiseLite.FractalType.DomainWarpIndependent;
        noise.SetFractalType(domain?FastNoiseLite.FractalType.None:p.fractal);
        warp.SetFractalType(domain?p.fractal:FastNoiseLite.FractalType.None);
        noise.SetFractalOctaves(p.octaves);noise.SetFractalGain(p.gain);noise.SetFractalLacunarity(p.lacunarity);
        warp.SetFractalOctaves(p.octaves);warp.SetFractalGain(p.gain);warp.SetFractalLacunarity(p.lacunarity);
        noise.SetFractalPingPongStrength(p.pingPong);noise.SetFractalWeightedStrength(p.weighted);
        warp.SetDomainWarpType(p.warp);warp.SetDomainWarpAmp(p.warpAmplitude);
    }
    public float sample(Object current,RotationProfile p,int axis) {
        bind(current);
        if(!p.randomized()) return 0;
        configure(p);
        // The second coordinate decorrelates axes; frequency scales time, not render/tick count.
        FastNoiseLite.Vector2 point=new FastNoiseLite.Vector2((float)(seconds()*p.frequency),17.371f+axis*31.719f);
        if(p.fractal==FastNoiseLite.FractalType.DomainWarpProgressive || p.fractal==FastNoiseLite.FractalType.DomainWarpIndependent)
            warp.DomainWarp(point);
        return clamp(noise.GetNoise(point.x,point.y),-1,1);
    }
    public Vec3 point(Object current,AxisAlignedBB box,Vec3 base,RotationProfile p) {
        bind(current);
        if(p.heuristics && p.randomization && p.strength>0) {
            double radius=.2*p.strength,t=seconds()*p.frequency*Math.PI*2;
            base=new Vec3(inside(base.xCoord+Math.sin(t+uniform(0,2,0)*6)*radius,box.minX,box.maxX),
                    inside(base.yCoord+Math.sin(t+uniform(0,3,0)*6)*radius,box.minY,box.maxY),
                    inside(base.zCoord+Math.sin(t+uniform(0,4,0)*6)*radius,box.minZ,box.maxZ));
        }
        if(!p.randomized() || !p.randomHitVec || p.radius<=0) return base;
        double radius=p.radius*p.strength;
        return new Vec3(inside(base.xCoord+sample(current,p,2)*radius,box.minX,box.maxX),
                inside(base.yCoord+sample(current,p,3)*radius,box.minY,box.maxY),
                inside(base.zCoord+sample(current,p,4)*radius,box.minZ,box.maxZ));
    }
    private double inside(double value,double min,double max) {
        double inset=Math.min(.001,(max-min)/4);
        return Math.max(min+inset,Math.min(max-inset,value));
    }
    public float[] advance(Object current,float yaw,float pitch,float targetYaw,float targetPitch,double distance,RotationProfile p) {
        // Unscoped callers retain time-based sampling; real consumers supply the manager's motion ID.
        if(p.speedCurve==RotationProfile.SpeedCurve.BEZIER) {
            long now=clock.getAsLong();
            return advanceMotion(current,yaw,pitch,targetYaw,targetPitch,distance,p,now,now);
        }
        if(speedEnvelope!=null)resetSpeedCurve();
        bind(current);long now=clock.getAsLong();
        double elapsed=lastStep==Long.MIN_VALUE ? .05 : Math.max(0,Math.min(.1,(now-lastStep)/1_000_000_000.0));
        lastStep=now;
        if(elapsed==0) return new float[]{yaw,pitch};
        boolean near=Math.abs(MoveFixController.wrap(targetYaw-yaw))<=p.yawSpeed*elapsed/.05 && Math.abs(targetPitch-pitch)<=p.pitchSpeed*elapsed/.05;
        float desiredYaw=targetYaw+angularSample(current,p,0,near)*(p.jitter+p.yawOffset)*p.strength;
        float coupling=p.advancedPitch?(float)(Math.sin(Math.toRadians(MoveFixController.wrap(targetYaw-yaw))*4)*8):0;
        float desiredPitch=clamp(targetPitch+coupling+angularSample(current,p,1,near)*(p.jitter+p.pitchOffset)*p.strength,-90,90);
        float dy=MoveFixController.wrap(desiredYaw-yaw),dp=desiredPitch-pitch;
        return new float[]{yaw+axisStep(dy,p.yawSpeed,distance,elapsed,p,speedFactor(current,p,5)),
                clamp(pitch+axisStep(dp,p.pitchSpeed,distance,elapsed,p,speedFactor(current,p,6)),-90,90)};
    }

    /** One calculation per motion. Rejected candidates consume time, never accepted-angle authority. */
    public float[] advanceMotion(Object current,float yaw,float pitch,float targetYaw,float targetPitch,
                                 double distance,RotationProfile p,long motionSequence,long motionNanos) {
        if(p.speedCurve!=RotationProfile.SpeedCurve.BEZIER)
            return advance(current,yaw,pitch,targetYaw,targetPitch,distance,p);
        if(curveMotion==motionSequence) {
            if(curveMotionTarget.get()==current)
                return curvePaused?new float[]{yaw,pitch}:new float[]{curveYaw,curvePitch};
            // A target change within the same scope cannot obtain a second first-step allowance.
            bind(current);resetSpeedCurve();lastStep=motionNanos;
            cacheMotion(motionSequence,yaw,pitch,true);
            return new float[]{yaw,pitch};
        }
        bind(current);
        double elapsed=lastStep==Long.MIN_VALUE?.05:Math.max(0,Math.min(.1,(motionNanos-lastStep)/1_000_000_000.0));
        lastStep=motionNanos;
        if(elapsed==0) {cacheMotion(motionSequence,yaw,pitch,false);return new float[]{yaw,pitch};}
        double strength=Float.isFinite(p.bezierStrength)?Math.max(0,Math.min(.9,p.bezierStrength)):.5;
        double duration=(Float.isFinite(p.bezierDurationMs)?Math.max(100,Math.min(2000,p.bezierDurationMs)):500)/1000.0;
        if(speedEnvelope==null || strength!=curveStrength || duration!=curveDuration) {
            curveStrength=strength;curveDuration=duration;curveSeconds=0;
            long acquisition=++curveAcquisition;
            speedEnvelope=new BezierSpeedEnvelope(1-strength*uniform(acquisition,9,1),
                    1-strength*uniform(acquisition,9,2),1-strength*uniform(acquisition,9,3),1);
        }
        double multiplier=speedEnvelope.average(curveSeconds/duration,(curveSeconds+elapsed)/duration);
        curveSeconds=Math.min(duration,curveSeconds+elapsed);
        boolean near=Math.abs(MoveFixController.wrap(targetYaw-yaw))<=p.yawSpeed*elapsed/.05 && Math.abs(targetPitch-pitch)<=p.pitchSpeed*elapsed/.05;
        float desiredYaw=targetYaw+angularSample(current,p,0,near)*(p.jitter+p.yawOffset)*p.strength;
        float coupling=p.advancedPitch?(float)(Math.sin(Math.toRadians(MoveFixController.wrap(targetYaw-yaw))*4)*8):0;
        float desiredPitch=clamp(targetPitch+coupling+angularSample(current,p,1,near)*(p.jitter+p.pitchOffset)*p.strength,-90,90);
        float dy=MoveFixController.wrap(desiredYaw-yaw),dp=desiredPitch-pitch;
        float yawStep=axisStep(dy,p.yawSpeed,distance,elapsed,p,speedFactor(current,p,5)*multiplier);
        float pitchStep=axisStep(dp,p.pitchSpeed,distance,elapsed,p,speedFactor(current,p,6)*multiplier);
        // Large elapsed intervals in Advanced mode still must not pass the desired endpoint.
        float nextYaw=yaw+clamp(yawStep,-Math.abs(dy),Math.abs(dy));
        float nextPitch=clamp(pitch+clamp(pitchStep,-Math.abs(dp),Math.abs(dp)),-90,90);
        cacheMotion(motionSequence,nextYaw,nextPitch,false);
        return new float[]{nextYaw,nextPitch};
    }
    private void cacheMotion(long sequence,float yaw,float pitch,boolean paused) {
        curveMotion=sequence;curveMotionTarget=target;curveYaw=yaw;curvePitch=pitch;curvePaused=paused;
    }
    /** Stop-on-target consumes the scope but accumulates no curve progress or catch-up allowance. */
    public void pauseMotion(Object current,long motionSequence,long motionNanos) {
        if(curveMotion==motionSequence)return;
        bind(current);lastStep=motionNanos;cacheMotion(motionSequence,0,0,true);
    }
    /** Idempotent invalidation: the next actual step chooses controls; no time or angles are restored. */
    public void resetSpeedCurve() {speedEnvelope=null;curveSeconds=0;}
    boolean hasSpeedCurve() {return speedEnvelope!=null;}
    private float angularSample(Object current,RotationProfile p,int axis,boolean near) {
        if(!p.angularRandomized() || (p.pattern==RotationProfile.Pattern.ONLY_ROTATION && near))return 0;
        if(p.pattern==RotationProfile.Pattern.NOISE)return sample(current,p,axis);
        double time=seconds()*p.frequency*20;long knot=(long)Math.floor(time);double mix=time-knot;mix=mix*mix*(3-2*mix);
        return (float)(legacyRandom(knot,axis,p.pattern)*(1-mix)+legacyRandom(knot+1,axis,p.pattern)*mix);
    }
    private double legacyRandom(long knot,int axis,RotationProfile.Pattern pattern) {
        double a=uniform(knot,axis,1),b=uniform(knot,axis,2);
        if(pattern==RotationProfile.Pattern.DOUBLED)return (Math.min(a,b)+(Math.max(a,b)-Math.min(a,b))*uniform(knot,axis,3))*2-1;
        return Math.max(-3,Math.min(3,Math.sqrt(-2*Math.log(Math.max(1e-12,a)))*Math.cos(2*Math.PI*b)));
    }
    private double uniform(long knot,int axis,int salt) {
        long value=((long)boundSeed<<32)^knot*0x9e3779b97f4a7c15L^axis*0x54ab183dL^salt*0x632be59bd9b4e019L;
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;
        return ((value^(value>>>31))>>>11)*0x1.0p-53;
    }
    private double speedFactor(Object current,RotationProfile p,int axis) {
        if(!p.randomized() || p.speedVariation<=0) return 1;
        double amount=p.speedVariation*p.strength;
        return 1-amount*.5+sample(current,p,axis)*amount*.5;
    }
    private float axisStep(float delta,float axisSpeed,double distance,double seconds,RotationProfile p,double speedFactor) {
        double ticks=seconds/.05,cap=axisSpeed*ticks,step=delta;
        if(p.mode==RotationProfile.Mode.SMOOTH) step=delta*(1-Math.exp(-ticks/Math.max(1,p.interpolation)));
        if(p.mode==RotationProfile.Mode.ADVANCED) {
            double low=Math.min(p.minSpeed,p.maxSpeed),high=Math.max(p.minSpeed,p.maxSpeed);
            double dynamic=low+(high-low)*Math.min(1,Math.abs(delta)/180.0);
            double acquisition=.25+.75*Math.min(1,seconds()/.25);
            cap=Math.min(axisSpeed,dynamic)*ticks*acquisition/Math.max(1,distance/2);
            step=delta*ticks/Math.max(1,p.interpolation);
        }
        if(p.heuristics && Math.abs(delta)<10)step*=1-Math.exp(-ticks*.7);
        if(p.interpolateRotation)step*=1-Math.exp(-ticks*axisSpeed/(35*Math.max(1,p.interpolation)));
        cap*=speedFactor;step*=speedFactor;
        return (float)Math.max(-cap,Math.min(cap,step));
    }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
}
