package me.youngjibbit.utils.render;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.optifine.shaders.Shaders;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import static org.lwjgl.opengl.GL11.*;

/**
 * Defers main-camera client geometry until the shaderpack's final color exists.
 * It draws directly into that color with a private depth copy. The borrowed color
 * texture is never owned or sampled by this bridge and is detached after dispatch.
 * All methods run on the render/context thread.
 */
public final class ShaderWorldOverlayBridge implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(ShaderWorldOverlayBridge.class);
    private static final String DEPTH_VERTEX = "#version 120\n"
            + "void main(){gl_Position=gl_Vertex;gl_TexCoord[0]=gl_MultiTexCoord0;}";
    private static final String DEPTH_FRAGMENT = "#version 120\n"
            + "uniform sampler2D sourceDepth;\n"
            + "void main(){gl_FragDepth=texture2D(sourceDepth,gl_TexCoord[0].xy).r;gl_FragColor=vec4(0.0);}";

    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
    private boolean pending, drawing, closeRequested;
    private int pass, frame, partialBits, displayWidth, displayHeight, sourceWidth, sourceHeight;
    private int sourceFramebuffer, sourceDepth;
    private long generation;
    private Object world;

    // Only these three GL objects and the optional resampling program are owned.
    private int aliasFramebuffer, copiedDepth, scaledDepth;
    private int resourceWidth, resourceHeight, resourceSourceWidth, resourceSourceHeight;
    private long resourceGeneration = -1;
    private ShaderUtil resample;
    private String lastFailure;

    public boolean capture(Minecraft mc, int eye, float partialTicks) {
        pending = false;
        if (drawing) return fail("recursive world-overlay capture");
        int width = Shaders.renderWidth, height = Shaders.renderHeight;
        int framebuffer = Shaders.getWorldFramebuffer(), depth = Shaders.getWorldDepthTexture();
        if (mc == null || mc.getFramebuffer() == null || width <= 0 || height <= 0
                || mc.displayWidth <= 0 || mc.displayHeight <= 0 || framebuffer <= 0 || depth <= 0) {
            return fail("main-camera shader depth or final color is unavailable");
        }
        projection.clear(); model.clear(); colorMask.clear();
        glGetFloat(GL_PROJECTION_MATRIX, projection);
        glGetFloat(GL_MODELVIEW_MATRIX, model);
        glGetBoolean(GL_COLOR_WRITEMASK, colorMask);
        pass = eye; partialBits = Float.floatToIntBits(partialTicks);
        frame = Shaders.getRenderFrameCounter(); generation = Shaders.getRenderResourceGeneration();
        world = mc.theWorld;
        displayWidth = mc.displayWidth; displayHeight = mc.displayHeight;
        sourceWidth = width; sourceHeight = height;
        sourceFramebuffer = framebuffer; sourceDepth = depth;
        pending = true;
        return true;
    }

    public void cancelPending() { pending = false; }

    /**
     * Returns whether this exact captured eye/frame dispatched. Callback failures
     * propagate after detachment and full state restoration, and cannot be replayed.
     */
    public boolean render(Minecraft mc, int eye, float partialTicks, Runnable draw) {
        if (!pending) return false;
        pending = false;
        if (mc == null || eye != pass || partialBits != Float.floatToIntBits(partialTicks)
                || frame != Shaders.getRenderFrameCounter() || generation != Shaders.getRenderResourceGeneration()
                || world != mc.theWorld || displayWidth != mc.displayWidth || displayHeight != mc.displayHeight
                || sourceWidth != Shaders.renderWidth || sourceHeight != Shaders.renderHeight
                || sourceFramebuffer != Shaders.getWorldFramebuffer() || sourceDepth != Shaders.getWorldDepthTexture()) {
            return fail("captured camera/depth expired before the final pass");
        }
        Framebuffer destination = mc.getFramebuffer();
        if (destination == null || destination.framebufferWidth != displayWidth
                || destination.framebufferHeight != displayHeight || destination.framebufferTexture <= 0
                || destination.framebufferTexture == sourceDepth || !glIsTexture(destination.framebufferTexture)
                || !glIsTexture(sourceDepth) || !framebufferExists(sourceFramebuffer)) {
            return fail("shader depth or final-color resource changed");
        }
        try (RenderState state = new RenderState()) {
            try {
                ensureResources();
                copyDepth();
                attach(destination.framebufferTexture);
                if (scaledDepth != 0) resampleDepth();
            } catch (RuntimeException failure) {
                detach();
                releaseOwned();
                return fail("cannot prepare private world-overlay depth: " + failure.getMessage());
            }
            drawing = true;
            lastFailure = null;
            try {
                prepareWorld();
                draw.run();
                return true;
            } finally {
                drawing = false;
                detach();
                if (closeRequested) releaseOwned();
            }
        }
    }

    private void ensureResources() {
        if (resourceGeneration != generation || resourceWidth != displayWidth || resourceHeight != displayHeight
                || resourceSourceWidth != sourceWidth || resourceSourceHeight != sourceHeight) {
            releaseOwned();
            resourceGeneration = generation;
            resourceWidth = displayWidth; resourceHeight = displayHeight;
            resourceSourceWidth = sourceWidth; resourceSourceHeight = sourceHeight;
        }
        if (aliasFramebuffer == 0) {
            aliasFramebuffer = OpenGlHelper.glGenFramebuffers();
            if (aliasFramebuffer <= 0) throw new IllegalStateException("framebuffer allocation failed");
            if (RenderDiagnostics.enabled) RenderDiagnostics.framebufferCreates++;
        }
        if (copiedDepth == 0) copiedDepth = createDepth(sourceWidth, sourceHeight);
        if (displayWidth != sourceWidth || displayHeight != sourceHeight) {
            if (scaledDepth == 0) scaledDepth = createDepth(displayWidth, displayHeight);
            if (resample == null) {
                try {
                    resample = new ShaderUtil(
                            new ByteArrayInputStream(DEPTH_FRAGMENT.getBytes(StandardCharsets.UTF_8)),
                            new ByteArrayInputStream(DEPTH_VERTEX.getBytes(StandardCharsets.UTF_8)));
                } catch (IOException exception) {
                    throw new IllegalStateException("depth program input", exception);
                }
            }
        }
    }

    private static int createDepth(int width, int height) {
        int texture = glGenTextures();
        if (texture <= 0) throw new IllegalStateException("depth texture allocation failed");
        boolean allocated = false;
        try {
            texture(texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glTexParameteri(GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL_LUMINANCE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT32, width, height, 0,
                    GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            allocated = true;
            return texture;
        } finally {
            if (!allocated) GlStateManager.deleteTexture(texture);
        }
    }

    private void copyDepth() {
        // A separate snapshot prevents client clears/depth writes from changing pack inputs.
        // This runs after hand1, so first-person hand occlusion is part of the copied depth.
        try (RenderState ignored = new RenderState()) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, sourceFramebuffer);
            requireComplete("source");
            texture(copiedDepth);
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, sourceWidth, sourceHeight);
            if (RenderDiagnostics.enabled) RenderDiagnostics.sceneCopies++;
        }
    }

    private void attach(int borrowedColor) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, aliasFramebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, borrowedColor, 0);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D, scaledDepth != 0 ? scaledDepth : copiedDepth, 0);
        glDrawBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
        glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
        requireComplete("overlay");
    }

    private void resampleDepth() {
        // The source and attached destination are different private textures.
        try (RenderState ignored = new RenderState()) {
            glViewport(0, 0, displayWidth, displayHeight);
            glDrawBuffer(GL_NONE); glReadBuffer(GL_NONE);
            glColorMask(false, false, false, false); GlStateManager.colorMask(false, false, false, false);
            GlStateManager.disableBlend(); glDisable(GL_BLEND);
            GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
            GlStateManager.disableCull(); glDisable(GL_CULL_FACE);
            GlStateManager.disableLighting(); glDisable(GL_LIGHTING);
            GlStateManager.disableFog(); glDisable(GL_FOG);
            glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST);
            GlStateManager.enableDepth(); glEnable(GL_DEPTH_TEST);
            GlStateManager.depthFunc(GL_ALWAYS); glDepthFunc(GL_ALWAYS);
            GlStateManager.depthMask(true); glDepthMask(true);
            texture(copiedDepth);
            resample.init();
            try {
                resample.setUniformi("sourceDepth", 0);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex2f(-1, -1);
                glTexCoord2f(1, 0); glVertex2f(1, -1);
                glTexCoord2f(1, 1); glVertex2f(1, 1);
                glTexCoord2f(0, 1); glVertex2f(-1, 1);
                glEnd();
                if (RenderDiagnostics.enabled) RenderDiagnostics.drawSubmissions++;
            } finally { resample.unload(); }
        }
    }

    private void prepareWorld() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, aliasFramebuffer);
        glDrawBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0); glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, displayWidth, displayHeight);
        glMatrixMode(GL_PROJECTION); glLoadMatrix(projection);
        glMatrixMode(GL_MODELVIEW); glLoadMatrix(model);
        GL20.glUseProgram(0);
        if (RenderDiagnostics.enabled) RenderDiagnostics.programBinds++;
        GlStateManager.enableDepth(); glEnable(GL_DEPTH_TEST);
        GlStateManager.depthFunc(GL_LEQUAL); glDepthFunc(GL_LEQUAL);
        GlStateManager.depthMask(true); glDepthMask(true);
        GlStateManager.enableCull(); glEnable(GL_CULL_FACE);
        GlStateManager.disableFog(); glDisable(GL_FOG);
        GlStateManager.disableLighting(); glDisable(GL_LIGHTING);
        GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
        GlStateManager.disableBlend(); glDisable(GL_BLEND);
        glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST);
        GlStateManager.shadeModel(GL_FLAT); glShadeModel(GL_FLAT);
        for (int unit = 3; unit >= 0; --unit) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager.disableTexture2D(); glDisable(GL_TEXTURE_2D);
        }
        texture(0); // Never sample the color/depth attachment while drawing into it.
        GlStateManager.color(1, 1, 1, 1); glColor4f(1, 1, 1, 1);
        // OptiFine renders each eye's gbuffer with all channels enabled. Its final
        // pass applies the stereo mask; repeat that restriction for direct output.
        boolean red = colorMask.get(0) != 0 && pass != 0;
        boolean green = colorMask.get(1) != 0 && pass != 1;
        boolean blue = colorMask.get(2) != 0 && pass != 1, alpha = colorMask.get(3) != 0;
        GlStateManager.colorMask(red, green, blue, alpha); glColorMask(red, green, blue, alpha);
    }

    private void detach() {
        if (aliasFramebuffer == 0 || !framebufferExists(aliasFramebuffer)) return;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, aliasFramebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, 0, 0);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D, 0, 0);
    }

    private void releaseOwned() {
        if (aliasFramebuffer != 0) {
            detach();
            OpenGlHelper.glDeleteFramebuffers(aliasFramebuffer);
            if (RenderDiagnostics.enabled) RenderDiagnostics.framebufferDeletes++;
            aliasFramebuffer = 0;
        }
        if (copiedDepth != 0) { GlStateManager.deleteTexture(copiedDepth); copiedDepth = 0; }
        if (scaledDepth != 0) { GlStateManager.deleteTexture(scaledDepth); scaledDepth = 0; }
        if (resample != null) { resample.close(); resample = null; }
        resourceGeneration = -1;
        resourceWidth = resourceHeight = resourceSourceWidth = resourceSourceHeight = 0;
        closeRequested = false;
    }

    @Override public void close() {
        pending = false;
        world = null;
        if (drawing) { closeRequested = true; return; }
        if (aliasFramebuffer == 0 && copiedDepth == 0 && scaledDepth == 0 && resample == null) return;
        try (RenderState ignored = new RenderState()) { releaseOwned(); }
    }

    String failure() { return lastFailure; }

    private boolean fail(String reason) {
        if (!reason.equals(lastFailure)) {
            LOGGER.warn("Shaderpack world overlay unavailable: {}. Compatibility is unverified for this state.", reason);
            lastFailure = reason;
        }
        return false;
    }
    private static void texture(int id) {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(id); glBindTexture(GL_TEXTURE_2D, id);
    }
    private static boolean framebufferExists(int id) {
        org.lwjgl.opengl.ContextCapabilities caps = GLContext.getCapabilities();
        return id > 0 && RenderState.framebufferExists(id,
                RenderState.framebufferApi(caps.OpenGL30, caps.GL_ARB_framebuffer_object, caps.GL_EXT_framebuffer_object));
    }
    private static void requireComplete(String owner) {
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(owner + " framebuffer status " + status);
        }
    }
}
