package me.youngjibbit.core.combat;

import net.minecraft.entity.EntityLivingBase;

public final class CombatTargetTracker {

    private static volatile EntityLivingBase primaryTarget;
    private static volatile String source = "none";
    private static volatile long updatedAt;

    private CombatTargetTracker() {
    }

    public static void update(EntityLivingBase target, String targetSource) {
        primaryTarget = target;
        source = targetSource == null ? "unknown" : targetSource;
        updatedAt = System.currentTimeMillis();
    }

    public static EntityLivingBase getPrimaryTarget() {
        return primaryTarget;
    }

    /** Read Aura directly so cursor, manual attacks and AimAssist cannot authorize its dependants. */
    public static EntityLivingBase getAuraTarget() {
        me.youngjibbit.module.ModuleManager modules = me.youngjibbit.Pulsar.INSTANCE.getMm();
        if (modules == null) return null;
        me.youngjibbit.module.impl.combat.Aura aura =
                (me.youngjibbit.module.impl.combat.Aura) modules.getModule(me.youngjibbit.module.impl.combat.Aura.class);
        return aura == null ? null : aura.getActiveTarget();
    }

    public static EntityLivingBase getPrimaryTarget(long maxAgeMs) {
        EntityLivingBase target = primaryTarget;
        if (target == null) {
            return null;
        }

        if (maxAgeMs > 0L && System.currentTimeMillis() - updatedAt > maxAgeMs) {
            return null;
        }

        if (!target.isEntityAlive()) {
            return null;
        }

        return target;
    }

    public static boolean hasFreshTarget(long maxAgeMs) {
        return getPrimaryTarget(maxAgeMs) != null;
    }

    public static String getSource() {
        return source;
    }

    public static long getUpdatedAt() {
        return updatedAt;
    }

    public static void clear() {
        primaryTarget = null;
        source = "none";
        updatedAt = System.currentTimeMillis();
    }

    public static void clear(String clearSource) {
        if (clearSource == null || "all".equalsIgnoreCase(clearSource) || source.equalsIgnoreCase(clearSource)) {
            clear();
        }
    }
}
