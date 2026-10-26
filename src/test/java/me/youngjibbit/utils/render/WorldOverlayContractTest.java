package me.youngjibbit.utils.render;

import me.youngjibbit.event.render.Event3D;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Structural guard for the actual bootstrap and hand boundary, not a full-world render test. */
public class WorldOverlayContractTest {
    private String source(String path) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("src"))) root = root.getParent();
        return new String(Files.readAllBytes(root.resolve("src/main/java/" + path)), StandardCharsets.UTF_8);
    }
    @Test public void activeRendererIsCreatedBeforeItsOnlyReloadRegistration() throws Exception {
        String mc=source("net/minecraft/client/Minecraft.java");
        assertTrue(mc.contains("this.entityRenderer = new me.youngjibbit.utils.render.CustomEntityRenderer(this);"));
        assertEquals(1, mc.split("registerReloadListener\\(this.entityRenderer\\)", -1).length-1);
        assertFalse(source("me/youngjibbit/Pulsar.java").contains("mc.entityRenderer ="));
    }
    @Test public void hookPrecedesFinalHandAndKeepsShadowGuard() throws Exception {
        String renderer=source("net/minecraft/client/renderer/EntityRenderer.java");
        int border=renderer.indexOf("renderglobal.renderWorldBorder(entity, partialTicks)");
        int hook=renderer.indexOf("this.renderWorldOverlays(pass, partialTicks)");
        assertTrue(border < hook);
        assertTrue(renderer.indexOf("Shaders.endWater()") < hook);
        assertTrue(hook < renderer.indexOf("ShadersRender.renderHand1(this, partialTicks, pass)"));
        assertTrue(renderer.substring(border,hook).contains("if (!Shaders.isShadowPass)"));
        assertFalse(source("me/youngjibbit/utils/render/CustomEntityRenderer.java").contains("super.renderWorldPass"));
    }
    @Test public void nativeDispatchWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue(Boolean.getBoolean("pulsar.world.overlay.native"));
        for (Class<?> probe : new Class<?>[]{WorldOverlayNativeCheck.class,RendererReloadNativeCheck.class}) {
        Path log=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"world-overlay-",".log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Duser.home="+System.getProperty("user.home"),
                "-Dorg.lwjgl.librarypath="+System.getProperty("org.lwjgl.librarypath"),
                "-cp",System.getProperty("java.class.path"),probe.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!child.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){child.destroy();fail("Native probe timeout: "+log);}
        String output=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);
        assertEquals(output,0,child.exitValue());System.out.print(output);
        }
    }
    @Test public void eventCarriesEachActualEyeAndLegacyPartialTicks() {
        for(int pass=0;pass<3;pass++) {
            Event3D event=new Event3D(.37f,pass);
            assertEquals(pass,event.getPass()); assertEquals(.37f,event.getPartialTicks(),0);
        }
        assertEquals(2,new Event3D(.2f).getPass());
    }
}
