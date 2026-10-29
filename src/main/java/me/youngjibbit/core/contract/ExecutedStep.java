package me.youngjibbit.core.contract;

import java.util.Objects;

/**
 * Immutable description of an entered player body. Creating this value neither
 * runs nor proves execution; a future adapter must create it only for a real body.
 * Withheld opportunities are represented separately by WithheldObservation.
 */
public final class ExecutedStep {
    public enum Kind { ORDINARY, EARNED_EXTRA }

    private final SceneContext scene;
    private final long outerTickId;
    private final int stepIndex;
    private final Kind kind;
    private final boolean interrupted;

    /** Index zero is ordinary; positive indices count executed earned extras. */
    public ExecutedStep(SceneContext scene, long outerTickId, int stepIndex, Kind kind, boolean interrupted) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (outerTickId < 0) throw new IllegalArgumentException("outerTickId must be nonnegative");
        if (stepIndex < 0 || (kind == Kind.ORDINARY ? stepIndex != 0 : stepIndex == 0)) {
            throw new IllegalArgumentException("ORDINARY requires index 0; EARNED_EXTRA requires a positive index");
        }
        this.outerTickId = outerTickId;
        this.stepIndex = stepIndex;
        this.interrupted = interrupted;
    }

    public SceneContext getScene() { return scene; }
    public long getOuterTickId() { return outerTickId; }
    public int getStepIndex() { return stepIndex; }
    public Kind getKind() { return kind; }
    public boolean isExecuted() { return true; }
    public boolean isInterrupted() { return interrupted; }
}
