package me.youngjibbit.core.world;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.entity.Entity;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real ray/face checks and synthetic block storage; no placement rights are fabricated. */
public class AdaptiveBridgeRouteTest {
    private static final class Access extends ScaffoldPlacementTest.WorldAccess {
        int faceQueries;
        Access() { cells.clear(); }
        @Override public AxisAlignedBB supportBounds(BlockPos pos) {
            faceQueries++;
            return super.supportBounds(pos);
        }
    }

    private static AdaptiveBridgePlanner.Frame frame(Access access) {
        AdaptiveBridgePlanner.Frame f=new AdaptiveBridgePlanner.Frame();
        f.scope=new AdaptiveBridgePlanner.Scope(new Object(),access.world,access.player,new Object(),0,1);
        f.sequence=1;f.authorized=true;f.grounded=true;
        f.feetX=access.feet.xCoord;f.feetY=access.feet.yCoord;f.feetZ=access.feet.zCoord;
        Vec3 eyes=access.eyes();f.eyeX=eyes.xCoord;f.eyeY=eyes.yCoord;f.eyeZ=eyes.zCoord;
        f.reach=access.reach;
        return f;
    }

    @Test public void legacyRadiusCandidateCannotEnterOrRemainInTheBridgeRoute() {
        Access access=new Access();access.block(new BlockPos(-2,63,0));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        PlacementTarget remote=service.planFace(new BlockPos(-2,63,0),EnumFacing.EAST,null);
        assertNotNull(remote);
        assertTrue("control: legacy retention admits the unrelated adjacent row",
                service.stillNeeded(remote,new PlacementContext(false,false,63)));
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);
        assertNull(adapter.findEarlyTarget(service,f,63,remote));
        assertFalse(adapter.stillNeeded(service,remote,f,63));
        assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
    }

    @Test public void laneRetainsOneRowWhenThePhysicalCenterCrossesACellBoundary() {
        Access access=new Access();access.feet=new Vec3(1.15,64,1.02);
        access.block(new BlockPos(0,63,0));access.block(new BlockPos(0,63,1));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);
        f.yaw=-90;f.forward=1;f.velocityX=.2;f.velocityZ=.015;
        f.hasLane=true;f.laneX=f.feetX;f.laneZ=.92;f.laneDirectionX=1;
        PlacementTarget target=adapter.findEarlyTarget(service,f,63,null);
        assertNotNull(target);assertEquals(new BlockPos(1,63,0),target.getDestination());
        assertTrue(adapter.stillNeeded(service,target,f,63));
        for(double z:new double[]{.98,1.01,.99,1.025}) {
            access.feet=new Vec3(1.15,64,z);f.feetZ=f.eyeZ=z;
            assertSame("cell-boundary noise must not switch the held face",target,
                    adapter.findEarlyTarget(service,f,63,target));
        }
        access.block(new BlockPos(1,63,1));
        assertFalse("an occupied physical foot cell must not grow a parallel row",
                adapter.stillNeeded(service,target,f,63));
    }

    @Test public void displacedPlayerCannotBorrowARouteOutsideTheirFootprint() {
        Access access=new Access();access.feet=new Vec3(1.15,64,1.5);
        access.block(new BlockPos(0,63,0));access.block(new BlockPos(0,63,1));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgePlanner.Frame f=frame(access);
        f.hasLane=true;f.laneX=f.feetX;f.laneZ=.92;f.laneDirectionX=1;
        PlacementTarget target=new AdaptiveBridgeScaffoldAdapter().findEarlyTarget(service,f,63,null);
        assertNotNull(target);assertEquals(new BlockPos(1,63,1),target.getDestination());
    }

    @Test public void aFutureVisibleFaceRemainsPreviewOnlyUntilTheFootCellNeedsIt() {
        Access access=new Access();access.feet=new Vec3(.86,64,.5);
        access.block(new BlockPos(0,63,0));access.block(new BlockPos(1,63,-1));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);f.yaw=-90;f.forward=1;f.velocityX=.2;
        PlacementTarget target=adapter.findEarlyTarget(service,f,63,null);
        assertNotNull(target);assertEquals(new BlockPos(1,63,0),target.getDestination());
        assertFalse(adapter.stillNeeded(service,target,f,63));
        access.feet=new Vec3(1.1,64,.5);f.feetX=f.eyeX=1.1;
        assertTrue(adapter.stillNeeded(service,target,f,63));
        assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
    }

    @Test public void onlyAMeasuredFallMayAcquireAnOverlappedSideCatch() {
        Access access=new Access();access.feet=new Vec3(1.15,63.9,1.02);
        access.block(new BlockPos(0,62,0));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);
        assertNull(adapter.findEarlyTarget(service,f,62,null));
        f.grounded=false;f.velocityY=-.2;
        PlacementTarget catchTarget=adapter.findEarlyTarget(service,f,62,null);
        assertNotNull(catchTarget);
        assertNotEquals(new BlockPos(1,62,1),catchTarget.getDestination());
        assertTrue(service.isValid(catchTarget));
        assertTrue(adapter.stillNeeded(service,catchTarget,f,62));
        f.grounded=true;
        assertNull(adapter.findEarlyTarget(service,f,62,catchTarget));
        assertFalse(adapter.stillNeeded(service,catchTarget,f,62));
    }

    @Test public void recoveryMayInspectOneLowerLayerButRespectsFixedHeightAndBounds() {
        Access access=new Access();access.feet=new Vec3(.5,63.1,.5);
        access.block(new BlockPos(-1,61,0));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);f.grounded=false;f.velocityY=-.3;
        PlacementTarget lower=adapter.findEarlyTarget(service,f,62,null);
        assertNotNull(lower);assertEquals(new BlockPos(0,61,0),lower.getDestination());
        assertTrue(adapter.stillNeeded(service,lower,f,62));
        f.allowLowerRecovery=false;
        assertNull(adapter.findEarlyTarget(service,f,62,lower));
        assertFalse(adapter.stillNeeded(service,lower,f,62));
        f.allowLowerRecovery=true;access.cells.clear();access.block(new BlockPos(-1,60,0));
        assertNull("recovery cannot chase support two layers lower",
                adapter.findEarlyTarget(service,f,62,null));
        access.cells.clear();access.faceQueries=0;f.velocityX=f.velocityZ=.4;f.predictionTicks=3;
        assertNull(adapter.findEarlyTarget(service,f,62,null));
        assertTrue(access.faceQueries>0);
        assertTrue("fall search remains bounded",access.faceQueries<=6*AdaptiveBridgeScaffoldAdapter.MAX_RECOVERY_DESTINATIONS);
    }

    @Test public void exactDiagonalCornerPlacesOneConnectorThenReturnsToItsCenterCell() {
        double[] yaws={-45,45,135,-135};
        for(int origin:new int[]{0,-9})for(double yaw:yaws) {
            int dx=-Math.sin(Math.toRadians(yaw))>0?1:-1;
            int dz=Math.cos(Math.toRadians(yaw))>0?1:-1;
            Access access=new Access();
            BlockPos previous=new BlockPos(origin,63,origin);
            BlockPos destination=previous.add(dx,0,dz);
            access.block(previous);
            access.feet=new Vec3(origin+(dx>0?1.05:-.05),64,origin+(dz>0?1.05:-.05));
            ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
            AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
            AdaptiveBridgePlanner.Frame f=frame(access);
            f.yaw=yaw;f.forward=1;f.velocityX=dx*.14;f.velocityZ=dz*.14;
            PlacementTarget connector=adapter.findEarlyTarget(service,f,63,null);
            assertNotNull("diagonal "+yaw+" at "+origin,connector);
            assertEquals(previous,connector.getBlockPos());
            assertEquals("equal axes have a deterministic connector",previous.add(dx,0,0),connector.getDestination());
            assertTrue(adapter.stillNeeded(service,connector,f,63));
            int queries=access.faceQueries;
            assertTrue(adapter.isDiagonalConnector(f,connector,63));
            assertEquals("urgency classification cannot search for another target",queries,access.faceQueries);
            assertFalse(adapter.isDiagonalConnector(f,connector,62));
            assertSame(connector,adapter.findEarlyTarget(service,f,63,connector));
            AdaptiveBridgeScaffoldAdapter.Prepared prepared=adapter.prepare(service,connector,f);
            assertNotNull(prepared);assertSame(connector,prepared.target);
            // A real accepted placement would now supply this exact connector.
            access.block(connector.getDestination());
            f.sequence++;
            PlacementTarget next=adapter.findEarlyTarget(service,f,63,connector);
            assertNotNull(next);assertEquals(destination,next.getDestination());
            assertFalse(adapter.isDiagonalConnector(f,next,63));
            assertTrue(adapter.stillNeeded(service,next,f,63));
            access.block(destination);
            assertFalse(adapter.stillNeeded(service,next,f,63));
            assertNull("completed diagonal must not fill the other connector or a parallel row",
                    adapter.findEarlyTarget(service,f,63,null));
            assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
        }
    }

    @Test public void diagonalConnectorsRequireAMissingCurrentCellAndActualBodyOverlap() {
        Access access=new Access();access.block(new BlockPos(0,63,0));
        access.feet=new Vec3(.95,64,.95);
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=frame(access);f.yaw=-45;f.forward=1;
        assertNull("occupied current support does not trigger a connector",
                adapter.findEarlyTarget(service,f,63,null));
        access.feet=new Vec3(1.6,64,1.6);f=frame(access);f.yaw=-45;f.forward=1;
        assertNull("a connector behind the physical footprint cannot rescue this path",
                adapter.findEarlyTarget(service,f,63,null));
        access.cells.clear();access.faceQueries=0;
        access.feet=new Vec3(1.05,64,1.05);f=frame(access);f.yaw=-45;f.forward=1;
        assertNull(adapter.findEarlyTarget(service,f,63,null));
        assertTrue("normal route plus at most two fixed corner faces",access.faceQueries<=20);
    }

    @Test public void usefulRayKeepsExactIntentAndAnUnrelatedRayCannotReplaceIt() {
        Access access=new Access();access.block(new BlockPos(-1,63,0));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        PlacementTarget target=service.planFace(new BlockPos(-1,63,0),EnumFacing.EAST,null);
        assertNotNull(target);
        Vec3 offCenter=new Vec3(0,63.8,.55);
        ScaffoldRotationPlan angles=ScaffoldRotationPlan.build("Back",access.eyes(),offCenter);
        PlacementTarget held=service.retainBridgeHit(target,angles.getYaw(),angles.getPitch());
        assertNotNull(held);
        assertSame(service.rotationIdentity(target),service.rotationIdentity(held));
        assertEquals(target.getBlockPos(),held.getBlockPos());
        assertEquals(target.getFacing(),held.getFacing());
        assertEquals(target.getDestination(),held.getDestination());
        // Minecraft uses table-backed float trigonometry; atan2 does not round-trip
        // the ideal point exactly. The retained point must be the native ray hit.
        Vec3 direction=Entity.getVectorForRotation(angles.getPitch(),angles.getYaw());
        Vec3 eyes=access.eyes();
        double distance=(target.getHitVec().xCoord-eyes.xCoord)/direction.xCoord;
        Vec3 expected=eyes.addVector(direction.xCoord*distance,direction.yCoord*distance,
                direction.zCoord*distance);
        assertTrue(expected.squareDistanceTo(held.getHitVec())<1e-20);
        assertTrue("off-center ray must not fall back to the old anchor",
                target.getHitVec().squareDistanceTo(held.getHitVec())>.01);
        assertNull(service.retainBridgeHit(target,-90,45));
        assertNull(service.retainBridgeHit(target,Float.NaN,45));
        assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
    }
}
