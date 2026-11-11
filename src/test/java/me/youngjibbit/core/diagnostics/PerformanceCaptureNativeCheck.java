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

                // Context teardown may occur before asynchronous queries finish; it must never wait or leak IDs.
                RenderDiagnostics.enabled = false;
                CAPTURE.start(mc, 120); boundary(mc, 300); completeFrame(mc, 301);
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
            }
        }
        System.out.println("PASS: real GL capture orchestration, frame/CPU/GPU CSV join, JSON export, settings/context abort retention, counter restoration and query cleanup.");
        System.out.println("NOT COVERED: actual runGameLoop, native Display, full game world, external shaderpacks or hardware performance gains.");
    }

    private static void boundary(Minecraft mc, long frame) throws Exception {
        sequence.setLong(mc, frame); CAPTURE.frame(mc);
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
