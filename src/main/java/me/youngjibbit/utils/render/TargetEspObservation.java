package me.youngjibbit.utils.render;

/** Cosmetic freshness gate. It never acquires rotation authority or changes a producer. */
public final class TargetEspObservation {
    private Object world, player, pipeline;
    private long epoch = Long.MIN_VALUE, sequence = Long.MIN_VALUE, changedAt;
    private boolean primed;

    /** A newly enabled view waits for one new motion sequence instead of reviving old state. */
    public boolean current(Object world, Object player, Object pipeline, long epoch, long sequence,
                           long now, boolean accepted, boolean collecting, boolean effective,
                           String owner, boolean auraEnabled, boolean scaffoldEnabled) {
        return current(world,player,pipeline,epoch,sequence,now,accepted,collecting,effective,
                owner,auraEnabled,scaffoldEnabled,false);
    }

    public boolean current(Object world, Object player, Object pipeline, long epoch, long sequence,
                           long now, boolean accepted, boolean collecting, boolean effective,
                           String owner, boolean auraEnabled, boolean scaffoldEnabled, boolean nscaffoldEnabled) {
        if (world == null || player == null || pipeline == null || epoch < 0 || sequence < 0) {
            reset(); return false;
        }
        if (this.world != world || this.player != player || this.pipeline != pipeline || this.epoch != epoch
                || now < changedAt || sequence < this.sequence) {
            this.world = world; this.player = player; this.pipeline = pipeline;
            this.epoch = epoch; this.sequence = sequence; changedAt = now; primed = false;
            return false;
        }
        if (sequence != this.sequence) { this.sequence = sequence; changedAt = now; primed = true; }
        return primed && now - changedAt <= 250_000_000L && accepted && !collecting && effective
                && ("aura".equals(owner) && auraEnabled || "scaffold".equals(owner) && scaffoldEnabled
                    || "nscaffold".equals(owner) && nscaffoldEnabled);
    }

    public void reset() {
        world = player = pipeline = null; epoch = sequence = Long.MIN_VALUE; changedAt = 0; primed = false;
    }
}
