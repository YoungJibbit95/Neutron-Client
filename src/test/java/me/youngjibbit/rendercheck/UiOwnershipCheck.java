package me.youngjibbit.rendercheck;
import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.gui.iosUi.component.impl.*;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.render.GlowESP;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Session;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.util.*;
import java.awt.Font;
import java.awt.Color;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;

/** Audit-only synthetic fixture; no bootstrap, real accounts, network or worlds. */
public class UiOwnershipCheck {
  static Field f(Class<?> c,String n)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ex){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
  static void set(Object o,String n,Object v)throws Exception{f(o.getClass(),n).set(o,v);}
  static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
  static class Screen extends iOS {void click(int x,int y)throws Exception{mouseClicked(x,y,0);} void release(){mouseReleased(0,0,0);}}
  public static void main(String[] args)throws Exception{
    File out=new File("fixture");out.mkdirs();System.setProperty("user.home",out.getAbsolutePath());
    File data=java.nio.file.Files.createTempDirectory(out.toPath(),"ui-audit-").toFile();
    try(PbufferTestContext context=new PbufferTestContext()){
      Minecraft mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("AuditSynthetic","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),new GameConfiguration.DisplayInformation(800,600,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),new GameConfiguration.GameInformation(false,"audit-only"),new GameConfiguration.ServerInformation(null,0)));
      context.initialize(mc,data);
      SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
      set(mc,"framebufferMc",new Framebuffer(800,600,true));
      FontManager fonts=new FontManager();TTFFontRenderer fallback=new TTFFontRenderer(new Font("Dialog",Font.PLAIN,18));set(fonts,"defaultFont",fallback);Pulsar.INSTANCE.fontManager=fonts;
      set(Pulsar.INSTANCE,"mm",new ModuleManager());
      ClientSettings.current().animations.setValue(false);
      Screen screen=new Screen();set(Pulsar.INSTANCE,"iOS",screen);screen.setWorldAndResolution(mc,400,300);

      if(args[0].equals("input")) {
        for(boolean motion:new boolean[]{false,true}) for(boolean close:new boolean[]{false,true}) {
          ClientSettings.current().animations.setValue(motion);
          screen.initGui();
          set(f(iOS.class,"animationEngine").get(screen),"prevTime",System.currentTimeMillis()-1000);
          float x=f(iOS.class,"x").getFloat(screen),y=f(iOS.class,"y").getFloat(screen);
          screen.click((int)x+40,(int)y+10);
          require(f(iOS.class,"dragging").getBoolean(screen),"header capture");
          iOSSlider slider=null;
          for(Object row:(List<?>)f(iOS.class,"MODULES").get(screen)) for(Object child:(List<?>)f(iOSModule.class,"VALUES").get(row)) if(child instanceof iOSSlider) slider=(iOSSlider)child;
          require(slider!=null,"slider fixture"); slider.updateComponent(20,20);slider.mouseClicked(170,28,0);
          require(f(iOSSlider.class,"dragging").getBoolean(slider),"slider capture");
          double value=slider.getValue().getValue();
          if(close) {screen.onGuiClosed();screen.onGuiClosed();} // Escape and child-screen share this lifecycle.
          screen.initGui();screen.initGui();
          require(!f(iOS.class,"dragging").getBoolean(screen),"header capture survived lifecycle");
          require(!f(iOSSlider.class,"dragging").getBoolean(slider),"slider capture survived lifecycle");
          Method move=iOS.class.getDeclaredMethod("smoothDrag",int.class,int.class);move.setAccessible(true);move.invoke(screen,200,150);
          require(f(iOS.class,"x").getFloat(screen)==x,"header moved without gesture");
          slider.drawScreen(-1000,-1000);require(slider.getValue().getValue()==value,"slider changed without gesture");
          slider.mouseClicked(170,28,0);require(f(iOSSlider.class,"dragging").getBoolean(slider),"fresh capture failed");slider.mouseReleased();
        }
        System.out.println("PASS input: close/reopen and reinit, motion/reduced motion, repeated cancellation, no-gesture draw, fresh drag");
      } else {
        GlowESP glow=new GlowESP();Method init=GlowESP.class.getDeclaredMethod("initFramebuffers",float.class);init.setAccessible(true);
        for(int i=0;i<20;i++) {
          mc.displayWidth=800+i;mc.displayHeight=600+i;
          Tracked gaussian=new Tracked(mc.displayWidth,mc.displayHeight);glow.glowFrameBuffer=gaussian;
          init.invoke(glow,3f);require(gaussian.deletes==1,"Gaussian leaked or double deleted");
          Method gaussianInit=GlowESP.class.getDeclaredMethod("initGaussianFramebuffer");gaussianInit.setAccessible(true);
          List<Framebuffer> chain=(List<Framebuffer>)f(GlowESP.class,"framebufferList").get(glow);
          require(chain.get(0)==glow.glowFrameBuffer,"Kawase output alias");
          List<Tracked> old=track(chain,glow);
          mc.displayWidth++;init.invoke(glow,3f);deleted(old);
          old=track(chain,glow);
          gaussianInit.invoke(glow);deleted(old);active(glow.glowFrameBuffer);
          Tracked resized=adopt(glow.glowFrameBuffer);glow.glowFrameBuffer=resized;
          mc.displayHeight++;gaussianInit.invoke(glow);require(resized.deletes==1,"Gaussian resize deletion");active(glow.glowFrameBuffer);
          Tracked last=adopt(glow.glowFrameBuffer);glow.glowFrameBuffer=last;
          glow.onDisable();glow.onDisable();require(last.deletes==1,"disable deletion");
          require(glow.glowFrameBuffer==null&&chain.isEmpty(),"disable references");
          require(glGetError()==GL_NO_ERROR,"transition GL error");
        }
        System.out.println("PASS glow: 20 Gaussian/Kawase transitions, size rebuilds, repeated disable; retired FBO/texture/depth deleted, active valid, no GL errors");
      }
      fallback.close();context.check();
    }
  }
  // Observe deletion before the driver can reuse numeric GL object names.
  static class Tracked extends Framebuffer {
    int deletes;
    Tracked(int w,int h){super(w,h,true);}
    @Override public void deleteFramebuffer(){int[] old=ids(this);super.deleteFramebuffer();if(old[0]>0){dead(old);deletes++;}}
  }
  static Tracked adopt(Framebuffer source)throws Exception {
    Tracked result=new Tracked(1,1);result.deleteFramebuffer();
    for(Field field:Framebuffer.class.getDeclaredFields()) if(!Modifier.isStatic(field.getModifiers())) {field.setAccessible(true);field.set(result,field.get(source));}
    result.deletes=0;return result;
  }
  static List<Tracked> track(List<Framebuffer> chain,GlowESP glow)throws Exception {
    List<Tracked> result=new ArrayList<>();for(int i=0;i<chain.size();i++){active(chain.get(i));Tracked b=adopt(chain.get(i));chain.set(i,b);result.add(b);}glow.glowFrameBuffer=chain.get(0);return result;
  }
  static void deleted(List<Tracked> all){for(Tracked b:all)require(b.deletes==1,"Kawase leaked or double deleted");}
  static int[] ids(Framebuffer b){return new int[]{b.framebufferObject,b.framebufferTexture,b.depthBuffer};}
  static void dead(int[] a){require(!GL30.glIsFramebuffer(a[0])&&!glIsTexture(a[1])&&!GL30.glIsRenderbuffer(a[2]),"retired framebuffer resource remains live: "+Arrays.toString(a));}
  static void active(Framebuffer b){int[] a=ids(b);require(GL30.glIsFramebuffer(a[0])&&glIsTexture(a[1])&&GL30.glIsRenderbuffer(a[2]),"active framebuffer invalid");}
}
