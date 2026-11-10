package me.youngjibbit.core.network;

import me.youngjibbit.event.EventFlow;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class PacketDelayDiagnosticsTest {
    @Test public void activityAgeBelongsToItsOwnerAndExpiredHoldsAreNotReportedAsActive(){
        AtomicLong clock=new AtomicLong();AtomicInteger applied=new AtomicInteger();
        PacketDelays delays=new PacketDelays(clock::get);Object first=new Object(),second=new Object();
        delays.request(first,200,(p,f)->true);
        delays.defer(new C03PacketPlayer(),EventFlow.INBOUND,applied::incrementAndGet);
        clock.set(50_000_000);
        delays.request(second,100,(p,f)->f==EventFlow.OUTBOUND);
        delays.defer(new C03PacketPlayer(),EventFlow.OUTBOUND,applied::incrementAndGet);
        clock.set(84_000_000);
        PacketDelays.Snapshot snap=delays.snapshot(second);
        assertEquals(84,snap.oldestAgeMs);assertEquals(34,snap.ownerOldestAgeMs);assertEquals(1,snap.liveHeldByOwner);
        assertTrue(snap.ownerActive);assertEquals(0,applied.get());
        clock.set(151_000_000);
        snap=delays.snapshot(second);assertEquals(0,snap.liveHeldByOwner);assertEquals(1,snap.heldByOwner);
        assertEquals("observation must never pump",0,applied.get());assertEquals(2,delays.size());
        clock.set(301_000_000);assertFalse(delays.snapshot(second).ownerActive);
        assertTrue("observation does not expire a gameplay request",delays.hasOwner(second));
    }
    @Test public void observesDirectionsAndRemainingHoldWithoutTestingMatchersOrPumping() {
        AtomicLong clock=new AtomicLong();AtomicInteger matches=new AtomicInteger(),applied=new AtomicInteger();
        PacketDelays delays=new PacketDelays(clock::get);Object owner=new Object();
        delays.request(owner,100,(p,f)->{matches.incrementAndGet();return true;});
        delays.defer(new C03PacketPlayer(),EventFlow.OUTBOUND,applied::incrementAndGet);
        clock.set(20_000_000);PacketDelays.Diagnostics a=delays.diagnosticSnapshot();
        assertEquals(1,a.outbound);assertEquals(0,a.inbound);assertEquals(20,a.oldestMs);assertEquals(80,a.remainingMs);assertEquals(100,a.configuredMs);
        clock.set(500_000_000);PacketDelays.Diagnostics b=delays.diagnosticSnapshot();
        assertEquals(0,b.owners);assertEquals(0,b.remainingMs);assertEquals(1,b.outbound);
        assertEquals(1,matches.get());assertEquals(0,applied.get());assertTrue(delays.hasOwner(owner));
        delays.pump();assertEquals(1,applied.get());
    }
}
