package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.AttackResultEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.player.MovementReadyEvent;
import me.youngjibbit.event.world.WorldEvent;
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
import java.util.function.LongSupplier;

@ModuleInfo(name = "WTap2", category = Category.Ghost)
public class WTap2 extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Legit", "Spoof", "STap"}, 0);
    private final SliderValue durationMs = new SliderValue("Duration (ms)", this, 140.0, 5.0, 400.0, true);
    private final SliderValue cooldownMs = new SliderValue("Cooldown (ms)", this, 260.0, 5.0, 1000.0, true);
    private final SliderValue minDistance = new SliderValue("Min Distance", this, 1.2, 0.0, 10.0, false);
    private final SliderValue maxDistance = new SliderValue("Max Distance", this, 3.6, 0.0, 10.0, false);

    private LongSupplier legitClock = () -> System.nanoTime() / 1_000_000L;
    private long lastLegitActivation = Long.MIN_VALUE;
    private EntityLivingBase candidate;
    private Object legitPlayer, legitWorld;
    private PacketPipeline legitConnection;
    private long legitEpoch;
    private boolean pendingTap, tappedStep, stapOwned;
    private boolean spoofedSprint;
    private long lastActivation;
    private long releaseAt;
    private boolean mouseDown;

    public WTap2() {
        this.setKey(Keyboard.KEY_0);
    }

    @Override
    public void onDisable() {
        resetLegit();
        spoofedSprint = false;
        lastActivation = 0L;
        releaseAt = 0L;
        mouseDown = false;
        if (stapOwned && mc.thePlayer != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
        }
        stapOwned = false;
        super.onDisable();
    }

    @Subscribe
    private final Listener<AttackEvent> onAttack = new Listener<>(event -> {
        candidate = null;
        if (mc.thePlayer == null || mc.theWorld == null || event == null || event.getTargetEntity() == null) {
            return;
        }

        EntityLivingBase target = event.getTargetEntity();
        switch (mode.getValue()) {
            case "STap":
                if (canActivate(target)) activateStapWindow();
                break;
            case "Legit":
                // Capture pre-attack eligibility only. Native successful sprint attacks have
                // already ended sprint by AttackResultEvent; a cancelled C02 must do nothing.
                if (usableLegit() && !event.isCancelled() && !pendingTap
                        && mc.thePlayer.onGround && mc.thePlayer.isSprinting()
                        && inRange(target) && (lastLegitActivation == Long.MIN_VALUE
                        || legitClock.getAsLong() - lastLegitActivation >= cooldownMs.getValue())) {
                    candidate = target;
                    legitPlayer = mc.thePlayer;
                    legitWorld = mc.theWorld;
                    legitConnection = currentConnection();
                    legitEpoch = legitConnection == null ? 0 : legitConnection.getContextVersion();
                }
                break;
            default:
                break;
        }
    });

    @Subscribe private final Listener<AttackResultEvent> attackAccepted = new Listener<>(event -> {
        EntityLivingBase expected = candidate;
        candidate = null;
        if (expected != null && event.getTargetEntity() == expected && validLegitContext()) {
            pendingTap = true;
            lastLegitActivation = legitClock.getAsLong();
        }
    });

    @Subscribe private final Listener<MovementInputEvent> input = new Listener<>(event -> {
        candidate = null;
        tappedStep = false;
        if (!validLegitContext()) { resetLegit(); return; }
        if (pendingTap && !event.isCancelled()) {
            pendingTap = false;
            tappedStep = true;
            if (event.getForward() > 0) event.setForward(0);
        }
    });

    @Subscribe private final Listener<WorldEvent> world = new Listener<>(event -> resetLegit());

    @Override public void onValueChange() { resetLegit(); }

    private boolean usableLegit() {
        return "Legit".equals(mode.getValue()) && mc != null && mc.thePlayer != null && mc.theWorld != null
                && mc.inGameHasFocus && mc.currentScreen == null && mc.thePlayer.isEntityAlive()
                && !mc.thePlayer.isSpectator() && !mc.thePlayer.isRiding();
    }

    private boolean validLegitContext() {
        return usableLegit() && legitPlayer == mc.thePlayer && legitWorld == mc.theWorld
                && legitConnection != null && legitConnection == currentConnection() && legitConnection.isActive()
                && legitEpoch == legitConnection.getContextVersion();
    }

    private PacketPipeline currentConnection() {
        return mc == null || mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getClientPackets();
    }

    private void resetLegit() {
        candidate = null; pendingTap = tappedStep = false;
        legitPlayer = legitWorld = null; legitConnection = null; lastLegitActivation = Long.MIN_VALUE;
    }

    @Subscribe
    private final Listener<TickEvent> tickEvent = new Listener<>(event -> {
        if (!usableLegit()) resetLegit();
        if (mc.thePlayer == null || mc.theWorld == null || !event.isPre()) {
            return;
        }

        EntityLivingBase target = resolveTarget();
        switch (mode.getValue()) {
            case "STap":
                handleStap(target);
                break;
            default:
                break;
        }
    });

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (event.isPre() && !RuntimeServices.rotation().hasPreparedMovement()) applySpoof(true);
    });

    @Subscribe private final Listener<MovementReadyEvent> movementReady = new Listener<>(event -> {
        if (tappedStep && validLegitContext()) mc.thePlayer.setSprinting(false);
        tappedStep = false;
        applySpoof(false);
    }, me.zero.alpine.event.EventPriority.LOWEST);

    private void applySpoof(boolean restorePhysicalKey) {
        if (mc.thePlayer == null || !"Spoof".equalsIgnoreCase(mode.getValue())) return;

        EntityLivingBase target = resolveTarget();
        if (target == null) {
            if (restorePhysicalKey && spoofedSprint && shouldResumeSprint()) {
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
            if (restorePhysicalKey && spoofedSprint && shouldResumeSprint()) {
                mc.thePlayer.setSprinting(true);
            }
            spoofedSprint = false;
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
        if (System.currentTimeMillis() - lastActivation < cooldownMs.getValue()) {
            return false;
        }
        return inRange(target);
    }

    private boolean inRange(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive() || mc.thePlayer == null) return false;
        double min = Math.min(minDistance.getValue(), maxDistance.getValue());
        double max = Math.max(minDistance.getValue(), maxDistance.getValue());
        double distance = mc.thePlayer.getDistanceToEntity(target);
        return distance >= min && distance <= max;
    }

    private void activateStapWindow() {
        stapOwned = true;
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
