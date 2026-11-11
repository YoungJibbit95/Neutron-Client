package me.youngjibbit.module.impl.client;

import me.youngjibbit.core.diagnostics.NetworkResearchTrace;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.*;
import me.youngjibbit.core.diagnostics.NetworkResearchSimulation;
import me.youngjibbit.core.diagnostics.NetworkResearchRecording;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.*;
import net.minecraft.util.Vec3;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Passive event subscriber. Never owns, queues, changes, releases or sends a packet or player step. */
@ModuleInfo(name="NetworkResearch", description="Passive timing/history profiles and offline network simulations", category=Category.Client)
public final class NetworkResearch extends Module {
    private static final Logger LOG = LogManager.getLogger(NetworkResearch.class);
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Backtrack", "Lagrange", "Tickbase"}, 0);
    private final ComboValue source = new ComboValue("Source", this, new String[]{"Observe", "Simulation"}, 0);
    private final ComboValue scenario = new ComboValue("Scenario", this, new String[]{"Baseline", "Jitter", "Burst", "Pause"}, 0);
    private final CheckBoxValue console = new CheckBoxValue("Console Summary", this, false);
    private final NetworkResearchTrace trace = new NetworkResearchTrace(512);
    private final NetworkResearchSimulation simulation = new NetworkResearchSimulation();
    private final Minecraft client;
    private final LongSupplier clock;
    private WeakReference<Object> pipeline = new WeakReference<>(null), world = new WeakReference<>(null),
            player = new WeakReference<>(null), connection = new WeakReference<>(null);
    private long epoch = -1, loggedAt, failures;
    private boolean logged;

    public NetworkResearch() { this(System::nanoTime, Minecraft.getMinecraft()); }
    public NetworkResearch(LongSupplier clock, Minecraft client) {
        this.clock = Objects.requireNonNull(clock, "clock"); this.client = client;
        scenario.setVisibility(module -> "Simulation".equals(source.getValue()));
    }
    private Mode profile() { return Mode.valueOf(mode.getValue().toUpperCase(Locale.ROOT)); }
    private boolean observing() { return isToggled() && "Observe".equals(source.getValue()); }

    @Override public void onEnable() { reset(); if (!observing()) simulate(); super.onEnable(); }
    @Override public void onDisable() { super.onDisable(); reset(); }
    @Override public void onValueChange() {
        if (trace != null && clock != null) { reset(); if (isToggled() && !observing()) simulate(); }
    }
    private void simulate() {
        simulation.run(NetworkResearchSimulation.Scenario.valueOf(scenario.getValue().toUpperCase(Locale.ROOT)), profile(), trace);
    }
    private void reset() {
        trace.startRun(profile(), Origin.CLIENT_OBSERVATION); simulation.reset();
        pipeline.clear(); world.clear(); player.clear(); connection.clear(); epoch = -1;
        logged = false; loggedAt = 0; failures = 0;
    }
    private PacketPipeline currentPipeline() {
        return client == null || client.thePlayer == null || client.theWorld == null || client.getNetHandler() == null
                ? null : client.getNetHandler().getNetworkManager().getClientPackets();
    }
    private boolean context(PacketPipeline candidate) {
        PacketPipeline current = currentPipeline();
        if (candidate == null || candidate != current || !candidate.isActive()) return false;
        Object network = client.getNetHandler().getNetworkManager();
        if (pipeline.get() != current || world.get() != client.theWorld || player.get() != client.thePlayer
                || connection.get() != network || epoch != current.getContextVersion()) {
            reset(); pipeline = new WeakReference<>(current); world = new WeakReference<>(client.theWorld);
            player = new WeakReference<>(client.thePlayer); connection = new WeakReference<>(network);
            epoch = current.getContextVersion();
        }
        return true;
    }
    private boolean includes(Packet<?> packet) {
        return profile() == Mode.LAGRANGE || packet instanceof S08PacketPlayerPosLook
                || profile() == Mode.BACKTRACK && (packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport)
                || profile() == Mode.TICKBASE && (packet instanceof C03PacketPlayer || packet instanceof S03PacketTimeUpdate);
    }
    private void capture(Packet<?> packet, Kind kind) {
        if (!includes(packet)) return;
        long now = clock.getAsLong();
        trace.packet(now, kind, packet.getClass().getSimpleName());
        if (packet instanceof S08PacketPlayerPosLook) trace.packet(now, Kind.CORRECTION, "S08PacketPlayerPosLook");
        if (profile() != Mode.BACKTRACK || client.theWorld == null) return;
        int id = packet instanceof S14PacketEntity ? ((S14PacketEntity) packet).getEntityId()
                : packet instanceof S18PacketEntityTeleport ? ((S18PacketEntityTeleport) packet).getEntityId() : -1;
        Entity entity = client.theWorld.getEntityByID(id);
        if (entity == null) return;
        Vec3 received = ((PacketPipeline) pipeline.get()).receivedPosition(entity);
        if (received != null) trace.entity(now, id, received.xCoord, received.yCoord, received.zCoord,
                entity.posX, entity.posY, entity.posZ);
    }
    // Instrumentation failures must not change packet handling or game state.
    private void observe(Runnable capture) {
        if (!observing()) return;
        try { capture.run(); } catch (RuntimeException failure) { failures++; }
    }
    @Subscribe private final Listener<PacketReceiveEvent> received = new Listener<>(event -> observe(() -> {
        if (context(event.getPipeline())) capture(event.getPacket(), Kind.RECEIVED);
    }));
    @Subscribe private final Listener<EventPacket> applied = new Listener<>(event -> {
        if (event.isPost()) observe(() -> {
            if (context(event.getPipeline())) capture(event.getPacket(), event.isInbound() ? Kind.APPLIED : Kind.LOCAL_ACCEPTED);
        });
    });
    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> {
        if (event.isPre()) observe(this::sampleTick);
    });
    private void sampleTick() {
            PacketPipeline pipe = currentPipeline();
            if (!context(pipe)) { if (pipeline.get() != null) reset(); return; }
            long now = clock.getAsLong();
            // TickEvent occurs before player simulation. -1 means no completed-step measurement at this hook.
            trace.tick(now, -1, false);
            PacketDelays.Diagnostics queue = pipe.delays().diagnosticSnapshot();
            trace.queue(now, queue.inbound, queue.outbound, queue.oldestMs);
            if (console.getValue() && (!logged || now - loggedAt >= 1_000_000_000L)) {
                LOG.info("NETWORK_RESEARCH run={} mode={} origin=CLIENT_OBSERVATION samples={} overwritten={} rejected={} captureFailures={} serverFlags=UNKNOWN",
                        trace.getRun(), profile(), trace.snapshot().size(), trace.getOverwritten(), trace.getRejected(), failures);
                logged = true; loggedAt = now;
            }
    }
    @Subscribe private final Listener<WorldEvent> worldChanged = new Listener<>(event -> { if (observing()) reset(); });
    @Subscribe private final Listener<PlaySessionEvent> session = new Listener<>(event -> {
        if (observing() && event.getState() == PlaySessionEvent.State.ENDED && connection.get() == event.getConnection()) reset();
    });

    public List<Entry> snapshot() { return trace.snapshot(); }
    public long getOverwritten() { return trace.getOverwritten(); }
    public long getCaptureFailures() { return failures; }
    public void writeJsonLines(Writer destination) throws IOException { trace.writeJsonLines(destination); }
    /** Capture metadata and scalars at one explicit client-thread inspection boundary. */
    public NetworkResearchRecording recording() {
        // Disabled modules have an empty observation run, even when Source still selects Simulation.
        boolean simulated = isToggled() && "Simulation".equals(source.getValue());
        return trace.recording(failures, simulated ? scenario.getValue().toUpperCase(Locale.ROOT) : "",
                simulated ? simulation.getRejected() : 0);
    }
    public void recordServerFlag(String evidenceReference, String check) {
        if (!observing() || !context(currentPipeline())) throw new IllegalStateException("active observed session required");
        trace.serverFlag(clock.getAsLong(), evidenceReference, check);
    }
}
