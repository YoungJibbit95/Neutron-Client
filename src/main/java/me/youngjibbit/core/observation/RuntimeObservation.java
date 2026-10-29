package me.youngjibbit.core.observation;

import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.contract.WithheldObservation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Client-thread observation only. It owns no gameplay state, budget or session. */
public final class RuntimeObservation {
    private final Supplier<SceneSample> source;
    private WeakReference<Object> connection = new WeakReference<>(null);
    private WeakReference<Object> player = new WeakReference<>(null);
    private WeakReference<Object> world = new WeakReference<>(null);
    private WeakReference<Object> pipeline = new WeakReference<>(null);
    private long connectionGeneration, packetEpoch = -1, outerTickId = -1, revision;
    private boolean initialized, lastLive, outerTickActive, stopped = true;
    private ExecutedStep currentStep;
    private List<ExecutedStep> lastSteps = Collections.emptyList();
    private WithheldObservation lastWithheld;

    public RuntimeObservation(Supplier<SceneSample> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** Called exclusively at actual Minecraft.runTick entry, never per render frame. */
    public void beginOuterTick() {
        outerTickId++;
        revision++;
        outerTickActive = true;
        stopped = false;
        currentStep = null;
        lastSteps = Collections.emptyList();
        lastWithheld = null;
        captureScene();
    }

    public void endOuterTick() {
        outerTickActive = false;
        currentStep = null;
    }

    /** Explicit world boundaries also cover A -> B -> A between scene samples. */
    public void invalidateScene() {
        connectionGeneration++;
        revision++;
        currentStep = null;
        lastSteps = Collections.emptyList();
        lastWithheld = null;
    }

    public void reset() {
        invalidateScene();
        endOuterTick();
        stopped = true;
    }

    /** Every read samples current identities; historical snapshots do not mutate. */
    public SceneContext captureScene() {
        SceneSample sample = source.get();
        return capture(sample);
    }

    public boolean isCurrent(SceneContext historical) {
        SceneSample sample = source.get();
        // The monitor keeps all current identity objects strongly reachable through comparison on JDK 8.
        synchronized (sample) {
            SceneContext fresh = capture(sample);
            return historical != null && historical.isValid() && fresh.isValid() && historical.sameSceneAs(fresh);
        }
    }

    private SceneContext capture(SceneSample sample) {
        boolean live = !stopped && sample.live && sample.connection != null && sample.player != null
                && sample.world != null && sample.pipeline != null && sample.packetEpoch >= 0;
        if (initialized && (connection.get() != sample.connection || player.get() != sample.player
                || world.get() != sample.world || pipeline.get() != sample.pipeline
                || packetEpoch != sample.packetEpoch || lastLive != live)) connectionGeneration++;
        connection = new WeakReference<>(sample.connection);
        player = new WeakReference<>(sample.player);
        world = new WeakReference<>(sample.world);
        pipeline = new WeakReference<>(sample.pipeline);
        packetEpoch = sample.packetEpoch;
        initialized = true;
        lastLive = live;
        return new SceneContext(sample.connection, sample.player, sample.world, connectionGeneration, packetEpoch,
                !live ? SceneContext.Availability.INVALIDATED : sample.suspended
                        ? SceneContext.Availability.SUSPENDED : SceneContext.Availability.ACTIVE);
    }

    /**
     * Executes the existing decision unchanged, wrapping only its real simulation calls.
     * No callback on normal decision return means withheld; an exception never invents one.
     */
    public void observePlayerStep(Object updatingPlayer, BooleanSupplier simulation,
                                  Consumer<BooleanSupplier> decision) {
        SceneSample sample = source.get();
        if (!outerTickActive || updatingPlayer != sample.player) {
            decision.accept(simulation);
            return;
        }
        final long tick = outerTickId, boundary = revision;
        final ExecutedStep previous = currentStep;
        final List<ExecutedStep> entered = new ArrayList<>(2);
        final int[] calls = {0};
        lastSteps = Collections.emptyList();
        lastWithheld = null;
        try {
            decision.accept(() -> {
                int index = calls[0]++;
                // A reset ends observation, never the existing callback or budget processing.
                if (!outerTickActive || revision != boundary) return simulation.getAsBoolean();
                SceneContext scene = captureScene();
                ExecutedStep.Kind kind = index == 0 ? ExecutedStep.Kind.ORDINARY : ExecutedStep.Kind.EARNED_EXTRA;
                ExecutedStep parent = currentStep;
                currentStep = new ExecutedStep(scene, tick, index, kind, false);
                boolean completed = false;
                try {
                    completed = simulation.getAsBoolean();
                    return completed;
                } finally {
                    currentStep = outerTickActive && revision == boundary ? parent : null;
                    entered.add(new ExecutedStep(scene, tick, index, kind, !completed || revision != boundary));
                }
            });
            if (calls[0] == 0 && outerTickActive && revision == boundary)
                lastWithheld = new WithheldObservation(captureScene(), tick);
        } finally {
            currentStep = outerTickActive && revision == boundary ? previous : null;
            lastSteps = Collections.unmodifiableList(new ArrayList<>(entered));
        }
    }

    public long getOuterTickId() { return outerTickId; }
    /** Client-thread read: current outer tick ID only during its active lifetime; otherwise -1. */
    public long getActiveOuterTickId() { return outerTickActive ? outerTickId : -1L; }
    /** While inside the body, completion is provisional; false/throw records an interrupted final value. */
    public ExecutedStep getCurrentStep() { return currentStep; }
    public List<ExecutedStep> getLastSteps() { return lastSteps; }
    public WithheldObservation getLastWithheld() { return lastWithheld; }

    /** Transient strong locals for one capture/validation; never retained by the observer or snapshots. */
    public static final class SceneSample {
        private final Object connection, player, world, pipeline;
        private final long packetEpoch;
        private final boolean live, suspended;

        public SceneSample(Object connection, Object player, Object world, Object pipeline,
                           long packetEpoch, boolean live, boolean suspended) {
            this.connection = connection;
            this.player = player;
            this.world = world;
            this.pipeline = pipeline;
            this.packetEpoch = packetEpoch;
            this.live = live;
            this.suspended = suspended;
        }
    }
}
