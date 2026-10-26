package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class BacktrackBehaviorHookTest {
    private Backtrack enable(AtomicLong now, ExploitHookFixture f) {
        Backtrack module = new Backtrack(now::get, f.client);
        assertNull(module.getDiagnostics());
        module.getBooleanSetting("Diagnostics").setValue(true);
        module.getNumberSetting("Min MS").setValue(500.0); module.getNumberSetting("Max MS").setValue(500.0);
        module.setToggled(true); return module;
    }
    private void tick() { Pulsar.BUS.post(new TickEvent()); }
    private S14PacketEntity move(int id, int delta) { return new S14PacketEntity.S15PacketEntityRelMove(id, (byte) delta, (byte) 0, (byte) 0, true); }

    @Test public void cursorAcquisitionAccumulatesMovementAndShortHoldExpiresInElapsedTime() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Backtrack back = enable(now, f);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy); tick();
                f.receive(move(7, 16)); f.receive(move(7, 16));
                assertEquals(96, enemy.serverPosX); assertEquals(4, f.pipe().receivedPosition(enemy).xCoord, 0);
                assertEquals(2, back.getDiagnostics().queue.heldByOwner); assertEquals("cursor", back.getDiagnostics().source);
                f.cursor(null); now.set(300000000); tick(); assertEquals("cursor_hold", back.getDiagnostics().source);
                now.set(351000000); tick(); assertEquals(-1, back.getDiagnostics().targetId);
                assertEquals(128, enemy.serverPosX); assertEquals(0, f.pipe().delays().size());
            } finally { back.setToggled(false); }
        }
    }
    @Test public void onlyAcceptedAttacksTakePriorityOverCursorAndNewAttacksSwitchTheLock() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Backtrack back = enable(now, f);
            PacketFlowTest.Observer packets = new PacketFlowTest.Observer(); Pulsar.BUS.subscribe(packets);
            try {
                ExploitHookFixture.Enemy a = f.enemy(7, 3), b = f.enemy(8, 3); f.cursor(b); tick();
                AttackEvent cancelled = new AttackEvent(a); cancelled.cancel(); Pulsar.BUS.post(cancelled);
                assertEquals(8, back.getDiagnostics().targetId);
                packets.action = e -> { if (e.isPre() && e.getPacket() instanceof C02PacketUseEntity) e.setPacket(new C02PacketUseEntity(a, C02PacketUseEntity.Action.INTERACT)); };
                assertFalse(f.controller.tryAttackEntity(f.player, a, () -> true)); assertEquals(8, back.getDiagnostics().targetId);
                packets.action = e -> { if (e.isPre() && e.getPacket() instanceof C02PacketUseEntity) e.cancel(); };
                assertFalse(f.controller.tryAttackEntity(f.player, a, () -> true)); assertEquals(8, back.getDiagnostics().targetId);
                packets.action = e -> { };
                assertTrue(f.controller.tryAttackEntity(f.player, a, () -> true)); tick(); assertEquals(7, back.getDiagnostics().targetId);
                assertEquals("attack", back.getDiagnostics().source);
                assertTrue(f.controller.tryAttackEntity(f.player, b, () -> true)); assertEquals(8, back.getDiagnostics().targetId);
                now.set(351000000); f.cursor(a); tick(); assertEquals(7, back.getDiagnostics().targetId); assertEquals("cursor", back.getDiagnostics().source);
            } finally { back.setToggled(false); Pulsar.BUS.unsubscribe(packets); }
        }
    }
    @Test public void acquisitionAfterForeignDelayUsesEarlierReceiptsAndDisableReleasesOnlyItsOwnHolds() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Backtrack back = enable(now, f); Object foreign = new Object();
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                f.pipe().delays().request(foreign, 500, (p, flow) -> true);
                f.receive(move(7, 16)); f.receive(move(7, 16));
                f.cursor(enemy); tick(); f.receive(move(7, 16));
                assertEquals(4.5, f.pipe().receivedPosition(enemy).xCoord, 0); assertEquals(96, enemy.serverPosX);
                assertEquals(1, back.getDiagnostics().queue.heldByOwner);
                back.setToggled(false); assertTrue(f.pipe().delays().hasOwner(foreign)); assertEquals(3, f.pipe().delays().size());
                f.pipe().delays().release(foreign); assertEquals(144, enemy.serverPosX);
            } finally { back.setToggled(false); }
        }
    }
    @Test public void hurtRangeGainZeroDelayAndNearestOptionsHaveObservableEffects() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Backtrack back = enable(new AtomicLong(), f);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy);
                enemy.hurtTime = 0; tick(); f.receive(move(7, 16)); assertEquals(0, f.pipe().delays().size());
                back.getBooleanSetting("Active On No Hit").setValue(true); tick();
                back.getNumberSetting("Min Server Distance").setValue(5.0); tick(); f.receive(move(7, 16)); assertEquals("distance_filter", back.getDiagnostics().reason);
                back.getNumberSetting("Min Server Distance").setValue(2.2); back.getNumberSetting("Min Distance Gain").setValue(1.0);
                tick(); assertEquals("distance_filter", back.getDiagnostics().reason);
                back.getNumberSetting("Min Distance Gain").setValue(.08); back.getNumberSetting("Min MS").setValue(0.0); back.getNumberSetting("Max MS").setValue(0.0);
                tick(); assertEquals("zero_delay", back.getDiagnostics().reason);
                f.cursor(null); back.getModeSetting("Target Source").setValue("Nearest"); tick(); assertEquals(7, back.getDiagnostics().targetId);
                enemy.posX = 9; tick(); assertEquals(-1, back.getDiagnostics().targetId);
            } finally { back.setToggled(false); }
        }
    }
    @Test public void teleportAndLocalVelocitySettleAndRemovalOrIdReuseCannotKeepOldTargetState() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Backtrack back = enable(now, f);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy); tick(); f.receive(move(7, 16));
                f.receive(new S18PacketEntityTeleport(7, 128, 0, 0, (byte) 0, (byte) 0, true));
                assertEquals(0, f.pipe().delays().size()); f.receive(move(7, 16)); assertEquals("settling", back.getDiagnostics().reason);
                now.set(151000000); tick(); f.receive(move(7, 16)); assertTrue(f.pipe().delays().size() > 0);
                f.receive(new S12PacketEntityVelocity(1, 0, .1, 0)); f.receive(move(7, 1)); assertEquals("settling", back.getDiagnostics().reason);
                back.getBooleanSetting("Release On Velocity").setValue(false); tick(); f.receive(move(7, 1));
                assertEquals("delaying_target_motion", back.getDiagnostics().reason);
                f.receive(new S13PacketDestroyEntities(7)); assertEquals(-1, back.getDiagnostics().targetId);
                ExploitHookFixture.Enemy replacement = f.enemy(7, 1); tick(); assertEquals(-1, back.getDiagnostics().targetId);
                f.cursor(replacement); tick(); assertEquals(7, back.getDiagnostics().targetId);
                assertEquals(1, f.pipe().receivedPosition(replacement).xCoord, 0);
            } finally { back.setToggled(false); }
        }
    }
}
