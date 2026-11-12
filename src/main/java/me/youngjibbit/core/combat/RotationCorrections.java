package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.player.JumpFixEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/** Small adapters at existing movement/render consumers; no packet handling or event subscriptions. */
public final class RotationCorrections {
    private static final BodyRotationController BODY = new BodyRotationController();
    private RotationCorrections() { }

    public static final class Selection {
        public final String owner;
        public final float yaw, pitch;
        public final MoveFixController.Mode movement;
        public final BodyRotationController.Mode body;
        public final boolean preserveHeading;
        private Selection(String owner, float yaw, float pitch, MoveFixController.Mode movement, BodyRotationController.Mode body) {
            this(owner, yaw, pitch, movement, body, false);
        }
        private Selection(String owner, float yaw, float pitch, MoveFixController.Mode movement,
                          BodyRotationController.Mode body, boolean preserveHeading) {
            this.owner = owner; this.yaw = yaw; this.pitch = pitch; this.movement = movement; this.body = body;
            this.preserveHeading = preserveHeading && "scaffold".equals(owner);
        }
    }

    public static Selection select(RotationManager rotation, String source, boolean enabled,
                                   MoveFixController.Mode movement, BodyRotationController.Mode body) {
        return select(rotation, source, enabled, movement, body, false);
    }

    public static Selection select(RotationManager rotation, String source, boolean enabled,
                                   MoveFixController.Mode movement, BodyRotationController.Mode body,
                                   boolean preserveHeading) {
        if (!enabled || source == null || !source.equals(rotation.getConfirmedOwner())) return null;
        return new Selection(source, rotation.getEffectiveYaw(), rotation.getEffectivePitch(), movement, body, preserveHeading);
    }

    private static Selection current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive()
                || mc.thePlayer.isDead || mc.thePlayer.isSpectator() || mc.thePlayer.isRiding()
                || mc.getRenderViewEntity() != mc.thePlayer) return null;
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        if (modules == null) return null;
        RotationManager rotation = RuntimeServices.rotation();
        if (mc.getNetHandler()==null || !mc.getNetHandler().getNetworkManager().isChannelOpen()) return null;
        String owner = rotation.getConfirmedOwner(mc.thePlayer, mc.theWorld, mc.getNetHandler().getNetworkManager().getClientPackets());
        if (mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused()) {
            BedBreaker bed = (BedBreaker) modules.getModule(BedBreaker.class);
            // UI suspension stops actions, while the already-owned Bed return keeps its movement basis.
            if (!BedBreaker.ROTATION_SOURCE.equals(owner) || bed == null
                    || !bed.rotationEngine.returning() || !bed.correctionEnabled()) return null;
        }
        if (RotationManager.isReleaseSource(owner)) {
            RotationManager.ReleaseView release = rotation.getRotationRelease();
            return release != null && owner.equals(release.releaseSource)
                    ? new Selection(owner, rotation.getEffectiveYaw(), rotation.getEffectivePitch(), release.movement, release.body) : null;
        }
        if ("aura".equals(owner)) {
            Aura aura = (Aura) modules.getModule(Aura.class);
            return aura == null ? null : select(rotation, "aura", aura.correctionEnabled(), aura.moveFixMode(), aura.bodyRotationMode());
        }
        if ("scaffold".equals(owner)) {
            Scaffold scaffold = (Scaffold) modules.getModule(Scaffold.class);
            return scaffold == null ? null : select(rotation, "scaffold", scaffold.correctionEnabled(),
                    scaffold.moveFixMode(), scaffold.bodyRotationMode(), scaffold.preservesBridgeHeading());
        }
        if (NScaffold.ROTATION_SOURCE.equals(owner)) {
            NScaffold scaffold = (NScaffold) modules.getModule(NScaffold.class);
            return scaffold == null ? null : select(rotation, NScaffold.ROTATION_SOURCE,
                    scaffold.correctionEnabled(), scaffold.moveFixMode(), scaffold.bodyRotationMode());
        }
        if ("velocity".equals(owner)) {
            Velocity velocity = (Velocity) modules.getModule(Velocity.class);
            return velocity == null ? null : select(rotation, "velocity", velocity.correctionEnabled(),
                    MoveFixController.Mode.SIMPLE, BodyRotationController.Mode.NATURAL);
        }
        if (BedBreaker.ROTATION_SOURCE.equals(owner)) {
            BedBreaker bedBreaker = (BedBreaker) modules.getModule(BedBreaker.class);
            return bedBreaker == null ? null : select(rotation, BedBreaker.ROTATION_SOURCE,
                    bedBreaker.correctionEnabled(), bedBreaker.moveFixMode(), bedBreaker.bodyRotationMode());
        }
        // AimAssist or any unowned/manual rotation cannot inherit another module's settings.
        return null;
    }

    public static void movement(PlayerMoveUpdateEvent event) {
        applyMovement(currentMovement(), event);
    }

    static MoveFixController.Mode movementModeFor(String source) {
        if (source == null) return MoveFixController.Mode.OFF;
        if (RotationManager.isReleaseSource(source)) {
            RotationManager.ReleaseView release = RuntimeServices.rotation().getRotationRelease();
            return release != null && source.equals(release.releaseSource) ? release.movement : MoveFixController.Mode.OFF;
        }
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        if (modules == null) return MoveFixController.Mode.OFF;
        if ("aura".equals(source)) {
            Aura aura = (Aura) modules.getModule(Aura.class);
            return aura != null && aura.correctionEnabled() ? aura.moveFixMode() : MoveFixController.Mode.OFF;
        }
        if ("scaffold".equals(source)) {
            Scaffold scaffold = (Scaffold) modules.getModule(Scaffold.class);
            return scaffold != null && scaffold.correctionEnabled() ? scaffold.moveFixMode() : MoveFixController.Mode.OFF;
        }
        if (NScaffold.ROTATION_SOURCE.equals(source)) {
            NScaffold scaffold = (NScaffold) modules.getModule(NScaffold.class);
            return scaffold != null && scaffold.correctionEnabled() ? scaffold.moveFixMode() : MoveFixController.Mode.OFF;
        }
        if ("velocity".equals(source)) {
            Velocity velocity = (Velocity) modules.getModule(Velocity.class);
            return velocity != null && velocity.correctionEnabled() ? MoveFixController.Mode.SIMPLE : MoveFixController.Mode.OFF;
        }
        if (BedBreaker.ROTATION_SOURCE.equals(source)) {
            BedBreaker bedBreaker = (BedBreaker) modules.getModule(BedBreaker.class);
            return bedBreaker != null && bedBreaker.correctionEnabled()
                    ? bedBreaker.moveFixMode() : MoveFixController.Mode.OFF;
        }
        return MoveFixController.Mode.OFF;
    }

    private static Selection currentMovement() {
        RotationManager.MovementRotation predicted = RuntimeServices.rotation().getPreparedMovement();
        if (predicted != null) {
            return predicted.source == null ? null : new Selection(predicted.source, predicted.yaw, predicted.pitch,
                    predicted.mode, BodyRotationController.Mode.VANILLA, preservesBridgeHeading(predicted.source));
        }
        return current();
    }

    private static boolean preservesBridgeHeading(String source) {
        if (!"scaffold".equals(source)) return false;
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        Scaffold scaffold = modules == null ? null : (Scaffold) modules.getModule(Scaffold.class);
        return scaffold != null && scaffold.preservesBridgeHeading();
    }

    /**
     * Sprint admission must use the SAME logical forward-key basis as the local
     * 1.8.9 moveFlying consumer. Rounded MoveFix can map physical W into a
     * sideways/backward module-yaw key; using the original W to retain sprint
     * creates a contradictory sprint/action state.
     *
     * This is a read-only query and uses the same transform for every enabled mode.
     */
    public static float sprintForward(float cameraYaw, float rawStrafe, float rawForward) {
        return correctedSprintForward(currentMovement(), cameraYaw, rawStrafe, rawForward);
    }

    static float correctedSprintForward(Selection selected, float cameraYaw,
                                        float rawStrafe, float rawForward) {
        if (selected == null || selected.movement == MoveFixController.Mode.OFF || selected.preserveHeading)
            return rawForward;
        // Demise rewrites keyboard axes before slowdown. Sprint consumes those axes directly.
        if (demiseOwner(selected.owner)) return rawForward;
        return MoveFixController.correct(rawStrafe, rawForward, cameraYaw,
                selected.yaw, selected.movement).forward;
    }

    /** Direction for a direct velocity writer consuming untransformed current-step inputs.
     * The direction already includes the writer's key/diagonal policy. Event-local MoveFix
     * inputs must not be passed here: moveFlying corrects its own event exactly once.
     */
    public static float directMotionYaw(float cameraYaw, float cameraDirectionYaw, float strafe, float forward) {
        Selection selected = currentMovement();
        if (selected == null || selected.movement == MoveFixController.Mode.OFF || selected.preserveHeading) return cameraDirectionYaw;
        if (demiseOwner(selected.owner)
                || selected.movement == MoveFixController.Mode.ROTATION_BOUND
                || selected.movement == MoveFixController.Mode.LEGACY_STRICT)
            return selected.yaw + (cameraDirectionYaw - cameraYaw);
        // Rounded key reconstruction needs the original axes, including diagonal keys.
        MoveFixController.Input input = MoveFixController.correct(strafe, forward, cameraYaw, selected.yaw, selected.movement);
        return selected.yaw - (float) Math.toDegrees(Math.atan2(input.strafe, input.forward));
    }

    static void applyMovement(Selection selected, PlayerMoveUpdateEvent event) {
        if (selected == null || selected.movement == MoveFixController.Mode.OFF || event.isCancelled() || !event.claimRotationCorrection()) return;
        // Adaptive Bridge keeps local input in its existing camera frame. This is
        // an exact world-vector correction without rounding to eight aim-relative
        // keys; vanilla still applies sneak/item strength and its diagonal cap once.
        // The selected placement yaw and its packet/action authority are untouched.
        if (selected.preserveHeading) return;
        if (demiseOwner(selected.owner)) {
            // RotationHandler.onStrafe changes only yaw; onMove has already fixed raw axes.
            event.setYaw(selected.yaw);
            return;
        }
        MoveFixController.Input input = MoveFixController.correct(event.getStrafe(), event.getForward(), event.getYaw(), selected.yaw, selected.movement);
        event.setStrafe(input.strafe); event.setForward(input.forward); event.setYaw(selected.yaw);
    }

    public static void jump(JumpFixEvent event) {
        applyJump(currentMovement(), event);
    }

    static void applyJump(Selection selected, JumpFixEvent event) {
        if (selected != null && selected.movement != MoveFixController.Mode.OFF && !event.isCancelled()
                && event.claimRotationCorrection() && !selected.preserveHeading)
            event.setYaw(MoveFixController.jumpYaw(event.getYaw(), selected.yaw, selected.movement));
    }

    public static BodyRotationController.Pose renderPose(EntityLivingBase entity, float vanillaBodyYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return null;
        RotationManager.ReleaseView release = RuntimeServices.rotation().getRotationRelease();
        if (release != null && release.body != BodyRotationController.Mode.VANILLA) {
            return BODY.sampleReturn(mc.thePlayer, mc.theWorld, release.identity, release.source,
                    release.yaw, release.pitch, vanillaBodyYaw, release.progress);
        }
        Selection selected = current();
        // Demise owners use the original EntityLivingBase model fields and vanilla interpolation.
        if (selected != null && demiseOwner(selected.owner)) {
            BODY.reset();
            return null;
        }
        return BODY.sampleFrame(mc.getRenderFrameSequence(), mc.thePlayer, mc.theWorld, selected == null ? null : selected.owner,
                selected == null ? BodyRotationController.Mode.VANILLA : selected.body,
                selected == null ? 0 : selected.yaw, selected == null ? 0 : selected.pitch, vanillaBodyYaw, System.nanoTime());
    }

    public static void reset(String source) { BODY.reset(source); }
    public static void reset() { BODY.reset(); }

    private static boolean demiseOwner(String source) {
        return NScaffold.ROTATION_SOURCE.equals(source) || BedBreaker.ROTATION_SOURCE.equals(source);
    }
}
