package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Executes the real filter and selection without constructing a game or GL context. */
public class TargetingServicePerformanceTest {
    private static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    public static class Player extends EntityPlayerSP {
        private Player() { super(null, null, null, null); }
        @Override public boolean canEntityBeSeen(Entity e) { return true; }
        @Override public Vec3 getPositionEyes(float partial) { return new Vec3(0, 0, 0); }
    }
    public static class Mob extends EntityMob {
        int score, reads;
        boolean alive = true, invisible;
        private Mob() { super(null); }
        @Override public int getTotalArmorValue() { reads++; return score; }
        @Override public int getHurtTime() { return score; }
        @Override public boolean isEntityAlive() { return alive; }
        @Override public boolean isInvisible() { return invisible; }
    }
    public static class HealthWatcher extends DataWatcher {
        float health;
        private HealthWatcher() { super(null); }
        @Override public float getWatchableObjectFloat(int id) { return health; }
    }
    private static void field(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static Mob mob(int score, float health) throws Exception {
        Mob m = (Mob) UNSAFE.allocateInstance(Mob.class);
        m.score = score; m.alive = true;
        m.posX = score / 1000.0;
        m.setEntityBoundingBox(new AxisAlignedBB(0, 0, 0, 1, 1, 1));
        HealthWatcher watcher = (HealthWatcher) UNSAFE.allocateInstance(HealthWatcher.class); watcher.health = health;
        field(m, Entity.class, "dataWatcher", watcher);
        return m;
    }
    @Test public void bestMatchesStableSortedFirstAndAvoidsSortingWork() throws Exception {
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft"); singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Minecraft mc = (Minecraft) UNSAFE.allocateInstance(Minecraft.class);
        mc.thePlayer = (Player) UNSAFE.allocateInstance(Player.class);
        mc.theWorld = (WorldClient) UNSAFE.allocateInstance(WorldClient.class);
        List<Entity> loaded = new ArrayList<>();
        field(mc.theWorld, net.minecraft.world.World.class, "loadedEntityList", loaded);
        singleton.set(null, mc);
        try {
            TargetingService service = new TargetingService();
            TargetingService.Options options = new TargetingService.Options(); options.mobs = true;
            Mob first = mob(5, 7), tied = mob(5, 7);
            loaded.add(first); loaded.add(tied);
            for (TargetingService.SortMode mode : TargetingService.SortMode.values()) {
                assertSame(first, service.getBestTarget(options, mode));
            }
            Random random = new Random(891);
            float[] health = {Float.NaN, -0.0f, 0.0f, 1.0f, Float.POSITIVE_INFINITY};
            for (int trial = 0; trial < 50; trial++) {
                loaded.clear();
                loaded.add(mc.thePlayer); loaded.add(null);
                for (int i = 0; i < 128; i++) {
                    Mob m = mob(random.nextInt(20), health[random.nextInt(health.length)]);
                    m.alive = i % 11 != 0; m.invisible = i % 13 == 0;
                    if (i % 17 == 0) m.setEntityBoundingBox(new AxisAlignedBB(20, 0, 0, 21, 1, 1));
                    loaded.add(m);
                }
                for (TargetingService.SortMode mode : TargetingService.SortMode.values()) {
                    List<EntityLivingBase> sorted = service.collectTargets(options, mode);
                    assertSame(sorted.isEmpty() ? null : sorted.get(0), service.getBestTarget(options, mode));
                }
            }
            loaded.clear();
            for (int i = 0; i < 1024; i++) loaded.add(mob(random.nextInt(1000), 10));
            List<EntityLivingBase> sorted = service.collectTargets(options, TargetingService.SortMode.ARMOR);
            int sortReads = 0;
            for (Entity e : loaded) { sortReads += ((Mob)e).reads; ((Mob)e).reads = 0; }
            assertSame(sorted.get(0), service.getBestTarget(options, TargetingService.SortMode.ARMOR));
            int scanReads = 0;
            for (Entity e : loaded) scanReads += ((Mob)e).reads;
            assertEquals(2 * (loaded.size() - 1), scanReads);
            assertTrue(sortReads > scanReads * 4);
            System.out.println("Targeting 1024 candidates: sort comparator calls=" + sortReads / 2
                    + ", minimum scan=" + scanReads / 2);
            loaded.clear();
            assertNull(service.getBestTarget(options, TargetingService.SortMode.HEALTH));
            mc.theWorld = null;
            assertNull(service.getBestTarget(options, TargetingService.SortMode.HEALTH));
        } finally { singleton.set(null, previous); }
    }
}
