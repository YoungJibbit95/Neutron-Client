package me.youngjibbit.core.contract;

import java.util.Objects;

/** A suppressed opportunity: no executed body, step index, or simulation callback. */
public final class WithheldObservation {
    private final SceneContext scene;
    private final long outerTickId;

    public WithheldObservation(SceneContext scene, long outerTickId) {
        this.scene = Objects.requireNonNull(scene, "scene");
        if (outerTickId < 0) throw new IllegalArgumentException("outerTickId must be nonnegative");
        this.outerTickId = outerTickId;
    }

    public SceneContext getScene() { return scene; }
    public long getOuterTickId() { return outerTickId; }
    public boolean isExecuted() { return false; }
}
