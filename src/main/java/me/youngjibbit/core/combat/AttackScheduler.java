package me.youngjibbit.core.combat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongSupplier;

/** One client-tick budget shared by controller attacks and Aura click attempts. */
public final class AttackScheduler {
    private final LongSupplier clock;
    private final DoubleBinaryOperator cps;
    private long tick, lastAttackTick = Long.MIN_VALUE, lastAutoTick = Long.MIN_VALUE;
    /** A reserved miss-click is not an accepted C02 or a native sprint impulse. */
    private long lastClickTick = Long.MIN_VALUE;
    private long attemptedMotion = Long.MIN_VALUE, lastAttackAt = Long.MIN_VALUE;
    /** A refused C02 still consumes its admitted click; only accepted C02 marks lastAttackAt. */
    private long lastClickAt = Long.MIN_VALUE;
    private long delay;
    private boolean automatic, controllerUsed, controllerInFlight;
    /** Abort a pending click-to-controller handoff after lifecycle reset. */
    private long resetRevision;

    public AttackScheduler() {
        this(() -> System.nanoTime() / 1_000_000L,
                (min, max) -> min == max ? min : ThreadLocalRandom.current().nextDouble(min, max));
    }

    public AttackScheduler(LongSupplier clock, DoubleBinaryOperator cps) {
        this.clock = clock; this.cps = cps;
    }

    public void beginTick() { tick++; }
    /** Legacy attack budget/accepted C02 view; click-only swings never claim a hit. */
    public boolean hasAttackThisTick() { return lastAttackTick == tick || lastAutoTick == tick; }
    private boolean hasSpentClickBudget() { return hasAttackThisTick() || lastClickTick == tick; }

    public boolean allowControllerAttack(boolean auraEnabled) {
        if (automatic) {
            if (controllerUsed) return false;
            controllerUsed = true;
            return true;
        }
        return !auraEnabled || !hasSpentClickBudget();
    }

    /** Reserve only an admitted controller call, including all of its synchronous callbacks. */
    public boolean beginControllerAttack(boolean auraEnabled) {
        if (controllerInFlight || !allowControllerAttack(auraEnabled)) return false;
        controllerInFlight = true;
        return true;
    }

    /** A cancelled or throwing manual call must not prevent a later independent retry. */
    public void endControllerAttack() {
        controllerInFlight = false;
    }

    public void recordControllerAttack() {
        lastAttackTick = tick;
        lastAttackAt = clock.getAsLong();
    }

    public boolean tryAttack(long motion, boolean hitValidated, double minCps, double maxCps, BooleanSupplier action) {
        double min = Math.max(.1, Math.min(minCps, maxCps));
        double max = Math.max(min, Math.max(minCps, maxCps));
        return tryAttack(motion, hitValidated, () -> (long) (1000 / cps.applyAsDouble(min, max)), action);
    }

    /** Sample one interval per accepted attack, never on every hit-check or catch-up step. */
    public boolean tryAttack(long motion, boolean hitValidated, LongSupplier nextDelay, BooleanSupplier action) {
        if (controllerInFlight || automatic) return false;
        if (attemptedMotion == motion) return false;
        attemptedMotion = motion;
        if (!hitValidated || hasSpentClickBudget()) return false;
        if (delay == 0) delay = Math.max(1, nextDelay.getAsLong());
        if (!cadenceReady(clock.getAsLong())) return false;
        lastAutoTick = tick;
        automatic = true;
        controllerUsed = false;
        try {
            boolean attacked = action.getAsBoolean();
            if (attacked) {
                recordControllerAttack();
                delay = Math.max(1, nextDelay.getAsLong());
            }
            return attacked;
        } finally { automatic = false; }
    }

    /**
     * Existing four-argument API. Its boolean is CLICK ELIGIBILITY, not a
     * prevalidated ray hit. Older callers still own any swing inside their
     * controller callback; false never draws a CPS sample.
     */
    public boolean tryClick(long motion, boolean clickEligible, LongSupplier nextDelay,
                            BooleanSupplier controllerAttack) {
        return click(motion, clickEligible, true, nextDelay, controllerAttack, () -> { });
    }

    /**
     * Continuous click contract with independently observable swing and attack.
     * The boolean here is HIT VALIDATION, not general eligibility: the caller
     * MUST first gate target/scene/accepted C03/owner (or use the four-argument
     * API with clickEligible=false). A ray MISS may swing but must never call
     * controllerAttack. Only the original controller can authorize C02.
     *
     * A reset/reconfiguration triggered by swing revokes this exact click's
     * pending controller action; the spent tick and last click time survive.
     */
    public boolean tryClick(long motion, boolean hitValidated, LongSupplier nextDelay,
                            BooleanSupplier controllerAttack, Runnable swing) {
        return click(motion, true, hitValidated, nextDelay, controllerAttack, swing);
    }

    private boolean click(long motion, boolean clickEligible, boolean hitValidated,
                          LongSupplier nextDelay, BooleanSupplier controllerAttack, Runnable swing) {
        if (controllerInFlight || automatic || attemptedMotion == motion) return false;
        attemptedMotion = motion;
        if (!clickEligible || hasSpentClickBudget()) return false;
        if (delay == 0) delay = Math.max(1, nextDelay.getAsLong());
        long now = clock.getAsLong();
        if (!cadenceReady(now)) return false;

        // A missed swing consumes one click but is never an accepted C02/sprint impulse.
        lastClickTick = tick;
        lastClickAt = now;
        automatic = true;
        controllerUsed = false;
        final long admissionRevision = resetRevision;
        try {
            // Reserves the physical motion/tick before the user-visible callback.
            swing.run();
            if (admissionRevision != resetRevision || controllerUsed) return false;
            // No hit means no controller call, even if a model/callback wants one.
            return hitValidated && controllerAttack.getAsBoolean();
        } finally {
            // Reset inside swing/attack revokes resampling of a stale session;
            // otherwise each real click draws exactly one next CPS interval.
            try {
                if (admissionRevision == resetRevision) delay = Math.max(1, nextDelay.getAsLong());
            } finally { automatic = false; }
        }
    }

    /** Alternating the old/new APIs or a backwards clock cannot bypass CPS. */
    private boolean cadenceReady(long now) {
        long last = Math.max(lastAttackAt, lastClickAt);
        return last == Long.MIN_VALUE || (now >= last && now - last >= delay);
    }

    public void reset() {
        resetRevision++;
        // A nested reset during swing is a revocation, never fresh admission.
        if (automatic) controllerUsed = true;
        else attemptedMotion = Long.MIN_VALUE;
        // Preserve due interval and click/C02 timestamps, plus current tick
        // reservations across disable, target loss or world change.
    }
}
