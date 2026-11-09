package me.youngjibbit.core.activity;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalCorrectionSnapshotTest {
    @Test public void copiesOnlyFiniteLocalFactsAndKeepsTheActualTimestamp(){
        LocalCorrectionSnapshot snapshot=new LocalCorrectionSnapshot(7,3,100,.5,5);
        assertEquals(7,snapshot.getRunId());assertEquals(3,snapshot.getSequence());
        assertEquals(100,snapshot.getObservedAtNanos());assertEquals(.5,snapshot.getDistance(),0);
        assertEquals(5,snapshot.getAngle(),0);assertFalse(snapshot.isFresh(99));assertTrue(snapshot.isFresh(100));
        assertTrue(snapshot.isFresh(100+LocalCorrectionSnapshot.TTL_NANOS));
        assertFalse(snapshot.isFresh(101+LocalCorrectionSnapshot.TTL_NANOS));
    }
    @Test public void invalidAndNonfiniteFactsAreNeverDisplayEvidence(){
        assertThrows(IllegalArgumentException.class,()->new LocalCorrectionSnapshot(1,1,0,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new LocalCorrectionSnapshot(1,1,0,0,Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new LocalCorrectionSnapshot(1,1,0,-1,0));
        assertThrows(IllegalArgumentException.class,()->new LocalCorrectionSnapshot(1,0,0,1,0));
    }
}
