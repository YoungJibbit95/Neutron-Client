package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import net.minecraft.inventory.Container;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class BacktrackLagRangeControlTest {
    static void clock(ExploitHookFixture f,AtomicLong now) throws Exception {
        Field delays=PacketPipeline.class.getDeclaredField("delays");delays.setAccessible(true);
        delays.set(f.pipe(),new PacketDelays(now::get));
    }
    @Test public void transactionIsAnsweredImmediatelyWithoutFlushingBacktrackMovement() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();clock(f,now);
            Backtrack back=new Backtrack(now::get,f.client);back.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));f.drainOutbound();
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)16,(byte)0,(byte)0,true));
                assertEquals(1,f.pipe().delays().size());assertEquals(96,enemy.serverPosX);
                f.player.inventoryContainer=new Container(){@Override public boolean canInteractWith(EntityPlayer player){return true;}};
                f.receive(new S32PacketConfirmTransaction(0,(short)12,false));
                assertTrue("transaction reply remains immediate",f.connection.channel.readOutbound() instanceof C0FPacketConfirmTransaction);
                assertEquals("independent confirmation must not release movement",1,f.pipe().delays().size());
                assertEquals(96,enemy.serverPosX);
                now.set(101_000_000L);f.pipe().pump();assertEquals(112,enemy.serverPosX);
            } finally { back.setToggled(false); }
        }
    }
    @Test public void controlRepliesDoNotFlushLagRangeButAttackStillDoes() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();clock(f,now);
            LagRange lag=new LagRange(now::get,(min,max)->500);lag.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,6);Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer movement=new C03PacketPlayer(true);f.connection.network.sendPacket(movement);
                assertEquals(1,f.pipe().delays().size());
                C0FPacketConfirmTransaction reply=new C0FPacketConfirmTransaction(0,(short)12,true);
                f.connection.network.sendPacket(reply);
                assertSame("acknowledgement is immediate, not preceded by a forced movement release",reply,f.connection.channel.readOutbound());
                f.receive(new S00PacketKeepAlive(12));
                assertTrue("real handler answers keepalive immediately",f.connection.channel.readOutbound() instanceof C00PacketKeepAlive);
                assertEquals(1,f.pipe().delays().size());
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                assertSame("attack still publishes earlier movement first",movement,f.connection.channel.readOutbound());
                assertTrue(f.connection.channel.readOutbound() instanceof C02PacketUseEntity);
            } finally { lag.setToggled(false); }
        }
    }
    @Test public void distanceNeedsGainButTimedAndPulseCanHoldStationaryTargetMovement() throws Exception {
        for(String mode:new String[]{"Distance","Timed","Pulse"})
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                AtomicLong now=new AtomicLong();clock(f,now);Backtrack back=new Backtrack(now::get,f.client);
                back.getModeSetting("Backtrack Mode").setValue(mode);back.setToggled(true);
                try {
                    ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                    assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)0,(byte)0,(byte)0,true));
                    assertEquals(mode,"Distance".equals(mode)?0:1,f.pipe().delays().size());
                    assertTrue(back.getStatus().startsWith(mode+":"));
                    back.setToggled(false);assertEquals(0,f.pipe().delays().size());
                } finally { back.setToggled(false); }
            }
    }
    @Test public void pulseReleasesTheWholeBatchWhileTimedKeepsItsLaterPacketDeadline() throws Exception {
        for(String mode:new String[]{"Timed","Pulse"})
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                AtomicLong now=new AtomicLong();clock(f,now);Backtrack back=new Backtrack(now::get,f.client);
                back.getModeSetting("Backtrack Mode").setValue(mode);back.setToggled(true);
                try {
                    ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                    assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                    now.set(50_000_000L);
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                    assertEquals(2,f.pipe().delays().size());assertEquals(96,enemy.serverPosX);
                    now.set(100_000_000L);Pulsar.BUS.post(new TickEvent());f.pipe().pump();
                    assertEquals(mode,"Pulse".equals(mode)?0:1,f.pipe().delays().size());
                    assertEquals("Pulse".equals(mode)?104:100,enemy.serverPosX);
                    now.set(150_000_000L);f.pipe().pump();assertEquals(104,enemy.serverPosX);
                    if("Pulse".equals(mode)) {
                        f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                        assertEquals("cooldown prevents immediate pulse rearm",0,f.pipe().delays().size());
                        assertTrue(back.getStatus().contains("settling"));
                    }
                } finally { back.setToggled(false); }
            }
    }
    @Test public void pulseExpiryOnReceiptWithoutTickReleasesBatchAndStartsCooldown() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();clock(f,now);Backtrack back=new Backtrack(now::get,f.client);
            back.getModeSetting("Backtrack Mode").setValue("Pulse");back.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                now.set(50_000_000L);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                assertEquals(2,f.pipe().delays().size());assertEquals(96,enemy.serverPosX);
                now.set(101_000_000L); // No TickEvent or explicit pump before this receipt.
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                assertEquals(0,f.pipe().delays().size());assertEquals(108,enemy.serverPosX);
                assertTrue(back.getStatus(),back.getStatus().contains("pulse complete"));
                now.set(102_000_000L);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                assertEquals(0,f.pipe().delays().size());assertEquals(112,enemy.serverPosX);
                assertTrue(back.getStatus(),back.getStatus().contains("settling"));
            } finally { back.setToggled(false); }
        }
    }
    @Test public void activationStatusExplainsMissingAttackAndImpossibleDistanceWindow() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();Backtrack back=new Backtrack(now::get,f.client);
            LagRange lag=new LagRange(now::get,(min,max)->min);
            lag.getNumberSetting("Max Target Range").setValue(3D);lag.getNumberSetting("Start Distance").setValue(5D);
            back.setToggled(true);lag.setToggled(true);
            try {
                f.enemy(7,2.8);Pulsar.BUS.post(new TickEvent());
                assertTrue(back.getStatus(),back.getStatus().contains("waiting for target: Attack"));
                assertTrue(lag.getStatus(),lag.getStatus().contains("Start Distance exceeds target range"));
                assertEquals(0,f.pipe().delays().size());
            } finally { back.setToggled(false);lag.setToggled(false); }
        }
    }
}
