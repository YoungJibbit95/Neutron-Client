package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.EventMove;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.module.impl.world.NScaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.lang.ref.WeakReference;

/** Scoped producers for Demise's original frame/input/model events, including its disable tail. */
public final class DemiseRotationHooks {
    private static WeakReference<NScaffold> boundModule = new WeakReference<>(null);
    private static WeakReference<Object> boundPlayer = new WeakReference<>(null), boundWorld = new WeakReference<>(null),
            boundConnection = new WeakReference<>(null), boundPipeline = new WeakReference<>(null);
    private static long boundEpoch;
    private static WeakReference<BedBreaker> boundBed = new WeakReference<>(null);
    private static WeakReference<Object> bedPlayer = new WeakReference<>(null), bedWorld = new WeakReference<>(null),
            bedConnection = new WeakReference<>(null), bedPipeline = new WeakReference<>(null);
    private static long bedEpoch;
    private static AcceptedRotation acceptedRotation;
    private DemiseRotationHooks() { }

    /** Factual C03 history survives focus/menu changes, but never a different network scene. */
    private static final class AcceptedRotation {
        final WeakReference<Object> player, world, connection, pipeline;
        final long epoch;
        final float yaw, pitch;
        AcceptedRotation(Minecraft mc, NetHandlerPlayClient connection, PacketPipeline pipeline, float yaw, float pitch) {
            player = new WeakReference<Object>(mc.thePlayer); world = new WeakReference<Object>(mc.theWorld);
            this.connection = new WeakReference<Object>(connection); this.pipeline = new WeakReference<Object>(pipeline);
            epoch = pipeline.getContextVersion(); this.yaw = yaw; this.pitch = pitch;
        }
        boolean matches(Minecraft mc, PacketPipeline packets) {
            return player.get() == mc.thePlayer && world.get() == mc.theWorld
                    && connection.get() == mc.thePlayer.sendQueue && pipeline.get() == packets
                    && epoch == packets.getContextVersion();
        }
    }

    private static NScaffold current() {
        Minecraft mc = Minecraft.getMinecraft();
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        NScaffold module = modules == null ? null : (NScaffold) modules.getModule(NScaffold.class);
        if (module == null || mc == null || mc.gameSettings == null || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.worldObj != mc.theWorld || mc.thePlayer.sendQueue == null
                || mc.thePlayer.sendQueue.getNetworkManager() == null
                || !mc.thePlayer.sendQueue.getNetworkManager().isChannelOpen()
                || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isDead || mc.thePlayer.isSpectator()
                || mc.thePlayer.isRiding() || mc.currentScreen != null || mc.isGamePaused() || !mc.inGameHasFocus
                || mc.getRenderViewEntity() != mc.thePlayer) {
            clearBound(mc);
            return null;
        }
        PacketPipeline pipeline = mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        if (pipeline == null || !pipeline.isActive()) { clearBound(mc); return null; }
        if (boundModule.get() != module || boundPlayer.get() != mc.thePlayer || boundWorld.get() != mc.theWorld
                || boundConnection.get() != mc.thePlayer.sendQueue || boundPipeline.get() != pipeline
                || boundEpoch != pipeline.getContextVersion()) {
            clearBound(mc);
            module.rotationEngine.clear(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            boundModule = new WeakReference<>(module);
            boundPlayer = new WeakReference<Object>(mc.thePlayer);
            boundWorld = new WeakReference<Object>(mc.theWorld);
            boundConnection = new WeakReference<Object>(mc.thePlayer.sendQueue);
            boundPipeline = new WeakReference<Object>(pipeline);
            boundEpoch = pipeline.getContextVersion();
        }
        if (!module.rotationEngine.hasServerRotation() && acceptedRotation != null && acceptedRotation.matches(mc, pipeline))
            module.rotationEngine.observeAcceptedPacket(true, acceptedRotation.yaw, acceptedRotation.pitch);
        return module;
    }

    private static void clearBound(Minecraft mc) {
        NScaffold previous = boundModule.get();
        if (previous != null) previous.rotationEngine.clear(mc != null && mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0,
                mc != null && mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0);
        boundModule.clear(); boundPlayer.clear(); boundWorld.clear(); boundConnection.clear(); boundPipeline.clear();
    }

    /** BedBreaker has a separate lifetime: binding or disabling one owner cannot reset the other. */
    private static BedBreaker currentBed() {
        Minecraft mc = Minecraft.getMinecraft();
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        BedBreaker module = modules == null ? null : (BedBreaker) modules.getModule(BedBreaker.class);
        if (module == null || mc == null || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.worldObj != mc.theWorld || mc.thePlayer.sendQueue == null
                || mc.thePlayer.sendQueue.getNetworkManager() == null
                || !mc.thePlayer.sendQueue.getNetworkManager().isChannelOpen()) {
            clearBed(mc);
            return null;
        }
        PacketPipeline pipeline = mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        if (pipeline == null || !pipeline.isActive()) { clearBed(mc); return null; }
        if (boundBed.get() != module || bedPlayer.get() != mc.thePlayer || bedWorld.get() != mc.theWorld
                || bedConnection.get() != mc.thePlayer.sendQueue || bedPipeline.get() != pipeline
                || bedEpoch != pipeline.getContextVersion()) {
            clearBed(mc);
            module.rotationEngine.clear(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            boundBed = new WeakReference<>(module);
            bedPlayer = new WeakReference<Object>(mc.thePlayer);
            bedWorld = new WeakReference<Object>(mc.theWorld);
            bedConnection = new WeakReference<Object>(mc.thePlayer.sendQueue);
            bedPipeline = new WeakReference<Object>(pipeline);
            bedEpoch = pipeline.getContextVersion();
        }
        if (!module.rotationEngine.hasServerRotation() && acceptedRotation != null && acceptedRotation.matches(mc, pipeline))
            module.rotationEngine.observeAcceptedPacket(true, acceptedRotation.yaw, acceptedRotation.pitch);
        boolean suspended = mc.gameSettings == null || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isDead
                || mc.thePlayer.isSpectator() || mc.thePlayer.isRiding() || mc.currentScreen != null
                || mc.isGamePaused() || !mc.inGameHasFocus || mc.getRenderViewEntity() != mc.thePlayer;
        if (suspended) module.rotationEngine.beginReturn();
        return module;
    }

    private static void clearBed(Minecraft mc) {
        BedBreaker previous = boundBed.get();
        if (previous != null) previous.onDemiseSceneInvalidated();
        boundBed.clear(); bedPlayer.clear(); bedWorld.clear(); bedConnection.clear(); bedPipeline.clear();
    }

    /** Bind before a module creates its first target; subsequent frames retain the original state. */
    public static void bind() { current(); }
    public static void bindBed() { currentBed(); }

    /** Rotation-only continuity is eligible in the same physical scene even while action input is suspended. */
    public static boolean bedReturnActive() {
        BedBreaker bed = currentBed();
        return bed != null && bed.rotationEngine.returning() && bed.correctionEnabled();
    }

    public static boolean bedReturnMatches(float yaw, float pitch) {
        BedBreaker bed = currentBed();
        DemiseRotationEngine.Aim pose = bed == null ? null : bed.rotationEngine.currentAim();
        return pose != null && bed.rotationEngine.returning() && bed.correctionEnabled()
                && Float.compare(pose.yaw, yaw) == 0 && Float.compare(pose.pitch, pitch) == 0;
    }

    public static void mousePre() {
        NScaffold module = current();
        if (module != null) module.rotationEngine.mousePre();
        BedBreaker bed = currentBed();
        if (bed != null) bed.rotationEngine.mousePre();
    }

    public static void mouseUpdate() {
        NScaffold module = current();
        BedBreaker bed = currentBed();
        if (module == null && bed == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null) return;
        if (module != null) module.rotationEngine.mouseUpdate(mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch, mc.gameSettings.mouseSensitivity);
        if (bed != null) bed.rotationEngine.mouseUpdate(mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch, mc.gameSettings.mouseSensitivity);
        DemiseRotationEngine owner = presentationEngine(module, bed);
        if (owner != null && !owner.silent()) {
            DemiseRotationEngine.Aim pose = owner.cameraAim(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            mc.thePlayer.rotationYaw = pose.yaw;
            mc.thePlayer.rotationPitch = pose.pitch;
        }
    }

    /** The inactive render branch has no physical mouse integration; only Bed's existing tail advances. */
    public static void mouseInactive() {
        BedBreaker bed = currentBed();
        Minecraft mc = Minecraft.getMinecraft();
        if (bed == null || mc.gameSettings == null) return;
        bed.rotationEngine.beginReturn();
        bed.rotationEngine.mouseUpdate(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.gameSettings.mouseSensitivity);
        if (presentationEngine(current(), bed) == bed.rotationEngine && !bed.rotationEngine.silent()) {
            DemiseRotationEngine.Aim pose = bed.rotationEngine.cameraAim(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            mc.thePlayer.rotationYaw = pose.yaw; mc.thePlayer.rotationPitch = pose.pitch;
        }
    }

    public static void angle(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return;
        NScaffold module = current();
        if (module != null && module.isToggled()) module.onDemiseAngle();
        BedBreaker bed = currentBed();
        if (bed != null && bed.isToggled()) bed.onDemiseAngle();
    }

    public static void update(EntityPlayerSP player) {
        NScaffold module = current();
        if (module != null && player == Minecraft.getMinecraft().thePlayer && module.isToggled()) module.onDemiseUpdate();
        BedBreaker bed = currentBed();
        if (bed != null && player == Minecraft.getMinecraft().thePlayer && bed.isToggled()) bed.onDemiseUpdate();
    }

    /** Runs inside beginMotion/prepareMovement, including each owner's disabled rotation-only tail. */
    public static void prepareRotation() {
        NScaffold module = current();
        if (module != null) module.requestDemiseRotation();
        BedBreaker bed = currentBed();
        if (bed != null) {
            String owner = RuntimeServices.rotation().getConfirmedOwner();
            if (owner != null && !BedBreaker.ROTATION_SOURCE.equals(owner))
                bed.rotationEngine.resumeFromServer();
            bed.requestDemiseRotation();
        }
    }

    /** Suspended input can continue a prior return, but cannot run general rotation/action callbacks. */
    public static void prepareBedReturn() {
        BedBreaker bed = currentBed();
        if (bed == null || !bed.rotationEngine.returning() || !bed.correctionEnabled()) return;
        String owner = RuntimeServices.rotation().getConfirmedOwner();
        if (owner != null && !BedBreaker.ROTATION_SOURCE.equals(owner)) bed.rotationEngine.resumeFromServer();
        bed.requestDemiseRotation();
    }

    /** RotationHandler's raw MoveInputEvent ran before Scaffold's handler and before sneak scaling. */
    public static void input(MovementInput input) {
        NScaffold module = current();
        BedBreaker bed = currentBed();
        Minecraft mc = Minecraft.getMinecraft();
        if (module == null && bed == null || mc.thePlayer.movementInput != input) return;
        MovementInputEvent event = new MovementInputEvent(input.moveStrafe, input.moveForward, input.jump, input.sneak);
        DemiseRotationEngine.Aim aim = module == null ? null : movementAim(NScaffold.ROTATION_SOURCE, module.rotationEngine);
        DemiseRotationEngine engine = aim == null ? null : module.rotationEngine;
        boolean returning = module != null && !module.isToggled();
        float direction = module == null ? 0 : module.demiseMovementYaw();
        if (aim == null && bed != null) {
            aim = movementAim(BedBreaker.ROTATION_SOURCE, bed.rotationEngine);
            engine = aim == null ? null : bed.rotationEngine;
            returning = bed.rotationEngine.returning();
            direction = inputDirection(mc.thePlayer.rotationYaw, event.getStrafe(), event.getForward());
        }
        if (aim != null && engine.correction() == DemiseRotationEngine.Correction.Silent) {
            DemiseMoveFix.Input fixed = DemiseMoveFix.correct(event.getStrafe(), event.getForward(), aim.yaw, mc.thePlayer.rotationYaw);
            event.setStrafe(fixed.strafe); event.setForward(fixed.forward);
        } else if (aim != null && engine.correction() == DemiseRotationEngine.Correction.None && returning) {
            // Original getDirection returns float radians from its sticky physical key yaw.
            // Neutron has no Demise TargetStrafe/omniSprint mode; that external exemption is false.
            direction = (float) Math.toDegrees((float) Math.toRadians(direction));
            if (Math.abs(MathHelper.wrapAngleTo180_float(direction - aim.yaw)) > 90) {
                if (mc.gameSettings.keyBindSprint != null)
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                mc.thePlayer.setSprinting(false);
            }
        }
        if (module != null && module.isToggled()) module.onDemiseInput(event);
        input.moveStrafe = event.getStrafe(); input.moveForward = event.getForward();
        input.jump = event.isJump(); input.sneak = event.isSneak();
    }

    private static DemiseRotationEngine.Aim movementAim(String source, DemiseRotationEngine engine) {
        RotationManager rotation = RuntimeServices.rotation();
        RotationManager.MovementRotation prepared = rotation.getPreparedMovement();
        if (prepared != null) return source.equals(prepared.source)
                ? DemiseRotationEngine.aim(prepared.yaw, prepared.pitch) : null;
        return source.equals(rotation.getConfirmedOwner()) && engine.shouldRotate()
                ? DemiseRotationEngine.aim(rotation.getEffectiveYaw(), rotation.getEffectivePitch()) : null;
    }

    private static float inputDirection(float yaw, float strafe, float forward) {
        if (forward < 0) yaw += 180;
        float factor = forward < 0 ? -.5f : forward > 0 ? .5f : 1;
        if (strafe > 0) yaw -= 90 * factor;
        if (strafe < 0) yaw += 90 * factor;
        return yaw;
    }

    public static EventMove move(EntityPlayerSP player, double x, double y, double z) {
        EventMove event = new EventMove(x, y, z);
        NScaffold module = current();
        if (module != null && player == Minecraft.getMinecraft().thePlayer && module.isToggled()) module.onDemiseMove(event);
        return event;
    }

    /** The original realistic-body path changes only model state, never camera yaw/pitch. */
    public static float modelYaw(EntityLivingBase entity, float vanillaYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return vanillaYaw;
        DemiseRotationEngine engine = presentationEngine(current(), currentBed());
        DemiseRotationEngine.Aim aim = engine == null ? null : engine.currentAim();
        return aim == null ? vanillaYaw : aim.yaw;
    }

    public static void headAfterActionState(EntityLivingBase entity) {
        entity.rotationYawHead = modelYaw(entity, entity.rotationYawHead);
    }

    /** Original AbstractClientPlayer.getLook posts LookEvent before constructing its look vector. */
    public static Vec3 lookVector(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return null;
        DemiseRotationEngine engine = presentationEngine(current(), currentBed());
        DemiseRotationEngine.Aim aim = engine == null ? null : engine.currentAim();
        return aim == null ? null : Entity.getVectorForRotation(aim.pitch,aim.yaw);
    }

    /** Original RendererLivingEntity interpolates local head pitch using interpolateRotation. */
    public static float renderPitch(EntityLivingBase entity, float partialTicks, float vanillaPitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return vanillaPitch;
        DemiseRotationEngine engine = presentationEngine(current(), currentBed());
        return engine != null && engine.shouldRotate()
                ? entity.prevRotationPitchHead + MathHelper.wrapAngleTo180_float(entity.rotationPitchHead
                    - entity.prevRotationPitchHead) * partialTicks : vanillaPitch;
    }

    public static void motion(EntityPlayerSP player, float yaw, float pitch) {
        NScaffold module = current();
        if (module != null && player == Minecraft.getMinecraft().thePlayer) module.rotationEngine.observeMotion(yaw, pitch);
        BedBreaker bed = currentBed();
        if (bed != null && player == Minecraft.getMinecraft().thePlayer) bed.rotationEngine.observeMotion(yaw, pitch);
    }

    public static void modelAfterMotion(EntityPlayerSP player, float yaw, float pitch) {
        DemiseRotationEngine engine = presentationEngine(current(), currentBed());
        if (engine != null && player == Minecraft.getMinecraft().thePlayer && engine.shouldRotate()) {
            player.rotationYawHead = yaw;
            player.rotationPitchHead = pitch;
        }
    }

    public static void acceptedPacket(NetHandlerPlayClient connection, C03PacketPlayer packet) {
        Minecraft mc = Minecraft.getMinecraft();
        // The original handler receives rotating C03 even with Scaffold disabled or a menu open.
        // Record only accepted transport for this exact live player/world/connection/epoch.
        if (packet.getRotating() && mc != null && mc.thePlayer != null && mc.theWorld != null
                && mc.thePlayer.worldObj == mc.theWorld && mc.thePlayer.sendQueue == connection
                && connection.getNetworkManager() != null && connection.getNetworkManager().isChannelOpen()
                && Float.isFinite(packet.getYaw()) && Float.isFinite(packet.getPitch())) {
            PacketPipeline packets = connection.getNetworkManager().getClientPackets();
            if (packets != null && packets.isActive())
                acceptedRotation = new AcceptedRotation(mc, connection, packets, packet.getYaw(), packet.getPitch());
        }
        NScaffold module = current();
        if (module != null && mc.thePlayer.sendQueue == connection)
            module.rotationEngine.observeAcceptedPacket(packet.getRotating(), packet.getYaw(), packet.getPitch());
        BedBreaker bed = currentBed();
        if (bed != null && mc.thePlayer.sendQueue == connection)
            bed.rotationEngine.observeAcceptedPacket(packet.getRotating(), packet.getYaw(), packet.getPitch());
    }

    private static DemiseRotationEngine presentationEngine(NScaffold scaffold, BedBreaker bed) {
        RotationManager rotation = RuntimeServices.rotation();
        RotationManager.MovementRotation prepared = rotation.getPreparedMovement();
        String owner = prepared == null ? rotation.getConfirmedOwner() : prepared.source;
        if ((owner == null || NScaffold.ROTATION_SOURCE.equals(owner)) && scaffold != null && scaffold.correctionEnabled())
            return scaffold.rotationEngine;
        if ((owner == null || BedBreaker.ROTATION_SOURCE.equals(owner)) && bed != null && bed.correctionEnabled())
            return bed.rotationEngine;
        return null;
    }
}
