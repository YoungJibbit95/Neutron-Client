package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.World;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/** Real player boxes and block collision shapes, without a game, transport or renderer. */
public class NScaffoldEdgeGuardTest {
    public static class CollisionWorld extends WorldClient {
        List<AxisAlignedBB> boxes;
        Map<BlockPos, IBlockState> blocks;
        WorldBorder border;
        Predicate<BlockPos> loaded;
        Runnable beforeLoaded, beforeCollision, beforeBorder;
        int loadedReads, collisionReads;
        AxisAlignedBB lastQuery;

        public CollisionWorld() { super(null, null, 0, null, null); }

        @Override public boolean isBlockLoaded(BlockPos pos) {
            loadedReads++;
            if (beforeLoaded != null) {
                Runnable callback = beforeLoaded;
                beforeLoaded = null;
                callback.run();
            }
            return loaded.test(pos);
        }

        @Override public WorldBorder getWorldBorder() {
            if (beforeBorder != null) {
                Runnable callback = beforeBorder;
                beforeBorder = null;
                callback.run();
            }
            return border;
        }

        @Override public IBlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.air.getDefaultState());
        }

        @Override public boolean isFlammableWithin(AxisAlignedBB box) { return false; }

        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity, AxisAlignedBB query) {
            collisionReads++;
            lastQuery = query;
            if (beforeCollision != null) {
                Runnable callback = beforeCollision;
                beforeCollision = null;
                callback.run();
            }
            List<AxisAlignedBB> result = new ArrayList<>();
            for (AxisAlignedBB box : boxes) if (box.intersectsWith(query)) result.add(box);
            for (Map.Entry<BlockPos, IBlockState> cell : blocks.entrySet())
                cell.getValue().getBlock().addCollisionBoxesToList(this, cell.getKey(), cell.getValue(),
                        query, result, entity);
            return result;
        }
    }

    private static final class Scene {
        final CollisionWorld world = RotationBehaviorHookTest.bare(CollisionWorld.class);
        final EntityPlayerSP player;

        Scene() throws Exception {
            this(EntityPlayerSP.class);
        }

        Scene(Class<? extends EntityPlayerSP> playerType) throws Exception {
            player = RotationBehaviorHookTest.bare(playerType);
            world.boxes = new ArrayList<>();
            world.blocks = new LinkedHashMap<>();
            world.border = new WorldBorder();
            world.loaded = pos -> true;
            player.worldObj = world;
            player.stepHeight = .6f;
            player.capabilities = new PlayerCapabilities();
            position(.5, 64, .5);
        }

        void position(double x, double y, double z) {
            player.posX = x;
            player.posY = y;
            player.posZ = z;
            player.onGround = true;
            player.setEntityBoundingBox(body(x, y, z));
        }

        NScaffoldEdgeGuard.Result move(double x, double z) {
            return NScaffoldEdgeGuard.limit(world, player, x, -.0784, z, () -> true);
        }
    }

    /** Only unrelated rendering, sound and block-trigger work is omitted from native movement. */
    public static class NativePlayer extends RotationBehaviorHookTest.Player {
        @Override public boolean isWet() { return false; }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override protected boolean canTriggerWalking() { return false; }
        @Override protected void doBlockCollisions() { }
        @Override protected void updateFallState(double y, boolean grounded, Block block, BlockPos pos) { }
    }

    private static final class CollisionProfiler extends Profiler {
        @Override public void startSection(String name) { }
        @Override public void endSection() { }
    }

    private static AxisAlignedBB body(double x, double y, double z) {
        return new AxisAlignedBB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
    }

    private static AxisAlignedBB floor(double minX, double minZ, double maxX, double maxZ, double top) {
        return new AxisAlignedBB(minX, top - 1, minZ, maxX, top, maxZ);
    }

    private static void unchanged(NScaffoldEdgeGuard.Result result, double x, double z) {
        assertTrue(result.active);
        assertFalse(result.limited);
        assertEquals(Double.doubleToLongBits(x), Double.doubleToLongBits(result.x));
        assertEquals(Double.doubleToLongBits(z), Double.doubleToLongBits(result.z));
    }

    private static void stopped(NScaffoldEdgeGuard.Result result) {
        assertTrue(result.active);
        assertTrue(result.limited);
        assertEquals(0, result.x, 0);
        assertEquals(0, result.z, 0);
    }

    @Test public void continuousFloorPreservesEveryDirectionAndSignedZeroExactly() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(-3, -3, 4, 4, 64));
        scene.player.motionX = .2;
        scene.player.motionY = -.0784;
        scene.player.motionZ = -.1;
        scene.player.rotationYaw = 71;
        scene.player.rotationPitch = 32;
        AxisAlignedBB original = scene.player.getEntityBoundingBox();
        for (double x : new double[]{-.871234567, -.0, .0, .318765432})
            for (double z : new double[]{-.472345678, -.0, .0, .519876543}) unchanged(scene.move(x, z), x, z);
        assertSame(original, scene.player.getEntityBoundingBox());
        assertEquals(.2, scene.player.motionX, 0);
        assertEquals(-.0784, scene.player.motionY, 0);
        assertEquals(-.1, scene.player.motionZ, 0);
        assertEquals(71, scene.player.rotationYaw, 0);
        assertEquals(32, scene.player.rotationPitch, 0);
    }

    @Test public void axialEdgesKeepTheEntireSupportedPrefixInAllFourDirections() throws Exception {
        for (double[] direction : new double[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Scene scene = new Scene();
            scene.world.boxes.add(floor(0, 0, 1, 1, 64));
            NScaffoldEdgeGuard.Result result = scene.move(direction[0] * 1.2, direction[1] * 1.2);
            assertTrue(result.active);
            assertTrue(result.limited);
            assertEquals(direction[0] * .8, result.x, 3E-5);
            assertEquals(direction[1] * .8, result.z, 3E-5);
            assertTrue(Math.max(Math.abs(result.x), Math.abs(result.z)) < .8);
            assertEquals(1, scene.world.collisionReads);
        }
    }

    @Test public void diagonalCornerRetainsDirectionAndClipsOnlyBeforeFirstLoss() throws Exception {
        for (int xSign : new int[]{-1, 1}) for (int zSign : new int[]{-1, 1}) {
            Scene scene = new Scene();
            scene.world.boxes.add(floor(0, 0, 1, 1, 64));
            NScaffoldEdgeGuard.Result result = scene.move(1.5 * xSign, .5 * zSign);
            assertTrue(result.limited);
            assertEquals(.8 * xSign, result.x, 3E-5);
            assertEquals(result.x / (3 * xSign), result.z / zSign, 1E-14);
        }
    }

    @Test public void negativeWorldCoordinatesUseTheSameFootprintGeometry() throws Exception {
        Scene scene = new Scene();
        scene.position(-.5, 64, -.5);
        scene.world.boxes.add(floor(-1, -1, 0, 0, 64));
        NScaffoldEdgeGuard.Result result = scene.move(-1.2, -1.2);
        assertEquals(-.8, result.x, 3E-5);
        assertEquals(-.8, result.z, 3E-5);
        assertTrue(result.limited);
    }

    @Test public void supportIsTheRealFootprintEvenWhenTheCenterHasPassedTheBlock() throws Exception {
        Scene scene = new Scene();
        scene.position(1.15, 64, .5);
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        unchanged(scene.move(.1, 0), .1, 0);
        NScaffoldEdgeGuard.Result result = scene.move(.3, 0);
        assertTrue(result.limited);
        assertEquals(.15, result.x, 3E-5);
        assertTrue(result.x > .14);
    }

    @Test public void aSupportedEndpointCannotSkipAnUnsupportedGap() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        scene.world.boxes.add(floor(2, 0, 3, 1, 64));
        NScaffoldEdgeGuard.Result result = scene.move(2, 0);
        assertTrue(result.limited);
        assertEquals(.8, result.x, 3E-5);
    }

    @Test public void evenAnUnsampledTinyGapOrAZeroAreaContactCannotJoinSupports() {
        for (double nextStart : new double[]{1.6, 1.6002}) {
            double fraction = NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 2, 0, .6,
                    Arrays.asList(floor(0, 0, 1, 1, 64), floor(nextStart, 0, 4, 1, 64)));
            assertEquals(.4, fraction, 2E-5);
            assertTrue(fraction < .4);
        }
    }

    @Test public void contiguousSupportIntervalsCanArriveInAnyCollisionOrder() {
        List<AxisAlignedBB> boxes = Arrays.asList(floor(2, 0, 3, 1, 64),
                floor(1, 0, 2, 1, 64), floor(0, 0, 1, 1, 64));
        assertEquals(1, NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 2, 0, .6, boxes), 0);
    }

    @Test public void tallWallsAndCeilingsDoNotPretendToBeFloor() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        scene.world.boxes.add(new AxisAlignedBB(1, 63, 0, 4, 67, 1));
        scene.world.boxes.add(new AxisAlignedBB(-1, 65, -1, 4, 66, 2));
        assertEquals(.4, NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 2, 0, .6,
                scene.world.boxes), 2E-5);
        // The real wall already stops the player on the existing floor. Preserve
        // the attempted movement so vanilla owns that ordinary collision.
        unchanged(scene.move(2, 0), 2, 0);
        scene.world.boxes.remove(0);
        stopped(scene.move(.2, 0));
    }

    @Test public void ordinaryHalfStepsAndCarpetHeightTransitionsStayUnchanged() throws Exception {
        for (double top : new double[]{63.5, 64.0625, 64.5}) {
            Scene scene = new Scene();
            scene.world.boxes.add(floor(0, 0, 1, 1, 64));
            scene.world.boxes.add(floor(1, 0, 3, 1, top));
            unchanged(scene.move(1.3, 0), 1.3, 0);
        }
    }

    @Test public void deepDropsAndStepsAboveVanillaStepHeightDoNotCountAsSupport() throws Exception {
        for (double top : new double[]{63, 65}) {
            Scene scene = new Scene();
            scene.world.boxes.add(floor(0, 0, 1, 1, 64));
            scene.world.boxes.add(floor(1, 0, 3, 1, top));
            assertEquals(.8, NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 1.3, 0, .6,
                    scene.world.boxes) * 1.3, 3E-5);
        }
        Scene lowStep = new Scene();
        lowStep.player.stepHeight = .25f;
        lowStep.world.boxes.add(floor(0, 0, 1, 1, 64));
        lowStep.world.boxes.add(floor(1, 0, 3, 1, 64.5));
        assertEquals(.8, NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 1.3, 0, .25,
                lowStep.world.boxes) * 1.3, 3E-5);
    }

    @Test public void aFreeDiagonalBridgeNeverReceivesTheConservativeWallAxisCheck() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        scene.world.boxes.add(floor(1, 1, 2, 2, 64));
        unchanged(scene.move(1, 1), 1, 1);
        // The isolated X leg is unsafe, but no wall redirects this diagonal.
        assertTrue(NScaffoldEdgeGuard.supportedFraction(body(.5, 64, .5), 1, 0, .6,
                scene.world.boxes) < 1);
    }

    @Test public void aTallWallOnlyLimitsTheUnsafeSlideAndLeavesTheBlockedAxisToVanilla() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        scene.world.boxes.add(floor(1, 1, 2, 2, 64));
        scene.world.boxes.add(new AxisAlignedBB(1, 64, 0, 2, 67, 1));
        NScaffoldEdgeGuard.Result result = scene.move(1, 1);
        assertTrue(result.active);
        assertTrue(result.limited);
        assertEquals(1, result.x, 0);
        assertEquals(.8, result.z, 3E-5);
        assertTrue(result.z < .8);
    }

    @Test public void fullBodySweepAlsoDetectsAnOverhangingWallAboveTheFootBand() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        scene.world.boxes.add(floor(1, 1, 2, 2, 64));
        scene.world.boxes.add(new AxisAlignedBB(1, 65, 0, 2, 67, 1));
        NScaffoldEdgeGuard.Result result = scene.move(1, 1);
        assertTrue(result.limited);
        assertEquals(1, result.x, 0);
        assertEquals(.8, result.z, 3E-5);
    }

    @Test public void guardedMovementSurvivesTheActualVanillaWallSlideOnADiagonalPlatform() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture fixture = new RotationBehaviorHookTest.Fixture()) {
            Scene scene = new Scene(NativePlayer.class);
            fixture.mc.thePlayer = scene.player;
            fixture.mc.theWorld = scene.world;
            scene.player.width = .6f;
            scene.player.height = 1.8f;
            ScaffoldClientFlowTest.set(EntityPlayerSP.class, scene.player, "mc", fixture.mc);
            ScaffoldClientFlowTest.set(World.class, scene.world, "theProfiler", new CollisionProfiler());
            scene.world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            scene.world.blocks.put(new BlockPos(1, 63, 1), Blocks.stone.getDefaultState());
            scene.world.blocks.put(new BlockPos(1, 64, 0), Blocks.stone.getDefaultState());
            scene.world.blocks.put(new BlockPos(1, 65, 0), Blocks.stone.getDefaultState());

            // Positive control: the unguarded native X-then-Z collision really
            // redirects the diagonal off both actual supporting blocks.
            scene.player.moveEntity(1, -.0784, 1);
            assertEquals(.7, scene.player.posX, 1E-12);
            assertEquals(1.5, scene.player.posZ, 1E-12);
            AxisAlignedBB unsupported = scene.player.getEntityBoundingBox();
            assertTrue(scene.world.getCollidingBoundingBoxes(scene.player,
                    new AxisAlignedBB(unsupported.minX, unsupported.minY - 1E-4, unsupported.minZ,
                            unsupported.maxX, unsupported.minY, unsupported.maxZ)).isEmpty());

            scene.position(.5, 64, .5);
            scene.player.motionX = 1;
            NScaffoldEdgeGuard.Result result = scene.move(1, 1);
            scene.player.moveEntity(result.x, -.0784, result.z);
            assertTrue(result.limited);
            assertEquals(.7, scene.player.posX, 1E-12);
            assertTrue(scene.player.posZ > 1.29 && scene.player.posZ < 1.3);
            AxisAlignedBB supported = scene.player.getEntityBoundingBox();
            assertFalse(scene.world.getCollidingBoundingBoxes(scene.player,
                    new AxisAlignedBB(supported.minX, supported.minY - 1E-4, supported.minZ,
                            supported.maxX, supported.minY, supported.maxZ)).isEmpty());
            assertEquals(64, scene.player.posY, 0);
            assertTrue(scene.player.isCollidedHorizontally);
            assertEquals("vanilla still consumes the real wall collision", 0, scene.player.motionX, 0);
        }
    }

    @Test public void nativeSlabCarpetAndFenceCollisionShapesSupplyTheirActualTopAndWidth() throws Exception {
        Bootstrap.register();
        for (IBlockState state : new IBlockState[]{Blocks.stone_slab.getDefaultState(),
                Blocks.carpet.getDefaultState(), Blocks.oak_fence.getDefaultState()}) {
            Scene scene = new Scene();
            scene.world.blocks.put(new BlockPos(0, 63, 0), state);
            boolean fence = state.getBlock() == Blocks.oak_fence;
            double top = fence ? 64.5 : state.getBlock() == Blocks.carpet ? 63.0625 : 63.5;
            scene.position(.5, top, .5);
            unchanged(scene.move(.05, 0), .05, 0);
            NScaffoldEdgeGuard.Result result = scene.move(1, 0);
            assertTrue(result.limited);
            assertEquals(fence ? .425 : .8, result.x, 3E-5);
        }
    }

    @Test public void alreadyMissingSupportStopsResidualMovementWithoutInventingFloor() throws Exception {
        Scene scene = new Scene();
        scene.player.motionX = .31;
        stopped(scene.move(scene.player.motionX, -.07));
        assertEquals(.31, scene.player.motionX, 0);
    }

    @Test public void newlyPlacedRealSupportReleasesTheNextCallImmediately() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(0, 0, 1, 1, 64));
        assertTrue(scene.move(1, 0).limited);
        scene.world.boxes.add(floor(1, 0, 2, 1, 64));
        unchanged(scene.move(1, 0), 1, 0);
        assertEquals(2, scene.world.collisionReads);
    }

    @Test public void airborneAscentFlightNoclipAndRidingNeverEnterTheCollisionQuery() throws Exception {
        for (int mode = 0; mode < 5; mode++) {
            Scene scene = new Scene();
            if (mode == 0) scene.player.onGround = false;
            if (mode == 2) scene.player.capabilities.isFlying = true;
            if (mode == 3) scene.player.noClip = true;
            if (mode == 4) scene.player.ridingEntity = RotationBehaviorHookTest.bare(EntityPlayerSP.class);
            NScaffoldEdgeGuard.Result result = NScaffoldEdgeGuard.limit(scene.world, scene.player,
                    .3, mode == 1 ? .42 : -.0784, -.2, () -> true);
            assertFalse(result.active);
            assertFalse(result.limited);
            assertEquals(.3, result.x, 0);
            assertEquals(-.2, result.z, 0);
            assertEquals(0, scene.world.loadedReads);
            assertEquals(0, scene.world.collisionReads);
        }
    }

    @Test public void unloadedCellsAreRejectedBeforeAnyCollisionRead() throws Exception {
        Scene scene = new Scene();
        scene.world.loaded = pos -> pos.getX() < 1;
        scene.world.boxes.add(floor(0, 0, 3, 1, 64));
        stopped(scene.move(1, 0));
        assertTrue(scene.world.loadedReads > 0);
        assertEquals(0, scene.world.collisionReads);
    }

    @Test public void merelyOverlappingWorldBorderIsNotEnoughToQueryBeyondIt() throws Exception {
        Scene scene = new Scene();
        scene.world.border.setCenter(.5, .5);
        scene.world.border.setTransition(2);
        stopped(scene.move(1, 0));
        assertEquals(0, scene.world.loadedReads);
        assertEquals(0, scene.world.collisionReads);
    }

    @Test public void revocationAtEveryWorldCallbackDiscardsTheResultWithoutLaterReads() throws Exception {
        for (int stage = 0; stage < 3; stage++) {
            Scene scene = new Scene();
            AtomicBoolean valid = new AtomicBoolean(true);
            if (stage == 0) scene.world.beforeBorder = () -> valid.set(false);
            if (stage == 1) scene.world.beforeLoaded = () -> valid.set(false);
            if (stage == 2) scene.world.beforeCollision = () -> valid.set(false);
            NScaffoldEdgeGuard.Result result = NScaffoldEdgeGuard.limit(scene.world, scene.player,
                    .3, -.0784, -.2, valid::get);
            assertFalse(result.active);
            assertFalse(result.limited);
            assertEquals(.3, result.x, 0);
            assertEquals(-.2, result.z, 0);
            assertEquals(stage == 2 ? 1 : 0, scene.world.collisionReads);
            if (stage == 0) assertEquals(0, scene.world.loadedReads);
            if (stage == 1) assertEquals(1, scene.world.loadedReads);
        }
    }

    @Test public void callbackChangesToBodyGroundFlightOrStepInvalidateEvenAPermissiveCaller() throws Exception {
        for (int change = 0; change < 4; change++) {
            Scene scene = new Scene();
            final int kind = change;
            scene.world.beforeCollision = () -> {
                if (kind == 0) scene.player.setEntityBoundingBox(body(9, 64, 9));
                if (kind == 1) scene.player.onGround = false;
                if (kind == 2) scene.player.capabilities.isFlying = true;
                if (kind == 3) scene.player.stepHeight = 1;
            };
            NScaffoldEdgeGuard.Result result = scene.move(.3, -.2);
            assertFalse(result.active);
            assertFalse(result.limited);
            assertEquals(.3, result.x, 0);
            assertEquals(-.2, result.z, 0);
        }
    }

    @Test public void aDisableReenableGenerationCannotReuseTheEarlierCollisionResult() throws Exception {
        Scene scene = new Scene();
        AtomicLong generation = new AtomicLong(7);
        scene.world.beforeCollision = () -> generation.addAndGet(2);
        NScaffoldEdgeGuard.Result result = NScaffoldEdgeGuard.limit(scene.world, scene.player,
                .3, -.0784, 0, () -> generation.get() == 7);
        assertFalse(result.active);
        assertFalse(result.limited);
        assertEquals(.3, result.x, 0);
    }

    @Test public void theCurrentPredicateCannotChangeTheBodyAndStillAuthorizeTheOldSample() throws Exception {
        Scene scene = new Scene();
        BooleanSupplier changesBody = () -> {
            scene.player.setEntityBoundingBox(body(9, 64, 9));
            return true;
        };
        NScaffoldEdgeGuard.Result result = NScaffoldEdgeGuard.limit(scene.world, scene.player,
                .3, -.0784, 0, changesBody);
        assertFalse(result.active);
        assertEquals(0, scene.world.collisionReads);
    }

    @Test public void excessiveMotionHasABoundedQueryAndNeverApprovesTheUncheckedRemainder() throws Exception {
        Scene scene = new Scene();
        scene.world.boxes.add(floor(-10, -10, 10, 10, 64));
        NScaffoldEdgeGuard.Result result = scene.move(1E12, -1E12);
        assertTrue(result.active);
        assertTrue(result.limited);
        assertEquals(2, result.x, 0);
        assertEquals(-2, result.z, 0);
        assertEquals(1, scene.world.collisionReads);
        assertTrue(scene.world.loadedReads <= 16);
        assertTrue(scene.world.lastQuery.maxX - scene.world.lastQuery.minX < 3);
        assertTrue(scene.world.lastQuery.maxZ - scene.world.lastQuery.minZ < 3);
    }

    @Test public void malformedMotionAndBodyFailClosedBeforeWorldAccess() throws Exception {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Scene scene = new Scene();
            stopped(scene.move(bad, .2));
            assertEquals(0, scene.world.loadedReads);
            assertEquals(0, scene.world.collisionReads);
        }
        Scene scene = new Scene();
        scene.player.setEntityBoundingBox(new AxisAlignedBB(-100, 64, -100, 100, 66, 100));
        stopped(scene.move(.2, .2));
        assertEquals(0, scene.world.loadedReads);
        assertEquals(0, scene.world.collisionReads);
    }
}
