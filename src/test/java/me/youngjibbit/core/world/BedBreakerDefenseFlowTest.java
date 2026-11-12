package me.youngjibbit.core.world;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Original Demise action modes through the actual update/input/controller and packet transport. */
public class BedBreakerDefenseFlowTest {
    @Test public void legitRemovesFirstHitDefensesThenBedWithoutChangingCamera() throws Exception {
        run("legitRemovesFirstHitDefensesThenBedWithoutChangingCamera");
    }
    @Test public void nativeProgressSurvivesEvenTickBedSearches() throws Exception {
        run("nativeProgressSurvivesEvenTickBedSearches");
    }
    @Test public void changedDefenseStateAbortsOldProgressBeforeStartingReplacement() throws Exception {
        run("changedDefenseStateAbortsOldProgressBeforeStartingReplacement");
    }
    @Test public void nonLegitMinesCoveredBedAndUsesMouseOverFace() throws Exception {
        run("nonLegitMinesCoveredBedAndUsesMouseOverFace");
    }
    @Test public void vanishedBedStopsDefenseMiningIncludingLateStartCallbacks() throws Exception {
        run("vanishedBedStopsDefenseMiningIncludingLateStartCallbacks");
    }
    @Test public void unbreakableLegitDefenseNeverProgressesOrBreaks() throws Exception {
        run("unbreakableLegitDefenseNeverProgressesOrBreaks");
    }
    @Test public void zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay() throws Exception {
        run("zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay");
    }
    @Test public void changingLegitModeAbortsOwnedDefenseAndContinuesAtBed() throws Exception {
        run("changingLegitModeAbortsOwnedDefenseAndContinuesAtBed");
    }

    private static void run(String name) throws Exception { Wave2IsolatedCase.run(Checks.class, name); }

    private static final BlockPos BED = new BlockPos(0, 64, 3);
    private static final BlockPos FIRST = new BlockPos(0, 65, 1);
    private static final BlockPos SECOND = new BlockPos(0, 64, 2);
    private static final BlockPos SIDE = new BlockPos(1, 64, 2);

    private static final class Defense extends Block {
        private final float damage;
        Defense(float damage) { super(Material.rock); this.damage = damage; setHardness(1); }
        @Override public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, BlockPos pos) {
            return damage;
        }
    }

    /** Keep the native division by zero, which yields a legal instant-breaking speed. */
    private static final class ZeroHardness extends Block {
        ZeroHardness() { super(Material.rock); setHardness(0); }
    }

    private static void arrange(BedBreakerTestSupport.Fixture f, float firstDamage, float secondDamage) {
        f.world.blocks.clear();
        f.f.player.rotationYaw = 123;
        f.f.player.rotationPitch = -17;
        f.world.blocks.put(BED, f.bed.getDefaultState());
        f.world.blocks.put(FIRST, new Defense(firstDamage).getDefaultState());
        f.world.blocks.put(SECOND, new Defense(secondDamage).getDefaultState());
        f.world.blocks.put(SIDE, new Defense(.01f).getDefaultState());
    }

    /** The ordinary update acts before the walking C03; a repeated input cannot spend the tick twice. */
    private static void tick(BedBreakerTestSupport.Fixture f, List<C07PacketPlayerDigging> packets) throws Exception {
        float cameraYaw = f.f.player.rotationYaw, cameraPitch = f.f.player.rotationPitch;
        f.input(false);
        int calls = f.controller.automaticCalls;
        float progress = f.progress();
        packets.addAll(f.digs());
        f.input(false);
        assertEquals("duplicate input in one player tick must not advance native mining", calls, f.controller.automaticCalls);
        assertEquals("duplicate input must preserve current native block progress", progress, f.progress(), 0);
        assertTrue("duplicate input must not send a second mining action", f.digs().isEmpty());
        f.rotation();
        packets.addAll(f.digs());
        assertEquals("silent yaw must not turn the camera", cameraYaw, f.f.player.rotationYaw, 0);
        assertEquals("silent pitch must not turn the camera", cameraPitch, f.f.player.rotationPitch, 0);
        f.next();
    }

    private static List<BlockPos> positions(List<C07PacketPlayerDigging> packets, C07PacketPlayerDigging.Action action) {
        List<BlockPos> result = new ArrayList<>();
        for (C07PacketPlayerDigging packet : packets) if (packet.getStatus() == action) result.add(packet.getPosition());
        return result;
    }

    public static class Checks {
        @Test public void legitRemovesFirstHitDefensesThenBedWithoutChangingCamera() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .5f, .5f);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                // Leave the camera away from the bed: acquisition must be smooth and silent.
                for (int i = 0; i < 120 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse("Legit must remove the selected bed after its first-hit defenses", f.world.blocks.containsKey(BED));
                assertFalse(f.world.blocks.containsKey(FIRST));
                assertFalse(f.world.blocks.containsKey(SECOND));
                assertTrue("a block outside the actual sightline must stay intact", f.world.blocks.containsKey(SIDE));
                assertEquals(3, f.world.removals);
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
                assertFalse(f.controller.func_181040_m());
                assertEquals(123, f.f.player.rotationYaw, 0);
                assertEquals(-17, f.f.player.rotationPitch, 0);
            }
        }

        @Test public void nativeProgressSurvivesEvenTickBedSearches() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .125f, .5f);
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 20 && f.controller.getBlockDamageProgress(FIRST) < .5f; i++) tick(f, packets);
                assertTrue(f.controller.isMiningBlock(FIRST));
                assertEquals(.5f, f.controller.getBlockDamageProgress(FIRST), 0);
                int reads = f.world.stateReads;
                tick(f, packets);
                assertEquals(.625f, f.controller.getBlockDamageProgress(FIRST), 0);
                tick(f, packets);
                assertEquals(.75f, f.controller.getBlockDamageProgress(FIRST), 0);
                assertTrue("two updates must include the original even-tick cube search", f.world.stateReads - reads >= 1000);
                assertTrue(f.controller.isMiningBlock(FIRST));
                assertEquals(0, f.world.removals);
                assertEquals(Arrays.asList(FIRST), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).isEmpty());
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
            }
        }

        @Test public void changedDefenseStateAbortsOldProgressBeforeStartingReplacement() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .125f, .5f);
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 20 && f.controller.getBlockDamageProgress(FIRST) < .25f; i++) tick(f, packets);
                assertTrue(f.controller.isMiningBlock(FIRST));
                assertEquals(.25f, f.controller.getBlockDamageProgress(FIRST), 0);

                f.world.blocks.put(FIRST, new Defense(.5f).getDefaultState());
                tick(f, packets);
                assertTrue("the replacement uses the same position with a fresh native operation", f.controller.isMiningBlock(FIRST));
                assertEquals("the removed state's progress cannot transfer to its replacement", 0,
                        f.controller.getBlockDamageProgress(FIRST), 0);
                assertEquals(Arrays.asList(FIRST), positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
                assertEquals(Arrays.asList(FIRST, FIRST), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));

                tick(f, packets);
                assertEquals("the next update advances only the replacement state's hardness", .5f,
                        f.controller.getBlockDamageProgress(FIRST), 0);
                assertEquals(0, f.world.removals);
                assertTrue(f.world.blocks.containsKey(BED));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).isEmpty());
                assertEquals(Arrays.asList(FIRST, FIRST), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
            }
        }

        @Test public void nonLegitMinesCoveredBedAndUsesMouseOverFace() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .125f, .5f);
                f.module.legit.setValue(false);
                f.aim();
                MovingObjectPosition mouse = f.f.mc.objectMouseOver;
                assertNotNull(mouse);
                assertEquals(FIRST, mouse.getBlockPos());
                EnumFacing mouseFace = mouse.sideHit;
                MovingObjectPosition bedHit = DemiseBedBreakerGeometry.rayTraceBed(f.world, BED,
                        f.f.player.getPositionEyes(1), f.f.player.getLook(1));
                assertNotNull(bedHit);
                assertNotEquals("fixture distinguishes mouse face from selected-bed collision face", bedHit.sideHit, mouseFace);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 40 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse("non-Legit uses the selected bed's own collision through cover", f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                assertTrue(f.world.blocks.containsKey(SIDE));
                assertEquals(1, f.world.removals);
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
                for (C07PacketPlayerDigging packet : packets)
                    assertEquals("Demise passes objectMouseOver.sideHit to native bed mining", mouseFace, packet.getFacing());
            }
        }

        @Test public void vanishedBedStopsDefenseMiningIncludingLateStartCallbacks() throws Exception {
            for (boolean lateStart : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .125f, .5f);
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                AtomicBoolean removedDuringStart = new AtomicBoolean();
                if (lateStart) {
                    f.observer.action = event -> {
                        if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging) {
                            C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                            if (packet.getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                                    && FIRST.equals(packet.getPosition()) && removedDuringStart.compareAndSet(false, true))
                                f.world.blocks.remove(BED);
                        }
                    };
                    for (int i = 0; i < 20 && !removedDuringStart.get(); i++) tick(f, packets);
                    assertTrue("the bed must disappear inside the actual START transport callback", removedDuringStart.get());
                } else {
                    for (int i = 0; i < 20 && f.controller.getBlockDamageProgress(FIRST) == 0; i++) tick(f, packets);
                    assertTrue(f.controller.isMiningBlock(FIRST));
                    assertTrue(f.controller.getBlockDamageProgress(FIRST) > 0);
                    f.world.blocks.remove(BED);
                    packets.clear();
                    tick(f, packets);
                }
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                assertTrue(f.world.blocks.containsKey(SIDE));
                assertEquals(0, f.world.removals);
                assertFalse(f.controller.func_181040_m());
                assertEquals(0, f.controller.getBlockDamageProgress(FIRST), 0);
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK).isEmpty());
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).isEmpty());
                assertEquals(lateStart ? 0 : 1, positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).size());
            }
        }

        @Test public void unbreakableLegitDefenseNeverProgressesOrBreaks() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .5f, .5f);
                f.world.blocks.put(FIRST, Blocks.bedrock.getDefaultState());
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 25; i++) tick(f, packets);
                assertTrue("the native Legit action path must be exercised", f.controller.automaticCalls > 0);
                assertEquals(0, f.controller.getBlockDamageProgress(FIRST), 0);
                assertEquals(0, f.world.removals);
                assertEquals(4, f.world.blocks.size());
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).isEmpty());
                for (BlockPos started : positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK))
                    assertEquals("native START is allowed, but the obstruction cannot be bypassed", FIRST, started);
            }
        }

        @Test public void zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, 1, 1);
                ZeroHardness instant = new ZeroHardness();
                f.world.blocks.put(FIRST, instant.getDefaultState());
                f.world.blocks.put(SECOND, instant.getDefaultState());
                assertEquals(Float.POSITIVE_INFINITY, instant.getPlayerRelativeBlockHardness(f.f.player, f.world, FIRST), 0);
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                tick(f, packets);
                assertFalse(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                tick(f, packets);
                assertFalse("instant survival breaking must not add a five-tick delay", f.world.blocks.containsKey(SECOND));
                tick(f, packets);
                assertTrue(f.controller.isMiningBlock(BED));
                for (int i = 0; i < 12 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(SIDE));
                assertEquals(3, f.world.removals);
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
            }
        }

        @Test public void changingLegitModeAbortsOwnedDefenseAndContinuesAtBed() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrange(f, .125f, .5f);
                f.aim();
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 20 && f.controller.getBlockDamageProgress(FIRST) < .25f; i++) tick(f, packets);
                assertEquals(.25f, f.controller.getBlockDamageProgress(FIRST), 0);
                f.module.legit.setValue(false);
                for (int i = 0; i < 80 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                assertTrue(f.world.blocks.containsKey(SIDE));
                assertEquals(0, f.controller.getBlockDamageProgress(FIRST), 0);
                assertEquals(1, f.world.removals);
                assertEquals(Arrays.asList(FIRST), positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
                assertEquals(Arrays.asList(FIRST, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
            }
        }
    }
}
