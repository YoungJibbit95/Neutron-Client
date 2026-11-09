package me.youngjibbit.core.world;

import me.youngjibbit.core.network.NetworkTestSupport;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.LivingEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Real fresh input, rotation collection, accepted C03 and native placement for both A2 profiles. */
public class AdaptiveBridgeRotationFlowTest {
    private static final String[] PROFILES = {"Adaptive", "Godbridge"};
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final class PacketHook implements Subscriber {
        @Subscribe final Listener<EventPacket> listener;
        PacketHook(Consumer<EventPacket> action) { listener = new Listener<>((EventPacket event) -> action.accept(event)); }
    }

    private static final class Connection implements AutoCloseable {
        final NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(network);
        Connection(AdaptiveBridgeInputSafetyFlowTest.Run r) throws Exception {
            NetHandlerPlayClient play = new NetHandlerPlayClient(null, null, network, null);
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, play, "gameController", r.f.mc);
            play.clientWorldController = r.scene;
            network.configureClientPackets(new NetworkTestSupport.MainThread(), play::observeAcceptedOutbound);
            network.setNetHandler(play);
            ScaffoldClientFlowTest.set(EntityPlayerSP.class, r.player, "sendQueue", play);
            r.f.mc.playerController = new PlayerControllerMP(r.f.mc, play);
        }
        @Override public void close() {
            network.closeChannel(new ChatComponentText("end bridge profile reconnect"));
            channel.finish();
            drain(channel);
        }
    }

    private AdaptiveBridgeInputSafetyFlowTest.Run run(String profile) throws Exception {
        AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run();
        try {
            Path directory = temp.newFolder().toPath();
            String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{"
                    + "\"scaffold_approach\":\"Adaptive Bridge\",\"rotations\":false,"
                    + "\"bridge_rotation_profile\":\"" + profile + "\",\"bridge_rotation_variation\":0,"
                    + "\"bridge_edge_safety\":\"Off\",\"movefix\":\"Aus\",\"swing\":false}}}}";
            Path file = directory.resolve("SavedRotationFlag.json");
            Files.write(file, saved.getBytes(StandardCharsets.UTF_8));
            ConfigManager configs = new ConfigManager(directory, Collections.<Module>singletonList(r.f.scaffold),
                    () -> false, message -> { });
            assertTrue(configs.getLastError(), configs.loadConfig("SavedRotationFlag"));
            assertFalse("saved legacy checkbox remains false", r.f.scaffold.getBooleanSetting("Rotations").getValue());
            assertFalse("bridge hides the legacy checkbox", r.f.scaffold.getBooleanSetting("Rotations").isVisible());
            assertEquals(saved, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return r;
        } catch (Exception | AssertionError failure) {
            r.close();
            throw failure;
        }
    }

    private void currentGap(AdaptiveBridgeInputSafetyFlowTest.Run r) {
        r.position(.5, .5);
        r.player.rotationYaw = 90;
        r.settings.keyBindBack.pressed = true;
    }

    private void visibleFutureFace(AdaptiveBridgeInputSafetyFlowTest.Run r) {
        r.scene.blocks.clear();
        r.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        r.scene.blocks.put(new BlockPos(1, 63, -1), Blocks.stone.getDefaultState());
        r.position(.8, .5);
        r.player.rotationYaw = -90;
        r.player.motionX = .15;
        r.settings.keyBindForward.pressed = true;
    }

    private static PlacementTarget frame(AdaptiveBridgeInputSafetyFlowTest.Run r) {
        try { return (PlacementTarget) ScaffoldOwnerContinuityTest.field(Scaffold.class, "frameTarget").get(r.f.scaffold); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> packets = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) packets.add(packet);
        return packets;
    }

    private List<Object> walk(AdaptiveBridgeInputSafetyFlowTest.Run r) {
        r.step(true, () -> { });
        return r.drain();
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static void noActions(List<Object> packets) {
        assertEquals("no unauthorized placement", 0, count(packets, C08PacketPlayerBlockPlacement.class));
        assertEquals("no placement slot change", 0, count(packets, C09PacketHeldItemChange.class));
    }

    private void complete(AdaptiveBridgeInputSafetyFlowTest.Run r, EmbeddedChannel channel,
                          BlockPos destination) {
        float[] previous = null;
        for (int tick = 0; tick < 100; tick++) {
            AtomicReference<PlacementTarget> original = new AtomicReference<>();
            r.step(true, () -> original.set(frame(r)));
            List<Object> packets = drain(channel);
            assertEquals(1, count(packets, C03PacketPlayer.class));
            int movement = -1;
            for (int i = 0; i < packets.size(); i++) {
                Object packet = packets.get(i);
                if (packet instanceof C03PacketPlayer) {
                    movement = i;
                    C03PacketPlayer c03 = (C03PacketPlayer) packet;
                    if (c03.getRotating()) {
                        assertTrue(Float.isFinite(c03.getYaw()));
                        assertTrue(Float.isFinite(c03.getPitch()));
                        if (previous != null) {
                            assertTrue("yaw cap still applies at the accepted packet",
                                    Math.abs(MathHelper.wrapAngleTo180_float(c03.getYaw() - previous[0]))
                                            <= r.f.scaffold.getNumberSetting("Yaw Speed").getValue() + .151);
                            assertTrue("pitch cap still applies at the accepted packet",
                                    Math.abs(c03.getPitch() - previous[1])
                                            <= r.f.scaffold.getNumberSetting("Pitch Speed").getValue() + .151);
                        }
                        previous = new float[]{c03.getYaw(), c03.getPitch()};
                    }
                }
                if (packet instanceof C08PacketPlayerBlockPlacement) {
                    assertTrue("POST must follow the original accepted C03", movement >= 0 && movement < i);
                    assertEquals(1, count(packets, C08PacketPlayerBlockPlacement.class));
                    assertNotNull("a real PRE intent is required", original.get());
                    C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) packet;
                    assertEquals(original.get().getBlockPos(), c08.getPosition());
                    assertEquals(original.get().getFacing().getIndex(), c08.getPlacedBlockDirection());
                    assertEquals(destination, original.get().getDestination());
                    assertEquals(Blocks.stone, r.scene.getBlockState(destination).getBlock());
                    assertEquals(63, r.player.inventory.mainInventory[3].stackSize);
                    return;
                }
            }
        }
        fail("profile must reach actual native placement within the bounded acquisition window");
    }

    @Test public void bothProfilesPlaceWithSavedLegacyRotationsOffAndKeepTheAcceptedPose() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                currentGap(r);
                r.f.scaffold.getNumberSetting("Yaw Speed").setValue(11D);
                r.f.scaffold.getNumberSetting("Pitch Speed").setValue(7D);
                r.f.scaffold.onEnable();
                complete(r, r.f.channel, new BlockPos(0, 63, 0));
                RotationManager rotation = r.f.runtime.getRotationManager();
                assertEquals("scaffold", rotation.getConfirmedOwner());
                float yaw = rotation.getEffectiveYaw(), pitch = rotation.getEffectivePitch();
                assertTrue("native placement acquired the downward face", pitch > 5);
                for (int idle = 0; idle < 4; idle++) {
                    noActions(walk(r));
                    assertEquals(yaw, rotation.getEffectiveYaw(), 0);
                    assertEquals(pitch, rotation.getEffectivePitch(), 0);
                }
                assertEquals(90, r.player.rotationYaw, 0);
                assertEquals(0, r.player.rotationPitch, 0);
            }
        }
    }

    @Test public void heldVisibleFaceSurvivesAlternativeSupportAndDuplicatePreWithoutEarlyPlacement() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                visibleFutureFace(r);
                r.f.scaffold.onEnable();
                AtomicReference<Vec3> first = new AtomicReference<>();
                for (int tick = 0; tick < 12; tick++) {
                    if (tick == 1) {
                        r.scene.blocks.put(new BlockPos(2, 63, 0), Blocks.stone.getDefaultState());
                        PlacementTarget alternate = r.f.runtime.getScaffoldPlacementService().planFace(
                                new BlockPos(2, 63, 0), EnumFacing.WEST, null);
                        assertNotNull("control: a second visible, reachable face is genuinely available", alternate);
                        assertEquals(new BlockPos(1, 63, 0), alternate.getDestination());
                    }
                    r.step(true, () -> {
                        PlacementTarget target = frame(r);
                        assertNotNull("future visible target enters the real rotation consumer", target);
                        assertEquals(new BlockPos(1, 63, -1), target.getBlockPos());
                        assertEquals(EnumFacing.SOUTH, target.getFacing());
                        Vec3 hit = target.getHitVec();
                        if (first.get() == null) first.set(hit);
                        assertEquals(first.get().xCoord, hit.xCoord, 1e-12);
                        assertEquals(first.get().yCoord, hit.yCoord, 1e-12);
                        assertEquals(first.get().zCoord, hit.zCoord, 1e-12);
                        RotationManager.MovementRotation prepared = r.f.runtime.getRotationManager().getPreparedMovement();
                        assertNotNull(prepared);
                        assertEquals("scaffold", prepared.source);
                        for (int repeat = 0; repeat < 5; repeat++)
                            Pulsar.BUS.post(new EventMotion(r.player.posX, r.player.posY, r.player.posZ,
                                    r.player.rotationYaw, r.player.rotationPitch, true));
                        assertSame("duplicate PRE cannot replace the frozen face", target, frame(r));
                        assertSame("duplicate PRE cannot advance another motion frame", prepared,
                                r.f.runtime.getRotationManager().getPreparedMovement());
                    });
                    noActions(r.drain());
                    assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                }
            }
        }
    }

    @Test public void directionChangedByFreshKeysRevokesBothProfilesOriginalIntent() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                visibleFutureFace(r);
                r.f.scaffold.onEnable();
                for (int tick = 0; tick < 6; tick++) noActions(walk(r));
                AtomicReference<PlacementTarget> before = new AtomicReference<>();
                Listener<LivingEvent> reverse = new Listener<>(event -> {
                    before.set(frame(r));
                    r.settings.keyBindForward.pressed = false;
                    r.settings.keyBindBack.pressed = true;
                });
                Pulsar.BUS.subscribe(reverse);
                try {
                    r.step(true, () -> {
                        assertNotNull("PRE captured the previous direction", before.get());
                        assertEquals(-1, r.player.movementInput.moveForward, 0);
                        assertNull("original face cannot survive the new direction", frame(r));
                    });
                    noActions(r.drain());
                    assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                } finally { Pulsar.BUS.unsubscribe(reverse); }
            }
        }
    }

    @Test public void bothProfilesReconnectAtLowerEpochAndAcquireBeforeNativePlacement() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                visibleFutureFace(r);
                for (int i = 0; i < 3; i++) r.f.network.getClientPackets().worldChanged();
                long oldEpoch = r.f.network.getClientPackets().getContextVersion();
                r.f.scaffold.onEnable();
                for (int tick = 0; tick < 6; tick++) noActions(walk(r));
                assertEquals("scaffold", r.f.runtime.getRotationManager().getConfirmedOwner());
                r.f.network.closeChannel(new ChatComponentText("bridge profile reconnect"));
                try (Connection next = new Connection(r)) {
                    assertTrue(next.network.getClientPackets().getContextVersion() < oldEpoch);
                    r.step(true, () -> {
                        RotationManager.MovementRotation prepared = r.f.runtime.getRotationManager().getPreparedMovement();
                        assertNotNull(prepared);
                        assertEquals("scaffold", prepared.source);
                        assertTrue(r.f.runtime.getRotationManager().isConnection(next.network.getClientPackets()));
                    });
                    List<Object> early = drain(next.channel);
                    assertEquals(1, count(early, C03PacketPlayer.class));
                    noActions(early);
                    r.position(1.05, .5);
                    complete(r, next.channel, new BlockPos(1, 63, 0));
                    assertNull(r.f.channel.readOutbound());
                }
            }
        }
    }

    @Test public void foreignPreparedOwnerBlocksPlacementAndReacquisitionUsesItsAcceptedBasis() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                visibleFutureFace(r);
                r.f.scaffold.onEnable();
                for (int tick = 0; tick < 6; tick++) noActions(walk(r));
                RotationManager rotation = r.f.runtime.getRotationManager();
                Listener<EventRotation> foreign = new Listener<>(event -> rotation.request("bridge-foreign", 100, 45, 20));
                Pulsar.BUS.subscribe(foreign);
                try {
                    r.step(true, () -> assertEquals("bridge-foreign", rotation.getPreparedMovement().source));
                    noActions(r.drain());
                    assertEquals("bridge-foreign", rotation.getConfirmedOwner());
                } finally { Pulsar.BUS.unsubscribe(foreign); }
                final float acceptedYaw = rotation.getEffectiveYaw(), acceptedPitch = rotation.getEffectivePitch();
                r.step(true, () -> {
                    RotationManager.MovementRotation prepared = rotation.getPreparedMovement();
                    assertNotNull(prepared);
                    assertEquals("scaffold", prepared.source);
                    assertEquals("fresh dynamics starts at the accepted owner basis", acceptedYaw, prepared.yaw, .151);
                    assertEquals(acceptedPitch, prepared.pitch, .151);
                });
                noActions(r.drain());
                assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                assertEquals(-90, r.player.rotationYaw, 0);
            }
        }
    }

    @Test public void cancelledOrRewrittenC03CannotAuthorizeEitherProfilesPlacement() throws Exception {
        for (String profile : PROFILES) for (boolean cancel : new boolean[]{true, false}) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                currentGap(r);
                AtomicInteger rejectedMovements = new AtomicInteger();
                PacketHook rejected = new PacketHook(event -> {
                    if (!event.isOutbound() || !event.isPre() || !(event.getPacket() instanceof C03PacketPlayer)) return;
                    rejectedMovements.incrementAndGet();
                    if (cancel) event.cancel();
                    else {
                        C03PacketPlayer original = (C03PacketPlayer) event.getPacket();
                        event.setPacket(new C03PacketPlayer.C05PacketPlayerLook(original.getYaw() + 15,
                                original.getPitch(), original.isOnGround()));
                    }
                });
                Pulsar.BUS.subscribe(rejected);
                r.f.scaffold.onEnable();
                try {
                    for (int tick = 0; tick < 18; tick++) {
                        List<Object> packets = walk(r);
                        noActions(packets);
                        assertEquals("control: the actual movement PRE was cancelled or rewritten", tick + 1, rejectedMovements.get());
                        assertEquals("the existing source ticket rejects a rewritten pose before transport", 0,
                                count(packets, C03PacketPlayer.class));
                        assertFalse(r.f.runtime.getRotationManager().isMotionAccepted());
                    }
                    assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                    assertEquals(Blocks.air, r.scene.getBlockState(new BlockPos(0, 63, 0)).getBlock());
                } finally { Pulsar.BUS.unsubscribe(rejected); }
            }
        }
    }

    @Test public void profileChangedDuringC03RevokesOldPostAndTheNewProfileCanPlaceLater() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                currentGap(r);
                AtomicBoolean changed = new AtomicBoolean();
                PacketHook change = new PacketHook(event -> {
                    if (event.isOutbound() && event.isPre() && event.getPacket() instanceof C03PacketPlayer
                            && changed.compareAndSet(false, true)) {
                        assertNotNull("control: old profile has a real PRE intent", frame(r));
                        r.f.scaffold.getModeSetting("Bridge Rotation Profile").setValue(
                                "Adaptive".equals(profile) ? "Godbridge" : "Adaptive");
                    }
                });
                Pulsar.BUS.subscribe(change);
                r.f.scaffold.onEnable();
                try {
                    noActions(walk(r));
                    assertTrue(changed.get());
                    assertEquals(64, r.player.inventory.mainInventory[3].stackSize);
                    assertNull(frame(r));
                } finally { Pulsar.BUS.unsubscribe(change); }
                complete(r, r.f.channel, new BlockPos(0, 63, 0));
            }
        }
    }

    @Test public void disablingEitherProfileKeepsOnlyTheExistingSmoothRotationReturn() throws Exception {
        for (String profile : PROFILES) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = run(profile)) {
                currentGap(r);
                r.f.scaffold.getBooleanSetting("Smooth Rotation Stop").setValue(true);
                r.f.scaffold.getNumberSetting("Return Ticks").setValue(3D);
                r.f.scaffold.onEnable();
                complete(r, r.f.channel, new BlockPos(0, 63, 0));
                RotationManager rotation = r.f.runtime.getRotationManager();
                float startPitch = rotation.getEffectivePitch();
                assertTrue(startPitch > 5);
                r.f.scaffold.setToggled(false);
                float previous = startPitch, last = startPitch;
                boolean intermediate = false;
                for (int tick = 0; tick < 6; tick++) {
                    List<Object> packets = walk(r);
                    noActions(packets);
                    for (Object packet : packets) if (packet instanceof C03PacketPlayer) {
                        C03PacketPlayer c03 = (C03PacketPlayer) packet;
                        if (!c03.getRotating()) continue;
                        last = c03.getPitch();
                        assertTrue("return approaches the unchanged camera without reacquiring", last <= previous + .151);
                        intermediate |= last > .3 && last < startPitch - .3;
                        previous = last;
                    }
                }
                assertTrue("existing smooth return emits an intermediate accepted pose", intermediate);
                assertEquals(r.player.rotationPitch, last, .3);
                assertEquals(63, r.player.inventory.mainInventory[3].stackSize);
                assertEquals(90, r.player.rotationYaw, 0);
                assertEquals(0, r.player.rotationPitch, 0);
            }
        }
    }
}
