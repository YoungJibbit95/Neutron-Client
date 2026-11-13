package me.youngjibbit.core.diagnostics;

import com.google.gson.*;
import me.youngjibbit.utils.render.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.optifine.shaders.Shaders;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit, bounded evidence capture. Lifecycle and GL calls belong to the render thread. */
public final class PerformanceCaptureSession {
    public static final PerformanceCaptureSession INSTANCE = new PerformanceCaptureSession();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final CpuPhaseTimings cpu = new CpuPhaseTimings(System::nanoTime);
    private final GcEventCapture gc = new GcEventCapture();
    private Thread owner;
    private boolean active, phaseArmed, previousCounters;
    private Object world;
    private int width, height, guiScale, fpsLimit, renderDistance;
    private boolean vsync;
    private long shaderGeneration, performanceRevision, firstFrameSequence, lastFrameSequence;
    private JsonObject metadata, settingsStart, settingsEnd, memoryStart, memoryEnd;
    private Map<String, Long> countersStart, countersEnd;
    private PerformanceCaptureSession() { }

    public void start(Minecraft mc, int seconds) {
        requireOwner();
        if (active) throw new IllegalStateException("Stop the current capture before starting another");
        if (seconds < 1 || seconds > 600) throw new IllegalArgumentException("Duration must be 1–600 seconds");
        if (mc == null || mc.gameSettings == null) throw new IllegalStateException("Client settings are unavailable");
        // Preparation failures leave the previous completed capture intact.
        JsonObject nextMetadata = PerformanceCaptureMetadata.environment(mc);
        JsonObject nextSettings = PerformanceCaptureMetadata.configuration(mc);
        owner = Thread.currentThread();
        metadata = nextMetadata;
        settingsStart = nextSettings;
        performanceRevision = nextSettings.getAsJsonObject("effectivePerformance").get("revision").getAsLong();
        settingsEnd = null; memoryEnd = null; countersEnd = null;
        world = mc.theWorld; width = mc.displayWidth; height = mc.displayHeight;
        guiScale = mc.gameSettings.guiScale; fpsLimit = mc.gameSettings.limitFramerate;
        renderDistance = mc.gameSettings.renderDistanceChunks; vsync = mc.gameSettings.enableVsync;
        shaderGeneration = shaderGeneration();
        previousCounters = RenderDiagnostics.enabled;
        try {
            cpu.start(3 * FrameTimeCapture.DEFAULT_MAX_SAMPLES);
            FrameTimeCapture.INSTANCE.start(FrameTimeCapture.DEFAULT_MAX_SAMPLES,
                    FrameTimeCapture.DEFAULT_LONG_FRAME_NANOS, seconds * 1_000_000_000L);
            ChunkPipelineMetrics.INSTANCE.start(8192);
            GpuPassTimings.INSTANCE.start(GpuPassTimings.DEFAULT_MAX_SAMPLES);
            gc.start();
            countersStart = counters(); RenderDiagnostics.enabled = true;
            memoryStart = safeMetadata(() -> PerformanceCaptureMetadata.memory());
            firstFrameSequence = lastFrameSequence = -1;
            phaseArmed = false; active = true;
        } catch (RuntimeException | Error failure) {
            active = phaseArmed = false;
            // The component buffers may already have been replaced; never export a mixed capture.
            metadata = null;
            RenderDiagnostics.enabled = previousCounters;
            try { gc.stop(); } finally {
                try { cpu.stop(); FrameTimeCapture.INSTANCE.abort("Capture start failed"); }
                finally {
                    try { ChunkPipelineMetrics.INSTANCE.abort("Capture start failed"); }
                    finally { GpuPassTimings.INSTANCE.abort("Capture start failed"); }
                }
            }
            throw failure;
        }
    }

    /** Called once after swap, limiter wait and all completed game-loop work. */
    public void frame(Minecraft mc) {
        if (active && Thread.currentThread() == owner && !performanceConfigurationUnchanged()) return;
        GpuPassTimings.INSTANCE.poll();
        if (!active || Thread.currentThread() != owner) return;
        if (world != mc.theWorld || width != mc.displayWidth || height != mc.displayHeight
                || guiScale != mc.gameSettings.guiScale || fpsLimit != mc.gameSettings.limitFramerate
                || vsync != mc.gameSettings.enableVsync || renderDistance != mc.gameSettings.renderDistanceChunks
                || shaderGeneration != shaderGeneration()) {
            abort("World, resolution, GUI scale, frame limit, view distance or shader resources changed");
            return;
        }
        if (!phaseArmed) firstFrameSequence = mc.getRenderFrameSequence() + 1;
        else lastFrameSequence = mc.getRenderFrameSequence();
        FrameTimeCapture.INSTANCE.frame();
        phaseArmed = true;
        if (!FrameTimeCapture.INSTANCE.isRecording()) finish(mc);
    }
    public boolean isRecording() { return active; }
    public long beginCpu() {
        return performanceConfigurationUnchanged() && phaseArmed ? cpu.begin() : Long.MIN_VALUE;
    }
    public void endCpu(CpuPhaseTimings.Phase phase, long token, long frameSequence) {
        if (performanceConfigurationUnchanged() && phaseArmed) cpu.end(phase, token, frameSequence);
    }
    public long beginGpu(String phase) {
        return performanceConfigurationUnchanged() && phaseArmed
                ? GpuPassTimings.INSTANCE.begin(phase, Minecraft.getMinecraft().getRenderFrameSequence()) : 0;
    }
    public void endGpu(long token) {
        if (active && !performanceConfigurationUnchanged()) return;
        // stop() may happen inside a pass and deliberately leaves it draining. Its
        // late end must still belong to the original policy, including round trips.
        if (!active && token != GpuPassTimings.NONE && owner != null
                && PerformanceOptions.current().revision != performanceRevision) {
            requireOwner();
            GpuPassTimings.INSTANCE.abort("Performance options changed while GPU capture was draining");
            return;
        }
        GpuPassTimings.INSTANCE.end(token);
    }
    public void stop() {
        requireOwner();
        if (!performanceConfigurationUnchanged()) return;
        FrameTimeCapture.INSTANCE.stop(); finish(Minecraft.getMinecraft());
    }
    public void abort(String reason) {
        if (!active) return;
        requireOwner();
        FrameTimeCapture.INSTANCE.abort(reason); ChunkPipelineMetrics.INSTANCE.abort(reason);
        finish(Minecraft.getMinecraft());
    }
    /** Before context destruction: retain evidence, delete queries in their original context. */
    public void contextEnding(String reason) {
        abort(reason);
        if (owner == null || Thread.currentThread() == owner) GpuPassTimings.INSTANCE.abort(reason);
    }
    private void finish(Minecraft mc) {
        active = phaseArmed = false;
        try {
            memoryEnd = safeMetadata(() -> PerformanceCaptureMetadata.memory());
            countersEnd = counters();
        } finally {
            RenderDiagnostics.enabled = previousCounters;
            try { cpu.stop(); } finally {
                try { gc.stop(); } finally {
                    try { ChunkPipelineMetrics.INSTANCE.stop(); }
                    finally { GpuPassTimings.INSTANCE.stop(); }
                }
            }
        }
        // One terminal snapshot after the last measured boundary; no disk I/O or GL queries.
        settingsEnd = safeMetadata(() -> PerformanceCaptureMetadata.configuration(mc));
    }
    public String status() {
        requireOwner();
        performanceConfigurationUnchanged();
        FrameTimeCapture.Snapshot snapshot = FrameTimeCapture.INSTANCE.snapshot();
        FrameTimeStatistics stats = snapshot.statistics;
        if (stats.frames == 0) return snapshot.state + ": no complete frame intervals yet";
        return String.format(Locale.ROOT, "%s: %d frames, %.1f FPS, p95 %.3f ms, p99 %.3f ms, 1%% low %.1f FPS, %d frames > 50 ms",
                snapshot.state, stats.frames, stats.averageFps, stats.p95Milliseconds, stats.p99Milliseconds,
                stats.onePercentLowFps, stats.longFrames);
    }

    /** Explicit export after capture; no hashing or file writes occur in render hooks. */
    public File export(Minecraft mc, String label) throws IOException {
        requireOwner(); validateLabel(label);
        performanceConfigurationUnchanged();
        if (active) throw new IllegalStateException("Stop capture before export");
        if (metadata == null) throw new IllegalStateException("No capture to export");
        GpuPassTimings.INSTANCE.poll();
        FrameTimeCapture.Snapshot frames = FrameTimeCapture.INSTANCE.snapshot();
        ChunkPipelineMetrics.Snapshot chunks = ChunkPipelineMetrics.INSTANCE.snapshot();
        GpuPassTimings.Snapshot gpu = GpuPassTimings.INSTANCE.snapshot();
        CpuPhaseTimings.Snapshot phases = cpu.snapshot();
        JsonObject report = new JsonObject();
        report.addProperty("schema", "neutron.performance.v1"); report.addProperty("label", label);
        report.addProperty("scope", "Completed game-loop intervals; CPU phase wall time; asynchronous GPU timestamps; no display-latency measurement");
        report.add("environment", metadata); report.add("artifact", PerformanceCaptureMetadata.artifact());
        report.add("settingsStart", settingsStart); report.add("settingsEnd", settingsEnd);
        report.addProperty("settingsMatchAtEndpoints", settingsStart.equals(settingsEnd));
        report.addProperty("configurationRule", "Keep settings fixed. Performance-option revisions detect intervening changes; other endpoint equality cannot detect temporary changes and restoration.");
        report.addProperty("firstGameFrameSequence", firstFrameSequence);
        report.addProperty("lastCompleteGameFrameSequence", lastFrameSequence);
        report.addProperty("state", frames.state.name()); report.addProperty("reason", frames.reason);
        report.addProperty("frameCapacity", frames.capacity);
        report.addProperty("requestedDurationNanos", frames.requestedDurationNanos);
        report.addProperty("rejectedIntervals", frames.rejectedIntervals);
        report.add("frameStatistics", frameStatistics(frames.statistics));
        report.addProperty("onePercentLowDefinition", "1000 / mean(slowest ceil(frameCount * 0.01) intervals in milliseconds); nearest-rank percentiles");
        report.add("cpu", scalarFields(phases));
        report.addProperty("cpuScope", "WORLD includes shadow/world/composite/hand/overlays; HUD excludes standalone screen; GUI includes screen drawing. Partial stop-frame CPU/GPU samples may exist: use the complete frame range.");
        report.addProperty("unbracketedWork", "Vanilla post shaderGroup/entity-outline pass, GuiTransitions/notifications, ticks, swap/present and limiter are included in frame intervals but outside WORLD/HUD/GUI phase brackets.");
        report.add("gpu", scalarFields(gpu)); report.addProperty("gpuSampleCount", gpu.samples.size());
        report.addProperty("gpuScope", "Timestamp intervals are not exclusive utilization. Pending/abandoned/dropped counts are explicit; export again after a few frames to drain pending results.");
        report.add("chunks", scalarFields(chunks)); report.addProperty("chunkTraceCount", chunks.traces.size());
        report.addProperty("chunkScope", "Decoded client-thread column packet to matching main-camera layer-end draw submission. Upper bound on CPU submission; excludes server, transfer and GPU/display completion. -1 means unobserved.");
        report.add("memoryStart", memoryStart); report.add("memoryEnd", memoryEnd);
        report.add("gcEvents", JSON.toJsonTree(gc.snapshot()));
        report.add("renderCounterDelta", JSON.toJsonTree(difference(countersStart, countersEnd)));
        report.addProperty("renderCounterScope", "Intercepted API submissions and client helpers; binds include redundant requests, multi-draw counts once. Uninstrumented raw GL is outside coverage; not physical GPU state transitions.");
        Path root = mc.mcDataDir.toPath().resolve("performance"); Files.createDirectories(root);
        Path destination = Files.createTempDirectory(root, label + "-" + PerformanceCaptureMetadata.utc("yyyyMMdd-HHmmss") + "-");
        try (Writer writer = Files.newBufferedWriter(destination.resolve("frames.csv"), StandardCharsets.UTF_8)) { frames.writeCsv(writer); }
        try (Writer writer = Files.newBufferedWriter(destination.resolve("chunks.csv"), StandardCharsets.UTF_8)) { chunks.writeCsv(writer); }
        try (Writer writer = Files.newBufferedWriter(destination.resolve("gpu.csv"), StandardCharsets.UTF_8)) { gpu.writeCsv(writer); }
        try (Writer writer = Files.newBufferedWriter(destination.resolve("cpu.csv"), StandardCharsets.UTF_8)) { phases.writeCsv(writer); }
        try (Writer writer = Files.newBufferedWriter(destination.resolve("capture.json"), StandardCharsets.UTF_8)) { JSON.toJson(report, writer); }
        return destination.toFile();
    }

    static void validateLabel(String label) {
        if (label == null || !label.matches("[A-Za-z0-9_-]{1,48}")) throw new IllegalArgumentException("Label must contain 1–48 letters, digits, underscores or hyphens");
    }
    private static JsonObject safeMetadata(java.util.function.Supplier<JsonObject> source) {
        try { return source.get(); }
        catch (RuntimeException | LinkageError unavailable) {
            JsonObject result = new JsonObject(); result.addProperty("status", "UNAVAILABLE");
            result.addProperty("reason", unavailable.getClass().getSimpleName()); return result;
        }
    }
    static JsonObject frameStatistics(FrameTimeStatistics stats) {
        JsonObject result = new JsonObject();
        result.addProperty("frames", stats.frames); result.addProperty("longFrames", stats.longFrames);
        result.addProperty("onePercentSampleCount", stats.onePercentSampleCount);
        result.addProperty("longFrameThresholdNanos", stats.longFrameThresholdNanos);
        finite(result, "durationMilliseconds", stats.durationMilliseconds); finite(result, "averageFps", stats.averageFps);
        finite(result, "p50Milliseconds", stats.p50Milliseconds); finite(result, "p95Milliseconds", stats.p95Milliseconds);
        finite(result, "p99Milliseconds", stats.p99Milliseconds); finite(result, "onePercentLowFps", stats.onePercentLowFps);
        finite(result, "longFrameRate", stats.longFrameRate);
        return result;
    }
    private static void finite(JsonObject object, String name, double value) {
        if (Double.isFinite(value)) object.addProperty(name, value); else object.add(name, JsonNull.INSTANCE);
    }
    private static JsonObject scalarFields(Object source) {
        JsonObject result = new JsonObject();
        for (Field field : source.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!(type.isPrimitive() || type == String.class || type.isEnum())) continue;
            try { result.add(field.getName(), JSON.toJsonTree(field.get(source))); }
            catch (IllegalAccessException impossible) { throw new IllegalStateException(impossible); }
        }
        return result;
    }
    private static Map<String, Long> counters() {
        Map<String, Long> result = new TreeMap<>();
        for (Field field : RenderDiagnostics.class.getFields()) if (field.getType() == long.class && Modifier.isStatic(field.getModifiers())) {
            try { result.put(field.getName(), field.getLong(null)); }
            catch (IllegalAccessException impossible) { throw new IllegalStateException(impossible); }
        }
        return result;
    }
    private static Map<String, Long> difference(Map<String, Long> start, Map<String, Long> end) {
        Map<String, Long> result = new TreeMap<>();
        for (String name : start.keySet()) result.put(name, end.get(name) - start.get(name));
        return result;
    }
    private static long shaderGeneration() { return OpenGlHelper.shadersSupported ? Shaders.getRenderResourceGeneration() : 0; }

    /** Policy setters are GL-free; cleanup is deferred to an actual owner-thread capture hook. */
    private boolean performanceConfigurationUnchanged() {
        if (!active) return false;
        requireOwner();
        if (PerformanceOptions.current().revision == performanceRevision) return true;
        String reason = "Performance options changed";
        try {
            abort(reason);
        } finally {
            // Abandon open pairs before their now-stale end hooks can add mixed-configuration samples.
            GpuPassTimings.INSTANCE.abort(reason);
        }
        return false;
    }

    private void requireOwner() {
        if (owner != null && Thread.currentThread() != owner) throw new IllegalStateException("Performance capture belongs to its starting render thread");
    }
}
