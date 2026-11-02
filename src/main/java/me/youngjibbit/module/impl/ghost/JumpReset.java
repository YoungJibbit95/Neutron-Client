package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/** Melee-confirmed pre-application reset, adapted from Arsenic; see docs/third-party/ARSENIC.md. */
@ModuleInfo(name = "JumpReset", description = "Resets jumps on confirmed nearby melee hits", category = Category.Ghost)
public class JumpReset extends Module {
    public final SliderValue jumpChance = new SliderValue("Jump Chance", this, 100D, 0, 100, true);
    private static JumpReset active;
    private final Map<Entity, Long> swings = new IdentityHashMap<>();
    private LongSupplier clock = System::nanoTime;
    private DoubleSupplier random = Math::random;
    private Object player, world;
    private PacketPipeline connection;
    private long epoch, hurtAt;
    private boolean hurt;

    public JumpReset() { this(System::nanoTime, Math::random); }
    public JumpReset(LongSupplier clock, DoubleSupplier random) { this.clock = clock; this.random = random; }
    public static boolean isHandlingJumps() { return active != null && active.isToggled(); }
    @Override public void onEnable() { reset(); active = this; super.onEnable(); }
    @Override public void onDisable() { if (active == this) active = null; reset(); super.onDisable(); }
    @Override public void onValueChange() { if (swings != null) reset(); }
    @Subscribe private final Listener<WorldEvent> onWorld = new Listener<>(e -> reset());

    @Subscribe private final Listener<PacketReceiveEvent> onPacket = new Listener<>(e -> {
        if (!context() || e.getPipeline() != connection) return;
        long now = clock.getAsLong();
        swings.entrySet().removeIf(entry -> now - entry.getValue() > 400_000_000L || entry.getKey().isDead
                || mc.theWorld.getEntityByID(entry.getKey().getEntityId()) != entry.getKey());
        if (e.getPacket() instanceof S0BPacketAnimation) {
            S0BPacketAnimation swing = (S0BPacketAnimation) e.getPacket();
            Entity entity = mc.theWorld.getEntityByID(swing.getEntityID());
            if (swing.getAnimationType() == 0 && entity instanceof EntityPlayer && entity != mc.thePlayer) {
                if (swings.size() >= 64) swings.clear();
                swings.put(entity, now);
            }
        } else if (e.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) e.getPacket();
            if (status.getOpCode() == 2 && status.getEntity(mc.theWorld) == mc.thePlayer) { hurtAt = now; hurt = true; }
        }
    });

    @Subscribe private final Listener<KnockbackEvent> onKnockback = new Listener<>(e -> {
        if (!e.isPre() || e.isCancelled() || !context()) return;
        long now = clock.getAsLong();
        boolean recentHurt = hurt && now - hurtAt >= 0 && now - hurtAt <= 150_000_000L;
        hurt = false;
        if (!recentHurt || e.getX() == 0 && e.getZ() == 0 || e.getY() <= 0
                || !mc.thePlayer.onGround || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || mc.thePlayer.isRiding() || mc.thePlayer.isDead || !mc.thePlayer.isEntityAlive()
                || mc.currentScreen != null || !mc.inGameHasFocus || !melee(now)
                || random.getAsDouble() >= jumpChance.getValue() / 100D) return;
        // Do not call jump(): its sprint impulse would be added a second time.
        // NetHandler immediately replaces this Y with the unchanged server knockback.
        mc.thePlayer.motionY = .42;
        mc.thePlayer.isAirBorne = true;
    });

    private boolean melee(long now) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1);
        for (Map.Entry<Entity, Long> entry : swings.entrySet()) {
            EntityPlayer enemy = (EntityPlayer) entry.getKey();
            if (now - entry.getValue() < 0 || now - entry.getValue() > 400_000_000L || enemy.isDead
                    || enemy.worldObj != mc.theWorld || mc.theWorld.getEntityByID(enemy.getEntityId()) != enemy
                    || projectile(enemy.getHeldItem()) || !mc.thePlayer.canEntityBeSeen(enemy)) continue;
            AxisAlignedBB box = enemy.getEntityBoundingBox();
            double dx = Math.max(box.minX, Math.min(box.maxX, eyes.xCoord)) - eyes.xCoord;
            double dy = Math.max(box.minY, Math.min(box.maxY, eyes.yCoord)) - eyes.yCoord;
            double dz = Math.max(box.minZ, Math.min(box.maxZ, eyes.zCoord)) - eyes.zCoord;
            if (dx * dx + dy * dy + dz * dz <= 4.5 * 4.5) return true;
        }
        return false;
    }
    private static boolean projectile(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemBow || item instanceof ItemFishingRod || item instanceof ItemEgg
                || item instanceof ItemSnowball || item instanceof ItemEnderPearl || item instanceof ItemPotion;
    }
    private boolean context() {
        PacketPipeline pipe = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getClientPackets();
        if (player != mc.thePlayer || world != mc.theWorld || connection != pipe || pipe != null && epoch != pipe.getContextVersion()) {
            reset(); player = mc.thePlayer; world = mc.theWorld; connection = pipe; epoch = pipe == null ? 0 : pipe.getContextVersion();
        }
        return player != null && world != null && pipe != null && pipe.isActive();
    }
    private void reset() { swings.clear(); hurt = false; player = world = null; connection = null; }
}
