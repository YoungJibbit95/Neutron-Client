package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class RotationReturnCurveTest {
    @Test public void returnUsesShortestArcAndSoftEndpoints() {
        assertEquals(0, RotationReturnCurve.progress(-1), 0);
        assertEquals(1, RotationReturnCurve.progress(2), 0);
        assertEquals(.5f, RotationReturnCurve.progress(.5f), 1e-6f);
        assertTrue(RotationReturnCurve.progress(.1f) < .01f);
        assertEquals(180, RotationReturnCurve.angle(179, -179, .5f), 1e-5f);
        float previous=0;
        for(int tick=0;tick<=12;tick++) {
            float progress=RotationReturnCurve.progress(tick/12f);
            assertTrue(progress>=previous); previous=progress;
        }
    }

    @Test public void presentationRetainsTheRenderedBodyAndConvergesToVanilla() {
        BodyRotationController controller=new BodyRotationController();
        Object player=new Object(),world=new Object(),lease=new Object();
        controller.sampleFrame(1,player,world,"aura",BodyRotationController.Mode.DIRECT,90,30,0,0);
        BodyRotationController.Pose start=controller.sampleReturn(player,world,lease,"aura",90,30,0,0);
        assertEquals(90,start.bodyYaw,0); assertEquals(90,start.headYaw,0);
        BodyRotationController.Pose middle=controller.sampleReturn(player,world,lease,"aura",45,15,0,.5f);
        assertEquals(45,middle.bodyYaw,0); assertEquals(15,middle.headPitch,0);
        BodyRotationController.Pose end=controller.sampleReturn(player,world,lease,"aura",0,0,0,1);
        assertEquals(0,end.bodyYaw,0); assertEquals(0,end.headYaw,0);
    }
}
