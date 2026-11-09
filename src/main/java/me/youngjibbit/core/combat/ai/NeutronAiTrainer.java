package me.youngjibbit.core.combat.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded Java 8 CPU training for the EXISTING fixed 160->16->2 NAI1 checkpoint.
 * Never runs on the constructing (game/render) thread, reads/writes no files and
 * never installs/publishes weights. Returns a quality-gated, still-untrusted
 * candidate to a future explicit consent+provenance loader.
 */
public final class NeutronAiTrainer implements AutoCloseable {
    public static final int MIN_FRAMES=600, MIN_CLICKS=15, MIN_REACTIONS=3;
    private static final int EMBARGO_FRAMES=8, MIN_VALIDATION_FRAMES=80;
    private static final int BATCH=32, PATIENCE=8;
    private static final double LEARNING_RATE=.035;
    private static final int BENCH_ITERATIONS=128;
    private final AtomicLong generation=new AtomicLong();
    private final ExecutorService executor;
    private final Thread submittingThread=Thread.currentThread();
    private Future<Result> active;
    private boolean closed;

    public enum Status {
        CANDIDATE, INSUFFICIENT_DATA, INVALID_INPUT, QUALITY_REJECTED,
        TIME_LIMIT, HEAP_LIMIT, INFERENCE_LIMIT, CANCELLED
    }

    public static final class Limits {
        public final long seed, maxTrainingMillis, maxHeapGrowthBytes, maxInferenceMeanNanos;
        public final int maxEpochs;
        public Limits(long seed,int maxEpochs,long maxTrainingMillis,
                      long maxHeapGrowthBytes,long maxInferenceMeanNanos) {
            if(maxEpochs<1 || maxEpochs>64 || maxTrainingMillis<1 || maxTrainingMillis>60_000
                    || maxHeapGrowthBytes<1 || maxHeapGrowthBytes>64L*1024*1024
                    || maxInferenceMeanNanos<1 || maxInferenceMeanNanos>20_000_000)
                throw new IllegalArgumentException("unbounded trainer limits");
            this.seed=seed;this.maxEpochs=maxEpochs;
            this.maxTrainingMillis=maxTrainingMillis;
            this.maxHeapGrowthBytes=maxHeapGrowthBytes;
            this.maxInferenceMeanNanos=maxInferenceMeanNanos;
        }
        public static Limits defaults(long seed) {
            return new Limits(seed,24,30_000,32L*1024*1024,10_000_000);
        }
    }

    public static final class Result {
        public final Status status;
        public final int frames, trainingFrames, validationFrames, epochs;
        public final float trainingLoss, validationLoss, zeroBaselineLoss;
        public final long elapsedNanos, heapGrowthBytes, inferenceMeanNanos, inferenceMaxNanos;
        private final byte[] candidate;
        private Result(Status status,int frames,int trainingFrames,int validationFrames,
                       int epochs,float trainingLoss,float validationLoss,float baseline,
                       long elapsedNanos,long heapGrowthBytes,long inferenceMeanNanos,
                       long inferenceMaxNanos,byte[] candidate) {
            this.status=status;this.frames=frames;
            this.trainingFrames=trainingFrames;this.validationFrames=validationFrames;
            this.epochs=epochs;this.trainingLoss=trainingLoss;this.validationLoss=validationLoss;
            this.zeroBaselineLoss=baseline;this.elapsedNanos=elapsedNanos;
            this.heapGrowthBytes=heapGrowthBytes;this.inferenceMeanNanos=inferenceMeanNanos;
            this.inferenceMaxNanos=inferenceMaxNanos;
            this.candidate=candidate==null?null:candidate.clone();
        }
        public byte[] candidateBytes() {return candidate==null?null:candidate.clone();}
        public boolean isCandidate(){return status==Status.CANDIDATE && candidate!=null;}
    }

    private static final class Progress {
        final long generation, startNanos, heapBase;
        long heapPeak;
        final Limits limits;
        final AtomicLong live;
        Progress(long generation,Limits limits,AtomicLong live) {
            this.generation=generation;this.limits=limits;this.live=live;
            this.startNanos=System.nanoTime();this.heapBase=usedHeap();
        }
        Status check() {
            if(Thread.currentThread().isInterrupted() || live.get()!=generation)return Status.CANCELLED;
            long elapsed=System.nanoTime()-startNanos;
            if(elapsed<0 || elapsed>limits.maxTrainingMillis*1_000_000L)return Status.TIME_LIMIT;
            heapPeak=Math.max(heapPeak,Math.max(0,usedHeap()-heapBase));
            if(heapPeak>limits.maxHeapGrowthBytes)return Status.HEAP_LIMIT;
            return null;
        }
        long elapsed(){return Math.max(0,System.nanoTime()-startNanos);}
    }

    public NeutronAiTrainer() {
        executor=Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread worker=new Thread(task,"neutron-ai-local-cpu-trainer");
                worker.setDaemon(true);
                worker.setPriority(Thread.MIN_PRIORITY);
                return worker;
            }
        });
    }

    /** Schedules a fresh generation. Never does neural work on the calling thread. */
    public synchronized Future<Result> trainAsync(NeutronAiManualDataset demonstration,Limits limits) {
        if(closed)throw new IllegalStateException("trainer already closed");
        long token=generation.incrementAndGet();
        if(active!=null)active.cancel(true);
        active=executor.submit(new Callable<Result>() {
            @Override public Result call() {
                if(Thread.currentThread()==submittingThread)
                    return rejected(Status.INVALID_INPUT,0,0,0,null);
                return train(demonstration,limits,token);
            }
        });
        return active;
    }

    public synchronized void cancel() {
        generation.incrementAndGet();
        if(active!=null)active.cancel(true);
        active=null;
    }

    @Override public synchronized void close() {
        if(closed)return;
        closed=true;cancel();executor.shutdownNow();
    }

    private Result train(NeutronAiManualDataset data,Limits limits,long token) {
        Progress progress=new Progress(token,limits, generation);
        if(limits==null || data==null)return rejected(Status.INVALID_INPUT,0,0,0,progress);
        int frames=data.size();
        int trainingEnd=frames*4/5,validationStart=trainingEnd+EMBARGO_FRAMES;
        if(frames<MIN_FRAMES || frames>NeutronAiManualDataset.MAX_FRAMES
                || frames-validationStart<MIN_VALIDATION_FRAMES
                || data.manualClicks()<MIN_CLICKS || data.manualReactions()<MIN_REACTIONS)
            return rejected(Status.INSUFFICIENT_DATA,frames,trainingEnd,frames-validationStart,progress);
        List<NeutronAiManualDataset.Frame> rows=data.frames();
        int trainClicks=0,valClicks=0,trainReactions=0,valReactions=0;
        for(int i=0;i<frames;i++) {
            NeutronAiManualDataset.Frame frame=rows.get(i);
            if(i<trainingEnd) {
                if(frame.manualClick)trainClicks++;
                if(frame.manualReaction)trainReactions++;
            } else if(i>=validationStart) {
                if(frame.manualClick)valClicks++;
                if(frame.manualReaction)valReactions++;
            }
        }
        if(trainClicks<12 || valClicks<3 || trainReactions<2 || valReactions<1)
            return rejected(Status.INSUFFICIENT_DATA,frames,trainingEnd,frames-validationStart,progress);

        // These simultaneous copies alone require this much primitive payload.
        // VM-wide net heap samples can remain flat inside an already reserved TLAB.
        long samplePayloadBytes=(long)frames*
                (NeutronAiCheckpoint.INPUTS+NeutronAiCheckpoint.OUTPUTS)*Float.BYTES;
        if(samplePayloadBytes>limits.maxHeapGrowthBytes)
            return rejected(Status.HEAP_LIMIT,frames,trainingEnd,frames-validationStart,progress);

        float[][] features=new float[frames][],labels=new float[frames][];
        for(int i=0;i<frames;i++) {
            if((i&63)==0) {
                Status limit=progress.check();
                if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
            }
            features[i]=rows.get(i).input();
            labels[i]=rows.get(i).label();
        }
        double baseline=zeroLoss(labels,validationStart,frames);
        if(!Double.isFinite(baseline) || baseline<1.0e-5)
            return rejected(Status.QUALITY_REJECTED,frames,trainingEnd,frames-validationStart,progress);

        Random random=new Random(limits.seed);
        double[] weights=new double[NeutronAiCheckpoint.PARAMETERS];
        for(int i=0;i<weights.length;i++)weights[i]=random.nextGaussian()*.035;
        double[] best=null,grad=new double[weights.length];
        double[] hidden=new double[NeutronAiCheckpoint.HIDDEN];
        double[] output=new double[NeutronAiCheckpoint.OUTPUTS];
        double[] outputDelta=new double[NeutronAiCheckpoint.OUTPUTS];
        double[] hiddenDelta=new double[NeutronAiCheckpoint.HIDDEN];
        int[] order=new int[trainingEnd];
        for(int i=0;i<trainingEnd;i++)order[i]=i;
        double bestValidation=Double.POSITIVE_INFINITY,bestTrain=Double.POSITIVE_INFINITY;
        int bestEpoch=0,stale=0,finishedEpochs=0;
        final int outputBase=NeutronAiCheckpoint.HIDDEN*(NeutronAiCheckpoint.INPUTS+1);

        for(int epoch=1;epoch<=limits.maxEpochs;epoch++) {
            Status limit=progress.check();
            if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
            for(int i=order.length-1;i>0;i--) {
                int j=random.nextInt(i+1);
                int swap=order[i];order[i]=order[j];order[j]=swap;
            }
            for(int offset=0;offset<trainingEnd;offset+=BATCH) {
                limit=progress.check();
                if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
                Arrays.fill(grad,0);
                int count=Math.min(BATCH,trainingEnd-offset);
                for(int row=offset;row<offset+count;row++) {
                    if((row&31)==0) {
                        limit=progress.check();
                        if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
                    }
                    int index=order[row];
                    forward(weights,features[index],hidden,output);
                    for(int k=0;k<NeutronAiCheckpoint.OUTPUTS;k++) {
                        double prediction=output[k];
                        outputDelta[k]=(prediction-labels[index][k])*(1-prediction*prediction);
                        int base=outputBase+k*(NeutronAiCheckpoint.HIDDEN+1);
                        for(int j=0;j<NeutronAiCheckpoint.HIDDEN;j++)
                            grad[base+j]+=outputDelta[k]*hidden[j];
                        grad[base+NeutronAiCheckpoint.HIDDEN]+=outputDelta[k];
                    }
                    for(int j=0;j<NeutronAiCheckpoint.HIDDEN;j++) {
                        double back=0;
                        for(int k=0;k<NeutronAiCheckpoint.OUTPUTS;k++)
                            back+=weights[outputBase+k*(NeutronAiCheckpoint.HIDDEN+1)+j]*outputDelta[k];
                        hiddenDelta[j]=back*(1-hidden[j]*hidden[j]);
                        int base=j*(NeutronAiCheckpoint.INPUTS+1);
                        for(int i=0;i<NeutronAiCheckpoint.INPUTS;i++)
                            grad[base+i]+=hiddenDelta[j]*features[index][i];
                        grad[base+NeutronAiCheckpoint.INPUTS]+=hiddenDelta[j];
                    }
                }
                double step=LEARNING_RATE/count;
                for(int i=0;i<weights.length;i++) {
                    double gradient=Math.max(-4,Math.min(4,grad[i]));
                    weights[i]=Math.max(-8,Math.min(8,weights[i]-step*gradient));
                }
            }
            finishedEpochs=epoch;
            double validation=loss(weights,features,labels,validationStart,frames,hidden,output);
            double training=loss(weights,features,labels,0,trainingEnd,hidden,output);
            if(!Double.isFinite(training) || !Double.isFinite(validation))
                return rejected(Status.INVALID_INPUT,frames,trainingEnd,frames-validationStart,progress);
            if(validation+1.0e-8<bestValidation) {
                bestValidation=validation;bestTrain=training;bestEpoch=epoch;
                best=weights.clone();stale=0;
            } else if(++stale>=PATIENCE)break;
        }
        Status limit=progress.check();
        if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
        if(best==null || bestEpoch==0 || bestValidation>=baseline*.98
                || bestTrain>=20 || bestValidation>=20)
            return qualityRejected(frames,trainingEnd,frames-validationStart,finishedEpochs,
                    bestTrain,bestValidation,baseline,progress);

        float[] learned=new float[best.length];
        for(int i=0;i<best.length;i++)learned[i]=(float)best[i];
        byte[] encoded=NeutronAiCheckpoint.encodeLocalCandidate(learned,frames,
                data.manualClicks(),data.manualReactions(),bestEpoch,(float)bestTrain,(float)bestValidation);
        NeutronAiCheckpoint checkpoint=NeutronAiCheckpoint.decode(encoded);
        if(checkpoint==null)
            return rejected(Status.INVALID_INPUT,frames,trainingEnd,frames-validationStart,progress);
        float[] workspace=new float[NeutronAiCheckpoint.HIDDEN];
        float[] response=new float[NeutronAiCheckpoint.OUTPUTS];
        for(int i=0;i<32;i++) {
            if(!checkpoint.infer(features[validationStart],workspace,response))
                return rejected(Status.INVALID_INPUT,frames,trainingEnd,frames-validationStart,progress);
        }
        long maxLatency=0,benchmarkStart=System.nanoTime();
        for(int i=0;i<BENCH_ITERATIONS;i++) {
            long start=System.nanoTime();
            if(!checkpoint.infer(features[validationStart+i%(frames-validationStart)],
                    workspace,response))
                return rejected(Status.INVALID_INPUT,frames,trainingEnd,frames-validationStart,progress);
            maxLatency=Math.max(maxLatency,Math.max(0,System.nanoTime()-start));
        }
        long meanLatency=Math.max(0,(System.nanoTime()-benchmarkStart)/BENCH_ITERATIONS);
        limit=progress.check();
        if(limit!=null)return rejected(limit,frames,trainingEnd,frames-validationStart,progress);
        if(meanLatency>limits.maxInferenceMeanNanos)
            return new Result(Status.INFERENCE_LIMIT,frames,trainingEnd,frames-validationStart,
                    bestEpoch,(float)bestTrain,(float)bestValidation,(float)baseline,
                    progress.elapsed(),progress.heapPeak,meanLatency,maxLatency,null);
        // Never write, auto-install, choose a remote endpoint or publish a checkpoint.
        return new Result(Status.CANDIDATE,frames,trainingEnd,frames-validationStart,
                bestEpoch,(float)bestTrain,(float)bestValidation,(float)baseline,
                progress.elapsed(),progress.heapPeak,meanLatency,maxLatency,encoded);
    }

    private static void forward(double[] weights,float[] features,double[] hidden,double[] output) {
        for(int j=0;j<NeutronAiCheckpoint.HIDDEN;j++) {
            int base=j*(NeutronAiCheckpoint.INPUTS+1);
            double sum=weights[base+NeutronAiCheckpoint.INPUTS];
            for(int i=0;i<NeutronAiCheckpoint.INPUTS;i++)
                sum+=weights[base+i]*features[i];
            hidden[j]=Math.tanh(sum);
        }
        int outputBase=NeutronAiCheckpoint.HIDDEN*(NeutronAiCheckpoint.INPUTS+1);
        for(int k=0;k<NeutronAiCheckpoint.OUTPUTS;k++) {
            int base=outputBase+k*(NeutronAiCheckpoint.HIDDEN+1);
            double sum=weights[base+NeutronAiCheckpoint.HIDDEN];
            for(int j=0;j<NeutronAiCheckpoint.HIDDEN;j++)
                sum+=weights[base+j]*hidden[j];
            output[k]=Math.tanh(sum);
        }
    }

    private static double zeroLoss(float[][] labels,int begin,int end) {
        double squared=0;
        for(int i=begin;i<end;i++)for(float label:labels[i])squared+=label*label;
        return squared/((end-begin)*NeutronAiCheckpoint.OUTPUTS);
    }
    private static double loss(double[] weights,float[][] features,float[][] labels,
                               int begin,int end,double[] hidden,double[] output) {
        double sum=0;
        for(int i=begin;i<end;i++) {
            forward(weights,features[i],hidden,output);
            for(int k=0;k<NeutronAiCheckpoint.OUTPUTS;k++) {
                double delta=output[k]-labels[i][k];sum+=delta*delta;
            }
        }
        return sum/((end-begin)*NeutronAiCheckpoint.OUTPUTS);
    }
    private static long usedHeap() {
        Runtime runtime=Runtime.getRuntime();
        return Math.max(0,runtime.totalMemory()-runtime.freeMemory());
    }
    private static Result rejected(Status status,int frames,int trainFrames,
                                   int validationFrames,Progress progress) {
        return new Result(status,frames,trainFrames,validationFrames,0,
                Float.NaN,Float.NaN,Float.NaN,progress==null?0:progress.elapsed(),
                progress==null?0:progress.heapPeak,0,0,null);
    }
    private static Result qualityRejected(int frames,int trainFrames,int validationFrames,
                                          int epochs,double trainLoss,double valLoss,
                                          double baseline,Progress progress) {
        return new Result(Status.QUALITY_REJECTED,frames,trainFrames,validationFrames,epochs,
                (float)trainLoss,(float)valLoss,(float)baseline,progress.elapsed(),
                progress.heapPeak,0,0,null);
    }
}
