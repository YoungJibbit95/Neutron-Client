package me.youngjibbit.rendercheck;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.src.Config;
import net.optifine.shaders.Shaders;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL11.*;

/** Explicit adapter for an offscreen context; never simulates a successfully created native Display. */
public final class PbufferTestContext implements AutoCloseable {
    private final RenderCheckDiagnostics diagnostics=new RenderCheckDiagnostics();
    private Pbuffer buffer;
    public PbufferTestContext() throws Exception {
        boolean ready=false;
        try {
            require(!Display.isCreated(),"Each render check requires its own JVM without a native Display");
            buffer=new Pbuffer(1280,960,new PixelFormat().withDepthBits(24).withStencilBits(8),null,null);
            buffer.makeCurrent();ready=true;
        } finally {if(!ready)close();}
    }
    public void initialize(Minecraft mc,File data) throws Exception {
        require(buffer.isCurrent(),"Pbuffer must be current before OptiFine/GL initialization");
        mc.gameSettings=new GameSettings(mc,data);
        mc.gameSettings.guiScale=2;
        require(Config.getGameSettings()==mc.gameSettings,"OptiFine must use the fixture settings");
        mc.gameSettings.saveOfOptions();
        require(new File(data,"optionsof.txt").isFile(),"Fixture OptiFine options must have a writable disposable path");

        // Config.checkInitialized() gates both capability discovery and an HTTP version check on Display.isCreated().
        // Run its real capability discovery against the current Pbuffer. Desktop management and HTTP are outside coverage.
        Method caps=Config.class.getDeclaredMethod("checkOpenGlCaps");caps.setAccessible(true);caps.invoke(null);
        setConfig("initialized",true);
        setConfig("desktopModeChecked",true); // no native window exists to update, resize or set VSync on
        OpenGlHelper.initializeTextures(); // normal helper flags and Shaders.startup still execute
        diagnostics.check();
        require(!Display.isCreated() && buffer.isCurrent(),"Initialization must keep the offscreen render context");
        require(GLContext.getCapabilities().OpenGL20 && OpenGlHelper.shadersSupported,"Real shader support is required");
        require(OpenGlHelper.framebufferSupported && OpenGlHelper.isFramebufferEnabled(),"FBO fallback is not a valid render check");
        require(glGetString(GL_VERSION).equals(Config.openGlVersion),"OptiFine capability discovery did not run");
        require(glGetString(GL_VERSION).equals(Shaders.glVersionString),"Shaders.startup did not run");
        require(Config.isMinecraftThread(),"OptiFine render-thread initialization did not run");
        require(!Config.isFastRender() && !Config.isAntialiasing() && !Config.isShaders(),"Fixture covers default FBO rendering without Fast Render, MSAA or shader packs");
        checkFramebufferReadback();
        System.out.println("COVERAGE: Pbuffer/FBO GUI composition, production shaders and bundled font subset; synthetic backdrop/input/data.");
        System.out.println("NOT COVERED: native Display creation, fullscreen/resize events/VSync, HTTP version check, world rendering, shader packs or full client bootstrap.");
    }
    private static void setConfig(String name,Object value) throws Exception {
        Field field=Config.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
    }
    private static void checkFramebufferReadback() {
        Framebuffer probe=new Framebuffer(16,16,true);
        try {
            require(probe.framebufferObject>0 && glIsTexture(probe.framebufferTexture),"Framebuffer must allocate real GPU storage");
            probe.bindFramebuffer(true);
            require(OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)==OpenGlHelper.GL_FRAMEBUFFER_COMPLETE,"Framebuffer incomplete");
            glClearColor(.25f,.5f,.75f,1);glClear(GL_COLOR_BUFFER_BIT);
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
            require(Math.abs((pixel.get(0)&255)-64)<=1 && Math.abs((pixel.get(1)&255)-128)<=1
                    && Math.abs((pixel.get(2)&255)-191)<=1 && (pixel.get(3)&255)==255,"GPU framebuffer readback failed");
            require(glGetError()==GL_NO_ERROR,"GL error during fixture initialization/readback");
        } finally {probe.deleteFramebuffer();}
    }
    public void check() {diagnostics.check();}
    @Override public void close() {
        try {if(buffer!=null)buffer.destroy();}
        finally {diagnostics.close();}
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
