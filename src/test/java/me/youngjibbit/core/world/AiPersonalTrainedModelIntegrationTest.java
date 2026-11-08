package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.NeutronAiPersonalModelLoader;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.ai.NeutronAiCheckpoint;
import me.youngjibbit.core.combat.ai.NeutronAiManualDataset;
import me.youngjibbit.core.combat.ai.NeutronAiTrainer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Session;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Trains a NEW local synthetic demonstration, passes it through real T3 NAI1
 * training and personal local HMAC save/load, then observes actual Aura and
 * Minecraft walking C03/C02 gates. No imported/predefined fake model weights.
 */
public class AiPersonalTrainedModelIntegrationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private static NeutronAiTrainer.Result trained;
    private static synchronized NeutronAiTrainer.Result trained() throws Exception {
        if(trained!=null)return trained;
        List<NeutronAiManualDataset.Frame> frames=new ArrayList<>();
        for(int i=0;i<1000;i++) {
            float[] features=new float[NeutronAiCheckpoint.INPUTS];
            features[0]=(float)(Math.sin(i*.018)*.2);
            features[1]=(float)(Math.cos(i*.015)*.2);
            features[40]=features[0];features[41]=features[1];
            float yaw=.72f+.05f*features[0];
            float pitch=.55f+.05f*features[1];
            frames.add(new NeutronAiManualDataset.Frame(i*50L,
                    i%15==0,i%75==0,features,new float[]{yaw,pitch}));
        }
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            NeutronAiTrainer.Result result=trainer.trainAsync(
                    NeutronAiManualDataset.of(frames),NeutronAiTrainer.Limits.defaults(417))
                    .get(45,TimeUnit.SECONDS);
            assertEquals("independently trained NAI1 candidate",NeutronAiTrainer.Status.CANDIDATE,result.status);
            assertNotNull(NeutronAiCheckpoint.decode(result.candidateBytes()));
            trained=result;
        }
        return trained;
    }

    private static long count(List<Object> packets,Class<?> type) {
        long n=0;
        for(Object packet:packets)if(type.isInstance(packet))n++;
        return n;
    }

    private static final class Run implements AutoCloseable {
        final Wave05AuraSelectionChecks.SelectionRun scene =
                new Wave05AuraSelectionChecks.SelectionRun("Single");
        final RotationManager rotation=scene.run.fixture.runtime.getRotationManager();
        final NeutronAiPersonalModelLoader loader;
        final NeutronAiPersonalModelLoader.Scope scope;
        Run(Path localFolder) throws Exception {
            scene.run.fixture.mc.session=new Session("LocalSynthetic","local-fixture","no-login-token","legacy");
            scene.run.fixture.aura.getModeSetting("Rot Mode").setValue("AI Learned");
            scene.run.fixture.aura.getBooleanSetting("Click Randomization Enabled").setValue(false);
            // Client's first actual walking frame binds the connection; it also
            // requires missing AI to HOLD with no C02 before any model install.
            List<Object> priming=scene.walk();
            assertEquals(0,count(priming,C02PacketUseEntity.class));
            assertFalse(rotation.isAiAuraMotionAccepted());
            rotation.bindConnection(scene.run.fixture.network.getClientPackets());
            loader=new NeutronAiPersonalModelLoader(localFolder,rotation);
            scope=NeutronAiPersonalModelLoader.Scope.capture(scene.run.fixture.mc);
            assertNotNull(scope);
        }
        void authorizeSaveAndLoad() throws Exception {
            assertFalse(loader.allowSession(false,scope));
            assertFalse(loader.isConsented());
            assertTrue(loader.allowSession(true,scope));
            assertTrue(loader.isConsented());
            assertFalse("unknown model must not be loaded implicitly",
                    loader.loadExplicit(true,scope));
            assertTrue(loader.saveTrainedCandidate(trained(),scope));
            assertFalse(loader.isLoaded());
            assertFalse("a checkpoint requires a separate explicit load action",
                    loader.loadExplicit(false,scope));
            assertTrue(loader.loadExplicit(true,scope));
            assertTrue(loader.isLoaded());
        }
        @Override public void close() throws Exception {
            loader.withdraw();
            scene.close();
        }
    }

    @Test public void realTrainerCheckpointEntersExistingAuraC03PipelineButNeverOverridesC02Gates() throws Exception {
        try(Run run=new Run(temp.newFolder().toPath())) {
            run.scene.run.fixture.player.usingItem=true;
            run.authorizeSaveAndLoad();
            List<Object> warmup=run.scene.walk();
            assertEquals("first model acquisition must HOLD",0,count(warmup,C02PacketUseEntity.class));
            assertFalse("initial AI acquisition is never accepted",run.rotation.isAiAuraMotionAccepted());
            boolean accepted=false;
            for(int tick=0;tick<36;tick++) {
                List<Object> packets=run.scene.walk();
                accepted |=run.rotation.isAiAuraMotionAccepted();
                assertEquals("using item denies the original controller/C02",0,
                        count(packets,C02PacketUseEntity.class));
                assertEquals(1,count(packets,C03PacketPlayer.class));
            }
            assertTrue("independently trained personal NAI1 weights feed actual accepted C03",accepted);
            AiLearnedAuraIntegrationTest.RejectC03 rejected=new AiLearnedAuraIntegrationTest.RejectC03();
            Pulsar.BUS.subscribe(rejected);
            try {
                for(int tick=0;tick<5;tick++) {
                    List<Object> packets=run.scene.walk();
                    assertEquals("cancelled C03 cannot grant C02",0,count(packets,C02PacketUseEntity.class));
                    assertFalse("cancelled movement withdraws AI authority",
                            run.rotation.isAiAuraMotionAccepted());
                }
            } finally {Pulsar.BUS.unsubscribe(rejected);}
        }
    }

    @Test public void tamperedModelManifestAndLocalKeyAreAlwaysRejectedAndDeletionPreservesOtherFiles() throws Exception {
        Path root=temp.newFolder().toPath();
        try(Run run=new Run(root)) {
            assertTrue(run.loader.allowSession(true,run.scope));
            assertTrue(run.loader.saveTrainedCandidate(trained(),run.scope));
            Path directory=root.resolve("neutron-ai/personal-v1");
            Path model=directory.resolve("model-v1.nai");
            Path manifest=directory.resolve("origin-v1.bin");
            Path key=directory.resolve("local-key-v1.bin");
            assertEquals(NeutronAiCheckpoint.SERIALIZED_BYTES,Files.size(model));
            assertEquals(108,Files.size(manifest));
            assertEquals(32,Files.size(key));
            assertFalse("load without current consent action",run.loader.loadExplicit(false,run.scope));

            for(Path path:new Path[]{model,manifest,key}) {
                byte[] baseline=Files.readAllBytes(path);
                byte[] modified=baseline.clone();modified[modified.length/2]^=1;
                Files.write(path,modified);
                assertFalse("tampered "+path.getFileName(),run.loader.loadExplicit(true,run.scope));
                assertFalse(run.loader.isLoaded());
                Files.write(path,baseline);
            }
            assertTrue(run.loader.loadExplicit(true,run.scope));
            assertTrue(run.loader.isLoaded());

            Path unrelated=directory.resolve("user-note.txt");
            Files.write(unrelated,new byte[]{42,43});
            assertTrue(run.loader.revokeAndDelete());
            assertFalse(run.loader.isConsented());
            assertFalse(run.loader.isLoaded());
            assertFalse(Files.exists(model));assertFalse(Files.exists(manifest));
            assertFalse(Files.exists(key));
            assertArrayEquals("do not traverse non-owned personal data",
                    new byte[]{42,43},Files.readAllBytes(unrelated));
            assertFalse("deleted model must not silently reload",run.loader.loadExplicit(true,run.scope));
        }
    }

    @Test public void accountChangeConnectionEpochAndWorldChangeDisposePrivateWeights() throws Exception {
        for(int scenario=0;scenario<3;scenario++) {
            try(Run run=new Run(temp.newFolder().toPath())) {
                run.authorizeSaveAndLoad();
                if(scenario==0)
                    run.scene.run.fixture.mc.session=
                            new Session("AnotherSynthetic","other-fixture","no-login-token","legacy");
                if(scenario==1)
                    run.scene.run.fixture.network.getClientPackets().worldChanged();
                if(scenario==2)
                    run.scene.run.fixture.mc.theWorld=run.scene.run.fixture.world;
                NeutronAiPersonalModelLoader.Scope now=
                        NeutronAiPersonalModelLoader.Scope.capture(run.scene.run.fixture.mc);
                run.loader.onScopeChange(now);
                assertFalse("scenario "+scenario,run.loader.isConsented());
                assertFalse(run.loader.isLoaded());
                assertFalse("cannot restore without consent",run.loader.loadExplicit(true,run.scope));
                assertNotEquals("aura",run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.isAiAuraMotionAccepted());
            }
        }
    }
}
