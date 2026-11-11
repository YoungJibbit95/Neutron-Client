package me.youngjibbit.core.world;

import org.junit.Test;

import static me.youngjibbit.core.world.NScaffoldModeController.Mode.*;
import static org.junit.Assert.*;

public class NScaffoldModeControllerTest {
    @Test public void straightFlightOpensOnlyAfterFourObservedTicksAndLandingClosesIt() {
        NScaffoldModeController controller=new NScaffoldModeController();
        assertFalse(controller.advance(10,true,-1,0,TELLY,4,2).canBuild);
        for(int airborne=1;airborne<=4;airborne++) {
            NScaffoldModeController.State state=controller.advance(10+airborne,false,-1,0,TELLY,4,2);
            assertTrue(state.valid);assertEquals(airborne,state.airborneTicks);
            assertEquals(4,state.requiredTicks);assertEquals(airborne==4,state.canBuild);
            assertEquals(0,state.groundTicks);
        }
        NScaffoldModeController.State landed=controller.advance(15,true,-1,0,TELLY,4,2);
        assertFalse(landed.canBuild);assertEquals(0,landed.airborneTicks);assertEquals(1,landed.groundTicks);
        assertFalse(controller.advance(16,false,-1,0,TELLY,4,2).canBuild);
        assertEquals(1,controller.getState().airborneTicks);
    }

    @Test public void diagonalThresholdCanChangeWithoutInventingFlightProgress() {
        NScaffoldModeController controller=new NScaffoldModeController();
        assertFalse(controller.advance(1,false,-1,0,TELLY,4,2).canBuild);
        NScaffoldModeController.State diagonal=controller.advance(2,false,-1,1,TELLY,4,2);
        assertTrue(diagonal.canBuild);assertEquals(2,diagonal.airborneTicks);assertEquals(2,diagonal.requiredTicks);
        NScaffoldModeController.State straight=controller.advance(3,false,-1,0,TELLY,4,2);
        assertFalse(straight.canBuild);assertEquals(3,straight.airborneTicks);assertEquals(4,straight.requiredTicks);
        assertTrue(controller.advance(4,false,-1,0,TELLY,4,2).canBuild);
    }

    @Test public void shortHopsCannotAccumulateAFlightWindowAcrossLandings() {
        NScaffoldModeController controller=new NScaffoldModeController();
        long tick=0;
        for(int hop=0;hop<4;hop++) {
            assertFalse(controller.advance(tick++,true,-1,0,TELLY,4,2).canBuild);
            for(int air=1;air<=3;air++) {
                NScaffoldModeController.State state=controller.advance(tick++,false,-1,0,TELLY,4,2);
                assertEquals(air,state.airborneTicks);assertFalse(state.canBuild);
            }
        }
    }

    @Test public void normalAndBreezilyDoNotAddATellyFlightGate() {
        for(NScaffoldModeController.Mode mode:new NScaffoldModeController.Mode[]{NORMAL,BREEZILY}) {
            NScaffoldModeController controller=new NScaffoldModeController();
            NScaffoldModeController.State state=controller.advance(0,true,-1,0,mode,5,5);
            assertTrue(state.valid);assertTrue(state.canBuild);assertEquals(1,state.groundTicks);
            assertTrue(controller.advance(1,false,-1,1,mode,5,5).canBuild);
        }
    }

    @Test public void duplicateObservationsArePureAndCannotAccelerateSneakOrTellyCounters() {
        NScaffoldModeController controller=new NScaffoldModeController();
        NScaffoldModeController.State grounded=controller.advance(20,true,-1,0,TELLY,4,2);
        for(int read=0;read<20;read++) {
            assertSame(grounded,controller.getState());
            assertSame(grounded,controller.advance(20,true,-1,0,TELLY,4,2));
            assertEquals(1,grounded.groundTicks);
        }
        NScaffoldModeController.State airborne=controller.advance(21,false,-1,0,TELLY,4,2);
        for(int duplicate=0;duplicate<20;duplicate++)assertSame(airborne,controller.advance(21,false,-1,0,TELLY,4,2));
        assertEquals(1,airborne.airborneTicks);assertFalse(airborne.canBuild);
    }

    @Test public void conflictingSameTickFactsDoNotReplaceTheAcceptedObservation() {
        NScaffoldModeController controller=new NScaffoldModeController();
        NScaffoldModeController.State accepted=controller.advance(10,false,-1,0,TELLY,4,2);
        assertFalse(controller.advance(10,false,-1,1,TELLY,4,2).valid);
        assertFalse(controller.advance(10,true,-1,0,TELLY,4,2).valid);
        assertFalse(controller.advance(10,false,-1,0,NORMAL,4,2).valid);
        assertFalse(controller.advance(10,false,-1,0,TELLY,1,2).valid);
        assertSame(accepted,controller.getState());
        assertEquals(2,controller.advance(11,false,-1,0,TELLY,4,2).airborneTicks);
    }

    @Test public void skippedAndRegressingTicksCannotManufactureACompletedFlight() {
        NScaffoldModeController controller=new NScaffoldModeController();
        controller.advance(100,false,-1,0,TELLY,4,2);
        controller.advance(101,false,-1,0,TELLY,4,2);
        assertFalse(controller.advance(99,false,-1,0,TELLY,4,2).valid);
        NScaffoldModeController.State resumed=controller.advance(110,false,-1,0,TELLY,4,2);
        assertEquals(1,resumed.airborneTicks);assertFalse(resumed.canBuild);
        controller.advance(111,true,-1,0,TELLY,4,2);
        assertEquals(1,controller.advance(120,true,-1,0,TELLY,4,2).groundTicks);
    }

    @Test public void toggleAndConfigResetsKeepTheConsumedTickButSceneResetDoesNot() {
        NScaffoldModeController controller=new NScaffoldModeController();
        controller.advance(30,false,-1,0,TELLY,1,1);
        controller.reset();
        assertFalse(controller.getState().valid);
        assertFalse(controller.advance(30,false,-1,0,TELLY,1,1).valid);
        assertEquals(1,controller.advance(31,false,-1,0,TELLY,1,1).airborneTicks);
        controller.resetForScene();
        NScaffoldModeController.State newScene=controller.advance(0,true,0,0,NORMAL,4,2);
        assertTrue(newScene.valid);assertTrue(newScene.canBuild);assertEquals(1,newScene.groundTicks);
    }

    @Test public void aNewModeOrTimingConfigurationStartsANewObservedWindow() {
        NScaffoldModeController controller=new NScaffoldModeController();
        controller.advance(1,false,-1,0,NORMAL,4,2);
        controller.advance(2,false,-1,0,NORMAL,4,2);
        assertEquals(1,controller.advance(3,false,-1,0,TELLY,4,2).airborneTicks);
        assertEquals(1,controller.advance(4,false,-1,0,TELLY,5,2).airborneTicks);
        assertEquals(2,controller.advance(5,false,-1,0,TELLY,5,2).airborneTicks);
    }

    @Test public void invalidSamplesCannotEraseOrAdvanceTheLastValidPhase() {
        NScaffoldModeController controller=new NScaffoldModeController();
        NScaffoldModeController.State accepted=controller.advance(1,true,-1,0,TELLY,4,2);
        assertFalse(controller.advance(-1,true,-1,0,TELLY,4,2).valid);
        assertFalse(controller.advance(2,true,Float.NaN,0,TELLY,4,2).valid);
        assertFalse(controller.advance(2,true,-1,Float.POSITIVE_INFINITY,TELLY,4,2).valid);
        assertFalse(controller.advance(2,true,-2,0,TELLY,4,2).valid);
        assertFalse(controller.advance(2,true,-1,0,null,4,2).valid);
        assertFalse(controller.advance(2,true,-1,0,TELLY,0,2).valid);
        assertFalse(controller.advance(2,true,-1,0,TELLY,4,6).valid);
        assertSame(accepted,controller.getState());
        assertEquals(2,controller.advance(2,true,-1,0,TELLY,4,2).groundTicks);
    }
}
