package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/**
 * Actual PacketPipeline + player-step consumer for the opt-in packet-only
 * LagRange variant, preserving original combat owner and safety barriers.
 */
@RunWith(Parameterized.class)
public class LagRangeBoundedFrameFlowTest {
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> cases() { return Arrays.asList(new Object[][]{
        {"boundedWindowRequiresFreshFrameAndNeverExpandsC03Past100ms"},
        {"unhealthyPausedOrLongFrameFlushesAndNeverWithholdsPlayerBody"},
        {"transactionAcknowledgementRemainsImmediateAndLocalImpulseDrains"},
        {"originalLagRangeModesAndDefaultConfigIdsAreStable"}
    }); }
    private final String caseName;
    public LagRangeBoundedFrameFlowTest(String caseName) { this.caseName=caseName; }
    @Test public void exerciseRealPipelineInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Checks.class,caseName);
    }

    private static LagRange enable(ExploitHookFixture f,AtomicLong time) throws Exception {
        NetworkTestSupport.clock(f,time);
        LagRange lag=new LagRange(time::get,(min,max)->max);
        lag.getBooleanSetting("Only Aura").setValue(false);
        lag.getModeSetting("Delay Mode").setValue("Bounded Frame");
        lag.getNumberSetting("Window Ticks").setValue(10D);
        lag.getNumberSetting("Release Cooldown MS").setValue(0D);
        lag.setToggled(true);
        f.enemy(7,6);
        return lag;
    }
    private static int targetDelay(LagRange lag) throws Exception {
        Field field=LagRange.class.getDeclaredField("targetDelay");
        field.setAccessible(true);
        return field.getInt(lag);
    }

    private static void boundedWindowRequiresFreshFrameAndNeverExpandsC03Past100ms() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            LagRange lag=enable(f,time);
            try {
                Pulsar.BUS.post(new TickEvent());
                assertEquals("no sampled frame means no transport hold",0,f.pipe().delays().size());
                assertTrue(lag.getStatus().contains("healthy frame required"));
                LagRange.beginFrame(f.client,false,1,50);
                Pulsar.BUS.post(new TickEvent());
                assertEquals("configured 10 ticks must be bounded to 100ms",100,targetDelay(lag));
                C03PacketPlayer packet=new C03PacketPlayer(true);
                f.connection.network.sendPacket(packet);
                assertEquals(1,f.pipe().delays().size());
                assertNull("physical C03 is really held, not fabricated",f.connection.channel.readOutbound());
                time.set(120_000_000L);
                Pulsar.BUS.post(new TickEvent());
                assertEquals(0,f.pipe().delays().size());
                assertSame(packet,f.connection.channel.readOutbound());
                assertTrue(lag.getStatus(),lag.getStatus().contains("frame window expired"));
            } finally {lag.setToggled(false);}
        }
    }

    private static void unhealthyPausedOrLongFrameFlushesAndNeverWithholdsPlayerBody() throws Exception {
        for(int condition=0;condition<2;condition++) {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                AtomicLong time=new AtomicLong();
                LagRange lag=enable(f,time);
                try {
                    // Context must be observed before the first eligible frame:
                    // binding a new player/world legitimately invalidates an old frame.
                    Pulsar.BUS.post(new TickEvent());
                    LagRange.beginFrame(f.client,false,1,50);
                    Pulsar.BUS.post(new TickEvent());
                    C03PacketPlayer movement=new C03PacketPlayer(true);
                    f.connection.network.sendPacket(movement);
                    assertEquals(1,f.pipe().delays().size());
                    AtomicInteger bodySteps=new AtomicInteger();
                    LagRange.runPlayerStep(f.player,()->{bodySteps.incrementAndGet();return true;});
                    assertEquals("packet hold may not withhold the loaded player step",1,bodySteps.get());
                    assertTrue(lag.getStatus(),lag.getStatus().contains("steps 0/0"));
                    LagRange.beginFrame(f.client,condition==0,condition==1?3:1,condition==1?300:50);
                    Pulsar.BUS.post(new TickEvent());
                    assertEquals("invalid frame immediately releases C03",0,f.pipe().delays().size());
                    assertSame(movement,f.connection.channel.readOutbound());
                    assertTrue(lag.getStatus(),lag.getStatus().contains("healthy frame required"));
                } finally {lag.setToggled(false);}
            }
        }
    }

    private static void transactionAcknowledgementRemainsImmediateAndLocalImpulseDrains() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            LagRange lag=enable(f,time);
            try {
                // Register the lifetime/transport before the first fresh sample.
                Pulsar.BUS.post(new TickEvent());
                LagRange.beginFrame(f.client,false,1,50);
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer movement=new C03PacketPlayer(true);
                f.connection.network.sendPacket(movement);
                assertEquals(1,f.pipe().delays().size());
                C0FPacketConfirmTransaction confirmation=new C0FPacketConfirmTransaction(0,(short)7,true);
                f.connection.network.sendPacket(confirmation);
                assertSame("control reply cannot be trapped behind movement",confirmation,
                        f.connection.channel.readOutbound());
                assertEquals(1,f.pipe().delays().size());
                // Actual NetHandler resolves the local player from the loaded world.
                f.world.entities.put(f.player.getEntityId(),f.player);
                f.receive(new S12PacketEntityVelocity(1,.2,.3,0));
                assertEquals("local impulse requires immediate FIFO recovery",0,f.pipe().delays().size());
                assertSame(movement,f.connection.channel.readOutbound());
            } finally {lag.setToggled(false);}
        }
    }

    private static void originalLagRangeModesAndDefaultConfigIdsAreStable() {
        LagRange lag=new LagRange();
        assertEquals("MS",lag.getModeSetting("Delay Mode").getValue());
        for(String old:new String[]{"MS","Ticks","Intave Approach"}) {
            lag.getModeSetting("Delay Mode").setValue(old);
            assertEquals(old,lag.getModeSetting("Delay Mode").getValue());
        }
        lag.getModeSetting("Delay Mode").setValue("Bounded Frame");
        assertEquals("Bounded Frame",lag.getModeSetting("Delay Mode").getValue());
        assertEquals(500D,lag.getNumberSetting("Release Cooldown MS").getValue(),0);
    }
    public static final class Checks {
        @Test public void boundedWindowRequiresFreshFrameAndNeverExpandsC03Past100ms() throws Exception {
            LagRangeBoundedFrameFlowTest.boundedWindowRequiresFreshFrameAndNeverExpandsC03Past100ms();
        }
        @Test public void unhealthyPausedOrLongFrameFlushesAndNeverWithholdsPlayerBody() throws Exception {
            LagRangeBoundedFrameFlowTest.unhealthyPausedOrLongFrameFlushesAndNeverWithholdsPlayerBody();
        }
        @Test public void transactionAcknowledgementRemainsImmediateAndLocalImpulseDrains() throws Exception {
            LagRangeBoundedFrameFlowTest.transactionAcknowledgementRemainsImmediateAndLocalImpulseDrains();
        }
        @Test public void originalLagRangeModesAndDefaultConfigIdsAreStable() throws Exception {
            LagRangeBoundedFrameFlowTest.originalLagRangeModesAndDefaultConfigIdsAreStable();
        }
    }

}
