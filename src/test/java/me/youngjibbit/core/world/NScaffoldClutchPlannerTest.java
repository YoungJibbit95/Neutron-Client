package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Real placement service/rays for successful plans; deliberately empty adapters prove search bounds. */
public class NScaffoldClutchPlannerTest {
    private static final NScaffoldClutchPlanner.Options PREDICTION=options(
            NScaffoldClutchPlanner.Criteria.PREDICTION,NScaffoldClutchPlanner.AimMode.CENTER,false,false,false,false,5);
    private static NScaffoldClutchPlanner.Options options(NScaffoldClutchPlanner.Criteria criteria,
            NScaffoldClutchPlanner.AimMode mode,boolean always,boolean miss,boolean predict,boolean sneak,int groundTicks) {
        return new NScaffoldClutchPlanner.Options(criteria,mode,always,miss,predict,sneak,groundTicks);
    }
    private static NScaffoldClutchPlanner.Sample sample(ScaffoldPlacementTest.Fixture f,Vec3 motion,
            float yaw,float pitch,boolean moving,boolean ground,int groundTicks,long tick) {
        return new NScaffoldClutchPlanner.Sample(f.world.feet,f.world.eyes(),motion,yaw,pitch,moving,ground,groundTicks,tick);
    }
    private static NScaffoldClutchPlanner.Sample fall(ScaffoldPlacementTest.Fixture f,long tick) {
        return sample(f,new Vec3(0,-.12,0),0,0,true,false,0,tick);
    }
    private static final class EmptyAccess implements NScaffoldClutchPlanner.Access {
        final Set<BlockPos> occupied=new HashSet<>(),destinations=new HashSet<>();
        int faces,rays,reads;Runnable onFace,onRead;
        public boolean replaceable(BlockPos p){reads++;if(onRead!=null)onRead.run();return !occupied.contains(p);}
        public boolean valid(PlacementTarget p){return p!=null;}
        public PlacementTarget ray(float yaw,float pitch){rays++;return null;}
        public PlacementTarget retain(PlacementTarget p,float yaw,float pitch){rays++;return null;}
        public PlacementTarget face(BlockPos p,EnumFacing f,Vec3 eyes,float yaw,float pitch,boolean center) {
            faces++;destinations.add(p.offset(f));if(onFace!=null)onFace.run();return null;
        }
    }

    @Test public void fallingBesideAnExposedWallProducesAReachableNormalControllerIntent() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        f.world.feet=new Vec3(.5,62.4,.5);f.world.cells.clear();f.world.block(new BlockPos(-1,61,0));
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Decision d=planner.plan(fall(f,1),PREDICTION,()->true);
        assertNotNull(d);assertEquals(new BlockPos(0,61,0),d.target.getDestination());
        assertEquals(new BlockPos(-1,61,0),d.target.getBlockPos());assertEquals(EnumFacing.EAST,d.target.getFacing());
        assertTrue(f.service.isValid(d.target));assertTrue(Float.isFinite(d.yaw));assertTrue(Float.isFinite(d.pitch));
        assertEquals(0,f.world.clicks);assertTrue(f.world.slots.isEmpty());
    }

    @Test public void normalUsesANearRayPointWhileCenterUsesTheActualFaceCenter() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldClutchPlanner.Sample s=sample(f,new Vec3(0,-.12,0),80,70,true,false,0,1);
        NScaffoldClutchPlanner.Decision center=new NScaffoldClutchPlanner(f.service).plan(s,PREDICTION,()->true);
        NScaffoldClutchPlanner.Options normal=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.NORMAL,false,false,false,false,5);
        NScaffoldClutchPlanner.Decision near=new NScaffoldClutchPlanner(f.service).plan(s,normal,()->true);
        assertNotNull(center);assertNotNull(near);
        assertEquals(0,center.target.getHitVec().xCoord,0);
        assertEquals(63.5,center.target.getHitVec().yCoord,0);
        assertEquals(.5,center.target.getHitVec().zCoord,0);
        assertTrue("near-ray hit is genuinely different",near.target.getHitVec().squareDistanceTo(center.target.getHitVec())>.01);
        assertTrue(f.service.isValid(center.target));assertTrue(f.service.isValid(near.target));
    }

    @Test public void anAcceptedNormalRayKeepsItsExactAnglesWithoutNewFaceSearch() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Options normal=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.NORMAL,false,false,false,false,5);
        NScaffoldClutchPlanner.Decision first=planner.plan(fall(f,1),normal,()->true);assertNotNull(first);
        NScaffoldClutchPlanner.Decision held=planner.plan(sample(f,new Vec3(0,-.12,0),first.yaw,first.pitch,true,false,0,2),normal,()->true);
        assertNotNull(held);assertTrue(held.retained);
        assertEquals(first.target.getBlockPos(),held.target.getBlockPos());assertEquals(first.target.getFacing(),held.target.getFacing());
        assertEquals(first.yaw,held.yaw,0);assertEquals(first.pitch,held.pitch,0);
        assertEquals(0,planner.getLastFaceChecks());assertEquals(2,planner.getLastRayChecks());
    }

    @Test public void mouseOverYieldsToAUsefulMovingRayButPredictionCanRetainIt() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Options mouse=options(NScaffoldClutchPlanner.Criteria.MOUSE_OVER,
                NScaffoldClutchPlanner.AimMode.CENTER,false,false,false,false,5);
        NScaffoldClutchPlanner.Decision first=planner.plan(fall(f,1),mouse,()->true);assertNotNull(first);
        NScaffoldClutchPlanner.Sample aligned=sample(f,new Vec3(0,-.12,0),first.yaw,first.pitch,true,false,0,2);
        assertNotNull(f.service.planBridgeRay(first.yaw,first.pitch));
        assertNull(planner.plan(aligned,mouse,()->true));assertEquals(0,planner.getLastFaceChecks());
        assertNotNull(planner.plan(aligned,PREDICTION,()->true));
    }

    @Test public void urgencySwitchesAreIndependentAndNeverChangeReachOrChooseAnotherTarget() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        for(int enabled=-1;enabled<3;enabled++) {
            NScaffoldClutchPlanner.Options o=options(NScaffoldClutchPlanner.Criteria.MOUSE_OVER,
                    NScaffoldClutchPlanner.AimMode.CENTER,enabled==0,enabled==1,enabled==2,false,5);
            NScaffoldClutchPlanner.Decision d=new NScaffoldClutchPlanner(f.service).plan(fall(f,1),o,()->true);
            assertNotNull(d);assertEquals(enabled>=0,d.urgent);assertEquals(new BlockPos(0,63,0),d.target.getDestination());
        }
        f.world.reach=.1;
        NScaffoldClutchPlanner.Options all=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.CENTER,true,true,true,false,5);
        assertNull(new NScaffoldClutchPlanner(f.service).plan(fall(f,1),all,()->true));
    }

    @Test public void onMissDoesNotBecomeAlwaysWhenTheSelectedFaceIsAlreadyVisible() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldClutchPlanner.Decision first=new NScaffoldClutchPlanner(f.service).plan(fall(f,1),PREDICTION,()->true);
        assertNotNull(first);
        NScaffoldClutchPlanner.Options miss=options(NScaffoldClutchPlanner.Criteria.MOUSE_OVER,
                NScaffoldClutchPlanner.AimMode.NORMAL,false,true,false,false,5);
        NScaffoldClutchPlanner.Decision d=new NScaffoldClutchPlanner(f.service).plan(
                sample(f,new Vec3(0,-.12,0),first.yaw,first.pitch,false,false,0,2),miss,()->true);
        assertNotNull(d);assertFalse(d.urgent);assertEquals(first.yaw,d.yaw,0);assertEquals(first.pitch,d.pitch,0);
    }

    @Test public void predictiveUrgencyDoesNotFireWhenActualMotionReturnsOverSupport() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.feet=new Vec3(.05,64,.5);
        NScaffoldClutchPlanner.Options predict=options(NScaffoldClutchPlanner.Criteria.MOUSE_OVER,
                NScaffoldClutchPlanner.AimMode.CENTER,false,false,true,false,5);
        NScaffoldClutchPlanner.Decision d=new NScaffoldClutchPlanner(f.service).plan(
                sample(f,new Vec3(-.15,0,0),0,0,true,true,2,1),predict,()->true);
        assertNotNull(d);assertFalse(d.predicted);assertFalse(d.urgent);
    }

    @Test public void predictionRetentionExpiresWithoutRepeatedSameTickRefresh() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.feet=new Vec3(.05,64,.5);
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        assertNotNull(planner.plan(sample(f,new Vec3(.1,0,0),0,0,true,true,3,10),PREDICTION,()->true));
        for(int tick=11;tick<=13;tick++)for(int repeated=0;repeated<3;repeated++) {
            NScaffoldClutchPlanner.Decision held=planner.plan(sample(f,new Vec3(-.15,0,0),0,0,true,true,3,tick),PREDICTION,()->true);
            assertNotNull(held);assertTrue(held.retained);assertFalse(held.predicted);
        }
        assertNull(planner.plan(sample(f,new Vec3(-.15,0,0),0,0,true,true,3,14),PREDICTION,()->true));
    }

    @Test public void groundedPredictionDoesNotInventAGravityHoleInAContinuousFloor() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.cells.clear();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)f.world.block(new BlockPos(x,63,z));
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        assertNull(planner.plan(sample(f,new Vec3(.2,0,.2),0,0,true,true,10,1),PREDICTION,()->true));
        assertEquals(0,planner.getLastFaceChecks());assertEquals(0,planner.getLastRayChecks());
    }

    @Test public void anAscendingJumpIsNotMistakenForAnEmergencyFall() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        assertNull(planner.plan(sample(f,new Vec3(.1,.3,0),0,0,true,false,0,1),PREDICTION,()->true));
        assertEquals(0,planner.getLastFaceChecks());assertEquals(0,planner.getLastRayChecks());
        assertNotNull(planner.plan(sample(f,new Vec3(.1,.04,0),0,0,true,false,0,2),PREDICTION,()->true));
    }

    @Test public void aFastFallStillUsesTheCurrentFaceWhenTheProjectionIsCapped() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldClutchPlanner.Options predict=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.CENTER,false,false,true,false,5);
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Decision d=planner.plan(sample(f,new Vec3(0,-3.8,0),0,0,true,false,0,1),predict,()->true);
        assertNotNull(d);assertTrue(d.urgent);assertFalse("no out-of-budget prediction claimed",d.predicted);
        assertEquals(new BlockPos(0,63,0),d.target.getDestination());assertEquals(5,planner.getLastFaceChecks());
        assertTrue(f.service.isValid(d.target));
    }

    @Test public void diagonalProjectionHasSevenConcreteCellsAndThirtyFiveFaceProbesAtMost() {
        EmptyAccess access=new EmptyAccess();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(access);
        Vec3 feet=new Vec3(.9,64,.9);
        NScaffoldClutchPlanner.Sample s=new NScaffoldClutchPlanner.Sample(feet,feet.addVector(0,1.62,0),
                new Vec3(.6,0,.6),0,0,true,true,5,1);
        assertNull(planner.plan(s,PREDICTION,()->true));
        Set<BlockPos> expected=new HashSet<>(Arrays.asList(new BlockPos(0,63,0),new BlockPos(1,63,1),
                new BlockPos(1,63,0),new BlockPos(0,63,1),new BlockPos(2,63,2),
                new BlockPos(2,63,1),new BlockPos(1,63,2)));
        assertEquals(expected,access.destinations);assertEquals(35,access.faces);
        assertEquals(NScaffoldClutchPlanner.MAX_NEW_FACE_CHECKS,planner.getLastFaceChecks());
        assertEquals(1,planner.getLastRayChecks());assertEquals(0,planner.getLastValidationChecks());
    }

    @Test public void stationaryAndNegativeCoordinatesNeverBecomeARadiusSearch() {
        EmptyAccess access=new EmptyAccess();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(access);
        Vec3 feet=new Vec3(-.1,64,-.1);
        assertNull(planner.plan(new NScaffoldClutchPlanner.Sample(feet,feet.addVector(0,1.62,0),new Vec3(0,0,0),
                0,0,false,true,0,1),PREDICTION,()->true));
        assertEquals(1,access.destinations.size());assertTrue(access.destinations.contains(new BlockPos(-1,63,-1)));
        assertEquals(5,access.faces);
    }

    @Test public void invalidMotionAnglesAndEyePositionsCannotStartWorldQueries() {
        EmptyAccess access=new EmptyAccess();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(access);
        Vec3 feet=new Vec3(.5,64,.5),eyes=feet.addVector(0,1.62,0);
        NScaffoldClutchPlanner.Sample[] invalid={
                new NScaffoldClutchPlanner.Sample(feet,eyes,new Vec3(Double.NaN,0,0),0,0,true,false,0,1),
                new NScaffoldClutchPlanner.Sample(feet,eyes,new Vec3(11,0,0),0,0,true,false,0,1),
                new NScaffoldClutchPlanner.Sample(feet,eyes,new Vec3(0,0,0),Float.NaN,0,true,false,0,1),
                new NScaffoldClutchPlanner.Sample(feet,eyes,new Vec3(0,0,0),0,91,true,false,0,1),
                new NScaffoldClutchPlanner.Sample(feet,eyes.addVector(0,2,0),new Vec3(0,0,0),0,0,true,false,0,1),
                new NScaffoldClutchPlanner.Sample(feet,eyes,new Vec3(0,0,0),0,0,true,false,0,-1)
        };
        for(NScaffoldClutchPlanner.Sample s:invalid)assertNull(planner.plan(s,PREDICTION,()->true));
        assertEquals(0,access.reads);assertEquals(0,access.faces);assertEquals(0,access.rays);
    }

    @Test public void normalReachOcclusionInventoryAndPermissionChecksRemainRequired() {
        for(int rejection=0;rejection<4;rejection++) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
            if(rejection==0)f.world.reach=.1;
            if(rejection==1)f.world.allowed=false;
            if(rejection==2)f.world.inventory[3]=null;
            if(rejection==3)f.world.block(new BlockPos(0,64,0));
            assertNull("rejection="+rejection,new NScaffoldClutchPlanner(f.service).plan(fall(f,1),PREDICTION,()->true));
            assertEquals(0,f.world.clicks);assertTrue(f.world.slots.isEmpty());
        }
    }

    @Test public void resetFromAWorldReadOrFaceQueryCannotPublishAStalePlan() {
        for(boolean atFace:new boolean[]{false,true}) {
            EmptyAccess access=new EmptyAccess();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(access);
            if(atFace)access.onFace=planner::reset;else access.onRead=planner::reset;
            Vec3 feet=new Vec3(.5,64,.5);
            assertNull(planner.plan(new NScaffoldClutchPlanner.Sample(feet,feet.addVector(0,1.62,0),
                    new Vec3(0,-.1,0),0,0,true,false,0,1),PREDICTION,()->true));
            assertEquals(atFace?1:0,access.faces);assertEquals(0,planner.getLastFaceChecks());
        }
    }

    @Test public void callerRevocationStopsSearchBeforeTheNextFace() {
        EmptyAccess access=new EmptyAccess();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(access);
        AtomicBoolean current=new AtomicBoolean(true);access.onFace=()->current.set(false);
        Vec3 feet=new Vec3(.5,64,.5);
        assertNull(planner.plan(new NScaffoldClutchPlanner.Sample(feet,feet.addVector(0,1.62,0),
                new Vec3(0,-.1,0),0,0,true,false,0,1),PREDICTION,current::get));
        assertEquals(1,access.faces);
    }

    @Test public void postPhysicsValidationNeverAcquiresAnotherTargetOrUpdatesPlanningState() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Decision d=planner.plan(fall(f,1),PREDICTION,()->true);assertNotNull(d);
        int faces=planner.getLastFaceChecks(),rays=planner.getLastRayChecks(),checks=planner.getLastValidationChecks();
        assertTrue(planner.onRoute(d.target,fall(f,1),PREDICTION,()->true));
        assertEquals(faces,planner.getLastFaceChecks());assertEquals(rays,planner.getLastRayChecks());assertEquals(checks,planner.getLastValidationChecks());
        f.world.allowed=false;assertFalse(planner.onRoute(d.target,fall(f,1),PREDICTION,()->true));f.world.allowed=true;
        f.world.feet=f.world.feet.addVector(4,0,0);
        assertFalse(planner.onRoute(d.target,fall(f,1),PREDICTION,()->true));
        assertEquals(faces,planner.getLastFaceChecks());assertEquals(0,f.world.clicks);
    }

    @Test public void resetPlacementAndOldTicksRevokePostPhysicsTargets() {
        for(int revoke=0;revoke<3;revoke++) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
            NScaffoldClutchPlanner.Decision d=planner.plan(fall(f,10),PREDICTION,()->true);assertNotNull(d);
            if(revoke==0)planner.reset();if(revoke==1)planner.placed();
            assertFalse(planner.onRoute(d.target,fall(f,revoke==2?12:10),PREDICTION,()->true));
        }
    }

    @Test public void sneakUsesGroundThresholdAndRealTickTailWithNoQuerySideEffects() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.feet=new Vec3(.05,64,.5);
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Options sneak=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.CENTER,false,false,false,true,5);
        NScaffoldClutchPlanner.Sample four=sample(f,new Vec3(.1,0,0),0,0,true,true,4,10);
        assertNotNull(planner.plan(four,sneak,()->true));assertFalse(planner.wantsSneak(four,sneak));
        NScaffoldClutchPlanner.Sample five=sample(f,new Vec3(.1,0,0),0,0,true,true,5,11);
        assertNotNull(planner.plan(five,sneak,()->true));assertTrue(planner.wantsSneak(five,sneak));
        for(int repeated=0;repeated<3;repeated++)assertNotNull(planner.plan(five,sneak,()->true));
        int faces=planner.getLastFaceChecks(),rays=planner.getLastRayChecks();
        for(int i=0;i<100;i++)assertTrue(planner.wantsSneak(five,sneak));
        assertEquals(faces,planner.getLastFaceChecks());assertEquals(rays,planner.getLastRayChecks());
        assertTrue(planner.wantsSneak(sample(f,new Vec3(0,-.1,0),0,0,true,false,0,12),sneak));
        assertFalse(planner.wantsSneak(sample(f,new Vec3(0,-.1,0),0,0,true,false,0,13),sneak));
        planner.reset();assertFalse(planner.wantsSneak(five,sneak));assertEquals(0,f.world.clicks);
    }

    @Test public void changedOptionsAndClockRollbackCannotCarrySneakIntoAnotherSession() {
        for(boolean rollback:new boolean[]{false,true}) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.feet=new Vec3(.05,64,.5);
            NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
            NScaffoldClutchPlanner.Options sneak=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                    NScaffoldClutchPlanner.AimMode.CENTER,false,false,false,true,0);
            NScaffoldClutchPlanner.Sample original=sample(f,new Vec3(.1,0,0),0,0,true,true,5,10);
            assertNotNull(planner.plan(original,sneak,()->true));assertTrue(planner.wantsSneak(original,sneak));
            NScaffoldClutchPlanner.Options next=rollback?sneak:PREDICTION;
            NScaffoldClutchPlanner.Sample changed=sample(f,new Vec3(0,.3,0),0,0,true,false,0,rollback?1:11);
            assertNull(planner.plan(changed,next,()->true));assertFalse(planner.wantsSneak(changed,sneak));
        }
    }

    @Test public void aRevokedOldCallerCannotResetTheNewerValidPlan() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.world.feet=new Vec3(.05,64,.5);
        NScaffoldClutchPlanner planner=new NScaffoldClutchPlanner(f.service);
        NScaffoldClutchPlanner.Options sneak=options(NScaffoldClutchPlanner.Criteria.PREDICTION,
                NScaffoldClutchPlanner.AimMode.CENTER,false,false,false,true,0);
        NScaffoldClutchPlanner.Sample current=sample(f,new Vec3(.1,0,0),0,0,true,true,5,10);
        NScaffoldClutchPlanner.Decision d=planner.plan(current,sneak,()->true);assertNotNull(d);
        assertNull(planner.plan(sample(f,new Vec3(.1,0,0),0,0,true,true,5,9),PREDICTION,()->false));
        assertTrue(planner.wantsSneak(current,sneak));assertTrue(planner.onRoute(d.target,current,sneak,()->true));
    }
}
