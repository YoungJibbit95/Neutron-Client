package net.minecraft.client.renderer.chunk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.youngjibbit.core.diagnostics.CorePerformanceProbe;
import net.minecraft.util.EnumFacing;

/** Isolated production visibility calculation, not chunk building or client FPS. */
public final class VisGraphBenchmark
{
    private static final int BATCH=20;
    private static volatile long blackhole;

    public static void main(String[] args) throws Exception
    {
        boolean fast=CorePerformanceProbe.fastMode(args);
        CorePerformanceProbe probe=new CorePerformanceProbe("visibility_graph",args[0],
                "Production computeVisibility only. Every operation receives a newly constructed and populated graph outside the timer/allocation interval. Geometry oracle, setup, result inspection and JSON I/O excluded; not meshing, chunk latency or FPS.");
        VisGraph[] graphs=new VisGraph[BATCH];
        SetVisibility[] outputs=new SetVisibility[BATCH];
        for(String name:VisGraphTestSupport.SCENES)
        {
            boolean[] opaque=VisGraphTestSupport.scene(name);
            boolean[][] matrix=VisGraphTestSupport.reference(opaque);
            long expected=0L;
            int occupied=0;
            for(boolean block:opaque) if(block) ++occupied;
            for(EnumFacing a:EnumFacing.VALUES)
                for(EnumFacing b:EnumFacing.VALUES)
                    if(matrix[a.ordinal()][b.ordinal()]) expected|=1L<<(a.ordinal()*6+b.ordinal());
            for(int warm=0;warm<CorePerformanceProbe.WARMUP;++warm)
                check(name,VisGraphTestSupport.graph(opaque).computeVisibility(fast),expected);

            JsonObject parameters=new JsonObject();
            parameters.addProperty("cells",4096);
            parameters.addProperty("opaque_cells",occupied);
            JsonObject scenario=probe.scenario(name,parameters,BATCH,Long.toHexString(expected));
            JsonArray times=new JsonArray(), allocations=new JsonArray();
            long observed=0L;
            for(int sample=0;sample<CorePerformanceProbe.SAMPLES;++sample)
            {
                for(int i=0;i<BATCH;++i) graphs[i]=VisGraphTestSupport.graph(opaque);
                long bytesBefore=probe.allocatedBytes();
                long begin=System.nanoTime();
                for(int i=0;i<BATCH;++i) outputs[i]=graphs[i].computeVisibility(fast);
                long elapsed=System.nanoTime()-begin;
                long bytesAfter=probe.allocatedBytes();
                times.add(elapsed);
                allocations.add(bytesBefore<0 || bytesAfter<0 ? null : Long.valueOf(bytesAfter-bytesBefore));
                for(SetVisibility output:outputs) observed=check(name,output,expected);
            }
            scenario.addProperty("observed_signature",Long.toHexString(observed));
            scenario.add("elapsed_ns",times);
            scenario.add("allocated_bytes",allocations);
        }
        JsonObject structure=new JsonObject();
        structure.addProperty("section_cells",4096);
        structure.addProperty("thread_queue_ints",fast?4096:0);
        structure.addProperty("fixed_scratch_bytes",fast?4096*4:0);
        structure.addProperty("fixed_scratch_scope","Retained primitive queue element payload per calling thread, excluding array header. Legacy IntegerCache already covers all section indices; no per-cell Integer-allocation saving is claimed.");
        probe.root.add("structure",structure);
        probe.write(args[1]);
    }

    private static long check(String scene,SetVisibility output,long expected)
    {
        long actual=VisGraphTestSupport.signature(output);
        blackhole=actual;
        if(actual!=expected) throw new AssertionError("Visibility mismatch in "+scene);
        return actual;
    }
}
