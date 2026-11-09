package me.youngjibbit.core.combat.ai;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** All labels are synthetic mathematical samples, not invented user recordings. */
public class NeutronAiTrainerTest {
    static NeutronAiManualDataset synthetic(int count,boolean varying,boolean clicks,
                                            boolean reactions) {
        List<NeutronAiManualDataset.Frame> rows=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            float[] input=new float[NeutronAiCheckpoint.INPUTS];
            input[0]=varying?(float)(.7*Math.sin(i*.018)):0;
            input[1]=varying?(float)(.6*Math.cos(i*.015)):0;
            input[40]=input[0]*.95f;
            input[41]=input[1]*.9f;
            float yaw=(float)Math.tanh(.65*input[0]+.2*input[1]+.12);
            float pitch=(float)Math.tanh(-.55*input[1]+.12);
            rows.add(new NeutronAiManualDataset.Frame(i*50L,
                    clicks && i%15==0,reactions && i%75==0,
                    input,new float[]{yaw,pitch}));
        }
        return NeutronAiManualDataset.of(rows);
    }

    @Test public void reproducibleCpuTrainingConvergesAndSerializesExistingCheckpoint() throws Exception {
        NeutronAiManualDataset data=synthetic(1000,true,true,true);
        NeutronAiTrainer.Result a,b;
        try(NeutronAiTrainer t=new NeutronAiTrainer()) {
            Future<NeutronAiTrainer.Result> future=t.trainAsync(data,NeutronAiTrainer.Limits.defaults(1337));
            a=future.get(45,TimeUnit.SECONDS);
        }
        try(NeutronAiTrainer t=new NeutronAiTrainer()) {
            b=t.trainAsync(data,NeutronAiTrainer.Limits.defaults(1337))
                    .get(45,TimeUnit.SECONDS);
        }
        assertEquals(NeutronAiTrainer.Status.CANDIDATE,a.status);
        assertEquals(NeutronAiTrainer.Status.CANDIDATE,b.status);
        assertEquals(1000,a.frames);
        assertTrue(a.trainingFrames>=600);
        assertTrue(a.validationFrames>=80);
        assertTrue(a.epochs>=1 && a.epochs<=24);
        assertTrue(Float.isFinite(a.trainingLoss));
        assertTrue(a.validationLoss<a.zeroBaselineLoss*.98f);
        assertEquals(a.trainingLoss,b.trainingLoss,0);
        assertEquals(a.validationLoss,b.validationLoss,0);
        assertArrayEquals("fixed seed must reproduce exact NAI1 serialized bytes",
                a.candidateBytes(),b.candidateBytes());
        byte[] serialized=a.candidateBytes();
        assertEquals(NeutronAiCheckpoint.SERIALIZED_BYTES,serialized.length);
        NeutronAiCheckpoint model=NeutronAiCheckpoint.decode(serialized);
        assertNotNull(model);
        assertEquals(a.frames,model.recordedFrames());
        assertEquals(data.manualClicks(),model.manualClicks());
        assertEquals(data.manualReactions(),model.reactions());
        assertTrue(a.elapsedNanos>0);
        assertTrue(a.inferenceMeanNanos>0);
        assertTrue(a.heapGrowthBytes<=32L*1024*1024);
        serialized[60]^=1;
        assertNull(NeutronAiCheckpoint.decode(serialized));
        assertNotNull(NeutronAiCheckpoint.decode(a.candidateBytes()));
    }

    @Test public void nullNoConsentCoverageAndUninformativeDemonstrationsCannotTrain() throws Exception {
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            NeutronAiTrainer.Limits limits=NeutronAiTrainer.Limits.defaults(7);
            assertEquals(NeutronAiTrainer.Status.INVALID_INPUT,
                    trainer.trainAsync(null,limits).get(10,TimeUnit.SECONDS).status);
            assertEquals(NeutronAiTrainer.Status.INSUFFICIENT_DATA,
                    trainer.trainAsync(synthetic(500,true,true,true),limits)
                            .get(10,TimeUnit.SECONDS).status);
            assertEquals(NeutronAiTrainer.Status.INSUFFICIENT_DATA,
                    trainer.trainAsync(synthetic(950,true,false,true),limits)
                            .get(10,TimeUnit.SECONDS).status);
            assertEquals(NeutronAiTrainer.Status.INSUFFICIENT_DATA,
                    trainer.trainAsync(synthetic(950,true,true,false),limits)
                            .get(10,TimeUnit.SECONDS).status);
        }
        List<NeutronAiManualDataset.Frame> zero=new ArrayList<>();
        for(int i=0;i<900;i++)
            zero.add(new NeutronAiManualDataset.Frame(i*50L,i%15==0,i%75==0,
                    new float[160],new float[2]));
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            NeutronAiTrainer.Result result=trainer.trainAsync(NeutronAiManualDataset.of(zero),
                    NeutronAiTrainer.Limits.defaults(11)).get(10,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.QUALITY_REJECTED,result.status);
            assertNull(result.candidateBytes());
        }
    }

    @Test public void cancellationAndNewGenerationNeverPublishOldWeights() throws Exception {
        NeutronAiManualDataset data=synthetic(2000,true,true,true);
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            Future<NeutronAiTrainer.Result> old=trainer.trainAsync(data,
                    new NeutronAiTrainer.Limits(5,64,60_000,64L*1024*1024,10_000_000));
            trainer.cancel();
            assertTrue(old.isCancelled());
            try {old.get(5,TimeUnit.SECONDS);fail();}
            catch(CancellationException expected){}
            Future<NeutronAiTrainer.Result> next=trainer.trainAsync(data,
                    NeutronAiTrainer.Limits.defaults(5));
            NeutronAiTrainer.Result result=next.get(45,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.CANDIDATE,result.status);
            assertNotNull(NeutronAiCheckpoint.decode(result.candidateBytes()));
        }
    }

    @Test public void hardTimeHeapAndInferenceBudgetsFailClosedWithoutAutoPublication() throws Exception {
        NeutronAiManualDataset data=synthetic(1000,true,true,true);
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            NeutronAiTrainer.Limits time=new NeutronAiTrainer.Limits(1,64,1,
                    32L*1024*1024,10_000_000);
            NeutronAiTrainer.Result timed=trainer.trainAsync(data,time).get(10,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.TIME_LIMIT,timed.status);
            assertNull(timed.candidateBytes());
            NeutronAiTrainer.Limits heap=new NeutronAiTrainer.Limits(1,24,30_000,1,10_000_000);
            NeutronAiTrainer.Result exhausted=trainer.trainAsync(data,heap).get(15,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.HEAP_LIMIT,exhausted.status);
            assertNull(exhausted.candidateBytes());
            NeutronAiTrainer.Limits inference=new NeutronAiTrainer.Limits(1,24,30_000,
                    32L*1024*1024,1);
            NeutronAiTrainer.Result slow=trainer.trainAsync(data,inference).get(45,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.INFERENCE_LIMIT,slow.status);
            assertNull(slow.candidateBytes());
            assertTrue(slow.inferenceMeanNanos>1);
        }
    }

    @Test public void mandatorySampleCopiesMustFitTheirPrimitivePayloadBudget() throws Exception {
        NeutronAiManualDataset data=synthetic(1000,true,true,true);
        long samplePayloadBytes=(long)data.size()*
                (NeutronAiCheckpoint.INPUTS+NeutronAiCheckpoint.OUTPUTS)*Float.BYTES;
        try(NeutronAiTrainer trainer=new NeutronAiTrainer()) {
            NeutronAiTrainer.Limits heap=new NeutronAiTrainer.Limits(1,24,30_000,
                    samplePayloadBytes-1,10_000_000);
            NeutronAiTrainer.Result exhausted=trainer.trainAsync(data,heap).get(15,TimeUnit.SECONDS);
            assertEquals(NeutronAiTrainer.Status.HEAP_LIMIT,exhausted.status);
            assertNull(exhausted.candidateBytes());
        }
    }

    @Test public void schemaBoundsAndClosedExecutorCannotEnqueueTraining() {
        for(int scenario=0;scenario<4;scenario++) {
            try {
                if(scenario==0)new NeutronAiTrainer.Limits(0,65,1_000,10_000,1_000);
                if(scenario==1)new NeutronAiTrainer.Limits(0,1,60_001,10_000,1_000);
                if(scenario==2)new NeutronAiTrainer.Limits(0,1,100,65L*1024*1024,1_000);
                if(scenario==3)new NeutronAiTrainer.Limits(0,1,100,10_000,0);
                fail("invalid limits "+scenario);
            } catch(IllegalArgumentException expected) {}
        }
        NeutronAiTrainer closed=new NeutronAiTrainer();closed.close();
        try {closed.trainAsync(null,NeutronAiTrainer.Limits.defaults(0));fail();}
        catch(IllegalStateException expected){}
    }

    @Test public void internalNai1SerializerRejectsBadParametersMetricsAndSampleCount() {
        float[] parameters=new float[NeutronAiCheckpoint.PARAMETERS];
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,599,30,4,5,.1f,.2f));
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,14,4,5,.1f,.2f));
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,30,2,5,.1f,.2f));
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,30,4,0,.1f,.2f));
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,30,4,5,Float.NaN,.2f));
        parameters[0]=Float.POSITIVE_INFINITY;
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,30,4,5,.1f,.2f));
        parameters[0]=0;
        assertNull(NeutronAiCheckpoint.encodeLocalCandidate(new float[3],900,30,4,5,.1f,.2f));
        assertNotNull(NeutronAiCheckpoint.decode(
                NeutronAiCheckpoint.encodeLocalCandidate(parameters,900,30,4,5,.1f,.2f)));
    }
}
