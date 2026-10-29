package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class ScaffoldPlacementService {
    /** World/controller boundary, also used by the headless regression fixtures. */
    public interface Access {
        boolean ready();
        Object world();
        Object player();
        Vec3 feet();
        Vec3 eyes();
        Vec3 wireEyes();
        double reach();
        boolean jumpDown();
        int selectedSlot();
        void selectSlot(int slot);
        boolean syncSlot();
        ItemStack stack(int slot);
        boolean usable(ItemStack stack);
        Object state(BlockPos pos);
        boolean replaceable(BlockPos pos);
        AxisAlignedBB supportBounds(BlockPos pos);
        boolean canPlace(PlacementTarget target);
        MovingObjectPosition rayTrace(Vec3 start, Vec3 end);
        boolean click(PlacementTarget target, Vec3 hit, BooleanSupplier valid);
        void swing(boolean silent);
    }

    public enum Rejection {
        NONE(""), CONTEXT("Ungültiger Spielkontext"), NO_BLOCKS("Kein geeigneter Blockslot"),
        OCCUPIED("Unter dem Spieler wird kein Block benötigt"), NO_FACE("Keine erreichbare freie Nachbarfläche"),
        NO_TARGET("Kein Platzierungsplan"), STALE_PLAN("Plan gehört nicht zum aktuellen Bewegungsschritt"),
        BUDGET("In diesem Tick wurde bereits eine Platzierung versucht"), DELAY("Platzierungsverzögerung läuft"),
        CHANGED("Plan durch Welt, Inventar, Slot oder Sichtlinie ungültig"),
        ROTATION("Noch keine gültige angenommene Rotation"), RAYCAST("Angenommene Augenposition/Rotation trifft die geplante Fläche nicht"),
        SLOT("Blockslot wurde nicht angenommen"), LATE_CHANGE("Plan nach Queue-/Packet-Verarbeitung ungültig"),
        CONTROLLER("Controller hat die Platzierung abgelehnt");
        public final String message;
        Rejection(String message) { this.message=message; }
    }
    private Rejection lastRejection=Rejection.NONE;
    public Rejection getLastRejection() { return lastRejection; }
    private boolean reject(Rejection reason) { lastRejection=reason;return false; }

    private final Access access;
    private final LongSupplier clock;
    private long lastAttempt, lastSequence = Long.MIN_VALUE, tick, lastAttemptTick = Long.MIN_VALUE;
    public void beginTick() { tick++; }

    public ScaffoldPlacementService() { this(new MinecraftAccess(), () -> System.nanoTime() / 1000000); }
    public ScaffoldPlacementService(Access access, LongSupplier clock) { this.access = access; this.clock = clock; reset(); }
    public void reset() { lastAttempt = clock.getAsLong(); lastSequence = Long.MIN_VALUE; }

    public int getBlockSlot() {
        if (!access.ready()) return -1;
        // Keep the selected block stack when possible; otherwise choose the first suitable hotbar stack.
        int selected = access.selectedSlot();
        if (selected >= 0 && selected < 9 && access.usable(access.stack(selected))) return selected;
        for (int i = 0; i < 9; i++) if (access.usable(access.stack(i))) return i;
        return -1;
    }

    public int getBlockCount() {
        if (!access.ready()) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) if (access.usable(access.stack(i))) count += access.stack(i).stackSize;
        return count;
    }

    public static double yLevel(double feetY, PlacementContext context, boolean jump) {
        double current = feetY - 1;
        double y = context.isKeepY() && !jump && current >= context.getKeepYCoord()
                && current - context.getKeepYCoord() <= 3 ? context.getKeepYCoord() : current;
        return y - (context.isDownwards() ? 1 : 0);
    }

    private BlockPos base(PlacementContext context) {
        Vec3 feet = access.feet();
        return new BlockPos(feet.xCoord, yLevel(feet.yCoord, context, access.jumpDown()), feet.zCoord);
    }

    public boolean stillNeeded(PlacementTarget target, PlacementContext context) {
        if (!isValid(target) || context == null) return false;
        BlockPos base = base(context);
        return access.replaceable(base) && target.getDestination().getY() == base.getY()
                && Math.abs(target.getDestination().getX() - base.getX()) <= 2
                && Math.abs(target.getDestination().getZ() - base.getZ()) <= 2;
    }

    public PlacementTarget findTarget(PlacementContext context) { return findTarget(context, "Back"); }
    public PlacementTarget findTarget(boolean downwards, boolean keepY, double y) {
        return findTarget(new PlacementContext(downwards, keepY, y));
    }

    public PlacementTarget findTarget(PlacementContext context, String mode) {
        return findTarget(context,mode,false,null);
    }
    public PlacementTarget findTarget(PlacementContext context,String mode,boolean round,Vec3 plannedEyes) {
        lastRejection=Rejection.NONE;
        if (!access.ready() || context == null) { reject(Rejection.CONTEXT);return null; }
        int slot = getBlockSlot();
        if (slot < 0) { reject(Rejection.NO_BLOCKS);return null; }
        BlockPos base = base(context);
        if (!access.replaceable(base)) { reject(Rejection.OCCUPIED);return null; }
        for (int radius = 0; radius <= 2; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                BlockPos destination = base.add(x, 0, z);
                if (!access.replaceable(destination)) continue;
                for (EnumFacing neighbor : EnumFacing.values()) {
                    BlockPos support = destination.offset(neighbor);
                    AxisAlignedBB bounds = access.supportBounds(support);
                    if (bounds == null) continue;
                    EnumFacing face = neighbor.getOpposite();
                    Vec3 origin=plannedEyes==null?access.eyes():plannedEyes;
                    Vec3 hit = ScaffoldRotationPlan.hitPoint(mode, bounds, face, origin);
                    if(hit==null)continue;
                    if(round)hit=ScaffoldRotationPlan.roundedFacePoint(bounds,face,origin,hit,90,ScaffoldRotationPlan.build(mode,origin,hit).getPitch());
                    if(hit==null)continue;
                    PlacementTarget target = new PlacementTarget(destination, support, face, hit, slot,
                            access.selectedSlot(), access.world(), access.player(), access.state(support), access.stack(slot));
                    if (isValid(target)) return target;
                }
            }
        }
        reject(Rejection.NO_FACE);
        return null;
    }

    /** Keep one immutable PRE/POST intent; only accept randomized points that pass the normal face checks. */
    public PlacementTarget randomize(PlacementTarget target,RotationManager rotations,me.youngjibbit.core.combat.RotationProfile profile) {
        if(target==null || target.pointChosen) return target;
        AxisAlignedBB box=access.supportBounds(target.getBlockPos());
        if(box==null) return target;
        Vec3 point=rotations.faceAimPoint("scaffold",target.trajectoryKey,box,target.getFacing(),target.baseHit(),profile);
        PlacementTarget candidate=target.withHit(point);
        return isValid(candidate)?candidate:target;
    }

    public Object rotationIdentity(PlacementTarget target) { return target.trajectoryKey; }
    public PlacementTarget align(PlacementTarget target,String mode,boolean round,Vec3 eyes) {
        if(target==null || (!round && !"Godbridge".equalsIgnoreCase(mode)))return target;
        AxisAlignedBB box=access.supportBounds(target.getBlockPos());if(box==null)return null;
        ScaffoldRotationPlan preferred=ScaffoldRotationPlan.build(mode,eyes,target.getHitVec());if(preferred==null)return null;
        Vec3 point=ScaffoldRotationPlan.roundedFacePoint(box,target.getFacing(),eyes,target.getHitVec(),round?90:45,preferred.getPitch());
        if(point==null)return null;
        PlacementTarget aligned=target.withHit(point);return isValid(aligned)?aligned:null;
    }

    public boolean isValid(PlacementTarget target) {
        return target != null && valid(target, target.getSelectedSlot());
    }

    private boolean sameContext(PlacementTarget target) {
        return access.ready() && sameIdentity(target);
    }

    private boolean sameIdentity(PlacementTarget target) {
        return access.world() == target.world && access.player() == target.player;
    }

    private boolean valid(PlacementTarget target, int selectedSlot) {
        if (!sameContext(target) || access.selectedSlot() != selectedSlot) return false;
        ItemStack stack = access.stack(target.getSlot());
        if (stack != target.stack || !access.usable(stack) || !ItemStack.areItemStacksEqual(stack, target.stackSnapshot)) return false;
        if (!target.getBlockPos().offset(target.getFacing()).equals(target.getDestination())
                || !Objects.equals(target.supportState, access.state(target.getBlockPos()))
                || !access.replaceable(target.getDestination()) || access.supportBounds(target.getBlockPos()) == null
                || !access.canPlace(target)) return false;
        Vec3 eyes = access.eyes();
        if (eyes.squareDistanceTo(target.getHitVec()) > access.reach()*access.reach()) return false;
        Vec3 direction = target.getHitVec().subtract(eyes).normalize();
        return matches(target, access.rayTrace(eyes, target.getHitVec().add(scaled(direction, .01))));
    }

    private static Vec3 scaled(Vec3 v, double scale) { return new Vec3(v.xCoord*scale, v.yCoord*scale, v.zCoord*scale); }

    private boolean matches(PlacementTarget target, MovingObjectPosition hit) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && target.getBlockPos().equals(hit.getBlockPos()) && hit.sideHit == target.getFacing()
                && hit.hitVec != null && hit.hitVec.squareDistanceTo(target.getHitVec()) <= .0025;
    }

    private MovingObjectPosition effectiveHit(PlacementTarget target, RotationManager rotations, boolean pre) {
        if (!rotations.canUseRotation(pre)) return null;
        Vec3 eyes = access.wireEyes();
        Vec3 direction = Entity.getVectorForRotation(rotations.getEffectivePitch(), rotations.getEffectiveYaw());
        MovingObjectPosition hit = access.rayTrace(eyes, eyes.add(scaled(direction, access.reach())));
        return matches(target, hit) && eyes.squareDistanceTo(hit.hitVec) <= access.reach()*access.reach() ? hit : null;
    }

    /** Consumes at most one controller attempt per motion cycle, including locally rejected clicks. */
    public boolean place(PlacementTarget target, RotationManager rotations, boolean pre, long sequence, long delay,
                         boolean silentSwitch, boolean swing, boolean silentSwing) {
        return placeWithAuthority(target, rotations, pre, sequence, delay, silentSwitch, swing, silentSwing,
                null, () -> true, () -> true);
    }

    /** Production authority is additive: null source retains the accepted camera/legacy path.
     * actionValid includes admission; sceneValid also protects cleanup after callback boundaries. */
    public boolean placeWithAuthority(PlacementTarget target, RotationManager rotations, boolean pre, long sequence, long delay,
                                     boolean silentSwitch, boolean swing, boolean silentSwing, String requiredRotationSource,
                                     BooleanSupplier actionValid, BooleanSupplier sceneValid) {
        lastRejection=Rejection.NONE;
        if (target == null) return reject(Rejection.NO_TARGET);
        if (!actionValid.getAsBoolean()) return reject(Rejection.CONTEXT);
        if (sequence != rotations.getMotionSequence()) return reject(Rejection.STALE_PLAN);
        if (requiredRotationSource != null && !rotations.canUseRotation(requiredRotationSource, pre)) return reject(Rejection.ROTATION);
        if (sequence == lastSequence || tick == lastAttemptTick) return reject(Rejection.BUDGET);
        if (clock.getAsLong() - lastAttempt < Math.max(0, delay)) return reject(Rejection.DELAY);
        if (!isValid(target)) return reject(Rejection.CHANGED);
        if (!rotations.canUseRotation(pre)) return reject(Rejection.ROTATION);
        if (effectiveHit(target, rotations, pre) == null) return reject(Rejection.RAYCAST);
        int previous = access.selectedSlot();
        boolean switched = previous != target.getSlot();
        boolean placed = false;
        try {
            if (switched) access.selectSlot(target.getSlot());
            if (!access.syncSlot()) return reject(Rejection.SLOT);
            BooleanSupplier gate = () -> (actionValid.getAsBoolean() && sequence == rotations.getMotionSequence()
                    && usableRotation(rotations, pre, requiredRotationSource)
                    && valid(target, target.getSlot()) && effectiveHit(target, rotations, pre) != null)
                    || reject(Rejection.LATE_CHANGE);
            if (!gate.getAsBoolean()) return false;
            lastSequence = sequence;
            lastAttemptTick = tick;
            lastAttempt = clock.getAsLong();
            MovingObjectPosition hit = effectiveHit(target, rotations, pre);
            if (hit == null) return reject(Rejection.LATE_CHANGE);
            placed = access.click(target, hit.hitVec, gate);
            if (placed && swing && sameContext(target) && actionValid.getAsBoolean()) access.swing(silentSwing);
            if (!placed && lastRejection==Rejection.NONE) reject(Rejection.CONTROLLER);
            return placed; // Local controller result only; the server may still reject the placement.
        } finally {
            // A listener/manual selection wins. Never send a slot restore into a replacement world/player.
            if (sameIdentity(target) && sceneValid.getAsBoolean()) {
                if (switched && (silentSwitch || !placed) && access.selectedSlot() == target.getSlot()) access.selectSlot(previous);
                access.syncSlot();
            }
        }
    }

    private boolean usableRotation(RotationManager rotations, boolean pre, String requiredSource) {
        return requiredSource == null ? rotations.canUseRotation(pre) : rotations.canUseRotation(requiredSource, pre);
    }

    private static final class MinecraftAccess implements Access {
        private final Minecraft mc = Minecraft.getMinecraft();
        private final Map<Class<?>, Boolean> interactive = new HashMap<>();
        public boolean ready() {
            return mc != null && mc.thePlayer != null && mc.theWorld != null && mc.playerController != null
                    && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isDead && !mc.thePlayer.isSpectator()
                    && !mc.thePlayer.isRiding() && mc.currentScreen == null && mc.inGameHasFocus;
        }
        public Object world() { return mc.theWorld; }
        public Object player() { return mc.thePlayer; }
        public Vec3 feet() { return new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ); }
        public Vec3 eyes() { return mc.thePlayer.getPositionEyes(1); }
        public Vec3 wireEyes() { return mc.thePlayer.getLastReportedPosition().addVector(0, mc.thePlayer.getEyeHeight(), 0); }
        public double reach() { return mc.playerController.getBlockReachDistance(); }
        public boolean jumpDown() { return mc.gameSettings.keyBindJump.isKeyDown(); }
        public int selectedSlot() { return mc.thePlayer.inventory.currentItem; }
        public void selectSlot(int slot) { mc.thePlayer.inventory.currentItem = slot; }
        public boolean syncSlot() { return mc.playerController.syncCurrentPlayItemChecked(); }
        public ItemStack stack(int slot) { return mc.thePlayer.inventory.getStackInSlot(slot); }
        public boolean usable(ItemStack stack) {
            if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return false;
            Block b = ((ItemBlock)stack.getItem()).getBlock();
            return (b.isFullBlock() || b == Blocks.glass) && b != Blocks.sand && b != Blocks.gravel
                    && b != Blocks.dispenser && b != Blocks.command_block && b != Blocks.noteblock && b != Blocks.furnace
                    && b != Blocks.crafting_table && b != Blocks.tnt && b != Blocks.dropper && b != Blocks.beacon;
        }
        public Object state(BlockPos pos) { return mc.theWorld.getBlockState(pos); }
        public boolean replaceable(BlockPos pos) {
            return pos.getY() >= 0 && pos.getY() < 256 && mc.theWorld.isBlockLoaded(pos)
                    && mc.theWorld.getWorldBorder().contains(pos)
                    && mc.theWorld.getBlockState(pos).getBlock().getMaterial().isReplaceable();
        }
        public AxisAlignedBB supportBounds(BlockPos pos) {
            if (!mc.theWorld.isBlockLoaded(pos) || !mc.theWorld.getWorldBorder().contains(pos)) return null;
            IBlockState state = mc.theWorld.getBlockState(pos);
            Block b = state.getBlock();
            if (!b.getMaterial().isSolid() || b.getMaterial().isLiquid() || b.getMaterial().isReplaceable()) return null;
            // Without sneaking, an interactive support would open a GUI instead of placing a block.
            if (!mc.thePlayer.isSneaking() && interactive.computeIfAbsent(b.getClass(), type -> {
                try { return type.getMethod("onBlockActivated", World.class, BlockPos.class, IBlockState.class,
                        EntityPlayer.class, EnumFacing.class, float.class, float.class, float.class).getDeclaringClass() != Block.class;
                } catch (ReflectiveOperationException e) { return true; }
            })) return null;
            b.setBlockBoundsBasedOnState(mc.theWorld, pos);
            return b.getCollisionBoundingBox(mc.theWorld, pos, state);
        }
        public boolean canPlace(PlacementTarget target) {
            ItemStack stack = stack(target.getSlot());
            ItemBlock item = (ItemBlock)stack.getItem();
            return mc.thePlayer.canPlayerEdit(target.getDestination(), target.getFacing(), stack)
                    && item.canPlaceBlockOnSide(mc.theWorld, target.getBlockPos(), target.getFacing(), mc.thePlayer, stack)
                    && mc.theWorld.canBlockBePlaced(item.getBlock(), target.getDestination(), false, target.getFacing(), null, stack);
        }
        public MovingObjectPosition rayTrace(Vec3 start, Vec3 end) { return mc.theWorld.rayTraceBlocks(start, end, false, false, false); }
        public boolean click(PlacementTarget target, Vec3 hit, BooleanSupplier valid) {
            ItemStack stack = stack(target.getSlot());
            boolean result = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                    target.getBlockPos(), target.getFacing(), hit, valid);
            if (stack.stackSize <= 0 && mc.thePlayer != null && mc.thePlayer.inventory.getStackInSlot(target.getSlot()) == stack)
                mc.thePlayer.inventory.setInventorySlotContents(target.getSlot(), null);
            return result;
        }
        public void swing(boolean silent) {
            if (silent) mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0APacketAnimation());
            else mc.thePlayer.swingItem();
        }
    }
}
