package me.youngjibbit.module.impl.client;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.CombatCorrectionPreview;
import me.youngjibbit.core.combat.CombatDiagnosticTrace;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.*;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.AttackResultEvent;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

/**
 * Read-only client-side diagnostics for controlled test servers.
 * Never changes a rotation, attack, packet, or server decision.
 * Server anticheat flags require authoritative server logs.
 */
@ModuleInfo(name = "CombatDiagnostics", description = "Local rotation/attack trace; not a server flag detector", category = Category.Client)
public final class CombatDiagnostics extends Module {
    private final CombatDiagnosticTrace history = new CombatDiagnosticTrace(120);
    private final CheckBoxValue consoleLog = new CheckBoxValue("Console Attack Log", this, false);
    private long postSequence = Long.MIN_VALUE, eventId;
    private float postCameraYaw, postCameraPitch;
    private MovementSample lastMovement;
    private WeakReference<Object> traceObserver = new WeakReference<>(null), tracePlayer = new WeakReference<>(null),
            traceWorld = new WeakReference<>(null), tracePipeline = new WeakReference<>(null);
    private long traceGeneration=Long.MIN_VALUE, traceEpoch=Long.MIN_VALUE;

    /** Immutable last observed moveFlying input. Does not influence acceleration or packets. */
    private static final class MovementSample {
        final int tick;
        final long timestampMs;
        final float rawStrafe, rawForward, rawYaw;
        final float finalStrafe, finalForward, finalYaw, friction;

        MovementSample(int tick, long timestampMs, float rawStrafe, float rawForward, float rawYaw,
                       float finalStrafe, float finalForward, float finalYaw, float friction) {
            this.tick = tick;
            this.timestampMs = timestampMs;
            this.rawStrafe = rawStrafe;
            this.rawForward = rawForward;
            this.rawYaw = rawYaw;
            this.finalStrafe = finalStrafe;
            this.finalForward = finalForward;
            this.finalYaw = finalYaw;
            this.friction = friction;
        }

        String describe(int currentTick, long currentMs) {
            return String.format(Locale.ROOT,
                    "moveAgeTicks=%d moveAgeMs=%d moveRaw=%.3f/%.3f@%.2f moveApplied=%.3f/%.3f@%.2f moveFriction=%.4f",
                    currentTick - tick, currentMs - timestampMs,
                    rawStrafe, rawForward, rawYaw, finalStrafe, finalForward, finalYaw, friction);
        }

        String compact() {
            return String.format(Locale.ROOT, "input S/F %.2f/%.2f -> %.2f/%.2f  yaw %.1f -> %.1f",
                    rawStrafe, rawForward, finalStrafe, finalForward, rawYaw, finalYaw);
        }
    }

    /**
     * Read-only observation at the actual moveFlying consumer, AFTER bus listeners and MoveFix
     * have selected their final inputs. No event is posted and no movement is changed.
     */
    private static CombatDiagnostics active() {
        if (Pulsar.INSTANCE == null || Pulsar.INSTANCE.getMm() == null) return null;
        CombatDiagnostics diagnostics =
                (CombatDiagnostics) Pulsar.INSTANCE.getMm().getModule(CombatDiagnostics.class);
        return diagnostics != null && diagnostics.isToggled() && diagnostics.inWorld() ? diagnostics : null;
    }

    /** Observation only: these reads never re-run admission, raycast or transport predicates. */
    private void refreshRun() {
        RuntimeObservation observation=RuntimeServices.observation();
        PacketPipeline pipeline=mc.thePlayer == null || mc.thePlayer.sendQueue == null ? null
                : mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        long generation=observation == null ? -1 : observation.getObservedConnectionGeneration();
        long epoch=pipeline == null ? -1 : pipeline.getContextVersion();
        if (traceObserver.get()!=observation || tracePlayer.get()!=mc.thePlayer || traceWorld.get()!=mc.theWorld
                || tracePipeline.get()!=pipeline || traceGeneration!=generation || traceEpoch!=epoch) {
            history.startRun();
            traceObserver=new WeakReference<>(observation);tracePlayer=new WeakReference<>(mc.thePlayer);
            traceWorld=new WeakReference<>(mc.theWorld);tracePipeline=new WeakReference<>(pipeline);
            traceGeneration=generation;traceEpoch=epoch;
        }
    }

    private void scalar(Kind kind, Stage stage, PacketType type, Origin origin, Outcome outcome,
                        long action, long packet, long causeAction) {
        RuntimeObservation observation=RuntimeServices.observation();
        ExecutedStep step=observation == null ? null : observation.getCurrentStep();
        scalar(kind,stage,type,origin,outcome,action,packet,causeAction,
                observation == null ? -1 : observation.getActiveOuterTickId(),
                step == null ? null : step.getKind(),step == null ? -1 : step.getStepIndex(),
                step == null ? StepState.NO_STEP : StepState.CURRENT_IN_PROGRESS);
    }

    private void scalar(Kind kind, Stage stage, PacketType type, Origin origin, Outcome outcome,
                        long action, long packet, long causeAction, long outer, ExecutedStep.Kind stepKind,
                        int stepIndex, StepState stepState) {
        ScalarRecord record=new ScalarRecord(history.getRunId(),history.nextEvent(),action,packet,causeAction,
                System.nanoTime(),outer,traceGeneration,traceEpoch,stepIndex,kind,stage,type,origin,outcome,stepState,stepKind);
        history.appendScalar(record);
        boolean sparse=kind==Kind.OPPORTUNITY && stage==Stage.BEGIN
                || kind==Kind.PACKET && (type==PacketType.C0A || type==PacketType.C02 || causeAction!=0)
                && (stage==Stage.LOCAL_ACCEPTED || stage==Stage.CANCELLED || stage==Stage.DENIED || stage==Stage.FAILED);
        if (!sparse) return;
        String line=String.format(Locale.ROOT,
                "%s run=%d event=%d action=%d packet=%d causeAction=%d stage=%s type=%s origin=%s server=UNKNOWN"
                        + " outcome=%s nanos=%d outer=%d step=%s stepIndex=%d stepState=%s observedGeneration=%d epoch=%d overwritten=%d captureFailures=%d staleTokens=%d",
                kind==Kind.OPPORTUNITY ? "H2_OPPORTUNITY" : "H2_PACKET",record.run,record.event,action,packet,causeAction,
                stage,type,origin,outcome,record.nanos,outer,stepKind,stepIndex,stepState,traceGeneration,traceEpoch,
                history.getOverwrittenCount(),history.getCaptureFailureCount(),history.getStaleTokenCount());
        history.append(line);
        if (consoleLog.getValue()) System.out.println("[NeutronCombatDiagnostics] "+line);
    }

    public static ActionToken beginOpportunity() {
        CombatDiagnostics diagnostics=active();
        if (diagnostics==null) return null;
        ActionToken token=null;
        try {
            diagnostics.refreshRun();
            token=diagnostics.history.beginOpportunity();
            diagnostics.scalar(Kind.OPPORTUNITY,Stage.BEGIN,PacketType.NONE,Origin.AURA_AUTOMATIC,Outcome.UNKNOWN,token.action,0,0);
            return token;
        } catch (RuntimeException failure) {
            diagnostics.history.captureFailed();diagnostics.history.endOpportunity(token);return null;
        }
    }

    public static void opportunityResult(ActionToken token, Stage stage, boolean actualResult) {
        if (token==null) return;
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return;
        try {
            diagnostics.refreshRun();if (!diagnostics.history.current(token.run)) return;
            diagnostics.scalar(Kind.OPPORTUNITY,stage,PacketType.NONE,Origin.AURA_AUTOMATIC,
                    actualResult ? Outcome.TRUE : Outcome.FALSE,token.action,0,0);
        } catch (RuntimeException failure) { diagnostics.history.captureFailed(); }
    }

    public static void endOpportunity(ActionToken token, boolean accepted, boolean interrupted) {
        if (token==null) return;
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return;
        try {
            diagnostics.refreshRun();
            if (diagnostics.history.current(token.run)) diagnostics.scalar(Kind.OPPORTUNITY,
                    interrupted ? Stage.INTERRUPTED : Stage.END,PacketType.NONE,Origin.AURA_AUTOMATIC,
                    accepted ? Outcome.TRUE : Outcome.FALSE,token.action,0,0);
        } catch (RuntimeException failure) { diagnostics.history.captureFailed(); }
        finally { diagnostics.history.endOpportunity(token); }
    }

    private static PacketType packetType(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer) return PacketType.C03;
        if (packet instanceof C0APacketAnimation) return PacketType.C0A;
        if (packet instanceof C02PacketUseEntity && ((C02PacketUseEntity)packet).getAction()==C02PacketUseEntity.Action.ATTACK) return PacketType.C02;
        return PacketType.OTHER;
    }

    public static PacketToken beginPacket(Packet<?> packet) {
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return null;
        try {
            PacketType type=packetType(packet);
            diagnostics.refreshRun();PacketToken token=diagnostics.history.beginPacket();
            diagnostics.scalar(Kind.PACKET,Stage.ATTEMPT,type,token.origin,Outcome.UNKNOWN,token.action,token.packet,0);
            return token;
        } catch (RuntimeException failure) { diagnostics.history.captureFailed();return null; }
    }

    public static void packetStage(PacketToken token, Packet<?> actual, Stage stage) {
        if (token==null) return;
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return;
        try {
            diagnostics.refreshRun();if (!diagnostics.history.current(token.run)) return;
            long current=diagnostics.history.getActiveAction();
            diagnostics.scalar(Kind.PACKET,stage,packetType(actual),token.origin,Outcome.UNKNOWN,token.action,token.packet,
                    current!=token.action ? current : 0);
        } catch (RuntimeException failure) { diagnostics.history.captureFailed(); }
    }

    /** Capture at the actual canonical body entry; only scalars survive into its finally. */
    public static StepToken beginStep(RuntimeObservation originObserver, long outer, ExecutedStep.Kind kind, int index) {
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return null;
        try {
            if (originObserver!=RuntimeServices.observation()) return null;
            diagnostics.refreshRun();
            return diagnostics.history.beginStep(outer,index,kind,diagnostics.traceGeneration,diagnostics.traceEpoch);
        } catch (RuntimeException failure) { diagnostics.history.captureFailed();return null; }
    }

    /** A predecessor's finally must not become a completion in a replacement observer/scene run. */
    public static void observeStepResult(StepToken token, boolean interrupted) {
        if (token==null) return;
        CombatDiagnostics diagnostics=active();if (diagnostics==null) return;
        try {
            diagnostics.refreshRun();
            if (!diagnostics.history.current(token.run)) return;
            diagnostics.scalar(Kind.STEP,interrupted ? Stage.INTERRUPTED : Stage.COMPLETED,PacketType.NONE,Origin.UNKNOWN,
                    Outcome.UNKNOWN,0,0,0,token.outerTick,token.stepKind,token.stepIndex,
                    interrupted ? StepState.INTERRUPTED : StepState.COMPLETED);
        } catch (RuntimeException failure) { diagnostics.history.captureFailed(); }
    }

    private void controllerStage(Stage stage) {
        if (!isToggled() || !inWorld()) return;
        try {
            refreshRun();scalar(Kind.CONTROLLER,stage,PacketType.NONE,history.getActiveOrigin(),Outcome.UNKNOWN,
                    history.getActiveAction(),0,0);
        } catch (RuntimeException failure) { history.captureFailed(); }
    }

    public static void observeMoveFlying(EntityPlayerSP player,
                                          float rawStrafe, float rawForward, float rawYaw,
                                          float finalStrafe, float finalForward, float finalYaw, float friction) {
        CombatDiagnostics diagnostics = active();
        if (diagnostics == null || diagnostics.mc.thePlayer != player) return;
        diagnostics.lastMovement = new MovementSample(player.ticksExisted, System.currentTimeMillis(),
                rawStrafe, rawForward, rawYaw, finalStrafe, finalForward, finalYaw, friction);
    }

    /** Actual main-thread S08 application, not a packet-receipt estimate and not a flag. */
    public static void observeAppliedS08(EntityPlayer player, double beforeX, double beforeY, double beforeZ,
                                         float beforeYaw, float beforePitch) {
        CombatDiagnostics diagnostics = active();
        if (diagnostics == null || diagnostics.mc.thePlayer != player) return;
        double dx = player.posX - beforeX, dy = player.posY - beforeY, dz = player.posZ - beforeZ;
        String details = String.format(Locale.ROOT,
                "s08Phase=APPLIED appliedPre=%.4f/%.4f/%.4f appliedPost=%.4f/%.4f/%.4f"
                        + " appliedOffset=%.4f/%.4f/%.4f appliedDistance=%.4f appliedRotationDelta=%.2f/%.2f",
                beforeX, beforeY, beforeZ, player.posX, player.posY, player.posZ,
                dx, dy, dz, Math.sqrt(dx * dx + dy * dy + dz * dz),
                MathHelper.wrapAngleTo180_float(player.rotationYaw - beforeYaw),
                player.rotationPitch - beforePitch);
        diagnostics.capture("S08_APPLIED_NOT_A_FLAG", null, details);
    }

    public CombatDiagnostics() {
        setKey(Keyboard.KEY_F9);
    }

    @Override public void onEnable() {
        clearTrace();
        super.onEnable();
    }

    @Override public void onDisable() {
        super.onDisable();
        clearTrace();
    }

    private void clearTrace() {
        history.clear();
        if (isToggled()) history.startRun();else history.stop();
        traceGeneration=traceEpoch=Long.MIN_VALUE;
        postSequence = Long.MIN_VALUE;
        eventId = 0;
        lastMovement = null;
    }

    @Subscribe private final Listener<WorldEvent> world = new Listener<>(event -> clearTrace());

    @Subscribe private final Listener<EventMotion> motion = new Listener<>(event -> {
        postSequence = Long.MIN_VALUE;
        if (!event.isPost() || !inWorld()) return;
        RotationManager rotations = RuntimeServices.rotation();
        // Observe the completed movement before default-priority combat listeners.
        // Aura temporarily changes the player pose while publishing its attack events.
        postSequence = rotations.getMotionSequence();
        postCameraYaw = mc.thePlayer.rotationYaw;
        postCameraPitch = mc.thePlayer.rotationPitch;
        if (rotations.getConfirmedOwner() != null || CombatTargetTracker.hasFreshTarget(750))
            capture(event.isAccepted() ? "MOVE_LOCAL_POST" : "MOVE_LOCAL_REJECT", null);
    }, EventPriority.HIGHEST);

    @Subscribe private final Listener<EventMotion> motionEnd = new Listener<>(event -> {
        if (event.isPost()) postSequence = Long.MIN_VALUE;
    }, EventPriority.LOWEST);

    @Subscribe private final Listener<AttackEvent> attack = new Listener<>(event -> {
        controllerStage(Stage.ATTEMPT);capture("ATTACK_PRE_ATTEMPT", event.getTargetEntity());
    });

    @Subscribe private final Listener<AttackResultEvent> result = new Listener<>(event -> {
        controllerStage(Stage.LOCAL_ACCEPTED);capture("ATTACK_LOCAL_ACCEPTED", event.getTargetEntity());
    });

    @Subscribe private final Listener<PacketReceiveEvent> inbound = new Listener<>(event -> {
        if (!isToggled() || !inWorld()) return;
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook packet = (S08PacketPlayerPosLook) event.getPacket();
            // PacketReceiveEvent happens before any optional delay/application.
            CombatCorrectionPreview preview = CombatCorrectionPreview.atReceipt(packet,
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            String details = String.format(Locale.ROOT,
                    "s08Phase=RECEIPT_NOT_APPLIED flags=%s s08RawPos=%.4f/%.4f/%.4f s08RawRot=%.2f/%.2f"
                            + " s08Projected=%.4f/%.4f/%.4f projectedRot=%.2f/%.2f recvOffset=%.4f/%.4f/%.4f recvDistance=%.4f",
                    preview.flags, packet.getX(), packet.getY(), packet.getZ(), packet.getYaw(), packet.getPitch(),
                    preview.targetX, preview.targetY, preview.targetZ, preview.targetYaw, preview.targetPitch,
                    preview.deltaX, preview.deltaY, preview.deltaZ, preview.distance);
            capture("S08_RECEIVED_NOT_A_FLAG", null, details);
        } else if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;
            capture("S12_SELF_VELOCITY_RECEIVED", null, String.format(Locale.ROOT,
                    "s12Phase=RECEIPT_NOT_APPLIED incomingVelocity=%.5f/%.5f/%.5f",
                    packet.getMotionX() / 8000.0, packet.getMotionY() / 8000.0, packet.getMotionZ() / 8000.0));
        }
    });

    @Subscribe private final Listener<KnockbackEvent> knockback = new Listener<>(event -> {
        if (event.isPost()) capture("KB_SELF_APPLIED_POST", null, String.format(Locale.ROOT,
                "appliedKnockback=%.5f/%.5f/%.5f", event.getX(), event.getY(), event.getZ()));
    });

    /** Manual marker for correlating a server-console flag with a local timestamp. */
    @Subscribe private final Listener<EventKey> marker = new Listener<>(event -> {
        if (event.getKey() == Keyboard.KEY_F8) capture("F8_MANUAL_MARK", null);
    });

    private boolean inWorld() {
        return mc != null && mc.thePlayer != null && mc.theWorld != null;
    }

    private boolean hasKnownWire(RotationManager rotations) {
        return rotations.hasEffectiveRotation()
                && Float.isFinite(rotations.getEffectiveYaw()) && Float.isFinite(rotations.getEffectivePitch())
                && rotations.getEffectivePitch() >= -90 && rotations.getEffectivePitch() <= 90;
    }

    private void capture(String type, EntityLivingBase eventTarget) {
        capture(type, eventTarget, "");
    }

    private void capture(String type, EntityLivingBase eventTarget, String details) {
        if (!isToggled() || !inWorld()) return;

        RotationManager rotations = RuntimeServices.rotation();
        String owner = rotations.getConfirmedOwner();
        if (owner == null) owner = "none";

        EntityLivingBase target = eventTarget != null ? eventTarget : CombatTargetTracker.getPrimaryTarget(750);
        if (target != null && target.worldObj != mc.theWorld) target = null;
        int targetId = target == null ? -1 : target.getEntityId();
        String trackedBy = CombatTargetTracker.getSource();

        boolean postCamera = postSequence == rotations.getMotionSequence();
        float playerYaw = mc.thePlayer.rotationYaw, playerPitch = mc.thePlayer.rotationPitch;
        float cameraYaw = postCamera ? postCameraYaw : playerYaw;
        float cameraPitch = postCamera ? postCameraPitch : playerPitch;
        boolean wireKnown = hasKnownWire(rotations);
        float wireYaw = wireKnown ? rotations.getEffectiveYaw() : Float.NaN;
        float wirePitch = wireKnown ? rotations.getEffectivePitch() : Float.NaN;
        float yawDifference = MathHelper.wrapAngleTo180_float(wireYaw - cameraYaw);
        float pitchDifference = wirePitch - cameraPitch;

        // Approximate target-center deviation is diagnostic, not proof of hit validity.
        float errorYaw = Float.NaN, errorPitch = Float.NaN;
        if (target != null && target.worldObj == mc.theWorld) {
            double dx = target.posX - mc.thePlayer.posX;
            double dz = target.posZ - mc.thePlayer.posZ;
            double dy = target.posY + target.getEyeHeight() * .5D
                    - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            errorYaw = MathHelper.wrapAngleTo180_float(targetYaw - wireYaw);
            errorPitch = targetPitch - wirePitch;
        }

        ModuleManager modules = Pulsar.INSTANCE == null ? null : Pulsar.INSTANCE.getMm();
        Aura aura = modules == null ? null : (Aura) modules.getModule(Aura.class);
        String auraConfigured = aura == null || !aura.isToggled() ? "disabled" : aura.moveFixMode().label;
        long timestampMs = System.currentTimeMillis();
        String movement = lastMovement == null ? "moveSample=none" :
                lastMovement.describe(mc.thePlayer.ticksExisted, timestampMs);
        String message = String.format(Locale.ROOT,
                "%s event=%d tick=%d epochMs=%d owner=%s track=%s seq=%d camera=%.2f/%.2f cameraSource=%s playerPose=%.2f/%.2f wireKnown=%s localWire=%.2f/%.2f motionAccepted=%s delta=%.2f/%.2f targetId=%d centerErr=%.2f/%.2f"
                        + " auraConfiguredMoveFix=%s onGround=%s sprint=%s motionXYZ=%.5f/%.5f/%.5f %s %s",
                type, ++eventId, mc.thePlayer.ticksExisted, timestampMs, owner, trackedBy,
                rotations.getMotionSequence(), cameraYaw, cameraPitch, postCamera ? "motionPost" : "player",
                playerYaw, playerPitch, wireKnown, wireYaw, wirePitch, rotations.isMotionAccepted(),
                yawDifference, pitchDifference, targetId, errorYaw, errorPitch,
                auraConfigured, mc.thePlayer.onGround, mc.thePlayer.isSprinting(),
                mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ,
                movement, details);
        history.append(message);

        // Console logs only sparse actions/markers/corrections, never every movement packet.
        if (consoleLog.getValue() && !"MOVE_LOCAL_POST".equals(type) && !"MOVE_LOCAL_REJECT".equals(type))
            System.out.println("[NeutronCombatDiagnostics] " + message);
    }

    @Subscribe private final Listener<Event2D> render = new Listener<>(event -> {
        if (!inWorld() || mc.fontRendererObj == null || mc.currentScreen != null) return;
        try (RenderState ignored = new RenderState()) {
            ScaledResolution resolution = event.getSr() != null ? event.getSr() : new ScaledResolution(mc);
            int width = Math.max(20, Math.min(690, resolution.getScaledWidth() - 10));
            List<String> items = history.recent(7);
            int left = 5, top = 6, height = 53 + items.size() * 11;
            Gui.drawRect(left, top, left + width, top + height, 0xCC101824);
            mc.fontRendererObj.drawStringWithShadow("CombatDiagnostics [CLIENT ONLY]  F9 toggle  F8 mark",
                    left + 4, top + 4, 0xFFFFCC66);
            mc.fontRendererObj.drawStringWithShadow("ATTACK_LOCAL_ACCEPTED != server hit/flag; S08 != proof of flag",
                    left + 4, top + 15, 0xFFBFC7D8);
            RotationManager rotations = RuntimeServices.rotation();
            boolean wireKnown = hasKnownWire(rotations);
            EntityLivingBase target = CombatTargetTracker.getPrimaryTarget(750);
            int targetId = target != null && target.worldObj == mc.theWorld ? target.getEntityId() : -1;
            String status = String.format(Locale.ROOT, "seq=%d owner=%s target=%d camera=%.1f/%.1f wire=%.1f/%.1f",
                    rotations.getMotionSequence(), rotations.getConfirmedOwner(), targetId,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    wireKnown ? rotations.getEffectiveYaw() : Float.NaN,
                    wireKnown ? rotations.getEffectivePitch() : Float.NaN);
            mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(status, width - 9),
                    left + 4, top + 26, 0xFFE3E8F0);
            String recentMovement = lastMovement == null ? "No moveFlying input observed" : lastMovement.compact();
            mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(recentMovement, width - 9),
                    left + 4, top + 37, 0xFFACD6FF);
            int y = top + 49;
            for (String line : items) {
                int color = line.startsWith("ATTACK") ? 0xFFFFD18A
                        : line.startsWith("F8") || line.startsWith("S08") ? 0xFFFF9B9B
                        : 0xFFE3E8F0;
                mc.fontRendererObj.drawStringWithShadow(
                        mc.fontRendererObj.trimStringToWidth(line, width - 9), left + 4, y, color);
                y += 11;
            }
        }
    });
}
