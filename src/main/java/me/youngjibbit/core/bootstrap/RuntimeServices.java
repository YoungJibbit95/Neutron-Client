package me.youngjibbit.core.bootstrap;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.combat.RaycastService;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.core.world.ScaffoldPlacementService;

/**
 * Canonical runtime service accessor for modules.
 * Uses the shared {@link ClientRuntime} instances once bootstrap is complete,
 * with static fallbacks only before runtime initialization.
 */
public final class RuntimeServices {

    private static final TargetingService FALLBACK_TARGETING = new TargetingService();
    private static final RotationManager FALLBACK_ROTATION = new RotationManager();
    private static final RaycastService FALLBACK_RAYCAST = new RaycastService();
    private static final AttackScheduler FALLBACK_ATTACK = new AttackScheduler();
    private static final ScaffoldPlacementService FALLBACK_SCAFFOLD = new ScaffoldPlacementService();

    private RuntimeServices() {
    }

    public static ClientRuntime runtime() {
        return Pulsar.INSTANCE.getRuntime();
    }

    public static TargetingService targeting() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getTargetingService() : FALLBACK_TARGETING;
    }

    public static RotationManager rotation() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getRotationManager() : FALLBACK_ROTATION;
    }

    public static RaycastService raycast() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getRaycastService() : FALLBACK_RAYCAST;
    }

    public static AttackScheduler attackScheduler() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getAttackScheduler() : FALLBACK_ATTACK;
    }

    public static ScaffoldPlacementService scaffoldPlacement() {
        ClientRuntime runtime = runtime();
        return runtime != null ? runtime.getScaffoldPlacementService() : FALLBACK_SCAFFOLD;
    }
}
