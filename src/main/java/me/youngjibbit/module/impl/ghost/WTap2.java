package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "WTap2", category = Category.Ghost)
public class WTap2 extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Legit", "Spoof", "STap"}, 0);
    private final SliderValue durationMs = new SliderValue("Duration (ms)", this, 140.0, 5.0, 400.0, true);
    private final SliderValue cooldownMs = new SliderValue("Cooldown (ms)", this, 260.0, 5.0, 1000.0, true);
    private final SliderValue minDistance = new SliderValue("Min Distance", this, 1.2, 0.0, 10.0, false);
    private final SliderValue maxDistance = new SliderValue("Max Distance", this, 3.6, 0.0, 10.0, false);

    private boolean stoppedLastTick;
    private boolean spoofedSprint;
    private long lastActivation;
    private long releaseAt;
    private boolean mouseDown;

    public WTap2() {
        this.setKey(Keyboard.KEY_0);
    }

    @Override
    public void onDisable() {
        stoppedLastTick = false;
        spoofedSprint = false;
        lastActivation = 0L;
        releaseAt = 0L;
        mouseDown = false;
        if (mc.thePlayer != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
            mc.thePlayer.setSprinting(Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()));
        }
        super.onDisable();
    }

    @Subscribe
    private final Listener<AttackEvent> onAttack = new Listener<>(event -> {
        if (mc.thePlayer == null || mc.theWorld == null || event == null || event.getTargetEntity() == null) {
            return;
        }

        EntityLivingBase target = event.getTargetEntity();
        if (!canActivate(target)) {
            return;
        }

        switch (mode.getValue()) {
            case "STap":
                activateStapWindow();
                break;
            case "Legit":
                if (mc.thePlayer.onGround && mc.thePlayer.isSprinting()) {
                    mc.gameSettings.keyBindSprint.pressed = false;
                    mc.gameSettings.keyBindForward.pressed = false;
                    stoppedLastTick = true;
                }
                break;
            default:
                break;
        }
    });

    @Subscribe
    private final Listener<TickEvent> tickEvent = new Listener<>(event -> {
        if (mc.thePlayer == null || mc.theWorld == null || !event.isPre()) {
            return;
        }

        EntityLivingBase target = resolveTarget();
        switch (mode.getValue()) {
            case "STap":
                handleStap(target);
                break;
            case "Legit":
                handleLegit(target);
                break;
            default:
                break;
        }
    });

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (mc.thePlayer == null || !event.isPre()) {
            return;
        }

        if (!"Spoof".equalsIgnoreCase(mode.getValue())) {
            return;
        }

        EntityLivingBase target = resolveTarget();
        if (target == null) {
            if (spoofedSprint && shouldResumeSprint()) {
                mc.thePlayer.setSprinting(true);
            }
            spoofedSprint = false;
            return;
        }

        if (target.hurtTime > 0 && mc.thePlayer.onGround && mc.thePlayer.isSprinting()) {
            if (!spoofedSprint) {
                mc.thePlayer.setSprinting(false);
                spoofedSprint = true;
            }
        } else {
            if (spoofedSprint && shouldResumeSprint()) {
                mc.thePlayer.setSprinting(true);
            }
            spoofedSprint = false;
        }
    });

    private void handleLegit(EntityLivingBase target) {
        if (stoppedLastTick) {
            mc.gameSettings.keyBindForward.pressed = Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode());
            stoppedLastTick = false;
        }

        if (target == null) {
            return;
        }

        if (target.hurtTime > 0 && mc.thePlayer.onGround && mc.thePlayer.isSprinting()) {
            mc.gameSettings.keyBindSprint.pressed = false;
            mc.gameSettings.keyBindForward.pressed = false;
            stoppedLastTick = true;
        }
    }

    private void handleStap(EntityLivingBase target) {
        boolean click = Mouse.isButtonDown(0);
        boolean clickEdge = click && !mouseDown;
        mouseDown = click;

        if (clickEdge && target != null && canActivate(target)) {
            activateStapWindow();
        }

        if (System.currentTimeMillis() < releaseAt) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
            return;
        }

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
    }

    private boolean canActivate(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive() || mc.thePlayer == null) {
            return false;
        }

        if (System.currentTimeMillis() - lastActivation < cooldownMs.getValue()) {
            return false;
        }

        double min = Math.min(minDistance.getValue(), maxDistance.getValue());
        double max = Math.max(minDistance.getValue(), maxDistance.getValue());
        double distance = mc.thePlayer.getDistanceToEntity(target);
        return distance >= min && distance <= max;
    }

    private void activateStapWindow() {
        lastActivation = System.currentTimeMillis();
        releaseAt = lastActivation + durationMs.getValue().longValue();
    }

    private boolean shouldResumeSprint() {
        return Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()) && MoveUtil.hasMovementInput(mc.thePlayer);
    }

    private EntityLivingBase resolveTarget() {
        EntityLivingBase tracked = CombatTargetTracker.getPrimaryTarget(350L);
        if (tracked != null) {
            return tracked;
        }

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            return (EntityLivingBase) mc.objectMouseOver.entityHit;
        }

        return null;
    }
}
