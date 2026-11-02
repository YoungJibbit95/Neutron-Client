package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.combat.CombatDiagnosticTrace;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** H2/T-04: real Aura, swing, controller and selected transport; synthetic world only. */
public class CombatSequenceWave2Checks {
    static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture fixture = new RotationBehaviorHookTest.Fixture();
        final ScaffoldClientFlowTest.Scene scene = ScaffoldClientFlowTest.prepare(fixture, .5, .48);
        final RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        final CombatDiagnostics diagnostics = new CombatDiagnostics();
        final CombatDiagnosticTrace history;
        final RuntimeObservation observation = fixture.runtime.getObservation();

        @SuppressWarnings("unchecked") Run(boolean traceEnabled) throws Exception {
            try {
                fixture.mc.gameSettings.keyBindUseItem = new KeyBinding("synthetic.use", 0, "synthetic");
                fixture.mc.gameSettings.keyBindAttack = new KeyBinding("synthetic.attack", 0, "synthetic");
                fixture.player.sendQueue.clientWorldController = scene;
                target.worldObj = scene; target.setEntityId(9);
                target.posX = .5; target.posY = 64; target.posZ = 2;
                target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 1.7, .8, 65.8, 2.3));
                ScaffoldOwnerContinuityTest.field(World.class, "loadedEntityList").set(scene,
                        new ArrayList<>(Collections.singletonList(target)));
                ScaffoldOwnerContinuityTest.field(PlayerControllerMP.class, "currentGameType").set(fixture.mc.playerController,
                        WorldSettings.GameType.SPECTATOR);
                ScaffoldOwnerContinuityTest.field(ClientRuntime.class, "attackScheduler").set(fixture.runtime,
                        new AttackScheduler(() -> 1000L, (min, max) -> 10));
                fixture.aura.getModeSetting("Randomization Style").setValue("Aus");
                fixture.aura.getNumberSetting("Jitter").setValue(0D);
                Map<Class<? extends Module>, Module> registry = (Map<Class<? extends Module>, Module>)
                        ScaffoldOwnerContinuityTest.field(ModuleManager.class, "modulesByClass").get(Pulsar.INSTANCE.getMm());
                registry.put(CombatDiagnostics.class, diagnostics);
                diagnostics.getBooleanSetting("Console Attack Log").setValue(false);
                history = (CombatDiagnosticTrace) ScaffoldOwnerContinuityTest.field(CombatDiagnostics.class, "history").get(diagnostics);
                if (traceEnabled) diagnostics.setToggled(true);
                if (traceEnabled) {
                    assertTrue("fixture enabled diagnostic history", history.isRecording());
                    assertSame("fixture registered the diagnostic instance", diagnostics,
                            Pulsar.INSTANCE.getMm().getModule(CombatDiagnostics.class));
                    java.lang.reflect.Method active = CombatDiagnostics.class.getDeclaredMethod("active");
                    active.setAccessible(true);
                    java.lang.reflect.Method inWorld = CombatDiagnostics.class.getDeclaredMethod("inWorld");
                    inWorld.setAccessible(true);
                    Object moduleClient = ScaffoldOwnerContinuityTest.field(Module.class, "mc").get(null);
                    assertSame("fixture singleton was installed", fixture.mc, net.minecraft.client.Minecraft.getMinecraft());
                    assertSame("fixture static-final client was installed by its existing Unsafe helper", fixture.mc, moduleClient);
                    assertSame("diagnostic fixture must be active before gameplay; inWorld=" + inWorld.invoke(diagnostics)
                                    + ", currentPlayer=" + (fixture.mc.thePlayer != null)
                                    + ", currentWorld=" + (fixture.mc.theWorld != null),
                            diagnostics, active.invoke(null));
                }
                fixture.aura.onEnable();
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        List<Object> walk() {
            ScaffoldClientFlowTest.ordinaryWalking(fixture);
            return drain();
        }

        List<Object> drain() {
            List<Object> packets = new ArrayList<>(); Object packet;
            while ((packet = fixture.channel.readOutbound()) != null) packets.add(packet);
            return packets;
        }

        List<String> lines() { return history.recent(120); }

        @Override public void close() throws Exception {
            try { fixture.aura.onDisable(); }
            finally {
                try { if (diagnostics.isToggled()) diagnostics.setToggled(false); }
                finally { fixture.close(); }
            }
        }
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static String value(String line, String key) {
        String prefix = key + "=";
        for (String word : line.split("\\s+")) if (word.startsWith(prefix)) return word.substring(prefix.length());
        fail("missing " + key + " in " + line); return null;
    }

    private static List<String> opportunities(Run run) {
        return run.lines().stream().filter(line -> line.startsWith("H2_OPPORTUNITY ")).collect(Collectors.toList());
    }

    private static List<String> accepted(Run run, String type) {
        return run.lines().stream().filter(line -> line.startsWith("H2_PACKET ")
                && "LOCAL_ACCEPTED".equals(value(line, "stage")) && type.equals(value(line, "type")))
                .collect(Collectors.toList());
    }

    private static String oneAccepted(Run run, String type) {
        List<String> matching = accepted(run, type);
        assertEquals("separate actual local " + type + " receipt: " + run.lines(), 1, matching.size());
        assertEquals("UNKNOWN", value(matching.get(0), "server"));
        return matching.get(0);
    }

    private static void withPacketHook(Consumer<me.youngjibbit.event.packet.EventPacket> action, Runnable body) {
        ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks(); hooks.packet = action;
        Pulsar.BUS.subscribe(hooks);
        try { body.run(); } finally { Pulsar.BUS.unsubscribe(hooks); }
    }

    @Test public void ordinaryAuraCorrelatesOpportunityAndDistinctAcceptedSwingAndAttack() throws Exception {
        try (Run run = new Run(true)) {
            List<Object> packets = run.walk();
            assertEquals(1, count(packets, C0APacketAnimation.class));
            assertEquals(1, count(packets, C02PacketUseEntity.class));
            assertFalse("actual Aura opportunity is missing: " + run.lines(), opportunities(run).isEmpty());
            String swing = oneAccepted(run, "C0A"), attack = oneAccepted(run, "C02");
            assertEquals(value(swing, "action"), value(attack, "action"));
            assertNotEquals("0", value(swing, "action"));
            assertEquals(value(swing, "run"), value(attack, "run"));
            assertNotEquals(value(swing, "packet"), value(attack, "packet"));
            assertEquals(value(swing, "origin"), value(attack, "origin"));
            assertNotEquals("UNKNOWN", value(swing, "origin"));
            assertTrue(opportunities(run).stream().anyMatch(line -> value(line, "action").equals(value(swing, "action"))
                    && value(line, "run").equals(value(swing, "run"))));
            assertEquals(0, run.fixture.player.rotationYaw, 0);
        }
    }

    @Test public void targetLossAfterAcceptedSwingRetainsSwingButHasNoAcceptedAttack() throws Exception {
        try (Run run = new Run(true)) {
            withPacketHook(event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C0APacketAnimation)
                    run.target.isDead = true;
            }, () -> {
                List<Object> packets = run.walk();
                assertEquals(1, count(packets, C0APacketAnimation.class));
                assertEquals(0, count(packets, C02PacketUseEntity.class));
                assertFalse("target-loss attempt is still an opportunity", opportunities(run).isEmpty());
                oneAccepted(run, "C0A");
                assertTrue(accepted(run, "C02").isEmpty());
                assertEquals(0, run.fixture.player.rotationYaw, 0);
            });
        }
    }

    @Test public void ownerLossAfterAcceptedSwingCannotAuthorizeC02FromEqualNumbers() throws Exception {
        try (Run run = new Run(true)) {
            withPacketHook(event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C0APacketAnimation) {
                    assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    run.fixture.runtime.getRotationManager().reset("aura");
                }
            }, () -> {
                List<Object> packets = run.walk();
                assertEquals(1, count(packets, C0APacketAnimation.class));
                assertEquals(0, count(packets, C02PacketUseEntity.class));
                oneAccepted(run, "C0A");
                assertTrue(accepted(run, "C02").isEmpty());
                assertEquals(0, run.fixture.player.rotationYaw, 0);
            });
        }
    }

    @Test public void swingBarrierReleasesTwoOwnerMovementHoldAndC02RechecksTheChangedWirePose() throws Exception {
        try (Run run = new Run(true)) {
            PacketPipeline pipeline = run.fixture.network.getClientPackets();
            Object firstOwner = new Object(), secondOwner = new Object();
            boolean[] queued = {false};
            Listener<EventMotion> queueBeforeAura = new Listener<>(EventMotion.class, event -> {
                if (!event.isPost() || queued[0]) return;
                queued[0] = true;
                pipeline.delays().request(firstOwner, 500, (packet, flow) -> flow == EventFlow.OUTBOUND && packet instanceof C03PacketPlayer);
                pipeline.delays().request(secondOwner, 500, (packet, flow) -> flow == EventFlow.OUTBOUND && packet instanceof C03PacketPlayer);
                assertNull(run.fixture.player.sendQueue.addToSendQueueTracked(new C03PacketPlayer.C05PacketPlayerLook(90, 0, true)));
                assertEquals(1, pipeline.delays().size());
                assertTrue(pipeline.delays().hasOwner(firstOwner));
                assertTrue(pipeline.delays().hasOwner(secondOwner));
            }, EventPriority.HIGHEST);
            Pulsar.BUS.subscribe(queueBeforeAura);
            try {
                List<Object> packets = run.walk();
                assertTrue(queued[0]);
                assertEquals(1, count(packets, C0APacketAnimation.class));
                assertEquals(0, count(packets, C02PacketUseEntity.class));
                assertEquals(0, pipeline.delays().size());
                int released = -1, swingIndex = -1;
                for (int index = 0; index < packets.size(); index++) {
                    if (packets.get(index) instanceof C03PacketPlayer.C05PacketPlayerLook) released = index;
                    if (packets.get(index) instanceof C0APacketAnimation) swingIndex = index;
                }
                assertTrue("queued look really precedes the swing barrier's action", released >= 0 && swingIndex > released);
                String swing = oneAccepted(run, "C0A");
                List<String> heldReceipts = accepted(run, "C03").stream()
                        .filter(line -> value(line, "causeAction").equals(value(swing, "action"))).collect(Collectors.toList());
                assertEquals("flush cause remains distinct from queued packet origin: " + run.lines(), 1, heldReceipts.size());
                assertNotEquals(value(swing, "action"), value(heldReceipts.get(0), "action"));
                assertTrue(accepted(run, "C02").isEmpty());
                assertEquals(0, run.fixture.player.rotationYaw, 0);
            } finally { Pulsar.BUS.unsubscribe(queueBeforeAura); }
        }
    }

    @Test public void earnedExtraAddsNoSwingOrAttackAndHasItsOwnObservedOpportunity() throws Exception {
        try (Run run = new Run(true)) {
            run.observation.beginOuterTick();
            try {
                run.observation.observePlayerStep(run.fixture.player, () -> {
                    run.fixture.player.onUpdateWalkingPlayer(); return true;
                }, body -> { body.getAsBoolean(); body.getAsBoolean(); });
            } finally { run.observation.endOuterTick(); }
            List<Object> packets = run.drain();
            assertEquals(1, count(packets, C0APacketAnimation.class));
            assertEquals(1, count(packets, C02PacketUseEntity.class));
            Set<String> actions = new HashSet<>();
            for (String line : opportunities(run)) actions.add(value(line, "action"));
            assertEquals("ordinary and denied extra are distinct observed attempts: " + run.lines(), 2, actions.size());
            oneAccepted(run, "C0A"); oneAccepted(run, "C02");
        }
    }

    @Test public void controllerOutsideAuraScopePreservesUnknownOriginAndManualBehavior() throws Exception {
        try (Run run = new Run(true)) {
            run.fixture.aura.setToggled(false);
            run.fixture.player.swingItem();
            assertTrue(run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
            List<Object> packets = run.drain();
            assertEquals(1, count(packets, C0APacketAnimation.class));
            assertEquals(1, count(packets, C02PacketUseEntity.class));
            assertTrue(opportunities(run).isEmpty());
            for (String line : new String[]{oneAccepted(run, "C0A"), oneAccepted(run, "C02")}) {
                assertEquals("UNKNOWN", value(line, "origin"));
                assertEquals("0", value(line, "action"));
            }
            assertNull(run.observation.getCurrentStep());
            assertEquals(-1, run.observation.getActiveOuterTickId());
        }
    }

    @Test public void diagnosticsOffPreservesRealAuraPacketsWithoutNewHistory() throws Exception {
        try (Run run = new Run(false)) {
            List<Object> packets = run.walk();
            assertEquals(1, count(packets, C0APacketAnimation.class));
            assertEquals(1, count(packets, C02PacketUseEntity.class));
            assertEquals(0, run.history.size());
            assertTrue(run.lines().isEmpty());
            assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
        }
    }
}
