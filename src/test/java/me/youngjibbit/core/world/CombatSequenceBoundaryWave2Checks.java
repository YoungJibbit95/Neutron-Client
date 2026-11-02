package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.CombatDiagnosticTrace;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.*;
import me.youngjibbit.core.observation.MinecraftSceneSource;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** Independent H2 lifecycle, terminal-failure and observation-only boundary probes. */
public class CombatSequenceBoundaryWave2Checks {
    private static long packets(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static List<ScalarRecord> records(CombatSequenceWave2Checks.Run run, Kind kind, Stage stage) {
        return run.history.scalarSnapshot().stream().filter(record -> record.kind == kind && record.stage == stage)
                .collect(Collectors.toList());
    }

    private static long accepted(CombatSequenceWave2Checks.Run run, PacketType type) {
        return records(run, Kind.PACKET, Stage.LOCAL_ACCEPTED).stream().filter(record -> record.type == type).count();
    }

    private static void withHook(Consumer<me.youngjibbit.event.packet.EventPacket> action, Runnable body) {
        ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
        hooks.packet = action;
        Pulsar.BUS.subscribe(hooks);
        try { body.run(); } finally { Pulsar.BUS.unsubscribe(hooks); }
    }

    @Test public void oldStepCompletionCannotMigrateToANewGenerationOrEpochRun() throws Exception {
        for (boolean generation : new boolean[]{false, true}) {
            try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
                long[] originalRun = {0}, originalOuter = {-1};
                run.observation.beginOuterTick();
                try {
                    run.observation.observePlayerStep(run.fixture.player, () -> {
                        run.fixture.player.onUpdateWalkingPlayer();
                        originalRun[0] = run.history.getRunId();
                        originalOuter[0] = run.observation.getActiveOuterTickId();
                        if (generation) run.observation.invalidateScene();
                        else run.fixture.network.getClientPackets().worldChanged();
                        return true;
                    }, body -> body.getAsBoolean());
                } finally { run.observation.endOuterTick(); }
                assertTrue(originalRun[0] > 0);
                assertFalse("an old body cannot become a successor-run receipt",
                        run.history.scalarSnapshot().stream().anyMatch(record -> record.kind == Kind.STEP
                                && record.outerTick == originalOuter[0] && record.run != originalRun[0]));
                assertTrue("the rejected old-step receipt remains observable", run.history.getStaleTokenCount() > 0);
                List<Object> actual = run.drain();
                assertEquals(1, packets(actual, C0APacketAnimation.class));
                assertEquals(1, packets(actual, C02PacketUseEntity.class));
            }
        }
    }

    @Test public void oldObserverFinallyCannotWriteAStepIntoItsReplacementRun() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            Field observerField = ScaffoldOwnerContinuityTest.field(ClientRuntime.class, "observation");
            RuntimeObservation replacement = new RuntimeObservation(() -> MinecraftSceneSource.capture(run.fixture.mc));
            long[] originalRun = {0}, originalOuter = {-1};
            run.observation.beginOuterTick();
            try {
                run.observation.observePlayerStep(run.fixture.player, () -> {
                    run.fixture.player.onUpdateWalkingPlayer();
                    originalRun[0] = run.history.getRunId();
                    originalOuter[0] = run.observation.getActiveOuterTickId();
                    try { observerField.set(run.fixture.runtime, replacement); }
                    catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                    replacement.beginOuterTick();
                    return true;
                }, body -> body.getAsBoolean());
                assertFalse("replacement observer must not own the predecessor's finally",
                        run.history.scalarSnapshot().stream().anyMatch(record -> record.kind == Kind.STEP
                                && record.outerTick == originalOuter[0] && record.run != originalRun[0]));
                assertTrue(run.history.getStaleTokenCount() > 0);
            } finally {
                replacement.reset();
                observerField.set(run.fixture.runtime, run.observation);
                run.observation.endOuterTick();
            }
        }
    }

    @Test public void relevantSelectedRewriteGetsExactlyOneReceiptEvenWhenOriginalPacketWasOther() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            withHook(event -> {
                if (event.isOutbound() && event.isPre() && event.getPacket() instanceof C09PacketHeldItemChange)
                    event.setPacket(new C0APacketAnimation());
            }, () -> assertTrue(run.fixture.player.sendQueue.addToSendQueueTracked(new C09PacketHeldItemChange(0))
                    instanceof C0APacketAnimation));
            List<Object> actual = run.drain();
            assertEquals(1, packets(actual, C0APacketAnimation.class));
            assertEquals(0, packets(actual, C09PacketHeldItemChange.class));
            assertEquals("final selected C0A really transported", 1, accepted(run, PacketType.C0A));
            ScalarRecord receipt = records(run, Kind.PACKET, Stage.LOCAL_ACCEPTED).get(0);
            assertEquals(Origin.UNKNOWN, receipt.origin);
            assertEquals(0, receipt.action);
            assertEquals(Outcome.UNKNOWN, receipt.server);
        }
    }

    private static void assertFailedPacket(CombatSequenceWave2Checks.Run run) {
        assertTrue("the throw occurred before transport", run.drain().isEmpty());
        List<ScalarRecord> failures = records(run, Kind.PACKET, Stage.FAILED);
        assertEquals("the attempted packet gets one terminal failure receipt", 1, failures.size());
        assertEquals(PacketType.C0A, failures.get(0).type);
        assertEquals(0, accepted(run, PacketType.C0A));
    }

    @Test public void packetPreThrowKeepsItsThrowableAndGetsATerminalFailureReceipt() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            IllegalStateException failure = new IllegalStateException("packet PRE boundary");
            withHook(event -> {
                if (event.isOutbound() && event.isPre() && event.getPacket() instanceof C0APacketAnimation) throw failure;
            }, () -> assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation()))));
            assertFailedPacket(run);
        }
    }

    @Test public void finalPredicateThrowRunsOnceKeepsItsThrowableAndGetsATerminalFailureReceipt() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            IllegalStateException failure = new IllegalStateException("final transport predicate boundary");
            AtomicInteger guardCalls = new AtomicInteger();
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation(), packet -> {
                        guardCalls.incrementAndGet(); throw failure;
                    })));
            assertEquals(1, guardCalls.get());
            assertFailedPacket(run);
        }
    }

    @Test public void auraPacketPostThrowKeepsAcceptedSwingAndRecordsInterruptedBody() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            IllegalStateException failure = new IllegalStateException("after actual swing transport");
            withHook(event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C0APacketAnimation) throw failure;
            }, () -> assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> ScaffoldClientFlowTest.ordinaryWalking(run.fixture))));
            List<Object> actual = run.drain();
            assertEquals(1, packets(actual, C0APacketAnimation.class));
            assertEquals(0, packets(actual, C02PacketUseEntity.class));
            assertEquals(1, accepted(run, PacketType.C0A));
            assertEquals(0, accepted(run, PacketType.C02));
            List<ScalarRecord> steps = records(run, Kind.STEP, Stage.INTERRUPTED);
            assertEquals(1, steps.size());
            assertEquals(StepState.INTERRUPTED, steps.get(0).stepState);
            assertEquals(1, records(run, Kind.OPPORTUNITY, Stage.INTERRUPTED).size());
            assertNull(run.observation.getCurrentStep());
            assertEquals(-1, run.observation.getActiveOuterTickId());
        }
    }

    @Test public void realPacketBurstStaysBoundedAndStoredEvidenceHasNoGameReferences() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            for (int index = 0; index < 80; index++)
                assertNotNull(run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation()));
            assertEquals(80, packets(run.drain(), C0APacketAnimation.class));
            List<ScalarRecord> snapshot = run.history.scalarSnapshot();
            assertEquals("the configured bounded ring is retained", 120, snapshot.size());
            assertEquals("one eviction per scalar beyond capacity",
                    snapshot.get(snapshot.size() - 1).event - snapshot.size(), run.history.getOverwrittenCount());
            for (Class<?> schema : new Class<?>[]{ScalarRecord.class, ActionToken.class, PacketToken.class, StepToken.class}) {
                for (Field field : schema.getDeclaredFields())
                    assertTrue(schema.getSimpleName() + "." + field.getName() + " retains a game/payload reference",
                            field.getType().isPrimitive() || field.getType().isEnum());
            }
            assertTrue(snapshot.stream().allMatch(record -> record.server == Outcome.UNKNOWN));
        }
    }

    @Test public void nestedOpportunityRestoresParentAndOldRunTokenCannotCaptureNewPackets() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            ActionToken outer = CombatDiagnostics.beginOpportunity();
            ActionToken inner = CombatDiagnostics.beginOpportunity();
            assertNotNull(outer); assertNotNull(inner);
            assertNotEquals(outer.action, inner.action);
            run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation());
            CombatDiagnostics.endOpportunity(inner, false, false);
            assertEquals(outer.action, run.history.getActiveAction());
            run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation());
            List<ScalarRecord> receipts = records(run, Kind.PACKET, Stage.LOCAL_ACCEPTED);
            assertEquals(2, receipts.size());
            assertEquals(inner.action, receipts.get(0).action);
            assertEquals(outer.action, receipts.get(1).action);
            CombatDiagnostics.endOpportunity(outer, false, false);
            assertEquals(0, run.history.getActiveAction());

            ActionToken stale = CombatDiagnostics.beginOpportunity();
            run.diagnostics.setToggled(false);
            run.diagnostics.setToggled(true);
            run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation());
            CombatDiagnostics.opportunityResult(stale, Stage.HIT_CHECK, true);
            CombatDiagnostics.endOpportunity(stale, true, false);
            receipts = records(run, Kind.PACKET, Stage.LOCAL_ACCEPTED);
            assertEquals(1, receipts.size());
            assertNotEquals(stale.run, receipts.get(0).run);
            assertEquals(0, receipts.get(0).action);
            assertEquals(Origin.UNKNOWN, receipts.get(0).origin);
            assertEquals(0, run.history.getActiveAction());
            assertTrue(run.history.getStaleTokenCount() > 0);
        }
    }

    @SuppressWarnings("unchecked") private static long[] observationCounts(boolean enabled) throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(enabled)) {
            Field source = ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source");
            Supplier<RuntimeObservation.SceneSample> original = (Supplier<RuntimeObservation.SceneSample>) source.get(run.observation);
            AtomicInteger sceneReads = new AtomicInteger(), guardCalls = new AtomicInteger();
            source.set(run.observation, (Supplier<RuntimeObservation.SceneSample>) () -> {
                sceneReads.incrementAndGet(); return original.get();
            });
            try {
                List<Object> actual = run.walk();
                assertNotNull(run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation(), packet -> {
                    guardCalls.incrementAndGet(); return true;
                }));
                actual.addAll(run.drain());
                return new long[]{sceneReads.get(), guardCalls.get(), packets(actual, C03PacketPlayer.class),
                        packets(actual, C0APacketAnimation.class), packets(actual, C02PacketUseEntity.class)};
            } finally { source.set(run.observation, original); }
        }
    }

    @Test public void diagnosticsOnAndOffHaveEqualSceneSamplingGuardCallsAndActualPackets() throws Exception {
        long[] disabled = observationCounts(false), enabled = observationCounts(true);
        assertTrue("real admission/observer sampling was exercised", disabled[0] > 0);
        assertEquals(1, disabled[1]);
        assertArrayEquals("logging cannot rerun scene/admission or transport predicates", disabled, enabled);
    }

    @Test public void traceOwnRuntimeFailureCannotChangePacketsOrTheGameplayThrowable() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(true)) {
            Field traceObserver = ScaffoldOwnerContinuityTest.field(CombatDiagnostics.class, "traceObserver");
            Object original = traceObserver.get(run.diagnostics);
            traceObserver.set(run.diagnostics, null);
            try {
                List<Object> actual = run.walk();
                assertEquals(1, packets(actual, C0APacketAnimation.class));
                assertEquals(1, packets(actual, C02PacketUseEntity.class));
                IllegalStateException failure = new IllegalStateException("original gameplay predicate");
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> run.fixture.player.sendQueue.addToSendQueueTracked(new C0APacketAnimation(), packet -> { throw failure; })));
                assertTrue(run.drain().isEmpty());
                assertTrue("capture failure is explicit", run.history.getCaptureFailureCount() > 0);
                assertEquals(0, run.history.getActiveAction());
            } finally { traceObserver.set(run.diagnostics, original); }
        }
    }
}
