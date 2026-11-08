package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Actual opt-in Scaffold PRE -> accepted C03 -> POST -> native ItemBlock/controller.
 * Only the headless world, physical input and network channel are synthetic.
 * This deliberately uses the production hooks rather than the pure planner alone. */
public class AdaptiveBridgeLiveFlowTest {
    private static final BlockPos DESTINATION = new BlockPos(0, 63, 0);
    private static final BlockPos ORIGINAL_SUPPORT = new BlockPos(-1, 63, 0);

    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final ScaffoldClientFlowTest.Scene scene = ScaffoldClientFlowTest.prepare(f, .5, .48);
        final RotationManager rotation = f.runtime.getRotationManager();

        Run() throws Exception {
            f.player.movementInput = new MovementInput();
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            // A saved legacy PRE timing must not bypass this approach's POST gate.
            f.scaffold.getModeSetting("Place Timing").setValue("Pre");
            f.scaffold.onEnable();
        }

        List<Object> walk() {
            scene.motionTime.addAndGet(50_000_000L);
            f.runtime.getScaffoldPlacementService().beginTick();
            ScaffoldClientFlowTest.ordinaryWalking(f);
            List<Object> sent = new ArrayList<>();
            Object packet;
            while ((packet = f.channel.readOutbound()) != null) sent.add(packet);
            return sent;
        }

        PlacementTarget intent() {
            try {
                return (PlacementTarget) ScaffoldOwnerContinuityTest.field(
                        Scaffold.class, "frameTarget").get(f.scaffold);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override public void close() throws Exception {
            f.scaffold.onDisable();
            f.close();
        }
    }

    private static final class PacketHook implements Subscriber {
        java.util.function.Consumer<EventPacket> callback = event -> {};
        @Subscribe final Listener<EventPacket> listener = new Listener<>(event -> callback.accept(event));
    }

    private static final class PriorityHook implements Subscriber {
        @Subscribe final Listener<EventRotation> listener;
        PriorityHook(RotationManager rotation) {
            listener = new Listener<>(event ->
                    rotation.request("aura", RotationManager.SCAFFOLD_PRIORITY + 10, 90, 0));
        }
    }

    private static boolean movementPre(EventPacket event) {
        return event.isOutbound() && event.isPre() && event.getPacket() instanceof C03PacketPlayer;
    }

    private static long count(List<Object> sent, Class<?> type) {
        long n = 0;
        for (Object packet : sent) if (type.isInstance(packet)) n++;
        return n;
    }

    private static void noPlacementOrSlot(List<Object> packets) {
        assertEquals("No C08 after an invalid/rejected C03", 0, count(packets, C08PacketPlayerBlockPlacement.class));
        assertEquals("No placement slot spoof after an invalid/rejected C03", 0, count(packets, C09PacketHeldItemChange.class));
    }

    @Test public void acceptedPostPlacesOnlyTheCapturedPreSupportFaceAndStack() throws Exception {
        try (Run r = new Run()) {
            AtomicReference<PlacementTarget> captured = new AtomicReference<>();
            PacketHook hook = new PacketHook();
            hook.callback = event -> {
                if (!movementPre(event)) return;
                PlacementTarget pre = r.intent();
                assertNotNull("Adaptive Bridge must publish an actual PRE intent", pre);
                assertSame(r.scene, pre.world);
                assertSame(r.f.player, pre.player);
                assertSame(r.f.player.inventory.mainInventory[3], pre.stack);
                assertEquals(3, pre.getSlot());
                assertEquals(0, pre.getSelectedSlot());
                assertEquals(DESTINATION, pre.getDestination());
                assertEquals(ORIGINAL_SUPPORT, pre.getBlockPos());
                captured.set(pre);
            };
            Pulsar.BUS.subscribe(hook);
            try {
                boolean placed = false;
                for (int tick = 0; tick < 35 && !placed; tick++) {
                    List<Object> sent = r.walk();
                    assertEquals(1, count(sent, C03PacketPlayer.class));
                    int c03 = -1, c08 = -1;
                    for (int i = 0; i < sent.size(); i++) {
                        if (sent.get(i) instanceof C03PacketPlayer) c03 = i;
                        if (sent.get(i) instanceof C08PacketPlayerBlockPlacement) c08 = i;
                    }
                    if (c08 >= 0) {
                        assertTrue("POST placement must follow the transport-accepted C03", c03 >= 0 && c08 > c03);
                        assertEquals(1, count(sent, C08PacketPlayerBlockPlacement.class));
                        assertNotNull(captured.get());
                        assertEquals(ORIGINAL_SUPPORT, captured.get().getBlockPos());
                        assertEquals(DESTINATION, captured.get().getDestination());
                        assertEquals(Blocks.stone, r.scene.getBlockState(DESTINATION).getBlock());
                        placed = true;
                    }
                }
                assertTrue("Adaptive Bridge must reach the real controller, not just a pure plan", placed);
                assertEquals(63, r.f.player.inventory.mainInventory[3].stackSize);
                assertEquals(0, r.f.player.inventory.currentItem);
            } finally {
                Pulsar.BUS.unsubscribe(hook);
            }
        }
    }

    @Test public void cancelledC03NeverGrantsC08OrConsumesTheInventoryStack() throws Exception {
        try (Run r = new Run()) {
            PacketHook hook = new PacketHook();
            hook.callback = event -> { if (movementPre(event)) event.cancel(); };
            Pulsar.BUS.subscribe(hook);
            try {
                for (int tick = 0; tick < 12; tick++) {
                    List<Object> sent = r.walk();
                    noPlacementOrSlot(sent);
                    assertEquals(0, count(sent, C03PacketPlayer.class));
                    assertEquals(64, r.f.player.inventory.mainInventory[3].stackSize);
                    assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
                    assertFalse(r.rotation.isMotionAccepted());
                }
            } finally {
                Pulsar.BUS.unsubscribe(hook);
            }
        }
    }

    @Test public void rewrittenC03OrHigherPriorityRotationCannotAuthorizeBridgePost() throws Exception {
        try (Run r = new Run()) {
            PacketHook hook = new PacketHook();
            hook.callback = event -> {
                if (movementPre(event)) {
                    C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
                    event.setPacket(new C03PacketPlayer.C05PacketPlayerLook(
                            packet.getYaw() + 15, packet.getPitch(), packet.isOnGround()));
                }
            };
            Pulsar.BUS.subscribe(hook);
            try {
                for (int tick = 0; tick < 8; tick++) noPlacementOrSlot(r.walk());
            } finally {
                Pulsar.BUS.unsubscribe(hook);
            }
            assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
        }
        try (Run r = new Run()) {
            PriorityHook competingOwner = new PriorityHook(r.rotation);
            Pulsar.BUS.subscribe(competingOwner);
            try {
                for (int tick = 0; tick < 8; tick++) noPlacementOrSlot(r.walk());
                assertNotEquals("scaffold", r.rotation.getConfirmedOwner());
                assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
            } finally {
                Pulsar.BUS.unsubscribe(competingOwner);
            }
        }
    }

    @Test public void slotStackAndWorldChangesDuringC03RejectTheOriginalIntent() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) {
            final int change = scenario;
            try (Run r = new Run()) {
                PacketHook hook = new PacketHook();
                hook.callback = event -> {
                    if (!movementPre(event)) return;
                    if (change == 0) r.f.player.inventory.currentItem = 1;
                    if (change == 1) r.f.player.inventory.mainInventory[3] =
                            r.f.player.inventory.mainInventory[3].copy();
                    if (change == 2) r.scene.blocks.remove(ORIGINAL_SUPPORT);
                    if (change == 3) r.f.mc.theWorld = r.f.world;
                };
                Pulsar.BUS.subscribe(hook);
                try {
                    noPlacementOrSlot(r.walk());
                    assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
                    assertEquals(64, r.f.player.inventory.mainInventory[3].stackSize);
                } finally {
                    Pulsar.BUS.unsubscribe(hook);
                }
            }
        }
    }

    @Test public void repeatPreCannotReplanAndPostNeverSearchesForANewSupport() throws Exception {
        try (Run r = new Run()) {
            PacketHook hook = new PacketHook();
            hook.callback = event -> {
                if (!movementPre(event)) return;
                PlacementTarget original = r.intent();
                assertNotNull(original);
                // Reentrant physical PRE must not replace the already captured intent.
                Pulsar.BUS.post(new EventMotion(.5, 64, .5, 0, 0, true));
                assertSame(original, r.intent());
                // Alternative support becomes available only AFTER PRE; original support vanishes.
                r.scene.blocks.remove(ORIGINAL_SUPPORT);
                r.scene.blocks.put(new BlockPos(0, 63, -1), Blocks.stone.getDefaultState());
            };
            Pulsar.BUS.subscribe(hook);
            try {
                noPlacementOrSlot(r.walk());
                assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
                assertEquals(64, r.f.player.inventory.mainInventory[3].stackSize);
            } finally {
                Pulsar.BUS.unsubscribe(hook);
            }
        }
    }

    @Test public void disableFocusAndReconnectRevokeOutstandingIntentAndTransport() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            final int change = scenario;
            try (Run r = new Run()) {
                PacketHook hook = new PacketHook();
                hook.callback = event -> {
                    if (!movementPre(event)) return;
                    if (change == 0) r.f.scaffold.setToggled(false);
                    if (change == 1) r.f.mc.inGameHasFocus = false;
                    if (change == 2) r.f.network.getClientPackets().worldChanged();
                };
                Pulsar.BUS.subscribe(hook);
                try {
                    noPlacementOrSlot(r.walk());
                    assertEquals(Blocks.air, r.scene.getBlockState(DESTINATION).getBlock());
                    assertNull("stale movement must not keep scaffold ownership",
                            r.rotation.getConfirmedOwner());
                    assertEquals(64, r.f.player.inventory.mainInventory[3].stackSize);
                } finally {
                    Pulsar.BUS.unsubscribe(hook);
                }
            }
        }
    }
}
