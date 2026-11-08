package me.youngjibbit.core.combat.ai;

/**
 * Pure Java 8, bounded neural ROTATION CANDIDATE engine. It has no Minecraft,
 * Aura, packet, attack, raycast, file, network or training dependency.
 * A consumer must revalidate its own C03/owner/action gates after every call.
 */
public final class NeutronAiCore {
    public static final double MAX_YAW_DPS = 160;
    public static final double MAX_PITCH_DPS = 100;
    public static final double MAX_ACCEL_DPS2 = 800;
    public static final double MAX_JERK_DPS3 = 12000;
    private static final long MAX_GAP_NANOS = 250_000_000L;
    private static final double MAX_DT_SECONDS = .075;
    private static final double EPS = 1e-8;

    /** Scope tokens are identity-based, including the account and play connection. */
    public static final class Acquisition {
        public final Object owner, scene, connection, target, accountScope;
        public final long connectionEpoch, generation;
        public Acquisition(Object owner, Object scene, Object connection,
                           Object target, Object accountScope,
                           long connectionEpoch, long generation) {
            this.owner = owner; this.scene = scene; this.connection = connection;
            this.target = target; this.accountScope = accountScope;
            this.connectionEpoch = connectionEpoch; this.generation = generation;
        }
        boolean usable() {
            return owner != null && scene != null && connection != null
                    && target != null && accountScope != null
                    && connectionEpoch >= 0 && generation >= 0;
        }
        boolean same(Acquisition other) {
            return other != null && owner == other.owner && scene == other.scene
                    && connection == other.connection && target == other.target
                    && accountScope == other.accountScope
                    && connectionEpoch == other.connectionEpoch && generation == other.generation;
        }
    }

    /** HOLD is never a request for an attack or a transport-accepted rotation. */
    public static final class Candidate {
        public final boolean hasRotation;
        public final double yaw, pitch, yawVelocity, pitchVelocity;
        private Candidate(boolean hasRotation, double yaw, double pitch,
                          double yawVelocity, double pitchVelocity) {
            this.hasRotation = hasRotation;
            this.yaw = yaw; this.pitch = pitch;
            this.yawVelocity = yawVelocity; this.pitchVelocity = pitchVelocity;
        }
    }

    private static final class State {
        final Acquisition acquisition;
        final float[] features = new float[NeutronAiCheckpoint.WIDTH];
        final float[] history = new float[NeutronAiCheckpoint.INPUTS];
        final float[] hidden = new float[NeutronAiCheckpoint.HIDDEN];
        final float[] commands = new float[NeutronAiCheckpoint.OUTPUTS];
        long sequence, nanos, startNanos;
        double yawVelocity, pitchVelocity, yawAcceleration, pitchAcceleration;
        Candidate cached;

        State(Acquisition acquisition, long sequence, long nanos, Candidate hold) {
            this.acquisition = acquisition;
            this.sequence = sequence;
            this.nanos = nanos;
            this.startNanos = nanos;
            this.cached = hold;
        }

        void pushFeatures() {
            System.arraycopy(history, 0, history, NeutronAiCheckpoint.WIDTH,
                    NeutronAiCheckpoint.INPUTS - NeutronAiCheckpoint.WIDTH);
            System.arraycopy(features, 0, history, 0, NeutronAiCheckpoint.WIDTH);
        }
    }

    private final Thread owningThread = Thread.currentThread();
    private NeutronAiCheckpoint model;
    private State current;
    private long lastEpoch = -1, lastGeneration = -1;
    private long inferences;

    /** Explicit injection only. There is no default model or automatic loading. */
    public synchronized boolean install(NeutronAiCheckpoint validated) {
        if (!onOwningThread()) return false;
        current = null;
        model = validated; // null explicitly disables all inference
        return true;
    }

    /** Invalidate the current acquisition without allowing that generation to rearm. */
    public synchronized void reset() {
        if (onOwningThread()) current = null;
    }

    /** Full consent/session withdrawal: discard weights as well as motion state. */
    public synchronized void dispose() {
        if (!onOwningThread()) return;
        current = null;
        model = null;
    }

    public synchronized boolean invalidateIfCurrent(Acquisition acquisition) {
        if (!onOwningThread() || current == null
                || !current.acquisition.same(acquisition)) return false;
        current = null;
        return true;
    }

    public synchronized boolean hasModel() { return model != null; }
    public synchronized long inferenceCount() { return inferences; }

    /**
     * Exactly one inference per distinct physical motion sequence. Render/POST
     * reentry returns the cached immutable candidate; stale sequences HOLD.
     * The caller supplies an actual account/world/connection owner token and
     * authorized=false on pause, UI/focus loss, rejection or cancellation.
     */
    public synchronized Candidate advance(Acquisition acquisition,
                                           NeutronAiFeatures.Observation observation,
                                           long motionSequence, long nanos,
                                           boolean authorized) {
        Candidate hold = hold(observation);
        if (!onOwningThread()) return hold;
        if (!authorized || acquisition == null || !acquisition.usable()
                || motionSequence < 0 || !NeutronAiFeatures.valid(observation)) {
            invalidateIfCurrent(acquisition);
            return hold;
        }

        int order = order(acquisition);
        if (order < 0) return hold;
        if (model == null) {
            // No model: even late installation may not recycle the old acquisition.
            if (order > 0) {
                lastEpoch = acquisition.connectionEpoch;
                lastGeneration = acquisition.generation;
            }
            current = null;
            return hold;
        }
        if (order > 0) {
            lastEpoch = acquisition.connectionEpoch;
            lastGeneration = acquisition.generation;
            State fresh = new State(acquisition, motionSequence, nanos, hold);
            if (!NeutronAiFeatures.populate(observation, 0, fresh.features)) return hold;
            fresh.pushFeatures();
            current = fresh;
            return hold; // first acquisition sample is a zero-motion HOLD
        }

        State state = current;
        if (state == null || !state.acquisition.same(acquisition)) return hold;
        if (motionSequence == state.sequence) return state.cached;
        if (motionSequence < state.sequence) return hold;
        if (nanos < state.nanos) { current = null; return hold; }
        long gap = nanos - state.nanos;
        if (gap < 0 || gap >= MAX_GAP_NANOS) { current = null; return hold; }
        state.sequence = motionSequence;
        if (gap == 0) { state.cached = hold; return hold; }
        state.nanos = nanos;
        long age = nanos - state.startNanos;
        if (age < 0 || !NeutronAiFeatures.populate(observation, age, state.features)) {
            current = null; return hold;
        }
        state.pushFeatures();

        double dt = Math.min(MAX_DT_SECONDS, gap * 1e-9);
        double yawError = NeutronAiFeatures.wrap(NeutronAiFeatures.targetYaw(observation) - observation.yaw);
        double pitchError = NeutronAiFeatures.targetPitch(observation) - observation.pitch;
        if (Math.abs(yawError) <= EPS && Math.abs(pitchError) <= EPS) {
            state.yawVelocity = state.pitchVelocity = 0;
            state.yawAcceleration = state.pitchAcceleration = 0;
            state.cached = hold;
            return hold;
        }
        if (!model.infer(state.history, state.hidden, state.commands)) {
            current = null; return hold;
        }
        inferences++;
        double desiredYaw = desiredRate(state.commands[0], yawError, MAX_YAW_DPS);
        double desiredPitch = desiredRate(state.commands[1], pitchError, MAX_PITCH_DPS);
        state.yawAcceleration = evolveAcceleration(state.yawAcceleration,
                state.yawVelocity, desiredYaw, dt);
        state.pitchAcceleration = evolveAcceleration(state.pitchAcceleration,
                state.pitchVelocity, desiredPitch, dt);
        state.yawVelocity = clamp(state.yawVelocity + state.yawAcceleration * dt,
                -MAX_YAW_DPS, MAX_YAW_DPS);
        state.pitchVelocity = clamp(state.pitchVelocity + state.pitchAcceleration * dt,
                -MAX_PITCH_DPS, MAX_PITCH_DPS);

        double yawStep = safeStep(state.yawVelocity * dt, yawError);
        double pitchStep = safeStep(state.pitchVelocity * dt, pitchError);
        state.yawVelocity = yawStep / dt;
        state.pitchVelocity = pitchStep / dt;
        double yaw = NeutronAiFeatures.wrap(observation.yaw + yawStep);
        double pitch = clamp(observation.pitch + pitchStep, -90, 90);
        state.cached = new Candidate(Math.abs(yawStep) > EPS || Math.abs(pitchStep) > EPS,
                yaw, pitch, state.yawVelocity, state.pitchVelocity);
        return state.cached;
    }

    private boolean onOwningThread() { return Thread.currentThread() == owningThread; }

    private int order(Acquisition acquisition) {
        if (acquisition.connectionEpoch != lastEpoch)
            return Long.compare(acquisition.connectionEpoch, lastEpoch);
        return Long.compare(acquisition.generation, lastGeneration);
    }

    private static Candidate hold(NeutronAiFeatures.Observation obs) {
        double yaw = obs != null && Double.isFinite(obs.yaw)
                ? NeutronAiFeatures.wrap(obs.yaw) : 0;
        double pitch = obs != null && Double.isFinite(obs.pitch)
                ? clamp(obs.pitch, -90, 90) : 0;
        return new Candidate(false, yaw, pitch, 0, 0);
    }

    private static double desiredRate(double command, double remaining, double cap) {
        if (!Double.isFinite(command) || remaining == 0) return 0;
        double wanted = clamp(command * cap, -cap, cap);
        return wanted * remaining > 0 ? wanted : 0;
    }

    private static double evolveAcceleration(double previous, double velocity,
                                             double desired, double dt) {
        double requested = clamp((desired - velocity) / dt, -MAX_ACCEL_DPS2, MAX_ACCEL_DPS2);
        return clamp(requested, previous - MAX_JERK_DPS3 * dt,
                previous + MAX_JERK_DPS3 * dt);
    }

    private static double safeStep(double step, double remaining) {
        if (step * remaining <= 0) return 0;
        return Math.copySign(Math.min(Math.abs(step), Math.abs(remaining)), remaining);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
