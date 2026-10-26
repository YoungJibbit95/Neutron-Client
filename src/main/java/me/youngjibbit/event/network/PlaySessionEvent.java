package me.youngjibbit.event.network;

import me.youngjibbit.event.Event;
import me.zero.alpine.event.EventPhase;
import net.minecraft.network.NetworkManager;

/** Client-thread notification. STARTED follows successful JoinGame application; ENDED follows connection termination.
 * The NetworkManager is the stable identity. World load/unload, respawn and dimension changes do not end a session.
 * Cancellation has no effect. Each connection emits at most one STARTED and, if started, one ENDED. */
public final class PlaySessionEvent extends Event {
    public enum State { STARTED, ENDED }
    private final NetworkManager connection;
    private final State state;
    public PlaySessionEvent(NetworkManager connection, State state) {
        this.connection=connection;this.state=state;setEventPhase(EventPhase.POST);
    }
    public NetworkManager getConnection() {return connection;}
    public State getState() {return state;}
}
