package me.youngjibbit.event.network;

import me.youngjibbit.event.Event;
import me.zero.alpine.event.EventPhase;
import net.minecraft.network.NetworkManager;

/** Client thread, after the chat/action-bar GUI call returned successfully, including silent application/replay.
 * Immutable delivery snapshot, not a transport receipt. Cancelled/failed/only-scheduled handlers emit nothing.
 * Type 2 denotes action bar; SessionInfo should ignore it. Cancellation has no effect. */
public final class ClientChatDeliveredEvent extends Event {
    private final NetworkManager connection;
    private final String text;
    private final byte type;
    public ClientChatDeliveredEvent(NetworkManager connection,String text,byte type) {
        this.connection=connection;this.text=text;this.type=type;setEventPhase(EventPhase.POST);
    }
    public NetworkManager getConnection() {return connection;}
    public String getText() {return text;}
    public byte getType() {return type;}
}
