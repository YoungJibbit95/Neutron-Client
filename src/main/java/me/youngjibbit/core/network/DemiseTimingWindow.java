package me.youngjibbit.core.network;

/** Bounded pulse/approach timing adapted from Demise; transport remains owned by PacketPipeline. */
public final class DemiseTimingWindow {
    public enum Decision { IDLE, START, HOLD, RELEASE }
    private boolean holding, cooling;
    private long until, readyAt, lastNow = Long.MIN_VALUE;

    public Decision update(long now, boolean eligible, double distance, double lower, double upper,
                           int holdMs, int gapMs, boolean approach) {
        if (lastNow != Long.MIN_VALUE && now < lastNow) {
            if (holding) {
                // The transport must release its old-clock owner before another
                // window can be armed against the new monotonic time base.
                stop(now, gapMs);
                lastNow = now;
                return Decision.RELEASE;
            }
            reset();
        }
        lastNow = now;
        boolean within = eligible && Double.isFinite(distance) && distance > lower && distance <= upper;
        if (holding && (!within || now - until >= 0)) {
            stop(now, gapMs);
            return Decision.RELEASE;
        }
        if (!within || cooling && now - readyAt < 0) return Decision.IDLE;
        cooling = false;
        if (holding) return Decision.HOLD;
        int duration = Math.max(1, Math.min(approach ? 250 : 120, holdMs));
        holding = true;
        until = now + duration * 1_000_000L;
        return Decision.START;
    }
    public int remainingMillis(long now) {
        return holding ? (int)Math.max(0, Math.min(250, (until - now + 999_999L) / 1_000_000L)) : 0;
    }
    public void stop(long now, int gapMs) {
        holding = false;
        until = 0;
        readyAt = now + Math.max(0, Math.min(1000, gapMs)) * 1_000_000L;
        cooling = gapMs > 0;
    }
    public void reset() { holding = cooling = false; until = readyAt = 0; lastNow = Long.MIN_VALUE; }
}
