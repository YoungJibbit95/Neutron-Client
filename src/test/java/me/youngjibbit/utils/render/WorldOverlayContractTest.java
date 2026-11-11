package me.youngjibbit.utils.render;

import me.youngjibbit.event.render.Event3D;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.bus.EventManager;
import me.zero.alpine.bus.EventBus;
import me.youngjibbit.event.render.WorldOverlaySubscriptions;
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
    @Test public void shaderpackDispatchUsesFinalColorBeforeFirstPersonOverlay() throws Exception {
        String renderer=source("net/minecraft/client/renderer/EntityRenderer.java");
        int hand=renderer.indexOf("ShadersRender.renderHand1(this, partialTicks, pass)");
        int composite=renderer.indexOf("Shaders.renderCompositeFinal()",hand);
        int dispatch=renderer.indexOf("this.renderShaderpackWorldOverlays(pass, partialTicks)",composite);
        int clear=renderer.indexOf("GlStateManager.clear(256)",dispatch);
        int overlay=renderer.indexOf("ShadersRender.renderFPOverlay(this, partialTicks, pass)",clear);
        assertTrue(hand>=0&&hand<composite&&composite<dispatch&&dispatch<clear&&clear<overlay);
        int end=renderer.indexOf("Shaders.endRender()",dispatch);
        int withoutHand=renderer.indexOf("this.renderShaderpackWorldOverlays(pass, partialTicks)",end);
        assertTrue(end<withoutHand);
        assertTrue(renderer.substring(end,withoutHand).contains("if (!this.renderHand && !Shaders.isShadowPass)"));
        assertEquals(2,renderer.split("this\\.renderShaderpackWorldOverlays\\(pass, partialTicks\\)",-1).length-1);
        String custom=source("me/youngjibbit/utils/render/CustomEntityRenderer.java");
        int capture=custom.indexOf("shaderWorldOverlays.capture(");
        int immediate=custom.indexOf("Pulsar.BUS.post(");
        assertTrue(capture>=0&&capture<immediate);
        assertTrue(custom.substring(capture,immediate).contains("return;"));
        assertTrue(custom.contains("shaderWorldOverlays.render("));
        assertTrue(custom.contains("finally { releaseClientRenderResources(); }"));
    }
    @Test public void eachMainEyeRequiresItsOwnFinalPass() throws Exception {
        String shaders=source("net/optifine/shaders/Shaders.java");
        int entry=shaders.indexOf("public static void beginRenderPass(");
        int next=shaders.indexOf("public static void setViewport(",entry);
        String pass=shaders.substring(entry,next);
        int shadow=pass.indexOf("if (!isShadowPass)");
        int world=pass.indexOf("isRenderingWorld = true;");
        int reset=pass.indexOf("isCompositeRendered = false;");
        assertTrue(shadow>=0&&shadow<world&&world<reset);
        int end=shaders.indexOf("public static void endRender()");
        String finish=shaders.substring(end,shaders.indexOf("public static void beginSky()",end));
        int guard=finish.indexOf("if (!isCompositeRendered)");
        assertTrue(guard>=0&&guard<finish.indexOf("renderCompositeFinal();"));
    }
    @Test public void nativeDispatchWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue(Boolean.getBoolean("pulsar.world.overlay.native"));
        for (Class<?> probe : new Class<?>[]{WorldOverlayNativeCheck.class,RendererReloadNativeCheck.class,
                ShaderWorldOverlayNativeCheck.class,ShaderCompatibilityNativeCheck.class,DemiseScaffoldOverlayNativeCheck.class}) {
        Path log=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"world-overlay-",".log");
        String javaExecutable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin",javaExecutable).toString(),
                "-Duser.home="+System.getProperty("user.home"),
                "-Dorg.lwjgl.librarypath="+System.getProperty("org.lwjgl.librarypath"),
                "-cp",System.getProperty("java.class.path"),probe.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!child.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){child.destroy();fail("Native probe timeout: "+log);}
        String output=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);
        assertEquals(output,0,child.exitValue());System.out.print(output);
        }
    }
    @Test public void onlyRegisteredWorldListenersRequireTheRenderGuard() {
        WorldOverlaySubscriptions subscriptions =
                new WorldOverlaySubscriptions();
        EventBus bus = EventManager.builder()
                .setName("world-overlay-test").setListenerListFactory(subscriptions).build();
        int[] calls = {0};
        Listener<Event3D> first =
                new Listener<>(event -> calls[0]++);
        Listener<Event3D> second =
                new Listener<>(event -> calls[0]++);
        Listener<String> other = new Listener<>(event -> {});
        bus.subscribe(other);
        assertFalse(subscriptions.hasListeners());
        bus.subscribe(first);
        bus.subscribe(first);
        bus.subscribe(second);
        bus.post(new Event3D(.5f));
        assertEquals(2, calls[0]);
        bus.unsubscribe(first);
        bus.unsubscribe(first);
        assertTrue(subscriptions.hasListeners());
        bus.unsubscribe(second);
        assertFalse(subscriptions.hasListeners());
    }

    @Test public void eventCarriesEachActualEyeAndLegacyPartialTicks() {
        for(int pass=0;pass<3;pass++) {
            Event3D event=new Event3D(.37f,pass);
            assertEquals(pass,event.getPass()); assertEquals(.37f,event.getPartialTicks(),0);
        }
        assertEquals(2,new Event3D(.2f).getPass());
    }
}
