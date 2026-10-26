package me.youngjibbit.utils;

import com.mojang.authlib.GameProfile;
import me.youngjibbit.module.impl.combat.DelayedPacketThread;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldSettings;

import java.util.UUID;

public final class PacketUtils implements Eierlecken {

    private PacketUtils() {
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S") || mc.getNetHandler() == null) {
            return;
        }
        mc.getNetHandler().sendSilent(packet);
    }

    public static void sendPacket(Packet<?> packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S") || mc.getNetHandler() == null) {
            return;
        }
        mc.getNetHandler().addToSendQueue(packet);
    }

    public static void receivePacketNoEvent(Packet<INetHandlerPlayClient> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }

        mc.getNetHandler().receiveSilent(packet);
    }

    public static void receivePacket(Packet<INetHandlerPlayClient> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }

        mc.getNetHandler().getNetworkManager().receivePacket(packet, false);
    }

    public static void sendBlocking(boolean callEvent, boolean placement) {
        if (mc.thePlayer == null) {
            return;
        }

        Packet<?> packet = placement
                ? new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0, 0, 0)
                : new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem());

        if (callEvent) {
            sendPacket(packet);
        } else {
            sendPacketNoEvent(packet);
        }
    }

    public static void releaseUseItem(boolean callEvent) {
        if (mc.thePlayer == null) {
            return;
        }

        Packet<?> packet = new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN);
        if (callEvent) {
            sendPacket(packet);
        } else {
            sendPacketNoEvent(packet);
        }
    }

    public static void releaseUseItem(long delay) {
        if (mc.thePlayer == null) {
            return;
        }

        DelayedPacketThread thread = new DelayedPacketThread(
                new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN),
                delay
        );
        thread.run();
    }

    public static void startSprinting() {
        sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
    }

    public static void stopSprinting() {
        sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
    }

    public static void startSneaking() {
        sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
    }

    public static void stopSneaking() {
        sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
    }

    public static boolean sendInteract(Entity target) {
        mc.playerController.syncCurrentPlayItem();
        sendPacketNoEvent(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
        return mc.playerController.getCurrentGameType() != WorldSettings.GameType.SPECTATOR && mc.thePlayer.interactWith(target);
    }

    public static void attackFakePlayer() {
        EntityOtherPlayerMP fakePlayer = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "a"));
        sendPacketNoEvent(new C0APacketAnimation());
        sendPacketNoEvent(new C02PacketUseEntity(fakePlayer, C02PacketUseEntity.Action.ATTACK));
    }
}
