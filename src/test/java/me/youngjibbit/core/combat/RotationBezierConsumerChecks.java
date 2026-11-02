package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.event.EventPriority;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/** Public settings, real module listeners, Ordinary walking and actual EmbeddedChannel output. */
public class RotationBezierConsumerChecks {
    private static void curve(Module module,boolean enabled) {
        assertNotNull("Missing optional Speed Curve setting",module.getModeSetting("Speed Curve"));
        module.getModeSetting("Speed Curve").setValue(enabled?"Bezier":"Off");
        if(enabled){module.getNumberSetting("Bezier Strength").setValue(.85);module.getNumberSetting("Bezier Duration").setValue(1000D);}
    }
    private static void plain(Module module){module.getModeSetting("Randomization Style").setValue("Aus");}
    private static long count(List<Object> packets,Class<?> type){return packets.stream().filter(type::isInstance).count();}
    private static void packetTrace(StringBuilder trace,RotationBezierFixture s,List<Object> packets) {
        for(Object packet:packets) {
            trace.append(packet.getClass().getSimpleName()).append(':');
            if(packet instanceof C03PacketPlayer){C03PacketPlayer p=(C03PacketPlayer)packet;trace.append(p.getRotating()).append('/');RotationLegacyGoldenTest.bits(trace,p.getYaw(),p.getPitch());}
        }
        trace.append(s.rotation.getConfirmedOwner()).append(':');
        RotationLegacyGoldenTest.bits(trace,s.rotation.getEffectiveYaw(),s.rotation.getEffectivePitch(),s.f.player.rotationYaw,s.f.player.rotationPitch);
    }
    @Test public void legacyConsumersGolden() throws Exception {
        StringBuilder trace=new StringBuilder();
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            for(boolean visible:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(String preset:new String[]{"Aus","Sanft","Organisch","Dynamisch","Benutzerdefiniert"}) {
                final int choice=mode;
                s.start(visible,m->{m.getModeSetting(visible?"Rotations":"Rot Mode").setValue((visible?new String[]{"Normal","Legit","Test"}:new String[]{"Basic","Smooth","Advanced"})[choice]);m.getModeSetting("Randomization Style").setValue(preset);});
                trace.append(visible).append('/').append(mode).append('/').append(preset).append('\n');
                for(int tick=0;tick<16;tick++) {
                    s.module.getModeSetting("Random Mode").setValue(new String[]{"Noise","Basic","OnlyRotation","Doubled"}[tick%4]);
                    s.f.player.rotationYaw=tick%3==0?2.1f:0;s.f.player.rotationPitch=tick%4==0?-1.7f:0;
                    packetTrace(trace,s,s.walk(tick*67_000_000L));
                    assertTrue(s.rotation.isMotionAccepted());
                }
            }
        }
        RotationLegacyGoldenTest.verify("CONSUMERS",RotationLegacyGoldenTest.CONSUMERS_EXPECTED,trace.toString());
    }
    @Test public void optionalCurveReachesBothRealConsumersWithBoundedQuantizedSteps() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) { for(boolean visible:new boolean[]{false,true}) {
            String[] traces=new String[2];
            for(int mode=0;mode<2;mode++) {
                final boolean enabled=mode==1;s.start(visible,m->{plain(m);curve(m,enabled);});StringBuilder trace=new StringBuilder();
                for(int tick=0;tick<12;tick++) {
                    float previousYaw=visible?s.f.player.rotationYaw:s.rotation.getEffectiveYaw();
                    float previousPitch=visible?s.f.player.rotationPitch:s.rotation.getEffectivePitch();
                    List<Object> packets=s.walk(tick*50_000_000L);assertTrue(s.rotation.isMotionAccepted());
                    assertEquals(visible?"aimassist":"aura",s.rotation.getConfirmedOwner());
                    float yaw=s.rotation.getEffectiveYaw(),pitch=s.rotation.getEffectivePitch();
                    double q=RotationSensitivity.mouseStep(s.f.mc.gameSettings.mouseSensitivity);
                    assertTrue(Math.abs(MoveFixController.wrap(yaw-previousYaw))<=7+q/2+1e-4);
                    assertTrue(Math.abs(pitch-previousPitch)<=9+q/2+1e-4);
                    double counts=(yaw-previousYaw)/q;assertEquals(Math.rint(counts),counts,2e-4);
                    assertEquals(visible?yaw:0,s.f.player.rotationYaw,0);
                    assertEquals(0,count(packets,C02PacketUseEntity.class));assertEquals(0,count(packets,C0APacketAnimation.class));
                    packetTrace(trace,s,packets);
                }
                traces[mode]=trace.toString();
            }
            assertNotEquals("explicit feature must affect actual consumer output",traces[0],traces[1]);
        } }
    }
    private RotationProfile profile(RotationBezierFixture s) throws Exception {
        return ((RotationSettings)RotationBehaviorHookTest.field(s.module.getClass(),"rotationSettings").get(s.module)).snapshot();
    }
    @Test public void duplicateMotionCalculationsConsumeOnlyOneStep() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            s.start(false,m->{plain(m);curve(m,true);});s.walk(0);RotationProfile p=profile(s);
            s.clock.set(50_000_000L);s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);
            float[] first=s.rotation.calculatePlanRotations("aura",0,0,s.target,120,60,2,p);
            for(int i=0;i<30;i++){s.clock.addAndGet(1_000_000L);assertArrayEquals("same motion cannot spend elapsed time twice",first,s.rotation.calculatePlanRotations("aura",0,0,s.target,120,60,2,p),0);}
            s.rotation.request("aura",20,first[0],first[1]);s.rotation.finishMotion(first[0],first[1],true);
            assertEquals(first[0],s.rotation.getEffectiveYaw(),0);
        }
    }
    @Test public void rejectedAndHeldC03NeverCommitCandidateAngles() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) { for(boolean visible:new boolean[]{false,true}) {
            s.start(visible,m->{plain(m);curve(m,true);});s.walk(0);float acceptedYaw=s.rotation.getEffectiveYaw(),acceptedPitch=s.rotation.getEffectivePitch();
            Listener<EventPacket> cancel=new Listener<>(EventPacket.class,event->{if(event.isOutbound()&&event.isPre()&&event.getPacket() instanceof C03PacketPlayer)event.cancel();});
            Pulsar.BUS.subscribe(cancel);
            try{assertEquals(0,count(s.walk(50_000_000L),C03PacketPlayer.class));}finally{Pulsar.BUS.unsubscribe(cancel);}
            assertFalse(s.rotation.isMotionAccepted());assertEquals(acceptedYaw,s.rotation.getEffectiveYaw(),0);assertEquals(acceptedPitch,s.rotation.getEffectivePitch(),0);
            Object owner=new Object();s.f.network.getClientPackets().delays().request(owner,500,(packet,flow)->flow==EventFlow.OUTBOUND&&packet instanceof C03PacketPlayer);
            assertEquals(0,count(s.walk(100_000_000L),C03PacketPlayer.class));assertFalse(s.rotation.isMotionAccepted());
            assertEquals(acceptedYaw,s.rotation.getEffectiveYaw(),0);assertTrue(s.f.network.getClientPackets().delays().size()>0);
            // Dispose the old connection/queue, then establish a genuinely fresh scene acquisition.
            s.f.network.getClientPackets().worldChanged();s.drain();s.walk(150_000_000L);
            assertTrue(s.rotation.isMotionAccepted());assertEquals(visible?"aimassist":"aura",s.rotation.getConfirmedOwner());
        } }
    }
    @Test public void targetLossAndForeignOwnerCannotLeakVisibleAssistance() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            s.start(true,m->{plain(m);curve(m,true);});s.walk(0);assertSame(s.target,((AimAssist)s.module).getTarget());
            s.f.mc.theWorld.loadedEntityList.clear();float camera=s.f.player.rotationYaw;s.walk(50_000_000L);
            assertNull(((AimAssist)s.module).getTarget());assertEquals(camera,s.f.player.rotationYaw,0);
            s.f.mc.theWorld.loadedEntityList.add(s.target);s.walk(100_000_000L);assertEquals("aimassist",s.rotation.getConfirmedOwner());
            s.f.accept("scaffold",90);camera=s.f.player.rotationYaw;s.walk(150_000_000L);
            assertNull(((AimAssist)s.module).getTarget());assertEquals(camera,s.f.player.rotationYaw,0);
            s.rotation.reset();s.walk(200_000_000L);assertEquals("aimassist",s.rotation.getConfirmedOwner());
        }
    }
    private RotationTrajectory ownedTrajectory(RotationBezierFixture s) throws Exception {
        Map<?,?> states=(Map<?,?>)RotationBehaviorHookTest.field(RotationManager.class,"states").get(s.rotation);
        Object state=states.get("aimassist");assertNotNull("original per-source trajectory remains available",state);
        return (RotationTrajectory)RotationBehaviorHookTest.field(state.getClass(),"trajectory").get(state);
    }
    private float[] rearmTrace(RotationBezierFixture s,int outcome,boolean explicitReset) throws Exception {
        s.start(true,m->{plain(m);curve(m,true);});s.walk(0);
        RotationTrajectory trajectory=ownedTrajectory(s);assertTrue(trajectory.hasSpeedCurve());
        s.f.player.rotationYaw=s.f.player.rotationPitch=0;
        Listener<EventPacket> cancel=new Listener<>(EventPacket.class,event->{
            if(outcome==0 && event.isOutbound() && event.isPre() && event.getPacket() instanceof C03PacketPlayer)event.cancel();
        });
        Listener<EventMotion> foreign=new Listener<>(EventMotion.class,event->{
            if(outcome==2 && event.isPre())s.rotation.request("wave05-foreign",100,0,0);
        },EventPriority.LOWEST);
        Object holdOwner=new Object();
        if(outcome==1)s.f.network.getClientPackets().delays().request(holdOwner,500,
                (packet,flow)->flow==EventFlow.OUTBOUND && packet instanceof C03PacketPlayer);
        Pulsar.BUS.subscribe(cancel);Pulsar.BUS.subscribe(foreign);
        try {
            List<Object> boundary=s.walk(50_000_000L);
            if(outcome==0 || outcome==1){assertFalse(s.rotation.isMotionAccepted());assertEquals(0,count(boundary,C03PacketPlayer.class));}
            if(outcome==2){assertTrue(s.rotation.isMotionAccepted());assertEquals("wave05-foreign",s.rotation.getConfirmedOwner());}
        } finally {Pulsar.BUS.unsubscribe(foreign);Pulsar.BUS.unsubscribe(cancel);}
        if(outcome>=0)assertFalse("the terminal outcome must invalidate the curve before another step",trajectory.hasSpeedCurve());
        else assertTrue("ordinary accepted continuation must retain the acquisition",trajectory.hasSpeedCurve());
        if(explicitReset)trajectory.resetSpeedCurve();
        // Remove only the synthetic hold. A scene reset here would mask a missing finishMotion hook.
        if(outcome==1){assertTrue(s.f.network.getClientPackets().delays().size()>0);s.f.network.getClientPackets().delays().discard();}
        float[] result=new float[16];
        for(int tick=2;tick<6;tick++) {
            s.f.player.rotationYaw=s.f.player.rotationPitch=0;
            List<Object> packets=s.walk(tick*50_000_000L);
            assertTrue(s.rotation.isMotionAccepted());assertEquals("aimassist",s.rotation.getConfirmedOwner());
            C03PacketPlayer movement=null;for(Object packet:packets)if(packet instanceof C03PacketPlayer)movement=(C03PacketPlayer)packet;
            assertNotNull(movement);assertTrue(trajectory.hasSpeedCurve());
            int index=(tick-2)*4;result[index]=movement.getYaw();result[index+1]=movement.getPitch();
            result[index+2]=s.f.player.rotationYaw;result[index+3]=s.f.player.rotationPitch;
        }
        return result;
    }
    @Test public void rejectedHeldAndForeignOutcomesMatchExplicitRearmControl() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            float[] continuous=rearmTrace(s,-1,false);
            for(int outcome=0;outcome<3;outcome++) {
                float[] natural=rearmTrace(s,outcome,false),explicit=rearmTrace(s,outcome,true);
                assertArrayEquals("natural invalidation must match an idempotent explicit rearm: "+outcome,explicit,natural,0);
                double difference=0;for(int i=0;i<natural.length;i++)difference+=Math.abs(natural[i]-continuous[i]);
                assertTrue("control must distinguish reacquisition from uninterrupted phase: "+outcome,difference>.01);
            }
        }
    }
    @Test public void laterManualInputSurvivesVisibleBezierResolution() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            s.start(true,m->{plain(m);curve(m,true);});s.rotation.bindConnection(s.f.network.getClientPackets());
            s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);EventMotion event=new EventMotion(.5,64,.5,0,0,true);Pulsar.BUS.post(event);
            s.rotation.resolve(event);float assistanceYaw=s.f.player.rotationYaw,assistancePitch=s.f.player.rotationPitch;
            s.start(true,m->{plain(m);curve(m,true);});s.rotation.bindConnection(s.f.network.getClientPackets());
            s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);event=new EventMotion(.5,64,.5,0,0,true);Pulsar.BUS.post(event);
            s.f.player.rotationYaw=7;s.f.player.rotationPitch=2;s.rotation.resolve(event);
            assertEquals(assistanceYaw+7,s.f.player.rotationYaw,1e-5);assertEquals(assistancePitch+2,s.f.player.rotationPitch,1e-5);
            assertEquals(s.f.player.rotationYaw,event.getYaw(),0);assertEquals(s.f.player.rotationPitch,event.getPitch(),0);
        }
    }
    private Object envelope(RotationTrajectory trajectory) throws Exception {
        return RotationBehaviorHookTest.field(RotationTrajectory.class,"speedEnvelope").get(trajectory);
    }
    private float[] standaloneTrace(RotationBezierFixture s,boolean externalPose,boolean explicitReset) throws Exception {
        s.start(true,m->{plain(m);curve(m,true);});s.walk(0);
        RotationTrajectory trajectory=ownedTrajectory(s);
        assertEquals("aimassist",s.rotation.getConfirmedOwner());assertTrue(trajectory.hasSpeedCurve());
        Object firstEnvelope=envelope(trajectory);float cameraYaw=s.f.player.rotationYaw,cameraPitch=s.f.player.rotationPitch;
        if(externalPose) {
            C03PacketPlayer.C05PacketPlayerLook replacement=new C03PacketPlayer.C05PacketPlayerLook(73,-12,true);
            assertSame("standalone C05 actually reaches local transport",replacement,s.f.player.sendQueue.addToSendQueueTracked(replacement));
            List<Object> transported=s.drain();assertEquals(1,count(transported,C03PacketPlayer.class));assertSame(replacement,transported.get(0));
            assertNull("different observed wire pose withdraws the old owner",s.rotation.getConfirmedOwner());
            assertEquals(73,s.rotation.getEffectiveYaw(),0);assertEquals(-12,s.rotation.getEffectivePitch(),0);
            assertEquals("wire observation does not rewrite the camera",cameraYaw,s.f.player.rotationYaw,0);
            assertEquals(cameraPitch,s.f.player.rotationPitch,0);
            assertSame("owner loss must not be hidden by replacing the source state",trajectory,ownedTrajectory(s));
            if(explicitReset)trajectory.resetSpeedCurve();
            assertFalse("transported unmatched pose invalidates the acquisition before the next motion",trajectory.hasSpeedCurve());
        }
        float[] trace=new float[16];
        for(int tick=1;tick<=4;tick++) {
            // Identical camera inputs prevent accepted-wire base differences from masquerading as rearming.
            s.f.player.rotationYaw=s.f.player.rotationPitch=0;
            List<Object> packets=s.walk(tick*50_000_000L);
            assertTrue(s.rotation.isMotionAccepted());assertEquals("aimassist",s.rotation.getConfirmedOwner());
            assertTrue("ordinary matching C03 must preserve its acquisition",trajectory.hasSpeedCurve());
            if(!externalPose)assertSame("ordinary matching acceptance must not select fresh controls",firstEnvelope,envelope(trajectory));
            C03PacketPlayer movement=null;for(Object packet:packets)if(packet instanceof C03PacketPlayer)movement=(C03PacketPlayer)packet;
            assertNotNull(movement);int offset=(tick-1)*4;
            trace[offset]=movement.getYaw();trace[offset+1]=movement.getPitch();
            trace[offset+2]=s.f.player.rotationYaw;trace[offset+3]=s.f.player.rotationPitch;
        }
        return trace;
    }
    @Test public void standaloneAcceptedPoseLossMatchesExplicitRearmOnNextRealAcquisition() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            float[] explicit=standaloneTrace(s,true,true);
            float[] natural=standaloneTrace(s,true,false);
            assertArrayEquals("standalone owner loss must match explicit acquisition reset",explicit,natural,0);
            float[] uninterrupted=standaloneTrace(s,false,false);double difference=0;
            for(int index=0;index<natural.length;index++)difference+=Math.abs(natural[index]-uninterrupted[index]);
            assertTrue("paired oracle distinguishes reset from uninterrupted progress",difference>.01);
        }
    }
    @Test public void ordinaryMatchingAcceptedC03KeepsOneEnvelopeAcrossRealMotions() throws Exception {
        try(RotationBezierFixture s=new RotationBezierFixture()) {
            float[] first=standaloneTrace(s,false,false),repeat=standaloneTrace(s,false,false);
            assertArrayEquals(first,repeat,0);
        }
    }
}
