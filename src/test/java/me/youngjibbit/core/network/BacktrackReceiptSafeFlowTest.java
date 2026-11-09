package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Real embedded packet pipeline with independent FIFO timing source. The new
 * opt-in receipt variant cannot invent a first server position, extend a
 * held C03 transport or indefinitely accumulate entity S14 packets.
 */
public class BacktrackReceiptSafeFlowTest {
    private static Backtrack create(ExploitHookFixture f,AtomicLong time) throws Exception {
        NetworkTestSupport.clock(f,time);
        Backtrack b=new Backtrack(time::get,f.client,(min,max)->max);
        b.getBooleanSetting("Only Aura").setValue(false);
        b.getModeSetting("Backtrack Mode").setValue("Receipt Safe");
        b.getModeSetting("Delay Mode").setValue("Fixed");
        b.getNumberSetting("Delay").setValue(450D);
        b.getNumberSetting("Cooldown Ticks").setValue(0D);
        b.setToggled(true);
        return b;
    }
    private static void move(ExploitHookFixture f) {
        f.receive(new S14PacketEntity.S15PacketEntityRelMove(
                7,(byte)4,(byte)0,(byte)0,true));
    }

    @Test public void noDelayBeforeActualTargetReceiptThenBoundTo100msFixedWindow() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Backtrack b=create(f,time);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                f.drainOutbound(); // isolate physical C03 from the setup controller's legitimate C02
                Pulsar.BUS.post(new TickEvent());
                assertEquals("no fabricated pre-receipt delay",0,f.pipe().delays().size());
                assertTrue(b.getStatus(),b.getStatus().contains("receipt missing or stale"));

                move(f);
                assertEquals(1,f.pipe().delays().size());
                assertEquals("100ms cap rather than configured 450ms",
                        96,enemy.serverPosX);
                assertTrue(b.getStatus(),b.getStatus().contains("100 ms"));

                // Outbound player movement is not held by this inbound-only variant.
                C03PacketPlayer walking=new C03PacketPlayer(true);
                f.connection.network.sendPacket(walking);
                assertSame(walking,f.connection.channel.readOutbound());
                time.set(25_000_000L);
                move(f);
                assertEquals(2,f.pipe().delays().size());
                time.set(101_000_000L);
                Pulsar.BUS.post(new TickEvent());
                assertEquals("held packet batch drains on time before any new S14",0,f.pipe().delays().size());
                assertEquals(104,enemy.serverPosX);
                assertTrue(b.getStatus(),b.getStatus().contains("receipt window complete"));
            } finally {b.setToggled(false);}
        }
    }

    @Test public void staleReceiptAndLocalKnockbackReleaseHeldFifoImmediately() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Backtrack b=create(f,time);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                f.drainOutbound(); // isolate physical C03 from the setup controller's legitimate C02
                move(f);assertEquals(1,f.pipe().delays().size());
                time.set(250_000_000L); // low-TPS / high-latency gap, no packet arrival
                Pulsar.BUS.post(new TickEvent());
                assertEquals(0,f.pipe().delays().size());
                assertEquals(100,enemy.serverPosX);
                assertTrue(b.getStatus(),b.getStatus().contains("receipt missing or stale"));

                time.set(500_000_000L);
                move(f);
                assertEquals(1,f.pipe().delays().size());
                f.receive(new S12PacketEntityVelocity(1,.3,.2,0));
                assertEquals("real local impulse forces FIFO recovery",0,f.pipe().delays().size());
            } finally {b.setToggled(false);}
        }
    }

    @Test public void hardQueueBudgetDrainsBeforeThirteenthHeldEntityMove() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Backtrack b=create(f,time);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                f.drainOutbound(); // isolate physical C03 from the setup controller's legitimate C02
                for(int i=1;i<=12;i++) {
                    move(f);
                    assertEquals("held FIFO count "+i,i,f.pipe().delays().size());
                    assertEquals(96,enemy.serverPosX);
                }
                move(f);
                assertEquals("additional target movement cannot exceed strict 12-packet cap",
                        0,f.pipe().delays().size());
                assertEquals(96+13*4,enemy.serverPosX);
                assertTrue(b.getStatus(),b.getStatus().contains("receipt queue budget"));
            } finally {b.setToggled(false);}
        }
    }

    @Test public void legacyModeOrderingAndDefaultsRemainUnchanged() throws Exception {
        Backtrack b=new Backtrack();
        assertEquals("Distance",b.getModeSetting("Backtrack Mode").getValue());
        for(String legacy:new String[]{"Distance","Timed","Pulse","Intave"}) {
            b.getModeSetting("Backtrack Mode").setValue(legacy);
            assertEquals(legacy,b.getModeSetting("Backtrack Mode").getValue());
        }
        b.getModeSetting("Backtrack Mode").setValue("Receipt Safe");
        assertEquals("Receipt Safe",b.getModeSetting("Backtrack Mode").getValue());
    }
}
