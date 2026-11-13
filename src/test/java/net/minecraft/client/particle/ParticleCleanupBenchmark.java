package net.minecraft.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import me.youngjibbit.core.diagnostics.CorePerformanceProbe;

/** Production removal strategy only: no particle ticking, rendering, or client-FPS claim. */
public final class ParticleCleanupBenchmark
{
    private static final int COUNT=4000, BATCH=10;
    private static final String[] SCENES={"all_live","sparse_deaths","half_dead","all_dead"};
    private static volatile long blackhole;

    public static void main(String[] args) throws Exception
    {
        final boolean fast=CorePerformanceProbe.fastMode(args);
        CorePerformanceProbe probe=new CorePerformanceProbe("particle_cleanup",args[0],
                "Production dead collection, membership, stable removal and scratch release only. Particle creation/ticks, layer setup, input reset, validation and JSON I/O excluded; not client frames or FPS.");
        ParticleRemovalBuffer<Item> buffer=fast?new ParticleRemovalBuffer<Item>():null;
        Item[] source=new Item[COUNT];
        for(int i=0;i<COUNT;++i) source[i]=new Item(i);
        List<List<Item>> targets=new ArrayList<List<Item>>();
        for(int i=0;i<BATCH;++i) targets.add(new ArrayList<Item>(COUNT));

        for(String name:SCENES)
        {
            List<Item> selected=new ArrayList<Item>();
            long expected=1L;
            for(Item item:source)
                if(dead(name,item.id)) selected.add(item);
                else expected=31L*expected+item.id;
            Item[] deaths=selected.toArray(new Item[selected.size()]);
            for(int warm=0;warm<CorePerformanceProbe.WARMUP;++warm)
            {
                reset(targets.get(0),source);
                run(targets.get(0),deaths,buffer,fast);
                check(name,targets.get(0),expected,COUNT-deaths.length);
            }

            JsonObject parameters=new JsonObject();
            parameters.addProperty("particles",COUNT);
            parameters.addProperty("deaths",deaths.length);
            JsonObject scenario=probe.scenario(name,parameters,BATCH,Long.toHexString(expected));
            JsonArray times=new JsonArray(), allocations=new JsonArray();
            long observed=0L;
            for(int sample=0;sample<CorePerformanceProbe.SAMPLES;++sample)
            {
                for(List<Item> target:targets) reset(target,source);
                long bytesBefore=probe.allocatedBytes();
                long begin=System.nanoTime();
                for(int iteration=0;iteration<BATCH;++iteration)
                    run(targets.get(iteration),deaths,buffer,fast);
                long elapsed=System.nanoTime()-begin;
                long bytesAfter=probe.allocatedBytes();
                times.add(elapsed);
                allocations.add(bytesBefore<0 || bytesAfter<0 ? null : Long.valueOf(bytesAfter-bytesBefore));
                for(List<Item> target:targets) observed=check(name,target,expected,COUNT-deaths.length);
            }
            scenario.addProperty("observed_signature",Long.toHexString(observed));
            scenario.add("elapsed_ns",times);
            scenario.add("allocated_bytes",allocations);
        }
        JsonObject structure=new JsonObject();
        structure.addProperty("particle_limit_per_layer",COUNT);
        structure.addProperty("retained_hash_slots_limit",fast?8192:0);
        structure.addProperty("fixed_scratch_bytes",fast?scratchArrayPayload(buffer):0L);
        structure.addProperty("fixed_scratch_scope","Retained scratch array element payload after all scenarios; excludes array/object headers. The hash table grows lazily and is bounded, and all references are cleared after each operation.");
        probe.root.add("structure",structure);
        probe.write(args[1]);
    }

    private static void run(List<Item> particles,Item[] deaths,ParticleRemovalBuffer<Item> buffer,boolean fast)
    {
        ParticleRemovalBuffer<Item> acquired=fast && buffer.tryAcquire()?buffer:null;
        List<Item> removed=acquired==null?new ArrayList<Item>():acquired.removed();
        try
        {
            for(Item item:deaths) removed.add(item);
            if(acquired==null) particles.removeAll(removed);
            else acquired.removeFrom(particles);
        }
        finally { if(acquired!=null) acquired.release(); }
    }

    private static void reset(List<Item> target,Item[] source)
    {
        target.clear();
        for(Item item:source) target.add(item);
    }

    private static long check(String scene,List<Item> particles,long expected,int size)
    {
        long actual=1L;
        for(int i=0;i<particles.size();++i) actual=31L*actual+particles.get(i).id;
        blackhole=actual;
        if(actual!=expected || particles.size()!=size)
            throw new AssertionError("Particle cleanup mismatch in "+scene);
        return actual;
    }

    private static boolean dead(String scene,int id)
    {
        switch(scene)
        {
            case "all_live": return false;
            case "sparse_deaths": return id%500==0;
            case "half_dead": return (id&1)==0;
            case "all_dead": return true;
            default: throw new IllegalArgumentException(scene);
        }
    }

    private static long scratchArrayPayload(Object buffer) throws Exception
    {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Object removed=field(buffer,"removed");
        Object[] deadArray=(Object[])field(removed,"elementData");
        Object[] hashArray=(Object[])field(field(buffer,"membership"),"table");
        return ((long)deadArray.length+hashArray.length)*unsafe.arrayIndexScale(Object[].class);
    }

    private static Object field(Object object,String name) throws Exception
    {
        Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class Item
    {
        final int id;
        Item(int id) { this.id=id; }
        @Override public int hashCode() { return this.id; }
        @Override public boolean equals(Object other) { return other instanceof Item && ((Item)other).id==this.id; }
    }
}
