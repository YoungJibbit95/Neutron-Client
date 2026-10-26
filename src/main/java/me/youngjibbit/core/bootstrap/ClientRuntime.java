package me.youngjibbit.core.bootstrap;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.core.world.ScaffoldPlacementService;
import me.youngjibbit.module.ModuleManager;

public final class ClientRuntime {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;

    private final TargetingService targetingService = new TargetingService();
    private final RotationManager rotationManager = new RotationManager();
    private final RaycastService raycastService = new RaycastService();
    private final AttackScheduler attackScheduler = new AttackScheduler();
    private final ScaffoldPlacementService scaffoldPlacementService = new ScaffoldPlacementService();

    public ClientRuntime(ModuleManager moduleManager, ConfigManager configManager) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TargetingService getTargetingService() {
        return targetingService;
    }

    public RotationManager getRotationManager() {
        return rotationManager;
    }

    public RaycastService getRaycastService() {
        return raycastService;
    }

    public AttackScheduler getAttackScheduler() {
        return attackScheduler;
    }

    public ScaffoldPlacementService getScaffoldPlacementService() {
        return scaffoldPlacementService;
    }
}
