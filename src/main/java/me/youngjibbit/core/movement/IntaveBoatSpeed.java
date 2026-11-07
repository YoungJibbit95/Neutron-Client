package me.youngjibbit.core.movement;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Opt-in near-boat acceleration experiment, not a verified server bypass. */
public final class IntaveBoatSpeed {
    private static final double RADIUS_SQUARED = 1.5 * 1.5;
    private static final float EXTRA_FRICTION = 0.035F;
    private static final long IMPULSE_PAUSE_NANOS = 500_000_000L;
    private final LongSupplier clock;
    // SceneContext itself holds only weak identity references.
    private SceneContext scene;
    private WeakReference<EntityBoat> boat = new WeakReference<>(null);
    private int warmup, lastPlayerTick = -1;
    private long lastOuterTick = -1, pauseUntil, generation;
    private double boatX, boatY, boatZ;
    private boolean paused;
    private String reason = "idle";

    public IntaveBoatSpeed() { this(System::nanoTime); }
    public IntaveBoatSpeed(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }
    public String getReason() { return reason; }

    public void reset() {
        generation++;
        clearWarmup("idle");
        scene = null;
        lastOuterTick = -1;
        paused = false;
    }

    public void pauseForImpulse() {
        generation++;
        clearWarmup("impulse pause");
        pauseUntil = clock.getAsLong() + IMPULSE_PAUSE_NANOS;
        paused = true;
    }

    /** Called after all moveFlying listeners and rotation correction; writes only event friction. */
    public boolean apply(Minecraft mc, PlayerMoveUpdateEvent event) {
        final long enteredGeneration = generation;
        RuntimeObservation observation = RuntimeServices.observation();
        ExecutedStep step = observation == null ? null : observation.getCurrentStep();
        SceneContext fresh = observation == null ? null : observation.captureScene();
        if (step == null || fresh == null || !fresh.isValid() || fresh.isSuspended()
                || !step.getScene().isValid() || step.getScene().isSuspended()
                || step.isInterrupted() || step.getKind() != ExecutedStep.Kind.ORDINARY
                || observation.getCurrentStep() != step
                || observation.getActiveOuterTickId() != step.getOuterTickId()
                || !step.getScene().sameSceneAs(fresh)) return reject("inactive step");
        if (scene == null || !scene.sameSceneAs(fresh)) {
            clearWarmup("scene changed");
            scene = fresh;
            lastOuterTick = -1;
        }
        if (lastOuterTick == step.getOuterTickId()) {
            reason = "duplicate step";
            return false;
        }
        lastOuterTick = step.getOuterTickId();
        if (paused) {
            if (clock.getAsLong() - pauseUntil < 0) return reject("impulse pause");
            paused = false;
        }
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player == null || mc.theWorld == null || player.worldObj != mc.theWorld
                || mc.getRenderViewEntity() != player || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.isGamePaused() || event == null || event.isCancelled()
                || !player.isEntityAlive() || player.isDead || player.isSpectator()
                || !player.onGround || !player.isSprinting() || player.isSneaking()
                || player.isUsingItem() || player.isRiding() || player.capabilities.isFlying
                || player.isOnLadder() || player.isInWater() || player.isInLava() || player.isInWeb
                || player.hurtTime > 0 || player.motionY < -0.1 || player.motionY > 0.05 || player.movementInput == null
                || player.movementInput.jump || player.movementInput.sneak
                || event.getForward() <= 0) return reject("movement gate");
        if (!finite(player.posX, player.posY, player.posZ, player.prevPosX, player.prevPosY,
                player.prevPosZ, player.motionX, player.motionY, player.motionZ,
                event.getForward(), event.getStrafe(), event.getFriction(), event.getYaw())
                || event.getFriction() <= 0 || event.getFriction() > 0.2F) return reject("invalid motion");

        // Inputs/yaw/friction are final: the caller runs after BUS and rotation correction.
        float strafe = event.getStrafe(), forward = event.getForward();
        float friction = event.getFriction() + EXTRA_FRICTION;
        if (forward <= 0 || !finite(strafe, forward, friction, event.getYaw())) return reject("corrected input");
        float squaredLength = strafe * strafe + forward * forward;
        if (!Float.isFinite(squaredLength) || squaredLength < 1.0E-4F) return reject("empty input");
        float length = MathHelper.sqrt_float(squaredLength);
        float scale = friction / Math.max(1.0F, length);
        strafe *= scale;
        forward *= scale;
        float radians = event.getYaw() * (float) Math.PI / 180.0F;
        float sine = MathHelper.sin(radians), cosine = MathHelper.cos(radians);
        double dx = player.motionX + (strafe * cosine - forward * sine);
        double dz = player.motionZ + (forward * cosine + strafe * sine);
        if (!finite(dx, dz) || Math.hypot(dx, dz) > 0.4) return reject("speed bound");

        EntityBoat selected = chooseBoat(mc, player, dx, dz);
        if (generation != enteredGeneration) return false;
        if (selected == null) return reject("no stationary boat");
        AxisAlignedBB bounds = player.getEntityBoundingBox();
        if (!mc.theWorld.getCollidingBoundingBoxes(player, bounds.addCoord(dx, 0, dz)).isEmpty()
                || !supported(mc, bounds) || !supported(mc, bounds.offset(dx, 0, dz)))
            return reject("obstacle or ledge");
        if (generation != enteredGeneration) return false;
        boolean sameBoat = boat.get() == selected;
        boolean unchanged = sameBoat && distanceSquared(selected.posX, selected.posY, selected.posZ,
                boatX, boatY, boatZ) < 0.0001;
        warmup = unchanged && player.ticksExisted == lastPlayerTick + 1 ? Math.min(6, warmup + 1) : 1;
        boat = new WeakReference<>(selected);
        boatX = selected.posX;
        boatY = selected.posY;
        boatZ = selected.posZ;
        lastPlayerTick = player.ticksExisted;
        if (warmup < 6) { reason = "warming " + warmup + "/6"; return false; }
        SceneContext finalScene = observation.captureScene();
        if (generation != enteredGeneration) return false;
        if (observation.getCurrentStep() != step || observation.getActiveOuterTickId() != step.getOuterTickId()
                || !finalScene.isValid() || finalScene.isSuspended() || !fresh.sameSceneAs(finalScene))
            return reject("scene changed during prediction");
        event.setFriction(friction);
        reason = "near-boat acceleration";
        return true;
    }

    private EntityBoat chooseBoat(Minecraft mc, EntityPlayerSP player, double dx, double dz) {
        EntityBoat previous = boat.get(), nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.theWorld.getEntitiesInAABBexcluding(player,
                player.getEntityBoundingBox().expand(2, 2, 2), e -> e instanceof EntityBoat && !e.isDead)) {
            EntityBoat candidate = (EntityBoat) entity;
            if (candidate.worldObj != mc.theWorld || mc.theWorld.getEntityByID(candidate.getEntityId()) != candidate
                    || !finite(candidate.posX, candidate.posY, candidate.posZ,
                    candidate.prevPosX, candidate.prevPosY, candidate.prevPosZ)
                    || distanceSquared(candidate.posX, candidate.posY, candidate.posZ,
                    candidate.prevPosX, candidate.prevPosY, candidate.prevPosZ) >= 0.0001
                    || !near(player.posX, player.posY, player.posZ, candidate)
                    || !near(player.prevPosX, player.prevPosY, player.prevPosZ, candidate)
                    || !near(player.posX + dx, player.posY, player.posZ + dz, candidate)) continue;
            if (candidate == previous) return candidate;
            double distance = distanceSquared(player.posX, player.posY, player.posZ,
                    candidate.posX, candidate.posY, candidate.posZ);
            if (distance < best) { nearest = candidate; best = distance; }
        }
        return nearest;
    }

    // Conservative first-run surface restriction: every footprint corner needs a full block.
    private static boolean supported(Minecraft mc, AxisAlignedBB box) {
        for (double x : new double[]{box.minX + 0.01, box.maxX - 0.01})
            for (double z : new double[]{box.minZ + 0.01, box.maxZ - 0.01}) {
                BlockPos below = new BlockPos(x, box.minY - 0.01, z);
                if (!mc.theWorld.isBlockLoaded(below)) return false;
                IBlockState state = mc.theWorld.getBlockState(below);
                AxisAlignedBB support = state.getBlock().getCollisionBoundingBox(mc.theWorld, below, state);
                // BlockAir inherits isFullCube()==true in this 1.8 source; require real support.
                if (!state.getBlock().getMaterial().isSolid() || !state.getBlock().isFullCube() || support == null
                        || Math.abs(support.maxY - box.minY) > 0.01
                        || x <= support.minX || x >= support.maxX || z <= support.minZ || z >= support.maxZ) return false;
            }
        return true;
    }

    private static boolean near(double x, double y, double z, EntityBoat boat) {
        return distanceSquared(x, y, z, boat.posX, boat.posY, boat.posZ) <= RADIUS_SQUARED;
    }
    private static double distanceSquared(double x, double y, double z, double a, double b, double c) {
        double dx = x - a, dy = y - b, dz = z - c;
        return dx * dx + dy * dy + dz * dz;
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private boolean reject(String reason) { clearWarmup(reason); return false; }
    private void clearWarmup(String reason) {
        boat.clear();
        warmup = 0;
        lastPlayerTick = -1;
        this.reason = reason;
    }
}
