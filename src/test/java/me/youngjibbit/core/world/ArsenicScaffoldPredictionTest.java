package me.youngjibbit.core.world;

import me.youngjibbit.core.arsenic.geometry.Box;
import me.youngjibbit.core.arsenic.scaffold.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArsenicScaffoldPredictionTest {
    @Test public void predictionUsesConfirmedMovementYawInsteadOfPendingLook() {
        ScaffoldCore.Input in=new ScaffoldCore.Input();in.onGround=true;in.moveForward=1;in.silentYaw=0;in.movementYaw=90;
        Box box=ScaffoldCore.predictedBox(in,1);
        assertTrue((box.minX+box.maxX)/2<-.09);assertEquals(0,(box.minZ+box.maxZ)/2,2e-5);
        in.movementYaw=Float.NaN;Box original=ScaffoldCore.predictedBox(in,1);
        assertTrue((original.minZ+original.maxZ)/2>.09);assertEquals(0,(original.minX+original.maxX)/2,2e-5);
    }
    @Test public void previewCannotConsumeTheEagleHoldOrReleaseCadence() {
        SneakPresses a=new SneakPresses(),b=new SneakPresses();
        for(int tick=0;tick<15;tick++) {
            final boolean edge=tick<5;
            for(int query=0;query<20;query++)assertEquals(a.peek(t->edge,true,2,false),b.peek(t->edge,true,2,false));
            assertEquals(a.next(t->edge,true,2,false,tick==3),b.next(t->edge,true,2,false,tick==3));
        }
    }
    @Test public void laneCorrectionProducesOnlyKeyboardDirectionsAndStopsInAir() {
        ScaffoldCore core=new ScaffoldCore(ScaffoldCore.Tuning.best(),new java.util.Random(1));ScaffoldCore.Input in=new ScaffoldCore.Input();
        in.onGround=true;in.keyForward=true;in.cameraYaw=0;
        for(double x:new double[]{.1,.3,.5,.7,.9}) {
            in.posX=x;float[] nudge=core.inputNudge(in);
            if(nudge!=null)for(float axis:nudge)assertTrue(axis==-1 || axis==0 || axis==1);
        }
        in.onGround=false;assertNull(core.inputNudge(in));
    }
}
