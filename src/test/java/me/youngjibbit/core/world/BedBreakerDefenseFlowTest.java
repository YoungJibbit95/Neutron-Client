package me.youngjibbit.core.world;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Real module/input/controller mining through a tiny world, including the planner's native cost adapter. */
public class BedBreakerDefenseFlowTest {
    @Test public void removesSuccessiveDefensesThenBedWithoutTouchingCameraOrSlowAlternative() throws Exception {
        run("removesSuccessiveDefensesThenBedWithoutTouchingCameraOrSlowAlternative");
    }
    @Test public void nativePartialProgressKeepsTheFasterRemainingRoute() throws Exception {
        run("nativePartialProgressKeepsTheFasterRemainingRoute");
    }
    @Test public void newlyFasterOpeningAbortsOnlyThePreviousOwnedBlock() throws Exception {
        run("newlyFasterOpeningAbortsOnlyThePreviousOwnedBlock");
    }
    @Test public void vanishedBedStopsDefenseMiningIncludingLateStartCallbacks() throws Exception {
        run("vanishedBedStopsDefenseMiningIncludingLateStartCallbacks");
    }
    @Test public void unbreakableDefenseProducesNoMiningActions() throws Exception {
        run("unbreakableDefenseProducesNoMiningActions");
    }
    @Test public void zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay() throws Exception {
        run("zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay");
    }

    private static void run(String name) throws Exception { Wave2IsolatedCase.run(Checks.class, name); }

    private static final BlockPos BED = new BlockPos(0, 64, 4);
    private static final BlockPos FIRST = new BlockPos(0, 64, 2);
    private static final BlockPos SECOND = new BlockPos(0, 64, 3);
    private static final BlockPos ALTERNATIVE = new BlockPos(1, 64, 3);

    private static final class Defense extends Block {
        float damage;
        Defense(float damage) { super(Material.rock); this.damage = damage; setHardness(1); }
        @Override public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, BlockPos pos) {
            return damage;
        }
    }

    /** Keep the native division by zero, which yields a legal instant-breaking speed. */
    private static final class ZeroHardness extends Block {
        ZeroHardness() { super(Material.rock); setHardness(0); }
    }

    private static Defense arrangeRoutes(BedBreakerTestSupport.Fixture f, float firstDamage, float secondDamage,
                                         float alternativeDamage) {
        f.world.blocks.clear();
        f.f.player.posX = 1.8;
        f.f.player.rotationYaw = 123;
        f.f.player.rotationPitch = -17;
        f.world.blocks.put(BED, f.bed.getDefaultState());
        f.world.blocks.put(FIRST, new Defense(firstDamage).getDefaultState());
        f.world.blocks.put(SECOND, new Defense(secondDamage).getDefaultState());
        Defense alternative = new Defense(alternativeDamage);
        f.world.blocks.put(ALTERNATIVE, alternative.getDefaultState());
        return alternative;
    }

    private static void tick(BedBreakerTestSupport.Fixture f, List<C07PacketPlayerDigging> packets) throws Exception {
        f.rotation();
        f.input(false);
        int calls = f.controller.automaticCalls;
        f.input(false);
        assertEquals("duplicate input in one player tick must not advance native mining", calls, f.controller.automaticCalls);
        packets.addAll(f.digs());
        assertEquals("silent yaw must not turn the camera", 123, f.f.player.rotationYaw, 0);
        assertEquals("silent pitch must not turn the camera", -17, f.f.player.rotationPitch, 0);
        f.next();
    }

    private static List<BlockPos> positions(List<C07PacketPlayerDigging> packets, C07PacketPlayerDigging.Action action) {
        List<BlockPos> result = new ArrayList<>();
        for (C07PacketPlayerDigging packet : packets) if (packet.getStatus() == action) result.add(packet.getPosition());
        return result;
    }

    public static class Checks {
        @Test public void removesSuccessiveDefensesThenBedWithoutTouchingCameraOrSlowAlternative() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrangeRoutes(f, .5f, .5f, .01f);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 45 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse("the selected bed must be removed after its two defenses", f.world.blocks.containsKey(BED));
                assertFalse(f.world.blocks.containsKey(FIRST));
                assertFalse(f.world.blocks.containsKey(SECOND));
                assertTrue("the slower alternative must stay intact", f.world.blocks.containsKey(ALTERNATIVE));
                assertEquals(3, f.world.removals);
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
                assertFalse(f.controller.func_181040_m());
            }
        }

        @Test public void nativePartialProgressKeepsTheFasterRemainingRoute() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                Defense alternative = arrangeRoutes(f, .125f, .5f, .025f);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 7; i++) tick(f, packets);
                assertTrue(f.controller.isMiningBlock(FIRST));
                assertEquals(.75f, f.controller.getBlockDamageProgress(FIRST), 0);
                // A fresh first route costs 27 ticks; this new opening costs 21.
                // The already paid native progress leaves only 20 on the current route.
                alternative.damage = .1f;
                tick(f, packets);
                assertTrue("remaining progress must beat restarting through the alternative", f.controller.isMiningBlock(FIRST));
                assertEquals(.875f, f.controller.getBlockDamageProgress(FIRST), 0);
                for (int i = 0; i < 35 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(ALTERNATIVE));
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).isEmpty());
            }
        }

        @Test public void newlyFasterOpeningAbortsOnlyThePreviousOwnedBlock() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                Defense alternative = arrangeRoutes(f, .125f, .5f, .025f);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                tick(f, packets);
                assertTrue(f.controller.isMiningBlock(FIRST));
                alternative.damage = 1;
                for (int i = 0; i < 25 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertFalse(f.world.blocks.containsKey(ALTERNATIVE));
                assertTrue(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                assertEquals(Arrays.asList(FIRST), positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
                assertEquals(Arrays.asList(FIRST, ALTERNATIVE, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
            }
        }

        @Test public void vanishedBedStopsDefenseMiningIncludingLateStartCallbacks() throws Exception {
            for (boolean lateStart : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrangeRoutes(f, .125f, .5f, .025f);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                if (lateStart) {
                    f.observer.action = event -> {
                        if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                                && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK)
                            f.world.blocks.remove(BED);
                    };
                } else {
                    tick(f, packets); tick(f, packets);
                    assertTrue(f.controller.isMiningBlock(FIRST));
                    assertTrue(f.controller.getBlockDamageProgress(FIRST) > 0);
                    f.world.blocks.remove(BED);
                    packets.clear();
                }
                tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                assertTrue(f.world.blocks.containsKey(ALTERNATIVE));
                assertEquals(0, f.world.removals);
                assertFalse(f.controller.func_181040_m());
                assertEquals(0, f.controller.getBlockDamageProgress(FIRST), 0);
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK).isEmpty());
                assertTrue(positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).isEmpty());
                assertEquals(lateStart ? 0 : 1, positions(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK).size());
            }
        }

        @Test public void unbreakableDefenseProducesNoMiningActions() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.world.blocks.clear();
                f.f.player.rotationYaw = 123; f.f.player.rotationPitch = -17;
                BlockPos bed = new BlockPos(0, 64, 3), wall = new BlockPos(0, 65, 1);
                f.world.blocks.put(bed, f.bed.getDefaultState());
                f.world.blocks.put(wall, Blocks.bedrock.getDefaultState());
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                for (int i = 0; i < 6; i++) tick(f, packets);
                assertEquals(0, f.controller.automaticCalls);
                assertTrue(packets.isEmpty());
                assertEquals(0, f.world.removals);
                assertEquals(2, f.world.blocks.size());
            }
        }

        @Test public void zeroHardnessUsesVanillaInstantBreakAndNoProgressiveDelay() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                arrangeRoutes(f, 1, 1, .01f);
                ZeroHardness instant = new ZeroHardness();
                f.world.blocks.put(FIRST, instant.getDefaultState());
                f.world.blocks.put(SECOND, instant.getDefaultState());
                assertEquals(Float.POSITIVE_INFINITY, instant.getPlayerRelativeBlockHardness(f.f.player, f.world, FIRST), 0);
                List<C07PacketPlayerDigging> packets = new ArrayList<>();
                tick(f, packets);
                assertFalse(f.world.blocks.containsKey(FIRST));
                assertTrue(f.world.blocks.containsKey(SECOND));
                tick(f, packets);
                assertFalse("instant survival breaking must not add a five-tick delay", f.world.blocks.containsKey(SECOND));
                tick(f, packets);
                assertTrue(f.controller.isMiningBlock(BED));
                for (int i = 0; i < 8 && f.world.blocks.containsKey(BED); i++) tick(f, packets);
                assertFalse(f.world.blocks.containsKey(BED));
                assertTrue(f.world.blocks.containsKey(ALTERNATIVE));
                assertEquals(Arrays.asList(FIRST, SECOND, BED), positions(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(Arrays.asList(BED), positions(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
            }
        }
    }
}
