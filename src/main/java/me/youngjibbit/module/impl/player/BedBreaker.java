package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.world.BedBreakerPlanner;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.lang.ref.WeakReference;

/** Silent, reach-checked native mining along a bounded fastest-time bed access route. */
@ModuleInfo(name = "BedBreaker", category = Category.Player,
        description = "Silently mines the fastest reachable route through bed defenses, with movement correction.")
public final class BedBreaker extends Module {
    public static final String ROTATION_SOURCE = "bedbreaker";
    private final SliderValue turnSpeed = new SliderValue("Turn Speed", this, 30D, 1, 180);
    private final CheckBoxValue swing = new CheckBoxValue("Swing", this, true);
    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, MINING, PAUSED_INPUT, PAUSED_MODULE }
    private Status status = Status.OFF;
    private long generation;
    private Scene scene;
    private BedBreakerPlanner.Target target;
    private Mine owned;
    private boolean processing;
    private WeakReference<Object> unloadedWorld = new WeakReference<>(null);
    // A toggle/config reset must not create a second automatic action in the same player tick.
    private final TickBudget aimBudget = new TickBudget(), searchBudget = new TickBudget(), mineBudget = new TickBudget();
    private final TickBudget manualAttack = new TickBudget();

    public BedBreaker() { enableSmartDisable(); }
    @Override public void onEnable() { invalidate(Status.WAITING, Cleanup.ABORT); super.onEnable(); }
    @Override public void onDisable() { try { invalidate(Status.OFF, Cleanup.ABORT); } finally { super.onDisable(); } }
    @Override public void onValueChange() { invalidate(isToggled() ? Status.WAITING : Status.OFF, Cleanup.ABORT); }
    public Status getStatus() { return status; }

    /** Shared prediction, movement and model adapters keep camera angles untouched. */
    public boolean correctionEnabled() {
        Scene frame = scene;
        return target != null && current(frame) && inputFree(frame);
    }
    public MoveFixController.Mode moveFixMode() { return MoveFixController.Mode.SIMPLE; }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.DIRECT; }

    private enum Cleanup { ABORT, DISCARD, MANUAL }
    private void state(Status next) { status = next; info = next == Status.OFF ? "" : next.name().replace('_', ' '); }
    private void invalidate(Status next, Cleanup cleanup) {
        generation++;
        Scene previous = scene; Mine old = owned;
        owned = null; target = null; scene = null; state(next);
        if (previous != null && previous.objects() && RuntimeServices.rotation() == previous.rotations)
            previous.rotations.reset(ROTATION_SOURCE);
        finish(old, cleanup);
    }
    private void finish(Mine old, Cleanup cleanup) {
        if (old == null || cleanup == Cleanup.MANUAL) return;
        PlayerControllerMP controller = old.scene.controller;
        if (controller.getMiningRevision() != old.revision || !controller.isMiningBlock(old.target.pos)) return;
        long stopping = generation;
        if (cleanup == Cleanup.ABORT && old.scene.objects())
            controller.resetBlockRemoving(old.target.pos, old.revision,
                    () -> generation == stopping && old.scene.objects());
        else controller.discardBlockRemoving(old.target.pos, old.revision);
    }

    @Subscribe private final Listener<WorldEvent> worldChanged = new Listener<>(event -> {
        if (event instanceof WorldEvent.Unload) unloadedWorld = new WeakReference<>(event.world);
        else if (event instanceof WorldEvent.Load) unloadedWorld.clear();
        invalidate(isToggled() ? Status.WAITING : Status.OFF, Cleanup.DISCARD);
    });
    @Subscribe private final Listener<TickEvent> contextChanged = new Listener<>(event -> {
        Scene previous = scene;
        if (previous != null && !current(previous)) invalidate(Status.WAITING, Cleanup.DISCARD);
    });
    @Subscribe private final Listener<EventRotation> rotation = new Listener<>(event -> {
        if (processing || !isToggled()) return;
        processing = true;
        try { prepareRotation(); } finally { processing = false; }
    });

    private void prepareRotation() {
        Scene frame = capture();
        if (frame == null) return;
        if (!admitted(frame)) { pause(frame); return; }
        if (foreignMining(frame)) { invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL); return; }
        if (toolChanged(frame)) return;
        BedBreakerPlanner.Target chosen = select(frame);
        if (!current(frame) || !admitted(frame)) return;
        if (chosen == null) { stopMining(frame); if (current(frame)) { frame.rotations.reset(ROTATION_SOURCE); state(Status.SEARCHING); } return; }
        if (!aimBudget.claim(frame.player, frame.world, 1)) return;
        Vec3 eyes = frame.player.getPositionEyes(1);
        if (!current(frame)) return;
        boolean effective = frame.rotations.hasEffectiveRotation() && Float.isFinite(frame.rotations.getEffectiveYaw())
                && Float.isFinite(frame.rotations.getEffectivePitch()) && Math.abs(frame.rotations.getEffectivePitch()) <= 90;
        float yaw = effective ? frame.rotations.getEffectiveYaw() : frame.player.rotationYaw;
        float pitch = effective ? frame.rotations.getEffectivePitch() : frame.player.rotationPitch;
        float[] desired = BedBreakerPlanner.rotations(eyes, chosen.aim);
        float[] next = desired == null ? null : BedBreakerPlanner.turn(yaw, pitch, desired[0], desired[1], turnSpeed.getValue().floatValue());
        if (next == null || !admitted(frame)) return;
        final long sequence = frame.rotations.getMotionSequence();
        frame.rotations.request(ROTATION_SOURCE, 5, next[0], next[1],
                () -> current(frame) && target == chosen && frame.rotations.getMotionSequence() == sequence
                        && admitted(frame) && current(frame));
        if (current(frame)) state(Status.ALIGNING);
    }

    /** Called only from the normal Minecraft block-input stage, outside synthetic player updates. */
    public static void observeManualAttack(Minecraft client) {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        BedBreaker module = modules == null ? null : (BedBreaker) modules.getModule(BedBreaker.class);
        if (module == null || client == null || client.thePlayer == null || client.theWorld == null
                || !client.isCallingFromMinecraftThread()) return;
        module.manualAttack.mark(client.thePlayer, client.theWorld);
        if (module.isToggled()) module.invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL);
    }

    /** Returns true only when the idle reset would invalidate a handled automatic operation. */
    public static boolean handleBlockInput(Minecraft client, boolean physicalAttack) {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        BedBreaker module = modules == null ? null : (BedBreaker) modules.getModule(BedBreaker.class);
        if (module == null || !module.isToggled() || client == null || !client.isCallingFromMinecraftThread()) return false;
        if (client.thePlayer == null || client.theWorld == null || client.playerController == null) return true;
        if (physicalAttack) {
            // The ordinary caller takes over this exact mining progress, even at the same block.
            module.manualAttack.mark(client.thePlayer, client.theWorld);
            module.invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL);
            return false;
        }
        if (module.processing) return true;
        PlayerControllerMP controller = client.playerController;
        Object player = client.thePlayer, world = client.theWorld;
        long revision = controller == null ? -1 : controller.getMiningRevision();
        module.processing = true;
        try {
            boolean consumed = module.mine(client);
            // A late callback may have taken over the normal input stage. Never run a stale idle reset after it.
            return consumed || client.playerController != controller || client.thePlayer != player || client.theWorld != world
                    || controller != null && controller.getMiningRevision() != revision;
        } finally { module.processing = false; }
    }

    private boolean mine(Minecraft client) {
        Scene frame = capture();
        if (frame == null || frame.client != client) return false;
        if (!admitted(frame)) { boolean had = owned != null; pause(frame); return had; }
        boolean allowed = LagRange.allowBlockAttack();
        if (!current(frame) || !allowed) { boolean had = owned != null; pause(frame); return had; }
        if (foreignMining(frame)) { invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL); return false; }
        if (toolChanged(frame)) return true;
        BedBreakerPlanner.Target chosen = select(frame);
        if (!current(frame) || !admitted(frame)) return false;
        if (chosen == null) { boolean had = owned != null; stopMining(frame); if (current(frame)) { frame.rotations.reset(ROTATION_SOURCE); state(Status.SEARCHING); } return had; }
        if (owned != null && !owned.target.pos.equals(chosen.pos)) { stopMining(frame); return true; }
        // This input stage precedes this tick's movement packet. Only the preceding
        // accepted server pose grants mining authority; camera picking stays independent.
        if (!rotationReady(frame)) {
            boolean had = owned != null; stopMining(frame); if (current(frame)) state(Status.ALIGNING); return had;
        }
        final long sequence = frame.rotations.getMotionSequence();
        final float yaw = frame.rotations.getEffectiveYaw(), pitch = frame.rotations.getEffectivePitch();
        Vec3 eyes = frame.player.getPositionEyes(1);
        float reach = frame.controller.getBlockReachDistance();
        if (!current(frame) || !BedBreakerPlanner.finite(eyes) || !Float.isFinite(reach) || reach <= 0 || reach > 5) return false;
        Vec3 direction = EntityPlayerSP.getVectorForRotation(pitch, yaw);
        if (!current(frame) || !BedBreakerPlanner.finite(direction)) return false;
        MovingObjectPosition hit = frame.world.rayTraceBlocks(eyes,
                eyes.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach), false, false, false);
        if (!current(frame) || !BedBreakerPlanner.matches(hit, chosen.pos, eyes, reach) || !admitted(frame)
                || !sameRotation(frame, sequence, yaw, pitch)) {
            boolean had = owned != null; stopMining(frame); if (current(frame)) state(Status.ALIGNING); return had;
        }
        if (!mineBudget.claim(frame.player, frame.world, 1)) return owned != null;
        long revision = frame.controller.getMiningRevision();
        if (owned != null && owned.revision != revision) { invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL); return false; }
        Mine attempt = new Mine(frame, chosen, revision + 1, sequence, yaw, pitch, direction);
        owned = attempt;
        boolean accepted = frame.controller.onPlayerDamageBlock(chosen.pos, hit.sideHit, chosen.state,
                () -> actionCurrent(attempt), () -> current(frame), () -> attempt.look);
        if (!current(frame) || owned != attempt) return true;
        if (frame.controller.getMiningRevision() != attempt.revision) { owned = null; return true; }
        if (accepted && actionCurrent(attempt)) {
            state(Status.MINING);
            if (frame.client.effectRenderer != null) frame.client.effectRenderer.addBlockHitEffects(chosen.pos, hit.sideHit);
            if (swing.getValue() && actionCurrent(attempt)) frame.player.swingItem();
        }
        if (owned == attempt && !frame.controller.isMiningBlock(chosen.pos)) owned = null;
        return true;
    }

    private boolean actionCurrent(Mine attempt) {
        Scene frame = attempt.scene;
        return owned == attempt && current(frame) && target == attempt.target
                && sameRotation(frame, attempt.sequence, attempt.yaw, attempt.pitch)
                && frame.controller.getMiningRevision() == attempt.revision
                && samePosition(frame.player, attempt.x, attempt.y, attempt.z)
                && frame.player.inventory.currentItem == attempt.slot && admitted(frame) && LagRange.allowBlockAttack()
                && (attempt.target.pos.equals(attempt.target.bedPos)
                    || frame.planner.hasBed(attempt.target, () -> current(frame)))
                && owned == attempt && current(frame)
                && frame.controller.getMiningRevision() == attempt.revision && frame.player.inventory.currentItem == attempt.slot
                && sameRotation(frame, attempt.sequence, attempt.yaw, attempt.pitch)
                && samePosition(frame.player, attempt.x, attempt.y, attempt.z);
    }
    private boolean rotationReady(Scene frame) {
        return current(frame) && frame.rotations.canUseRotation(ROTATION_SOURCE, false)
                && ROTATION_SOURCE.equals(frame.rotations.getConfirmedOwner(frame.player, frame.world, frame.packets));
    }
    private boolean sameRotation(Scene frame, long sequence, float yaw, float pitch) {
        return rotationReady(frame) && frame.rotations.getMotionSequence() == sequence
                && Float.compare(frame.rotations.getEffectiveYaw(), yaw) == 0
                && Float.compare(frame.rotations.getEffectivePitch(), pitch) == 0;
    }
    private void pause(Scene frame) { if (current(frame)) invalidate(Status.PAUSED_INPUT, Cleanup.ABORT); }
    private void stopMining(Scene frame) {
        if (!current(frame)) return;
        Mine old = owned; owned = null; finish(old, Cleanup.ABORT);
    }
    private boolean foreignMining(Scene frame) {
        return frame.controller.func_181040_m() && (owned == null || owned.scene != frame
                || owned.revision != frame.controller.getMiningRevision() || !frame.controller.isMiningBlock(owned.target.pos));
    }
    private boolean toolChanged(Scene frame) {
        Mine old = owned;
        if (old == null || old.sameTool()) return false;
        mineBudget.claim(frame.player, frame.world, 1);
        stopMining(frame);
        if (current(frame)) state(Status.WAITING);
        return true;
    }

    private BedBreakerPlanner.Target select(Scene frame) {
        Vec3 eyes = frame.player.getPositionEyes(1);
        float reach = frame.controller.getBlockReachDistance();
        if (!current(frame)) return null;
        boolean search = searchBudget.claim(frame.player, frame.world, 2);
        BedBreakerPlanner.Target selected = frame.planner.select(eyes, reach, target, search, () -> current(frame) && inputFree(frame));
        if (current(frame)) target = selected;
        return selected;
    }
    private Scene capture() {
        Minecraft client = Minecraft.getMinecraft();
        if (!isToggled() || client == null || !client.isCallingFromMinecraftThread() || client.thePlayer == null
                || client.theWorld == null || client.playerController == null || client.gameSettings == null
                || unloadedWorld.get() == client.theWorld) return null;
        if (scene != null && !scene.objects()) { invalidate(Status.WAITING, Cleanup.DISCARD); return null; }
        if (scene == null) {
            EntityPlayerSP player = client.thePlayer;
            if (player.sendQueue == null || player.sendQueue.getNetworkManager() == null) return null;
            PacketPipeline packets = player.sendQueue.getNetworkManager().getClientPackets();
            ModuleManager modules = Pulsar.INSTANCE.getMm();
            if (packets == null || !packets.isActive() || modules == null || modules.getModule(BedBreaker.class) != this) return null;
            scene = new Scene(client, modules, packets, generation);
        }
        return current(scene) ? scene : null;
    }
    private boolean current(Scene frame) {
        return frame != null && scene == frame && isToggled() && generation == frame.generation
                && unloadedWorld.get() != frame.world && frame.objects() && frame.modules.getModule(BedBreaker.class) == this;
    }
    private boolean inputFree(Scene frame) {
        GameSettings keys = frame.client.gameSettings;
        return keys != null && frame.client.currentScreen == null && frame.client.inGameHasFocus
                && !manualAttack.blocked(frame.player, frame.world)
                && !frame.client.isGamePaused() && frame.client.getRenderViewEntity() == frame.player
                && !frame.player.isDead && !frame.player.isRiding() && frame.player.inventory != null
                && keys.keyBindAttack != null && !keys.keyBindAttack.isKeyDown()
                && keys.keyBindUseItem != null && !keys.keyBindUseItem.isKeyDown() && !frame.player.isUsingItem();
    }
    private boolean admitted(Scene frame) {
        if (!current(frame) || !inputFree(frame) || !frame.player.isEntityAlive() || frame.player.isSpectator()) return false;
        Module scaffold = frame.modules.getModule(Scaffold.class), next = frame.modules.getModule(NScaffold.class);
        if (scaffold != null && scaffold.isToggled() || next != null && next.isToggled()) return false;
        Aura aura = (Aura) frame.modules.getModule(Aura.class);
        if (aura != null && aura.isToggled() && aura.getActiveTarget() != null) return false;
        return current(frame) && inputFree(frame) && !RuntimeServices.attackScheduler().hasAttackThisTick() && current(frame);
    }
    private static boolean samePosition(EntityPlayerSP player, double x, double y, double z) {
        return Double.compare(player.posX, x) == 0 && Double.compare(player.posY, y) == 0 && Double.compare(player.posZ, z) == 0;
    }

    private static final class Scene {
        final Minecraft client; final EntityPlayerSP player; final WorldClient world;
        final PlayerControllerMP controller; final NetHandlerPlayClient handler;
        final PacketPipeline packets; final ModuleManager modules; final RotationManager rotations;
        final long epoch, generation;
        final BedBreakerPlanner planner;
        Scene(Minecraft client, ModuleManager modules, PacketPipeline packets, long generation) {
            this.client = client; player = client.thePlayer; world = client.theWorld; controller = client.playerController;
            handler = player.sendQueue; this.packets = packets; this.modules = modules; this.generation = generation;
            epoch = packets.getContextVersion(); rotations = RuntimeServices.rotation();
            planner = new BedBreakerPlanner(new BedBreakerPlanner.Access() {
                public boolean loaded(BlockPos pos) { return world.isBlockLoaded(pos); }
                public IBlockState state(BlockPos pos) { return world.getBlockState(pos); }
                public AxisAlignedBB bounds(BlockPos pos, IBlockState state) { return state.getBlock().getCollisionBoundingBox(world, pos, state); }
                public MovingObjectPosition ray(Vec3 from, Vec3 to) { return world.rayTraceBlocks(from, to, false, false, false); }
                public double miningTicks(BlockPos pos, IBlockState state) {
                    if (!world.getWorldBorder().contains(pos) || player.isSpectator()) return Double.POSITIVE_INFINITY;
                    ItemStack held = player.getHeldItem();
                    if (controller.isInCreativeMode())
                        return held != null && held.getItem() instanceof ItemSword ? Double.POSITIVE_INFINITY : 1;
                    if (state.getBlock().getBlockHardness(world, pos) < 0) return Double.POSITIVE_INFINITY;
                    if (controller.getCurrentGameType().isAdventure() && !player.isAllowEdit()
                            && (held == null || !held.canDestroy(state.getBlock()))) return Double.POSITIVE_INFINITY;
                    float hardness = state.getBlock().getPlayerRelativeBlockHardness(player, world, pos);
                    // Zero-hardness blocks produce positive infinity and break instantly in vanilla.
                    if (hardness >= 1) return 1;
                    if (!Float.isFinite(hardness) || hardness <= 0) return Double.POSITIVE_INFINITY;
                    float progress = controller.getBlockDamageProgress(pos);
                    return (controller.isMiningBlock(pos) ? 0 : 1) + Math.max(1, Math.ceil((1D - progress) / hardness));
                }
                public double postBreakDelayTicks(BlockPos pos, IBlockState state) {
                    return !controller.isInCreativeMode()
                            && state.getBlock().getPlayerRelativeBlockHardness(player, world, pos) >= 1 ? 0 : 5;
                }
            });
        }
        boolean objects() {
            return Minecraft.getMinecraft() == client && client.thePlayer == player && client.theWorld == world
                    && client.playerController == controller && player.worldObj == world && player.sendQueue == handler
                    && Pulsar.INSTANCE.getMm() == modules && handler.getNetworkManager().getClientPackets() == packets
                    && packets.isActive() && packets.getContextVersion() == epoch && handler.getNetworkManager().isChannelOpen()
                    && RuntimeServices.rotation() == rotations;
        }
    }
    private static final class Mine {
        final Scene scene; final BedBreakerPlanner.Target target; final long revision, sequence;
        final Vec3 look;
        final ItemStack held, heldSnapshot;
        final double x, y, z; final float yaw, pitch; final int slot;
        Mine(Scene scene, BedBreakerPlanner.Target target, long revision, long sequence, float yaw, float pitch, Vec3 look) {
            this.scene = scene; this.target = target; this.revision = revision; this.sequence = sequence;
            this.yaw = yaw; this.pitch = pitch; this.look = look;
            x = scene.player.posX; y = scene.player.posY; z = scene.player.posZ;
            slot = scene.player.inventory.currentItem;
            held = scene.player.inventory.getCurrentItem(); heldSnapshot = held == null ? null : held.copy();
        }
        boolean sameTool() {
            return scene.player.inventory != null && scene.player.inventory.currentItem == slot
                    && scene.player.inventory.getCurrentItem() == held
                    && ItemStack.areItemStacksEqual(heldSnapshot, held);
        }
    }
    private static final class TickBudget {
        private WeakReference<Object> player = new WeakReference<>(null), world = new WeakReference<>(null);
        private long last = Long.MIN_VALUE;
        void mark(EntityPlayerSP currentPlayer, WorldClient currentWorld) {
            player = new WeakReference<>(currentPlayer); world = new WeakReference<>(currentWorld);
            last = currentPlayer.ticksExisted;
        }
        boolean blocked(EntityPlayerSP currentPlayer, WorldClient currentWorld) {
            return player.get() == currentPlayer && world.get() == currentWorld && currentPlayer.ticksExisted <= last;
        }
        boolean claim(EntityPlayerSP currentPlayer, WorldClient currentWorld, int gap) {
            if (player.get() != currentPlayer || world.get() != currentWorld) {
                player = new WeakReference<>(currentPlayer); world = new WeakReference<>(currentWorld); last = Long.MIN_VALUE;
            }
            long tick = currentPlayer.ticksExisted;
            if (last != Long.MIN_VALUE && tick - last < gap) return false;
            last = tick; return true;
        }
    }
}
