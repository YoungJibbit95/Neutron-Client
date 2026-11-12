package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.DemiseBedBreakerGeometry;
import me.youngjibbit.core.world.DemiseRotationEngine;
import me.youngjibbit.core.world.DemiseRotationHooks;
import me.youngjibbit.core.world.DemiseRotationSettings;
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
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.lang.ref.WeakReference;

/**
 * BedBreaker selection, settings and action flow from DemisePrivate e219a97 (AGPL-3.0).
 * See the pinned Demise manifest. Continuous rotation, scene lifetime and native-mining
 * ownership are Neutron integration boundaries.
 */
@ModuleInfo(name = "BedBreaker", category = Category.Player,
        description = "Demise bed targeting and mining with continuous rotations and movement correction.")
public final class BedBreaker extends Module {
    public static final String ROTATION_SOURCE = "bedbreaker";
    public final CheckBoxValue legit = new CheckBoxValue("Legit", this, false);
    public final DemiseRotationSettings rotationSettings = new DemiseRotationSettings(this);
    public final DemiseRotationEngine rotationEngine = DemiseRotationEngine.continuous();
    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, MINING, PAUSED_INPUT, PAUSED_MODULE }
    private Status status = Status.OFF;
    // Mining generations can end while this same scene continues its rotation-only return.
    private long generation;
    private Scene scene;
    private DemiseBedBreakerGeometry.Target target;
    private Mine owned;
    private boolean processing;
    private WeakReference<Object> unloadedWorld = new WeakReference<>(null);
    // These budgets deliberately survive toggles and config changes.
    private final TickBudget updateBudget = new TickBudget();
    private final ManualBudget manualAttack = new ManualBudget();

    public BedBreaker() { enableSmartDisable(); }
    @Override public void onEnable() {
        endAction(Status.WAITING, Cleanup.ABORT);
        DemiseRotationHooks.bindBed();
        capture();
        super.onEnable();
    }
    @Override public void onDisable() {
        try { endAction(Status.OFF, Cleanup.ABORT); } finally { super.onDisable(); }
    }
    @Override public void onValueChange() {
        // Slider construction can notify its owner before the engine field is initialized.
        if (rotationEngine != null) endAction(isToggled() ? Status.WAITING : Status.OFF, Cleanup.ABORT);
    }
    public Status getStatus() { return status; }

    /** AngleEvent only refreshes the selected bed center; it never scans or mines. */
    public void onDemiseAngle() {
        if (processing || !isToggled()) return;
        processing = true;
        try {
            Scene frame = capture();
            if (frame == null) return;
            long action = generation;
            if (!admitted(frame)) { pause(frame); return; }
            DemiseBedBreakerGeometry.Target bed = target;
            if (bed == null) { rotationEngine.beginReturn(); return; }
            if (!bedPresent(frame, bed, action)) {
                if (active(frame, action)) endAction(Status.SEARCHING, Cleanup.ABORT);
                return;
            }
            float[] desired = DemiseBedBreakerGeometry.rotations(frame.player.getPositionEyes(1), bed.pos);
            if (desired == null || !active(frame, action) || target != bed || !admitted(frame) || !active(frame, action)) return;
            rotationEngine.setRotation(desired, rotationSettings.snapshot());
            if (status != Status.MINING) state(Status.ALIGNING);
        } finally { processing = false; }
    }

    /** Original UpdateEvent: speed sample, even-tick bed search, then native mining. */
    public void onDemiseUpdate() {
        if (processing || !isToggled()) return;
        processing = true;
        try {
            Scene frame = capture();
            if (frame == null) return;
            // Suspension ends owned mining even when no ordinary action body is admitted.
            if (!viewReady(frame)) { pause(frame); return; }
            if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                    || !updateBudget.claim(frame.player, frame.world)) return;
            final long action = generation;
            rotationEngine.updateSpeeds(rotationSettings.snapshot());
            if (!active(frame, action)) return;
            if (!admitted(frame) || !LagRange.allowBlockAttack()) { pause(frame); return; }
            if (foreignMining(frame)) { endAction(Status.PAUSED_INPUT, Cleanup.MANUAL); return; }
            if (toolChanged(frame)) return;
            if (frame.player.ticksExisted % 2 == 0) {
                Vec3 feet = new Vec3(frame.player.posX, frame.player.posY, frame.player.posZ);
                DemiseBedBreakerGeometry.Target selected = DemiseBedBreakerGeometry.find(frame.access, feet,
                        frame.player.getEyeHeight(), () -> active(frame, action) && admitted(frame)
                                && samePosition(frame.player, feet.xCoord, feet.yCoord, feet.zCoord));
                if (!active(frame, action)) return;
                if (selected != null && target != null && selected.pos.equals(target.pos) && selected.state.equals(target.state))
                    selected = target;
                target = selected;
            }
            DemiseBedBreakerGeometry.Target bed = target;
            if (bed == null || !bedPresent(frame, bed, action)) {
                if (active(frame, action)) endAction(Status.SEARCHING, Cleanup.ABORT);
                return;
            }
            mine(frame, bed, action);
        } finally { processing = false; }
    }

    /** Shared preparation also publishes the disabled rotation-only return. */
    public void requestDemiseRotation() {
        final Scene frame = scene;
        if (frame != null && !current(frame)) { onDemiseSceneInvalidated(); return; }
        if (!correctionEnabled()) return;
        final DemiseRotationEngine.Aim aim = rotationEngine.currentAim();
        if (aim == null) return;
        final long sequence = frame.rotations.getMotionSequence();
        frame.rotations.request(ROTATION_SOURCE, 5, aim.yaw, aim.pitch,
                () -> current(frame) && frame.rotations.getMotionSequence() == sequence
                        && rotationEngine.shouldRotate() && rotationViewReady(frame));
    }
    public boolean correctionEnabled() {
        return rotationEngine.shouldRotate() && current(scene) && rotationViewReady(scene);
    }
    public MoveFixController.Mode moveFixMode() {
        return rotationEngine.correction() == DemiseRotationEngine.Correction.None
                ? MoveFixController.Mode.OFF : MoveFixController.Mode.ROTATION_BOUND;
    }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.DIRECT; }

    /** Called by the always-live Demise hooks even after this module unsubscribes. */
    public void onDemiseSceneInvalidated() {
        Scene previous = scene; Mine old = owned;
        generation++; scene = null; target = null; owned = null;
        state(isToggled() ? Status.WAITING : Status.OFF);
        Minecraft client = Minecraft.getMinecraft();
        rotationEngine.clear(client != null && client.thePlayer != null ? client.thePlayer.rotationYaw : 0,
                client != null && client.thePlayer != null ? client.thePlayer.rotationPitch : 0);
        if (previous != null) previous.rotations.reset(ROTATION_SOURCE);
        finish(old, Cleanup.DISCARD);
    }

    private enum Cleanup { ABORT, DISCARD, MANUAL }
    private void state(Status next) { status = next; info = next == Status.OFF ? "" : next.name().replace('_', ' '); }
    private void endAction(Status next, Cleanup cleanup) {
        generation++;
        Mine old = owned; owned = null; target = null;
        state(next); rotationEngine.beginReturn();
        finish(old, cleanup);
    }
    private void finish(Mine old, Cleanup cleanup) {
        if (old == null || cleanup == Cleanup.MANUAL) return;
        PlayerControllerMP controller = old.scene.controller;
        if (controller.getMiningRevision() != old.revision || !controller.isMiningBlock(old.pos)) return;
        long stopping = generation;
        if (cleanup == Cleanup.ABORT && old.scene.objects())
            controller.resetBlockRemoving(old.pos, old.revision,
                    () -> generation == stopping && scene == old.scene && old.scene.objects());
        else controller.discardBlockRemoving(old.pos, old.revision);
    }

    @Subscribe private final Listener<WorldEvent> worldChanged = new Listener<>(event -> {
        if (event instanceof WorldEvent.Unload) unloadedWorld = new WeakReference<>(event.world);
        else if (event instanceof WorldEvent.Load) unloadedWorld.clear();
        onDemiseSceneInvalidated();
    });
    @Subscribe private final Listener<TickEvent> contextChanged = new Listener<>(event -> {
        if (scene != null) {
            if (!current(scene)) onDemiseSceneInvalidated();
            else if (!viewReady(scene)) pause(scene);
        }
    });

    private static BedBreaker instance() {
        ModuleManager modules = Pulsar.INSTANCE.getMm();
        return modules == null ? null : (BedBreaker) modules.getModule(BedBreaker.class);
    }
    public static void observeManualAttack(Minecraft client) {
        BedBreaker module = instance();
        if (module == null || client == null || client.thePlayer == null || client.theWorld == null
                || !client.isCallingFromMinecraftThread()) return;
        module.manualAttack.mark(client.thePlayer, client.theWorld);
        if (module.isToggled()) module.endAction(Status.PAUSED_INPUT, Cleanup.MANUAL);
    }

    /** Physical input owns manual progress; idle input only preserves an exact existing owner. */
    public static boolean handleBlockInput(Minecraft client, boolean physicalAttack) {
        BedBreaker module = instance();
        if (module == null || client == null || !client.isCallingFromMinecraftThread()) return false;
        if (physicalAttack) { observeManualAttack(client); return false; }
        Mine old = module.owned;
        return old != null && module.isToggled() && old.scene.client == client && module.current(old.scene)
                && old.scene.controller.getMiningRevision() == old.revision && old.scene.controller.isMiningBlock(old.pos);
    }

    private void mine(Scene frame, DemiseBedBreakerGeometry.Target bed, long action) {
        if (!active(frame, action) || target != bed || !actionAllowed(frame) || !active(frame, action)) return;
        if (!rotationReady(frame)) { stopMining(frame); if (active(frame, action)) state(Status.ALIGNING); return; }
        // Update precedes this tick's walking packet: only the last accepted wire pose can authorize mining.
        final long sequence = frame.rotations.getMotionSequence();
        final float yaw = frame.rotations.getEffectiveYaw(), pitch = frame.rotations.getEffectivePitch();
        final Vec3 look = EntityPlayerSP.getVectorForRotation(pitch, yaw);
        final Vec3 eyes = frame.player.getPositionEyes(1);
        final boolean legitimate = legit.getValue();
        final MovingObjectPosition mouse = frame.client.objectMouseOver;
        if (!active(frame, action) || !DemiseBedBreakerGeometry.finite(eyes) || !DemiseBedBreakerGeometry.finite(look)
                || mouse == null || mouse.sideHit == null) { stopMining(frame); return; }
        final EnumFacing face = mouse.sideHit;
        final BlockPos pos;
        final IBlockState expected;
        if (legitimate) {
            if (mouse.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mouse.getBlockPos() == null) {
                stopMining(frame); return;
            }
            pos = new BlockPos(mouse.getBlockPos());
            if (!frame.world.isBlockLoaded(pos) || !active(frame, action)) { stopMining(frame); return; }
            expected = frame.world.getBlockState(pos);
        } else {
            DemiseRotationEngine.Aim aim = rotationEngine.currentAim();
            Vec3 liveLook = aim == null ? null : EntityPlayerSP.getVectorForRotation(aim.pitch, aim.yaw);
            MovingObjectPosition hit = DemiseBedBreakerGeometry.rayTraceBed(frame.world, bed.pos, eyes, liveLook);
            if (!active(frame, action) || !DemiseBedBreakerGeometry.matches(hit, bed.pos, eyes, DemiseBedBreakerGeometry.REACH)) {
                stopMining(frame); if (active(frame, action)) state(Status.ALIGNING); return;
            }
            pos = bed.pos; expected = bed.state;
        }
        if (expected == null || !active(frame, action) || target != bed || legit.getValue() != legitimate
                || !actionAllowed(frame) || !active(frame, action) || !sameRotation(frame, sequence, yaw, pitch)) return;
        long revision = frame.controller.getMiningRevision();
        if (owned != null && owned.revision != revision || foreignMining(frame)) {
            endAction(Status.PAUSED_INPUT, Cleanup.MANUAL); return;
        }
        if (owned != null && (!owned.pos.equals(pos) || !owned.expected.equals(expected))) {
            // The update budget is already claimed. A clean abort can be followed
            // by this update's replacement, but callbacks cannot take over it.
            ItemStack held = frame.player.inventory.getCurrentItem();
            ItemStack snapshot = held == null ? null : held.copy();
            int slot = frame.player.inventory.currentItem;
            double x = frame.player.posX, y = frame.player.posY, z = frame.player.posZ;
            stopMining(frame);
            if (!active(frame, action) || target != bed || owned != null || foreignMining(frame)
                    || frame.controller.getMiningRevision() != revision + 1 || !sameRotation(frame, sequence, yaw, pitch)
                    || !samePosition(frame.player, x, y, z) || frame.player.inventory.currentItem != slot
                    || frame.player.inventory.getCurrentItem() != held || !ItemStack.areItemStacksEqual(snapshot, held)) return;
            revision = frame.controller.getMiningRevision();
        }
        if (!active(frame, action) || !actionAllowed(frame) || !active(frame, action)) return;
        Mine attempt = new Mine(frame, bed, pos, expected, legitimate, action, revision + 1, sequence, yaw, pitch, look);
        owned = attempt;
        boolean accepted = legitimate
                ? frame.controller.onPlayerDamageBlock(pos, face, expected, () -> actionCurrent(attempt),
                        () -> active(frame, action), () -> attempt.look)
                : frame.controller.onPlayerDamageBed(pos, face, expected, () -> actionCurrent(attempt),
                        () -> active(frame, action), () -> attempt.look);
        if (!active(frame, action) || owned != attempt) return;
        if (frame.controller.getMiningRevision() != attempt.revision) {
            endAction(Status.PAUSED_INPUT, Cleanup.MANUAL); return;
        }
        if (accepted && actionCurrent(attempt)) {
            state(Status.MINING);
            frame.handler.addToSendQueueGuarded(new C0APacketAnimation(), () -> actionCurrent(attempt));
        }
        if (!active(frame, action) || owned != attempt) return;
        if (!bedPresent(frame, bed, action)) {
            // Native destruction/tool callbacks finish before the rotation return starts.
            if (active(frame, action)) endAction(Status.SEARCHING, Cleanup.ABORT);
        } else if (owned == attempt && !frame.controller.isMiningBlock(pos)) owned = null;
    }

    private boolean actionCurrent(Mine attempt) {
        Scene frame = attempt.scene;
        return owned == attempt && active(frame, attempt.generation) && target == attempt.bed
                && legit.getValue() == attempt.legitimate && sameRotation(frame, attempt.sequence, attempt.yaw, attempt.pitch)
                && frame.controller.getMiningRevision() == attempt.revision
                && samePosition(frame.player, attempt.x, attempt.y, attempt.z)
                && frame.player.inventory.currentItem == attempt.slot && actionAllowed(frame)
                && (attempt.removesBed || bedPresent(frame, attempt.bed, attempt.generation))
                && owned == attempt && active(frame, attempt.generation) && target == attempt.bed
                && frame.controller.getMiningRevision() == attempt.revision && frame.player.inventory.currentItem == attempt.slot
                && legit.getValue() == attempt.legitimate && sameRotation(frame, attempt.sequence, attempt.yaw, attempt.pitch)
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
    private boolean bedPresent(Scene frame, DemiseBedBreakerGeometry.Target bed, long action) {
        if (bed == null || !active(frame, action)) return false;
        boolean loaded = frame.world.isBlockLoaded(bed.pos);
        if (!loaded || !active(frame, action)) return false;
        IBlockState state = frame.world.getBlockState(bed.pos);
        return active(frame, action) && bed.state.equals(state);
    }
    private void pause(Scene frame) { if (current(frame)) endAction(Status.PAUSED_INPUT, Cleanup.ABORT); }
    private void stopMining(Scene frame) {
        if (!current(frame)) return;
        Mine old = owned; owned = null; finish(old, Cleanup.ABORT);
    }
    private boolean foreignMining(Scene frame) {
        return frame.controller.func_181040_m() && (owned == null || owned.scene != frame
                || owned.revision != frame.controller.getMiningRevision() || !frame.controller.isMiningBlock(owned.pos));
    }
    private boolean toolChanged(Scene frame) {
        Mine old = owned;
        if (old == null || old.sameTool()) return false;
        long action = generation;
        stopMining(frame);
        if (active(frame, action)) state(Status.WAITING);
        return true;
    }
    private Scene capture() {
        Minecraft client = Minecraft.getMinecraft();
        if (scene != null && !current(scene)) { onDemiseSceneInvalidated(); return null; }
        if (!isToggled() || client == null || !client.isCallingFromMinecraftThread() || client.thePlayer == null
                || client.theWorld == null || client.playerController == null || client.gameSettings == null
                || client.thePlayer.inventory == null || unloadedWorld.get() == client.theWorld) return null;
        if (scene == null) {
            DemiseRotationHooks.bindBed();
            EntityPlayerSP player = client.thePlayer;
            if (player.sendQueue == null || player.sendQueue.getNetworkManager() == null) return null;
            PacketPipeline packets = player.sendQueue.getNetworkManager().getClientPackets();
            ModuleManager modules = Pulsar.INSTANCE.getMm();
            if (packets == null || !packets.isActive() || modules == null || modules.getModule(BedBreaker.class) != this) return null;
            scene = new Scene(client, modules, packets);
        }
        return current(scene) ? scene : null;
    }
    private boolean current(Scene frame) {
        return frame != null && scene == frame && unloadedWorld.get() != frame.world && frame.objects()
                && frame.modules.getModule(BedBreaker.class) == this;
    }
    private boolean active(Scene frame, long action) { return isToggled() && generation == action && current(frame); }
    private boolean viewReady(Scene frame) {
        return rotationViewReady(frame) && frame.client.currentScreen == null && frame.client.inGameHasFocus
                && !frame.client.isGamePaused();
    }
    /** A same-scene menu/focus pause can finish its rotation without admitting mining. */
    private boolean rotationViewReady(Scene frame) {
        return frame.client.gameSettings != null && frame.client.getRenderViewEntity() == frame.player
                && !frame.player.isDead && frame.player.isEntityAlive() && !frame.player.isSpectator() && !frame.player.isRiding();
    }
    private boolean inputFree(Scene frame) {
        GameSettings keys = frame.client.gameSettings;
        return viewReady(frame) && !manualAttack.blocked(frame.player, frame.world)
                && keys.keyBindAttack != null && !keys.keyBindAttack.isKeyDown()
                && keys.keyBindUseItem != null && !keys.keyBindUseItem.isKeyDown() && !frame.player.isUsingItem();
    }
    private boolean admitted(Scene frame) {
        if (!isToggled() || !current(frame) || !inputFree(frame)) return false;
        Module scaffold = frame.modules.getModule(Scaffold.class), next = frame.modules.getModule(NScaffold.class);
        if (scaffold != null && scaffold.isToggled() || next != null && next.isToggled()) return false;
        Aura aura = (Aura) frame.modules.getModule(Aura.class);
        if (aura != null && aura.isToggled() && aura.getActiveTarget() != null) return false;
        return current(frame) && inputFree(frame) && !RuntimeServices.attackScheduler().hasAttackThisTick() && current(frame);
    }
    private boolean actionAllowed(Scene frame) {
        return admitted(frame) && AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)
                && LagRange.allowBlockAttack() && current(frame);
    }
    private static boolean samePosition(EntityPlayerSP player, double x, double y, double z) {
        return Double.compare(player.posX, x) == 0 && Double.compare(player.posY, y) == 0 && Double.compare(player.posZ, z) == 0;
    }

    private static final class Scene {
        final Minecraft client; final EntityPlayerSP player; final WorldClient world;
        final PlayerControllerMP controller; final NetHandlerPlayClient handler;
        final PacketPipeline packets; final ModuleManager modules; final RotationManager rotations;
        final RuntimeObservation observation;
        final long epoch;
        final DemiseBedBreakerGeometry.Access access;
        Scene(Minecraft client, ModuleManager modules, PacketPipeline packets) {
            this.client = client; player = client.thePlayer; world = client.theWorld; controller = client.playerController;
            handler = player.sendQueue; this.packets = packets; this.modules = modules;
            epoch = packets.getContextVersion(); rotations = RuntimeServices.rotation(); observation = RuntimeServices.observation();
            access = new DemiseBedBreakerGeometry.Access() {
                public boolean loaded(BlockPos pos) { return world.isBlockLoaded(pos); }
                public IBlockState state(BlockPos pos) { return world.getBlockState(pos); }
            };
        }
        boolean objects() {
            return Minecraft.getMinecraft() == client && client.thePlayer == player && client.theWorld == world
                    && client.playerController == controller && player.worldObj == world && player.sendQueue == handler
                    && player.inventory != null && Pulsar.INSTANCE.getMm() == modules && handler.getNetworkManager() != null
                    && handler.getNetworkManager().getClientPackets() == packets && packets.isActive()
                    && packets.getContextVersion() == epoch && handler.getNetworkManager().isChannelOpen()
                    && RuntimeServices.rotation() == rotations && RuntimeServices.observation() == observation;
        }
    }
    private static final class Mine {
        final Scene scene; final DemiseBedBreakerGeometry.Target bed; final BlockPos pos;
        final IBlockState expected;
        final long generation, revision, sequence;
        final boolean legitimate, removesBed;
        final Vec3 look;
        final ItemStack held, heldSnapshot;
        final double x, y, z; final float yaw, pitch; final int slot;
        Mine(Scene scene, DemiseBedBreakerGeometry.Target bed, BlockPos pos, IBlockState expected, boolean legitimate,
             long generation, long revision, long sequence, float yaw, float pitch, Vec3 look) {
            this.scene = scene; this.bed = bed; this.pos = pos; this.expected = expected; this.legitimate = legitimate;
            this.generation = generation; this.revision = revision; this.sequence = sequence;
            this.yaw = yaw; this.pitch = pitch; this.look = look;
            removesBed = pos.equals(bed.pos) || sameBedHalf(bed, pos, expected);
            x = scene.player.posX; y = scene.player.posY; z = scene.player.posZ;
            slot = scene.player.inventory.currentItem;
            held = scene.player.inventory.getCurrentItem(); heldSnapshot = held == null ? null : held.copy();
        }
        private static boolean sameBedHalf(DemiseBedBreakerGeometry.Target bed, BlockPos pos, IBlockState state) {
            if (!(state.getBlock() instanceof BlockBed) || !(bed.state.getBlock() instanceof BlockBed)) return false;
            EnumFacing facing = bed.state.getValue(BlockBed.FACING);
            boolean head = bed.state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD;
            return pos.equals(bed.pos.offset(head ? facing.getOpposite() : facing))
                    && state.getValue(BlockBed.FACING) == facing
                    && state.getValue(BlockBed.PART) != bed.state.getValue(BlockBed.PART);
        }
        boolean sameTool() {
            return scene.player.inventory != null && scene.player.inventory.currentItem == slot
                    && scene.player.inventory.getCurrentItem() == held && ItemStack.areItemStacksEqual(heldSnapshot, held);
        }
    }
    private static final class TickBudget {
        private WeakReference<Object> player = new WeakReference<>(null), world = new WeakReference<>(null);
        private long last = Long.MIN_VALUE;
        boolean claim(EntityPlayerSP currentPlayer, WorldClient currentWorld) {
            if (player.get() != currentPlayer || world.get() != currentWorld) {
                player = new WeakReference<>(currentPlayer); world = new WeakReference<>(currentWorld); last = Long.MIN_VALUE;
            }
            long tick = currentPlayer.ticksExisted;
            if (last != Long.MIN_VALUE && tick <= last) return false;
            last = tick; return true;
        }
    }
    private static final class ManualBudget {
        private WeakReference<Object> player = new WeakReference<>(null), world = new WeakReference<>(null);
        private WeakReference<RuntimeObservation> observation = new WeakReference<>(null);
        private long tick = Long.MIN_VALUE, outerTick = -1;
        void mark(EntityPlayerSP currentPlayer, WorldClient currentWorld) {
            player = new WeakReference<>(currentPlayer); world = new WeakReference<>(currentWorld);
            tick = currentPlayer.ticksExisted;
            RuntimeObservation current = RuntimeServices.observation();
            observation = new WeakReference<>(current); outerTick = current == null ? -1 : current.getActiveOuterTickId();
        }
        boolean blocked(EntityPlayerSP currentPlayer, WorldClient currentWorld) {
            if (player.get() != currentPlayer || world.get() != currentWorld) return false;
            RuntimeObservation current = RuntimeServices.observation();
            return currentPlayer.ticksExisted <= tick || outerTick >= 0 && current != null && observation.get() == current
                    && current.getActiveOuterTickId() == outerTick;
        }
    }
}
