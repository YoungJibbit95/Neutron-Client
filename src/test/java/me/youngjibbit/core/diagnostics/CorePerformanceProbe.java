package me.youngjibbit.core.diagnostics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;

/** Standalone CPU probe support. Timing assertions deliberately live outside CI pass/fail. */
public final class CorePerformanceProbe
{
    public static final int WARMUP = 1000;
    public static final int SAMPLES = 40;
    private final com.sun.management.ThreadMXBean allocations;
    private final long threadId = Thread.currentThread().getId();
    public final JsonObject root = new JsonObject();
    public final JsonArray scenarios = new JsonArray();

    public CorePerformanceProbe(String benchmark, String mode, String scope)
    {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean candidate = bean instanceof com.sun.management.ThreadMXBean
                ? (com.sun.management.ThreadMXBean)bean : null;
        if (candidate != null)
        {
            try
            {
                if (candidate.isThreadAllocatedMemorySupported() && !candidate.isThreadAllocatedMemoryEnabled())
                    candidate.setThreadAllocatedMemoryEnabled(true);
                if (!candidate.isThreadAllocatedMemorySupported() || !candidate.isThreadAllocatedMemoryEnabled())
                    candidate = null;
            }
            catch (UnsupportedOperationException | SecurityException unavailable) { candidate = null; }
        }
        this.allocations = candidate;
        this.root.addProperty("schema", "neutron.core-performance.v1");
        this.root.addProperty("benchmark", benchmark);
        this.root.addProperty("mode", mode);
        this.root.addProperty("baseline_ref", "5eccfb7b28110731e9d522dfb6e20ca3270b6ec3");
        this.root.addProperty("java_runtime", System.getProperty("java.runtime.version"));
        this.root.addProperty("processors", Runtime.getRuntime().availableProcessors());
        this.root.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
        this.root.addProperty("allocation_counter_enabled", this.allocations != null);
        this.root.addProperty("scope", scope);
        this.root.addProperty("baseline_note", "Same current classes in separate legacy/fast JVMs. Visibility bit-index correction applies to both strategies; this is not an unmodified historical-client FPS comparison.");
        this.root.add("scenarios", this.scenarios);
    }

    public static boolean fastMode(String[] args)
    {
        if (args.length != 2 || !("legacy".equals(args[0]) || "fast".equals(args[0])))
            throw new IllegalArgumentException("Usage: legacy|fast output.json");
        return "fast".equals(args[0]);
    }

    public long allocatedBytes()
    {
        return this.allocations == null ? -1L : this.allocations.getThreadAllocatedBytes(this.threadId);
    }

    public JsonObject scenario(String name, JsonObject parameters, int batch, String signature)
    {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.add("parameters", parameters);
        result.addProperty("warmup_iterations", WARMUP);
        result.addProperty("iterations_per_sample", batch);
        result.addProperty("expected_signature", signature);
        result.addProperty("observed_signature", signature);
        this.scenarios.add(result);
        return result;
    }

    public void write(String path) throws Exception
    {
        try (PrintWriter output = new PrintWriter(path, "UTF-8"))
        {
            new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(this.root, output);
            if (output.checkError()) throw new java.io.IOException("Could not write complete benchmark JSON");
        }
    }
}
