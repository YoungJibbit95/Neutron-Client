package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class DemiseTimingFlowTest {
    @Test public void packetPulseKeepsPlayerStepsAndExpires()throws Exception{Wave2IsolatedCase.run(Checks.class,"packetPulseKeepsPlayerStepsAndExpires");}
    @Test public void approachDisableAndOtherOwnerReleaseCorrectly()throws Exception{Wave2IsolatedCase.run(Checks.class,"approachDisableAndOtherOwnerReleaseCorrectly");}
    @Test public void demiseBacktrackUsesLiveReceiptAndReleasesOnDisable()throws Exception{Wave2IsolatedCase.run(Checks.class,"demiseBacktrackUsesLiveReceiptAndReleasesOnDisable");}
    public static final class Checks {
        @Test public void packetPulseKeepsPlayerStepsAndExpires()throws Exception{
            try(ExploitHookFixture f=new ExploitHookFixture()){
                AtomicLong now=new AtomicLong();NetworkTestSupport.clock(f,now);
                Tickbase m=new Tickbase(now::get,f.client);m.getBooleanSetting("Only Aura").setValue(false);
                m.getModeSetting("Mode").setValue("Demise Pulse");m.setToggled(true);
                try{
                    f.cursor(f.enemy(7,4));Tickbase.beginFrame(f.client,false,1,50);
                    AtomicInteger calls=new AtomicInteger();Tickbase.runPlayerStep(f.player,()->{calls.incrementAndGet();return true;});
                    assertEquals("packet mode never withholds the body",1,calls.get());
                    f.connection.network.sendPacket(new C03PacketPlayer(true));
                    assertEquals(1,f.pipe().delays().size());assertNull(f.connection.channel.readOutbound());
                    now.set(34_000_000);assertNotNull(m.getActivitySnapshot());
                    now.set(121_000_000);Tickbase.beginFrame(f.client,false,1,50);
                    assertEquals(0,f.pipe().delays().size());assertNotNull(f.connection.channel.readOutbound());
                    assertNull(m.getActivitySnapshot());
                }finally{m.setToggled(false);}
            }
        }
        @Test public void approachDisableAndOtherOwnerReleaseCorrectly()throws Exception{
            try(ExploitHookFixture f=new ExploitHookFixture()){
                AtomicLong now=new AtomicLong();NetworkTestSupport.clock(f,now);
                Tickbase m=new Tickbase(now::get,f.client);m.getBooleanSetting("Only Aura").setValue(false);
                m.getModeSetting("Mode").setValue("Demise Approach");m.setToggled(true);
                try{
                    f.cursor(f.enemy(7,4));Object other=new Object();assertTrue(f.pipe().combatTiming().acquire(other));
                    Tickbase.beginFrame(f.client,false,1,50);f.connection.network.sendPacket(new C03PacketPlayer(true));
                    assertEquals(0,f.pipe().delays().size());assertNotNull(f.connection.channel.readOutbound());
                    assertTrue(f.pipe().combatTiming().owns(other));f.pipe().combatTiming().release(other,0);
                    now.set(70_000_000);Tickbase.beginFrame(f.client,false,1,50);
                    f.connection.network.sendPacket(new C03PacketPlayer(true));assertEquals(1,f.pipe().delays().size());
                    m.setToggled(false);assertEquals(0,f.pipe().delays().size());assertNotNull(f.connection.channel.readOutbound());
                    assertNull(m.getActivitySnapshot());
                }finally{m.setToggled(false);}
            }
        }
        @Test public void demiseBacktrackUsesLiveReceiptAndReleasesOnDisable()throws Exception{
            try(ExploitHookFixture f=new ExploitHookFixture()){
                AtomicLong now=new AtomicLong();NetworkTestSupport.clock(f,now);
                Backtrack m=new Backtrack(now::get,f.client);m.getBooleanSetting("Only Aura").setValue(false);
                m.getModeSetting("Target Source").setValue("Attack / Cursor");m.getModeSetting("Backtrack Mode").setValue("Demise");
                m.getBooleanSetting("Only When Needed").setValue(true);m.setToggled(true);
                try{
                    f.cursor(f.enemy(7,3));Pulsar.BUS.post(new TickEvent());
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)16,(byte)0,(byte)0,true));
                    assertEquals(1,f.pipe().delays().size());assertNotNull(m.getActivitySnapshot());
                    m.setToggled(false);assertEquals(0,f.pipe().delays().size());assertNull(m.getActivitySnapshot());
                }finally{m.setToggled(false);}
            }
        }
    }
}
