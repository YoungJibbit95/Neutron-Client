package me.youngjibbit.core.bootstrap;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.combat.RaycastService;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.NeutronAiPersonalModelLoader;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.core.world.ScaffoldPlacementService;
import me.youngjibbit.core.observation.RuntimeObservation;

/**
 * Canonical runtime service accessor for modules.
 * Uses the shared {@link ClientRuntime} instances once bootstrap is complete,
 * with static fallbacks only before runtime initialization.
 */
public final class RuntimeServices {

    /** Canonical-runtime callers do not need a second, permanently retained service graph. */
    private static final class Fallbacks {
        static final TargetingService TARGETING = new TargetingService();
        static final RotationManager ROTATION = new RotationManager();
        static final RaycastService RAYCAST = new RaycastService();
        static final AttackScheduler ATTACK = new AttackScheduler();
        static final ScaffoldPlacementService SCAFFOLD = new ScaffoldPlacementService();

        static void initialize() {
            // The first prebootstrap accessor initializes this entire group on
            // its caller, retaining the original owner-thread/clock capture order.
        }
    }

    private RuntimeServices() {
    }

    public static ClientRuntime runtime() {
        ClientRuntime runtime = Pulsar.INSTANCE.getRuntime();
        if (runtime == null) Fallbacks.initialize();
        return runtime;
    }

    /** Observation starts with the canonical runtime; there is no parallel fallback observer. */
    public static RuntimeObservation observation() {
        ClientRuntime runtime = runtime();
        return runtime == null ? null : runtime.getObservation();
    }

    public static TargetingService targeting() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getTargetingService() : Fallbacks.TARGETING;
    }

    /** Explicit-only personal AI loader; no implicit activation or model I/O. */
    public static NeutronAiPersonalModelLoader personalAiLoader() {
        ClientRuntime runtime=runtime();
        return runtime==null?null:runtime.getPersonalAiLoader();
    }

    public static RotationManager rotation() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getRotationManager() : Fallbacks.ROTATION;
    }

    public static RaycastService raycast() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getRaycastService() : Fallbacks.RAYCAST;
    }

    public static AttackScheduler attackScheduler() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getAttackScheduler() : Fallbacks.ATTACK;
    }

    public static ScaffoldPlacementService scaffoldPlacement() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getScaffoldPlacementService() : Fallbacks.SCAFFOLD;
    }
}
