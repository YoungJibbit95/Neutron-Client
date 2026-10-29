package me.youngjibbit.core.contract;

/**
 * Pure prerequisite for automatic module attempts, never a grant or reservation.
 * Callers must still use the existing AttackScheduler, placement service and
 * guarded inventory/controller paths and satisfy their budgets and validation.
 * Manual/Vanilla/external controller actions do not consult this policy.
 */
public final class AutomationEligibility {
    public enum ModuleAction { ATTACK, PLACEMENT, INVENTORY }

    private AutomationEligibility() { }

    /**
     * The caller supplies a freshly validated current scene and outer-tick ID on
     * every attempt, including after resume. No state, action, callback or budget
     * is consumed. A missing step denies only this automatic module prerequisite.
     * Scene/rotation/slot acceptance and module-specific scheduling remain external.
     */
    public static boolean mayAttempt(ModuleAction action, ExecutedStep step,
                                     SceneContext currentScene, long currentOuterTickId) {
        if (action == null || step == null || currentScene == null || currentOuterTickId < 0
                || step.isInterrupted() || step.getKind() != ExecutedStep.Kind.ORDINARY
                || step.getOuterTickId() != currentOuterTickId) return false;
        SceneContext captured = step.getScene();
        return captured.isValid() && !captured.isSuspended()
                && currentScene.isValid() && !currentScene.isSuspended()
                && captured.sameSceneAs(currentScene);
    }
}
