package me.youngjibbit.module.impl.combat;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.combat.AutoblockController;
import me.youngjibbit.core.combat.AdaptiveAutoblockController;
import me.youngjibbit.core.combat.AdaptiveTargetPolicy;
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
import me.youngjibbit.event.player.MovementInputEvent;
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
    private PendingBlock blockAfterHit;
    private boolean previousAdaptiveApproach,previousAiApproach;

    /** An accepted hit may request one later use action, never new movement authority. */
    private final class PendingBlock {
        final EntityLivingBase hit;
        final Object player = mc.thePlayer, world = mc.theWorld;
        final RotationManager manager = RuntimeServices.rotation();
        final me.youngjibbit.core.network.PacketPipeline pipeline;
        final long epoch, generation = lifecycleGeneration;
        final int dueTick = mc.thePlayer.ticksExisted + 1;
        PendingBlock(EntityLivingBase hit, me.youngjibbit.core.network.PacketPipeline pipeline) {
            this.hit = hit; this.pipeline = pipeline; this.epoch = pipeline.getContextVersion();
        }
        boolean current() {
            return generation == lifecycleGeneration && player == mc.thePlayer && world == mc.theWorld
                    && manager == RuntimeServices.rotation() && mc.getNetHandler() != null
                    && pipeline == mc.getNetHandler().getNetworkManager().getClientPackets()
                    && pipeline.isActive() && pipeline.getContextVersion() == epoch;
        }
    }
    private PendingBlock capturePendingBlock(EntityLivingBase hit) {
        if (hit == null || mc.getNetHandler() == null) return null;
        me.youngjibbit.core.network.PacketPipeline pipeline = mc.getNetHandler().getNetworkManager().getClientPackets();
        return pipeline == null || !pipeline.isActive() ? null : new PendingBlock(hit, pipeline);
    }
    private final List<EntityLivingBase> targets = new ArrayList<>();

    private final AutoblockController autoblockController = new AutoblockController();
    private AdaptiveAutoblockController adaptiveAutoblock;
    private AdaptiveTargetPolicy adaptiveSelection;

    private final CheckBoxValue players = new CheckBoxValue("Players", this, true);
    private final CheckBoxValue animals = new CheckBoxValue("Animals", this, false);
    private final CheckBoxValue mobs = new CheckBoxValue("Mobs", this, false);
    private final CheckBoxValue invisibles = new CheckBoxValue("Invisibles", this, false);
    private final CheckBoxValue teams = new CheckBoxValue("Teams", this, false);
    private final CheckBoxValue throughWalls = new CheckBoxValue("Through Walls", this, false);

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Single", "Multi", "Switch"}, 0);
    private final ComboValue sortMode = new ComboValue("Sort Mode", this, new String[]{"Range", "Hurt Time", "Health", "Armor", "Angle"}, 0);

    private final SliderValue minCps = new SliderValue("Min CPS", this, 10.0, 1.0, 20.0, true);
    private final SliderValue maxCps = new SliderValue("Max CPS", this, 12.0, 1.0, 20.0, true);
    private final ClickTimingSettings clickTiming = new ClickTimingSettings(this, minCps, maxCps, true);
    private ClickRandomizer.Sample clickSample;
    private final SliderValue reach = new SliderValue("Reach", this, 3.0, 1.0, 6.0, false);
    private final SliderValue aimRangeMargin = new SliderValue("Aim Range Margin", this, 0.0, 0.0, 3.0, false);
    private final SliderValue multiTargets = new SliderValue("Multi Targets", this, 3.0, 2.0, 6.0, true);
    private final SliderValue maxYawStep = new SliderValue("Yaw Speed", this, 28.0, 1.0, 90.0, true);
    private final SliderValue maxPitchStep = new SliderValue("Pitch Speed", this, 28.0, 1.0, 90.0, true);
    private final SliderValue jitter = new SliderValue("Jitter", this, 0.35, 0.0, 5.0, false);

    // Explicit opt-in for camera-only, off-target swings; never licenses a C02 attack.
    private final CheckBoxValue clickWhileAiming = new CheckBoxValue("Click While Aiming", this, false);
    private final CheckBoxValue rotations = new CheckBoxValue("Rotations", this, true);
    private final CheckBoxValue bestHitVec = new CheckBoxValue("Best Aim Vec", this, true);
    private final CheckBoxValue randomHitVec = new CheckBoxValue("Random Hit Vec", this, false);
    private final RotationSettings rotationSettings = new RotationSettings(this,false,maxYawStep,maxPitchStep,jitter,randomHitVec);
    private final CheckBoxValue raycast = new CheckBoxValue("RayCast", this, false);
    private final MoveFixValue movefix = new MoveFixValue(this, true);
    private final ComboValue bodyRotation = new ComboValue("Body Rotation", this, new String[]{"Vanilla", "Direkt", "Natürlich"}, 0);
    private final RotationReturnSettings rotationReturn = new RotationReturnSettings(this);

    private final CheckBoxValue autoblock = new CheckBoxValue("Autoblock", this, false);
    private final ComboValue autoblockMode = new ComboValue("Autoblock Mode", this, new String[]{"Watchdog", "Verus", "Fake", "Intave", "Vanilla", "Release", "Force"}, 0);
    private final CheckBoxValue customBlockFrequency = new CheckBoxValue("Custom Block Frequency", this, false);
    private final SliderValue blockFrequency = new SliderValue("Block Frequency", this, 4D, 0, 10, true);
    // Append new settings without changing historical serialized indices.
    private final ComboValue attackMode = new ComboValue("Attack Mode", this, new String[]{"PlayerController", "Legit", "Packet"}, 0);
    private final CheckBoxValue failSwing = new CheckBoxValue("Fail Swing", this, false);
    private final SliderValue switchDelay = new SliderValue("Switch Delay MS", this, 500D, 0, 2000, true);
    private final SliderValue blockRange = new SliderValue("Block Range", this, 3D, 1, 6, false);
    private final SliderValue reblockDelay = new SliderValue("Reblock Delay", this, 1D, 0, 10, true);

    public Aura() {
        this.setKey(Keyboard.KEY_R);
        bestHitVec.setVisibility(m->!rotationSettings.arsenic() && (!rotationSettings.adaptive()
                || "Legacy".equals(rotationSettings.adaptiveAimPoint.getValue())));
        raycast.setVisibility(m -> !rotationSettings.adaptive());
        attackMode.setVisibility(m -> rotationSettings.adaptive());
        failSwing.setVisibility(m -> rotationSettings.adaptive());
        clickWhileAiming.setVisibility(m -> !rotationSettings.adaptive());
        switchDelay.setVisibility(m -> "Switch".equals(mode.getValue()));
        multiTargets.setVisibility(m -> "Multi".equals(mode.getValue()));
        blockRange.setVisibility(m -> adaptiveBlockEnabled() && autoblock.getValue());
        reblockDelay.setVisibility(m -> adaptiveBlockEnabled() && autoblock.getValue()
                && !"Force".equals(autoblockMode.getValue()) && !"Fake".equals(autoblockMode.getValue()));
        aimRangeMargin.setVisibility(m -> rotations.getValue());
        customBlockFrequency.setVisibility(m -> !adaptiveBlockEnabled() && autoblock.getValue() && !"Fake".equals(autoblockMode.getValue())
                && !"Intave".equals(autoblockMode.getValue()));
        blockFrequency.setVisibility(m -> customBlockFrequency.isVisible() && customBlockFrequency.getValue());
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.equalsIgnoreCase("Range")?"reach":RotationSettings.canonicalName(name)); }

    @Override
    public void onEnable() {
        previousAdaptiveApproach=rotationSettings.adaptive();
        previousAiApproach=rotationSettings.aiLearned();
        clearCombat(true);
        super.onEnable();
    }

    @Override public void beginConfigUpdate() {
        super.beginConfigUpdate();
        clickTiming.legacyDefaults();
        clickWhileAiming.setValue(false);
        customBlockFrequency.setValue(false);
        blockFrequency.reset();
        aimRangeMargin.reset();
        // Missing legacy keys cannot inherit activation; explicit incoming values apply afterward.
        rotationSettings.softArsenicMotion.reset();
        rotationSettings.hitpointVariation.reset();
        rotationSettings.speedCurve.reset();
        // Missing Adaptive-only keys in a warm import revert to new-mode defaults.
        rotationSettings.adaptiveSpeedPreset.reset();
        rotationSettings.adaptiveHitpointPreset.reset();
        rotationSettings.adaptiveVariationStrength.reset();
        rotationSettings.adaptiveAimPoint.reset();
        rotationSettings.adaptiveKeepAim.reset();
        rotationSettings.beginAdaptiveConfigUpdate();
        attackMode.reset(); failSwing.reset(); switchDelay.reset(); blockRange.reset(); reblockDelay.reset();
        if ("Switch".equals(mode.getValue())) mode.reset();
        if ("Angle".equals(sortMode.getValue())) sortMode.reset();
        if ("Vanilla".equals(autoblockMode.getValue()) || "Release".equals(autoblockMode.getValue())
                || "Force".equals(autoblockMode.getValue())) autoblockMode.reset();
        if(rotationSettings.arsenic() || rotationSettings.adaptive() || rotationSettings.aiLearned())
            rotationSettings.mode.reset();
    }

    @Override public void endConfigUpdate() {
        rotationSettings.endAdaptiveConfigUpdate();
        super.endConfigUpdate();
    }

    @Override public void onValueChange() {
        blockAfterHit = null;
        if (rotationReturn == null) return;
        final long generation = ++lifecycleGeneration;
        if (autoblockController != null) autoblockController.release();
        if (adaptiveAutoblock != null) adaptiveAutoblock.release();
        if (adaptiveSelection != null) adaptiveSelection.reset();
        if (generation != lifecycleGeneration) return;
        final RotationManager manager = RuntimeServices.rotation();
        boolean adaptive=rotationSettings.adaptive(),ai=rotationSettings.aiLearned();
        if(adaptive || previousAdaptiveApproach || ai || previousAiApproach)
            manager.reset(ROTATION_SOURCE);
        previousAdaptiveApproach=adaptive;
        previousAiApproach=ai;
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
        blockAfterHit = null;
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
        if (adaptiveSelection != null) adaptiveSelection.reset();
        // A target loss is not a world transition. Keep the scene identity while returning.
        if (worldChanged) worldContext = playerContext = null;
        manager.reset(ROTATION_SOURCE);
        if (!manager.hasRotationRelease(ROTATION_SOURCE)) RotationCorrections.reset(ROTATION_SOURCE);
        CombatTargetTracker.clear("aura");
        RuntimeServices.attackScheduler().reset();
        if (worldChanged) autoblockController.reset();
        else autoblockController.release();
        if (adaptiveAutoblock != null) {
            if (worldChanged) adaptiveAutoblock.reset(); else adaptiveAutoblock.release();
        }
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

    private double acquisitionRange() {
        return reach.getValue() + (rotations.getValue() ? aimRangeMargin.getValue() : 0);
    }

    private TargetingService.Options acquisitionOptions() {
        TargetingService.Options options = options();
        options.range = acquisitionRange();
        return options;
    }

    /** Current selection, revalidated for dependent modules between Aura updates. */
    public EntityLivingBase getActiveTarget() {
        if (!usable() || worldContext != mc.theWorld || playerContext != mc.thePlayer || target == null
                || target.worldObj != mc.theWorld || mc.theWorld.getEntityByID(target.getEntityId()) != target
                || !mc.theWorld.loadedEntityList.contains(target)
                || !RuntimeServices.targeting().isValidTarget(target, acquisitionOptions())) return null;
        return target;
    }

    @Subscribe
    private final Listener<TickEvent> onContextTick = new Listener<>(event -> {
        if (!usable()) clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
        else if (target != null && (target.worldObj != mc.theWorld
                || !mc.theWorld.loadedEntityList.contains(target)
                || !RuntimeServices.targeting().isValidTarget(target, acquisitionOptions()))) {
            // TickEvent precedes entity movement: do not keep movefix on a lost target until PRE.
            clearCombat(false);
        }
    });

    @Subscribe
    private final Listener<WorldEvent> onWorld = new Listener<>(event -> clearCombat(true));

    @Subscribe
    private final Listener<AttackEvent> onAttack = new Listener<>(event -> {
        autoblockController.beforeAttack();
        if (adaptiveAutoblock != null && adaptiveAutoblock.ownsUse()
                && !adaptiveAutoblock.prepareAttack(() -> usable())) event.cancel();
    });

    @Subscribe private final Listener<MovementInputEvent> blockInput = new Listener<>(event -> {
        PendingBlock pending = blockAfterHit;
        blockAfterHit = null;
        if (pending == null || event.isCancelled() || !usable() || !pending.current()
                || mc.thePlayer.ticksExisted != pending.dueTick) return;
        EntityLivingBase hit = pending.hit;
        RotationManager.MovementRotation prepared = pending.manager.getPreparedMovement();
        // Prediction can veto a competing physical step, but cannot authorize an attack.
        if (prepared == null || (rotations.getValue() ? !ROTATION_SOURCE.equals(prepared.source)
                : prepared.source != null)) return;
        // A full movement separates the use and release. Vanilla releases on the next unheld use key.
        mc.playerController.trySetSwordBlocking(mc.thePlayer, true, () -> usable() && pending.current()
                && pending.manager.getPreparedMovement() == prepared && autoblock.getValue()
                && "Intave".equals(autoblockMode.getValue()) && !mc.gameSettings.keyBindUseItem.isKeyDown()
                && AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                && worldContext == mc.theWorld && playerContext == mc.thePlayer
                && target == hit && mc.theWorld.getEntityByID(hit.getEntityId()) == hit
                && RuntimeServices.targeting().isValidTarget(hit, options()));
    });

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (event.isPre() && RuntimeServices.rotation().hasPreparedMovement()) return;
        handleMotion(event);
    });

    @Subscribe private final Listener<EventRotation> prepareRotation = new Listener<>(preparation -> handleMotion(preparation.motion()));

    private void handleMotion(EventMotion event) {
        // Retain the historical POST eligibility and scheduler invocation order.
        // Only the initial PRE scene comparison requires a stable local client.
        if (!usable()) {
            clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
            return;
        }
        if(rotationSettings.aiLearned() && !rotations.getValue())return;

        if (event.isPre()) {
            final net.minecraft.client.Minecraft entryClient=mc;
            if(entryClient==null)return; // stale synthetic/reentrant client; no new authority
            if (worldContext != entryClient.theWorld || playerContext != entryClient.thePlayer) {
                clearCombat(true);
                // Only AI's new candidate path needs the stronger post-reset
                // scene gate. All older PRE/POST modes retain their original flow.
                if(rotationSettings.aiLearned() && (mc!=entryClient || !usable()))return;
            }
            worldContext = entryClient.theWorld;
            playerContext = entryClient.thePlayer;
            final long generation = lifecycleGeneration;

            List<EntityLivingBase> collected = RuntimeServices.targeting().collectTargets(acquisitionOptions(), mapSort(sortMode.getValue()));
            if (generation != lifecycleGeneration || !usable()) return;
            targets.clear();
            targets.addAll(collected);
            EntityLivingBase previous = target;
            if ((rotationSettings.adaptive() && !"Multi".equals(mode.getValue())) || "Switch".equals(mode.getValue())) {
                if (adaptiveSelection == null) adaptiveSelection = new AdaptiveTargetPolicy();
                target = adaptiveSelection.select(targets, target, "Switch".equals(mode.getValue()),
                        switchDelay.getValue().longValue(), System.nanoTime() / 1_000_000L);
            } else {
                target = TargetingService.selectTarget(targets, target, "Multi".equalsIgnoreCase(mode.getValue()), multiTargets.getValue().intValue(), advanceTarget);
            }
            if (target != previous) {
                blockAfterHit = null;
                if (adaptiveAutoblock != null) adaptiveAutoblock.release();
                if (generation != lifecycleGeneration || !usable()) return;
            }
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
                RotationProfile profile=rotationSettings.snapshot();profile.reach=(float)acquisitionRange();profile.throughWalls=throughWalls.getValue();
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
                } else {
                    // First warm-up/missing-model HOLD offers no C03; retain acquisition.
                    if(rotationSettings.aiLearned())return;
                    clearCombat(false);return;
                }
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
                final long opportunityGeneration = lifecycleGeneration;
                boolean owns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE)
                        : !rotationManager.isOwner("scaffold") && rotationManager.getRotationRelease() == null;
                boolean aiAccepted=!rotationSettings.aiLearned() || rotationManager.isAiAuraMotionAccepted();
                boolean executionReady = !rotationSettings.adaptive() || !"Legit".equals(attackMode.getValue())
                        || mc.playerController.canNativeEntityClick(selected);
                if (adaptiveBlockEnabled() && executionReady && owns && rotationManager.isMotionAccepted()
                        && RuntimeServices.attackScheduler().isClickDue(rotationManager.getMotionSequence())
                        && validTargetRay(selected, yaw, pitch, reach.getValue())) {
                    adaptiveBlock().prepareAttack(() -> opportunityGeneration == lifecycleGeneration
                            && getActiveTarget() == selected && RuntimeServices.rotation() == rotationManager
                            && rotationManager.isMotionAccepted() && validTargetRay(selected, yaw, pitch, reach.getValue()));
                    if (opportunityGeneration != lifecycleGeneration || !usable()) return;
                }
                boolean hit = rotationManager.isMotionAccepted() && owns && aiAccepted && executionReady
                        && validHit(selected,yaw,pitch) && !mc.thePlayer.isUsingItem();
                CombatDiagnostics.opportunityResult(opportunity,Stage.HIT_CHECK,hit);
                // Capture before callbacks: an accepted send can reconfigure or replace this lifecycle.
                PendingBlock pending = hit && autoblock.getValue() && "Intave".equals(autoblockMode.getValue())
                        ? capturePendingBlock(selected) : null;
                // Clicks are continuous while Aura owns an accepted C03 motion, but
                // only the original controller/raycast path may emit an actual C02.
                boolean clickEligible=rotationManager.isMotionAccepted() && owns && aiAccepted
                        && !mc.thePlayer.isUsingItem() && selected != null && getActiveTarget()==selected
                        // Camera-only off-target swings require explicit opt-in; only a
                        // validated hit may progress to actual controller attack.
                        && (rotations.getValue() || hit || clickWhileAiming.getValue())
                        && (!rotationSettings.adaptive() || hit || failSwing.getValue());
                final long clickGeneration=lifecycleGeneration;
                // The legacy overload's false eligibility preserves the old
                // attempted-motion tombstone without spending any click or RNG.
                final java.util.function.LongSupplier nextClickDelay = () -> {
                    clickSample=clickTiming.sample();
                    return clickSample.delayMillis;
                };
                if (!clickEligible) {
                    attackAccepted=RuntimeServices.attackScheduler().tryClick(
                            rotationManager.getMotionSequence(), false, nextClickDelay, () -> false);
                } else {
                    // Local swing and controller C02 permission are different effects.
                    // In particular, a valid C03-owned miss may swing but never attack.
                    attackAccepted=RuntimeServices.attackScheduler().tryClick(
                            rotationManager.getMotionSequence(), hit, nextClickDelay, () -> {
                        if (clickGeneration!=lifecycleGeneration || !usable()
                                || getActiveTarget()!=selected || RuntimeServices.rotation()!=rotationManager
                                || !rotationManager.isMotionAccepted()
                                || (rotations.getValue() ? !rotationManager.isOwner(ROTATION_SOURCE)
                                        : rotationManager.isOwner("scaffold")
                                          || rotationManager.getRotationRelease()!=null)
                                || !validHit(selected,yaw,pitch)) return false;
                        net.minecraft.client.entity.EntityPlayerSP attackingPlayer=mc.thePlayer;
                        net.minecraft.client.multiplayer.PlayerControllerMP attackingController=mc.playerController;
                        if(attackingPlayer==null || attackingController==null) return false;
                        float cameraYaw=attackingPlayer.rotationYaw, cameraPitch=attackingPlayer.rotationPitch;
                        try {
                            attackingPlayer.rotationYaw=yaw;
                            attackingPlayer.rotationPitch=pitch;
                            boolean attacked=attackingController.tryAttackEntity(attackingPlayer,selected, attackExecution(),
                                    () -> clickGeneration==lifecycleGeneration && validHit(selected,yaw,pitch));
                            if (attacked) {
                                advanceTarget=true;
                                if (clickTiming.debugging.getValue() && clickSample!=null)
                                    me.youngjibbit.Pulsar.INSTANCE.debugMessage("[Aura/" + clickTiming.mode.getValue() + "] "
                                            + me.youngjibbit.utils.MathUtils.round(clickSample.cps,1) + " cps");
                            }
                            return attacked;
                        } finally {
                            attackingPlayer.rotationYaw=cameraYaw;
                            attackingPlayer.rotationPitch=cameraPitch;
                        }
                    }, () -> {
                        if(clickGeneration!=lifecycleGeneration || !usable()
                                || getActiveTarget()!=selected || RuntimeServices.rotation()!=rotationManager
                                || !rotationManager.isMotionAccepted()
                                || (rotations.getValue() ? !rotationManager.isOwner(ROTATION_SOURCE)
                                        : rotationManager.isOwner("scaffold")
                                          || rotationManager.getRotationRelease()!=null)) return;
                        net.minecraft.client.entity.EntityPlayerSP player=mc.thePlayer;
                        if(player==null) return;
                        boolean canAttack=hit && validHit(selected,yaw,pitch);
                        if(canAttack && !adaptiveBlockEnabled()) autoblockController.beforeAttack();
                        if(clickGeneration!=lifecycleGeneration || !usable()
                                || mc.thePlayer!=player || getActiveTarget()!=selected
                                || RuntimeServices.rotation()!=rotationManager
                                || !rotationManager.isMotionAccepted()) return;
                        float cameraYaw=player.rotationYaw, cameraPitch=player.rotationPitch;
                        try {
                            player.rotationYaw=yaw;
                            player.rotationPitch=pitch;
                            player.swingItem();
                        } finally {
                            player.rotationYaw=cameraYaw;
                            player.rotationPitch=cameraPitch;
                        }
                    });
                }
                CombatDiagnostics.opportunityResult(opportunity,Stage.SCHEDULER_RESULT,attackAccepted);
                if (!usable()) {
                    clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
                    return;
                }
                boolean blockOwns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE)
                        : !rotationManager.isOwner("scaffold") && rotationManager.getRotationRelease() == null;
                if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) return;
                if ("Intave".equals(autoblockMode.getValue())) {
                    if (autoblock.getValue() && attackAccepted && pending != null && pending.current()
                            && target == selected && blockOwns) {
                        blockAfterHit = pending;
                    }
                    return;
                }
                if (adaptiveBlockEnabled()) {
                    adaptiveBlock().update(autoblock.getValue(), adaptiveBlockMode(), attackAccepted,
                            reblockDelay.getValue().intValue(), () -> opportunityGeneration == lifecycleGeneration
                                    && getActiveTarget() == selected && RuntimeServices.rotation() == rotationManager
                                    && rotationManager.isMotionAccepted() && holdingSword()
                                    && validTargetRay(selected, yaw, pitch, blockRange.getValue()));
                    return;
                }
                autoblockController.update(autoblock.getValue() && attacking && blockOwns && rotationManager.isMotionAccepted()
                                && validHit(selected, yaw, pitch) && PlayerUtil.isHoldingWeapon(),
                        mapAutoblock(autoblockMode.getValue()), false, true, customBlockFrequency.getValue(), blockFrequency.getValue());
            } catch (RuntimeException | Error failure) {
                interrupted=true;
                if (adaptiveAutoblock != null) try { adaptiveAutoblock.release(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            } finally { CombatDiagnostics.endOpportunity(opportunity,attackAccepted,interrupted); }
        } else if (!adaptiveBlockEnabled() && AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) {
            autoblockController.update(autoblock.getValue() && !"Intave".equals(autoblockMode.getValue()) && attacking && PlayerUtil.isHoldingWeapon(),
                    mapAutoblock(autoblockMode.getValue()), true, false, customBlockFrequency.getValue(), blockFrequency.getValue());
        }
    }

    public boolean correctionEnabled() { return usable() && rotations.getValue(); }
    public MoveFixController.Mode moveFixMode() { return MoveFixController.Mode.fromSetting(movefix.getValue()); }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.fromSetting(bodyRotation.getValue()); }

    private boolean validHit(EntityLivingBase entity, float yaw, float pitch) {
        return mc.thePlayer != null && !mc.thePlayer.isUsingItem()
                && (!rotationSettings.adaptive() || !manualUseHeld())
                && validTargetRay(entity, yaw, pitch, reach.getValue());
    }

    private boolean validTargetRay(EntityLivingBase entity, float yaw, float pitch, double range) {
        return AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK) && currentTargetRay(entity, yaw, pitch, range);
    }

    /** Read-only geometry/identity check; does not grant a new automatic action outside an ordinary step. */
    private boolean currentTargetRay(EntityLivingBase entity, float yaw, float pitch, double range) {
        RotationManager manager = RuntimeServices.rotation();
        TargetingService.Options allowed = options(); allowed.range = range;
        return usable() && worldContext == mc.theWorld && playerContext == mc.thePlayer
                && manager.matchesEffective(yaw, pitch)
                && (rotations.getValue() ? manager.isOwner(ROTATION_SOURCE)
                    : !manager.isOwner("scaffold") && manager.getRotationRelease() == null)
                && entity != null && mc.theWorld.loadedEntityList.contains(entity)
                && entity.worldObj == mc.theWorld && mc.theWorld.getEntityByID(entity.getEntityId()) == entity
                && RuntimeServices.targeting().isValidTarget(entity, allowed)
                && RuntimeServices.raycast().canHit(entity, yaw, pitch, (float) range, throughWalls.getValue());
    }

    private boolean adaptiveBlockEnabled() {
        String block = autoblockMode.getValue();
        return !"Intave".equals(block) && (rotationSettings.adaptive()
                || "Vanilla".equals(block) || "Release".equals(block) || "Force".equals(block));
    }
    private AdaptiveAutoblockController adaptiveBlock() {
        if (adaptiveAutoblock == null) adaptiveAutoblock = new AdaptiveAutoblockController();
        return adaptiveAutoblock;
    }
    private boolean holdingSword() { return mc.thePlayer != null && mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword; }
    private boolean manualUseHeld() { return mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null
            && mc.gameSettings.keyBindUseItem.isKeyDown(); }
    private AdaptiveAutoblockController.Mode adaptiveBlockMode() {
        String value = autoblockMode.getValue();
        if ("Fake".equals(value)) return AdaptiveAutoblockController.Mode.FAKE;
        if ("Force".equals(value)) return AdaptiveAutoblockController.Mode.FORCE;
        if ("Release".equals(value) || "Verus".equals(value)) return AdaptiveAutoblockController.Mode.RELEASE;
        return AdaptiveAutoblockController.Mode.VANILLA;
    }
    private net.minecraft.client.multiplayer.PlayerControllerMP.AttackExecution attackExecution() {
        if (rotationSettings.adaptive()) {
            if ("Packet".equals(attackMode.getValue())) return net.minecraft.client.multiplayer.PlayerControllerMP.AttackExecution.PACKET;
            if ("Legit".equals(attackMode.getValue())) return net.minecraft.client.multiplayer.PlayerControllerMP.AttackExecution.NATIVE_CLICK;
        }
        return net.minecraft.client.multiplayer.PlayerControllerMP.AttackExecution.CONTROLLER;
    }

    /** Renderer-only query. Fake never sets item use, a key binding or network ownership. */
    public boolean shouldRenderAdaptiveBlock(net.minecraft.item.ItemStack rendered) {
        return usable() && adaptiveBlockEnabled() && autoblock.getValue() && "Fake".equals(autoblockMode.getValue())
                && adaptiveAutoblock != null && adaptiveAutoblock.visualBlocking() && getActiveTarget() != null
                && rendered != null && rendered == mc.thePlayer.getHeldItem() && holdingSword()
                && RuntimeServices.rotation().isOwner(ROTATION_SOURCE);
    }

    /** Preserve confirmed automatic use between clicks without synthesizing a physical key. */
    public boolean keepsAdaptiveSwordUse() {
        if (!usable() || !adaptiveBlockEnabled() || !autoblock.getValue() || adaptiveAutoblock == null
                || !adaptiveAutoblock.ownsUse() || !holdingSword()) return false;
        RotationManager manager = RuntimeServices.rotation();
        return getActiveTarget() != null && manager.isMotionAccepted()
                && currentTargetRay(target, manager.getEffectiveYaw(), manager.getEffectivePitch(), blockRange.getValue());
    }

    private TargetingService.SortMode mapSort(String mode) {
        if ("Angle".equalsIgnoreCase(mode)) return TargetingService.SortMode.ANGLE;
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
