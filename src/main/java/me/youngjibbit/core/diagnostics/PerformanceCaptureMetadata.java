package me.youngjibbit.core.diagnostics;

import com.google.gson.*;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.optifine.shaders.Shaders;
import net.optifine.shaders.config.ShaderOption;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/** Metadata is collected only at explicit capture start/stop/export, never per rendered pass. */
final class PerformanceCaptureMetadata {
    private static final Gson JSON = new Gson();
    private PerformanceCaptureMetadata() { }

    static JsonObject configuration(Minecraft mc) {
        JsonObject result = new JsonObject();
        // One read keeps flags, upload limits and revision from the same publication.
        PerformanceOptions.Snapshot performance = PerformanceOptions.current();
        JsonObject effectivePerformance = new JsonObject();
        effectivePerformance.addProperty("revision", performance.revision);
        effectivePerformance.addProperty("fastParticleCleanup", performance.fastParticleCleanup);
        effectivePerformance.addProperty("fastVisibilityGraph", performance.fastVisibilityGraph);
        effectivePerformance.addProperty("fastGaussian", performance.fastGaussian);
        effectivePerformance.addProperty("sceneBlurDivisor", performance.sceneBlurDivisor);
        effectivePerformance.addProperty("uploadBudgetNanos", performance.uploadLimits.nanoseconds);
        effectivePerformance.addProperty("uploadBudgetBytes", performance.uploadLimits.bytes);
        result.add("effectivePerformance", effectivePerformance);
        if (mc == null || mc.gameSettings == null) { result.addProperty("status", "UNAVAILABLE"); return result; }
        GameSettings settings = mc.gameSettings;
        result.addProperty("width", mc.displayWidth); result.addProperty("height", mc.displayHeight);
        // Primitive settings include graphics/OptiFine toggles, never account/server text.
        JsonObject numeric = new JsonObject();
        for (Field field : GameSettings.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive() || field.getType() == char.class) continue;
            try { numeric.add(field.getName(), JSON.toJsonTree(field.get(settings))); }
            catch (IllegalAccessException impossible) { throw new IllegalStateException(impossible); }
        }
        result.add("gameSettings", numeric);
        result.add("resourcePacks", JSON.toJsonTree(settings.resourcePacks));
        if (mc.getResourcePackRepository() != null) {
            JsonArray packs = new JsonArray();
            for (net.minecraft.client.resources.ResourcePackRepository.Entry entry : mc.getResourcePackRepository().getRepositoryEntries()) {
                packs.add(new JsonPrimitive(entry.getResourcePackName()));
            }
            result.add("activeResourcePacks", packs);
            if (mc.getResourcePackRepository().getResourcePackInstance() != null)
                result.addProperty("serverResourcePack", mc.getResourcePackRepository().getResourcePackInstance().getPackName());
        }
        result.addProperty("uploadBudgetNanos", performance.uploadLimits.nanoseconds);
        result.addProperty("uploadBudgetBytes", performance.uploadLimits.bytes);
        if (OpenGlHelper.shadersSupported) {
            result.addProperty("shaderPack", Shaders.getShaderPackName());
            result.addProperty("shaderResourceGeneration", Shaders.getRenderResourceGeneration());
            result.addProperty("shaderRenderScale", Shaders.configRenderResMul);
            result.addProperty("shaderShadowScale", Shaders.configShadowResMul);
            result.addProperty("shaderRenderWidth", Shaders.renderWidth); result.addProperty("shaderRenderHeight", Shaders.renderHeight);
            JsonObject options = new JsonObject();
            ShaderOption[] packOptions = Shaders.getShaderPackOptions();
            if (packOptions != null) for (ShaderOption option : packOptions) {
                if (option != null) options.addProperty(option.getName(), option.getValue());
            }
            result.add("shaderOptions", options);
        } else result.addProperty("shaderPack", "UNSUPPORTED");
        JsonObject modules = new JsonObject();
        if (Pulsar.INSTANCE.getMm() != null) for (Module module : Pulsar.INSTANCE.getMm().getModules().values()) {
            if (module.getCategory() != Category.Render && module.getCategory() != Category.Client) continue;
            JsonObject data = new JsonObject(); data.addProperty("enabled", module.isToggled());
            for (Value<?> value : module.getValues()) {
                if (value instanceof CheckBoxValue || value instanceof SliderValue || value instanceof ComboValue) {
                    data.add(value.getId(), JSON.toJsonTree(value.getValue()));
                } else if (value instanceof ColorValue) {
                    ColorValue color = (ColorValue)value;
                    JsonObject properties = new JsonObject(); properties.addProperty("argb", color.getValue().getRGB());
                    properties.addProperty("rainbow", color.isRainbow());
                    if (color.isRainbow()) properties.add("animation", color.getRainbow().getJsonObject());
                    data.add(value.getId(), properties);
                }
            }
            modules.add(module.getId(), data);
        }
        result.add("visualModules", modules);
        return result;
    }

    static JsonObject environment(Minecraft mc) {
        JsonObject result = new JsonObject(); result.addProperty("startedUtc", utc("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        for (String name : new String[]{"os.name", "os.version", "os.arch", "java.version", "java.runtime.version", "java.vm.name", "java.vendor"}) result.addProperty(name, System.getProperty(name));
        result.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty("cpu", cpuName());
        result.addProperty("gpuVendor", GL11.glGetString(GL11.GL_VENDOR));
        result.addProperty("gpuRenderer", GL11.glGetString(GL11.GL_RENDERER));
        result.addProperty("glVersionAndDriver", GL11.glGetString(GL11.GL_VERSION));
        List<String> flags = new ArrayList<>();
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.matches("-Xm[sx][0-9]+[kKmMgG]?|-XX:[+-]Use[A-Za-z0-9]+GC|-XX:(?:MaxDirectMemorySize|MaxGCPauseMillis|G1HeapRegionSize)=[0-9]+[kKmMgG]?")) flags.add(argument);
        }
        result.add("heapAndGcArguments", JSON.toJsonTree(flags));
        JsonObject vmOptions = new JsonObject();
        try {
            com.sun.management.HotSpotDiagnosticMXBean vm = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            for (String name : new String[]{"UseG1GC", "UseParallelGC", "UseConcMarkSweepGC", "UseSerialGC", "InitialHeapSize", "MaxHeapSize", "MaxDirectMemorySize", "ParallelGCThreads", "ConcGCThreads", "MaxGCPauseMillis"}) {
                try { vmOptions.addProperty(name, vm.getVMOption(name).getValue()); }
                catch (IllegalArgumentException unsupported) { vmOptions.add(name, JsonNull.INSTANCE); }
            }
        } catch (RuntimeException unavailable) { vmOptions.addProperty("status", "UNAVAILABLE"); }
        result.add("effectiveVmOptions", vmOptions);
        if (OpenGlHelper.shadersSupported && Shaders.getShaderPackName() != null) {
            Path packRoot = mc.mcDataDir.toPath().resolve("shaderpacks").toAbsolutePath().normalize();
            Path pack = packRoot.resolve(Shaders.getShaderPackName()).normalize();
            if (packRoot.equals(pack.getParent())) result.add("shaderPackArtifactAtStart", fileIdentity(pack));
        }
        result.addProperty("revisionLabel", System.getProperty("neutron.performance.revision", "UNSPECIFIED; use artifact SHA-256"));
        result.addProperty("instrumentation", "Frame/CPU/GPU/chunk/GC/counter capture enabled; compare both variants with equivalent instrumentation");
        return result;
    }

    private static String cpuName() {
        String windows = System.getenv("PROCESSOR_IDENTIFIER");
        if (windows != null) return windows;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/cpuinfo"), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) if (line.startsWith("model name") && line.indexOf(':') >= 0) return line.substring(line.indexOf(':') + 1).trim();
        } catch (IOException unavailable) { /* Architecture remains in metadata. */ }
        return "UNAVAILABLE";
    }

    static JsonObject memory() {
        JsonObject result = new JsonObject();
        result.addProperty("heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        result.addProperty("heapCommittedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted());
        result.addProperty("heapMaxBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax());
        result.addProperty("nonHeapUsedBytes", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            JsonObject data = new JsonObject(); data.addProperty("count", pool.getCount());
            data.addProperty("usedBytes", pool.getMemoryUsed()); data.addProperty("capacityBytes", pool.getTotalCapacity()); result.add(pool.getName(), data);
        }
        JsonObject collectors = new JsonObject();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            JsonObject data = new JsonObject(); data.addProperty("count", bean.getCollectionCount()); data.addProperty("collectionTimeMillis", bean.getCollectionTime()); collectors.add(bean.getName(), data);
        }
        result.add("gcCumulative", collectors);
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean instanceof com.sun.management.ThreadMXBean) {
            com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean)threadBean;
            if (allocation.isThreadAllocatedMemorySupported() && allocation.isThreadAllocatedMemoryEnabled()) result.addProperty("renderThreadAllocatedBytes", allocation.getThreadAllocatedBytes(Thread.currentThread().getId()));
        }
        result.addProperty("scope", "Capture start/stop endpoints, including partial start/stop and capture overhead; allocation covers render thread only, GC time is cumulative collector time");
        return result;
    }

    static JsonObject artifact() {
        try {
            Path path = Paths.get(PerformanceCaptureSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return fileIdentity(path);
        } catch (Exception unavailable) {
            JsonObject result = new JsonObject(); result.addProperty("sha256", "UNAVAILABLE: " + unavailable.getClass().getSimpleName()); return result;
        }
    }
    private static JsonObject fileIdentity(Path path) {
        JsonObject result = new JsonObject();
        try {
            result.addProperty("name", path.getFileName().toString());
            if (!Files.isRegularFile(path)) { result.addProperty("sha256", "UNAVAILABLE: not a regular packaged file"); return result; }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream stream = Files.newInputStream(path)) { int length; while ((length = stream.read(buffer)) >= 0) if (length != 0) digest.update(buffer, 0, length); }
            StringBuilder hash = new StringBuilder(); for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            result.addProperty("sha256", hash.toString()); result.addProperty("bytes", Files.size(path));
        } catch (Exception unavailable) { result.addProperty("sha256", "UNAVAILABLE: " + unavailable.getClass().getSimpleName()); }
        return result;
    }
    static String utc(String format) {
        SimpleDateFormat date = new SimpleDateFormat(format, Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("UTC")); return date.format(new Date());
    }
}
