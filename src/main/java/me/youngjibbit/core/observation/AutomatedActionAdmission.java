package me.youngjibbit.core.observation;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;

/** Fresh prerequisite for module attempts, never an action reservation or success signal. */
public final class AutomatedActionAdmission {
    private AutomatedActionAdmission() { }

    public static boolean mayAttempt(ModuleAction action) {
        return mayAttempt(action, RuntimeServices.observation());
    }

    static boolean mayAttempt(ModuleAction action, RuntimeObservation observation) {
        if (observation == null) return false;
        ExecutedStep step = observation.getCurrentStep();
        SceneContext scene = observation.captureScene();
        // A lifecycle boundary during capture must not revive the previously read body.
        return step == observation.getCurrentStep()
                && AutomationEligibility.mayAttempt(action, step, scene, observation.getOuterTickId());
    }
}
