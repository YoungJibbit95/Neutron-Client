package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.activity.WidgetIncident;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.module.impl.client.island.*;
import me.youngjibbit.notifications.Notification;
import me.youngjibbit.notifications.NotificationManager;
import me.youngjibbit.notifications.NotificationType;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.utils.render.RenderState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL20;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

/** Actual Island painters in the existing isolated Pbuffer, with frozen display-only observations. */
public final class IslandCompactRenderCheck {
    private IslandCompactRenderCheck(){}
    public static void run() throws Exception {
        FontManager previous=Pulsar.INSTANCE.getFontManager(),fixtureFonts=null;
        boolean diagnostics=RenderDiagnostics.enabled;
        WatermarkHUDModule watermark=new WatermarkHUDModule();
        ArrayListSurface surface=(ArrayListSurface)SessionInfoRenderCheck.get(watermark,"surface");
        try{
            Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck")
                    .getDeclaredMethod("minimalFonts",String[].class);
            fonts.setAccessible(true);
            fixtureFonts=(FontManager)fonts.invoke(null,(Object)new String[]{"DmSansMedium 18","DmSansMedium 14","DmSansMedium 12"});
            Pulsar.INSTANCE.fontManager=fixtureFonts;
            if(SessionInfoRenderCheck.mc.getTextureManager()==null)
                SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"renderEngine",
                        new TextureManager(SessionInfoRenderCheck.mc.getResourceManager()));
            watermark.getModeSetting("Effects Source").setValue("Local");
            TTFFontRenderer font=fixtureFonts.getFont("DmSansMedium 18");
            TTFFontRenderer wordmark=fixtureFonts.getWordmarkFont("Neutron","DmSansMedium 18");
            Theme theme=Theme.resolve(Theme.getDefaultTheme());
            Method idle=method("paintIslandIdle",IslandPresentation.class,IslandLayout.Frame.class,
                    TTFFontRenderer.class,TTFFontRenderer.class,Theme.class,float.class,String.class,
                    IslandWidget[].class,boolean.class,boolean.class);
            Method notice=method("paintIslandNotification",IslandPresentation.class,IslandLayout.Frame.class,
                    TTFFontRenderer.class,Theme.class,float.class);
            Method targetPainter=method("paintIslandTarget",IslandTargetComposition.class,IslandLayout.Frame.class,
                    TTFFontRenderer.class,Theme.class,float.class);
            Method widget=method("paintIslandWidgetBar",IslandWidget.class,WidgetIncident.class,
                    float.class,float.class,float.class,float.class,Theme.class,float.class,boolean.class,float.class);
            IslandWidget[] widgets={
                    new IslandWidget(IslandWidget.Kind.CPS,"CPS","7·3","L / R",true,.37f,true),
                    new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,.37f,true),
                    new IslandWidget(IslandWidget.Kind.EXPLOIT,"EXP","--","Idle",false,0,false)};
            IslandPresentation base=IslandPresentation.from(null,"Neutron","Alpha 2","12:34",true,true,true)
                    .withClockFace(IslandClockFace.from("12:34"));
            List<BufferedImage> images=new ArrayList<>();List<String> captions=new ArrayList<>();
            ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);
            for(NotificationType type:new NotificationType[]{NotificationType.SUCCESS,NotificationType.WARNING,
                    NotificationType.ERROR,NotificationType.INFO}){
                NotificationManager.IslandNotification message=new NotificationManager.IslandNotification(1,
                        new Notification(type,"Config loaded","The configuration was applied successfully.",5),1,0,5000,2000);
                IslandStateMachine.Selection selected=new IslandStateMachine().resolve(1,2000,
                        null,null,null,null,false,false,false,false,message);
                IslandPresentation view=IslandPresentation.from(selected,"Neutron","Alpha 2","12:34",true,true,true);
                IslandWidgetLayout layout=IslandWidgetLayout.capture(IslandLayout.forState(
                        IslandStateMachine.State.NOTIFICATION,sr.getScaledWidth(),sr.getScaledHeight(),true),3,true);
                IslandAttachmentLayout attachment=new IslandAttachmentLayout(layout.frame,234,
                        IslandStateMachine.State.NOTIFICATION,layout.scale);
                SessionInfoRenderCheck.require(attachment.detail.width<=attachment.base.width*.75f,
                        "Island notification exceeded compact width");
                SessionInfoRenderCheck.backdrop();
                surface.withScene(theme,true,false,3,()->{
                    surface.islandContour(attachment.silhouette(),theme,true,false,true,3,.85f,.24f,()->{
                        invoke(idle,watermark,base,attachment.base,font,wordmark,theme,1f,"Modern",widgets,true,true);
                        invoke(notice,watermark,view,attachment.detail,font,theme,1f);
                    });
                    for(int i=0;i<widgets.length;i++){
                        final int index=i;
                        surface.islandContour(ArrayListSurface.Shape.roundedRectangle(layout.x(i),layout.y,
                                        layout.width(i),layout.diameter,layout.diameter/2),
                                theme,true,false,false,3,.85f,.24f,
                                ()->invoke(widget,watermark,widgets[index],null,layout.x(index),layout.y,
                                        layout.width(index),layout.diameter,theme,1f,layout.left(index),.2f));
                    }
                });
                SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Island notification GL error: "+type);
                captions.add(type+" · centered compact detail and left/right widgets");
                images.add(SessionInfoRenderCheck.capture());
            }
            SessionInfoRenderCheck.sheet("island-compact-notifications-resize.png",captions,images);
            images.clear();captions.clear();
            IslandTargetComposition target=IslandTargetComposition.capture(IslandPresentation.from(
                    new IslandStateMachine().resolve(7,100,new IslandStateMachine.Target(4,"Preview Target",15,20,4),
                            null,true,false),"Neutron","Alpha 2","12:34",true,true,true),.37f,true);
            for(float opening:new float[]{.25f,.6f,1}){
                IslandLayout.Frame desired=IslandLayout.forState(IslandStateMachine.State.TARGET,
                        sr.getScaledWidth(),sr.getScaledHeight(),true);
                IslandAttachmentLayout attachment=new IslandAttachmentLayout(
                        desired.withGeometry(234,34+48*opening,17,1),234,IslandStateMachine.State.TARGET);
                SessionInfoRenderCheck.backdrop();
                surface.withScene(theme,true,false,3,()->surface.islandContour(attachment.silhouette(),theme,
                        true,false,true,3,.85f,.24f,()->{
                            invoke(idle,watermark,base,attachment.base,font,wordmark,theme,1f,"Modern",widgets,true,true);
                            try(me.youngjibbit.clickgui.utils.GuiClip clip=me.youngjibbit.clickgui.utils.GuiClip.push(
                                    new me.youngjibbit.clickgui.utils.ClipRect(attachment.detail.x,attachment.detail.y,
                                            attachment.detail.width,attachment.shell.height-attachment.base.height))){
                                invoke(targetPainter,watermark,target,attachment.detail,font,theme,opening);
                            }
                        }));
                SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Island target header GL error");
                images.add(SessionInfoRenderCheck.capture());captions.add("Fixed logo / clock header · target reveal "+opening);
            }
            SessionInfoRenderCheck.sheet("island-target-header-contour.png",captions,images,true);
            images.clear();captions.clear();
            IslandStateMachine.Scaffold observed=IslandObservations.scaffold(new ScaffoldActivitySnapshot(
                    1,100,1,0,33,"Adaptive Bridge","Godbridge",ScaffoldActivitySnapshot.Status.SEARCHING),100);
            IslandScaffoldIndicator first=IslandScaffoldIndicator.capture(observed,0,true);
            IslandScaffoldIndicator moving=IslandScaffoldIndicator.capture(observed,.37f,true);
            for(float reveal:new float[]{.2f,.65f,1}){
                SessionInfoRenderCheck.backdrop();
                try(RenderState state=new RenderState()){
                    state.screenSpace();
                    for(int kind=0;kind<5;kind++)IslandGlyphRenderer.notice(30+kind*42,42,kind,0xff8ce6cd,1,reveal);
                    IslandGlyphRenderer.scaffold(reveal<.5f?first:moving,240,30,0xff57b6ff,1,reveal);
                }
                images.add(SessionInfoRenderCheck.capture());captions.add("Rounded state glyph reveal "+reveal);
            }
            SessionInfoRenderCheck.require(differences(images.get(0),images.get(2))>30,
                    "Icon state reveal produced no visible change");
            SessionInfoRenderCheck.sheet("island-glyph-motion.png",captions,images);

            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            try(RenderState state=new RenderState()){
                state.screenSpace();glEnable(GL_CULL_FACE);glEnable(GL_ALPHA_TEST);glShadeModel(GL_FLAT);
                glMatrixMode(GL_PROJECTION);
                int program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
                for(int i=0;i<120;i++){
                    IslandGlyphRenderer.notice(40,40,1,0xff8ce6cd,1,(i%60)/59f);
                    IslandGlyphRenderer.scaffold(moving,70,28,0xff57b6ff,1,1);
                    IslandGlyphRenderer.widgetRim(110,24,34,widgets[0],0xff57b6ff,1);
                }
                SessionInfoRenderCheck.require(glIsEnabled(GL_CULL_FACE)&&glIsEnabled(GL_ALPHA_TEST)
                                &&glGetInteger(GL_SHADE_MODEL)==GL_FLAT&&glGetInteger(GL_MATRIX_MODE)==GL_PROJECTION
                                &&glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,
                        "Rounded glyph renderer leaked caller GL state");
            }
            SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0
                            &&RenderDiagnostics.shaderCompiles==0,"Glyph animation allocated GPU resources in steady state");
            SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Island glyph GL error");
            ThemeLogoRenderCheck.run();
        }finally{
            surface.close();watermark.onDisable();
            if(fixtureFonts!=null)fixtureFonts.close();
            Pulsar.INSTANCE.fontManager=previous;RenderDiagnostics.enabled=diagnostics;
        }
        System.out.println("PASS: compact Island notifications, balanced dock composition, rounded icon reveal, GL state and zero steady GPU allocations");
    }
    private static Method method(String name,Class<?>... parameters) throws Exception {
        Method result=WatermarkHUDModule.class.getDeclaredMethod(name,parameters);
        result.setAccessible(true);return result;
    }
    private static void invoke(Method method,Object owner,Object... arguments){
        try{method.invoke(owner,arguments);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(method.getName(),failure);}
    }
    private static int differences(BufferedImage a,BufferedImage b){
        int changed=0;
        for(int y=0;y<Math.min(a.getHeight(),b.getHeight());y++)
            for(int x=0;x<Math.min(a.getWidth(),b.getWidth());x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
        return changed;
    }
}
