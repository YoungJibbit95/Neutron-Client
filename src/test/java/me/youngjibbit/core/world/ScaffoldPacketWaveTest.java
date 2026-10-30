package me.youngjibbit.core.world;

import io.netty.buffer.Unpooled;
import me.youngjibbit.Pulsar;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Real Scaffold/walking/controller/packet path; world storage and physical input are synthetic. */
public class ScaffoldPacketWaveTest {
    private static final BlockPos DESTINATION = new BlockPos(0, 63, 0);

    private List<Object> untilHook(ScaffoldOwnerContinuityTest.Run run, AtomicBoolean called) {
        List<Object> packets = new ArrayList<>();
        for (int i = 0; i < 20 && !called.get(); i++) packets.addAll(run.walk());
        assertTrue("real controller packet PRE hook was reached", called.get());
        return packets;
    }

    private long slots(List<Object> packets, int slot) {
        return packets.stream().filter(p -> p instanceof C09PacketHeldItemChange
                && ((C09PacketHeldItemChange) p).getSlotId() == slot).count();
    }

    private long placements(List<Object> packets) {
        return packets.stream().filter(p -> p instanceof C08PacketPlayerBlockPlacement).count();
    }

    private void changedSlotPre(Consumer<ScaffoldOwnerContinuityTest.Run> change) throws Exception {
        changedSlotPre(change, null);
    }

    private void changedSlotPre(Consumer<ScaffoldOwnerContinuityTest.Run> change, Integer finalSlot) throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            AtomicBoolean called = new AtomicBoolean();
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> {
                if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C09PacketHeldItemChange
                        && ((C09PacketHeldItemChange) e.getPacket()).getSlotId() == 3
                        && called.compareAndSet(false, true)) change.accept(run);
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = untilHook(run, called);
                assertEquals("invalidated automated slot selection must not reach transport", 0, slots(packets, 3));
                assertEquals("no placement after the slot callback invalidates its action", 0, placements(packets));
                assertEquals(Blocks.air, run.scene.getBlockState(DESTINATION).getBlock());
                assertEquals(64, run.f.player.inventory.mainInventory[3].stackSize);
                if (finalSlot != null) assertEquals("manual choice survives cleanup", finalSlot.intValue(), run.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void c09DisableInPacketPreCannotSendTheOldAutomatedSlotSelection() throws Exception {
        changedSlotPre(run -> run.f.scaffold.setToggled(false));
    }

    @Test public void c09GuiInPacketPreCannotSendTheOldAutomatedSlotSelection() throws Exception {
        changedSlotPre(run -> run.f.mc.currentScreen = new GuiChat());
    }

    @Test public void c09EpochInPacketPreCannotSendTheOldAutomatedSlotSelection() throws Exception {
        changedSlotPre(run -> run.f.network.getClientPackets().worldChanged());
    }

    @Test public void c09ManualSelectionInPacketPreWinsBeforeTheOldSlotTransports() throws Exception {
        changedSlotPre(run -> run.f.player.inventory.currentItem = 7, 7);
    }

    @Test public void c09ReplacementCannotBorrowTheAutomatedSelectionAuthority() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            AtomicBoolean called = new AtomicBoolean();
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> {
                if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C09PacketHeldItemChange
                        && ((C09PacketHeldItemChange) e.getPacket()).getSlotId() == 3
                        && called.compareAndSet(false, true)) e.setPacket(new C09PacketHeldItemChange(5));
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = untilHook(run, called);
                assertEquals("a different slot cannot inherit this placement's authority", 0, slots(packets, 5));
                assertEquals(0, placements(packets));
                assertEquals(Blocks.air, run.scene.getBlockState(DESTINATION).getBlock());
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void manualNoArgumentSlotSyncStillUsesItsNormalPacketPath() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.f.scaffold.setToggled(false);
            run.f.player.inventory.currentItem = 7;
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> {
                if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C09PacketHeldItemChange)
                    e.setPacket(new C09PacketHeldItemChange(5));
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                assertFalse(run.f.mc.playerController.syncCurrentPlayItemChecked());
                assertEquals(1, slots(run.drain(), 5));
                assertEquals(7, run.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertTrue(run.f.mc.playerController.syncCurrentPlayItemChecked());
            assertEquals(1, slots(run.drain(), 7));
        }
    }

    @Test public void manualSelectionDuringRestorePreWinsBeforeTheOldRestoreTransports() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            AtomicBoolean called = new AtomicBoolean();
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> {
                if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C09PacketHeldItemChange
                        && ((C09PacketHeldItemChange) e.getPacket()).getSlotId() == 0
                        && called.compareAndSet(false, true)) run.f.player.inventory.currentItem = 7;
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = untilHook(run, called);
                assertEquals("legitimate placement completed before cleanup", 1, placements(packets));
                assertEquals("manual selection supersedes the pending restore", 0, slots(packets, 0));
                assertEquals(7, run.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    private void changedPlacementPre(Consumer<C08PacketPlayerBlockPlacement> change) throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            AtomicBoolean called = new AtomicBoolean();
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = e -> {
                if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C08PacketPlayerBlockPlacement
                        && called.compareAndSet(false, true)) change.accept((C08PacketPlayerBlockPlacement) e.getPacket());
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = untilHook(run, called);
                assertEquals("an in-place rewrite cannot inherit the original placement authority", 0, placements(packets));
                assertEquals("rejected wire intent cannot place the original local block", Blocks.air,
                        run.scene.getBlockState(DESTINATION).getBlock());
                assertEquals(64, run.f.player.inventory.mainInventory[3].stackSize);
                assertEquals("safe same-scene slot restore", 0, run.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void c08MutablePacketStackCannotChangeTheSealedIntent() throws Exception {
        changedPlacementPre(packet -> packet.getStack().stackSize--);
    }

    private void rewrite(C08PacketPlayerBlockPlacement packet, BlockPos position, int face,
                         float x, float y, float z) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        ItemStack stack = packet.getStack();
        try {
            new C08PacketPlayerBlockPlacement(position, face, stack, x, y, z).writePacketData(buffer);
            packet.readPacketData(buffer);
        } catch (IOException error) { throw new AssertionError(error); }
        finally { buffer.release(); }
    }

    @Test public void c08InPlacePositionRewriteCannotChangeTheSealedIntent() throws Exception {
        changedPlacementPre(packet -> rewrite(packet, packet.getPosition().add(20, 0, 0),
                packet.getPlacedBlockDirection(), packet.getPlacedBlockOffsetX(),
                packet.getPlacedBlockOffsetY(), packet.getPlacedBlockOffsetZ()));
    }

    @Test public void c08InPlaceFaceRewriteCannotChangeTheSealedIntent() throws Exception {
        changedPlacementPre(packet -> rewrite(packet, packet.getPosition(),
                (packet.getPlacedBlockDirection() + 1) % 6, packet.getPlacedBlockOffsetX(),
                packet.getPlacedBlockOffsetY(), packet.getPlacedBlockOffsetZ()));
    }

    @Test public void c08InPlaceHitRewriteCannotChangeTheSealedIntent() throws Exception {
        changedPlacementPre(packet -> rewrite(packet, packet.getPosition(), packet.getPlacedBlockDirection(),
                packet.getPlacedBlockOffsetX(), packet.getPlacedBlockOffsetY() < .5f ? .875f : .125f,
                packet.getPlacedBlockOffsetZ()));
    }

    private void changeOnly(C08PacketPlayerBlockPlacement packet, String name, Object value) {
        try {
            java.lang.reflect.Field field = C08PacketPlayerBlockPlacement.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(packet, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test public void c08PositionOnlyMutationCannotBorrowTheOriginalIntent() throws Exception {
        changedPlacementPre(packet -> changeOnly(packet, "position", packet.getPosition().add(20, 0, 0)));
    }

    @Test public void c08FaceOnlyMutationCannotBorrowTheOriginalIntent() throws Exception {
        changedPlacementPre(packet -> changeOnly(packet, "placedBlockDirection", (packet.getPlacedBlockDirection() + 1) % 6));
    }

    @Test public void c08HitOnlyMutationCannotBorrowTheOriginalIntent() throws Exception {
        changedPlacementPre(packet -> changeOnly(packet, "facingY", packet.getPlacedBlockOffsetY() < .5f ? .875f : .125f));
    }

    @Test public void unchangedNativePreAndPostKeepOnePlacementAndOrderedSlotRestore() throws Exception {
        for (String timing : new String[]{"Pre", "Post"}) {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run(timing)) {
                List<Object> packets = run.complete(DESTINATION);
                int select = -1, place = -1, restore = -1;
                for (int i = 0; i < packets.size(); i++) {
                    Object packet = packets.get(i);
                    if (packet instanceof C09PacketHeldItemChange) {
                        if (((C09PacketHeldItemChange) packet).getSlotId() == 3) select = i;
                        if (((C09PacketHeldItemChange) packet).getSlotId() == 0) restore = i;
                    }
                    if (packet instanceof C08PacketPlayerBlockPlacement) place = i;
                }
                assertEquals(timing, 1, placements(packets));
                assertEquals(timing, 1, slots(packets, 3));
                assertEquals(timing, 1, slots(packets, 0));
                assertTrue(timing + ": C09 select -> C08 -> C09 restore", select >= 0 && place > select && restore > place);
                assertEquals(63, run.f.player.inventory.mainInventory[3].stackSize);
            }
        }
    }
}
