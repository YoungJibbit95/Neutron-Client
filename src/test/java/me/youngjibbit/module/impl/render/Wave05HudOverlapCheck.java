package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatDiagnosticTrace;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.render.*;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
import static me.youngjibbit.gui.Wave05UiInputTest.get;
import static me.youngjibbit.gui.Wave05UiInputTest.set;

/** Actual native Event2D owner; default HUD positions/modes; optional strict default-layout contract. */
public final class Wave05HudOverlapCheck {
    private static Minecraft mc;
    private static RecordingFont font;
    private static CombatDiagnostics diagnostics;
    private static SessionInfo session;
    private static WatermarkHUDModule watermark;
    private static ArrayListHUDModule array;
    private static GuiIngame owner;
    private static SessionStatistics statistics;
    private static final File OUT=new File(System.getProperty("wave05.output","wave05-hud-overlap"));
    private static final List<Map<String,Object>> scenes=new ArrayList<>();
    private static final boolean STRICT=Boolean.getBoolean("wave05.hud.strict");
    private static final List<String> violations=new ArrayList<>();
    private interface Draw {void run()throws Exception;}
    private static final class Label {
        final String text;final float x,y,width,height;
        Label(String text,float x,float y,float width,float height){this.text=text;this.x=x;this.y=y;this.width=width;this.height=height;}
        float[] rect(){return new float[]{x,y,width,height};}
    }
    private static final class RecordingFont extends FontRenderer {
        final List<Label> labels=new ArrayList<>();
        RecordingFont(){super(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);onResourceManagerReload(mc.getResourceManager());}
        @Override public int drawStringWithShadow(String text,float x,float y,int color){
            labels.add(new Label(text,x,y,getStringWidth(text)+1,FONT_HEIGHT+1));
            return super.drawStringWithShadow(text,x,y,color);
        }
    }
    private static final class Capture {
        final BufferedImage image;final List<Label> labels;
        Capture(BufferedImage image,List<Label> labels){this.image=image;this.labels=labels;}
    }
    public static void main(String[] args)throws Exception {
        OUT.mkdirs();File data=new File(OUT,"fixture");data.mkdirs();System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        // Reuse the independently corrected Pbuffer/resources/fonts initialization, not its scenarios.
        try(PbufferTestContext context=new PbufferTestContext()) {
            Class<?> fixture=Class.forName("me.youngjibbit.utils.render.Wave05UiRenderCheck");
            Method init=fixture.getDeclaredMethod("initialize",PbufferTestContext.class,File.class);init.setAccessible(true);init.invoke(null,context,data);
            Field minecraft=fixture.getDeclaredField("mc");minecraft.setAccessible(true);mc=(Minecraft)minecraft.get(null);
            Method glyph=fixture.getDeclaredMethod("glyphFixtureCheck");glyph.setAccessible(true);glyph.invoke(null);
            setupWorld();
            ClientSettings settings=new ClientSettings();settings.animations.setValue(false);settings.reducedMotion.setValue(true);
            settings.gradientMotion.setValue("Static");settings.surface.setValue("Standard");settings.blur.setValue(false);settings.glow.setValue(false);
            statistics=new SessionStatistics(()->0L);
            session=new SessionInfo(statistics);watermark=new WatermarkHUDModule();array=new ArrayListHUDModule();diagnostics=new CombatDiagnostics();
            ModuleManager manager=new ModuleManager(Arrays.asList(settings,new HUD(),session,watermark,array,diagnostics));
            set(Pulsar.INSTANCE,"mm",manager);UiEffects.invalidate();UiEffects.beginFrame();
            font=new RecordingFont();mc.fontRendererObj=font;owner=new GuiIngame(mc);mc.ingameGUI=owner;mc.currentScreen=null;
            try {
                for(Module module:Arrays.asList(session,watermark,array,diagnostics))module.setToggled(true);
                CombatDiagnosticTrace history=(CombatDiagnosticTrace)get(diagnostics,"history");
                for(int i=0;i<7;i++)history.append("F8_FIXTURE_"+i+" LOCAL observation, no server verdict");
                for(int[] size:new int[][]{{1280,720,2},{1100,640,2},{320,224,1}})observe(size);
                context.check();
                if(glGetError()!=GL_NO_ERROR)throw new AssertionError("HUD observation GL error");
                Map<String,Object> report=new LinkedHashMap<>();
                report.put("mode",STRICT?"STRICT_DEFAULT_LAYOUT":"BASELINE_OBSERVATION_ONLY");report.put("sourceRevision",System.getProperty("wave05.revision","UNSPECIFIED"));
                report.put("coverage","Actual GuiIngame.renderClientHud Event2D/bloom owner and actual HUD listeners; synthetic disconnected player/world; default positions/modes; static Standard effects without blur/glow for stable pixel attribution");
                report.put("notCovered","Native Display/live gameplay/server/scoreboard; arbitrary user HUD positions or saturated ArrayList; only default-position synthetic native scenes are asserted");
                report.put("scenes",scenes);report.put("violations",violations);
                try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(OUT,"hud-overlap-observations.json")),"UTF-8")) {
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report,writer);
                }
                if(STRICT&&!violations.isEmpty())throw new AssertionError(violations.toString());
            } finally {
                for(Module module:manager.getAllModules())if(module.isToggled())module.setToggled(false);
                mc.thePlayer=null;mc.theWorld=null;mc.currentScreen=null;
                Pulsar.INSTANCE.fontManager.close();ArrayListSurface.releaseResources();GuiShaderBloom.releaseResources();
            }
        }
        System.out.println(STRICT?"PASS default-position HUD native docking: stable diagnostic ink, no SessionInfo text overlap, 42px hotbar reserve; custom layouts/live Display NOT_RUN"
                :"HUD BASELINE OBSERVATION ONLY: inspect JSON and actual PNGs; no layout acceptance PASS claimed");
    }
    private static void setupWorld()throws Exception {
        WorldClient world=new WorldClient(null,new WorldSettings(0,WorldSettings.GameType.CREATIVE,false,false,WorldType.DEFAULT),0,EnumDifficulty.NORMAL,new net.minecraft.profiler.Profiler());
        net.optifine.player.PlayerConfigurations.setPlayerConfiguration(mc.getSession().getUsername(),new net.optifine.player.PlayerConfiguration());
        NetHandlerPlayClient profile=new NetHandlerPlayClient(null,null,null,mc.getSession().getProfile());
        mc.theWorld=world;mc.thePlayer=new IngameHudRenderCheck.LocalPlayer(mc,world,profile);
        set(mc,"renderViewEntity",mc.thePlayer);
    }
    private static void observe(int[] size)throws Exception {
        mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);
        UiEffects.invalidate();UiEffects.beginFrame();ScaledResolution sr=new ScaledResolution(mc);
        Event2D event=new Event2D(0,sr);String id=size[0]+"x"+size[1]+"-scale"+sr.getScaleFactor();
        Method renderOwner=GuiIngame.class.getDeclaredMethod("renderClientHud",float.class,ScaledResolution.class);renderOwner.setAccessible(true);
        // Warm actual layouts/lazy glyphs, then capture same source/settings with no toggles between passes.
        capture(()->renderOwner.invoke(owner,0f,sr));
        Capture empty=capture(()->{});
        Capture diagnosticOnly=capture(()->listener(diagnostics,"render",event));
        Capture watermarkOnly=capture(()->listener(watermark,"onRender2D",event));
        Capture arrayOnly=capture(()->listener(array,"onRender2D",event));
        Capture sessionOnly=capture(()->listener(session,"render",event));
        Capture combined=capture(()->renderOwner.invoke(owner,0f,sr));
        Capture combinedRepeat=capture(()->renderOwner.invoke(owner,0f,sr));
        Capture diagnosticRepeat=capture(()->listener(diagnostics,"render",event));
        save(id+"-diagnostics",diagnosticOnly.image);save(id+"-watermark",watermarkOnly.image);save(id+"-arraylist",arrayOnly.image);save(id+"-session",sessionOnly.image);save(id+"-combined",combined.image);
        SessionInfo.Frame sessionFrame=session.layout(statistics.snapshot(),mc.getSession().getUsername(),sr.getScaledWidth(),sr.getScaledHeight());
        ArrayListSurface.Shape shape=(ArrayListSurface.Shape)get(array,"frameShape");
        Map<String,Object> scene=new LinkedHashMap<>();scene.put("id",id);scene.put("scaledWidth",sr.getScaledWidth());scene.put("scaledHeight",sr.getScaledHeight());
        int aloneDrift=difference(diagnosticOnly.image,diagnosticRepeat.image),combinedDrift=difference(combined.image,combinedRepeat.image);
        scene.put("diagnosticRepeatChangedPixels",aloneDrift);scene.put("combinedRepeatChangedPixels",combinedDrift);
        scene.put("pixelAttributionStable",aloneDrift==0&&combinedDrift==0);
        scene.put("sessionPanel",new float[]{sessionFrame.x,sessionFrame.y,sessionFrame.width,sessionFrame.height});
        scene.put("arrayShape",shape==null?null:new float[]{shape.left,shape.top,shape.right-shape.left,shape.bottom-shape.top});
        scene.put("watermarkChangedPixelBoundsGui",pixelBounds(empty.image,watermarkOnly.image,sr.getScaleFactor()));
        List<Map<String,Object>> labels=new ArrayList<>();int totalChangedInk=0;
        for(Label line:diagnosticOnly.labels) {
            Map<String,Object> entry=new LinkedHashMap<>();entry.put("text",line.text);entry.put("actualDrawRect",line.rect());
            List<String> collisions=new ArrayList<>();
            for(SessionInfo.TextRun run:sessionFrame.text)if(!run.text.isEmpty()&&intersects(line.rect(),new float[]{sessionFrame.x+run.x,sessionFrame.y+run.y,run.width,run.font.getHeight(run.text)}))collisions.add("SessionInfo: "+run.text);
            entry.put("overlappingSessionText",collisions);
            int changed=changedInk(diagnosticOnly.image,combined.image,line,sr.getScaleFactor());entry.put("changedDiagnosticInkPixels",changed);totalChangedInk+=changed;labels.add(entry);
        }
        scene.put("diagnosticLabels",labels);scene.put("changedDiagnosticInkPixels",totalChangedInk);
        scene.put("interpretation",aloneDrift!=0||combinedDrift!=0?"Frame drift: inspect geometry and PNGs; do not attribute pixel differences solely to occlusion":totalChangedInk>0?"Stable diagnostic ink differs after actual combined HUD composition; inspect intersecting labels and PNGs":"No changed diagnostic ink observed in this scene");
        scene.put("positionsUnchanged",session.getNumberSetting("Position X").getValue()==50&&session.getNumberSetting("Position Y").getValue()==40&&array.getNumberSetting("Position X").getValue()==10&&array.getNumberSetting("Position Y").getValue()==20);
        validateDefaultScene(id,sr,scene,diagnosticOnly.labels,labels,aloneDrift,combinedDrift,totalChangedInk);
        scenes.add(scene);System.out.println(id+" observed changed diagnostic ink pixels="+totalChangedInk);
    }
    private static void validateDefaultScene(String id,ScaledResolution sr,Map<String,Object> scene,List<Label> actual,
            List<Map<String,Object>> measured,int aloneDrift,int combinedDrift,int changedInk)throws Exception {
        require(aloneDrift==0&&combinedDrift==0,id+": unstable comparison frames");
        require(changedInk==0,id+": other default HUDs changed diagnostic ink");
        require(Boolean.TRUE.equals(scene.get("positionsUnchanged")),id+": existing HUD position Values changed");
        require(actual.size()>=4,id+": four diagnostic header/status lines missing");
        if(actual.size()>=4) {
            require(actual.get(0).text.contains("CLIENT ONLY"),id+": client-only warning absent");
            require(actual.get(1).text.contains("S08")&&actual.get(1).text.contains("flag")&&actual.get(1).text.contains("server"),id+": S08/server warning absent");
        }
        int historyRows=0;
        for(Label label:actual) {
            if(label.text.startsWith("F8_FIXTURE_"))historyRows++;
            require(label.x>=0&&label.y>=0&&label.x+label.width<=sr.getScaledWidth()
                    &&label.y+label.height<=sr.getScaledHeight()-42,id+": diagnostic label outside screen/hotbar reserve: "+label.text);
        }
        for(Map<String,Object> label:measured)
            require(((List<?>)label.get("overlappingSessionText")).isEmpty(),id+": diagnostic label overlaps actual SessionInfo text: "+label.get("text"));
        int expectedRows=sr.getScaledHeight()==224?1:sr.getScaledHeight()==320?5:sr.getScaledHeight()==360?7:-1;
        require(historyRows==expectedRows,id+": expected "+expectedRows+" recent history rows, got "+historyRows);
        require(((CombatDiagnosticTrace)get(diagnostics,"history")).size()==7,id+": layout discarded recorded history");
        scene.put("displayedHistoryRows",historyRows);scene.put("expectedHistoryRows",expectedRows);
        scene.put("retainedHistoryRows",((CombatDiagnosticTrace)get(diagnostics,"history")).size());
    }
    private static void require(boolean condition,String message){if(!condition){violations.add(message);System.out.println("VIOLATION "+message);}}
    private static void listener(Object module,String field,Event2D event)throws Exception {
        GuiShaderBloom.beginBatch();try{((Listener<Event2D>)get(module,field)).accept(event);}finally{GuiShaderBloom.flushBatch(2,3);}
    }
    private static Capture capture(Draw draw)throws Exception {
        mc.getFramebuffer().bindFramebuffer(true);glViewport(0,0,mc.displayWidth,mc.displayHeight);font.labels.clear();
        try(RenderState state=new RenderState()) {
            state.screenSpace();GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
            glClearColor(.025f,.035f,.055f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);draw.run();
        }
        glFinish();ByteBuffer bytes=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);
        glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int p=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-1-y,(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255));}
        return new Capture(image,new ArrayList<>(font.labels));
    }
    private static int changedInk(BufferedImage alone,BufferedImage combined,Label label,int scale) {
        int changed=0;
        for(int y=Math.max(0,(int)(label.y*scale));y<Math.min(alone.getHeight(),(int)Math.ceil((label.y+label.height)*scale));y++)
            for(int x=Math.max(0,(int)(label.x*scale));x<Math.min(alone.getWidth(),(int)Math.ceil((label.x+label.width)*scale));x++) {
                int rgb=alone.getRGB(x,y);if(Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))>=100&&rgb!=combined.getRGB(x,y))changed++;
            }
        return changed;
    }
    private static float[] pixelBounds(BufferedImage empty,BufferedImage rendered,int scale) {
        int left=rendered.getWidth(),top=rendered.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<rendered.getHeight();y++)for(int x=0;x<rendered.getWidth();x++)if(empty.getRGB(x,y)!=rendered.getRGB(x,y)){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        return right<left?new float[]{0,0,0,0}:new float[]{left/(float)scale,top/(float)scale,(right-left+1)/(float)scale,(bottom-top+1)/(float)scale};
    }
    private static boolean intersects(float[] a,float[] b){return a[0]<b[0]+b[2]&&b[0]<a[0]+a[2]&&a[1]<b[1]+b[3]&&b[1]<a[1]+a[3];}
    private static int difference(BufferedImage a,BufferedImage b){int changed=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;return changed;}
    private static void save(String name,BufferedImage image)throws Exception{ImageIO.write(image,"png",new File(OUT,name+".png"));}
}
