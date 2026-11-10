package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GuardedTransportAcceptanceTest {
    private Packet<?> packet() { return new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
            BlockPos.ORIGIN, EnumFacing.DOWN); }
    private PacketPipeline pipeline(NetworkTestSupport.MainThread main) {
        return new PacketPipeline(main, () -> true, ignored -> { }, error -> { throw new AssertionError(error); });
    }

    @Test public void callbackDistinguishesPreRejectionFromPostFailure() {
        for (int stage = 0; stage < 4; stage++) {
            final int selected = stage; AtomicInteger transported = new AtomicInteger(), accepted = new AtomicInteger();
            PacketPipeline p = pipeline(new NetworkTestSupport.MainThread());
            NetworkTestSupport.Observer hooks = new NetworkTestSupport.Observer();
            hooks.action = event -> {
                if (event.isPre() && selected == 0) event.cancel();
                if (event.isPre() && selected == 1) throw new IllegalStateException("PRE");
                if (event.isPost()) {
                    assertEquals("observation precedes POST", 1, accepted.get());
                    if (selected == 2) throw new IllegalStateException("POST");
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                try {
                    Packet<?> packet = packet();
                    Packet<?> result = p.outboundSelected(packet, false, ignored -> transported.incrementAndGet(),
                            ignored -> true, ignored -> accepted.incrementAndGet());
                    if (selected == 0) assertNull(result); else assertSame(packet, result);
                    assertTrue(selected == 0 || selected == 3);
                } catch (IllegalStateException expected) { assertTrue(selected == 1 || selected == 2); }
                assertEquals(selected >= 2 ? 1 : 0, transported.get());
                assertEquals(transported.get(), accepted.get());
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void transportFailureAndDeniedGuardNeverReportAcceptance() {
        PacketPipeline p = pipeline(new NetworkTestSupport.MainThread()); AtomicInteger accepted = new AtomicInteger();
        assertNull(p.outboundSelected(packet(), false, ignored -> fail(), ignored -> false, ignored -> accepted.incrementAndGet()));
        try { p.outboundSelected(packet(), false, ignored -> { throw new IllegalStateException("transport"); },
                ignored -> true, ignored -> accepted.incrementAndGet()); fail(); }
        catch (IllegalStateException expected) { assertEquals(0, accepted.get()); }
    }

    @Test public void schedulingIsNotAcceptanceAndMainThreadDeliveryReportsExactlyOnce() throws Exception {
        NetworkTestSupport.MainThread main = new NetworkTestSupport.MainThread(); PacketPipeline p = pipeline(main);
        AtomicInteger accepted = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Packet<?>> result = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> result.set(p.outboundSelected(packet(), true, ignored -> { },
                ignored -> true, ignored -> accepted.incrementAndGet())));
        worker.start(); worker.join(); assertNull(result.get()); assertEquals(0, accepted.get());
        main.drain(); assertEquals(1, accepted.get()); main.drain(); assertEquals(1, accepted.get());
    }
}
