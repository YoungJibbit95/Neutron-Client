package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandPlacementMetricsTest {
    @Test public void switchingIndependentProducersBeginsANewBaselineWithoutCrossSourceClicks() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();Object a=new Object(),b=new Object();
        assertEquals(0,metrics.sample(a,8,0).cps);
        assertEquals(0,metrics.sample(b,64,125_000_000L).cps);
        assertEquals(1,metrics.sample(b,65,250_000_000L).cps);
        IslandPlacementMetrics.Snapshot returned=metrics.sample(a,8,375_000_000L);
        assertEquals(0,returned.cps);assertTrue(returned.known);
        for(int i=0;i<returned.samples();i++)assertEquals(0,returned.sample(i),0);
        assertEquals(1,metrics.sample(a,9,500_000_000L).cps);
    }
    @Test public void sameProducerCounterResetAndObservedReenableDiscardThePreviousRunHistory() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();Object producer=new Object();
        metrics.sample(producer,8,0);assertEquals(1,metrics.sample(producer,9,125_000_000L).cps);
        IslandPlacementMetrics.Snapshot restarted=metrics.sample(producer,0,250_000_000L);
        assertEquals(0,restarted.cps);
        for(int i=0;i<restarted.samples();i++)assertEquals(0,restarted.sample(i),0);
        assertEquals(1,metrics.sample(producer,1,375_000_000L).cps);
        assertFalse(metrics.sample(null,-1,400_000_000L).known);
        assertEquals(0,metrics.sample(producer,64,500_000_000L).cps);
        assertEquals(1,metrics.sample(producer,65,625_000_000L).cps);
        metrics.reset();assertEquals(0,metrics.sample(producer,90,750_000_000L).cps);
    }
    @Test public void equalDisplayLabelsCannotMergeReplacementProducerInstances() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();
        Object first=new String("Scaffold"),replacement=new String("Scaffold");
        assertEquals(first,replacement);assertNotSame(first,replacement);
        metrics.sample(first,1,0);assertEquals(1,metrics.sample(first,2,125_000_000L).cps);
        assertEquals(0,metrics.sample(replacement,100,250_000_000L).cps);
        assertEquals(1,metrics.sample(replacement,101,375_000_000L).cps);
        assertFalse(metrics.sample(null,101,500_000_000L).known);
    }
    @Test public void measuredCpsCountsOnlyConfirmedDeltasAndNotRepeatedFrames() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();
        assertEquals(0,metrics.sample(20,0).cps);
        for(int i=1;i<=100;i++)assertEquals(0,metrics.sample(20,i*1_000_000L).cps);
        assertEquals(1,metrics.sample(21,150_000_000L).cps);
        assertEquals(3,metrics.sample(23,200_000_000L).cps);
        assertEquals(3,metrics.sample(23,600_000_000L).cps);
        assertEquals(3,metrics.sample(23,1_100_000_000L).cps);
        assertEquals(2,metrics.sample(23,1_150_000_000L).cps);
        assertEquals(0,metrics.sample(23,1_200_000_000L).cps);
    }
    @Test public void unknownRestartRollbackAndUnobservedGapNeverInventABurst() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();
        assertFalse(metrics.sample(-1,1).known);
        assertTrue(metrics.sample(40,100).known);
        assertEquals(1,metrics.sample(41,200).cps);
        assertEquals(0,metrics.sample(0,300).cps);
        assertEquals(1,metrics.sample(1,400).cps);
        assertEquals(0,metrics.sample(2,100).cps);
        assertEquals(0,metrics.sample(25,800_000_000L).cps);
    }
    @Test public void trendIsBoundedFrozenAndContainsMeasuredRates() {
        IslandPlacementMetrics metrics=new IslandPlacementMetrics();
        metrics.sample(0,0);
        IslandPlacementMetrics.Snapshot first=metrics.sample(1,125_000_000L);
        assertEquals(16,first.samples());assertEquals(1,first.sample(15),0);
        for(int i=2;i<40;i++)metrics.sample(i,i*125_000_000L);
        assertEquals(1,first.sample(15),0);
        IslandPlacementMetrics.Snapshot current=metrics.sample(39,39*125_000_000L);
        assertTrue(current.ceiling()>=1&&current.ceiling()<=8);
        for(int i=0;i<current.samples();i++)assertTrue(current.sample(i)>=0&&current.sample(i)<=8);
    }
}
