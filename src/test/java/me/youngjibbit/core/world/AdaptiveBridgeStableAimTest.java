package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationSensitivity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native face intersection with bounded synthetic block storage, without placement authority. */
public class AdaptiveBridgeStableAimTest {
    private static final class Access extends ScaffoldPlacementTest.WorldAccess {
        final List<BlockPos> queried = new ArrayList<>();
        int fullReachRays;
        @Override public AxisAlignedBB supportBounds(BlockPos pos) {
            queried.add(pos); return super.supportBounds(pos);
        }
        @Override public MovingObjectPosition rayTrace(Vec3 from,Vec3 to) {
            if(from.distanceTo(to)>=reach-.01)fullReachRays++;
            return super.rayTrace(from,to);
        }
    }
    private static AdaptiveBridgePlanner.Frame frame(Access access, double yaw, double pitch) {
        AdaptiveBridgePlanner.Frame frame = new AdaptiveBridgePlanner.Frame();
        frame.scope = new AdaptiveBridgePlanner.Scope(new Object(), access.world, access.player, new Object(), 0, 1);
        frame.sequence = 1; frame.authorized = frame.grounded = frame.hasRotation = true;
        frame.feetX = access.feet.xCoord; frame.feetY = access.feet.yCoord; frame.feetZ = access.feet.zCoord;
        Vec3 eyes = access.eyes(); frame.eyeX = eyes.xCoord; frame.eyeY = eyes.yCoord; frame.eyeZ = eyes.zCoord;
        frame.rotationYaw = yaw; frame.rotationPitch = pitch; frame.reach = access.reach;
        return frame;
    }
    private static double wrap(double angle) {
        angle %= 360; if (angle > 180) angle -= 360; if (angle <= -180) angle += 360; return angle;
    }
    private static double yaw(Vec3 eyes, Vec3 point) {
        return ScaffoldRotationPlan.build("Back", eyes, point).getYaw();
    }

    @Test public void diagonalFacesUseTheirSharedEdgeInsteadOfAlternatingBetweenCentres() {
        for (int origin : new int[]{0, -9}) for (int xSign : new int[]{-1, 1}) for (int zSign : new int[]{-1, 1}) {
            AxisAlignedBB box = new AxisAlignedBB(origin, 63, origin, origin + 1, 64, origin + 1);
            Vec3 eyes = new Vec3(origin + (xSign > 0 ? 1.05 : -.05), 65.62,
                    origin + (zSign > 0 ? 1.05 : -.05));
            EnumFacing xFace = xSign > 0 ? EnumFacing.EAST : EnumFacing.WEST;
            EnumFacing zFace = zSign > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            Vec3 corner = new Vec3(origin + (xSign > 0 ? 1 : 0), 63.8, origin + (zSign > 0 ? 1 : 0));
            double heading = yaw(eyes, corner);
            Vec3 a = ScaffoldRotationPlan.stableFacePoint(box, xFace, eyes, heading, 83.52);
            Vec3 b = ScaffoldRotationPlan.stableFacePoint(box, zFace, eyes, heading, 83.52);
            assertNotNull(a); assertNotNull(b);
            double stableSwing = Math.abs(wrap(yaw(eyes, a) - yaw(eyes, b)));
            Vec3 aCentre = ScaffoldRotationPlan.hitPoint("Back", box, xFace, eyes);
            Vec3 bCentre = ScaffoldRotationPlan.hitPoint("Back", box, zFace, eyes);
            double centreSwing = Math.abs(wrap(yaw(eyes, aCentre) - yaw(eyes, bCentre)));
            assertTrue("shared-edge turns remain small", stableSwing < 6);
            assertTrue("both diagonal faces must materially improve", stableSwing < centreSwing / 8);
            assertEquals(xFace, box.calculateIntercept(eyes, a.addVector(
                    (a.xCoord - eyes.xCoord) * .01, (a.yCoord - eyes.yCoord) * .01,
                    (a.zCoord - eyes.zCoord) * .01)).sideHit);
            assertEquals(zFace, box.calculateIntercept(eyes, b.addVector(
                    (b.xCoord - eyes.xCoord) * .01, (b.yCoord - eyes.yCoord) * .01,
                    (b.zCoord - eyes.zCoord) * .01)).sideHit);
        }
    }

    @Test public void godbridgeKeepsItsMathematicalHeadingAsTheHitpointMovesWithThePlayer() {
        AxisAlignedBB box = new AxisAlignedBB(-1, 63, 0, 0, 64, 1);
        Vec3 last = null;
        for (double x : new double[]{.16, .18, .20}) {
            Vec3 eyes = new Vec3(x, 65.62, .6);
            Vec3 hit = ScaffoldRotationPlan.godbridgeFacePoint(box, EnumFacing.EAST, eyes, 135, 83.52);
            assertNotNull(hit);
            ScaffoldRotationPlan aim = ScaffoldRotationPlan.build("Back", eyes, hit);
            assertEquals(0, wrap(aim.getYaw()-135), 1e-4); assertEquals(83.52, aim.getPitch(), 1e-4);
            assertEquals(0, hit.xCoord, 0);
            if (last != null) assertTrue("a fixed viewing angle is not a fixed world coordinate", last.squareDistanceTo(hit) > .001);
            last = hit;
        }
    }

    @Test public void currentRayAcquisitionAndKeepingDoNotEnumerateOtherSupportingFaces() {
        Access access = new Access();
        ScaffoldPlacementService service = new ScaffoldPlacementService(access, () -> access.time);
        Vec3 wanted = new Vec3(0, 63.8, .85);
        ScaffoldRotationPlan direction = ScaffoldRotationPlan.build("Back", access.eyes(), wanted);
        AdaptiveBridgePlanner.Frame frame = frame(access, direction.getYaw(), direction.getPitch());
        AdaptiveBridgeScaffoldAdapter adapter = new AdaptiveBridgeScaffoldAdapter();
        access.queried.clear();
        PlacementTarget target = adapter.findEarlyTarget(service, frame, 63, null);
        assertNotNull(target); assertEquals(new BlockPos(0, 63, 0), target.getDestination());
        assertNotNull(adapter.prepare(service, target, frame));
        assertFalse(access.queried.isEmpty());
        for (BlockPos queried : access.queried)
            assertEquals("a current valid ray needs no other support search", new BlockPos(-1, 63, 0), queried);
        access.fullReachRays=0;
        assertSame(target, adapter.findEarlyTarget(service, frame, 63, target));
        assertEquals("validating an acquired point needs no new current-ray search",0,access.fullReachRays);
        assertEquals(0, access.clicks); assertTrue(access.slots.isEmpty());
    }

    @Test public void unrelatedCurrentRayCannotAcquireAnOffRouteRow() {
        Access access = new Access(); access.cells.clear(); access.block(new BlockPos(-2, 63, 0));
        ScaffoldPlacementService service = new ScaffoldPlacementService(access, () -> access.time);
        ScaffoldRotationPlan direction = ScaffoldRotationPlan.build("Back", access.eyes(), new Vec3(-1, 63.5, .5));
        assertNotNull(service.planBridgeRay(direction.getYaw(), direction.getPitch()));
        AdaptiveBridgePlanner.Frame frame = frame(access, direction.getYaw(), direction.getPitch());
        assertNull(new AdaptiveBridgeScaffoldAdapter().findEarlyTarget(service, frame, 63, null));
        assertEquals(0, access.clicks); assertTrue(access.slots.isEmpty());
    }

    @Test public void stabilizingHitPreservesIdentityAndUsesMinimalVisibleFaceCorrection() {
        Access access = new Access(); access.cells.clear(); access.block(new BlockPos(0, 63, 0));
        access.feet = new Vec3(1.05, 64, 1.05);
        ScaffoldPlacementService service = new ScaffoldPlacementService(access, () -> access.time);
        PlacementTarget target = service.planFace(new BlockPos(0, 63, 0), EnumFacing.EAST, null);
        assertNotNull(target);
        PlacementTarget stable = service.stabilizeBridgeHit(target, access.eyes(), 135, 83.52f, false);
        assertNotNull(stable); assertTrue(service.isValid(stable));
        assertSame(service.rotationIdentity(target), service.rotationIdentity(stable));
        assertEquals(target.getBlockPos(), stable.getBlockPos()); assertEquals(target.getFacing(), stable.getFacing());
        assertEquals(target.getDestination(), stable.getDestination()); assertSame(target.stack, stable.stack);
        assertTrue(Math.abs(wrap(yaw(access.eyes(), stable.getHitVec()) - 135)) < 3);
        access.cells.clear();
        assertNull("stale support never gets a mathematical rescue", service.stabilizeBridgeHit(
                target, access.eyes(), 135, 83.52f, false));
    }

    @Test public void nearestFaceProjectionSupportsPartialBoundsAndRejectsUnusableDirections() {
        AxisAlignedBB slab = new AxisAlignedBB(0, 63, 0, 1, 63.5, 1);
        Vec3 eyes = new Vec3(1.1, 65.1, .8);
        Vec3 point = ScaffoldRotationPlan.stableFacePoint(slab, EnumFacing.EAST, eyes, 90, 83.52);
        assertNotNull(point); assertEquals(1, point.xCoord, 0);
        assertTrue(point.yCoord > 63 && point.yCoord < 63.5);
        assertEquals(.8, point.zCoord, 1e-8);
        assertNull(ScaffoldRotationPlan.stableFacePoint(slab, EnumFacing.EAST, eyes, -90, 83.52));
        assertNull(ScaffoldRotationPlan.stableFacePoint(slab, EnumFacing.EAST, eyes, Double.NaN, 83.52));
        assertNull(ScaffoldRotationPlan.godbridgeFacePoint(null, EnumFacing.EAST, eyes, 90, 83.52));
    }

    @Test public void nearestEdgePointRemainsOnItsExactFaceAfterActualMouseQuantization() {
        for(float sensitivity:new float[]{0,.5f,1})for(int origin:new int[]{0,-9})
            for(int xSign:new int[]{-1,1})for(int zSign:new int[]{-1,1})for(double gap:new double[]{.05,.15}) {
                Access access=new Access();access.cells.clear();
                BlockPos support=new BlockPos(origin,63,origin);access.block(support);
                access.feet=new Vec3(origin+(xSign>0?1+gap:-gap),64,origin+(zSign>0?1+gap:-gap));
                ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
                Vec3 corner=new Vec3(origin+(xSign>0?1:0),63.8,origin+(zSign>0?1:0));
                float heading=(float)yaw(access.eyes(),corner),pitch=83.52f;
                for(EnumFacing face:new EnumFacing[]{xSign>0?EnumFacing.EAST:EnumFacing.WEST,
                        zSign>0?EnumFacing.SOUTH:EnumFacing.NORTH}) {
                    PlacementTarget original=service.planFace(support,face,null);assertNotNull(original);
                    PlacementTarget candidate=service.stabilizeBridgeHit(original,access.eyes(),heading,pitch,false,sensitivity);
                    assertNotNull(candidate);
                    ScaffoldRotationPlan wanted=ScaffoldRotationPlan.build("Back",access.eyes(),candidate.getHitVec());
                    float snappedYaw=RotationSensitivity.snap(heading+(float)wrap(wanted.getYaw()-heading),heading,sensitivity);
                    float snappedPitch=RotationSensitivity.snap(wanted.getPitch(),pitch,sensitivity);
                    assertNotNull("native mouse grid must not redirect a near-top face hit: sensitivity="+sensitivity
                            +" face="+face+" gap="+gap,
                            service.retainBridgeHit(candidate,snappedYaw,snappedPitch));
                    assertSame(service.rotationIdentity(original),service.rotationIdentity(candidate));
                }
                assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
            }
    }
}
