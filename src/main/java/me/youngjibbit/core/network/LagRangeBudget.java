package me.youngjibbit.core.network;

/** Credits are earned only by withholding real player steps, never from guessed server time. */
public final class LagRangeBudget {
    private int credit, consecutive;
    private long lastCharge = Long.MIN_VALUE, earnedAt;

    public boolean withhold(long now, int reserve) {
        expire(now);
        if (credit >= 10 || consecutive >= 3 || reserve >= 10
                || lastCharge != Long.MIN_VALUE && now - lastCharge < 50_000_000L) {
            consecutive = 0;
            return false;
        }
        if (credit == 0) earnedAt = now;
        credit++;
        consecutive++;
        lastCharge = now;
        return true;
    }
    public void ordinaryStep() { consecutive = 0; }
    public int available(long now, int reserve) { expire(now); return Math.max(0, credit - Math.max(0, reserve)); }
    public boolean spend(long now, int reserve) {
        if (available(now, reserve) == 0) return false;
        credit--; consecutive = 0; return true;
    }
    private void expire(long now) { if (credit > 0 && (now - earnedAt < 0 || now - earnedAt > 2_000_000_000L)) reset(); }
    public void reset() { credit = consecutive = 0; lastCharge = Long.MIN_VALUE; }
}
