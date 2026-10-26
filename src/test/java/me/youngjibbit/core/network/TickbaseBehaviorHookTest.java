package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.module.impl.exploit.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class TickbaseBehaviorHookTest {
    private Tickbase enable(AtomicLong now, ExploitHookFixture f) {
        Tickbase tick = new Tickbase(now::get, f.client); assertNull(tick.getDiagnostics());
        tick.getBooleanSetting("Debug").setValue(true); tick.getNumberSetting("Charge Ticks").setValue(2.0);
        tick.getNumberSetting("Min Charge").setValue(1.0); tick.getNumberSetting("Release Speed").setValue(2.0);
        tick.setToggled(true); return tick;
    }
    private void frame(ExploitHookFixture f) { Tickbase.beginFrame(f.client, false, 1, 50); }
    private int step(ExploitHookFixture f) {
        AtomicInteger calls = new AtomicInteger(); Tickbase.runPlayerStep(f.player, () -> { calls.incrementAndGet(); return true; }); return calls.get();
    }
    private void chargeTwo(ExploitHookFixture f, Tickbase module) {
        frame(f); assertEquals(0, step(f)); frame(f); assertEquals(1, step(f)); frame(f); assertEquals(0, step(f));
        assertEquals(2, module.getDiagnostics().credit);
    }

    @Test public void acceptedAttackReleasesEarnedStepsAndCooldownBlocksImmediateRecharge() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Tickbase tick = enable(now, f);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy); chargeTwo(f, tick);
                assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true)); assertTrue(tick.getDiagnostics().releasing);
                frame(f); assertEquals(2, step(f)); assertEquals(1, tick.getDiagnostics().extraSteps);
                frame(f); assertEquals(2, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                assertFalse(tick.getDiagnostics().releasing); assertEquals(300, tick.getDiagnostics().cooldownMs);
                frame(f); assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                now.set(300000000); frame(f); assertEquals(0, step(f)); assertEquals(1, tick.getDiagnostics().credit);
            } finally { tick.setToggled(false); }
        }
    }
    @Test public void replacedCancelledAndInvalidatedAttacksCannotTriggerRelease() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Tickbase tick = enable(now, f);
            PacketFlowTest.Observer packets = new PacketFlowTest.Observer(); Pulsar.BUS.subscribe(packets);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy); chargeTwo(f, tick);
                AttackEvent cancelled = new AttackEvent(enemy); cancelled.cancel(); Pulsar.BUS.post(cancelled); assertFalse(tick.getDiagnostics().releasing);
                packets.action = e -> { if (e.isPre() && e.getPacket() instanceof C02PacketUseEntity) e.setPacket(new C02PacketUseEntity(enemy, C02PacketUseEntity.Action.INTERACT)); };
                assertFalse(f.controller.tryAttackEntity(f.player, enemy, () -> true)); assertFalse(tick.getDiagnostics().releasing);
                packets.action = e -> { if (e.isPre() && e.getPacket() instanceof C02PacketUseEntity) e.cancel(); };
                assertFalse(f.controller.tryAttackEntity(f.player, enemy, () -> true)); assertFalse(tick.getDiagnostics().releasing);
                packets.action = e -> { if (e.isPost() && e.getPacket() instanceof C03PacketPlayer) enemy.isDead = true; };
                f.pipe().delays().request(this, 500, (p, flow) -> true);
                f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertFalse(f.controller.tryAttackEntity(f.player, enemy, () -> !enemy.isDead)); assertFalse(tick.getDiagnostics().releasing);
                assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
            } finally { Pulsar.BUS.unsubscribe(packets); tick.setToggled(false); }
        }
    }
    @Test public void attackMustMatchChargedTargetAndRangeAndExtraStepRechecksIt() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Tickbase tick = enable(new AtomicLong(), f);
            try {
                ExploitHookFixture.Enemy a = f.enemy(7, 3), b = f.enemy(8, 3); f.cursor(a); chargeTwo(f, tick);
                assertTrue(f.controller.tryAttackEntity(f.player, b, () -> true)); assertFalse(tick.getDiagnostics().releasing); assertEquals(0, tick.getDiagnostics().credit);
                frame(f); assertEquals(0, step(f));
                a.serverPosX = 128; assertTrue(f.controller.tryAttackEntity(f.player, a, () -> true)); assertFalse(tick.getDiagnostics().releasing);
                assertEquals("outside_release_range", tick.getDiagnostics().reason);
                a.serverPosX = 96; frame(f); assertTrue(f.controller.tryAttackEntity(f.player, a, () -> true));
                AtomicInteger performed = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> { performed.incrementAndGet(); a.isDead = true; return true; });
                assertEquals(1, performed.get()); assertEquals(0, tick.getDiagnostics().credit);
            } finally { tick.setToggled(false); }
        }
    }
    @Test public void approachRequiresAnActualRangeCrossingAndNeverSendsAnAttackItself() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Tickbase tick = enable(now, f);
            try {
                tick.getModeSetting("Release On").setValue("Attack / Approach");
                ExploitHookFixture.Enemy enemy = f.enemy(7, 4); f.cursor(enemy); chargeTwo(f, tick); assertFalse(tick.getDiagnostics().releasing);
                Object other = new Object(); f.pipe().delays().request(other, 500, (p, flow) -> true);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) -32, (byte) 0, (byte) 0, true));
                assertEquals(128, enemy.serverPosX); // the received position crossed the range, even while client application is delayed
                frame(f); assertEquals(2, step(f)); assertTrue(tick.getDiagnostics().releasing);
                assertNull(f.connection.channel.readOutbound());
            } finally { tick.setToggled(false); }
        }
    }
    @Test public void activationTargetSwitchStaleFrameAndInvalidMidFrameContextRequireFreshEvidence() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Tickbase tick = enable(now, f);
            try {
                ExploitHookFixture.Enemy a = f.enemy(7, 3), b = f.enemy(8, 3); f.cursor(a);
                assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                assertEquals("fresh_frame_required", tick.getDiagnostics().reason);
                frame(f); now.set(151000000); assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                frame(f); assertEquals(0, step(f)); f.cursor(b); assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                frame(f); assertEquals(0, step(f)); f.client.currentScreen = new GuiChat(); assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                f.client.currentScreen = null; assertEquals(1, step(f)); frame(f); assertEquals(0, step(f));
                f.player.hurtTime = 3; assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                f.player.hurtTime = 0; frame(f); assertEquals(0, step(f));
                f.receive(new S07PacketRespawn()); assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
                frame(f); assertEquals(0, step(f)); f.connection.network.closeChannel(new net.minecraft.util.ChatComponentText("synthetic"));
                assertEquals(1, step(f)); assertEquals(0, tick.getDiagnostics().credit);
            } finally { tick.setToggled(false); }
        }
    }
    @Test public void simultaneousRealBacktrackLagRangeAndTickbaseShareQueuesWithoutSpendingForeignHolds() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Tickbase tick = enable(now, f);
            Backtrack back = new Backtrack(now::get, f.client); LagRange lag = new LagRange();
            back.getNumberSetting("Min MS").setValue(500.0); back.getNumberSetting("Max MS").setValue(500.0);
            lag.getNumberSetting("Min MS").setValue(500.0); lag.getNumberSetting("Max MS").setValue(500.0);
            back.setToggled(true); lag.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 2.4); f.cursor(enemy); CombatTargetTracker.update(enemy, "synthetic");
                Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                chargeTwo(f, tick); f.connection.network.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(0, 0, 0, 15, 20, true));
                assertTrue(f.pipe().delays().size() >= 2);
                assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                assertTrue(f.connection.channel.readOutbound() instanceof C03PacketPlayer); assertTrue(f.connection.channel.readOutbound() instanceof C02PacketUseEntity);
                assertEquals(1, f.pipe().delays().size()); // Backtrack's inbound hold survives LagRange's successful-hit release
                frame(f); assertEquals(2, step(f)); back.setToggled(false); assertEquals(0, f.pipe().delays().size());
            } finally { back.setToggled(false); lag.setToggled(false); tick.setToggled(false); }
        }
    }
}
