package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TargetingService {

    public enum SortMode {
        DISTANCE,
        HEALTH,
        HURT_TIME,
        ARMOR
    }

    public static final class Options {
        public double range = 3.0D;
        public boolean players = true;
        public boolean animals = false;
        public boolean mobs = false;
        public boolean invisibles = false;
        public boolean throughWalls = false;
        public boolean sameTeam = false;
        public boolean allowDead = false;
        /** Full horizontal cone in degrees; 360 preserves unrestricted historical callers. */
        public double fov=360;
    }

    private final Minecraft mc = Minecraft.getMinecraft();

    public List<EntityLivingBase> collectTargets(Options options, SortMode sortMode) {
        List<EntityLivingBase> targets = new ArrayList<>();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return targets;
        }

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase livingBase = (EntityLivingBase) entity;
            if (!isValidTarget(livingBase, options)) {
                continue;
            }

            targets.add(livingBase);
        }

        sortTargets(targets, sortMode);
        return targets;
    }

    public EntityLivingBase getBestTarget(Options options, SortMode sortMode) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        Comparator<EntityLivingBase> comparator = targetComparator(sortMode);
        EntityLivingBase best = null;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) entity;
            if (isValidTarget(candidate, options)
                    && (best == null || comparator.compare(candidate, best) < 0)) {
                // Strict improvement preserves the first loaded entity on stable-sort ties.
                best = candidate;
            }
        }
        return best;
    }

    private void sortTargets(List<EntityLivingBase> targets, SortMode sortMode) {
        targets.sort(targetComparator(sortMode));
    }

    private Comparator<EntityLivingBase> targetComparator(SortMode sortMode) {
        switch (sortMode) {
            case HEALTH:
                return Comparator.comparingDouble(EntityLivingBase::getHealth);
            case HURT_TIME:
                return Comparator.comparingInt(EntityLivingBase::getHurtTime);
            case ARMOR:
                return Comparator.comparingInt(EntityLivingBase::getTotalArmorValue);
            case DISTANCE:
            default:
                return Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity));
        }
    }

    public boolean isValidTarget(EntityLivingBase entity, Options options) {
        if (entity == null || mc.thePlayer == null) {
            return false;
        }

        if (entity == mc.thePlayer) {
            return false;
        }

        if (entity instanceof EntityArmorStand || entity instanceof EntityVillager) {
            return false;
        }

        if (!options.allowDead && !entity.isEntityAlive()) {
            return false;
        }

        net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        float border = entity.getCollisionBorderSize();
        net.minecraft.util.AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        double x = Math.max(box.minX, Math.min(box.maxX, eyes.xCoord)) - eyes.xCoord;
        double y = Math.max(box.minY, Math.min(box.maxY, eyes.yCoord)) - eyes.yCoord;
        double z = Math.max(box.minZ, Math.min(box.maxZ, eyes.zCoord)) - eyes.zCoord;
        if (x*x + y*y + z*z > options.range * options.range) {
            return false;
        }
        if(options.fov<360) {
            double yaw=Math.toDegrees(Math.atan2(entity.posZ-mc.thePlayer.posZ,entity.posX-mc.thePlayer.posX))-90;
            if(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_double(yaw-mc.thePlayer.rotationYaw))>options.fov/2)return false;
        }

        boolean visible = mc.thePlayer.canEntityBeSeen(entity);
        if (!options.throughWalls && !visible) {
            return false;
        }

        if (entity.isInvisible() && !options.invisibles) {
            return false;
        }

        if (entity instanceof EntityPlayer) {
            if (!options.players) {
                return false;
            }
            if (!options.sameTeam
                    && mc.thePlayer.getTeam() != null
                    && ((EntityPlayer) entity).getTeam() != null
                    && mc.thePlayer.getTeam().isSameTeam(((EntityPlayer) entity).getTeam())) {
                return false;
            }
            return true;
        }

        if (entity instanceof EntityAnimal) {
            return options.animals;
        }

        if (entity instanceof EntityMob) {
            return options.mobs;
        }

        return false;
    }

    /** Multi keeps the selected member until an actual attack, then cycles within the configured limit. */
    public static <T> T selectTarget(List<T> sorted, T previous, boolean multi, int limit, boolean advance) {
        if (sorted.isEmpty()) return null;
        int count = multi ? Math.min(Math.max(1, limit), sorted.size()) : 1;
        int index = sorted.subList(0, count).indexOf(previous);
        if (index < 0) index = 0;
        else if (multi && advance) index = (index + 1) % count;
        return sorted.get(index);
    }
}
