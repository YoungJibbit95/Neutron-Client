package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class LagRangeApproachTest {
    private void clock(ExploitHookFixture f, AtomicLong now) throws Exception {
        Field delays = PacketPipeline.class.getDeclaredField("delays"); delays.setAccessible(true);
        delays.set(f.pipe(), new PacketDelays(now::get));
    }
    private void tick(AtomicLong now, long millis) { now.set(millis * 1_000_000); Pulsar.BUS.post(new TickEvent()); }

    @Test public void intaveHoldsOnlyWhileDistanceClosesOutsideStartRange() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); clock(f, now);
            LagRange lag = new LagRange(now::get, (min, max) -> max); lag.getBooleanSetting("Only Aura").setValue(false);
            lag.getModeSetting("Delay Mode").setValue("Intave Approach");
            lag.setToggled(true);
            try {
                f.enemy(7, 6); tick(now, 0);
                f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(0, f.pipe().delays().size());
                f.player.posX = .2; tick(now, 50);
                f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(1, f.pipe().delays().size());
                f.player.posX = 1; tick(now, 51);
                assertEquals(0, f.pipe().delays().size());
            } finally { lag.setToggled(false); }
        }
    }

    @Test public void approachingBuildsMonotoneOutboundDelayAndNeverHoldsEnemyMovement() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); clock(f, now);
            LagRange lag = new LagRange(now::get, (min, max) -> 500); lag.getBooleanSetting("Only Aura").setValue(false);
            lag.getNumberSetting("Min MS").setValue(500D); lag.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 6); tick(now, 0);
                C03PacketPlayer first = new C03PacketPlayer(true); f.connection.network.sendPacket(first);
                assertEquals(1, f.pipe().delays().size());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                assertEquals(208, enemy.serverPosX); // inbound is applied immediately
                now.set(40_000_000); f.pipe().pump(); assertNull(f.connection.channel.readOutbound());
                now.set(41_000_000); f.pipe().pump(); assertSame(first, f.connection.channel.readOutbound());
                tick(now, 50);
                C03PacketPlayer second = new C03PacketPlayer(true); f.connection.network.sendPacket(second);
                now.set(132_000_000); f.pipe().pump(); assertNull(f.connection.channel.readOutbound());
                now.set(133_000_000); f.pipe().pump(); assertSame(second, f.connection.channel.readOutbound());
            } finally { lag.setToggled(false); }
        }
    }

    @Test public void attackFlushesMovementBeforeActionAndRetreatOrTargetLossReleases() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); clock(f, now);
            LagRange lag = new LagRange(now::get, (min, max) -> max); lag.getBooleanSetting("Only Aura").setValue(false); lag.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 6); tick(now, 0);
                C03PacketPlayer move = new C03PacketPlayer(true); f.connection.network.sendPacket(move);
                assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                assertSame(move, f.connection.channel.readOutbound());
                assertTrue(f.connection.channel.readOutbound() instanceof C02PacketUseEntity);
                tick(now, 449); f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(0, f.pipe().delays().size()); f.drainOutbound();
                tick(now, 500); f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(1, f.pipe().delays().size());
                f.player.posX = -1; tick(now, 501); assertEquals(0, f.pipe().delays().size());
                f.player.posX = 0; tick(now, 1001); f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(1, f.pipe().delays().size());
                f.world.entities.remove(7); tick(now, 1002); assertEquals(0, f.pipe().delays().size());
            } finally { lag.setToggled(false); }
        }
    }
}
