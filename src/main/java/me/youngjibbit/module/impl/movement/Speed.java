package me.youngjibbit.module.impl.movement;

import me.youngjibbit.event.motion.EventMotion;
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

@ModuleInfo(name = "Speed", category = Category.Movement)
public class Speed extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"BMC", "Intave", "Polar", "Vulcan", "Vulcan2", "Intave Momentum"}, 1);
    private final CheckBoxValue airSpeed = new CheckBoxValue("Air Speed", this, false);

    private int offGroundTicks;
    private int groundTicks;
    private int polarAirTicks;
    private boolean vulcan2DropApplied;

    public Speed() {
        setKey(Keyboard.KEY_C);
        airSpeed.setVisibility(m -> !"Intave Momentum".equals(mode.getValue()));
        mode.setOnChange(selected -> {
            if (isToggled() && "Intave Momentum".equals(selected)) {
                resetMovementOverrides();
                resetState();
            }
        });
    }

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (!"Intave Momentum".equals(mode.getValue())) resetMovementOverrides();
        resetState();
        super.onDisable();
    }

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
        if (mc.thePlayer == null || mc.theWorld == null || !event.isPre() || "Intave Momentum".equals(mode.getValue())) {
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
