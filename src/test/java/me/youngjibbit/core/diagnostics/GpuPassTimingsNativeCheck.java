package me.youngjibbit.core.diagnostics;

import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import me.youngjibbit.rendercheck.PbufferTestContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;
import static org.lwjgl.opengl.GL11.*;

/** Explicit native probe: actual queries and drawing, not a full-world or hardware FPS benchmark. */
public final class GpuPassTimingsNativeCheck {
    public static void main(String[] args) throws Exception {
        GpuPassTimings capture = new GpuPassTimings();
        int vertex = 0, fragment = 0, program = 0, outerQuery = 0, framebuffer = 0, color = 0;
        boolean outerActive = false;
        try (PbufferTestContext context = new PbufferTestContext()) {
            try {
                System.out.println("GL_VENDOR=" + glGetString(GL_VENDOR));
                System.out.println("GL_RENDERER=" + glGetString(GL_RENDERER));
                System.out.println("GL_VERSION=" + glGetString(GL_VERSION));
                require(GLContext.getCapabilities().GL_EXT_framebuffer_object, "Native color FBO support required");
                // Match the client's offscreen render path. Drawing into the Pbuffer's
                // default drawable can invoke an unrelated X11 visual/present path on flush.
                framebuffer = EXTFramebufferObject.glGenFramebuffersEXT();
                color = EXTFramebufferObject.glGenRenderbuffersEXT();
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
                EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, color);
                EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, GL_RGBA8, 64, 64);
                EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, color);
                glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
                glReadBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
                require(EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                        == EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT, "Native color FBO incomplete");
                glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
                capture.start(8);
                require(capture.snapshot().supported, "Native timer support required: " + capture.snapshot().reason);
                vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nvoid main(){gl_Position=gl_Vertex;}");
                fragment = compile(GL20.GL_FRAGMENT_SHADER, "#version 120\nvoid main(){gl_FragColor=vec4(.2,.4,.6,1.);}");
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
                require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, "Native shader did not link");
                GL20.glUseProgram(program); glViewport(1, 2, 60, 59);
                glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND);
                outerQuery = GL15.glGenQueries();
                GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, outerQuery); outerActive = true;
                for (int frame = 0; frame < 4; frame++) {
                    long world = capture.begin("NATIVE_OUTER");
                    draw();
                    long hud = capture.begin("NATIVE_NESTED");
                    draw(); capture.end(hud); capture.end(world);
                    capture.poll();
                    require(GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) == outerQuery,
                            "Timestamp markers changed an external elapsed-query binding");
                    require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "Markers changed the active shader");
                    require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == framebuffer,
                            "Markers changed the active color FBO");
                }
                capture.stop();
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED); outerActive = false;
                // The offscreen fixture has no swap/present to submit its work. Flush only here;
                // production capture performs no flush, finish, wait or busy polling.
                glFlush();
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (capture.snapshot().pending != 0 && System.nanoTime() < deadline) {
                    capture.poll(); Thread.sleep(1);
                }
                GpuPassTimings.Snapshot result = capture.snapshot();
                require(result.pending == 0, "Native queries did not complete within fixture timeout");
                require(result.samples.size() == 8 && result.invalidResults == 0, "Missing or invalid native timestamps");
                require(result.queryObjectsRemaining == 0, "Capture retained completed query IDs");
                boolean nonzero = false;
                for (GpuPassTimings.Sample row : result.samples) nonzero |= row.elapsedNanos > 0;
                require(nonzero, "Native query fixture produced no measured time");
                IntBuffer viewport = BufferUtils.createIntBuffer(16); glGetInteger(GL_VIEWPORT, viewport);
                require(viewport.get(0) == 1 && viewport.get(1) == 2 && viewport.get(2) == 60 && viewport.get(3) == 59,
                        "Capture changed the viewport");
                require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "Drain changed the active shader");
                require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == framebuffer,
                        "Drain changed the active color FBO");
                ByteBuffer pixel = BufferUtils.createByteBuffer(4);
                glReadPixels(31, 31, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                require(Math.abs((pixel.get(0) & 255) - 51) <= 1 && Math.abs((pixel.get(1) & 255) - 102) <= 1
                        && Math.abs((pixel.get(2) & 255) - 153) <= 1 && (pixel.get(3) & 255) == 255,
                        "Actual shader draw did not produce its expected RGBA pixel");
                require(glGetError() == GL_NO_ERROR, "GL error during actual timestamp probe");
                context.check();
                System.out.println("PASS: real asynchronous " + result.api + " timestamps; counterBits=" + result.counterBits
                        + "; nested=8; query IDs released; color FBO/readback, shader/viewport/external elapsed query preserved.");
                OutputStreamWriter csv = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
                result.writeCsv(csv); csv.flush();
                System.out.println("NOT COVERED: hardware performance gains, full world, external shaderpacks, presentation latency.");
            } finally {
                capture.abort("Native fixture cleanup"); capture.reset();
                if (outerActive) GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
                if (outerQuery != 0) GL15.glDeleteQueries(outerQuery);
                GL20.glUseProgram(0);
                if (program != 0) GL20.glDeleteProgram(program);
                if (vertex != 0) GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
                if (framebuffer != 0) {
                    EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
                }
                if (color != 0) EXTFramebufferObject.glDeleteRenderbuffersEXT(color);
            }
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096); GL20.glDeleteShader(shader);
            throw new AssertionError(log);
        }
        return shader;
    }
    private static void draw() {
        for (int i = 0; i < 32; i++) {
            glBegin(GL_QUADS); glVertex2f(-1, -1); glVertex2f(1, -1); glVertex2f(1, 1); glVertex2f(-1, 1); glEnd();
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
