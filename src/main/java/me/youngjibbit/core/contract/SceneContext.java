package me.youngjibbit.core.contract;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Read-only observation of a scene, not a lifecycle manager. Identity references
 * are weak so retaining a historical snapshot cannot retain a player or world.
 * The scene owner must keep the current objects alive during validation.
 */
public final class SceneContext {
    public enum Availability { ACTIVE, SUSPENDED, INVALIDATED }

    private final WeakReference<Object> connection;
    private final WeakReference<Object> player;
    private final WeakReference<Object> world;
    private final long connectionGeneration;
    private final long packetContextEpoch;
    private final Availability availability;

    /** Missing identities or negative versions describe an invalid observation. */
    public SceneContext(Object connection, Object player, Object world,
                        long connectionGeneration, long packetContextEpoch, Availability availability) {
        this.connection = new WeakReference<>(connection);
        this.player = new WeakReference<>(player);
        this.world = new WeakReference<>(world);
        this.connectionGeneration = connectionGeneration;
        this.packetContextEpoch = packetContextEpoch;
        this.availability = Objects.requireNonNull(availability, "availability");
    }

    public long getConnectionGeneration() { return connectionGeneration; }
    public long getPacketContextEpoch() { return packetContextEpoch; }
    public Availability getAvailability() { return availability; }
    public boolean isSuspended() { return availability == Availability.SUSPENDED; }
    public boolean isInvalidated() { return availability == Availability.INVALIDATED; }

    /** Suspension preserves scene validity, but forbids synthetic application. */
    public boolean isValid() {
        return !isInvalidated() && connectionGeneration >= 0 && packetContextEpoch >= 0
                && connection.get() != null && player.get() != null && world.get() != null;
    }

    /**
     * Compares live reference identities and both versions, independently of
     * availability. This is not Object.equals: incomplete/expired identities
     * never match, even themselves. Availability must be checked separately.
     */
    public boolean sameSceneAs(SceneContext other) {
        if (other == null || connectionGeneration < 0 || packetContextEpoch < 0
                || connectionGeneration != other.connectionGeneration
                || packetContextEpoch != other.packetContextEpoch) return false;
        Object liveConnection = connection.get();
        Object livePlayer = player.get();
        Object liveWorld = world.get();
        return liveConnection != null && livePlayer != null && liveWorld != null
                && liveConnection == other.connection.get()
                && livePlayer == other.player.get()
                && liveWorld == other.world.get();
    }
}
