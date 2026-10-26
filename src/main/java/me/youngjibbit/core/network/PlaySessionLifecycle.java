package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.Event;
import me.youngjibbit.event.network.*;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IThreadListener;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/** One connection's confirmed play lifetime, independent of the world's delay generation and HUD visibility. */
public final class PlaySessionLifecycle {
    private final NetworkManager connection;
    private final IThreadListener thread;
    private final AtomicBoolean endRequested=new AtomicBoolean();
    private final ArrayDeque<Event> notifications=new ArrayDeque<>();
    private boolean started,emitting;
    private volatile boolean active;
    private int applicationDepth;
    // Connection lifetime, including pipeline reconfiguration. Weak keys release replay payloads when no caller retains them.
    private final java.util.Set<S02PacketChat> deliveredChats=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<S02PacketChat,Boolean>());
    private final java.util.Set<S02PacketChat> applyingChats=new java.util.HashSet<>();
    public PlaySessionLifecycle(NetworkManager connection,IThreadListener thread) {this.connection=connection;this.thread=thread;}
    public NetworkManager getConnection() {return connection;}
    public boolean isActive() {return active;}
    public void confirmStarted() {
        requireMain();
        if(started || endRequested.get())return;
        started=true;active=true;emit(new PlaySessionEvent(connection,PlaySessionEvent.State.STARTED));
    }
    /** May be called by Netty. Ending never depends on the closed packet handoff being drained. */
    public void requestEnd() {
        if(!endRequested.compareAndSet(false,true))return;
        if(thread.isCallingFromMinecraftThread())finishEnd();else thread.addScheduledTask(this::finishEnd);
    }
    private void finishEnd() {
        requireMain();
        if(endRequested.get() && active && applicationDepth==0) {
            active=false;emit(new PlaySessionEvent(connection,PlaySessionEvent.State.ENDED));
        }
    }
    /** Wrap the real GUI application, not PRE. Quick-exit continuations reach this method only on their actual run. */
    public void deliverChat(S02PacketChat packet,Runnable application) {
        requireMain();
        if(deliveredChats.contains(packet) || !applyingChats.add(packet))return;
        applicationDepth++;
        try {
            String text=packet.getChatComponent().getUnformattedText();
            byte type=packet.getType();
            application.run();
            // Mark before notifying listeners, even if application changed the world or closed the transport.
            deliveredChats.add(packet);
            emit(new ClientChatDeliveredEvent(connection,text,type));
        } finally {applyingChats.remove(packet);applicationDepth--;finishEnd();}
    }
    private void emit(Event event) {
        if(notifications.size()>=64)throw new IllegalStateException("Play lifecycle notification limit exceeded");
        notifications.add(event);
        if(emitting)return;
        emitting=true;
        try {
            for(int i=0;i<64 && !notifications.isEmpty();i++)Pulsar.BUS.post(notifications.poll());
            if(!notifications.isEmpty())throw new IllegalStateException("Play lifecycle dispatch limit exceeded");
        } finally {emitting=false;notifications.clear();}
    }
    private void requireMain() {
        if(!thread.isCallingFromMinecraftThread())throw new IllegalStateException("Play lifecycle requires client thread");
    }
}
