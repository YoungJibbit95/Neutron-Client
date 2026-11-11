package me.youngjibbit.utils.render;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class TargetEspRayTest {
    private static void point(Vec3 point,double x,double y,double z) {
        assertNotNull(point);assertEquals(x,point.xCoord,.0002);assertEquals(y,point.yCoord,.0002);assertEquals(z,point.zCoord,.0002);
    }
    @Test public void acceptedAnglesUseVanillaYawAndPitchWithoutCameraInterpolation() {
        point(TargetEspRay.direction(0,0),0,0,1);
        point(TargetEspRay.direction(90,0),-1,0,0);
        point(TargetEspRay.direction(-90,0),1,0,0);
        point(TargetEspRay.direction(180,0),0,0,-1);
        point(TargetEspRay.direction(35,90),0,-1,0);
        point(TargetEspRay.direction(35,-90),0,1,0);
        Vec3 eyes=new Vec3(10.25,63.62,-5.5);
        point(TargetEspRay.endpoint(eyes,TargetEspRay.direction(0,0),6),10.25,63.62,.5);
    }
    @Test public void markerSelectsTheFirstSolidIntersectionRatherThanAnIntendedTarget() {
        Vec3 eyes=new Vec3(0,1,0),end=new Vec3(0,1,6);
        Vec3 entity=TargetEspRay.intercept(new AxisAlignedBB(-.4,0,3,.4,2,4),eyes,end);
        Vec3 wall=TargetEspRay.intercept(new AxisAlignedBB(-1,0,2,1,2,2.5),eyes,end);
        point(entity,0,1,3);point(wall,0,1,2);
        assertFalse(TargetEspRay.nearer(eyes,entity,wall));
        assertTrue(TargetEspRay.nearer(eyes,wall,entity));
        assertFalse(TargetEspRay.nearer(eyes,wall,wall));
        assertTrue(TargetEspRay.nearer(eyes,entity,null));
        assertNull(TargetEspRay.intercept(new AxisAlignedBB(2,0,2,3,2,3),eyes,end));
        assertSame(eyes,TargetEspRay.intercept(new AxisAlignedBB(-1,0,-1,1,2,1),eyes,end));
    }
    @Test public void invalidAnglesAndCoordinatesProduceNoMarker() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertNull(TargetEspRay.direction(bad,0));assertNull(TargetEspRay.direction(0,bad));
        }
        assertNull(TargetEspRay.direction(0,91));assertNull(TargetEspRay.direction(0,-91));
        Vec3 eyes=new Vec3(0,1,0),direction=new Vec3(0,0,1),bad=new Vec3(Double.NaN,0,0);
        assertNull(TargetEspRay.endpoint(eyes,direction,0));assertNull(TargetEspRay.endpoint(eyes,direction,Double.NaN));
        assertNull(TargetEspRay.endpoint(bad,direction,4));assertNull(TargetEspRay.endpoint(eyes,null,4));
        assertFalse(TargetEspRay.nearer(eyes,bad,null));assertFalse(TargetEspRay.finite(null));
    }
}
