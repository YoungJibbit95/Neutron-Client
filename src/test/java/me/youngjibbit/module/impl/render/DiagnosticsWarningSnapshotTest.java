package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.LocalCorrectionSnapshot;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.EnumSet;
import static org.junit.Assert.*;

public class DiagnosticsWarningSnapshotTest {
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static final class Live implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture fixture;
        final Diagnostics diagnostics;
        final Field config;final Object oldConfig;
        Live()throws Exception{
            fixture=new RotationBehaviorHookTest.Fixture();config=field(net.minecraft.src.Config.class,"minecraft");
            oldConfig=config.get(null);config.set(null,fixture.mc);
            fixture.mc.renderGlobal=RotationBehaviorHookTest.bare(net.minecraft.client.renderer.RenderGlobal.class);
            field(Minecraft.class,"mcThread").set(fixture.mc,Thread.currentThread());
            NetHandlerPlayClient play=fixture.player.sendQueue;
            field(NetHandlerPlayClient.class,"gameController").set(play,fixture.mc);
            field(NetHandlerPlayClient.class,"doneLoadingTerrain").set(play,true);
            fixture.player.setPosition(10,64,20);fixture.player.rotationYaw=30;fixture.player.ticksExisted=200;
            diagnostics=new Diagnostics();diagnostics.getBooleanSetting("Performance Widget").setValue(false);diagnostics.setToggled(true);
        }
        S08PacketPlayerPosLook packet(){return new S08PacketPlayerPosLook(.5,64,20,5,0,
                EnumSet.of(S08PacketPlayerPosLook.EnumFlags.X,S08PacketPlayerPosLook.EnumFlags.Y_ROT));}
        LocalCorrectionSnapshot apply(){fixture.network.receivePacket(packet(),false);return diagnostics.getLocalCorrectionSnapshot(System.nanoTime());}
        public void close()throws Exception{try{diagnostics.setToggled(false);}finally{config.set(null,oldConfig);fixture.close();}}
    }
    @Test public void onlyAnAppliedCorrectionPublishesAndReadsNeverSendAnotherPacket()throws Exception{
        try(Live live=new Live()){
            Subscriber cancel=new Subscriber(){@Subscribe final Listener<EventPacket> listener=new Listener<>(e->{if(e.isPre()&&e.isInbound())e.cancel();});};
            Pulsar.BUS.subscribe(cancel);
            try{assertNull(live.apply());assertNull(live.fixture.channel.readOutbound());}finally{Pulsar.BUS.unsubscribe(cancel);}
            LocalCorrectionSnapshot snapshot=live.apply();assertNotNull(snapshot);
            assertEquals(.5,snapshot.getDistance(),1e-9);assertEquals(5,snapshot.getAngle(),0);
            assertTrue(live.fixture.channel.readOutbound() instanceof C03PacketPlayer.C06PacketPlayerPosLook);
            for(int i=0;i<100;i++)assertSame(snapshot,live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
            assertNull(live.fixture.channel.readOutbound());
            live.diagnostics.clearHistory();assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
            live.fixture.player.ticksExisted=1;live.fixture.network.receivePacketSilent(live.packet());
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
        }
    }
    @Test public void expiryWorldLossAndDisabledDetectorCannotReviveOldEvidence()throws Exception{
        try(Live live=new Live()){
            LocalCorrectionSnapshot first=live.apply();assertNotNull(first);
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(first.getObservedAtNanos()+LocalCorrectionSnapshot.TTL_NANOS+1));
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(first.getObservedAtNanos()));
            assertNotNull(live.apply());net.minecraft.client.multiplayer.WorldClient oldWorld=live.fixture.mc.theWorld;
            live.fixture.mc.theWorld=null;
            try{assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));}finally{live.fixture.mc.theWorld=oldWorld;}
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
            LocalCorrectionSnapshot next=live.apply();assertNotNull(next);
            live.diagnostics.getBooleanSetting("Flag Detector").setValue(false);
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
            live.diagnostics.getBooleanSetting("Flag Detector").setValue(true);
            assertNull(live.diagnostics.getLocalCorrectionSnapshot(System.nanoTime()));
            LocalCorrectionSnapshot renewed=live.apply();assertNotNull(renewed);assertNotEquals(next.getRunId(),renewed.getRunId());
        }
    }
}
