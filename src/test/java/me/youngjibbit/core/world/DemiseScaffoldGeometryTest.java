package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

/**
 * Behavioral fixtures from DemisePrivate e219a97, Scaffold.java (blob f5cbead2)
 * and RotationUtils.java (blob 8b6b8704). World storage and ray replies are synthetic;
 * production Minecraft positions, blocks, eye interpolation and look vectors are used.
 */
public class DemiseScaffoldGeometryTest {
    public static class Scene extends WorldClient {
        Map<BlockPos, IBlockState> cells;
        int blockReads;
        int rays;
        Vec3 firstFrom, firstTo, lastTo;
        boolean stopOnLiquid, ignoreWithoutBox, returnLast;
        BiFunction<Vec3, Vec3, MovingObjectPosition> response;

        public Scene() { super(null, null, 0, null, null); }

        @Override public IBlockState getBlockState(BlockPos pos) {
            blockReads++;
            return cells.getOrDefault(pos, Blocks.air.getDefaultState());
        }

        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to,
                boolean liquid, boolean empty, boolean last) {
            rays++;
            if (rays == 1) { firstFrom = from; firstTo = to; }
            lastTo = to;
            stopOnLiquid = liquid;
            ignoreWithoutBox = empty;
            returnLast = last;
            return response == null ? null : response.apply(from, to);
        }

        void put(BlockPos pos, Block block) { cells.put(pos, block.getDefaultState()); }
    }

    @BeforeClass public static void bootstrapBlocks() { Bootstrap.register(); }

    static Scene scene() throws Exception {
        Scene scene = RotationBehaviorHookTest.bare(Scene.class);
        scene.cells = new HashMap<>();
        return scene;
    }

    static EntityPlayerSP player(Scene world) throws Exception {
        EntityPlayerSP player = RotationBehaviorHookTest.bare(EntityPlayerSP.class);
        player.worldObj = world;
        player.posX = player.prevPosX = .5;
        player.posY = player.prevPosY = 65;
        player.posZ = player.prevPosZ = .5;
        return player;
    }

    private static void assertPlacement(DemiseScaffoldGeometry.PlaceData actual,
            BlockPos support, EnumFacing facing) {
        assertNotNull(actual);
        assertEquals(support, actual.blockPos);
        assertSame(facing, actual.facing);
    }

    @Test public void immediateSupportsKeepOriginalWestEastSouthNorthDownPriority() throws Exception {
        Scene world = scene();
        BlockPos origin = new BlockPos(-4, 63, -7);
        BlockPos[] supports = {origin.west(), origin.east(), origin.south(), origin.north(), origin.down()};
        EnumFacing[] faces = {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP};
        for (BlockPos support : supports) world.put(support, Blocks.stone);
        for (int i = 0; i < supports.length; i++) {
            // An immediate support wins even when a later support is nearer the player.
            assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.east(10), null), supports[i], faces[i]);
            world.cells.remove(supports[i]);
        }
    }

    @Test public void previousBlockOverridesMaterialAndInteractionFiltersAtItsOriginalPosition() throws Exception {
        Scene world = scene();
        BlockPos origin = new BlockPos(0, 63, 0);
        world.put(origin.east(), Blocks.stone);
        for (Block previous : new Block[]{Blocks.chest, Blocks.tallgrass}) {
            world.put(origin.west(), previous);
            assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), null), origin.east(), EnumFacing.WEST);
            assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), origin.west()), origin.west(), EnumFacing.EAST);
        }
        world.put(origin.west(), Blocks.air);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), origin.west()), origin.east(), EnumFacing.WEST);
        world.put(origin.west(), Blocks.stone);
        world.put(origin.east(), Blocks.chest);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), origin.east()), origin.west(), EnumFacing.EAST);
    }

    @Test public void supportEligibilityDoesNotApplyInventoryOrFullCubeFilters() throws Exception {
        Scene world = scene();
        BlockPos origin = new BlockPos(0, 63, 0);
        world.put(origin.west(), Blocks.carpet);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), null), origin.west(), EnumFacing.EAST);
    }

    @Test public void stableDistanceSortingAndSixOffsetLevelsMatchThePinnedSearch() throws Exception {
        Scene world = scene();
        BlockPos origin = new BlockPos(0, 63, 0);
        world.put(origin.west(2), Blocks.stone);
        world.put(origin.east(2), Blocks.stone);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), null), origin.west(2), EnumFacing.EAST);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.east(2).up(), null), origin.east(2), EnumFacing.WEST);
        world.cells.clear();
        world.put(origin.west(7), Blocks.stone);
        assertPlacement(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), null), origin.west(7), EnumFacing.EAST);
        world.cells.clear();
        world.put(origin.west(8), Blocks.stone);
        world.blockReads = 0;
        assertNull(DemiseScaffoldGeometry.findBlock(world, origin, origin.up(), null));
        // Original multiset traversal: five neighbor reads for 1+5+25+125+625+3125+15625 origins.
        assertEquals(97655, world.blockReads);
    }

    @Test public void faceCenterUsesTheUnitCubeAndRotationsRetainUnwrappedYaw() throws Exception {
        Vec3 top = DemiseScaffoldGeometry.faceCenter(new BlockPos(-2, 63, 5), EnumFacing.UP);
        assertEquals(-1.5, top.xCoord, 0);
        assertEquals(64, top.yCoord, 0);
        assertEquals(5.5, top.zCoord, 0);
        EntityPlayerSP player = player(scene());
        float[] aim = DemiseScaffoldGeometry.rotations(player,
                new Vec3(player.posX - 1, player.posY + player.getEyeHeight(), player.posZ - 1));
        assertEquals(-225, aim[0], 0);
        assertEquals(0, aim[1], 0);
    }

    @Test public void rayUsesInterpolatedEyesAndOriginalFlagsAndReturnsAnEndpointMiss() throws Exception {
        Scene world = scene();
        EntityPlayerSP player = player(world);
        player.posX = 2; player.prevPosX = 0;
        player.posY = 64; player.prevPosY = 62;
        player.posZ = 4; player.prevPosZ = 0;
        player.rotationYaw = 90;
        MovingObjectPosition miss = DemiseScaffoldGeometry.rayTrace(player, new float[]{0, 0}, 4.5, .5f);
        assertEquals(1, world.firstFrom.xCoord, 0);
        assertEquals(63 + (double) player.getEyeHeight(), world.firstFrom.yCoord, 0);
        assertEquals(2, world.firstFrom.zCoord, 0);
        assertEquals(1, world.lastTo.xCoord, 1e-6);
        assertEquals(6.5, world.lastTo.zCoord, 1e-6);
        assertFalse(world.stopOnLiquid);
        assertTrue(world.ignoreWithoutBox);
        assertTrue(world.returnLast);
        assertSame(MovingObjectPosition.MovingObjectType.MISS, miss.typeOfHit);
        assertSame(EnumFacing.UP, miss.sideHit);
        assertEquals(0, world.lastTo.squareDistanceTo(miss.hitVec), 0);
        assertEquals(new BlockPos(world.lastTo), miss.getBlockPos());
    }

    @Test public void denseFaceSearchTestsThePreviousBestRayAndKeepsAllFloatSamples() throws Exception {
        Scene world = scene();
        EntityPlayerSP player = player(world);
        BlockPos support = new BlockPos(0, 63, 0);
        DemiseScaffoldGeometry.PlaceData data = new DemiseScaffoldGeometry.PlaceData(EnumFacing.UP, support);
        float[] firstCandidate = DemiseScaffoldGeometry.rotations(player, new Vec3(.1f, 64, .1f));
        world.response = (from, to) -> world.rays == 1 ? new MovingObjectPosition(to, EnumFacing.UP, support) : null;
        assertArrayEquals(firstCandidate, DemiseScaffoldGeometry.bestRotation(player, data, firstCandidate), 0);
        assertEquals(6561, world.rays);
        // The first candidate is a corner, but the original first ray still points at the face center.
        assertEquals(player.posX, world.firstTo.xCoord, 1e-6);
        assertEquals(player.posZ, world.firstTo.zCoord, 1e-6);
        assertTrue(world.firstTo.yCoord < world.firstFrom.yCoord);
    }

    @Test public void candidateChoiceFollowsServerRotationAndFallsBackToCenterWhenItsRayMisses() throws Exception {
        Scene world = scene();
        EntityPlayerSP player = player(world);
        BlockPos support = new BlockPos(0, 63, 0);
        DemiseScaffoldGeometry.PlaceData data = new DemiseScaffoldGeometry.PlaceData(EnumFacing.UP, support);
        world.response = (from, to) -> new MovingObjectPosition(to, EnumFacing.UP, support);
        float[] south = DemiseScaffoldGeometry.bestRotation(player, data, new float[]{0, 0});
        float[] north = DemiseScaffoldGeometry.bestRotation(player, data, new float[]{180, 0});
        assertEquals(0, south[0], .001);
        assertEquals(-180, north[0], .001);
        assertTrue(south[1] < 82);
        assertTrue(north[1] < 82);
        assertEquals(0, player.rotationYaw, 0);
        world.response = null;
        assertArrayEquals(DemiseScaffoldGeometry.rotations(player, DemiseScaffoldGeometry.faceCenter(support, EnumFacing.UP)),
                DemiseScaffoldGeometry.bestRotation(player, data, new float[]{0, 0}), 0);
    }
}
