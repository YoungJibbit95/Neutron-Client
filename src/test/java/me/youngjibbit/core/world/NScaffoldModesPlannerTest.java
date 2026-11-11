package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.junit.Test;

import static me.youngjibbit.core.world.NScaffoldPlanner.RotationStyle.*;
import static org.junit.Assert.*;

/** Real placement-service visibility/reach checks for the additional visible poses. */
public class NScaffoldModesPlannerTest {
    private static NScaffoldPlanner.Sample sample(ScaffoldPlacementTest.Fixture f,float yaw,float pitch,
            float forward,float strafe,NScaffoldPlanner.RotationStyle style,boolean fixed,float preferred) {
        return new NScaffoldPlanner.Sample(f.world.feet,f.world.eyes(),yaw,pitch,forward,strafe,
                63,.65,style,fixed,preferred);
    }

    private static ScaffoldPlacementTest.Fixture northEdge(double x,double z) {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        f.world.cells.clear();f.world.block(new BlockPos(0,63,0));f.world.feet=new Vec3(x,64,z);
        return f;
    }

    @Test public void centerUsesTheActualFaceCenterEvenWhenAnEdgeRayIsAlreadyValid() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        Vec3 edge=new Vec3(0,63.95,.5);
        ScaffoldRotationPlan edgePose=ScaffoldRotationPlan.build("Back",f.world.eyes(),edge);
        assertNotNull(f.service.planBridgeRay(edgePose.getYaw(),edgePose.getPitch()));
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision centered=planner.plan(sample(f,edgePose.getYaw(),edgePose.getPitch(),0,0,CENTER,false,75.7f),()->true);
        assertNotNull(centered);assertEquals(EnumFacing.EAST,centered.target.getFacing());
        assertEquals(0,centered.target.getHitVec().xCoord,1.0e-9);
        assertEquals(63.5,centered.target.getHitVec().yCoord,1.0e-9);
        assertEquals(.5,centered.target.getHitVec().zCoord,1.0e-9);
        assertTrue(Math.abs(centered.pitch-edgePose.getPitch())>1);
        assertTrue(f.service.isValid(centered.target));assertEquals(0,f.world.clicks);
    }

    @Test public void centerKeepsItsWorldPointAcrossAChangedEyePositionWithoutAcquiringAnotherFace() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision first=planner.plan(sample(f,0,0,0,0,CENTER,false,75.7f),()->true);
        assertNotNull(first);
        f.world.feet=f.world.feet.addVector(.15,0,.1);
        NScaffoldPlanner.Decision moved=planner.plan(sample(f,first.yaw,first.pitch,0,0,CENTER,false,75.7f),()->true);
        assertNotNull(moved);assertTrue(moved.retained);assertEquals(0,planner.getLastFaceChecks());
        assertEquals(first.target.getBlockPos(),moved.target.getBlockPos());
        assertEquals(first.target.getFacing(),moved.target.getFacing());
        assertEquals(0,first.target.getHitVec().squareDistanceTo(moved.target.getHitVec()),1.0e-12);
        assertTrue(f.service.isValid(moved.target));
    }

    @Test public void switchingFromAnEdgeStyleToCenterCannotRetainTheOldOffCenterPoint() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision edge=planner.plan(sample(f,90,89,0,0,STANDARD,false,75.7f),()->true);
        assertNotNull(edge);
        NScaffoldPlanner.Decision center=planner.plan(sample(f,edge.yaw,edge.pitch,0,0,CENTER,false,75.7f),()->true);
        assertNotNull(center);assertFalse(center.retained);
        assertEquals(63.5,center.target.getHitVec().yCoord,1.0e-9);
    }

    @Test public void breezilyUsesAnActuallyVisibleStraightFixedPose() {
        ScaffoldPlacementTest.Fixture f=northEdge(.5,-.3);
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision decision=planner.plan(sample(f,0,60,-1,0,BREEZILY,false,75.7f),()->true);
        assertNotNull(decision);assertEquals(0,decision.yaw,0);assertEquals(80,decision.pitch,0);
        assertEquals(new BlockPos(0,63,-1),decision.target.getDestination());
        assertEquals(EnumFacing.NORTH,decision.target.getFacing());
        assertTrue(f.service.isValid(decision.target));assertEquals(0,planner.getLastFaceChecks());
        assertEquals(0,f.world.clicks);assertTrue(f.world.slots.isEmpty());
    }

    @Test public void breezilyDiagonalHeadingDoesNotFeedItsOwnVisibleTurnBackIntoTheNextPlan() {
        ScaffoldPlacementTest.Fixture f=northEdge(.8,-.4);
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision first=planner.plan(sample(f,0,60,-1,1,BREEZILY,false,75.7f),()->true);
        assertNotNull(first);assertEquals(45,first.yaw,0);assertEquals(75.7f,first.pitch,0);
        for(float strafe:new float[]{1,1,-1,1}) {
            NScaffoldPlanner.Decision next=planner.plan(sample(f,first.yaw,first.pitch,-1,strafe,BREEZILY,false,75.7f),()->true);
            assertNotNull(next);assertEquals(45,next.yaw,0);assertEquals(75.7f,next.pitch,0);
            assertTrue(f.service.isValid(next.target));
        }
    }

    @Test public void staticifyRequiresTheFixedPoseItselfInsteadOfFallingBackToADifferentAngle() {
        ScaffoldPlacementTest.Fixture f=northEdge(.5,-.05);
        NScaffoldPlanner normal=new NScaffoldPlanner(f.service);
        assertNotNull(normal.plan(sample(f,0,60,-1,0,STANDARD,false,75.7f),()->true));
        NScaffoldPlanner fixed=new NScaffoldPlanner(f.service);
        assertNull(fixed.plan(sample(f,0,60,-1,0,REVERSE,true,75.7f),()->true));
        assertEquals(0,fixed.getLastFaceChecks());assertEquals(0,f.world.clicks);
    }

    @Test public void staticReverseAndGodbridgeKeepTheirSpecifiedReachablePitch() {
        ScaffoldPlacementTest.Fixture f=northEdge(.5,-.3);
        NScaffoldPlanner reverse=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision fixed=reverse.plan(sample(f,0,60,-1,0,REVERSE,true,75.7f),()->true);
        assertNotNull(fixed);assertEquals(0,fixed.yaw,0);assertEquals(80,fixed.pitch,0);
        NScaffoldPlanner godbridge=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision configured=godbridge.plan(sample(f,0,70,-1,0,GODBRIDGE,true,80),()->true);
        assertNotNull(configured);assertEquals(0,configured.yaw,0);assertEquals(80,configured.pitch,0);
        godbridge.reset();
        assertNull(godbridge.plan(sample(f,0,70,-1,0,GODBRIDGE,true,75.7f),()->true));
    }

    @Test public void idleFixedModesCanOnlyUseTheRealDownwardRayOnTheirRoute() {
        for(NScaffoldPlanner.RotationStyle style:new NScaffoldPlanner.RotationStyle[]{BREEZILY,GODBRIDGE}) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
            f.world.cells.clear();f.world.block(new BlockPos(0,62,0));
            NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
            NScaffoldPlanner.Decision down=planner.plan(sample(f,0,70,0,0,style,true,75.7f),()->true);
            assertNotNull(down);assertEquals(90,down.pitch,0);
            assertEquals(new BlockPos(0,63,0),down.target.getDestination());
            assertTrue(f.service.isValid(down.target));
        }
    }

    @Test public void addedPosesStillRejectOutOfReachAndInvalidatedWorlds() {
        for(NScaffoldPlanner.RotationStyle style:new NScaffoldPlanner.RotationStyle[]{CENTER,REVERSE,BREEZILY}) {
            ScaffoldPlacementTest.Fixture f=northEdge(.5,-.3);
            f.world.reach=.1;
            NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
            assertNull(planner.plan(sample(f,0,60,-1,0,style,true,75.7f),()->true));
            f.world.reach=4.5;f.world.allowed=false;
            assertNull(planner.plan(sample(f,0,60,-1,0,style,true,75.7f),()->true));
            assertEquals(0,f.world.clicks);assertTrue(f.world.slots.isEmpty());
        }
    }

    @Test public void resettingInsideAFixedRayReadCannotPublishAStaleModeDecision() {
        ScaffoldPlacementTest.Fixture f=northEdge(.5,-.3);
        NScaffoldPlanner[] planner=new NScaffoldPlanner[1];
        planner[0]=new NScaffoldPlanner(new NScaffoldPlanner.Access() {
            public boolean replaceable(BlockPos p){return f.service.isReplaceableDestination(p);}
            public boolean valid(PlacementTarget p){return f.service.isValid(p);}
            public PlacementTarget ray(float yaw,float pitch){
                PlacementTarget result=f.service.planBridgeRay(yaw,pitch);planner[0].reset();return result;
            }
            public PlacementTarget retain(PlacementTarget p,float yaw,float pitch){return f.service.retainBridgeHit(p,yaw,pitch);}
            public PlacementTarget face(BlockPos p,EnumFacing side,Vec3 eyes,float yaw,float pitch){return f.service.planBridgeFace(p,side,eyes,yaw,pitch);}
        });
        assertNull(planner[0].plan(sample(f,0,60,-1,0,BREEZILY,false,75.7f),()->true));
        assertEquals(0,f.world.clicks);
    }

    @Test public void invalidAdditionalStyleSamplesDoNotSearch() {
        ScaffoldPlacementTest.Fixture f=northEdge(.5,-.3);
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        assertNull(planner.plan(sample(f,0,60,-1,0,null,false,75.7f),()->true));
        assertNull(planner.plan(sample(f,Float.NaN,60,-1,0,BREEZILY,false,75.7f),()->true));
        assertNull(planner.plan(sample(f,0,60,-1,Float.POSITIVE_INFINITY,REVERSE,true,75.7f),()->true));
        assertNull(planner.plan(sample(f,0,60,-1,0,GODBRIDGE,true,Float.NaN),()->true));
        assertEquals(0,planner.getLastFaceChecks());assertEquals(0,f.world.clicks);
    }
}
