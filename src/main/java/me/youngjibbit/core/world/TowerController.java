package me.youngjibbit.core.world;

import me.youngjibbit.event.motion.EventMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

public final class TowerController {
    public interface Access {
        boolean ready();
        boolean onGround();
        double motionY();
        void motionY(double value);
        Vec3 position();
        boolean move(Vec3 position);
    }
    private final Access access;
    private int ticks;
    public TowerController() { this(new MinecraftAccess()); }
    public TowerController(Access access) { this.access = access; }

    public void apply(EventMotion event, boolean enabled, String mode, boolean jumpPressed, boolean moving, boolean hasTarget) {
        if (!enabled || !jumpPressed || !hasTarget || event == null || !access.ready()) {
            reset();
            return;
        }
        ticks++;
        Vec3 position = access.position();
        if ("NCP".equalsIgnoreCase(mode)) {
            if (access.onGround()) access.motionY(.42);
            else if (access.motionY() < .2 && access.move(new Vec3(position.xCoord, Math.floor(position.yCoord), position.zCoord)))
                access.motionY(.42);
        } else if ("Watchdog".equalsIgnoreCase(mode)) {
            if (!moving) access.move(new Vec3(Math.floor(position.xCoord)+.5, position.yCoord, Math.floor(position.zCoord)+.5));
            access.motionY(ticks % 3 == 0 ? .24 : .32);
        } else access.motionY(.42);
        // Keep local position, the motion event and subsequent placement geometry in agreement.
        position = access.position();
        event.setX(position.xCoord); event.setY(position.yCoord); event.setZ(position.zCoord);
        event.setOnGround(access.onGround());
    }

    public void reset() { ticks = 0; }

    private static final class MinecraftAccess implements Access {
        private final Minecraft mc = Minecraft.getMinecraft();
        public boolean ready() { return mc != null && mc.theWorld != null && mc.thePlayer != null && mc.thePlayer.isEntityAlive(); }
        public boolean onGround() { return mc.thePlayer.onGround; }
        public double motionY() { return mc.thePlayer.motionY; }
        public void motionY(double value) { mc.thePlayer.motionY = value; }
        public Vec3 position() { return new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ); }
        public boolean move(Vec3 next) {
            Vec3 old = position();
            if (next.yCoord < 0 || next.yCoord >= 256 || !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                    mc.thePlayer.getEntityBoundingBox().offset(next.xCoord-old.xCoord, next.yCoord-old.yCoord, next.zCoord-old.zCoord)).isEmpty()) return false;
            mc.thePlayer.setPosition(next.xCoord, next.yCoord, next.zCoord);
            return true;
        }
    }
}
