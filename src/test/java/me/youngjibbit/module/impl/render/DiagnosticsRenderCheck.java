package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.diagnostics.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.ScaledResolution;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Actual production panel/font/shader/graph paint with explicitly synthetic diagnostic evidence. */
public final class DiagnosticsRenderCheck {
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    public static void run()throws Exception {
        Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
        Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"Product-Sans-Bold 18","Product-Sans-Regular 16","Product-Sans-Regular 12"});
        File out=new File("diagnostics-preview");out.mkdirs();
        Diagnostics hud=new Diagnostics();hud.getModeSetting("Effects Source").setValue("Local");Theme.setDefaultTheme(Theme.SynthWave);
        draw(hud,new File(out,"idle.png"));
        hud.displayMode.setValue("Simple");
        draw(hud,new File(out,"simple-empty.png"));
        hud.displayMode.setValue("Text");
        AtomicLong clock=new AtomicLong(1);RenderTimings timings=new RenderTimings(clock::get);Object profiler=new Object();timings.enable(profiler);timings.frame();
        for(int i=0;i<60;i++) {
            timings.start(profiler,"root");timings.start(profiler,"render");timings.start(profiler,"gameRenderer");
            timings.start(profiler,"terrain");clock.addAndGet(3_200_000);timings.end(profiler);
            timings.start(profiler,"entities");clock.addAndGet(1_800_000);timings.end(profiler);
            timings.start(profiler,"hud");long token=timings.surfaceStart();clock.addAndGet(700_000);timings.surfaceEnd("GlowESP",token);timings.end(profiler);
            timings.end(profiler);timings.end(profiler);timings.end(profiler);clock.addAndGet(i==40?50_000_000:10_000_000+(i%5)*500_000);timings.frame();
        }
        set(hud,"renderSnapshot",timings.sample());
        RotationManager r=new RotationManager();r.beginMotion(15,5);r.request("aura",20,54,12);r.finishMotion(54,12,true);set(hud,"rotation",r.diagnosticSnapshot());
        set(hud,"ping",42);set(hud,"network",Arrays.asList("Backtrack: Timed / tracking target / 120 ms / held 3","LagRange: waiting for window / 0 ms / held 0"));
        me.youngjibbit.core.network.PacketDelays delays=new me.youngjibbit.core.network.PacketDelays(()->20_000_000L);
        delays.request(new Object(),120,(packet,flow)->true);
        for(int i=0;i<3;i++)delays.defer(new net.minecraft.network.play.client.C03PacketPlayer(),me.youngjibbit.event.EventFlow.OUTBOUND,()->{});
        set(hud,"queue",delays.diagnosticSnapshot());
        Field history=Diagnostics.class.getDeclaredField("corrections");history.setAccessible(true);
        ((CorrectionHistory)history.get(hud)).record(System.nanoTime(),.42f,12,false,"Aura enabled; last local rotation: aura; queued in/out=3/0",
                "Aura / Range=3.0; Backtrack / Delay=120","A/B test Delay and Range separately. Cause is unconfirmed; compare server logs.");
        set(hud,"cachedPanels",null);draw(hud,new File(out,"compact.png"));
        hud.getBooleanSetting("Rotation Developer Details").setValue(true);hud.getBooleanSetting("Flag Developer Details").setValue(true);hud.getBooleanSetting("Network Values").setValue(true);
        set(hud,"values",Arrays.asList("Backtrack / Delay = 120","Backtrack / Acquire Range = 6.0","LagRange / Min MS = 300","LagRange / Max MS = 500"));
        set(hud,"cachedPanels",null);draw(hud,new File(out,"developer.png"));
        net.minecraft.client.Minecraft mc=SessionInfoRenderCheck.mc;
        net.minecraft.client.renderer.texture.TextureManager textures=new net.minecraft.client.renderer.texture.TextureManager(mc.getResourceManager());
        SessionInfoRenderCheck.set(mc,"renderEngine",textures);
        mc.fontRendererObj=new net.minecraft.client.gui.FontRenderer(mc.gameSettings,new net.minecraft.util.ResourceLocation("textures/font/ascii.png"),textures,false);
        mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
        DiagnosticsEditor editor=new DiagnosticsEditor(hud);mc.currentScreen=editor;
        ScaledResolution editorResolution=new ScaledResolution(mc);editor.setWorldAndResolution(mc,editorResolution.getScaledWidth(),editorResolution.getScaledHeight());
        draw(hud,new File(out,"developer-editor.png"));
        int oldWidth=mc.displayWidth,oldHeight=mc.displayHeight;
        net.minecraft.client.shader.Framebuffer oldFramebuffer=mc.getFramebuffer(),smallFramebuffer=new net.minecraft.client.shader.Framebuffer(854,480,true);
        mc.displayWidth=854;mc.displayHeight=480;SessionInfoRenderCheck.set(mc,"framebufferMc",smallFramebuffer);
        editorResolution=new ScaledResolution(mc);editor.setWorldAndResolution(mc,editorResolution.getScaledWidth(),editorResolution.getScaledHeight());
        draw(hud,new File(out,"developer-editor-small.png"));
        Field samples=Diagnostics.class.getDeclaredField("pingHistory");samples.setAccessible(true);
        @SuppressWarnings("unchecked") Deque<Integer> pingSamples=(Deque<Integer>) samples.get(hud);
        pingSamples.addAll(Arrays.asList(40,48,61,-1,54,42));
        // Change via the module setting while the already initialized editor stays open.
        hud.displayMode.setValue("Simple");
        draw(hud,new File(out,"simple-editor-small.png"));
        Field controls=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("buttonList");controls.setAccessible(true);
        @SuppressWarnings("unchecked") List<net.minecraft.client.gui.GuiButton> buttons=(List<net.minecraft.client.gui.GuiButton>) controls.get(editor);
        SessionInfoRenderCheck.require(buttons.stream().anyMatch(b -> b.id==2 && "Mode: Simple".equals(b.displayString)), "Stale mode button on rendered Simple editor");
        hud.displayMode.setValue("Text");
        smallFramebuffer.deleteFramebuffer();mc.displayWidth=oldWidth;mc.displayHeight=oldHeight;SessionInfoRenderCheck.set(mc,"framebufferMc",oldFramebuffer);
        editor.onGuiClosed();mc.currentScreen=null;
        // Long unbroken data, small screen and all settings produce finite, bounded geometry and bounded visible rows.
        set(hud,"network",Collections.singletonList(String.join("",Collections.nCopies(250,"long-source"))));set(hud,"cachedPanels",null);
        for(Diagnostics.Panel p:hud.panels()) {
            hud.layout(p,320,180);SessionInfoRenderCheck.require(p.widget.x>=4&&p.widget.y>=4&&p.widget.x+p.widget.width<=316.01&&p.widget.y+p.widget.height<=176.01,"Small-screen bounds");
            SessionInfoRenderCheck.require(p.visibleRows<=12,"Unbounded visible rows");
        }
        SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Diagnostics leaked an OpenGL error");hud.onDisable();
        System.out.println("PASS: diagnostics production panels, CPU meters/frame graph, empty/compact/developer states, two themes, bounded long data and layout.");
    }
    private static void draw(Diagnostics hud,File path)throws Exception {
        SessionInfoRenderCheck.backdrop();ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);
        List<Diagnostics.Panel> panels=hud.panels();
        GuiShaderBloom.beginBatch();for(Diagnostics.Panel p:panels){hud.layout(p,sr.getScaledWidth(),sr.getScaledHeight());hud.paint(p,1/60f);}GuiShaderBloom.flushBatch(2,3);
        if(SessionInfoRenderCheck.mc.currentScreen instanceof DiagnosticsEditor) {
            for(Diagnostics.Panel p:panels)SessionInfoRenderCheck.require(p.widget.y+p.widget.height<=sr.getScaledHeight()-DiagnosticsEditor.CONTROL_STRIP,"Editor chrome overlap");
            ((DiagnosticsEditor)SessionInfoRenderCheck.mc.currentScreen).drawScreen(-1,-1,0);
        }
        ImageIO.write(SessionInfoRenderCheck.capture(),"png",path);
        SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Panel paint error");
        Theme.setDefaultTheme(Theme.Neutron);
    }
}
