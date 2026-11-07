package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickGuiRenderCheck;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.gui.Wave05UiInputTest;
import me.youngjibbit.gui.Wave05UiInputTest.IosFixture;
import me.youngjibbit.gui.Wave05UiInputTest.Panel;
import me.youngjibbit.gui.Wave05UiInputTest.PanelDummy;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.font.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
import static me.youngjibbit.gui.Wave05UiInputTest.*;

/**
 * Run this identical source before and after production edits in separate JVMs/directories.
 * -Dwave05.captureOnly=true records baseline violations; it NEVER prints a verification PASS.
 * Default mode requires every contract. No Display, live world, account, or FPS claim.
 */
public final class Wave05UiRenderCheck {
    private static final boolean CAPTURE_ONLY=Boolean.getBoolean("wave05.captureOnly");
    private static final File OUT=new File(System.getProperty("wave05.output","wave05-ui"));
    private static final List<Map<String,Object>> evidence=new ArrayList<>();
    private static final List<String> violations=new ArrayList<>();
    private static Minecraft mc;
    private static ClientSettings settings;
    private static PanelDummy panelValue;
    interface Draw {void run()throws Exception;}

    public static void main(String[] args)throws Exception {
        OUT.mkdirs();File data=new File(OUT,"fixture");data.mkdirs();
        System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        try(PbufferTestContext context=new PbufferTestContext()) {
            initialize(context,data);
            glyphFixtureCheck();
            for(int guiScale:new int[]{1,2,3,4}) {
                resize(1280,960,guiScale);
                popupCase("scale-"+guiScale,1,0,66);
            }
            resize(320,224,1);popupCase("small-fit",.75f,-20,66);
            resize(960,640,2);
            for(long elapsed:new long[]{0,94,158,171})popupCase("timeline-"+elapsed,1,0,elapsed);
            moduleCaptureCase();panelCase();
            context.check();check(glGetError()==GL_NO_ERROR,"Final GL error");
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("mode",CAPTURE_ONLY?"BASELINE_CAPTURE_ONLY":"STRICT_VERIFY");
            report.put("sourceRevision",System.getProperty("wave05.revision","UNSPECIFIED"));
            report.put("gpu",glGetString(GL_VENDOR)+" / "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            report.put("coverage","Real iOS combo/module and Panel drawing/handlers; Pbuffer; synthetic settings; frozen animation timers; caller-applied fit/scroll matrices");
            report.put("notCovered","Native Display, full iOS root fit calculation, live account/world/server, FPS, shaderpack");
            report.put("evidence",evidence);report.put("violations",violations);
            try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(OUT,"ui-evidence.json")),"UTF-8")) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report,writer);
            }
            mc.getFramebuffer().deleteFramebuffer();
        }
        if(CAPTURE_ONLY)System.out.println("BASELINE CAPTURE ONLY: "+violations.size()+" observed contract violations; NOT a test PASS");
        else {if(!violations.isEmpty())throw new AssertionError(violations.toString());System.out.println("PASS Wave05 UI Pbuffer contracts; live Display/world NOT_RUN");}
    }
    private static void initialize(PbufferTestContext context,File data)throws Exception {
        mc=new Minecraft(new GameConfiguration(
                new GameConfiguration.UserInformation(new Session("Wave05Fixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960,640,false,false),
                new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"Wave05 UI Pbuffer"),new GameConfiguration.ServerInformation(null,0)));
        context.initialize(mc,data);
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
        resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
        set(mc,"framebufferMc",new Framebuffer(960,640,true));set(mc,"renderEngine",new TextureManager(resources));
        mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);
        mc.fontRendererObj.onResourceManagerReload(resources);
        Method fonts=ClickGuiRenderCheck.class.getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
        // minimalFonts already preloads Product-Sans-Regular 20 and icon1 23 (PanelUtils.icon2).
        // Add only keys absent from that base to avoid duplicate font atlas allocation.
        Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{
                "Product-Sans-Medium 20","Poppins-Medium 20","Poppins-Regular 19",
                "SFProText-Medium 22","SFProText-Medium 17"});
        Map<?,?> loadedFonts=(Map<?,?>)get(Pulsar.INSTANCE.fontManager,"fonts");
        for(String key:new String[]{"Product-Sans-Regular 20","Product-Sans-Medium 20","Poppins-Medium 20",
                "Poppins-Regular 19","SFProText-Medium 22","SFProText-Medium 17","icon1 23",
                "Poppins-Regular 18","Poppins-Regular 20","Poppins-Medium 18","Product-Sans-Bold 28","icon1 21","icon 21"}) {
            if(!loadedFonts.containsKey(key))throw new IllegalStateException("Fixture font not preloaded: "+key);
        }
        settings=new ClientSettings();panelValue=new PanelDummy();
        set(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(settings,panelValue)));set(Pulsar.INSTANCE,"iOS",shell());
        settings.surface.setValue("Standard");settings.blur.setValue(false);settings.glow.setValue(false);
        settings.ambientMotion.setValue(false);settings.animations.setValue(true);settings.reducedMotion.setValue(false);
        Theme.setDefaultTheme(Theme.SynthWave);UiEffects.timeSource(()->0L);UiEffects.invalidate();UiEffects.beginFrame();
    }
    private static void resize(int width,int height,int scale) {
        mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;
        mc.getFramebuffer().createBindFramebuffer(width,height);UiEffects.invalidate();UiEffects.beginFrame();
    }
    private static void glyphFixtureCheck()throws Exception {
        BufferedImage image=frame(()->Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 20").drawString("O",20,20,0xffffffff));
        save(image,"fixture-glyph-O");
        int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1,ink=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int pixel=image.getRGB(x,y);
            if((pixel>>16&255)>100&&(pixel>>8&255)>100&&(pixel&255)>100) {
                ink++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
            }
        }
        boolean outline=ink>10&&maxX-minX>=4&&maxY-minY>=6;
        if(outline) {
            int center=image.getRGB((minX+maxX)/2,(minY+maxY)/2);
            outline=(center>>16&255)<80&&(center>>8&255)<80&&(center&255)<80;
        }
        record("fixture-glyph-O-has-transparent-center",outline);
        // A broken fixture invalidates baseline pixels too; captureOnly never bypasses this gate.
        if(!outline)throw new IllegalStateException("Font fixture rendered an empty/solid glyph; native UI pixel evidence is invalid");
    }
    private static void popupCase(String id,float fit,float scroll,long elapsed)throws Exception {
        set(Pulsar.INSTANCE.getIOS(),"scrollOffset",scroll);
        IosFixture fixture=new IosFixture(elapsed);
        float bottom=90+fixture.revealHeight();
        Draw draw=()->{glPushMatrix();try{glScalef(fit,fit,1);glTranslatef(0,scroll,0);fixture.combo.drawScreen(-1,-1);}finally{glPopMatrix();}};
        BufferedImage actual=frame(draw);save(actual,id+"-actual");
        BufferedImage oracle=frame(()->{
            glPushMatrix();try {
                glScalef(fit,fit,1);glTranslatef(0,scroll,0);
                // Independent clipping oracle includes the whole header and only revealed popup.
                try(GuiClip clip=GuiClip.pushTransformed(new ClipRect(0,0,1000,bottom))) {fixture.combo.drawScreen(-1,-1);}
            } finally {glPopMatrix();}
        });save(oracle,id+"-clip-oracle");
        record(id+"-popup-total-diff-pixels",difference(actual,oracle));
        int leaked=optionInkOutsideReveal(actual,oracle,bottom,fit,scroll);
        record(id+"-popup-text-leak-pixels",leaked);
        check(leaked==0,id+": option text extends beyond reveal rectangle");
        // Invoke real container, not a synthetic hit function. Select the first option center below reveal.
        int hidden=-1;for(int i=0;i<4;i++)if(100+22*i>=bottom){hidden=i;break;}
        if(hidden>=0) {
            String original=fixture.value.combo.getOptions()[(hidden+1)%4];
            fixture.value.combo.setValue(original);fixture.value.changes=0;
            fixture.row.mouseClicked(190,(int)(100+22*hidden+scroll),0);
            record(id+"-hidden-option-callbacks",fixture.value.changes);
            check(original.equals(fixture.value.combo.getValue())&&fixture.value.changes==0,id+": invisible option activated");
        }
        if(bottom>100) {
            IosFixture visible=new IosFixture(elapsed);visible.value.combo.setValue("Bravo");visible.value.changes=0;
            visible.row.mouseClicked(190,(int)(100+scroll),0);visible.row.mouseReleased();
            check("Alpha".equals(visible.value.combo.getValue())&&visible.value.changes==1,id+": visible option did not dispatch exactly once");
        }
    }
    private static void moduleCaptureCase()throws Exception {
        set(Pulsar.INSTANCE.getIOS(),"scrollOffset",0f);
        IosFixture fixture=new IosFixture(1000);frame(()->fixture.row.drawScreen(-1,-1));
        fixture.row.mouseClicked(190,(int)fixture.slider.getY()+18,0);
        check((Boolean)get(fixture.slider,"dragging"),"Slider fixture did not capture");
        fixture.row.mouseClicked(30,20,1);
        boolean captured=(Boolean)get(fixture.slider,"dragging");record("slider-captured-after-close",captured);
        check(!captured,"Closing module retained slider capture");
        freeze(fixture.row,"openAnimation",Direction.BACKWARDS,66);
        double value=fixture.value.slider.getValue();fixture.value.changes=0;
        save(frame(()->fixture.row.drawScreen(240,130)),"module-collapsing-after-close");
        record("slider-close-draw-callbacks",fixture.value.changes);
        check(value==fixture.value.slider.getValue()&&fixture.value.changes==0,"Collapsing hidden slider mutated Value");
    }
    private static void panelCase()throws Exception {
        Panel panel=new Panel();ScaledResolution sr=new ScaledResolution(mc);
        panel.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());panel.layoutNow();
        ClickModule row=panel.first();
        panel.click((int)row.getX()+12,(int)row.getY()+12,1);freeze(row,"openAnimation",Direction.FORWARDS,0);panel.layoutNow();
        save(frame(panel::drawZeroFrame),"panel-zero-reveal-before-input");
        panelValue.changes=0;panel.key(Keyboard.KEY_TAB);panel.key(Keyboard.KEY_SPACE);panel.release();
        save(frame(panel::drawZeroFrame),"panel-zero-reveal-after-input");
        record("panel-hidden-checkbox-callbacks",panelValue.changes);
        check(!panelValue.check.getValue()&&panelValue.changes==0,"Panel keyboard activated invisible checkbox");
        panel.onGuiClosed();
    }
    private static BufferedImage frame(Draw draw)throws Exception {
        mc.getFramebuffer().bindFramebuffer(true);glViewport(0,0,mc.displayWidth,mc.displayHeight);
        int framebuffer=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try(RenderState guard=new RenderState()) {
            guard.screenSpace();
            // Match the real GUI frame precondition: screenSpace only establishes matrices/depth.
            // The legacy glyph renderer binds textures but assumes texturing is already enabled.
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
            GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
            glClearColor(.025f,.035f,.055f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glEnable(GL_SCISSOR_TEST);glScissor(0,0,mc.displayWidth,mc.displayHeight);
            IntBuffer before=BufferUtils.createIntBuffer(16);glGetInteger(GL_SCISSOR_BOX,before);
            draw.run();
            IntBuffer after=BufferUtils.createIntBuffer(16);glGetInteger(GL_SCISSOR_BOX,after);
            check(glIsEnabled(GL_SCISSOR_TEST),"Production draw leaked caller scissor enable");
            for(int i=0;i<4;i++)check(before.get(i)==after.get(i),"Production draw leaked caller scissor box "+i);
        }
        check(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==framebuffer,"Guarded frame leaked FBO");
        check(glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,"Guarded frame leaked program");
        check(glGetError()==GL_NO_ERROR,"Frame GL error");glFinish();
        ByteBuffer bytes=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);
        glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++) {
            int p=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-1-y,(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255));
        }
        return image;
    }
    private static int difference(BufferedImage first,BufferedImage second) {
        int count=0;for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getRGB(x,y)!=second.getRGB(x,y))count++;return count;
    }
    private static int optionInkOutsideReveal(BufferedImage actual,BufferedImage clipped,float bottom,float fit,float scroll) {
        float scale=fit*new ScaledResolution(mc).getScaleFactor();
        // The rounded shader intentionally draws a 1-GUI-unit antialias fringe. Count bright option
        // ink in the popup interior, beyond that fringe, rather than rejecting decoration/shadows.
        int left=Math.max(0,(int)Math.ceil(171*scale)),right=Math.min(actual.getWidth(),(int)Math.floor(235*scale));
        int top=Math.max(0,(int)Math.ceil((bottom+scroll+1.5f)*scale));
        int end=Math.min(actual.getHeight(),(int)Math.ceil((190+scroll)*scale));
        int count=0;
        for(int y=top;y<end;y++)for(int x=left;x<right;x++) {
            int rgb=actual.getRGB(x,y);int light=Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));
            if(light>=100&&rgb!=clipped.getRGB(x,y))count++;
        }
        return count;
    }
    private static void save(BufferedImage image,String name)throws Exception {
        ImageIO.write(image,"png",new File(OUT,name+".png"));
        Map<String,Object> item=new LinkedHashMap<>();item.put("image",name+".png");item.put("width",image.getWidth());item.put("height",image.getHeight());
        item.put("guiScale",new ScaledResolution(mc).getScaleFactor());evidence.add(item);
    }
    private static void record(String name,Object value){Map<String,Object> item=new LinkedHashMap<>();item.put("contract",name);item.put("observed",value);evidence.add(item);System.out.println(name+"="+value);}
    private static void check(boolean condition,String label){if(!condition){violations.add(label);System.out.println("VIOLATION "+label);}}
}
