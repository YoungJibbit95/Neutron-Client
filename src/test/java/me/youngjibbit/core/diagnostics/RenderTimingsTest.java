package me.youngjibbit.core.diagnostics;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class RenderTimingsTest {
    @Test public void exclusiveSectionsDoNotDoubleCountAndExcludeTicksAndForeignProfilers()throws Exception {
        AtomicLong clock=new AtomicLong(1);RenderTimings timing=new RenderTimings(clock::get);Object profiler=new Object();
        timing.enable(profiler);timing.frame();
        timing.start(profiler,"root");timing.start(profiler,"tick");clock.addAndGet(50_000_000);timing.end(profiler);
        timing.start(profiler,"render");timing.start(profiler,"gameRenderer");
        clock.addAndGet(1_000_000);timing.start(profiler,"terrain");clock.addAndGet(4_000_000);
        timing.start(new Object(),"foreign");timing.end(new Object());
        Thread worker=new Thread(()->{timing.start(profiler,"worker");timing.end(profiler);});worker.start();worker.join();
        timing.end(profiler);clock.addAndGet(2_000_000);timing.end(profiler);timing.end(profiler);timing.end(profiler);timing.frame();
        RenderTimings.Snapshot result=timing.sample();
        assertEquals(1,result.frames);assertEquals(2,result.sections.size());
        assertEquals("root/render/gameRenderer/terrain",result.sections.get(0).name);
        assertEquals(4,result.sections.get(0).milliseconds,0);assertEquals(3,result.sections.get(1).milliseconds,0);
        assertEquals(1,result.sections.stream().mapToDouble(r->r.share).sum(),1e-10);
        assertEquals(57,result.frameMilliseconds[0],0);
    }
    @Test public void disabledPathDoesNotReadClockAndFrameHistoryIsBounded() {
        AtomicLong reads=new AtomicLong(),clock=new AtomicLong();Object profiler=new Object();
        RenderTimings timing=new RenderTimings(()->{reads.incrementAndGet();return clock.get();});
        timing.start(profiler,"root");timing.end(profiler);timing.frame();assertEquals(-1,timing.surfaceStart());assertEquals(0,reads.get());
        timing.enable(profiler);timing.frame();
        for(int i=0;i<90;i++){clock.addAndGet(16_000_000);timing.frame();}
        assertEquals(60,timing.sample().frameMilliseconds.length);
        timing.disable();long before=reads.get();timing.start(profiler,"root");timing.frame();assertEquals(before,reads.get());
        assertSame(RenderTimings.Snapshot.EMPTY,timing.sample());
    }
    @Test public void surfacesAreSeparateInclusiveMeasurementsAndFreshEnableDropsOldEvidence() {
        AtomicLong clock=new AtomicLong(100);RenderTimings timing=new RenderTimings(clock::get);Object profiler=new Object();
        timing.enable(profiler);timing.frame();long token=timing.surfaceStart();clock.addAndGet(2_000_000);timing.surfaceEnd("Diagnostics",token);timing.frame();
        RenderTimings.Snapshot result=timing.sample();assertTrue(result.sections.isEmpty());assertEquals(2,result.surfaces.get(0).milliseconds,0);
        timing.enable(profiler);timing.frame();assertTrue(timing.sample().surfaces.isEmpty());
    }
    @Test public void samplingDuringAnOpenRenderFrameNeverPublishesOrSplitsItsPartialTime() {
        AtomicLong clock=new AtomicLong(1);RenderTimings timing=new RenderTimings(clock::get);Object profiler=new Object();
        timing.enable(profiler);timing.frame();timing.start(profiler,"root");timing.start(profiler,"render");timing.start(profiler,"gameRenderer");
        timing.start(profiler,"terrain");clock.addAndGet(2_000_000);timing.end(profiler);
        assertTrue(timing.sample().sections.isEmpty());
        clock.addAndGet(3_000_000);timing.end(profiler);timing.end(profiler);timing.end(profiler);timing.frame();
        RenderTimings.Snapshot complete=timing.sample();assertEquals(1,complete.frames);
        assertEquals(5,complete.sections.stream().mapToDouble(r->r.milliseconds).sum(),0);
        assertTrue(timing.sample().sections.isEmpty());
    }
}
