package me.youngjibbit.core.world;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;

/** Scaffold's MoveUtil operations from DemisePrivate e219a97 (AGPL-3.0).
 * The last key direction intentionally survives a frame with no movement keys.
 */
public final class DemiseScaffoldMovement {
    private float yaw;

    public static boolean moving(EntityPlayerSP player) {
        return player != null && (player.moveForward != 0F || player.moveStrafing != 0F);
    }

    public float keyYaw(EntityPlayerSP player, GameSettings keys) {
        if (keys.keyBindForward.isKeyDown() && keys.keyBindLeft.isKeyDown()) yaw = 45f;
        else if (keys.keyBindForward.isKeyDown() && keys.keyBindRight.isKeyDown()) yaw = -45f;
        else if (keys.keyBindBack.isKeyDown() && keys.keyBindLeft.isKeyDown()) yaw = 135f;
        else if (keys.keyBindBack.isKeyDown() && keys.keyBindRight.isKeyDown()) yaw = -135f;
        else if (keys.keyBindBack.isKeyDown()) yaw = 180f;
        else if (keys.keyBindLeft.isKeyDown()) yaw = 90f;
        else if (keys.keyBindRight.isKeyDown()) yaw = -90f;
        else if (keys.keyBindForward.isKeyDown()) yaw = 0f;
        return player.rotationYaw - yaw;
    }

    public boolean straight(EntityPlayerSP player, GameSettings keys) {
        float direction = keyYaw(player, keys) + 180;
        float movingYaw = Math.round(direction / 45) * 45;
        return movingYaw % 90 == 0f;
    }

    public static double speed(EntityPlayerSP player) {
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    public void strafe(EntityPlayerSP player, GameSettings keys, double speed) {
        double motionX = player.motionX, motionZ = player.motionZ;
        if (!moving(player)) return;
        double direction = (float) Math.toRadians(keyYaw(player, keys));
        player.motionX = -Math.sin(direction) * speed;
        player.motionZ = Math.cos(direction) * speed;
        player.motionX = motionX + (player.motionX - motionX);
        player.motionZ = motionZ + (player.motionZ - motionZ);
    }

    public void diagonalSpeed(EntityPlayerSP player, GameSettings keys) {
        KeyBinding[] bindings = {keys.keyBindForward, keys.keyBindRight, keys.keyBindBack, keys.keyBindLeft};
        int down = 0;
        for (KeyBinding binding : bindings) if (binding.isKeyDown()) down++;
        if (down != 1 || !moving(player)) return;
        double groundIncrease = (0.1299999676734952 - 0.12739998266255503) + 1E-7 - 1E-8;
        double airIncrease = (0.025999999334873708 - 0.025479999685988748) - 1E-8;
        double increase = player.onGround ? groundIncrease : airIncrease;
        double direction = (float) Math.toRadians(keyYaw(player, keys));
        player.motionX += -MathHelper.sin((float) direction) * increase;
        player.motionZ += MathHelper.cos((float) direction) * increase;
    }
}
