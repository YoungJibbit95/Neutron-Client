package me.youngjibbit.module.impl.client;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.CombatDiagnosticTrace;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.AttackResultEvent;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Locale;

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
        postSequence = Long.MIN_VALUE;
        eventId = 0;
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

    @Subscribe private final Listener<AttackEvent> attack = new Listener<>(event ->
            capture("ATTACK_PRE_ATTEMPT", event.getTargetEntity()));

    @Subscribe private final Listener<AttackResultEvent> result = new Listener<>(event ->
            capture("ATTACK_LOCAL_ACCEPTED", event.getTargetEntity()));

    @Subscribe private final Listener<PacketReceiveEvent> inbound = new Listener<>(event -> {
        if (event.getPacket() instanceof S08PacketPlayerPosLook)
            capture("S08_RECEIVED_NOT_A_FLAG", null);
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

        String message = String.format(Locale.ROOT,
                "%s event=%d tick=%d epochMs=%d owner=%s track=%s seq=%d camera=%.2f/%.2f cameraSource=%s playerPose=%.2f/%.2f wireKnown=%s localWire=%.2f/%.2f motionAccepted=%s delta=%.2f/%.2f targetId=%d centerErr=%.2f/%.2f",
                type, ++eventId, mc.thePlayer.ticksExisted, System.currentTimeMillis(), owner, trackedBy,
                rotations.getMotionSequence(), cameraYaw, cameraPitch, postCamera ? "motionPost" : "player",
                playerYaw, playerPitch, wireKnown, wireYaw, wirePitch, rotations.isMotionAccepted(),
                yawDifference, pitchDifference, targetId, errorYaw, errorPitch);
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
            int left = 5, top = 6, height = 42 + items.size() * 11;
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
            int y = top + 38;
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
