package me.youngjibbit.core.world;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** No Minecraft classes or packets: proposals must be revalidated by the host. */
public class AdaptiveBridgePlannerTest {
    @Test public void acceptedAimSelectsTheSmallerTurnWithoutChangingRawInputPrediction() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner.SupportFace first=face(f);
        AdaptiveBridgePlanner.SupportFace alternate=new AdaptiveBridgePlanner.SupportFace(
                1,64,1,AdaptiveBridgePlanner.Face.WEST,1,64.8,1.5,
                f.selectedSlot,f.scope.world,f.scope.player,f.approvedStack,new Object(),true,true,true);
        List<AdaptiveBridgePlanner.SupportFace> options=new ArrayList<>();
        options.add(first);options.add(alternate);
        assertSame(first,new AdaptiveBridgePlanner().prepare(f,options).intent);
        double[] before=AdaptiveBridgePlanner.predict(f);
        f.hasRotation=true;
        f.rotationYaw=Math.toDegrees(Math.atan2(alternate.hitZ-f.eyeZ,alternate.hitX-f.eyeX))-90;
        f.rotationPitch=-Math.toDegrees(Math.atan2(alternate.hitY-f.eyeY,
                Math.hypot(alternate.hitX-f.eyeX,alternate.hitZ-f.eyeZ)));
        assertSame(alternate,new AdaptiveBridgePlanner().prepare(f,options).intent);
        assertArrayEquals(before,AdaptiveBridgePlanner.predict(f),0);
        f.rotationPitch=100;
        assertFalse(new AdaptiveBridgePlanner().prepare(f,options).hasCandidate);
    }

    @Test public void fallPredictionIsBoundedAndNeitherGroundedMotionNorRisingJumpIsAClutch() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.velocityY=.42;
        assertFalse(AdaptiveBridgePlanner.falling(f));
        assertEquals(f.feetY,AdaptiveBridgePlanner.predictFeetY(f),0);
        f.velocityY=-.1;
        assertTrue(AdaptiveBridgePlanner.falling(f));
        assertEquals(f.feetY-.14,AdaptiveBridgePlanner.predictFeetY(f),1e-9);
        f.velocityY=-3;f.predictionTicks=3;
        assertEquals(f.feetY-1,AdaptiveBridgePlanner.predictFeetY(f),0);
        f.grounded=true;
        assertFalse(AdaptiveBridgePlanner.falling(f));
        assertEquals(f.feetY,AdaptiveBridgePlanner.predictFeetY(f),0);
        f.velocityY=Double.NaN;
        assertFalse(plan(f).hasCandidate);
    }

    @Test public void changedLaneOrVerticalSampleCannotBuyASecondPlanInOneSequence() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.hasLane=true;f.laneX=f.feetX;f.laneZ=f.feetZ;f.laneDirectionZ=1;
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.laneZ+=.1;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f=frame();planner=new AdaptiveBridgePlanner();
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.velocityY=-.1;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f=frame();f.hasLane=true;
        assertFalse("zero-length lane is invalid",plan(f).hasCandidate);
    }
    private static AdaptiveBridgePlanner.Frame frame() {
        AdaptiveBridgePlanner.Frame f = new AdaptiveBridgePlanner.Frame();
        f.scope = new AdaptiveBridgePlanner.Scope(new Object(), new Object(),
                new Object(), new Object(), 1, 1);
        f.approvedStack = new Object();
        f.sequence = 1; f.selectedSlot = 2;
        f.feetX = .5; f.feetY = 65; f.feetZ = .5;
        f.eyeX = .5; f.eyeY = 66.62; f.eyeZ = .5;
        f.destinationX = 0; f.destinationY = 64; f.destinationZ = 1;
        f.yaw = 0; f.forward = 1; f.strafe = 0; f.reach = 4.5;
        f.authorized = true;
        return f;
    }
    private static AdaptiveBridgePlanner.SupportFace face(AdaptiveBridgePlanner.Frame f) {
        return new AdaptiveBridgePlanner.SupportFace(0,64,0,AdaptiveBridgePlanner.Face.SOUTH,
                .5,64.8,1,2,f.scope.world,f.scope.player,f.approvedStack,
                new Object(),true,true,true);
    }
    private static List<AdaptiveBridgePlanner.SupportFace> one(AdaptiveBridgePlanner.Frame f) {
        return Collections.singletonList(face(f));
    }
    private static AdaptiveBridgePlanner.Plan plan(AdaptiveBridgePlanner.Frame f) {
        return new AdaptiveBridgePlanner().prepare(f, one(f));
    }

    @Test public void validPreProposalKeepsExactSupportFaceSlotAndStackIdentity() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner.SupportFace offered=face(f);
        AdaptiveBridgePlanner.Plan p=new AdaptiveBridgePlanner().prepare(f,
                Collections.singletonList(offered));
        assertTrue(p.hasCandidate);
        assertSame(offered,p.intent);
        assertEquals(f.sequence,p.sequence);
        assertSame(f.approvedStack,p.intent.stack);
        assertEquals(2,p.intent.slot);
        assertEquals(AdaptiveBridgePlanner.Face.SOUTH,p.intent.face);
        assertEquals(0,p.intent.x+p.intent.face.x);
        assertEquals(64,p.intent.y+p.intent.face.y);
        assertEquals(1,p.intent.z+p.intent.face.z);
        assertTrue(Double.isFinite(p.yaw) && Double.isFinite(p.pitch));
        assertTrue(p.pitch>=-90 && p.pitch<=90);
        assertFalse("Eagle is opt-in only",p.suggestSneak);
    }

    @Test public void predictiveHeadingSupportsForwardBackwardSideAndDiagonal() {
        for (int direction = 0; direction < 5; direction++) {
            AdaptiveBridgePlanner.Frame f=frame();
            if(direction==1) f.forward=-1;
            if(direction==2) {f.forward=0;f.strafe=1;}
            if(direction==3) f.yaw=90;
            if(direction==4) f.strafe=1;
            AdaptiveBridgePlanner.Plan p=plan(f);
            assertTrue(p.hasCandidate);
            double dx=p.predictedFeetX-f.feetX, dz=p.predictedFeetZ-f.feetZ;
            assertTrue(Math.hypot(dx,dz)<=AdaptiveBridgePlanner.MAX_PREDICTION_BLOCKS+1e-8);
            if(direction==0) assertTrue(dz>0 && Math.abs(dx)<1e-9);
            if(direction==1) assertTrue(dz<0);
            if(direction==2) assertTrue(dx>0 && Math.abs(dz)<1e-9);
            if(direction==3) assertTrue(dx<0 && Math.abs(dz)<1e-9);
            if(direction==4) {
                assertTrue(dx>0 && dz>0);
                assertEquals(dx,dz,1e-9);
            }
        }
    }

    @Test public void accelerationAndVelocityCannotExtrapolateBeyondPointSixBlock() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.velocityX=1.8;f.velocityZ=1.5;
        AdaptiveBridgePlanner.Plan p=plan(f);
        assertTrue(p.hasCandidate);
        assertEquals(.6,Math.hypot(p.predictedFeetX-f.feetX,p.predictedFeetZ-f.feetZ),1e-9);
        f.velocityX=2.1;
        assertFalse(plan(f).hasCandidate); // discontinuity/teleport-like input
    }

    @Test public void negativeWorldCoordinatesAndFacePlaneAreSupported() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.feetX=f.eyeX=-4.5;f.feetZ=f.eyeZ=-7.5;
        f.destinationX=-5; f.destinationZ=-7;
        AdaptiveBridgePlanner.SupportFace o=new AdaptiveBridgePlanner.SupportFace(
                -5,64,-8,AdaptiveBridgePlanner.Face.SOUTH,-4.5,64.8,-7,
                f.selectedSlot,f.scope.world,f.scope.player,f.approvedStack,new Object(),true,true,true);
        AdaptiveBridgePlanner.Plan p=new AdaptiveBridgePlanner().prepare(f,Collections.singletonList(o));
        assertTrue(p.hasCandidate);
        assertSame(o,p.intent);
    }

    @Test public void wrongFacePlaneOrPointOutsideBlockNeverBecomesCandidate() {
        AdaptiveBridgePlanner.Frame f=frame();
        List<AdaptiveBridgePlanner.SupportFace> invalid=new ArrayList<>();
        invalid.add(new AdaptiveBridgePlanner.SupportFace(0,64,0,AdaptiveBridgePlanner.Face.SOUTH,
                .5,64.8,.8,2,f.scope.world,f.scope.player,f.approvedStack,new Object(),true,true,true));
        invalid.add(new AdaptiveBridgePlanner.SupportFace(0,64,0,AdaptiveBridgePlanner.Face.SOUTH,
                1.2,64.8,1,2,f.scope.world,f.scope.player,f.approvedStack,new Object(),true,true,true));
        invalid.add(new AdaptiveBridgePlanner.SupportFace(0,64,0,AdaptiveBridgePlanner.Face.NORTH,
                .5,64.8,0,2,f.scope.world,f.scope.player,f.approvedStack,new Object(),true,true,true));
        assertFalse(new AdaptiveBridgePlanner().prepare(f,invalid).hasCandidate);
    }

    @Test public void unknownSlotStackWorldStateOrHostValidationAlwaysHold() {
        AdaptiveBridgePlanner.Frame f=frame();
        for (int invalid=0;invalid<8;invalid++) {
            Object world=invalid==0?new Object():f.scope.world;
            Object player=invalid==1?new Object():f.scope.player;
            Object stack=invalid==2?new Object():f.approvedStack;
            Object supportState=invalid==3?null:new Object();
            int slot=invalid==4?4:2;
            boolean loaded=invalid!=5,collision=invalid!=6,allowed=invalid!=7;
            AdaptiveBridgePlanner.SupportFace o=new AdaptiveBridgePlanner.SupportFace(
                    0,64,0,AdaptiveBridgePlanner.Face.SOUTH,.5,64.8,1,slot,
                    world,player,stack,supportState,loaded,collision,allowed);
            assertFalse("case "+invalid,new AdaptiveBridgePlanner().prepare(
                    f,Collections.singletonList(o)).hasCandidate);
        }
        f.selectedSlot=9;
        assertFalse(plan(f).hasCandidate);
    }

    @Test public void reachingOnlyFromPredictedPositionIsNotAccepted() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.reach=1.8;f.velocityZ=.4;
        // Original eye distance is about 1.887; prediction cannot illegally extend reach.
        assertFalse(plan(f).hasCandidate);
        f.reach=4.5;
        assertTrue(plan(f).hasCandidate);
    }

    @Test public void atMostTwentyFourOptionsAndStableTieOrdering() {
        AdaptiveBridgePlanner.Frame f=frame();
        List<AdaptiveBridgePlanner.SupportFace> options=new ArrayList<>();
        for(int i=0;i<25;i++)options.add(face(f));
        assertFalse(new AdaptiveBridgePlanner().prepare(f,options).hasCandidate);
        options.remove(options.size()-1);
        AdaptiveBridgePlanner.Plan p=new AdaptiveBridgePlanner().prepare(f,options);
        assertTrue(p.hasCandidate);
        assertSame("equal score uses first PRE option",options.get(0),p.intent);
    }

    @Test public void duplicatePreIsIdempotentAndChangedIntentInSameSequenceBlocks() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        AdaptiveBridgePlanner.Plan first=planner.prepare(f,one(f));
        assertTrue(first.hasCandidate);
        assertSame(first,planner.prepare(f,Collections.<AdaptiveBridgePlanner.SupportFace>emptyList()));
        f.destinationZ=2;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.destinationZ=1;
        f.sequence=2;
        assertFalse("same acquisition cannot resurrect after conflicting frame",
                planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(f.scope.owner,f.scope.world,
                f.scope.player,f.scope.connection,1,2);
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
    }

    @Test public void rejectedOrOldOwnerCannotRearmSameGenerationOrEraseNewOwner() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        AdaptiveBridgePlanner.Scope old=f.scope;
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.sequence=2; f.authorized=false;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.authorized=true;f.sequence=3;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(old.owner,old.world,old.player,
                old.connection,1,2);
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        assertFalse(planner.invalidateIfCurrent(old));
        assertFalse(new AdaptiveBridgePlanner().prepare(invalidFrame(),null).hasCandidate);
        assertTrue(planner.invalidateIfCurrent(f.scope));
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
    }

    private static AdaptiveBridgePlanner.Frame invalidFrame() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.eyeX=Double.NaN;
        return f;
    }

    @Test public void eagleSuggestionRequiresHostEdgeRiskAndARealValidCandidate() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.eagleEnabled=true;f.physicalEdgeRisk=false;
        assertFalse(plan(f).suggestSneak);
        f.physicalEdgeRisk=true;
        assertTrue(plan(f).suggestSneak);
        f.authorized=false;
        assertFalse(plan(f).suggestSneak);
        f.authorized=true;
        f.selectedSlot=8;
        assertFalse(plan(f).suggestSneak);
    }

    @Test public void wrongThreadCannotConsumeOrInvalidateScope() throws Exception {
        final AdaptiveBridgePlanner.Frame f=frame();
        final AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        final AtomicReference<AdaptiveBridgePlanner.Plan> result=new AtomicReference<>();
        Thread worker=new Thread(()->{
            result.set(planner.prepare(f,one(f)));
            planner.reset();
        },"scaffold-plan-foreign");
        worker.start();worker.join();
        assertNotNull(result.get());
        assertFalse(result.get().hasCandidate);
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
    }

    @Test public void frameValidationRejectsNonfiniteAndOutOfBoundsStates() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.velocityX=Double.NaN;assertFalse(plan(f).hasCandidate);
        f.velocityX=0;f.yaw=Double.POSITIVE_INFINITY;assertFalse(plan(f).hasCandidate);
        f.yaw=0;f.eyeY=f.feetY+5;assertFalse(plan(f).hasCandidate);
        f.eyeY=66.62;f.reach=0;assertFalse(plan(f).hasCandidate);
        f.reach=4.5;f.forward=1.1;assertFalse(plan(f).hasCandidate);
        f.forward=1;f.destinationY=256;assertFalse(plan(f).hasCandidate);
        f.destinationY=64;assertTrue(plan(f).hasCandidate);
    }

    @Test public void newConnectionWithLowerEpochRequiresANewAcquisitionAndRejectsOldCallbacks() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner.Scope original=f.scope;
        f.scope=new AdaptiveBridgePlanner.Scope(original.owner,original.world,original.player,
                original.connection,7,4);
        AdaptiveBridgePlanner.Scope old=f.scope;
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        planner.reset();

        Object replacement=new Object();
        f.scope=new AdaptiveBridgePlanner.Scope(old.owner,old.world,old.player,replacement,0,4);
        assertFalse("connection replacement cannot reuse an old acquisition",
                planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(old.owner,new Object(),new Object(),replacement,0,5);
        AdaptiveBridgePlanner.Scope current=f.scope;
        f.sequence=1;
        AdaptiveBridgePlanner.Plan fresh=planner.prepare(f,one(f));
        assertTrue("a legitimate new connection starts with its own epoch",fresh.hasCandidate);

        assertFalse(planner.invalidateIfCurrent(old));
        f.scope=old;f.sequence=100;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(old.owner,old.world,old.player,
                old.connection,1000,4);
        assertFalse("old callback cannot win with an unrelated larger epoch",
                planner.prepare(f,one(f)).hasCandidate);
        f.authorized=false;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.authorized=true;f.scope=current;f.sequence=1;
        assertSame("old callbacks cannot erase the live acquisition",fresh,
                planner.prepare(f,one(f)));
    }

    @Test public void sameConnectionCannotRewindEpochAndResetCannotRearmTheSameScope() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner.Scope initial=f.scope;
        f.scope=new AdaptiveBridgePlanner.Scope(initial.owner,initial.world,initial.player,
                initial.connection,5,8);
        AdaptiveBridgePlanner.Scope current=f.scope;
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(initial.owner,initial.world,initial.player,
                initial.connection,4,9);
        assertFalse("new acquisition cannot legalize an older epoch on the same connection",
                planner.prepare(f,one(f)).hasCandidate);
        f.scope=current;planner.reset();f.sequence++;
        assertFalse("reset keeps the revoked scope tombstone",planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(initial.owner,initial.world,initial.player,
                initial.connection,5,9);
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(new Object(),initial.world,initial.player,
                new Object(),100,100);
        assertFalse("a foreign owner never acquires this planner",planner.prepare(f,one(f)).hasCandidate);
    }

    @Test public void foreignThreadCannotClearAnExistingLiveAcquisition() throws Exception {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        AdaptiveBridgePlanner.Plan first=planner.prepare(f,one(f));
        AtomicReference<Boolean> invalidated=new AtomicReference<>();
        Thread worker=new Thread(()->{
            invalidated.set(planner.invalidateIfCurrent(f.scope));
            planner.reset();
        },"scaffold-plan-late-thread");
        worker.start();worker.join();
        assertEquals(Boolean.FALSE,invalidated.get());
        assertSame(first,planner.prepare(f,one(f)));
    }

    @Test public void statelessPredictionUsesABoundedHorizonWithoutConsumingPre() {
        AdaptiveBridgePlanner.Frame f=frame();
        f.predictionTicks=0;
        assertArrayEquals(new double[]{f.feetX,f.feetZ},AdaptiveBridgePlanner.predict(f),0);
        f.predictionTicks=2;
        assertEquals(f.feetZ+.36,AdaptiveBridgePlanner.predict(f)[1],1e-9);
        f.velocityZ=.7;f.predictionTicks=3;
        assertEquals(.6,AdaptiveBridgePlanner.predict(f)[1]-f.feetZ,1e-9);
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        assertTrue("preview does not reserve the actual PRE sequence",
                planner.prepare(f,one(f)).hasCandidate);
        f.predictionTicks=Double.NaN;
        assertArrayEquals(new double[]{f.feetX,f.feetZ},AdaptiveBridgePlanner.predict(f),0);
        assertFalse(plan(f).hasCandidate);
        f.predictionTicks=4;
        assertFalse(plan(f).hasCandidate);
    }

    @Test public void directionChangeInsideOneSequenceRevokesInsteadOfReplanning() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
        f.forward=-1;
        assertFalse("fresh opposite input cannot inherit an earlier candidate",
                planner.prepare(f,one(f)).hasCandidate);
        f.forward=1;f.sequence++;
        assertFalse("next frame must use a new acquisition token",
                planner.prepare(f,one(f)).hasCandidate);
        f.scope=new AdaptiveBridgePlanner.Scope(f.scope.owner,f.scope.world,f.scope.player,
                f.scope.connection,f.scope.connectionEpoch,f.scope.acquisitionGeneration+1);
        assertTrue(planner.prepare(f,one(f)).hasCandidate);
    }

    @Test public void invalidOldFrameCannotEraseANewerSequenceInTheSameScope() {
        AdaptiveBridgePlanner.Frame f=frame();
        AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
        f.sequence=10;
        AdaptiveBridgePlanner.Plan current=planner.prepare(f,one(f));
        assertTrue(current.hasCandidate);
        f.sequence=9;f.authorized=false;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
        f.sequence=10;f.authorized=true;
        assertSame("late invalid callback cannot revoke current PRE",current,
                planner.prepare(f,one(f)));
        f.authorized=false;
        assertFalse("invalid CURRENT frame must still revoke",planner.prepare(f,one(f)).hasCandidate);
        f.authorized=true;f.sequence=11;
        assertFalse(planner.prepare(f,one(f)).hasCandidate);
    }
}
