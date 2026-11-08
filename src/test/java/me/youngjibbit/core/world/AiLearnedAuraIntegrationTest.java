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
import org.junit.Test;
import static org.junit.Assert.*;

/** Real Aura, rotation, walking and controller C03/C02; model weights are test-only. */
public class AiLearnedAuraIntegrationTest {
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

    @Test public void missingOrCorruptedModelNeverGrantsAiC03OrC02() throws Exception {
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

    @Test public void validSyntheticModelReachesAcceptedWalkingC03() throws Exception {
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

    @Test public void acceptedAiPoseReusesExistingRaycastAndControllerC02() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);inject(f,testModel(3,3));
            boolean acceptedHit=false;
            for(int i=0;i<70;i++) {
                List<Object> sent=f.walk();
                if(count(sent,C02PacketUseEntity.class)>0) {
                    assertEquals("aura",rotation(f).getConfirmedOwner());
                    assertTrue(rotation(f).isAiAuraMotionAccepted());
                    acceptedHit=true;break;
                }
            }
            assertTrue("only the original tracked controller emits C02",acceptedHit);
        }
    }

    @Test public void targetSwitchBeginsNewHoldAndDoesNotReuseOldC03() throws Exception {
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

    @Test public void accountSwitchAndFocusLossDiscardOldModelState() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.player.usingItem=true;inject(f,testModel(3,3));
            for(int i=0;i<10;i++)f.walk();
            assertTrue(rotation(f).isAiAuraMotionAccepted());
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
    @Test public void cancelledC03NeverConfersC02Authority() throws Exception {
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

    @Test public void twentySixtyOneFortyFourTwoFortyHzRenderQueriesDoNotInfer() throws Exception {
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

    @Test public void rotationsOffNeverFallsBackToCameraOnlyAiAttack() throws Exception {
        try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
            select(f);f.run.fixture.aura.getBooleanSetting("Rotations").setValue(false);
            for(int i=0;i<5;i++) {
                assertEquals(0,count(f.walk(),C02PacketUseEntity.class));
                assertFalse(rotation(f).isAiAuraMotionAccepted());
            }
        }
    }
}
