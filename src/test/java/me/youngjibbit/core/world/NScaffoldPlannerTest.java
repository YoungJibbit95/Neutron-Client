package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class NScaffoldPlannerTest {
    private static NScaffoldPlanner.Sample sample(Vec3 feet,float yaw,float pitch,float forward,float strafe,boolean godbridge) {
        return new NScaffoldPlanner.Sample(feet,feet.addVector(0,1.62,0),yaw,pitch,forward,strafe,63,.65,godbridge,75.7f);
    }
    private static final class EmptyAccess implements NScaffoldPlanner.Access {
        final Set<BlockPos> occupied=new HashSet<>(),destinations=new HashSet<>();
        int calls;Runnable onFace;
        public boolean replaceable(BlockPos p){return !occupied.contains(p);}
        public boolean valid(PlacementTarget p){return p!=null;}
        public PlacementTarget ray(float y,float p){return null;}
        public PlacementTarget retain(PlacementTarget p,float y,float pitch){return null;}
        public PlacementTarget face(BlockPos p,EnumFacing f,Vec3 e,float y,float pitch){
            calls++;destinations.add(p.offset(f));if(onFace!=null)onFace.run();return null;
        }
    }

    @Test public void stableVisibleFaceAndPointSurviveAnAlternativeSupport() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Sample sample=sample(f.world.feet,0,0,0,0,false);
        NScaffoldPlanner.Decision first=planner.plan(sample,()->true);
        assertNotNull(first);assertEquals(new BlockPos(0,63,0),first.target.getDestination());
        assertEquals(5,planner.getLastFaceChecks());
        f.world.block(new BlockPos(1,63,0));
        NScaffoldPlanner.Decision held=planner.plan(sample,()->true);
        assertNotNull(held);assertTrue(held.retained);assertSame(first.target,held.target);
        assertEquals(0,planner.getLastFaceChecks());
        NScaffoldPlanner.Decision onRay=planner.plan(sample(f.world.feet,first.yaw,first.pitch,0,0,false),()->true);
        assertNotNull(onRay);assertTrue(onRay.retained);
        assertEquals(first.target.getBlockPos(),onRay.target.getBlockPos());
        assertEquals(first.target.getFacing(),onRay.target.getFacing());
        assertEquals(0,planner.getLastFaceChecks());
    }

    @Test public void disappearedSupportRequiresANewActuallyVisibleFace() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Sample s=sample(f.world.feet,0,0,0,0,false);
        assertNotNull(planner.plan(s,()->true));
        f.world.cells.clear();f.world.block(new BlockPos(1,63,0));
        NScaffoldPlanner.Decision changed=planner.plan(s,()->true);
        assertNotNull(changed);assertEquals(new BlockPos(1,63,0),changed.target.getBlockPos());
        assertEquals(EnumFacing.WEST,changed.target.getFacing());
    }

    @Test public void diagonalSearchContainsOnlyGoalAndTwoConnectorsAndHasAFixedBudget() {
        EmptyAccess access=new EmptyAccess();access.occupied.add(new BlockPos(0,63,0));
        NScaffoldPlanner planner=new NScaffoldPlanner(access);
        assertNull(planner.plan(sample(new Vec3(.7,64,.7),-45,75,1,0,false),()->true));
        assertEquals(15,access.calls);assertEquals(NScaffoldPlanner.MAX_NEW_FACE_CHECKS,planner.getLastFaceChecks());
        Set<BlockPos> expected=new HashSet<>();
        expected.add(new BlockPos(1,63,1));expected.add(new BlockPos(1,63,0));expected.add(new BlockPos(0,63,1));
        assertEquals(expected,access.destinations);
    }

    @Test public void floorAtNegativeCoordinatesAndNoStationaryRadiusSearch() {
        EmptyAccess access=new EmptyAccess();NScaffoldPlanner planner=new NScaffoldPlanner(access);
        assertNull(planner.plan(sample(new Vec3(-.2,64,-.2),0,0,0,0,false),()->true));
        assertEquals(5,access.calls);assertEquals(1,access.destinations.size());
        assertTrue(access.destinations.contains(new BlockPos(-1,63,-1)));
        access.calls=0;access.occupied.add(new BlockPos(-1,63,-1));
        assertNull(planner.plan(sample(new Vec3(-.2,64,-.2),0,0,0,0,false),()->true));
        assertEquals(0,access.calls);
    }

    @Test public void resetInsideAWorldReadCannotPublishAnOldPlan() {
        EmptyAccess access=new EmptyAccess();NScaffoldPlanner planner=new NScaffoldPlanner(access);
        access.onFace=planner::reset;
        assertNull(planner.plan(sample(new Vec3(.5,64,.5),0,0,0,0,false),()->true));
        assertEquals(1,access.calls);assertEquals(0,planner.getLastFaceChecks());
        access.onFace=null;access.calls=0;
        assertNull(planner.plan(sample(new Vec3(.5,64,.5),0,0,0,0,false),()->false));
        assertEquals(0,access.calls);
    }

    @Test public void rejectedWorldReachAndInventoryNeverBecomePlannerSuccesses() {
        for(int change=0;change<3;change++) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
            if(change==0)f.world.reach=.2;
            if(change==1)f.world.allowed=false;
            if(change==2)f.world.inventory[3]=null;
            NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
            assertNull(planner.plan(sample(f.world.feet,0,0,0,0,false),()->true));
            assertEquals(0,f.world.clicks);assertTrue(f.world.slots.isEmpty());
        }
    }

    @Test public void godbridgeUsesFiniteReachableGeometryAndKeepsTheAcquiredRay() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision first=planner.plan(sample(f.world.feet,0,0,0,0,true),()->true);
        assertNotNull(first);assertTrue(Float.isFinite(first.yaw));assertTrue(Float.isFinite(first.pitch));
        assertTrue(f.service.isValid(first.target));
        NScaffoldPlanner.Decision held=planner.plan(sample(f.world.feet,first.yaw,first.pitch,0,0,true),()->true);
        assertNotNull(held);assertTrue(held.retained);assertEquals(0,planner.getLastFaceChecks());
        assertEquals(first.yaw,held.yaw,0);assertEquals(first.pitch,held.pitch,0);
    }

    @Test public void postPhysicsRouteCannotKeepAPlanSeveralCellsBehind() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
        NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
        NScaffoldPlanner.Decision decision=planner.plan(sample(f.world.feet,0,0,0,0,false),()->true);
        assertNotNull(decision);
        assertTrue(planner.onRoute(decision.target,sample(f.world.feet,0,0,0,0,false),()->true));
        assertFalse(planner.onRoute(decision.target,sample(new Vec3(3.5,64,.5),0,0,0,0,false),()->true));
    }

    @Test public void visibleTurnBudgetUsesTheShortPathAndRejectsNonfiniteInput() {
        float[] small=NScaffoldPlanner.turn(0,0,90,60,10);
        float[] large=NScaffoldPlanner.turn(0,0,90,60,100);
        assertEquals(10,Math.hypot(small[0],small[1]),.001);
        assertEquals(100,Math.hypot(large[0],large[1]),.001);
        assertEquals(180,NScaffoldPlanner.turn(179,0,-179,0,1)[0],.001);
        assertNull(NScaffoldPlanner.turn(0,0,Float.NaN,0,10));
        assertNull(NScaffoldPlanner.turn(0,0,20,0,0));
    }

    @Test public void heldBackAndDiagonalsBuildAcrossNormalTicksWithoutHeadingFeedback() {
        for(boolean godbridge:new boolean[]{false,true})for(float strafe:new float[]{-1,0,1}) {
            ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();
            f.world.cells.clear();f.world.block(new BlockPos(0,63,0));
            NScaffoldPlanner planner=new NScaffoldPlanner(f.service);
            float yaw=45,pitch=75.7f;double totalYawChange=0;
            for(int tick=0;tick<80;tick++) {
                f.service.beginTick();f.world.time+=50;
                NScaffoldPlanner.Decision next=planner.plan(sample(f.world.feet,yaw,pitch,-1,strafe,godbridge),()->true);
                if(next!=null) {
                    totalYawChange+=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(next.yaw-yaw));
                    yaw=next.yaw;pitch=next.pitch;
                }
                // Ordinary camera-relative S / S+A / S+D, without counter-steering.
                double angle=Math.toRadians(yaw),magnitude=Math.hypot(-1,strafe);
                f.world.feet=f.world.feet.addVector((strafe*Math.cos(angle)+Math.sin(angle))/magnitude*.1,
                        0,(-Math.cos(angle)+strafe*Math.sin(angle))/magnitude*.1);
                f.rotations.beginMotion(yaw,pitch);f.rotations.request("nscaffold",15,yaw,pitch);
                f.rotations.finishMotion(yaw,pitch,true);
                if(next!=null && planner.onRoute(next.target,sample(f.world.feet,yaw,pitch,-1,strafe,godbridge),()->true)) {
                    PlacementTarget aligned=f.service.alignAcceptedHit(next.target,f.rotations,"nscaffold");
                    if(aligned!=null && f.service.placeWithAuthority(aligned,f.rotations,false,
                            f.rotations.getMotionSequence(),0,false,false,false,"nscaffold",()->true,()->true))planner.placed();
                }
                assertTrue("held heading, mode="+godbridge+", strafe="+strafe,
                        Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw-45))<=18.05);
            }
            assertTrue("continuous bridge, mode="+godbridge+", strafe="+strafe+", placed="+f.world.clicks,f.world.clicks>=4);
            assertTrue("no alternating large turns, mode="+godbridge+", strafe="+strafe,totalYawChange<100);
        }
    }
}
