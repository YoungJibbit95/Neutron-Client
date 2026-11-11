package me.youngjibbit.core.world;

import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.junit.Test;
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
        assertTrue(world.loadedQueries <= BedBreakerPlanner.MAX_CELLS);
        assertTrue(world.rays <= BedBreakerPlanner.MAX_CANDIDATES);
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
    @Test public void visibleTurnSpeedIsABoundedVectorAndDoesNotOvershoot() {
        float[] step = BedBreakerPlanner.turn(0, 0, 90, 45, 10);
        assertEquals(10, Math.hypot(step[0], step[1]), .00001);
        assertArrayEquals(new float[]{90, 45}, BedBreakerPlanner.turn(89, 44, 90, 45, 10), .00001f);
        assertEquals(181, BedBreakerPlanner.turn(179, 0, -179, 0, 5)[0], .00001);
    }
}
