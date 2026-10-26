package me.youngjibbit.utils.rotations;

import com.google.common.base.Predicates;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.utils.Eierlecken;
import me.youngjibbit.utils.MathUtils;
import me.youngjibbit.utils.PacketUtils;
import me.youngjibbit.utils.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.*;

import java.io.IOException;
import java.util.List;

public class RUtil implements Eierlecken
{

    public static void setVisualRotations(float yaw, float pitch) {
        // Update the player's visual rotations on the client side
        mc.thePlayer.rotationYawHead =  yaw;
        mc.thePlayer.rotationPitchHead = pitch;
        mc.thePlayer.renderYawOffset = yaw;

        // Create and send a packet with the new rotations to the server
        // The server needs to know about the rotation updates to properly process them
        PacketUtils.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
    }


    public static void setVisualRotations(float[] rotations) {
        setVisualRotations(rotations[0], rotations[1]);
    }

    public static void setVisualRotations(EventMotion e) {
        setVisualRotations(e.getYaw(), e.getPitch());
    }

    public static float clampRotation() {
        float rotationYaw = Minecraft.getMinecraft().thePlayer.rotationYaw;
        float n = 1.0f;
        if (Minecraft.getMinecraft().thePlayer.movementInput.moveForward < 0.0f) {
            rotationYaw += 180.0f;
            n = -0.5f;
        } else if (Minecraft.getMinecraft().thePlayer.movementInput.moveForward > 0.0f) {
            n = 0.5f;
        }
        if (Minecraft.getMinecraft().thePlayer.movementInput.moveStrafe > 0.0f) {
            rotationYaw -= 90.0f * n;
        }
        if (Minecraft.getMinecraft().thePlayer.movementInput.moveStrafe < 0.0f) {
            rotationYaw += 90.0f * n;
        }
        return rotationYaw * 0.017453292f;
    }

    public static float getSensitivityMultiplier() {
        float SENSITIVITY = Minecraft.getMinecraft().gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return (SENSITIVITY * SENSITIVITY * SENSITIVITY * 8.0F) * 0.15F;
    }

    public static float smoothRotation(float from, float to, float speed) {
        float f = MathHelper.wrapAngleTo180_float(to - from);

        if (f > speed) {
            f = speed;
        }

        if (f < -speed) {
            f = -speed;
        }

        return from + f;
    }

    public static float[] getFacingRotations(ScaffoldUtils.BlockCache blockCache) {
        double d1 = blockCache.getPosition().getX() + 0.5D - mc.thePlayer.posX + blockCache.getFacing().getFrontOffsetX() / 2.0D;
        double d2 = blockCache.getPosition().getZ() + 0.5D - mc.thePlayer.posZ + blockCache.getFacing().getFrontOffsetZ() / 2.0D;
        double d3 = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - (blockCache.getPosition().getY());
        double d4 = MathHelper.sqrt_double(d1 * d1 + d2 * d2);
        float f1 = (float) (Math.atan2(d2, d1) * 180.0D / Math.PI) - 90.0F;
        float f2 = (float) (Math.atan2(d3, d4) * 180.0D / Math.PI);
        if (f1 < 0.0F) {
            f1 += 360.0F;
        }
        return new float[]{f1, f2};
    }

    public static float[] getRotations(BlockPos blockPos, EnumFacing enumFacing) {
        double d = (double) blockPos.getX() + 0.5 - mc.thePlayer.posX + (double) enumFacing.getFrontOffsetX() * 0.25;
        double d2 = (double) blockPos.getZ() + 0.5 - mc.thePlayer.posZ + (double) enumFacing.getFrontOffsetZ() * 0.25;
        double d3 = mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight() - blockPos.getY() - (double) enumFacing.getFrontOffsetY() * 0.25;
        double d4 = MathHelper.sqrt_double(d * d + d2 * d2);
        float f = (float) (Math.atan2(d2, d) * 180.0 / Math.PI) - 90.0f;
        float f2 = (float) (Math.atan2(d3, d4) * 180.0 / Math.PI);
        return new float[]{MathHelper.wrapAngleTo180_float(f), f2};
    }

    public static float[] getRotationsNeeded(final EntityLivingBase entity) {
        if (entity == null) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        final double xSize = entity.posX - mc.thePlayer.posX;
        final double ySize = entity.posY + entity.getEyeHeight() / 2 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        final double zSize = entity.posZ - mc.thePlayer.posZ;
        final double theta = MathHelper.sqrt_double(xSize * xSize + zSize * zSize);
        final float yaw = (float) (Math.atan2(zSize, xSize) * 180 / Math.PI) - 90;
        final float pitch = (float) (-(Math.atan2(ySize, theta) * 180 / Math.PI));
        return new float[]{(mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw)) % 360, (mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch)) % 360.0f};
    }

    public static float[] getRotationsDiff(EntityLivingBase entity) {
        if (entity == null) {
            return null;
        }
        float[] rotations = getRotationsNeeded(entity);
        return new float[]{Math.abs(mc.thePlayer.rotationYaw - rotations[0]), Math.abs(mc.thePlayer.rotationPitch - rotations[1])};
    }

    public static float[] getFacingRotations2(final int paramInt1, final double d, final int paramInt3) {
        final EntitySnowball localEntityPig = new EntitySnowball(Minecraft.getMinecraft().theWorld);
        localEntityPig.posX = paramInt1 + 0.5;
        localEntityPig.posY = d + 0.5;
        localEntityPig.posZ = paramInt3 + 0.5;
        return getRotationsNeeded(localEntityPig.getThrower());
    }

    public static float getEnumRotations(EnumFacing facing) {
        float yaw = 0;
        if (facing == EnumFacing.NORTH) {
            yaw = 0;
        }
        if (facing == EnumFacing.EAST) {
            yaw = 90;
        }
        if (facing == EnumFacing.WEST) {
            yaw = -90;
        }
        if (facing == EnumFacing.SOUTH) {
            yaw = 180;
        }
        return yaw;
    }

    public static float getYaw(Vec3 to) {
        float x = (float) (to.xCoord - mc.thePlayer.posX);
        float z = (float) (to.zCoord - mc.thePlayer.posZ);
        float var1 = (float) (StrictMath.atan2(z, x) * 180.0D / StrictMath.PI) - 90.0F;
        float rotationYaw = mc.thePlayer.rotationYaw;
        return rotationYaw + MathHelper.wrapAngleTo180_float(var1 - rotationYaw);
    }

    public static Vec3 getVecRotations(float yaw, float pitch) {
        double d = Math.cos(Math.toRadians(-yaw) - Math.PI);
        double d1 = Math.sin(Math.toRadians(-yaw) - Math.PI);
        double d2 = -Math.cos(Math.toRadians(-pitch));
        double d3 = Math.sin(Math.toRadians(-pitch));
        return new Vec3(d1 * d2, d3, d * d2);
    }

    public static float[] getSmoothRotations(EntityLivingBase entity, float rotationSpeed) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float fac = sensitivity * sensitivity * sensitivity * 256.0F;

        float currentYaw = mc.thePlayer.rotationYaw % 360;
        float currentPitch = mc.thePlayer.rotationPitch % 360;

        double x = entity.posX - mc.thePlayer.posX;
        double z = entity.posZ - mc.thePlayer.posZ;
        double y = entity.posY + entity.getEyeHeight()
                - (mc.thePlayer.getEntityBoundingBox().minY
                + (mc.thePlayer.getEntityBoundingBox().maxY
                - mc.thePlayer.getEntityBoundingBox().minY));

        double distance = MathHelper.sqrt_double(x * x + z * z);
        float targetYaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(y, distance) * 180.0 / Math.PI));

        float yawDifference = targetYaw - currentYaw;
        if (yawDifference < -180) yawDifference += 360;
        else if (yawDifference > 180) yawDifference -= 360;

        float pitchDifference = targetPitch - currentPitch;
        if (pitchDifference < -180) pitchDifference += 360;
        else if (pitchDifference > 180) pitchDifference -= 360;

        // Add jitter to the rotation targets (dynamic jitter)
        targetYaw += MathUtils.getRandomFloat(-4.0F, 4.0F);  // Small random jitter to yaw
        targetPitch += MathUtils.getRandomFloat(-3.0F, 3.0F); // Small random jitter to pitch

        // Adjust the smoothing speed based on rotationSpeed and the yaw/pitch difference
        float yawSpeed = fac * rotationSpeed * MathUtils.interpolateFloat(4.0F, 10.0F, Math.min(Math.abs(yawDifference) / 180f, 1f));
        float pitchSpeed = fac * rotationSpeed * MathUtils.interpolateFloat(4.0F, 10.0F, Math.min(Math.abs(pitchDifference) / 180f, 1f));

        // Slow down the rotation as it gets closer to the target
        if (Math.abs(yawDifference) < 10) {
            yawSpeed *= 0.5F; // Reduce speed when near target
        }
        if (Math.abs(pitchDifference) < 10) {
            pitchSpeed *= 0.5F; // Reduce speed when near target
        }

        // Apply the smoothing to the yaw and pitch with enhanced interpolation options
        float smoothYaw = smoothRotation(mc.thePlayer.prevRotationYawHead, targetYaw, yawSpeed);
        float smoothPitch = smoothRotation(mc.thePlayer.prevRotationPitchHead, targetPitch, pitchSpeed);

        // Return the smoothened yaw and pitch values
        return new float[]{smoothYaw, smoothPitch};
    }

    private static float[] calculateTargetRotations(EntityLivingBase entity) {
        double diffX = entity.posX - Wrapper.mc.thePlayer.posX;
        double diffY = (entity.posY + entity.getEyeHeight()) - (Wrapper.mc.thePlayer.posY + Wrapper.mc.thePlayer.getEyeHeight());
        double diffZ = entity.posZ - Wrapper.mc.thePlayer.posZ;

        double distance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Math.atan2(diffY, distance) * (180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] applyGCD(float yaw, float pitch, float prevYaw, float prevPitch){
        float d = ((mc.gameSettings.mouseSensitivity) * 0.6F + 0.2F) * 1.1f;
        float e = d * d * d;
        //float f2 = (float)this.mc.mouseHelper.deltaX * f1;
        //float f3 = (float)this.mc.mouseHelper.deltaY * f1;
        float f = e * 8.0F;

        double yawSens = (yaw - prevYaw) * f;
        double pitchSens = (pitch - prevPitch) * f;

        return new float[]{(float) (prevYaw + yawSens), (float) (prevPitch + pitchSens)};
    }

    public static boolean isMouseOver(final float yaw, final float pitch, final Entity target, final float range) {
        final float partialTicks = mc.timer.renderPartialTicks;
        final Entity entity = mc.getRenderViewEntity();
        MovingObjectPosition objectMouseOver;
        Entity mcPointedEntity = null;

        if (entity != null && mc.theWorld != null) {

            mc.mcProfiler.startSection("pick");
            final double d0 = mc.playerController.getBlockReachDistance();
            objectMouseOver = entity.rayTrace(d0, partialTicks);
            double d1 = d0;
            final Vec3 vec3 = entity.getPositionEyes(partialTicks);
            final boolean flag = d0 > (double) range;

            if (objectMouseOver != null) {
                d1 = objectMouseOver.hitVec.distanceTo(vec3);
            }

            final Vec3 vec31 = mc.thePlayer.getVectorForRotation(pitch, yaw);
            final Vec3 vec32 = vec3.addVector(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0);
            Entity pointedEntity = null;
            Vec3 vec33 = null;
            final float f = 1.0F;
            final List<Entity> list = mc.theWorld.getEntitiesInAABBexcluding(entity, entity.getEntityBoundingBox().addCoord(vec31.xCoord * d0, vec31.yCoord * d0, vec31.zCoord * d0).expand(f, f, f), Predicates.and(EntitySelectors.NOT_SPECTATING, Entity::canBeCollidedWith));
            double d2 = d1;

            for (final Entity entity1 : list) {
                final float f1 = entity1.getCollisionBorderSize();
                final AxisAlignedBB axisalignedbb = entity1.getEntityBoundingBox().expand(f1, f1, f1);
                final MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32);

                if (axisalignedbb.isVecInside(vec3)) {
                    if (d2 >= 0.0D) {
                        pointedEntity = entity1;
                        vec33 = movingobjectposition == null ? vec3 : movingobjectposition.hitVec;
                        d2 = 0.0D;
                    }
                } else if (movingobjectposition != null) {
                    final double d3 = vec3.distanceTo(movingobjectposition.hitVec);

                    if (d3 < d2 || d2 == 0.0D) {
                        pointedEntity = entity1;
                        vec33 = movingobjectposition.hitVec;
                        d2 = d3;
                    }
                }
            }

            if (pointedEntity != null && flag && vec3.distanceTo(vec33) > (double) range) {
                pointedEntity = null;
                objectMouseOver = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, vec33, null, new BlockPos(vec33));
            }

            if (pointedEntity != null && (d2 < d1 || objectMouseOver == null)) {
                if (pointedEntity instanceof EntityLivingBase || pointedEntity instanceof EntityItemFrame) {
                    mcPointedEntity = pointedEntity;
                }
            }

            mc.mcProfiler.endSection();

            return mcPointedEntity == target;
        }

        return false;
    }

}
