package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.notifications.*;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;
public final class NotificationRenderCheck {
 static Object timeline()throws Exception{java.lang.reflect.Field f=NotificationManager.class.getDeclaredField("timeline");f.setAccessible(true);return f.get(null);}
 static void run()throws Exception{
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();
  try{
   mc.displayWidth=1280;mc.displayHeight=960;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1280,960);c.reducedMotion.setValue(true);c.notificationVisible.setValue(4.0);c.notificationPosition.setValue("Top Right");c.notificationDuration.setValue(1.0);NotificationManager.releaseResources();
   Thread producer=new Thread(()->{for(NotificationType type:new NotificationType[]{NotificationType.INFO,NotificationType.SUCCESS,NotificationType.WARNING,NotificationType.ERROR})NotificationManager.show(new Notification(type,"Synthetic "+type.name(),"Preview only. A long message wraps across several lines.\nExplicit line breaks and verylongwordswithoutexistingbreakpointsremainbounded.",0));for(int i=0;i<9;i++)NotificationManager.show(new Notification(NotificationType.INFO,"Synthetic INFO","Preview only. A long message wraps across several lines.\nExplicit line breaks and verylongwordswithoutexistingbreakpointsremainbounded.",0));});producer.start();producer.join();
   java.util.List<BufferedImage> pictures=new ArrayList<>();
   for(String design:new String[]{"Dark","Outline","Liquid Glass"}){c.notificationDesign.setValue(design);SessionInfoRenderCheck.backdrop();NotificationManager.render();Object timeline=timeline(); // static reflection handled below
    java.util.List<?> cards=(java.util.List<?>)SessionInfoRenderCheck.get(timeline,"visible");SessionInfoRenderCheck.require(cards.size()==4,"Notification visible limit/worker drain");
    SessionInfoRenderCheck.require((int)SessionInfoRenderCheck.get(cards.get(0),"count")==10,"Notification repeats not coalesced");
    for(Object card:cards){Object layout=SessionInfoRenderCheck.get(card,"layout");SessionInfoRenderCheck.require((float)SessionInfoRenderCheck.get(layout,"height")<=112,"Notification card exceeded layout budget");SessionInfoRenderCheck.require(((java.util.List<?>)SessionInfoRenderCheck.get(layout,"lines")).size()>1,"Notification long text did not wrap");}
    BufferedImage capture=SessionInfoRenderCheck.capture();pictures.add(capture.getSubimage(730,0,550,960));
   }
   BufferedImage sheet=new BufferedImage(1650,988,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=sheet.createGraphics();for(int i=0;i<3;i++){graphics.drawImage(pictures.get(i),i*550,28,null);graphics.setColor(java.awt.Color.WHITE);graphics.drawString(new String[]{"Dark","Outline","Liquid Glass"}[i],i*550+10,19);}graphics.dispose();File out=new File("notification-preview");out.mkdirs();ImageIO.write(sheet,"png",new File(out,"notification-designs.png"));
   c.glow.setValue(false);c.notificationDesign.setValue("Liquid Glass");RenderDiagnostics.enabled=true;RenderDiagnostics.reset();SessionInfoRenderCheck.backdrop();NotificationManager.render();SessionInfoRenderCheck.require(RenderDiagnostics.blurPasses==2,"Four notification cards repeat scene blur");c.notificationVisible.setValue(1.0);RenderDiagnostics.reset();SessionInfoRenderCheck.backdrop();NotificationManager.render();SessionInfoRenderCheck.require(RenderDiagnostics.blurPasses==2,"Notification effect passes depend on card count");RenderDiagnostics.enabled=false;
   RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<5;i++){SessionInfoRenderCheck.backdrop();NotificationManager.render();}SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Stable notifications allocate GPU resources");RenderDiagnostics.enabled=false;
   for(String position:new String[]{"Bottom Right","Top Left","Bottom Left"}){c.notificationPosition.setValue(position);mc.displayWidth=701;mc.displayHeight=501;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(701,501);SessionInfoRenderCheck.backdrop();NotificationManager.render();}
   Thread.sleep(1100);SessionInfoRenderCheck.backdrop();NotificationManager.render();Object timeline=timeline();SessionInfoRenderCheck.require(((List<?>)SessionInfoRenderCheck.get(timeline,"visible")).isEmpty(),"Expired notification not removed");
   c.notifications.setValue(false);NotificationManager.show(new Notification(NotificationType.INFO,"Hidden","Master off",0));SessionInfoRenderCheck.backdrop();BufferedImage before=SessionInfoRenderCheck.capture();NotificationManager.render();SessionInfoRenderCheck.require(GlobalEffectsCheck.same(before,SessionInfoRenderCheck.capture()),"Notification master off draws content");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Notification GL error");System.out.println("PASS: notification compositor, all four icons/types, three designs, multiline layout, worker inbox/coalescing, four corner positions, reduced motion, expiry, master off, warm reuse and resize");
  }finally{NotificationManager.releaseResources();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
