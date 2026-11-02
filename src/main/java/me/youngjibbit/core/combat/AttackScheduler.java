package me.youngjibbit.core.combat;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongSupplier;

/** One client-tick budget for controller attacks, with an Aura-only CPS cadence. */
public final class AttackScheduler {
    private final LongSupplier clock;
    private final DoubleBinaryOperator cps;
    private long tick, lastAttackTick = Long.MIN_VALUE, lastAutoTick = Long.MIN_VALUE;
    private long attemptedMotion = Long.MIN_VALUE, lastAttackAt = Long.MIN_VALUE;
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
    public boolean hasAttackThisTick() { return lastAttackTick == tick || lastAutoTick == tick; }

    public boolean allowControllerAttack(boolean auraEnabled) {
        if (automatic) {
            if (controllerUsed) return false;
            controllerUsed = true;
            return true;
        }
        return !auraEnabled || !hasAttackThisTick();
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
        if (!hitValidated || hasAttackThisTick()) return false;
        if (delay == 0) delay = Math.max(1, nextDelay.getAsLong());
        if (lastAttackAt != Long.MIN_VALUE && clock.getAsLong() - lastAttackAt < delay) return false;
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

    public void reset() {
        attemptedMotion = Long.MIN_VALUE;
        delay = 0;
        // Do not erase a manual/other module's attack budget or its timestamp.
    }
}
