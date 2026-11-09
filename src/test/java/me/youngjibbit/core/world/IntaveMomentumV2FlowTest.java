package me.youngjibbit.core.world;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.utils.Eierlecken;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Native final movement and fresh-input regression coverage for opt-in V2. */
@RunWith(Parameterized.class)
public class IntaveMomentumV2FlowTest {
    @Parameterized.Parameters(name = "{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"ordinarySprintAddsBoundedNativeAccelerationWithoutOwningJumpOrOverrides"},
                {"acceptedMoveFixAndFinalListenersKeepOneTransformForBothOwnersAndEveryKeyDirection"},
                {"duplicateExtraWithheldAndSameTickResetCannotCreateOrdinaryCredit"},
                {"freshSprintSneakItemManualJumpAndRealScaffoldGuardReachTheFinalConsumer"},
                {"airborneTailExpiresAndLandingWallAndDirectionChangesStartFromTheirOwnLimits"},
                {"actualS12PreservesImpulseAndBlocksRecoveryWhileCancelledS12KeepsTheRamp"},
                {"appliedS08PauseSurvivesInitialObservationSuspensionAndSettingsReset"},
                {"lifecycleChangesStartColdPreserveForeignOverridesAndRejectReentrantObservationReplacement"}
        });
    }
    private final String method;
    public IntaveMomentumV2FlowTest(String method) { this.method = method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    public static class Checks {
    private static final String MODE = "Intave Momentum V2";

    public static class CallbackScene extends IntaveBoatSpeedTest.Scene {
        Runnable onRead;
        @Override public IBlockState getBlockState(BlockPos position) {
            Runnable callback = onRead;
            onRead = null;
            if (callback != null) callback.run();
            return super.getBlockState(position);
        }
    }

    private static final class Run implements AutoCloseable {
        final IntaveBoatSpeedTest.Run base;
        final Speed speed;
        final Listener<PlayerMoveUpdateEvent> capture;
        final sun.misc.Unsafe unsafe;
        final Field utilityClient;
        final Object oldUtilityClient;
        EmbeddedChannel replacementChannel;
        PlayerMoveUpdateEvent last;

        Run() throws Exception {
            Field instance = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            unsafe = (sun.misc.Unsafe) instance.get(null);
            utilityClient = Eierlecken.class.getDeclaredField("mc");
            oldUtilityClient = utilityClient.get(null);
            base = new IntaveBoatSpeedTest.Run();
            speed = new Speed();
            unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), base.f.mc);
            // V2 has no boat dependency. Keep the local player for actual S12 entity lookup.
            base.world.entities.clear();
            base.world.entities.add(base.f.player);
            base.f.player.setEntityId(7);
            base.f.player.movementInput.moveForward = .98F;
            base.f.mc.gameSettings.keyBindSneak = new KeyBinding("momentum.sneak", 42, "test");
            base.f.mc.gameSettings.keyBindSprint = new KeyBinding("momentum.sprint", 29, "test");
            ScaffoldClientFlowTest.set(Minecraft.class, base.f.mc, "mcThread", Thread.currentThread());
            ScaffoldClientFlowTest.set(EntityPlayerSP.class, base.f.player, "mc", base.f.mc);
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, base.f.player.sendQueue, "gameController", base.f.mc);
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, base.f.player.sendQueue, "doneLoadingTerrain", true);
            base.f.player.sendQueue.clientWorldController = base.world;
            ScaffoldClientFlowTest.set(PacketPipeline.class, base.f.network.getClientPackets(), "delays",
                    new PacketDelays(base.time::get));
            ScaffoldClientFlowTest.set(Speed.class, speed, "momentumClock", (LongSupplier) base.time::get);
            speed.getModeSetting("Mode").setValue(MODE);
            speed.setToggled(true);
            capture = new Listener<>(PlayerMoveUpdateEvent.class, event -> last = event, EventPriority.LOWEST);
            Pulsar.BUS.subscribe(capture);
        }

        void zeroHorizontal() { base.f.player.motionX = base.f.player.motionZ = 0; }
        PlayerMoveUpdateEvent move(float strafe, float forward, float friction) {
            last = null;
            base.f.player.moveFlying(strafe, forward, friction);
            assertNotNull("the native movement event was delivered", last);
            return last;
        }
        PlayerMoveUpdateEvent tick(float strafe, float forward, float friction) {
            base.tick(() -> move(strafe, forward, friction));
            return last;
        }
        void observed(Consumer<BooleanSupplier> dispatch, Runnable action) {
            base.f.player.ticksExisted++;
            base.time.addAndGet(50_000_000L);
            RuntimeObservation observation = base.f.runtime.getObservation();
            observation.beginOuterTick();
            try {
                observation.observePlayerStep(base.f.player, () -> { action.run(); return true; }, dispatch);
            } finally { observation.endOuterTick(); }
        }
        void warm() {
            for (int i = 0; i < 3; i++) {
                zeroHorizontal();
                tick(0, .98F, .1F);
            }
            assertEquals(.112F, last.getFriction(), 1e-7);
        }
        void velocity(double x, double y, double z) {
            base.f.channel.writeInbound(new S12PacketEntityVelocity(7, x, y, z));
        }
        void world(IntaveBoatSpeedTest.Scene scene) {
            scene.entities = new ArrayList<>();
            scene.entities.add(base.f.player);
            scene.floor = true;
            base.f.mc.theWorld = scene;
            base.f.player.worldObj = scene;
            base.f.player.sendQueue.clientWorldController = scene;
        }
        void reconnect() throws Exception {
            NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
            replacementChannel = new EmbeddedChannel(network);
            NetHandlerPlayClient play = new NetHandlerPlayClient(null, null, network, null);
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, play, "gameController", base.f.mc);
            play.clientWorldController = base.f.mc.theWorld;
            network.configureClientPackets(new NetworkTestSupport.MainThread());
            network.setNetHandler(play);
            ScaffoldClientFlowTest.set(PacketPipeline.class, network.getClientPackets(), "delays",
                    new PacketDelays(base.time::get));
            ScaffoldClientFlowTest.set(EntityPlayerSP.class, base.f.player, "sendQueue", play);
            base.f.mc.playerController = new PlayerControllerMP(base.f.mc, play);
        }
        @Override public void close() throws Exception {
            try {
                speed.setToggled(false);
                Pulsar.BUS.unsubscribe(capture);
                base.f.mc.gameSettings.keyBindJump.pressed = false;
                base.f.mc.gameSettings.keyBindSneak.pressed = false;
                MovementInput.moveForward = MovementInput.moveStrafe = 0;
                if (replacementChannel != null) {
                    replacementChannel.finish();
                    while (replacementChannel.readOutbound() != null) { }
                }
            } finally {
                unsafe.putObject(unsafe.staticFieldBase(utilityClient), unsafe.staticFieldOffset(utilityClient), oldUtilityClient);
                base.close();
            }
        }
    }

    @Test public void ordinarySprintAddsBoundedNativeAccelerationWithoutOwningJumpOrOverrides() throws Exception {
        for (float baseFriction : new float[]{.1F, .13F}) {
            try (Run run = new Run()) {
                run.speed.setToggled(false);
                run.zeroHorizontal();
                PlayerMoveUpdateEvent vanilla = run.tick(0, .98F, baseFriction);
                double vanillaX = run.base.f.player.motionX, vanillaZ = run.base.f.player.motionZ;
                run.speed.getModeSetting("Mode").setValue("Intave Momentum");
                run.speed.setToggled(true);
                run.zeroHorizontal();
                PlayerMoveUpdateEvent legacy = run.tick(0, .98F, baseFriction);
                double legacyX = run.base.f.player.motionX, legacyZ = run.base.f.player.motionZ;
                assertEquals(vanilla.getFriction(), legacy.getFriction(), 0);
                assertEquals(vanillaX, legacyX, 0);
                assertEquals(vanillaZ, legacyZ, 0);
                run.speed.setToggled(false);
                run.speed.getModeSetting("Mode").setValue(MODE);
                run.speed.setToggled(true);
                run.base.f.mc.timer.timerSpeed = 1.15F;
                run.base.f.player.speedInAir = .077F;
                double matureX = 0, matureZ = 0;
                for (int tick = 0; tick < 5; tick++) {
                    run.zeroHorizontal();
                    PlayerMoveUpdateEvent event = run.tick(0, .98F, baseFriction);
                    assertEquals(baseFriction + Math.min(.012F, .004F * (tick + 1)), event.getFriction(), 1e-7);
                    assertEquals(vanilla.getYaw(), event.getYaw(), 0);
                    assertEquals(vanilla.getStrafe(), event.getStrafe(), 0);
                    assertEquals(vanilla.getForward(), event.getForward(), 0);
                    nativeMotion(run.base.f.player, event, 0, 0, 0);
                    assertTrue(run.base.f.player.motionZ > legacyZ);
                    matureX = run.base.f.player.motionX;
                    matureZ = run.base.f.player.motionZ;
                    assertFalse("V2 never creates an automatic jump", run.base.f.player.movementInput.jump);
                    assertFalse(run.base.f.mc.gameSettings.keyBindJump.pressed);
                    assertEquals(1.15F, run.base.f.mc.timer.timerSpeed, 0);
                    assertEquals(.077F, run.base.f.player.speedInAir, 0);
                }
                System.out.printf(Locale.ROOT,
                        "V2 native moveFlying only: grounded=true sprint=true wall=false strafe=0 forward=.98 yaw=0 baseFriction=%.3f incomingXZ=(0,0) vanillaDeltaXZ=(%.9f,%.9f) legacyMomentumDeltaXZ=(%.9f,%.9f) matureV2DeltaXZ=(%.9f,%.9f) extraDeltaXZ=(%.9f,%.9f)%n",
                        baseFriction, vanillaX, vanillaZ, legacyX, legacyZ, matureX, matureZ,
                        matureX - legacyX, matureZ - legacyZ);
                run.base.f.player.sprint = false;
                run.zeroHorizontal();
                assertEquals("walking retains vanilla friction", baseFriction,
                        run.tick(0, .98F, baseFriction).getFriction(), 0);
                run.base.f.player.sprint = true;
                run.zeroHorizontal();
                assertEquals("sprint resumes with a cold ramp", baseFriction + .004F,
                        run.tick(0, .98F, baseFriction).getFriction(), 1e-7);
                run.zeroHorizontal();
                assertEquals(baseFriction, run.tick(0, 0, baseFriction).getFriction(), 0);
                assertEquals(0, run.base.f.player.motionZ, 0);
                run.base.f.player.motionZ = .39;
                PlayerMoveUpdateEvent bounded = run.tick(0, .98F, baseFriction);
                assertEquals("unsafe extra acceleration is refused", baseFriction, bounded.getFriction(), 0);
                nativeMotion(run.base.f.player, bounded, 0, 0, .39);
            }
        }
    }

    @Test public void acceptedMoveFixAndFinalListenersKeepOneTransformForBothOwnersAndEveryKeyDirection()
            throws Exception {
        for (String owner : new String[]{"aura", "scaffold"}) {
            for (MoveFixController.Mode correction : MoveFixController.Mode.values()) {
                try (Run run = new Run()) {
                    run.base.f.player.rotationYaw = 180;
                    run.base.f.aura.getModeSetting("movefix").setValue(correction.label);
                    run.base.f.scaffold.getModeSetting("movefix").setValue(correction.label);
                    for (float forward : new float[]{-1, 0, 1}) for (float strafe : new float[]{-1, 0, 1}) {
                        if (forward == 0 && strafe == 0) continue;
                        run.speed.setToggled(false);
                        run.zeroHorizontal();
                        run.base.tick(() -> {
                            run.base.f.accept(owner, 73);
                            run.move(strafe * .98F, forward * .98F, .1F);
                        });
                        PlayerMoveUpdateEvent baseline = run.last;
                        double baselineX = run.base.f.player.motionX, baselineZ = run.base.f.player.motionZ;
                        run.speed.setToggled(true);
                        run.zeroHorizontal();
                        run.base.tick(() -> {
                            run.base.f.accept(owner, 73);
                            run.move(strafe * .98F, forward * .98F, .1F);
                        });
                        PlayerMoveUpdateEvent adjusted = run.last;
                        assertEquals(.104F, adjusted.getFriction(), 1e-7);
                        assertEquals(baseline.getYaw(), adjusted.getYaw(), 0);
                        assertEquals(baseline.getStrafe(), adjusted.getStrafe(), 0);
                        assertEquals(baseline.getForward(), adjusted.getForward(), 0);
                        double ratio = adjusted.getFriction() / (double) baseline.getFriction();
                        assertEquals("accepted direction is scaled once", baselineX * ratio,
                                run.base.f.player.motionX, 2e-7);
                        assertEquals(baselineZ * ratio, run.base.f.player.motionZ, 2e-7);
                        assertEquals(180, run.base.f.player.rotationYaw, 0);
                    }
                }
            }
        }
        try (Run run = new Run()) {
            Listener<PlayerMoveUpdateEvent> finalInput = new Listener<>(PlayerMoveUpdateEvent.class, event -> {
                event.setStrafe(.3F); event.setForward(.7F); event.setYaw(37); event.setFriction(.15F);
            }, EventPriority.LOWEST);
            Pulsar.BUS.subscribe(finalInput);
            try {
                PlayerMoveUpdateEvent actual = run.tick(0, .98F, .1F);
                assertEquals(.154F, actual.getFriction(), 1e-7);
                assertEquals(.3F, actual.getStrafe(), 0);
                assertEquals(.7F, actual.getForward(), 0);
                assertEquals(37, actual.getYaw(), 0);
                nativeMotion(run.base.f.player, actual, 0, 0, 0);
            } finally { Pulsar.BUS.unsubscribe(finalInput); }
        }
    }

    @Test public void duplicateExtraWithheldAndSameTickResetCannotCreateOrdinaryCredit() throws Exception {
        try (Run run = new Run()) {
            assertEquals("a consumer outside simulation cannot acquire a boost", .1F,
                    run.move(0, .98F, .1F).getFriction(), 0);
            run.base.tick(() -> {
                run.zeroHorizontal();
                assertEquals(.104F, run.move(0, .98F, .1F).getFriction(), 1e-7);
                run.zeroHorizontal();
                assertEquals("duplicate consumer", .1F, run.move(0, .98F, .1F).getFriction(), 0);
                run.speed.setToggled(false);
                run.speed.setToggled(true);
                run.zeroHorizontal();
                assertEquals("re-enable cannot reopen the physical opportunity", .1F,
                        run.move(0, .98F, .1F).getFriction(), 0);
            });
            run.zeroHorizontal();
            assertEquals(.104F, run.tick(0, .98F, .1F).getFriction(), 1e-7);
            int[] bodies = {0};
            run.observed(step -> { step.getAsBoolean(); step.getAsBoolean(); }, () -> {
                run.zeroHorizontal();
                float expected = ++bodies[0] == 1 ? .108F : .1F;
                assertEquals("only the ordinary body receives the next ramp step", expected,
                        run.move(0, .98F, .1F).getFriction(), 1e-7);
            });
            assertEquals(2, bodies[0]);
            bodies[0] = 0;
            run.observed(step -> { step.getAsBoolean(); step.getAsBoolean(); }, () -> {
                if (++bodies[0] == 2) {
                    run.zeroHorizontal();
                    assertEquals("an extra body cannot claim an unused ordinary opportunity", .1F,
                            run.move(0, .98F, .1F).getFriction(), 0);
                }
            });
            run.zeroHorizontal();
            assertEquals("missing ordinary movement starts cold", .104F,
                    run.tick(0, .98F, .1F).getFriction(), 1e-7);
            run.observed(step -> { }, () -> fail("withheld simulation must stay withheld"));
            run.zeroHorizontal();
            assertEquals("withheld ticks accumulate no credit", .104F,
                    run.tick(0, .98F, .1F).getFriction(), 1e-7);
        }
    }

    @Test public void freshSprintSneakItemManualJumpAndRealScaffoldGuardReachTheFinalConsumer() throws Exception {
        for (String input : new String[]{"sprint", "walking", "sneak", "item", "manual jump", "guard", "guard manual jump"}) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run run = new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                boolean guard = input.startsWith("guard"), manual = input.endsWith("jump");
                if (guard) {
                    run.approachingEdge("Smart");
                    run.position(.28, .5);
                    run.player.motionX = 0;
                    run.f.scaffold.onEnable();
                } else {
                    run.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
                    run.scene.blocks.put(new BlockPos(0, 63, 1), Blocks.stone.getDefaultState());
                    run.position(.5, .5);
                    run.settings.keyBindForward.pressed = true;
                }
                run.player.sprint = !"walking".equals(input);
                run.settings.keyBindSprint.pressed = run.player.sprint;
                run.settings.keyBindSneak.pressed = "sneak".equals(input);
                run.settings.keyBindJump.pressed = manual;
                run.player.usingItem = "item".equals(input);
                Speed speed = new Speed();
                ScaffoldClientFlowTest.set(Speed.class, speed, "momentumClock", (LongSupplier) run.scene.motionTime::get);
                speed.getModeSetting("Mode").setValue(MODE);
                speed.setToggled(true);
                PlayerMoveUpdateEvent[] movement = {null};
                Listener<PlayerMoveUpdateEvent> capture = new Listener<>(PlayerMoveUpdateEvent.class, event -> movement[0] = event);
                Pulsar.BUS.subscribe(capture);
                try {
                    run.step(false, () -> {
                        assertEquals("the real key sample remains authoritative", manual, run.player.movementInput.jump);
                        assertEquals(manual, run.settings.keyBindJump.pressed);
                        if (guard) {
                            assertTrue("real projected foot collisions acquire the Scaffold guard", run.player.movementInput.sneak);
                            assertFalse("guard does not press the physical key", run.settings.keyBindSneak.pressed);
                        }
                        if ("sneak".equals(input)) assertTrue(run.player.movementInput.sneak);
                        if ("item".equals(input)) assertEquals(.2F, MovementInput.moveForward, 1e-7);
                        double beforeX = run.player.motionX, beforeY = run.player.motionY, beforeZ = run.player.motionZ;
                        run.player.moveFlying(MovementInput.moveStrafe * .98F, MovementInput.moveForward * .98F, .1F);
                        assertNotNull(movement[0]);
                        boolean boost = "sprint".equals(input) || "manual jump".equals(input);
                        assertEquals("the final current input decides eligibility: " + input,
                                boost ? .104F : .1F, movement[0].getFriction(), 1e-7);
                        nativeMotion(run.player, movement[0], beforeX, beforeY, beforeZ);
                        assertEquals(manual, run.player.movementInput.jump);
                        assertEquals(manual, run.settings.keyBindJump.pressed);
                        assertEquals(1F, run.f.mc.timer.timerSpeed, 0);
                    });
                } finally {
                    Pulsar.BUS.unsubscribe(capture);
                    speed.setToggled(false);
                }
            }
        }
    }

    @Test public void airborneTailExpiresAndLandingWallAndDirectionChangesStartFromTheirOwnLimits() throws Exception {
        try (Run run = new Run()) {
            run.warm();
            run.base.f.player.onGround = false;
            run.base.f.player.motionY = .2;
            float previous = Float.POSITIVE_INFINITY;
            for (int step = 0; step < 6; step++) {
                run.zeroHorizontal();
                PlayerMoveUpdateEvent event = run.tick(0, .98F, .02F);
                float expected = step < 4 ? .02F + .002F * (4 - step) / 4F : .02F;
                assertEquals(expected, event.getFriction(), 1e-7);
                assertTrue(event.getFriction() <= previous);
                previous = event.getFriction();
                nativeMotion(run.base.f.player, event, 0, .2, 0);
            }
            run.base.f.player.onGround = true;
            run.base.f.player.motionY = 0;
            run.zeroHorizontal();
            assertEquals("landing cannot reuse mature ground credit", .104F,
                    run.tick(0, .98F, .1F).getFriction(), 1e-7);
            run.base.f.player.isCollidedHorizontally = true;
            for (int step = 0; step < 3; step++) {
                run.zeroHorizontal();
                assertEquals("ground wall acceleration is bounded separately", .106F,
                        run.tick(0, .98F, .1F).getFriction(), 1e-7);
            }
            run.base.f.player.isCollidedHorizontally = false;
            run.zeroHorizontal();
            assertEquals("a reversal cannot spend the previous direction's ramp", .1F,
                    run.tick(0, -.98F, .1F).getFriction(), 0);
            run.zeroHorizontal();
            assertEquals(.104F, run.tick(0, -.98F, .1F).getFriction(), 1e-7);
        }
        try (Run run = new Run()) {
            run.base.f.player.onGround = false;
            assertEquals("no airborne boost without a prior actual ground run", .02F,
                    run.tick(0, .98F, .02F).getFriction(), 0);
        }
    }

    @Test public void actualS12PreservesImpulseAndBlocksRecoveryWhileCancelledS12KeepsTheRamp() throws Exception {
        try (Run run = new Run()) {
            run.warm();
            run.base.f.player.motionX = .02;
            run.base.f.player.motionY = .03;
            run.base.f.player.motionZ = -.04;
            Listener<KnockbackEvent> cancel = new Listener<>(KnockbackEvent.class, event -> {
                if (event.isPre()) event.cancel();
            });
            Pulsar.BUS.subscribe(cancel);
            try { run.velocity(.2, .3, -.1); }
            finally { Pulsar.BUS.unsubscribe(cancel); }
            assertEquals(.02, run.base.f.player.motionX, 0);
            assertEquals(.03, run.base.f.player.motionY, 0);
            assertEquals(-.04, run.base.f.player.motionZ, 0);
            assertFalse(run.base.f.network.getClientPackets().isRecoveringLocalImpulse(run.base.f.player));
            PlayerMoveUpdateEvent afterCancelled = run.tick(0, .98F, .1F);
            assertEquals("cancelled knockback never supplied an applied impulse", .112F,
                    afterCancelled.getFriction(), 1e-7);
            nativeMotion(run.base.f.player, afterCancelled, .02, .03, -.04);
            run.velocity(.2, .3, -.1);
            long appliedAt = run.base.time.get();
            assertEquals(.2, run.base.f.player.motionX, 1e-7);
            assertEquals(.3, run.base.f.player.motionY, 1e-7);
            assertEquals(-.1, run.base.f.player.motionZ, 1e-7);
            run.base.f.player.onGround = false;
            PlayerMoveUpdateEvent impulse = run.tick(0, .98F, .02F);
            assertEquals(.02F, impulse.getFriction(), 0);
            nativeMotion(run.base.f.player, impulse, .2, .3, -.1);
            run.base.f.player.onGround = true;
            run.base.f.player.motionY = 0;
            while (run.base.time.get() - appliedAt < 450_000_000L) {
                run.zeroHorizontal();
                assertEquals("ground contact cannot shorten the 500 ms impulse window", .1F,
                        run.tick(0, .98F, .1F).getFriction(), 0);
            }
            run.base.time.set(appliedAt + 550_000_000L);
            run.base.f.player.hurtTime = 1;
            run.zeroHorizontal();
            assertEquals(.1F, run.tick(0, .98F, .1F).getFriction(), 0);
            assertTrue(run.base.f.network.getClientPackets().isRecoveringLocalImpulse(run.base.f.player));
            run.base.f.player.hurtTime = 0;
            run.base.f.player.onGround = false;
            run.base.f.player.motionY = -.01;
            run.zeroHorizontal();
            assertEquals(.02F, run.tick(0, .98F, .02F).getFriction(), 0);
            assertTrue(run.base.f.network.getClientPackets().isRecoveringLocalImpulse(run.base.f.player));
            run.base.f.player.onGround = true;
            run.base.f.player.motionY = .05;
            run.zeroHorizontal();
            assertEquals("upward velocity remains under impulse authority", .1F,
                    run.tick(0, .98F, .1F).getFriction(), 0);
            assertTrue(run.base.f.network.getClientPackets().isRecoveringLocalImpulse(run.base.f.player));
            run.base.f.player.motionY = 0;
            run.zeroHorizontal();
            assertEquals("actual recovery starts a fresh ramp", .104F,
                    run.tick(0, .98F, .1F).getFriction(), 1e-7);
        }
    }

    @Test public void appliedS08PauseSurvivesInitialObservationSuspensionAndSettingsReset() throws Exception {
        for (String variant : new String[]{"ordinary", "initial", "focus", "gui", "settings"}) {
            try (Run run = new Run()) {
                if (!"initial".equals(variant)) run.warm();
                long appliedAt = run.base.time.get();
                Field configClient = ScaffoldOwnerContinuityTest.field(net.minecraft.src.Config.class, "minecraft");
                Object previousConfigClient = configClient.get(null);
                net.minecraft.client.renderer.RenderGlobal previousRenderer = run.base.f.mc.renderGlobal;
                run.base.f.mc.renderGlobal = RotationBehaviorHookTest.bare(net.minecraft.client.renderer.RenderGlobal.class);
                configClient.set(null, run.base.f.mc);
                try {
                    run.base.f.channel.writeInbound(new S08PacketPlayerPosLook(3.25, 64, 2, 45, 10,
                            Collections.<S08PacketPlayerPosLook.EnumFlags>emptySet()));
                } finally {
                    configClient.set(null, previousConfigClient);
                    run.base.f.mc.renderGlobal = previousRenderer;
                }
                assertEquals(3.25, run.base.f.player.posX, 0);
                assertEquals(64, run.base.f.player.posY, 0);
                assertEquals(2, run.base.f.player.posZ, 0);
                assertEquals(45, run.base.f.player.rotationYaw, 0);
                boolean acknowledged = false;
                Object outbound;
                while ((outbound = run.base.f.channel.readOutbound()) != null) {
                    if (outbound instanceof net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook)
                        acknowledged = true;
                }
                assertTrue("the native correction handler sent its normal acknowledgement", acknowledged);
                for (int step = 0; step < 9; step++) {
                    if (step == 0 && "focus".equals(variant)) run.base.f.mc.inGameHasFocus = false;
                    if (step == 0 && "gui".equals(variant)) run.base.f.mc.currentScreen = new GuiScreen() { };
                    if (step == 1 && "settings".equals(variant)) {
                        run.speed.getBooleanSetting("Air Speed").setValue(true);
                        run.speed.getBooleanSetting("Air Speed").reset();
                    }
                    run.zeroHorizontal();
                    assertEquals("ordinary or suspended steps cannot shorten S08 recovery: " + variant, .1F,
                            run.tick(0, .98F, .1F).getFriction(), 0);
                    run.base.f.mc.inGameHasFocus = true;
                    run.base.f.mc.currentScreen = null;
                }
                assertEquals(appliedAt + 450_000_000L, run.base.time.get());
                run.zeroHorizontal();
                assertEquals("the first eligible deadline starts cold: " + variant, .104F,
                        run.tick(0, .98F, .1F).getFriction(), 1e-7);
                assertEquals(appliedAt + 500_000_000L, run.base.time.get());
            }
        }
    }

    @Test public void lifecycleChangesStartColdPreserveForeignOverridesAndRejectReentrantObservationReplacement()
            throws Exception {
        for (String boundary : new String[]{"focus", "gui", "world", "connection", "disable", "settings",
                "Intave Momentum", "Intave Boat"}) {
            try (Run run = new Run()) {
                run.warm();
                run.base.f.mc.timer.timerSpeed = 1.17F;
                run.base.f.player.speedInAir = .071F;
                run.base.f.player.movementInput.jump = true;
                run.base.f.mc.gameSettings.keyBindJump.pressed = true;
                if ("focus".equals(boundary)) run.base.f.mc.inGameHasFocus = false;
                else if ("gui".equals(boundary)) run.base.f.mc.currentScreen = new GuiScreen() { };
                else if ("world".equals(boundary)) run.world(RotationBehaviorHookTest.bare(IntaveBoatSpeedTest.Scene.class));
                else if ("connection".equals(boundary)) run.reconnect();
                else if ("disable".equals(boundary)) run.speed.setToggled(false);
                else if ("settings".equals(boundary)) {
                    run.speed.getBooleanSetting("Air Speed").setValue(true);
                    run.speed.getBooleanSetting("Air Speed").reset();
                } else run.speed.getModeSetting("Mode").setValue(boundary);
                foreignOverrides(run);
                boolean inactive = "focus".equals(boundary) || "gui".equals(boundary)
                        || "disable".equals(boundary) || boundary.startsWith("Intave");
                run.zeroHorizontal();
                assertEquals("the changed lifetime cannot reuse mature credit: " + boundary,
                        inactive ? .1F : .104F, run.tick(0, .98F, .1F).getFriction(), 1e-7);
                foreignOverrides(run);
                if (inactive) {
                    run.base.f.mc.inGameHasFocus = true;
                    run.base.f.mc.currentScreen = null;
                    if ("disable".equals(boundary)) run.speed.setToggled(true);
                    if (boundary.startsWith("Intave")) run.speed.getModeSetting("Mode").setValue(MODE);
                    run.zeroHorizontal();
                    assertEquals("resuming V2 starts cold: " + boundary, .104F,
                            run.tick(0, .98F, .1F).getFriction(), 1e-7);
                    foreignOverrides(run);
                }
            }
        }
        for (String callback : new String[]{"clock", "world"}) {
            try (Run run = new Run()) {
                CallbackScene world = RotationBehaviorHookTest.bare(CallbackScene.class);
                run.world(world);
                run.warm();
                RuntimeObservation original = run.base.f.runtime.getObservation();
                @SuppressWarnings("unchecked")
                Supplier<RuntimeObservation.SceneSample> source = (Supplier<RuntimeObservation.SceneSample>)
                        ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source").get(original);
                RuntimeObservation replacement = new RuntimeObservation(source);
                LongSupplier clock = (LongSupplier) ScaffoldOwnerContinuityTest.field(Speed.class, "momentumClock").get(run.speed);
                boolean[] replaced = {false};
                Runnable replace = () -> {
                    if (replaced[0]) return;
                    replaced[0] = true;
                    try { ScaffoldClientFlowTest.set(ClientRuntime.class, run.base.f.runtime, "observation", replacement); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                };
                try {
                    if ("clock".equals(callback)) {
                        ScaffoldClientFlowTest.set(Speed.class, run.speed, "momentumClock",
                                (LongSupplier) () -> { replace.run(); return run.base.time.get(); });
                    } else world.onRead = replace;
                    run.zeroHorizontal();
                    assertEquals("a callback replaced canonical observation: " + callback, .1F,
                            run.tick(0, .98F, .1F).getFriction(), 0);
                    assertTrue("the real callback was reached", replaced[0]);
                    assertSame(run.base.f.player, run.base.f.mc.thePlayer);
                    assertSame(world, run.base.f.mc.theWorld);
                    assertSame(replacement, run.base.f.runtime.getObservation());
                    run.zeroHorizontal();
                    assertEquals("the replacement observer needs its own ordinary body", .104F,
                            run.tick(0, .98F, .1F).getFriction(), 1e-7);
                } finally {
                    world.onRead = null;
                    ScaffoldClientFlowTest.set(Speed.class, run.speed, "momentumClock", clock);
                    ScaffoldClientFlowTest.set(ClientRuntime.class, run.base.f.runtime, "observation", original);
                }
            }
        }
    }

    private static void foreignOverrides(Run run) {
        assertEquals("V2 has no timer ownership", 1.17F, run.base.f.mc.timer.timerSpeed, 0);
        assertEquals("V2 has no speedInAir ownership", .071F, run.base.f.player.speedInAir, 0);
        assertTrue("the physical jump key remains held", run.base.f.mc.gameSettings.keyBindJump.pressed);
        assertTrue("the manual jump input remains held", run.base.f.player.movementInput.jump);
        assertFalse(run.base.f.mc.gameSettings.keyBindSneak.pressed);
    }

    private static void nativeMotion(EntityPlayerSP player, PlayerMoveUpdateEvent event,
                                     double beforeX, double beforeY, double beforeZ) {
        float length = MathHelper.sqrt_float(event.getStrafe() * event.getStrafe()
                + event.getForward() * event.getForward());
        float scale = event.getFriction() / Math.max(1, length);
        float strafe = event.getStrafe() * scale, forward = event.getForward() * scale;
        float radians = event.getYaw() * (float) Math.PI / 180F;
        assertEquals(beforeX + strafe * MathHelper.cos(radians) - forward * MathHelper.sin(radians),
                player.motionX, 2e-7);
        assertEquals(beforeZ + forward * MathHelper.cos(radians) + strafe * MathHelper.sin(radians),
                player.motionZ, 2e-7);
        assertEquals("the final friction consumer preserves vertical motion", beforeY, player.motionY, 0);
    }
}
}
