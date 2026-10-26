package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.network.ClientChatDeliveredEvent;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;

/** One session per play connection; only successfully applied chat can update statistics. */
final class SessionInfoLifecycle implements Subscriber {
    final SessionStatistics statistics;
    private NetworkManager connection;
    private final java.util.function.Supplier<String> username;
    SessionInfoLifecycle() { this(() -> Minecraft.getMinecraft()==null ? null : Minecraft.getMinecraft().getSession().getUsername()); }
    SessionInfoLifecycle(java.util.function.Supplier<String> username) { this(username, System::nanoTime); }
    SessionInfoLifecycle(java.util.function.Supplier<String> username, java.util.function.LongSupplier clock) {
        this.username=username;
        this.statistics=new SessionStatistics(clock);
    }
    private boolean installed;
    synchronized void install() { if (!installed) { Pulsar.BUS.subscribe(this); installed=true; } }

    private String username() { return username.get(); }

    @Subscribe private final Listener<PlaySessionEvent> sessions = new Listener<>(this::onSession);
    private void onSession(PlaySessionEvent event) {
        if (event.getState() == PlaySessionEvent.State.STARTED) {
            connection = event.getConnection();
            statistics.observe(connection, true);
        } else if (connection == event.getConnection()) {
            statistics.observe(null,false);
            connection=null;
        }
    }
    @Subscribe private final Listener<ClientChatDeliveredEvent> chat = new Listener<>(this::onChat);
    private void onChat(ClientChatDeliveredEvent event) {
        if (connection == event.getConnection() && event.getType()!=2)
            statistics.chat(event.getText(), username(), statistics.generation());
    }
}
