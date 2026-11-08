package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import static org.junit.Assert.*;

/** Native PRE/C03/POST placement and the final fresh-input jump request, with controlled time. */
public class AdaptiveBridgeCadenceFlowTest {
    private static final long TICK = 50_000_000L;
    private static final BlockPos FIRST = new BlockPos(0, 63, 0);

    private static final class PacketHook implements Subscriber, AutoCloseable {
        @Subscribe final Listener<EventPacket> listener;
        PacketHook(Consumer<EventPacket> action) {
            listener = new Listener<>((EventPacket event) -> action.accept(event));
            Pulsar.BUS.subscribe(this);
        }
        @Override public void close() { Pulsar.BUS.unsubscribe(this); }
    }

    private static final class Run implements AutoCloseable {
        final AdaptiveBridgeInputSafetyFlowTest.Run input = new AdaptiveBridgeInputSafetyFlowTest.Run();
        Run(String rotation, boolean autoJump) throws Exception {
            input.adaptive("Smart");
            input.f.scaffold.getModeSetting("Bridge Rotation Profile").setValue(rotation);
            input.f.scaffold.getModeSetting("Bridge Rotation Speed").setValue("Fast");
            input.f.scaffold.getNumberSetting("Bridge Rotation Variation").setValue(0D);
            input.f.scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(autoJump);
            input.f.scaffold.getNumberSetting("Godbridge Jump After Blocks").setValue(8D);
            input.f.scaffold.getBooleanSetting("Place Debug").setValue(false);
            input.f.scaffold.getModeSetting("Click Profile").setValue("Basic");
            input.f.scaffold.getModeSetting("Place Interpolation").setValue("Stable");
            cps(20, 20, 0);
            ScaffoldClientFlowTest.set(Scaffold.class, input.f.scaffold, "bridgePlacementClock",
                    (LongSupplier) input.scene.motionTime::get);
            ScaffoldClientFlowTest.set(ScaffoldPlacementService.class,
                    input.f.runtime.getScaffoldPlacementService(), "clock",
                    (LongSupplier) () -> input.scene.motionTime.get() / 1_000_000L);
            input.f.runtime.getScaffoldPlacementService().reset();
            input.position(.25, .5);
            input.player.rotationYaw = 90;
            input.settings.keyBindBack.pressed = true;
        }
        void cps(double min, double max, double variation) {
            input.f.scaffold.getNumberSetting("Place Min CPS").setValue(min);
            input.f.scaffold.getNumberSetting("Place Max CPS").setValue(max);
            input.f.scaffold.getNumberSetting("Variation Strength").setValue(variation);
        }
        void enable() { input.f.scaffold.onEnable(); }
        long now() { return input.scene.motionTime.get(); }
        List<Object> walk() { return walkAfter(TICK, () -> { }); }
        List<Object> walkAfter(long elapsed, Runnable afterInput) {
            assertTrue(elapsed >= 0);
            // Run.step adds its normal tick before any production consumer reads the clock.
            input.scene.motionTime.addAndGet(elapsed - TICK);
            input.step(true, afterInput);
            return input.drain();
        }
        void fresh(Runnable afterInput) { input.step(false, afterInput); }
        @Override public void close() throws Exception { input.close(); }
    }
    private static final class Attempts implements AutoCloseable {
        final Run run;
        final List<Long> times = new ArrayList<>();
        final PacketHook hook;
        boolean repeating;
        Attempts(Run run, boolean reject, boolean duplicatePost) {
            this.run = run;
            hook = new PacketHook(event -> {
                if (!event.isOutbound() || !event.isPre()
                        || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                times.add(run.now());
                if (duplicatePost && !repeating) {
                    repeating = true;
                    try { repeatPost(run); }
                    finally { repeating = false; }
                }
                if (reject) event.cancel();
            });
        }
        long next() {
            int before = times.size();
            for (int tick = 0; tick < 160 && times.size() == before; tick++) run.walk();
            assertEquals("one actual controller attempt in a bounded acquisition", before + 1, times.size());
            return times.get(before);
        }
        @Override public void close() { hook.close(); }
    }

    private static void repeatPost(Run run) {
        AdaptiveBridgeInputSafetyFlowTest.InputPlayer player = run.input.player;
        EventMotion post = new EventMotion(player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch, player.onGround);
        post.setEventPhase(EventPhase.POST);
        post.setAccepted(true);
        for (int copy = 0; copy < 5; copy++) Pulsar.BUS.post(post);
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static void exactNextInterval(Run run, Attempts attempts, long interval) {
        int before = attempts.times.size();
        long previous = attempts.times.get(before - 1);
        long deadline = previous + interval;
        // Keep the ordinary motion consumers active; inactivity has its own rotation expiry.
        while (deadline - run.now() > TICK) {
            run.walk();
            assertEquals("intermediate physical opportunities cannot spend the interval early",
                    before, attempts.times.size());
        }
        long remaining = deadline - run.now();
        assertTrue("the deadline is still ahead", remaining > 0);
        run.walkAfter(remaining - 1, () -> { });
        assertEquals("no controller attempt before the deadline", before, attempts.times.size());
        run.walkAfter(1, () -> { });
        assertEquals("one controller attempt at the next deadline", before + 1, attempts.times.size());
        assertEquals(deadline, attempts.times.get(before).longValue());
    }

    @Test public void everyClickProfileAndInterpolationUsesTheControlledDeadlineWhenVariationIsZero()
            throws Exception {
        for (String profile : new String[]{"Basic", "Smoothstep", "Perlin"}) {
            for (String interpolation : new String[]{"Stable", "Smooth", "Dynamic"}) {
                try (Run run = new Run("Godbridge", false); Attempts attempts = new Attempts(run, true, false)) {
                    run.cps(4, 6, 0);
                    run.input.f.scaffold.getModeSetting("Click Profile").setValue(profile);
                    run.input.f.scaffold.getModeSetting("Place Interpolation").setValue(interpolation);
                    run.enable();
                    attempts.next();
                    exactNextInterval(run, attempts, 200_000_000L);
                    exactNextInterval(run, attempts, 200_000_000L);
                    assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                    assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
                    assertEquals("debug-off produces no placement messages", 0, run.input.player.diagnosticMessages);
                }
            }
        }
    }

    @Test public void varyingProfilesNeverExceedTheirMaximumCpsAtActualControllerAttempts() throws Exception {
        for (String profile : new String[]{"Basic", "Smoothstep", "Perlin"}) {
            try (Run run = new Run("Godbridge", false); Attempts attempts = new Attempts(run, true, false)) {
                run.cps(4, 8, 1);
                run.input.f.scaffold.getModeSetting("Click Profile").setValue(profile);
                run.input.f.scaffold.getModeSetting("Place Interpolation").setValue("Smooth");
                run.enable();
                for (int attempt = 0; attempt < 8; attempt++) attempts.next();
                for (int attempt = 1; attempt < attempts.times.size(); attempt++) {
                    assertTrue("maximum CPS is respected by native attempts for " + profile,
                            attempts.times.get(attempt) - attempts.times.get(attempt - 1) >= 125_000_000L);
                }
                // Other gates and the physical input opportunities may postpone an otherwise due interval.
                assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
            }
        }
    }

    @Test public void deniedC08AndReentrantDuplicatePostConsumeOnlyOneCadenceInterval() throws Exception {
        try (Run run = new Run("Godbridge", false); Attempts attempts = new Attempts(run, true, true)) {
            run.cps(2, 2, 0);
            run.enable();
            attempts.next();
            assertEquals(1, attempts.times.size());
            assertEquals(Blocks.air, run.input.scene.getBlockState(FIRST).getBlock());
            assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
            assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
            exactNextInterval(run, attempts, 500_000_000L);
            assertEquals(2, attempts.times.size());
            assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
        }
    }

    @Test public void aLongPauseCreatesNoCatchupBurstAndSchedulesFromTheActualAttempt() throws Exception {
        try (Run run = new Run("Godbridge", false); Attempts attempts = new Attempts(run, true, true)) {
            run.cps(4, 4, 0);
            run.enable();
            attempts.next();
            int before = attempts.times.size();
            run.walkAfter(5_000_000_000L, () -> { });
            assertTrue("one physical opportunity cannot repay missed intervals", attempts.times.size() <= before + 1);
            if (attempts.times.size() == before) attempts.next();
            assertEquals(before + 1, attempts.times.size());
            exactNextInterval(run, attempts, 250_000_000L);
            assertEquals(64, run.input.player.inventory.mainInventory[3].stackSize);
        }
    }

    @Test public void existingPlaceDelayRemainsAnAdditionalGateAfterCadenceIsDue() throws Exception {
        try (Run run = new Run("Godbridge", false); Attempts attempts = new Attempts(run, true, false)) {
            run.input.f.scaffold.getNumberSetting("Place Delay").setValue(200D);
            run.enable();
            long first = attempts.next();
            for (int tick = 0; tick < 3; tick++) {
                run.walk();
                assertEquals("legacy delay still blocks a due cadence", 1, attempts.times.size());
            }
            run.walk();
            assertEquals(2, attempts.times.size());
            assertEquals(first + 200_000_000L, attempts.times.get(1).longValue());
            assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
        }
    }

    @Test public void eightAppliedBlocksCreateOneSafeFreshJumpOnlyForOptedInGodbridge() throws Exception {
        for (int choice = 0; choice < 4; choice++) {
            String profile = choice == 2 ? "Adaptive" : "Godbridge";
            boolean enabled = choice != 1;
            try (Run run = new Run(profile, enabled)) {
                boolean nonSilent = choice == 3;
                if (nonSilent) {
                    run.input.f.scaffold.getBooleanSetting("Item Spoof").setValue(false);
                    assertEquals("the first native placement must change the selected slot", 0,
                            run.input.player.inventory.currentItem);
                }
                run.enable();
                placeEight(run);
                if (nonSilent) assertEquals("a successful non-silent placement keeps the block slot", 3,
                        run.input.player.inventory.currentItem);
                boolean expectedJump = choice == 0 || nonSilent;
                run.fresh(() -> {
                    assertEquals("profile and opt-in control the automatic request", expectedJump,
                            run.input.player.movementInput.jump);
                    assertFalse(run.input.settings.keyBindJump.pressed);
                    assertFalse(run.input.player.movementInput.sneak);
                    assertEquals(8, run.input.f.scaffold.bridgePlacementCount());
                    // The fixture reaches the real input boundary, before normal vanilla jump physics.
                    assertEquals(0, run.input.player.motionY, 0);
                });
                run.fresh(() -> {
                    assertFalse("one group of eight cannot request another jump", run.input.player.movementInput.jump);
                    assertFalse(run.input.settings.keyBindJump.pressed);
                });
            }
        }
    }

    @Test public void acceptedC08WithoutOurAppliedTargetNeverAddsBlockOrJumpCredit() throws Exception {
        for (boolean ignoreLocalWrite : new boolean[]{false, true}) {
            try (Run run = new Run("Godbridge", true)) {
                if (ignoreLocalWrite) {
                    // World reports a successful set call while the local target remains air.
                    // ItemBlock therefore returns true and spends a block; that still is not an applied target.
                    run.input.scene.blocks = new LinkedHashMap<BlockPos, IBlockState>(run.input.scene.blocks) {
                        @Override public IBlockState put(BlockPos position, IBlockState state) {
                            if (FIRST.equals(position)) return get(position);
                            return super.put(position, state);
                        }
                    };
                }
                int[] accepted = {0};
                try (PacketHook hook = new PacketHook(event -> {
                    if (!event.isOutbound() || !event.isPost()
                            || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;
                    accepted[0]++;
                    if (!ignoreLocalWrite) {
                        // This accepted transport is followed by a changed world before our ItemBlock applies.
                        run.input.scene.blocks.put(FIRST, Blocks.obsidian.getDefaultState());
                    }
                })) {
                    run.enable();
                    long delivered = 0;
                    for (int tick = 0; tick < 320 && accepted[0] < 8; tick++) {
                        if (!ignoreLocalWrite) run.input.scene.blocks.remove(FIRST);
                        List<Object> packets = run.walk();
                        delivered += count(packets, C08PacketPlayerBlockPlacement.class);
                        assertEquals("a transport receipt is not an applied block", 0,
                                run.input.f.scaffold.bridgePlacementCount());
                        assertFalse(run.input.player.movementInput.jump);
                    }
                    assertEquals(8, accepted[0]);
                    assertEquals("these are actual transported packets", 8, delivered);
                    assertEquals(ignoreLocalWrite ? 56 : 64, run.input.player.inventory.mainInventory[3].stackSize);
                    assertEquals(ignoreLocalWrite ? Blocks.air : Blocks.obsidian,
                            run.input.scene.getBlockState(FIRST).getBlock());
                    run.input.scene.blocks = new LinkedHashMap<>(run.input.scene.blocks);
                    run.input.scene.blocks.put(FIRST, Blocks.stone.getDefaultState());
                    run.fresh(() -> {
                        assertFalse("eight receipts cannot earn an automatic jump", run.input.player.movementInput.jump);
                        assertFalse(run.input.player.movementInput.sneak);
                        assertFalse(run.input.settings.keyBindJump.pressed);
                        assertEquals(0, run.input.f.scaffold.bridgePlacementCount());
                    });
                }
            }
        }
    }

    @Test public void unsafeManualAirborneAndForeignInputsCannotTriggerThePendingAutomaticJump() throws Exception {
        for (String blocker : new String[]{"edge", "sneak", "foreign owner", "manual jump", "using item",
                "airborne", "stack callback", "slot callback", "flight toggle"}) {
            try (Run run = new Run("Godbridge", true)) {
                run.enable();
                placeEight(run);
                if ("edge".equals(blocker)) {
                    run.input.position(7.9, .5);
                    run.input.player.motionX = .4;
                }
                if ("sneak".equals(blocker)) run.input.settings.keyBindSneak.pressed = true;
                if ("manual jump".equals(blocker)) run.input.settings.keyBindJump.pressed = true;
                if ("using item".equals(blocker)) run.input.player.usingItem = true;
                if ("airborne".equals(blocker)) run.input.player.onGround = false;
                if ("flight toggle".equals(blocker)) {
                    run.input.player.capabilities.allowFlying = true;
                    run.input.player.capabilities.isFlying = false;
                    ScaffoldClientFlowTest.set(EntityPlayer.class, run.input.player, "flyToggleTimer", 3);
                }
                int[] inputCallbacks = {0};
                Listener<MovementInputEvent> change = new Listener<>((MovementInputEvent event) -> {
                    inputCallbacks[0]++;
                    if ("stack callback".equals(blocker)) {
                        run.input.player.inventory.mainInventory[3] = run.input.player.inventory.mainInventory[3].copy();
                    }
                    if ("slot callback".equals(blocker)) {
                        run.input.player.inventory.currentItem = (run.input.player.inventory.currentItem + 1) % 9;
                    }
                });
                Listener<EventRotation> foreign = new Listener<>((EventRotation event) -> {
                    if ("foreign owner".equals(blocker)) {
                        run.input.f.runtime.getRotationManager().request("cadence-foreign", 100, 45, 20);
                    }
                });
                Pulsar.BUS.subscribe(change);
                Pulsar.BUS.subscribe(foreign);
                try {
                    run.fresh(() -> {
                        assertEquals("physical Jump remains the user's input: " + blocker,
                                "manual jump".equals(blocker), run.input.player.movementInput.jump);
                        assertEquals("key binding is not written: " + blocker,
                                "manual jump".equals(blocker), run.input.settings.keyBindJump.pressed);
                        if ("edge".equals(blocker)) {
                            assertTrue("the real unsafe floor path requests sneak", run.input.player.movementInput.sneak);
                        }
                        if ("sneak".equals(blocker)) {
                            assertTrue(run.input.settings.keyBindSneak.pressed);
                            assertTrue(run.input.player.movementInput.sneak);
                        }
                        if ("foreign owner".equals(blocker)) {
                            assertNotNull(run.input.f.runtime.getRotationManager().getPreparedMovement());
                            assertEquals("cadence-foreign",
                                    run.input.f.runtime.getRotationManager().getPreparedMovement().source);
                        }
                        assertFalse("automatic Jump cannot toggle vanilla flight", run.input.player.capabilities.isFlying);
                        assertEquals("the input hook does not apply jump velocity", 0, run.input.player.motionY, 0);
                    });
                    assertEquals("the mutation happened in the real fresh-input event", 1, inputCallbacks[0]);
                } finally {
                    Pulsar.BUS.unsubscribe(foreign);
                    Pulsar.BUS.unsubscribe(change);
                }
                if ("manual jump".equals(blocker)) {
                    run.input.settings.keyBindJump.pressed = false;
                    run.fresh(() -> {
                        assertFalse("manual Jump discarded the pending automatic cycle", run.input.player.movementInput.jump);
                        assertFalse(run.input.settings.keyBindJump.pressed);
                    });
                }
            }
        }
    }

    @Test public void everySceneCallbackBeforeAutomaticJumpRechecksStackAndFlightPermission() throws Exception {
        final int callbacks;
        try (Run run = new Run("Godbridge", true)) {
            run.enable();
            placeEight(run);
            callbacks = freshWithContextMutation(run, 0, () -> { });
            assertTrue("the positive native jump traverses at least one scene callback", callbacks > 0);
        }
        for (boolean flight : new boolean[]{false, true}) {
            for (int ordinal = 1; ordinal <= callbacks; ordinal++) {
                try (Run run = new Run("Godbridge", true)) {
                    run.enable();
                    placeEight(run);
                    freshWithContextMutation(run, ordinal, () -> {
                        if (flight) {
                            run.input.player.capabilities.allowFlying = true;
                            try {
                                ScaffoldClientFlowTest.set(EntityPlayer.class, run.input.player, "flyToggleTimer", 3);
                            } catch (Exception failure) { throw new AssertionError(failure); }
                        } else {
                            run.input.player.inventory.mainInventory[3] =
                                    run.input.player.inventory.mainInventory[3].copy();
                        }
                    });
                }
            }
        }
    }

    /** Arms only after physical input was sampled, and never mutates after the positive jump boundary. */
    private static int freshWithContextMutation(Run run, int atCallback, Runnable mutation) throws Exception {
        RuntimeObservation observation = run.input.f.runtime.getObservation();
        @SuppressWarnings("unchecked")
        Supplier<RuntimeObservation.SceneSample> original = (Supplier<RuntimeObservation.SceneSample>)
                ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source").get(observation);
        boolean[] armed = {false}, mutated = {false};
        int[] calls = {0};
        Supplier<RuntimeObservation.SceneSample> wrapped = () -> {
            if (armed[0] && !run.input.player.movementInput.jump) {
                int ordinal = ++calls[0];
                if (atCallback > 0 && ordinal == atCallback) {
                    mutation.run();
                    mutated[0] = true;
                }
            }
            return original.get();
        };
        Listener<MovementInputEvent> arm = new Listener<>((MovementInputEvent event) -> armed[0] = true);
        ScaffoldClientFlowTest.set(RuntimeObservation.class, observation, "source", wrapped);
        try {
            Pulsar.BUS.subscribe(arm);
            run.fresh(() -> armed[0] = false);
            if (atCallback > 0) {
                assertTrue("the real scene supplier reached mutation callback " + atCallback, mutated[0]);
            }
            assertEquals("only an unchanged positive context can request Jump, callback " + atCallback,
                    atCallback == 0, run.input.player.movementInput.jump);
            assertFalse("the automatic request never writes the key binding", run.input.settings.keyBindJump.pressed);
            assertFalse("a late scene callback cannot turn the automatic request into flight",
                    run.input.player.capabilities.isFlying);
            return calls[0];
        } finally {
            armed[0] = false;
            Pulsar.BUS.unsubscribe(arm);
            ScaffoldClientFlowTest.set(RuntimeObservation.class, observation, "source", original);
        }
    }

    private static void placeEight(Run run) {
        for (int block = 0; block < 8; block++) {
            BlockPos destination = new BlockPos(block, 63, 0);
            run.input.position(block + .25, .5);
            boolean applied = false;
            for (int tick = 0; tick < 160 && !applied; tick++) {
                List<Object> packets = run.walkAfter(TICK, () -> {
                    assertFalse("fewer than eight applied blocks cannot request an automatic jump",
                            run.input.player.movementInput.jump);
                    assertFalse(run.input.settings.keyBindJump.pressed);
                });
                if (run.input.scene.getBlockState(destination).getBlock() == Blocks.stone) {
                    assertEquals(1, count(packets, C08PacketPlayerBlockPlacement.class));
                    int movement = -1, placement = -1;
                    for (int i = 0; i < packets.size(); i++) {
                        if (packets.get(i) instanceof C03PacketPlayer) movement = i;
                        if (packets.get(i) instanceof C08PacketPlayerBlockPlacement) placement = i;
                    }
                    assertTrue("the native C08 follows the accepted movement packet",
                            movement >= 0 && movement < placement);
                    assertEquals(63 - block, run.input.player.inventory.mainInventory[3].stackSize);
                    assertEquals(block + 1, run.input.f.scaffold.bridgePlacementCount());
                    applied = true;
                }
            }
            assertTrue("native placement must reach block " + block, applied);
        }
        assertEquals(8, run.input.f.scaffold.bridgePlacementCount());
        assertEquals(56, run.input.player.inventory.mainInventory[3].stackSize);
    }
}
