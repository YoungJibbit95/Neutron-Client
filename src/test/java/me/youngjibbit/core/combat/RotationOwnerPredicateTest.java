package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Accepted owner/phase contract; synthetic frames and the existing embedded transport fixture. */
public class RotationOwnerPredicateTest {
    private static final class PacketObserver implements Subscriber {
        java.util.function.Consumer<EventPacket> action = event -> { };
        @Subscribe final Listener<EventPacket> listener = new Listener<>(event -> action.accept(event));
    }

    private EventMotion resolve(RotationManager rotation) {
        EventMotion event = new EventMotion(0, 0, 0, 12, 3, true);
        rotation.resolve(event);
        return event;
    }

    private void accept(RotationManager rotation, String source, float yaw, float pitch) {
        rotation.beginMotion(12, 3);
        rotation.request(source, RotationManager.SCAFFOLD_PRIORITY, yaw, pitch);
        EventMotion event = resolve(rotation);
        rotation.finishMotion(event.getYaw(), event.getPitch(), true);
    }

    private void denied(RotationManager rotation) {
        for (String source : new String[]{null, "", "  ", "scaffold", "aura", "unknown"}) {
            assertFalse(String.valueOf(source), rotation.canUseRotation(source, true));
            assertFalse(String.valueOf(source), rotation.canUseRotation(source, false));
        }
        assertNull(rotation.getConfirmedOwner());
    }

    private void confirmed(RotationManager rotation, String source, boolean pre) {
        assertEquals(source, rotation.getConfirmedOwner());
        assertTrue(rotation.canUseRotation(pre));
        assertTrue(rotation.canUseRotation(source, pre));
        assertFalse(rotation.canUseRotation("unknown", pre));
        assertFalse(rotation.canUseRotation(null, pre));
        assertFalse(rotation.canUseRotation("", pre));
        assertFalse(rotation.canUseRotation(" " + source, pre));
        assertFalse(rotation.canUseRotation(source.toUpperCase(java.util.Locale.ROOT), pre));
    }

    @Test public void c01FreshManagerHasNoOwnerForEitherPhaseOrInvalidSource() {
        RotationManager rotation = new RotationManager();
        denied(rotation);
        assertFalse(rotation.hasEffectiveRotation());
        assertFalse(rotation.isMotionAccepted());
    }

    @Test public void c02OnlyTheAcceptedSourceOwnsPost() {
        RotationManager rotation = new RotationManager();
        accept(rotation, "scaffold", 80, 70);
        confirmed(rotation, "scaffold", false);
        assertFalse(rotation.canUseRotation("aura", false));
        assertTrue(rotation.matchesEffective(80, 70));
    }

    @Test public void c03PreKeepsPreviousScaffoldDespitePendingAuraAndPostChangesOwner() {
        RotationManager rotation = new RotationManager();
        accept(rotation, "scaffold", 80, 70);
        rotation.beginMotion(12, 3);
        rotation.request("aura", RotationManager.AURA_PRIORITY, -40, 10);
        EventMotion event = resolve(rotation);
        assertEquals(-40, event.getYaw(), 0);
        confirmed(rotation, "scaffold", true);
        assertFalse(rotation.canUseRotation("aura", true));
        assertFalse(rotation.canUseRotation("scaffold", false));
        assertEquals(80, rotation.getEffectiveYaw(), 0);
        rotation.finishMotion(event.getYaw(), event.getPitch(), true);
        confirmed(rotation, "aura", false);
        assertFalse(rotation.canUseRotation("scaffold", false));
        // No saved previous-owner/angle pair exists after finishMotion overwrites it.
        assertTrue(rotation.canUseRotation(true)); // Legacy numeric flag is deliberately unchanged.
        assertFalse(rotation.canUseRotation("aura", true));
        assertFalse(rotation.canUseRotation("scaffold", true));
    }

    @Test public void c04FirstPendingAndResolvedRequestCannotAuthorizePre() {
        RotationManager rotation = new RotationManager();
        rotation.beginMotion(12, 3);
        rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
        denied(rotation);
        EventMotion event = resolve(rotation);
        denied(rotation);
        rotation.finishMotion(event.getYaw(), event.getPitch(), true);
        confirmed(rotation, "scaffold", false);
    }

    @Test public void c05RejectionDeniesPostAndTheFollowingPreDespiteOldEffectiveAngles() {
        RotationManager rotation = new RotationManager();
        accept(rotation, "scaffold", 80, 70);
        rotation.beginMotion(12, 3);
        rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 90, 60);
        resolve(rotation);
        rotation.finishMotion(90, 60, false);
        denied(rotation);
        assertTrue(rotation.hasEffectiveRotation());
        assertEquals(80, rotation.getEffectiveYaw(), 0);
        rotation.beginMotion(12, 3);
        rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
        denied(rotation);
    }

    @Test public void c06MismatchedAcceptedYawOrPitchCannotInheritOldOwnership() {
        for (float[] packet : new float[][]{{81, 70}, {80, 69}}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, "scaffold", 80, 70);
            rotation.beginMotion(12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
            resolve(rotation);
            rotation.finishMotion(packet[0], packet[1], true);
            denied(rotation);
            assertTrue(rotation.canUseRotation(false));
            assertEquals(packet[0], rotation.getEffectiveYaw(), 0);
        }
    }

    @Test public void c06MatchingAnglesBelongOnlyToTheActuallySelectedWinner() {
        RotationManager rotation = new RotationManager();
        accept(rotation, "scaffold", 80, 70);
        rotation.beginMotion(12, 3);
        rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
        rotation.request("aura", RotationManager.SCAFFOLD_PRIORITY + 1, 80, 70);
        EventMotion event = resolve(rotation);
        rotation.finishMotion(event.getYaw(), event.getPitch(), true);
        confirmed(rotation, "aura", false);
        assertFalse(rotation.canUseRotation("scaffold", false));
    }

    @Test public void c07SourceResetRemovesRightsButPreservesLegacyAcceptedNumbers() {
        for (boolean pre : new boolean[]{false, true}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, "scaffold", 80, 70);
            if (pre) rotation.beginMotion(12, 3);
            rotation.reset("aura");
            confirmed(rotation, "scaffold", pre);
            rotation.reset("scaffold");
            denied(rotation);
            assertTrue(rotation.canUseRotation(pre));
            assertTrue(rotation.hasEffectiveRotation());
            assertEquals(80, rotation.getEffectiveYaw(), 0);
        }
    }

    @Test public void c08ChangedOutboundAnglesLoseOwnerInPreAndPostAndCannotRestoreIt() {
        for (boolean pre : new boolean[]{false, true}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, "scaffold", 80, 70);
            if (pre) rotation.beginMotion(12, 3);
            rotation.observeOutboundRotation(81, 70);
            denied(rotation);
            assertTrue(rotation.canUseRotation(pre));
            rotation.observeOutboundRotation(80, 70);
            denied(rotation); // Numerically returning to the old pose grants no owner.
        }
    }

    @Test public void c08InvalidOutboundAnglesRevokeBothAcceptedPhases() {
        for (float[] packet : new float[][]{{Float.NaN, 0}, {Float.POSITIVE_INFINITY, 0},
                {Float.NEGATIVE_INFINITY, 0}, {80, Float.NaN}, {80, Float.POSITIVE_INFINITY},
                {80, 91}, {80, -91}}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, "scaffold", 80, 70);
            rotation.beginMotion(12, 3);
            rotation.observeOutboundRotation(packet[0], packet[1]);
            denied(rotation);
            assertFalse(rotation.canUseRotation(true));
            assertFalse(rotation.canUseRotation(false));
        }
    }

    @Test public void c09ConnectionReplacementInvalidationAndResetNeverRestoreSourceRights() {
        RotationManager rotation = new RotationManager();
        Object first = new Object(), second = new Object();
        rotation.bindConnection(first);
        accept(rotation, "scaffold", 80, 70);
        rotation.bindConnection(first);
        rotation.invalidateConnection(second);
        confirmed(rotation, "scaffold", false);
        rotation.bindConnection(second);
        denied(rotation);
        rotation.bindConnection(first);
        denied(rotation);
        accept(rotation, "scaffold", 80, 70);
        rotation.invalidateConnection(first);
        denied(rotation);
        assertTrue(rotation.isConnection(null));
        accept(rotation, "scaffold", 80, 70);
        rotation.reset();
        denied(rotation);
    }

    @Test public void c09PlayerAndWorldBindingsInvalidatePreIncludingReturningToOldScene() {
        for (boolean changePlayer : new boolean[]{false, true}) {
            RotationManager rotation = new RotationManager();
            Object player = new Object(), world = new Object(), connection = new Object();
            rotation.bindConnection(connection);
            rotation.beginMotion(player, world, 12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
            resolve(rotation);
            rotation.finishMotion(80, 70, true);
            assertEquals("scaffold", rotation.getConfirmedOwner(player, world, connection));
            assertNull(rotation.getConfirmedOwner(new Object(), world, connection));
            assertNull(rotation.getConfirmedOwner(player, new Object(), connection));
            assertNull(rotation.getConfirmedOwner(player, world, new Object()));
            assertNull(rotation.getConfirmedOwner(player, world, null));
            rotation.beginMotion(changePlayer ? new Object() : player, changePlayer ? world : new Object(), 12, 3);
            denied(rotation);
            rotation.beginMotion(player, world, 12, 3);
            denied(rotation);
        }
    }

    @Test public void c10ExistingPrioritiesChooseScaffoldInEitherOrderButRequireRenewedRequests() {
        for (boolean scaffoldFirst : new boolean[]{false, true}) {
            RotationManager rotation = new RotationManager();
            rotation.beginMotion(12, 3);
            for (String source : scaffoldFirst ? new String[]{"scaffold", "aura"} : new String[]{"aura", "scaffold"})
                rotation.request(source, "scaffold".equals(source) ? RotationManager.SCAFFOLD_PRIORITY
                        : RotationManager.AURA_PRIORITY, 80, 70);
            EventMotion event = resolve(rotation);
            denied(rotation);
            rotation.finishMotion(event.getYaw(), event.getPitch(), true);
            confirmed(rotation, "scaffold", false);
            rotation.beginMotion(80, 70);
            confirmed(rotation, "scaffold", true);
            // Same accepted numbers with no current request are an unowned camera frame.
            rotation.finishMotion(80, 70, true);
            denied(rotation);
            assertTrue(rotation.canUseRotation(false));
            rotation.beginMotion(80, 70);
            denied(rotation);
            assertTrue(rotation.canUseRotation(true));
        }
    }

    @Test public void unownedAcceptedCameraFrameRemainsNumericallyUsable() {
        RotationManager rotation = new RotationManager();
        rotation.beginMotion(80, 70);
        rotation.finishMotion(80, 70, true);
        assertTrue(rotation.canUseRotation(false));
        assertTrue(rotation.isMotionAccepted());
        denied(rotation);
    }

    @Test public void emptyOrWhitespaceWinnerIsNeverAConfirmedSource() {
        for (String source : new String[]{"", " \t "}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, source, 80, 70);
            assertTrue(rotation.canUseRotation(false));
            assertFalse(rotation.canUseRotation(source, false));
            assertNull(rotation.getConfirmedOwner());
            rotation.beginMotion(12, 3);
            assertFalse(rotation.canUseRotation(source, true));
            assertNull(rotation.getConfirmedOwner());
        }
    }

    @Test public void sourcesAreExactRequestIdentifiersRatherThanAHardcodedModuleRegistry() {
        RotationManager rotation = new RotationManager();
        accept(rotation, "custom-source", 80, 70);
        confirmed(rotation, "custom-source", false);
        assertFalse(rotation.canUseRotation("scaffold", false));
    }

    @Test public void c11WrapClampAndExistingMatchingToleranceStayIntact() {
        for (float yaw : new float[]{180, -180}) for (float pitch : new float[]{120, -120}) {
            RotationManager rotation = new RotationManager();
            rotation.beginMotion(12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, yaw, pitch);
            EventMotion event = resolve(rotation);
            assertEquals(Math.copySign(90, pitch), event.getPitch(), 0);
            rotation.finishMotion(-yaw, event.getPitch(), true);
            confirmed(rotation, "scaffold", false);
            rotation.observeOutboundRotation(yaw, event.getPitch());
            confirmed(rotation, "scaffold", false);
        }
        for (float delta : new float[]{.0005f, .002f}) {
            RotationManager rotation = new RotationManager();
            rotation.beginMotion(12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 40, 10);
            resolve(rotation);
            rotation.finishMotion(40 + delta, 10 + delta, true);
            assertEquals(delta < .001f, rotation.canUseRotation("scaffold", false));
            assertEquals(delta < .001f ? "scaffold" : null, rotation.getConfirmedOwner());
        }
    }

    @Test public void c11InvalidPacketAnglesAndInvalidRequestsCannotCreateOwners() {
        for (float[] packet : new float[][]{{Float.NaN, 0}, {Float.POSITIVE_INFINITY, 0},
                {Float.NEGATIVE_INFINITY, 0}, {80, Float.NaN}, {80, Float.NEGATIVE_INFINITY}, {80, 91}, {80, -91}}) {
            RotationManager rotation = new RotationManager();
            accept(rotation, "scaffold", 80, 70);
            rotation.beginMotion(12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 80, 70);
            rotation.finishMotion(packet[0], packet[1], true);
            denied(rotation);
            rotation.beginMotion(12, 3);
            denied(rotation);
        }
        for (float[] request : new float[][]{{Float.NaN, 0}, {0, Float.POSITIVE_INFINITY}}) {
            RotationManager rotation = new RotationManager();
            rotation.beginMotion(12, 3);
            rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, request[0], request[1]);
            EventMotion event = resolve(rotation);
            rotation.finishMotion(event.getYaw(), event.getPitch(), true);
            denied(rotation);
            assertTrue(rotation.canUseRotation(false));
        }
    }

    private Map<String, Object> state(RotationManager rotation) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : RotationManager.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(rotation);
            values.put(field.getName(), value);
            if (value instanceof WeakReference)
                values.put(field.getName() + ".referent", ((WeakReference<?>) value).get());
            if (value instanceof Map)
                values.put(field.getName() + ".entries", new HashMap<>((Map<?, ?>) value));
            if (value != null && value.getClass().getEnclosingClass() == RotationManager.class)
                for (Field nested : value.getClass().getDeclaredFields()) {
                    nested.setAccessible(true);
                    values.put(field.getName() + "." + nested.getName(), nested.get(value));
                }
        }
        return values;
    }

    @Test public void c12RepeatedQueriesLeaveEveryManagerFieldAndTransportUntouched() throws Exception {
        try (NetworkMoveFixIntegrationTest.Fixture f = new NetworkMoveFixIntegrationTest.Fixture()) {
            f.accept("scaffold", 80);
            for (int phase = 0; phase < 3; phase++) {
                if (phase == 1) {
                    f.rotation.beginMotion(f.player, f.world, 12, 3);
                    f.rotation.request("aura", RotationManager.AURA_PRIORITY, 40, 10);
                }
                if (phase == 2) f.rotation.finishMotion(40, 10, false);
                Map<String, Object> before = state(f.rotation);
                for (int i = 0; i < 100; i++) {
                    for (boolean pre : new boolean[]{false, true}) {
                        assertEquals(phase < 2 && pre == (phase == 1), f.rotation.canUseRotation("scaffold", pre));
                        assertFalse(f.rotation.canUseRotation("aura", pre));
                        assertFalse(f.rotation.canUseRotation(null, pre));
                    }
                    assertEquals(before, state(f.rotation));
                    assertNull(f.channel.readOutbound());
                }
            }
        }
    }

    @Test public void actualPacketCancellationAndDelayCannotGrantPostOrSubsequentPreRights() {
        for (boolean delay : new boolean[]{false, true})
            try (NetworkMoveFixIntegrationTest.Fixture f = new NetworkMoveFixIntegrationTest.Fixture()) {
                f.accept("scaffold", 80);
                PacketObserver packets = new PacketObserver();
                if (delay) f.pipe().delays().request("owner-test", 200, (p, flow) -> flow == EventFlow.OUTBOUND);
                else packets.action = event -> { if (event.isPre()) event.cancel(); };
                Pulsar.BUS.subscribe(packets);
                try {
                    f.rotation.beginMotion(f.player, f.world, 12, 3);
                    f.rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 90, 15);
                    resolve(f.rotation);
                    Packet<?> accepted = f.network.sendPacketTracked(new C03PacketPlayer.C05PacketPlayerLook(90, 15, true), false);
                    assertNull(accepted);
                    f.rotation.finishMotion(90, 15, accepted != null);
                    denied(f.rotation);
                    f.rotation.beginMotion(f.player, f.world, 12, 3);
                    denied(f.rotation);
                    if (delay) {
                        f.pipe().delays().release("owner-test");
                        assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer);
                        assertEquals(90, f.rotation.getEffectiveYaw(), 0);
                        denied(f.rotation);
                    } else assertNull(f.channel.readOutbound());
                } finally { Pulsar.BUS.unsubscribe(packets); }
            }
    }

    @Test public void actualPacketRewriteAndExternalReplayRevokeTheAcceptedOwner() {
        try (NetworkMoveFixIntegrationTest.Fixture f = new NetworkMoveFixIntegrationTest.Fixture()) {
            f.accept("scaffold", 80);
            PacketObserver packets = new PacketObserver();
            C03PacketPlayer replacement = new C03PacketPlayer.C05PacketPlayerLook(-40, 10, true);
            packets.action = event -> { if (event.isPre()) event.setPacket(replacement); };
            Pulsar.BUS.subscribe(packets);
            try {
                f.rotation.beginMotion(f.player, f.world, 12, 3);
                f.rotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 90, 15);
                resolve(f.rotation);
                assertSame(replacement, f.network.sendPacketTracked(new C03PacketPlayer.C05PacketPlayerLook(90, 15, true), false));
                f.rotation.finishMotion(f.rotation.getEffectiveYaw(), f.rotation.getEffectivePitch(), true);
                denied(f.rotation);
                assertTrue(f.rotation.canUseRotation(false));
                assertSame(replacement, f.channel.readOutbound());
            } finally { Pulsar.BUS.unsubscribe(packets); }
            f.accept("scaffold", 80);
            f.network.sendPacketSilent(new C03PacketPlayer.C05PacketPlayerLook(-40, 10, true));
            assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer);
            denied(f.rotation);
        }
    }
}
