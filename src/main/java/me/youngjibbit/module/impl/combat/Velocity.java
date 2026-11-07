package me.youngjibbit.module.impl.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.ghost.JumpReset;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/** Arsenic-inspired legit reduction; see docs/third-party/ARSENIC.md. */
@ModuleInfo(name = "Velocity", description = "Reduces knockback through normal jumps and validated sprint hits", category = Category.Combat)
public class Velocity extends Module {
    public final ComboValue mode = new ComboValue("Mode", this, new String[]{"Legit", "Legit Jumpreset", "Intave Reduce"}, 0);
    public final SliderValue reduceTicks = new SliderValue("Reduce Ticks", this, 2D, 1, 3, true);
    public final CheckBoxValue jumpReset = new CheckBoxValue("Jump Reset", this, true);
    private final CombatPacketState actions = new CombatPacketState();
    private Object player, world;
    private PacketPipeline connection;
    private long epoch, expires;
    private int remaining;
    private boolean pendingJump;
    private EntityLivingBase target;

    public Velocity() {
        reduceTicks.setVisibility(m -> !"Legit Jumpreset".equals(mode.getValue()));
        jumpReset.setVisibility(m -> "Legit".equals(mode.getValue()));
    }

    @Override public void onEnable() { clear(); super.onEnable(); }
    @Override public void onDisable() { clear(); super.onDisable(); }
    @Override public void onValueChange() { if (actions != null) clear(); }
    @Subscribe private final Listener<WorldEvent> onWorld = new Listener<>(e -> clear());
    @Subscribe private final Listener<TickEvent> onTick = new Listener<>(e -> {
        if (!context() || !usable() || System.nanoTime() - expires >= 0) clearReduction();
    });

    @Subscribe private final Listener<EventPacket> onPacket = new Listener<>(e -> {
        if (!e.isPost() || !context() || e.getPipeline() != connection) return;
        long now = System.nanoTime();
        if (e.isOutbound()) { actions.accepted(e.getPacket(), now); return; }
        if (!"Legit".equals(mode.getValue()) || !(e.getPacket() instanceof S12PacketEntityVelocity) || !usable()) return;
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) e.getPacket();
        if (velocity.getEntityID() != mc.thePlayer.getEntityId() || velocity.getMotionY() <= 0) return;
        arm(now);
    });

    // The local handler emits POST only after setVelocity, including delayed S12 delivery.
    @Subscribe private final Listener<KnockbackEvent> appliedImpulse = new Listener<>(e -> {
        if (!e.isPost() || "Legit".equals(mode.getValue()) || !context() || !usable()) return;
        if (e.getY() <= 0 || e.getX() == 0 && e.getZ() == 0) { clearReduction(); return; }
        arm(System.nanoTime());
    });

    private void arm(long now) {
        boolean jumpOnly = "Legit Jumpreset".equals(mode.getValue());
        remaining = jumpOnly ? 0 : reduceTicks.getValue().intValue();
        expires = now + 250_000_000L;
        pendingJump = (jumpOnly || "Legit".equals(mode.getValue()) && jumpReset.getValue()) && !JumpReset.isHandlingJumps()
                && mc.thePlayer.onGround && mc.thePlayer.isSprinting();
    }

    @Subscribe private final Listener<MovementInputEvent> onInput = new Listener<>(e -> {
        boolean jump = pendingJump;
        pendingJump = false;
        if (jump && context() && usable() && System.nanoTime() - expires < 0
                && !e.isCancelled() && !e.isJump() && !JumpReset.isHandlingJumps()
                && mc.thePlayer.onGround && mc.thePlayer.isSprinting()
                && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()) e.setJump(true);
    });

    @Subscribe private final Listener<EventMotion> onMotion = new Listener<>(e -> {
        if (e.isPre() && RuntimeServices.rotation().hasPreparedMovement()) return;
        handleMotion(e);
    });

    @Subscribe private final Listener<EventRotation> prepareRotation = new Listener<>(preparation -> handleMotion(preparation.motion()));

    private void handleMotion(EventMotion e) {
        if (!context() || !usable() || System.nanoTime() - expires >= 0) { clearReduction(); return; }
        RotationManager rotation = RuntimeServices.rotation();
        if (e.isPre()) {
            target = null;
            if (remaining <= 0 || !canReduce()) { rotation.reset("velocity"); return; }
            remaining--;
            Module aura = Pulsar.INSTANCE.getMm() == null ? null : Pulsar.INSTANCE.getMm().getModule(Aura.class);
            if (aura != null && aura.isToggled() || RuntimeServices.attackScheduler().hasAttackThisTick()) return;
            TargetingService.Options options = new TargetingService.Options();
            options.range = 3;
            if ("Intave Reduce".equals(mode.getValue())) {
                target = RuntimeServices.targeting().getBestTarget(options, TargetingService.SortMode.DISTANCE);
            } else {
                double farthest = -1;
                for (EntityLivingBase candidate : RuntimeServices.targeting().collectTargets(options, TargetingService.SortMode.DISTANCE)) {
                    double distance = candidate.getDistanceSqToEntity(mc.thePlayer);
                    if (distance > farthest) { farthest = distance; target = candidate; }
                }
            }
            if (target == null) return;
            RotationProfile profile = new RotationProfile();
            profile.randomization = false;
            profile.yawSpeed = profile.pitchSpeed = 18;
            profile.reach = 3;
            float[] next = rotation.calculateRotations("velocity", e.getYaw(), e.getPitch(), target, true, profile, false);
            if (next != null) rotation.request("velocity", 15, next[0], next[1]);
        } else if (e.isPost() && e.isAccepted() && target != null && rotation.isOwner("velocity")) {
            final EntityLivingBase hit = target;
            final float yaw = rotation.getEffectiveYaw(), pitch = rotation.getEffectivePitch();
            if (!actions.mayAttack(System.nanoTime()) || !validHit(hit, yaw, pitch)) return;
            RuntimeServices.attackScheduler().tryAttack(rotation.getMotionSequence(), true, 20, 20, () -> {
                if (!validHit(hit, yaw, pitch)) return false;
                net.minecraft.client.entity.EntityPlayerSP attackingPlayer = mc.thePlayer;
                float cameraYaw = attackingPlayer.rotationYaw, cameraPitch = attackingPlayer.rotationPitch;
                try {
                    attackingPlayer.rotationYaw = yaw; attackingPlayer.rotationPitch = pitch;
                    attackingPlayer.swingItem();
                    return mc.playerController.tryAttackEntity(attackingPlayer, hit, () -> validHit(hit, yaw, pitch));
                } finally { attackingPlayer.rotationYaw = cameraYaw; attackingPlayer.rotationPitch = cameraPitch; }
            });
        }
    }

    private boolean validHit(EntityLivingBase hit, float yaw, float pitch) {
        return context() && usable() && canReduce() && AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                && !mc.thePlayer.isUsingItem() && !actions.usingItem(System.nanoTime())
                && hit != null && hit.worldObj == mc.theWorld && mc.theWorld.getEntityByID(hit.getEntityId()) == hit
                && !hit.isDead && hit.isEntityAlive() && RuntimeServices.rotation().isOwner("velocity")
                && RuntimeServices.rotation().matchesEffective(yaw, pitch)
                && RuntimeServices.raycast().canHit(hit, yaw, pitch, 3, false);
    }
    private boolean canReduce() {
        return !"Intave Reduce".equals(mode.getValue()) || mc.thePlayer.isSprinting();
    }
    public boolean correctionEnabled() { return isToggled() && context() && usable() && target != null; }
    private boolean usable() {
        return mc.thePlayer != null && mc.theWorld != null && mc.playerController != null
                && mc.currentScreen == null && mc.inGameHasFocus && mc.getRenderViewEntity() == mc.thePlayer
                && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isDead && !mc.thePlayer.isRiding() && !mc.thePlayer.isSpectator();
    }
    private boolean context() {
        PacketPipeline pipe = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getClientPackets();
        if (player != mc.thePlayer || world != mc.theWorld || connection != pipe
                || pipe != null && epoch != pipe.getContextVersion()) {
            clear(); player = mc.thePlayer; world = mc.theWorld; connection = pipe;
            epoch = pipe == null ? 0 : pipe.getContextVersion();
        }
        return player != null && world != null && pipe != null && pipe.isActive();
    }
    private void clearReduction() {
        remaining = 0; expires = 0; pendingJump = false; target = null;
        RuntimeServices.rotation().reset("velocity");
    }
    private void clear() { clearReduction(); actions.reset(); player = world = null; connection = null; }
}
