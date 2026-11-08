package me.youngjibbit.core.bootstrap;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.core.observation.MinecraftSceneSource;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.ScaffoldPlacementService;
import me.youngjibbit.module.ModuleManager;

public final class ClientRuntime {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;

    private final TargetingService targetingService = new TargetingService();
    private final RotationManager rotationManager = new RotationManager();
    private NeutronAiPersonalModelLoader personalAiLoader;
    private final RaycastService raycastService = new RaycastService();
    private final AttackScheduler attackScheduler = new AttackScheduler();
    private final ScaffoldPlacementService scaffoldPlacementService = new ScaffoldPlacementService();
    private final RuntimeObservation observation = new RuntimeObservation(
            () -> MinecraftSceneSource.capture(net.minecraft.client.Minecraft.getMinecraft()));

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

    /**
     * Non-autostart runtime endpoint for a future explicit-consent UI.
     * Merely asking for the service never reads a model, begins recording,
     * creates a directory or grants consent.
     */
    public synchronized NeutronAiPersonalModelLoader getPersonalAiLoader() {
        if(personalAiLoader==null) {
            java.io.File data=net.minecraft.client.Minecraft.getMinecraft().mcDataDir;
            if(data==null)return null;
            personalAiLoader=new NeutronAiPersonalModelLoader(data.toPath(),rotationManager);
        }
        return personalAiLoader;
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

    public RuntimeObservation getObservation() {
        return observation;
    }
}
