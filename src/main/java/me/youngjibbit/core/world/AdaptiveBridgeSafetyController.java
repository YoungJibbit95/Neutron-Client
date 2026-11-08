package me.youngjibbit.core.world;

import java.lang.ref.WeakReference;

/**
 * Per-input Adaptive Bridge safety decisions. This class has no player, key,
 * rotation, timer or packet write access. Its host supplies current collision
 * facts and applies a decision only to that same fresh input sample.
 *
 * A candidate or accepted rotation never substitutes for supporting terrain.
 * STOP also requests grounded sneak at an unsafe edge: zero input alone does
 * not remove residual velocity. Requests expire with the input sample; a new
 * sample must prove the scene, owner and terrain again.
 */
public final class AdaptiveBridgeSafetyController {
    public enum State { SEARCHING, ALIGNING, READY, PLACING, EDGE_GUARD, RECOVERING }

    public enum Mode {
        OFF, SNEAK, STOP, SMART;

        public static Mode fromSetting(String value) {
            if ("Off".equalsIgnoreCase(value)) return OFF;
            if ("Sneak".equalsIgnoreCase(value)) return SNEAK;
            if ("Stop".equalsIgnoreCase(value)) return STOP;
            return SMART;
        }
    }

    /** Host facts captured after the physical input has been refreshed. */
    public static final class Snapshot {
        public AdaptiveBridgePlanner.Scope scope;
        public long inputSequence = -1;
        public Mode mode = Mode.SMART;
        public boolean enabled, contextValid, ownerAllowed, grounded;
        /** Collision under the real player footprint, not just the centre cell. */
        public boolean currentSupported, pathSupported;
        public boolean hasCandidate, acceptedIntent, recovering;
    }

    public static final class Input {
        public final float forward, strafe;
        public final boolean sneak;

        private Input(float forward, float strafe, boolean sneak) {
            this.forward = forward;
            this.strafe = strafe;
            this.sneak = sneak;
        }
    }

    public static final class Decision {
        public final State state;
        public final boolean requestSneak, limitInput;
        public final long inputSequence;

        private Decision(State state, boolean requestSneak, boolean limitInput,
                         long inputSequence) {
            this.state = state;
            this.requestSneak = requestSneak;
            this.limitInput = limitInput;
            this.inputSequence = inputSequence;
        }

        /** Preserve an existing physical/foreign sneak, including its slowdown. */
        public Input apply(float forward, float strafe, boolean alreadySneaking) {
            if (limitInput) { forward = 0; strafe = 0; }
            if (requestSneak && !alreadySneaking) {
                forward *= .3f;
                strafe *= .3f;
            }
            return new Input(forward, strafe, alreadySneaking || requestSneak);
        }
    }

    /** Bounded queries against the host's real collision boxes. */
    public interface SupportProbe {
        boolean supported(double offsetX, double offsetZ);
    }

    public static final class Ground {
        public final boolean currentSupported, pathSupported;
        public final double offsetX, offsetZ;

        private Ground(boolean currentSupported, boolean pathSupported,
                       double offsetX, double offsetZ) {
            this.currentSupported = currentSupported;
            this.pathSupported = pathSupported;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }

    public static final double MAX_PROBE_BLOCKS = .6;
    public static final int PROBE_STEPS = 3;
    private static final double PROBE_ROUNDING_EPS = Math.ulp(MAX_PROBE_BLOCKS) * 4;

    /**
     * Check the current footprint and three points along a bounded displacement.
     * A displacement beyond rounding tolerance is never declared safe by clipping it.
     * The host revalidates its lifecycle after these potentially reentrant reads.
     */
    public static Ground probe(SupportProbe probe, double offsetX, double offsetZ) {
        if (probe == null || !Double.isFinite(offsetX) || !Double.isFinite(offsetZ))
            return new Ground(false, false, 0, 0);
        double length = Math.hypot(offsetX, offsetZ);
        boolean withinBudget = length <= MAX_PROBE_BLOCKS + PROBE_ROUNDING_EPS;
        if (length > MAX_PROBE_BLOCKS) {
            offsetX = offsetX / length * MAX_PROBE_BLOCKS;
            offsetZ = offsetZ / length * MAX_PROBE_BLOCKS;
        }
        boolean current = probe.supported(0, 0);
        if (length == 0) return new Ground(current, current, 0, 0);
        boolean path = current && withinBudget;
        for (int step = 1; step <= PROBE_STEPS; step++) {
            boolean supported = probe.supported(offsetX * step / PROBE_STEPS,
                    offsetZ * step / PROBE_STEPS);
            path &= supported;
        }
        return new Ground(current, path, offsetX, offsetZ);
    }

    /** Keep stale-callback barriers without retaining an unloaded scene. */
    private static final class ScopeKey {
        final WeakReference<Object> owner, world, player, connection;
        final long epoch, generation;

        ScopeKey(AdaptiveBridgePlanner.Scope scope) {
            owner = new WeakReference<>(scope.owner);
            world = new WeakReference<>(scope.world);
            player = new WeakReference<>(scope.player);
            connection = new WeakReference<>(scope.connection);
            epoch = scope.connectionEpoch;
            generation = scope.acquisitionGeneration;
        }

        boolean same(AdaptiveBridgePlanner.Scope token) {
            return token != null && owner.get() == token.owner && world.get() == token.world
                    && player.get() == token.player && connection.get() == token.connection
                    && epoch == token.connectionEpoch && generation == token.acquisitionGeneration;
        }
    }

    private final Thread owningThread = Thread.currentThread();
    private ScopeKey scope;
    private boolean active, hasPlan, placementAttempted, placementCompleted;
    private long planSequence = -1, inputSequence = -1;
    private int inputFacts;
    private Decision cached;
    private State state = State.SEARCHING;

    public synchronized State getState() { return state; }

    /** Tombstones survive reset; the host must advance its acquisition generation. */
    public synchronized void reset() {
        if (Thread.currentThread() != owningThread) return;
        active = false;
        hasPlan = false;
        placementAttempted = placementCompleted = false;
        planSequence = inputSequence = -1;
        cached = null;
        state = State.SEARCHING;
    }

    public synchronized void observePlan(AdaptiveBridgePlanner.Scope token,
                                         long sequence, boolean candidate) {
        if (!admit(token) || sequence < 0 || sequence <= planSequence) return;
        planSequence = sequence;
        hasPlan = candidate;
        placementAttempted = placementCompleted = false;
        state = candidate ? State.ALIGNING : State.SEARCHING;
    }

    /** Informational only: the host still owns all accepted-C03 validation. */
    public synchronized boolean ready(AdaptiveBridgePlanner.Scope token, long sequence) {
        if (!currentPlan(token, sequence) || !hasPlan || placementAttempted) return false;
        state = State.READY;
        return true;
    }

    public synchronized boolean placing(AdaptiveBridgePlanner.Scope token, long sequence) {
        if (!currentPlan(token, sequence) || !hasPlan || placementAttempted || state != State.READY) return false;
        placementAttempted = true;
        state = State.PLACING;
        return true;
    }

    public synchronized void placementResult(AdaptiveBridgePlanner.Scope token,
                                             long sequence, boolean success) {
        if (!currentPlan(token, sequence) || !placementAttempted || placementCompleted) return;
        placementCompleted = true;
        hasPlan = false;
        state = success ? State.SEARCHING : State.RECOVERING;
    }

    public synchronized void invalidate(AdaptiveBridgePlanner.Scope token) {
        if (!current(token)) return;
        hasPlan = false;
        state = State.RECOVERING;
        cached = none(inputSequence, state);
    }

    /** At most one decision per fresh input; changed duplicate facts cannot rearm it. */
    public synchronized Decision update(Snapshot sample) {
        if (sample == null || sample.inputSequence < 0 || !admit(sample.scope))
            return none(sample == null ? -1 : sample.inputSequence, State.RECOVERING);
        if (sample.inputSequence < inputSequence)
            return none(sample.inputSequence, State.RECOVERING);
        int facts = facts(sample);
        if (sample.inputSequence == inputSequence) {
            if (facts != inputFacts) {
                hasPlan = false;
                state = State.RECOVERING;
                cached = none(inputSequence, state);
            }
            return cached;
        }
        inputSequence = sample.inputSequence;
        inputFacts = facts;
        if (!sample.enabled || !sample.contextValid || !sample.ownerAllowed || !sample.grounded) {
            hasPlan = false;
            state = State.RECOVERING;
            return cached = none(inputSequence, state);
        }

        state = sample.recovering ? State.RECOVERING
                : sample.hasCandidate ? (sample.acceptedIntent ? State.READY : State.ALIGNING)
                : State.SEARCHING;
        boolean unsafe = !sample.currentSupported || !sample.pathSupported;
        if (!unsafe || sample.mode == Mode.OFF || sample.mode == null)
            return cached = none(inputSequence, state);

        state = State.EDGE_GUARD;
        // SMART keeps a slow approach possible while acquiring a currently
        // occluded face. Stopping too early would prevent valid acquisition.
        boolean stop = sample.mode == Mode.STOP || sample.mode == Mode.SMART
                && (!sample.currentSupported || sample.recovering);
        return cached = new Decision(state, true, stop, inputSequence);
    }

    private boolean admit(AdaptiveBridgePlanner.Scope token) {
        if (Thread.currentThread() != owningThread || token == null || !token.usable()) return false;
        if (scope != null) {
            if (token.owner != scope.owner.get() || token.acquisitionGeneration < scope.generation)
                return false;
            if (token.acquisitionGeneration == scope.generation)
                return active && scope.same(token);
            // Epochs order only one connection. New connections may start at zero.
            if (token.connection == scope.connection.get() && token.connectionEpoch < scope.epoch)
                return false;
        }
        scope = new ScopeKey(token);
        active = true;
        hasPlan = false;
        placementAttempted = placementCompleted = false;
        planSequence = inputSequence = -1;
        cached = null;
        state = State.SEARCHING;
        return true;
    }

    private boolean current(AdaptiveBridgePlanner.Scope token) {
        return Thread.currentThread() == owningThread && active && scope != null && scope.same(token);
    }

    private boolean currentPlan(AdaptiveBridgePlanner.Scope token, long sequence) {
        return current(token) && sequence >= 0 && sequence == planSequence;
    }

    private static Decision none(long sequence, State state) {
        return new Decision(state, false, false, sequence);
    }

    private static int facts(Snapshot s) {
        int bits = s.mode == null ? 4 : s.mode.ordinal();
        bits = bits * 2 + (s.enabled ? 1 : 0);
        bits = bits * 2 + (s.contextValid ? 1 : 0);
        bits = bits * 2 + (s.ownerAllowed ? 1 : 0);
        bits = bits * 2 + (s.grounded ? 1 : 0);
        bits = bits * 2 + (s.currentSupported ? 1 : 0);
        bits = bits * 2 + (s.pathSupported ? 1 : 0);
        bits = bits * 2 + (s.hasCandidate ? 1 : 0);
        bits = bits * 2 + (s.acceptedIntent ? 1 : 0);
        return bits * 2 + (s.recovering ? 1 : 0);
    }
}
