package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventMove;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.movement.Sprint;
import me.youngjibbit.module.impl.world.NScaffold;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

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
    @Test public void updatePlacesThroughTheGuardedControllerBeforeFreshInputAndNeverFromPost() throws Exception {
        isolated("updatePlacesThroughTheGuardedControllerBeforeFreshInputAndNeverFromPost");
    }
    @Test public void rayTraceDispatchUsesOriginalSupportFaceHitAndPreviousBlockRules() throws Exception {
        isolated("rayTraceDispatchUsesOriginalSupportFaceHitAndPreviousBlockRules");
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

    public static class Scene extends ScaffoldClientFlowTest.Scene {
        int blockReads, rayReads;
        BiFunction<Vec3,Vec3,MovingObjectPosition> rayResponse;
        @Override public IBlockState getBlockState(BlockPos position) {
            blockReads++;
            return super.getBlockState(position);
        }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to, boolean liquid, boolean empty, boolean last) {
            rayReads++;
            return rayResponse == null ? super.rayTraceBlocks(from, to, liquid, empty, last) : rayResponse.apply(from, to);
        }
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

    private static final class Run implements AutoCloseable {
        final float originalForward = MovementInput.moveForward, originalStrafe = MovementInput.moveStrafe;
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final NScaffold module = new NScaffold();
        final AtomicLong millis = new AtomicLong(1_000);
        final AtomicLong activityNanos = new AtomicLong(1_000_000_000L);
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
            f.mc.theWorld = world; f.player.worldObj = world;
            nanos = world.motionTime; nanos.set(1_000_000_000L);
            f.player.capabilities = new PlayerCapabilities();
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
            field(NScaffold.class, "clutchTime").setLong(module, millis.get());
            field(NScaffold.class, "sneakTime").setLong(module, millis.get());
            field(NScaffold.class, "rotationEngine").set(module, new DemiseRotationEngine(millis::get, nanos::get, (min, max) -> min));
            // Center avoids thousands of geometry samples here; Normal has dedicated pinned-source fixtures.
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
        void step(Runnable body) {
            f.runtime.getObservation().beginOuterTick();
            try {
                f.runtime.getObservation().observePlayerStep(f.player, () -> { body.run(); return true; }, step -> step.getAsBoolean());
            } finally { f.runtime.getObservation().endOuterTick(); }
        }
        void update() { step(() -> DemiseRotationHooks.update(f.player)); }
        void updateThenInput(Runnable afterUpdate) {
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
                assertEquals(2, r.f.player.inventory.currentItem);
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

        @Test public void updatePlacesThroughTheGuardedControllerBeforeFreshInputAndNeverFromPost() throws Exception {
            try (Run r = new Run()) {
                r.module.getBooleanSetting("RayTrace").setValue(false);
                r.forward(true); r.frame(0, 0);
                assertFalse(r.world.blocks.containsKey(r.target));
                r.updateThenInput(() -> {
                    assertEquals("fresh input has not been sampled yet", 0, r.inputs);
                    assertEquals(Blocks.stone, r.world.getBlockState(r.target).getBlock());
                    assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);
                });
                assertEquals(NScaffold.Status.BUILDING, r.module.getStatus());
                List<Object> packets = r.drain(); assertEquals(1, r.placements(packets));
                int slot = -1, placement = -1, animation = -1;
                for (int index = 0; index < packets.size(); index++) {
                    Object packet = packets.get(index);
                    if (packet instanceof C09PacketHeldItemChange) slot = index;
                    if (packet instanceof C08PacketPlayerBlockPlacement) placement = index;
                    if (packet instanceof C0APacketAnimation) animation = index;
                }
                assertTrue(slot >= 0); assertTrue(slot < placement); assertTrue(placement < animation);
                assertEquals(8, ((C09PacketHeldItemChange) packets.get(slot)).getSlotId());
                r.clearPlacedBlock();
                EventMotion post = new EventMotion(.5, 64, .5, 0, 0, true);
                post.setEventPhase(EventPhase.POST); post.setAccepted(true);
                Pulsar.BUS.post(post); Pulsar.BUS.post(post);
                assertFalse(r.world.blocks.containsKey(r.target)); assertEquals(0, r.placements(r.drain()));
                r.update(); assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(1, r.placements(r.drain()));
            }
        }

        @Test public void rayTraceDispatchUsesOriginalSupportFaceHitAndPreviousBlockRules() throws Exception {
            try (Run r = new Run()) {
                assertTrue("the original RayTrace default stays enabled", r.module.getBooleanSetting("RayTrace").getValue());
                r.frame(0, 0);
                DemiseScaffoldGeometry.PlaceData data = (DemiseScaffoldGeometry.PlaceData) field(NScaffold.class, "data").get(r.module);
                BlockPos support = new BlockPos(-1, 63, 0);
                assertEquals(support, data.blockPos); assertEquals(EnumFacing.EAST, data.facing);

                BlockPos wrongSupport = new BlockPos(1, 63, 0);
                r.world.blocks.put(wrongSupport, Blocks.stone.getDefaultState());
                r.world.rayResponse = (from, to) -> new MovingObjectPosition(new Vec3(1.25, 64, .75), EnumFacing.UP, wrongSupport);
                r.update(); assertEquals(0, r.placements(r.drain()));
                assertEquals(wrongSupport.up(), field(NScaffold.class, "previousBlock").get(r.module));
                assertEquals(0, counter(r.module, "blocksPlacedJump"));

                r.module.getBooleanSetting("Strict rayTrace").setValue(true);
                r.world.rayResponse = (from, to) -> new MovingObjectPosition(new Vec3(-.25, 64, .75), EnumFacing.UP, support);
                r.update(); assertEquals(0, r.placements(r.drain()));
                assertEquals(support.up(), field(NScaffold.class, "previousBlock").get(r.module));
                assertFalse(r.world.blocks.containsKey(support.up()));

                r.module.getBooleanSetting("Strict rayTrace").setValue(false);
                r.update(); List<Object> nonStrict = r.drain(); assertEquals(1, r.placements(nonStrict));
                C08PacketPlayerBlockPlacement above = null;
                for (Object packet : nonStrict) if (packet instanceof C08PacketPlayerBlockPlacement)
                    above = (C08PacketPlayerBlockPlacement) packet;
                assertNotNull(above); assertEquals(support, above.getPosition());
                assertEquals(EnumFacing.UP.getIndex(), above.getPlacedBlockDirection());
                assertEquals(.75F, above.getPlacedBlockOffsetX(), 0);
                assertEquals(1F, above.getPlacedBlockOffsetY(), 0); assertEquals(.75F, above.getPlacedBlockOffsetZ(), 0);
                assertTrue(r.world.blocks.containsKey(support.up())); assertFalse(r.world.blocks.containsKey(r.target));
                assertEquals(1, counter(r.module, "blocksPlacedJump"));

                BlockPos occupied = support.south(); r.world.blocks.put(occupied, Blocks.stone.getDefaultState());
                r.world.rayResponse = (from, to) -> new MovingObjectPosition(new Vec3(-.25, 63.75, 1), EnumFacing.SOUTH, support);
                r.update(); assertEquals(0, r.placements(r.drain()));
                assertEquals("previousBlock follows the ray even when the controller refuses an occupied target",
                        occupied, field(NScaffold.class, "previousBlock").get(r.module));
                assertEquals(1, counter(r.module, "blocksPlacedJump")); assertEquals(15, r.f.player.inventory.mainInventory[8].stackSize);

                r.module.getBooleanSetting("Strict rayTrace").setValue(true);
                r.world.rayResponse = (from, to) -> new MovingObjectPosition(new Vec3(0, 63.25, .75), EnumFacing.EAST, support);
                r.update(); List<Object> strict = r.drain(); assertEquals(1, r.placements(strict));
                C08PacketPlayerBlockPlacement exact = null;
                for (Object packet : strict) if (packet instanceof C08PacketPlayerBlockPlacement)
                    exact = (C08PacketPlayerBlockPlacement) packet;
                assertNotNull(exact); assertEquals(EnumFacing.EAST.getIndex(), exact.getPlacedBlockDirection());
                assertEquals(1F, exact.getPlacedBlockOffsetX(), 0);
                assertEquals(.25F, exact.getPlacedBlockOffsetY(), 0); assertEquals(.75F, exact.getPlacedBlockOffsetZ(), 0);
                assertTrue(r.world.blocks.containsKey(r.target)); assertEquals(2, counter(r.module, "blocksPlacedJump"));
                assertEquals(r.target, field(NScaffold.class, "previousBlock").get(r.module));
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
                assertTrue(r.f.player.movementInput.jump); assertTrue(r.f.player.movementInput.sneak);
                assertEquals(.3F, MovementInput.moveForward, 1e-6F);
                r.clearPlacedBlock(); r.frame(0, 0); r.updateThenInput(() -> {});
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
                assertEquals(snapshot.getSequence(), refreshed.getSequence());
                assertEquals(r.activityNanos.get(), refreshed.getObservedAtNanos());
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
