package me.youngjibbit.core.network;

import me.youngjibbit.event.EventFlow;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class PacketDelayDiagnosticsTest {
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
