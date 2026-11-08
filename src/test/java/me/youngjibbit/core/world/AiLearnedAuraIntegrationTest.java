package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationProfile;
import me.youngjibbit.core.combat.ai.NeutronAiCheckpoint;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Session;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real Aura, rotation, walking and controller C03/C02; model weights are test-only. */
@RunWith(Parameterized.class)
public class AiLearnedAuraIntegrationTest {
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
            {"missingOrCorruptedModelNeverGrantsAiC03OrC02"},
            {"validSyntheticModelReachesAcceptedWalkingC03"},
            {"acceptedAiPoseReusesExistingRaycastAndControllerC02"},
            {"targetSwitchBeginsNewHoldAndDoesNotReuseOldC03"},
            {"accountSwitchAndFocusLossDiscardOldModelState"},
            {"cancelledC03NeverConfersC02Authority"},
            {"twentySixtyOneFortyFourTwoFortyHzRenderQueriesDoNotInfer"},
            {"rotationsOffNeverFallsBackToCameraOnlyAiAttack"}
        });
    }
    private final String caseName;
    public AiLearnedAuraIntegrationTest(String caseName) { this.caseName=caseName; }
    @Test public void realAuraPathInIsolatedJava8Jvm() throws Exception {
        Wave2IsolatedCase.run(Checks.class,caseName);
    }

    /** The original production-contract assertions run without JIT-inlined static fixture state. */
    private static Session session() {
        return new Session("SyntheticAI", "synthetic-id", "not-a-user-token", "legacy");
    }
    private static NeutronAiCheckpoint testModel(float yaw,float pitch) throws Exception {
        byte[] bytes=new byte[NeutronAiCheckpoint.SERIALIZED_BYTES];
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0x4e414931);b.putInt(1);
        b.putInt(NeutronAiCheckpoint.WIDTH);b.putInt(NeutronAiCheckpoint.HISTORY);
        b.putInt(NeutronAiCheckpoint.HIDDEN);b.putInt(NeutronAiCheckpoint.OUTPUTS);
        b.putInt(NeutronAiCheckpoint.PARAMETERS);
        b.putInt(900);b.putInt(40);b.putInt(12);b.putInt(10);
        b.putFloat(.25f);b.putFloat(.34f);
        float[] weights=new float[NeutronAiCheckpoint.PARAMETERS];
        int p=NeutronAiCheckpoint.HIDDEN*(NeutronAiCheckpoint.INPUTS+1);
        weights[p+NeutronAiCheckpoint.HIDDEN]=yaw;
        weights[p+2*NeutronAiCheckpoint.HIDDEN+1]=pitch;
        for(float weight:weights)b.putFloat(weight);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                java.util.Arrays.copyOf(bytes,bytes.length-NeutronAiCheckpoint.SHA_BYTES));
        System.arraycopy(digest,0,bytes,bytes.length-digest.length,digest.length);
        NeutronAiCheckpoint model=NeutronAiCheckpoint.decode(bytes);
        assertNotNull("synthetic weights structurally valid",model);
        return model;
    }
    private static void select(Wave05AuraSelectionChecks.SelectionRun f) {
        f.run.fixture.mc.session=session();
        f.run.fixture.aura.getModeSetting("Rot Mode").setValue("AI Learned");
        f.run.fixture.aura.getBooleanSetting("Click Randomization Enabled").setValue(false);
    }
    private static RotationManager rotation(Wave05AuraSelectionChecks.SelectionRun f) {
        return f.run.fixture.runtime.getRotationManager();
    }
    private static void inject(Wave05AuraSelectionChecks.SelectionRun f,
                               NeutronAiCheckpoint checkpoint) throws Exception {
        // Production's first PRE binds the world/player and resets previously
        // unscoped rotation state. Prime that *actual* walking boundary before
        // installing the synthetic checkpoint; otherwise we test a model that
        // was correctly disposed by the subsequent first scene acquisition.
        // No model means this priming step is required to HOLD without C02.
        List<Object> priming=f.walk();
        assertEquals(0,count(priming,C02PacketUseEntity.class));
        assertFalse(rotation(f).isAiAuraMotionAccepted());
        RotationManager manager=rotation(f);
        manager.bindConnection(f.run.fixture.network.getClientPackets());
        // No production model loader; this package-private entry is only for JUnit.
        Method m=RotationManager.class.getDeclaredMethod("installAiTestCheckpoint",NeutronAiCheckpoint.class);
        m.setAccessible(true);
        assertTrue((Boolean)m.invoke(manager,checkpoint));
    }
    private static long inferences(RotationManager r) throws Exception {
        Method m=RotationManager.class.getDeclaredMethod("aiInferenceCountForTests");
        m.setAccessible(true);
        return (Long)m.invoke(r);
    }
    private static long count(List<Object> sent,Class<?> type) {
        return sent.stream().filter(type::isInstance).count();
    }
    /** Failing synthetic CI evidence; no additional motion, packets or authority. */
    private static String aiDebug(Wave05AuraSelectionChecks.SelectionRun f,int tick,List<Object> sent)
            throws Exception {
        RotationManager r=rotation(f);
        java.lang.reflect.Field statesField=RotationManager.class.getDeclaredField("states");
        statesField.setAccessible(true);
        java.util.Map<?,?> states=(java.util.Map<?,?>)statesField.get(r);
        Object auraState=states.get("aura");
        String model="missing-state";
        if(auraState!=null) {
            java.lang.reflect.Field aiField=auraState.getClass().getDeclaredField("ai");
            aiField.setAccessible(true);
            Object ai=aiField.get(auraState);
            model=ai==null?"missing-core":
                    ((me.youngjibbit.core.combat.ai.NeutronAiCore)ai).hasModel()?"present":"disposed";
        }
        return tick+":t="+f.run.scene.motionTime.get()
                +" owner="+r.getConfirmedOwner()
                +" accepted="+r.isAiAuraMotionAccepted()
                +" yaw="+r.getEffectiveYaw()+" pitch="+r.getEffectivePitch()
                +" infer="+inferences(r)+" model="+model
                +" seq="+r.getMotionSequence()
                +" mode="+f.run.fixture.aura.getModeSetting("Rot Mode").getValue()
                +" focused="+f.run.fixture.mc.inGameHasFocus
                +" pipeline="+f.run.fixture.network.getClientPackets().isActive()
                +" C03="+count(sent,C03PacketPlayer.class)
                +" C02="+count(sent,C02PacketUseEntity.class)
                +" targetFirst="+(f.run.fixture.aura.getActiveTarget()==f.first);
    }

    private static void missingOrCorruptedModelNeverGrantsAiC03OrC02() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);
            assertNull(NeutronAiCheckpoint.decode(new byte[NeutronAiCheckpoint.SERIALIZED_BYTES]));
            for(int i=0;i<7;i++) {
                assertEquals(0,count(f.walk(),C02PacketUseEntity.class));
                assertFalse(rotation(f).isAiAuraMotionAccepted());
                assertNotEquals("aura",rotation(f).getConfirmedOwner());
            }
        }
    }

    private static void validSyntheticModelReachesAcceptedWalkingC03() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.player.usingItem=true;inject(f,testModel(3,3));
            f.walk(); // first acquisition must HOLD
            assertFalse(rotation(f).isAiAuraMotionAccepted());
            boolean accepted=false;
            for(int i=0;i<35;i++) {
                List<Object> sent=f.walk();
                accepted |= rotation(f).isAiAuraMotionAccepted();
                assertEquals(0,count(sent,C02PacketUseEntity.class));
            }
            assertTrue("model-fed real Aura→C03 acceptance",accepted);
            assertTrue(inferences(rotation(f))>0);
        }
    }

    private static void acceptedAiPoseReusesExistingRaycastAndControllerC02() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);inject(f,testModel(3,3));
            boolean acceptedHit=false;
            java.util.List<String> trace=new java.util.ArrayList<>();
            for(int i=0;i<70;i++) {
                List<Object> sent=f.walk();
                trace.add(aiDebug(f,i,sent));
                if(count(sent,C02PacketUseEntity.class)>0) {
                    assertEquals("aura",rotation(f).getConfirmedOwner());
                    assertTrue(rotation(f).isAiAuraMotionAccepted());
                    acceptedHit=true;break;
                }
            }
            assertTrue("only the original tracked controller emits C02; "+trace,acceptedHit);
        }
    }

    private static void targetSwitchBeginsNewHoldAndDoesNotReuseOldC03() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.player.usingItem=true;inject(f,testModel(3,3));
            for(int i=0;i<12;i++)f.walk();
            assertTrue(rotation(f).isAiAuraMotionAccepted());
            f.run.scene.loadedEntityList.clear();
            f.run.scene.loadedEntityList.add(f.second);
            f.walk();
            assertSame(f.second,f.run.fixture.aura.getActiveTarget());
            assertFalse("target identity change resets neural motion",rotation(f).isAiAuraMotionAccepted());
        }
    }

    private static void accountSwitchAndFocusLossDiscardOldModelState() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.player.usingItem=true;inject(f,testModel(3,3));
            java.util.List<String> trace=new java.util.ArrayList<>();
            for(int i=0;i<10;i++)trace.add(aiDebug(f,i,f.walk()));
            assertTrue("AI must accept before session transition; "+trace,
                    rotation(f).isAiAuraMotionAccepted());
            f.run.fixture.mc.session=session(); // different opaque account object
            f.walk();
            assertFalse(rotation(f).isAiAuraMotionAccepted());
            f.run.fixture.mc.inGameHasFocus=false;
            f.walk();
            f.run.fixture.mc.inGameHasFocus=true;
            f.walk();
            assertFalse(rotation(f).isAiAuraMotionAccepted());
        }
    }

    public static final class RejectC03 implements Subscriber {
        @Subscribe final Listener<EventPacket> packets=new Listener<>(event->{
            if(event.isOutbound() && event.isPre() && event.getPacket() instanceof C03PacketPlayer)
                event.cancel();
        });
    }
    private static void cancelledC03NeverConfersC02Authority() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);inject(f,testModel(3,3));f.walk();
            RejectC03 rejected=new RejectC03();
            Pulsar.BUS.subscribe(rejected);
            try {
                for(int i=0;i<6;i++) {
                    assertEquals(0,count(f.walk(),C02PacketUseEntity.class));
                    assertFalse(rotation(f).isAiAuraMotionAccepted());
                }
            } finally {Pulsar.BUS.unsubscribe(rejected);}
            assertEquals(0,count(f.walk(),C02PacketUseEntity.class));
        }
    }

    private static void twentySixtyOneFortyFourTwoFortyHzRenderQueriesDoNotInfer() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.player.usingItem=true;inject(f,testModel(3,3));
            for(int hz:new int[]{20,60,144,240}) {
                f.walk();long before=inferences(rotation(f));
                for(int i=0;i<hz;i++) {
                    RotationProfile p=new RotationProfile();p.aiLearned=true;
                    assertNull(rotation(f).calculateRotations("aura",0,0,f.first,false,p,false));
                }
                assertEquals("no render-time inference",before,inferences(rotation(f)));
            }
        }
    }

    private static void rotationsOffNeverFallsBackToCameraOnlyAiAttack() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.aura.getBooleanSetting("Rotations").setValue(false);
            for(int i=0;i<5;i++) {
                assertEquals(0,count(f.walk(),C02PacketUseEntity.class));
                assertFalse(rotation(f).isAiAuraMotionAccepted());
            }
        }
    }
    /** The unchanged assertions are executed one-by-one through Wave2IsolatedCase. */
    public static final class Checks {
        @Test public void missingOrCorruptedModelNeverGrantsAiC03OrC02() throws Exception {
            AiLearnedAuraIntegrationTest.missingOrCorruptedModelNeverGrantsAiC03OrC02();
        }
        @Test public void validSyntheticModelReachesAcceptedWalkingC03() throws Exception {
            AiLearnedAuraIntegrationTest.validSyntheticModelReachesAcceptedWalkingC03();
        }
        @Test public void acceptedAiPoseReusesExistingRaycastAndControllerC02() throws Exception {
            AiLearnedAuraIntegrationTest.acceptedAiPoseReusesExistingRaycastAndControllerC02();
        }
        @Test public void targetSwitchBeginsNewHoldAndDoesNotReuseOldC03() throws Exception {
            AiLearnedAuraIntegrationTest.targetSwitchBeginsNewHoldAndDoesNotReuseOldC03();
        }
        @Test public void accountSwitchAndFocusLossDiscardOldModelState() throws Exception {
            AiLearnedAuraIntegrationTest.accountSwitchAndFocusLossDiscardOldModelState();
        }
        @Test public void cancelledC03NeverConfersC02Authority() throws Exception {
            AiLearnedAuraIntegrationTest.cancelledC03NeverConfersC02Authority();
        }
        @Test public void twentySixtyOneFortyFourTwoFortyHzRenderQueriesDoNotInfer() throws Exception {
            AiLearnedAuraIntegrationTest.twentySixtyOneFortyFourTwoFortyHzRenderQueriesDoNotInfer();
        }
        @Test public void rotationsOffNeverFallsBackToCameraOnlyAiAttack() throws Exception {
            AiLearnedAuraIntegrationTest.rotationsOffNeverFallsBackToCameraOnlyAiAttack();
        }
    }

}
