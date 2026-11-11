package me.youngjibbit.core.world;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Local, visible camera interpolation. A result grants no placement authority.
 * The Linear/Relative and inertia controls adapt the authorized Demise
 * RotationManager/RotationHandler reference at e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb.
 * Unlike that frame-driven handler, this controller consumes one player tick,
 * never writes input/packets, and retains hard per-axis speed limits.
 */
public final class NScaffoldRotationController {
    public enum Smoothing { LINEAR, RELATIVE }

    /** Speeds are degrees per player tick; duration is monotonic elapsed milliseconds. */
    public static final class Settings {
        public final Smoothing smoothing;
        public final double extraSmoothingFactor, yawMinimum, yawMaximum, pitchMinimum, pitchMaximum;
        public final boolean accelerate, imperfectCorrelation, shortStop;
        public final double yawAcceleration, pitchAcceleration, rotationThreshold;
        public final long shortStopDurationMillis;
        public final int thresholdAttempts;
        private final boolean valid;

        public Settings(Smoothing smoothing, double extraSmoothingFactor,
                        double yawMinimum, double yawMaximum, double pitchMinimum, double pitchMaximum,
                        boolean accelerate, double yawAcceleration, double pitchAcceleration,
                        boolean imperfectCorrelation, boolean shortStop, long shortStopDurationMillis,
                        double rotationThreshold, int thresholdAttempts) {
            this.valid=smoothing!=null && finite(extraSmoothingFactor,yawMinimum,yawMaximum,
                    pitchMinimum,pitchMaximum,yawAcceleration,pitchAcceleration,rotationThreshold);
            this.smoothing=smoothing;
            this.extraSmoothingFactor=clamp(extraSmoothingFactor,.01,1);
            this.yawMinimum=clamp(Math.min(yawMinimum,yawMaximum),.01,180);
            this.yawMaximum=clamp(Math.max(yawMinimum,yawMaximum),.01,180);
            this.pitchMinimum=clamp(Math.min(pitchMinimum,pitchMaximum),.01,180);
            this.pitchMaximum=clamp(Math.max(pitchMinimum,pitchMaximum),.01,180);
            this.accelerate=accelerate;
            this.yawAcceleration=clamp(yawAcceleration,.01,.9);
            this.pitchAcceleration=clamp(pitchAcceleration,.01,.9);
            this.imperfectCorrelation=imperfectCorrelation;
            this.shortStop=shortStop;
            this.shortStopDurationMillis=Math.max(25,Math.min(1000,shortStopDurationMillis));
            this.rotationThreshold=clamp(rotationThreshold,50,720);
            this.thresholdAttempts=Math.max(0,Math.min(5,thresholdAttempts));
        }

        private boolean same(Settings other) {
            return other!=null && valid==other.valid && smoothing==other.smoothing
                    && extraSmoothingFactor==other.extraSmoothingFactor
                    && yawMinimum==other.yawMinimum && yawMaximum==other.yawMaximum
                    && pitchMinimum==other.pitchMinimum && pitchMaximum==other.pitchMaximum
                    && accelerate==other.accelerate && yawAcceleration==other.yawAcceleration
                    && pitchAcceleration==other.pitchAcceleration
                    && imperfectCorrelation==other.imperfectCorrelation && shortStop==other.shortStop
                    && shortStopDurationMillis==other.shortStopDurationMillis
                    && rotationThreshold==other.rotationThreshold && thresholdAttempts==other.thresholdAttempts;
        }
    }

    public static final class Step {
        public final float yaw,pitch;
        public final boolean stopped,held;
        private Step(float yaw,float pitch,boolean stopped,boolean held) {
            this.yaw=yaw;this.pitch=pitch;this.stopped=stopped;this.held=held;
        }
    }

    private static final class Request {
        final float yaw,pitch,targetYaw,targetPitch;
        final boolean keepValidRay,urgent;
        final Settings settings;
        Request(float yaw,float pitch,float targetYaw,float targetPitch,boolean keepValidRay,
                boolean urgent,Settings settings) {
            this.yaw=yaw;this.pitch=pitch;this.targetYaw=targetYaw;this.targetPitch=targetPitch;
            this.keepValidRay=keepValidRay;this.urgent=urgent;this.settings=settings;
        }
        boolean same(float yaw,float pitch,float targetYaw,float targetPitch,boolean keepValidRay,
                     boolean urgent,Settings settings) {
            return bits(this.yaw,yaw) && bits(this.pitch,pitch) && bits(this.targetYaw,targetYaw)
                    && bits(this.targetPitch,targetPitch) && this.keepValidRay==keepValidRay
                    && this.urgent==urgent && this.settings.same(settings);
        }
    }

    private final DoubleSupplier random;
    private final LongSupplier clockNanos;
    private long lastTick=Long.MIN_VALUE,revision,pauseStarted,lastClock;
    private boolean computing,paused,clockKnown;
    private double previousYawDelta,previousPitchDelta,travel;
    private int thresholdAttempts;
    private Settings settings;
    private Request request;
    private Step result;

    public NScaffoldRotationController() {
        this(()->ThreadLocalRandom.current().nextDouble(),System::nanoTime);
    }
    public NScaffoldRotationController(DoubleSupplier random,LongSupplier clockNanos) {
        if(random==null || clockNanos==null)throw new IllegalArgumentException("rotation suppliers");
        this.random=random;this.clockNanos=clockNanos;
    }

    /** Clear interpolation/pause state without buying another turn in the same tick. */
    public void reset() { revision++;clearMotion();settings=null; }

    /** Only for an actual scene/runtime identity replacement with a new tick timeline. */
    public void resetForScene() { reset();lastTick=Long.MIN_VALUE; }

    /**
     * Same-tick identical calls reuse the result; changed or older requests fail closed.
     * Callers must pass the real outer player tick, not a render or motion-event counter.
     * Urgent acquisition cancels optional pauses/inertia/easing, but never its hard axis caps.
     * A fresh checked valid ray is preserved bit-for-bit and clears unused turn history.
     */
    public Step update(long tick,float yaw,float pitch,float targetYaw,float targetPitch,
                       boolean keepValidRay,boolean urgent,Settings next) {
        if(computing || tick<0 || next==null || !next.valid || !finite(yaw,pitch,targetYaw,targetPitch)
                || Math.abs(pitch)>90 || Math.abs(targetPitch)>90)return null;
        if(tick<=lastTick)return tick==lastTick && request!=null
                && request.same(yaw,pitch,targetYaw,targetPitch,keepValidRay,urgent,next)?result:null;
        lastTick=tick;
        computing=true;
        final long expected=revision;
        try {
            if(settings==null || !settings.same(next))clearMotion();
            else observePrevious(yaw,pitch);
            settings=next;
            double dy=wrap((double)targetYaw-yaw),dp=(double)targetPitch-pitch;
            if(keepValidRay || dy==0 && dp==0) {
                clearMotion();
                return finish(expected,yaw,pitch,targetYaw,targetPitch,keepValidRay,urgent,next,
                        new Step(yaw,pitch,false,true));
            }
            if(urgent) {
                clearMotion();
            } else if(next.shortStop) {
                long now=clockNanos.getAsLong();
                if(revision!=expected)return null;
                if(clockKnown && now-lastClock<0){paused=false;travel=0;thresholdAttempts=0;}
                clockKnown=true;lastClock=now;
                if(paused && now-pauseStarted>=next.shortStopDurationMillis*1_000_000L)paused=false;
                if(!paused && travel>=next.rotationThreshold) {
                    travel=0;
                    if(thresholdAttempts<next.thresholdAttempts)thresholdAttempts++;
                    else {thresholdAttempts=0;paused=true;pauseStarted=now;}
                }
                if(paused) {
                    previousYawDelta=previousPitchDelta=0;
                    return finish(expected,yaw,pitch,targetYaw,targetPitch,keepValidRay,false,next,
                            new Step(yaw,pitch,true,false));
                }
            } else {
                paused=false;travel=0;thresholdAttempts=0;clockKnown=false;
            }

            double h=urgent?next.yawMaximum:sample(next.yawMinimum,next.yawMaximum);
            if(revision!=expected)return null;
            double v=urgent?next.pitchMaximum:sample(next.pitchMinimum,next.pitchMaximum);
            if(revision!=expected)return null;
            if(!urgent && next.imperfectCorrelation) {
                h=Math.min(next.yawMaximum,h*(.9+.2*unit()));
                if(revision!=expected)return null;
                v=Math.min(next.pitchMaximum,v*(.9+.2*unit()));
                if(revision!=expected)return null;
            }
            double y,p;
            if(!urgent && next.smoothing==Smoothing.RELATIVE) {
                y=signedLimit(dy*.25,dy,h);p=signedLimit(dp*.25,dp,v);
            } else {
                double length=Math.hypot(dy,dp);
                y=signedLimit(dy,dy,Math.abs(dy)/length*h);
                p=signedLimit(dp,dp,Math.abs(dp)/length*v);
            }
            if(!urgent && next.accelerate) {
                y=y*(1-next.yawAcceleration)+previousYawDelta*next.yawAcceleration;
                p=p*(1-next.pitchAcceleration)+previousPitchDelta*next.pitchAcceleration;
            }
            y=signedLimit(y,dy,h);p=signedLimit(p,dp,v);
            previousYawDelta=urgent?0:y;previousPitchDelta=urgent?0:p;
            double factor=urgent?1:next.extraSmoothingFactor;
            float nextYaw=advance(yaw,y*factor,dy,next.yawMaximum);
            float nextPitch=advance(pitch,p*factor,dp,next.pitchMaximum);
            return finish(expected,yaw,pitch,targetYaw,targetPitch,keepValidRay,urgent,next,
                    new Step(nextYaw,nextPitch,false,false));
        } finally { computing=false; }
    }

    /** Only a proposal observed on the actual camera contributes to the pause threshold. */
    private void observePrevious(float yaw,float pitch) {
        if(result==null || request==null)return;
        if(Math.abs(wrap((double)yaw-result.yaw))>1e-4 || Math.abs((double)pitch-result.pitch)>1e-4) {
            clearMotion();return;
        }
        if(!result.held && !result.stopped)travel=Math.min(1440,
                travel+Math.hypot(wrap((double)yaw-request.yaw),(double)pitch-request.pitch));
    }

    private Step finish(long expected,float yaw,float pitch,float targetYaw,float targetPitch,
                        boolean keepValidRay,boolean urgent,Settings next,Step step) {
        if(revision!=expected)return null;
        request=new Request(yaw,pitch,targetYaw,targetPitch,keepValidRay,urgent,next);
        result=step;return step;
    }
    private void clearMotion() {
        previousYawDelta=previousPitchDelta=travel=0;thresholdAttempts=0;
        paused=false;clockKnown=false;request=null;result=null;
    }
    private double sample(double minimum,double maximum) {
        return minimum==maximum?minimum:minimum+(maximum-minimum)*unit();
    }
    private double unit() {
        double n=random.getAsDouble();
        return Double.isFinite(n)?clamp(n,0,1):.5;
    }
    private static double signedLimit(double amount,double error,double maximum) {
        if(error==0 || amount==0 || Math.signum(amount)!=Math.signum(error))return 0;
        return Math.copySign(Math.min(Math.abs(amount),Math.min(Math.abs(error),maximum)),error);
    }

    /** Round toward the target while keeping the hard cap, including float-sized final steps. */
    private static float advance(float start,double amount,double error,double maximum) {
        if(amount==0)return start;
        double allowed=Math.min(Math.abs(error),maximum);
        float value=(float)(start+amount);
        if(Math.abs((double)value-start)>allowed)value=Math.nextAfter(value,start);
        if(value==start)value=Math.nextAfter(start,amount>0?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY);
        double moved=(double)value-start;
        return Float.isFinite(value) && Math.signum(moved)==Math.signum(error)
                && Math.abs(moved)<=allowed?value:start;
    }
    private static double wrap(double angle) {
        angle%=360;
        if(angle>=180)angle-=360;
        if(angle< -180)angle+=360;
        return angle;
    }
    private static boolean bits(float a,float b){return Float.floatToIntBits(a)==Float.floatToIntBits(b);}
    private static boolean finite(double... values) {
        for(double value:values)if(!Double.isFinite(value))return false;
        return true;
    }
    private static double clamp(double value,double low,double high) {
        return Math.max(low,Math.min(high,value));
    }
}
