package me.youngjibbit.core.activity;

import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetIncidentJournalTest {
    private static final long MS=1_000_000L;
    private static WidgetIncidentJournal journal(){
        WidgetIncidentJournal journal=new WidgetIncidentJournal();journal.context(new Object(),new Object(),7);return journal;
    }
    @Test public void slowFrameRequiresARealBaselineAndExpiresWithoutRefresh(){
        WidgetIncidentJournal journal=journal();long now=MS;
        for(int i=0;i<31;i++){journal.observeFrame(now);now+=16*MS;}
        assertNull(journal.latest(WidgetIncident.Kind.FPS,now));
        now+=80*MS;journal.observeFrame(now);
        WidgetIncident incident=journal.latest(WidgetIncident.Kind.FPS,now);
        assertNotNull(incident);assertEquals("Slow client frame",incident.headline);
        assertTrue(incident.detail.contains("baseline 16.0 ms"));assertTrue(incident.detail.contains("Cause unconfirmed"));
        journal.observeFrame(now+90*MS);assertSame("cooldown prevents expanding every slow frame",incident,journal.latest(WidgetIncident.Kind.FPS,now+90*MS));
        assertNull(journal.latest(WidgetIncident.Kind.FPS,incident.expiresAtNanos));
    }
    @Test public void sustainedMeasuredFpsDropHasNoClaimAboutItsCause(){
        WidgetIncidentJournal journal=journal();long now=MS;
        for(int i=0;i<121;i++){journal.observeFrame(now);now+=10*MS;}
        for(int i=0;i<50&&journal.latest(WidgetIncident.Kind.FPS,now)==null;i++){journal.observeFrame(now);now+=25*MS;}
        WidgetIncident incident=journal.latest(WidgetIncident.Kind.FPS,now);
        assertNotNull(incident);assertEquals("FPS drop",incident.headline);
        assertTrue(incident.value.contains("FPS"));assertTrue(incident.detail.startsWith("Measured across"));
        assertTrue(incident.detail.endsWith("Cause unconfirmed."));
    }
    @Test public void hiddenFramesAndClockDiscontinuitiesCannotInventAStall(){
        WidgetIncidentJournal journal=journal();long now=MS;
        for(int i=0;i<31;i++){journal.observeFrame(now);now+=16*MS;}
        journal.suspendFrames();journal.observeFrame(now+400*MS);
        assertNull(journal.latest(WidgetIncident.Kind.FPS,now+400*MS));
        journal.observeFrame(now+2000*MS);assertNull(journal.latest(WidgetIncident.Kind.FPS,now+2000*MS));
        journal.observeFrame(now);assertNull(journal.latest(WidgetIncident.Kind.FPS,now));
    }
    @Test public void correctionNeedsRecentPrecedingActivityFromTheSameEpoch(){
        WidgetIncidentJournal journal=journal();
        journal.observeCorrection(new LocalCorrectionSnapshot(1,1,100*MS,1,20),100*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,100*MS));
        journal.observeActivity(ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,101*MS,8,2,34),101*MS);
        journal.observeCorrection(new LocalCorrectionSnapshot(1,2,150*MS,1,20),150*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,150*MS));
        journal.observeActivity(ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,151*MS,7,2,34),151*MS);
        journal.observeCorrection(new LocalCorrectionSnapshot(1,3,200*MS,1,20),200*MS);
        WidgetIncident incident=journal.latest(WidgetIncident.Kind.EXPLOIT,200*MS);
        assertNotNull(incident);assertEquals("Correction observed",incident.headline);
        assertTrue(incident.detail.contains("49 ms after Backtrack holding (34 ms)"));
        assertTrue(incident.detail.endsWith("Cause unconfirmed."));
        journal.observeCorrection(new LocalCorrectionSnapshot(1,3,300*MS,10,40),300*MS);
        assertSame("same event cannot renew its lifetime",incident,journal.latest(WidgetIncident.Kind.EXPLOIT,300*MS));
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,incident.expiresAtNanos));
    }
    @Test public void staleFutureAndOutOfWindowActivityDoNotCorrelate(){
        WidgetIncidentJournal journal=journal();
        journal.observeActivity(ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,MS,7,1,1),300*MS);
        journal.observeCorrection(new LocalCorrectionSnapshot(1,1,400*MS,1,20),400*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,400*MS));
        journal.observeActivity(ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,600*MS,7,1,1),500*MS);
        journal.observeCorrection(new LocalCorrectionSnapshot(1,2,700*MS,1,20),700*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,700*MS));
        journal.observeActivity(ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,800*MS,7,1,1),800*MS);
        journal.observeCorrection(new LocalCorrectionSnapshot(1,3,3000*MS,1,20),3000*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,3000*MS));
    }
    @Test public void onlyTheExactAppliedCorrectionCanCarryAnIncidentAcrossItsEpoch(){
        Object world=new Object(),connection=new Object();WidgetIncidentJournal journal=new WidgetIncidentJournal();
        journal.context(world,connection,7);
        journal.observeActivity(ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.TICKBASE,MS,7,0,2,true),MS);
        journal.appliedCorrection(world,connection,7,10,new LocalCorrectionSnapshot(0,1,20*MS,2,10),20*MS);
        WidgetIncident incident=journal.latest(WidgetIncident.Kind.EXPLOIT,20*MS);assertNotNull(incident);
        journal.context(world,connection,10);assertSame(incident,journal.latest(WidgetIncident.Kind.EXPLOIT,20*MS));
        journal.context(world,connection,11);assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,20*MS));
        journal.observeActivity(ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.TICKBASE,MS,11,0,2,true),MS);
        journal.context(new Object(),connection,11);
        journal.observeCorrection(new LocalCorrectionSnapshot(0,2,30*MS,2,10),30*MS);
        assertNull(journal.latest(WidgetIncident.Kind.EXPLOIT,30*MS));
    }
}
