package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.diagnostics.CorrectionHistory;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.utils.render.HudDrag;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

public class DiagnosticsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static CorrectionHistory history(Diagnostics d)throws Exception{return (CorrectionHistory)field(Diagnostics.class,"corrections").get(d);}
    // As in DiagnosticsWarningSnapshotTest, the fixture rebinds static-final
    // Module.mc with Unsafe. Fresh JVMs keep hot helpers from retaining a prior
    // fake client, without disabling the ordinary Java 8 JIT or changing checks.
    @Test public void liveSamplingDoesNotAccelerateOrFreezeHistory()throws Exception {
        Wave2IsolatedCase.run(Checks.class,"liveSamplingDoesNotAccelerateOrFreezeHistory");
    }
    @Test public void correctionCopyIsCompactAndDoesNotClaimAnAntiCheatCause(){
        assertEquals("2.5 blocks \u00b7 12.0\u00b0\nCause unknown",Diagnostics.correctionMessage(2.5,12));
    }
    @Test public void restartingCorrectionDetectorAllowsANewNotification()throws Exception {
        Diagnostics d=new Diagnostics();
        field(Diagnostics.class,"notified").setBoolean(d,true);
        long previousRun=field(Diagnostics.class,"localWarningRun").getLong(d);
        d.getBooleanSetting("Flag Detector").setValue(false);
        assertFalse(field(Diagnostics.class,"notified").getBoolean(d));
        assertNotEquals(previousRun,field(Diagnostics.class,"localWarningRun").getLong(d));
        d.getBooleanSetting("Flag Detector").setValue(true);
        assertFalse(field(Diagnostics.class,"notified").getBoolean(d));
    }
    @Test public void renderCategoryAndEveryPositionPersistWithoutDuplicateSettingIds()throws Exception {
        Diagnostics a=new Diagnostics();assertEquals(Category.Render,a.getCategory());
        Set<String> ids=new HashSet<>();a.getValues().forEach(v->assertTrue("Duplicate "+v.getId(),ids.add(v.getId())));
        java.nio.file.Path dir=folder.newFolder().toPath();
        ConfigManager configs=new ConfigManager(dir,Collections.singletonList(a),()->false,s->{});configs.initialize(Collections.emptyList());
        for(String name:new String[]{"Performance","Rotation","Network","Flags"}){a.getNumberSetting(name+" X").setValue(79.25);a.getNumberSetting(name+" Y").setValue(111.5);}
        assertTrue(configs.saveConfig("diagnostics"));configs.shutdown();
        Diagnostics b=new Diagnostics();ConfigManager restored=new ConfigManager(dir,Collections.singletonList(b),()->false,s->{});restored.initialize(Collections.emptyList());
        for(Diagnostics.Widget w:b.widgets){assertEquals(79.25,w.x,0);assertEquals(111.5,w.y,0);}restored.shutdown();
        assertFalse(b.getBooleanSetting("Rotation Developer Details").getValue());assertFalse(b.getBooleanSetting("Rotation Console").getValue());
    }
    @Test public void dragNeedsFreshEditorGestureHasOneOwnerAndCanResetEveryWidget() {
        Diagnostics d=new Diagnostics();Diagnostics.Panel first=d.new Panel(d.widgets[0],"Performance","TEST"),second=d.new Panel(d.widgets[1],"Rotation","TEST");
        d.getNumberSetting("Rotation X").setValue(12.0);d.getNumberSetting("Rotation Y").setValue(12.0);d.layout(first,800,600);d.layout(second,800,600);
        HudDrag.context(false,false);HudDrag.context(true,false);
        d.interact(first,true,true,true,30,25,800,600);d.interact(second,true,true,true,30,25,800,600);
        assertTrue(d.widgets[0].dragging);assertFalse(d.widgets[1].dragging);
        d.interact(first,true,true,false,130,125,800,600);assertEquals(112,d.widgets[0].x,0);
        assertEquals(112,d.getNumberSetting("Performance X").getValue(),0);
        HudDrag.context(false,true);d.interact(first,false,true,false,330,225,800,600);assertFalse(d.widgets[0].dragging);
        d.resetPositions();assertEquals(12,d.widgets[0].x,0);assertEquals(276,d.widgets[1].x,0);HudDrag.context(false,false);
    }
    @Test public void snapshotNeverEvaluatesTheRequestPredicateOrChangesAcceptedRotation() {
        RotationManager manager=new RotationManager();manager.beginMotion(0,0);
        manager.requestVisible("test",10,45,12,0,0,()->{throw new AssertionError("Diagnostics evaluated validity");});
        RotationManager.DiagnosticSnapshot s=manager.diagnosticSnapshot();assertEquals("test",s.requestedBy);assertEquals(45,s.requestedYaw,0);assertTrue(s.collecting);assertFalse(s.accepted);
        assertEquals(s.sequence,manager.getMotionSequence());assertFalse(manager.hasEffectiveRotation());
    }
    @Test public void editorReservesControlsAndRestoresOrdinaryPositionsWithoutDraggingThem()throws Exception {
        Wave2IsolatedCase.run(Checks.class,"editorReservesControlsAndRestoresOrdinaryPositionsWithoutDraggingThem");
    }
    @Test public void realS08ApplicationRecordsRelativeCorrectionAfterAckAndIgnoresCancelledPackets()throws Exception {
        Wave2IsolatedCase.run(Checks.class,"realS08ApplicationRecordsRelativeCorrectionAfterAckAndIgnoresCancelledPackets");
    }
    public static final class Checks {
    @Test public void liveSamplingDoesNotAccelerateOrFreezeHistory()throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Diagnostics d=new Diagnostics();
            d.getBooleanSetting("Performance Widget").setValue(false);
            java.lang.reflect.Method sample=Diagnostics.class.getDeclaredMethod("sample",long.class);sample.setAccessible(true);
            sample.invoke(d,1_000_000_000L);
            Deque<?> history=(Deque<?>)field(Diagnostics.class,"pingHistory").get(d);
            assertEquals(1,history.size());
            sample.invoke(d,1_050_000_000L);assertEquals(1_000_000_000L,field(Diagnostics.class,"sampledAt").getLong(d));
            sample.invoke(d,1_100_000_000L);assertEquals(1_100_000_000L,field(Diagnostics.class,"sampledAt").getLong(d));
            assertEquals(1,history.size());
            sample.invoke(d,1_500_000_000L);assertEquals(2,history.size());
        }
    }
    @Test public void editorReservesControlsAndRestoresOrdinaryPositionsWithoutDraggingThem()throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Diagnostics d=new Diagnostics();f.mc.currentScreen=new DiagnosticsEditor(d);
            d.getNumberSetting("Flags X").setValue(80.0);d.getNumberSetting("Flags Y").setValue(270.0);
            Diagnostics.Panel p=d.new Panel(d.widgets[3],"Flags","TEST");for(int i=0;i<12;i++)p.line("Context","");
            d.layout(p,550,320);assertTrue(d.widgets[3].y+d.widgets[3].height<=270);
            HudDrag.context(false,false);HudDrag.context(true,false);d.interact(p,true,true,true,90,300,550,320);assertFalse(d.widgets[3].dragging);
            assertEquals(270,d.getNumberSetting("Flags Y").getValue(),0);d.restoreConfiguredPositions();assertEquals(270,d.widgets[3].y,0);HudDrag.context(false,false);
        }
    }
    @Test public void realS08ApplicationRecordsRelativeCorrectionAfterAckAndIgnoresCancelledPackets()throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Field configClient=field(net.minecraft.src.Config.class,"minecraft");Object oldConfigClient=configClient.get(null);
            configClient.set(null,f.mc);f.mc.renderGlobal=RotationBehaviorHookTest.bare(net.minecraft.client.renderer.RenderGlobal.class);
            try {
            field(Minecraft.class,"mcThread").set(f.mc,Thread.currentThread());
            NetHandlerPlayClient play=f.player.sendQueue;
            field(NetHandlerPlayClient.class,"gameController").set(play,f.mc);field(NetHandlerPlayClient.class,"doneLoadingTerrain").set(play,true);
            f.player.setPosition(10,64,20);f.player.rotationYaw=30;f.player.ticksExisted=200;f.accept("aura",60);
            Diagnostics d=new Diagnostics();d.getBooleanSetting("Performance Widget").setValue(false);d.setToggled(true);
            Subscriber cancel=new Subscriber(){@Subscribe final Listener<EventPacket> listener=new Listener<>(e->{if(e.isPre()&&e.isInbound())e.cancel();});};
            try {
                S08PacketPlayerPosLook packet=new S08PacketPlayerPosLook(.5,64,20,5,0,EnumSet.of(S08PacketPlayerPosLook.EnumFlags.X,S08PacketPlayerPosLook.EnumFlags.Y_ROT));
                Pulsar.BUS.subscribe(cancel);f.network.receivePacket(packet,false);assertNull(history(d).latest());assertNull(f.channel.readOutbound());Pulsar.BUS.unsubscribe(cancel);
                f.network.receivePacket(packet,false);
                CorrectionHistory.Entry e=history(d).latest();assertNotNull(e);assertEquals(.5,e.distance,1e-9);assertEquals(5,e.angle,0);assertTrue(e.possibleSetback);
                assertTrue(e.context,e.context.contains("aura"));assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer.C06PacketPlayerPosLook);
                assertEquals(10.5,f.player.posX,0);assertEquals(35,f.player.rotationYaw,0);assertNull(f.channel.readOutbound());
                f.player.ticksExisted=1;f.network.receivePacketSilent(packet);assertTrue(history(d).latest().initialization);assertFalse(history(d).latest().possibleSetback);
                d.getBooleanSetting("Flag Detector").setValue(false);assertNull(history(d).latest());
            }finally{Pulsar.BUS.unsubscribe(cancel);d.setToggled(false);}
            }finally{configClient.set(null,oldConfigClient);}
        }
    }
    }
}
