package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Source-level parity against pinned Demise bodies, plus explicit Neutron scene boundaries. */
public class DemiseBedBreakerGeometryTest {
    @BeforeClass public static void blocks() { Bootstrap.register(); }

    @Test public void referenceResourcesMatchPinnedGitBlobs() throws Exception {
        assertEquals("f3eb8bab8cb2b087d87568f6184e10d0781275e2", gitBlob("BedBreaker.java.txt"));
        assertEquals("8b6b870489603d843635776e4f4545f3466f7696", gitBlob("RotationUtils.java.txt"));
        assertEquals("973ef3e8b2d0989a7a851bc4da0d65ec68cda64b", gitBlob("Vec3i.java.txt"));
    }

    @Test public void selectedBedsMatchOriginalSearchAcrossNegativeCoordinatesAndBothParts() {
        Random random = new Random(0xe219a97L);
        Vec3[] feet = {new Vec3(-.2,64.2,-.7), new Vec3(.5,64,.5), new Vec3(-6.9,70.125,4.4),
                new Vec3(10.75,2.2,-8.25), new Vec3(3,252,3)};
        for (Vec3 position : feet) for (float eye : new float[]{0,1.54f,1.62f}) for (int run=0;run<4;run++) {
            Cells cells = new Cells();
            for (int i=0;i<100;i++) {
                BlockPos pos = new BlockPos(Math.floor(position.xCoord)+random.nextInt(13)-6,
                        Math.floor(position.yCoord)+random.nextInt(14)-5,
                        Math.floor(position.zCoord)+random.nextInt(13)-6);
                if (pos.getY()<0 || pos.getY()>=256) continue;
                cells.blocks.put(pos, random.nextBoolean() ? Blocks.stone.getDefaultState()
                        : Blocks.bed.getDefaultState().withProperty(BlockBed.PART,
                        random.nextBoolean()?BlockBed.EnumPartType.FOOT:BlockBed.EnumPartType.HEAD));
            }
            Original original = new Original(cells.blocks,position,eye);
            original.getBedPos();
            DemiseBedBreakerGeometry.Target actual = DemiseBedBreakerGeometry.find(cells,position,eye,()->true);
            assertEquals(original.bedPos,actual==null?null:actual.pos);
            if (actual!=null) {
                assertSame(cells.blocks.get(actual.pos),actual.state);
                assertEquals(actual.pos.getX()+.5,actual.aim.xCoord,0);
                assertEquals(actual.pos.getY()+.5,actual.aim.yCoord,0);
                assertEquals(actual.pos.getZ()+.5,actual.aim.zCoord,0);
            }
        }
    }

    @Test public void traversalRetainsOriginalDuplicatesAndXThenYThenZOrder() {
        Cells cells = new Cells(); Vec3 feet = new Vec3(-.2,64,-.7);
        cells.bed(new BlockPos(-4,64,-4));
        Original original = new Original(cells.blocks,feet,1.62f); original.getBedPos();
        DemiseBedBreakerGeometry.Target target = DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true);
        assertNotNull(target);
        assertEquals(1000,original.mc.theWorld.reads.size());
        assertEquals(original.mc.theWorld.reads,cells.reads.subList(0,1000));
        assertEquals("Only final live-state validation adds a read",1001,cells.reads.size());
        assertEquals(target.pos,cells.reads.get(1000));
        assertEquals(4,Collections.frequency(cells.reads,new BlockPos(0,64,0)));
    }

    @Test public void negativeCoordinatesUseSourceIntTruncationRatherThanFloor() {
        Cells cells = new Cells(); Vec3 feet = new Vec3(-.2,64,.5);
        cells.bed(new BlockPos(-5,64,0));
        assertNull(DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true));
        for (BlockPos read : cells.reads) assertNotEquals(-5,read.getX());
        cells.reads.clear(); cells.bed(new BlockPos(-4,64,0));
        assertEquals(new BlockPos(-4,64,0),DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
        assertEquals(2,Collections.frequency(cells.reads,new BlockPos(0,64,0)));
    }

    @Test public void equalDistancesKeepTheFirstCellInTheSourceTraversal() {
        Vec3 feet = new Vec3(0,64,0);
        BlockPos[][] pairs = {{new BlockPos(-1,64,0),new BlockPos(1,64,0)},
                {new BlockPos(0,63,0),new BlockPos(0,65,0)},
                {new BlockPos(0,64,-1),new BlockPos(0,64,1)}};
        for (BlockPos[] pair : pairs) {
            Cells cells = new Cells(); cells.bed(pair[0]); cells.bed(pair[1]);
            assertEquals(pair[0],DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
        }
    }

    @Test public void rankingUsesBlockCornerAndPlayerFeetInsteadOfCenterOrEyes() {
        Cells cells = new Cells(); Vec3 feet = new Vec3(.9,64,.9);
        cells.bed(new BlockPos(0,64,0)); cells.bed(new BlockPos(1,64,1));
        assertEquals(new BlockPos(1,64,1),DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
        cells.blocks.clear(); feet = new Vec3(.1,64.2,.1);
        cells.bed(new BlockPos(0,64,0)); cells.bed(new BlockPos(0,66,0));
        assertEquals(new BlockPos(0,64,0),DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
    }

    @Test public void eachHalfCanWinAndEverySearchReevaluatesTheNearestBed() {
        Cells cells = new Cells(); Vec3 feet = new Vec3(.5,64,.5);
        BlockPos foot = new BlockPos(0,64,3), head = new BlockPos(1,64,3);
        cells.blocks.put(foot,Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.EAST));
        cells.blocks.put(head,Blocks.bed.getDefaultState().withProperty(BlockBed.PART,BlockBed.EnumPartType.HEAD)
                .withProperty(BlockBed.FACING,EnumFacing.EAST));
        assertEquals(foot,DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
        assertEquals(head,DemiseBedBreakerGeometry.find(cells,new Vec3(1.2,64,1),1.62f,()->true).pos);
        BlockPos closer = new BlockPos(0,64,1); cells.bed(closer);
        assertEquals(closer,DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true).pos);
        cells.blocks.clear(); assertNull(DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true));
    }

    @Test public void sourceCubeSearchDoesNotAddASphericalReachOrOcclusionFilter() throws Exception {
        Cells cells = new Cells(); Vec3 feet = new Vec3(0,64,0);
        BlockPos far = new BlockPos(4,68,4); cells.bed(far);
        DemiseBedBreakerGeometry.Target target = DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true);
        assertNotNull(target); assertEquals(far,target.pos);
        Vec3 eyes = new Vec3(0,64+1.62f,0);
        assertTrue(eyes.squareDistanceTo(target.aim)>4.5*4.5);
        RayWorld world = world(); world.blocks.putAll(cells.blocks);
        float[] aim = DemiseBedBreakerGeometry.rotations(eyes,far);
        assertNull(DemiseBedBreakerGeometry.rayTraceBed(world,far,eyes,
                EntityPlayerSP.getVectorForRotation(aim[1],aim[0])));
    }

    @Test public void unloadedCellsAndWorldHeightAreHostBoundaries() {
        Cells cells = new Cells(); BlockPos bed = new BlockPos(0,1,0); cells.bed(bed); cells.unloaded.add(bed);
        cells.bed(new BlockPos(0,-1,0));
        assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(.5,0,.5),1.62f,()->true));
        assertFalse(cells.reads.contains(bed));
        for (BlockPos pos : cells.loads) assertTrue(pos.getY()>=0&&pos.getY()<256);
        for (BlockPos pos : cells.reads) assertTrue(pos.getY()>=0&&pos.getY()<256);
    }

    @Test public void staleLoadedAndStateCallbacksCannotPublishOrContinueARead() {
        for (boolean loaded : new boolean[]{true,false}) {
            Cells cells = new Cells(); cells.bed(new BlockPos(0,64,1)); AtomicBoolean valid = new AtomicBoolean(true);
            if (loaded) cells.afterLoaded=p->valid.set(false); else cells.afterRead=p->valid.set(false);
            assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(.5,64,.5),1.62f,valid::get));
            assertEquals(loaded?0:1,cells.reads.size());
        }
    }

    @Test public void finalValidationRejectsABedRemovedAfterItsScanCell() {
        Cells cells = new Cells(); BlockPos bed = new BlockPos(0,64,1); cells.bed(bed);
        cells.afterRead=pos->{if (pos.equals(new BlockPos(5,70,5))) cells.blocks.remove(bed);};
        assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(.5,64,.5),1.62f,()->true));
        assertEquals(bed,cells.reads.get(cells.reads.size()-1));
    }

    @Test public void malformedInputCannotEnterTheScanOrReadWorldData() {
        Cells cells = new Cells();
        for (Vec3 feet : new Vec3[]{null,new Vec3(Double.NaN,64,0),new Vec3(0,Double.POSITIVE_INFINITY,0),
                new Vec3(Double.MAX_VALUE,64,0),new Vec3(Integer.MAX_VALUE,64,0)})
            assertNull(DemiseBedBreakerGeometry.find(cells,feet,1.62f,()->true));
        for (float eye : new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.MAX_VALUE,-1})
            assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(0,64,0),eye,()->true));
        assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(0,64,0),1.62f,()->false));
        assertNull(DemiseBedBreakerGeometry.find(cells,new Vec3(0,64,0),1.62f,null));
        assertEquals(0,cells.loads.size()); assertEquals(0,cells.reads.size());
    }

    @Test public void centerAimRetainsTheOriginalFloatSqrtAndUnwrappedYaw() {
        for (Vec3 eyes : new Vec3[]{new Vec3(.123,65.62,.456),new Vec3(-.5,72.125,3.4),new Vec3(.5,64.5,.5)})
            for (BlockPos bed : new BlockPos[]{new BlockPos(3,63,1),new BlockPos(-2,64,-2),new BlockPos(0,64,0)}) {
                float[] original = Original.rotations(eyes,bed), actual = DemiseBedBreakerGeometry.rotations(eyes,bed);
                assertEquals(Float.floatToIntBits(original[0]),Float.floatToIntBits(actual[0]));
                assertEquals(Float.floatToIntBits(original[1]),Float.floatToIntBits(actual[1]));
            }
        assertEquals(-225,DemiseBedBreakerGeometry.rotations(new Vec3(.5,64.5,.5),new BlockPos(-2,64,-2))[0],0);
        assertArrayEquals(new float[]{-90,-0.0f},DemiseBedBreakerGeometry.rotations(new Vec3(.5,64.5,.5),new BlockPos(0,64,0)),0);
    }

    @Test public void nonLegitRayIntersectsTheBedWhileLegitWorldRayFindsTheDefense() throws Exception {
        RayWorld world = world(); Vec3 eyes = new Vec3(.5,65.62,.5);
        BlockPos bed = new BlockPos(0,64,3), defense = new BlockPos(0,65,1);
        world.blocks.put(bed,Blocks.bed.getDefaultState()); world.blocks.put(defense,Blocks.stone.getDefaultState());
        float[] aim = DemiseBedBreakerGeometry.rotations(eyes,bed);
        Vec3 look = EntityPlayerSP.getVectorForRotation(aim[1],aim[0]);
        MovingObjectPosition direct = DemiseBedBreakerGeometry.rayTraceBed(world,bed,eyes,look);
        assertNotNull(direct); assertEquals(bed,direct.getBlockPos()); assertEquals(0,world.worldRays);
        assertTrue(direct.hitVec.yCoord<=64.5625+1e-7);
        MovingObjectPosition picked = world.rayTraceBlocks(eyes,eyes.addVector(look.xCoord*4.5,look.yCoord*4.5,look.zCoord*4.5),false,false,false);
        assertNotNull(picked); assertEquals(defense,picked.getBlockPos());
        assertTrue(DemiseBedBreakerGeometry.matches(direct,bed,eyes,4.5));
        assertFalse(DemiseBedBreakerGeometry.matches(picked,bed,eyes,4.5));
        assertTrue(DemiseBedBreakerGeometry.matches(picked,defense,eyes,4.5));
    }

    private static final class Cells implements DemiseBedBreakerGeometry.Access {
        final Map<BlockPos,IBlockState> blocks = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        final List<BlockPos> loads = new ArrayList<>(), reads = new ArrayList<>();
        Consumer<BlockPos> afterLoaded, afterRead;
        void bed(BlockPos pos) { blocks.put(pos,Blocks.bed.getDefaultState()); }
        public boolean loaded(BlockPos pos) {
            loads.add(pos); boolean result=!unloaded.contains(pos);
            if (afterLoaded!=null) afterLoaded.accept(pos); return result;
        }
        public IBlockState state(BlockPos pos) {
            reads.add(pos); IBlockState result=blocks.getOrDefault(pos,Blocks.air.getDefaultState());
            if (afterRead!=null) afterRead.accept(pos); return result;
        }
    }

    public static class RayWorld extends WorldClient {
        Map<BlockPos,IBlockState> blocks; int worldRays;
        public RayWorld() { super(null,null,0,null,null); }
        @Override public IBlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.air.getDefaultState()); }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from,Vec3 to,boolean liquid,boolean empty,boolean last) {
            worldRays++; return super.rayTraceBlocks(from,to,liquid,empty,last);
        }
    }
    private static RayWorld world() throws Exception {
        RayWorld world=RotationBehaviorHookTest.bare(RayWorld.class); world.blocks=new HashMap<>(); return world;
    }

    /** Unmodified getBedPos method body with minimal original Minecraft accessors. */
    private static final class Original {
        final Client mc;
        BlockPos bedPos, cachedBedPos;
        Original(Map<net.minecraft.util.BlockPos,IBlockState> blocks,Vec3 feet,float eyeHeight) {
            mc=new Client(blocks,feet,eyeHeight);
        }
        private void getBedPos() {
            bedPos = null;
            double nearestPos = Double.MAX_VALUE;
            for (double x = mc.thePlayer.posX - 4.5; x <= mc.thePlayer.posX + 4.5; x++) {
                for (double y = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 4.5; y <= mc.thePlayer.posY + mc.thePlayer.getEyeHeight() + 4.5; y++) {
                    for (double z = mc.thePlayer.posZ - 4.5; z <= mc.thePlayer.posZ + 4.5; z++) {
                        BlockPos pos = new BlockPos((int) x, (int) y, (int) z);

                        if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed && pos.distanceTo(mc.thePlayer.getPositionVector()) < nearestPos) {
                            bedPos = pos;
                            cachedBedPos = pos;
                            nearestPos = pos.distanceTo(mc.thePlayer.getPositionVector());
                        }
                    }
                }
            }
        }
        private static float[] rotations(Vec3 eyes,net.minecraft.util.BlockPos pos) {
            return getRotations(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,eyes.xCoord,eyes.yCoord,eyes.zCoord);
        }
        /** Unmodified six-coordinate RotationUtils.getRotations body. */
        private static float[] getRotations(double rotX,double rotY,double rotZ,double startX,double startY,double startZ) {
            double x = rotX - startX;
            double y = rotY - startY;
            double z = rotZ - startZ;
            double dist = MathHelper.sqrt_double(x * x + z * z);
            float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
            float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
            return new float[]{yaw, pitch};
        }
        private static final class BlockPos extends net.minecraft.util.BlockPos {
            BlockPos(int x,int y,int z) { super(x,y,z); }
            public double distanceTo(Vec3 vec) {
                double dx = (double) this.getX() - vec.xCoord;
                double dy = (double) this.getY() - vec.yCoord;
                double dz = (double) this.getZ() - vec.zCoord;
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        private static final class Client {
            final Player thePlayer; final World theWorld;
            Client(Map<net.minecraft.util.BlockPos,IBlockState> blocks,Vec3 feet,float eye) {
                thePlayer=new Player(feet,eye); theWorld=new World(blocks);
            }
        }
        private static final class Player {
            final double posX,posY,posZ; final float eye;
            Player(Vec3 feet,float eye) { posX=feet.xCoord;posY=feet.yCoord;posZ=feet.zCoord;this.eye=eye; }
            float getEyeHeight() { return eye; }
            Vec3 getPositionVector() { return new Vec3(posX,posY,posZ); }
        }
        private static final class World {
            final Map<net.minecraft.util.BlockPos,IBlockState> blocks;
            final List<net.minecraft.util.BlockPos> reads=new ArrayList<>();
            World(Map<net.minecraft.util.BlockPos,IBlockState> blocks) { this.blocks=blocks; }
            IBlockState getBlockState(net.minecraft.util.BlockPos pos) {
                reads.add(pos); return blocks.getOrDefault(pos,Blocks.air.getDefaultState());
            }
        }
    }

    private static String gitBlob(String name) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (InputStream input=DemiseBedBreakerGeometryTest.class.getResourceAsStream("/demise-reference/"+name)) {
            assertNotNull(input); byte[] block=new byte[4096]; int read;
            while ((read=input.read(block))!=-1) bytes.write(block,0,read);
        }
        MessageDigest sha=MessageDigest.getInstance("SHA-1");
        sha.update(("blob "+bytes.size()+"\0").getBytes(StandardCharsets.US_ASCII));
        StringBuilder result=new StringBuilder();
        for (byte value:sha.digest(bytes.toByteArray())) result.append(String.format("%02x",value&255));
        return result.toString();
    }
}
