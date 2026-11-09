package me.youngjibbit.module.impl.movement;

import me.youngjibbit.core.movement.IntaveBoatSpeed;
import me.youngjibbit.core.movement.IntaveMomentumProfile;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.motion.LocalImpulseEvent;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.utils.player.PlayerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;
import net.minecraft.potion.Potion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;

@ModuleInfo(name = "Speed", category = Category.Movement)
public class Speed extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"BMC", "Intave", "Polar", "Vulcan", "Vulcan2", "Intave Momentum", "Intave Boat", "Intave Momentum V2"}, 1);
    private final CheckBoxValue airSpeed = new CheckBoxValue("Air Speed", this, false);
    private final IntaveBoatSpeed intaveBoat = new IntaveBoatSpeed();
    private IntaveMomentumProfile momentumProfile = new IntaveMomentumProfile();
    private final Thread momentumThread = Thread.currentThread();
    private java.lang.ref.WeakReference<RuntimeObservation> momentumObservation = new java.lang.ref.WeakReference<>(null);
    private SceneContext momentumScene;
    private long momentumGeneration, momentumLastOuter = -1, momentumLastTime, momentumCorrectionAt;
    private boolean momentumHasTime, momentumCorrectionPending;
    private final java.util.function.LongSupplier momentumClock = () -> RuntimeServices.rotation().readMonotonicNanos();
    private String previousMode = "Intave";
    private String modeBeforeChange = "Intave";
    private static Speed active;

    private int offGroundTicks;
    private int groundTicks;
    private int polarAirTicks;
    private boolean vulcan2DropApplied;

    public Speed() {
        setKey(Keyboard.KEY_C);
        airSpeed.setVisibility(m -> !inputSpeedMode() && !momentumV2());
        mode.setOnChange(selected -> {
            if (isToggled() && inputSpeedMode()) {
                if (!"Intave Momentum V2".equals(modeBeforeChange)) resetMovementOverrides();
                resetState();
            }
        });
    }

    @Override
    public void onEnable() {
        resetState();
        intaveBoat.reset();
        resetMomentumV2();
        previousMode = mode.getValue();
        active = this;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (active == this) active = null;
        if (!inputSpeedMode() && !momentumV2()) resetMovementOverrides();
        intaveBoat.reset();
        resetMomentumV2();
        info = "";
        resetState();
        super.onDisable();
    }

    private boolean inputSpeedMode() {
        return "Intave Momentum".equals(mode.getValue()) || "Intave Boat".equals(mode.getValue());
    }

    private boolean momentumV2() { return "Intave Momentum V2".equals(mode.getValue()); }

    @Override public void onValueChange() {
        if (intaveBoat != null) intaveBoat.reset();
        if (momentumProfile != null) {
            if (mode != null && momentumV2() && "Intave Momentum V2".equals(previousMode)) resetMomentumRamp();
            else resetMomentumV2();
        }
        // Only an outgoing legacy motion mode owns these overrides. V2 itself owns none.
        if (mode != null) {
            if (!mode.getValue().equals(previousMode)) modeBeforeChange = previousMode;
            if (isToggled() && momentumV2() && !"Intave Momentum V2".equals(previousMode)
                    && !"Intave Momentum".equals(previousMode) && !"Intave Boat".equals(previousMode))
                resetMovementOverrides();
            previousMode = mode.getValue();
        }
        info = "";
    }

    /** Final moveFlying consumer, after all listeners and the accepted rotation correction. */
    public static void applyBoatAcceleration(EntityPlayerSP player, PlayerMoveUpdateEvent event) {
        Speed speed = active;
        if (speed != null && speed.momentumV2()) {
            speed.applyMomentumV2(player, event);
            return;
        }
        if (speed == null || !"Intave Boat".equals(speed.mode.getValue()) || mc == null || player != mc.thePlayer) return;
        speed.intaveBoat.apply(mc, event);
        if (active == speed && "Intave Boat".equals(speed.mode.getValue()))
            speed.info = "Boat: " + speed.intaveBoat.getReason();
    }

    @Subscribe private final Listener<WorldEvent> boatWorld = new Listener<>(event -> {
        intaveBoat.reset(); resetMomentumV2(); info = "";
    });
    @Subscribe private final Listener<LocalImpulseEvent> momentumImpulse = new Listener<>(event -> {
        Minecraft client = Minecraft.getMinecraft();
        if (active == this && momentumV2() && client != null && event.getPlayer() == client.thePlayer
                && event.getPipeline() == momentumPackets(client.thePlayer)) resetMomentumV2();
    });
    @Subscribe private final Listener<EventPacket> momentumCorrection = new Listener<>((EventPacket event) -> {
        Minecraft client = Minecraft.getMinecraft();
        if (active != this || !momentumV2() || !event.isPost() || !event.isInbound()
                || !(event.getPacket() instanceof S08PacketPlayerPosLook) || client == null
                || event.getPipeline() != momentumPackets(client.thePlayer)) return;
        resetMomentumV2();
        long generation = momentumGeneration;
        RuntimeObservation observation = RuntimeServices.observation();
        long now = momentumClock.getAsLong();
        if (Minecraft.getMinecraft() != client || !momentumCurrent(generation, client.thePlayer)
                || RuntimeServices.observation() != observation) return;
        momentumCorrectionAt = now;
        momentumCorrectionPending = true;
    });
    @Subscribe private final Listener<KnockbackEvent> boatImpulse = new Listener<>(event -> {
        if (event.isPost() && "Intave Boat".equals(mode.getValue())) intaveBoat.pauseForImpulse();
    });
    @Subscribe private final Listener<EventPacket> boatPacket = new Listener<>(event -> {
        if (!event.isPost() || !event.isInbound() || !"Intave Boat".equals(mode.getValue())
                || mc == null || mc.getNetHandler() == null
                || event.getPipeline() != mc.getNetHandler().getNetworkManager().getClientPackets()) return;
        if (event.getPacket() instanceof S08PacketPlayerPosLook) intaveBoat.pauseForImpulse();
        else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
            if (explosion.func_149149_c() != 0 || explosion.func_149144_d() != 0 || explosion.func_149147_e() != 0)
                intaveBoat.pauseForImpulse();
        }
    });

    @Subscribe
    private final Listener<MovementInputEvent> momentum = new Listener<>(event -> {
        if (event.isCancelled() || event.isSneak() || event.getForward() <= 0 || !momentumReady()
                || mc.thePlayer.isCollidedHorizontally || !mc.thePlayer.isSprinting()) return;
        event.setJump(true);
    });

    @Subscribe private final Listener<PlayerMoveUpdateEvent> wallAcceleration = new Listener<>(event -> {
        if (event.isCancelled() || !momentumReady() || !mc.thePlayer.isCollidedHorizontally
                || event.getFriction() <= 0 || event.getForward() == 0 && event.getStrafe() == 0) return;
        // Intave's collision branch widens horizontal tolerance. Let vanilla resolve the blocked axis.
        event.setFriction(event.getFriction() + .012F);
    });

    private boolean momentumReady() {
        return "Intave Momentum".equals(mode.getValue()) && mc.thePlayer != null && mc.theWorld != null
                && mc.currentScreen == null && mc.inGameHasFocus && mc.thePlayer.onGround && mc.thePlayer.hurtTime == 0
                && !mc.thePlayer.isSneaking() && !mc.thePlayer.isUsingItem() && !mc.thePlayer.isRiding()
                && !mc.thePlayer.capabilities.isFlying && !mc.thePlayer.isOnLadder() && !PlayerUtil.inLiquid();
    }

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (momentumV2()) return;
        if (mc.thePlayer == null || mc.theWorld == null || !event.isPre() || inputSpeedMode()) {
            return;
        }

        if (PlayerUtil.inLiquid() || mc.thePlayer.isOnLadder()) {
            resetMovementOverrides();
            resetState();
            return;
        }

        switch (mode.getValue()) {
            case "Intave":
                handleIntave();
                break;
            case "BMC":
                handleBmc();
                break;
            case "Polar":
                handlePolar();
                break;
            case "Vulcan":
                handleVulcan();
                break;
            case "Vulcan2":
                handleVulcan2();
                break;
            default:
                break;
        }
    });

    /** V2 owns only numeric ramp state; resets preserve the observer's consumed outer-tick tombstone. */
    private void resetMomentumV2() {
        momentumGeneration++;
        momentumScene = null;
        momentumHasTime = momentumCorrectionPending = false;
        if (momentumProfile != null) momentumProfile.reset(momentumGeneration);
    }

    private void resetMomentumRamp() {
        boolean pending = momentumCorrectionPending;
        long at = momentumCorrectionAt;
        resetMomentumV2();
        momentumCorrectionPending = pending;
        momentumCorrectionAt = at;
    }

    private boolean momentumCurrent(long generation, EntityPlayerSP player) {
        Minecraft client = Minecraft.getMinecraft();
        return Thread.currentThread() == momentumThread && active == this && isToggled() && momentumV2()
                && momentumGeneration == generation && client != null && player != null && player == client.thePlayer
                && client.theWorld != null && player.worldObj == client.theWorld;
    }

    private boolean momentumCurrent(long generation, EntityPlayerSP player, RuntimeObservation observation, Minecraft client) {
        return Minecraft.getMinecraft() == client && momentumCurrent(generation, player)
                && RuntimeServices.observation() == observation
                && momentumObservation.get() == observation;
    }

    private static PacketPipeline momentumPackets(EntityPlayerSP player) {
        return player == null || player.sendQueue == null || player.sendQueue.getNetworkManager() == null
                ? null : player.sendQueue.getNetworkManager().getClientPackets();
    }

    /** Existing final moveFlying hook; no direct motion, jump, key, timer or packet writes. */
    private void applyMomentumV2(EntityPlayerSP player, PlayerMoveUpdateEvent event) {
        Minecraft client = Minecraft.getMinecraft();
        long generation = momentumGeneration;
        if (!momentumCurrent(generation, player) || event == null || event.isCancelled()) return;
        RuntimeObservation observation = RuntimeServices.observation();
        if (observation == null) return;
        if (momentumObservation.get() != observation) {
            momentumObservation = new java.lang.ref.WeakReference<>(observation);
            momentumProfile = new IntaveMomentumProfile();
            momentumLastOuter = -1;
            resetMomentumRamp();
            generation = momentumGeneration;
        }
        ExecutedStep step = observation.getCurrentStep();
        if (step == null || step.isInterrupted() || step.getKind() != ExecutedStep.Kind.ORDINARY
                || step.getOuterTickId() < 0 || observation.getActiveOuterTickId() != step.getOuterTickId()
                || step.getOuterTickId() <= momentumLastOuter) return;
        momentumLastOuter = step.getOuterTickId();
        SceneContext fresh = observation.captureScene();
        if (!momentumCurrent(generation, player, observation, client)) return;
        if (!activeMomentumScene(fresh) || !activeMomentumScene(step.getScene())
                || !fresh.sameSceneAs(step.getScene()) || observation.getCurrentStep() != step) {
            resetMomentumRamp();return;
        }
        if (momentumScene == null || !momentumScene.sameSceneAs(fresh)) {
            // A correction reset keeps its pause while acquiring the first scene again.
            resetMomentumRamp();
            generation = momentumGeneration;
            momentumScene = fresh;
        }
        long now = momentumClock.getAsLong();
        if (!momentumCurrent(generation, player, observation, client)) return;
        if (momentumHasTime && now < momentumLastTime) {
            boolean pending = momentumCorrectionPending;
            resetMomentumV2();
            momentumCorrectionPending = pending;
            momentumCorrectionAt = now;
            return;
        }
        momentumLastTime = now;momentumHasTime = true;
        boolean correction = momentumCorrectionPending && now - momentumCorrectionAt < 500_000_000L;
        if (!correction) momentumCorrectionPending = false;
        PacketPipeline pipeline = momentumPackets(player);
        boolean impulse = pipeline == null || !pipeline.isActive() || pipeline.isRecoveringLocalImpulse(player);
        if (!momentumCurrent(generation, player, observation, client)) return;
        // The ladder read can call into the world. Revalidate the scene after these queries.
        boolean environment = !player.isOnLadder() && !player.isInWater() && !player.isInLava();
        if (!momentumCurrent(generation, player, observation, client)) return;
        SceneContext last = observation.captureScene();
        if (!momentumCurrent(generation, player, observation, client) || observation.getCurrentStep() != step
                || observation.getActiveOuterTickId() != step.getOuterTickId()
                || !activeMomentumScene(last) || !fresh.sameSceneAs(last)) return;
        boolean eligible = environment && !correction && !impulse && client.getRenderViewEntity() == player
                && client.currentScreen == null && client.inGameHasFocus && !client.isGamePaused()
                && player.isEntityAlive() && !player.isDead && !player.isSpectator()
                && player.isSprinting() && !player.isSneaking() && !player.isUsingItem() && !player.isRiding()
                && !player.capabilities.isFlying && !player.capabilities.allowFlying && !player.isInWeb
                && player.hurtTime == 0 && player.movementInput != null && !player.movementInput.sneak
                && Double.isFinite(player.posX) && Double.isFinite(player.posY) && Double.isFinite(player.posZ)
                && Double.isFinite(player.motionY) && (!player.onGround || Math.abs(player.motionY) <= .1);
        if (!momentumCurrent(generation, player, observation, client)) return;
        IntaveMomentumProfile.Result result = momentumProfile.step(new IntaveMomentumProfile.Input(
                generation, step.getOuterTickId(), true, eligible, player.onGround, player.isCollidedHorizontally,
                event.getStrafe(), event.getForward(), event.getYaw(), event.getFriction(), player.motionX, player.motionZ));
        if (!result.hasAdjustment() || !momentumCurrent(generation, player, observation, client)) return;
        // Verify the model bound using Vanilla's actual float sine table and normalization.
        float strafe = event.getStrafe(), forward = event.getForward();
        float length = net.minecraft.util.MathHelper.sqrt_float(strafe * strafe + forward * forward);
        float scale = result.friction / Math.max(1F, length);
        strafe *= scale;forward *= scale;
        float radians = event.getYaw() * (float)Math.PI / 180F;
        float deltaX = strafe * net.minecraft.util.MathHelper.cos(radians)
                - forward * net.minecraft.util.MathHelper.sin(radians);
        float deltaZ = forward * net.minecraft.util.MathHelper.cos(radians)
                + strafe * net.minecraft.util.MathHelper.sin(radians);
        double dx = player.motionX + deltaX, dz = player.motionZ + deltaZ;
        if (Double.isFinite(dx) && Double.isFinite(dz)
                && Math.hypot(dx, dz) <= IntaveMomentumProfile.MAX_PREDICTED_HORIZONTAL)
            event.setFriction(result.friction);
    }

    private static boolean activeMomentumScene(SceneContext scene) {
        return scene != null && scene.isValid() && !scene.isSuspended();
    }

    private void handleIntave() {
        if (!MoveUtil.hasMovementInput(mc.thePlayer) || PlayerUtil.jumpDown()) {
            return;
        }

        if (mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            if (mc.thePlayer.isSprinting()) {
                float directionRadians = (float) MoveUtil.confirmedDirectionRadians(mc.thePlayer);
                MoveUtil.setHorizontalMotion(mc.thePlayer, Math.max(0.29F, (float) getModeBaseSpeed()), directionRadians);
            }
            offGroundTicks = 0;
            return;
        }

        offGroundTicks++;
        if (airSpeed.getValue() && mc.thePlayer.motionY > 0.0 && mc.thePlayer.isSprinting()) {
            mc.thePlayer.motionX *= 1.0015;
            mc.thePlayer.motionZ *= 1.0015;
        }
    }

    private void handleBmc() {
        if (!MoveUtil.hasMovementInput(mc.thePlayer)) {
            resetMovementOverrides();
            return;
        }

        mc.timer.timerSpeed = 1.0f;
        if (mc.thePlayer.onGround) {
            offGroundTicks = 0;
            groundTicks++;
            if (groundTicks % 2 == 0) {
                mc.thePlayer.jump();
            }
            MoveUtil.strafe(mc.thePlayer, getModeBaseSpeed() * 1.08);
            return;
        }

        groundTicks = 0;
        offGroundTicks++;
        mc.thePlayer.speedInAir = airSpeed.getValue() ? 0.03F : 0.02F;
        if (airSpeed.getValue() && offGroundTicks > 4) {
            mc.thePlayer.motionX *= 1.003;
            mc.thePlayer.motionZ *= 1.003;
        }
    }

    private void handlePolar() {
        if (!MoveUtil.hasMovementInput(mc.thePlayer) || mc.thePlayer.hurtTime > 0) {
            mc.timer.timerSpeed = 1.0f;
            polarAirTicks = 0;
            return;
        }

        if (mc.thePlayer.onGround) {
            polarAirTicks = 0;
            mc.timer.timerSpeed = 1.0f;
            mc.thePlayer.jump();
            MoveUtil.strafe(mc.thePlayer, getModeBaseSpeed() * 1.18);
            return;
        }

        polarAirTicks++;
        if (polarAirTicks == 1) {
            MoveUtil.strafe(mc.thePlayer, Math.max(MoveUtil.horizontalSpeed(mc.thePlayer) * 0.98, getModeBaseSpeed()));
        }

        mc.timer.timerSpeed = polarAirTicks >= 3 && polarAirTicks <= 6 ? 1.03f : 1.0f;
    }

    private void handleVulcan() {
        if (!MoveUtil.hasMovementInput(mc.thePlayer)) {
            resetMovementOverrides();
            return;
        }

        mc.timer.timerSpeed = 1.0f;
        int amp = getSpeedAmplifier();
        double base = getModeBaseSpeed();

        if (mc.thePlayer.onGround) {
            offGroundTicks = 0;
            mc.thePlayer.jump();
            MoveUtil.strafe(mc.thePlayer, base * (1.30 + amp * 0.10));
            return;
        }

        offGroundTicks++;
        if (offGroundTicks == 1) {
            MoveUtil.strafe(mc.thePlayer, Math.max(MoveUtil.horizontalSpeed(mc.thePlayer) * 0.92, base * 1.02));
        }

        if (offGroundTicks > 4 && mc.thePlayer.motionY < 0.0 && mc.thePlayer.fallDistance < 1.0f) {
            mc.thePlayer.motionY -= 0.03;
        }
    }

    private void handleVulcan2() {
        if (!MoveUtil.hasMovementInput(mc.thePlayer) || mc.gameSettings.keyBindJump.isKeyDown()) {
            resetMovementOverrides();
            return;
        }

        mc.timer.timerSpeed = 1.0f;
        int amp = getSpeedAmplifier();
        double base = getModeBaseSpeed();

        if (mc.thePlayer.onGround) {
            offGroundTicks = 0;
            vulcan2DropApplied = false;
            mc.thePlayer.jump();

            double boost = base * (1.34 + amp * 0.08);
            if (mc.thePlayer.moveStrafing != 0.0f) {
                boost -= 0.01;
            }
            MoveUtil.strafe(mc.thePlayer, boost);
            return;
        }

        offGroundTicks++;
        if (!vulcan2DropApplied && offGroundTicks == 1) {
            mc.thePlayer.motionY = -0.13;
            vulcan2DropApplied = true;
        }

        if (airSpeed.getValue() && offGroundTicks <= 2) {
            MoveUtil.strafe(mc.thePlayer, Math.max(MoveUtil.horizontalSpeed(mc.thePlayer) * 0.62, base));
        }
    }

    // Module-specific tuning, not vanilla walk speed or the MoveFix input transform.
    private double getModeBaseSpeed() {
        double baseSpeed = mc.thePlayer.isSneaking() ? 0.0663D : (mc.thePlayer.isSprinting() ? 0.2873D : 0.221D);
        int amplifier = (mc.thePlayer.isPotionActive(Potion.moveSpeed) ? mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1 : 0)
                - (mc.thePlayer.isPotionActive(Potion.moveSlowdown) ? mc.thePlayer.getActivePotionEffect(Potion.moveSlowdown).getAmplifier() + 1 : 0);
        return baseSpeed * (1.0 + 0.2 * (amplifier + 1));
    }

    private int getSpeedAmplifier() {
        return mc.thePlayer.isPotionActive(Potion.moveSpeed)
                ? mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() : 0;
    }

    private void resetState() {
        offGroundTicks = 0;
        groundTicks = 0;
        polarAirTicks = 0;
        vulcan2DropApplied = false;
    }

    private void resetMovementOverrides() {
        if (mc.thePlayer == null) {
            return;
        }

        mc.gameSettings.keyBindJump.pressed = Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
        mc.timer.timerSpeed = 1.0f;
        mc.thePlayer.jumpMovementFactor = 0.02F;
        mc.thePlayer.speedInAir = 0.02F;
    }
}
