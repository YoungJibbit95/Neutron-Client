package me.youngjibbit.core.activity;

import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

import java.lang.ref.WeakReference;

/**
 * Latest-only, passive lifecycle evidence. No module discovery, event publication,
 * game action or persistence occurs here. Readers receive scalar data only.
 */
public final class ModuleToggleJournal {
    private static long sequence;
    private static long configurationGeneration;
    private static int configurationDepth;
    private static Entry latest;

    private ModuleToggleJournal() { }

    /** A renderer resets its cursor to this value when its display context resets. */
    public static synchronized long currentSequence() { return sequence; }

    /** Bounded context/TTL validation; never scans modules or invokes their hooks. */
    public static synchronized ModuleToggleSnapshot getSnapshot() {
        Minecraft client = Minecraft.getMinecraft();
        if (!onClientThread(client)) return null;
        if (configurationDepth != 0 || latest == null) return null;
        try {
            if (latest.owner.get() == null || !latest.snapshot.isFresh(System.nanoTime())
                    || !latest.context.isCurrent(client)) {
                latest = null;
                return null;
            }
            return latest.snapshot;
        } catch (RuntimeException unavailableScene) {
            latest = null;
            return null;
        }
    }

    /** Only the existing ConfigManager application scopes call these two hooks. */
    public static synchronized void beginConfigApplication() {
        configurationDepth++;
        configurationGeneration++;
        latest = null;
    }

    public static synchronized void endConfigApplication() {
        if (configurationDepth > 0) configurationDepth--;
    }

    private static boolean onClientThread(Minecraft client) {
        return client != null && client == Minecraft.getMinecraft() && client.isCallingFromMinecraftThread();
    }

    private static synchronized Context begin(Minecraft client, Object owner, boolean configuring) {
        if (latest != null && latest.owner.get() == owner) latest = null;
        if (configuring || configurationDepth != 0 || !onClientThread(client)) return null;
        try {
            return Context.capture(client, configurationGeneration);
        } catch (RuntimeException unavailableScene) {
            return null;
        }
    }

    private static synchronized void completed(Minecraft client, Object owner, Context context,
                                                String id, String name, boolean enabled) {
        if (context == null || configurationDepth != 0 || sequence == Long.MAX_VALUE
                || context.configurationGeneration != configurationGeneration || !onClientThread(client)) return;
        try {
            if (!context.isCurrent(client)) return;
            ModuleToggleSnapshot snapshot = new ModuleToggleSnapshot(sequence + 1, System.nanoTime(), id, name, enabled);
            latest = new Entry(owner, context, snapshot);
            sequence = snapshot.getSequence();
        } catch (RuntimeException unavailableScene) {
            // Observation must not change an already completed lifecycle's result.
        }
    }

    /**
     * One private instance per Module. Nested callbacks retain the existing lifecycle
     * order, while only the outer successful net state change becomes visible.
     */
    public static final class Transition {
        private int depth;
        private boolean initialState;
        private boolean failed;
        private Context context;

        public void begin(Minecraft client, Object owner, boolean previousState, boolean configuring) {
            if (depth++ != 0) return;
            initialState = previousState;
            failed = false;
            context = ModuleToggleJournal.begin(client, owner, configuring);
        }

        public void end(Minecraft client, Object owner, String id, String name, boolean state, boolean successful) {
            if (!successful) failed = true;
            if (--depth != 0) return;
            Context completedContext = context;
            context = null;
            if (!failed && state != initialState)
                ModuleToggleJournal.completed(client, owner, completedContext, id, name, state);
        }
    }

    private static final class Entry {
        final WeakReference<Object> owner;
        final Context context;
        final ModuleToggleSnapshot snapshot;
        Entry(Object owner, Context context, ModuleToggleSnapshot snapshot) {
            this.owner = new WeakReference<>(owner);
            this.context = context;
            this.snapshot = snapshot;
        }
    }

    /** Private weak identities do not retain a world, player, module or connection. */
    private static final class Context {
        final WeakReference<Minecraft> client;
        final WeakReference<EntityPlayerSP> player;
        final WeakReference<WorldClient> world;
        final WeakReference<NetHandlerPlayClient> handler;
        final WeakReference<NetworkManager> network;
        final WeakReference<PacketPipeline> pipeline;
        final long packetEpoch;
        final long configurationGeneration;

        private Context(Minecraft client, EntityPlayerSP player, WorldClient world,
                        NetHandlerPlayClient handler, NetworkManager network, PacketPipeline pipeline,
                        long configurationGeneration) {
            this.client = new WeakReference<>(client);
            this.player = new WeakReference<>(player);
            this.world = new WeakReference<>(world);
            this.handler = new WeakReference<>(handler);
            this.network = new WeakReference<>(network);
            this.pipeline = new WeakReference<>(pipeline);
            this.packetEpoch = pipeline.getContextVersion();
            this.configurationGeneration = configurationGeneration;
        }

        static Context capture(Minecraft client, long configurationGeneration) {
            EntityPlayerSP player = client.thePlayer;
            WorldClient world = client.theWorld;
            NetHandlerPlayClient handler = player == null ? null : player.sendQueue;
            NetworkManager network = handler == null ? null : handler.getNetworkManager();
            PacketPipeline pipeline = network == null ? null : network.getClientPackets();
            if (player == null || world == null || handler == null || network == null || pipeline == null) return null;
            Context context = new Context(client, player, world, handler, network, pipeline, configurationGeneration);
            return context.isCurrent(client) ? context : null;
        }

        boolean isCurrent(Minecraft observedClient) {
            EntityPlayerSP expectedPlayer = player.get();
            WorldClient expectedWorld = world.get();
            NetHandlerPlayClient expectedHandler = handler.get();
            NetworkManager expectedNetwork = network.get();
            PacketPipeline expectedPipeline = pipeline.get();
            return client.get() == observedClient && expectedPlayer != null && expectedWorld != null
                    && expectedHandler != null && expectedNetwork != null && expectedPipeline != null
                    && observedClient.thePlayer == expectedPlayer && observedClient.theWorld == expectedWorld
                    && expectedPlayer.worldObj == expectedWorld && !expectedPlayer.isDead && expectedPlayer.isEntityAlive()
                    && expectedPlayer.sendQueue == expectedHandler && expectedHandler.getNetworkManager() == expectedNetwork
                    && expectedNetwork.getClientPackets() == expectedPipeline && expectedPipeline.isActive()
                    && expectedPipeline.getContextVersion() == packetEpoch;
        }
    }
}
