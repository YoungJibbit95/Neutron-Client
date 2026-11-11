package me.youngjibbit.module.impl.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.MoveFixController;
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
 * Original event order, settings, arithmetic and state transitions are retained.
 * Host adapters supply Java 8 values/events, scene lifetime, guarded controller transport,
 * cosmetic slot consumers and immutable HUD facts. See docs/NSCAFFOLD-DEMISE-PARITY.md.
 */
@ModuleInfo(name="NScaffold", description="Demise Scaffold: original rotations, movement and bridge behaviour.", category=Category.World)
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
    private boolean isOnRightSide, clutching, startClutch, spoofing, shaderCalled;
    private float yaw, pitch, initialYaw, initialPitch;
    private final DemiseScaffoldMovement movement = new DemiseScaffoldMovement();
    private final LongSupplier millis = System::currentTimeMillis;
    private final BooleanSupplier jumpKey = NScaffold::physicalJumpDown;
    private long clutchTime = millis.getAsLong(), sneakTime = millis.getAsLong();
    private final LongSupplier activityClock = System::nanoTime;
    private long generation, activitySequence, activityPlacements;
    private Scope scope;
    private ActivityView activityView;
    private boolean placing;
    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, BUILDING, PAUSED_AURA, TELLY_WAIT, CLUTCHING }
    private Status status = Status.OFF;

    public NScaffold() {
        straightTellyTicks.setVisibility(m -> is(mode, "Telly"));
        diagonalTellyTicks.setVisibility(m -> is(mode, "Telly"));
        rotations.setVisibility(m -> !is(mode, "Breezily"));
        staticify.setVisibility(m -> rotations.isVisible() && !is(rotations, "Normal") && !is(rotations, "Center"));
        for (Value<?> value : new Value<?>[]{clutchCriteria, clutchRotMode, instantAlways, instantMiss, instantPredict, autoSneakOnClutch})
            value.setVisibility(m -> clutch.getValue());
        groundTicksToSneak.setVisibility(m -> clutch.getValue() && autoSneakOnClutch.getValue());
        strictRayTrace.setVisibility(m -> rayTrace.getValue());
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
        activityPlacements = 0;
        activityView = null;
        status = Status.WAITING;
        bindScene();
        DemiseRotationHooks.bind();
        super.onEnable();
    }
    @Override public void onDisable() {
        Minecraft c = client();
        if (scope != null && c != null && c.thePlayer == scope.player && c.theWorld == scope.world)
            scope.player.inventory.currentItem = oldSlot;
        spoofing = false;
        blocksPlacedJump = blocksPlacedSneak = 0;
        if (c != null && c.gameSettings != null && c.gameSettings.keyBindUseItem != null)
            KeyBinding.setKeyBindState(c.gameSettings.keyBindUseItem.getKeyCode(), false);
        activityView = null;
        status = Status.OFF;
        // Mouse UPDATE owns the original 150-ms expiry and subsequent return to the camera.
        super.onDisable();
    }
    @Override public void onValueChange() { activityView = null; }
    public Status getStatus() { return status; }

    /** AngleEvent precedes the physical mouse delta in Entity.setAngles. */
    public void onDemiseAngle() {
        if (!activeScene()) return;
        final Scope current = scope;
        final Minecraft c = client();
        final EntityPlayerSP p = current.player;
        p.inventory.currentItem = DemiseScaffoldInventory.blockSlot(p);
        spoofing = true;
        data = null;
        if (p.onGround) onGroundY = p.getEntityBoundingBox().minY;
        double posY = p.getEntityBoundingBox().minY;
        if (keepY.getValue() || speedKeepY.getValue() && speedEnabled() && !c.gameSettings.keyBindJump.isKeyDown()) posY = onGroundY;
        if (towerMoving() || towering()) onGroundY = posY = p.getEntityBoundingBox().minY;
        targetBlock = new BlockPos(p.posX, posY - 1, p.posZ);
        data = DemiseScaffoldGeometry.findBlock(current.world, targetBlock, p.getPosition(), previousBlock);
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
        // Missing support is an original null dereference, not an alternate aiming mode.
        if (data == null) { status = Status.SEARCHING; publishActivity(); return; }
        if (c.entityRenderer != null) c.entityRenderer.getMouseOver(1);
        MovingObjectPosition mouse = c.objectMouseOver;
        boolean rotateCheck = mouse == null || mouse.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !data.blockPos.offset(data.facing).equals(mouse.getBlockPos());
        if (rotateCheck) {
            switch (mode.getValue()) {
                case "Normal": case "Telly":
                    switch (rotations.getValue()) {
                        case "Normal":
                            if (waitingForServerRotation()) return;
                            initialYaw = bestRotation()[0]; initialPitch = bestRotation()[1]; break;
                        case "Center":
                            Vec3 hitVec = DemiseScaffoldGeometry.faceCenter(data.blockPos, data.facing);
                            initialYaw = DemiseScaffoldGeometry.rotations(p, hitVec)[0];
                            initialPitch = DemiseScaffoldGeometry.rotations(p, hitVec)[1]; break;
                        case "GodBridge":
                            if (!staticify.getValue() && waitingForServerRotation()) return;
                            float movingYaw = moving() ? movement.keyYaw(p, c.gameSettings) - 180 : p.rotationYaw - 180;
                            if (p.onGround) isOnRightSide = Math.floor(p.posX + Math.cos(Math.toRadians(movingYaw)) * .5) != Math.floor(p.posX)
                                    || Math.floor(p.posZ + Math.sin(Math.toRadians(movingYaw)) * .5) != Math.floor(p.posZ);
                            float bridgeYaw = movement.straight(p, c.gameSettings) ? movingYaw + (isOnRightSide ? 45 : -45) : movingYaw;
                            initialYaw = Math.round(bridgeYaw / 45) * 45;
                            initialPitch = staticify.getValue() ? moving() ? 75.7f : 90f : bestRotation()[1]; break;
                        case "Reverse":
                            if (!staticify.getValue() && waitingForServerRotation()) return;
                            initialYaw = movement.keyYaw(p, c.gameSettings) - 180; initialPitch = staticify.getValue() ? 80 : bestRotation()[1]; break;
                    }
                    break;
                case "Breezily":
                    initialYaw = Math.round((movement.keyYaw(p, c.gameSettings) - 180) / 45) * 45;
                    initialPitch = moving() ? movement.straight(p, c.gameSettings) ? 80 : 75.7f : 90; break;
            }
        }
        float[] rotation = {initialYaw, initialPitch};
        if (clutch.getValue() && air(new BlockPos(p.getPositionVector().subtract(0, 2, 0)))) {
            switch (clutchCriteria.getValue()) {
                case "MouseOver":
                    MovingObjectPosition ray = DemiseScaffoldGeometry.rayTrace(p, rotation, 4.5, 1);
                    if (ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !moving()
                            || ray.getBlockPos() != null && ray.getBlockPos().distanceSq(data.blockPos) > 1) {
                        if (!setClutchRot(rotateCheck)) return;
                        clutching = true;
                    } else clutching = false;
                    break;
                case "Prediction":
                    boolean nextAir = air(p.getPosition().offset(EnumFacing.fromAngle(initialYaw), 1).down());
                    boolean leaning = air(data.blockPos.offset(data.facing));
                    if (leaning && nextAir || !moving()) startClutch = true;
                    else if (startClutch) { clutchTime = millis.getAsLong(); startClutch = false; }
                    clutching = startClutch || millis.getAsLong() - clutchTime <= 200;
                    if (clutching && !setClutchRot(rotateCheck)) return;
                    break;
            }
        } else clutching = false;
        if (!scopeCurrent(current)) return;
        if (!is(mode, "Telly") || p.offGroundTicks >= tellyTicks) {
            rotationEngine.setRotation(new float[]{clutching ? yaw : initialYaw, clutching ? pitch : initialPitch}, rotationSettings.snapshot());
            status = clutching ? Status.CLUTCHING : Status.ALIGNING;
        } else status = Status.TELLY_WAIT;
        publishActivity();
    }
    private void setSprintKey(boolean pressed) { KeyBinding.setKeyBindState(client().gameSettings.keyBindSprint.getKeyCode(), pressed); }
    private boolean moving() { return scope != null && DemiseScaffoldMovement.moving(scope.player); }
    public float demiseMovementYaw() { return movement.keyYaw(client().thePlayer, client().gameSettings); }
    private boolean air(BlockPos pos) { return scope.world.getBlockState(pos).getBlock() instanceof BlockAir; }
    /** Fresh scenes wait for factual C03 angles where the original search requires them. */
    private boolean waitingForServerRotation() {
        if (rotationEngine.hasServerRotation()) return false;
        data = null;
        status = Status.WAITING;
        publishActivity();
        return true;
    }
    private float[] bestRotation() { return DemiseScaffoldGeometry.bestRotation(scope.player, data, rotationEngine.serverRotation()); }
    private boolean setClutchRot(boolean rotateCheck) {
        if (rotateCheck && is(clutchRotMode, "Normal") && waitingForServerRotation()) return false;
        Vec3 hitVec = DemiseScaffoldGeometry.faceCenter(data.blockPos, data.facing);
        if (rotateCheck) {
            if (is(clutchRotMode, "Normal")) { yaw = bestRotation()[0]; pitch = bestRotation()[1]; }
            else { yaw = DemiseScaffoldGeometry.rotations(scope.player, hitVec)[0]; pitch = DemiseScaffoldGeometry.rotations(scope.player, hitVec)[1]; }
        }
        boolean leaning = air(data.blockPos.offset(data.facing));
        boolean nextAir = air(scope.player.getPosition().offset(EnumFacing.fromAngle(initialYaw), 1).down());
        boolean instant = instantPredict.getValue() && leaning && nextAir;
        if (instantAlways.getValue()) instant = true;
        MovingObjectPosition mouse = client().objectMouseOver;
        if (instantMiss.getValue() && (mouse == null || mouse.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !data.blockPos.equals(mouse.getBlockPos()))) instant = true;
        if (instant) rotationEngine.forceSpeeds(180, 180);
        return true;
    }

    /** UpdateEvent is at the very start of EntityPlayerSP.onLivingUpdate, before new input. */
    public void onDemiseUpdate() {
        if (!activeScene()) return;
        info = mode.getValue();
        rotationEngine.updateSpeeds(rotationSettings.snapshot());
        if (data == null) { status = Status.SEARCHING; publishActivity(); return; }
        if (is(mode, "Telly") && scope.player.offGroundTicks < tellyTicks) { status = Status.TELLY_WAIT; publishActivity(); return; }
        if (placing || !AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT)) return;
        placing = true;
        try { place(); } finally { placing = false; publishActivity(); }
    }
    private void place() {
        final Scope current = scope;
        final PlaceData target = data;
        final Minecraft c = client();
        final EntityPlayerSP p = current.player;
        final ItemStack held = p.getHeldItem();
        final net.minecraft.client.multiplayer.PlayerControllerMP controller = c.playerController;
        final int slot = p.inventory.currentItem;
        final long attemptGeneration = generation;
        BooleanSupplier valid = () -> isToggled() && generation == attemptGeneration && data == target
                && scopeCurrent(current) && controller != null && c.playerController == controller && p.inventory.currentItem == slot
                && p.getHeldItem() == held && AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT);
        if (!valid.getAsBoolean()) return;
        if (!rayTrace.getValue()) {
            if (controller.onPlayerRightClick(p, current.world, held, target.blockPos, target.facing,
                    DemiseScaffoldGeometry.faceCenter(target.blockPos, target.facing), valid)) placed(current, attemptGeneration);
            if (scopeCurrent(current) && data == target) previousBlock = target.blockPos.offset(target.facing);
        } else {
            MovingObjectPosition ray = DemiseScaffoldGeometry.rayTrace(p, rotationEngine.rotation(), 4.5, 1);
            if (target.blockPos.equals(ray.getBlockPos()) && (!strictRayTrace.getValue() || ray.sideHit == target.facing))
                if (controller.onPlayerRightClick(p, current.world, held, ray.getBlockPos(), ray.sideHit, ray.hitVec, valid)) placed(current, attemptGeneration);
            if (scopeCurrent(current) && data == target) previousBlock = ray.getBlockPos().offset(ray.sideHit);
        }
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
            if (!p.onGround && onlySneakOnGround.getValue()) return;
            if (blocksPlacedSneak > blocksToSneak.getValue().floatValue()) blocksPlacedSneak = 0;
            if (blocksPlacedSneak == blocksToSneak.getValue().floatValue()) event.setSneak(true);
        }
    }
    public void onDemiseMove(EventMove event) {
        if (!activeScene()) return;
        EntityPlayerSP p = scope.player;
        if (tower.isVisible() && is(tower, "Vanilla") && !p.isPotionActive(Potion.jump) && towering()) event.setY(p.motionY = .42);
        if (towerMove.isVisible() && is(towerMove, "Vanilla") && moving() && DemiseScaffoldMovement.speed(p) > .1
                && !p.isPotionActive(Potion.jump) && towerMoving()) p.motionY = .42f;
    }
    private void onDemiseMotion(EventMotion event) {
        if (!event.isPre() || !activeScene()) return;
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
        return module != null && module.isToggled() && module.scopeCurrent(module.scope) && entity == module.scope.player
                && (module.safewalk.getValue() && entity.onGround || module.safewalkNoData.getValue() && module.data == null);
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
        if (current == null || scope != current || instance() != this || !basic(c)
                || c != current.client || c.thePlayer != current.player || c.theWorld != current.world
                || RuntimeServices.observation() != current.observation || !current.packets.isActive()
                || current.packets.getContextVersion() != current.epoch
                || current.player.sendQueue.getNetworkManager().getClientPackets() != current.packets) return false;
        SceneContext fresh = current.observation.captureScene();
        return fresh.isValid() && !fresh.isSuspended() && current.scene.sameSceneAs(fresh);
    }
    private void clearScene() {
        Minecraft c = client();
        if (scope != null && spoofing && c != null && c.thePlayer == scope.player && c.theWorld == scope.world)
            scope.player.inventory.currentItem = oldSlot;
        generation++;
        scope = null;
        data = null;
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
                : status == Status.SEARCHING ? ScaffoldActivitySnapshot.Status.SEARCHING
                : status == Status.ALIGNING || status == Status.CLUTCHING ? ScaffoldActivitySnapshot.Status.ALIGNING
                : status == Status.PAUSED_AURA ? ScaffoldActivitySnapshot.Status.PAUSED : ScaffoldActivitySnapshot.Status.WAITING;
        String profile = mode.getValue() + " / " + rotations.getValue();
        if (activityView == null || activityView.snapshot.getPlacementCount() != activityPlacements
                || !activityView.snapshot.hasSameFacts(id, metadata, count, "NScaffold", profile, visible)) activitySequence++;
        activityView = new ActivityView(new ScaffoldActivitySnapshot(activitySequence, activityClock.getAsLong(),
                id, metadata, count, "NScaffold", profile, visible, activityPlacements), inventory, current.observation,
                generation, current.observation.getObservedConnectionGeneration(), current.observation.getObservedSuspensionRevision());
    }
}
