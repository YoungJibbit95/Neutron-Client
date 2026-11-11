package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import me.youngjibbit.rendercheck.PbufferTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import net.optifine.render.GlAlphaState;
import net.optifine.render.GlBlendState;
import net.optifine.shaders.Program;
import net.optifine.shaders.ProgramStage;
import net.optifine.shaders.Shaders;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import static org.lwjgl.opengl.GL11.*;

/** Real GL/OptiFine bridge fixtures, not an external-pack or full-world compatibility claim. */
public final class ShaderCompatibilityNativeCheck {
    private static final String VERTEX = "#version 120\nvoid main(){gl_Position=gl_Vertex;}";
    private static final String WORLD = "#version 120\nuniform ivec4 blendFunc;\n"
            + "void main(){gl_FragData[0]=vec4(float(blendFunc.x)/1024.0,0.,0.,1.);"
            + "gl_FragData[1]=vec4(1.,0.,0.,1.);}";
    private static final String CLIENT = "#version 120\nuniform vec4 clientColor;\n"
            + "void main(){gl_FragColor=clientColor;}";

    public static void main(String[] args) throws Exception {
        File root = new File("shader-compatibility-preview"); root.mkdirs();
        System.setProperty("user.home", new File(root, "home").getAbsolutePath());
        File data = Files.createTempDirectory(root.toPath(), "fixture-").toFile();
        try (PbufferTestContext context = new PbufferTestContext()) {
            Minecraft mc = new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("ShaderFixture",
                            "00000000000000000000000000000000", "0", "legacy"),
                            new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(128, 96, false, false),
                    new GameConfiguration.FolderInformation(data, new File(data, "packs"),
                            new File(data, "assets"), null),
                    new GameConfiguration.GameInformation(false, "Shader compatibility fixture"),
                    new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data);
            require(GLContext.getCapabilities().OpenGL30, "Fixture needs real split framebuffer/MRT support");
            RenderStateFramebufferCheck.run();
            checkPass(false);
            checkPass(true);
            checkShadowPass();
            context.check();
        }
        System.out.println("PASS: actual GL programs/uniforms, MRT attachment isolation, nested/exception scopes, "
                + "shaderpack deferred alpha/blend state, independent read/draw FBOs, viewport, standalone shader calls, "
                + "shadow program preservation and resource-generation invalidation.");
        System.out.println("NOT COVERED: external shaderpacks, full world, native Display, hardware FPS.");
    }

    private static ShaderUtil shader(String fragment) throws Exception {
        return new ShaderUtil(new ByteArrayInputStream(fragment.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(VERTEX.getBytes(StandardCharsets.UTF_8)));
    }

    private static void checkPass(boolean overrides) throws Exception {
        Framebuffer target = new Framebuffer(32, 32, true);
        Framebuffer source = new Framebuffer(16, 16, false);
        int extra = glGenTextures();
        int previousDfb = (Integer) field("dfb").get(null);
        boolean previousLoaded = Shaders.shaderPackLoaded;
        boolean previousWorld = Shaders.isRenderingWorld, previousDfbFlag = Shaders.isRenderingDfb;
        try (ShaderUtil world = shader(WORLD); ShaderUtil client = shader(CLIENT)) {
            target.bindFramebuffer(true);
            glBindTexture(GL_TEXTURE_2D, extra);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 32, 32, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, extra, 0);
            glDrawBuffer(GL30.GL_COLOR_ATTACHMENT1); glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            GlStateManager.disableDepth(); glDisable(GL_DEPTH_TEST);
            GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
            GlStateManager.disableBlend(); glDisable(GL_BLEND);
            GlStateManager.alphaFunc(GL_ALWAYS, 0);
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

            Program pack = new Program(1000, "fixture-gbuffers", ProgramStage.GBUFFERS, true);
            pack.setId(world.getProgramId());
            IntBuffer outputs = BufferUtils.createIntBuffer(2);
            outputs.put(GL30.GL_COLOR_ATTACHMENT0).put(GL30.GL_COLOR_ATTACHMENT1).flip();
            pack.setDrawBuffers(outputs);
            if (overrides) {
                pack.setAlphaState(new GlAlphaState(true, GL_GREATER, .2f));
                pack.setBlendState(new GlBlendState(true, GL_ONE, GL_ZERO));
            }
            Shaders.shaderPackLoaded = true;
            field("dfb").set(null, target.framebufferObject);
            Shaders.isRenderingWorld = Shaders.isRenderingDfb = true;
            Shaders.useProgram(pack);
            Shaders.uniform_blendFunc.setValue(11, 12, 13, 14);
            int[] expectedUniform = uniform(world.getProgramId(), "blendFunc");
            GlAlphaState logicalAlpha = new GlAlphaState(); GlStateManager.getAlphaState(logicalAlpha);
            GlBlendState logicalBlend = new GlBlendState(); GlStateManager.getBlendState(logicalBlend);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
            glViewport(3, 4, 20, 21);

            try (RenderState state = new RenderState()) {
                require(Shaders.isClientRenderActive(), "Client pass did not suspend the pack");
                require(!Shaders.isRenderingWorld && !Shaders.isRenderingDfb, "OF world callbacks still active");
                require(glGetInteger(GL20.GL_DRAW_BUFFER0) == GL30.GL_COLOR_ATTACHMENT0
                        && glGetInteger(GL20.GL_DRAW_BUFFER1) == GL_NONE, "Client pass can overwrite gbuffer metadata");
                GlStateManager.disableAlpha(); GlStateManager.disableBlend();
                require(!glIsEnabled(GL_ALPHA_TEST) && !glIsEnabled(GL_BLEND), "Pack overrides constrain client effects");
                client.init(); client.setUniformf("clientColor", 0, 1, 0, 1);
                GlStateManager.tryBlendFuncSeparate(GL_ZERO, GL_ONE, GL_ONE, GL_ZERO);
                Shaders.useProgram(Shaders.ProgramTextured);
                Shaders.setDrawBuffers(outputs);
                require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == client.getProgramId(), "Model helper replaced client program");
                require(glGetInteger(GL20.GL_DRAW_BUFFER1) == GL_NONE, "Model helper replaced client outputs");
                require(Shaders.uniform_blendFunc.getProgram() == 0, "Pack uniforms target the client namespace");
                try (RenderState nested = new RenderState()) {
                    GL20.glUseProgram(0); glViewport(0, 0, 2, 2);
                    GlStateManager.enableAlpha();
                }
                require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == client.getProgramId(), "Nested scope lost client program");
                require(Shaders.isClientRenderActive(), "Nested scope ended outer suspension");
                GlStateManager.disableBlend(); glDisable(GL_BLEND);
                quad();
                client.unload();
                state.bindTarget();
                require(glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == target.framebufferObject,
                        "Scene-copy source is not current drawing destination");
                viewport(3, 4, 20, 21);
            }
            restored(pack, outputs, target, source);
            require(java.util.Arrays.equals(expectedUniform, uniform(world.getProgramId(), "blendFunc")),
                    "Client setters changed or disabled pack uniforms");
            GlAlphaState afterAlpha = new GlAlphaState(); GlStateManager.getAlphaState(afterAlpha);
            GlBlendState afterBlend = new GlBlendState(); GlStateManager.getBlendState(afterBlend);
            require(logicalAlpha.toString().equals(afterAlpha.toString()), "Pending pack alpha state changed");
            require(logicalBlend.toString().equals(afterBlend.toString()), "Pending pack blend state changed");
            require(glIsEnabled(GL_ALPHA_TEST) == overrides && glIsEnabled(GL_BLEND) == overrides,
                    "Physical pack override state changed");
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.framebufferObject);
            glReadBuffer(GL30.GL_COLOR_ATTACHMENT1);
            java.nio.ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels(5, 6, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            require((pixel.get(0) & 255) == 255 && (pixel.get(1) & 255) == 0, "Client draw damaged metadata attachment");
            glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            glReadPixels(5, 6, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            require((pixel.get(0) & 255) == 0 && (pixel.get(1) & 255) == 255, "Client color did not render");
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
            try {
                try (RenderState state = new RenderState()) {
                    client.init();
                    throw new IllegalStateException("synthetic client callback");
                }
            } catch (IllegalStateException expected) {
                require(expected.getMessage().startsWith("synthetic"), "Unexpected callback failure");
            }
            restored(pack, outputs, target, source);

            // Legacy unguarded API call must restore its external pack caller on unload.
            client.init(); client.setUniformf("clientColor", 1, 1, 0, 1);
            GlStateManager.tryBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
            client.unload();
            restored(pack, outputs, target, source);
            require(glGetError() == GL_NO_ERROR, "OpenGL error in pack/client bridge fixture");

            // A reload during a client callback must not publish saved pack metadata from an old generation.
            try (RenderState state = new RenderState()) { Shaders.uninit(); }
            require(!Shaders.isClientRenderActive() && Shaders.activeProgram == Shaders.ProgramNone
                    && Shaders.activeProgramID == 0 && glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0,
                    "Reload restored stale pack state");
        } finally {
            Shaders.isShadowPass = false;
            Shaders.useProgram(Shaders.ProgramNone);
            Shaders.isRenderingWorld = previousWorld; Shaders.isRenderingDfb = previousDfbFlag;
            Shaders.shaderPackLoaded = previousLoaded;
            field("dfb").set(null, previousDfb);
            glDeleteTextures(extra); target.deleteFramebuffer(); source.deleteFramebuffer();
        }
        System.out.println("PASS shader bridge: pack overrides=" + overrides);
    }

    private static void checkShadowPass() throws Exception {
        int previousId = Shaders.ProgramShadow.getId();
        boolean previousLoaded = Shaders.shaderPackLoaded;
        try (ShaderUtil shadow = shader(WORLD); ShaderUtil client = shader(CLIENT)) {
            Shaders.shaderPackLoaded = true;
            Shaders.ProgramShadow.setId(shadow.getProgramId());
            Shaders.isShadowPass = true;
            Shaders.useProgram(Shaders.ProgramShadow);
            try (RenderState state = new RenderState()) {
                require(Shaders.isShadowPass, "Client scope changed shadow-camera identity");
                client.init(); client.setUniformf("clientColor", 1, 0, 1, 1);
                Shaders.useProgram(Shaders.ProgramTextured);
                require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == client.getProgramId(), "Shadow coercion replaced client program");
                client.unload();
            }
            require(Shaders.activeProgram == Shaders.ProgramShadow
                    && glGetInteger(GL20.GL_CURRENT_PROGRAM) == shadow.getProgramId(), "Shadow program not restored");
            require(glGetError() == GL_NO_ERROR, "Shadow bridge GL error");
        } finally {
            Shaders.isShadowPass = false;
            Shaders.useProgram(Shaders.ProgramNone);
            Shaders.ProgramShadow.setId(previousId); Shaders.shaderPackLoaded = previousLoaded;
        }
    }

    private static void restored(Program pack, IntBuffer outputs, Framebuffer draw, Framebuffer read) throws Exception {
        require(!Shaders.isClientRenderActive(), "Client suspension leaked");
        require(Shaders.activeProgram == pack && Shaders.activeProgramID == pack.getId()
                && glGetInteger(GL20.GL_CURRENT_PROGRAM) == pack.getId(), "Program caches disagree with GL");
        require(field("activeDrawBuffers").get(null) == outputs, "Drawbuffer cache not restored");
        require(glGetInteger(GL20.GL_DRAW_BUFFER0) == GL30.GL_COLOR_ATTACHMENT0
                && glGetInteger(GL20.GL_DRAW_BUFFER1) == GL30.GL_COLOR_ATTACHMENT1, "MRT outputs not restored");
        require(glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == draw.framebufferObject
                && glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == read.framebufferObject, "Read/draw targets not restored");
        require(Shaders.isRenderingWorld && Shaders.isRenderingDfb, "World phase flags not restored");
        viewport(3, 4, 20, 21);
        Shaders.useProgram(pack);
        require(glGetInteger(GL20.GL_CURRENT_PROGRAM) == pack.getId(), "Same-program cache can skip a needed rebind");
    }

    private static int[] uniform(int program, String name) {
        IntBuffer value = BufferUtils.createIntBuffer(16);
        GL20.glGetUniform(program, GL20.glGetUniformLocation(program, name), value);
        return new int[]{value.get(0), value.get(1), value.get(2), value.get(3)};
    }
    private static void viewport(int x, int y, int w, int h) {
        IntBuffer value = BufferUtils.createIntBuffer(16); glGetInteger(GL_VIEWPORT, value);
        require(value.get(0) == x && value.get(1) == y && value.get(2) == w && value.get(3) == h,
                "Viewport not restored");
    }
    private static Field field(String name) throws Exception {
        Field field = Shaders.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void quad() {
        glBegin(GL_QUADS); glVertex2f(-1, -1); glVertex2f(1, -1); glVertex2f(1, 1); glVertex2f(-1, 1); glEnd();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
