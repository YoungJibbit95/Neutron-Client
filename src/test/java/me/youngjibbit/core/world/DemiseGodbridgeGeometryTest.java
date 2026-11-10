package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationSensitivity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

/** Movement/side selection and real native face checks for the authorized Demise geometry. */
public class DemiseGodbridgeGeometryTest {
    @Test public void cardinalMovementChoosesCellSideAndDiagonalMovementKeepsItsReverseHeading() {
        for(int origin:new int[]{0,-9,13}) {
            assertEquals(-135,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.25,origin+.25,0,1,0),0);
            assertEquals(135,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.75,origin+.25,0,1,0),0);
            assertEquals(135,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.25,origin+.25,-45,1,0),0);
            assertEquals(135,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.25,origin+.25,35955,1,0),0);
            assertEquals(135,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.25,origin+.25,0,1,1),0);
            assertEquals(-45,ScaffoldRotationPlan.godbridgeMovementYaw(origin+.25,origin+.25,135,1,0),0);
        }
        assertTrue(Double.isNaN(ScaffoldRotationPlan.godbridgeMovementYaw(.5,.5,Double.NaN,1,0)));
    }

    @Test public void movementPoseUsesSeventyFivePointSevenPitchWhenTheRealFaceAllowsIt() {
        for(float sensitivity:new float[]{0,.5f,1}) {
            ScaffoldPlacementTest.WorldAccess access=new ScaffoldPlacementTest.WorldAccess();
            access.feet=new Vec3(.4,64,.6);
            ScaffoldPlacementService service=new ScaffoldPlacementService(access,()->access.time);
            PlacementTarget original=service.planFace(new BlockPos(-1,63,0),EnumFacing.EAST,null);
            assertNotNull(original);
            AdaptiveBridgePlanner.Frame frame=new AdaptiveBridgePlanner.Frame();
            frame.feetX=.4;frame.feetZ=.6;frame.yaw=-90;frame.forward=1;
            PlacementTarget aimed=service.movementGodbridgeHit(original,access.eyes(),frame,90,0,sensitivity);
            assertNotNull(aimed);assertTrue(service.isValid(aimed));
            ScaffoldRotationPlan actual=ScaffoldRotationPlan.build("Back",access.eyes(),aimed.getHitVec());
            float tolerance=RotationSensitivity.mouseStep(sensitivity)+.01f;
            assertEquals(0,net.minecraft.util.MathHelper.wrapAngleTo180_float(actual.getYaw()-135),tolerance);
            assertEquals(75.7,actual.getPitch(),tolerance);
            assertSame(service.rotationIdentity(original),service.rotationIdentity(aimed));
            assertEquals(original.getDestination(),aimed.getDestination());
            assertEquals(0,access.clicks);assertTrue(access.slots.isEmpty());
        }
    }
}
