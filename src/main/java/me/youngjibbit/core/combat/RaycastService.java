package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import java.util.ArrayList;
import java.util.List;

public final class RaycastService {
    private final Minecraft mc;
    public RaycastService() { this(Minecraft.getMinecraft()); }
    public RaycastService(Minecraft mc) { this.mc=mc; }

    public boolean canHit(Entity target, float yaw, float pitch, float reach) {
        return canHit(target, yaw, pitch, reach, false);
    }

    public boolean canHit(Entity target, float yaw, float pitch, float reach, boolean throughWalls) {
        return target != null && raycast(yaw, pitch, reach, throughWalls) == target;
    }

    public Entity raycast(float yaw, float pitch, float reach) {
        return raycast(yaw, pitch, reach, false);
    }

    public Entity raycast(float yaw, float pitch, float reach, boolean throughWalls) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return null;
        return raycastFrom(mc.thePlayer.getLastReportedPosition(),yaw,pitch,reach,throughWalls);
    }

    /** Explicit feet position: camera assistance uses the local pose; action guards retain the accepted pose. */
    public boolean canHitFrom(Vec3 position,Entity target,float yaw,float pitch,float reach,boolean throughWalls) {
        return target!=null && raycastFrom(position,yaw,pitch,reach,throughWalls)==target;
    }
    public Entity raycastFrom(Vec3 position,float yaw,float pitch,float reach,boolean throughWalls) {
        if(mc==null || mc.thePlayer==null || mc.theWorld==null || position==null)return null;
        Entity viewer = mc.thePlayer;
        double range = Math.max(0, reach);
        Vec3 eyes = position.addVector(0, viewer.getEyeHeight(), 0);
        Vec3 look = viewer.getVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition block = throughWalls ? null : mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        double obstruction = block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? eyes.distanceTo(block.hitVec) : Double.POSITIVE_INFINITY;
        AxisAlignedBB search = viewer.getEntityBoundingBox().offset(position.xCoord-viewer.posX,
                position.yCoord-viewer.posY,position.zCoord-viewer.posZ).addCoord(look.xCoord*range, look.yCoord*range, look.zCoord*range).expand(1,1,1);
        List<Collision<Entity>> collisions = new ArrayList<>();
        for (Entity entity : mc.theWorld.getEntitiesInAABBexcluding(viewer, search, EntitySelectors.NOT_SPECTATING)) {
            if (!entity.canBeCollidedWith()) continue;
            float border = entity.getCollisionBorderSize();
            collisions.add(new Collision<>(entity, entity.getEntityBoundingBox().expand(border,border,border), entity.getEntityId()));
        }
        Entity hit = closestHit(eyes, look, range, obstruction, collisions);
        return hit instanceof EntityLivingBase ? hit : null;
    }

    /** Same nearest-hit calculation used in the world path, with explicit collision inputs for tests. */
    public static <T> T closestHit(Vec3 eyes, Vec3 look, double reach, double obstruction, List<Collision<T>> collisions) {
        double closest = Math.max(0, reach);
        T hitEntity = null;
        int hitId = Integer.MAX_VALUE;
        Vec3 end = eyes.addVector(look.xCoord*closest, look.yCoord*closest, look.zCoord*closest);
        for (Collision<T> collision : collisions) {
            MovingObjectPosition hit = collision.bounds.calculateIntercept(eyes, end);
            double distance = collision.bounds.isVecInside(eyes) ? 0 : hit == null ? Double.POSITIVE_INFINITY : eyes.distanceTo(hit.hitVec);
            if (distance < obstruction && distance <= reach && (distance < closest ||
                    (distance == closest && (hitEntity == null || collision.id < hitId)))) {
                hitEntity = collision.entity;
                hitId = collision.id;
                closest = distance;
            }
        }
        return hitEntity;
    }

    public static final class Collision<T> {
        final T entity;
        final AxisAlignedBB bounds;
        final int id;
        public Collision(T entity, AxisAlignedBB bounds, int id) { this.entity=entity; this.bounds=bounds; this.id=id; }
    }
}
