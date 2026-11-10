package me.youngjibbit.core.network;

import me.youngjibbit.event.EventFlow;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class DemiseTimingWindowTest {
    @Test public void pulseHasFixedDeadlineAndRecoveryGap() {
        DemiseTimingWindow w=new DemiseTimingWindow();
        assertEquals(DemiseTimingWindow.Decision.START,w.update(0,true,4,1.5,6,250,60,false));
        assertEquals(120,w.remainingMillis(0));
        assertEquals(DemiseTimingWindow.Decision.HOLD,w.update(70_000_000,true,4,1.5,6,250,60,false));
        assertEquals(50,w.remainingMillis(70_000_000));
        assertEquals(DemiseTimingWindow.Decision.RELEASE,w.update(120_000_000,true,4,1.5,6,250,60,false));
        assertEquals(DemiseTimingWindow.Decision.IDLE,w.update(150_000_000,true,4,1.5,6,250,60,false));
        assertEquals(DemiseTimingWindow.Decision.START,w.update(180_000_000,true,4,1.5,6,250,60,false));
    }
    @Test public void approachReleasesOnRangeEntryAndInvalidation() {
        DemiseTimingWindow w=new DemiseTimingWindow();
        assertEquals(DemiseTimingWindow.Decision.START,w.update(0,true,4,3.2,6,200,60,true));
        assertEquals(DemiseTimingWindow.Decision.RELEASE,w.update(20_000_000,true,3.1,3.2,6,200,60,true));
        w.reset();assertEquals(DemiseTimingWindow.Decision.START,w.update(-100_000_000,true,4,3.2,6,200,60,true));
        assertEquals(DemiseTimingWindow.Decision.RELEASE,w.update(-80_000_000,false,4,3.2,6,200,60,true));
    }
    @Test public void rollbackReleasesTheOldPacketOwnerBeforeAFreshCooldownAndDeadline() {
        AtomicLong clock=new AtomicLong(1_000_000_000L);
        AtomicInteger applied=new AtomicInteger();
        PacketDelays packets=new PacketDelays(clock::get);
        Object owner=new Object();
        DemiseTimingWindow w=new DemiseTimingWindow();
        assertEquals(DemiseTimingWindow.Decision.START,w.update(clock.get(),true,4,3.2,6,200,60,true));
        packets.request(owner,w.remainingMillis(clock.get()),(packet,flow)->flow==EventFlow.OUTBOUND);
        assertTrue(packets.defer(new C03PacketPlayer(),EventFlow.OUTBOUND,applied::incrementAndGet));
        clock.set(1_050_000_000L);
        assertEquals(DemiseTimingWindow.Decision.HOLD,w.update(clock.get(),true,4,3.2,6,200,60,true));
        assertEquals(1,packets.activeHeldCount(owner));
        clock.set(900_000_000L);
        DemiseTimingWindow.Decision rollback=w.update(clock.get(),true,4,3.2,6,200,60,true);
        assertEquals("the host must be told to release its old-clock owner",DemiseTimingWindow.Decision.RELEASE,rollback);
        if(rollback==DemiseTimingWindow.Decision.RELEASE)packets.release(owner);
        assertFalse(packets.hasOwner(owner));
        assertEquals(0,packets.size());
        assertEquals(1,applied.get());
        assertEquals(0,w.remainingMillis(clock.get()));
        assertEquals(DemiseTimingWindow.Decision.IDLE,w.update(959_000_000L,true,4,3.2,6,200,60,true));
        assertEquals(DemiseTimingWindow.Decision.START,w.update(960_000_000L,true,4,3.2,6,200,60,true));
        assertEquals(200,w.remainingMillis(960_000_000L));
    }
    @Test public void backtrackUsesReceivedAndVisibleBoxDistances() {
        assertTrue(DemiseBacktrackPolicy.shouldHold(3.4,2.8,2,6,3,true,true,true,false));
        assertFalse(DemiseBacktrackPolicy.shouldHold(3.4,3.1,2,6,3,true,true,true,false));
        assertFalse(DemiseBacktrackPolicy.shouldHold(4.6,2.8,2,6,3,true,true,true,false));
        assertFalse(DemiseBacktrackPolicy.shouldHold(3.4,2.8,2,6,3,true,true,false,false));
        assertTrue(DemiseBacktrackPolicy.shouldHold(3.4,2.8,2,6,3,false,false,false,false));
        assertFalse(DemiseBacktrackPolicy.shouldHold(Double.NaN,2.8,2,6,3,false,false,true,false));
    }
}
