package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class LocalImpulseTimingTest {
    private Tickbase charged(ExploitHookFixture f, AtomicLong now) throws Exception {
        BacktrackLagRangeControlTest.clock(f, now);
        f.world.entities.put(1, f.player);
        f.cursor(f.enemy(7, 3));
        Tickbase tick = new Tickbase(now::get, f.client);
        tick.getBooleanSetting("Only Aura").setValue(false);
        tick.getBooleanSetting("Debug").setValue(true);
        tick.getNumberSetting("Charge Ticks").setValue(1D);
        tick.getNumberSetting("Min Charge").setValue(1D);
        tick.getNumberSetting("Release Speed").setValue(2D);
        tick.getNumberSetting("Release Cooldown MS").setValue(0D);
        tick.setToggled(true);
        Tickbase.beginFrame(f.client, false, 1, 50);
        Tickbase.runPlayerStep(f.player, () -> { fail("charge a withheld player body"); return true; });
        assertEquals(1, tick.getDiagnostics().credit);
        return tick;
    }
    private void impulse(ExploitHookFixture f) {
        f.receive(new S12PacketEntityVelocity(1, .3, .2, -.1));
    }
    private int step(ExploitHookFixture f) {
        AtomicInteger count = new AtomicInteger();
        Tickbase.beginFrame(f.client, false, 1, 50);
        Tickbase.runPlayerStep(f.player, () -> { count.incrementAndGet(); return true; });
        return count.get();
    }

    @Test public void velocityWithoutHurtStatusDiscardsChargeAndCannotRearmInFlight() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            Tickbase tick = charged(f, now);
            try {
                impulse(f);
                assertEquals(0, f.player.hurtTime);
                assertEquals(0, tick.getDiagnostics().credit);
                assertEquals(.2, f.player.motionY, 1e-9);
                assertEquals(1, step(f));
                now.set(600_000_000L);
                f.player.onGround = false; f.player.motionY = -.1;
                assertEquals(1, step(f));
                f.player.onGround = true; f.player.motionY = 0; f.player.hurtTime = 1;
                assertEquals(1, step(f));
                f.player.hurtTime = 0;
                assertEquals(0, step(f));
                assertEquals(1, tick.getDiagnostics().credit);
            } finally { tick.setToggled(false); }
        }
    }

    @Test public void velocityInsideOrdinaryBodyPreventsAnAlreadyArmedExtraBody() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Tickbase tick = charged(f, new AtomicLong());
            try {
                assertTrue(f.controller.tryAttackEntity(f.player, (net.minecraft.entity.EntityLivingBase)f.client.objectMouseOver.entityHit, () -> true));
                assertTrue(tick.getDiagnostics().releasing);
                Tickbase.beginFrame(f.client, false, 1, 50);
                AtomicInteger count = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> { count.incrementAndGet(); impulse(f); return true; });
                assertEquals(1, count.get());
                assertEquals(0, tick.getDiagnostics().credit);
                assertFalse(tick.getDiagnostics().releasing);
            } finally { tick.setToggled(false); }
        }
    }

    @Test public void preImpulseMovementIsPublishedBeforeVelocityAndStaleOwnersCannotHoldRecovery() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); BacktrackLagRangeControlTest.clock(f, now);
            f.world.entities.put(1, f.player);
            ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
            Object owner = new Object();
            f.pipe().delays().request(owner, 500, (packet, flow) -> true);
            C03PacketPlayer before = new C03PacketPlayer(true);
            f.connection.network.sendPacket(before);
            assertNull(f.connection.channel.readOutbound());
            AtomicInteger observed = new AtomicInteger();
            PacketFlowTest.Observer packets = new PacketFlowTest.Observer();
            packets.action = event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() == before) {
                    assertEquals("old movement precedes applying the impulse", 0, f.player.motionX, 0);
                    observed.incrementAndGet();
                }
            };
            Pulsar.BUS.subscribe(packets);
            try {
                impulse(f);
                assertEquals(1, observed.get());
                assertSame(before, f.connection.channel.readOutbound());
                C03PacketPlayer after = new C03PacketPlayer(true);
                f.connection.network.sendPacket(after);
                assertSame(after, f.connection.channel.readOutbound());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte)4, (byte)0, (byte)0, true));
                assertEquals(100, enemy.serverPosX);
                assertEquals(0, f.pipe().delays().size());
            } finally { Pulsar.BUS.unsubscribe(packets); }
        }
    }
}
