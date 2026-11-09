package me.youngjibbit.core.activity;

/** Measured local correction only; neither an anticheat flag nor a confirmed cause. */
public final class LocalCorrectionSnapshot {
    public static final long TTL_NANOS=2_400_000_000L;
    private final long runId,sequence,observedAtNanos;
    private final double distance;
    private final float angle;
    public LocalCorrectionSnapshot(long runId,long sequence,long observedAtNanos,double distance,float angle){
        if(runId<0||sequence<1||!Double.isFinite(distance)||distance<0||!Float.isFinite(angle)||angle<0)
            throw new IllegalArgumentException("invalid observed correction");
        this.runId=runId;this.sequence=sequence;this.observedAtNanos=observedAtNanos;
        this.distance=distance;this.angle=angle;
    }
    public long getRunId(){return runId;}
    public long getSequence(){return sequence;}
    public long getObservedAtNanos(){return observedAtNanos;}
    public double getDistance(){return distance;}
    public float getAngle(){return angle;}
    public boolean isFresh(long now){long age=now-observedAtNanos;return age>=0&&age<=TTL_NANOS;}
}
