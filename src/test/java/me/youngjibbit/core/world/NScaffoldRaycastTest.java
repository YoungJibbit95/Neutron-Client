package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.*;
import net.minecraft.world.border.WorldBorder;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Real World voxel traversal and block collision rays; only storage and lifetime are supplied. */
public class NScaffoldRaycastTest {
    private static final BlockPos DESTINATION=new BlockPos(0,63,0),SUPPORT=DESTINATION.west();
    public static class Scene extends WorldClient {
        Map<BlockPos,IBlockState> cells; Set<BlockPos> unloaded,unloadedChunks; WorldBorder border;
        int reads,rays,unloadedReads; Vec3 lastFrom; Runnable afterRay; boolean invalidateOnRead; AtomicBoolean current;
        public Scene(){super(null,null,0,null,null);}
        @Override public IBlockState getBlockState(BlockPos pos){reads++;if(unloadedChunks.contains(chunk(pos)))unloadedReads++;if(invalidateOnRead)current.set(false);return cells.getOrDefault(pos,Blocks.air.getDefaultState());}
        @Override public boolean isBlockLoaded(BlockPos pos){return pos.getY()>=0&&pos.getY()<256&&!unloaded.contains(pos)&&!unloadedChunks.contains(chunk(pos));}
        @Override public WorldBorder getWorldBorder(){return border;}
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from,Vec3 to,boolean liquid,boolean ignore,boolean last){
            rays++;lastFrom=from;MovingObjectPosition hit=super.rayTraceBlocks(from,to,liquid,ignore,last);afterRay.run();return hit;
        }
        void put(BlockPos pos,Block block){cells.put(pos,block.getDefaultState());}
        static BlockPos chunk(BlockPos pos){return new BlockPos(pos.getX()>>4,0,pos.getZ()>>4);}
    }
    /** A real narrow collision box obstructs west rays while east rays stay visible. */
    private static final class PartialOccluder extends Block {
        PartialOccluder(){super(Material.rock);setBlockBounds(0,0,0,.45f,1,1);}
    }
    @BeforeClass public static void blocks(){Bootstrap.register();}
    private static Scene scene() throws Exception {
        Scene w=RotationBehaviorHookTest.bare(Scene.class);w.cells=new HashMap<>();w.unloaded=new HashSet<>();w.unloadedChunks=new HashSet<>();
        w.border=new WorldBorder();w.afterRay=()->{};w.current=new AtomicBoolean(true);return w;
    }
    private static EntityPlayerSP player(Scene w,double x,double y,double z)throws Exception {
        EntityPlayerSP p=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);p.worldObj=w;
        p.posX=p.prevPosX=x;p.posY=p.prevPosY=y;p.posZ=p.prevPosZ=z;p.setEntityBoundingBox(new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3));return p;
    }
    private static float[] aim(Vec3 a,Vec3 b){double x=b.xCoord-a.xCoord,y=b.yCoord-a.yCoord,z=b.zCoord-a.zCoord;
        return new float[]{(float)Math.toDegrees(Math.atan2(z,x))-90,(float)-Math.toDegrees(Math.atan2(y,Math.sqrt(x*x+z*z)))};}
    private static NScaffoldRaycast.Plan centerPlan(Scene w,EntityPlayerSP p,Vec3 point){float[] r=aim(p.getPositionEyes(1),point);
        return NScaffoldRaycast.find(p,DESTINATION,null,r[0],r[1],4.5,w.current::get);}
    private static MovingObjectPosition trace(Scene w,EntityPlayerSP p,NScaffoldRaycast.Plan plan,Vec3 point,double reach){float[] r=aim(p.getPositionEyes(1),point);
        return NScaffoldRaycast.trace(w,p.getPositionEyes(1),r[0],r[1],reach,plan,w.current::get);}

    @Test public void retainsARealCurrentFaceInOneRayWithoutChangingCamera()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);p.rotationYaw=37;p.rotationPitch=-12;
        NScaffoldRaycast.Plan plan=centerPlan(w,p,new Vec3(0,63.5,.5));assertNotNull(plan);assertEquals(1,w.rays);
        assertEquals(DESTINATION,plan.destination);assertEquals(SUPPORT,plan.data.blockPos);assertEquals(EnumFacing.EAST,plan.data.facing);
        assertEquals(w.cells.get(SUPPORT),plan.supportState);assertEquals(0,plan.hitVec.xCoord,1e-7);
        assertNotNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));
        assertEquals(37,p.rotationYaw,0);assertEquals(-12,p.rotationPitch,0);
        float[] copy=plan.rotation();copy[0]=999;assertNotEquals(999,plan.yaw,0);
    }
    @Test public void anOccupiedRequestedCellDoesNotStartBuildingBesideIt()throws Exception {
        Scene w=scene();w.put(DESTINATION,Blocks.stone);w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        assertNull(NScaffoldRaycast.find(p,DESTINATION,SUPPORT,0,0,4.5,w.current::get));assertEquals(0,w.rays);
    }
    @Test public void hiddenFirstSupportDoesNotHideTheVisibleAlternative()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);w.put(DESTINATION.east(),Blocks.stone);w.put(DESTINATION.up(),new PartialOccluder());
        EntityPlayerSP p=player(w,.5,64,.5);float[] west=aim(p.getPositionEyes(1),new Vec3(0,63.5,.5));
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,DESTINATION,SUPPORT,west[0],west[1],4.5,w.current::get);
        assertNotNull(plan);assertEquals(DESTINATION,plan.destination);assertEquals(DESTINATION.east(),plan.data.blockPos);assertEquals(EnumFacing.WEST,plan.data.facing);
        assertNotNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));assertTrue(w.rays<20);
    }
    @Test public void unreachableEarlierSupportIsSkippedWithinTheActualReach()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);w.put(DESTINATION.east(),Blocks.stone);EntityPlayerSP p=player(w,.99,64,.5);
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,DESTINATION,SUPPORT,90,70,1.89,w.current::get);
        assertNotNull(plan);assertEquals(DESTINATION.east(),plan.data.blockPos);assertTrue(p.getPositionEyes(1).distanceTo(plan.hitVec)<=1.89);
        assertNotNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,1.89,plan,w.current::get));
        assertNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,1,plan,w.current::get));
    }
    @Test public void aMissGetsARealInteriorAim()throws Exception {
        Scene w=scene();BlockPos support=new BlockPos(8,63,-1),destination=support.east();w.put(support,Blocks.stone);
        Vec3 eyes=new Vec3(9.299980012,65.62,-.93259317);EntityPlayerSP p=player(w,eyes.xCoord,64,eyes.zCoord);
        float yaw=67.000015f,pitch=83.00001f;Vec3 direction=net.minecraft.entity.Entity.getVectorForRotation(pitch,yaw);
        assertNull(w.rayTraceBlocks(eyes,eyes.addVector(direction.xCoord*4.5,direction.yCoord*4.5,direction.zCoord*4.5),false,false,false));
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,eyes,destination,support,yaw,pitch,4.5,w.current::get);
        assertNotNull(plan);assertEquals(destination,plan.destination);assertEquals(support,plan.data.blockPos);
        MovingObjectPosition hit=NScaffoldRaycast.trace(w,eyes,plan.yaw,plan.pitch,4.5,plan,w.current::get);
        assertNotNull(hit);assertEquals(MovingObjectPosition.MovingObjectType.BLOCK,hit.typeOfHit);
    }
    @Test public void aWrongTopHitKeepsTheAlreadyUsableTangentialCoordinate()throws Exception {
        Scene w=scene();BlockPos support=new BlockPos(11,63,0),destination=support.east();w.put(support,Blocks.stone);
        Vec3 eyes=new Vec3(12.29998,65.62,.97359316);EntityPlayerSP p=player(w,eyes.xCoord,64,eyes.zCoord);
        Vec3 direction=net.minecraft.entity.Entity.getVectorForRotation(75.4f,90);
        MovingObjectPosition old=w.rayTraceBlocks(eyes,eyes.addVector(direction.xCoord*4.5,direction.yCoord*4.5,direction.zCoord*4.5),false,false,false);
        assertNotNull(old);assertEquals(EnumFacing.UP,old.sideHit);
        for(double margin:new double[]{0,.01}) {
            NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,eyes,destination,support,90,75.4f,4.5,w.current::get,margin);
            assertNotNull(plan);assertEquals(destination,plan.destination);assertEquals(EnumFacing.EAST,plan.data.facing);
            assertEquals(0,MathHelper.wrapAngleTo180_float(plan.yaw-90),.01);
            MovingObjectPosition hit=NScaffoldRaycast.trace(w,eyes,plan.yaw,plan.pitch,4.5,plan,w.current::get);
            assertNotNull(hit);assertEquals(MovingObjectPosition.MovingObjectType.BLOCK,hit.typeOfHit);
            assertTrue(NScaffoldRaycast.interiorHit(plan,hit,margin));assertEquals(eyes.zCoord,hit.hitVec.zCoord,.001);
        }
    }
    @Test public void traceRejectsWrongFaceMissObstructionStateAndOccupiedDestination()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        NScaffoldRaycast.Plan plan=centerPlan(w,p,new Vec3(0,63.5,.5));assertNotNull(plan);
        assertNull(trace(w,p,plan,new Vec3(-.5,64,.5),4.5));
        assertNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),0,0,4.5,plan,w.current::get));
        w.put(DESTINATION.up(),new PartialOccluder());assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));w.cells.remove(DESTINATION.up());
        w.put(SUPPORT,Blocks.dirt);assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));w.put(SUPPORT,Blocks.stone);
        w.put(DESTINATION,Blocks.stone);assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));
    }
    @Test public void callbacksCannotReturnAPreviousSceneOrSupportResult()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        NScaffoldRaycast.Plan plan=centerPlan(w,p,new Vec3(0,63.5,.5));assertNotNull(plan);
        w.afterRay=()->w.current.set(false);assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));
        w.current.set(true);w.afterRay=()->w.put(SUPPORT,Blocks.dirt);assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));
        w.put(SUPPORT,Blocks.stone);w.afterRay=()->w.put(DESTINATION,Blocks.stone);assertNull(trace(w,p,plan,new Vec3(0,63.5,.5),4.5));
        w.cells.remove(DESTINATION);w.afterRay=()->w.put(DESTINATION.up(),new PartialOccluder());
        assertNull("new intervening obstruction invalidates the previous hit",trace(w,p,plan,new Vec3(0,63.5,.5),4.5));
        w.cells.remove(DESTINATION.up());w.afterRay=()->{};w.invalidateOnRead=true;assertNull(centerPlan(w,p,new Vec3(0,63.5,.5)));
    }
    @Test public void forecastingVisibleSideDoesNotAuthorizeTheCurrentHiddenSide()throws Exception {
        Scene w=scene();BlockPos support=new BlockPos(0,63,0),future=new BlockPos(1,63,0);
        w.put(support,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);Vec3 predicted=new Vec3(1.2,65.62,.5);
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,predicted,future,support,90,80,4.5,w.current::get);
        assertNotNull(plan);assertEquals(future,plan.destination);assertEquals(EnumFacing.EAST,plan.data.facing);
        assertNull("forecast geometry grants no click from current eyes",NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));
        assertNotNull(NScaffoldRaycast.trace(w,predicted,plan.yaw,plan.pitch,4.5,plan,w.current::get));assertEquals(.5,p.posX,0);
    }
    @Test public void deduplicatedRecoveryIsNearAndTheEmptyWorldHasABoundedReadCount()throws Exception {
        Scene w=scene();EntityPlayerSP p=player(w,.5,64,.5);w.put(SUPPORT.west(),Blocks.stone);
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,DESTINATION,null,90,70,4.5,w.current::get);
        assertNotNull(plan);assertEquals(SUPPORT,plan.destination);assertEquals(SUPPORT.west(),plan.data.blockPos);
        w.cells.clear();w.reads=w.rays=0;assertNull(NScaffoldRaycast.find(p,DESTINATION,null,0,0,4.5,w.current::get));
        assertEquals(1,w.rays);assertTrue("deduplication replaces 97,655 original reads: "+w.reads,w.reads<1600);
    }
    @Test public void manyHiddenFacesCannotExceedTheRayBudget()throws Exception {
        Scene w=scene();EntityPlayerSP p=player(w,.5,65,.5);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){w.put(new BlockPos(x,62,z),Blocks.stone);w.put(new BlockPos(x,64,z),Blocks.stone);}
        assertNull(NScaffoldRaycast.find(p,DESTINATION,null,0,0,4.5,w.current::get));assertEquals(NScaffoldRaycast.MAX_RAYS,w.rays);
    }
    @Test public void worldIdentityAndChangedSlabBoundsInvalidateAnOldPlan()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone_slab);EntityPlayerSP p=player(w,.5,64,.5);
        NScaffoldRaycast.Plan plan=centerPlan(w,p,new Vec3(0,63.25,.5));assertNotNull(plan);
        Scene another=scene();another.cells.putAll(w.cells);
        assertNull(NScaffoldRaycast.trace(another,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,another.current::get));assertEquals(0,another.rays);
        w.cells.put(SUPPORT,Blocks.stone_slab.getDefaultState().withProperty(BlockSlab.HALF,BlockSlab.EnumBlockHalf.TOP));
        assertNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));
    }
    @Test public void nonfiniteInputsAndAnAlreadyRevokedGuardDoNoWork()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        assertNull(NScaffoldRaycast.find(p,DESTINATION,null,Float.NaN,0,4.5,w.current::get));
        assertNull(NScaffoldRaycast.find(p,DESTINATION,null,0,0,Double.POSITIVE_INFINITY,w.current::get));
        assertNull(NScaffoldRaycast.find(p,new Vec3(Double.NaN,0,0),DESTINATION,null,0,0,4.5,w.current::get));
        w.current.set(false);assertNull(NScaffoldRaycast.find(p,DESTINATION,null,0,0,4.5,w.current::get));assertEquals(0,w.rays);assertEquals(0,w.reads);
    }
    @Test public void strictPlanningMovesAnExistingEdgeRayIntoTheRealFaceInterior()throws Exception {
        Scene w=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        float[] edge=aim(p.getPositionEyes(1),new Vec3(0,63.001,.5));
        NScaffoldRaycast.Plan loose=NScaffoldRaycast.find(p,p.getPositionEyes(1),DESTINATION,null,edge[0],edge[1],4.5,w.current::get,0);
        assertNotNull(loose);MovingObjectPosition oldHit=trace(w,p,loose,new Vec3(0,63.001,.5),4.5);
        assertNotNull(oldHit);assertFalse(NScaffoldRaycast.interiorHit(loose,oldHit,.01));
        NScaffoldRaycast.Plan strict=NScaffoldRaycast.find(p,p.getPositionEyes(1),DESTINATION,null,edge[0],edge[1],4.5,w.current::get,.01);
        assertNotNull(strict);MovingObjectPosition hit=NScaffoldRaycast.trace(w,p.getPositionEyes(1),strict.yaw,strict.pitch,4.5,strict,w.current::get);
        assertNotNull(hit);assertTrue(NScaffoldRaycast.interiorHit(strict,hit,.01));assertTrue(strict.hitVec.yCoord>loose.hitVec.yCoord+.01);
    }
    @Test public void unknownIntermediateRayCellsRejectEvenWithLoadedEndpoints()throws Exception {
        Scene w=scene();BlockPos support=new BlockPos(15,63,15),dest=support.east();w.put(support,Blocks.stone);
        EntityPlayerSP p=player(w,17.5,64,17.5);Vec3 point=new Vec3(16,63.5,15.5);float[] r=aim(p.getPositionEyes(1),point);
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,dest,null,r[0],r[1],4.5,w.current::get);assertNotNull(plan);
        w.unloaded.add(new BlockPos(16,64,16));int rays=w.rays;
        assertTrue(w.isBlockLoaded(new BlockPos(p.getPositionEyes(1))));assertTrue(w.isBlockLoaded(support));
        assertNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));assertEquals(rays,w.rays);
    }
    @Test public void eyesAboveBuildHeightStillRayToALoadedValidSupport()throws Exception {
        Scene w=scene();BlockPos dest=new BlockPos(0,255,0),support=dest.west();w.put(support,Blocks.stone);
        EntityPlayerSP p=player(w,.5,256,.5);float[] r=aim(p.getPositionEyes(1),new Vec3(0,255.5,.5));
        assertFalse(w.isBlockLoaded(new BlockPos(p.getPositionEyes(1))));
        NScaffoldRaycast.Plan plan=NScaffoldRaycast.find(p,dest,null,r[0],r[1],4.5,w.current::get);
        assertNotNull(plan);assertEquals(dest,plan.destination);assertEquals(support,plan.data.blockPos);
        assertNotNull(NScaffoldRaycast.trace(w,p.getPositionEyes(1),plan.yaw,plan.pitch,4.5,plan,w.current::get));
    }
    @Test public void negativeDiagonalChecksVanillasSideChunkBeforeReadingIt()throws Exception {
        Scene w=scene();BlockPos support=new BlockPos(15,64,15),dest=support.south();w.put(support,Blocks.stone);
        Vec3 direction=net.minecraft.entity.Entity.getVectorForRotation(0,135);
        Vec3 eyes=new Vec3(16-direction.xCoord,64.5,16-direction.zCoord);
        Vec3 end=eyes.addVector(direction.xCoord*4.5,0,direction.zCoord*4.5);
        w.unloadedChunks.add(new BlockPos(1,0,0));
        assertTrue(w.isBlockLoaded(new BlockPos(eyes)));assertTrue(w.isBlockLoaded(support));
        w.rayTraceBlocks(eyes,end,false,false,false);
        assertTrue("real World DDA visits the negative corner's side chunk",w.unloadedReads>0);
        w.unloadedReads=0;
        EntityPlayerSP p=player(w,eyes.xCoord,64,eyes.zCoord);
        NScaffoldRaycast.find(p,eyes,dest,null,135,0,4.5,w.current::get);
        assertEquals("all actual World-ray reads were prechecked",0,w.unloadedReads);
    }
    @Test public void aGuardThatChangesWorldCannotAuthorizeOldWorldReads()throws Exception {
        Scene w=scene(),next=scene();w.put(SUPPORT,Blocks.stone);EntityPlayerSP p=player(w,.5,64,.5);
        assertNull(NScaffoldRaycast.find(p,DESTINATION,null,90,75,4.5,()->{p.worldObj=next;return true;}));
        assertEquals(0,w.reads);assertEquals(0,w.rays);
    }
    @Test public void realSlabAndCarpetFacesKeepTheirOwnHeightAndStrictMargin()throws Exception {
        Scene w=scene();EntityPlayerSP p=player(w,.5,64,.5);
        for(Block block:new Block[]{Blocks.stone_slab,Blocks.carpet}){
            w.put(SUPPORT,block);double height=block==Blocks.carpet?.0625:.5;
            NScaffoldRaycast.Plan plan=centerPlan(w,p,new Vec3(0,63+height/2,.5));assertNotNull(plan);assertEquals(63+height,plan.actualBounds.maxY,0);
            MovingObjectPosition middle=trace(w,p,plan,new Vec3(0,63+height/2,.5),4.5);assertNotNull(middle);
            assertTrue(NScaffoldRaycast.interiorHit(plan,middle,.01));assertTrue(NScaffoldRaycast.interiorHit(plan,middle,10));
            MovingObjectPosition edge=trace(w,p,plan,new Vec3(0,63+.001,.5),4.5);assertNotNull(edge);assertFalse(NScaffoldRaycast.interiorHit(plan,edge,.01));
            MovingObjectPosition another=trace(w,p,plan,new Vec3(0,63+height/2,.25),4.5);assertNotNull(another);
            assertTrue("Strict accepts another interior hit on the same face",NScaffoldRaycast.interiorHit(plan,another,.01));
        }
    }
}
