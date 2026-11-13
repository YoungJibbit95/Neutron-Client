package me.youngjibbit.core.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.PropertyMap;
import java.io.File;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.render.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import static org.lwjgl.opengl.GL11.*;

/** Real offscreen GL plus production capture hooks/export; no claim to execute the game loop or a world. */
public final class PerformanceCaptureNativeCheck {
    private static final PerformanceCaptureSession CAPTURE = PerformanceCaptureSession.INSTANCE;
    private static Field sequence;

    public static void main(String[] args) throws Exception {
        File root = new File("performance-capture-preview"); root.mkdirs();
        System.setProperty("user.home", new File(root, "home").getAbsolutePath());
        File data = Files.createTempDirectory(root.toPath(), "fixture-").toFile();
        boolean previousCounters = RenderDiagnostics.enabled;
        PerformanceOptions.Snapshot previousOptions = PerformanceOptions.current();
        PerformanceOptions.defaults();
        try (PbufferTestContext context = new PbufferTestContext()) {
            Minecraft mc = new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("CaptureFixture",
                            "00000000000000000000000000000000", "0", "legacy"),
                            new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(64, 48, false, false),
                    new GameConfiguration.FolderInformation(data, new File(data, "packs"), new File(data, "assets"), null),
                    new GameConfiguration.GameInformation(false, "Capture lifecycle fixture"),
                    new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data);
            sequence = field(Minecraft.class, "renderFrameSequence");
            Framebuffer target = new Framebuffer(64, 48, true);
            field(Minecraft.class, "framebufferMc").set(mc, target);
            try {
                target.bindFramebuffer(true);
                GlStateManager.disableDepth(); glDisable(GL_DEPTH_TEST);
                GlStateManager.disableTexture2D(); glDisable(GL_TEXTURE_2D);
                GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
                glMatrixMode(GL_PROJECTION); glLoadIdentity(); glMatrixMode(GL_MODELVIEW); glLoadIdentity();
                RenderDiagnostics.enabled = false;
                CAPTURE.start(mc, 120);
                require(GpuPassTimings.INSTANCE.snapshot().supported, "Actual timer-query support required for this fixture");
                require(RenderDiagnostics.enabled, "Capture did not enable counters");
                boundary(mc, 100); // first full interval starts here
                for (long frame = 101; frame <= 103; frame++) completeFrame(mc, frame);
                CAPTURE.stop();
                require(!CAPTURE.isRecording() && !RenderDiagnostics.enabled, "Stop did not restore disabled counters");
                drain(mc);
                JsonObject report = exportAndCheck(mc, "completed", 3, "STOPPED");
                require(report.get("firstGameFrameSequence").getAsLong() == 101, "First complete-frame ID");
                require(report.get("lastCompleteGameFrameSequence").getAsLong() == 103, "Last complete-frame ID");
                require(report.get("gpuSampleCount").getAsInt() == 3, "Missing actual GPU phase rows");
                require(report.get("settingsMatchAtEndpoints").getAsBoolean(), "Unchanged fixture settings differ");
                JsonObject counters = report.getAsJsonObject("renderCounterDelta");
                require(counters.get("programBinds").getAsLong() >= 3, "Real program API requests were not counted");
                require(counters.get("framebufferBinds").getAsLong() >= 3, "Real framebuffer API requests were not counted");
                JsonObject initialOptions = report.getAsJsonObject("settingsStart").getAsJsonObject("effectivePerformance");
                require(initialOptions.get("revision").getAsLong() == PerformanceOptions.current().revision,
                        "Start metadata did not use the effective option snapshot");
                require(initialOptions.get("sceneBlurDivisor").getAsInt() == 1
                        && !initialOptions.get("fastGaussian").getAsBoolean(), "Default effective options not exported");

                // Settings abort must keep prior full-frame evidence and preserve an already enabled counter owner.
                RenderDiagnostics.enabled = true;
                CAPTURE.start(mc, 120); boundary(mc, 200); completeFrame(mc, 201);
                int guiScale = mc.gameSettings.guiScale;
                mc.gameSettings.guiScale = guiScale + 1;
                boundary(mc, 202);
                require(!CAPTURE.isRecording() && RenderDiagnostics.enabled, "Settings abort lost previous counter state");
                drain(mc);
                report = exportAndCheck(mc, "settings-abort", 1, "ABORTED");
                require(!report.get("settingsMatchAtEndpoints").getAsBoolean(), "Settings change went unreported");
                mc.gameSettings.guiScale = guiScale;

                // Reapplying unchanged effective values must not interrupt a real capture.
                CAPTURE.start(mc, 120); boundary(mc, 220); completeFrame(mc, 221);
                long unchangedRevision = PerformanceOptions.current().revision;
                require(!PerformanceOptions.configure(false, false, false, 1, 1, 4), "Unchanged options were published");
                require(PerformanceOptions.current().revision == unchangedRevision, "Unchanged revision advanced");
                completeFrame(mc, 222);
                require(CAPTURE.isRecording(), "Unchanged apply aborted recording");
                CAPTURE.stop(); drain(mc);
                report = exportAndCheck(mc, "performance-unchanged", 2, "STOPPED");
                require(report.get("settingsMatchAtEndpoints").getAsBoolean(), "Unchanged options differ at endpoints");

                // Settings callbacks remain GL-free. A changed-and-restored policy is still detected
                // at the next owner-thread boundary, before accepting an interval with mixed choices.
                CAPTURE.start(mc, 120); boundary(mc, 240); completeFrame(mc, 241);
                PerformanceOptions.Snapshot beforeRoundTrip = PerformanceOptions.current();
                sequence.setLong(mc, 242);
                long interruptedCpu = CAPTURE.beginCpu();
                long interruptedGpu = CAPTURE.beginGpu("POLICY_PENDING");
                require(interruptedGpu != 0, "Policy fixture did not open its real GPU pair");
                int queryIds = GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining;
                PerformanceOptions.configure(true, true, true, 4, 2, 8);
                restoreOptions(beforeRoundTrip);
                require(CAPTURE.isRecording(), "Policy publication performed capture teardown in the setting callback");
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == queryIds,
                        "Policy setter deleted native queries");
                require(PerformanceOptions.current().revision == beforeRoundTrip.revision + 2,
                        "Changed-and-restored options lost their monotone revision");
                boundary(mc, 242);
                require(!CAPTURE.isRecording(), "Intervening policy change escaped the frame boundary");
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0, "Policy abort retained query IDs");
                require(GpuPassTimings.INSTANCE.snapshot().abandoned > 0, "Interrupted GPU pass was not reported abandoned");
                CAPTURE.endCpu(CpuPhaseTimings.Phase.WORLD, interruptedCpu, 242);
                CAPTURE.endGpu(interruptedGpu);
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0, "Stale policy-pass end recreated queries");
                report = exportAndCheck(mc, "performance-roundtrip-abort", 1, "ABORTED");
                require("Performance options changed".equals(report.get("reason").getAsString()), "Policy abort reason missing");
                require(!report.get("settingsMatchAtEndpoints").getAsBoolean(), "Revision did not reveal the restored change");
                JsonObject startOptions = report.getAsJsonObject("settingsStart").getAsJsonObject("effectivePerformance");
                JsonObject endOptions = report.getAsJsonObject("settingsEnd").getAsJsonObject("effectivePerformance");
                require(startOptions.get("revision").getAsLong() == beforeRoundTrip.revision,
                        "Capture revision did not match its start snapshot");
                require(endOptions.get("revision").getAsLong() == beforeRoundTrip.revision + 2,
                        "Capture end omitted the intervening revisions");
                require(startOptions.get("fastGaussian").equals(endOptions.get("fastGaussian"))
                        && startOptions.get("sceneBlurDivisor").equals(endOptions.get("sceneBlurDivisor")),
                        "Fixture must actually restore the original option values");

                // The earlier end-hook also detects a change without waiting for a full-frame hook.
                CAPTURE.start(mc, 120); boundary(mc, 260); completeFrame(mc, 261);
                sequence.setLong(mc, 262);
                interruptedCpu = CAPTURE.beginCpu();
                interruptedGpu = CAPTURE.beginGpu("POLICY_END_HOOK");
                require(interruptedGpu != 0, "End-hook fixture did not open a GPU pair");
                PerformanceOptions.configure(false, false, true, 2);
                CAPTURE.endGpu(interruptedGpu);
                CAPTURE.endCpu(CpuPhaseTimings.Phase.WORLD, interruptedCpu, 262);
                require(!CAPTURE.isRecording() && GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0,
                        "Policy-changing end hook retained active capture or query objects");
                report = exportAndCheck(mc, "performance-pass-abort", 1, "ABORTED");
                require(report.getAsJsonObject("settingsEnd").getAsJsonObject("effectivePerformance")
                        .get("fastGaussian").getAsBoolean(), "Effective enabled option missing at abort");
                PerformanceOptions.defaults();

                // Legacy budget-only callers participate in the same revision contract; stop must
                // report the configuration interruption even when no subsequent frame was drawn.
                CAPTURE.start(mc, 120); boundary(mc, 280); completeFrame(mc, 281);
                ChunkFrameBudget.configure(1.25, 5);
                CAPTURE.stop();
                report = exportAndCheck(mc, "performance-budget-abort", 1, "ABORTED");
                require("Performance options changed".equals(report.get("reason").getAsString()),
                        "Budget-only change was mislabeled as a normal stop");
                PerformanceOptions.defaults();

                // A normal stop inside a GPU pass must preserve its unchanged late end.
                CAPTURE.start(mc, 120); boundary(mc, 300); completeFrame(mc, 301);
                sequence.setLong(mc, 302);
                long stoppedGpu = CAPTURE.beginGpu("STOP_UNCHANGED");
                require(stoppedGpu != 0, "Stop fixture did not open a GPU pair");
                CAPTURE.stop();
                require(GpuPassTimings.INSTANCE.snapshot().state == GpuPassTimings.State.DRAINING,
                        "Stop did not leave the open GPU pair draining");
                CAPTURE.endGpu(stoppedGpu); drain(mc);
                GpuPassTimings.Snapshot stopped = GpuPassTimings.INSTANCE.snapshot();
                require(stopped.state == GpuPassTimings.State.STOPPED && stopped.samples.size() == 2
                        && stopped.abandoned == 0, "Unchanged late GPU end was lost");
                require(stopped.samples.get(1).frameSequence == 302,
                        "Partial stop-frame GPU pass lost its actual frame mapping");
                require(FrameTimeCapture.INSTANCE.snapshot().statistics.frames == 1,
                        "GPU draining invented a completed frame interval");

                // The frame capture has ended, but an open GPU bracket must not cross
                // an option revision before its late end hook, even when values return.
                CAPTURE.start(mc, 120); boundary(mc, 320); completeFrame(mc, 321);
                sequence.setLong(mc, 322);
                stoppedGpu = CAPTURE.beginGpu("STOP_POLICY_PENDING");
                require(stoppedGpu != 0, "Stopped-policy fixture did not open a GPU pair");
                PerformanceOptions.Snapshot beforeStoppedChange = PerformanceOptions.current();
                CAPTURE.stop();
                GpuPassTimings.Snapshot draining = GpuPassTimings.INSTANCE.snapshot();
                require(draining.state == GpuPassTimings.State.DRAINING && draining.pending > 0,
                        "Stopped-policy fixture did not retain its open pair");
                PerformanceOptions.configure(false, false, true, 2);
                restoreOptions(beforeStoppedChange);
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == draining.queryObjectsRemaining,
                        "Policy setter touched the stopped capture's native query pool");
                CAPTURE.endGpu(stoppedGpu);
                GpuPassTimings.Snapshot discarded = GpuPassTimings.INSTANCE.snapshot();
                require(discarded.state == GpuPassTimings.State.ABORTED && discarded.pending == 0
                        && discarded.queryObjectsRemaining == 0, "Changed late GPU end retained pending queries");
                require(discarded.abandoned == draining.abandoned + draining.pending
                        && discarded.samples.size() == draining.samples.size(),
                        "Changed late GPU end accepted new samples or discarded already resolved evidence");
                CAPTURE.endGpu(stoppedGpu); // repeated stale ends remain neutral after cleanup
                require(GpuPassTimings.INSTANCE.snapshot().abandoned == discarded.abandoned,
                        "Repeated stale end changed abandoned-sample accounting");
                report = exportAndCheck(mc, "performance-stopped-pass-abort", 1, "STOPPED");
                require("ABORTED".equals(report.getAsJsonObject("gpu").get("state").getAsString())
                        && "Performance options changed while GPU capture was draining".equals(
                                report.getAsJsonObject("gpu").get("reason").getAsString()),
                        "Stopped frame capture hid the interrupted GPU drain");
                require(report.get("settingsMatchAtEndpoints").getAsBoolean()
                        && report.getAsJsonObject("settingsEnd").getAsJsonObject("effectivePerformance")
                                .get("revision").getAsLong() == beforeStoppedChange.revision,
                        "Later GPU cleanup rewrote the completed frame capture's stop snapshot");

                // Context teardown may occur before asynchronous queries finish; it must never wait or leak IDs.
                RenderDiagnostics.enabled = false;
                CAPTURE.start(mc, 120); boundary(mc, 340); completeFrame(mc, 341);
                long unfinished = CAPTURE.beginGpu("ABORT_PENDING");
                require(unfinished != 0 && GpuPassTimings.INSTANCE.snapshot().pending > 0,
                        "Context-abort fixture must own an unfinished real timestamp pair");
                CAPTURE.contextEnding("Fixture context ending");
                require(!CAPTURE.isRecording() && !RenderDiagnostics.enabled, "Context abort did not stop capture");
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0, "Context abort retained query IDs");
                require(GpuPassTimings.INSTANCE.snapshot().abandoned > 0, "Unfinished timestamp pair was not reported abandoned");
                CAPTURE.endGpu(unfinished); // stale token must not recreate native objects after teardown
                require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0, "Stale end recreated query IDs");
                exportAndCheck(mc, "context-abort", 1, "ABORTED");
                context.check();
            } finally {
                CAPTURE.contextEnding("Fixture final cleanup");
                GpuPassTimings.INSTANCE.reset();
                target.deleteFramebuffer();
                RenderDiagnostics.enabled = previousCounters;
                restoreOptions(previousOptions);
            }
        }
        System.out.println("PASS: real GL capture orchestration, frame/CPU/GPU CSV join, JSON export, settings/context abort retention, performance revision/roundtrip/open-pass guards, counter restoration and query cleanup.");
        System.out.println("NOT COVERED: actual runGameLoop, native Display, full game world, external shaderpacks or hardware performance gains.");
    }

    private static void boundary(Minecraft mc, long frame) throws Exception {
        sequence.setLong(mc, frame); CAPTURE.frame(mc);
    }
    private static void restoreOptions(PerformanceOptions.Snapshot options) {
        PerformanceOptions.configure(options.fastParticleCleanup, options.fastVisibilityGraph,
                options.fastGaussian, options.sceneBlurDivisor,
                options.uploadLimits.nanoseconds / 1_000_000D, options.uploadLimits.bytes / 1_048_576D);
    }
    private static void completeFrame(Minecraft mc, long frame) throws Exception {
        sequence.setLong(mc, frame);
        long cpu = CAPTURE.beginCpu(), gpu = CAPTURE.beginGpu("WORLD");
        require(gpu != 0, "Enabled capture did not issue a real timestamp pair");
        try {
            mc.getFramebuffer().bindFramebuffer(false);
            OpenGlHelper.glUseProgram(0);
            glColor4f(.2f, .4f, .6f, 1);
            glBegin(GL_QUADS); glVertex2f(-1, -1); glVertex2f(1, -1); glVertex2f(1, 1); glVertex2f(-1, 1); glEnd();
        } finally {
            CAPTURE.endGpu(gpu); CAPTURE.endCpu(CpuPhaseTimings.Phase.WORLD, cpu, frame);
        }
        CAPTURE.frame(mc);
    }
    private static void drain(Minecraft mc) throws Exception {
        glFlush(); // offscreen fixture only: production capture never forces GL submission or completion
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (GpuPassTimings.INSTANCE.snapshot().pending != 0 && System.nanoTime() < deadline) {
            CAPTURE.frame(mc); Thread.sleep(1);
        }
        require(GpuPassTimings.INSTANCE.snapshot().pending == 0, "GPU results did not drain");
        require(GpuPassTimings.INSTANCE.snapshot().queryObjectsRemaining == 0, "Stopped capture retained query IDs");
    }
    private static JsonObject exportAndCheck(Minecraft mc, String label, int expectedFrames, String state) throws Exception {
        File output = CAPTURE.export(mc, label);
        JsonObject report;
        try (Reader reader = Files.newBufferedReader(new File(output, "capture.json").toPath(), StandardCharsets.UTF_8)) {
            report = new JsonParser().parse(reader).getAsJsonObject();
        }
        require(state.equals(report.get("state").getAsString()), "Export state mismatch");
        require(report.getAsJsonObject("frameStatistics").get("frames").getAsInt() == expectedFrames, "Complete intervals lost");
        require(lines(output, "frames.csv").size() == expectedFrames + 1, "Frame CSV length");
        long first = report.get("firstGameFrameSequence").getAsLong();
        long last = report.get("lastCompleteGameFrameSequence").getAsLong();
        List<String> cpu = lines(output, "cpu.csv");
        require(cpu.size() == expectedFrames + 1, "CPU CSV length");
        for (int i = 1; i < cpu.size(); i++) {
            long frame = Long.parseLong(cpu.get(i).split(",")[1]);
            require(frame >= first && frame <= last, "CPU frame outside complete range");
        }
        List<String> gpu = lines(output, "gpu.csv");
        require(gpu.get(0).contains("game_frame_sequence"), "GPU join key missing");
        for (int i = 1; i < gpu.size(); i++) {
            String[] row = gpu.get(i).split(",");
            long frame = Long.parseLong(row[2]);
            require(frame >= first && frame <= last, "GPU frame outside complete range");
            require("VALID".equals(row[row.length - 1]), "Invalid native GPU interval");
        }
        require(lines(output, "chunks.csv").size() == 1, "Fixture must not pretend to have chunk samples");
        System.out.println("Validated " + label + ": completeFrames=" + expectedFrames + ", cpuRows=" + (cpu.size() - 1)
                + ", gpuRows=" + (gpu.size() - 1) + ", state=" + state);
        return report;
    }
    private static List<String> lines(File directory, String name) throws Exception {
        return Files.readAllLines(new File(directory, name).toPath(), StandardCharsets.UTF_8);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
