package me.youngjibbit.module.impl.combat;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.combat.AutoblockController;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.core.combat.RotationProfile;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.value.impl.MoveFixValue;
import me.youngjibbit.event.motion.EventMotion;
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

    private final CheckBoxValue autoblock = new CheckBoxValue("Autoblock", this, false);
    private final ComboValue autoblockMode = new ComboValue("Autoblock Mode", this, new String[]{"Watchdog", "Verus", "Fake"}, 0);

    public Aura() {
        this.setKey(Keyboard.KEY_R);
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.equalsIgnoreCase("Range")?"reach":RotationSettings.canonicalName(name)); }

    @Override
    public void onEnable() {
        clearCombat(true);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        clearCombat(false);
        super.onDisable();
    }

    private void clearCombat(boolean worldChanged) {
        target = null;
        targets.clear();
        attacking = false;
        advanceTarget = false;
        worldContext = playerContext = null;
        if (worldChanged) autoblockController.reset();
        else autoblockController.release();
        RuntimeServices.attackScheduler().reset();
        RuntimeServices.rotation().reset(ROTATION_SOURCE);
        RotationCorrections.reset(ROTATION_SOURCE);
        CombatTargetTracker.clear("aura");
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
        if (!usable()) {
            clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
            return;
        }

        if (event.isPre()) {
            if (worldContext != mc.theWorld || playerContext != mc.thePlayer) clearCombat(true);
            worldContext = mc.theWorld;
            playerContext = mc.thePlayer;

            targets.clear();
            targets.addAll(RuntimeServices.targeting().collectTargets(options(), mapSort(sortMode.getValue())));
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

                if (nextRotations != null) {
                    rotationManager.request(ROTATION_SOURCE, RotationManager.AURA_PRIORITY, nextRotations[0], nextRotations[1]);
                } else { clearCombat(false);return; }
            } else if (target != null) {
                RuntimeServices.rotation().reset(ROTATION_SOURCE);
            }
        }

        if (event.isPost()) {
            if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) return;
            RotationManager rotationManager = RuntimeServices.rotation();
            float yaw = rotationManager.getEffectiveYaw(), pitch = rotationManager.getEffectivePitch();
            EntityLivingBase selected = target;
            boolean owns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE) : !rotationManager.isOwner("scaffold");
            boolean hit = rotationManager.isMotionAccepted() && owns && validHit(selected, yaw, pitch) && !mc.thePlayer.isUsingItem();
            RuntimeServices.attackScheduler().tryAttack(rotationManager.getMotionSequence(), hit, minCps.getValue(), maxCps.getValue(), () -> {
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
                    if (attacked) advanceTarget = true;
                    return attacked;
                } finally {
                    attackingPlayer.rotationYaw = cameraYaw;
                    attackingPlayer.rotationPitch = cameraPitch;
                }
            });
            if (!usable()) {
                clearCombat(mc == null || mc.thePlayer == null || mc.theWorld == null);
                return;
            }
            boolean blockOwns = rotations.getValue() ? rotationManager.isOwner(ROTATION_SOURCE) : !rotationManager.isOwner("scaffold");
            if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) return;
            autoblockController.update(autoblock.getValue() && attacking && blockOwns && rotationManager.isMotionAccepted() && PlayerUtil.isHoldingWeapon(),
                    mapAutoblock(autoblockMode.getValue()), false, true);
        } else if (AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) {
            autoblockController.update(autoblock.getValue() && attacking && PlayerUtil.isHoldingWeapon(),
                    mapAutoblock(autoblockMode.getValue()), true, false);
        }
    });

    public boolean correctionEnabled() { return usable() && rotations.getValue(); }
    public MoveFixController.Mode moveFixMode() { return MoveFixController.Mode.fromSetting(movefix.getValue()); }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.fromSetting(bodyRotation.getValue()); }

    private boolean validHit(EntityLivingBase entity, float yaw, float pitch) {
        RotationManager manager = RuntimeServices.rotation();
        return AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                && usable() && worldContext == mc.theWorld && playerContext == mc.thePlayer
                && !mc.thePlayer.isUsingItem()
                && manager.matchesEffective(yaw, pitch)
                && (rotations.getValue() ? manager.isOwner(ROTATION_SOURCE) : !manager.isOwner("scaffold"))
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
