package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.player.MovementReadyEvent;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.ai.NeutronAiCore;
import me.youngjibbit.core.combat.ai.NeutronAiCheckpoint;
import me.youngjibbit.core.combat.ai.NeutronAiFeatures;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.world.AdaptiveBridgeMotion;
import me.youngjibbit.core.world.AdaptiveBridgePlanner;
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
    private PersonalModelGate personalModelGate;
    private final RaycastService rays=new RaycastService(mc);
    private final Map<String, RotationState> states = new HashMap<>();
    private final Map<String,Long> trajectoryEpochs = new HashMap<>();
    private final LongSupplier clock;
    private final long seed;
    private final Thread bridgeOwningThread=Thread.currentThread();
    public RotationManager() { this(System::nanoTime,ThreadLocalRandom.current().nextLong()); }
    public RotationManager(LongSupplier clock,long seed) { this.clock=clock;this.seed=seed; }
    /** Synthetic JUnit injection only. There is no production file/model loader. */
    boolean installAiTestCheckpoint(NeutronAiCheckpoint checkpoint) {
        if(collecting)return false;
        RotationState s=state("aura");s.invalidateAi();
        if(s.ai==null)s.ai=new NeutronAiCore();
        return s.ai.install(checkpoint);
    }
    /**
     * Explicit, locally authenticated personal model installation. Package
     * access keeps this entry owned by the consent/provenance loader; JUnit's
     * separate synthetic injection keeps its existing contract.
     */
    boolean installAiPersonalCheckpoint(NeutronAiCheckpoint checkpoint,Object account,
                                         Object world,Object player,Object connection,long epoch) {
        PersonalModelGate gate=new PersonalModelGate(account,world,player,connection,epoch);
        if(checkpoint==null || collecting || !gate.current(mc))return false;
        reset("aura");
        RotationState active=state("aura");
        if(active.ai==null)active.ai=new NeutronAiCore();
        if(!active.ai.install(checkpoint))return false;
        personalModelGate=gate;
        return true;
    }
    /** Consent revoke, scene switch or model corruption eliminates all AI state. */
    void clearAiPersonalCheckpoint() {
        if(personalModelGate!=null)reset("aura");
        personalModelGate=null;
    }
    boolean hasAiPersonalCheckpoint() {
        RotationState aura=states.get("aura");
        return personalModelGate!=null && personalModelGate.current(mc)
                && aura!=null && aura.ai!=null && aura.ai.hasModel();
    }

    /** Identity check is independent from Aura's ordinary target acquisition. */
    private static final class PersonalModelGate {
        final Object account,world,player,connection;
        final long epoch;
        PersonalModelGate(Object account,Object world,Object player,Object connection,long epoch) {
            this.account=account;this.world=world;this.player=player;
            this.connection=connection;this.epoch=epoch;
        }
        boolean current(Minecraft mc) {
            if(mc==null || account==null || world==null || player==null
                    || connection==null || epoch<0 || mc.theWorld!=world
                    || mc.thePlayer!=player || mc.getSession()!=account
                    || mc.thePlayer.sendQueue==null
                    || mc.thePlayer.sendQueue.getNetworkManager()==null)return false;
            PacketPipeline pipeline=mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
            return pipeline==connection && pipeline!=null && pipeline.isActive()
                    && pipeline.getContextVersion()==epoch;
        }
    }

    /** Same-motion offered candidate PLUS locally accepted Aura C03. */
    public boolean isAiAuraMotionAccepted() {
        RotationState s=states.get("aura");
        return s!=null && s.aiOfferedSequence==sequence && accepted && "aura".equals(owner);
    }
    long aiInferenceCountForTests() {
        RotationState s=states.get("aura");
        return s==null||s.ai==null?0:s.ai.inferenceCount();
    }
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
    private MovementFrame movementFrame;
    private BridgeRotationCandidate bridgeCandidate;
    private BridgeFinalization bridgeFinalization;
    private VisibleAssistStop visibleAssistStop;
    private static final String VISIBLE_STOP = "aimassist-return";

    /** Explicit Adaptive lifetime; generic source names never confer this permission. */
    private static final class BridgeMotionScope {
        final WeakReference<Object> owner, world, player, connection;
        final long epoch, generation;
        BridgeMotionScope(AdaptiveBridgePlanner.Scope scope) {
            owner=new WeakReference<>(scope.owner);world=new WeakReference<>(scope.world);
            player=new WeakReference<>(scope.player);connection=new WeakReference<>(scope.connection);
            epoch=scope.connectionEpoch;generation=scope.acquisitionGeneration;
        }
        boolean same(AdaptiveBridgePlanner.Scope scope) {
            return scope!=null && owner.get()==scope.owner && world.get()==scope.world
                    && player.get()==scope.player && connection.get()==scope.connection
                    && epoch==scope.connectionEpoch && generation==scope.acquisitionGeneration;
        }
        AdaptiveBridgePlanner.Scope live() {
            return new AdaptiveBridgePlanner.Scope(owner.get(),world.get(),player.get(),connection.get(),epoch,generation);
        }
    }

    private static final class BridgeRotationCandidate {
        final BridgeMotionScope scope;
        final AdaptiveBridgeMotion controller;
        final Object identity;
        final long sequence;
        final float yaw,pitch;
        boolean consumed;
        BridgeRotationCandidate(AdaptiveBridgePlanner.Scope scope,AdaptiveBridgeMotion controller,Object identity,
                                long sequence,float yaw,float pitch) {
            this.scope=new BridgeMotionScope(scope);this.sequence=sequence;this.yaw=yaw;this.pitch=pitch;
            this.controller=controller;this.identity=identity;
        }
    }

    /** The input frame remains immutable; only this exact late C03 request may differ. */
    private final class BridgeFinalization {
        final MovementFrame movement;
        final Request request;
        final BridgeMotionScope scope;
        final BridgeRotationCandidate candidate;
        BridgeFinalization(MovementFrame movement,Request request,AdaptiveBridgePlanner.Scope scope,
                           BridgeRotationCandidate candidate) {
            this.movement=movement;this.request=request;this.scope=new BridgeMotionScope(scope);
            this.candidate=candidate;
        }
        boolean current() {
            AdaptiveBridgePlanner.Scope live=scope.live();
            if(bridgeFinalization!=this || !bridgeCandidateCurrent(candidate) || !candidate.consumed
                    || winner!=request || movementFrame!=movement
                    || !movement.claimed || !movement.current() || movement.request==null
                    || movement.request.bridgeScope==null || !movement.request.bridgeScope.same(live)
                    || !bridgeCameraMovement(live))return false;
            return bridgeFinalization==this && bridgeCandidateCurrent(candidate)
                    && winner==request && movementFrame==movement
                    && movement.claimed && movement.current();
        }
    }

    /** A visible assist has no silent yaw to return from. Decay only its last accepted camera delta. */
    public void beginVisibleAssistStop() {
        RotationState state = states.get("aimassist");
        if (state == null || state.visibleStep == null || !canUseRotation("aimassist", collecting)) return;
        if (state.visibleStep.current()) visibleAssistStop = state.visibleStep;
    }

    public void cancelVisibleAssistStop() {
        visibleAssistStop = null;
        if (winner != null && VISIBLE_STOP.equals(winner.source)) winner = null;
    }

    private final class VisibleAssistStop {
        final RuntimeObservation observation = RuntimeServices.observation();
        final long suspension = observation == null ? -1 : observation.getObservedSuspensionRevision();
        final long connectionGeneration = observation == null ? -1 : observation.getObservedConnectionGeneration();
        final long acquired = sequence, startNanos = clock.getAsLong();
        final float yawDelta, pitchDelta;
        VisibleAssistStop(float yawDelta, float pitchDelta) {
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
        }
        boolean current() {
            long age = clock.getAsLong() - startNanos;
            return age >= 0 && age <= 210_000_000L && sequence >= acquired && sequence - acquired <= 3
                    && observation != null && observation == RuntimeServices.observation()
                    && observation.getObservedSuspensionRevision() == suspension
                    && observation.getObservedConnectionGeneration() == connectionGeneration
                    && mc != null && mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null
                    && mc.inGameHasFocus && !mc.isGamePaused() && motionPlayer.get() == mc.thePlayer
                    && motionWorld.get() == mc.theWorld && mc.thePlayer.isEntityAlive()
                    && !mc.thePlayer.isRiding() && !mc.thePlayer.isSpectator() && mc.getRenderViewEntity() == mc.thePlayer;
        }
        Request next() {
            int tick = (int) (sequence - acquired);
            if (tick <= 0 || !current()) return null;
            float gain = 1 - RotationReturnCurve.progress(tick / 3.5f);
            float yaw = mc.thePlayer.rotationYaw, pitch = mc.thePlayer.rotationPitch;
            float[] pose = quantize(yaw + yawDelta * gain, pitch + pitchDelta * gain, yaw, pitch,
                    mc.gameSettings.mouseSensitivity);
            return new Request(VISIBLE_STOP, 0, pose[0], pose[1], true, yaw, pitch, this::current);
        }
    }

    /** Predict local physics before living input; only finishMotion can grant packet/action authority. */
    public void prepareMovement(EntityPlayerSP player) {
        RuntimeObservation observation = RuntimeServices.observation();
        if (mc == null || player != mc.thePlayer || mc.theWorld == null || player.worldObj != mc.theWorld
                || observation == null || observation.getCurrentStep() == null || !player.isEntityAlive()
                || player.isRiding() || player.isSpectator() || mc.currentScreen != null || !mc.inGameHasFocus
                || mc.getRenderViewEntity() != player || player.sendQueue == null) return;
        Object step = observation.getCurrentStep();
        PacketPipeline pipeline = player.sendQueue.getNetworkManager().getClientPackets();
        if (pipeline == null || !pipeline.isActive() || !player.sendQueue.getNetworkManager().isChannelOpen()) return;
        SceneContext scene = observation.captureScene();
        if (!scene.isValid() || scene.isSuspended() || observation.getCurrentStep() != step) return;
        bindConnection(pipeline);
        beginMotion(player, mc.theWorld, player.rotationYaw, player.rotationPitch);
        final long preparingSequence = sequence;
        EventRotation preparation = new EventRotation(
                new EventMotion(player.posX, player.getEntityBoundingBox().minY, player.posZ,
                        player.rotationYaw, player.rotationPitch, player.onGround));
        try {
            Pulsar.BUS.post(preparation);
            if (sequence != preparingSequence || !collecting || observation.getCurrentStep() != step) return;
            resolve(preparation.motion());
            if (sequence != preparingSequence || !collecting || observation.getCurrentStep() != step) return;
            MoveFixController.Mode mode = RotationCorrections.movementModeFor(winner == null ? null : winner.source);
            if (sequence != preparingSequence || !collecting || observation.getCurrentStep() != step) return;
            movementFrame = new MovementFrame(observation, step, scene, pipeline, winner, preparation,
                    preparation.motion().getYaw(), preparation.motion().getPitch(), mode);
            if (!movementFrame.current()) { movementFrame = null; return; }
        } catch (RuntimeException | Error error) {
            if (sequence == preparingSequence) finishMotion(player.rotationYaw, player.rotationPitch, false);
            throw error;
        }
    }

    public boolean hasPreparedMovement() { return movementFrame != null && movementFrame.current(); }

    /** Final sprint owners run before automatic/vanilla jumps and horizontal acceleration. */
    public void beforeMovementPhysics() {
        MovementFrame frame = movementFrame;
        if (frame == null || !frame.current() || frame.movementReady) return;
        frame.movementReady = true;
        Pulsar.BUS.post(new MovementReadyEvent());
        if (movementFrame == frame && frame.current()) frame.preparation.runBeforePhysics();
    }

    public boolean isPreparedMovementClaimed() {
        return movementFrame != null && movementFrame.current() && movementFrame.claimed;
    }

    public boolean resumePreparedMovement(EntityPlayerSP player, Object world) {
        MovementFrame frame = movementFrame;
        if (frame == null || !frame.current() || frame.claimed || motionPlayer.get() != player || motionWorld.get() != world) return false;
        // Claim before the scene supplier: a callback cannot send this physical step twice.
        frame.claimed = true;
        SceneContext fresh = frame.observation.captureScene();
        return movementFrame == frame && frame.current() && fresh.isValid() && !fresh.isSuspended()
                && frame.scene.sameSceneAs(fresh);
    }

    /** This prediction is for local input/physics only, never for attacks, placement or rendering. */
    public MovementRotation getPreparedMovement() {
        MovementFrame frame = movementFrame;
        return frame != null && frame.current() ? frame.rotation : null;
    }

    public static final class MovementRotation {
        public final String source;
        public final float yaw, pitch;
        public final MoveFixController.Mode mode;
        private MovementRotation(String source, float yaw, float pitch, MoveFixController.Mode mode) {
            this.source = source; this.yaw = yaw; this.pitch = pitch; this.mode = mode;
        }
    }

    private final class MovementFrame {
        final RuntimeObservation observation;
        final Object step;
        final SceneContext scene;
        final WeakReference<PacketPipeline> pipeline;
        final long frameSequence, epoch, suspension;
        final Request request;
        final EventRotation preparation;
        final MovementRotation rotation;
        boolean claimed, movementReady;
        RotationReleaseLease returnAfterMovement;
        MovementFrame(RuntimeObservation observation, Object step, SceneContext scene, PacketPipeline pipeline,
                      Request request, EventRotation preparation, float yaw, float pitch, MoveFixController.Mode mode) {
            this.observation = observation; this.step = step; this.scene = scene;
            this.pipeline = new WeakReference<>(pipeline); this.request = request;
            this.preparation = preparation;
            frameSequence = sequence; epoch = pipeline.getContextVersion();
            suspension = observation.getObservedSuspensionRevision();
            rotation = new MovementRotation(request == null ? null : request.source, yaw, pitch, mode);
        }
        boolean current() {
            PacketPipeline pipe = pipeline.get();
            return collecting && sequence == frameSequence && observation == RuntimeServices.observation()
                    && observation.getCurrentStep() == step && observation.getActiveOuterTickId() >= 0
                    && observation.getObservedConnectionGeneration() == scene.getConnectionGeneration()
                    && observation.getObservedSuspensionRevision() == suspension
                    && pipe != null && pipe.isActive() && pipe.getContextVersion() == epoch && isConnection(pipe)
                    && mc.thePlayer == motionPlayer.get() && mc.theWorld == motionWorld.get()
                    && mc.thePlayer != null && mc.thePlayer.worldObj == mc.theWorld;
        }
    }

    public void beginMotion(Object player, Object world, float cameraYaw, float cameraPitch) {
        if (personalModelGate!=null && !personalModelGate.current(mc))
            clearAiPersonalCheckpoint();
        if (motionPlayer.get() != player || motionWorld.get() != world) {
            reset();
            motionPlayer = new WeakReference<>(player);
            motionWorld = new WeakReference<>(world);
        }
        beginMotion(cameraYaw, cameraPitch);
    }

    public void beginMotion(float cameraYaw, float cameraPitch) {
        movementFrame = null;
        bridgeCandidate=null;bridgeFinalization=null;
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

    /** Mark an early Adaptive hold whose local movement stays in the camera input frame. */
    public boolean requestBridgeMovement(AdaptiveBridgePlanner.Scope scope,float yaw,float pitch) {
        if(Thread.currentThread()!=bridgeOwningThread || movementFrame!=null
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch)>90)return false;
        final long motion=sequence;
        RuntimeObservation observation=RuntimeServices.observation();
        SceneContext scene=bridgeScene(scope,observation,motion);
        if(scene==null || !bridgeCameraMovement(scope) || sequence!=motion || movementFrame!=null)return false;
        Request request=new Request("scaffold",SCAFFOLD_PRIORITY,yaw,pitch,false,0,0,()->true);
        request.bridgeScope=new BridgeMotionScope(scope);
        offer(request);
        return winner==request;
    }

    /**
     * Commit the checked actual-PRE candidate after input/physics. Only an explicitly
     * scoped Adaptive camera-frame hold may receive a different final C03 angle.
     * Neither the consumed input axes nor the immutable MovementRotation are changed.
     */
    public boolean requestBridgeFinalRotation(AdaptiveBridgePlanner.Scope scope,float yaw,float pitch) {
        if(Thread.currentThread()!=bridgeOwningThread)return false;
        final long motion=sequence;
        final BridgeRotationCandidate candidate=bridgeCandidate;
        final MovementFrame prepared=movementFrame;
        if(candidate==null || candidate.consumed || candidate.sequence!=motion || !candidate.scope.same(scope)
                || Float.compare(candidate.yaw,yaw)!=0 || Float.compare(candidate.pitch,pitch)!=0
                || !bridgeCandidateCurrent(candidate))return false;
        if(prepared!=null && (!prepared.current() || !prepared.claimed || prepared.request==null
                || prepared.request.bridgeScope==null || !prepared.request.bridgeScope.same(scope)
                || winner!=prepared.request))return false;
        RuntimeObservation observation=RuntimeServices.observation();
        SceneContext scene=bridgeScene(scope,observation,motion);
        if(scene==null || !bridgeCameraMovement(scope) || sequence!=motion || bridgeCandidate!=candidate
                || candidate.consumed || movementFrame!=prepared
                || !bridgeCandidateCurrent(candidate))return false;
        if(prepared!=null && (!prepared.current() || !prepared.claimed || winner!=prepared.request))return false;
        Request request=new Request("scaffold",SCAFFOLD_PRIORITY,yaw,pitch,false,0,0,()->true);
        request.bridgeScope=new BridgeMotionScope(scope);
        request.bridgeCandidate=candidate;
        candidate.consumed=true;
        if(prepared==null) {
            offer(request);
            return winner==request;
        }
        cancelVisibleAssistStop();cancelRotationRelease();
        offeredSources.add("scaffold");
        winner=request;
        bridgeFinalization=new BridgeFinalization(prepared,request,scope,candidate);
        return bridgeFinalization.current();
    }

    private boolean bridgeCameraMovement(AdaptiveBridgePlanner.Scope scope) {
        if(!bridgeContext(scope))return false;
        me.youngjibbit.module.impl.world.Scaffold scaffold=(me.youngjibbit.module.impl.world.Scaffold)scope.owner;
        boolean camera=scaffold.moveFixMode()==MoveFixController.Mode.OFF || scaffold.preservesBridgeHeading();
        return camera && scaffold.isCurrentBridgeScope(scope) && bridgeContext(scope);
    }

    private boolean bridgeCandidateCurrent(BridgeRotationCandidate candidate) {
        RotationState state=states.get("scaffold");
        return candidate!=null && bridgeCandidate==candidate && candidate.sequence==sequence
                && state!=null && state.bridgeMotion==candidate.controller
                && candidate.controller.isTaskCurrent(candidate.scope.live(),candidate.identity,candidate.sequence);
    }

    public void requestVisible(String source,int priority,float yaw,float pitch,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
        offer(new Request(source,priority,yaw,pitch,true,cameraYaw,cameraPitch,valid));
    }

    private void offer(Request request) {
        if (!collecting || request.source==null || isReleaseSource(request.source)
                || !Float.isFinite(request.yaw) || !Float.isFinite(request.pitch)) return;
        cancelVisibleAssistStop();
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
        MovementFrame prepared = movementFrame;
        if (prepared != null && prepared.current()) {
            BridgeFinalization bridge=bridgeFinalization;
            if(bridge!=null && bridge.movement==prepared && bridge.current()) {
                event.setRotations(bridge.request.yaw,bridge.request.pitch);
                resolvedRequest=bridge.request;resolvedFrame=new Object();
                resolvedTicket=new ResolvedC03SendTicket(bridge.request,resolvedFrame,Float.NaN,Float.NaN);
                return;
            }
            if(sequence!=resolvingSequence || movementFrame!=prepared || !prepared.current())return;
            // Physics has already used this exact pose. A late replacement/disable may
            // withdraw its source, but must not relabel the acceleration with another yaw.
            if (winner != prepared.request) {
                RotationReleaseLease release = currentRotationRelease();
                if (sequence != resolvingSequence || movementFrame != prepared || !prepared.current()) return;
                if (prepared.request != null && release != null && release.basis.source.equals(prepared.request.source)) {
                    // Disable happened after physics. Send its consumed pose as a strict,
                    // rotation-only return frame, then start easing from that accepted pose.
                    winner = new Request(release.releaseSource, 0, prepared.rotation.yaw, prepared.rotation.pitch,
                            false, 0, 0, () -> true);
                    prepared.returnAfterMovement = release;
                } else winner = null;
            }
            event.setRotations(prepared.rotation.yaw, prepared.rotation.pitch);
            if (winner != null && ("aura".equals(winner.source) || "scaffold".equals(winner.source) || isReleaseSource(winner.source))) {
                resolvedRequest = winner; resolvedFrame = new Object();
                resolvedTicket = new ResolvedC03SendTicket(winner, resolvedFrame, Float.NaN, Float.NaN);
            }
            return;
        }
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
        if (visibleAssistStop != null) {
            if (winner != null && VISIBLE_STOP.equals(winner.source) && visibleAssistStop.current()) {
                // A second resolution of the same frame must not move the camera twice.
                event.setRotations(winner.yaw, winner.pitch);
                return;
            }
            if (winner != null || release != null || cameraIntent || !visibleAssistStop.current()) cancelVisibleAssistStop();
            else winner = visibleAssistStop.next();
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
            RotationState visible = states.get(winner.source);
            if ("aimassist".equals(winner.source) && visible != null && visible.softVisible) {
                winner.visibleStep = new VisibleAssistStop(MathHelper.wrapAngleTo180_float(winner.yaw - winner.cameraYaw),
                        winner.pitch - winner.cameraPitch);
            }
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
        private float yaw, pitch;
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
        private final BridgeFinalization bridge;
        private final WeakReference<Object> player, world;
        private final WeakReference<NetHandlerPlayClient> handler;
        private final WeakReference<PacketPipeline> packets;

        private ResolvedC03SendTicket(ResolvedC03SendTicket captured, float wireYaw, float wirePitch) {
            request = captured.request; frame = captured.frame; motionSequence = captured.motionSequence;
            outerTick = captured.outerTick; yaw = captured.yaw; pitch = captured.pitch;
            observation = captured.observation; scene = captured.scene;
            suspensionRevision = captured.suspensionRevision; release = captured.release; bridge=captured.bridge;
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
            bridge=bridgeFinalization!=null && bridgeFinalization.request==request?bridgeFinalization:null;
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
            if(request.bridgeScope!=null && !sameFrame())return false;
            if(request.bridgeCandidate!=null && (!request.bridgeCandidate.consumed
                    || !bridgeCandidateCurrent(request.bridgeCandidate)))return false;
            if(bridge!=null && !bridge.current())return false;
            if(request.bridgeScope!=null && !bridgeCameraMovement(request.bridgeScope.live()))return false;
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
                    && (release == null || currentRotationRelease() == release)
                    && (bridge==null || bridge.current())
                    && (request.bridgeCandidate==null || bridgeCandidateCurrent(request.bridgeCandidate))
                    && (request.bridgeScope==null || bridgeCameraMovement(request.bridgeScope.live()));
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
        final MovementFrame appliedMovement = movementFrame;
        movementFrame = null;
        bridgeCandidate=null;bridgeFinalization=null;
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
        if (!accepted) { cancelVisibleAssistStop(); invalidateUnownedSpeedCurves(null); return; }
        hasEffective = true;
        effectiveYaw = yaw;
        effectivePitch = clamp(pitch, -90, 90);
        owner = winner != null && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw - yaw)) < .001f
                && Math.abs(winner.pitch - pitch) < .001f ? winner.source : null;
        if (rotationRelease != null && rotationRelease.releaseSource.equals(owner)) {
            rotationRelease.lastYaw = effectiveYaw;
            rotationRelease.lastPitch = effectivePitch;
            if (appliedMovement != null && appliedMovement.returnAfterMovement == rotationRelease) {
                rotationRelease.yaw = effectiveYaw;
                rotationRelease.pitch = effectivePitch;
            }
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
        if (!VISIBLE_STOP.equals(owner)) cancelVisibleAssistStop();
        if (owner != null) {
            RotationState state = state(owner);
            state.visibleStep = winner.visibleStep;
            state.yaw = effectiveYaw;
            state.pitch = effectivePitch;
            state.active = true;
        }
    }

    private void invalidateUnownedSpeedCurves(String acceptedSource) {
        for (Map.Entry<String,RotationState> entry : states.entrySet()) {
            RotationTrajectory trajectory=entry.getValue().trajectory;
            if(trajectory.hasSpeedCurve() && !entry.getKey().equals(acceptedSource))trajectory.resetSpeedCurve();
            if(!entry.getKey().equals(acceptedSource)) {
                entry.getValue().arsenic.reset();
                entry.getValue().invalidateAdaptive();
                if(entry.getValue().bridgeMotion!=null) {
                    entry.getValue().bridgeMotion.rejectMotion(sequence);
                    revokeBridgeCandidate(entry.getValue().bridgeMotion,sequence,null);
                }
                // A first-frame neural HOLD is not an offer; it remains warm.
                if(entry.getValue().aiOfferedSequence==sequence)entry.getValue().invalidateAi();
            }
        }
    }

    /** Read the existing monotonic source without advancing any motion, trajectory or action state. */
    public long readMonotonicNanos() { return clock.getAsLong(); }

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

    /** Scalar observation only: unlike authority getters, this never expires a lease or evaluates a predicate. */
    public DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(sequence, collecting, accepted, hasEffective, owner,
                winner == null ? null : winner.source, winner == null ? 0 : winner.priority,
                winner == null ? Float.NaN : winner.yaw, winner == null ? Float.NaN : winner.pitch,
                effectiveYaw, effectivePitch, offeredSources.size(), rotationRelease != null);
    }
    public DiagnosticSnapshot diagnosticSnapshot(Object player,Object world,Object pipeline) {
        return pipeline!=null&&connection.get()==pipeline&&motionPlayer.get()==player&&motionWorld.get()==world
                ?diagnosticSnapshot():new DiagnosticSnapshot(sequence,false,false,false,null,null,0,
                Float.NaN,Float.NaN,Float.NaN,Float.NaN,0,false);
    }
    public static final class DiagnosticSnapshot {
        public final long sequence;
        public final boolean collecting, accepted, hasEffective, returning;
        public final String owner, requestedBy;
        public final int priority, contenders;
        public final float requestedYaw, requestedPitch, wireYaw, wirePitch;
        DiagnosticSnapshot(long sequence, boolean collecting, boolean accepted, boolean hasEffective, String owner,
                           String requestedBy, int priority, float requestedYaw, float requestedPitch,
                           float wireYaw, float wirePitch, int contenders, boolean returning) {
            this.sequence=sequence;this.collecting=collecting;this.accepted=accepted;this.hasEffective=hasEffective;
            this.owner=owner;this.requestedBy=requestedBy;this.priority=priority;this.requestedYaw=requestedYaw;
            this.requestedPitch=requestedPitch;this.wireYaw=wireYaw;this.wirePitch=wirePitch;
            this.contenders=contenders;this.returning=returning;
        }
    }

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
        if (visibleAssistStop != null && !(collecting && winner != null && VISIBLE_STOP.equals(winner.source)
                && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw - yaw)) < .001f
                && Math.abs(winner.pitch - pitch) < .001f)) cancelVisibleAssistStop();
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
        if(target==null || mc==null || mc.thePlayer==null) {
            if(profile!=null && (profile.adaptive||profile.aiLearned)) {
                RotationState old=states.get(source);
                if(old!=null) {old.invalidateAdaptive();old.invalidateAi();}
            }
            return null;
        }
        RotationState state=state(source);
        if(!profile.adaptive) state.invalidateAdaptive();
        if(!profile.aiLearned) state.invalidateAi();
        state.softVisible = visible && profile.arsenic != RotationProfile.ArsenicMode.OFF && profile.softArsenicMotion;
        AxisAlignedBB box=target.getEntityBoundingBox();
        Vec3 feet=new Vec3(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        if(!visible && (profile.adaptive || profile.arsenic!=RotationProfile.ArsenicMode.OFF || profile.smartAim || profile.stopOnTarget || profile.heuristics))feet=mc.thePlayer.getPlannedMovementPosition(feet.xCoord,feet.yCoord,feet.zCoord);
        Vec3 eyes=feet.addVector(0,mc.thePlayer.getEyeHeight(),0);
        float fromYaw=visible?cameraYaw:collecting?baseYaw:state.active?state.yaw:cameraYaw;
        float fromPitch=visible?cameraPitch:collecting?basePitch:state.active?state.pitch:cameraPitch;
        if(profile.aiLearned) {
            if(!"aura".equals(source) || visible) {state.invalidateAi();return null;}
            return aiRotations(state,target,box,eyes,profile,fromYaw,fromPitch);
        }
        if(!profile.adaptive && profile.stopOnTarget && rays.canHitFrom(feet,target,fromYaw,fromPitch,profile.reach,profile.throughWalls)) {
            state.invalidateAdaptive();
            state.arsenic.reset();
            if(profile.speedCurve==RotationProfile.SpeedCurve.BEZIER) {
                if(collecting)state.trajectory.pauseMotion(target,sequence,curveMotionTime());
            } else state.trajectory.pause(target);
            return new float[]{fromYaw,fromPitch};
        }
        if(profile.adaptive) {
            if(!"aura".equals(source) || visible) { state.invalidateAdaptive(); return null; }
            return adaptiveRotations(state,target,box,feet,eyes,bestHitVec,profile,fromYaw,fromPitch);
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

    /** AI Learned is an opt-in mathematical candidate, never rotation/attack authority. */
    private float[] aiRotations(RotationState s,EntityLivingBase target,AxisAlignedBB box,
                                Vec3 eyes,RotationProfile profile,float fromYaw,float fromPitch) {
        if(personalModelGate!=null && !personalModelGate.current(mc)) {
            clearAiPersonalCheckpoint();return null;
        }
        // Render/POST callbacks cannot spend a second neural sample.
        if(!collecting && mc!=null && mc.currentScreen==null && mc.inGameHasFocus)return null;
        if(!collecting || mc==null || mc.thePlayer==null || mc.theWorld==null
                || mc.gameSettings==null || mc.currentScreen!=null || !mc.inGameHasFocus
                || mc.isGamePaused() || mc.getRenderViewEntity()!=mc.thePlayer
                || motionPlayer.get()!=mc.thePlayer || motionWorld.get()!=mc.theWorld
                || mc.getSession()==null || target.isDead || !target.isEntityAlive()
                || !mc.thePlayer.canEntityBeSeen(target) || mc.thePlayer.sendQueue==null
                || mc.thePlayer.sendQueue.getNetworkManager()==null) {
            s.invalidateAi();return null;
        }
        PacketPipeline pipeline=mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        RuntimeObservation obs=RuntimeServices.observation();
        SceneContext scene=obs==null?null:obs.captureScene();
        if(pipeline==null || !pipeline.isActive() || !isConnection(pipeline)
                || scene==null || !scene.isValid() || scene.isSuspended()
                || obs.getActiveOuterTickId()<0 || !obs.isCurrent(scene)) {
            s.invalidateAi();return null;
        }
        if(s.aiBlockedSequence==sequence)return null;
        if(s.ai==null || !s.ai.hasModel()) {s.invalidateAi();return null;}
        Object account=mc.getSession();
        if(s.aiScene!=null && (!s.aiScene.sameSceneAs(scene)
                || s.aiPlayer.get()!=mc.thePlayer || s.aiWorld.get()!=mc.theWorld
                || s.aiConnection.get()!=pipeline || s.aiAccount.get()!=account)) {
            // Never move a personal model across world, player, connection or account.
            s.ai.dispose();s.invalidateAi();return null;
        }
        if(owner!=null && previousAccepted && !"aura".equals(owner)) {
            s.invalidateAi();return null;
        }
        if(s.aiToken==null || s.aiTarget.get()!=target
                || s.aiSuspension!=obs.getObservedSuspensionRevision()) {
            if(s.aiLastSequence==sequence) {
                s.invalidateAi();s.aiBlockedSequence=sequence;return null;
            }
            s.invalidateAi();
            s.aiToken=new NeutronAiCore.Acquisition(s,mc.theWorld,pipeline,target,account,
                    scene.getConnectionGeneration(),++s.aiOrdinal);
            s.aiScene=scene;
            s.aiTarget=new WeakReference<>(target);s.aiWorld=new WeakReference<>(mc.theWorld);
            s.aiPlayer=new WeakReference<>(mc.thePlayer);s.aiConnection=new WeakReference<>(pipeline);
            s.aiAccount=new WeakReference<>(account);
            s.aiSuspension=obs.getObservedSuspensionRevision();
        }

        NeutronAiFeatures.Observation o=new NeutronAiFeatures.Observation();
        o.eyeX=eyes.xCoord;o.eyeY=eyes.yCoord;o.eyeZ=eyes.zCoord;
        o.targetX=(box.minX+box.maxX)*.5;
        o.targetY=(box.minY+box.maxY)*.5;
        o.targetZ=(box.minZ+box.maxZ)*.5;
        o.yaw=fromYaw;o.pitch=fromPitch;
        o.playerVx=sampleDelta(mc.thePlayer.posX-mc.thePlayer.lastTickPosX);
        o.playerVy=sampleDelta(mc.thePlayer.posY-mc.thePlayer.lastTickPosY);
        o.playerVz=sampleDelta(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ);
        o.targetVx=sampleDelta(target.posX-target.lastTickPosX);
        o.targetVy=sampleDelta(target.posY-target.lastTickPosY);
        o.targetVz=sampleDelta(target.posZ-target.lastTickPosZ);
        o.targetWidth=box.maxX-box.minX;o.targetHeight=box.maxY-box.minY;
        o.targetDepth=box.maxZ-box.minZ;o.reach=profile.reach;
        o.visible=mc.thePlayer.canEntityBeSeen(target);
        o.targetAlive=target.isEntityAlive();
        o.playerGrounded=mc.thePlayer.onGround;o.targetGrounded=target.onGround;
        if(!NeutronAiFeatures.valid(o)){s.invalidateAi();return null;}
        long suspension=obs.getObservedSuspensionRevision();
        NeutronAiCore.Candidate candidate=s.ai.advance(s.aiToken,o,sequence,curveMotionTime(),true);
        s.aiLastSequence=sequence;
        // Core can HOLD because it has settled OR because a pause/backwards
        // clock invalidated its acquisition. Only the former may retain a
        // previously accepted same-target Aura C03.
        if(!s.ai.hasCurrentAcquisition(s.aiToken)) {s.invalidateAi();return null;}
        if(!collecting || !obs.isCurrent(scene)
                || obs.getObservedSuspensionRevision()!=suspension
                || mc.currentScreen!=null || !mc.inGameHasFocus) {
            s.invalidateAi();return null;
        }
        if(!candidate.hasRotation)return aiAcceptedHold(s,fromYaw,fromPitch);
        float wanted=fromYaw+MathHelper.wrapAngleTo180_float((float)candidate.yaw-fromYaw);
        float[] snapped=quantize(wanted,(float)candidate.pitch,fromYaw,fromPitch,
                mc.gameSettings.mouseSensitivity);
        if(!Float.isFinite(snapped[0]) || !Float.isFinite(snapped[1])) {
            s.invalidateAi();return null;
        }
        if(Math.abs(MathHelper.wrapAngleTo180_float(snapped[0]-fromYaw))<.0001f
                && Math.abs(snapped[1]-fromPitch)<.0001f)
            return aiAcceptedHold(s,fromYaw,fromPitch);
        s.aiOfferedSequence=sequence;
        return snapped;
    }

    /**
     * Reoffer only the *already accepted* prior AI pose for a still-live
     * acquisition. This must win fresh request arbitration and fresh C03
     * acceptance before the existing controller/raycast can act in POST.
     * A first-sample HOLD, foreign owner, rejected packet, changed target or
     * lost Core acquisition can never take this path.
     */
    private float[] aiAcceptedHold(RotationState state,float yaw,float pitch) {
        if(previousAccepted && "aura".equals(owner)
                && state.aiOfferedSequence!=Long.MIN_VALUE
                && state.aiOfferedSequence+1==sequence) {
            state.aiOfferedSequence=sequence;
            return new float[]{yaw,pitch};
        }
        return null;
    }

    /**
     * Adaptive is only an Aura candidate consumer. The existing Request arbitration,
     * sensitivity grid, transport-accepted C03 and AttackScheduler remain authoritative.
     * No new packet/action path exists here.
     */
    private float[] adaptiveRotations(RotationState state, EntityLivingBase target, AxisAlignedBB box,
                                      Vec3 feet, Vec3 eyes, boolean bestHitVec,
                                      RotationProfile profile, float fromYaw, float fromPitch) {
        // An unrelated POST/render observer cannot spend a second budget or reset a live profile.
        if(!collecting && mc!=null && mc.currentScreen==null && mc.inGameHasFocus)
            return new float[]{fromYaw,fromPitch};
        if(!collecting || mc==null || mc.thePlayer==null || mc.theWorld==null
                || mc.gameSettings==null || mc.currentScreen!=null || !mc.inGameHasFocus
                || mc.isGamePaused() || mc.getRenderViewEntity()!=mc.thePlayer
                || motionPlayer.get()!=mc.thePlayer || motionWorld.get()!=mc.theWorld
                || !Float.isFinite(profile.yawSpeed) || !Float.isFinite(profile.pitchSpeed)
                || profile.yawSpeed<=0 || profile.pitchSpeed<=0) {
            state.invalidateAdaptive();
            return null;
        }
        final RuntimeObservation observation=RuntimeServices.observation();
        final SceneContext scene=observation==null?null:observation.captureScene();
        if(scene==null || !scene.isValid() || scene.isSuspended()
                || observation.getActiveOuterTickId()<0
                || !isConnection(mc.thePlayer.sendQueue==null?null:
                        mc.thePlayer.sendQueue.getNetworkManager().getClientPackets())) {
            state.invalidateAdaptive();
            return null;
        }
        final AdaptivePresetMotion.Configuration preset;
        try {
            preset = new AdaptivePresetMotion.Configuration(profile.adaptiveSpeed,
                    profile.adaptiveHitpoint, profile.adaptiveVariation,
                    profile.yawSpeed, profile.pitchSpeed);
        } catch (IllegalArgumentException badPreset) {
            state.invalidateAdaptive();
            return null;
        }
        // Changed tuning invalidates the previous acquisition and recreates its fixed limits.
        if(state.adaptivePreset!=null && !state.adaptivePreset.same(preset)) {
            state.invalidateAdaptive();
            state.adaptive=null;
            state.adaptivePreset=null;
        }
        // No second acquisition is permitted within a consumed physical motion sequence.
        if(state.adaptiveBlockedSequence==sequence) return new float[]{fromYaw,fromPitch};
        // A previously accepted different owner cannot carry an Adaptive trajectory.
        if(owner!=null && previousAccepted && !"aura".equals(owner)) {
            state.invalidateAdaptive();
            return new float[]{fromYaw,fromPitch};
        }
        final long suspension=observation.getObservedSuspensionRevision();
        final boolean acquire=state.adaptiveToken==null || state.adaptiveTarget.get()!=target
                || state.adaptiveScene==null || !state.adaptiveScene.sameSceneAs(scene)
                || state.adaptiveSuspension!=suspension;
        // A target change cannot perform another aim search or acquisition after
        // this physical sequence has already spent its motion budget.
        if(acquire && state.adaptiveLastSequence==sequence) {
            state.invalidateAdaptive();
            state.adaptiveBlockedSequence=sequence;
            return new float[]{fromYaw,fromPitch};
        }
        // Demise's update-on-miss idea is bound to this exact target, current
        // scene and the existing range/obstruction checks. Holding still consumes
        // the normal motion clock below; it cannot accumulate a later turn burst.
        boolean keepAim=(profile.adaptiveKeepAim || profile.stopOnTarget)
                && rays.canHitFrom(feet,target,fromYaw,fromPitch,profile.reach,profile.throughWalls);
        Vec3 point=baseAimPoint(target,bestHitVec,box);
        if(!keepAim && profile.adaptiveAimPoint!=AdaptivePresetMotion.AimPoint.LEGACY) {
            double[] anchor=AdaptivePresetMotion.aimPoint(profile.adaptiveAimPoint,
                    eyes.xCoord,eyes.yCoord,eyes.zCoord,box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);
            if(anchor!=null)point=new Vec3(anchor[0],anchor[1],anchor[2]);
        }
        if(!keepAim && (profile.smartAim || profile.heuristics || !hitsPoint(rays,feet,target,point,profile)))
            point=visibleAimPoint(target,box,point,feet,fromYaw,fromPitch,profile,rays);
        if(point==null || !observation.isCurrent(scene)) {
            state.invalidateAdaptive();
            return null;
        }
        final long now=curveMotionTime();
        if(acquire) {
            state.invalidateAdaptive();
            if(state.adaptive==null) {
                state.adaptive=new AdaptiveMotionController(state.adaptiveSeed, preset.limits());
                state.adaptivePreset=preset;
            }
            state.adaptiveToken=new AdaptiveMotionController.Acquisition(state,mc.theWorld,target,
                    scene.getConnectionGeneration(),++state.adaptiveOrdinal);
            state.adaptiveTarget=new WeakReference<>(target);
            state.adaptiveScene=scene;
            state.adaptiveSuspension=suspension;
            state.adaptiveStartNanos=now;
            state.adaptiveHitpoint=new AdaptivePresetMotion.HitpointTrajectory(
                    state.adaptiveSeed ^ (state.adaptiveOrdinal * 0x9e3779b97f4a7c15L),
                    preset.hitpoint);
        }
        // Relative monotonic time is safe even when the VM's System.nanoTime origin is negative.
        long elapsed=now-state.adaptiveStartNanos;
        if(elapsed<0) { state.invalidateAdaptive(); return null; }
        if(state.adaptiveHitpoint==null) { state.invalidateAdaptive(); return null; }
        double[] hit=state.adaptiveHitpoint.point(
                point.xCoord,point.yCoord,point.zCoord,
                box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,
                elapsed * 1e-9);
        if(hit==null) { state.invalidateAdaptive(); return null; }
        if(!keepAim && preset.hitpoint!=AdaptivePresetMotion.Hitpoint.STABLE) {
            Vec3 candidate=new Vec3(hit[0],hit[1],hit[2]);
            // Preserve the original raycast/visibility policy: an invalid point is never sent.
            if(hitsPoint(rays,feet,target,candidate,profile)) point=candidate;
        }
        final float[] desired=keepAim?new float[]{fromYaw,fromPitch}:angles(eyes,point);
        AdaptiveMotionController.Result result=state.adaptive.advance(state.adaptiveToken,sequence,
                elapsed,fromYaw,fromPitch,desired[0],desired[1],true);
        state.adaptiveLastSequence=sequence;
        if(state.adaptive.profile()==null) {
            state.adaptiveBlockedSequence=sequence;
            state.invalidateAdaptive(); // Deadline/long-pause tombstone; next motion re-acquires.
            return new float[]{fromYaw,fromPitch};
        }
        if(!observation.isCurrent(scene) || observation.getObservedSuspensionRevision()!=suspension
                || !collecting || mc.currentScreen!=null || !mc.inGameHasFocus) {
            state.invalidateAdaptive();
            return null;
        }
        // Unwrap around the accepted pose *before* Neutron's nearest-mouse-grid rounding.
        float wantedYaw=fromYaw+MathHelper.wrapAngleTo180_float((float)result.yaw-fromYaw);
        float[] snapped=quantize(wantedYaw,(float)result.pitch,fromYaw,fromPitch,
                mc.gameSettings.mouseSensitivity);
        if(!Float.isFinite(snapped[0]) || !Float.isFinite(snapped[1])) {
            state.invalidateAdaptive();
            return null;
        }
        return snapped;
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
        // Score each point once; stable sorting preserves base/grid ordering on ties.
        Map<Vec3,Double> scores = new java.util.IdentityHashMap<>();
        for (Vec3 point : candidates) {
            float[] a=angles(eyes,point);double dy=MathHelper.wrapAngleTo180_float(a[0]-yaw),dp=a[1]-pitch;
            scores.put(point,dy*dy+dp*dp);
        }
        candidates.sort(Comparator.comparingDouble(scores::get));
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

    /** A checked Scaffold face proposal; this method never requests rotation ownership. */
    public Vec3 bridgeFacePoint(AdaptiveBridgePlanner.Scope scope,Object identity,AxisAlignedBB bounds,
                               net.minecraft.util.EnumFacing face,Vec3 originalAnchor,
                               AdaptiveBridgeMotion.Settings settings) {
        if(Thread.currentThread()!=bridgeOwningThread)return null;
        if(identity==null || bounds==null || face==null || originalAnchor==null || settings==null)return null;
        final long motion=sequence;
        RuntimeObservation observation=RuntimeServices.observation();
        SceneContext scene=bridgeScene(scope,observation,motion);
        if(scene==null)return null;
        RotationState state=state("scaffold");
        if(state.bridgeMotion==null)state.bridgeMotion=new AdaptiveBridgeMotion(state.adaptiveSeed ^ 0x315bc74d9f42a68eL);
        AdaptiveBridgeMotion.Point point=state.bridgeMotion.point(scope,identity,settings,motion,curveMotionTime(),
                AdaptiveBridgePlanner.Face.valueOf(face.name()),bounds.minX,bounds.minY,bounds.minZ,
                bounds.maxX,bounds.maxY,bounds.maxZ,originalAnchor.xCoord,originalAnchor.yCoord,originalAnchor.zCoord,true);
        if(point==null || !currentBridgeScene(scope,observation,scene,motion,state)) {
            rejectBridgeProposal(state,scope,identity,motion);return null;
        }
        return new Vec3(point.x,point.y,point.z);
    }

    /**
     * Separate opt-in entry: old calculatePlanRotations, Aura and their clocks are unchanged.
     * Dynamics start from this motion's accepted base, followed by the existing final mouse grid.
     */
    public float[] calculateBridgePlanRotations(AdaptiveBridgePlanner.Scope scope,Object identity,
                                               float targetYaw,float targetPitch,
                                               AdaptiveBridgeMotion.Settings settings) {
        return bridgePlanRotations(scope,identity,targetYaw,targetPitch,settings,null);
    }

    /** Task urgency changes only the candidate mathematics; ownership and the mouse grid stay here. */
    public float[] calculateBridgePlanRotations(AdaptiveBridgePlanner.Scope scope,Object identity,
                                               float targetYaw,float targetPitch,
                                               AdaptiveBridgeMotion.Settings settings,
                                               AdaptiveBridgeMotion.Task task) {
        if(task==null)return null;
        return bridgePlanRotations(scope,identity,targetYaw,targetPitch,settings,task);
    }

    private float[] bridgePlanRotations(AdaptiveBridgePlanner.Scope scope,Object identity,
                                       float targetYaw,float targetPitch,
                                       AdaptiveBridgeMotion.Settings settings,AdaptiveBridgeMotion.Task task) {
        if(Thread.currentThread()!=bridgeOwningThread)return null;
        if(identity==null || settings==null)return null;
        final long motion=sequence;
        RuntimeObservation observation=RuntimeServices.observation();
        SceneContext scene=bridgeScene(scope,observation,motion);
        if(scene==null)return null;
        RotationState state=state("scaffold");
        if(state.bridgeMotion==null)state.bridgeMotion=new AdaptiveBridgeMotion(state.adaptiveSeed ^ 0x315bc74d9f42a68eL);
        double yaw,pitch;
        if(task==null) {
            AdaptiveMotionController.Result next=state.bridgeMotion.advance(scope,identity,settings,motion,curveMotionTime(),
                    baseYaw,basePitch,targetYaw,targetPitch,true);
            if(next==null) {rejectBridgeProposal(state,scope,identity,motion);return null;}
            yaw=next.yaw;pitch=next.pitch;
        } else {
            AdaptiveBridgeMotion.Turn next=state.bridgeMotion.advanceTask(scope,identity,settings,task,motion,curveMotionTime(),
                    baseYaw,basePitch,targetYaw,targetPitch,true);
            if(next==null) {rejectBridgeProposal(state,scope,identity,motion);return null;}
            yaw=next.yaw;pitch=next.pitch;
        }
        if(!currentBridgeScene(scope,observation,scene,motion,state)) {
            rejectBridgeProposal(state,scope,identity,motion);return null;
        }
        float wantedYaw=baseYaw+MathHelper.wrapAngleTo180_float((float)yaw-baseYaw);
        float[] snapped=quantize(wantedYaw,(float)pitch,baseYaw,basePitch,mc.gameSettings.mouseSensitivity);
        if(!Float.isFinite(snapped[0]) || !Float.isFinite(snapped[1])) {
            rejectBridgeProposal(state,scope,identity,motion);return null;
        }
        if(task!=null && (bridgeCandidate==null || bridgeCandidate.sequence!=motion
                || !bridgeCandidate.scope.same(scope)))
            bridgeCandidate=new BridgeRotationCandidate(scope,state.bridgeMotion,identity,motion,snapped[0],snapped[1]);
        return snapped;
    }

    private void rejectBridgeProposal(RotationState state,AdaptiveBridgePlanner.Scope scope,Object identity,long motion) {
        state.bridgeMotion.rejectMotion(scope,identity,motion);
        revokeBridgeCandidate(state.bridgeMotion,motion,scope);
    }

    /** A stale controller/scope callback must never revoke a replacement proposal. */
    private void revokeBridgeCandidate(AdaptiveBridgeMotion controller,long motion,AdaptiveBridgePlanner.Scope scope) {
        BridgeRotationCandidate candidate=bridgeCandidate;
        if(candidate==null || candidate.controller!=controller || candidate.sequence!=motion
                || scope!=null && !candidate.scope.same(scope))return;
        bridgeCandidate=null;
        if(bridgeFinalization!=null && bridgeFinalization.candidate==candidate)bridgeFinalization=null;
    }

    /** A stale acquisition cannot dispose a replacement source's active motion. */
    public void resetBridgeMotion(AdaptiveBridgePlanner.Scope expected) {
        if(Thread.currentThread()!=bridgeOwningThread)return;
        if(bridgeCandidate!=null && bridgeCandidate.scope.same(expected))bridgeCandidate=null;
        if(bridgeFinalization!=null && bridgeFinalization.scope.same(expected))bridgeFinalization=null;
        RotationState state=states.get("scaffold");
        if(state!=null && state.bridgeMotion!=null)state.bridgeMotion.reset(expected);
    }

    private SceneContext bridgeScene(AdaptiveBridgePlanner.Scope scope,RuntimeObservation observation,long motion) {
        if(!bridgeContext(scope) || observation==null || observation.getActiveOuterTickId()<0)return null;
        SceneContext scene=observation.captureScene();
        if(scene==null || !scene.isValid() || scene.isSuspended() || sequence!=motion || !bridgeContext(scope))return null;
        boolean current=observation.isCurrent(scene);
        return current && sequence==motion && RuntimeServices.observation()==observation && bridgeContext(scope)?scene:null;
    }

    private boolean currentBridgeScene(AdaptiveBridgePlanner.Scope scope,RuntimeObservation observation,
                                       SceneContext scene,long motion,RotationState state) {
        if(sequence!=motion || states.get("scaffold")!=state || !bridgeContext(scope)
                || RuntimeServices.observation()!=observation)return false;
        boolean current=observation.isCurrent(scene);
        return current && sequence==motion && states.get("scaffold")==state && bridgeContext(scope)
                && RuntimeServices.observation()==observation;
    }

    private boolean bridgeContext(AdaptiveBridgePlanner.Scope scope) {
        if(!collecting || scope==null || scope.owner==null || scope.acquisitionGeneration<0
                || scope.connectionEpoch<0 || mc==null || mc.thePlayer==null || mc.theWorld==null
                || mc.gameSettings==null || mc.currentScreen!=null || !mc.inGameHasFocus || mc.isGamePaused()
                || mc.getRenderViewEntity()!=mc.thePlayer || motionPlayer.get()!=scope.player
                || motionWorld.get()!=scope.world || mc.thePlayer!=scope.player || mc.theWorld!=scope.world
                || !Float.isFinite(baseYaw) || !Float.isFinite(basePitch)
                || !Float.isFinite(mc.gameSettings.mouseSensitivity)
                || mc.thePlayer.sendQueue==null || mc.thePlayer.sendQueue.getNetworkManager()==null)return false;
        PacketPipeline packets=mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        if(packets==null || packets!=scope.connection || !packets.isActive()
                || packets.getContextVersion()!=scope.connectionEpoch || !isConnection(packets))return false;
        me.youngjibbit.module.ModuleManager modules=Pulsar.INSTANCE.getMm();
        if(modules==null || modules.getModule(me.youngjibbit.module.impl.world.Scaffold.class)!=scope.owner)return false;
        me.youngjibbit.module.impl.world.Scaffold scaffold=(me.youngjibbit.module.impl.world.Scaffold)scope.owner;
        if(!scaffold.isCurrentBridgeScope(scope))return false;
        // Respect a winning foreign request already known during PRE collection.
        return winner==null || "scaffold".equals(winner.source) || winner.priority<SCAFFOLD_PRIORITY
                || winner.priority==SCAFFOLD_PRIORITY && winner.source.compareTo("scaffold")>0;
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
        if("scaffold".equals(source)) {bridgeCandidate=null;bridgeFinalization=null;}
        if("aura".equals(source) && personalModelGate!=null) {
            RotationState prior=states.get("aura");
            if(prior!=null && prior.ai!=null)prior.ai.dispose();
            personalModelGate=null;
        }
        if (releaseAcceptance != null && source.equals(releaseAcceptance.source)) releaseAcceptance = null;
        if (isReleaseSource(source) && rotationRelease != null && source.equals(rotationRelease.releaseSource)) cancelRotationRelease();
        RotationState discarded=states.get(source);
        if(discarded!=null && discarded.bridgeMotion!=null)discarded.bridgeMotion.reset();
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
        if(personalModelGate!=null) {
            RotationState prior=states.get("aura");
            if(prior!=null && prior.ai!=null)prior.ai.dispose();
            personalModelGate=null;
        }
        cancelVisibleAssistStop();
        movementFrame = null;
        bridgeCandidate=null;bridgeFinalization=null;
        curveMotionSequence=Long.MIN_VALUE;
        rotationRelease = null;
        releaseAcceptance = null;
        offeredSources.clear();
        cameraIntent = false;
        RotationCorrections.reset();
        for(RotationState discarded:states.values())if(discarded.bridgeMotion!=null)discarded.bridgeMotion.reset();
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
            state = new RotationState(new RotationTrajectory(clock,(int)trajectorySeed),
                    new ArsenicAimTrajectory(trajectorySeed),trajectorySeed ^ 0x5ea31cb92af17d4cL);
            states.put(key, state);
        }
        return state;
    }

    private static long sourceHash(String key) { return key.hashCode()*0x9e3779b97f4a7c15L; }

    private static final class RotationState {
        final RotationTrajectory trajectory;
        final ArsenicAimTrajectory arsenic;
        final long adaptiveSeed;
        AdaptiveMotionController adaptive;
        AdaptiveBridgeMotion bridgeMotion;
        NeutronAiCore ai;
        NeutronAiCore.Acquisition aiToken;
        SceneContext aiScene;
        WeakReference<Object> aiTarget=new WeakReference<>(null),aiPlayer=new WeakReference<>(null),
                aiWorld=new WeakReference<>(null),aiAccount=new WeakReference<>(null),
                aiConnection=new WeakReference<>(null);
        long aiOrdinal,aiSuspension;
        long aiLastSequence=Long.MIN_VALUE,aiBlockedSequence=Long.MIN_VALUE,
                aiOfferedSequence=Long.MIN_VALUE;
        AdaptivePresetMotion.Configuration adaptivePreset;
        AdaptivePresetMotion.HitpointTrajectory adaptiveHitpoint;
        AdaptiveMotionController.Acquisition adaptiveToken;
        SceneContext adaptiveScene;
        WeakReference<Object> adaptiveTarget=new WeakReference<>(null);
        long adaptiveOrdinal,adaptiveStartNanos,adaptiveSuspension;
        long adaptiveLastSequence=Long.MIN_VALUE,adaptiveBlockedSequence=Long.MIN_VALUE;
        RotationState(RotationTrajectory trajectory,ArsenicAimTrajectory arsenic,long adaptiveSeed) {
            this.trajectory=trajectory;this.arsenic=arsenic;this.adaptiveSeed=adaptiveSeed;
        }
        void invalidateAdaptive() {
            if(adaptive!=null && adaptiveToken!=null) adaptive.invalidateIfCurrent(adaptiveToken);
            adaptiveToken=null;
            adaptiveHitpoint=null;
            adaptiveScene=null;
            adaptiveTarget.clear();
        }
        void invalidateAi() {
            if(ai!=null && aiToken!=null)ai.invalidateIfCurrent(aiToken);
            aiToken=null;aiScene=null;aiOfferedSequence=Long.MIN_VALUE;
            aiTarget.clear();aiPlayer.clear();aiWorld.clear();
            aiAccount.clear();aiConnection.clear();
        }
        private float yaw;
        private float pitch;
        private boolean active, softVisible;
        private VisibleAssistStop visibleStep;

    }

    private static final class Request {
        final String source;
        final int priority;
        float yaw,pitch;
        final boolean visible;
        final float cameraYaw,cameraPitch;
        final BooleanSupplier valid;
        BridgeMotionScope bridgeScope;
        BridgeRotationCandidate bridgeCandidate;
        VisibleAssistStop visibleStep;
        Request(String source,int priority,float yaw,float pitch,boolean visible,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
            this.source=source;this.priority=priority;this.yaw=yaw;this.pitch=pitch;
            this.visible=visible;this.cameraYaw=cameraYaw;this.cameraPitch=cameraPitch;this.valid=valid;
        }
    }
}
