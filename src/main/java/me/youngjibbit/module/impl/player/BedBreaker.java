package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
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
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.lang.ref.WeakReference;

/** Visible, ordinary bed mining. Source adaptation and intentional boundaries: docs/BEDBREAKER.md. */
@ModuleInfo(name = "BedBreaker", category = Category.Player,
        description = "Visibly aims at a nearby exposed bed and mines it through normal reach and input checks.")
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
        if (chosen == null) { stopMining(frame); if (current(frame)) state(Status.SEARCHING); return; }
        if (!aimBudget.claim(frame.player, frame.world, 1)) return;
        Vec3 eyes = frame.player.getPositionEyes(1);
        if (!current(frame)) return;
        float yaw = frame.player.rotationYaw, pitch = frame.player.rotationPitch;
        float[] desired = BedBreakerPlanner.rotations(eyes, chosen.aim);
        float[] next = desired == null ? null : BedBreakerPlanner.turn(yaw, pitch, desired[0], desired[1], turnSpeed.getValue().floatValue());
        if (next == null || !admitted(frame)) return;
        final double x = frame.player.posX, y = frame.player.posY, z = frame.player.posZ;
        final long sequence = frame.rotations.getMotionSequence();
        frame.rotations.requestVisible(ROTATION_SOURCE, 5, next[0], next[1], yaw, pitch,
                () -> current(frame) && target == chosen && frame.rotations.getMotionSequence() == sequence
                        && sameView(frame.player, x, y, z, yaw, pitch) && admitted(frame)
                        && current(frame) && sameView(frame.player, x, y, z, yaw, pitch));
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
        if (chosen == null) { boolean had = owned != null; stopMining(frame); if (current(frame)) state(Status.SEARCHING); return had; }
        if (owned != null && !owned.target.pos.equals(chosen.pos)) { stopMining(frame); return true; }
        // Preserve native entity/miss priority; a block-only ray cannot override it.
        MovingObjectPosition picked = client.objectMouseOver;
        if (!nativeBedPick(picked, chosen)) { boolean had = owned != null; stopMining(frame); return had; }
        Vec3 eyes = frame.player.getPositionEyes(1);
        float reach = frame.controller.getBlockReachDistance();
        if (!current(frame) || !BedBreakerPlanner.finite(eyes) || !Float.isFinite(reach) || reach <= 0 || reach > 5) return false;
        Vec3 direction = frame.player.getLook(1);
        if (!current(frame) || !BedBreakerPlanner.finite(direction)) return false;
        MovingObjectPosition hit = frame.world.rayTraceBlocks(eyes,
                eyes.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach), false, false, false);
        if (!current(frame) || !BedBreakerPlanner.matches(hit, chosen.pos, eyes, reach) || !admitted(frame)) {
            boolean had = owned != null; stopMining(frame); if (current(frame)) state(Status.ALIGNING); return had;
        }
        if (!mineBudget.claim(frame.player, frame.world, 1)) return owned != null;
        long revision = frame.controller.getMiningRevision();
        if (owned != null && owned.revision != revision) { invalidate(Status.PAUSED_INPUT, Cleanup.MANUAL); return false; }
        Mine attempt = new Mine(frame, chosen, picked, revision + 1);
        owned = attempt;
        boolean accepted = frame.controller.onPlayerDamageBlock(chosen.pos, hit.sideHit, chosen.state,
                () -> actionCurrent(attempt), () -> current(frame));
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
                && frame.client.objectMouseOver == attempt.picked && nativeBedPick(attempt.picked, attempt.target)
                && frame.controller.getMiningRevision() == attempt.revision
                && sameView(frame.player, attempt.x, attempt.y, attempt.z, attempt.yaw, attempt.pitch)
                && frame.player.inventory.currentItem == attempt.slot && admitted(frame) && LagRange.allowBlockAttack()
                && owned == attempt && current(frame)
                && frame.controller.getMiningRevision() == attempt.revision && frame.player.inventory.currentItem == attempt.slot
                && frame.client.objectMouseOver == attempt.picked && nativeBedPick(attempt.picked, attempt.target)
                && sameView(frame.player, attempt.x, attempt.y, attempt.z, attempt.yaw, attempt.pitch);
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
                && !frame.player.isDead && frame.player.inventory != null
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
    private static boolean sameView(EntityPlayerSP player, double x, double y, double z, float yaw, float pitch) {
        return Double.compare(player.posX, x) == 0 && Double.compare(player.posY, y) == 0 && Double.compare(player.posZ, z) == 0
                && Float.compare(player.rotationYaw, yaw) == 0 && Float.compare(player.rotationPitch, pitch) == 0;
    }

    private static boolean nativeBedPick(MovingObjectPosition pick, BedBreakerPlanner.Target chosen) {
        return pick != null && pick.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && chosen.pos.equals(pick.getBlockPos());
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
        final Scene scene; final BedBreakerPlanner.Target target; final long revision;
        final MovingObjectPosition picked;
        final ItemStack held, heldSnapshot;
        final double x, y, z; final float yaw, pitch; final int slot;
        Mine(Scene scene, BedBreakerPlanner.Target target, MovingObjectPosition picked, long revision) {
            this.scene = scene; this.target = target; this.picked = picked; this.revision = revision;
            x = scene.player.posX; y = scene.player.posY; z = scene.player.posZ;
            yaw = scene.player.rotationYaw; pitch = scene.player.rotationPitch; slot = scene.player.inventory.currentItem;
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
