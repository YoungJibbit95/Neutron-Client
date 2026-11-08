package me.youngjibbit.core.world;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real placement-service geometry with synthetic world storage; no game session. */
public class AdaptiveBridgeAcquisitionTest {
    private static class Access extends ScaffoldPlacementTest.WorldAccess {
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
        f.sequence=1;f.feetX=access.feet.xCoord;f.feetY=access.feet.yCoord;f.feetZ=access.feet.zCoord;
        Vec3 eyes=access.eyes();f.eyeX=eyes.xCoord;f.eyeY=eyes.yCoord;f.eyeZ=eyes.zCoord;
        f.reach=access.reach;f.authorized=true;
        return f;
    }
    private static Access east() {
        Access access=new Access();access.feet=new Vec3(.86,64,.5);
        access.block(new BlockPos(0,63,0));access.block(new BlockPos(1,63,-1));
        return access;
    }
    private static AdaptiveBridgePlanner.Frame eastFrame(Access access) {
        AdaptiveBridgePlanner.Frame f=frame(access);
        f.yaw=-90;f.forward=1;f.velocityX=.2;return f;
    }

    @Test public void acquiresAVisibleFutureFaceWhileCurrentFootCellIsOccupied() {
        Access access=east();
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        assertNull(service.findTarget(new PlacementContext(false,false,63)));
        assertEquals(ScaffoldPlacementService.Rejection.OCCUPIED,service.getLastRejection());
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=eastFrame(access);
        PlacementTarget early=adapter.findEarlyTarget(service,f,63,null);
        assertNotNull("occupied foot cell must not stop early acquisition",early);
        assertEquals(new BlockPos(1,63,0),early.getDestination());
        assertEquals(new BlockPos(1,63,-1),early.getBlockPos());
        assertTrue(service.isValid(early));
        assertSame(early,adapter.findEarlyTarget(service,f,63,early));
        AdaptiveBridgeScaffoldAdapter.Prepared pre=adapter.prepare(service,early,f);
        assertNotNull("enable preview cannot consume the real PRE",pre);
        assertSame(early,pre.target);
        assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
        assertEquals(64,access.inventory[3].stackSize);
    }

    @Test public void occludedFutureFaceCannotBorrowPredictedEyesForCurrentRaycast() {
        Access access=east();access.cells.remove(new BlockPos(1,63,-1));
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        assertNull(new AdaptiveBridgeScaffoldAdapter().findEarlyTarget(service,eastFrame(access),63,null));
        assertEquals(0,access.clicks);
    }

    @Test public void directionChangeDropsAValidPointOutsideTheNewCorridor() {
        Access access=east();
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        AdaptiveBridgePlanner.Frame f=eastFrame(access);
        PlacementTarget early=adapter.findEarlyTarget(service,f,63,null);
        assertNotNull(early);f.forward=-1;f.velocityX=0;
        assertTrue(service.isValid(early));
        assertNull(adapter.findEarlyTarget(service,f,63,early));
    }

    @Test public void cardinalAndDiagonalPredictionHandlesNegativeCoordinates() {
        double[][] positions={{.86,.5},{-.86,.5},{.5,.86},{.5,-.86},{.86,.86}};
        int[][] supports={{1,-1},{-2,-1},{-1,1},{-1,-2},{1,2}};
        int[][] destinations={{1,0},{-2,0},{0,1},{0,-2},{1,1}};
        double[] yaw={-90,90,0,180,0};
        for(int direction=0;direction<positions.length;direction++) {
            Access access=new Access();access.feet=new Vec3(positions[direction][0],64,positions[direction][1]);
            access.block(new BlockPos(access.feet.xCoord,63,access.feet.zCoord));
            access.block(new BlockPos(supports[direction][0],63,supports[direction][1]));
            AdaptiveBridgePlanner.Frame f=frame(access);f.yaw=yaw[direction];f.forward=1;
            f.velocityX=direction==0?.2:direction==1?-.2:direction==4?.16:0;
            f.velocityZ=direction==2?.2:direction==3?-.2:direction==4?.16:0;
            if(direction==4)f.strafe=1;
            ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
            PlacementTarget target=new AdaptiveBridgeScaffoldAdapter().findEarlyTarget(service,f,63,null);
            assertNotNull("heading "+direction,target);
            assertEquals(new BlockPos(destinations[direction][0],63,destinations[direction][1]),target.getDestination());
            assertTrue(service.isValid(target));assertEquals(0,access.clicks);
        }
    }

    @Test public void missingBlocksCollisionAndForeignSceneFailClosed() {
        for(int scenario=0;scenario<4;scenario++) {
            Access access=east();AdaptiveBridgePlanner.Frame f=eastFrame(access);
            if(scenario==0)access.inventory[3]=null;
            if(scenario==1)access.collision=true;
            if(scenario==2)access.world=new Object();
            if(scenario==3){access.reach=1;f.reach=1;}
            ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
            assertNull("scenario "+scenario,new AdaptiveBridgeScaffoldAdapter().findEarlyTarget(service,f,63,null));
            assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
        }
    }

    @Test public void searchIsBoundedAndForeignThreadCannotQueryTheWorld() throws Exception {
        Access access=new Access();access.feet=new Vec3(.86,64,.86);
        AdaptiveBridgePlanner.Frame f=frame(access);
        f.forward=f.strafe=1;f.velocityX=f.velocityZ=.3;
        ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
        AdaptiveBridgeScaffoldAdapter adapter=new AdaptiveBridgeScaffoldAdapter();
        assertNull(adapter.findEarlyTarget(service,f,63,null));
        assertTrue(access.faceQueries>0 && access.faceQueries<=18);
        int before=access.faceQueries;
        AtomicReference<PlacementTarget> result=new AtomicReference<>();
        Thread worker=new Thread(()->result.set(adapter.findEarlyTarget(service,f,63,null)),"bridge-preview-foreign");
        worker.start();worker.join();
        assertNull(result.get());assertEquals(before,access.faceQueries);
        f.predictionTicks=Double.NaN;
        assertNull(adapter.findEarlyTarget(service,f,63,null));assertEquals(before,access.faceQueries);
    }
}
