package me.youngjibbit.core.combat;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.*;

public class CombatCorrectionPreviewTest {

    @Test public void absoluteS08ComputesPreviewWithoutChangingThePacket() {
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(3, 4, 5, 90, -30,
                EnumSet.noneOf(S08PacketPlayerPosLook.EnumFlags.class));
        CombatCorrectionPreview p = CombatCorrectionPreview.atReceipt(packet, 1, 2, 3, 45, 10);
        assertEquals(3, p.targetX, 0);
        assertEquals(4, p.targetY, 0);
        assertEquals(5, p.targetZ, 0);
        assertEquals(2, p.deltaX, 0);
        assertEquals(2, p.deltaY, 0);
        assertEquals(2, p.deltaZ, 0);
        assertEquals(Math.sqrt(12), p.distance, 0);
        assertEquals(90, p.targetYaw, 0);
        assertEquals(-30, p.targetPitch, 0);
        assertEquals("[]", p.flags);
        assertEquals(3, packet.getX(), 0);
        assertEquals(90, packet.getYaw(), 0);
    }

    @Test public void allRelativeFlagsUseTheReceiptTimePlayerPose() {
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(.5, -.25, 1, 2, -3,
                EnumSet.allOf(S08PacketPlayerPosLook.EnumFlags.class));
        CombatCorrectionPreview p = CombatCorrectionPreview.atReceipt(packet, 100, 64, -20, -179, 40);
        assertEquals(100.5, p.targetX, 0);
        assertEquals(63.75, p.targetY, 0);
        assertEquals(-19, p.targetZ, 0);
        assertEquals(.5, p.deltaX, 0);
        assertEquals(-.25, p.deltaY, 0);
        assertEquals(1, p.deltaZ, 0);
        assertEquals(Math.sqrt(.25 + .0625 + 1), p.distance, 1e-12);
        assertEquals(-177, p.targetYaw, 0);
        assertEquals(37, p.targetPitch, 0);
    }

    @Test public void mixedFlagsApplyOnlyToTheirAxes() {
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(2, 77, -8, 15, 20,
                EnumSet.of(S08PacketPlayerPosLook.EnumFlags.X, S08PacketPlayerPosLook.EnumFlags.Y_ROT));
        CombatCorrectionPreview p = CombatCorrectionPreview.atReceipt(packet, 10, 5, -10, 50, -10);
        assertEquals(12, p.targetX, 0);
        assertEquals(77, p.targetY, 0);
        assertEquals(-8, p.targetZ, 0);
        assertEquals(65, p.targetYaw, 0);
        assertEquals(20, p.targetPitch, 0);
        assertEquals("[X, Y_ROT]", p.flags);
    }

    @Test public void nullFlagSetIsInterpretedAsNoRelativeAxes() {
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(0, 0, 0, 0, 0, null);
        CombatCorrectionPreview p = CombatCorrectionPreview.atReceipt(packet, 12, 64, -8, 90, 10);
        assertEquals(-12, p.deltaX, 0);
        assertEquals(-64, p.deltaY, 0);
        assertEquals(8, p.deltaZ, 0);
        assertEquals("[]", p.flags);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotPreviewANullPacket() {
        CombatCorrectionPreview.atReceipt(null, 0, 0, 0, 0, 0);
    }
}
