package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class DemiseAdaptiveAimGeometryTest {
    @Test public void straightAnchorTracksEyeHeightWithinTheUpperBodyAndKeepsHorizontalCentre() {
        for(double eye:new double[]{-20,64,64.9,65.62,100}) {
            double[] point=AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.STRAIGHT,
                    -30,eye,40,-.3,64,1.7,.3,65.8,2.3);
            assertNotNull(point);assertEquals(0,point[0],0);assertEquals(2,point[2],0);
            assertTrue(point[1]>=64+1.8*.45-1e-10);assertTrue(point[1]<65.8);
            if(eye>=64.81 && eye<65.7982)assertEquals(eye,point[1],1e-12);
        }
    }

    @Test public void nearestAnchorNeverExpandsTheBoxAndInvalidGeometryCannotProduceAim() {
        double[] point=AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.NEAREST,
                -5,100,8,0,0,0,1,2,1);
        assertNotNull(point);assertEquals(.001,point[0],1e-12);
        assertEquals(1.998,point[1],1e-12);assertEquals(.999,point[2],1e-12);
        assertNull(AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.LEGACY,0,0,0,0,0,0,1,2,1));
        assertNull(AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.NEUTRON_NOISE,0,0,0,0,0,0,1,2,1));
        assertNull(AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.STRAIGHT,Double.NaN,0,0,0,0,0,1,2,1));
        assertNull(AdaptivePresetMotion.aimPoint(AdaptivePresetMotion.AimPoint.STRAIGHT,0,0,0,0,0,0,0,2,1));
    }
}
