package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.player.JumpFixEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
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
        private Selection(String owner, float yaw, float pitch, MoveFixController.Mode movement, BodyRotationController.Mode body) {
            this.owner = owner; this.yaw = yaw; this.pitch = pitch; this.movement = movement; this.body = body;
        }
    }

    public static Selection select(RotationManager rotation, String source, boolean enabled,
                                   MoveFixController.Mode movement, BodyRotationController.Mode body) {
        if (!enabled || source == null || !source.equals(rotation.getConfirmedOwner())) return null;
        return new Selection(source, rotation.getEffectiveYaw(), rotation.getEffectivePitch(), movement, body);
    }

    private static Selection current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isEntityAlive()
                || mc.thePlayer.isDead || mc.thePlayer.isSpectator() || mc.thePlayer.isRiding()
                || mc.currentScreen != null || !mc.inGameHasFocus || mc.getRenderViewEntity() != mc.thePlayer) return null;
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        if (modules == null) return null;
        RotationManager rotation = RuntimeServices.rotation();
        if (mc.getNetHandler()==null || !mc.getNetHandler().getNetworkManager().isChannelOpen()) return null;
        String owner = rotation.getConfirmedOwner(mc.thePlayer, mc.theWorld, mc.getNetHandler().getNetworkManager().getClientPackets());
        if ("aura".equals(owner)) {
            Aura aura = (Aura) modules.getModule(Aura.class);
            return aura == null ? null : select(rotation, "aura", aura.correctionEnabled(), aura.moveFixMode(), aura.bodyRotationMode());
        }
        if ("scaffold".equals(owner)) {
            Scaffold scaffold = (Scaffold) modules.getModule(Scaffold.class);
            return scaffold == null ? null : select(rotation, "scaffold", scaffold.correctionEnabled(), scaffold.moveFixMode(), scaffold.bodyRotationMode());
        }
        // AimAssist or any unowned/manual rotation cannot inherit Aura/Scaffold settings.
        return null;
    }

    public static void movement(PlayerMoveUpdateEvent event) {
        applyMovement(current(), event);
    }

    /** Direction for a direct velocity writer consuming untransformed current-step inputs.
     * The direction already includes the writer's key/diagonal policy. Event-local MoveFix
     * inputs must not be passed here: moveFlying corrects its own event exactly once.
     */
    public static float directMotionYaw(float cameraYaw, float cameraDirectionYaw, float strafe, float forward) {
        Selection selected = current();
        if (selected == null || selected.movement == MoveFixController.Mode.OFF
                || selected.movement == MoveFixController.Mode.PRECISE) return cameraDirectionYaw;
        if (selected.movement == MoveFixController.Mode.ROTATION_BOUND
                || selected.movement == MoveFixController.Mode.LEGACY_STRICT)
            return selected.yaw + (cameraDirectionYaw - cameraYaw);
        // Rounded key reconstruction needs the original axes, including diagonal keys.
        MoveFixController.Input input = selected.movement == MoveFixController.Mode.LEGACY_ROUNDED
                ? MoveFixController.correct(strafe, forward, cameraYaw, selected.yaw, selected.movement)
                : MoveFixController.correct(0, 1, cameraDirectionYaw, selected.yaw, selected.movement);
        return selected.yaw - (float) Math.toDegrees(Math.atan2(input.strafe, input.forward));
    }

    static void applyMovement(Selection selected, PlayerMoveUpdateEvent event) {
        if (selected == null || selected.movement == MoveFixController.Mode.OFF || event.isCancelled() || !event.claimRotationCorrection()) return;
        MoveFixController.Input input = MoveFixController.correct(event.getStrafe(), event.getForward(), event.getYaw(), selected.yaw, selected.movement);
        event.setStrafe(input.strafe); event.setForward(input.forward); event.setYaw(selected.yaw);
    }

    public static void jump(JumpFixEvent event) {
        applyJump(current(), event);
    }

    static void applyJump(Selection selected, JumpFixEvent event) {
        if (selected != null && selected.movement != MoveFixController.Mode.OFF && !event.isCancelled() && event.claimRotationCorrection())
            event.setYaw(MoveFixController.jumpYaw(event.getYaw(), selected.yaw, selected.movement));
    }

    public static BodyRotationController.Pose renderPose(EntityLivingBase entity, float vanillaBodyYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity != mc.thePlayer) return null;
        Selection selected = current();
        return BODY.sampleFrame(mc.getRenderFrameSequence(), mc.thePlayer, mc.theWorld, selected == null ? null : selected.owner,
                selected == null ? BodyRotationController.Mode.VANILLA : selected.body,
                selected == null ? 0 : selected.yaw, selected == null ? 0 : selected.pitch, vanillaBodyYaw, System.nanoTime());
    }

    public static void reset(String source) { BODY.reset(source); }
    public static void reset() { BODY.reset(); }
}
