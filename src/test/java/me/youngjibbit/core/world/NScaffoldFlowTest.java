package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventMove;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.movement.Sprint;
import me.youngjibbit.module.impl.world.NScaffold;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * Real NScaffold, Demise frame/input hooks, vanilla controller, ItemBlock and accepted transport.
 * Only world storage/collisions, physical key state and time are supplied by the fixture.
 * Each case gets a fresh JVM because the existing client fixture installs Module.mc with Unsafe.
 */
public class NScaffoldFlowTest {
    private static void isolated(String method) throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    @Test public void mouseFrameKeepsTheCameraFreeAndPublishesVisibleModelRotation() throws Exception {
        isolated("mouseFrameKeepsTheCameraFreeAndPublishesVisibleModelRotation");
    }
    @Test public void angleSelectsTheOriginalLastSlotWhileCosmeticConsumersKeepTheOldSlot() throws Exception {
        isolated("angleSelectsTheOriginalLastSlotWhileCosmeticConsumersKeepTheOldSlot");
    }
    @Test public void updatePreparesThenAcceptedWalkingPlacesAfterInputExactlyOnce() throws Exception {
        isolated("updatePreparesThenAcceptedWalkingPlacesAfterInputExactlyOnce");
    }
    @Test public void realRayMissesAndWrongFacesNeverPlaceOrChangeSuccessfulHistory() throws Exception {
        isolated("realRayMissesAndWrongFacesNeverPlaceOrChangeSuccessfulHistory");
    }
    @Test public void realRayUsesReportedPositionAndAcceptedWireAngles() throws Exception {
        isolated("realRayUsesReportedPositionAndAcceptedWireAngles");
    }
    @Test public void lowCpsDoesNotCatchUpOrDependOnRenderFrameCount() throws Exception {
        isolated("lowCpsDoesNotCatchUpOrDependOnRenderFrameCount");
    }
    @Test public void onePhysicalTickCannotSpendTwoAttemptsAcrossWalkingSequences() throws Exception {
        isolated("onePhysicalTickCannotSpendTwoAttemptsAcrossWalkingSequences");
    }
    @Test public void legitModeRequiresARealRayAndNativeFourTickDelay() throws Exception {
        isolated("legitModeRequiresARealRayAndNativeFourTickDelay");
    }
    @Test public void rejectedC03AndForeignOwnerCannotAuthorizePlacement() throws Exception {
        isolated("rejectedC03AndForeignOwnerCannotAuthorizePlacement");
    }
    @Test public void rejectedControllerAttemptConsumesCadenceWithoutInventingHistory() throws Exception {
        isolated("rejectedControllerAttemptConsumesCadenceWithoutInventingHistory");
    }
    @Test public void edgeSafetyHoldsOnlyUnsupportedMotionAndReleasesAfterPlacement() throws Exception {
        isolated("edgeSafetyHoldsOnlyUnsupportedMotionAndReleasesAfterPlacement");
    }
    @Test public void movingBridgeStaysSupportedAcrossRealRaysAndFrameSequences() throws Exception {
        isolated("movingBridgeStaysSupportedAcrossRealRaysAndFrameSequences");
    }
    @Test public void movingLegitBridgeStaysSupportedAcrossRealRaysAndFrameSequences() throws Exception {
        isolated("movingLegitBridgeStaysSupportedAcrossRealRaysAndFrameSequences");
    }
    @Test public void diagonalBridgeAdvancesAcrossRealRaysAndFrameSequences() throws Exception {
        isolated("diagonalBridgeAdvancesAcrossRealRaysAndFrameSequences");
    }
    @Test public void diagonalLegitBridgeAdvancesAcrossRealRaysAndFrameSequences() throws Exception {
        isolated("diagonalLegitBridgeAdvancesAcrossRealRaysAndFrameSequences");
    }
    @Test public void stationaryJumpTowerKeepsItsAimBetweenRealPlacements() throws Exception {
        isolated("stationaryJumpTowerKeepsItsAimBetweenRealPlacements");
    }
    @Test public void retainedRotationHonorsAuraTellyDisableAndEpoch() throws Exception {
        isolated("retainedRotationHonorsAuraTellyDisableAndEpoch");
    }
    @Test public void supportCallbackCannotRenewRotationAfterAuraAcquiresTarget() throws Exception {
        isolated("supportCallbackCannotRenewRotationAfterAuraAcquiresTarget");
    }
    @Test public void relativeFramesReachARealSupportWithinThreeSecondsWithoutMovingTheCamera() throws Exception {
        isolated("relativeFramesReachARealSupportWithinThreeSecondsWithoutMovingTheCamera");
    }
    @Test public void acceleratedFramesEscapeARealSupportMissWithinTwoSeconds() throws Exception {
        isolated("acceleratedFramesEscapeARealSupportMissWithinTwoSeconds");
    }
    @Test public void reenableDuringSceneOrAdmissionCaptureRejectsThePreviousGeneration() throws Exception {
        isolated("reenableDuringSceneOrAdmissionCaptureRejectsThePreviousGeneration");
    }
    @Test public void tellyUsesOriginalGroundCountersAndSeparateStraightAndDiagonalWindows() throws Exception {
        isolated("tellyUsesOriginalGroundCountersAndSeparateStraightAndDiagonalWindows");
    }
    @Test public void sprintModuleRespectsAllOriginalScaffoldSprintChoices() throws Exception {
        isolated("sprintModuleRespectsAllOriginalScaffoldSprintChoices");
    }
    @Test public void towerModesRetainOriginalMotionAndPacketRules() throws Exception {
        isolated("towerModesRetainOriginalMotionAndPacketRules");
    }
    @Test public void speedModesRetainOriginalArithmeticAndPhysicalKeyConditions() throws Exception {
        isolated("speedModesRetainOriginalArithmeticAndPhysicalKeyConditions");
    }
    @Test public void clutchRetainsTheOriginalHundredAndTwoHundredMillisecondWindows() throws Exception {
        isolated("clutchRetainsTheOriginalHundredAndTwoHundredMillisecondWindows");
    }
    @Test public void successfulPlacementsDriveJumpSneakCountersAndOnlyOneSneakScale() throws Exception {
        isolated("successfulPlacementsDriveJumpSneakCountersAndOnlyOneSneakScale");
    }
    @Test public void zeroThresholdsKeepTheOriginalAirborneComparisonOrder() throws Exception {
        isolated("zeroThresholdsKeepTheOriginalAirborneComparisonOrder");
    }
    @Test public void disableAndReenableRestoreThenRecaptureTheOriginalSlot() throws Exception {
        isolated("disableAndReenableRestoreThenRecaptureTheOriginalSlot");
    }
    @Test public void worldChangeDisablesUnconditionallyAndClearsTheFrameLifetime() throws Exception {
        isolated("worldChangeDisablesUnconditionallyAndClearsTheFrameLifetime");
    }
    @Test public void reenableDuringSlotTransportRejectsThePreviousPlacementGeneration() throws Exception {
        isolated("reenableDuringSlotTransportRejectsThePreviousPlacementGeneration");
    }
    @Test public void menuOpeningDuringPlacementTransportRejectsTheAction() throws Exception {
        isolated("menuOpeningDuringPlacementTransportRejectsTheAction");
    }
    @Test public void epochChangeDuringPlacementTransportRejectsTheAction() throws Exception {
        isolated("epochChangeDuringPlacementTransportRejectsTheAction");
    }
    @Test public void controllerReplacementDuringTransportRejectsTheOldController() throws Exception {
        isolated("controllerReplacementDuringTransportRejectsTheOldController");
    }
    @Test public void animationReenableCannotCreditAnOldPlacementToTheNewRun() throws Exception {
        isolated("animationReenableCannotCreditAnOldPlacementToTheNewRun");
    }
    @Test public void activityFactsAreReadOnlyExpireAndRefreshWithoutInventingPlacements() throws Exception {
        isolated("activityFactsAreReadOnlyExpireAndRefreshWithoutInventingPlacements");
    }
    @Test public void activityCannotReviveAfterAMenuRoundTripOrConnectionEpoch() throws Exception {
        isolated("activityCannotReviveAfterAMenuRoundTripOrConnectionEpoch");
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static int counter(NScaffold module, String name) throws Exception {
        return field(NScaffold.class, name).getInt(module);
    }

    /** Map-backed world storage with the actual World voxel traversal and block ray intersection. */
    public static class Scene extends WorldClient {
        Map<BlockPos,IBlockState> blocks;
        WorldBorder border;
        AtomicLong motionTime;
        int blockReads, rayReads;
        boolean realPlacementConditions;
        Vec3 lastRayFrom, lastRayTo;
        public Scene() { super(null, null, 0, null, null); }
        @Override public IBlockState getBlockState(BlockPos position) {
            blockReads++;
            return blocks.getOrDefault(position, Blocks.air.getDefaultState());
        }
        @Override public boolean isBlockLoaded(BlockPos position) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public Entity getEntityByID(int id) {
            for (Entity entity : loadedEntityList) if (entity.getEntityId() == id) return entity;
            return null;
        }
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity excluded, AxisAlignedBB area,
                com.google.common.base.Predicate<? super Entity> predicate) {
            List<Entity> found = new ArrayList<>();
            for (Entity entity : loadedEntityList)
                if (entity != excluded && entity.getEntityBoundingBox().intersectsWith(area)
                        && (predicate == null || predicate.apply(entity))) found.add(entity);
            return found;
        }
        @Override public boolean canBlockBePlaced(Block block, BlockPos position, boolean ignore, EnumFacing side,
                                                  Entity entity, ItemStack stack) {
            if (realPlacementConditions) return super.canBlockBePlaced(block, position, ignore, side, entity, stack);
            return getBlockState(position).getBlock().getMaterial().isReplaceable();
        }
        @Override public boolean setBlockState(BlockPos position, IBlockState state, int flags) {
            blocks.put(position, state); return true;
        }
        @Override public void playSoundEffect(double x, double y, double z, String sound, float volume, float pitch) { }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to, boolean liquid, boolean empty, boolean last) {
            rayReads++;
            lastRayFrom = from; lastRayTo = to;
            return super.rayTraceBlocks(from, to, liquid, empty, last);
        }
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity, AxisAlignedBB area) {
            List<AxisAlignedBB> collisions = new ArrayList<>();
            for (Map.Entry<BlockPos,IBlockState> cell : blocks.entrySet())
                cell.getValue().getBlock().addCollisionBoxesToList(this, cell.getKey(), cell.getValue(), area, collisions, entity);
            return collisions;
        }
        @Override public boolean isAreaLoaded(BlockPos from, BlockPos to, boolean allowEmpty) { return true; }
        @Override public boolean canLightningStrike(BlockPos pos) { return false; }
        @Override public boolean isFlammableWithin(AxisAlignedBB area) { return false; }
        @Override public boolean handleMaterialAcceleration(AxisAlignedBB area, Material material, Entity entity) {
            // This fixture contains stone and air; no fluid can accelerate the real player physics.
            return false;
        }
        @Override public void playSound(double x, double y, double z, String name, float volume, float pitch, boolean delay) { }
    }

    /** A real block-shape callback can change module admission while support is being read. */
    private static final class SupportCallbackBlock extends Block {
        Runnable afterBounds = () -> { };
        SupportCallbackBlock() { super(Material.rock); setBlockBounds(0, 0, 0, 1, 1, 1); }
        @Override public AxisAlignedBB getSelectedBoundingBox(World world, BlockPos position) {
            AxisAlignedBB bounds = super.getSelectedBoundingBox(world, position);
            afterBounds.run();
            return bounds;
        }
    }

    /** Keep headless collision physics independent of OptiFine's rendering profiler. */
    private static final class CollisionProfiler extends Profiler {
        @Override public void startSection(String name) { }
        @Override public void endSection() { }
    }

    /** Calls Entity's real ground-counter producer without running unrelated entity simulation. */
    private static class GroundCounter extends Entity {
        GroundCounter() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
        @Override public void onEntityUpdate() { }
    }

    private static final class PacketObserver implements AutoCloseable {
        private final Listener<EventPacket> packets;
        PacketObserver(Consumer<EventPacket> action) {
            packets = new Listener<>((EventPacket event) -> action.accept(event));
            Pulsar.BUS.subscribe(packets);
        }
        @Override public void close() { Pulsar.BUS.unsubscribe(packets); }
    }

    private static final class MotionObserver implements AutoCloseable {
        private final Listener<EventMotion> motions;
        MotionObserver(Consumer<EventMotion> action) {
            motions = new Listener<>((EventMotion event) -> action.accept(event));
            Pulsar.BUS.subscribe(motions);
        }
        @Override public void close() { Pulsar.BUS.unsubscribe(motions); }
    }

    private static final class Run implements AutoCloseable {
        final float originalForward = MovementInput.moveForward, originalStrafe = MovementInput.moveStrafe;
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final NScaffold module = new NScaffold();
        final AtomicLong millis = new AtomicLong(1_000);
        final AtomicLong activityNanos = new AtomicLong(1_000_000_000L);
        final AtomicLong placementNanos = new AtomicLong(1_000_000_000L);
        final AtomicLong nanos;
        final Scene world;
        final GroundCounter ground = RotationBehaviorHookTest.bare(GroundCounter.class);
        ItemStack initialItem;
        final BlockPos target = new BlockPos(0, 63, 0);
        int inputs;

        @SuppressWarnings("unchecked") Run() throws Exception {
            field(Module.class, "toggled").setBoolean(f.aura, false);
            field(Module.class, "toggled").setBoolean(f.scaffold, false);
            Map<Class<? extends Module>,Module> modules =
                    (Map<Class<? extends Module>,Module>) field(ModuleManager.class, "modulesByClass").get(Pulsar.INSTANCE.getMm());
            modules.put(NScaffold.class, module);
            ScaffoldClientFlowTest.Scene prepared = ScaffoldClientFlowTest.prepare(f, .5, .5);
            world = RotationBehaviorHookTest.bare(Scene.class);
            world.blocks = prepared.blocks; world.border = prepared.border; world.motionTime = prepared.motionTime;
            field(net.minecraft.world.World.class, "loadedEntityList").set(world, new ArrayList<Entity>());
            field(net.minecraft.world.World.class, "theProfiler").set(world, new CollisionProfiler());
            f.mc.theWorld = world; f.player.worldObj = world;
            nanos = world.motionTime; nanos.set(1_000_000_000L);
            f.player.capabilities = new PlayerCapabilities();
            // Unsafe skips EntityLivingBase's ordinary empty map; real landings query jump potions.
            field(EntityLivingBase.class, "activePotionsMap").set(f.player, new HashMap<>());
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            f.player.width = .6F; f.player.height = 1.8F;
            f.player.inventory = new InventoryPlayer(f.player);
            f.player.inventory.currentItem = 2;
            initialItem = new ItemStack(Items.stick);
            f.player.inventory.mainInventory[2] = initialItem;
            f.player.inventory.mainInventory[3] = new ItemStack(Blocks.stone, 64);
            f.player.inventory.mainInventory[7] = new ItemStack(Blocks.tnt, 64);
            f.player.inventory.mainInventory[8] = new ItemStack(Blocks.stone, 16);
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            f.player.openContainer = f.player.inventoryContainer;
            GameSettings keys = f.mc.gameSettings;
            keys.keyBindForward = new KeyBinding("demise.forward", 17, "demise.test");
            keys.keyBindBack = new KeyBinding("demise.back", 31, "demise.test");
            keys.keyBindLeft = new KeyBinding("demise.left", 30, "demise.test");
            keys.keyBindRight = new KeyBinding("demise.right", 32, "demise.test");
            keys.keyBindJump = new KeyBinding("demise.jump", 57, "demise.test");
            keys.keyBindSneak = new KeyBinding("demise.sneak", 42, "demise.test");
            keys.keyBindSprint = new KeyBinding("demise.sprint", 29, "demise.test");
            keys.keyBindUseItem = new KeyBinding("demise.use", -99, "demise.test");
            keys.keyBindAttack = new KeyBinding("demise.attack", -100, "demise.test");
            f.player.movementInput = new MovementInputFromOptions(keys);
            MovementInput.moveForward = MovementInput.moveStrafe = 0;
            field(NScaffold.class, "millis").set(module, (LongSupplier) millis::get);
            field(NScaffold.class, "activityClock").set(module, (LongSupplier) activityNanos::get);
            field(NScaffold.class, "placementClock").set(module, (LongSupplier) placementNanos::get);
            field(NScaffold.class, "clutchTime").setLong(module, millis.get());
            field(NScaffold.class, "sneakTime").setLong(module, millis.get());
            field(NScaffold.class, "rotationEngine").set(module, new DemiseRotationEngine(millis::get, nanos::get, (min, max) -> min));
            // Lifecycle cases use a simple center aim; the moving bridge below uses the default Normal planner.
            module.getModeSetting("Rotations").setValue("Center");
            module.getBooleanSetting("Swing").setValue(false);
            f.runtime.getObservation().beginOuterTick();
            try { module.setToggled(true); } finally { f.runtime.getObservation().endOuterTick(); }
            step(() -> DemiseRotationHooks.update(f.player));
            drain();
        }

        void press(KeyBinding key, boolean pressed) { KeyBinding.setKeyBindState(key.getKeyCode(), pressed); }
        void forward(boolean pressed) {
            press(f.mc.gameSettings.keyBindForward, pressed);
            f.player.moveForward = pressed ? 1 : 0;
        }
        void left(boolean pressed) {
            press(f.mc.gameSettings.keyBindLeft, pressed);
            f.player.moveStrafing = pressed ? 1 : 0;
        }
        void groundTick(boolean onGround) {
            ground.onGround = onGround; ground.onUpdate();
            f.player.onGround = onGround;
            f.player.onGroundTicks = ground.onGroundTicks; f.player.offGroundTicks = ground.offGroundTicks;
        }
        void frame(float physicalYaw, float physicalPitch) {
            millis.addAndGet(16); nanos.addAndGet(16_000_000L);
            DemiseRotationHooks.mousePre();
            DemiseRotationHooks.mouseUpdate();
            f.player.setAngles(physicalYaw, physicalPitch);
        }
        void frameAt(long now) {
            millis.set(now / 1_000_000L); nanos.set(now);
            DemiseRotationHooks.mousePre();
            DemiseRotationHooks.mouseUpdate();
            f.player.setAngles(0, 0);
        }
        void physicsAt(long now) { physicsAt(now, false); }
        void physicsAt(long now, boolean processJump) {
            millis.set(now / 1_000_000L); nanos.set(now); placementNanos.set(now);
            step(() -> {
                groundTick(f.player.onGround);
                DemiseRotationHooks.update(f.player);
                f.runtime.getRotationManager().prepareMovement(f.player);
                f.player.movementInput.updatePlayerMoveState();
                inputs++;
                f.runtime.getRotationManager().beforeMovementPhysics();
                if (processJump) {
                    // EntityLivingBase.onLivingUpdate's native grounded jump gate. The impulse,
                    // subsequent travel, gravity and landing all use the real player methods.
                    if (f.player.jumpTicks > 0) f.player.jumpTicks--;
                    if (Math.abs(f.player.motionX) < .005D) f.player.motionX = 0;
                    if (Math.abs(f.player.motionY) < .005D) f.player.motionY = 0;
                    if (Math.abs(f.player.motionZ) < .005D) f.player.motionZ = 0;
                    if (f.player.movementInput.jump) {
                        if (f.player.onGround && f.player.jumpTicks == 0) {
                            f.player.jump();
                            f.player.jumpTicks = 10;
                        }
                    } else f.player.jumpTicks = 0;
                }
                f.player.moveStrafing = MovementInput.moveStrafe * .98F;
                f.player.moveForward = MovementInput.moveForward * .98F;
                f.player.prevPosX = f.player.posX; f.player.prevPosY = f.player.posY; f.player.prevPosZ = f.player.posZ;
                // Vanilla travel executes MoveFix, acceleration, EntityPlayerSP.moveEntity,
                // collision resolution, gravity and drag before this tick's C03 and C08.
                f.player.moveEntityWithHeading(f.player.moveStrafing, f.player.moveForward);
                f.player.onUpdateWalkingPlayer();
            });
        }
        void step(Runnable body) {
            f.runtime.getObservation().beginOuterTick();
            try {
                f.player.ticksExisted++;
                // Minecraft.runTick owns this decrement, once per physical tick.
                if (f.mc.rightClickDelayTimer > 0) f.mc.rightClickDelayTimer--;
                f.runtime.getObservation().observePlayerStep(f.player, () -> { body.run(); return true; }, step -> step.getAsBoolean());
            } finally { f.runtime.getObservation().endOuterTick(); }
        }
        void updateOnly() { step(() -> DemiseRotationHooks.update(f.player)); }
        void update() { updateAt(placementNanos.get() + 50_000_000L); }
        void updateAt(long now) {
            placementNanos.set(now);
            step(() -> { DemiseRotationHooks.update(f.player); f.player.onUpdateWalkingPlayer(); });
        }
        void updateThenInput(Runnable afterUpdate) {
            placementNanos.addAndGet(50_000_000L);
            step(() -> {
                DemiseRotationHooks.update(f.player);
                afterUpdate.run();
                f.runtime.getRotationManager().prepareMovement(f.player);
                f.player.movementInput.updatePlayerMoveState();
                inputs++;
                f.runtime.getRotationManager().beforeMovementPhysics();
                f.player.onUpdateWalkingPlayer();
            });
        }
        void frames(int count) { for (int frame = 0; frame < count; frame++) frame(0, 0); }
        Vec3 wireEyes() { return f.player.getLastReportedPosition().addVector(0, f.player.getEyeHeight(), 0); }
        MovingObjectPosition ray(float yaw, float pitch) {
            Vec3 eyes = wireEyes(), direction = Entity.getVectorForRotation(pitch, yaw);
            return world.rayTraceBlocks(eyes, eyes.addVector(direction.xCoord * 4.5, direction.yCoord * 4.5,
                    direction.zCoord * 4.5), false, false, false);
        }
        void alignRealRay() throws Exception {
            updateOnly();
            for (int frame = 0; frame < 120; frame++) {
                frame(0, 0);
                DemiseRotationEngine.Aim aim = module.rotationEngine.currentAim();
                DemiseScaffoldGeometry.PlaceData data =
                        (DemiseScaffoldGeometry.PlaceData) field(NScaffold.class, "data").get(module);
                MovingObjectPosition hit = aim == null ? null : ray(aim.yaw, aim.pitch);
                if (data != null && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && data.blockPos.equals(hit.getBlockPos()) && data.facing == hit.sideHit) return;
            }
            fail("Demise render frames did not acquire the real support face");
        }
        void bridgeDiagnostic(int fps, int tick, int stopped) throws Exception {
            NScaffoldRaycast.Plan plan = (NScaffoldRaycast.Plan) field(NScaffold.class, "rayPlan").get(module);
            float wireYaw = f.runtime.getRotationManager().getEffectiveYaw();
            float wirePitch = f.runtime.getRotationManager().getEffectivePitch();
            MovingObjectPosition hit = ray(wireYaw, wirePitch);
            MovingObjectPosition actionHit = NScaffoldRaycast.trace(world, wireEyes(), wireYaw, wirePitch,
                    f.mc.playerController.getBlockReachDistance(), plan, () -> true);
            MovingObjectPosition targetHit = plan == null ? null : ray(plan.yaw, plan.pitch);
            System.out.printf(java.util.Locale.ROOT,
                    "NScaffold bridge diagnostic placement=%s fps=%d tick=%d stopped=%d pos=(%.9f,%.9f,%.9f) wire=%s motion=(%.9f,%.9f) "
                            + "status=%s plan=%s support=%s face=%s planAngles=%s currentAim=%s targetAngles=%s target=%s "
                            + "planningEyes=%s wireAngles=(%.6f,%.6f) wireHit=%s actionHit=%s targetHit=%s owner=%s%n",
                    module.getModeSetting("Placement mode").getValue(), fps, tick, stopped,
                    f.player.posX, f.player.posY, f.player.posZ, f.player.getLastReportedPosition(),
                    f.player.motionX, f.player.motionZ, module.getStatus(), plan == null ? null : plan.destination,
                    plan == null ? null : plan.data.blockPos, plan == null ? null : plan.data.facing,
                    plan == null ? null : java.util.Arrays.toString(plan.rotation()),
                    java.util.Arrays.toString(module.rotationEngine.rotation()),
                    java.util.Arrays.toString((float[]) field(DemiseRotationEngine.class, "targetRotation").get(module.rotationEngine)),
                    module.targetBlock, field(NScaffold.class, "planningEyes").get(module), wireYaw, wirePitch,
                    hit, actionHit, targetHit, f.runtime.getRotationManager().getConfirmedOwner());
        }
        void walking() { step(() -> f.player.onUpdateWalkingPlayer()); }
        void motion(EventPhase phase) {
            EventMotion event = new EventMotion(f.player.posX, f.player.posY, f.player.posZ,
                    f.player.rotationYaw, f.player.rotationPitch, f.player.onGround);
            event.setEventPhase(phase); Pulsar.BUS.post(event);
        }
        MovementInputEvent input() {
            MovementInputEvent event = new MovementInputEvent(0, 1, false, false);
            module.onDemiseInput(event);
            return event;
        }
        List<Object> drain() {
            List<Object> packets = new ArrayList<>();
            Object packet; while ((packet = f.channel.readOutbound()) != null) packets.add(packet);
            return packets;
        }
        int placements(List<Object> packets) {
            int count = 0; for (Object packet : packets) if (packet instanceof C08PacketPlayerBlockPlacement) count++;
            return count;
        }
        void clearPlacedBlock() { world.blocks.remove(target); }
        @Override public void close() throws Exception {
            try { module.setToggled(false); }
            finally {
                try { f.close(); }
                finally { MovementInput.moveForward = originalForward; MovementInput.moveStrafe = originalStrafe; }
            }
        }
    }

    public static class Checks {
        @Test public void mouseFrameKeepsTheCameraFreeAndPublishesVisibleModelRotation() throws Exception {
            try (Run r = new Run()) {
                r.f.player.prevRotationYaw = -12; r.f.player.prevRotationPitch = 7;
                r.module.getModeSetting("Rotations").setValue("Reverse");
                r.module.getBooleanSetting("Static-ify").setValue(true);
                r.forward(true);
                r.frame(20, -10);
                // AngleEvent saw yaw 0 before vanilla applied the user's +3-degree delta.
                assertEquals(-180F, field(NScaffold.class, "initialYaw").getFloat(r.module), 0);
                assertEquals(3F, r.f.player.rotationYaw, 0);
                assertEquals(1.5F, r.f.player.rotationPitch, 0);
                assertEquals(-9F, r.f.player.prevRotationYaw, 0);
                assertEquals(8.5F, r.f.player.prevRotationPitch, 0);
                r.frame(0, 0);
                DemiseRotationEngine.Aim aim = r.module.rotationEngine.currentAim();
                assertNotNull(aim); assertTrue(Math.abs(aim.yaw - r.f.player.rotationYaw) > 1);
                assertTrue(aim.pitch > r.f.player.rotationPitch);
                assertEquals(3F, r.f.player.rotationYaw, 0); assertEquals(1.5F, r.f.player.rotationPitch, 0);
                r.walking();
                C03PacketPlayer sent = null;
                for (Object packet : r.drain()) if (packet instanceof C03PacketPlayer && ((C03PacketPlayer) packet).getRotating())
                    sent = (C03PacketPlayer) packet;
                assertNotNull("walking transports the Demise pose", sent);
                assertEquals(aim.yaw, sent.getYaw(), 0); assertEquals(aim.pitch, sent.getPitch(), 0);
                assertEquals(aim.yaw, r.f.player.rotationYawHead, 0); assertEquals(aim.pitch, r.f.player.rotationPitchHead, 0);
                assertEquals(aim.yaw, DemiseRotationHooks.modelYaw(r.f.player, 999), 0);
                assertEquals(3F, r.f.player.rotationYaw, 0); assertEquals(1.5F, r.f.player.rotationPitch, 0);
                r.frame(-40, 20);
                assertEquals(-3F, r.f.player.rotationYaw, 0); assertEquals(-1.5F, r.f.player.rotationPitch, 0);
                assertTrue(r.module.isToggled());
            }
        }

        @Test public void angleSelectsTheOriginalLastSlotWhileCosmeticConsumersKeepTheOldSlot() throws Exception {
            try (Run r = new Run()) {
                assertEquals("the initial Update prepares the original last eligible slot", 8, r.f.player.inventory.currentItem);
                r.frame(0, 0);
                assertEquals(8, r.f.player.inventory.currentItem);
                assertEquals(2, NScaffold.displayedSlot(r.f.player));
                assertSame(r.initialItem, NScaffold.displayedStack(r.f.player));
                assertSame(r.f.player.inventory.mainInventory[8], r.f.player.getHeldItem());
                r.f.player.inventory.mainInventory[8] = new ItemStack(Blocks.chest, 64);
                r.frame(0, 0); assertEquals(3, r.f.player.inventory.currentItem);
                assertEquals(2, NScaffold.displayedSlot(r.f.player));
                r.f.player.inventory.mainInventory[3] = null;
                r.frame(0, 0); assertEquals(3, r.f.player.inventory.currentItem);
                assertEquals(0, DemiseScaffoldInventory.blockCount(r.f.player));
            }
        }

        @Test public void updatePreparesThenAcceptedWalkingPlacesAfterInputExactlyOnce() throws Exception {
            try (Run r = new Run()) {
                r.forward(true); r.alignRealRay();
                r.updateThenInput(() -> {
                    assertEquals("fresh input has not been sampled yet", 0, r.inputs);
                    assertFalse("Update only prepares the target", r.world.blocks.containsKey(r.target));
                    assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize);
                });
                assertEquals(1, r.inputs);
                assertEquals(Blocks.stone, r.world.getBlockState(r.target).getBlock());
                assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(NScaffold.Status.BUILDING, r.module.getStatus());
                List<Object> packets = r.drain(); assertEquals(1, r.placements(packets));
                int movement = -1, slot = -1, placement = -1, animation = -1;
                for (int index = 0; index < packets.size(); index++) {
                    Object packet = packets.get(index);
                    if (packet instanceof C03PacketPlayer) movement = index;
                    if (packet instanceof C09PacketHeldItemChange) slot = index;
                    if (packet instanceof C08PacketPlayerBlockPlacement) placement = index;
                    if (packet instanceof C0APacketAnimation) animation = index;
                }
                assertTrue("the current C03 is accepted before a placement", movement >= 0 && movement < placement);
                assertTrue(slot >= 0 && slot < placement); assertTrue(placement < animation);
                assertEquals(8, ((C09PacketHeldItemChange) packets.get(slot)).getSlotId());
                assertTrue(r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                r.clearPlacedBlock();
                EventMotion post = new EventMotion(.5, 64, .5, 0, 0, true);
                post.setEventPhase(EventPhase.POST); post.setAccepted(true);
                r.step(() -> { Pulsar.BUS.post(post); Pulsar.BUS.post(post); });
                assertFalse("a previous C03 cannot authorize a new physical tick", r.world.blocks.containsKey(r.target));
                assertEquals(0, r.placements(r.drain()));
                r.update(); assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void realRayMissesAndWrongFacesNeverPlaceOrChangeSuccessfulHistory() throws Exception {
            try (Run r = new Run()) {
                assertTrue(r.module.getBooleanSetting("RayTrace").getValue());
                r.module.getModeSetting("Rotations").setValue("Reverse");
                r.module.getBooleanSetting("Static-ify").setValue(true); r.forward(true);
                r.frames(80); r.update();
                assertTrue("the miss still has an accepted NScaffold C03", r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                assertNull("this real northward ray misses the western support",
                        r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(), r.f.runtime.getRotationManager().getEffectivePitch()));
                assertEquals(0, r.placements(r.drain()));
                assertNull(field(NScaffold.class, "previousBlock").get(r.module));

                r.module.getModeSetting("Rotations").setValue("Center"); r.alignRealRay(); r.update();
                assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(1, r.placements(r.drain()));
                assertEquals(r.target, field(NScaffold.class, "previousBlock").get(r.module));
                BlockPos obstruction = new BlockPos(0, 64, 0);
                for (boolean strict : new boolean[]{false, true}) {
                    r.clearPlacedBlock(); r.module.getBooleanSetting("Strict rayTrace").setValue(strict);
                    int placed = counter(r.module, "blocksPlacedJump");
                    int stack = r.f.player.inventory.mainInventory[8].stackSize;
                    r.world.blocks.put(obstruction, Blocks.stone.getDefaultState());
                    r.update();
                    MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                            r.f.runtime.getRotationManager().getEffectivePitch());
                    assertNotNull(hit); assertEquals(obstruction, hit.getBlockPos()); assertEquals(EnumFacing.UP, hit.sideHit);
                    assertEquals("neither ray mode may build on an unrelated face", 0, r.placements(r.drain()));
                    assertFalse(r.world.blocks.containsKey(r.target)); assertFalse(r.world.blocks.containsKey(obstruction.up()));
                    assertEquals(placed, counter(r.module, "blocksPlacedJump"));
                    assertEquals(stack, r.f.player.inventory.mainInventory[8].stackSize);
                    assertEquals("a rejected ray cannot change placement history", r.target, field(NScaffold.class, "previousBlock").get(r.module));
                    r.world.blocks.remove(obstruction); r.alignRealRay(); r.update();
                    assertEquals(1, r.placements(r.drain())); assertTrue(r.world.blocks.containsKey(r.target));
                    assertEquals(placed + 1, counter(r.module, "blocksPlacedJump"));
                }
            }
        }


        @Test public void realRayUsesReportedPositionAndAcceptedWireAngles() throws Exception {
            try (Run r = new Run()) {
                // This displacement is below vanilla's C03 position threshold.
                r.f.player.setPosition(.52, 64, .5);
                r.f.player.rotationYaw = -37; r.f.player.rotationPitch = 8;
                r.alignRealRay();
                final Vec3[] usedEyes = {null};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C08PacketPlayerBlockPlacement)
                        usedEyes[0] = r.world.lastRayFrom;
                })) { r.update(); }
                List<Object> packets = r.drain(); assertEquals(1, r.placements(packets));
                assertNotNull(usedEyes[0]);
                assertEquals(.5, r.f.player.getLastReportedPosition().xCoord, 1e-12);
                assertEquals(.5, usedEyes[0].xCoord, 1e-12);
                assertEquals(64 + (double) r.f.player.getEyeHeight(), usedEyes[0].yCoord, 1e-12);
                assertEquals(.5, usedEyes[0].zCoord, 1e-12);
                assertEquals(.52, r.f.player.posX, 1e-12);
                assertEquals(-37, r.f.player.rotationYaw, 0); assertEquals(8, r.f.player.rotationPitch, 0);
                C08PacketPlayerBlockPlacement click = null;
                C03PacketPlayer walking = null;
                for (Object packet : packets) {
                    if (packet instanceof C03PacketPlayer) walking = (C03PacketPlayer) packet;
                    if (packet instanceof C08PacketPlayerBlockPlacement) click = (C08PacketPlayerBlockPlacement) packet;
                }
                assertNotNull(walking); assertFalse("the accepted position remains unchanged", walking.isMoving());
                assertNotNull(click); assertEquals(new BlockPos(-1, 63, 0), click.getPosition());
                assertEquals(EnumFacing.EAST.getIndex(), click.getPlacedBlockDirection());
                assertEquals(1F, click.getPlacedBlockOffsetX(), 1e-6F);
                assertTrue(click.getPlacedBlockOffsetY() > 0 && click.getPlacedBlockOffsetY() < 1);
                assertTrue(click.getPlacedBlockOffsetZ() > 0 && click.getPlacedBlockOffsetZ() < 1);
                assertTrue(r.world.blocks.containsKey(r.target));
            }
        }

        @Test public void lowCpsDoesNotCatchUpOrDependOnRenderFrameCount() throws Exception {
            try (Run r = new Run()) {
                r.module.getNumberSetting("Min CPS").setValue(2D);
                r.module.getNumberSetting("Max CPS").setValue(2D);
                r.alignRealRay();
                long first = r.placementNanos.get();
                r.updateAt(first); assertEquals("the first valid opportunity is immediate", 1, r.placements(r.drain()));
                r.clearPlacedBlock();
                for (int tick = 1; tick < 10; tick++) {
                    r.frames(new int[]{1, 4, 2, 7}[tick % 4]);
                    assertEquals("render frames cannot place", 0, r.placements(r.drain()));
                    r.updateAt(first + tick * 50_000_000L);
                    assertEquals("the complete CPS interval is required", 0, r.placements(r.drain()));
                    assertFalse(r.world.blocks.containsKey(r.target));
                }
                r.updateAt(first + 500_000_000L); assertEquals(1, r.placements(r.drain()));
                r.clearPlacedBlock();
                long afterStall = first + 10_000_000_000L;
                r.updateAt(afterStall); assertEquals("missed intervals create one opportunity only", 1, r.placements(r.drain()));
                r.clearPlacedBlock();
                r.updateAt(afterStall); r.updateAt(afterStall + 499_999_999L);
                assertEquals(0, r.placements(r.drain())); assertFalse(r.world.blocks.containsKey(r.target));
                r.updateAt(afterStall + 500_000_000L); assertEquals(1, r.placements(r.drain()));
                assertEquals(4, counter(r.module, "blocksPlacedJump"));
                assertEquals(12, r.f.player.inventory.mainInventory[8].stackSize);
            }
        }

        @Test public void onePhysicalTickCannotSpendTwoAttemptsAcrossWalkingSequences() throws Exception {
            try (Run r = new Run()) {
                r.alignRealRay();
                final long[] sequence = {-1};
                r.step(() -> {
                    DemiseRotationHooks.update(r.f.player); r.f.player.onUpdateWalkingPlayer();
                    sequence[0] = r.f.runtime.getRotationManager().getMotionSequence();
                    assertTrue(r.world.blocks.containsKey(r.target));
                    r.clearPlacedBlock();
                    EventMotion duplicate = new EventMotion(r.f.player.posX, r.f.player.posY, r.f.player.posZ,
                            r.f.runtime.getRotationManager().getEffectiveYaw(), r.f.runtime.getRotationManager().getEffectivePitch(), true);
                    duplicate.setEventPhase(EventPhase.POST); duplicate.setAccepted(true);
                    Pulsar.BUS.post(duplicate); Pulsar.BUS.post(duplicate);
                    // Additional walking frames cannot turn one player tick into another click budget.
                    for (int extra = 0; extra < 3; extra++) {
                        r.placementNanos.addAndGet(1_000_000_000L);
                        DemiseRotationHooks.update(r.f.player); r.f.player.onUpdateWalkingPlayer();
                    }
                });
                assertTrue(r.f.runtime.getRotationManager().getMotionSequence() > sequence[0]);
                assertEquals(1, r.placements(r.drain())); assertFalse(r.world.blocks.containsKey(r.target));
                assertEquals(1, counter(r.module, "blocksPlacedJump"));
                r.update(); assertEquals(1, r.placements(r.drain())); assertTrue(r.world.blocks.containsKey(r.target));
            }
        }

        @Test public void legitModeRequiresARealRayAndNativeFourTickDelay() throws Exception {
            try (Run r = new Run()) {
                r.module.getModeSetting("Placement mode").setValue("Legit");
                r.module.getBooleanSetting("RayTrace").setValue(false);
                r.module.getModeSetting("Rotations").setValue("Reverse");
                r.module.getBooleanSetting("Static-ify").setValue(true); r.forward(true);
                r.frames(80); r.update();
                assertEquals("Legit cannot bypass an invalid real ray", 0, r.placements(r.drain()));
                assertEquals(0, r.f.mc.rightClickDelayTimer);
                r.module.getModeSetting("Rotations").setValue("Center"); r.alignRealRay();
                long first = r.placementNanos.get(); r.updateAt(first);
                List<Object> initial = r.drain(); assertEquals(1, r.placements(initial)); assertEquals(4, r.f.mc.rightClickDelayTimer);
                for (Object packet : initial) if (packet instanceof C08PacketPlayerBlockPlacement)
                    assertNotEquals("Legit must not emit an extra air-use", 255, ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection());
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown());
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isPressed());
                r.clearPlacedBlock();
                for (int tick = 1; tick <= 3; tick++) {
                    r.updateAt(first + tick * 50_000_000L);
                    assertEquals(4 - tick, r.f.mc.rightClickDelayTimer);
                    assertEquals(0, r.placements(r.drain()));
                }
                r.updateAt(first + 200_000_000L);
                assertEquals(1, r.placements(r.drain())); assertEquals(4, r.f.mc.rightClickDelayTimer);
                assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(14, r.f.player.inventory.mainInventory[8].stackSize);
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown());
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isPressed());
            }
        }

        @Test public void rejectedC03AndForeignOwnerCannotAuthorizePlacement() throws Exception {
            try (Run r = new Run()) {
                r.module.getNumberSetting("Min CPS").setValue(1D); r.module.getNumberSetting("Max CPS").setValue(1D);
                r.alignRealRay(); long now = r.placementNanos.get();
                final int[] rejected = {0};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C03PacketPlayer) {
                        rejected[0]++; event.cancel();
                    }
                })) { r.updateAt(now); }
                assertEquals(1, rejected[0]); assertEquals(0, r.placements(r.drain()));
                assertFalse(r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                assertNull(field(NScaffold.class, "previousBlock").get(r.module));
                try (MotionObserver ignored = new MotionObserver(event -> {
                    if (event.isPre()) {
                        DemiseRotationEngine.Aim aim = r.module.rotationEngine.currentAim();
                        r.f.runtime.getRotationManager().request("fixture-owner", 100, aim.yaw, aim.pitch);
                    }
                })) { r.updateAt(now); }
                assertTrue(r.f.runtime.getRotationManager().canUseRotation("fixture-owner", false));
                assertFalse(r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                assertEquals(0, r.placements(r.drain())); assertFalse(r.world.blocks.containsKey(r.target));
                assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize);
                r.updateAt(now);
                assertEquals("rejected authority did not consume the immediate first opportunity", 1, r.placements(r.drain()));
                assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(r.target, field(NScaffold.class, "previousBlock").get(r.module));
            }
        }

        @Test public void rejectedControllerAttemptConsumesCadenceWithoutInventingHistory() throws Exception {
            try (Run r = new Run()) {
                r.module.getNumberSetting("Min CPS").setValue(2D); r.module.getNumberSetting("Max CPS").setValue(2D);
                r.alignRealRay(); long first = r.placementNanos.get();
                final int[] rejected = {0};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                        rejected[0]++; event.cancel();
                    }
                })) { r.updateAt(first); }
                assertEquals("the real controller reached its C08 transport boundary", 1, rejected[0]);
                assertEquals(0, r.placements(r.drain())); assertFalse(r.world.blocks.containsKey(r.target));
                assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(0, counter(r.module, "blocksPlacedJump"));
                assertNull(field(NScaffold.class, "previousBlock").get(r.module));
                r.updateAt(first + 499_999_999L);
                assertEquals("controller rejection cannot grant an immediate retry", 0, r.placements(r.drain()));
                assertNull(field(NScaffold.class, "previousBlock").get(r.module));
                r.updateAt(first + 500_000_000L);
                assertEquals(1, r.placements(r.drain())); assertTrue(r.world.blocks.containsKey(r.target));
                assertEquals(1, counter(r.module, "blocksPlacedJump"));
                assertEquals(r.target, field(NScaffold.class, "previousBlock").get(r.module));
            }
        }

        @Test public void edgeSafetyHoldsOnlyUnsupportedMotionAndReleasesAfterPlacement() throws Exception {
            try (Run r = new Run()) {
                assertEquals("Stop", r.module.getModeSetting("Edge safety").getValue());
                r.module.getNumberSetting("Min CPS").setValue(2D); r.module.getNumberSetting("Max CPS").setValue(2D);
                r.f.player.setPosition(.2, 64, .5); r.walking(); r.drain();
                r.f.player.motionX = .4; r.f.player.motionZ = 0;
                EventMove held = DemiseRotationHooks.move(r.f.player, .4, -.08, 0);
                assertTrue(held.getX() >= 0 && held.getX() < .1);
                assertEquals(-.08, held.getY(), 0); assertEquals(0, held.getZ(), 0);
                assertEquals("edge protection changes the displacement, not persistent momentum", .4, r.f.player.motionX, 0);
                assertEquals(NScaffold.Status.EDGE_GUARD, r.module.getStatus());
                assertFalse(r.world.getCollidingBoundingBoxes(r.f.player,
                        r.f.player.getEntityBoundingBox().offset(held.getX(), -.05, held.getZ())).isEmpty());

                r.module.getModeSetting("Edge safety").setValue("Off");
                assertEquals(.4, DemiseRotationHooks.move(r.f.player, .4, 0, 0).getX(), 0);
                r.module.getModeSetting("Edge safety").setValue("Sneak");
                assertTrue("Sneak uses the actual unsupported movement forecast", r.input().isSneak());
                r.module.getModeSetting("Edge safety").setValue("Stop");
                assertFalse(r.input().isSneak());
                r.f.player.movementInput.jump = true;
                assertEquals("intentional takeoff is preserved", .4, DemiseRotationHooks.move(r.f.player, .4, 0, 0).getX(), 0);
                r.f.player.movementInput.jump = false; r.f.player.onGround = false;
                assertEquals("airborne movement is preserved", .4, DemiseRotationHooks.move(r.f.player, .4, -.08, 0).getX(), 0);
                r.f.player.onGround = true;
                assertEquals("rising motion is preserved", .4, DemiseRotationHooks.move(r.f.player, .4, .42, 0).getX(), 0);

                r.f.player.motionX = 0; r.alignRealRay(); r.update();
                long first = r.placementNanos.get(); assertEquals(1, r.placements(r.drain()));
                assertEquals(.4, DemiseRotationHooks.move(r.f.player, .4, -.08, 0).getX(), 0);
                r.f.player.setPosition(1.2, 64, .5); r.walking(); r.drain(); r.alignRealRay();
                r.updateAt(first + 100_000_000L);
                assertEquals(0, r.placements(r.drain())); assertFalse(r.world.blocks.containsKey(new BlockPos(1, 63, 0)));
                EventMove waiting = DemiseRotationHooks.move(r.f.player, .4, 0, 0);
                assertTrue("a valid ray does not bypass CPS or edge support", waiting.getX() >= 0 && waiting.getX() < .1);
                r.updateAt(first + 500_000_000L);
                assertEquals(1, r.placements(r.drain())); assertTrue(r.world.blocks.containsKey(new BlockPos(1, 63, 0)));
                assertEquals("a successful new floor releases the held motion", .4,
                        DemiseRotationHooks.move(r.f.player, .4, 0, 0).getX(), 0);
            }
        }

        @Test public void movingBridgeStaysSupportedAcrossRealRaysAndFrameSequences() throws Exception {
            checkMovingBridge("Default");
        }

        @Test public void movingLegitBridgeStaysSupportedAcrossRealRaysAndFrameSequences() throws Exception {
            checkMovingBridge("Legit");
        }

        @Test public void diagonalBridgeAdvancesAcrossRealRaysAndFrameSequences() throws Exception {
            checkDiagonalBridge("Default");
        }

        @Test public void diagonalLegitBridgeAdvancesAcrossRealRaysAndFrameSequences() throws Exception {
            checkDiagonalBridge("Legit");
        }

        private void checkDiagonalBridge(String placementMode) throws Exception {
            for (int signX : new int[]{1, -1}) for (int signZ : new int[]{1, -1}) {
                double minimumPostWarmupDistance = Double.MAX_VALUE, maximumPostWarmupDistance = 0;
                for (int fps : new int[]{30, 60, 144}) {
                    try (Run r = new Run()) {
                        String scenario = placementMode + "/diagonal(" + signX + "," + signZ + ")/" + fps + " FPS";
                        r.module.getModeSetting("Rotations").setValue("Normal");
                        r.module.getModeSetting("Placement mode").setValue(placementMode);
                        r.module.getModeSetting("Edge safety").setValue("Stop");
                        assertEquals("Silent", r.module.getModeSetting("Movement correction").getValue());
                        assertEquals(20D, r.module.getNumberSetting("Min CPS").getValue(), 0);
                        assertEquals(20D, r.module.getNumberSetting("Max CPS").getValue(), 0);
                        assertTrue(r.module.getBooleanSetting("RayTrace").getValue());
                        r.world.blocks.clear();
                        r.world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
                        r.f.player.inventory.mainInventory[8] = new ItemStack(Blocks.stone, 64);
                        r.f.player.setPosition(.5, 64, .5);
                        r.f.player.motionX = r.f.player.motionZ = 0; r.f.player.motionY = -.08;
                        r.f.player.stepHeight = .6F; r.f.player.jumpMovementFactor = .02F;
                        assertEquals(.1F, r.f.player.getAIMoveSpeed(), 0);
                        // Held keyboard axes feed actual travel and MoveFix, including negative grid crossings.
                        r.press(r.f.mc.gameSettings.keyBindLeft, signX > 0);
                        r.press(r.f.mc.gameSettings.keyBindRight, signX < 0);
                        r.press(r.f.mc.gameSettings.keyBindForward, signZ > 0);
                        r.press(r.f.mc.gameSettings.keyBindBack, signZ < 0);
                        final int[] witnessed = {0};
                        int placements = 0, firstPlacementTick = -1, lastPlacementTick = -4;
                        int stoppedAfterWarmup = 0, cadenceWaits = 0, stopRun = 0, longestStop = 0;
                        double warmupProgress = 0;
                        long start = r.nanos.get(), framePeriod = 1_000_000_000L / fps;
                        long nextFrame = start + framePeriod;
                        final int ticks = 100, warmup = 20;
                        try (PacketObserver ignored = new PacketObserver(event -> {
                            if (!event.isPre() || !event.isOutbound()
                                    || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
                            assertTrue("diagonal clicks require the current accepted own C03: " + scenario,
                                    r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                            MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                                    r.f.runtime.getRotationManager().getEffectivePitch());
                            assertNotNull("the accepted pose must hit real diagonal support: " + scenario, hit);
                            assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, hit.typeOfHit);
                            assertEquals(hit.getBlockPos(), packet.getPosition());
                            assertEquals(hit.sideHit.getIndex(), packet.getPlacedBlockDirection());
                            BlockPos destination = packet.getPosition().offset(hit.sideHit);
                            assertEquals(63, destination.getY());
                            assertTrue("each click extends unoccupied terrain: " + scenario,
                                    r.world.getBlockState(destination).getBlock().getMaterial().isReplaceable());
                            witnessed[0]++;
                        })) {
                            for (int tick = 1; tick <= ticks; tick++) {
                                long tickAt = start + tick * 50_000_000L;
                                int beforeFrames = r.f.player.ticksExisted;
                                while (nextFrame <= tickAt) {
                                    r.frameAt(nextFrame); nextFrame += framePeriod;
                                }
                                assertEquals(beforeFrames, r.f.player.ticksExisted);
                                assertEquals("render frames cannot place blocks", 0, r.placements(r.drain()));
                                double beforeX = r.f.player.posX, beforeZ = r.f.player.posZ;
                                r.physicsAt(tickAt);
                                List<Object> packets = r.drain();
                                int clicks = r.placements(packets), movementIndex = -1;
                                int previousPlacementTick = lastPlacementTick;
                                assertTrue("one physical tick grants at most one controller attempt", clicks <= 1);
                                for (int index = 0; index < packets.size(); index++) {
                                    if (packets.get(index) instanceof C03PacketPlayer) movementIndex = index;
                                    if (packets.get(index) instanceof C08PacketPlayerBlockPlacement)
                                        assertTrue("accepted C03 precedes every diagonal C08: " + scenario,
                                                movementIndex >= 0 && movementIndex < index);
                                }
                                assertTrue("each physical tick traverses the real walking transport", movementIndex >= 0);
                                if (clicks > 0 && firstPlacementTick < 0) firstPlacementTick = tick;
                                if (clicks > 0 && "Legit".equals(placementMode)) {
                                    assertTrue("Legit keeps its four-tick placement interval: " + scenario,
                                            tick - lastPlacementTick >= 4);
                                    assertEquals(4, r.f.mc.rightClickDelayTimer);
                                    lastPlacementTick = tick;
                                }
                                placements += clicks;
                                assertEquals("diagonal support must prevent every fall: " + scenario + " tick=" + tick,
                                        64, r.f.player.posY, 1e-8);
                                assertTrue(r.f.player.onGround);
                                assertFalse(r.world.getCollidingBoundingBoxes(r.f.player,
                                        r.f.player.getEntityBoundingBox().offset(0, -.001, 0)).isEmpty());
                                assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                                if (tick > warmup && Math.hypot(r.f.player.posX - beforeX, r.f.player.posZ - beforeZ) < 1e-7) {
                                    stoppedAfterWarmup++; stopRun++; longestStop = Math.max(longestStop, stopRun);
                                    if ("Legit".equals(placementMode) && previousPlacementTick >= 0) {
                                        int sincePlacement = tick - previousPlacementTick;
                                        // Physics precedes POST, so the earliest allowed C08 tick
                                        // can still be a final guarded tick. Earlier waits need
                                        // a real ready ray and the native use-item delay.
                                        boolean dueClick = sincePlacement == 4 && clicks == 1;
                                        NScaffoldRaycast.Plan waiting = (NScaffoldRaycast.Plan)
                                                field(NScaffold.class, "rayPlan").get(r.module);
                                        MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                                                r.f.runtime.getRotationManager().getEffectivePitch());
                                        boolean readyWait = sincePlacement > 0 && sincePlacement < 4 && clicks == 0
                                                && r.f.mc.rightClickDelayTimer > 0 && waiting != null
                                                && r.f.runtime.getRotationManager().canUseRotation("nscaffold", false)
                                                && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                                && hit.getBlockPos().equals(waiting.data.blockPos) && hit.sideHit == waiting.data.facing
                                                && r.world.getBlockState(waiting.destination).getBlock().getMaterial().isReplaceable();
                                        if (dueClick || readyWait) cadenceWaits++;
                                    }
                                } else stopRun = 0;
                                if (tick == warmup) warmupProgress = diagonalProgress(r, signX, signZ);
                                if (stopRun == 4) r.bridgeDiagnostic(fps, tick, stopRun);
                            }
                        }
                        double progress = diagonalProgress(r, signX, signZ);
                        double postWarmupDistance = progress - warmupProgress;
                        minimumPostWarmupDistance = Math.min(minimumPostWarmupDistance, postWarmupDistance);
                        maximumPostWarmupDistance = Math.max(maximumPostWarmupDistance, postWarmupDistance);
                        // A diagonal corner often needs two placements four ticks apart.
                        // Classify proven native-cadence waits so an arbitrary global stop
                        // count does not conflate legal cooldowns with ray-search stalls.
                        int unexplainedStops = stoppedAfterWarmup - cadenceWaits;
                        System.out.printf(java.util.Locale.ROOT,
                                "NScaffold diagonal scenario=%s ticks=%d progress=%.4f postWarmupDistance=%.4f blocks=%d "
                                        + "stoppedAfterWarmup=%d cadenceWaits=%d unexplainedStops=%d longestStop=%d firstC08Tick=%d final=(%.6f,%.6f)%n",
                                scenario, ticks, progress, postWarmupDistance, placements, stoppedAfterWarmup,
                                cadenceWaits, unexplainedStops, longestStop, firstPlacementTick, r.f.player.posX, r.f.player.posZ);
                        if (progress < 12 || placements < 12 || unexplainedStops > 8 || longestStop > 3
                                || firstPlacementTick < 1 || firstPlacementTick > 25)
                            r.bridgeDiagnostic(fps, ticks, stopRun);
                        assertEquals("all diagonal clicks were verified against the accepted ray", placements, witnessed[0]);
                        assertEquals(placements + 1, r.world.blocks.size());
                        assertEquals(64 - placements, r.f.player.inventory.mainInventory[8].stackSize);
                        assertEquals(placements, counter(r.module, "blocksPlacedJump"));
                        assertEquals(ticks, r.inputs);
                        assertTrue("diagonal acquisition must finish within 25 ticks: " + scenario,
                                firstPlacementTick >= 1 && firstPlacementTick <= 25);
                        assertTrue("the diagonal bridge must advance beyond the first corner: " + scenario, progress >= 12);
                        assertTrue("both diagonal axes must advance: " + scenario,
                                (r.f.player.posX - .5) * signX >= 6 && (r.f.player.posZ - .5) * signZ >= 6);
                        assertTrue("the diagonal route needs many successful placements: " + scenario, placements >= 12);
                        assertTrue("target/ray stalls must remain bounded beyond proven native cadence waits: " + scenario,
                                unexplainedStops <= 8);
                        assertTrue("diagonal target search must not remain stuck: " + scenario, longestStop <= 3);
                    }
                }
                assertTrue("frame rate must not starve one diagonal direction: " + placementMode + "/" + signX + "/" + signZ,
                        maximumPostWarmupDistance - minimumPostWarmupDistance <= 4);
            }
        }

        private double diagonalProgress(Run r, int signX, int signZ) {
            return ((r.f.player.posX - .5) * signX + (r.f.player.posZ - .5) * signZ) / Math.sqrt(2);
        }

        @Test public void stationaryJumpTowerKeepsItsAimBetweenRealPlacements() throws Exception {
            for (int fps : new int[]{30, 60, 144}) {
                try (Run r = new Run()) {
                    String scenario = "Jump tower/" + fps + " FPS";
                    r.module.getModeSetting("Rotations").setValue("Normal");
                    r.module.getModeSetting("Tower").setValue("Jump");
                    r.module.getModeSetting("Sprint mode").setValue("None");
                    r.module.getModeSetting("Edge safety").setValue("Stop");
                    assertTrue(r.module.getBooleanSetting("RayTrace").getValue());
                    // Run the actual World placement gate, including the live player's AABB.
                    // Unsafe skipped EntityLivingBase's ordinary collision-admission initializer.
                    r.f.player.preventEntitySpawning = true;
                    r.world.loadedEntityList.add(r.f.player);
                    r.world.realPlacementConditions = true;
                    r.world.blocks.clear();
                    r.world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
                    r.f.player.inventory.mainInventory[8] = new ItemStack(Blocks.stone, 64);
                    r.f.player.setPosition(.5, 64, .5);
                    r.f.player.motionX = r.f.player.motionZ = 0; r.f.player.motionY = -.08;
                    r.f.player.stepHeight = .6F; r.f.player.jumpMovementFactor = .02F;
                    r.forward(false); r.left(false);
                    r.press(r.f.mc.gameSettings.keyBindBack, false); r.press(r.f.mc.gameSettings.keyBindRight, false);
                    r.press(r.f.mc.gameSettings.keyBindJump, true);
                    field(NScaffold.class, "jumpKey").set(r.module, (BooleanSupplier) () -> true);
                    final int[] witnessed = {0};
                    final float[] firstPlacedPitch = {Float.NaN};
                    int placements = 0, longIdleGaps = 0, firstPlacementTick = -1;
                    long idleStarted = -1, longestIdle = 0;
                    boolean countedIdle = false;
                    long start = r.nanos.get(), framePeriod = 1_000_000_000L / fps, nextFrame = start + framePeriod;
                    final int ticks = 100;
                    try (PacketObserver ignored = new PacketObserver(event -> {
                        if (!event.isPre() || !event.isOutbound()
                                || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
                        assertTrue("tower clicks require the current accepted own C03: " + scenario,
                                r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                        float wirePitch = r.f.runtime.getRotationManager().getEffectivePitch();
                        MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(), wirePitch);
                        assertNotNull(hit); assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, hit.typeOfHit);
                        assertEquals(hit.getBlockPos(), packet.getPosition());
                        assertEquals(EnumFacing.UP, hit.sideHit);
                        assertEquals(hit.sideHit.getIndex(), packet.getPlacedBlockDirection());
                        BlockPos destination = packet.getPosition().up();
                        assertEquals(0, destination.getX()); assertEquals(0, destination.getZ());
                        AxisAlignedBB placedBody = Blocks.stone.getCollisionBoundingBox(r.world, destination,
                                Blocks.stone.getDefaultState());
                        assertFalse("the real target block must not intersect the jumping player's body",
                                placedBody.intersectsWith(r.f.player.getEntityBoundingBox()));
                        assertTrue("a held rotation cannot click an already occupied tower cell",
                                r.world.getBlockState(destination).getBlock().getMaterial().isReplaceable());
                        if (Float.isNaN(firstPlacedPitch[0])) firstPlacedPitch[0] = wirePitch;
                        witnessed[0]++;
                    })) {
                        for (int tick = 1; tick <= ticks; tick++) {
                            long tickAt = start + tick * 50_000_000L;
                            int beforeFrames = r.f.player.ticksExisted;
                            while (nextFrame <= tickAt) {
                                r.frameAt(nextFrame);
                                if (placements > 0) {
                                    assertTrue("the held tower target must stay enabled beyond the original 150-ms expiry: " + scenario,
                                            r.module.rotationEngine.enabled());
                                    DemiseRotationEngine.Aim aim = r.module.rotationEngine.currentAim();
                                    assertNotNull("tower frames retain their silent rotation", aim);
                                    assertTrue("tower aiming must not turn back toward the horizontal camera between blocks: " + scenario,
                                            aim.pitch >= firstPlacedPitch[0] - 1.5F);
                                    BlockPos foot = new BlockPos(r.f.player.posX, r.f.player.posY - 1, r.f.player.posZ);
                                    boolean occupiedIdle = field(NScaffold.class, "data").get(r.module) == null
                                            && !r.world.getBlockState(foot).getBlock().getMaterial().isReplaceable();
                                    if (occupiedIdle) {
                                        if (idleStarted < 0) idleStarted = nextFrame;
                                        long idle = nextFrame - idleStarted;
                                        longestIdle = Math.max(longestIdle, idle);
                                        if (idle > 150_000_000L && !countedIdle) { longIdleGaps++; countedIdle = true; }
                                    } else { idleStarted = -1; countedIdle = false; }
                                }
                                assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                                nextFrame += framePeriod;
                            }
                            assertEquals(beforeFrames, r.f.player.ticksExisted);
                            assertEquals("tower render frames cannot click", 0, r.placements(r.drain()));
                            r.physicsAt(tickAt, true);
                            List<Object> packets = r.drain();
                            int clicks = r.placements(packets), movementIndex = -1;
                            assertTrue(clicks <= 1);
                            for (int index = 0; index < packets.size(); index++) {
                                if (packets.get(index) instanceof C03PacketPlayer) movementIndex = index;
                                if (packets.get(index) instanceof C08PacketPlayerBlockPlacement)
                                    assertTrue("accepted C03 precedes every tower C08", movementIndex >= 0 && movementIndex < index);
                            }
                            assertTrue(movementIndex >= 0);
                            if (clicks > 0 && firstPlacementTick < 0) firstPlacementTick = tick;
                            placements += clicks;
                            assertEquals("stationary tower cannot drift horizontally", .5, r.f.player.posX, 1e-8);
                            assertEquals("stationary tower cannot drift horizontally", .5, r.f.player.posZ, 1e-8);
                            assertTrue("real jump/collision physics cannot fall through placed tower blocks", r.f.player.posY >= 64);
                            assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                        }
                    }
                    System.out.printf(java.util.Locale.ROOT,
                            "NScaffold tower scenario=%s ticks=%d height=%.4f blocks=%d firstC08Tick=%d "
                                    + "longIdleGaps=%d longestOccupiedIdleMs=%.3f firstPlacedPitch=%.4f finalPitch=%.4f%n",
                            scenario, ticks, r.f.player.posY - 64, placements, firstPlacementTick, longIdleGaps,
                            longestIdle / 1_000_000D, firstPlacedPitch[0], r.module.rotationEngine.rotation()[1]);
                    assertEquals(placements, witnessed[0]);
                    assertEquals(placements + 1, r.world.blocks.size());
                    assertEquals(64 - placements, r.f.player.inventory.mainInventory[8].stackSize);
                    assertEquals(ticks, r.inputs);
                    assertTrue("a real tower must place repeatedly: " + scenario, placements >= 6);
                    assertTrue("real jumps must climb the placed column: " + scenario, r.f.player.posY >= 70);
                    assertTrue("the test must traverse several occupied gaps longer than Demise's expiry: " + scenario,
                            longIdleGaps >= 2 && longestIdle > 150_000_000L);
                    for (int height = 63; height < 64 + placements; height++)
                        assertTrue("the tower is a contiguous placed column", r.world.blocks.containsKey(new BlockPos(0, height, 0)));
                }
            }
        }

        @Test public void retainedRotationHonorsAuraTellyDisableAndEpoch() throws Exception {
            for (String cause : new String[]{"Aura", "Telly", "Disable", "Epoch"}) {
                try (Run r = new Run()) {
                    try {
                        prepareOccupiedRetention(r, "Telly".equals(cause));
                        long heldAt = r.nanos.get();
                        if ("Aura".equals(cause)) activateValidAuraTarget(r);
                        else if ("Telly".equals(cause)) r.groundTick(true);
                        else if ("Disable".equals(cause)) r.module.setToggled(false);
                        else {
                            r.f.network.getClientPackets().worldChanged();
                            Pulsar.BUS.post(new TickEvent());
                        }
                        for (int frame = 1; frame <= 12; frame++) {
                            r.frameAt(heldAt + frame * 20_000_000L);
                            if (frame == 1 && !"Epoch".equals(cause))
                                assertTrue("ordinary admission loss keeps the original short return tail: " + cause,
                                        r.module.rotationEngine.enabled());
                            assertEquals(0, r.f.player.rotationYaw, 0);
                            assertEquals(0, r.f.player.rotationPitch, 0);
                        }
                        assertFalse("an occupied cell must not keep refreshing after admission is lost: " + cause,
                                r.module.rotationEngine.enabled());
                        assertEquals("rotation retention never grants another occupied-cell click", 0, r.placements(r.drain()));
                        assertTrue(r.world.blocks.containsKey(r.target));
                        if ("Aura".equals(cause)) {
                            assertNotNull("Aura admission remains based on a real loaded target", r.f.aura.getActiveTarget());
                            assertEquals(NScaffold.Status.PAUSED_AURA, r.module.getStatus());
                            r.f.aura.setToggled(false);
                        } else if ("Telly".equals(cause)) {
                            for (int tick = 0; tick < 4; tick++) r.groundTick(false);
                        } else if ("Disable".equals(cause)) r.module.setToggled(true);
                        else assertNull("an epoch change discards the previous scene's rotation", r.module.rotationEngine.currentAim());
                        r.frameAt(heldAt + 260_000_000L);
                        if ("Aura".equals(cause) || "Telly".equals(cause))
                            assertTrue("temporary gates may resume the still-current geometric intent: " + cause,
                                    r.module.rotationEngine.enabled());
                        else
                            assertFalse("a new lifecycle cannot revive a previous occupied-cell intent: " + cause,
                                    r.module.rotationEngine.enabled());
                        assertNull(field(NScaffold.class, "data").get(r.module));
                        assertNull(field(NScaffold.class, "rayPlan").get(r.module));
                        assertEquals(0, r.placements(r.drain()));
                    } finally {
                        if (r.f.aura.isToggled()) r.f.aura.setToggled(false);
                    }
                }
            }
        }

        @Test public void supportCallbackCannotRenewRotationAfterAuraAcquiresTarget() throws Exception {
            try (Run r = new Run()) {
                try {
                    prepareOccupiedRetention(r, false);
                    long heldAt = r.nanos.get();
                    SupportCallbackBlock block = new SupportCallbackBlock();
                    final boolean[] fired = {false};
                    r.world.blocks.put(r.target, block.getDefaultState());
                    block.afterBounds = () -> {
                        if (fired[0]) return;
                        fired[0] = true;
                        try { activateValidAuraTarget(r); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    };
                    // No movement or placement is requested. This shape callback runs inside
                    // rotation-only support validation, after the frame's initial Aura check.
                    r.frameAt(heldAt + 151_000_000L);
                    assertTrue("the real support-shape callback must execute", fired[0]);
                    assertNotNull("the callback acquired a loaded, valid Aura target", r.f.aura.getActiveTarget());
                    assertFalse("a block callback cannot revive an expired Scaffold intent after Aura takes over",
                            r.module.rotationEngine.enabled());
                    for (int frame = 1; frame <= 10; frame++) r.frameAt(heldAt + 151_000_000L + frame * 20_000_000L);
                    assertFalse(r.module.rotationEngine.enabled());
                    assertEquals(0, r.placements(r.drain()));
                    assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                    block.afterBounds = () -> { };
                    r.f.aura.setToggled(false);
                    r.frameAt(heldAt + 400_000_000L);
                    assertTrue("the same support remains usable after the genuine Aura gate is released",
                            r.module.rotationEngine.enabled());
                    assertNull(field(NScaffold.class, "data").get(r.module));
                    assertNull(field(NScaffold.class, "rayPlan").get(r.module));
                    assertEquals(0, r.placements(r.drain()));
                } finally {
                    if (r.f.aura.isToggled()) r.f.aura.setToggled(false);
                }
            }
        }

        private void prepareOccupiedRetention(Run r, boolean telly) throws Exception {
            if (telly) {
                r.module.getModeSetting("Mode").setValue("Telly");
                for (int tick = 0; tick < 4; tick++) r.groundTick(false);
            }
            r.alignRealRay();
            r.update();
            assertEquals("retention begins only after an actual accepted controller placement", 1, r.placements(r.drain()));
            assertTrue(r.world.blocks.containsKey(r.target));
            long placedAt = r.nanos.get();
            for (int frame = 1; frame <= 12; frame++) r.frameAt(placedAt + frame * 20_000_000L);
            assertTrue("the positive control must keep its occupied-cell intent beyond 150 ms", r.module.rotationEngine.enabled());
            assertNull(field(NScaffold.class, "data").get(r.module));
            assertNull(field(NScaffold.class, "rayPlan").get(r.module));
            assertEquals(0, r.placements(r.drain()));
        }

        private void activateValidAuraTarget(Run r) throws Exception {
            RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            target.worldObj = r.world;
            target.setEntityId(91);
            target.setPosition(.5, 64, 2);
            target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 1.7, .8, 65.8, 2.3));
            r.world.loadedEntityList.add(target);
            r.f.aura.setToggled(true);
            field(Aura.class, "target").set(r.f.aura, target);
            field(Aura.class, "worldContext").set(r.f.aura, r.world);
            field(Aura.class, "playerContext").set(r.f.aura, r.f.player);
            assertSame("Aura uses the real scene and target-validity path", target, r.f.aura.getActiveTarget());
        }

        @Test public void relativeFramesReachARealSupportWithinThreeSecondsWithoutMovingTheCamera() throws Exception {
            try (Run r = new Run()) {
                r.module.getModeSetting("Rotations").setValue("Normal");
                r.module.getModeSetting("Smooth mode").setValue("Relative");
                assertEquals("Default", r.module.getModeSetting("Placement mode").getValue());
                assertEquals("Silent", r.module.getModeSetting("Movement correction").getValue());
                assertTrue(r.module.getBooleanSetting("Silent").getValue());
                assertEquals(.5F, r.f.mc.gameSettings.mouseSensitivity, 0);
                assertEquals(.15F, r.module.getNumberSetting("Extra smoothing factor").getValue().floatValue(), 0);
                for (String name : new String[]{"Yaw rotation speed (min)", "Yaw rotation speed (max)",
                        "Pitch rotation speed (min)", "Pitch rotation speed (max)"})
                    assertEquals(name, 180D, r.module.getNumberSetting(name).getValue(), 0);
                final int[] witnessed = {0};
                int ticks = 0, frames = 0, placements = 0;
                long start = r.nanos.get(), period = 1_000_000_000L / 60, nextFrame = start + period;
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!event.isPre() || !event.isOutbound()
                            || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                    assertTrue(r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                    MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                            r.f.runtime.getRotationManager().getEffectivePitch());
                    assertNotNull("Relative must reach the real full-block side, not merely a target angle", hit);
                    assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, hit.typeOfHit);
                    C08PacketPlayerBlockPlacement click = (C08PacketPlayerBlockPlacement) event.getPacket();
                    assertEquals(new BlockPos(-1, 63, 0), hit.getBlockPos());
                    assertEquals(EnumFacing.EAST, hit.sideHit);
                    assertEquals(hit.getBlockPos(), click.getPosition());
                    assertEquals(hit.sideHit.getIndex(), click.getPlacedBlockDirection());
                    witnessed[0]++;
                })) {
                    while (ticks < 60 && !r.world.blocks.containsKey(r.target)) {
                        long tickAt = start + (++ticks) * 50_000_000L;
                        while (nextFrame <= tickAt) {
                            r.frameAt(nextFrame); nextFrame += period; frames++;
                            assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                        }
                        assertEquals("render frames do not perform the controller click", 0, r.placements(r.drain()));
                        r.millis.set(tickAt / 1_000_000L); r.nanos.set(tickAt); r.updateAt(tickAt);
                        List<Object> packets = r.drain(); int movement = -1;
                        for (int index = 0; index < packets.size(); index++) {
                            if (packets.get(index) instanceof C03PacketPlayer) movement = index;
                            if (packets.get(index) instanceof C08PacketPlayerBlockPlacement)
                                assertTrue("the Relative click follows accepted walking", movement >= 0 && movement < index);
                        }
                        assertTrue("every attempt traverses walking transport", movement >= 0);
                        placements += r.placements(packets);
                        assertEquals(.5, r.f.player.posX, 0); assertEquals(64, r.f.player.posY, 0);
                        assertEquals(.5, r.f.player.posZ, 0);
                        assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                    }
                }
                System.out.printf(java.util.Locale.ROOT, "NScaffold Relative fps=60 ticks=%d frames=%d clicks=%d%n",
                        ticks, frames, placements);
                if (!r.world.blocks.containsKey(r.target)) r.bridgeDiagnostic(60, ticks, ticks);
                assertTrue("Relative defaults must cross the reachable side within three seconds of actual frames",
                        r.world.blocks.containsKey(r.target));
                assertEquals(1, placements); assertEquals(1, witnessed[0]);
                assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(1, counter(r.module, "blocksPlacedJump"));
                assertEquals(r.target, field(NScaffold.class, "previousBlock").get(r.module));
                r.frame(20, -10);
                assertEquals("physical mouse input still owns the camera", 3F, r.f.player.rotationYaw, 0);
                assertEquals(1.5F, r.f.player.rotationPitch, 0);
                assertEquals(0, r.placements(r.drain()));
            }
        }

        @Test public void acceleratedFramesEscapeARealSupportMissWithinTwoSeconds() throws Exception {
            for (int fps : new int[]{30, 144}) {
                checkAcceleratedSupportMiss(fps, false, .25F);
                for (float factor : new float[]{.25F, .8F, .9F})
                    checkAcceleratedSupportMiss(fps, true, factor);
            }
        }

        private void checkAcceleratedSupportMiss(int fps, boolean accelerate, float factor) throws Exception {
            try (Run r = new Run()) {
                r.module.getModeSetting("Rotations").setValue("Normal");
                assertEquals("Linear", r.module.getModeSetting("Smooth mode").getValue());
                r.module.getBooleanSetting("Accelerate").setValue(accelerate);
                r.module.getNumberSetting("Yaw accel factor").setValue((double) factor);
                r.module.getNumberSetting("Pitch accel factor").setValue((double) factor);
                assertEquals(factor, r.module.getNumberSetting("Yaw accel factor").getValue().floatValue(), 0);
                assertEquals(factor, r.module.getNumberSetting("Pitch accel factor").getValue().floatValue(), 0);
                assertEquals(.15F, r.module.getNumberSetting("Extra smoothing factor").getValue().floatValue(), 0);
                assertEquals(.5F, r.f.mc.gameSettings.mouseSensitivity, 0);
                assertTrue(r.module.getBooleanSetting("Silent").getValue());
                BlockPos support = new BlockPos(14, 63, -1), destination = new BlockPos(14, 63, 0);
                r.world.blocks.clear(); r.world.blocks.put(support, Blocks.stone.getDefaultState());
                r.f.player.setPosition(15.299980012, 64, .015443532);
                // This exact current pose was reached by the native moving-bridge regression.
                // Seed it once; every subsequent change comes from real render/walking hooks.
                field(DemiseRotationEngine.class, "currentRotation").set(r.module.rotationEngine,
                        new float[]{90.125F, 68.375F});
                r.module.rotationEngine.setRotation(new float[]{91.10596F, 69.322525F}, r.module.rotationSettings.snapshot());
                r.walking();
                assertEquals("seeding a pose must not perform a placement", 0, r.placements(r.drain()));
                assertEquals(15.299980012, r.f.player.getLastReportedPosition().xCoord, 0);
                assertNull("the reached wire angle really misses this almost parallel side", r.ray(90.125F, 68.375F));
                MovingObjectPosition targetHit = r.ray(91.10596F, 69.322525F);
                assertNotNull("the geometric target is an actual reachable full-block face", targetHit);
                assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, targetHit.typeOfHit);
                assertEquals(support, targetHit.getBlockPos()); assertEquals(EnumFacing.SOUTH, targetHit.sideHit);
                final int[] witnessed = {0};
                int ticks = 0, frames = 0, placements = 0;
                long start = r.nanos.get(), period = 1_000_000_000L / fps, nextFrame = start + period;
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!event.isPre() || !event.isOutbound()
                            || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                    assertTrue(r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                    MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                            r.f.runtime.getRotationManager().getEffectivePitch());
                    assertNotNull(hit); assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, hit.typeOfHit);
                    assertEquals(support, hit.getBlockPos()); assertEquals(EnumFacing.SOUTH, hit.sideHit);
                    C08PacketPlayerBlockPlacement click = (C08PacketPlayerBlockPlacement) event.getPacket();
                    assertEquals(support, click.getPosition());
                    assertEquals(EnumFacing.SOUTH.getIndex(), click.getPlacedBlockDirection());
                    witnessed[0]++;
                })) {
                    while (ticks < 40 && !r.world.blocks.containsKey(destination)) {
                        long tickAt = start + (++ticks) * 50_000_000L;
                        while (nextFrame <= tickAt) {
                            r.frameAt(nextFrame); nextFrame += period; frames++;
                            assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                        }
                        assertEquals(0, r.placements(r.drain()));
                        r.millis.set(tickAt / 1_000_000L); r.nanos.set(tickAt); r.updateAt(tickAt);
                        List<Object> packets = r.drain(); int movement = -1;
                        for (int index = 0; index < packets.size(); index++) {
                            if (packets.get(index) instanceof C03PacketPlayer) movement = index;
                            if (packets.get(index) instanceof C08PacketPlayerBlockPlacement)
                                assertTrue("an accepted walking packet precedes the recovery click", movement >= 0 && movement < index);
                        }
                        assertTrue(movement >= 0); placements += r.placements(packets);
                        assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                    }
                }
                System.out.printf(java.util.Locale.ROOT,
                        "NScaffold accel regression accelerate=%s factor=%.2f fps=%d ticks=%d frames=%d clicks=%d%n",
                        accelerate, factor, fps, ticks, frames, placements);
                if (!r.world.blocks.containsKey(destination)) r.bridgeDiagnostic(fps, ticks, ticks);
                assertTrue("the real miss must recover within two seconds; accelerate=" + accelerate
                                + " factor=" + factor + " fps=" + fps,
                        r.world.blocks.containsKey(destination));
                assertEquals(1, placements); assertEquals(1, witnessed[0]);
                assertEquals(2, r.world.blocks.size());
                assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(1, counter(r.module, "blocksPlacedJump"));
                assertEquals(destination, field(NScaffold.class, "previousBlock").get(r.module));
                assertEquals(15.299980012, r.f.player.posX, 0); assertEquals(64, r.f.player.posY, 0);
                assertEquals(.015443532, r.f.player.posZ, 0);
            }
        }

        private void checkMovingBridge(String placementMode) throws Exception {
            double minimumPostWarmupDistance = Double.MAX_VALUE, maximumPostWarmupDistance = 0;
            for (int fps : new int[]{30, 60, 144}) {
                try (Run r = new Run()) {
                    String scenario = placementMode + "/" + fps + " FPS";
                    r.module.getModeSetting("Rotations").setValue("Normal");
                    assertEquals("Normal", r.module.getModeSetting("Mode").getValue());
                    assertEquals("Default", r.module.getModeSetting("Placement mode").getValue());
                    r.module.getModeSetting("Placement mode").setValue(placementMode);
                    assertEquals(placementMode, r.module.getModeSetting("Placement mode").getValue());
                    assertEquals("Silent", r.module.getModeSetting("Movement correction").getValue());
                    assertEquals(20D, r.module.getNumberSetting("Min CPS").getValue(), 0);
                    assertEquals(20D, r.module.getNumberSetting("Max CPS").getValue(), 0);
                    assertTrue(r.module.getBooleanSetting("RayTrace").getValue());
                    assertTrue(r.module.getBooleanSetting("Silent").getValue());
                    r.f.player.inventory.mainInventory[8] = new ItemStack(Blocks.stone, 64);
                    r.f.player.setPosition(.2, 64, .5);
                    r.f.player.motionX = r.f.player.motionZ = 0; r.f.player.motionY = -.08;
                    r.f.player.stepHeight = .6F; r.f.player.jumpMovementFactor = .02F;
                    // Held strafe is ordinary walking at vanilla's .1 ground acceleration;
                    // no fabricated impulse or manual position update advances the bridge.
                    assertEquals(.1F, r.f.player.getAIMoveSpeed(), 0); r.left(true);
                    final double startX = r.f.player.posX;
                    final int[] witnessed = {0};
                    int placements = 0, stoppedAfterWarmup = 0, longestStop = 0, stopRun = 0, lastPlacementTick = -4;
                    int firstPlacementTick = -1;
                    double warmupX = Double.NaN, warmupY = Double.NaN, warmupZ = Double.NaN;
                    long start = r.nanos.get(), nextFrame = start + 1_000_000_000L / fps;
                    long framePeriod = 1_000_000_000L / fps;
                    final int ticks = 100, warmup = 20;
                    try (PacketObserver ignored = new PacketObserver(event -> {
                        if (!event.isPre() || !event.isOutbound()
                                || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                        C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
                        assertTrue("bridge clicks need the current accepted own C03",
                                r.f.runtime.getRotationManager().canUseRotation("nscaffold", false));
                        MovingObjectPosition hit = r.ray(r.f.runtime.getRotationManager().getEffectiveYaw(),
                                r.f.runtime.getRotationManager().getEffectivePitch());
                        assertNotNull("the wire-pose ray must intersect real stored blocks", hit);
                        assertEquals(MovingObjectPosition.MovingObjectType.BLOCK, hit.typeOfHit);
                        assertEquals(hit.getBlockPos(), packet.getPosition());
                        assertEquals(hit.sideHit.getIndex(), packet.getPlacedBlockDirection());
                        BlockPos destination = packet.getPosition().offset(hit.sideHit);
                        assertEquals("building remains at the walking floor height", 63, destination.getY());
                        assertTrue(r.world.getBlockState(destination).getBlock().getMaterial().isReplaceable());
                        witnessed[0]++;
                    })) {
                        for (int tick = 1; tick <= ticks; tick++) {
                            long tickAt = start + tick * 50_000_000L;
                            int physicalBeforeFrames = r.f.player.ticksExisted;
                            while (nextFrame <= tickAt) {
                                r.frameAt(nextFrame);
                                nextFrame += framePeriod;
                            }
                            assertEquals("render frames are not physical placement ticks",
                                    physicalBeforeFrames, r.f.player.ticksExisted);
                            assertEquals("mouse frames may prepare a ray but cannot click", 0, r.placements(r.drain()));
                            double beforeX = r.f.player.posX, beforeZ = r.f.player.posZ;
                            r.physicsAt(tickAt);
                            List<Object> packets = r.drain();
                            int clicks = r.placements(packets), movementIndex = -1;
                            assertTrue("one physical tick grants at most one controller attempt", clicks <= 1);
                            for (int index = 0; index < packets.size(); index++) {
                                if (packets.get(index) instanceof C03PacketPlayer) movementIndex = index;
                                if (packets.get(index) instanceof C08PacketPlayerBlockPlacement)
                                    assertTrue("accepted walking precedes every bridge click", movementIndex >= 0 && movementIndex < index);
                            }
                            assertTrue("each simulated tick traverses the real walking transport", movementIndex >= 0);
                            if (clicks > 0 && firstPlacementTick < 0) firstPlacementTick = tick;
                            if (clicks > 0 && "Legit".equals(placementMode)) {
                                assertTrue("moving Legit respects the four-player-tick interval: " + scenario,
                                        tick - lastPlacementTick >= 4);
                                assertEquals("the real placement resets the native use delay", 4, r.f.mc.rightClickDelayTimer);
                                lastPlacementTick = tick;
                            }
                            placements += clicks;
                            assertEquals("continuous stone support prevents a fall: " + scenario + " tick=" + tick,
                                    64, r.f.player.posY, 1e-8);
                            assertTrue(r.f.player.onGround);
                            assertFalse(r.world.getCollidingBoundingBoxes(r.f.player,
                                    r.f.player.getEntityBoundingBox().offset(0, -.001, 0)).isEmpty());
                            assertEquals(0, r.f.player.rotationYaw, 0); assertEquals(0, r.f.player.rotationPitch, 0);
                            double travelled = Math.hypot(r.f.player.posX - beforeX, r.f.player.posZ - beforeZ);
                            if (tick > warmup && travelled < 1e-7) {
                                stoppedAfterWarmup++; stopRun++; longestStop = Math.max(longestStop, stopRun);
                            } else stopRun = 0;
                            if (tick == warmup) {
                                warmupX = r.f.player.posX; warmupY = r.f.player.posY; warmupZ = r.f.player.posZ;
                            }
                            if (stopRun == 3) r.bridgeDiagnostic(fps, tick, stopRun);
                        }
                    }
                    double distance = r.f.player.posX - startX;
                    // Demise's original 1/90-second frame cap makes initial turning depend on FPS.
                    // Compare the same four seconds after tick 20; startup has its own 25-tick gate.
                    double postWarmupDistance = r.f.player.posX - warmupX;
                    minimumPostWarmupDistance = Math.min(minimumPostWarmupDistance, postWarmupDistance);
                    maximumPostWarmupDistance = Math.max(maximumPostWarmupDistance, postWarmupDistance);
                    System.out.printf(java.util.Locale.ROOT,
                            "NScaffold bridge placement=%s fps=%d ticks=%d distance=%.4f blocks=%d stoppedAfterWarmup=%d longestStop=%d "
                                    + "firstC08Tick=%d warmupTick=%d warmupPosition=(%.6f,%.6f,%.6f) warmupDistance=%.4f postWarmupDistance=%.4f%n",
                            placementMode, fps, ticks, distance, placements, stoppedAfterWarmup, longestStop,
                            firstPlacementTick, warmup, warmupX, warmupY, warmupZ, warmupX - startX, postWarmupDistance);
                    if (distance < 12 || placements < 12 || stoppedAfterWarmup > 4 || longestStop > 2
                            || firstPlacementTick < 1 || firstPlacementTick > 25)
                        r.bridgeDiagnostic(fps, ticks, stopRun);
                    assertEquals("every successful click was checked against actual geometry", placements, witnessed[0]);
                    assertEquals(placements + 1, r.world.blocks.size());
                    assertEquals(64 - placements, r.f.player.inventory.mainInventory[8].stackSize);
                    assertEquals(placements, counter(r.module, "blocksPlacedJump"));
                    assertEquals(ticks, r.inputs);
                    assertTrue("the first real placement must arrive within 25 player ticks: " + scenario,
                            firstPlacementTick >= 1 && firstPlacementTick <= 25);
                    assertTrue("the bridge must make useful progress rather than merely holding the first edge: " + scenario,
                            distance >= 12);
                    assertTrue("the controller must extend the bridge across many distinct blocks: " + scenario, placements >= 12);
                    assertTrue("steady 20 CPS walking must not repeatedly stop at every new edge: " + scenario,
                            stoppedAfterWarmup <= 4);
                    assertTrue("no persistent edge deadlock is allowed: " + scenario, longestStop <= 2);
                }
            }
            assertTrue("frame count must not cause substantially different throughput in the same four seconds after warmup: " + placementMode,
                    maximumPostWarmupDistance - minimumPostWarmupDistance <= 3);
        }

        @SuppressWarnings("unchecked")
        @Test public void reenableDuringSceneOrAdmissionCaptureRejectsThePreviousGeneration() throws Exception {
            for (boolean admission : new boolean[]{false, true}) {
                try (Run r = new Run()) {
                    r.alignRealRay();
                    RuntimeObservation observation = r.f.runtime.getObservation();
                    Field source = field(RuntimeObservation.class, "source");
                    Supplier<RuntimeObservation.SceneSample> original =
                            (Supplier<RuntimeObservation.SceneSample>) source.get(observation);
                    final boolean[] fired = {false};
                    long generation = field(NScaffold.class, "generation").getLong(r.module);
                    source.set(observation, (Supplier<RuntimeObservation.SceneSample>) () -> {
                        boolean placing = false, boundary = false;
                        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                            if (NScaffold.class.getName().equals(frame.getClassName())
                                    && "place".equals(frame.getMethodName())) placing = true;
                            if (admission ? AutomatedActionAdmission.class.getName().equals(frame.getClassName())
                                    && "mayAttempt".equals(frame.getMethodName())
                                    : NScaffold.class.getName().equals(frame.getClassName())
                                    && "scopeCurrent".equals(frame.getMethodName())) boundary = true;
                        }
                        if (!fired[0] && placing && boundary) {
                            fired[0] = true;
                            r.module.setToggled(false);
                            r.module.setToggled(true);
                        }
                        return original.get();
                    });
                    try { r.update(); }
                    finally { source.set(observation, original); }
                    assertTrue("fixture must reach the final " + (admission ? "admission" : "scene") + " callback", fired[0]);
                    assertTrue(r.module.isToggled());
                    assertTrue(field(NScaffold.class, "generation").getLong(r.module) > generation);
                    assertEquals("a revived module cannot inherit the older placement", 0, r.placements(r.drain()));
                    assertFalse(r.world.blocks.containsKey(r.target));
                    assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize);
                    assertEquals(0, counter(r.module, "blocksPlacedJump"));
                    assertNull(field(NScaffold.class, "previousBlock").get(r.module));
                    r.alignRealRay(); r.update();
                    assertEquals("fresh preparation and accepted transport restore normal placement", 1, r.placements(r.drain()));
                    assertTrue(r.world.blocks.containsKey(r.target));
                }
            }
        }

        @Test public void tellyUsesOriginalGroundCountersAndSeparateStraightAndDiagonalWindows() throws Exception {
            try (Run r = new Run()) {
                r.module.getModeSetting("Mode").setValue("Telly"); r.module.getBooleanSetting("RayTrace").setValue(false);
                // The original sliders store floats; the Telly consumer casts rather than rounds.
                r.module.getNumberSetting("Straight telly ticks").setValue(4.75D);
                r.module.getNumberSetting("Diagonal telly ticks").setValue(2.75D);
                r.forward(true); r.f.player.sprint = true;
                r.groundTick(true); r.frame(0, 0); r.updateThenInput(() -> {});
                assertEquals(1, r.f.player.onGroundTicks); assertEquals(0, r.f.player.offGroundTicks);
                assertTrue(r.f.player.movementInput.jump); assertFalse(r.world.blocks.containsKey(r.target));
                for (int tick = 1; tick <= 4; tick++) {
                    r.groundTick(false); r.frame(0, 0); r.updateThenInput(() -> {});
                    assertEquals(tick, r.f.player.offGroundTicks); assertEquals(0, r.f.player.onGroundTicks);
                    assertEquals("straight Telly places at the original fourth airborne update", tick == 4, r.world.blocks.containsKey(r.target));
                    assertFalse(r.f.player.movementInput.jump);
                }
                assertEquals(1, r.placements(r.drain()));
                r.clearPlacedBlock(); r.left(true); r.groundTick(true); r.frame(0, 0); r.update();
                assertEquals(0, r.f.player.offGroundTicks);
                for (int tick = 1; tick <= 2; tick++) {
                    r.groundTick(false); r.frame(0, 0); r.update();
                    assertEquals("diagonal Telly uses its separate two-tick window", tick == 2, r.world.blocks.containsKey(r.target));
                }
                assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void clutchRetainsTheOriginalHundredAndTwoHundredMillisecondWindows() throws Exception {
            try (Run r = new Run()) {
                r.module.getModeSetting("Rotations").setValue("Reverse"); r.module.getBooleanSetting("Static-ify").setValue(true);
                r.module.getBooleanSetting("Clutch").setValue(true);
                r.module.getModeSetting("Clutch criteria").setValue("Prediction");
                r.module.getBooleanSetting("Auto sneak on clutch").setValue(true);
                r.forward(true); r.millis.set(2_000);
                for (int tick = 0; tick < 4; tick++) r.groundTick(true);
                r.module.onDemiseAngle(); assertEquals(NScaffold.Status.CLUTCHING, r.module.getStatus());
                assertFalse("four ground ticks do not arm the five-tick sneak", r.input().isSneak());
                r.groundTick(true); assertTrue(r.input().isSneak());
                r.forward(false);
                r.millis.set(2_100); assertTrue("the boundary is inclusive in TimerUtils", r.input().isSneak());
                r.millis.set(2_101); assertFalse(r.input().isSneak());

                r.forward(true);
                // EntityPlayerSP.getPosition adds 0.5 on X/Z before flooring;
                // at (.5,64,.5) the predicted northern neighbor is (1,63,0).
                r.world.blocks.put(r.f.player.getPosition().offset(EnumFacing.NORTH).down(), Blocks.stone.getDefaultState());
                r.millis.set(2_110); r.module.onDemiseAngle();
                assertFalse(field(NScaffold.class, "startClutch").getBoolean(r.module));
                assertTrue(field(NScaffold.class, "clutching").getBoolean(r.module));
                r.millis.set(2_310); r.module.onDemiseAngle();
                assertTrue("prediction retains the original 200-ms tail", field(NScaffold.class, "clutching").getBoolean(r.module));
                r.millis.set(2_311); r.module.onDemiseAngle();
                assertFalse(field(NScaffold.class, "clutching").getBoolean(r.module));
                assertFalse(r.input().isSneak());
            }
        }

        @Test public void sprintModuleRespectsAllOriginalScaffoldSprintChoices() throws Exception {
            try (Run r = new Run()) {
                Sprint sprint = new Sprint(); sprint.setToggled(true);
                try {
                    r.forward(true);
                    for (String mode : new String[]{"Normal", "Silent", "Ground", "Air", "None"}) {
                        for (boolean grounded : new boolean[]{true, false}) {
                            r.module.getModeSetting("Sprint mode").setValue(mode);
                            r.f.player.onGround = grounded; r.f.player.sprint = true;
                            r.module.onDemiseAngle();
                            boolean expected = "Normal".equals(mode) || "Silent".equals(mode)
                                    || "Ground".equals(mode) && grounded || "Air".equals(mode) && !grounded;
                            assertEquals(mode, expected, r.f.mc.gameSettings.keyBindSprint.isKeyDown());
                            assertEquals(mode, expected, r.f.player.sprint);
                            Pulsar.BUS.post(new EventUpdate());
                            assertEquals("Sprint must not overwrite " + mode, expected, r.f.player.sprint);
                            assertEquals("Silent".equals(mode), NScaffold.silentSprint(r.f.player));
                        }
                    }
                    r.module.setToggled(false); r.f.player.sprint = false;
                    Pulsar.BUS.post(new EventUpdate());
                    assertTrue("ordinary Sprint resumes when NScaffold is disabled", r.f.player.sprint);
                } finally { sprint.setToggled(false); }
            }
        }

        @Test public void successfulPlacementsDriveJumpSneakCountersAndOnlyOneSneakScale() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false);
                r.module.getBooleanSetting("Jump").setValue(true); r.module.getNumberSetting("Blocks to jump").setValue(1D);
                r.module.getBooleanSetting("Sneak").setValue(true); r.module.getNumberSetting("Blocks to sneak").setValue(1D);
                r.forward(true); r.frame(0, 0);
                r.updateThenInput(() -> {});
                assertEquals(1, counter(r.module, "blocksPlacedJump")); assertEquals(1, counter(r.module, "blocksPlacedSneak"));
                assertFalse("input precedes this tick's successful POST click", r.f.player.movementInput.jump);
                assertFalse(r.f.player.movementInput.sneak);
                r.clearPlacedBlock(); r.frame(0, 0); r.updateThenInput(() -> {});
                assertTrue(r.f.player.movementInput.jump); assertTrue(r.f.player.movementInput.sneak);
                assertEquals(.3F, MovementInput.moveForward, 1e-6F);
                assertEquals(2, counter(r.module, "blocksPlacedJump")); assertEquals(2, counter(r.module, "blocksPlacedSneak"));
                r.frame(0, 0); r.updateThenInput(() -> {});
                assertEquals("greater-than resets before equality", 0, counter(r.module, "blocksPlacedJump"));
                assertEquals(0, counter(r.module, "blocksPlacedSneak"));
                assertFalse(r.f.player.movementInput.jump); assertFalse(r.f.player.movementInput.sneak);
                assertEquals(2, r.placements(r.drain()));
            }
        }

        @Test public void towerModesRetainOriginalMotionAndPacketRules() throws Exception {
            try (Run r = new Run()) {
                field(NScaffold.class, "jumpKey").set(r.module, (BooleanSupplier) () -> true);
                for (boolean moving : new boolean[]{false, true}) {
                    for (String mode : new String[]{"Jump", "Vanilla", "PullDown", "NCP"}) {
                        r.module.getModeSetting("Tower").setValue(moving ? "Jump" : mode);
                        r.module.getModeSetting("Tower move").setValue(moving ? mode : "Jump");
                        r.forward(moving); r.f.player.onGround = true; r.f.player.offGroundTicks = 0;
                        r.f.player.setPosition(.5, 64, .5);
                        r.f.player.motionX = .2; r.f.player.motionZ = -.1; r.f.player.motionY = .125;
                        EventMove movement = DemiseRotationHooks.move(r.f.player, .2, .125, -.1);
                        if ("Vanilla".equals(mode)) {
                            assertEquals(moving ? (double) .42F : .42D, r.f.player.motionY, 0);
                            assertEquals("moving Vanilla changes motionY only", moving ? .125D : .42D, movement.getY(), 0);
                        } else {
                            assertEquals(.125, movement.getY(), 0); assertEquals(.125, r.f.player.motionY, 0);
                        }
                        r.motion(EventPhase.PRE);
                        List<Object> sent = r.drain();
                        if ("NCP".equals(mode)) {
                            assertEquals(1, r.placements(sent));
                            C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) sent.get(0);
                            assertEquals(new BlockPos(-1, -1, -1), packet.getPosition());
                            assertEquals(255, packet.getPlacedBlockDirection()); assertNull(packet.getStack());
                            assertEquals((double) .42F, r.f.player.motionY, 0); assertEquals(64D, r.f.player.posY, 0);
                            r.f.player.onGround = false; r.f.player.offGroundTicks = 1;
                            r.f.player.setPosition(.5, 64.05, .5); r.f.player.motionY = .125;
                            r.motion(EventPhase.PRE);
                            assertEquals(64D, r.f.player.posY, 0); assertEquals(0D, r.f.player.motionY, 0);
                            assertEquals(1, r.placements(r.drain()));
                            r.f.player.setPosition(.5, 64.2, .5); r.f.player.motionY = .125;
                            r.motion(EventPhase.PRE);
                            assertEquals(64.2, r.f.player.posY, 0); assertEquals(.125, r.f.player.motionY, 0);
                            assertEquals(1, r.placements(r.drain()));
                        } else {
                            assertEquals(0, r.placements(sent));
                            if ("PullDown".equals(mode)) {
                                assertEquals((double) .42F, r.f.player.motionY, 0);
                                assertEquals(.2 * (double) .95F, r.f.player.motionX, 0);
                                assertEquals(-.1 * (double) .95F, r.f.player.motionZ, 0);
                                r.f.player.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(
                                        new BlockPos(8, 62, 8), EnumFacing.UP.getIndex(), r.f.player.getHeldItem(), .5F, .5F, .5F));
                                assertEquals((double) .42F, r.f.player.motionY, 0);
                                r.f.player.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(
                                        new BlockPos(r.f.player.posX, r.f.player.posY - 1.4, r.f.player.posZ),
                                        EnumFacing.UP.getIndex(), r.f.player.getHeldItem(), .5F, .5F, .5F));
                                assertEquals(-.0784000015258789, r.f.player.motionY, 0);
                                assertEquals(2, r.placements(r.drain()));
                                r.f.player.onGround = false; r.f.player.motionY = .125;
                                r.motion(EventPhase.PRE); assertEquals(.125, r.f.player.motionY, 0);
                            } else if ("Jump".equals(mode)) assertEquals(.125, r.f.player.motionY, 0);
                        }
                    }
                }
                r.module.getModeSetting("Tower").setValue("Vanilla");
                r.module.getModeSetting("Tower move").setValue("Vanilla");
                for (String mode : new String[]{"Breezily", "Telly"}) for (boolean moving : new boolean[]{false, true}) {
                    r.module.getModeSetting("Mode").setValue(mode); r.forward(moving);
                    r.f.player.motionY = .125;
                    assertEquals(.125, DemiseRotationHooks.move(r.f.player, .2, .125, -.1).getY(), 0);
                    r.motion(EventPhase.PRE); assertEquals(.125, r.f.player.motionY, 0);
                    assertTrue(r.drain().isEmpty());
                }
            }
        }

        @Test public void speedModesRetainOriginalArithmeticAndPhysicalKeyConditions() throws Exception {
            try (Run r = new Run()) {
                r.forward(true); r.module.getModeSetting("Speed mode").setValue("Tick");
                r.f.player.onGround = true; r.f.player.ticksExisted = 15;
                r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                r.motion(EventPhase.PRE);
                assertTrue(r.f.player.motionX >= .3 - 1e-12 && r.f.player.motionX < .302);
                assertTrue(r.f.player.motionZ <= -.6 + 1e-12 && r.f.player.motionZ > -.604);
                for (int tick : new int[]{2, 17}) {
                    r.f.player.ticksExisted = tick; r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                    r.motion(EventPhase.PRE);
                    assertEquals(tick == 2 ? .2 * .7 : .2, r.f.player.motionX, 0);
                    assertEquals("ticksExisted - 2 % 15 only matches tick 2", tick == 2 ? -.4 * .7 : -.4, r.f.player.motionZ, 0);
                }
                r.f.player.ticksExisted = 15; r.f.player.onGround = false;
                r.f.player.motionX = .2; r.f.player.motionZ = -.4; r.motion(EventPhase.PRE);
                assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4, r.f.player.motionZ, 0);
                r.f.player.onGround = true; r.motion(EventPhase.POST);
                assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4, r.f.player.motionZ, 0);

                r.module.getModeSetting("Speed mode").setValue("Exempt");
                for (float strafe : new float[]{0, 1}) {
                    MovementInput.moveStrafe = strafe; r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                    r.motion(EventPhase.PRE);
                    assertEquals(0D, r.f.player.motionX, 1e-12);
                    assertEquals((double) (strafe == 0 ? .1591F : .1565F), r.f.player.motionZ, 1e-12);
                }
                r.f.player.onGround = false; r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                r.motion(EventPhase.PRE); assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4, r.f.player.motionZ, 0);

                r.module.getModeSetting("Speed mode").setValue("Diagonal");
                for (boolean grounded : new boolean[]{true, false}) {
                    r.f.player.onGround = grounded; r.forward(true); r.left(false);
                    r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                    double increase = grounded ? (0.1299999676734952 - 0.12739998266255503) + 1E-7 - 1E-8
                            : (0.025999999334873708 - 0.025479999685988748) - 1E-8;
                    r.motion(EventPhase.PRE);
                    assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4 + increase, r.f.player.motionZ, 0);
                    r.left(true); r.f.player.motionX = .2; r.f.player.motionZ = -.4;
                    r.motion(EventPhase.PRE);
                    assertEquals("two physical keys do not qualify", .2, r.f.player.motionX, 0);
                    assertEquals(-.4, r.f.player.motionZ, 0);
                    r.forward(false); r.left(false); r.f.player.moveForward = 1;
                    r.motion(EventPhase.PRE); assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4, r.f.player.motionZ, 0);
                    r.press(r.f.mc.gameSettings.keyBindRight, true); r.motion(EventPhase.PRE);
                    assertEquals(.2 - increase, r.f.player.motionX, 1e-8); assertEquals(-.4, r.f.player.motionZ, 1e-8);
                    r.press(r.f.mc.gameSettings.keyBindRight, false);
                }
                r.module.getModeSetting("Speed mode").setValue("None");
                r.f.player.motionX = .2; r.f.player.motionZ = -.4; r.motion(EventPhase.PRE);
                assertEquals(.2, r.f.player.motionX, 0); assertEquals(-.4, r.f.player.motionZ, 0);
            }
        }

        @Test public void zeroThresholdsKeepTheOriginalAirborneComparisonOrder() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("Jump").setValue(true); r.module.getNumberSetting("Blocks to jump").setValue(0D);
                r.module.getBooleanSetting("Sneak").setValue(true); r.module.getNumberSetting("Blocks to sneak").setValue(0D);
                r.forward(true); r.groundTick(false);
                MovementInputEvent groundedOnly = r.input();
                assertTrue("airborne counter reset is followed by the original zero equality", groundedOnly.isJump());
                assertFalse(groundedOnly.isSneak());
                r.module.getBooleanSetting("Only sneak on ground").setValue(false);
                MovementInputEvent airborneSneak = r.input(); assertTrue(airborneSneak.isJump()); assertTrue(airborneSneak.isSneak());
                r.forward(false); assertFalse(r.input().isJump());
            }
        }

        @Test public void disableAndReenableRestoreThenRecaptureTheOriginalSlot() throws Exception {
            try (Run r = new Run()) {
                r.frame(0, 0); r.frame(0, 0);
                assertEquals(8, r.f.player.inventory.currentItem); assertNotNull(r.module.rotationEngine.currentAim());
                r.press(r.f.mc.gameSettings.keyBindUseItem, true);
                r.module.setToggled(false);
                assertEquals(2, r.f.player.inventory.currentItem); assertEquals(2, NScaffold.displayedSlot(r.f.player));
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown()); assertNull(r.module.getActivitySnapshot());
                assertNotNull("ordinary disable retains Demise's rotation return tail", r.module.rotationEngine.currentAim());
                r.f.player.inventory.currentItem = 4;
                r.module.setToggled(true); r.frame(0, 0);
                assertEquals(8, r.f.player.inventory.currentItem); assertEquals(4, NScaffold.displayedSlot(r.f.player));
                r.module.setToggled(false); assertEquals(4, r.f.player.inventory.currentItem);
                assertEquals(0, counter(r.module, "blocksPlacedJump")); assertEquals(0, counter(r.module, "blocksPlacedSneak"));
            }
        }

        @Test public void worldChangeDisablesUnconditionallyAndClearsTheFrameLifetime() throws Exception {
            try (Run r = new Run()) {
                r.frame(0, 0); r.frame(0, 0);
                assertNotNull(r.module.rotationEngine.currentAim()); assertEquals(8, r.f.player.inventory.currentItem);
                r.press(r.f.mc.gameSettings.keyBindUseItem, true);
                Pulsar.BUS.post(new WorldEvent.Unload(r.world));
                assertFalse(r.module.isToggled()); assertEquals(2, r.f.player.inventory.currentItem);
                assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown());
                assertNull(r.module.rotationEngine.currentAim()); assertNull(r.module.targetBlock);
                assertNull(r.module.getActivitySnapshot()); assertEquals(NScaffold.Status.OFF, r.module.getStatus());
                r.update(); r.frame(0, 0); assertEquals(0, r.placements(r.drain()));
            }
        }

        @Test public void reenableDuringSlotTransportRejectsThePreviousPlacementGeneration() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0);
                final boolean[] fired = {false};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!fired[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C09PacketHeldItemChange) {
                        fired[0] = true; r.module.setToggled(false); r.module.setToggled(true);
                    }
                })) { r.update(); }
                assertTrue(fired[0]); assertTrue(r.module.isToggled());
                assertFalse(r.world.blocks.containsKey(r.target)); assertEquals(0, r.placements(r.drain()));
                assertEquals(0, counter(r.module, "blocksPlacedJump"));
                r.frame(0, 0); r.update();
                assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void menuOpeningDuringPlacementTransportRejectsTheAction() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0);
                final GuiChat screen = RotationBehaviorHookTest.bare(GuiChat.class);
                final boolean[] fired = {false};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!fired[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                        fired[0] = true; r.f.mc.currentScreen = screen;
                    }
                })) { r.update(); }
                assertTrue(fired[0]); assertFalse(r.world.blocks.containsKey(r.target)); assertEquals(0, r.placements(r.drain()));
                assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(0, counter(r.module, "blocksPlacedJump")); assertNull(r.module.getActivitySnapshot());
                r.f.mc.currentScreen = null;
            }
        }

        @Test public void epochChangeDuringPlacementTransportRejectsTheAction() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0);
                final boolean[] fired = {false};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!fired[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                        fired[0] = true; r.f.network.getClientPackets().worldChanged();
                    }
                })) { r.update(); }
                assertTrue(fired[0]); assertFalse(r.world.blocks.containsKey(r.target)); assertEquals(0, r.placements(r.drain()));
                assertEquals(0, counter(r.module, "blocksPlacedJump")); assertNull(r.module.getActivitySnapshot());
            }
        }

        @Test public void controllerReplacementDuringTransportRejectsTheOldController() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0);
                final boolean[] fired = {false};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!fired[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                        fired[0] = true; r.f.mc.playerController = new PlayerControllerMP(r.f.mc, r.f.player.sendQueue);
                    }
                })) { r.update(); }
                assertTrue(fired[0]); assertFalse(r.world.blocks.containsKey(r.target)); assertEquals(0, r.placements(r.drain()));
                assertEquals(16, r.f.player.inventory.mainInventory[8].stackSize); assertEquals(0, counter(r.module, "blocksPlacedJump"));
                r.update(); assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void animationReenableCannotCreditAnOldPlacementToTheNewRun() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0);
                final boolean[] fired = {false};
                try (PacketObserver ignored = new PacketObserver(event -> {
                    if (!fired[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C0APacketAnimation) {
                        fired[0] = true; r.module.setToggled(false); r.module.setToggled(true);
                    }
                })) { r.update(); }
                assertTrue(fired[0]); assertTrue(r.module.isToggled());
                assertTrue("the old click was already accepted before animation", r.world.blocks.containsKey(r.target));
                assertEquals(1, r.placements(r.drain())); assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);
                assertEquals(0, counter(r.module, "blocksPlacedJump")); assertEquals(0, counter(r.module, "blocksPlacedSneak"));
                assertNotNull(r.module.getActivitySnapshot()); assertEquals(0, r.module.getActivitySnapshot().getPlacementCount());
                r.clearPlacedBlock(); r.frame(0, 0); r.update();
                assertEquals(1, counter(r.module, "blocksPlacedJump")); assertEquals(1, r.module.getActivitySnapshot().getPlacementCount());
                assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void activityFactsAreReadOnlyExpireAndRefreshWithoutInventingPlacements() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0); r.update(); r.drain();
                ScaffoldActivitySnapshot snapshot = r.module.getActivitySnapshot(); assertNotNull(snapshot);
                assertEquals(1, snapshot.getPlacementCount()); assertEquals(79, snapshot.getBlockCount());
                assertEquals(ScaffoldActivitySnapshot.Status.PLACING, snapshot.getStatus());
                Object storedView = field(NScaffold.class, "activityView").get(r.module);
                int reads = r.world.blockReads, rays = r.world.rayReads; float[] pose = r.module.rotationEngine.rotation();
                for (int read = 0; read < 100; read++) assertSame(snapshot, r.module.getActivitySnapshot());
                assertEquals(reads, r.world.blockReads); assertEquals(rays, r.world.rayReads);
                assertArrayEquals(pose, r.module.rotationEngine.rotation(), 0);
                assertTrue(r.drain().isEmpty()); assertSame(storedView, field(NScaffold.class, "activityView").get(r.module));
                r.activityNanos.addAndGet(ScaffoldActivitySnapshot.TTL_NANOS);
                assertSame(snapshot, r.module.getActivitySnapshot());
                r.activityNanos.incrementAndGet(); assertNull(r.module.getActivitySnapshot());
                assertSame(storedView, field(NScaffold.class, "activityView").get(r.module));
                r.update();
                ScaffoldActivitySnapshot refreshed = r.module.getActivitySnapshot(); assertNotNull(refreshed);
                assertNotSame(snapshot, refreshed); assertEquals(1, refreshed.getPlacementCount());
                // Update now prepares a fresh aim before POST. The occupied destination
                // rejects the click, so PLACING changes to ALIGNING without a new placement.
                assertEquals(ScaffoldActivitySnapshot.Status.ALIGNING, refreshed.getStatus());
                assertEquals(snapshot.getSequence() + 1, refreshed.getSequence());
                assertEquals(snapshot.getBlockCount(), refreshed.getBlockCount());
                assertEquals(r.activityNanos.get(), refreshed.getObservedAtNanos());
                assertEquals(0, r.placements(r.drain()));
                r.update();
                ScaffoldActivitySnapshot sameFacts = r.module.getActivitySnapshot(); assertNotNull(sameFacts);
                assertEquals("refreshing unchanged facts must keep their sequence", refreshed.getSequence(), sameFacts.getSequence());
                assertEquals(1, sameFacts.getPlacementCount());
                assertEquals(ScaffoldActivitySnapshot.Status.ALIGNING, sameFacts.getStatus());
                assertEquals(0, r.placements(r.drain()));
            }
        }

        @Test public void activityCannotReviveAfterAMenuRoundTripOrConnectionEpoch() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false); r.frame(0, 0); r.update();
                assertNotNull(r.module.getActivitySnapshot());
                Object stored = field(NScaffold.class, "activityView").get(r.module);
                r.f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiChat.class);
                r.f.runtime.getObservation().captureScene(); assertNull(r.module.getActivitySnapshot());
                r.f.mc.currentScreen = null; r.f.runtime.getObservation().captureScene();
                assertNull("an observed menu round trip cannot revive old facts", r.module.getActivitySnapshot());
                assertSame(stored, field(NScaffold.class, "activityView").get(r.module));
                r.update(); assertNotNull(r.module.getActivitySnapshot());
                r.f.network.getClientPackets().worldChanged(); assertNull(r.module.getActivitySnapshot());
            }
        }
    }
}
