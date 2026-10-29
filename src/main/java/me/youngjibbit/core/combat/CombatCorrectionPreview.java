package me.youngjibbit.core.combat;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.Collections;
import java.util.Set;

/**
 * A receipt-time preview of an S08 position/look packet. No player state or packet is changed.
 * PacketReceiveEvent may precede delayed application, so this is NOT a measured setback.
 */
public final class CombatCorrectionPreview {
    public final double targetX, targetY, targetZ;
    public final double deltaX, deltaY, deltaZ, distance;
    public final float targetYaw, targetPitch;
    public final String flags;

    private CombatCorrectionPreview(double x, double y, double z,
                                    double dx, double dy, double dz, float yaw, float pitch,
                                    String flags) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.deltaX = dx;
        this.deltaY = dy;
        this.deltaZ = dz;
        this.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.flags = flags;
    }

    public static CombatCorrectionPreview atReceipt(S08PacketPlayerPosLook packet,
                                                    double x, double y, double z,
                                                    float yaw, float pitch) {
        if (packet == null) throw new IllegalArgumentException("S08 packet required");
        Set<S08PacketPlayerPosLook.EnumFlags> flags = packet.func_179834_f();
        if (flags == null) flags = Collections.emptySet();

        double resultX = packet.getX() + (flags.contains(S08PacketPlayerPosLook.EnumFlags.X) ? x : 0);
        double resultY = packet.getY() + (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y) ? y : 0);
        double resultZ = packet.getZ() + (flags.contains(S08PacketPlayerPosLook.EnumFlags.Z) ? z : 0);
        float resultYaw = packet.getYaw() + (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT) ? yaw : 0);
        float resultPitch = packet.getPitch() + (flags.contains(S08PacketPlayerPosLook.EnumFlags.X_ROT) ? pitch : 0);

        return new CombatCorrectionPreview(resultX, resultY, resultZ, resultX - x, resultY - y,
                resultZ - z, resultYaw, resultPitch, flags.toString());
    }
}
