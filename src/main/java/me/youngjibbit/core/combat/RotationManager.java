package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class RotationManager {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RaycastService rays=new RaycastService(mc);
    private final Map<String, RotationState> states = new HashMap<>();
    private final Map<String,Long> trajectoryEpochs = new HashMap<>();
    private final LongSupplier clock;
    private final long seed;
    public RotationManager() { this(System::nanoTime,ThreadLocalRandom.current().nextLong()); }
    public RotationManager(LongSupplier clock,long seed) { this.clock=clock;this.seed=seed; }
    public static final int AIM_ASSIST_PRIORITY = 10;
    public static final int AURA_PRIORITY = 20;
    public static final int SCAFFOLD_PRIORITY = 30;
    public static final String SCAFFOLD_RELEASE_SOURCE = "scaffold-release";
    public static final String AURA_RELEASE_SOURCE = "aura-release";
    private boolean collecting, accepted, previousAccepted, hasEffective;
    private long sequence;
    private long curveMotionSequence=Long.MIN_VALUE,curveMotionNanos;
    private float baseYaw, basePitch, effectiveYaw, effectivePitch;
    private String owner;
    private WeakReference<Object> connection = new WeakReference<>(null);
    public void bindConnection(Object current) {
        if (connection.get()!=current) { reset(); connection=new WeakReference<>(current); }
    }
    public boolean isConnection(Object current) { return connection.get()==current; }
    public void invalidateConnection(Object current) { if (isConnection(current)) { reset(); connection.clear(); } }
    private Request winner;
    private Request resolvedRequest;
    private Object resolvedFrame;
    private ResolvedC03SendTicket resolvedTicket;
    private AcceptedRotationBasis releaseAcceptance;
    private RotationReleaseLease rotationRelease;
    private final java.util.Set<String> offeredSources = new java.util.HashSet<>();
    private boolean cameraIntent;
    private WeakReference<Object> motionPlayer = new WeakReference<>(null);
    private WeakReference<Object> motionWorld = new WeakReference<>(null);

    public void beginMotion(Object player, Object world, float cameraYaw, float cameraPitch) {
        if (motionPlayer.get() != player || motionWorld.get() != world) {
            reset();
            motionPlayer = new WeakReference<>(player);
            motionWorld = new WeakReference<>(world);
        }
        beginMotion(cameraYaw, cameraPitch);
    }

    public void beginMotion(float cameraYaw, float cameraPitch) {
        // Keep the observed packet values for finishMotion's rejection, but never smooth from them.
        boolean usableBase = hasEffective && Float.isFinite(effectiveYaw) && Float.isFinite(effectivePitch)
                && effectivePitch >= -90 && effectivePitch <= 90;
        baseYaw = usableBase ? effectiveYaw : cameraYaw;
        basePitch = usableBase ? effectivePitch : cameraPitch;
        collecting = true;
        previousAccepted = accepted;
        accepted = false;
        winner = null;
        resolvedRequest = null;
        resolvedFrame = null;
        resolvedTicket = null;
        offeredSources.clear();
        cameraIntent = false;
        sequence++;
    }

    public void request(String source, int priority, float yaw, float pitch) {
        offer(new Request(source,priority,yaw,pitch,false,0,0,()->true));
    }

    public void requestVisible(String source,int priority,float yaw,float pitch,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
        offer(new Request(source,priority,yaw,pitch,true,cameraYaw,cameraPitch,valid));
    }

    private void offer(Request request) {
        if (!collecting || request.source==null || isReleaseSource(request.source)
                || !Float.isFinite(request.yaw) || !Float.isFinite(request.pitch)) return;
        offeredSources.add(request.source);
        cancelRotationRelease();
        request.pitch=clamp(request.pitch,-90,90);
        if (winner==null || request.priority>winner.priority
                || (request.priority==winner.priority && request.source.compareTo(winner.source)<0)) winner=request;
    }

    public void resolve(EventMotion event) {
        final long resolvingSequence = sequence;
        resolvedRequest = null;
        resolvedFrame = null;
        resolvedTicket = null;
        RotationReleaseLease release = currentRotationRelease();
        if (sequence != resolvingSequence || !collecting) return;
        if (winner == null && release != null && !competingIntent(release.basis.source)) {
            RuntimeObservation observation = release.basis.observation.get();
            if (collecting && observation != null && observation.getActiveOuterTickId() >= 0) {
                float progress = release.progress(observation.getOuterTickId());
                float yaw = RotationReturnCurve.angle(release.yaw, event.getYaw(), progress);
                float pitch = release.pitch + (clamp(event.getPitch(), -90, 90) - release.pitch) * progress;
                float[] next = quantize(yaw, pitch, baseYaw, basePitch, mc.gameSettings.mouseSensitivity);
                if (rotationRelease == release && sequence == resolvingSequence && collecting && winner == null)
                    winner = new Request(release.releaseSource, 0, next[0], next[1], false, 0, 0, () -> true);
            }
        }
        if (winner==null) return;
        if (winner.visible) {
            String confirmed=getConfirmedOwner();
            if (mc==null || mc.thePlayer==null || motionPlayer.get()!=mc.thePlayer || motionWorld.get()!=mc.theWorld
                    || mc.currentScreen!=null || !mc.inGameHasFocus || mc.getRenderViewEntity()!=mc.thePlayer
                    || "aura".equals(confirmed) || "scaffold".equals(confirmed) || !winner.valid.getAsBoolean()) {
                winner=null;return;
            }
            // Add assistance to the latest camera; a later manual mouse delta must survive arbitration.
            mc.thePlayer.rotationYaw+=MathHelper.wrapAngleTo180_float(winner.yaw-winner.cameraYaw);
            mc.thePlayer.rotationPitch=clamp(mc.thePlayer.rotationPitch+winner.pitch-winner.cameraPitch,-90,90);
            winner.yaw=mc.thePlayer.rotationYaw;winner.pitch=mc.thePlayer.rotationPitch;
        }
        event.setRotations(winner.yaw,winner.pitch);
        if ("scaffold".equals(winner.source) || "aura".equals(winner.source) || isReleaseSource(winner.source)) {
            resolvedRequest = winner;
            resolvedFrame = new Object();
            // Bind the resolve lifetime now; a later getter cannot rebind it to another tick/scene.
            resolvedTicket = new ResolvedC03SendTicket(resolvedRequest, resolvedFrame, Float.NaN, Float.NaN);
        }
    }

    /** Capture immediately after resolve, before constructing this frame's C03.
     * Aura's ordinary packets retain their existing replay semantics. Their receipt is
     * used only internally after synchronous acceptance to arm a later return.
     * Scaffold and return packets retain the strict immutable transport ticket.
     */
    public ResolvedC03SendTicket captureResolvedC03SendTicket(float wireYaw, float wirePitch) {
        ResolvedC03SendTicket captured = resolvedTicket;
        return captured == null || "aura".equals(captured.request.source) ? null : new ResolvedC03SendTicket(captured, wireYaw, wirePitch);
    }

    /** Arms rotation only from the original scene of a real accepted Scaffold frame.
     * The lifecycle predicate is transient: no disabled module listener or callback is retained.
     */
    public boolean beginScaffoldRelease(BooleanSupplier lifetimeValid) {
        return beginRotationRelease("scaffold", 3, MoveFixController.Mode.OFF, BodyRotationController.Mode.VANILLA, lifetimeValid);
    }

    public static boolean isReleaseSource(String source) {
        return SCAFFOLD_RELEASE_SOURCE.equals(source) || AURA_RELEASE_SOURCE.equals(source);
    }

    private boolean competingIntent(String source) {
        return cameraIntent || offeredSources.stream().anyMatch(offered -> !source.equals(offered));
    }

    /** The immutable accepted receipt is never replaced by a fresh scene at disable time. */
    public boolean beginRotationRelease(String source, int ticks, MoveFixController.Mode movement,
                                        BodyRotationController.Mode body, BooleanSupplier lifetimeValid) {
        if (!"scaffold".equals(source) && !"aura".equals(source)) return false;
        final AcceptedRotationBasis basis = releaseAcceptance;
        final long motionSequence = sequence;
        if (lifetimeValid == null || !lifetimeValid.getAsBoolean() || basis == null
                || !source.equals(basis.source) || !canUseRotation(source, collecting)
                || collecting && competingIntent(source)) return false;
        final RuntimeObservation observation = basis.observation.get();
        if (observation == null || !basis.isCurrent()) return false;
        final long outerTick = observation.getOuterTickId(), started = clock.getAsLong();
        final float yaw = effectiveYaw, pitch = effectivePitch;
        if (outerTick < 0 || !lifetimeValid.getAsBoolean()) return false;
        final SceneContext fresh = observation.captureScene();
        long elapsed = clock.getAsLong() - started;
        if (!fresh.isValid() || fresh.isSuspended() || !basis.scene.sameSceneAs(fresh)
                || !basis.isCurrent() || releaseAcceptance != basis || sequence != motionSequence
                || observation.getOuterTickId() != outerTick || elapsed < 0 || elapsed >= 200_000_000L
                || !canUseRotation(source, collecting) || collecting && competingIntent(source)
                || !lifetimeValid.getAsBoolean()) return false;
        rotationRelease = new RotationReleaseLease(basis, outerTick, started, yaw, pitch,
                Math.max(3, Math.min(12, ticks)), movement, body);
        return true;
    }

    public void cancelScaffoldRelease() { cancelRotationRelease("scaffold"); }
    public void cancelRotationRelease() { cancelScaffoldRelease(rotationRelease); }
    public void cancelRotationRelease(String source) {
        RotationReleaseLease release = rotationRelease;
        if (release != null && source.equals(release.basis.source)) cancelScaffoldRelease(release);
    }
    public boolean hasRotationRelease(String source) {
        RotationReleaseLease release = currentRotationRelease();
        return release != null && source.equals(release.basis.source);
    }

    /** Presentation may retain the last accepted pose between disable and the next C03.
     * Movement correction still requires a confirmed release owner; this view grants no actions.
     */
    public ReleaseView getRotationRelease() {
        RotationReleaseLease release = currentRotationRelease();
        if (release == null) return null;
        RuntimeObservation observation = release.basis.observation.get();
        if (observation == null) return null;
        return new ReleaseView(release, release.basis.source, release.releaseSource, release.lastYaw, release.lastPitch,
                release.progress(observation.getOuterTickId()), release.movement, release.body);
    }

    public static final class ReleaseView {
        public final Object identity;
        public final String source, releaseSource;
        public final float yaw, pitch, progress;
        public final MoveFixController.Mode movement;
        public final BodyRotationController.Mode body;
        private ReleaseView(Object identity, String source, String releaseSource, float yaw, float pitch, float progress,
                            MoveFixController.Mode movement, BodyRotationController.Mode body) {
            this.identity=identity; this.source=source; this.releaseSource=releaseSource;
            this.yaw=yaw; this.pitch=pitch; this.progress=progress; this.movement=movement; this.body=body;
        }
    }

    /** A real camera-target intent also prevents a later disable in this same frame from arming a lease. */
    public void cancelScaffoldReleaseForCameraIntent() {
        if (collecting) cameraIntent = true;
        cancelRotationRelease();
    }

    private void cancelScaffoldRelease(RotationReleaseLease expected) {
        if (expected == null || rotationRelease != expected) return;
        rotationRelease = null;
        states.remove(expected.releaseSource);
        if (expected.releaseSource.equals(owner)) {
            owner = null;
            accepted = previousAccepted = false;
        }
        if (winner != null && expected.releaseSource.equals(winner.source)) winner = null;
        if (resolvedRequest != null && expected.releaseSource.equals(resolvedRequest.source)) {
            resolvedRequest = null;
            resolvedFrame = null;
            resolvedTicket = null;
        }
    }

    private RotationReleaseLease currentRotationRelease() {
        RotationReleaseLease expected = rotationRelease;
        if (expected == null) return null;
        try {
            if (expected.isCurrent() && rotationRelease == expected) return expected;
        } catch (RuntimeException | Error error) {
            cancelScaffoldRelease(expected);
            throw error;
        }
        cancelScaffoldRelease(expected);
        return null;
    }

    /** Weak evidence copied before finishMotion clears its resolved ticket; never resampled at arm. */
    private final class AcceptedRotationBasis {
        private final String source;
        private final WeakReference<Object> player, world;
        private final WeakReference<NetHandlerPlayClient> handler;
        private final WeakReference<PacketPipeline> packets;
        private final WeakReference<RuntimeObservation> observation;
        private final SceneContext scene;
        private final long suspensionRevision;

        private AcceptedRotationBasis(ResolvedC03SendTicket ticket) {
            source = ticket.request.source;
            player = ticket.player; world = ticket.world; handler = ticket.handler; packets = ticket.packets;
            observation = new WeakReference<>(ticket.observation);
            scene = ticket.scene;
            suspensionRevision = ticket.suspensionRevision;
        }

        private boolean sameObjects() {
            Object p = player.get(), w = world.get();
            NetHandlerPlayClient h = handler.get();
            PacketPipeline pipe = packets.get();
            return p instanceof EntityPlayerSP && w != null && h != null && pipe != null
                    && RuntimeServices.rotation() == RotationManager.this && mc == Minecraft.getMinecraft()
                    && mc.thePlayer == p && mc.theWorld == w && motionPlayer.get() == p && motionWorld.get() == w
                    && connection.get() == pipe && ((EntityPlayerSP)p).worldObj == w && ((EntityPlayerSP)p).sendQueue == h
                    && h.getNetworkManager().getClientPackets() == pipe && scene != null
                    && pipe.getContextVersion() == scene.getPacketContextEpoch();
        }

        private boolean isCurrent() {
            Object p = player.get(), w = world.get();
            NetHandlerPlayClient h = handler.get();
            PacketPipeline pipe = packets.get();
            RuntimeObservation observed = observation.get();
            if (!(p instanceof EntityPlayerSP) || w == null || h == null || pipe == null || observed == null
                    || scene == null || !scene.isValid() || scene.isSuspended()
                    || RuntimeServices.rotation() != RotationManager.this || mc != Minecraft.getMinecraft()
                    || mc.thePlayer != p || mc.theWorld != w || motionPlayer.get() != p || motionWorld.get() != w
                    || connection.get() != pipe || ((EntityPlayerSP)p).worldObj != w || ((EntityPlayerSP)p).sendQueue != h
                    || h.getNetworkManager().getClientPackets() != pipe || !pipe.isActive()
                    || !((EntityPlayerSP)p).isEntityAlive() || ((EntityPlayerSP)p).isDead
                    || ((EntityPlayerSP)p).isSpectator() || ((EntityPlayerSP)p).isRiding()
                    || mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused()
                    || mc.getRenderViewEntity() != p) return false;
            // Live checks may call connection/player code. Recheck identity and observed boundaries afterwards.
            return observed == RuntimeServices.observation()
                    && observed.getObservedConnectionGeneration() == scene.getConnectionGeneration()
                    && observed.getObservedSuspensionRevision() == suspensionRevision
                    && pipe.getContextVersion() == scene.getPacketContextEpoch()
                    && RuntimeServices.rotation() == RotationManager.this && mc == Minecraft.getMinecraft()
                    && mc.thePlayer == p && mc.theWorld == w && motionPlayer.get() == p && motionWorld.get() == w
                    && connection.get() == pipe && ((EntityPlayerSP)p).worldObj == w && ((EntityPlayerSP)p).sendQueue == h
                    && h.getNetworkManager().getClientPackets() == pipe;
        }
    }

    private final class RotationReleaseLease {
        private final AcceptedRotationBasis basis;
        private final String releaseSource;
        private final int ticks;
        private final MoveFixController.Mode movement;
        private final BodyRotationController.Mode body;
        private final long outerTick, started;
        private long lastObservedNanos, lastObservedOuterTick;
        private final float yaw, pitch;
        private float lastYaw, lastPitch;
        private RotationReleaseLease(AcceptedRotationBasis basis, long outerTick, long started, float yaw, float pitch,
                                     int ticks, MoveFixController.Mode movement, BodyRotationController.Mode body) {
            this.basis = basis; this.outerTick = outerTick; this.started = started;
            this.ticks = ticks;
            this.movement = movement == null ? MoveFixController.Mode.OFF : movement;
            this.body = body == null ? BodyRotationController.Mode.VANILLA : body;
            releaseSource = "aura".equals(basis.source) ? AURA_RELEASE_SOURCE : SCAFFOLD_RELEASE_SOURCE;
            lastObservedNanos = started;
            lastObservedOuterTick = outerTick;
            this.yaw = yaw; this.pitch = pitch;
            lastYaw = yaw; lastPitch = pitch;
        }
        private float progress(long tick) { return RotationReturnCurve.progress((tick - outerTick) / (float) ticks); }
        private boolean isCurrent() {
            if (!basis.isCurrent() || rotationRelease != this) return false;
            RuntimeObservation observed = basis.observation.get();
            long now = clock.getAsLong(), tick = observed == null ? -1 : observed.getOuterTickId();
            long elapsed = now - started;
            boolean valid = rotationRelease == this && observed != null && observed == RuntimeServices.observation()
                    && observed.getObservedConnectionGeneration() == basis.scene.getConnectionGeneration()
                    && observed.getObservedSuspensionRevision() == basis.suspensionRevision
                    && tick >= outerTick && tick >= lastObservedOuterTick && tick >= 0 && tick - outerTick < ticks
                    && elapsed >= 0 && elapsed < (ticks + 1L) * 50_000_000L && now - lastObservedNanos >= 0
                    && basis.sameObjects();
            if (valid) { lastObservedNanos = now; lastObservedOuterTick = tick; }
            return valid;
        }
    }

    /** Immutable evidence for one resolved frame, not an accepted owner or a release lease. */
    public final class ResolvedC03SendTicket {
        private final Request request;
        private final Object frame;
        private final long motionSequence, outerTick;
        private final float yaw, pitch, wireYaw, wirePitch;
        private final RuntimeObservation observation;
        private final SceneContext scene;
        private final long suspensionRevision;
        private final RotationReleaseLease release;
        private final WeakReference<Object> player, world;
        private final WeakReference<NetHandlerPlayClient> handler;
        private final WeakReference<PacketPipeline> packets;

        private ResolvedC03SendTicket(ResolvedC03SendTicket captured, float wireYaw, float wirePitch) {
            request = captured.request; frame = captured.frame; motionSequence = captured.motionSequence;
            outerTick = captured.outerTick; yaw = captured.yaw; pitch = captured.pitch;
            observation = captured.observation; scene = captured.scene;
            suspensionRevision = captured.suspensionRevision; release = captured.release;
            player = captured.player; world = captured.world; handler = captured.handler; packets = captured.packets;
            this.wireYaw = wireYaw; this.wirePitch = wirePitch;
        }

        private ResolvedC03SendTicket(Request request, Object frame, float wireYaw, float wirePitch) {
            this.request = request;
            this.frame = frame;
            motionSequence = sequence;
            yaw = request.yaw; pitch = request.pitch;
            this.wireYaw = wireYaw; this.wirePitch = wirePitch;
            Object currentPlayer = motionPlayer.get();
            player = new WeakReference<>(currentPlayer);
            world = new WeakReference<>(motionWorld.get());
            NetHandlerPlayClient currentHandler = currentPlayer instanceof EntityPlayerSP
                    ? ((EntityPlayerSP)currentPlayer).sendQueue : null;
            handler = new WeakReference<>(currentHandler);
            packets = new WeakReference<>(currentHandler == null ? null : currentHandler.getNetworkManager().getClientPackets());
            observation = RuntimeServices.observation();
            outerTick = observation == null ? -1 : observation.getActiveOuterTickId();
            suspensionRevision = observation == null ? -1 : observation.getObservedSuspensionRevision();
            release = isReleaseSource(request.source) ? rotationRelease : null;
            scene = observation == null ? null : observation.captureScene();
        }

        private boolean sameFrame() {
            return collecting && sequence == motionSequence && resolvedFrame == frame
                    && resolvedRequest == request && winner == request && RuntimeServices.rotation() == RotationManager.this;
        }

        private boolean sameObjects() {
            Object p = player.get(), w = world.get();
            NetHandlerPlayClient h = handler.get();
            PacketPipeline pipe = packets.get();
            return p instanceof EntityPlayerSP && w != null && h != null && pipe != null
                    && mc == Minecraft.getMinecraft() && mc.thePlayer == p && mc.theWorld == w
                    && motionPlayer.get() == p && motionWorld.get() == w && connection.get() == pipe
                    && ((EntityPlayerSP)p).worldObj == w && ((EntityPlayerSP)p).sendQueue == h
                    && h.getNetworkManager().getClientPackets() == pipe && pipe.isActive()
                    && mc.getRenderViewEntity() == p;
        }

        private boolean current() {
            if (isReleaseSource(request.source)
                    && (release == null || currentRotationRelease() != release)) return false;
            if (!sameFrame() || !sameObjects() || observation == null || observation != RuntimeServices.observation()
                    || outerTick < 0 || observation.getActiveOuterTickId() != outerTick
                    || scene == null || !scene.isValid() || scene.isSuspended()) return false;
            // Sampling maintains existing observer identity/generation bookkeeping, never action quota.
            SceneContext fresh = observation.captureScene();
            return fresh.isValid() && !fresh.isSuspended() && scene.sameSceneAs(fresh)
                    && sameFrame() && sameObjects() && observation == RuntimeServices.observation()
                    && observation.getActiveOuterTickId() == outerTick
                    && (release == null || currentRotationRelease() == release);
        }

        /** Revalidate the captured lifetime after transport callbacks, before granting source authority. */
        public boolean isCurrent() { return current(); }

        /** Seal packet facts before PRE; selected must preserve this envelope and the resolved pose. */
        public Predicate<Packet<?>> guard(C03PacketPlayer original) {
            final Class<?> type = original.getClass();
            final boolean moving = original.isMoving(), rotating = original.getRotating(), ground = original.isOnGround();
            final double x = original.getPositionX(), y = original.getPositionY(), z = original.getPositionZ();
            return selected -> {
                if (selected == null || selected.getClass() != type || !current()) return false;
                C03PacketPlayer actual = (C03PacketPlayer)selected;
                float actualYaw = actual.getRotating() ? actual.getYaw() : wireYaw;
                float actualPitch = actual.getRotating() ? actual.getPitch() : wirePitch;
                return actual.isMoving() == moving && actual.getRotating() == rotating && actual.isOnGround() == ground
                        && (!moving || Double.compare(actual.getPositionX(), x) == 0
                            && Double.compare(actual.getPositionY(), y) == 0 && Double.compare(actual.getPositionZ(), z) == 0)
                        && Float.isFinite(actualYaw) && Float.isFinite(actualPitch) && actualPitch >= -90 && actualPitch <= 90
                        && Math.abs(MathHelper.wrapAngleTo180_float(yaw - actualYaw)) < .001f
                        && Math.abs(pitch - actualPitch) < .001f;
            };
        }
    }

    public void finishMotion(float yaw, float pitch, boolean packetAccepted) {
        final long finishingSequence = sequence;
        final boolean releaseFrame = winner != null && isReleaseSource(winner.source);
        if (releaseFrame) {
            RotationReleaseLease expected = rotationRelease;
            if (expected == null || currentRotationRelease() != expected) packetAccepted = false;
            if (sequence != finishingSequence) return;
        }
        final ResolvedC03SendTicket evidence = resolvedTicket;
        resolvedRequest = null;
        resolvedFrame = null;
        resolvedTicket = null;
        collecting = false;
        releaseAcceptance = null;
        accepted = packetAccepted && Float.isFinite(yaw) && Float.isFinite(pitch) && pitch >= -90 && pitch <= 90;
        if (!accepted) { invalidateUnownedSpeedCurves(null); return; }
        hasEffective = true;
        effectiveYaw = yaw;
        effectivePitch = clamp(pitch, -90, 90);
        owner = winner != null && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw - yaw)) < .001f
                && Math.abs(winner.pitch - pitch) < .001f ? winner.source : null;
        if (rotationRelease != null && rotationRelease.releaseSource.equals(owner)) {
            rotationRelease.lastYaw = effectiveYaw;
            rotationRelease.lastPitch = effectivePitch;
        }
        invalidateUnownedSpeedCurves(owner);
        if (("scaffold".equals(owner) || "aura".equals(owner)) && evidence != null && evidence.request == winner
                && evidence.motionSequence == sequence && evidence.observation != null
                && evidence.observation == RuntimeServices.observation() && evidence.outerTick >= 0
                && evidence.observation.getActiveOuterTickId() == evidence.outerTick
                && evidence.scene != null && evidence.scene.isValid() && !evidence.scene.isSuspended()
                && evidence.scene.getConnectionGeneration() == evidence.observation.getObservedConnectionGeneration()
                && evidence.suspensionRevision == evidence.observation.getObservedSuspensionRevision())
            releaseAcceptance = new AcceptedRotationBasis(evidence);
        if (owner != null) {
            RotationState state = state(owner);
            state.yaw = effectiveYaw;
            state.pitch = effectivePitch;
            state.active = true;
        }
    }

    private void invalidateUnownedSpeedCurves(String acceptedSource) {
        for (Map.Entry<String,RotationState> entry : states.entrySet()) {
            RotationTrajectory trajectory=entry.getValue().trajectory;
            if(trajectory.hasSpeedCurve() && !entry.getKey().equals(acceptedSource))trajectory.resetSpeedCurve();
            if(!entry.getKey().equals(acceptedSource))entry.getValue().arsenic.reset();
        }
    }

    /** Capture only when the feature is used: OFF retains its original clock-read order. */
    private long curveMotionTime() {
        if(curveMotionSequence!=sequence) {
            curveMotionSequence=sequence;curveMotionNanos=clock.getAsLong();
        }
        return curveMotionNanos;
    }

    public boolean isOwner(String source) { currentRotationRelease(); return hasEffective && source.equals(owner); }
    /** During PRE, only the last accepted owner is usable; a pending winner grants no correction. */
    public String getConfirmedOwner() {
        return canUseRotation(owner, collecting) ? owner : null;
    }
    public String getConfirmedOwner(Object player, Object world) {
        return motionPlayer.get() == player && motionWorld.get() == world ? getConfirmedOwner() : null;
    }
    public String getConfirmedOwner(Object player, Object world, Object playConnection) {
        return playConnection != null && isConnection(playConnection) ? getConfirmedOwner(player,world) : null;
    }
    /** PRE consumes the preceding accepted movement; POST consumes the current movement result. */
    public boolean canUseRotation(boolean pre) {
        currentRotationRelease();
        return hasEffective && (pre ? previousAccepted : accepted)
                && Float.isFinite(effectiveYaw) && Float.isFinite(effectivePitch);
    }
    /** True only when this source owns a locally accepted rotation for the queried phase.
     * PRE is available while collecting; finishMotion replaces that basis with the POST result.
     */
    public boolean canUseRotation(String source, boolean pre) {
        currentRotationRelease();
        return pre == collecting && source != null && !source.trim().isEmpty()
                && source.equals(owner) && canUseRotation(pre);
    }
    public boolean isMotionAccepted() { currentRotationRelease(); return accepted; }
    public long getMotionSequence() { return sequence; }
    public float getEffectiveYaw() { return effectiveYaw; }
    public float getEffectivePitch() { return effectivePitch; }
    public boolean hasEffectiveRotation() { return hasEffective; }

    public boolean matchesEffective(float yaw, float pitch) {
        currentRotationRelease();
        return accepted && Math.abs(MathHelper.wrapAngleTo180_float(effectiveYaw-yaw)) < .001f
                && Math.abs(effectivePitch-pitch) < .001f;
    }

    /** Also observe replays/other movement emitted by attack listeners, not just the motion event. */
    public void observeOutboundRotation(float yaw, float pitch) {
        boolean matchingRelease = collecting && winner != null && isReleaseSource(winner.source)
                && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw - yaw)) < .001f
                && Math.abs(winner.pitch - pitch) < .001f;
        if (rotationRelease != null && !matchingRelease) cancelRotationRelease();
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
            releaseAcceptance = null;
            accepted = false;
            previousAccepted = false;
            owner = null;
            invalidateUnownedSpeedCurves(null);
        } else if (!hasEffective || Math.abs(MathHelper.wrapAngleTo180_float(effectiveYaw-yaw)) >= .001f
                || Math.abs(effectivePitch-pitch) >= .001f) {
            releaseAcceptance = null;
            owner = null;
            // Matching ordinary transport belongs to this acquisition; an unrelated
            // observed/replayed pose withdraws it without changing packet authority.
            String matchingSource = collecting && winner != null
                    && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw-yaw)) < .001f
                    && Math.abs(winner.pitch-pitch) < .001f ? winner.source : null;
            invalidateUnownedSpeedCurves(matchingSource);
        }
        hasEffective = true;
        effectiveYaw = yaw;
        effectivePitch = pitch;
    }

    /** The numeric step is shared by combat/placement and can be tested without Minecraft. */
    public float[] step(String source, float yaw, float pitch, float targetYaw, float targetPitch,
                        float yawStep, float pitchStep, float sensitivity) {
        RotationState state = state(source);
        float fromYaw = collecting ? baseYaw : state.active ? state.yaw : yaw;
        float fromPitch = collecting ? basePitch : state.active ? state.pitch : pitch;
        float[] next = smooth(fromYaw, fromPitch, targetYaw, targetPitch, yawStep, pitchStep);
        next = quantize(next[0], next[1], fromYaw, fromPitch, sensitivity);
        if (!collecting) {
            state.yaw = next[0]; state.pitch = next[1]; state.active = true;
        }
        return next;
    }

    public float[] step(String source, float yaw, float pitch, float targetYaw, float targetPitch, float yawStep, float pitchStep) {
        return step(source, yaw, pitch, targetYaw, targetPitch, yawStep, pitchStep, mc.gameSettings.mouseSensitivity);
    }

    private Vec3 baseAimPoint(EntityLivingBase target,boolean best,AxisAlignedBB box) {
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);
        if(best) return new Vec3(clampInside(eyes.xCoord,box.minX,box.maxX,.03),
                clampInside(eyes.yCoord,box.minY,box.maxY,.03),clampInside(eyes.zCoord,box.minZ,box.maxZ,.03));
        return new Vec3(clampInside(target.posX,box.minX,box.maxX,.001),
                clampInside(target.posY+target.getEyeHeight()*.82,box.minY,box.maxY,.001),
                clampInside(target.posZ,box.minZ,box.maxZ,.001));
    }

    public float[] rotationsTo(Entity target, Vec3 aimPoint) {
        if (target == null || mc.thePlayer == null) {
            return null;
        }

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 hit = aimPoint != null ? aimPoint : new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        return angles(eyes,hit);
    }
    private float[] angles(Vec3 eyes,Vec3 hit) {
        double diffX = hit.xCoord - eyes.xCoord;
        double diffY = hit.yCoord - eyes.yCoord;
        double diffZ = hit.zCoord - eyes.zCoord;
        double distance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, distance));
        return new float[]{yaw, pitch};
    }

    public float[] smooth(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxYawStep, float maxPitchStep) {
        float smoothedYaw = me.youngjibbit.utils.rotations.RUtil.smoothRotation(currentYaw,targetYaw,maxYawStep);
        float smoothedPitch = currentPitch + clamp(clamp(targetPitch, -90, 90) - currentPitch, -maxPitchStep, maxPitchStep);
        smoothedPitch = clamp(smoothedPitch, -90.0f, 90.0f);
        return new float[]{smoothedYaw, smoothedPitch};
    }

    public float[] applyGcd(float yaw, float pitch, float previousYaw, float previousPitch) {
        return quantize(yaw, pitch, previousYaw, previousPitch, mc.gameSettings.mouseSensitivity);
    }

    private float[] quantize(float yaw, float pitch, float previousYaw, float previousPitch, float mouseSensitivity) {
        // Snap every requested delta to an integer mouse count relative to the
        // last actual rotation. Previously sub-GCD deltas were sent unmodified,
        // even though no integer 1.8.9 mouse movement could generate them.
        yaw = RotationSensitivity.snap(yaw, previousYaw, mouseSensitivity);
        pitch = RotationSensitivity.snap(pitch, previousPitch, mouseSensitivity);
        // Vanilla pitch clamping is the only intentional exception to the grid.
        return new float[]{yaw, clamp(pitch, -90.0f, 90.0f)};
    }

    public float[] calculateRotations(String source,float cameraYaw,float cameraPitch,EntityLivingBase target,
                                      boolean bestHitVec,RotationProfile profile,boolean visible) {
        if(target==null || mc==null || mc.thePlayer==null) return null;
        RotationState state=state(source);
        AxisAlignedBB box=target.getEntityBoundingBox();
        Vec3 feet=new Vec3(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        if(!visible && (profile.arsenic!=RotationProfile.ArsenicMode.OFF || profile.smartAim || profile.stopOnTarget || profile.heuristics))feet=mc.thePlayer.getPlannedMovementPosition(feet.xCoord,feet.yCoord,feet.zCoord);
        Vec3 eyes=feet.addVector(0,mc.thePlayer.getEyeHeight(),0);
        float fromYaw=visible?cameraYaw:collecting?baseYaw:state.active?state.yaw:cameraYaw;
        float fromPitch=visible?cameraPitch:collecting?basePitch:state.active?state.pitch:cameraPitch;
        if(profile.stopOnTarget && rays.canHitFrom(feet,target,fromYaw,fromPitch,profile.reach,profile.throughWalls)) {
            state.arsenic.reset();
            if(profile.speedCurve==RotationProfile.SpeedCurve.BEZIER) {
                if(collecting)state.trajectory.pauseMotion(target,sequence,curveMotionTime());
            } else state.trajectory.pause(target);
            return new float[]{fromYaw,fromPitch};
        }
        if(profile.arsenic!=RotationProfile.ArsenicMode.OFF) {
            if(!collecting)return new float[]{fromYaw,fromPitch};
            me.youngjibbit.core.arsenic.aim.AimCore.Input in=new me.youngjibbit.core.arsenic.aim.AimCore.Input();
            in.eyeX=eyes.xCoord;in.eyeY=eyes.yCoord;in.eyeZ=eyes.zCoord;
            in.minX=box.minX;in.minY=box.minY;in.minZ=box.minZ;in.maxX=box.maxX;in.maxY=box.maxY;in.maxZ=box.maxZ;
            in.selfDX=sampleDelta(mc.thePlayer.posX-mc.thePlayer.lastTickPosX);in.selfDZ=sampleDelta(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ);
            in.targetDX=sampleDelta(target.posX-target.lastTickPosX);in.targetDZ=sampleDelta(target.posZ-target.lastTickPosZ);
            in.targetId=target.getEntityId();in.curYaw=fromYaw;in.curPitch=fromPitch;
            float[] next=state.arsenic.advance(target,in,profile,sequence,curveMotionTime());
            return quantize(next[0],next[1],fromYaw,fromPitch,mc.gameSettings.mouseSensitivity);
        }
        state.arsenic.reset();
        Vec3 base=baseAimPoint(target,bestHitVec,box);
        if(profile.smartAim || profile.heuristics)base=visibleAimPoint(target,box,base,feet,fromYaw,fromPitch,profile,rays);
        if(base==null)return null;
        Vec3 aim=state.trajectory.point(target,box,base,profile);
        if((profile.smartAim || profile.heuristics) && !hitsPoint(rays,feet,target,aim,profile))aim=base;
        float[] desired=angles(eyes,aim);
        return profileStep(source,cameraYaw,cameraPitch,target,desired[0],desired[1],eyes.distanceTo(aim),profile,visible);
    }

    private boolean hitsPoint(RaycastService rays,Vec3 feet,EntityLivingBase target,Vec3 point,RotationProfile profile) {
        float[] direction=angles(feet.addVector(0,mc.thePlayer.getEyeHeight(),0),point);
        return rays.canHitFrom(feet,target,direction[0],direction[1],profile.reach,profile.throughWalls);
    }
    private static double sampleDelta(double delta) { return Double.isFinite(delta) && Math.abs(delta)<=2?delta:0; }
    private Vec3 visibleAimPoint(EntityLivingBase target,AxisAlignedBB box,Vec3 base,Vec3 feet,float yaw,float pitch,RotationProfile profile,RaycastService rays) {
        Vec3 eyes=feet.addVector(0,mc.thePlayer.getEyeHeight(),0);
        List<Vec3> candidates=new ArrayList<>();candidates.add(base);
        for(double x:axisSamples(box.minX,box.maxX))
            for(double y:axisSamples(box.minY,box.maxY))
                for(double z:axisSamples(box.minZ,box.maxZ))
                    candidates.add(new Vec3(x,y,z));
        candidates.sort(Comparator.comparingDouble(point->{float[] a=angles(eyes,point);double dy=MathHelper.wrapAngleTo180_float(a[0]-yaw),dp=a[1]-pitch;return dy*dy+dp*dp;}));
        for(Vec3 candidate:candidates)if(hitsPoint(rays,feet,target,candidate,profile))return candidate;
        return null;
    }
    private double[] axisSamples(double min,double max) {
        double inset=Math.min(.001,(max-min)/4);
        return new double[]{min+inset,(min+max)/2,max-inset};
    }

    public float[] calculatePlanRotations(String source,float cameraYaw,float cameraPitch,Object target,float targetYaw,float targetPitch,double distance,RotationProfile profile) {
        return profileStep(source,cameraYaw,cameraPitch,target,targetYaw,targetPitch,distance,profile,false);
    }

    public Vec3 faceAimPoint(String source,Object target,AxisAlignedBB box,net.minecraft.util.EnumFacing face,Vec3 base,RotationProfile profile) {
        Vec3 point=state(source).trajectory.point(target,box,base,profile);
        switch(face) {
            case WEST:return new Vec3(box.minX,point.yCoord,point.zCoord);
            case EAST:return new Vec3(box.maxX,point.yCoord,point.zCoord);
            case DOWN:return new Vec3(point.xCoord,box.minY,point.zCoord);
            case UP:return new Vec3(point.xCoord,box.maxY,point.zCoord);
            case NORTH:return new Vec3(point.xCoord,point.yCoord,box.minZ);
            default:return new Vec3(point.xCoord,point.yCoord,box.maxZ);
        }
    }

    private float[] profileStep(String source,float cameraYaw,float cameraPitch,Object target,float targetYaw,float targetPitch,double distance,RotationProfile profile,boolean visible) {
        RotationState state=state(source);
        float fromYaw=visible?cameraYaw:collecting?baseYaw:state.active?state.yaw:cameraYaw;
        float fromPitch=visible?cameraPitch:collecting?basePitch:state.active?state.pitch:cameraPitch;
        float[] next;
        if(profile.speedCurve==RotationProfile.SpeedCurve.BEZIER) {
            if(!collecting)return new float[]{fromYaw,fromPitch};
            next=state.trajectory.advanceMotion(target,fromYaw,fromPitch,targetYaw,targetPitch,distance,profile,sequence,curveMotionTime());
        } else next=state.trajectory.advance(target,fromYaw,fromPitch,targetYaw,targetPitch,distance,profile);
        // Existing nearest-grid rounding may add half a mouse quantum to a pre-GCD cap.
        // Visible assistance stays relative to the camera; later manual input is preserved at resolve.
        return quantize(next[0],next[1],fromYaw,fromPitch,mc.gameSettings.mouseSensitivity);
    }

    public void reset(String source) {
        if (source == null) {
            return;
        }
        if (releaseAcceptance != null && source.equals(releaseAcceptance.source)) releaseAcceptance = null;
        if (isReleaseSource(source) && rotationRelease != null && source.equals(rotationRelease.releaseSource)) cancelRotationRelease();
        states.remove(source);
        if (source.equals(owner)) owner = null;
        if (winner != null && source.equals(winner.source)) winner = null;
        if (resolvedRequest != null && source.equals(resolvedRequest.source)) {
            resolvedRequest = null;
            resolvedFrame = null;
            resolvedTicket = null;
        }
    }

    public void reset() {
        curveMotionSequence=Long.MIN_VALUE;
        rotationRelease = null;
        releaseAcceptance = null;
        offeredSources.clear();
        cameraIntent = false;
        RotationCorrections.reset();
        states.clear();
        owner = null;
        winner = null;
        resolvedRequest = null;
        resolvedFrame = null;
        resolvedTicket = null;
        accepted = false;
        hasEffective = false;
        previousAccepted = false;
        motionPlayer.clear();
        motionWorld.clear();
    }

    private double clampInside(double value, double min, double max, double inset) {
        double innerMin = min + inset;
        double innerMax = max - inset;
        if (innerMin > innerMax) {
            innerMin = min;
            innerMax = max;
        }
        return MathHelper.clamp_double(value, innerMin, innerMax);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private RotationState state(String source) {
        String key = source == null ? "shared" : source;
        RotationState state = states.get(key);
        if (state == null) {
            long epoch=trajectoryEpochs.merge(key,1L,Long::sum);
            long trajectorySeed=seed ^ sourceHash(key) ^ epoch*0x54ab183dL;
            state = new RotationState(new RotationTrajectory(clock,(int)trajectorySeed),new ArsenicAimTrajectory(trajectorySeed));
            states.put(key, state);
        }
        return state;
    }

    private static long sourceHash(String key) { return key.hashCode()*0x9e3779b97f4a7c15L; }

    private static final class RotationState {
        final RotationTrajectory trajectory;
        final ArsenicAimTrajectory arsenic;
        RotationState(RotationTrajectory trajectory,ArsenicAimTrajectory arsenic) { this.trajectory=trajectory;this.arsenic=arsenic; }
        private float yaw;
        private float pitch;
        private boolean active;

    }

    private static final class Request {
        final String source;
        final int priority;
        float yaw,pitch;
        final boolean visible;
        final float cameraYaw,cameraPitch;
        final BooleanSupplier valid;
        Request(String source,int priority,float yaw,float pitch,boolean visible,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
            this.source=source;this.priority=priority;this.yaw=yaw;this.pitch=pitch;
            this.visible=visible;this.cameraYaw=cameraYaw;this.cameraPitch=cameraPitch;this.valid=valid;
        }
    }
}
