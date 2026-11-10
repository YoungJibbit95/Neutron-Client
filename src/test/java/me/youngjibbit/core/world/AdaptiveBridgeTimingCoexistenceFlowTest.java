package me.youngjibbit.core.world;

import me.youngjibbit.core.network.NetworkTestSupport;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.utils.Eierlecken;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * W08-F: three additional cross-module consumer cases.
 * Physical keys, elapsed time, world storage and incoming physical snapshots are synthetic.
 * Input sampling, final moveFlying, timing-module decisions, transport acceptance,
 * rotation ownership and native local placement use their existing production paths.
 * The reused input fixture deliberately stops before full EntityLivingBase physics.
 */
@RunWith(Parameterized.class)
public class AdaptiveBridgeTimingCoexistenceFlowTest {
    private static final long TICK = 50_000_000L;
    private static final BlockPos DESTINATION = new BlockPos(1, 63, 0);

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"lagRangeHeldMovementCannotAuthorizeAdaptivePlacementOrCatchup"},
                {"receiptRecoveryAndReconnectDiscardGodbridgeJumpAndSpeedState"},
                {"realTickbaseWithholdAndEarnedExtraDoNotMintScaffoldOrSpeedCredit"}
        });
    }

    private final String caseName;
    public AdaptiveBridgeTimingCoexistenceFlowTest(String caseName) { this.caseName = caseName; }

    @Test public void nativeConsumersInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Checks.class, caseName);
    }

    private static final class PacketHook implements Subscriber, AutoCloseable {
        @Subscribe final Listener<EventPacket> listener;
        PacketHook(Consumer<EventPacket> callback) {
            listener = new Listener<>((EventPacket event) -> callback.accept(event));
            Pulsar.BUS.subscribe(this);
        }
        @Override public void close() { Pulsar.BUS.unsubscribe(this); }
    }

    public static class Enemy extends EntityOtherPlayerMP {
        public Enemy() { super(null, null); }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public boolean isInvisible() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public net.minecraft.scoreboard.Team getTeam() { return null; }
    }

    private static final class Move {
        final ExecutedStep.Kind kind;
        final PlayerMoveUpdateEvent event;
        Move(ExecutedStep.Kind kind, PlayerMoveUpdateEvent event) { this.kind = kind; this.event = event; }
    }

    private static final class Step {
        final List<Object> packets;
        final List<Move> moves;
        final int bodies;
        Step(List<Object> packets, List<Move> moves, int bodies) {
            this.packets = packets; this.moves = moves; this.bodies = bodies;
        }
    }

    private static final class Run implements AutoCloseable {
        final AdaptiveBridgeInputSafetyFlowTest.Run input = new AdaptiveBridgeInputSafetyFlowTest.Run();
        final Speed speed = new Speed();
        final List<Move> moves = new ArrayList<>();
        final Listener<PlayerMoveUpdateEvent> capture;
        final sun.misc.Unsafe unsafe;
        final Field utilityClient;
        final Object oldUtilityClient;
        final Class<?> inputBoundary = Class.forName(
                AdaptiveBridgeInputSafetyFlowTest.class.getName() + "$ReachedInputBoundary");
        NetworkManager network = input.f.network;
        EmbeddedChannel channel = input.f.channel;
        LagRange lag;
        Backtrack backtrack;
        Tickbase tickbase;
        int bodies;
        boolean controlledIncomingMotion = true;

        Run(String profile, boolean autoJump) throws Exception {
            Field instance = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            unsafe = (sun.misc.Unsafe) instance.get(null);
            utilityClient = Eierlecken.class.getDeclaredField("mc");
            utilityClient.setAccessible(true);
            utilityClient.get(null); // Initialize before rebinding the existing fixture's static client.
            oldUtilityClient = unsafe.getObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient));
            unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), input.f.mc);
            input.f.aura.setToggled(false);
            CombatTargetTracker.clear();
            ScaffoldClientFlowTest.set(Minecraft.class, input.f.mc, "mcThread", Thread.currentThread());
            input.player.setEntityId(1);
            input.scene.loadedEntityList.add(input.player);
            bindTransport(network, input.player.sendQueue);
            input.adaptive("Smart");
            input.f.scaffold.getModeSetting("Bridge Rotation Profile").setValue(profile);
            input.f.scaffold.getModeSetting("Bridge Rotation Speed").setValue("Fast");
            input.f.scaffold.getNumberSetting("Bridge Rotation Variation").setValue(0D);
            input.f.scaffold.getNumberSetting("Place Min CPS").setValue(20D);
            input.f.scaffold.getNumberSetting("Place Max CPS").setValue(20D);
            input.f.scaffold.getNumberSetting("Variation Strength").setValue(0D);
            input.f.scaffold.getNumberSetting("Place Delay").setValue(0D);
            input.f.scaffold.getModeSetting("Click Profile").setValue("Basic");
            input.f.scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(autoJump);
            input.f.scaffold.getNumberSetting("Godbridge Jump After Blocks").setValue(1D);
            input.f.scaffold.getBooleanSetting("Sprint").setValue(true);
            ScaffoldClientFlowTest.set(Scaffold.class, input.f.scaffold, "bridgePlacementClock",
                    (LongSupplier) input.scene.motionTime::get);
            ScaffoldClientFlowTest.set(ScaffoldPlacementService.class,
                    input.f.runtime.getScaffoldPlacementService(), "clock",
                    (LongSupplier) () -> input.scene.motionTime.get() / 1_000_000L);
            input.f.runtime.getScaffoldPlacementService().reset();
            input.scene.blocks.clear();
            input.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            input.scene.blocks.put(new BlockPos(1, 63, -1), Blocks.stone.getDefaultState());
            input.position(.8, .5);
            input.player.rotationYaw = -90;
            input.player.motionX = .05;
            input.player.motionY = input.player.motionZ = 0;
            input.player.sprint = true;
            input.settings.keyBindForward.pressed = true;
            input.settings.keyBindSprint.pressed = true;
            PlacementTarget visible = input.f.runtime.getScaffoldPlacementService().planFace(
                    new BlockPos(1, 63, -1), EnumFacing.SOUTH, null);
            assertNotNull("the future face is visible from the real eyes", visible);
            assertFalse(input.f.runtime.getScaffoldPlacementService().stillNeeded(visible,
                    new PlacementContext(false, false, 63)));
            ScaffoldClientFlowTest.set(Speed.class, speed, "momentumClock",
                    (LongSupplier) input.scene.motionTime::get);
            speed.getModeSetting("Mode").setValue("Intave Momentum V2");
            speed.setToggled(true);
            capture = new Listener<>(PlayerMoveUpdateEvent.class, event -> {
                ExecutedStep step = input.f.runtime.getObservation().getCurrentStep();
                assertNotNull("moveFlying belongs to a real observed body", step);
                moves.add(new Move(step.getKind(), event));
            });
            Pulsar.BUS.subscribe(capture);
            input.f.scaffold.onEnable();
        }

        void bindTransport(NetworkManager manager, NetHandlerPlayClient play) throws Exception {
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, play, "gameController", input.f.mc);
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, play, "doneLoadingTerrain", true);
            play.clientWorldController = input.scene;
            manager.configureClientPackets(new NetworkTestSupport.MainThread(), play::observeAcceptedOutbound);
            ScaffoldClientFlowTest.set(PacketPipeline.class, manager.getClientPackets(), "delays",
                    new PacketDelays(input.scene.motionTime::get));
        }

        PacketPipeline pipe() { return network.getClientPackets(); }

        Step tick() { return tick(TICK); }

        Step tick(long elapsed) {
            input.scene.motionTime.addAndGet(elapsed);
            input.f.runtime.getScaffoldPlacementService().beginTick();
            int beforeBodies = bodies, beforeMoves = moves.size();
            RuntimeObservation observation = input.f.runtime.getObservation();
            observation.beginOuterTick();
            try {
                // Use the original frame consumers and the original body dispatcher.
                if (lag != null && lag.isToggled()) LagRange.beginFrame(input.f.mc, false, 1, elapsed / 1_000_000L);
                if (tickbase != null && tickbase.isToggled()) Tickbase.beginFrame(input.f.mc, false, 1, elapsed / 1_000_000L);
                Pulsar.BUS.post(new TickEvent());
                Tickbase.runPlayerStep(input.player, () -> {
                    bodies++;
                    input.player.ticksExisted++;
                    if (controlledIncomingMotion) {
                        // Controlled incoming physics snapshot, not a Speed or budget implementation.
                        input.player.motionX = .05;
                        input.player.motionZ = 0;
                    }
                    try {
                        input.player.onLivingUpdate();
                        fail("the reused fixture must reach its post-input boundary");
                    } catch (RuntimeException boundary) {
                        // Catch only the existing fixture sentinel; every other production exception fails.
                        if (boundary.getClass() != inputBoundary) throw boundary;
                    }
                    input.player.moveFlying(MovementInput.moveStrafe * .98F,
                            MovementInput.moveForward * .98F, .1F);
                    input.player.onUpdateWalkingPlayer();
                    return true;
                });
            } finally { observation.endOuterTick(); }
            return new Step(drain(channel), new ArrayList<>(moves.subList(beforeMoves, moves.size())), bodies - beforeBodies);
        }

        void warm() {
            Step latest = null;
            for (int tick = 0; tick < 80; tick++) {
                latest = tick();
                assertEquals(1, latest.bodies);
                assertEquals(1, count(latest.packets, C03PacketPlayer.class));
                assertEquals("an occupied foot cell cannot place early", 0, count(latest.packets, C08PacketPlayerBlockPlacement.class));
            }
            assertEquals("scaffold", input.f.runtime.getRotationManager().getConfirmedOwner());
            assertNotNull(latest);
            assertEquals("V2 is actually active beside accepted Scaffold rotation", .112F,
                    latest.moves.get(0).event.getFriction(), 1e-7);
            assertEquals(0, input.f.scaffold.bridgePlacementCount());
        }

        Enemy enemy(double x) throws Exception {
            Enemy enemy = RotationBehaviorHookTest.bare(Enemy.class);
            enemy.worldObj = input.scene;
            enemy.setEntityId(17);
            enemy.posX = x; enemy.posY = 64; enemy.posZ = .5;
            enemy.serverPosX = (int) (x * 32); enemy.serverPosY = 64 * 32; enemy.serverPosZ = 16;
            enemy.hurtTime = 5;
            enemy.inventory = new InventoryPlayer(enemy);
            enemy.setEntityBoundingBox(new AxisAlignedBB(x - .3, 64, .2, x + .3, 65.8, .8));
            input.scene.loadedEntityList.add(enemy);
            input.f.mc.objectMouseOver = new MovingObjectPosition(enemy, enemy.getPositionVector());
            return enemy;
        }

        void enableLag() {
            lag = new LagRange(input.scene.motionTime::get, (min, max) -> max);
            lag.getBooleanSetting("Only Aura").setValue(false);
            lag.getModeSetting("Delay Mode").setValue("Bounded Frame");
            lag.getNumberSetting("Start Distance").setValue(1D);
            lag.getNumberSetting("Window Ticks").setValue(2D);
            lag.setToggled(true);
        }

        void enableReceipt() {
            backtrack = new Backtrack(input.scene.motionTime::get, input.f.mc);
            backtrack.getBooleanSetting("Only Aura").setValue(false);
            backtrack.getModeSetting("Target Source").setValue("Attack / Cursor");
            backtrack.getModeSetting("Backtrack Mode").setValue("Receipt Safe");
            backtrack.getModeSetting("Delay Mode").setValue("Fixed");
            backtrack.getNumberSetting("Delay").setValue(100D);
            backtrack.getNumberSetting("Cooldown Ticks").setValue(0D);
            backtrack.setToggled(true);
        }

        void enableTickbase() {
            tickbase = new Tickbase(input.scene.motionTime::get, input.f.mc);
            tickbase.getBooleanSetting("Only Aura").setValue(false);
            tickbase.getBooleanSetting("Debug").setValue(true);
            tickbase.getNumberSetting("Charge Ticks").setValue(2D);
            tickbase.getNumberSetting("Min Charge").setValue(1D);
            tickbase.getNumberSetting("Release Speed").setValue(2D);
            tickbase.setToggled(true);
        }

        void receiveMovement() {
            channel.writeInbound(new S14PacketEntity.S15PacketEntityRelMove(17, (byte) 4, (byte) 0, (byte) 0, true));
        }

        void placeOne() {
            input.position(1.05, .5);
            int attempts = 0;
            for (int tick = 0; tick < 160 && input.scene.getBlockState(DESTINATION).getBlock() != Blocks.stone; tick++) {
                Step step = tick();
                long placed = count(step.packets, C08PacketPlayerBlockPlacement.class);
                assertTrue("no physical opportunity may place twice", placed <= 1);
                attempts = Math.addExact(attempts, Math.toIntExact(placed));
                if (placed != 0) orderedMovementAndPlacement(step.packets);
            }
            assertEquals("a genuine local ItemBlock placement is required", Blocks.stone,
                    input.scene.getBlockState(DESTINATION).getBlock());
            assertEquals(1, attempts);
            assertEquals(1, input.f.scaffold.bridgePlacementCount());
            assertEquals(63, input.player.inventory.mainInventory[3].stackSize);
        }

        void reconnect() throws Exception {
            network.closeChannel(new ChatComponentText("W08-F reconnect"));
            network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
            channel = new EmbeddedChannel(network);
            NetHandlerPlayClient play = new NetHandlerPlayClient(null, null, network, null);
            bindTransport(network, play);
            network.setNetHandler(play);
            ScaffoldClientFlowTest.set(EntityPlayerSP.class, input.player, "sendQueue", play);
            input.f.mc.playerController = new PlayerControllerMP(input.f.mc, play);
        }

        @Override public void close() throws Exception {
            try {
                if (tickbase != null) tickbase.setToggled(false);
                if (backtrack != null) backtrack.setToggled(false);
                if (lag != null) lag.setToggled(false);
                speed.setToggled(false);
                Pulsar.BUS.unsubscribe(capture);
                CombatTargetTracker.clear();
                if (network != input.f.network) {
                    network.closeChannel(new ChatComponentText("end W08-F reconnect"));
                    channel.finish();
                    drain(channel);
                }
            } finally {
                unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), oldUtilityClient);
                input.close();
            }
        }
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> packets = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) packets.add(packet);
        return packets;
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static void orderedMovementAndPlacement(List<Object> packets) {
        int movement = -1, placement = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i) instanceof C03PacketPlayer) movement = i;
            if (packets.get(i) instanceof C08PacketPlayerBlockPlacement) placement = i;
        }
        assertTrue("native placement follows accepted current walking transport", movement >= 0 && placement > movement);
    }

    private static void lagRangeHeldMovementCannotAuthorizeAdaptivePlacementOrCatchup() throws Exception {
        try (Run run = new Run("Adaptive", false)) {
            run.warm();
            run.enemy(6);
            run.enableLag();
            run.tick(); // Context binding deliberately invalidates the first pre-bind frame.
            Step held = run.tick();
            assertTrue("Bounded Frame really owns the existing timing window", run.pipe().combatTiming().owns(run.lag));
            assertTrue("the existing queue really contains held C03", run.pipe().delays().size() > 0);
            assertEquals(0, count(held.packets, C03PacketPlayer.class));
            C0FPacketConfirmTransaction ack = new C0FPacketConfirmTransaction(0, (short) 7, true);
            run.network.sendPacket(ack);
            assertSame("control reply stays independent of the movement hold", ack, run.channel.readOutbound());
            run.input.position(1.05, .5);
            Step gap = run.tick();
            assertEquals("Bounded Frame still executes exactly one ordinary player body", 1, gap.bodies);
            assertEquals(ExecutedStep.Kind.ORDINARY, gap.moves.get(0).kind);
            assertEquals(.112F, gap.moves.get(0).event.getFriction(), 1e-7);
            assertTrue(run.pipe().delays().size() > 0);
            assertEquals(0, count(gap.packets, C03PacketPlayer.class));
            assertEquals(0, count(gap.packets, C08PacketPlayerBlockPlacement.class));
            assertEquals(0, run.input.f.scaffold.bridgePlacementCount());

            int bodies = run.bodies, moves = run.moves.size();
            long sequence = run.input.f.runtime.getRotationManager().getMotionSequence();
            run.input.scene.motionTime.addAndGet(30_000_000L);
            run.pipe().pump();
            List<Object> released = drain(run.channel);
            assertEquals("the original pump drains the due held entries", 0, run.pipe().delays().size());
            assertEquals("Scaffold's immutable C03 ticket expires with its original motion frame", 0,
                    count(released, C03PacketPlayer.class));
            assertEquals("an expired movement ticket cannot manufacture another POST placement", 0,
                    count(released, C08PacketPlayerBlockPlacement.class));
            assertEquals(bodies, run.bodies);
            assertEquals(moves, run.moves.size());
            assertEquals(sequence, run.input.f.runtime.getRotationManager().getMotionSequence());
            assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
            assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
            assertEquals(Blocks.air, run.input.scene.getBlockState(DESTINATION).getBlock());

            run.lag.setToggled(false);
            assertEquals(0, run.pipe().delays().size());
            List<Object> stopped = drain(run.channel);
            assertEquals(0, count(stopped, C03PacketPlayer.class));
            assertEquals(0, count(stopped, C08PacketPlayerBlockPlacement.class));
            try (PacketHook reject = new PacketHook(event -> {
                if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C03PacketPlayer) event.cancel();
            })) {
                Step cancelled = run.tick();
                assertEquals(0, count(cancelled.packets, C03PacketPlayer.class));
                assertEquals(0, count(cancelled.packets, C08PacketPlayerBlockPlacement.class));
                assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
            }
            run.placeOne();
            run.input.f.scaffold.setToggled(false);
            run.speed.setToggled(false);
            run.input.position(2.05, .5);
            Step disabled = run.tick();
            assertEquals(0, count(disabled.packets, C08PacketPlayerBlockPlacement.class));
            assertEquals(.1F, disabled.moves.get(0).event.getFriction(), 0);
            assertFalse(run.input.player.movementInput.jump);
        }
    }

    private static void receiptRecoveryAndReconnectDiscardGodbridgeJumpAndSpeedState() throws Exception {
        try (Run run = new Run("Godbridge", true)) {
            for (int epoch = 0; epoch < 3; epoch++) run.pipe().worldChanged();
            run.warm();
            run.placeOne();
            Enemy enemy = run.enemy(3.5);
            run.enableReceipt();
            Pulsar.BUS.post(new TickEvent());
            assertEquals("a toggle and cursor alone are not receipt evidence", 0, run.pipe().delays().size());
            int previousPosition = enemy.serverPosX;
            run.receiveMovement();
            assertEquals("Receipt Safe really holds the received S14", 1, run.pipe().delays().size());
            assertTrue(run.pipe().combatTiming().owns(run.backtrack));
            assertEquals(previousPosition, enemy.serverPosX);
            assertEquals((previousPosition + 4) / 32D, run.pipe().receivedPosition(enemy).xCoord, 0);
            run.channel.writeInbound(new S12PacketEntityVelocity(1, .2, .3, 0));
            assertEquals("the actual local impulse drains the earlier receipt FIFO", 0, run.pipe().delays().size());
            assertEquals(previousPosition + 4, enemy.serverPosX);
            assertEquals(.2, run.input.player.motionX, 1e-4);
            assertEquals(.3, run.input.player.motionY, 1e-4);
            assertEquals(1, run.input.f.scaffold.bridgePlacementCount());
            // A grounded physical snapshot before the original 500-ms recovery deadline.
            // Other ordinary jump/sprint gates are valid, so recovery itself must win.
            run.input.player.motionY = 0;
            run.controlledIncomingMotion = false;
            assertTrue(run.pipe().isRecoveringLocalImpulse(run.input.player));
            Step recovery = run.tick();
            assertEquals(1, recovery.bodies);
            assertEquals(.1F, recovery.moves.get(0).event.getFriction(), 0);
            assertFalse("the confirmed Godbridge block cannot autojump during real recovery", run.input.player.movementInput.jump);
            assertFalse(run.input.settings.keyBindJump.pressed);
            assertEquals(1, run.input.f.scaffold.bridgePlacementCount());
            assertEquals(0, count(recovery.packets, C08PacketPlayerBlockPlacement.class));

            long oldEpoch = run.pipe().getContextVersion();
            run.reconnect();
            assertTrue("replacement transport really starts at a lower raw epoch", run.pipe().getContextVersion() < oldEpoch);
            run.controlledIncomingMotion = true;
            Step fresh = run.tick();
            assertEquals("a new scene gets a cold V2 ramp", .104F, fresh.moves.get(0).event.getFriction(), 1e-7);
            assertEquals(1, count(fresh.packets, C03PacketPlayer.class));
            assertEquals(0, count(fresh.packets, C08PacketPlayerBlockPlacement.class));
            assertEquals("old local placement count is not a new connection's jump credit", 0,
                    run.input.f.scaffold.bridgePlacementCount());
            assertFalse(run.input.player.movementInput.jump);
            assertEquals("old receipt evidence cannot hold packets on the replacement connection", 0, run.pipe().delays().size());
            assertTrue(run.backtrack.getStatus(), run.backtrack.getStatus().contains("receipt missing or stale"));
            run.receiveMovement();
            assertEquals("only a new receipt can arm the replacement connection", 1, run.pipe().delays().size());
            run.backtrack.setToggled(false);
            assertEquals(0, run.pipe().delays().size());
            assertEquals(0, count(drain(run.channel), C08PacketPlayerBlockPlacement.class));
            assertNull(run.input.f.channel.readOutbound());
        }
    }

    private static void realTickbaseWithholdAndEarnedExtraDoNotMintScaffoldOrSpeedCredit() throws Exception {
        try (Run run = new Run("Adaptive", false)) {
            run.warm();
            Enemy enemy = run.enemy(3);
            run.input.position(1.05, .5);
            boolean[] rejectPlacement = {true};
            List<ExecutedStep.Kind> attemptedBy = new ArrayList<>();
            try (PacketHook placement = new PacketHook(event -> {
                if (!event.isPre() || !event.isOutbound()
                        || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                attemptedBy.add(run.input.f.runtime.getObservation().getCurrentStep().getKind());
                if (rejectPlacement[0]) event.cancel();
            })) {
                for (int tick = 0; tick < 160 && attemptedBy.isEmpty(); tick++) {
                    Step denied = run.tick();
                    assertEquals(0, count(denied.packets, C08PacketPlayerBlockPlacement.class));
                }
                assertFalse("the future release uses an already reachable actual controller intent", attemptedBy.isEmpty());
                assertEquals(Blocks.air, run.input.scene.getBlockState(DESTINATION).getBlock());
                assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                int rejectedBeforePause = attemptedBy.size();
                run.enableTickbase();
                long sequence = run.input.f.runtime.getRotationManager().getMotionSequence();
                Step withheld = run.tick();
                assertEquals("the actual TickbaseBudget decision withholds the body", 0, withheld.bodies);
                assertTrue(withheld.moves.isEmpty());
                assertEquals(1, run.tickbase.getDiagnostics().credit);
                assertEquals("withheld_player_step", run.tickbase.getDiagnostics().reason);
                assertEquals(sequence, run.input.f.runtime.getRotationManager().getMotionSequence());
                assertEquals(0, count(withheld.packets, C03PacketPlayer.class));
                assertEquals(0, count(withheld.packets, C08PacketPlayerBlockPlacement.class));

                Step late = run.tick(250_000_000L);
                assertEquals("a stalled frame has one normal body and no catchup", 1, late.bodies);
                assertEquals(0, run.tickbase.getDiagnostics().credit);
                assertEquals(0, run.tickbase.getDiagnostics().extraSteps);
                assertEquals("elapsed time and the withheld body cannot mature V2", .104F,
                        late.moves.get(0).event.getFriction(), 1e-7);
                assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                assertEquals(0, count(late.packets, C08PacketPlayerBlockPlacement.class));
                assertEquals("the stalled rotation cannot offer another controller intent", rejectedBeforePause,
                        attemptedBy.size());

                assertEquals(0, run.tick().bodies); // Earn one actual withheld body.
                assertEquals("a withheld body cannot reacquire or retry placement", rejectedBeforePause,
                        attemptedBy.size());
                Step ordinaryReacquisition=run.tick(); // Original alternation requires an ordinary body.
                assertEquals(1,ordinaryReacquisition.bodies);
                assertEquals(1,count(ordinaryReacquisition.packets,C03PacketPlayer.class));
                assertEquals(0,count(ordinaryReacquisition.packets,C08PacketPlayerBlockPlacement.class));
                // A new real motion may already point at the retained, still-valid face.
                // Its nominal first-step budget does not have to spend an idle HOLD tick.
                assertTrue("one fresh ordinary body has at most one controller opportunity",
                        attemptedBy.size()>=rejectedBeforePause && attemptedBy.size()<=rejectedBeforePause+1);
                if(attemptedBy.size()>rejectedBeforePause)
                    assertEquals(ExecutedStep.Kind.ORDINARY,attemptedBy.get(attemptedBy.size()-1));
                int afterOrdinaryReacquisition=attemptedBy.size();
                assertEquals(0, run.tick().bodies); // Earn the second actual withheld body.
                assertEquals(2, run.tickbase.getDiagnostics().credit);
                assertEquals("the second withheld body cannot spend the ordinary body's intent", afterOrdinaryReacquisition,
                        attemptedBy.size());

                // The real 300-ms gap between bodies invalidated the rotation curve.
                // Require a fresh native C08 attempt after physically sampled sneak,
                // whether the earlier ordinary body already reacquired its useful ray
                // or needs more time. The two earned credits stay untouched, and the
                // original V2 ramp stays cold without changing any private state.
                run.input.settings.keyBindSneak.pressed = true;
                int beforeSneakRecheck=attemptedBy.size();
                for (int tick = 0; tick < 80 && attemptedBy.size() == beforeSneakRecheck; tick++) {
                    int beforeBody=attemptedBy.size();
                    Step reacquiring = run.tick();
                    assertEquals(1, reacquiring.bodies);
                    assertEquals(1, reacquiring.moves.size());
                    assertEquals(ExecutedStep.Kind.ORDINARY, reacquiring.moves.get(0).kind);
                    assertTrue("fresh physical sneak reaches the actual input consumer", run.input.player.movementInput.sneak);
                    assertEquals(.1F, reacquiring.moves.get(0).event.getFriction(), 0);
                    assertEquals(2, run.tickbase.getDiagnostics().credit);
                    assertEquals(0, run.tickbase.getDiagnostics().extraSteps);
                    assertFalse(run.tickbase.getDiagnostics().releasing);
                    assertEquals(1, count(reacquiring.packets, C03PacketPlayer.class));
                    assertEquals(0, count(reacquiring.packets, C08PacketPlayerBlockPlacement.class));
                    assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                    assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
                    assertEquals(Blocks.air, run.input.scene.getBlockState(DESTINATION).getBlock());
                    assertTrue("elapsed time cannot mint more than one attempt for this ordinary body",
                            attemptedBy.size()>=beforeBody && attemptedBy.size()<=beforeBody+1);
                }
                assertEquals("a new real rejected C08 proves fresh post-stall placement revalidation", beforeSneakRecheck + 1,
                        attemptedBy.size());
                assertEquals(ExecutedStep.Kind.ORDINARY, attemptedBy.get(attemptedBy.size() - 1));
                run.input.settings.keyBindSneak.pressed = false;
                // As in ExploitHookFixture, omit unrelated local damage simulation only.
                // Native C02 acceptance and the resulting AttackResultEvent remain real.
                Field gameType = PlayerControllerMP.class.getDeclaredField("currentGameType");
                gameType.setAccessible(true);
                Object priorGameType = gameType.get(run.input.f.mc.playerController);
                try {
                    gameType.set(run.input.f.mc.playerController, WorldSettings.GameType.SPECTATOR);
                    assertTrue(run.input.f.mc.playerController.tryAttackEntity(run.input.player, enemy, () -> true));
                } finally { gameType.set(run.input.f.mc.playerController, priorGameType); }
                assertEquals(1, count(drain(run.channel), C02PacketUseEntity.class));
                assertTrue("only the original accepted attack releases earned credit", run.tickbase.getDiagnostics().releasing);
                rejectPlacement[0] = false;
                attemptedBy.clear();
                Step release = run.tick();
                assertEquals("actual earned release executes ordinary plus one extra", 2, release.bodies);
                assertFalse("the release body samples the newly released physical sneak key", run.input.player.movementInput.sneak);
                assertEquals(2, release.moves.size());
                assertEquals(ExecutedStep.Kind.ORDINARY, release.moves.get(0).kind);
                assertEquals(ExecutedStep.Kind.EARNED_EXTRA, release.moves.get(1).kind);
                assertEquals(.104F, release.moves.get(0).event.getFriction(), 1e-7);
                assertEquals("earned extra never claims V2's ordinary ramp opportunity", .1F,
                        release.moves.get(1).event.getFriction(), 0);
                assertEquals(1, run.tickbase.getDiagnostics().credit);
                assertEquals(1, run.tickbase.getDiagnostics().extraSteps);
                assertEquals("ordinary placement really executes beside the earned extra body", 1,
                        count(release.packets, C08PacketPlayerBlockPlacement.class));
                orderedMovementAndPlacementBeforeExtra(release.packets);
                assertEquals(1, attemptedBy.size());
                assertEquals(ExecutedStep.Kind.ORDINARY, attemptedBy.get(0));
                assertEquals("the extra body cannot recount the first body's local placement", 1,
                        run.input.f.scaffold.bridgePlacementCount());
                assertEquals(Blocks.stone, run.input.scene.getBlockState(DESTINATION).getBlock());
                assertEquals(63, run.input.player.inventory.mainInventory[3].stackSize);
                assertFalse(run.input.player.movementInput.jump);
                run.tickbase.setToggled(false);
                Step ordinary = run.tick();
                assertEquals(1, ordinary.bodies);
                assertEquals("the extra body did not advance the ramp", .108F,
                        ordinary.moves.get(0).event.getFriction(), 1e-7);
            }
        }
    }

    private static void orderedMovementAndPlacementBeforeExtra(List<Object> packets) {
        int firstMovement = -1, secondMovement = -1, placement = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i) instanceof C03PacketPlayer) {
                if (firstMovement < 0) firstMovement = i;
                else if (secondMovement < 0) secondMovement = i;
            }
            if (packets.get(i) instanceof C08PacketPlayerBlockPlacement) placement = i;
        }
        assertTrue("ordinary C08 follows its native C03, before an extra body can publish another C03",
                firstMovement >= 0 && placement > firstMovement && secondMovement > placement);
    }

    public static final class Checks {
        @Test public void lagRangeHeldMovementCannotAuthorizeAdaptivePlacementOrCatchup() throws Exception {
            AdaptiveBridgeTimingCoexistenceFlowTest.lagRangeHeldMovementCannotAuthorizeAdaptivePlacementOrCatchup();
        }
        @Test public void receiptRecoveryAndReconnectDiscardGodbridgeJumpAndSpeedState() throws Exception {
            AdaptiveBridgeTimingCoexistenceFlowTest.receiptRecoveryAndReconnectDiscardGodbridgeJumpAndSpeedState();
        }
        @Test public void realTickbaseWithholdAndEarnedExtraDoNotMintScaffoldOrSpeedCredit() throws Exception {
            AdaptiveBridgeTimingCoexistenceFlowTest.realTickbaseWithholdAndEarnedExtraDoNotMintScaffoldOrSpeedCredit();
        }
    }
}
