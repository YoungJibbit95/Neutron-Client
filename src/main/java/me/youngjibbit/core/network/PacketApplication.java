package me.youngjibbit.core.network;

import net.minecraft.network.*;
import net.minecraft.util.IThreadListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Carries completion across PacketThreadUtil's quick exit without a premature or duplicate POST. */
public final class PacketApplication {
    private static final ThreadLocal<Application> CURRENT = new ThreadLocal<>();
    private PacketApplication() { }

    public static void process(Packet<?> packet, INetHandler handler, BooleanSupplier valid, Runnable success) {
        new Application(packet, handler, valid, success).run();
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
        final AtomicBoolean complete = new AtomicBoolean();
        Application(Packet packet, INetHandler handler, BooleanSupplier valid, Runnable success) {
            this.packet = packet; this.handler = handler; this.valid = valid; this.success = success;
        }
        @SuppressWarnings("unchecked") public void run() {
            if (complete.get() || !valid.getAsBoolean()) return;
            Application previous = CURRENT.get();
            CURRENT.set(this);
            try {
                packet.processPacket(handler);
                if (complete.compareAndSet(false, true) && valid.getAsBoolean()) success.run();
            } catch (ThreadQuickExitException scheduled) {
                // PacketThreadUtil owns the continuation, not successful completion.
            } finally {
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            }
        }
    }
}
