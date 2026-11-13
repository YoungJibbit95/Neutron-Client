package me.youngjibbit.core.network;

import net.minecraft.network.*;
import net.minecraft.util.IThreadListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Carries completion across PacketThreadUtil's quick exit without a premature or duplicate POST. */
public final class PacketApplication {
    private static final ThreadLocal<Application> CURRENT = new ThreadLocal<>();
    private PacketApplication() { }

    public static void process(Packet<?> packet, INetHandler handler, BooleanSupplier valid, Runnable success) {
        new Application(packet, handler, valid, success, null).run();
    }

    /** Optional terminal observation for an owned envelope; a QuickExit alone is not terminal. */
    public static void process(Packet<?> packet, INetHandler handler, BooleanSupplier valid, Runnable success,
                               Consumer<Throwable> terminal) {
        new Application(packet, handler, valid, success, terminal).run();
    }

    public static boolean enqueue(Packet<?> packet, INetHandler handler, IThreadListener thread) {
        Application current = CURRENT.get();
        if (current == null || current.packet != packet || current.handler != handler) return false;
        thread.addScheduledTask(current);
        return true;
    }

    private static final class Application implements Runnable {
        final Packet packet;
        final INetHandler handler;
        final BooleanSupplier valid;
        final Runnable success;
        final Consumer<Throwable> terminal;
        final AtomicBoolean complete = new AtomicBoolean();
        final AtomicBoolean terminated;
        Application(Packet packet, INetHandler handler, BooleanSupplier valid, Runnable success, Consumer<Throwable> terminal) {
            this.packet = packet; this.handler = handler; this.valid = valid; this.success = success; this.terminal = terminal;
            this.terminated = terminal == null ? null : new AtomicBoolean();
        }
        @SuppressWarnings("unchecked") public void run() {
            if (complete.get()) return;
            try {
                if (!valid.getAsBoolean()) { terminate(null); return; }
            } catch (RuntimeException | Error failure) { terminate(failure); throw failure; }
            Application previous = CURRENT.get();
            CURRENT.set(this);
            try {
                packet.processPacket(handler);
                if (complete.compareAndSet(false, true) && valid.getAsBoolean()) success.run();
                terminate(null);
            } catch (ThreadQuickExitException scheduled) {
                // PacketThreadUtil owns the continuation, not successful completion.
                if (complete.get()) terminate(scheduled);
            } catch (RuntimeException | Error failure) {
                terminate(failure); throw failure;
            } finally {
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            }
        }
        private void terminate(Throwable failure) {
            if (terminal == null || !terminated.compareAndSet(false, true)) return;
            complete.set(true);
            try { terminal.accept(failure); }
            catch (RuntimeException | Error callbackFailure) {
                if (failure == null) throw callbackFailure;
                if (callbackFailure != failure) failure.addSuppressed(callbackFailure);
            }
        }
    }
}
