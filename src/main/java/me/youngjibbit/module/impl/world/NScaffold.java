package me.youngjibbit.module.impl.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.*;
import me.youngjibbit.core.world.DemiseScaffoldGeometry.PlaceData;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventMove;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.youngjibbit.value.impl.DemiseSliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Ported from DemisePrivate e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb, Scaffold.java (AGPL-3.0).
 * Retains Demise's frame rotations, MoveFix, bridge styles and movement options.
 * Placement uses a bounded ray planner, accepted walking poses, a click cadence and
 * collision-based edge protection. See docs/NSCAFFOLD-DEMISE-PARITY.md for these extensions.
 */
@ModuleInfo(name="NScaffold", description="Demise rotations, ray-traced building and edge protection.", category=Category.World)
public final class NScaffold extends Module {
    public static final String ROTATION_SOURCE = "nscaffold";
    private static final int ROTATION_PRIORITY = 15;
    public final ComboValue mode = new ComboValue("Mode", this, new String[]{"Normal", "Breezily", "Telly"}, 0);
    private final SliderValue straightTellyTicks = new DemiseSliderValue("Straight telly ticks", this, 4D, 1, 5, 1);
    private final SliderValue diagonalTellyTicks = new DemiseSliderValue("Diagonal telly ticks", this, 2D, 1, 5, 1);
    private final ComboValue rotations = new ComboValue("Rotations", this, new String[]{"Normal", "Center", "GodBridge", "Reverse"}, 0);
    private final CheckBoxValue staticify = new CheckBoxValue("Static-ify", this, false);
    private final CheckBoxValue clutch = new CheckBoxValue("Clutch", this, false);
    private final ComboValue clutchCriteria = new ComboValue("Clutch criteria", this, new String[]{"MouseOver", "Prediction"}, 0);
    private final ComboValue clutchRotMode = new ComboValue("Clutch rotation mode", this, new String[]{"Normal", "Center"}, 1);
    private final CheckBoxValue instantAlways = new CheckBoxValue("Instant rot Always", this, false);
    private final CheckBoxValue instantMiss = new CheckBoxValue("Instant rot On miss", this, false);
    private final CheckBoxValue instantPredict = new CheckBoxValue("Instant rot Predict", this, false);
    private final CheckBoxValue autoSneakOnClutch = new CheckBoxValue("Auto sneak on clutch", this, false);
    private final SliderValue groundTicksToSneak = new DemiseSliderValue("Ground ticks to sneak", this, 5D, 0, 10, 1);
    public final DemiseRotationSettings rotationSettings = new DemiseRotationSettings(this);
    public final DemiseRotationEngine rotationEngine = new DemiseRotationEngine();
    public final ComboValue sprintMode = new ComboValue("Sprint mode", this, new String[]{"Normal", "Silent", "Ground", "Air", "None"}, 0);
    private final ComboValue speedMode = new ComboValue("Speed mode", this, new String[]{"Tick", "Exempt", "Diagonal", "None"}, 3);
    private final CheckBoxValue swing = new CheckBoxValue("Swing", this, true);
    private final CheckBoxValue rayTrace = new CheckBoxValue("RayTrace", this, true);
    private final ComboValue placementMode = new ComboValue("Placement mode", this, new String[]{"Default", "Legit"}, 0);
    private final SliderValue minCps = new SliderValue("Min CPS", this, 20D, 1, 20, true);
    private final SliderValue maxCps = new SliderValue("Max CPS", this, 20D, 1, 20, true);
    private final ComboValue edgeSafety = new ComboValue("Edge safety", this, new String[]{"Off", "Stop", "Sneak"}, 1);
    private final CheckBoxValue keepY = new CheckBoxValue("KeepY", this, false);
    private final CheckBoxValue speedKeepY = new CheckBoxValue("Speed keepY", this, false);
    private final CheckBoxValue jump = new CheckBoxValue("Jump", this, false);
    private final CheckBoxValue safewalk = new CheckBoxValue("Safewalk", this, false);
    private final CheckBoxValue safewalkNoData = new CheckBoxValue("Safewalk when no data", this, false);
    // The pinned Scaffold registers Hover but does not read it. No substitute behaviour is invented.
    private final CheckBoxValue hover = new CheckBoxValue("Hover", this, false);
    private final CheckBoxValue sneak = new CheckBoxValue("Sneak", this, false);
    private final CheckBoxValue targetEsp = new CheckBoxValue("Target block ESP", this, false);
    private final CheckBoxValue strictRayTrace = new CheckBoxValue("Strict rayTrace", this, false);
    private final SliderValue blocksToJump = new DemiseSliderValue("Blocks to jump", this, 6D, 0, 15, 1);
    private final CheckBoxValue onlyStraight = new CheckBoxValue("Only straight", this, true);
    private final SliderValue blocksToSneak = new DemiseSliderValue("Blocks to sneak", this, 7D, 0, 15, 1);
    private final CheckBoxValue onlySneakOnGround = new CheckBoxValue("Only sneak on ground", this, true);
    private final ComboValue tower = new ComboValue("Tower", this, new String[]{"Jump", "Vanilla", "PullDown", "NCP"}, 0);
    private final ComboValue towerMove = new ComboValue("Tower move", this, new String[]{"Jump", "Vanilla", "PullDown", "NCP"}, 0);
    private final SliderValue pullDownMotion = new DemiseSliderValue("PullDown motion", this, (double) .95f, .5, 1, .01);

    private BlockPos previousBlock;
    public BlockPos targetBlock;
    private double onGroundY;
    private int oldSlot, blocksPlacedSneak, blocksPlacedJump, tellyTicks;
    private PlaceData data;
    private NScaffoldRaycast.Plan rayPlan;
    private Vec3 planningEyes;
    private float[] rotationTarget;
    private NScaffoldRaycast.Plan rotationSupport;
    private boolean isOnRightSide, clutching, startClutch, spoofing, shaderCalled;
    private float yaw, pitch, initialYaw, initialPitch;
    private final DemiseScaffoldMovement movement = new DemiseScaffoldMovement();
    private final LongSupplier millis = System::currentTimeMillis;
    private final BooleanSupplier jumpKey = NScaffold::physicalJumpDown;
    private long clutchTime = millis.getAsLong(), sneakTime = millis.getAsLong();
    private final LongSupplier activityClock = System::nanoTime;
    private final LongSupplier placementClock = System::nanoTime;
    private final BridgePlacementCadence cadence = new BridgePlacementCadence();
    private BridgePlacementCadence.Scope cadenceScope;
    private EntityPlayerSP lastAttemptPlayer;
    private long lastAttemptSequence = -1;
    private int lastAttemptTick = Integer.MIN_VALUE;
    private RotationManager lastPostManager;
    private long lastPostSequence = -1;
    private long generation, activitySequence, activityPlacements;
    private Scope scope;
    private ActivityView activityView;
    private boolean placing;
    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, BUILDING, PAUSED_AURA, TELLY_WAIT, CLUTCHING, EDGE_GUARD }
    private Status status = Status.OFF;

    public NScaffold() {
        straightTellyTicks.setVisibility(m -> is(mode, "Telly"));
        diagonalTellyTicks.setVisibility(m -> is(mode, "Telly"));
        rotations.setVisibility(m -> !is(mode, "Breezily"));
        staticify.setVisibility(m -> rotations.isVisible() && !is(rotations, "Normal") && !is(rotations, "Center"));
        for (Value<?> value : new Value<?>[]{clutchCriteria, clutchRotMode, instantAlways, instantMiss, instantPredict, autoSneakOnClutch})
            value.setVisibility(m -> clutch.getValue());
        groundTicksToSneak.setVisibility(m -> clutch.getValue() && autoSneakOnClutch.getValue());
        strictRayTrace.setVisibility(m -> usesRayTrace());
        blocksToJump.setVisibility(m -> jump.getValue());
        onlyStraight.setVisibility(m -> jump.getValue());
        blocksToSneak.setVisibility(m -> sneak.getValue());
        onlySneakOnGround.setVisibility(m -> sneak.getValue());
        tower.setVisibility(m -> is(mode, "Normal"));
        towerMove.setVisibility(m -> is(mode, "Normal"));
        pullDownMotion.setVisibility(m -> towerMove.isVisible() && is(towerMove, "PullDown"));
    }
    private static boolean is(ComboValue value, String selected) { return selected.equals(value.getValue()); }
    private static Minecraft client() { return Minecraft.getMinecraft(); }
    private static NScaffold instance() {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        return modules == null ? null : (NScaffold) modules.getModule(NScaffold.class);
    }
    private boolean speedEnabled() {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        Module speed = modules == null ? null : modules.getModule(Speed.class);
        return speed != null && speed.isToggled();
    }
    private boolean auraTarget() {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        Aura aura = modules == null ? null : (Aura) modules.getModule(Aura.class);
        return aura != null && aura.isToggled() && aura.getActiveTarget() != null;
    }
    @Override public void onEnable() {
        generation++;
        resetPlacementPlan();
        activityPlacements = 0;
        activityView = null;
        status = Status.WAITING;
        bindScene();
        DemiseRotationHooks.bind();
        super.onEnable();
    }
    @Override public void onDisable() {
        generation++;
        Minecraft c = client();
        if (scope != null && c != null && c.thePlayer == scope.player && c.theWorld == scope.world)
            scope.player.inventory.currentItem = oldSlot;
        spoofing = false;
        blocksPlacedJump = blocksPlacedSneak = 0;
        if (c != null && c.gameSettings != null && c.gameSettings.keyBindUseItem != null)
            KeyBinding.setKeyBindState(c.gameSettings.keyBindUseItem.getKeyCode(), false);
        activityView = null;
        status = Status.OFF;
        resetPlacementPlan();
        // Mouse UPDATE owns the original 150-ms expiry and subsequent return to the camera.
        super.onDisable();
    }
    @Override public void onValueChange() {
        activityView = null;
        // A callback cannot turn an in-flight click into an action under a different profile.
        if (cadence != null && isToggled()) { generation++; resetPlacementPlan(); }
    }
    public Status getStatus() { return status; }
    private boolean usesRayTrace() { return rayTrace.getValue() || is(placementMode, "Legit"); }
    private void resetPlacementPlan() {
        data = null; rayPlan = null; planningEyes = null;
        rotationTarget = null; rotationSupport = null;
        cadenceScope = null;
        if (cadence != null) cadence.reset();
        if (rotationEngine != null) rotationEngine.resetFrameObservation();
    }

    /** AngleEvent precedes the physical mouse delta in Entity.setAngles. */
    public void onDemiseAngle() {
        if (!activeScene()) return;
        final Scope current = scope;
        final long angleGeneration = generation;
        final Minecraft c = client();
        final EntityPlayerSP p = current.player;
        int selectedSlot = DemiseScaffoldInventory.blockSlot(p);
        if (!currentGeneration(current, angleGeneration) || !scopeCurrent(current)) return;
        p.inventory.currentItem = selectedSlot;
        spoofing = true;
        data = null; rayPlan = null; planningEyes = null;
        if (p.onGround) onGroundY = p.getEntityBoundingBox().minY;
        double posY = p.getEntityBoundingBox().minY;
        if (keepY.getValue() || speedKeepY.getValue() && speedEnabled() && !c.gameSettings.keyBindJump.isKeyDown()) posY = onGroundY;
        if (towerMoving() || towering()) onGroundY = posY = p.getEntityBoundingBox().minY;
        targetBlock = new BlockPos(p.posX, posY - 1, p.posZ);
        if (usesRayTrace()) prepareRayPlan(current, targetBlock, posY);
        else {
            PlaceData found = DemiseScaffoldGeometry.findBlock(current.world, targetBlock, p.getPosition(), previousBlock);
            if (!currentGeneration(current, angleGeneration) || !scopeCurrent(current)) return;
            data = found;
        }
        if (generation != angleGeneration || !isToggled() || !scopeCurrent(current)) return;
        if (is(mode, "Telly")) tellyTicks = movement.straight(p, c.gameSettings) ? (int) straightTellyTicks.getValue().floatValue() : (int) diagonalTellyTicks.getValue().floatValue();
        if (auraTarget()) { status = Status.PAUSED_AURA; publishActivity(); return; }
        switch (sprintMode.getValue()) {
            case "Normal": case "Silent": setSprintKey(true); break;
            case "Ground": setSprintKey(p.onGround); if (!p.onGround) p.setSprinting(false); break;
            case "Air": setSprintKey(!p.onGround); if (p.onGround) p.setSprinting(false); break;
            case "None": setSprintKey(false); p.setSprinting(false); break;
        }
        if (tower.isVisible() && (!is(tower, "Jump") && towering() || !is(towerMove, "Jump") && towerMoving()))
            blocksPlacedJump = blocksPlacedSneak = 0;
        if (data == null) {
            retainRotation(current, angleGeneration);
            if (!currentGeneration(current, angleGeneration) || !scopeCurrent(current)) return;
            status = Status.SEARCHING; publishActivity(); return;
        }
        final PlaceData aiming = data;
        if (c.entityRenderer != null) c.entityRenderer.getMouseOver(1);
        if (generation != angleGeneration || !isToggled() || !scopeCurrent(current) || data != aiming) return;
        MovingObjectPosition mouse = c.objectMouseOver;
        // A plan already preserves a suitable preferred ray; update its future aiming origin as we move.
        boolean rotateCheck = rayPlan != null || mouse == null || mouse.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !data.blockPos.equals(mouse.getBlockPos()) || mouse.sideHit != data.facing;
        if (rotateCheck) {
            switch (mode.getValue()) {
                case "Normal": case "Telly":
                    switch (rotations.getValue()) {
                        case "Normal":
                            if (rayPlan == null && waitingForServerRotation()) return;
                            float[] best = bestRotation(); initialYaw = best[0]; initialPitch = best[1]; break;
                        case "Center":
                            float[] center = centerRotation(); initialYaw = center[0]; initialPitch = center[1]; break;
                        case "GodBridge":
                            if (!staticify.getValue() && rayPlan == null && waitingForServerRotation()) return;
                            float movingYaw = moving() ? movement.keyYaw(p, c.gameSettings) - 180 : p.rotationYaw - 180;
                            if (p.onGround) isOnRightSide = Math.floor(p.posX + Math.cos(Math.toRadians(movingYaw)) * .5) != Math.floor(p.posX)
                                    || Math.floor(p.posZ + Math.sin(Math.toRadians(movingYaw)) * .5) != Math.floor(p.posZ);
                            float bridgeYaw = movement.straight(p, c.gameSettings) ? movingYaw + (isOnRightSide ? 45 : -45) : movingYaw;
                            initialYaw = Math.round(bridgeYaw / 45) * 45;
                            initialPitch = staticify.getValue() ? moving() ? 75.7f : 90f : bestRotation()[1]; break;
                        case "Reverse":
                            if (!staticify.getValue() && rayPlan == null && waitingForServerRotation()) return;
                            initialYaw = movement.keyYaw(p, c.gameSettings) - 180; initialPitch = staticify.getValue() ? 80 : bestRotation()[1]; break;
                    }
                    break;
                case "Breezily":
                    initialYaw = Math.round((movement.keyYaw(p, c.gameSettings) - 180) / 45) * 45;
                    initialPitch = moving() ? movement.straight(p, c.gameSettings) ? 80 : 75.7f : 90; break;
            }
        }
        float[] rotation = {initialYaw, initialPitch};
        boolean clutchAir = clutch.getValue() && air(new BlockPos(p.getPositionVector().subtract(0, 2, 0)));
        if (!aimingCurrent(current, angleGeneration, aiming)) return;
        if (clutchAir) {
            switch (clutchCriteria.getValue()) {
                case "MouseOver":
                    MovingObjectPosition ray = DemiseScaffoldGeometry.rayTrace(p, rotation, 4.5, 1);
                    if (!aimingCurrent(current, angleGeneration, aiming)) return;
                    if (ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !moving()
                            || ray.getBlockPos() != null && ray.getBlockPos().distanceSq(aiming.blockPos) > 1) {
                        if (!setClutchRot(rotateCheck)) return;
                        clutching = true;
                    } else clutching = false;
                    break;
                case "Prediction":
                    boolean nextAir = air(p.getPosition().offset(EnumFacing.fromAngle(initialYaw), 1).down());
                    if (!aimingCurrent(current, angleGeneration, aiming)) return;
                    boolean leaning = air(aiming.blockPos.offset(aiming.facing));
                    if (!aimingCurrent(current, angleGeneration, aiming)) return;
                    if (leaning && nextAir || !moving()) startClutch = true;
                    else if (startClutch) { clutchTime = millis.getAsLong(); startClutch = false; }
                    clutching = startClutch || millis.getAsLong() - clutchTime <= 200;
                    if (clutching && !setClutchRot(rotateCheck)) return;
                    break;
            }
        } else clutching = false;
        if (generation != angleGeneration || !isToggled() || !scopeCurrent(current) || data != aiming) return;
        if (!is(mode, "Telly") || p.offGroundTicks >= tellyTicks) {
            DemiseRotationSettings.Snapshot settings = rotationSettings.snapshot();
            float[] requested = placementRotationTarget(current, angleGeneration, aiming,
                    new float[]{clutching ? yaw : initialYaw, clutching ? pitch : initialPitch}, settings);
            if (requested == null || !aimingCurrent(current, angleGeneration, aiming)) return;
            rotationEngine.setRotation(requested, settings);
            rotationTarget = requested.clone();
            rotationSupport = rayPlan;
            status = clutching ? Status.CLUTCHING : Status.ALIGNING;
        } else status = Status.TELLY_WAIT;
        publishActivity();
    }

    /** Demise refreshes its last target even while the destination is already occupied. */
    private void retainRotation(Scope current, long expected) {
        final float[] retained = rotationTarget;
        final NScaffoldRaycast.Plan support = rotationSupport;
        if (!usesRayTrace() || retained == null
                || is(mode, "Telly") && current.player.offGroundTicks < tellyTicks) return;
        final EntityPlayerSP p = current.player;
        final BlockPos feet = targetBlock;
        final net.minecraft.client.multiplayer.PlayerControllerMP controller = current.client.playerController;
        final double x = p.posX, y = p.posY, z = p.posZ;
        final AxisAlignedBB body = p.getEntityBoundingBox();
        final float eyeHeight = p.getEyeHeight();
        BooleanSupplier valid = () -> currentGeneration(current, expected) && scopeCurrent(current)
                && currentGeneration(current, expected) && rotationTarget == retained && rotationSupport == support
                && data == null && rayPlan == null && targetBlock == feet
                && current.client.playerController == controller && p.posX == x && p.posY == y && p.posZ == z
                && p.getEntityBoundingBox() == body && p.getEyeHeight() == eyeHeight
                && !auraTarget() && (!is(mode, "Telly") || p.offGroundTicks >= tellyTicks)
                && currentGeneration(current, expected) && rotationTarget == retained && data == null && rayPlan == null;
        if (!valid.getAsBoolean()) return;
        if (NScaffoldRaycast.hasRotationSupport(current.world, new Vec3(x, y + eyeHeight, z), feet,
                support, controller.getBlockReachDistance(), valid) && valid.getAsBoolean()) {
            // Keep the requested endpoint, not the interpolated current pose: smoothing must continue.
            // This refresh grants no placement plan, packet or click permission.
            rotationEngine.setRotation(retained.clone(), rotationSettings.snapshot());
        }
    }
    /** A real target can lie inside Demise's integer deadband while the current ray still misses. */
    private float[] placementRotationTarget(Scope current, long expected, PlaceData target, float[] requested,
                                             DemiseRotationSettings.Snapshot settings) {
        final NScaffoldRaycast.Plan plan = rayPlan;
        DemiseRotationEngine.Aim aim = rotationEngine.currentAim();
        // Fixed bridge-style angles keep their original meaning; only geometric aims use this correction.
        if (plan == null || aim == null || !clutching && (is(mode, "Breezily")
                || !is(rotations, "Normal") && !is(rotations, "Center"))) return requested;
        float f = client().gameSettings.mouseSensitivity * .6F + .2F;
        float deadband = f * f * f * 8.0F * (settings.smoothMode == DemiseRotationEngine.Smoothing.Relative ? 4 : 1);
        boolean stalledAcceleration = settings.accel && rotationEngine.wasLastFrameStationary();
        float yawDeadband = deadband * (stalledAcceleration ? survivingCounts(settings.yawAccelFactor) : 1);
        float pitchDeadband = deadband * (stalledAcceleration ? survivingCounts(settings.pitchAccelFactor) : 1);
        float dx = MathHelper.wrapAngleTo180_float(requested[0] - aim.yaw);
        float dy = MathHelper.wrapAngleTo180_float(requested[1] - aim.pitch);
        if (!Float.isFinite(deadband) || deadband <= 0 || Math.abs(dx) >= yawDeadband || Math.abs(dy) >= pitchDeadband
                || Math.max(Math.abs(dx), Math.abs(dy)) < 1.0e-4F) return requested;
        final EntityPlayerSP p = current.player;
        final net.minecraft.client.multiplayer.PlayerControllerMP controller = current.client.playerController;
        final double x = p.posX, y = p.posY, z = p.posZ;
        final AxisAlignedBB body = p.getEntityBoundingBox();
        final float eyeHeight = p.getEyeHeight();
        final Vec3 plannedEyes = planningEyes;
        BooleanSupplier valid = () -> {
            if (rayPlan != plan || !aimingCurrent(current, expected, target) || p.getEyeHeight() != eyeHeight) return false;
            DemiseRotationEngine.Aim now = rotationEngine.currentAim();
            return now != null && now.yaw == aim.yaw && now.pitch == aim.pitch
                    && p.posX == x && p.posY == y && p.posZ == z && p.getEntityBoundingBox() == body
                    && current.client.playerController == controller && currentGeneration(current, expected)
                    && rayPlan == plan && data == target && planningEyes == plannedEyes;
        };
        // This is an aiming correction, so use the same bounded forecast as the geometric plan.
        // Only place() can authorize a click, using the actual accepted walking pose.
        Vec3 eyes = plannedEyes == null ? new Vec3(x, y + eyeHeight, z) : plannedEyes;
        double reach = controller.getBlockReachDistance();
        MovingObjectPosition hit = NScaffoldRaycast.trace(current.world, eyes, aim.yaw, aim.pitch, reach, plan, valid);
        if (!valid.getAsBoolean()) return null;
        if (hit != null && (!strictRayTrace.getValue() || NScaffoldRaycast.interiorHit(plan, hit, .01))) return requested;
        // Even a predictive aim must point at a real visible face in the current world.
        hit = NScaffoldRaycast.trace(current.world, eyes, requested[0], requested[1], reach, plan, valid);
        if (!valid.getAsBoolean()) return null;
        if (hit == null || strictRayTrace.getValue() && !NScaffoldRaycast.interiorHit(plan, hit, .01)) return requested;
        int axis = Math.abs(dx) >= Math.abs(dy) ? 0 : 1;
        float present = axis == 0 ? aim.yaw : aim.pitch, delta = axis == 0 ? dx : dy;
        // Request a count that survives the observed rounding stall. The unchanged engine still owns
        // speed limits, frame timing, smoothing, acceleration, short stops and actual pitch clamping.
        float threshold = axis == 0 ? yawDeadband : pitchDeadband;
        requested[axis] = present + Math.copySign(threshold + Math.max(1.0e-4F, Math.ulp(present) * 2), delta);
        return requested;
    }

    private static int survivingCounts(float factor) {
        // Use Demise's float multiplication/cast: .8F needs six counts, not the idealized five.
        for (int counts = 1; counts <= 16; counts++)
            if ((int) (counts * (1 - factor)) >= 1) return counts;
        return 1;
    }

    /** Aim ahead of the next walking packet; only the later real ray can authorize a click. */
    private void prepareRayPlan(Scope current, BlockPos feet, double feetY) {
        EntityPlayerSP p = current.player;
        final long planGeneration = generation;
        final double originX = p.posX, originY = p.posY, originZ = p.posZ;
        final AxisAlignedBB body = p.getEntityBoundingBox();
        BooleanSupplier valid = () -> isToggled() && generation == planGeneration && scopeCurrent(current)
                && p.posX == originX && p.posY == originY && p.posZ == originZ && p.getEntityBoundingBox() == body
                && currentGeneration(current, planGeneration);
        Vec3 eyes = p.getPositionEyes(1);
        // Forecast the intended step even when the physical edge guard currently holds it.
        // Clamping this vector to the already supported path would starve the next target.
        Vec3 motion = predictedMotion(p);
        if (!valid.getAsBoolean()) return;
        float[] preferred = rotationEngine.shouldRotate() ? rotationEngine.rotation()
                : new float[]{p.rotationYaw, p.rotationPitch};
        net.minecraft.client.settings.GameSettings keys = current.client.gameSettings;
        boolean walking = keys.keyBindForward.isKeyDown() != keys.keyBindBack.isKeyDown()
                || keys.keyBindLeft.isKeyDown() != keys.keyBindRight.isKeyDown();
        if (!is(mode, "Breezily") && is(rotations, "Normal") && walking) {
            // Align the preferred ray with the intended path instead of retaining the first
            // oblique acquisition angle and feeding its sideways drift back into discrete MoveFix.
            preferred[0] = movement.keyYaw(p, keys) - 180;
            // Do not lower the endpoint to the still-interpolating pitch while yaw is
            // catching up. That turns a good interior aim into a grazing top-edge hit.
            if (rotationTarget != null) preferred[1] = MathHelper.clamp_float(rotationTarget[1], -90, 90);
        }
        double reach = client().playerController.getBlockReachDistance();
        double margin = strictRayTrace.getValue() ? .01 : 0;
        if (!current.world.isBlockLoaded(feet) || !valid.getAsBoolean()) return;
        boolean underfootEmpty = current.world.getBlockState(feet).getBlock().getMaterial().isReplaceable();
        if (!valid.getAsBoolean()) return;
        Vec3 supportedMotion = motion;
        if (underfootEmpty && guardGround(p) && !physicalJump() && !p.movementInput.jump) {
            NScaffoldEdgeGuard.Result limit = NScaffoldEdgeGuard.limit(current.world, p, motion.xCoord, 0, motion.zCoord, valid);
            if (limit.active) supportedMotion = new Vec3(limit.x, 0, limit.z);
        }
        if (!valid.getAsBoolean()) return;
        BlockPos wanted = feet;
        double length = Math.hypot(motion.xCoord, motion.zCoord);
        if (!underfootEmpty) {
            if (length < 1.0E-5) return;
            // Begin turning before the centre crosses the current support's edge.
            wanted = new BlockPos(p.posX + motion.xCoord + motion.xCoord / length * .32,
                    feetY - 1, p.posZ + motion.zCoord + motion.zCoord / length * .32);
            if (wanted.equals(feet)) return;
        }
        double preview = 0;
        if (length > 1.0E-5) {
            double directionX = motion.xCoord / length, directionZ = motion.zCoord / length;
            double crossing = Double.NEGATIVE_INFINITY;
            if (directionX > 1.0e-5) crossing = Math.max(crossing, (wanted.getX() - p.posX) / directionX);
            else if (directionX < -1.0e-5) crossing = Math.max(crossing, (wanted.getX() + 1 - p.posX) / directionX);
            if (directionZ > 1.0e-5) crossing = Math.max(crossing, (wanted.getZ() - p.posZ) / directionZ);
            else if (directionZ < -1.0e-5) crossing = Math.max(crossing, (wanted.getZ() + 1 - p.posZ) / directionZ);
            preview = Math.max(0, Math.min(1.5, crossing + .2));
        }
        // Keep the early aim through the plane crossing instead of pitching nearly vertical
        // for one frame and then undoing that turn at the supported overhang.
        double advance = Math.max(length, preview);
        // Once the foot cell is empty, aim from the next physically reachable walking pose.
        // A stopped player cannot reach an unguarded forecast; tiny local motion may also
        // leave vanilla's reported position unchanged. Reuse its actual reporting decision.
        planningEyes = underfootEmpty
                ? p.getPlannedMovementPosition(originX + supportedMotion.xCoord, body.minY,
                        originZ + supportedMotion.zCoord).addVector(0, p.getEyeHeight(), 0)
                : length > 1.0E-5
                        ? eyes.addVector(motion.xCoord / length * advance, 0, motion.zCoord / length * advance) : eyes;
        if (!underfootEmpty && length > 1.0E-5 && guardGround(p) && !physicalJump() && !p.movementInput.jump) {
            // Aim early from the reachable end of the current support, not from a point
            // barely across its plane that forces a near-vertical turn at every diagonal step.
            double probe = Math.min(1.5, advance + .6);
            NScaffoldEdgeGuard.Result edge = NScaffoldEdgeGuard.limit(current.world, p,
                    motion.xCoord / length * probe, 0, motion.zCoord / length * probe, valid);
            if (edge.active && edge.limited)
                planningEyes = eyes.addVector(edge.x, 0, edge.z);
        }
        if (!valid.getAsBoolean()) return;
        NScaffoldRaycast.Plan plan = underfootEmpty
                ? walking
                        ? NScaffoldRaycast.findMoving(p, planningEyes, wanted, previousBlock, preferred[0], preferred[1], reach, valid, margin)
                        : NScaffoldRaycast.find(p, planningEyes, wanted, previousBlock, preferred[0], preferred[1], reach, valid, margin)
                : NScaffoldRaycast.findAhead(p, planningEyes, feet, wanted, previousBlock,
                        preferred[0], preferred[1], reach, valid, margin);
        if (plan == null && underfootEmpty) {
            planningEyes = p.getPlannedMovementPosition(originX, body.minY, originZ).addVector(0, p.getEyeHeight(), 0);
            plan = walking
                    ? NScaffoldRaycast.findMoving(p, planningEyes, wanted, previousBlock, preferred[0], preferred[1], reach, valid, margin)
                    : NScaffoldRaycast.find(p, planningEyes, wanted, previousBlock, preferred[0], preferred[1], reach, valid, margin);
        } else if (plan == null && length > 1.0E-5 && preview + 1.0e-5 < advance) {
            // A forward side is occluded until the eye passes its plane. This is only an aiming preview.
            planningEyes = eyes.addVector(motion.xCoord / length * preview, 0, motion.zCoord / length * preview);
            plan = NScaffoldRaycast.findAhead(p, planningEyes, feet, wanted, previousBlock,
                    preferred[0], preferred[1], reach, valid, margin);
        }
        if (!valid.getAsBoolean()) return;
        rayPlan = plan;
        data = plan == null ? null : plan.data;
        if (plan != null) targetBlock = plan.destination;
    }

    /** Bounded vanilla horizontal prediction; it never writes input, velocity or player position. */
    private Vec3 predictedMotion(EntityPlayerSP p) {
        net.minecraft.client.settings.GameSettings keys = client().gameSettings;
        float forward = (keys.keyBindForward.isKeyDown() ? 1 : 0) - (keys.keyBindBack.isKeyDown() ? 1 : 0);
        float strafe = (keys.keyBindLeft.isKeyDown() ? 1 : 0) - (keys.keyBindRight.isKeyDown() ? 1 : 0);
        float inputYaw = p.rotationYaw;
        DemiseRotationEngine.Aim aim = rotationEngine.currentAim();
        if (aim != null && rotationEngine.correction() != DemiseRotationEngine.Correction.None) {
            if (rotationEngine.correction() == DemiseRotationEngine.Correction.Silent) {
                DemiseMoveFix.Input input = DemiseMoveFix.correct(strafe, forward, aim.yaw, p.rotationYaw);
                strafe = input.strafe; forward = input.forward;
            }
            inputYaw = aim.yaw;
        }
        return predictedMotion(p, forward, strafe, inputYaw, keys.keyBindSneak.isKeyDown());
    }
    private Vec3 predictedMotion(EntityPlayerSP p, float forward, float strafe, float inputYaw, boolean sneaking) {
        if (forward == 0 && strafe == 0) return boundedMotion(p.motionX, p.motionZ);
        float scale = .98F * (sneaking ? .3F : 1F) * (p.isUsingItem() ? .2F : 1F);
        forward *= scale; strafe *= scale;
        double strength = Math.max(1, Math.hypot(strafe, forward));
        double acceleration = p.onGround ? p.getAIMoveSpeed() : p.jumpMovementFactor;
        if (p.onGround) {
            Scope current = scope;
            BlockPos below = new BlockPos(p.posX, p.getEntityBoundingBox().minY - 1, p.posZ);
            if (current == null || !current.world.isBlockLoaded(below) || !scopeCurrent(current)) return new Vec3(0, 0, 0);
            float friction = current.world.getBlockState(below).getBlock().slipperiness * .91F;
            acceleration *= .16277136F / (friction * friction * friction);
        }
        double angle = Math.toRadians(inputYaw), sine = Math.sin(angle), cosine = Math.cos(angle);
        double dx = p.motionX + (strafe * cosine - forward * sine) / strength * acceleration;
        double dz = p.motionZ + (forward * cosine + strafe * sine) / strength * acceleration;
        return boundedMotion(dx, dz);
    }
    private static Vec3 boundedMotion(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) return new Vec3(0, 0, 0);
        double length = Math.hypot(dx, dz);
        return length <= .8 ? new Vec3(dx, 0, dz) : new Vec3(dx / length * .8, 0, dz / length * .8);
    }

    private float[] centerRotation() {
        if (rayPlan == null || planningEyes == null)
            return DemiseScaffoldGeometry.rotations(scope.player, DemiseScaffoldGeometry.faceCenter(data.blockPos, data.facing));
        AxisAlignedBB box = rayPlan.actualBounds;
        Vec3 center = new Vec3((box.minX + box.maxX) * .5, (box.minY + box.maxY) * .5, (box.minZ + box.maxZ) * .5);
        switch (data.facing) {
            case EAST: center = new Vec3(box.maxX, center.yCoord, center.zCoord); break;
            case WEST: center = new Vec3(box.minX, center.yCoord, center.zCoord); break;
            case UP: center = new Vec3(center.xCoord, box.maxY, center.zCoord); break;
            case DOWN: center = new Vec3(center.xCoord, box.minY, center.zCoord); break;
            case NORTH: center = new Vec3(center.xCoord, center.yCoord, box.minZ); break;
            case SOUTH: center = new Vec3(center.xCoord, center.yCoord, box.maxZ); break;
        }
        Vec3 offset = center.subtract(planningEyes);
        return new float[]{(float) Math.toDegrees(Math.atan2(offset.zCoord, offset.xCoord)) - 90,
                (float) -Math.toDegrees(Math.atan2(offset.yCoord, Math.hypot(offset.xCoord, offset.zCoord)))};
    }
    private void setSprintKey(boolean pressed) { KeyBinding.setKeyBindState(client().gameSettings.keyBindSprint.getKeyCode(), pressed); }
    private boolean moving() { return scope != null && DemiseScaffoldMovement.moving(scope.player); }
    public float demiseMovementYaw() { return movement.keyYaw(client().thePlayer, client().gameSettings); }
    private boolean air(BlockPos pos) {
        Scope current = scope;
        long expected = generation;
        return current != null && current.world.getBlockState(pos).getBlock() instanceof BlockAir
                && generation == expected && scopeCurrent(current) && currentGeneration(current, expected);
    }
    /** Fresh scenes wait for factual C03 angles where the original search requires them. */
    private boolean waitingForServerRotation() {
        if (rotationEngine.hasServerRotation()) return false;
        data = null;
        status = Status.WAITING;
        publishActivity();
        return true;
    }
    private float[] bestRotation() {
        return rayPlan == null ? DemiseScaffoldGeometry.bestRotation(scope.player, data, rotationEngine.serverRotation())
                : new float[]{rayPlan.yaw, rayPlan.pitch};
    }
    private boolean setClutchRot(boolean rotateCheck) {
        final Scope current = scope;
        final long expected = generation;
        final PlaceData aiming = data;
        if (rotateCheck && rayPlan == null && is(clutchRotMode, "Normal") && waitingForServerRotation()) return false;
        if (rotateCheck) {
            float[] aim = is(clutchRotMode, "Normal") ? bestRotation() : centerRotation();
            if (!aimingCurrent(current, expected, aiming)) return false;
            yaw = aim[0]; pitch = aim[1];
        }
        boolean leaning = air(aiming.blockPos.offset(aiming.facing));
        if (!aimingCurrent(current, expected, aiming)) return false;
        boolean nextAir = air(current.player.getPosition().offset(EnumFacing.fromAngle(initialYaw), 1).down());
        if (!aimingCurrent(current, expected, aiming)) return false;
        boolean instant = instantPredict.getValue() && leaning && nextAir;
        if (instantAlways.getValue()) instant = true;
        MovingObjectPosition mouse = client().objectMouseOver;
        if (instantMiss.getValue() && (mouse == null || mouse.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !aiming.blockPos.equals(mouse.getBlockPos()))) instant = true;
        if (instant) rotationEngine.forceSpeeds(180, 180);
        return true;
    }
    private boolean aimingCurrent(Scope current, long expected, PlaceData target) {
        return currentGeneration(current, expected) && data == target && scopeCurrent(current)
                && currentGeneration(current, expected) && data == target;
    }

    /** Prepare before fresh input; the click belongs to the accepted walking packet's POST. */
    public void onDemiseUpdate() {
        if (!activeScene()) return;
        info = mode.getValue();
        rotationEngine.updateSpeeds(rotationSettings.snapshot());
        onDemiseAngle();
    }
    private BridgePlacementCadence.Settings cadenceSettings() {
        return new BridgePlacementCadence.Settings(minCps.getValue(), maxCps.getValue(),
                BridgePlacementCadence.Profile.BASIC, BridgePlacementCadence.Interpolation.STABLE, 1);
    }
    private void place(RotationManager manager, long sequence) {
        final Scope current = scope;
        final PlaceData target = data;
        if (current == null || target == null) return;
        final NScaffoldRaycast.Plan plan = rayPlan;
        final Minecraft c = client();
        final EntityPlayerSP p = current.player;
        final ItemStack held = p.getHeldItem();
        final net.minecraft.client.multiplayer.PlayerControllerMP controller = c.playerController;
        final int slot = p.inventory.currentItem;
        final long attemptGeneration = generation;
        final boolean traced = usesRayTrace(), legit = is(placementMode, "Legit");
        final float acceptedYaw = manager.getEffectiveYaw(), acceptedPitch = manager.getEffectivePitch();
        final Vec3 reported = p.getLastReportedPosition();
        final float eyeHeight = p.getEyeHeight();
        final Vec3 wireEyes = reported.addVector(0, eyeHeight, 0);
        final double posX = p.posX, posY = p.posY, posZ = p.posZ;
        final double reach = controller.getBlockReachDistance();
        if (held == null || held.stackSize <= 0 || !DemiseScaffoldInventory.isValidBlock(held)
                || lastAttemptPlayer == p && (lastAttemptTick == p.ticksExisted || lastAttemptSequence == sequence)
                || legit && lastAttemptPlayer == p && p.ticksExisted - lastAttemptTick < 4
                || legit && (c.rightClickDelayTimer > 0 || p.isUsingItem() || controller.func_181040_m())) return;
        BooleanSupplier valid = () -> isToggled() && generation == attemptGeneration && data == target && rayPlan == plan
                && scopeCurrent(current) && controller != null && c.playerController == controller && p.inventory.currentItem == slot
                && p.getHeldItem() == held && held.stackSize > 0 && DemiseScaffoldInventory.isValidBlock(held)
                && p.posX == posX && p.posY == posY && p.posZ == posZ
                && AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT)
                && RuntimeServices.rotation() == manager && manager.getMotionSequence() == sequence
                && ROTATION_SOURCE.equals(manager.getConfirmedOwner(p, current.world, current.packets))
                && manager.canUseRotation(ROTATION_SOURCE, false) && manager.getRotationRelease() == null
                && manager.getEffectiveYaw() == acceptedYaw && manager.getEffectivePitch() == acceptedPitch
                && p.getLastReportedPosition().squareDistanceTo(reported) == 0
                && p.getEyeHeight() == eyeHeight
                && currentGeneration(current, attemptGeneration) && data == target && rayPlan == plan
                && c.playerController == controller && c.thePlayer == p && c.theWorld == current.world
                && p.inventory.currentItem == slot && p.inventory.mainInventory[slot] == held && held.stackSize > 0
                && p.posX == posX && p.posY == posY && p.posZ == posZ
                && RuntimeServices.rotation() == manager && manager.getMotionSequence() == sequence
                && current.packets.getContextVersion() == current.epoch;
        if (!valid.getAsBoolean()) return;
        MovingObjectPosition ray = traced
                ? NScaffoldRaycast.trace(current.world, wireEyes, acceptedYaw, acceptedPitch, reach, plan, valid) : null;
        if (traced && (ray == null || strictRayTrace.getValue() && !NScaffoldRaycast.interiorHit(plan, ray, .01))) return;
        final BlockPos destination = target.blockPos.offset(target.facing);
        BooleanSupplier geometry = () -> {
            if (!valid.getAsBoolean()) return false;
            if (traced) {
                MovingObjectPosition fresh = NScaffoldRaycast.trace(current.world, wireEyes, acceptedYaw, acceptedPitch, reach, plan, valid);
                return fresh != null && (!strictRayTrace.getValue() || NScaffoldRaycast.interiorHit(plan, fresh, .01))
                        && valid.getAsBoolean();
            }
            return current.world.isBlockLoaded(destination) && current.world.getWorldBorder().contains(destination)
                    && current.world.getBlockState(destination).getBlock().getMaterial().isReplaceable()
                    && !current.world.getBlockState(target.blockPos).getBlock().getMaterial().isReplaceable()
                    && valid.getAsBoolean();
        };
        if (!geometry.getAsBoolean()) return;
        BridgePlacementCadence.Settings settings = cadenceSettings();
        long now = placementClock.getAsLong();
        if (!valid.getAsBoolean()) return;
        if (cadenceScope == null) {
            cadenceScope = new BridgePlacementCadence.Scope(this, current.world, p, current.packets, current.epoch, attemptGeneration);
            if (!cadence.start(cadenceScope, settings, now)) { cadenceScope = null; return; }
        }
        if (cadence.tryConsume(cadenceScope, settings, sequence, now, true, true) == null) return;
        // Consume before C09/C08 callbacks, including controller rejection or re-entrance.
        lastAttemptPlayer = p; lastAttemptTick = p.ticksExisted; lastAttemptSequence = sequence;
        if (legit) c.rightClickDelayTimer = 4;
        Vec3 hit = ray == null ? DemiseScaffoldGeometry.faceCenter(target.blockPos, target.facing) : ray.hitVec;
        boolean success = controller.onPlayerRightClick(p, current.world, held, target.blockPos, target.facing, hit, geometry);
        if (!success || !isToggled() || generation != attemptGeneration || !scopeCurrent(current) || data != target) return;
        if (p.inventory.currentItem == slot && p.getHeldItem() == held && held.stackSize == 0)
            p.inventory.mainInventory[slot] = null;
        placed(current, attemptGeneration);
        if (isToggled() && generation == attemptGeneration && scopeCurrent(current) && data == target)
            previousBlock = destination;
    }
    private void placed(Scope current, long attemptGeneration) {
        if (!isToggled() || generation != attemptGeneration || !scopeCurrent(current)) return;
        Minecraft c = client();
        if (swing.getValue()) {
            current.player.swingItem();
            if (c.getItemRenderer() != null) c.getItemRenderer().resetEquippedProgress();
        } else current.player.sendQueue.addToSendQueue(new C0APacketAnimation());
        if (!isToggled() || generation != attemptGeneration || !scopeCurrent(current)) return;
        blocksPlacedSneak++;
        blocksPlacedJump++;
        activityPlacements++;
        status = Status.BUILDING;
    }

    /** Called before vanilla sneak scaling, after the original discrete MoveFix. */
    public void onDemiseInput(MovementInputEvent event) {
        if (!activeScene()) return;
        EntityPlayerSP p = scope.player;
        Minecraft c = client();
        switch (mode.getValue()) {
            case "Breezily":
                if (data != null && air(data.blockPos.offset(data.facing)) && moving() && movement.straight(p, c.gameSettings)) {
                    BlockPos b = new BlockPos(p.posX, p.posY - 1, p.posZ);
                    switch (p.getHorizontalFacing()) {
                        case EAST: event.setStrafe(b.getZ() + .5 > p.posZ ? 1 : -1); break;
                        case WEST: event.setStrafe(b.getZ() + .5 < p.posZ ? 1 : -1); break;
                        case SOUTH: event.setStrafe(b.getX() + .5 < p.posX ? 1 : -1); break;
                        case NORTH: event.setStrafe(b.getX() + .5 > p.posX ? 1 : -1); break;
                        default: break;
                    }
                }
                break;
            case "Telly":
                if (p.onGround && !towering() && !towerMoving() && !speedEnabled() && p.isSprinting() && moving()) event.setJump(true);
                break;
        }
        if (jump.getValue() && moving()) {
            if (blocksPlacedJump > blocksToJump.getValue().floatValue() || !p.onGround || onlyStraight.getValue() && !movement.straight(p, c.gameSettings)) blocksPlacedJump = 0;
            if (blocksPlacedJump == blocksToJump.getValue().floatValue()) event.setJump(true);
        }
        if (clutch.getValue()) {
            if (clutching && autoSneakOnClutch.getValue() && p.onGroundTicks >= groundTicksToSneak.getValue().floatValue() && moving()) sneakTime = millis.getAsLong();
            if (millis.getAsLong() - sneakTime <= 100) event.setSneak(true);
        }
        if (sneak.getValue()) {
            if (p.onGround || !onlySneakOnGround.getValue()) {
                if (blocksPlacedSneak > blocksToSneak.getValue().floatValue()) blocksPlacedSneak = 0;
                if (blocksPlacedSneak == blocksToSneak.getValue().floatValue()) event.setSneak(true);
            }
        }
        if (is(edgeSafety, "Sneak") && guardGround(p) && !event.isJump() && !physicalJump()) {
            final Scope current = scope;
            final long inputGeneration = generation;
            PlayerMoveUpdateEvent prediction = new PlayerMoveUpdateEvent(event.getStrafe(), event.getForward(),
                    1, p.rotationYaw, p.rotationPitch);
            // Use the actual consumer: own Demise axes have already been corrected; foreign owners may still correct them.
            RotationCorrections.movement(prediction);
            Vec3 next = predictedMotion(p, prediction.getForward(), prediction.getStrafe(), prediction.getYaw(), event.isSneak());
            NScaffoldEdgeGuard.Result guard = NScaffoldEdgeGuard.limit(current.world, p, next.xCoord, 0, next.zCoord,
                    () -> currentGeneration(current, inputGeneration) && scopeCurrent(current)
                            && currentGeneration(current, inputGeneration));
            if (guard.active && guard.limited) { event.setSneak(true); status = Status.EDGE_GUARD; }
        }
    }
    private boolean guardGround(EntityPlayerSP p) {
        final Scope current = scope;
        final long expected = generation;
        return !is(edgeSafety, "Off") && p.onGround && !p.noClip && !p.isRiding()
                && !p.isInWater() && !p.isInLava() && (p.capabilities == null || !p.capabilities.isFlying)
                && generation == expected && current != null && p == current.player && scopeCurrent(current)
                && currentGeneration(current, expected);
    }
    public void onDemiseMove(EventMove event) {
        if (!activeScene()) return;
        EntityPlayerSP p = scope.player;
        if (tower.isVisible() && is(tower, "Vanilla") && !p.isPotionActive(Potion.jump) && towering()) event.setY(p.motionY = .42);
        if (towerMove.isVisible() && is(towerMove, "Vanilla") && moving() && DemiseScaffoldMovement.speed(p) > .1
                && !p.isPotionActive(Potion.jump) && towerMoving()) p.motionY = .42f;
        if (guardGround(p) && event.getY() <= 0 && !physicalJump() && !p.movementInput.jump) {
            final Scope current = scope;
            final long moveGeneration = generation;
            NScaffoldEdgeGuard.Result guard = NScaffoldEdgeGuard.limit(current.world, p, event.getX(), event.getY(), event.getZ(),
                    () -> currentGeneration(current, moveGeneration) && scopeCurrent(current)
                            && currentGeneration(current, moveGeneration));
            if (guard.active && guard.limited) {
                event.setX(guard.x); event.setZ(guard.z);
                status = Status.EDGE_GUARD;
            }
        }
    }
    private void onDemiseMotion(EventMotion event) {
        if (!activeScene()) return;
        if (event.isPost()) {
            final Scope current = scope;
            final long expected = generation;
            RotationManager manager = RuntimeServices.rotation();
            if (!event.isAccepted() || manager == null || !manager.canUseRotation(ROTATION_SOURCE, false)
                    || manager.getRotationRelease() != null) return;
            long sequence = manager.getMotionSequence();
            if (lastPostManager == manager && lastPostSequence == sequence) return;
            lastPostManager = manager; lastPostSequence = sequence;
            final PlaceData target = data;
            if (target == null || placing || auraTarget() || !AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT)
                    || !aimingCurrent(current, expected, target)) return;
            if (is(mode, "Telly") && current.player.offGroundTicks < tellyTicks) { status = Status.TELLY_WAIT; publishActivity(); return; }
            placing = true;
            try { place(manager, sequence); } finally { placing = false; publishActivity(); }
            return;
        }
        if (!event.isPre()) return;
        EntityPlayerSP p = scope.player;
        switch (speedMode.getValue()) {
            case "Tick":
                if (p.onGround) {
                    if (p.ticksExisted % 15 == 0) { p.motionX *= 1.5 + Math.random() / 100D; p.motionZ *= 1.5 + Math.random() / 100D; }
                    // Retains the original operator precedence, including its tick-2 special case.
                    else if (p.ticksExisted - 2 % 15 == 0) { p.motionX *= .7; p.motionZ *= .7; }
                }
                break;
            case "Exempt": if (p.onGround) movement.strafe(p, client().gameSettings, p.movementInput.moveStrafe == 0 ? .1591f : .1565f); break;
            case "Diagonal": movement.diagonalSpeed(p, client().gameSettings); break;
        }
        if (tower.isVisible() && is(tower, "NCP") && towering()) ncpTower(p);
        if (towerMove.isVisible() && is(towerMove, "NCP") && towerMoving()) ncpTower(p);
        if (pullDown()) {
            if (p.onGround) p.motionY = .42F;
            p.motionX *= pullDownMotion.getValue().floatValue();
            p.motionZ *= pullDownMotion.getValue().floatValue();
        }
    }
    private void ncpTower(EntityPlayerSP p) {
        p.sendQueue.sendSilent(new C08PacketPlayerBlockPlacement(null));
        if (p.posY % 1 <= .00153598) { p.setPosition(p.posX, Math.floor(p.posY), p.posZ); p.motionY = .42F; }
        else if (p.posY % 1 < .1 && p.offGroundTicks != 0) { p.motionY = 0; p.setPosition(p.posX, Math.floor(p.posY), p.posZ); }
    }
    private boolean pullDown() {
        return towerMove.isVisible() && towerMoving() && is(towerMove, "PullDown") || tower.isVisible() && towering() && is(tower, "PullDown");
    }
    public boolean towering() { return physicalJump() && !moving(); }
    public boolean towerMoving() { return physicalJump() && moving(); }
    private boolean physicalJump() { return jumpKey.getAsBoolean(); }
    private static boolean physicalJumpDown() {
        Minecraft c = client();
        return c != null && c.gameSettings != null && c.gameSettings.keyBindJump != null && Keyboard.isCreated()
                && c.gameSettings.keyBindJump.getKeyCode() >= 0 && Keyboard.isKeyDown(c.gameSettings.keyBindJump.getKeyCode());
    }
    public static boolean safeWalk(Entity entity) {
        if (!(entity instanceof EntityPlayerSP)) return false;
        NScaffold module = instance();
        EntityPlayerSP p = (EntityPlayerSP) entity;
        return module != null && module.isToggled() && module.scopeCurrent(module.scope) && entity == module.scope.player
                && p.onGround && p.motionY <= 0 && !module.physicalJump() && (p.movementInput == null || !p.movementInput.jump)
                && (module.safewalk.getValue() || module.safewalkNoData.getValue() && module.data == null);
    }
    public static boolean silentSprint(EntityPlayerSP player) {
        NScaffold module = instance();
        return module != null && module.isToggled() && module.scopeCurrent(module.scope) && player == module.scope.player && is(module.sprintMode, "Silent");
    }
    /** Original Sprint.onUpdate leaves sprint input to an enabled Scaffold. */
    public static boolean ownsSprintInput() {
        NScaffold module = instance();
        return module != null && module.isToggled();
    }
    /** Called during shared preparation even after disable, until Demise finishes its return. */
    public void requestDemiseRotation() {
        final Scope current = scope;
        final DemiseRotationEngine.Aim aim = rotationEngine.currentAim();
        final RotationManager manager = RuntimeServices.rotation();
        if (aim == null || manager == null || !scopeCurrent(current)) return;
        final long sequence = manager.getMotionSequence();
        manager.request(ROTATION_SOURCE, ROTATION_PRIORITY, aim.yaw, aim.pitch,
                () -> scopeCurrent(current) && RuntimeServices.rotation() == manager
                        && manager.getMotionSequence() == sequence && rotationEngine.shouldRotate());
    }
    public boolean correctionEnabled() { return rotationEngine.shouldRotate() && scopeCurrent(scope); }
    public MoveFixController.Mode moveFixMode() {
        return rotationEngine.correction() == DemiseRotationEngine.Correction.None
                ? MoveFixController.Mode.OFF : MoveFixController.Mode.ROTATION_BOUND;
    }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.DIRECT; }
    /** Cosmetic selection only; vanilla placement uses the actual selected stack. */
    public static int displayedSlot(EntityPlayer player) {
        NScaffold module = instance();
        return module != null && module.spoofing && module.isToggled() && module.scopeCurrent(module.scope) && player == module.scope.player
                ? module.oldSlot : player.inventory.currentItem;
    }
    public static ItemStack displayedStack(EntityPlayer player) { return player.inventory.getStackInSlot(displayedSlot(player)); }

    /** Maps Demise's ShaderEvent to the host's global blur/glow settings. */
    public static void renderTargetShaders() {
        NScaffold module = instance();
        if (module == null || !module.isToggled() || !module.targetEsp.getValue()
                || !module.scopeCurrent(module.scope) || module.data == null) return;
        BlockPos target = module.data.blockPos.offset(module.data.facing);
        if (me.youngjibbit.utils.render.DemiseScaffoldEffects.render(target)) module.shaderCalled = true;
    }

    @Subscribe private final Listener<EventMotion> motion = new Listener<>(this::onDemiseMotion);
    @Subscribe private final Listener<EventPacket> packets = new Listener<>(event -> {
        if (event.isPre() && event.isOutbound() && activeScene() && pullDown()
                && scope.player.motionY > -.0784000015258789 && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
            EntityPlayerSP p = scope.player;
            if (packet.getPosition().equals(new BlockPos(p.posX, p.posY - 1.4, p.posZ))) p.motionY = -.0784000015258789;
        }
        if (event.isPost() && event.isInbound() && (event.getPacket() instanceof S2FPacketSetSlot || event.getPacket() instanceof S30PacketWindowItems
                || event.getPacket() instanceof S2DPacketOpenWindow || event.getPacket() instanceof S2EPacketCloseWindow)) activityView = null;
    });
    @Subscribe private final Listener<WorldEvent> worldChanged = new Listener<>(event -> { setToggled(false); clearScene(); });
    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> { if (scope != null && !scopeCurrent(scope)) clearScene(); });
    @Subscribe private final Listener<Event3D> render = new Listener<>(event -> {
        if (targetEsp.getValue() && !shaderCalled && activeScene() && data != null)
            me.youngjibbit.utils.render.DemiseScaffoldOverlay.render(data.blockPos.offset(data.facing));
        shaderCalled = false;
    });

    private static final class Scope {
        final Minecraft client;
        final EntityPlayerSP player;
        final WorldClient world;
        final PacketPipeline packets;
        final RuntimeObservation observation;
        final SceneContext scene;
        final long epoch;
        Scope(Minecraft c, PacketPipeline packets, RuntimeObservation observation, SceneContext scene) {
            client = c; player = c.thePlayer; world = c.theWorld; this.packets = packets;
            this.observation = observation; this.scene = scene; epoch = packets.getContextVersion();
        }
    }
    private static boolean basic(Minecraft c) {
        return c != null && c.thePlayer != null && c.theWorld != null && c.thePlayer.worldObj == c.theWorld
                && c.gameSettings != null && c.playerController != null && c.thePlayer.inventory != null
                && !c.thePlayer.isDead && c.thePlayer.isEntityAlive() && !c.thePlayer.isSpectator()
                && c.thePlayer.sendQueue != null && c.thePlayer.sendQueue.getNetworkManager() != null
                && c.thePlayer.sendQueue.getNetworkManager().isChannelOpen();
    }
    private void bindScene() {
        Minecraft c = client();
        if (!basic(c)) return;
        RuntimeObservation observation = RuntimeServices.observation();
        PacketPipeline packets = c.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        if (observation == null || packets == null || !packets.isActive()) return;
        SceneContext scene = observation.captureScene();
        if (!scene.isValid() || scene.isSuspended()) return;
        scope = new Scope(c, packets, observation, scene);
        oldSlot = c.thePlayer.inventory.currentItem;
        onGroundY = c.thePlayer.getEntityBoundingBox().minY;
    }
    private boolean activeScene() {
        if (!isToggled() || instance() != this) return false;
        if (scope == null) bindScene();
        return scopeCurrent(scope);
    }
    private boolean scopeCurrent(Scope current) {
        Minecraft c = client();
        final long expected = generation;
        if (current == null || scope != current || instance() != this || !basic(c)
                || c != current.client || c.thePlayer != current.player || c.theWorld != current.world
                || RuntimeServices.observation() != current.observation || !current.packets.isActive()
                || current.packets.getContextVersion() != current.epoch
                || current.player.sendQueue.getNetworkManager().getClientPackets() != current.packets) return false;
        SceneContext fresh = current.observation.captureScene();
        return fresh.isValid() && !fresh.isSuspended() && current.scene.sameSceneAs(fresh)
                && generation == expected && scope == current && instance() == this && client() == c
                && c.thePlayer == current.player && c.theWorld == current.world
                && RuntimeServices.observation() == current.observation
                && current.packets.isActive() && current.packets.getContextVersion() == current.epoch
                && current.player.sendQueue != null && current.player.sendQueue.getNetworkManager() != null
                && current.player.sendQueue.getNetworkManager().getClientPackets() == current.packets;
    }
    /** Pure final barrier after callbacks; ordinary disable still keeps the separate rotation tail. */
    private boolean currentGeneration(Scope current, long expected) {
        return isToggled() && generation == expected && scope == current && instance() == this;
    }
    private void clearScene() {
        Minecraft c = client();
        if (scope != null && spoofing && c != null && c.thePlayer == scope.player && c.theWorld == scope.world)
            scope.player.inventory.currentItem = oldSlot;
        generation++;
        scope = null;
        resetPlacementPlan();
        lastAttemptPlayer = null; lastPostManager = null;
        lastAttemptTick = Integer.MIN_VALUE; lastAttemptSequence = lastPostSequence = -1;
        targetBlock = previousBlock = null;
        spoofing = clutching = startClutch = false;
        activityView = null;
        rotationEngine.clear(c != null && c.thePlayer != null ? c.thePlayer.rotationYaw : 0,
                c != null && c.thePlayer != null ? c.thePlayer.rotationPitch : 0);
        status = isToggled() ? Status.WAITING : Status.OFF;
    }
    private static final class ActivityView {
        final ScaffoldActivitySnapshot snapshot;
        final ActivityInventoryContext inventory;
        final RuntimeObservation observation;
        final long generation, connection, suspension;
        ActivityView(ScaffoldActivitySnapshot snapshot, ActivityInventoryContext inventory, RuntimeObservation observation,
                     long generation, long connection, long suspension) {
            this.snapshot = snapshot; this.inventory = inventory; this.observation = observation;
            this.generation = generation; this.connection = connection; this.suspension = suspension;
        }
    }
    /** Render consumers read immutable facts and never run a planner or inventory scan. */
    public ScaffoldActivitySnapshot getActivitySnapshot() {
        ActivityView view = activityView;
        Minecraft c = client();
        if (view == null || !isToggled() || instance() != this || generation != view.generation
                || !basic(c) || c.currentScreen != null || !c.inGameHasFocus || c.isGamePaused()
                || c.getRenderViewEntity() != c.thePlayer || RuntimeServices.observation() != view.observation
                || view.observation.getObservedConnectionGeneration() != view.connection
                || view.observation.getObservedSuspensionRevision() != view.suspension
                || !view.snapshot.isFresh(activityClock.getAsLong()) || !view.inventory.isCurrent(c)) return null;
        return view == activityView && isToggled() && generation == view.generation
                && RuntimeServices.observation() == view.observation
                && view.observation.getObservedConnectionGeneration() == view.connection
                && view.observation.getObservedSuspensionRevision() == view.suspension ? view.snapshot : null;
    }
    private void publishActivity() {
        if (!isToggled() || !scopeCurrent(scope)) return;
        Scope current = scope;
        EntityPlayerSP p = current.player;
        if (p.inventoryContainer == null || p.openContainer != p.inventoryContainer) { activityView = null; return; }
        int slot = DemiseScaffoldInventory.blockSlot(p), count = DemiseScaffoldInventory.blockCount(p);
        ItemStack stack = slot < 0 || slot > 8 ? null : p.inventory.getStackInSlot(slot);
        int id = stack == null ? -1 : Item.getIdFromItem(stack.getItem());
        int metadata = stack == null ? -1 : stack.getMetadata();
        ActivityInventoryContext inventory = ActivityInventoryContext.capture(current.client, 36 + slot);
        if (inventory == null || !scopeCurrent(current)) return;
        ScaffoldActivitySnapshot.Status visible = status == Status.BUILDING ? ScaffoldActivitySnapshot.Status.PLACING
                : status == Status.EDGE_GUARD ? ScaffoldActivitySnapshot.Status.EDGE_GUARD
                : status == Status.SEARCHING ? ScaffoldActivitySnapshot.Status.SEARCHING
                : status == Status.ALIGNING || status == Status.CLUTCHING ? ScaffoldActivitySnapshot.Status.ALIGNING
                : status == Status.PAUSED_AURA ? ScaffoldActivitySnapshot.Status.PAUSED : ScaffoldActivitySnapshot.Status.WAITING;
        String profile = mode.getValue() + " / " + rotations.getValue() + " / " + placementMode.getValue();
        if (activityView == null || activityView.snapshot.getPlacementCount() != activityPlacements
                || !activityView.snapshot.hasSameFacts(id, metadata, count, "NScaffold", profile, visible)) activitySequence++;
        activityView = new ActivityView(new ScaffoldActivitySnapshot(activitySequence, activityClock.getAsLong(),
                id, metadata, count, "NScaffold", profile, visible, activityPlacements), inventory, current.observation,
                generation, current.observation.getObservedConnectionGeneration(), current.observation.getObservedSuspensionRevision());
    }
}
