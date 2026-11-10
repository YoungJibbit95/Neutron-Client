package me.youngjibbit.core.activity;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputClickJournalTest {
    @Test public void onlyActualButtonDownEventsCountAndExpireAtOneSecond() {
        InputClickJournal journal=new InputClickJournal();Object world=new Object();
        journal.observe(world,true,0,true,100);
        journal.observe(world,true,0,false,200);
        journal.observe(world,true,1,true,300);
        journal.observe(world,true,2,true,400);
        journal.observe(world,true,-1,true,500);
        InputClickJournal.Snapshot first=journal.snapshot(world,true,600);
        assertEquals(1,first.left);assertEquals(1,first.right);
        assertEquals(2,journal.snapshot(world,true,600).total());
        assertEquals(1,journal.snapshot(world,true,1_000_000_100L).total());
        assertEquals(0,journal.snapshot(world,true,1_000_000_300L).total());
    }
    @Test public void sceneFocusAndClockChangesCannotLeakClicks() {
        InputClickJournal journal=new InputClickJournal();Object world=new Object();
        journal.observe(world,true,0,true,100);
        assertEquals(0,journal.snapshot(new Object(),true,101).total());
        journal.observe(world,true,1,true,102);
        assertEquals(0,journal.snapshot(world,false,103).total());
        journal.observe(world,true,0,true,104);
        assertEquals(0,journal.snapshot(world,true,99).total());
        journal.observe(world,true,0,true,100);
        assertEquals(0,journal.snapshot(null,true,101).total());
    }
    @Test public void observationMemoryIsBoundedAndSnapshotsAreImmutable() {
        InputClickJournal journal=new InputClickJournal();Object world=new Object();
        for(int i=0;i<1000;i++)journal.observe(world,true,i%2,true,i);
        InputClickJournal.Snapshot snapshot=journal.snapshot(world,true,1000);
        assertEquals(256,snapshot.total());assertEquals(128,snapshot.left);assertEquals(128,snapshot.right);
        journal.reset();assertEquals(256,snapshot.total());
        assertEquals(0,journal.snapshot(world,true,1001).total());
    }
}
