package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
/** Real fragment output with configured defaults; deterministic screen-coordinate contract. */
public final class GlobalEffectsCheck {
 static boolean same(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
 static void run()throws Exception{
  ModuleManager old=Pulsar.INSTANCE.getMm();Minecraft mc=SessionInfoRenderCheck.mc;ArrayListSurface surface=new ArrayListSurface();TextEffects text=new TextEffects();
  SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();
  try{
   mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);c.surface.setValue("Gradient");c.gradientFillStrength.setValue(1.0);c.blur.setValue(false);c.glow.setValue(false);c.gradientMotion.setValue("Static");Theme theme=Theme.SynthWave;
   List<BufferedImage> shots=new ArrayList<>();List<String> names=new ArrayList<>();
   for(String axis:new String[]{"Vertical","Horizontal","Up","Left"}){
    c.direction.setValue(axis);SessionInfoRenderCheck.backdrop();surface.panel(20,30,400,220,8,theme,true,false,false,5,1,0,true,()->{});BufferedImage image=SessionInfoRenderCheck.capture();
    for(int[] point:new int[][]{{100,100},{600,100},{100,400},{600,400}}){int expected=UiEffects.gradientColor(theme,UiEffects.Channel.BORDER,point[0]/2f,point[1]/2f);int actual=image.getRGB(point[0],point[1]);for(int shift:new int[]{0,8,16})SessionInfoRenderCheck.require(Math.abs((expected>>shift&255)-(actual>>shift&255))<=3,"Shared gradient fragment differs from common coordinate contract: "+axis);}
    shots.add(image);names.add(axis+" / static common viewport gradient");
   }
   SessionInfoRenderCheck.sheet("10-global-gradients.png",names,shots);
   c.surface.setValue("Standard");shots.clear();names.clear();c.direction.setValue("Vertical");c.glow.setValue(false);c.blur.setValue(true);
   SessionInfoRenderCheck.backdrop();BufferedImage backdrop=SessionInfoRenderCheck.capture();
   for(String method:new String[]{"Gaussian","Kawase"}){
    c.blurMethod.setValue(method);for(int radius:new int[]{0,18}){
     c.roundness.setValue((double)radius);SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,false,5,.1f,0,()->{});BufferedImage image=SessionInfoRenderCheck.capture();
     int changes=0;for(int y=0;y<640;y++)for(int x=0;x<960;x++){if(x<38||x>402||y<58||y>262)SessionInfoRenderCheck.require(image.getRGB(x,y)==backdrop.getRGB(x,y),"Scene blur changed outside surface");else if(image.getRGB(x,y)!=backdrop.getRGB(x,y))changes++;}
     SessionInfoRenderCheck.require(changes>1000,"Selected scene blur has no effect");shots.add(image);names.add(method+" / roundness "+radius);
    }
   }
   SessionInfoRenderCheck.require(!Arrays.equals(shots.get(0).getRGB(50,70,300,150,null,0,300),shots.get(2).getRGB(50,70,300,150,null,0,300)),"Gaussian and Kawase produced identical output");
   SessionInfoRenderCheck.sheet("11-global-blur-shapes.png",names,shots);
   c.roundness.setValue(5.0);c.blur.setValue(false);c.glow.setValue(false);SessionInfoRenderCheck.backdrop();Blur.startBlur();UIUtils.drawRound(20,30,180,100,5,java.awt.Color.WHITE);Blur.endBlur(5,1);SessionInfoRenderCheck.require(same(backdrop,SessionInfoRenderCheck.capture()),"Global Blur off leaked legacy mask");
   for(boolean master:new boolean[]{false,true}){
    c.glow.setValue(master);SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,false,false,true,5,0,0,()->{});BufferedImage halo=SessionInfoRenderCheck.capture();int outside=0;
    for(int y=70;y<250;y++)for(int x=50;x<390;x++)SessionInfoRenderCheck.require(halo.getRGB(x,y)==backdrop.getRGB(x,y),"Global halo contributes inside transparent surface");
    for(int y=0;y<640;y++)for(int x=0;x<960;x++)if(halo.getRGB(x,y)!=backdrop.getRGB(x,y))outside++;
    SessionInfoRenderCheck.require(master?outside>100:outside==0,"Global Glow switch not authoritative");
   }
   shots.clear();names.clear();c.glow.setValue(false);c.surface.setValue("Liquid Glass");c.glassTint.setValue(0.0);c.glassLight.setValue(0.0);
   for(String method:new String[]{"Gaussian","Kawase"}){c.blurMethod.setValue(method);for(boolean blur:new boolean[]{false,true}){
    c.blur.setValue(blur);c.glassRefraction.setValue(0.0);SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,false,5,.5f,0,()->{});BufferedImage flat=SessionInfoRenderCheck.capture();
    c.glassRefraction.setValue(1.0);SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,false,5,.5f,0,()->{});BufferedImage bent=SessionInfoRenderCheck.capture();int refracted=0;
    for(int y=0;y<640;y++)for(int x=0;x<960;x++){if(x<38||x>402||y<58||y>262)SessionInfoRenderCheck.require(bent.getRGB(x,y)==backdrop.getRGB(x,y),"Liquid Glass escaped mask");if(x>42&&x<398&&y>62&&y<258&&bent.getRGB(x,y)!=flat.getRGB(x,y))refracted++;}
    SessionInfoRenderCheck.require(refracted>500,"Liquid Glass refraction is not sampling displaced scene");shots.add(bent);names.add("Liquid Glass / "+method+" / blur "+blur);
   }}
   SessionInfoRenderCheck.sheet("12-liquid-glass.png",names,shots);
   boolean shaders=net.minecraft.client.renderer.OpenGlHelper.shadersSupported;try{net.minecraft.client.renderer.OpenGlHelper.shadersSupported=false;SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,true,5,.5f,1,()->{});SessionInfoRenderCheck.require(!same(backdrop,SessionInfoRenderCheck.capture()),"Shader-free surface fallback is blank");}finally{net.minecraft.client.renderer.OpenGlHelper.shadersSupported=shaders;}
   c.blur.setValue(true);c.glow.setValue(true);c.glassTint.setValue(.18);c.glassLight.setValue(.3);
   SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,true,5,.5f,1,()->UIUtils.drawRound(80,60,12,8,0,java.awt.Color.WHITE));BufferedImage sharp=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.require((sharp.getRGB(170,126)&0xffffff)==0xffffff,"Glass blurred its own sharp content");
   for(int q=1;q<=4;q++){c.blurQuality.setValue((double)q);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,false,5,.5f,0,()->{});SessionInfoRenderCheck.require(RenderDiagnostics.blurPasses==q*2,"Kawase quality does not select pass count");}RenderDiagnostics.enabled=false;
   c.surface.setValue("Gradient");c.glow.setValue(false);c.blur.setValue(false);c.synchronize.setValue(true);c.gradientMotion.setValue("Animated");c.gradientSpeed.setValue(1.0);c.direction.setValue("Vertical");
   java.lang.reflect.Method clock=UiEffects.class.getDeclaredMethod("timeSource",java.util.function.LongSupplier.class);clock.setAccessible(true);java.util.concurrent.atomic.AtomicLong nanos=new java.util.concurrent.atomic.AtomicLong();clock.invoke(null,(java.util.function.LongSupplier)nanos::get);
   try{shots.clear();names.clear();BufferedImage initial=null;for(int phase=0;phase<=4;phase++){nanos.set(phase*250000000L);UiEffects.beginFrame();SessionInfoRenderCheck.backdrop();surface.panel(20,30,400,220,5,theme,true,false,false,5,1,0,true,()->{});BufferedImage image=SessionInfoRenderCheck.capture();if(phase==0)initial=image;if(phase==4)SessionInfoRenderCheck.require(same(initial,image),"Animated gradient jumps at cycle boundary");else{shots.add(image);names.add("Vertical animated / phase "+phase+"/4");}}SessionInfoRenderCheck.require(!same(shots.get(0),shots.get(1)),"Animated gradient does not move on GPU");SessionInfoRenderCheck.sheet("13-gradient-motion.png",names,shots);}finally{clock.invoke(null,(java.util.function.LongSupplier)System::nanoTime);}
   c.synchronize.setValue(false);c.textDirection.setValue("Vertical");c.textMotion.setValue("Static");c.glowMotion.setValue("Solid");c.glow.setValue(true);
   me.youngjibbit.utils.font.TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 20");
   try(RenderState state=new RenderState()){
    state.screenSpace();glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);text.draw(font,"Text glow",70,80,0xffff0000,0xff0000ff,1,false,0,false,1,5,false);BufferedImage sharpOnly=SessionInfoRenderCheck.capture();
    glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);text.draw(font,"Text glow",70,80,0xffff0000,0xff0000ff,1,false,0,true,1,5,false);BufferedImage withHalo=SessionInfoRenderCheck.capture();int haloPixels=0;
    for(int y=0;y<640;y++)for(int x=0;x<960;x++)if((sharpOnly.getRGB(x,y)&0xffffff)==0&&(withHalo.getRGB(x,y)&0xffffff)!=0){int rgb=withHalo.getRGB(x,y);SessionInfoRenderCheck.require((rgb&255)<=1,"Text halo incorrectly follows separate TEXT rather than GLOW channel");haloPixels++;}
    SessionInfoRenderCheck.require(haloPixels>30,"Separate text halo has no output");
   }
   c.synchronize.setValue(true);c.gradientMotion.setValue("Static");
   System.out.println("PASS: actual scene refraction with Blur on/off and both algorithms; shader-free tinted fallback; sharp foreground; 2-8 scene passes");
   SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,true,5,.2f,1,()->{});
   RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<5;i++){SessionInfoRenderCheck.backdrop();surface.panel(20,30,180,100,5,theme,true,true,true,5,.2f,1,()->{});}SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Global effect steady allocations");RenderDiagnostics.enabled=false;
   System.out.println("PASS: Gaussian/Kawase distinct scene output; roundness extrema; global off never paints masks; transparent interior excludes glow; warm resources reused");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Shared gradient GL state error");System.out.println("PASS: four gradient directions; GPU colors match common CPU viewport coordinates; shared contour compositor");
  }finally{surface.close();text.close();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
