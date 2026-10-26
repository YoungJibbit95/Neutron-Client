package me.youngjibbit.clickgui;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ArrayListHUDModule;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Isolated pbuffer checks of production Arraylist layout/composition, with a structured synthetic scene. */
public final class ArrayListRenderCheck {
    static Minecraft mc;
    static ArrayListHUDModule hud;
    static final File OUT=new File("arraylist-preview");
    static final List<BufferedImage> animation=new ArrayList<>();
    @ModuleInfo(id="array_test_a",name="InventoryManager",category=Category.Combat)
    public static class A extends Module { String label="InventoryManager"; @Override public String getName(){return label;} }
    @ModuleInfo(id="array_test_b",name="HitDelayFix",category=Category.Combat)
    public static class B extends Module { }
    @ModuleInfo(id="array_test_c",name="Sprint",category=Category.Movement)
    public static class C extends Module { }
    static A a; static B b; static C c;

    public static void main(String[] args) throws Exception {
        OUT.mkdirs(); System.setProperty("user.home",new File(OUT,"home").getAbsolutePath());
        File data=java.nio.file.Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        try (PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                    new Session("ArraylistPreview","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(960,640,false,false),
                    new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"Arraylist regression check"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources); set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
            set(mc,"framebufferMc",new Framebuffer(mc.displayWidth,mc.displayHeight,true));
            Pulsar.INSTANCE.fontManager=ClickGuiRenderCheck.minimalFonts("DmSansRegular 20", "Product-Sans-Medium 20",
                    "Poppins-Medium 20", "Inter-Regular 20", "SFProText-Regular 20", "KhmerOureang 20");
            set(mc,"renderEngine",new TextureManager(resources));
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            ModuleManager manager=new ModuleManager(); set(Pulsar.INSTANCE,"mm",manager);
            for (Module module:new ArrayList<>(manager.getAllModules())) manager.unregisterModule(module);
            a=new A(); b=new B(); c=new C(); hud=new ArrayListHUDModule();hud.getModeSetting("Effects Source").setValue("Local");
            manager.registerModule(a); manager.registerModule(b); manager.registerModule(c); manager.registerModule(hud);
            hud.getModeSetting("Font").setValue("Poppins"); hud.getModeSetting("Font Strength").setValue("Regular");
            Theme.setDefaultTheme(Theme.SynthWave);
            checkEffects(); checkModuleLayout(); checkBatchAndState(); checkPaddingAndAnchors(); checkResizeAndModes();
            require(glGetError()==GL_NO_ERROR,"OpenGL errors");
            context.check();
        }
        System.out.println("PASS [Pbuffer/FBO only]: unified contour; zero interior glow delta; scene blur confined to mask; animation/sorting snapshots; glyph metrics; modes/filters; batch ordering; GL state; FBO reuse/resize/release");
    }

    static ArrayListSurface.Shape shape() {
        return new ArrayListSurface.Shape(24,new float[]{30,54,78},new float[]{54,78,102},new float[]{190,145,100},3);
    }
    static void checkEffects() throws Exception {
        ArrayListSurface surface=new ArrayListSurface();
        backdrop(); BufferedImage scene=capture();
        backdrop(); surface.render(shape(),Theme.SynthWave,true,false,false,0.75f,()->{}); BufferedImage plain=capture();
        Object[] pool=((Framebuffer[])get(surface,"buffers")).clone();
        backdrop(); surface.render(shape(),Theme.SynthWave,true,true,false,0.75f,()->{}); BufferedImage blurred=capture();
        Framebuffer[] buffers=(Framebuffer[])get(surface,"buffers"); int texture=buffers[0].framebufferTexture;
        backdrop(); surface.render(shape(),Theme.SynthWave,true,true,true,0.75f,()->{}); BufferedImage glowing=capture();
        require(buffers[0].framebufferTexture==texture,"Stable framebuffer reuse");
        int scale=new ScaledResolution(mc).getScaleFactor(), changedInside=0, changedOutside=0;
        for (int y=0;y<mc.displayHeight;y++) for (int x=0;x<mc.displayWidth;x++) {
            float gx=(x+0.5f)/scale, gy=(y+0.5f)/scale;
            float right=gy<54?190:gy<78?145:100;
            boolean inside=gx>28 && gx<right-4 && gy>34 && gy<98 && Math.abs(gy-54)>4 && Math.abs(gy-78)>4;
            boolean far=gx<20 || gy<26 || gy>106 || gx>194 || (gy>58 && gx>149) || (gy>82 && gx>104);
            if (inside) {
                require(blurred.getRGB(x,y)==glowing.getRGB(x,y),"Glow leaked through translucent interior at "+x+","+y);
                if (plain.getRGB(x,y)!=blurred.getRGB(x,y)) changedInside++;
            }
            if (far) require(plain.getRGB(x,y)==blurred.getRGB(x,y),"Blur changed scene outside silhouette");
            if (far && glowing.getRGB(x,y)!=blurred.getRGB(x,y)) changedOutside++;
        }
        require(changedInside>100 && changedOutside>100,"Effects must visibly affect their intended regions");
        // Transparency cannot conceal the glow-mask test: repeat with no background/outline at all.
        backdrop(); surface.render(shape(),Theme.SynthWave,false,false,false,0.75f,()->{}); BufferedImage transparentOff=capture();
        backdrop(); surface.render(shape(),Theme.SynthWave,false,false,true,0.75f,()->{}); BufferedImage transparentOn=capture();
        for (int y=35*scale;y<98*scale;y++) for (int x=29*scale;x<95*scale;x++) {
            require(transparentOff.getRGB(x,y)==transparentOn.getRGB(x,y),"Interior exclusion without fill");
        }
        sheet("02-glow-blur.png",new String[]{"Scene","Fill: blur off, glow off","Blur on","Blur + exterior glow","No fill, glow off","No fill, exterior glow"},
                scene,plain,blurred,glowing,transparentOff,transparentOn);
        surface.close(); require(((Framebuffer[])get(surface,"buffers"))[0]==null,"Surface retained shared mask reference");require(glIsTexture(texture),"Shared mask deleted by an individual owner");ArrayListSurface.releaseResources();require(!glIsTexture(texture),"Shared effects survived global resource release");
    }

    static void checkModuleLayout() throws Exception {
        a.setToggled(true); render(); finishAnimations(); render(); BufferedImage single=capture();
        b.setToggled(true); c.setToggled(true); render();
        progress(65); render(); BufferedImage entering=capture();
        finishAnimations(); render(); BufferedImage multiple=capture();
        for (int i=0;i<20;i++) { render(); Thread.sleep(10); }
        multiple=capture();
        // A changed label changes the width sort, while per-module Y positions animate.
        a.label="Fly"; render(); BufferedImage sorting=capture();
        for (int i=0;i<20;i++) { render(); Thread.sleep(10); }
        BufferedImage sorted=capture();
        b.setToggled(false); render(); progress(80); render(); BufferedImage exiting=capture();
        for (int i=0;i<25;i++) { render(); Thread.sleep(10); }
        BufferedImage removed=capture();
        sheet("01-shape-animation.png",new String[]{"One row","Entering","Different widths","Reordering","New order","Leaving","Removed"},single,entering,multiple,sorting,sorted,exiting,removed);
        require(((Map<?,?>)get(hud,"visibilityAnimations")).size()==2,"Hidden row state removed");
        // The stored advance/extent must exactly match the same per-glyph renderer calls.
        List<?> rows=rows();
        for (Object row:rows) {
            Object metrics=get(row,"metrics"); String[] glyphs=(String[])get(metrics,"glyphs"); float[] positions=(float[])get(metrics,"positions");
            me.youngjibbit.utils.font.TTFFontRenderer renderer=(me.youngjibbit.utils.font.TTFFontRenderer)get(metrics,"renderer");
            float x=0,extent=0;
            for (int i=0;i<glyphs.length;i++) { require(Math.abs(positions[i]-x)<0.0001,"Glyph advance mismatch"); float w=renderer.getWidth(glyphs[i]); extent=Math.max(extent,x+w); x+=w-1.2f; }
            float scale=((Number)get(metrics,"scale")).floatValue();
            require(Math.abs(((Number)get(metrics,"width")).floatValue()-extent*scale)<0.0001,"Measured text extent mismatch");
        }
        hud.getBooleanSetting("Show Combat").setValue(false); render(); require(rows().size()==1,"Category filter");
        hud.getBooleanSetting("Show Combat").setValue(true); render(); finishAnimations();
    }

    static void checkBatchAndState() throws Exception {
        backdrop(); GuiShaderBloom.beginBatch();
        GuiShaderBloom.queueRoundedRect(130,35,25,20,4,0xFFFF00FF);
        invokeRender();
        Runnable frozenComposite=(Runnable)((List<?>)field(GuiShaderBloom.class,"afterBatch").get(null)).get(0);
        GuiShaderBloom.beginBatch(); GuiShaderBloom.queueRoundedRect(180,35,25,20,4,0xFF00FFFF); GuiShaderBloom.flushBatch(2,2);
        require(GuiShaderBloom.isBatching(),"Arraylist consumed parent batch");
        GuiShaderBloom.flushBatch(2,2); BufferedImage mixed=capture();
        // Equivalent order: both ordinary glow sources first, Arraylist after them.
        backdrop(); GuiShaderBloom.beginBatch(); GuiShaderBloom.queueRoundedRect(130,35,25,20,4,0xFFFF00FF);
        GuiShaderBloom.queueRoundedRect(180,35,25,20,4,0xFF00FFFF); GuiShaderBloom.flushBatch(2,2); frozenComposite.run();
        BufferedImage reference=capture(); require(equal(mixed,reference),"Deferred composition lost/reordered bloom");
        final int[] callbacks={0}; GuiShaderBloom.beginBatch(); GuiShaderBloom.afterBatch(()->callbacks[0]++); GuiShaderBloom.flushBatch(1,1);
        require(callbacks[0]==1,"Empty bloom batch lost compositor");
        ArrayListSurface surface=new ArrayListSurface(); backdrop();
        GlStateManager.enableDepth(); glEnable(GL_DEPTH_TEST); GlStateManager.enableAlpha(); glEnable(GL_ALPHA_TEST);
        glEnable(GL_STENCIL_TEST); glStencilFunc(GL_ALWAYS,3,255); glStencilOp(GL_KEEP,GL_KEEP,GL_KEEP);
        glEnable(GL_SCISSOR_TEST); glScissor(3,5,600,400);
        glViewport(4,6,700,500); GL13.glActiveTexture(GL13.GL_TEXTURE1); GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        glMatrixMode(GL_TEXTURE); glColorMask(true,false,true,false); GlStateManager.colorMask(true,false,true,false);
        surface.render(shape(),Theme.Neon,true,true,true,0.75f,()->{});
        IntBuffer box=BufferUtils.createIntBuffer(16); glGetInteger(GL_VIEWPORT,box);
        require(box.get(0)==4 && box.get(3)==500,"Viewport restoration");
        require(glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_ALPHA_TEST)&&glIsEnabled(GL_STENCIL_TEST)&&glIsEnabled(GL_SCISSOR_TEST),"GL enable restoration");
        require(glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE1 && glGetInteger(GL_MATRIX_MODE)==GL_TEXTURE,"GL texture/matrix restoration");
        ByteBuffer mask=BufferUtils.createByteBuffer(16); glGetBoolean(GL_COLOR_WRITEMASK,mask); require(mask.get(0)!=0&&mask.get(1)==0&&mask.get(2)!=0&&mask.get(3)==0,"Color mask restoration");
        surface.close(); backdrop();
        sheet("03-theme-scale-batch.png",new String[]{"Arraylist + nested ordinary bloom"},mixed);
    }

    static void checkPaddingAndAnchors()throws Exception {
        a.setToggled(true);b.setToggled(true);c.setToggled(true);hud.getModeSetting("Mode").setValue("Glass");hud.getNumberSetting("Scale").setValue(1.0);
        hud.getBooleanSetting("Custom Container Padding").setValue(true);hud.getNumberSetting("Position X").setValue(25.0);hud.getNumberSetting("Position Y").setValue(30.0);
        List<BufferedImage> pictures=new ArrayList<>();List<String> captions=new ArrayList<>();
        for(String font:new String[]{"DmSans","Product-Sans","Poppins","Minecraft","Inter","SFPro","KhmerOureang"})for(boolean right:new boolean[]{false,true})for(int extreme:new int[]{0,1}){
            hud.getModeSetting("Font").setValue(font);hud.getBooleanSetting("Right Anchored").setValue(right);
            int pt=extreme==0?0:24,pb=extreme==0?24:0,pl=extreme==0?0:24,pr=extreme==0?24:0,gap=extreme==0?0:20;
            hud.getNumberSetting("Container Padding Top").setValue((double)pt);hud.getNumberSetting("Container Padding Bottom").setValue((double)pb);hud.getNumberSetting("Container Padding Left").setValue((double)pl);hud.getNumberSetting("Container Padding Right").setValue((double)pr);hud.getNumberSetting("Row Spacing").setValue((double)gap);
            for(int i=0;i<10;i++){render();Thread.sleep(25);}render();ArrayListSurface.Shape shape=(ArrayListSurface.Shape)get(hud,"frameShape");
            Method build=ArrayListHUDModule.class.getDeclaredMethod("buildRows",float.class,float.class);build.setAccessible(true);
            List<?> current=(List<?>)build.invoke(hud,(float)get(hud,"anchorX"),(float)get(hud,"anchorY"));
            current.sort(java.util.Comparator.comparingDouble(row->{try{return (float)get(row,"textY");}catch(Exception e){throw new RuntimeException(e);}}));
            for(int i=0;i<current.size();i++){
                Object row=current.get(i),metrics=get(row,"metrics");float tx=(float)get(row,"textX"),rx=(float)get(row,"rectX"),rw=(float)get(row,"rectW"),tw=(float)get(metrics,"width");
                require(Math.abs(rw-tw-pl-pr)<.1,"Container horizontal padding does not drive width");
                require(Math.abs((right?rx+rw-tx-tw:tx-rx)-(right?pr:pl))<.1,"Text offset does not match selected anchor/padding");
                if(i==0)require(Math.abs((float)get(row,"textY")-shape.top-pt)<.7,"Container top padding");
                if(i==current.size()-1)require(Math.abs(shape.bottom-(float)get(row,"textY")-(float)get(metrics,"height")-pb)<.7,"Container bottom padding");
                if(i>0){Object before=current.get(i-1);require(Math.abs((float)get(row,"textY")-(float)get(before,"textY")-(float)get(get(before,"metrics"),"height")-gap)<.7,"Row gap is not independent of outer padding");}
            }
            require(hud.getNumberSetting("Position X").getValue()==25&&hud.getNumberSetting("Position Y").getValue()==30,"Padding/font/anchor changed persisted coordinates");
            float side=right?shape.right:shape.left;require(Math.abs(side-(right?new ScaledResolution(mc).getScaledWidth()-25:25))<.1,"Anchored contour edge moved");
            if(font.equals("Poppins")){BufferedImage shot=capture();if(right){int crop=Math.max(0,(int)(shape.left*new ScaledResolution(mc).getScaleFactor())-30);shot=shot.getSubimage(crop,0,shot.getWidth()-crop,shot.getHeight());}pictures.add(shot);captions.add((right?"Right":"Left")+"; top/bottom "+pt+"/"+pb+"; left/right "+pl+"/"+pr+"; row gap "+gap);}
        }
        sheet("05-container-padding.png",captions.toArray(new String[0]),pictures.toArray(new BufferedImage[0]));
        hud.getBooleanSetting("Custom Container Padding").setValue(false);hud.getBooleanSetting("Right Anchored").setValue(false);hud.getNumberSetting("Position X").reset();hud.getNumberSetting("Position Y").reset();
        System.out.println("PASS: asymmetric min/max container padding, independent row spacing, seven fonts, both stepped anchors and stable persisted position");
    }

    static void checkResizeAndModes() throws Exception {
        List<BufferedImage> pictures=new ArrayList<>(); List<String> captions=new ArrayList<>();
        for (String mode:new String[]{"Glass","Compact","BasicV2","Basic","Rounded"}) {
            hud.getModeSetting("Mode").setValue(mode); render(); require(!rows().isEmpty(),"Mode ID lost: "+mode);
        }
        for (boolean bg:new boolean[]{true,false}) for (boolean glow:new boolean[]{true,false}) {
            hud.getBooleanSetting("Background").setValue(bg); hud.getBooleanSetting("Glow").setValue(glow);
            render(); pictures.add(capture()); captions.add("Background "+bg+", glow "+glow);
        }
        hud.getBooleanSetting("Background").setValue(true); hud.getBooleanSetting("Glow").setValue(true);
        for (String font:new String[]{"DmSans","Product-Sans","Poppins","Minecraft","Inter","SFPro","KhmerOureang"}) {
            hud.getModeSetting("Font").setValue(font); render(); require(!rows().isEmpty(),"Font selection");
        }
        hud.getModeSetting("Font").setValue("Poppins");
        for (int scale:new int[]{1,2,3}) {
            Framebuffer previous=((Framebuffer[])get(get(hud,"surface"),"buffers"))[0];
            boolean resized=mc.displayWidth!=1101 || mc.displayHeight!=733;
            mc.displayWidth=1101; mc.displayHeight=733; mc.gameSettings.guiScale=scale;
            mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);
            hud.getNumberSetting("Scale").setValue(scale==1?0.75:1.35); Theme.setDefaultTheme(Theme.Neon);
            render(); pictures.add(capture()); captions.add("Neon; GUI scale "+scale+"; odd resolution");
            Framebuffer maskBuffer=((Framebuffer[])get(get(hud,"surface"),"buffers"))[0];
            require(!resized || previous.framebufferTexture==-1,"Old resize resources not deleted");
            maskBuffer.bindFramebuffer(false);
            ByteBuffer alpha=BufferUtils.createByteBuffer(4);
            glReadPixels(mc.displayWidth-1,mc.displayHeight-1,1,1,GL_RGBA,GL_UNSIGNED_BYTE,alpha);
            require(alpha.get(3)==0,"Glow mask must clear to transparent after GUI-scale/resize");
            mc.getFramebuffer().bindFramebuffer(true);
            Framebuffer[] pool=(Framebuffer[])get(get(hud,"surface"),"buffers"); int id=pool[0].framebufferTexture;
            render(); require(id==pool[0].framebufferTexture,"Resize pool not reused");
        }
        sheet("04-switches-resize.png",captions.toArray(new String[0]),pictures.toArray(new BufferedImage[0]));
        hud.getModeSetting("Mode").setValue("None"); backdrop(); BufferedImage none=capture(); invokeRender();
        require(equal(none,capture()),"None mode drew content");
        require(((Framebuffer[])get(get(hud,"surface"),"buffers"))[0]==null,"None mode leaked FBOs");
        hud.getModeSetting("Mode").setValue("Glass"); a.setToggled(false); b.setToggled(false); c.setToggled(false); render();
        for(int i=0;i<25;i++){render();Thread.sleep(10);} require(rows().isEmpty(),"Empty list");
    }

    static void render() throws Exception { backdrop(); GuiShaderBloom.beginBatch(); invokeRender(); GuiShaderBloom.flushBatch(2,3); glFinish(); }
    static void invokeRender() throws Exception { Method method=ArrayListHUDModule.class.getDeclaredMethod("renderArrayList");method.setAccessible(true);method.invoke(hud); }
    static List<?> rows() throws Exception { Method method=ArrayListHUDModule.class.getDeclaredMethod("buildRows",float.class,float.class);method.setAccessible(true);return (List<?>)method.invoke(hud,10f,20f); }
    static void progress(int time) throws Exception { for(Object o:((Map<?,?>)get(hud,"visibilityAnimations")).values()) { DecelerateAnimation animation=(DecelerateAnimation)o; if(time>=210 || !animation.isDone()) animation.timerUtil.setTime(System.currentTimeMillis()-time); } }
    static void finishAnimations() throws Exception { progress(500); }
    static void backdrop() {
        mc.getFramebuffer().bindFramebuffer(true); glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth(); glDisable(GL_ALPHA_TEST);GlStateManager.disableAlpha();
        glDisable(GL_BLEND);GlStateManager.disableBlend();glColorMask(true,true,true,true);GlStateManager.colorMask(true,true,true,true);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL20.glUseProgram(0);
        glDisable(GL_TEXTURE_2D);GlStateManager.disableTexture2D();
        ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();
        glClearColor(0.12f,0.17f,0.24f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glBegin(GL_QUADS);
        for(int y=0;y<sr.getScaledHeight();y+=5)for(int x=0;x<sr.getScaledWidth();x+=5){boolean light=((x/5+y/5)&1)==0;glColor3f(light?0.28f:0.08f,light?0.34f:0.14f,light?0.43f:0.22f);glVertex2f(x,y);glVertex2f(x,y+5);glVertex2f(x+5,y+5);glVertex2f(x+5,y);}
        glEnd();glEnable(GL_TEXTURE_2D);GlStateManager.enableTexture2D();glColor4f(1,1,1,1);GlStateManager.color(1,1,1,1);
    }
    static BufferedImage capture(){glFinish();ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}return image;}
    static boolean equal(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
    static void sheet(String name,String[] captions,BufferedImage... images)throws Exception{int w=440,h=280,cols=2;BufferedImage sheet=new BufferedImage(w*cols,(h+28)*((images.length+cols-1)/cols),BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,14));for(int i=0;i<images.length;i++){int x=i%cols*w,y=i/cols*(h+28);g.setColor(new Color(18,20,25));g.fillRect(x,y,w,h+28);g.setColor(Color.WHITE);g.drawString(captions[i],x+10,y+19);g.drawImage(images[i],x,y+28,x+Math.min(w,images[i].getWidth()),y+28+Math.min(h,images[i].getHeight()),0,0,Math.min(w,images[i].getWidth()),Math.min(h,images[i].getHeight()),null);}g.dispose();ImageIO.write(sheet,"png",new File(OUT,name));}
    static Field field(Class<?> type,String name)throws Exception{while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);} static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}static void setStatic(Class<?> c,String n,Object v)throws Exception{field(c,n).set(null,v);}static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
