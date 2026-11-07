package me.youngjibbit.module.impl.combat;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.combat.AutoblockController;
import me.youngjibbit.core.combat.ClickTimingSettings;
import me.youngjibbit.core.combat.ClickRandomizer;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.core.combat.RotationProfile;
import me.youngjibbit.core.combat.RotationReturnSettings;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.ActionToken;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.Stage;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import me.youngjibbit.value.impl.MoveFixValue;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.player.PlayerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Aura", description = "killaura", category = Category.Combat)
public class Aura extends Module {

    private static final String ROTATION_SOURCE = "aura";

    private boolean attacking;
    private boolean advanceTarget;
    private Object worldContext, playerContext;
    private long lifecycleGeneration;
    private EntityLivingBase target;
    private final List<EntityLivingBase> targets = new ArrayList<>();

    private final AutoblockController autoblockController = new AutoblockController();

    private final CheckBoxValue players = new CheckBoxValue("Players", this, true);
    private final CheckBoxValue animals = new CheckBoxValue("Animals", this, false);
    private final CheckBoxValue mobs = new CheckBoxValue("Mobs", this, false);
    private final CheckBoxValue invisibles = new CheckBoxValue("Invisibles", this, false);
    private final CheckBoxValue teams = new CheckBoxValue("Teams", this, false);
    private final CheckBoxValue throughWalls = new CheckBoxValue("Through Walls", this, false);

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Single", "Multi"}, 0);
    private final ComboValue sortMode = new ComboValue("Sort Mode", this, new String[]{"Range", "Hurt Time", "Health", "Armor"}, 0);

    private final SliderValue minCps = new SliderValue("Min CPS", this, 10.0, 1.0, 20.0, true);
    private final SliderValue maxCps = new SliderValue("Max CPS", this, 12.0, 1.0, 20.0, true);
    private final ClickTimingSettings clickTiming = new ClickTimingSettings(this, minCps, maxCps, true);
    private ClickRandomizer.Sample clickSample;
    private final SliderValue reach = new SliderValue("Reach", this, 3.0, 1.0, 6.0, false);
    private final SliderValue multiTargets = new SliderValue("Multi Targets", this, 3.0, 2.0, 6.0, true);
    private final SliderValue maxYawStep = new SliderValue("Yaw Speed", this, 28.0, 1.0, 90.0, true);
    private final SliderValue maxPitchStep = new SliderValue("Pitch Speed", this, 28.0, 1.0, 90.0, true);
    private final SliderValue jitter = new SliderValue("Jitter", this, 0.35, 0.0, 5.0, false);

    private final CheckBoxValue rotations = new CheckBoxValue("Rotations", this, true);
    private final CheckBoxValue bestHitVec = new CheckBoxValue("Best Aim Vec", this, true);
    private final CheckBoxValue randomHitVec = new CheckBoxValue("Random Hit Vec", this, false);
    private final RotationSettings rotationSettings = new RotationSettings(this,false,maxYawStep,maxPitchStep,jitter,randomHitVec);
    private final CheckBoxValue raycast = new CheckBoxValue("RayCast", this, false);
    private final MoveFixValue movefix = new MoveFixValue(this, true);
    private final ComboValue bodyRotation = new ComboValue("Body Rotation", this, new String[]{"Vanilla", "Direkt", "Natürlich"}, 0);
    private final RotationReturnSettings rotationReturn = new RotationReturnSettings(this);

    private final CheckBoxValue autoblock = new CheckBoxValue("Autoblock", this, false);
    private final ComboValue autoblockMode = new ComboValue("Autoblock Mode", this, new String[]{"Watchdog", "Verus", "Fake"}, 0);
    private final CheckBoxValue customBlockFrequency = new CheckBoxValue("Custom Block Frequency", this, false);
    private final SliderValue blockFrequency = new SliderValue("Block Frequency", this, 4D, 0, 10, true);

    public Aura() {
        this.setKey(Keyboard.KEY_R);
        bestHitVec.setVisibility(m->!rotationSettings.arsenic());
        customBlockFrequency.setVisibility(m -> autoblock.getValue() && !"Fake".equals(autoblockMode.getValue()));
        blockFrequency.setVisibility(m -> customBlockFrequency.isVisible() && customBlockFrequency.getValue());
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.equalsIgnoreCase("Range")?"reach":RotationSettings.canonicalName(name)); }

    @Override
    public void onEnable() {
        clearCombat(true);
        super.onEnable();
    }

    @Override public void beginConfigUpdate() {
        super.beginConfigUpdate();
        clickTiming.legacyDefaults();
        customBlockFrequency.setValue(false);
        blockFrequency.reset();
        // Missing legacy keys cannot inherit activation; explicit incoming values apply afterward.
        rotationSettings.softArsenicMotion.reset();
        rotationSettings.hitpointVariation.reset();
        rotationSettings.speedCurve.reset();
        if(rotationSettings.arsenic())rotationSettings.mode.reset();
    }

    @Override public void onValueChange() {
        if (rotationReturn == null) return;
        final long generation = ++lifecycleGeneration;
        final RotationManager manager = RuntimeServices.rotation();
        if (!rotationReturn.enabled.getValue() && !softArsenic()) manager.cancelRotationRelease(ROTATION_SOURCE);
        if (isToggled() && !rotations.getValue()) {
            rotationReturn.begin(manager, ROTATION_SOURCE, moveFixMode(), bodyRotationMode(), softArsenic(),
                    () -> generation == lifecycleGeneration && manager == RuntimeServices.rotation());
            if (generation == lifecycleGeneration && manager == RuntimeServices.rotation()) manager.reset(ROTATION_SOURCE);
        }
    }

    @Override
    public void onDisable() {
        // Reentrant scene callbacks must not unsubscribe a newly enabled generation.
        super.onDisable();
        clearCombat(false);
    }

    private boolean softArsenic() {
        return rotationSettings.arsenic() && rotationSettings.softArsenicMotion.getValue();
    }

    private void clearCombat(boolean worldChanged) {
        final long generation = ++lifecycleGeneration;
        final RotationManager manager = RuntimeServices.rotation();
        if (worldChanged || (softArsenic() && (mc == null || mc.currentScreen != null || !mc.inGameHasFocus))) manager.cancelRotationRelease(ROTATION_SOURCE);
        else rotationReturn.begin(manager, ROTATION_SOURCE, moveFixMode(), bodyRotationMode(), softArsenic(),
                () -> generation == lifecycleGeneration && manager == RuntimeServices.rotation());
        if (generation != lifecycleGeneration || manager != RuntimeServices.rotation()) return;
        target = null;
        targets.clear();
        attacking = false;
        advanceTarget = false;
        // A target loss is not a world transition. Keep the scene identity while returning.
        if (worldChanged) worldContext = playerContext = null;
        manager.reset(ROTATION_SOURCE);
        if (!manager.hasRotationRelease(ROTATION_SOURCE)) RotationCorrections.reset(ROTATION_SOURCE);
        CombatTargetTracker.clear("aura");
        RuntimeServices.attackScheduler().reset();
        if (worldChanged) autoblockController.reset();
        else autoblockController.release();
    }

    private boolean usable() {
        return isToggled() && mc != null && mc.thePlayer != null && mc.theWorld != null && mc.playerController != null
                && mc.currentScreen == null && mc.inGameHasFocus && mc.getRenderViewEntity() == mc.thePlayer
                && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isDead && !mc.thePlayer.isSpectator()
                && !mc.thePlayer.isRiding() && mc.thePlayer.ticksExisted > 5;
    }

    private TargetingService.Options options() {
        TargetingService.Options options = new TargetingService.Options();
        options.range = reach.getValue();
        options.players = players.getValue(); options.animals = animals.getValue(); options.mobs = mobs.getValue();
        options.invisibles = invisibles.getValue(); options.throughWalls = throughWalls.getValue(); options.sameTeam = teams.getValue();
        return options;
    }

    @Subscribe
    private final Listener<TickEvent> onContextTick = new Listener<>(event -> {
        if (!usable()) clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
        else if (target != null && (target.worldObj != mc.theWorld
                || !mc.theWorld.loadedEntityList.contains(target)
                || !RuntimeServices.targeting().isValidTarget(target, options()))) {
            // TickEvent precedes entity movement: do not keep movefix on a lost target until PRE.
            clearCombat(false);
        }
    });

    @Subscribe
    private final Listener<WorldEvent> onWorld = new Listener<>(event -> clearCombat(true));

    @Subscribe
    private final Listener<AttackEvent> onAttack = new Listener<>(event -> autoblockController.beforeAttack());

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (event.isPre() && RuntimeServices.rotation().hasPreparedMovement()) return;
        handleMotion(event);
    });

    @Subscribe private final Listener<EventRotation> prepareRotation = new Listener<>(preparation -> handleMotion(preparation.motion()));

    private void handleMotion(EventMotion event) {
        if (!usable()) {
            clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
            return;
        }

        if (event.isPre()) {
            if (worldContext != mc.theWorld || playerContext != mc.thePlayer) clearCombat(true);
            worldContext = mc.theWorld;
            playerContext = mc.thePlayer;
            final long generation = lifecycleGeneration;

            List<EntityLivingBase> collected = RuntimeServices.targeting().collectTargets(options(), mapSort(sortMode.getValue()));
            if (generation != lifecycleGeneration || !usable()) return;
            targets.clear();
            targets.addAll(collected);
            target = TargetingService.selectTarget(targets, target, "Multi".equalsIgnoreCase(mode.getValue()), multiTargets.getValue().intValue(), advanceTarget);
            advanceTarget = false;
            attacking = target != null;
            if (target != null) {
                CombatTargetTracker.update(target, "aura");
            } else {
                clearCombat(false);
                return;
            }

            if (target != null && rotations.getValue()) {
                RotationManager rotationManager = RuntimeServices.rotation();
                RotationProfile profile=rotationSettings.snapshot();profile.reach=reach.getValue().floatValue();profile.throughWalls=throughWalls.getValue();
                float[] nextRotations = rotationManager.calculateRotations(
                        ROTATION_SOURCE,
                        event.getYaw(),
                        event.getPitch(),
                        target,
                        bestHitVec.getValue(),
                        profile, false
                );
                if (generation != lifecycleGeneration || !usable() || rotationManager != RuntimeServices.rotation()) return;
                if (nextRotations != null) {
                    rotationManager.request(ROTATION_SOURCE, RotationManager.AURA_PRIORITY, nextRotations[0], nextRotations[1]);
                } else { clearCombat(false);return; }
            } else if (target != null) {
                RotationManager manager = RuntimeServices.rotation();
                rotationReturn.begin(manager, ROTATION_SOURCE, moveFixMode(), bodyRotationMode(), softArsenic(),
                        () -> generation == lifecycleGeneration && usable());
                if (generation != lifecycleGeneration || !usable()) return;
                // Preserve our own stop; an unrelated camera-target intent still preempts Scaffold.
                if (!manager.hasRotationRelease(ROTATION_SOURCE)) manager.cancelScaffoldReleaseForCameraIntent();
                manager.reset(ROTATION_SOURCE);
            }
        }

        if (event.isPost()) {
            ActionToken opportunity=CombatDiagnostics.beginOpportunity();
            boolean attackAccepted=false, interrupted=false;
            try {
                boolean admitted=AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK);
                CombatDiagnostics.opportunityResult(opportunity,Stage.ADMISSION,admitted);
                if (!admitted) return;
                RotationManager rotationManager = RuntimeServices.rotation();
                float yaw = rotationManager.getEffectiveYaw(), pitch = rotationManager.getEffectivePitch();
                EntityLivingBase selected = target;
                boolean owns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE)
                        : !rotationManager.isOwner("scaffold") && rotationManager.getRotationRelease() == null;
                boolean hit = rotationManager.isMotionAccepted() && owns && validHit(selected, yaw, pitch) && !mc.thePlayer.isUsingItem();
                CombatDiagnostics.opportunityResult(opportunity,Stage.HIT_CHECK,hit);
                attackAccepted=RuntimeServices.attackScheduler().tryAttack(rotationManager.getMotionSequence(), hit, () -> {
                    clickSample = clickTiming.sample();
                    return clickSample.delayMillis;
                }, () -> {
                    // The optional RayCast setting adds an early recheck before changing block state.
                    // The final controller gate below is mandatory, even when this option is off.
                    if (raycast.getValue() && !validHit(selected, yaw, pitch)) return false;
                    net.minecraft.client.entity.EntityPlayerSP attackingPlayer = mc.thePlayer;
                    net.minecraft.client.multiplayer.PlayerControllerMP attackingController = mc.playerController;
                    autoblockController.beforeAttack();
                    if (!validHit(selected, yaw, pitch)) return false;
                    float cameraYaw = attackingPlayer.rotationYaw, cameraPitch = attackingPlayer.rotationPitch;
                    try {
                        // The normal local attack/knockback uses player yaw. Keep it consistent
                        // with the sent silent rotation, then restore the separate camera view.
                        attackingPlayer.rotationYaw = yaw;
                        attackingPlayer.rotationPitch = pitch;
                        attackingPlayer.swingItem();
                        boolean attacked = attackingController.tryAttackEntity(attackingPlayer, selected, () -> validHit(selected, yaw, pitch));
                        if (attacked) {
                            advanceTarget = true;
                            if (clickTiming.debugging.getValue() && clickSample != null)
                                me.youngjibbit.Pulsar.INSTANCE.debugMessage("[Aura/" + clickTiming.mode.getValue() + "] "
                                        + me.youngjibbit.utils.MathUtils.round(clickSample.cps, 1) + " cps");
                        }
                        return attacked;
                    } finally {
                        attackingPlayer.rotationYaw = cameraYaw;
                        attackingPlayer.rotationPitch = cameraPitch;
                    }
                });
                CombatDiagnostics.opportunityResult(opportunity,Stage.SCHEDULER_RESULT,attackAccepted);
                if (!usable()) {
                    clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
                    return;
                }
                boolean blockOwns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE)
                        : !rotationManager.isOwner("scaffold") && rotationManager.getRotationRelease() == null;
                if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) return;
                autoblockController.update(autoblock.getValue() && attacking && blockOwns && rotationManager.isMotionAccepted()
                                && validHit(selected, yaw, pitch) && PlayerUtil.isHoldingWeapon(),
                        mapAutoblock(autoblockMode.getValue()), false, true, customBlockFrequency.getValue(), blockFrequency.getValue());
            } catch (RuntimeException | Error failure) {
                interrupted=true;throw failure;
            } finally { CombatDiagnostics.endOpportunity(opportunity,attackAccepted,interrupted); }
        } else if (AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) {
            autoblockController.update(autoblock.getValue() && attacking && PlayerUtil.isHoldingWeapon(),
                    mapAutoblock(autoblockMode.getValue()), true, false, customBlockFrequency.getValue(), blockFrequency.getValue());
        }
    }

    public boolean correctionEnabled() { return usable() && rotations.getValue(); }
    public MoveFixController.Mode moveFixMode() { return MoveFixController.Mode.fromSetting(movefix.getValue()); }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.fromSetting(bodyRotation.getValue()); }

    private boolean validHit(EntityLivingBase entity, float yaw, float pitch) {
        RotationManager manager = RuntimeServices.rotation();
        return AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                && usable() && worldContext == mc.theWorld && playerContext == mc.thePlayer
                && !mc.thePlayer.isUsingItem()
                && manager.matchesEffective(yaw, pitch)
                && (rotations.getValue() ? manager.isOwner(ROTATION_SOURCE)
                    : !manager.isOwner("scaffold") && manager.getRotationRelease() == null)
                && entity != null && mc.theWorld.loadedEntityList.contains(entity)
                && RuntimeServices.targeting().isValidTarget(entity, options())
                && RuntimeServices.raycast().canHit(entity, yaw, pitch, reach.getValue().floatValue(), throughWalls.getValue());
    }

    private TargetingService.SortMode mapSort(String mode) {
        if ("Health".equalsIgnoreCase(mode)) {
            return TargetingService.SortMode.HEALTH;
        }
        if ("Hurt Time".equalsIgnoreCase(mode)) {
            return TargetingService.SortMode.HURT_TIME;
        }
        if ("Armor".equalsIgnoreCase(mode)) {
            return TargetingService.SortMode.ARMOR;
        }
        return TargetingService.SortMode.DISTANCE;
    }

    private AutoblockController.Mode mapAutoblock(String mode) {
        if ("Verus".equalsIgnoreCase(mode)) {
            return AutoblockController.Mode.VERUS;
        }
        if ("Fake".equalsIgnoreCase(mode)) {
            return AutoblockController.Mode.FAKE;
        }
        return AutoblockController.Mode.WATCHDOG;
    }
}
