package me.youngjibbit.core.network;

import java.util.function.DoubleBinaryOperator;

/** FakeLag's criteria and Pulse/Spoof state machine from DemisePrivate e219a97 (AGPL-3.0). */
public final class DemiseFakeLagPolicy {
    public static final class Settings {
        public boolean smart = true, earlyRelease, alwaysSpoof, dynamicHurtTime;
        public float attackHurtTime = 3, attackRange = 4, searchRange = 6, recoilMs = 50;
        public float delayMin = 100, delayMax = 100, opponentAttackMs = 200;
        public float timer = .75f, timerTicks = 1, hurtTimeToStop;
        public String mode = "Pulse", keepRange = "None";
    }
    public static final class Input {
        public boolean eligible, visible, targetSwinging, targetAimingAtServer;
        public double boxDistance, mouseDistance;
        public float clientDistance, serverDistance;
        public int selfHurtTime, targetHurtTime;
    }
    public enum Action { NONE, PULSE, SPOOF, RELEASE }
    public static final class Result {
        public final Action action;
        public final boolean pickPosition, resetPosition;
        public final int delayMs;
        private Result(Action action, boolean pick, boolean reset, int delay) {
            this.action = action; pickPosition = pick; resetPosition = reset; delayMs = delay;
        }
    }
    private final DoubleBinaryOperator random;
    private long delayAt, recoilAt, hurtAt, timerAt, lastNow = Long.MIN_VALUE;
    private boolean blinking, picked, attacked, dispatched, forceWTap, firstHit = true, shouldTimer;
    private enum AfterDispatch { NONE, CLEAR_PICKED, PULSE_CRITERIA_LOSS, SPOOF_CRITERIA_LOSS, INELIGIBLE }
    private AfterDispatch afterDispatch = AfterDispatch.NONE;
    private boolean pendingUpdate;
    private int delayMs, targetHits;

    public DemiseFakeLagPolicy(DoubleBinaryOperator random) { this.random = java.util.Objects.requireNonNull(random); }
    public void reset(long now) {
        delayAt = recoilAt = hurtAt = timerAt = now; lastNow = now;
        blinking = picked = attacked = dispatched = forceWTap = shouldTimer = false;
        pendingUpdate = false; afterDispatch = AfterDispatch.NONE;
        firstHit = true; delayMs = targetHits = 0;
    }
    public Result update(long now, Settings settings, Input input) {
        Result result = beginUpdate(now, settings, input);
        finishUpdate(now);
        return result;
    }
    /** The module applies the returned packet action before finishing source post-dispatch state. */
    public Result beginUpdate(long now, Settings settings, Input input) {
        if (lastNow != Long.MIN_VALUE && now < lastNow) reset(now);
        lastNow = now;
        pendingUpdate = true; afterDispatch = AfterDispatch.NONE;
        if (delayMs == 0) delayMs = sample(settings);
        if (!input.eligible) {
            Action action = blinking ? Action.RELEASE : Action.NONE;
            afterDispatch = AfterDispatch.INELIGIBLE;
            return new Result(action, false, false, delayMs);
        }
        if (input.boxDistance > 3) firstHit = true;
        boolean lag = criteria(now, settings, input);
        Action action = Action.NONE;
        boolean pick = false, resetPosition = false;
        if ("Pulse".equals(settings.mode)) {
            if (lag) {
                if (elapsed(now, recoilAt, settings.recoilMs)) blinking = true;
                if (elapsed(now, delayAt, delayMs) && blinking) {
                    blinking = false; delayAt = recoilAt = now;
                }
                if (blinking) {
                    if (!picked) { picked = true; pick = true; }
                    action = Action.PULSE;
                } else { action = Action.RELEASE; afterDispatch = AfterDispatch.CLEAR_PICKED; }
            } else {
                delayMs = sample(settings);
                if (blinking) action = Action.RELEASE;
                afterDispatch = AfterDispatch.PULSE_CRITERIA_LOSS;
            }
        } else {
            if (lag) {
                if (elapsed(now, recoilAt, settings.recoilMs)) {
                    delayMs = settings.smart && input.targetAimingAtServer && input.targetSwinging
                            ? (int) settings.opponentAttackMs : sample(settings);
                    action = Action.SPOOF; blinking = true; dispatched = false;
                } else blinking = false;
            } else {
                if (!dispatched) action = Action.RELEASE;
                afterDispatch = AfterDispatch.SPOOF_CRITERIA_LOSS;
            }
            resetPosition = !lag || !blinking;
        }
        return new Result(action, pick, resetPosition, delayMs);
    }
    /** Fresh time and callback-mutated blinking are essential for the Pulse loss branch. */
    public void finishUpdate(long now) {
        if (!pendingUpdate) return;
        pendingUpdate = false;
        AfterDispatch phase = afterDispatch; afterDispatch = AfterDispatch.NONE;
        if (phase == AfterDispatch.INELIGIBLE) { blinking = picked = false; return; }
        if (phase == AfterDispatch.CLEAR_PICKED) picked = false;
        else if (phase == AfterDispatch.PULSE_CRITERIA_LOSS) {
            picked = false;
            if (elapsed(now, delayAt, delayMs) && blinking) { blinking = false; delayAt = recoilAt = now; }
        } else if (phase == AfterDispatch.SPOOF_CRITERIA_LOSS) {
            dispatched = true; blinking = picked = false; recoilAt = delayAt = now;
        }
        // Force first hit runs after this boundary, so its new AttackEvent is
        // retained for the following Update, just as the separate source Tick.
        attacked = false;
    }
    /** Also evaluated by the original early-release callback, including its stateful hurt counter. */
    public boolean criteria(long now, Settings settings, Input input) {
        if (!input.eligible) return false;
        if (!settings.smart) {
            float upper = settings.alwaysSpoof ? Float.MAX_VALUE : settings.searchRange;
            float distance = (float) input.boxDistance;
            return input.selfHurtTime <= settings.hurtTimeToStop && input.visible
                    && distance >= Math.min(settings.attackRange, upper)
                    && distance <= Math.max(settings.attackRange, upper);
        }
        boolean registeredAttack = attacked && input.targetHurtTime <= settings.attackHurtTime;
        boolean range = input.boxDistance <= (settings.alwaysSpoof ? Float.MAX_VALUE : settings.searchRange);
        if (input.selfHurtTime > settings.hurtTimeToStop) hurtAt = now;
        if (registeredAttack && input.selfHurtTime == 0 && input.boxDistance > 2.5 && input.boxDistance <= 3) {
            if ("WTap".equals(settings.keepRange)) forceWTap = true;
            else if ("Timer down".equals(settings.keepRange)) { timerAt = now; shouldTimer = true; }
        }
        if (settings.dynamicHurtTime) {
            if (input.selfHurtTime == 9) targetHits++;
            if (input.targetHurtTime == 10 || input.boxDistance > 4.5) targetHits = 0;
            if (targetHits > 2 && input.mouseDistance > 3) return true;
        }
        return !registeredAttack && range && elapsed(now, hurtAt, 100) && input.clientDistance < input.serverDistance;
    }
    private int sample(Settings settings) {
        double low = Math.min(settings.delayMin, settings.delayMax), high = Math.max(settings.delayMin, settings.delayMax);
        return (int) random.applyAsDouble(low, high);
    }
    private static boolean elapsed(long now, long from, double duration) { return now - from > (long) duration; }
    public void attack() { attacked = true; }
    public void consumeFirstHit() { firstHit = false; }
    public boolean firstHit() { return firstHit; }
    public boolean blinking() { return blinking; }
    public boolean picked() { return picked; }
    public int delayMs() { return delayMs; }
    public int targetHits() { return targetHits; }
    public boolean consumeWTap() { boolean value = forceWTap; forceWTap = false; return value; }
    public float timer(long now, Settings settings) {
        if (!settings.smart || elapsed(now, timerAt, settings.timerTicks * 50L)) shouldTimer = false;
        return shouldTimer ? settings.timer : Float.NaN;
    }
    public void earlyRelease() { blinking = false; }
}
