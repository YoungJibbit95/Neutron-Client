package me.youngjibbit.core.world;

import me.youngjibbit.core.network.NetworkTestSupport;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.LivingEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.world.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real fresh-key input, module events and subsequent Downwards consumer.
 * World storage and physical keys are synthetic; no native window or server.
 * The living hook stops only after the production input/sprint/rotation hooks.
 */
public class AdaptiveBridgeInputSafetyFlowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final class ReachedInputBoundary extends RuntimeException { }

    public static class InputPlayer extends RotationBehaviorHookTest.Player {
        FoodStats food;
        @Override public boolean isSneaking() {
            return movementInput != null && movementInput.sneak;
        }
        @Override public FoodStats getFoodStats() { return food; }
        @Override protected boolean pushOutOfBlocks(double x, double y, double z) { return false; }
        @Override public boolean isRidingHorse() { throw new ReachedInputBoundary(); }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean isWet() { return false; }
        @Override protected boolean canTriggerWalking() { return false; }
        @Override protected void doBlockCollisions() { }
        @Override protected void updateFallState(double y, boolean grounded, Block block, BlockPos pos) { }
    }

    public static class Scene extends ArsenicScaffoldFlowTest.Scene {
        @Override public boolean isFlammableWithin(AxisAlignedBB box) { return false; }
    }

    /** Profiling would initialize unrelated OptiFine rendering globals in this headless fixture. */
    private static final class CollisionProfiler extends Profiler {
        @Override public void startSection(String name) { }
        @Override public void endSection() { }
    }

    static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final InputPlayer player = RotationBehaviorHookTest.bare(InputPlayer.class);
        final Scene scene = RotationBehaviorHookTest.bare(Scene.class);
        final GameSettings settings;

        Run() throws Exception {
            ScaffoldClientFlowTest.Scene original = ScaffoldClientFlowTest.prepare(f, .05, .05);
            scene.blocks = original.blocks;
            scene.border = original.border;
            scene.motionTime = original.motionTime;
            ScaffoldClientFlowTest.set(World.class, scene, "loadedEntityList", new java.util.ArrayList<>());
            ScaffoldClientFlowTest.set(World.class, scene, "theProfiler", new CollisionProfiler());
            for (Class<?> type = RotationBehaviorHookTest.Player.class;
                 type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    field.set(player, field.get(f.player));
                }
            }
            player.worldObj = scene;
            player.width = .6f;
            player.height = 1.8f;
            player.inventory = new InventoryPlayer(player);
            player.inventory.mainInventory[3] = f.player.inventory.mainInventory[3];
            player.capabilities = new PlayerCapabilities();
            player.food = new FoodStats();
            player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            f.mc.thePlayer = player;
            f.mc.theWorld = scene;
            ScaffoldClientFlowTest.set(Minecraft.class, f.mc, "renderViewEntity", player);
            settings = f.mc.gameSettings;
            settings.keyBindForward = new KeyBinding("bridge.forward", 17, "bridge");
            settings.keyBindBack = new KeyBinding("bridge.back", 31, "bridge");
            settings.keyBindLeft = new KeyBinding("bridge.left", 30, "bridge");
            settings.keyBindRight = new KeyBinding("bridge.right", 32, "bridge");
            settings.keyBindJump = new KeyBinding("bridge.jump", 57, "bridge");
            settings.keyBindSneak = new KeyBinding("bridge.sneak", 42, "bridge");
            settings.keyBindSprint = new KeyBinding("bridge.sprint", 29, "bridge");
            player.movementInput = new MovementInputFromOptions(settings);
            MovementInput.moveForward = MovementInput.moveStrafe = 0;
            f.scaffold.getModeSetting("Place Timing").setValue("Post");
            f.scaffold.getModeSetting("movefix").setValue("Aus");
        }

        void input() { step(false, () -> { }); }

        void step(boolean walking, Runnable afterInput) {
            scene.motionTime.addAndGet(50_000_000L);
            f.runtime.getScaffoldPlacementService().beginTick();
            RuntimeObservation observation = f.runtime.getObservation();
            observation.beginOuterTick();
            try {
                observation.observePlayerStep(player, () -> {
                    try {
                        player.onLivingUpdate();
                        fail("fixture must reach the boundary after the real input consumer");
                    } catch (ReachedInputBoundary expected) { }
                    afterInput.run();
                    if (walking) player.onUpdateWalkingPlayer();
                    return true;
                }, step -> step.getAsBoolean());
            } finally {
                observation.endOuterTick();
            }
        }

        void position(double x, double z) {
            player.posX = x; player.posY = 64; player.posZ = z;
            player.setEntityBoundingBox(new AxisAlignedBB(x - .3, 64, z - .3,
                    x + .3, 65.8, z + .3));
            player.onGround = true;
        }

        List<Object> drain() {
            List<Object> packets = new ArrayList<>();
            Object packet;
            while ((packet = f.channel.readOutbound()) != null) packets.add(packet);
            return packets;
        }

        void adaptive(String safety) {
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            assertNotNull("production edge policy must be registered", f.scaffold.getModeSetting("Bridge Edge Safety"));
            f.scaffold.getModeSetting("Bridge Edge Safety").setValue(safety);
        }

        void approachingEdge(String safety) {
            adaptive(safety);
            position(-.1, .5);
            player.rotationYaw = -90;
            player.motionX = .6;
            player.motionY = player.motionZ = 0;
            settings.keyBindForward.pressed = true;
            // No success can hide a guard failure by adding a floor during this test.
            player.inventory.mainInventory[3] = null;
        }

        @Override public void close() throws Exception {
            try {
                f.scaffold.onDisable();
                for (KeyBinding key : new KeyBinding[]{settings.keyBindForward, settings.keyBindBack,
                        settings.keyBindLeft, settings.keyBindRight, settings.keyBindJump,
                        settings.keyBindSneak, settings.keyBindSprint}) key.pressed = false;
                MovementInput.moveForward = MovementInput.moveStrafe = 0;
            } finally {
                f.close();
            }
        }
    }

    private void legacyConfig(Run r, String approach) throws Exception {
        Path directory = temp.newFolder().toPath();
        String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":"
                + "{\"scaffold_approach\":\"" + approach + "\",\"downwards\":true}}}}";
        Files.write(directory.resolve("OldBridge.json"), saved.getBytes(StandardCharsets.UTF_8));
        ConfigManager configs = new ConfigManager(directory,
                Collections.<Module>singletonList(r.f.scaffold), () -> false, message -> { });
        assertTrue(configs.getLastError(), configs.loadConfig("OldBridge"));
        assertEquals(approach, r.f.scaffold.getModeSetting("Scaffold Approach").getValue());
        assertTrue(r.f.scaffold.getBooleanSetting("Downwards").getValue());
        assertEquals("loading must not rewrite the old settings file", saved,
                new String(Files.readAllBytes(directory.resolve("OldBridge.json")), StandardCharsets.UTF_8));
        // This regression isolates the old Downwards consumer from new edge policy.
        if (r.f.scaffold.getModeSetting("Bridge Edge Safety") != null)
            r.f.scaffold.getModeSetting("Bridge Edge Safety").setValue("Off");
    }

    @Test public void savedAdaptiveDownwardsCannotUndoPhysicalSneakAfterTheInputEvent() throws Exception {
        try (Run r = new Run()) {
            legacyConfig(r, "Adaptive Bridge");
            assertFalse("legacy Downwards is hidden in Adaptive Bridge",
                    r.f.scaffold.getBooleanSetting("Downwards").isVisible());
            r.settings.keyBindForward.pressed = r.settings.keyBindSneak.pressed = true;
            AtomicReference<float[]> eventInput = new AtomicReference<>();
            Listener<MovementInputEvent> observe = new Listener<>(event -> eventInput.set(
                    new float[]{event.getForward(), event.getStrafe(), event.isSneak() ? 1 : 0}));
            Pulsar.BUS.subscribe(observe);
            r.f.scaffold.onEnable();
            try {
                r.input();
                assertNotNull("the real fresh input event must run", eventInput.get());
                assertEquals(.3f, eventInput.get()[0], 1e-6f);
                assertEquals(1, eventInput.get()[2], 0);
                PlacementTarget target = (PlacementTarget) ScaffoldOwnerContinuityTest.field(
                        Scaffold.class, "currentTarget").get(r.f.scaffold);
                assertNotNull("a real validated target must exercise the former suppression condition", target);
                assertTrue(r.f.runtime.getScaffoldPlacementService().isValid(target));
                assertTrue("the later Downwards consumer must preserve physical sneak", r.player.movementInput.sneak);
                assertEquals("sneak slowdown must not be undone or applied twice", .3f,
                        r.player.movementInput.moveForward, 1e-6f);
                assertTrue(r.settings.keyBindSneak.isKeyDown());
                r.f.scaffold.onDisable();
                r.input();
                assertTrue(r.player.movementInput.sneak);
                assertEquals(.3f, r.player.movementInput.moveForward, 1e-6f);
                assertTrue(r.settings.keyBindSneak.isKeyDown());
            } finally {
                Pulsar.BUS.unsubscribe(observe);
            }
        }
    }

    @Test public void savedNeutronDownwardsRetainsItsExistingSuppressionAndScaling() throws Exception {
        try (Run r = new Run()) {
            legacyConfig(r, "Neutron");
            r.scene.blocks.put(new BlockPos(-1, 62, 0), Blocks.stone.getDefaultState());
            r.settings.keyBindForward.pressed = r.settings.keyBindSneak.pressed = true;
            r.f.scaffold.onEnable();
            r.input();
            PlacementTarget target = (PlacementTarget) ScaffoldOwnerContinuityTest.field(
                    Scaffold.class, "currentTarget").get(r.f.scaffold);
            assertNotNull("legacy downwards has its own valid lower placement target", target);
            assertEquals(62, target.getDestination().getY());
            assertTrue(r.f.runtime.getScaffoldPlacementService().isValid(target));
            assertFalse("Neutron retains its explicit downwards input behavior", r.player.movementInput.sneak);
            assertEquals(1, r.player.movementInput.moveForward, 1e-6f);
            assertTrue("only the fresh sample changes, never the physical key", r.settings.keyBindSneak.isKeyDown());
        }
    }

    @Test public void visibleFutureFaceStartsRotationBeforeTheOccupiedFootCellNeedsPlacement() throws Exception {
        try (Run r = new Run()) {
            r.adaptive("Smart");
            r.scene.blocks.clear();
            r.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            r.scene.blocks.put(new BlockPos(1, 63, -1), Blocks.stone.getDefaultState());
            r.position(.8, .5);
            r.player.rotationYaw = -90;
            r.player.motionX = .15;
            r.settings.keyBindForward.pressed = true;
            PlacementTarget visible = r.f.runtime.getScaffoldPlacementService().planFace(
                    new BlockPos(1, 63, -1), net.minecraft.util.EnumFacing.SOUTH, null);
            assertNotNull("positive control: the future side support is visible from the real eyes", visible);
            assertFalse(r.f.runtime.getScaffoldPlacementService().stillNeeded(visible,
                    new PlacementContext(false, false, 63)));
            r.f.scaffold.onEnable();
            final int[] prepared = {0};
            final boolean[] changedPitch = {false};
            for (int tick = 0; tick < 8; tick++) {
                r.step(true, () -> {
                    RotationManager.MovementRotation rotation = r.f.runtime.getRotationManager().getPreparedMovement();
                    assertNotNull("early preparation must use the real rotation hook", rotation);
                    assertEquals("scaffold", rotation.source);
                    prepared[0]++;
                    changedPitch[0] |= Math.abs(rotation.pitch - r.player.rotationPitch) > .001;
                });
                List<Object> packets = r.drain();
                assertEquals(1, packets.stream().filter(C03PacketPlayer.class::isInstance).count());
                assertEquals("early rotation never grants placement over an occupied foot cell", 0,
                        packets.stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
                assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
            }
            assertEquals(8, prepared[0]);
            assertTrue("the future face changes the actual prepared rotation", changedPitch[0]);
            assertEquals(Blocks.air, r.scene.getBlockState(new BlockPos(1, 63, 0)).getBlock());
        }
    }

    @Test public void smartGuardUsesVanillaEdgeCollisionDespiteExistingVelocityAndThenReleases() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Smart");
            // .4 velocity + .18 fresh input stays inside the normal .6 probe bound.
            r.player.motionX = .4;
            r.f.scaffold.onEnable();
            r.step(true, () -> {
                assertTrue("grounded guard must reach the real player sneak consumer", r.player.isSneaking());
                assertFalse("no synthetic key ownership", r.settings.keyBindSneak.isKeyDown());
                assertEquals("input protection does not erase an existing impulse", .4, r.player.motionX, 1e-12);
                r.player.moveEntity(r.player.motionX, -.08, r.player.motionZ);
                assertTrue("native collision must leave some body support", r.player.getEntityBoundingBox().minX < 0);
                assertFalse(r.scene.getCollidingBoundingBoxes(r.player,
                        r.player.getEntityBoundingBox().offset(0, -1, 0)).isEmpty());
                assertEquals(64, r.player.posY, 1e-12);
            });
            assertEquals(0, r.drain().stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
            r.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            r.scene.blocks.put(new BlockPos(1, 63, 0), Blocks.stone.getDefaultState());
            r.player.motionX = .1;
            r.player.onGround = true;
            r.step(true, () -> {
                assertFalse("new safe floor must release the guard on the next fresh sample", r.player.isSneaking());
                assertEquals(1, r.player.movementInput.moveForward, 1e-6);
                assertFalse(r.settings.keyBindSneak.isKeyDown());
            });
        }
    }

    @Test public void freshConnectionWithLowerEpochResumesRealPlanningAndPlacement() throws Exception {
        try (Run r = new Run()) {
            r.adaptive("Off");
            r.scene.blocks.clear();
            r.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            r.scene.blocks.put(new BlockPos(1, 63, -1), Blocks.stone.getDefaultState());
            r.position(.8, .5);
            r.player.rotationYaw = -90;
            r.player.motionX = .15;
            r.settings.keyBindForward.pressed = true;
            for (int boundary = 0; boundary < 3; boundary++)
                r.f.network.getClientPackets().worldChanged();
            long previousEpoch = r.f.network.getClientPackets().getContextVersion();
            r.f.scaffold.onEnable();
            r.step(true, () -> assertEquals("control: old connection has productive bridge intent",
                    "scaffold", r.f.runtime.getRotationManager().getPreparedMovement().source));
            assertEquals(0, r.drain().stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());

            r.f.network.closeChannel(new ChatComponentText("synthetic bridge reconnect"));
            NetworkManager next = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(next);
            try {
                NetHandlerPlayClient play = new NetHandlerPlayClient(null, null, next, null);
                ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, play, "gameController", r.f.mc);
                play.clientWorldController = r.scene;
                next.configureClientPackets(new NetworkTestSupport.MainThread(), play::observeAcceptedOutbound);
                next.setNetHandler(play);
                ScaffoldClientFlowTest.set(EntityPlayerSP.class, r.player, "sendQueue", play);
                r.f.mc.playerController = new PlayerControllerMP(r.f.mc, play);
                assertNotSame(r.f.network.getClientPackets(), next.getClientPackets());
                assertTrue("a fresh pipeline restarts below the prior pipeline's epoch",
                        next.getClientPackets().getContextVersion() < previousEpoch);
                r.step(true, () -> {
                    RotationManager.MovementRotation prepared = r.f.runtime.getRotationManager().getPreparedMovement();
                    assertNotNull("new connection must acquire a real movement frame", prepared);
                    assertEquals("scaffold", prepared.source);
                    assertTrue(r.f.runtime.getRotationManager().isConnection(next.getClientPackets()));
                });
                int earlyMovements = 0, earlyPlacements = 0;
                Object earlyPacket;
                while ((earlyPacket = channel.readOutbound()) != null) {
                    if (earlyPacket instanceof C03PacketPlayer) earlyMovements++;
                    if (earlyPacket instanceof C08PacketPlayerBlockPlacement) earlyPlacements++;
                }
                assertEquals("the fresh connection accepts its early movement", 1, earlyMovements);
                assertEquals("reconnect must not authorize placement over the occupied foot cell", 0, earlyPlacements);
                assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                r.position(1.05, .5);
                int placements = 0;
                for (int tick = 0; tick < 35 && placements == 0; tick++) {
                    r.step(true, () -> { });
                    Object packet;
                    while ((packet = channel.readOutbound()) != null)
                        if (packet instanceof C08PacketPlayerBlockPlacement) placements++;
                }
                assertEquals("reconnection must reach native placement after accepted movement", 1, placements);
                assertEquals(Blocks.stone, r.scene.getBlockState(new BlockPos(1, 63, 0)).getBlock());
                assertEquals(63, r.player.inventory.mainInventory[3].stackSize);
                assertNull("the closed old transport must remain unused", r.f.channel.readOutbound());
            } finally {
                next.closeChannel(new ChatComponentText("end synthetic bridge reconnect"));
                channel.finish();
                while (channel.readOutbound() != null) { }
            }
        }
    }

    @Test public void offPolicyLeavesResidualVelocityAndDoesNotPretendInputCanStopAFall() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Off");
            r.settings.keyBindForward.pressed = false;
            r.f.scaffold.onEnable();
            r.step(true, () -> {
                assertEquals(0, r.player.movementInput.moveForward, 0);
                assertFalse(r.player.isSneaking());
                r.player.moveEntity(r.player.motionX, -.08, 0);
                assertTrue("control: zero input does not remove the existing horizontal displacement",
                        r.player.getEntityBoundingBox().minX > 0);
                assertTrue(r.scene.getCollidingBoundingBoxes(r.player,
                        r.player.getEntityBoundingBox().offset(0, -1, 0)).isEmpty());
            });
        }
    }

    @Test public void counterSteeringCannotHideExistingVelocityFromNativeEdgeProtection() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Smart");
            r.player.motionX = .5;
            r.settings.keyBindForward.pressed = false;
            r.settings.keyBindBack.pressed = true;
            assertTrue("control: existing velocity alone would leave the last supporting block",
                    r.scene.getCollidingBoundingBoxes(r.player,
                            r.player.getEntityBoundingBox().offset(.5, -1, 0)).isEmpty());
            assertFalse("control: subtracting the full predicted reverse input would look supported",
                    r.scene.getCollidingBoundingBoxes(r.player,
                            r.player.getEntityBoundingBox().offset(.32, -1, 0)).isEmpty());
            AtomicReference<Float> sampledForward = new AtomicReference<>();
            Listener<MovementInputEvent> observe = new Listener<>(event -> sampledForward.set(event.getForward()));
            Pulsar.BUS.subscribe(observe);
            r.f.scaffold.onEnable();
            try {
                r.step(true, () -> {
                    assertNotNull("the production input sampler must actually read the reverse key", sampledForward.get());
                    assertEquals(-1, sampledForward.get(), 0);
                    assertTrue("reverse input must not hide an unsafe existing displacement", r.player.isSneaking());
                    assertTrue(r.settings.keyBindBack.isKeyDown());
                    assertFalse(r.settings.keyBindSneak.isKeyDown());
                    assertEquals("guard must preserve the real impulse", .5, r.player.motionX, 1e-12);
                    r.player.moveEntity(r.player.motionX, -.08, 0);
                    assertTrue("the real vanilla edge clamp must retain support", r.player.getEntityBoundingBox().minX < 0);
                    assertFalse(r.scene.getCollidingBoundingBoxes(r.player,
                            r.player.getEntityBoundingBox().offset(0, -1, 0)).isEmpty());
                    assertEquals(64, r.player.posY, 1e-12);
                });
                assertEquals(0, r.drain().stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
            } finally {
                Pulsar.BUS.unsubscribe(observe);
            }
        }
    }

    @Test public void freshDirectionChangeAfterPreInvalidatesItsOriginalPlacementIntent() throws Exception {
        try (Run r = new Run()) {
            r.adaptive("Smart");
            r.player.rotationYaw = -90;
            r.settings.keyBindForward.pressed = true;
            AtomicReference<PlacementTarget> original = new AtomicReference<>();
            Listener<LivingEvent> reverse = new Listener<>(event -> {
                try {
                    original.set((PlacementTarget) ScaffoldOwnerContinuityTest.field(
                            Scaffold.class, "frameTarget").get(r.f.scaffold));
                } catch (Exception error) { throw new AssertionError(error); }
                r.settings.keyBindForward.pressed = false;
                r.settings.keyBindBack.pressed = true;
            });
            Pulsar.BUS.subscribe(reverse);
            r.f.scaffold.onEnable();
            try {
                r.step(true, () -> {
                    assertNotNull("control: PRE captured a valid intent before the new key sample", original.get());
                    assertTrue("fresh reverse key must reach the real input consumer", r.player.movementInput.moveForward < 0);
                    try {
                        assertNull("old direction must not retain the PRE placement token",
                                ScaffoldOwnerContinuityTest.field(Scaffold.class, "frameTarget").get(r.f.scaffold));
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                assertEquals(0, r.drain().stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
                assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
            } finally { Pulsar.BUS.unsubscribe(reverse); }
        }
    }

    @Test public void higherPriorityPreparedOwnerKeepsItsFreshInputUnmodified() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Smart");
            Listener<EventRotation> foreign = new Listener<>(event ->
                    r.f.runtime.getRotationManager().request("foreign-input-test", 100, 45, 20));
            Pulsar.BUS.subscribe(foreign);
            r.f.scaffold.onEnable();
            try {
                r.step(true, () -> {
                    RotationManager.MovementRotation prepared = r.f.runtime.getRotationManager().getPreparedMovement();
                    assertNotNull(prepared);
                    assertEquals("foreign-input-test", prepared.source);
                    assertFalse("bridge must not steal the selected foreign movement owner", r.player.isSneaking());
                    assertEquals(1, r.player.movementInput.moveForward, 0);
                    assertEquals(.6, r.player.motionX, 1e-12);
                });
                assertEquals(0, r.drain().stream().filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
            } finally { Pulsar.BUS.unsubscribe(foreign); }
        }
    }

    @Test public void physicalJumpAndAirborneMotionRemainUnderThePlayersControl() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Smart");
            r.settings.keyBindJump.pressed = true;
            r.f.scaffold.onEnable();
            r.step(true, () -> {
                assertTrue("physical jump is not an automated guard-owned action", r.player.movementInput.jump);
                assertTrue(r.settings.keyBindJump.isKeyDown());
            });
            r.settings.keyBindJump.pressed = false;
            r.player.onGround = false;
            r.player.motionY = -.2;
            r.step(true, () -> {
                assertFalse("airborne guard may not pin the player through sneak", r.player.isSneaking());
                assertEquals(1, r.player.movementInput.moveForward, 0);
                assertEquals(.6, r.player.motionX, 1e-12);
                assertEquals(-.2, r.player.motionY, 1e-12);
            });
        }
    }

    @Test public void disableOrFocusLossCannotCarryASyntheticSneakIntoTheNextInput() throws Exception {
        for (boolean disable : new boolean[]{false, true}) {
            try (Run r = new Run()) {
                r.approachingEdge("Smart");
                r.f.scaffold.onEnable();
                r.step(true, () -> assertTrue("control: guard is active", r.player.isSneaking()));
                if (disable) r.f.scaffold.setToggled(false);
                else r.f.mc.inGameHasFocus = false;
                r.step(true, () -> {
                    assertFalse("synthetic sneak is limited to a valid fresh sample", r.player.isSneaking());
                    assertEquals(1, r.player.movementInput.moveForward, 0);
                    assertFalse(r.settings.keyBindSneak.isKeyDown());
                });
            }
        }
    }

    @Test public void contextChangeInsideCollisionProbeCannotPublishItsOldGuard() throws Exception {
        try (Run r = new Run()) {
            r.approachingEdge("Smart");
            r.f.scaffold.onEnable();
            r.scene.beforeCollision = () -> r.f.scaffold.setToggled(false);
            r.step(true, () -> {
                assertFalse("control: collision callback actually ran", r.f.scaffold.isToggled());
                assertFalse(r.player.isSneaking());
                assertEquals(1, r.player.movementInput.moveForward, 0);
                assertFalse(r.settings.keyBindSneak.isKeyDown());
            });
        }
    }
}
