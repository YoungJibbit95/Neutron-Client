package me.youngjibbit.core.combat.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.Arrays;
import static org.junit.Assert.*;

public class NeutronAiManualRecorderTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private static final class Lifetime {
        final Object account=new Object(), world=new Object(), player=new Object();
        final Object connection=new Object(), target=new Object();
        final long epoch=4, generation=10;
        NeutronAiManualRecorder.Scope scope() {
            return new NeutronAiManualRecorder.Scope(account,world,player,connection,target,epoch,generation);
        }
    }

    private static boolean manual(NeutronAiManualRecorder recorder,
                                  NeutronAiManualRecorder.Scope scope,long ms) {
        return recorder.record(scope,NeutronAiManualRecorder.Origin.MANUAL_PHYSICAL_MOUSE,
                false,NeutronAiFeaturesTest.visibleTarget(),ms,20,-10,ms==100,ms==150);
    }

    @Test public void inactiveDefaultAndNoExplicitConsentCannotWriteOrRecord() throws Exception {
        Path game=temp.newFolder().toPath();
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(game);
        Lifetime life=new Lifetime();
        assertFalse(r.isRecording());
        assertFalse(manual(r,life.scope(),0));
        assertNull(r.stopAndSave());
        assertFalse(r.start(false,life.scope()));
        assertFalse(r.start(true,null));
        assertFalse(manual(r,life.scope(),0));
        assertFalse(Files.exists(game.resolve("neutron-ai"),LinkOption.NOFOLLOW_LINKS));
        assertTrue(r.revokeAndDelete());
    }

    @Test public void consentedManualInputUsesNeutronFeatureHistoryAndLocalOnlyFile() throws Exception {
        Path game=temp.newFolder().toPath();
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(game);
        Lifetime life=new Lifetime();
        assertTrue(r.start(true,life.scope()));
        assertTrue(manual(r,life.scope(),0));
        assertTrue(manual(r,life.scope(),50));
        assertTrue(manual(r,life.scope(),100));
        assertTrue(manual(r,life.scope(),150));
        assertEquals(4,r.bufferedFrames());
        Path saved=r.stopAndSave();
        assertFalse(r.isRecording());assertEquals(0,r.bufferedFrames());
        assertTrue(Files.isRegularFile(saved));
        assertEquals(game.resolve("neutron-ai/manual-v4/demo-00.nmd"),saved);
        assertTrue(Files.size(saved)<NeutronAiManualDataset.MAX_FILE_BYTES);
        assertNull("read always needs its own explicit user permission",r.read(0,false));
        NeutronAiManualDataset dataset=r.read(0,true);
        assertNotNull(dataset);
        assertEquals(4,dataset.size());
        assertEquals(1,dataset.manualClicks());assertEquals(1,dataset.manualReactions());
        float[] fromModel=new float[NeutronAiCheckpoint.WIDTH];
        assertTrue(NeutronAiFeatures.populate(NeutronAiFeaturesTest.visibleTarget(),0,fromModel));
        assertArrayEquals(fromModel,Arrays.copyOfRange(dataset.frames().get(0).input(),0,40),0);
        for(int i=40;i<NeutronAiCheckpoint.INPUTS;i++)
            assertEquals(0,dataset.frames().get(0).input()[i],0);
        float[] latest=dataset.frames().get(3).input();
        float[] older=dataset.frames().get(2).input();
        assertArrayEquals(Arrays.copyOfRange(older,0,120),
                Arrays.copyOfRange(latest,40,160),0);
        assertEquals(.125f,dataset.frames().get(3).label()[0],1e-6);
        assertEquals(-.1f,dataset.frames().get(3).label()[1],1e-6);
        byte[] bytes=Files.readAllBytes(saved);
        String asText=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(asText.contains("synthetic-id"));
        assertFalse(asText.contains("https://"));
    }

    @Test public void inferenceAndAutomationAreNeverRelabelledAsHumanDemonstrations() throws Exception {
        Path game=temp.newFolder().toPath();
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(game);
        Lifetime life=new Lifetime();
        assertTrue(r.start(true,life.scope()));
        assertFalse(r.record(life.scope(),NeutronAiManualRecorder.Origin.ORDINARY_GAME_EVENT,
                false,NeutronAiFeaturesTest.visibleTarget(),0,20,10,false,false));
        assertEquals(0,r.bufferedFrames());
        assertTrue(manual(r,life.scope(),0));
        assertFalse(r.record(life.scope(),NeutronAiManualRecorder.Origin.AI_INFERENCE,
                false,NeutronAiFeaturesTest.visibleTarget(),50,20,10,false,false));
        assertFalse(r.isRecording());assertEquals(0,r.bufferedFrames());
        assertTrue(r.start(true,life.scope()));assertTrue(manual(r,life.scope(),0));
        assertFalse(r.record(life.scope(),NeutronAiManualRecorder.Origin.MANUAL_PHYSICAL_MOUSE,
                true,NeutronAiFeaturesTest.visibleTarget(),50,20,10,false,false));
        assertFalse(r.isRecording());assertNull(r.stopAndSave());
        assertFalse(Files.exists(game.resolve("neutron-ai"),LinkOption.NOFOLLOW_LINKS));
    }

    @Test public void accountWorldPlayerConnectionEpochTargetAndGenerationChangesStopImmediately() throws Exception {
        for(int mutation=0;mutation<7;mutation++) {
            NeutronAiManualRecorder r=new NeutronAiManualRecorder(temp.newFolder().toPath());
            Lifetime life=new Lifetime();
            NeutronAiManualRecorder.Scope original=life.scope();
            assertTrue(r.start(true,original));assertTrue(manual(r,original,0));
            Object a=mutation==0?new Object():life.account;
            Object w=mutation==1?new Object():life.world;
            Object p=mutation==2?new Object():life.player;
            Object c=mutation==3?new Object():life.connection;
            Object t=mutation==4?new Object():life.target;
            long e=mutation==5?life.epoch+1:life.epoch;
            long g=mutation==6?life.generation+1:life.generation;
            r.onScopeChange(new NeutronAiManualRecorder.Scope(a,w,p,c,t,e,g));
            assertFalse("scope "+mutation,r.isRecording());
            assertEquals(0,r.bufferedFrames());
            assertNull(r.stopAndSave());
        }
    }

    @Test public void oversizedOrInvalidPhysicalSamplesFailClosedAndNoDiskDataAppears() throws Exception {
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(temp.newFolder().toPath());
        Lifetime life=new Lifetime();
        assertTrue(r.start(true,life.scope()));
        assertFalse(r.record(life.scope(),NeutronAiManualRecorder.Origin.MANUAL_PHYSICAL_MOUSE,
                false,NeutronAiFeaturesTest.visibleTarget(),0,Double.NaN,0,false,false));
        assertFalse(r.isRecording());
        assertTrue(r.start(true,life.scope()));
        assertTrue(manual(r,life.scope(),0));
        assertFalse(r.record(life.scope(),NeutronAiManualRecorder.Origin.MANUAL_PHYSICAL_MOUSE,
                false,NeutronAiFeaturesTest.visibleTarget(),300,20,0,false,false));
        assertFalse(r.isRecording());
        assertTrue(r.start(true,life.scope()));
        for(int i=0;i<NeutronAiManualDataset.MAX_FRAMES;i++)
            assertTrue("bounded event "+i,manual(r,life.scope(),i*50L));
        assertEquals(NeutronAiManualDataset.MAX_FRAMES,r.bufferedFrames());
        assertFalse(manual(r,life.scope(),NeutronAiManualDataset.MAX_FRAMES*50L));
        assertEquals(0,r.bufferedFrames());assertFalse(r.isRecording());
    }

    @Test public void saveErrorClearsConsentAndDoesNotDeleteUnownedPaths() throws Exception {
        Path root=temp.newFolder().toPath();
        Files.write(root.resolve("neutron-ai"),new byte[]{1,2,3});
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(root);
        Lifetime life=new Lifetime();
        assertTrue(r.start(true,life.scope()));assertTrue(manual(r,life.scope(),0));
        try {r.stopAndSave();fail("unsafe storage path must fail");}
        catch(IOException expected){}
        assertFalse(r.isRecording());assertEquals(0,r.bufferedFrames());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(root.resolve("neutron-ai")));
        assertFalse("unowned regular file must never be removed",r.revokeAndDelete());
    }

    @Test public void explicitDeleteRemovesOnlyKnownDatasetsAndConsentCannotBeResumed() throws Exception {
        Path root=temp.newFolder().toPath();
        NeutronAiManualRecorder r=new NeutronAiManualRecorder(root);
        Lifetime life=new Lifetime();
        for(int i=0;i<2;i++) {
            assertTrue(r.start(true,life.scope()));
            assertTrue(manual(r,life.scope(),0));
            assertTrue(Files.exists(r.stopAndSave()));
        }
        Path ownFolder=root.resolve("neutron-ai/manual-v4");
        Path unrelated=ownFolder.resolve("user-data.txt");
        Files.write(unrelated,new byte[]{42});
        assertTrue(r.revokeAndDelete());
        assertFalse(r.isRecording());assertEquals(0,r.bufferedFrames());
        assertNull(r.read(0,true));assertNull(r.read(1,true));
        assertArrayEquals(new byte[]{42},Files.readAllBytes(unrelated));
        assertFalse(manual(r,life.scope(),0));
        assertTrue(Files.exists(ownFolder,LinkOption.NOFOLLOW_LINKS));
    }
}
