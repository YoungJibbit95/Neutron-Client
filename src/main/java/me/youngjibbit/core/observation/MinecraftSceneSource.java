package me.youngjibbit.core.observation;

import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

/** Reads existing connection/packet evidence; never creates or manages a session. */
public final class MinecraftSceneSource {
    private MinecraftSceneSource() { }

    public static RuntimeObservation.SceneSample capture(Minecraft client) {
        EntityPlayerSP player = client == null ? null : client.thePlayer;
        WorldClient world = client == null ? null : client.theWorld;
        NetHandlerPlayClient handler = player == null ? null : player.sendQueue;
        NetworkManager connection = handler == null ? null : handler.getNetworkManager();
        PacketPipeline pipeline = connection == null ? null : connection.getClientPackets();
        boolean live = player != null && world != null && player.worldObj == world
                && !player.isDead && player.isEntityAlive() && pipeline != null && pipeline.isActive();
        boolean suspended = client == null || client.currentScreen != null || !client.inGameHasFocus || client.isGamePaused();
        return new RuntimeObservation.SceneSample(connection, player, world, pipeline,
                pipeline == null ? -1 : pipeline.getContextVersion(), live, suspended);
    }
}
