package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Native world ray/controller/transport with a tiny explicit block store; no game window or server claim. */
final class BedBreakerTestSupport {
    static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    static final class Bed extends BlockBed {
        float hardness = .25f;
        Runnable clicked = () -> { };
        @Override public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, BlockPos pos) { return hardness; }
        @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) { clicked.run(); }
    }
    static final class BedWorld extends WorldClient implements BedBreakerPlanner.Access {
        Map<BlockPos, IBlockState> blocks;
        WorldBorder border;
        Runnable afterRay, beforeRemoval;
        Consumer<BlockPos> afterRead;
        int stateReads, rays, removals, cracks, loadedQueries;
        BedWorld() { super(null, null, 0, null, null); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            stateReads++; IBlockState result = blocks.get(pos);
            if (afterRead != null) afterRead.accept(pos);
            return result == null ? Blocks.air.getDefaultState() : result;
        }
        @Override public boolean isBlockLoaded(BlockPos pos) { loadedQueries++; return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to, boolean liquid, boolean ignore, boolean last) {
            rays++; MovingObjectPosition result = super.rayTraceBlocks(from, to, liquid, ignore, last);
            Runnable callback = afterRay; afterRay = null; if (callback != null) callback.run();
            return result;
        }
        @Override public boolean setBlockToAir(BlockPos pos) {
            Runnable callback = beforeRemoval; beforeRemoval = null; if (callback != null) callback.run();
            if (blocks.remove(pos) == null) return false; removals++; return true;
        }
        @Override public void sendBlockBreakProgress(int id, BlockPos pos, int progress) { cracks++; }
        @Override public void playAuxSFX(int id, BlockPos pos, int data) { }
        @Override public boolean extinguishFire(EntityPlayer player, BlockPos pos, EnumFacing side) { return false; }
        public boolean loaded(BlockPos pos) { return isBlockLoaded(pos); }
        public IBlockState state(BlockPos pos) { return getBlockState(pos); }
        public AxisAlignedBB bounds(BlockPos pos, IBlockState state) { return state.getBlock().getCollisionBoundingBox(this, pos, state); }
        public MovingObjectPosition ray(Vec3 from, Vec3 to) { return rayTraceBlocks(from, to, false, false, false); }
    }
    static BedWorld world() throws Exception {
        Bootstrap.register();
        BedWorld world = RotationBehaviorHookTest.bare(BedWorld.class);
        world.blocks = new HashMap<>(); world.border = new WorldBorder(); return world;
    }
    static final class Sound extends SoundHandler {
        Sound() { super(null, null); }
        @Override public void playSound(ISound sound) { }
    }
    static final class Effects extends net.minecraft.client.particle.EffectRenderer {
        Runnable callback;
        Effects() { super(null, null); }
        @Override public void addBlockHitEffects(BlockPos pos, EnumFacing side) { callback.run(); }
    }
    static final class Controller extends PlayerControllerMP {
        int automaticCalls, manualAttacks;
        Controller(Minecraft client, NetHandlerPlayClient handler) { super(client, handler); }
        @Override public boolean onPlayerDamageBlock(BlockPos pos, EnumFacing face, IBlockState state,
                                                    java.util.function.BooleanSupplier valid, java.util.function.BooleanSupplier lifecycle,
                                                    java.util.function.Supplier<Vec3> look) {
            automaticCalls++; return super.onPlayerDamageBlock(pos, face, state, valid, lifecycle, look);
        }
        @Override public void attackEntity(EntityPlayer player, Entity target) { manualAttacks++; }
    }
    static final class Observer implements Subscriber {
        Consumer<EventPacket> action = event -> { };
        @Subscribe private final Listener<EventPacket> listener = new Listener<>(event -> action.accept(event));
    }
    static final class Fixture implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f;
        final BedWorld world;
        final Bed bed;
        final BedBreaker module;
        final Controller controller;
        final Observer observer = new Observer();
        final Map<Class<? extends Module>, Module> modules;
        final BlockPos pos = new BlockPos(0, 64, 2);
        final Method input;
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            Bootstrap.register();
            f = new RotationBehaviorHookTest.Fixture(); world = world(); bed = new Bed();
            f.mc.theWorld = world; f.player.worldObj = world;
            f.player.capabilities = new PlayerCapabilities(); f.player.onGround = true;
            f.player.posX = .5; f.player.posY = 64; f.player.posZ = .5;
            field(Minecraft.class, "mcThread").set(f.mc, Thread.currentThread());
            field(Minecraft.class, "mcSoundHandler").set(f.mc, RotationBehaviorHookTest.bare(Sound.class));
            Effects effects = RotationBehaviorHookTest.bare(Effects.class); effects.callback = () -> { };
            f.mc.effectRenderer = effects;
            field(Module.class, "toggled").setBoolean(f.aura, false);
            field(Module.class, "toggled").setBoolean(f.scaffold, false);
            f.mc.gameSettings = RotationBehaviorHookTest.bare(GameSettings.class);
            f.mc.gameSettings.keyBindAttack = RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindUseItem = RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.mouseSensitivity = .5f;
            controller = new Controller(f.mc, f.player.sendQueue); f.mc.playerController = controller;
            field(NetHandlerPlayClient.class, "gameController").set(f.player.sendQueue, f.mc);
            f.player.sendQueue.clientWorldController = world;
            modules = (Map<Class<? extends Module>, Module>) field(ModuleManager.class, "modulesByClass").get(Pulsar.INSTANCE.getMm());
            module = new BedBreaker(); modules.put(BedBreaker.class, module);
            module.getBooleanSetting("Swing").setValue(false);
            module.getNumberSetting("Turn Speed").setValue(180D); module.setToggled(true);
            world.blocks.put(pos, bed.getDefaultState());
            input = Minecraft.class.getDeclaredMethod("sendClickBlockToController", boolean.class); input.setAccessible(true);
            Pulsar.BUS.subscribe(observer);
        }
        void aim() {
            BedBreakerPlanner.Target target = new BedBreakerPlanner(world).select(f.player.getPositionEyes(1), 4.5, null, true, () -> true);
            if (target == null) throw new AssertionError("fixture bed not visible");
            float[] pose = BedBreakerPlanner.rotations(f.player.getPositionEyes(1), target.aim);
            f.player.rotationYaw = pose[0]; f.player.rotationPitch = pose[1];
            pick();
            rotation();
        }
        void rotation() {
            RotationManager manager = f.runtime.getRotationManager(); manager.bindConnection(f.network.getClientPackets());
            manager.beginMotion(f.player, world, f.player.rotationYaw, f.player.rotationPitch);
            EventMotion event = new EventMotion(f.player.posX, f.player.posY, f.player.posZ,
                    f.player.rotationYaw, f.player.rotationPitch, true);
            Pulsar.BUS.post(new EventRotation(event)); manager.resolve(event);
            manager.finishMotion(event.getYaw(), event.getPitch(), true);
            pick();
        }
        void pick() {
            Vec3 eyes = f.player.getPositionEyes(1), look = f.player.getLook(1);
            double reach = controller.getBlockReachDistance();
            f.mc.objectMouseOver = world.ray(eyes, eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        }
        void input(boolean attack) throws Exception { input.invoke(f.mc, attack); }
        void next() { f.player.ticksExisted++; }
        float progress() throws Exception { return field(PlayerControllerMP.class, "curBlockDamageMP").getFloat(controller); }
        List<C07PacketPlayerDigging> digs() {
            List<C07PacketPlayerDigging> result = new ArrayList<>(); Object packet;
            while ((packet = f.channel.readOutbound()) != null) if (packet instanceof C07PacketPlayerDigging) result.add((C07PacketPlayerDigging) packet);
            return result;
        }
        void key(KeyBinding binding, boolean down) throws Exception { field(KeyBinding.class, "pressed").setBoolean(binding, down); }
        @Override public void close() throws Exception {
            observer.action = event -> { };
            try { module.setToggled(false); } finally { Pulsar.BUS.unsubscribe(observer); f.close(); }
        }
    }
}
