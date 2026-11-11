package me.youngjibbit.core.module;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;

import java.util.function.Supplier;

/**
 * Opt-in lifecycle stop for a module. It only observes client-thread scene state;
 * disabling goes through the owner's existing cleanup and never re-enables it.
 */
public final class ModuleSmartDisable implements Subscriber {
    private final Module owner;
    private final Supplier<Minecraft> client;
    private final CheckBoxValue death;
    private final CheckBoxValue worldChange;
    private boolean active, seenWorld;
    private Object world, player;
    private NetworkManager connection;

    public ModuleSmartDisable(Module owner, Supplier<Minecraft> client) {
        this.owner = owner;
        this.client = client;
        // Append-only settings; old configurations retain their existing behavior.
        death = new CheckBoxValue("Smart Disable on Death", owner, false);
        worldChange = new CheckBoxValue("Smart Disable on World Change", owner, false);
        worldChange.setOnChange(value -> {
            Minecraft current = observableClient();
            if (current != null) remember(current);
        });
    }

    public void enable() {
        disable();
        active = true;
        Minecraft current = client.get();
        if (current != null) remember(current);
        Pulsar.BUS.subscribe(this);
    }

    public void disable() {
        active = false;
        Pulsar.BUS.unsubscribe(this);
        seenWorld = false;
        world = player = null;
        connection = null;
    }

    /** Missing keys in a warm legacy import must not inherit an earlier opt-in. */
    public void resetConfigDefaults() {
        death.reset();
        worldChange.reset();
    }

    private Minecraft observableClient() {
        if (!active || !owner.isToggled()) return null;
        Minecraft current = client.get();
        return current != null && current.isCallingFromMinecraftThread() ? current : null;
    }

    private static NetworkManager connection(Minecraft client) {
        return client.thePlayer == null || client.thePlayer.sendQueue == null
                ? null : client.thePlayer.sendQueue.getNetworkManager();
    }

    private void remember(Minecraft current) {
        world = current.theWorld;
        player = current.thePlayer;
        connection = connection(current);
        seenWorld = world != null;
    }

    private void check() {
        Minecraft current = observableClient();
        if (current == null) return;
        if (worldChange.getValue() && seenWorld
                && (current.theWorld != world || player != null && current.thePlayer != player
                    || connection != null && connection(current) != connection)) {
            stop();
            return;
        }
        if (death.getValue() && current.thePlayer != null
                && (current.thePlayer.isDead || !current.thePlayer.isEntityAlive())) {
            stop();
            return;
        }
        // A module enabled in a menu may enter its first world. With the option
        // off, track the current scene so a later opt-in starts from that scene.
        remember(current);
    }

    private void stop() {
        // Detach first: normal onDisable can synchronously publish other events.
        disable();
        if (owner.isToggled()) owner.setToggled(false);
    }

    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> check(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventUpdate> update = new Listener<>(event -> check(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventMotion> motion = new Listener<>(event -> check(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventRotation> rotation = new Listener<>(event -> check(), EventPriority.HIGHEST);

    private void worldChanged(WorldEvent event) {
        Minecraft current = observableClient();
        if (current == null) return;
        if (worldChange.getValue() && seenWorld && event instanceof WorldEvent.Unload
                && event.world == world) {
            stop();
            return;
        }
        check();
    }

    private void sessionChanged(PlaySessionEvent event) {
        if (observableClient() == null) return;
        if (worldChange.getValue() && seenWorld && event.getState() == PlaySessionEvent.State.ENDED
                && connection != null && event.getConnection() == connection) stop();
    }

    @Subscribe private final Listener<WorldEvent> worldEvent = new Listener<>(this::worldChanged, EventPriority.HIGHEST);
    @Subscribe private final Listener<PlaySessionEvent> session = new Listener<>(this::sessionChanged, EventPriority.HIGHEST);
}
