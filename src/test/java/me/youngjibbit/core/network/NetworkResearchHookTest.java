package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.diagnostics.NetworkResearchAnalysis;
import me.youngjibbit.core.diagnostics.NetworkResearchRecording;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.client.NetworkResearch;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

/** Real event/embedded-transport checks; no server or anticheat is simulated by this fixture. */
public class NetworkResearchHookTest {
    private static S14PacketEntity move(int id, int delta) {
        return new S14PacketEntity.S15PacketEntityRelMove(id, (byte) delta, (byte) 0, (byte) 0, true);
    }
    private static List<Entry> ofKind(NetworkResearch research, Kind kind) {
        return research.snapshot().stream().filter(entry -> entry.kind == kind).collect(Collectors.toList());
    }
    private static void tick() { Pulsar.BUS.post(new TickEvent()); }

    @Test public void actualPacketsKeepIdentityOrderAndOutcomeWithObservationEnabled() throws Exception {
        assertEquals(packetRun(false), packetRun(true));
    }
    private List<String> packetRun(boolean enabled) throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 0, f.client);
            research.getModeSetting("Mode").setValue("Lagrange"); research.setToggled(enabled);
            NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
            List<String> order = new ArrayList<>(); Packet<?>[] expected = {null};
            observer.action = event -> {
                assertSame(expected[0], event.getPacket());
                order.add((event.isInbound() ? "in:" : "out:") + (event.isPre() ? "pre:" : "post:")
                        + event.getPacket().getClass().getSimpleName());
            };
            Pulsar.BUS.subscribe(observer);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                for (Packet<?> packet : Arrays.asList(move(7, 16), move(7, 16))) {
                    expected[0] = packet; f.receive(packet);
                }
                for (Packet<?> packet : Arrays.asList(new C03PacketPlayer(true), new C0APacketAnimation())) {
                    expected[0] = packet; f.connection.network.sendPacket(packet);
                    assertSame(packet, f.connection.channel.readOutbound());
                }
                assertEquals(128, enemy.serverPosX); assertEquals(0, f.pipe().delays().size());
                assertNull(f.connection.channel.readOutbound());
                if (enabled) {
                    assertEquals(2, ofKind(research, Kind.RECEIVED).size());
                    assertEquals(2, ofKind(research, Kind.APPLIED).size());
                    assertEquals(2, ofKind(research, Kind.LOCAL_ACCEPTED).size());
                    assertTrue(research.snapshot().stream().allMatch(entry -> entry.origin == Origin.CLIENT_OBSERVATION));
                    assertEquals(0, research.getCaptureFailures());
                } else assertTrue(research.snapshot().isEmpty());
                return order;
            } finally { Pulsar.BUS.unsubscribe(observer); research.setToggled(false); }
        }
    }

    @Test public void samplingChangingProfileAndDisablingNeverPumpsAForeignQueue() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 0, f.client);
            Object owner = new Object(); AtomicInteger applied = new AtomicInteger();
            f.pipe().delays().request(owner, 500, (packet, flow) -> true);
            assertTrue(f.pipe().delays().defer(move(7, 1), EventFlow.INBOUND, applied::incrementAndGet));
            research.setToggled(true);
            try {
                tick(); assertTrue(ofKind(research, Kind.QUEUE).get(0).detail.startsWith("inbound=1;outbound=0;"));
                research.getModeSetting("Mode").setValue("Lagrange"); tick();
                research.getModeSetting("Mode").setValue("Tickbase"); tick();
                research.setToggled(false); tick();
                assertEquals(0, applied.get()); assertEquals(1, f.pipe().delays().size());
                assertEquals(1, f.pipe().delays().activeHeldCount(owner));
                assertTrue(f.pipe().delays().hasOwner(owner)); assertTrue(research.snapshot().isEmpty());
            } finally { research.setToggled(false); }
        }
    }
    @Test public void backtrackHistoryCopiesReceiptCoordinatesWithoutChangingEntityApplication() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 25, f.client);
            ExploitHookFixture.Enemy enemy = f.enemy(7, 3); research.setToggled(true);
            try {
                f.receive(move(7, 16)); List<Entry> history = ofKind(research, Kind.ENTITY);
                assertEquals(2, history.size()); assertEquals(7, history.get(0).entityId);
                assertEquals(3.5, history.get(0).x, 0); assertEquals(3, history.get(0).renderedX, 0);
                assertEquals(112, enemy.serverPosX); f.receive(move(7, 16));
                assertEquals(128, enemy.serverPosX); assertEquals(3.5, history.get(0).x, 0);
                assertEquals(0, research.getCaptureFailures());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void profileWorldEpochAndCurrentSessionBoundariesResetTheTickBaseline() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); NetworkResearch research = new NetworkResearch(now::get, f.client);
            research.setToggled(true);
            try {
                tick(); now.set(70_000_000); tick();
                assertEquals(20_000_000, ofKind(research, Kind.TICK).get(1).deviationNanos);
                long run = research.snapshot().get(0).run;
                research.getModeSetting("Mode").setValue("Tickbase");
                assertTrue(research.snapshot().isEmpty()); tick();
                assertTrue(research.snapshot().get(0).run > run);
                assertEquals(Mode.TICKBASE, research.snapshot().get(0).mode);
                assertEquals(0, ofKind(research, Kind.TICK).get(0).intervalNanos);
                Pulsar.BUS.post(new PlaySessionEvent(new NetworkManager(EnumPacketDirection.CLIENTBOUND), PlaySessionEvent.State.ENDED));
                assertFalse(research.snapshot().isEmpty());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world)); assertTrue(research.snapshot().isEmpty());
                tick(); assertTrue(ofKind(research, Kind.TICK).get(0).detail.contains("baseline=true"));
                f.pipe().worldChanged(); now.set(500_000_000); tick();
                assertEquals(1, ofKind(research, Kind.TICK).size());
                assertEquals(0, ofKind(research, Kind.TICK).get(0).intervalNanos);
                Pulsar.BUS.post(new PlaySessionEvent(f.connection.network, PlaySessionEvent.State.ENDED));
                assertTrue(research.snapshot().isEmpty());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void simulationAndObservationNeverShareProvenanceAndDisableUnsubscribes() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 0, f.client);
            research.getModeSetting("Source").setValue("Simulation"); research.setToggled(true);
            try {
                assertFalse(research.snapshot().isEmpty());
                assertTrue(research.snapshot().stream().allMatch(entry -> entry.origin == Origin.SIMULATION));
                List<Entry> saved = research.snapshot(); tick(); f.receive(move(999, 1));
                assertEquals(saved, research.snapshot());
                assertThrows(IllegalStateException.class, () -> research.recordServerFlag("fixture/log#1", "ExampleCheck"));
                research.getModeSetting("Source").setValue("Observe");
                assertTrue(research.snapshot().isEmpty()); tick();
                assertTrue(research.snapshot().stream().allMatch(entry -> entry.origin == Origin.CLIENT_OBSERVATION));
                research.setToggled(false); tick(); f.receive(move(999, 1)); assertTrue(research.snapshot().isEmpty());
                research.setToggled(true); research.setToggled(true); tick();
                assertEquals(1, ofKind(research, Kind.TICK).size());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void correctionObservationNeverInventsAServerFlag() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 0, f.client); research.setToggled(true);
            try {
                // Notification only: no correction application or Intave simulation.
                Pulsar.BUS.post(new PacketReceiveEvent(new S08PacketPlayerPosLook(), f.pipe()));
                assertEquals(1, ofKind(research, Kind.CORRECTION).size()); assertTrue(ofKind(research, Kind.SERVER_FLAG).isEmpty());
                research.recordServerFlag("fixture/server.log#line=12", "ExampleCheck");
                Entry evidence = ofKind(research, Kind.SERVER_FLAG).get(0);
                assertEquals(Origin.SERVER_EVIDENCE, evidence.origin);
                assertEquals("fixture/server.log#line=12", evidence.detail); assertEquals("ExampleCheck", evidence.packetType);
                assertThrows(IllegalArgumentException.class, () -> research.recordServerFlag("", "ExampleCheck"));
            } finally { research.setToggled(false); }
        }
    }

    @Test public void nettyHandoffIsObservedOnlyWhenTheClientThreadDrainsIt() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); NetworkResearch research = new NetworkResearch(now::get, f.client);
            ExploitHookFixture.Enemy enemy = f.enemy(7, 3); research.setToggled(true);
            try {
                tick(); List<Entry> before = research.snapshot();
                f.connection.netty(() -> f.receive(move(7, 16)));
                assertEquals(before, research.snapshot()); assertEquals(96, enemy.serverPosX);
                now.set(123); f.connection.main.drain(); assertEquals(112, enemy.serverPosX);
                assertEquals(1, ofKind(research, Kind.RECEIVED).size());
                assertEquals(123, ofKind(research, Kind.RECEIVED).get(0).nanos);
                assertEquals(1, ofKind(research, Kind.APPLIED).size()); assertEquals(0, research.getCaptureFailures());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void recordingExportRetainsItsRunAfterWorldResetWithoutApplyingAnything() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkResearch research = new NetworkResearch(now::get, f.client);
            research.setToggled(true);
            try {
                tick(); now.set(50_000_000); tick();
                NetworkResearchRecording recording = research.recording();
                assertEquals(Origin.CLIENT_OBSERVATION, recording.origin);
                assertEquals(4, recording.entries.size());
                assertEquals(0, recording.overwritten); assertEquals(0, recording.captureFailures);
                StringWriter out = new StringWriter(); recording.write(out);
                NetworkResearchRecording imported = NetworkResearchRecording.read(new StringReader(out.toString()));
                assertEquals(1, NetworkResearchAnalysis.analyze(imported).tickIntervals.samples);
                assertEquals(0, f.pipe().delays().size()); assertNull(f.connection.channel.readOutbound());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertTrue(research.recording().entries.isEmpty());
                assertTrue(research.recording().run > recording.run);
                assertEquals(4, recording.entries.size());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void simulationRecordingMetadataTracksSettingAndDisableBoundaries() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            NetworkResearch research = new NetworkResearch(() -> 0, f.client);
            research.getModeSetting("Source").setValue("Simulation");
            research.getModeSetting("Scenario").setValue("Jitter");
            research.setToggled(true);
            try {
                NetworkResearchRecording saved = research.recording();
                assertEquals(Origin.SIMULATION, saved.origin); assertEquals("JITTER", saved.scenario);
                assertEquals(61, saved.entries.size()); assertEquals(0, saved.simulationRejected);
                research.getModeSetting("Scenario").setValue("Pause");
                assertTrue(research.recording().run > saved.run);
                assertEquals("PAUSE", research.recording().scenario);
                research.setToggled(false);
                NetworkResearchRecording stopped = research.recording();
                assertEquals(Origin.CLIENT_OBSERVATION, stopped.origin);
                assertEquals("", stopped.scenario); assertTrue(stopped.entries.isEmpty());
                assertEquals(61, saved.entries.size());
                assertEquals(0, f.pipe().delays().size()); assertNull(f.connection.channel.readOutbound());
            } finally { research.setToggled(false); }
        }
    }

    @Test public void recordingKeepsCaptureFailuresWithoutChangingTheObservedTransport() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicBoolean failing = new AtomicBoolean();
            NetworkResearch research = new NetworkResearch(() -> {
                if (failing.get()) throw new IllegalStateException("synthetic clock failure");
                return 0L;
            }, f.client);
            research.getModeSetting("Mode").setValue("Lagrange"); research.setToggled(true);
            try {
                tick(); failing.set(true);
                C0APacketAnimation packet = new C0APacketAnimation();
                f.connection.network.sendPacket(packet);
                assertSame(packet, f.connection.channel.readOutbound());
                assertEquals(1, research.recording().captureFailures);
                assertTrue(NetworkResearchAnalysis.analyze(research.recording()).issues
                        .contains(NetworkResearchAnalysis.Issue.CAPTURE_FAILURES));
                assertEquals(0, f.pipe().delays().size());
            } finally { research.setToggled(false); }
        }
    }
}
