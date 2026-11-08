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
        if (controllerInFlight) return false;
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
     * Schedule a continuous Aura click opportunity. Unlike tryAttack, the
     * interval advances after every actual click, including a cancelled or
     * raycast-rejected attack. The BooleanSupplier must return ONLY the original
     * controller's accepted attack result. False is never promoted into C02.
     *
     * clickEligible requires a real selected target, owned/accepted C03 and
     * current scene; the final raycast/controller check belongs to the callback.
     * A repeated motion, nested callback or spent client-tick budget cannot click.
     */
    public boolean tryClick(long motion, boolean clickEligible, LongSupplier nextDelay,
                            BooleanSupplier controllerAttack) {
        if (controllerInFlight || attemptedMotion == motion) return false;
        attemptedMotion = motion;
        if (!clickEligible || hasSpentClickBudget()) return false;
        if (delay == 0) delay = Math.max(1, nextDelay.getAsLong());
        long now = clock.getAsLong();
        if (!cadenceReady(now)) return false;

        // A visible click-only swing blocks further automation this tick, but
        // MUST NOT masquerade as an accepted attack for sprint/physics consumers.
        lastClickTick = tick;
        lastClickAt = now;
        automatic = true;
        controllerUsed = false;
        try {
            return controllerAttack.getAsBoolean();
        } finally {
            // No CPS catch-up or hidden second attack after a refused controller.
            // Drop automatic ownership even if sampling throws.
            try { delay = Math.max(1, nextDelay.getAsLong()); }
            finally { automatic = false; }
        }
    }

    /** Alternating the old/new APIs or a backwards clock cannot bypass CPS. */
    private boolean cadenceReady(long now) {
        long last = Math.max(lastAttackAt, lastClickAt);
        return last == Long.MIN_VALUE || (now >= last && now - last >= delay);
    }

    public void reset() {
        attemptedMotion = Long.MIN_VALUE;
        delay = 0;
        // Keep the real click/attack timestamps and shared tick budget across
        // disable, target loss or world change; resets cannot create click bursts.
    }
}
