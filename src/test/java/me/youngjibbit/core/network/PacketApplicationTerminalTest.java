package me.youngjibbit.core.network;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.IThreadListener;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PacketApplicationTerminalTest {
    private static final class Fixture {
        final AtomicBoolean ready = new AtomicBoolean(), valid = new AtomicBoolean(true);
        final AtomicInteger success = new AtomicInteger(), applied = new AtomicInteger();
        final List<Runnable> scheduled = new ArrayList<>();
        final List<Throwable> terminals = new ArrayList<>();
        final Packet<?> packet = new S1CPacketEntityMetadata();
        final INetHandlerPlayClient[] handler = {null};
        final IThreadListener thread = new IThreadListener() {
            public boolean isCallingFromMinecraftThread() { return ready.get(); }
            public ListenableFuture<Object> addScheduledTask(Runnable runnable) { scheduled.add(runnable); return Futures.immediateFuture(null); }
        };
        Runnable body = () -> { };
        Fixture() {
            handler[0] = NetworkTestSupport.handler(arguments -> {
                PacketThreadUtil.checkThreadAndEnqueue((Packet) arguments[0], handler[0], thread);
                applied.incrementAndGet(); body.run();
            });
        }
        void start() { PacketApplication.process(packet, handler[0], valid::get, success::incrementAndGet, terminals::add); }
        void resume() { ready.set(true); scheduled.get(0).run(); }
    }

    @Test public void quickExitIsNonterminalAndActualSuccessCompletesExactlyOnce() {
        Fixture f = new Fixture(); f.start(); assertEquals(1, f.scheduled.size()); assertTrue(f.terminals.isEmpty());
        f.resume(); f.resume(); assertEquals(1, f.applied.get()); assertEquals(1, f.success.get());
        assertEquals(1, f.terminals.size()); assertNull(f.terminals.get(0));
    }
    @Test public void initialAndDelayedVetoBothTerminateWithoutApplicationOrSuccess() {
        for (boolean delayed : new boolean[]{false, true}) {
            Fixture f = new Fixture(); if (!delayed) f.valid.set(false); f.start();
            if (delayed) { f.valid.set(false); f.resume(); f.valid.set(true); f.resume(); }
            assertEquals(0, f.applied.get()); assertEquals(0, f.success.get()); assertEquals(1, f.terminals.size()); assertNull(f.terminals.get(0));
        }
    }
    @Test public void vetoAfterApplicationTerminatesWithoutReportingSuccess() {
        Fixture f = new Fixture(); f.body = () -> f.valid.set(false); f.start(); f.resume(); f.resume();
        assertEquals(1, f.applied.get()); assertEquals(0, f.success.get()); assertEquals(1, f.terminals.size()); assertNull(f.terminals.get(0));
    }
    @Test public void scheduledHandlerFailureIsReportedOnceAndTheOriginalExceptionStillPropagates() {
        Fixture f = new Fixture(); IllegalStateException failure = new IllegalStateException("scheduled handler");
        f.body = () -> { throw failure; }; f.start();
        assertSame(failure, assertThrows(IllegalStateException.class, f::resume)); f.resume();
        assertEquals(1, f.applied.get()); assertEquals(0, f.success.get()); assertEquals(1, f.terminals.size()); assertSame(failure, f.terminals.get(0));
    }
    @Test public void theExistingSignatureStillDefersSuccessAndNeverReportsItTwice() {
        Fixture f = new Fixture(); PacketApplication.process(f.packet, f.handler[0], f.valid::get, f.success::incrementAndGet);
        assertEquals(0, f.success.get()); f.resume(); f.resume(); assertEquals(1, f.success.get()); assertTrue(f.terminals.isEmpty());
    }
}
