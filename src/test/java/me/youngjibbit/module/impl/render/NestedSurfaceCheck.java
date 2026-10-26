package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
public final class NestedSurfaceCheck {
 static void run()throws Exception{
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager before=Pulsar.INSTANCE.getMm();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();ArrayListSurface root=new ArrayListSurface(),child=new ArrayListSurface();
  try{
   mc.displayWidth=1000;mc.displayHeight=700;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1000,700);c.glow.setValue(false);c.border.setValue(false);c.direction.setValue("Vertical");c.gradientMotion.setValue("Static");
   List<BufferedImage> images=new ArrayList<>();List<String> labels=new ArrayList<>();
   for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){
    c.surface.setValue(mode);c.surfaceOpacity.setValue(.65);c.blurMethod.setValue("Gaussian");
    SessionInfoRenderCheck.backdrop();root.panel(10,15,450,300,5,Theme.SynthWave,true,true,false,5,.5f,0,()->{});BufferedImage base=SessionInfoRenderCheck.capture();
    RenderDiagnostics.enabled=true;RenderDiagnostics.reset();SessionInfoRenderCheck.backdrop();root.panel(10,15,450,300,5,Theme.SynthWave,true,true,false,5,.5f,0,()->{UIUtils.drawRound(30,40,180,110,5,new Color(20,20,25));child.panel(240,40,180,110,5,Theme.SynthWave,true,true,false,5,.5f,0,()->{});});BufferedImage nested=SessionInfoRenderCheck.capture();
    int expected=mode.equals("Blur")||mode.equals("Liquid Glass")?2:0;SessionInfoRenderCheck.require(RenderDiagnostics.blurPasses==expected,"Nested surface blurs its parent again: "+mode);RenderDiagnostics.enabled=false;
    for(int y=130;y<230;y++)for(int x=130;x<330;x++)for(int shift:new int[]{0,8,16})SessionInfoRenderCheck.require(Math.abs((base.getRGB(x,y)>>shift&255)-(nested.getRGB(x,y)>>shift&255))<=2,"Nested primitive compounds opacity/blur: "+mode);
    SessionInfoRenderCheck.require(UiEffects.scene()==null,"Surface scene scope leaked");images.add(nested);labels.add(mode+" / root, actual rounded control, nested panel");
   }
   SessionInfoRenderCheck.sheet("15-nested-substances.png",labels,images);
   SessionInfoRenderCheck.require(!GlobalEffectsCheck.same(images.get(0),images.get(1))&&!GlobalEffectsCheck.same(images.get(0),images.get(2))&&!GlobalEffectsCheck.same(images.get(2),images.get(3)),"Surface modes are aliases");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Nested material GL error");System.out.println("PASS: all four substances share root/control/nested scene; no repeated scene blur or compounded opacity; exact scope restoration");
  }finally{root.close();child.close();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",before);UiEffects.invalidate();}
 }
}
