package me.youngjibbit.core.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Restrictive client-thread gate for automatic jump requests. The onGround flag
 * alone can outlive the support below the player until the next physics step.
 * A shared claim also prevents two automatic requests before that step runs.
 */
public final class GroundJumpGuard {
    private static final double SUPPORT_DEPTH = .01, EPSILON = 1e-4;
    private static WeakReference<EntityPlayerSP> claimedPlayer = new WeakReference<>(null);
    private static WeakReference<World> claimedWorld = new WeakReference<>(null);
    private static int claimedTick;

    private GroundJumpGuard() { }

    public static boolean supported(Minecraft client) {
        if (client == null) return false;
        EntityPlayerSP player = client.thePlayer;
        World world = client.theWorld;
        if (!eligible(player, world)) return false;
        AxisAlignedBB box = player.getEntityBoundingBox();
        if (!valid(box)) return false;
        double insetX = Math.min(.01, (box.maxX - box.minX) * .1);
        double insetZ = Math.min(.01, (box.maxZ - box.minZ) * .1);
        AxisAlignedBB probe = new AxisAlignedBB(box.minX + insetX, box.minY - SUPPORT_DEPTH, box.minZ + insetZ,
                box.maxX - insetX, box.minY + EPSILON, box.maxZ - insetZ);
        int tick = player.ticksExisted;
        List<AxisAlignedBB> collisions = world.getCollidingBoundingBoxes(player, probe);
        // Collision queries can invoke block callbacks; do not use a stale scene.
        if (client.thePlayer != player || client.theWorld != world || player.ticksExisted != tick
                || !eligible(player, world) || !same(box, player.getEntityBoundingBox()) || collisions == null) return false;
        for (AxisAlignedBB support : collisions) {
            if (valid(support) && support.maxY >= box.minY - SUPPORT_DEPTH
                    && support.maxY <= box.minY + EPSILON
                    && support.maxX > probe.minX && support.minX < probe.maxX
                    && support.maxZ > probe.minZ && support.minZ < probe.maxZ) return true;
        }
        return false;
    }

    public static boolean tryClaim(Minecraft client, BooleanSupplier ownerStillEnabled) {
        if (client == null || ownerStillEnabled == null || !ownerStillEnabled.getAsBoolean()) return false;
        EntityPlayerSP player = client.thePlayer;
        World world = client.theWorld;
        if (alreadyClaimed(player, world) || !supported(client)
                || client.thePlayer != player || client.theWorld != world
                || !ownerStillEnabled.getAsBoolean() || alreadyClaimed(player, world)) return false;
        claimedPlayer = new WeakReference<>(player);
        claimedWorld = new WeakReference<>(world);
        claimedTick = player.ticksExisted;
        return true;
    }

    private static boolean alreadyClaimed(EntityPlayerSP player, World world) {
        return player != null && claimedPlayer.get() == player && claimedWorld.get() == world
                && claimedTick == player.ticksExisted;
    }

    private static boolean eligible(EntityPlayerSP player, World world) {
        return player != null && world != null && player.worldObj == world && player.onGround
                && !player.noClip && !player.isDead && player.isEntityAlive()
                && Float.isFinite(player.fallDistance) && player.fallDistance <= 0;
    }

    private static boolean valid(AxisAlignedBB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ;
    }

    private static boolean same(AxisAlignedBB before, AxisAlignedBB after) {
        return after != null && before.minX == after.minX && before.minY == after.minY && before.minZ == after.minZ
                && before.maxX == after.maxX && before.maxY == after.maxY && before.maxZ == after.maxZ;
    }
}
