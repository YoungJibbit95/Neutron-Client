package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class BedBreakerPlannerTest {
    @Test public void negativeCellsUseFloorAndRealBedHeight() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world();
        BlockPos pos = new BlockPos(-5, 65, 0); world.blocks.put(pos, Blocks.bed.getDefaultState());
        BedBreakerPlanner.Target target = new BedBreakerPlanner(world).select(new Vec3(-.2, 65.3, .5), 4.5, null, true, () -> true);
        assertNotNull(target); assertEquals(pos, target.pos);
        assertTrue(target.aim.yCoord > 65 && target.aim.yCoord < 65.5625);
    }
    @Test public void nearestVisibleBedIsRetainedUntilItIsNoLongerUseful() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); BedBreakerPlanner planner = new BedBreakerPlanner(world);
        Vec3 eyes = new Vec3(.5, 65.62, .5); BlockPos farther = new BlockPos(0, 64, 3), nearer = new BlockPos(2, 64, 0);
        world.blocks.put(farther, Blocks.bed.getDefaultState());
        BedBreakerPlanner.Target held = planner.select(eyes, 4.5, null, true, () -> true); assertEquals(farther, held.pos);
        world.blocks.put(nearer, Blocks.bed.getDefaultState());
        assertEquals(farther, planner.select(eyes, 4.5, held, true, () -> true).pos);
        assertEquals(nearer, planner.select(eyes, 4.5, null, true, () -> true).pos);
        world.blocks.remove(farther); assertNull(planner.select(eyes, 4.5, held, false, () -> true));
    }
    @Test public void firstNativeWorldHitRejectsWallsAndOtherBlockTypes() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); BedBreakerPlanner planner = new BedBreakerPlanner(world);
        Vec3 eyes = new Vec3(.5, 65.62, .5); BlockPos pos = new BlockPos(0, 64, 2);
        world.blocks.put(pos, Blocks.stone.getDefaultState()); assertNull(planner.select(eyes, 4.5, null, true, () -> true));
        world.blocks.put(pos, Blocks.bed.getDefaultState()); world.blocks.put(new BlockPos(0, 65, 1), Blocks.stone.getDefaultState());
        assertNull(planner.select(eyes, 4.5, null, true, () -> true));
        world.blocks.remove(new BlockPos(0, 65, 1)); assertNotNull(planner.select(eyes, 4.5, null, true, () -> true));
    }
    @Test public void ordinaryReachIsSphericalAndCreativeOnlyAddsItsNormalHalfBlock() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); BedBreakerPlanner planner = new BedBreakerPlanner(world);
        Vec3 eyes = new Vec3(.5, 65.3, .5); world.blocks.put(new BlockPos(5, 65, 0), Blocks.bed.getDefaultState());
        assertNull(planner.select(eyes, 4.5, null, true, () -> true));
        assertNotNull(planner.select(eyes, 5, null, true, () -> true));
        world.blocks.clear(); world.blocks.put(new BlockPos(4, 65, 4), Blocks.bed.getDefaultState());
        assertNull(planner.select(eyes, 4.5, null, true, () -> true));
    }
    @Test public void scanAndRayWorkHaveFixedBounds() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world();
        for (int x = -5; x <= 5; x++) for (int y = 60; y <= 70; y++) for (int z = -5; z <= 5; z++)
            world.blocks.put(new BlockPos(x, y, z), Blocks.bed.getDefaultState());
        new BedBreakerPlanner(world).select(new Vec3(.5, 65.3, .5), 5, null, true, () -> true);
        assertTrue(world.loadedQueries <= BedBreakerPlanner.MAX_CELLS
                + (BedBreakerPlanner.MAX_CANDIDATES * BedBreakerPlanner.MAX_AIM_POINTS + 2)
                * BedBreakerPlanner.MAX_RAY_CELLS * 8 + BedBreakerPlanner.MAX_RAYS);
        assertTrue(world.rays <= BedBreakerPlanner.MAX_RAYS);
    }
    @Test public void staleReadAndRayCallbacksCannotPublishACandidate() throws Exception {
        for (boolean ray : new boolean[]{false, true}) {
            BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); AtomicBoolean current = new AtomicBoolean(true);
            world.blocks.put(new BlockPos(0, 64, 2), Blocks.bed.getDefaultState());
            if (ray) world.afterRay = () -> current.set(false); else world.afterRead = pos -> current.set(false);
            assertNull(new BedBreakerPlanner(world).select(new Vec3(.5, 65.62, .5), 4.5, null, true, current::get));
        }
    }
    @Test public void invalidInputDoesNotQueryTheWorld() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); BedBreakerPlanner planner = new BedBreakerPlanner(world);
        for (double reach : new double[]{0, -1, 5.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertNull(planner.select(new Vec3(.5, 65.6, .5), reach, null, true, () -> true));
        assertNull(planner.select(new Vec3(Double.NaN, 65, 0), 4.5, null, true, () -> true));
        assertEquals(0, world.loadedQueries); assertEquals(0, world.stateReads);
    }
    @Test public void turnSpeedIsABoundedVectorAndDoesNotOvershoot() {
        float[] step = BedBreakerPlanner.turn(0, 0, 90, 45, 10);
        assertEquals(10, Math.hypot(step[0], step[1]), .00001);
        assertArrayEquals(new float[]{90, 45}, BedBreakerPlanner.turn(89, 44, 90, 45, 10), .00001f);
        assertEquals(181, BedBreakerPlanner.turn(179, 0, -179, 0, 5)[0], .00001);
    }
    @Test public void followsDefenseThenBedWithoutChangingTheWorld() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world();
        MiningAccess access = new MiningAccess(world); BedBreakerPlanner planner = new BedBreakerPlanner(access);
        Vec3 eyes = new Vec3(.5, 65.62, .5); BlockPos bed = new BlockPos(0, 64, 3), defense = new BlockPos(0, 65, 1);
        world.blocks.put(bed, Blocks.bed.getDefaultState()); world.blocks.put(defense, Blocks.wool.getDefaultState());
        access.costs.put(defense, 4D);
        BedBreakerPlanner.Target first = planner.select(eyes, 4.5, null, true, () -> true);
        assertNotNull(first); assertEquals(defense, first.pos); assertEquals(bed, first.bedPos);
        assertEquals(Arrays.asList(defense, bed), first.path); assertEquals(11, first.estimatedTicks, 0);
        assertEquals(2, world.blocks.size()); assertEquals(0, world.removals);
        assertSame(first, planner.select(eyes, 4.5, first, false, () -> true));
        world.blocks.remove(defense);
        BedBreakerPlanner.Target next = planner.select(eyes, 4.5, first, false, () -> true);
        assertNotNull(next); assertEquals(bed, next.pos); assertEquals(Arrays.asList(bed), next.path);
        world.blocks.remove(bed);
        assertFalse(planner.hasBed(first, () -> true));
        assertNull(planner.select(eyes, 4.5, next, false, () -> true));
    }
    @Test public void twoFastBlocksBeatOneSlowBlockOnTheSameBed() throws Exception {
        RouteFixture f = new RouteFixture();
        BedBreakerPlanner.Target target = f.planner.select(f.eyes, 4.5, null, true, () -> true);
        assertNotNull(target); assertEquals(f.first, target.pos);
        assertEquals(Arrays.asList(f.first, f.second, f.bed), target.path);
        assertEquals(18, target.estimatedTicks, 0);
        assertEquals(4, f.world.blocks.size());
    }
    @Test public void instantBlocksDoNotGetProgressiveBreakingDelays() throws Exception {
        RouteFixture f = new RouteFixture();
        f.access.costs.put(f.first, 1D); f.access.costs.put(f.second, 1D); f.access.costs.put(f.hard, 3D);
        f.access.delays.put(f.first, 0D); f.access.delays.put(f.second, 0D);
        BedBreakerPlanner.Target target = f.planner.select(f.eyes, 4.5, null, true, () -> true);
        assertNotNull(target); assertEquals(f.first, target.pos);
        assertEquals(Arrays.asList(f.first, f.second, f.bed), target.path);
        assertEquals(4, target.estimatedTicks, 0);
    }
    @Test public void cheaperOpeningReplansButRemainingProgressAndEqualCostsAreRetained() throws Exception {
        RouteFixture f = new RouteFixture();
        BedBreakerPlanner.Target original = f.planner.select(f.eyes, 4.5, null, true, () -> true);
        assertEquals(f.first, original.pos);
        f.access.costs.put(f.hard, 9D); // 16 ticks; the old full route would take 18.
        f.access.costs.put(f.first, 1D); // Existing progress lowers the retained route to the same 16 ticks.
        BedBreakerPlanner.Target progress = f.planner.select(f.eyes, 4.5, original, true, () -> true);
        assertNotNull(progress); assertEquals(f.first, progress.pos); assertEquals(16, progress.estimatedTicks, 0);
        f.access.costs.put(f.hard, 1D);
        assertEquals(f.first, f.planner.select(f.eyes, 4.5, progress, false, () -> true).pos);
        BedBreakerPlanner.Target faster = f.planner.select(f.eyes, 4.5, progress, true, () -> true);
        assertNotNull(faster); assertEquals(f.hard, faster.pos);
        assertEquals(Arrays.asList(f.hard, f.bed), faster.path); assertEquals(8, faster.estimatedTicks, 0);
        f.world.blocks.remove(f.hard);
        BedBreakerPlanner.Target open = f.planner.select(f.eyes, 4.5, faster, false, () -> true);
        assertNotNull(open); assertEquals(f.bed, open.pos);
    }
    @Test public void considersBothBedHalvesAndFindsAnExposedFarFace() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world();
        MiningAccess access = new MiningAccess(world); BedBreakerPlanner planner = new BedBreakerPlanner(access);
        BlockPos foot = new BlockPos(0, 64, 4), head = new BlockPos(1, 64, 4), defense = new BlockPos(0, 64, 3);
        world.blocks.put(foot, Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST));
        world.blocks.put(head, Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST)
                .withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));
        world.blocks.put(defense, Blocks.stone.getDefaultState());
        BedBreakerPlanner.Target target = planner.select(new Vec3(.5, 65.62, .5), 4.5, null, true, () -> true);
        assertNotNull(target); assertEquals(head, target.pos); assertEquals(head, target.bedPos);
        assertEquals(1, target.path.size()); assertEquals(2, target.estimatedTicks, 0);
    }
    @Test public void rejectsUnbreakableAndInvalidMiningCosts() throws Exception {
        for (double cost : new double[]{Double.POSITIVE_INFINITY, Double.NaN, 0, -1}) {
            BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); MiningAccess access = new MiningAccess(world);
            BlockPos bed = new BlockPos(0, 64, 3), wall = new BlockPos(0, 65, 1);
            world.blocks.put(bed, Blocks.bed.getDefaultState()); world.blocks.put(wall, Blocks.bedrock.getDefaultState());
            access.costs.put(wall, cost);
            assertNull(new BedBreakerPlanner(access).select(new Vec3(.5, 65.62, .5), 4.5, null, true, () -> true));
        }
    }
    @Test public void neverTracesThroughAnUnloadedIntermediateCell() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); MiningAccess access = new MiningAccess(world);
        BlockPos bed = new BlockPos(0, 64, 3); world.blocks.put(bed, Blocks.bed.getDefaultState());
        access.unloaded.add(new BlockPos(0, 65, 1));
        assertNull(new BedBreakerPlanner(access).select(new Vec3(.5, 65.62, .5), 4.5, null, true, () -> true));
        assertEquals(0, world.rays);
    }
    @Test public void changedBedOrRouteCannotKeepAnOldDefenseTarget() throws Exception {
        RouteFixture f = new RouteFixture();
        BedBreakerPlanner.Target target = f.planner.select(f.eyes, 4.5, null, true, () -> true);
        f.world.blocks.put(f.second, Blocks.bedrock.getDefaultState()); f.access.costs.put(f.second, Double.POSITIVE_INFINITY);
        assertNull(f.planner.select(f.eyes, 4.5, target, false, () -> true));
        assertEquals(f.hard, f.planner.select(f.eyes, 4.5, target, true, () -> true).pos);
        f.world.blocks.put(f.bed, Blocks.stone.getDefaultState());
        assertFalse(f.planner.hasBed(target, () -> true));
        assertNull(f.planner.select(f.eyes, 4.5, target, true, () -> true));
    }
    @Test public void staleCostCallbackCannotPublishAPath() throws Exception {
        RouteFixture f = new RouteFixture(); AtomicBoolean current = new AtomicBoolean(true);
        f.access.afterCost = () -> current.set(false);
        assertNull(f.planner.select(f.eyes, 4.5, null, true, current::get));
    }
    @Test public void negativeDirectionPathsDoNotRepeatTheSameObstruction() throws Exception {
        BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world(); MiningAccess access = new MiningAccess(world);
        BlockPos bed = new BlockPos(-4, 64, 0), defense = new BlockPos(-2, 64, 0);
        world.blocks.put(bed, Blocks.bed.getDefaultState()); world.blocks.put(defense, Blocks.wool.getDefaultState());
        access.costs.put(defense, 4D);
        BedBreakerPlanner.Target target = new BedBreakerPlanner(access).select(new Vec3(.5, 64.3, .5), 4.5, null, true, () -> true);
        assertNotNull(target); assertEquals(Arrays.asList(defense, bed), target.path);
    }

    private static final class MiningAccess implements BedBreakerPlanner.Access {
        final BedBreakerTestSupport.BedWorld world;
        final Map<BlockPos, Double> costs = new HashMap<>(), delays = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        Runnable afterCost;
        MiningAccess(BedBreakerTestSupport.BedWorld world) { this.world = world; }
        public boolean loaded(BlockPos pos) { return !unloaded.contains(pos) && world.loaded(pos); }
        public IBlockState state(BlockPos pos) { return world.state(pos); }
        public AxisAlignedBB bounds(BlockPos pos, IBlockState state) { return world.bounds(pos, state); }
        public MovingObjectPosition ray(Vec3 from, Vec3 to) { return world.ray(from, to); }
        public double miningTicks(BlockPos pos, IBlockState state) {
            double result = costs.containsKey(pos) ? costs.get(pos) : state.getBlock() instanceof BlockBed ? 2 : 10;
            if (afterCost != null) afterCost.run();
            return result;
        }
        public double postBreakDelayTicks(BlockPos pos, IBlockState state) {
            return delays.containsKey(pos) ? delays.get(pos) : BedBreakerPlanner.BREAK_DELAY_TICKS;
        }
    }
    private static final class RouteFixture {
        final BedBreakerTestSupport.BedWorld world = BedBreakerTestSupport.world();
        final MiningAccess access = new MiningAccess(world);
        final BedBreakerPlanner planner = new BedBreakerPlanner(access);
        final Vec3 eyes = new Vec3(1.8, 65.62, .5);
        final BlockPos bed = new BlockPos(0, 64, 4), first = new BlockPos(0, 64, 2), second = new BlockPos(0, 64, 3), hard = new BlockPos(1, 64, 3);
        RouteFixture() throws Exception {
            world.blocks.put(bed, Blocks.bed.getDefaultState());
            world.blocks.put(first, Blocks.wool.getDefaultState()); world.blocks.put(second, Blocks.wool.getDefaultState());
            world.blocks.put(hard, Blocks.obsidian.getDefaultState());
            access.costs.put(first, 3D); access.costs.put(second, 3D); access.costs.put(hard, 100D);
        }
    }
}
