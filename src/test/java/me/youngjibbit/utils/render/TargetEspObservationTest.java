package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class TargetEspObservationTest {
    private final TargetEspObservation observation = new TargetEspObservation();
    private final Object world = new Object(), player = new Object(), pipeline = new Object();
    private long now = 1_000_000_000L;

    private boolean current(long sequence, String owner, boolean aura, boolean scaffold) {
        return observation.current(world,player,pipeline,3,sequence,now,true,false,true,owner,aura,scaffold);
    }
    private void prime() {
        assertFalse(current(10,"aura",true,true));
        now += 50_000_000;
        assertTrue(current(11,"aura",true,true));
    }

    @Test public void newlyEnabledViewWaitsForANewAcceptedMotionAndExpiresWithoutMoreTicks() {
        prime();
        now += 250_000_000;
        assertTrue(current(11,"aura",true,true));
        now++;
        assertFalse(current(11,"aura",true,true));
        assertTrue(current(12,"aura",true,true));
    }
    @Test public void onlyTheAcceptedActiveProducerCanOwnTheMarker() {
        prime();
        assertFalse(current(11,null,true,true));
        assertFalse(current(11,"aimassist",true,true));
        assertFalse(current(11,"aura",false,true));
        assertFalse(current(11,"scaffold",true,false));
        assertTrue(current(11,"scaffold",false,true));
        assertFalse(observation.current(world,player,pipeline,3,11,now,false,false,true,"aura",true,true));
        assertFalse(observation.current(world,player,pipeline,3,11,now,true,true,true,"aura",true,true));
        assertFalse(observation.current(world,player,pipeline,3,11,now,true,false,false,"aura",true,true));
    }
    @Test public void worldsPlayersPipelinesAndConnectionEpochsCannotReviveOldMarkers() {
        for(int change=0;change<4;change++) {
            observation.reset();prime();
            Object w=change==0?new Object():world,p=change==1?new Object():player,pipe=change==2?new Object():pipeline;
            long epoch=change==3?4:3;
            assertFalse(observation.current(w,p,pipe,epoch,11,now,true,false,true,"aura",true,true));
            assertFalse(observation.current(w,p,pipe,epoch,11,now+1,true,false,true,"aura",true,true));
            assertTrue(observation.current(w,p,pipe,epoch,12,now+2,true,false,true,"aura",true,true));
        }
    }
    @Test public void resetRegressionAndClockRollbackWaitForFreshState() {
        prime();observation.reset();
        assertFalse(current(11,"aura",true,true));
        assertTrue(current(12,"aura",true,true));
        assertFalse(current(9,"aura",true,true));
        assertTrue(current(10,"aura",true,true));
        now--;
        assertFalse(current(10,"aura",true,true));
        assertTrue(current(11,"aura",true,true));
        assertFalse(observation.current(null,player,pipeline,3,12,now,true,false,true,"aura",true,true));
        assertFalse(current(12,"aura",true,true));
    }

    @Test public void newBuilderIsAnIndependentExactOwnerInsteadOfAnAliasForScaffold() {
        prime();
        assertTrue(observation.current(world,player,pipeline,3,12,now,true,false,true,"nscaffold",false,false,true));
        assertFalse(observation.current(world,player,pipeline,3,12,now,true,false,true,"nscaffold",false,true,false));
        assertFalse(observation.current(world,player,pipeline,3,12,now,true,false,true,"scaffold",false,false,true));
        assertFalse(current(12,"nscaffold",true,true));
        assertFalse(observation.current(world,player,pipeline,3,12,now,false,false,true,"nscaffold",false,false,true));
        assertFalse(observation.current(world,player,pipeline,3,12,now,true,true,true,"nscaffold",false,false,true));
    }

    @Test public void newBuilderHasTheSameSceneFreshnessAndExpiryAsExistingProducers() {
        assertFalse(observation.current(world,player,pipeline,3,1,now,true,false,true,"nscaffold",false,false,true));
        assertTrue(observation.current(world,player,pipeline,3,2,now+1,true,false,true,"nscaffold",false,false,true));
        assertFalse(observation.current(world,player,pipeline,3,2,now+250_000_002L,true,false,true,"nscaffold",false,false,true));
        assertFalse(observation.current(world,player,new Object(),3,3,now+250_000_003L,true,false,true,"nscaffold",false,false,true));
    }
}
