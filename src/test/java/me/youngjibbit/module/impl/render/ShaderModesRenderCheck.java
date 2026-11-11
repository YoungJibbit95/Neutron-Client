package me.youngjibbit.module.impl.render;

import com.google.gson.*;
import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import static org.lwjgl.opengl.GL11.*;

/** Production GLSL, controlled time and synthetic filled silhouettes; never a world/bootstrap claim. */
public final class ShaderModesRenderCheck {
    private static final File OUT=new File("shader-preview");
    private static final String[] MODES={"Flame","Neon","Lines"};
    private static final float X=210,Y=140,W=220,H=190;
    private static final AtomicLong CLOCK=new AtomicLong();
    private static final JsonObject REPORT=new JsonObject();
    private static final JsonArray SAMPLES=new JsonArray();
    private static final List<BufferedImage> GALLERY=new ArrayList<>();
    private static final List<String> CAPTIONS=new ArrayList<>();
    private static final ShaderUtil[] shaders=new ShaderUtil[3];
    private static Minecraft mc;
    private static ClientSettings settings;
    private static FontManager fonts;
    private static TTFFontRenderer font;
    private static ContourMaskRenderer contours;
    private static ArrayListSurface surface;
    private static Framebuffer mask;

    public static void main(String[] args)throws Exception{
        OUT.mkdirs();File data=Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        REPORT.addProperty("format_version",1);REPORT.addProperty("status","incomplete");
        REPORT.addProperty("scope","Production GLSL/Pbuffer/FBO, synthetic boxes/silhouettes/UI and controlled time. No native Display, game world, shaderpack, VSync or client FPS claim.");
        try(PbufferTestContext context=new PbufferTestContext()){
            initialize(context,data);
            try{rectangleAppearance();filledSilhouettes();motionAndAnimation();maximalFlameBounds();uiConsumers();textGlowColorSources();pulsePaletteSemantics();stateAndClipping();resizeAndResources();performanceEvidence();contactSheet();context.check();require(glGetError()==GL_NO_ERROR,"Shader-mode GL error");}
            finally{release();}
            require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Final FBO lifetime imbalance: "+RenderDiagnostics.snapshot());
            require(RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes,"Final program lifetime imbalance: "+RenderDiagnostics.snapshot());
            require(RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes,"Final shader lifetime imbalance");
            require(glGetError()==GL_NO_ERROR,"Shader-mode cleanup GL error");
            REPORT.addProperty("framebuffer_creates",RenderDiagnostics.framebufferCreates);REPORT.addProperty("framebuffer_deletes",RenderDiagnostics.framebufferDeletes);
            REPORT.addProperty("program_creates",RenderDiagnostics.programCreates);REPORT.addProperty("program_deletes",RenderDiagnostics.programDeletes);
            REPORT.add("measurements",SAMPLES);REPORT.addProperty("status","passed");writeReport();context.check();
        }catch(Throwable error){REPORT.addProperty("status","failed");REPORT.addProperty("error",error.toString());REPORT.add("measurements",SAMPLES);writeReport();throw error;}
        System.out.println("PASS: shader modes actual exterior geometry, white neon core, separate line contours, filled masks, motion, UI toggles, state, resize and resource lifetime");
    }
    private static void initialize(PbufferTestContext context,File data)throws Exception{
        mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("ShaderModeFixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(640,480,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"Shader mode Pbuffer check"),new GameConfiguration.ServerInformation(null,0)));
        context.initialize(mc,data);IMetadataSerializer metadata=new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);SessionInfoRenderCheck.set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
        RenderDiagnostics.enabled=true;RenderDiagnostics.reset();SessionInfoRenderCheck.set(mc,"framebufferMc",new Framebuffer(640,480,true));mc.gameSettings.guiScale=1;
        settings=new ClientSettings();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager(Collections.singletonList(settings)));
        Method minimal=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);minimal.setAccessible(true);
        fonts=(FontManager)minimal.invoke(null,(Object)new String[]{"Poppins-Bold 23","Product-Sans-Regular 20"});Pulsar.INSTANCE.fontManager=fonts;font=fonts.getFont("Poppins-Bold 23");
        Theme.setDefaultTheme(Theme.SynthWave);settings.blur.setValue(false);settings.glow.setValue(true);settings.border.setValue(true);settings.textGlow.setValue("On");settings.textGlowColor.setValue("Theme");settings.gradientMotion.setValue("Animated");settings.reducedMotion.setValue(false);
        Method time=UiEffects.class.getDeclaredMethod("timeSource",LongSupplier.class);time.setAccessible(true);time.invoke(null,(LongSupplier)CLOCK::get);UiEffects.invalidate();UiEffects.beginFrame();
        for(int i=0;i<MODES.length;i++)shaders[i]=new ShaderUtil("pulsar/shader/esp/"+MODES[i].toLowerCase(java.util.Locale.ROOT)+".frag");
        contours=new ContourMaskRenderer();surface=new ArrayListSurface();mask=new Framebuffer(mc.displayWidth,mc.displayHeight,false);
        REPORT.addProperty("java",System.getProperty("java.runtime.version"));REPORT.addProperty("gl_vendor",glGetString(GL_VENDOR));REPORT.addProperty("gl_renderer",glGetString(GL_RENDERER));REPORT.addProperty("gl_version",glGetString(GL_VERSION));
    }
    private static void rectangleAppearance()throws Exception{
        for(String mode:MODES){BufferedImage image=rectangle(mode,0,0,0);save(image,"rectangle-"+mode+".png");gallery(mode+" / analytic ESP box",image);
            require(changed(image,(int)X-110,(int)Y-100,(int)W+220,96)>20||changed(image,(int)(X+W+3),(int)Y,100,(int)H)>50,mode+" only recolors the original border; exterior geometry missing");
            require(changed(image,(int)X+35,(int)Y+40,(int)W-70,(int)H-80)==0,mode+" unexpectedly fills the ESP box interior");}
        BufferedImage neon=rectangle("Neon",0,0,0);int white=whitePixels(neon,(int)X-4,(int)Y-4,(int)W+8,(int)H+8);require(white>12,"Neon has no bright near-white core: "+white);
        require(coloredPixels(neon,(int)(X+W+4),(int)Y+15,42,(int)H-30)>20,"Neon outer halo has no saturated theme colors");
        settings.neonCore.setValue(.05);BufferedImage thin=rectangle("Neon",0,0,0);settings.neonCore.setValue(.95);BufferedImage broad=rectangle("Neon",0,0,0);
        require(differences(thin,broad)>80,"Neon Core control has no visible effect");save(broad,"neon-core-high.png");settings.neonCore.reset();
        settings.neonGlow.setValue(.2);BufferedImage soft=rectangle("Neon",0,0,0);settings.neonGlow.setValue(1.8);BufferedImage strong=rectangle("Neon",0,0,0);
        require(energy(strong,(int)(X+W+5),(int)Y+20,40,(int)H-40)>energy(soft,(int)(X+W+5),(int)Y+20,40,(int)H-40)*1.15,"Neon Glow does not strengthen exterior halo");settings.neonGlow.reset();
        settings.lineSpacing.setValue(6.0);settings.lineSegments.setValue(2.0);List<Integer> two=contourPeaks(rectangle("Lines",0,0,0),(int)(X+W));
        settings.lineSegments.setValue(6.0);BufferedImage sixImage=rectangle("Lines",0,0,0);List<Integer> six=contourPeaks(sixImage,(int)(X+W));
        require(two.size()==2&&six.size()==6,"Lines count is not geometric: two="+two+", six="+six);save(sixImage,"lines-count-six-gap-six.png");
        settings.lineSpacing.setValue(3.0);List<Integer> narrow=contourPeaks(rectangle("Lines",0,0,0),(int)(X+W));require(narrow.size()==6,"Narrow Lines spacing merged/lost contours: "+narrow);require(span(six)>span(narrow)+8,"Lines Spacing does not move separate contours");settings.lineSegments.reset();settings.lineSpacing.reset();
        System.out.println("PASS: analytic exterior flame/neon/lines; hollow interiors, white neon core, halo strength and exact line count/spacing");
    }
    private static void filledSilhouettes()throws Exception{
        fillMask(1);
        for(String mode:MODES){BufferedImage image=silhouette(mode,0,0,0);save(image,"silhouette-"+mode+".png");gallery(mode+" / filled entity silhouette",image);
            require(changed(image,180,75,280,330)>350,"Filled-mask "+mode+" has no contour");require(changed(image,307,215,26,35)==0,mode+" filled the middle of the source silhouette");
            require(changed(image,238,196,18,90)>12||changed(image,365,196,36,90)>20,mode+" has no outer silhouette glow");
            if("Neon".equals(mode)){require(whitePixels(image,250,140,140,225)>8,"Filled-mask Neon lost its white core");neonStraightEdgeContinuity(image);}}
        clearMask();for(String mode:MODES)require(changed(silhouette(mode,0,0,0),0,0,mc.displayWidth,mc.displayHeight)==0,"Empty source mask creates stray "+mode+" pixels");
        fillMask(12);save(silhouette("Neon",0,0,.3f),"silhouette-twelve.png");fillMask(1);
        System.out.println("PASS: filled silhouette exterior, hollow body, white core, empty masks and shared twelve-entity mask");
    }
    private static void neonStraightEdgeContinuity(BufferedImage image){
        // The left arm's vertical edge is x=258, y=202..297. Sample its three
        // exterior columns over 64 rows, well away from both corners. A second
        // difference rejects alternating beads without penalizing a slow palette gradient.
        double[] profile=new double[64];double total=0;
        for(int i=0;i<profile.length;i++){
            for(int x=255;x<=257;x++){int rgb=image.getRGB(x,218+i);profile[i]+=(.2126*(rgb>>16&255)+.7152*(rgb>>8&255)+.0722*(rgb&255))/3;}
            total+=profile[i];
        }
        double sum=0,maximum=0;
        for(int i=1;i<profile.length-1;i++){double curvature=Math.abs(profile[i-1]-2*profile[i]+profile[i+1]);sum+=curvature;maximum=Math.max(maximum,curvature);}
        double mean=sum/(profile.length-2);
        REPORT.addProperty("neon_straight_edge_mean_second_difference",mean);REPORT.addProperty("neon_straight_edge_max_second_difference",maximum);
        require(total/profile.length>35,"Neon straight-edge continuity reference has no visible core/shoulder");
        require(mean<=12&&maximum<=36,"Neon straight silhouette edge forms a bright/dim bead chain: mean="+mean+", max="+maximum);
        System.out.println("PASS: long straight Neon silhouette edge stays continuous outside corners; mean curvature="+mean+", max="+maximum);
    }
    private static void motionAndAnimation()throws Exception{
        BufferedImage stable=rectangle("Flame",0,0,0);require(differences(stable,rectangle("Flame",0,0,0))==0,"Equal phase/input does not reproduce Flame pixels");
        BufferedImage left=rectangle("Flame",-1,0,1),right=rectangle("Flame",1,0,1);
        double leftCenter=centroidX(left,(int)X-100,(int)Y-100,(int)W+200,95),rightCenter=centroidX(right,(int)X-100,(int)Y-100,(int)W+200,95);
        require(Double.isFinite(leftCenter)&&Double.isFinite(rightCenter)&&rightCenter>leftCenter+.75,"Opposite normalized flow does not bend upper Flame: "+leftCenter+" / "+rightCenter);
        save(left,"flame-flow-left.png");save(right,"flame-flow-right.png");
        for(String mode:MODES){BufferedImage before=rectangle(mode,0,0,0);advance(.7);require(differences(before,rectangle(mode,0,0,0))>50,mode+" does not visibly animate");}
        List<BufferedImage> frames=new ArrayList<>();
        for(int i=0;i<10;i++){float flow=(float)Math.sin(i*Math.PI/5)*.85f;advance(.1);BufferedImage image=rectangle("Flame",flow,0,Math.abs(flow));frames.add(image);if(i%3==0)save(image,"flame-frame-"+i+".png");}
        gif(frames,"flame-motion.gif");
        settings.animations.setValue(false);BufferedImage frozen=rectangle("Flame",0,0,0);advance(1);require(differences(frozen,rectangle("Flame",0,0,0))==0,"Disabled animations still advance Flame");
        settings.animations.setValue(true);settings.reducedMotion.setValue(true);frozen=rectangle("Neon",0,0,0);advance(.5);require(differences(frozen,rectangle("Neon",0,0,0))==0,"Reduced Motion still advances Neon");settings.reducedMotion.setValue(false);
        System.out.println("PASS: deterministic frames, directional flame flow, all animated modes and animation/reduced-motion freeze");
    }
    private static void maximalFlameBounds()throws Exception{
        settings.flameHeight.setValue(1.0);settings.flameMotion.setValue(1.0);
        try{
            BufferedImage bounded=rectangle("Flame",1,-1,1);save(bounded,"flame-max-height-motion.png");
            // Reuse the actual production program and its unchanged uniforms. Only
            // replace its padded geometry with a full-viewport quad to reveal clipping.
            begin();try(RenderState state=new RenderState()){
                glDisable(GL_CULL_FACE);GlStateManager.enableBlend();glEnable(GL_BLEND);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
                shaders[0].init();try{ShaderUtil.drawQuads();}finally{shaders[0].unload();}
            }
            BufferedImage unbounded=capture();save(unbounded,"flame-max-height-motion-reference.png");
            require(changed(unbounded,0,0,mc.displayWidth,1)==0&&changed(unbounded,0,mc.displayHeight-1,mc.displayWidth,1)==0
                    &&changed(unbounded,0,0,1,mc.displayHeight)==0&&changed(unbounded,mc.displayWidth-1,0,1,mc.displayHeight)==0,
                    "Maximum Flame reference reaches viewport edge; clipping comparison is inconclusive");
            require(changed(bounded,100,30,440,(int)Y-30)>100,"Maximum Flame has no visible upper plume");
            require(differences(bounded,unbounded)==0,"Production padding clips maximum-height Flame with full upward/lateral wind");
        }finally{settings.flameHeight.reset();settings.flameMotion.reset();}
        System.out.println("PASS: maximum Flame height/motion matches the full-viewport production-shader reference without clipping");
    }
    private static void uiConsumers()throws Exception{
        require(Arrays.equals(new String[]{"Standard","Flame","Neon"},settings.uiGlowMode.getOptions()),"UI exposes unsupported contour modes");List<BufferedImage> images=new ArrayList<>();
        for(String mode:new String[]{"Standard","Flame","Neon"}){
            settings.uiGlowMode.setValue(mode);require(ContourEffects.specialUi()==!"Standard".equals(mode),"UI mode dispatch disagrees with setting");
            BufferedImage image=uiScene();images.add(image);save(image,"ui-"+mode+".png");gallery(mode+" / production surface and font glow",image);
            require(changed(image,8,8,24,24)==0&&changed(image,image.getWidth()-32,image.getHeight()-32,24,24)==0,
                    mode+" UI overwrites distant dark background pixels");
            require(changed(image,165,145,310,135)>500,mode+" UI panel/content is missing");
            require(whitePixels(image,183,163,230,65)>25,mode+" UI has no readable bright text in its expected content area");
        }
        require(differences(images.get(0),images.get(1))>100&&differences(images.get(1),images.get(2))>100,"UI selection does not reach production surface/font renderer");
        settings.glow.setValue(false);settings.textGlow.setValue("Off");settings.uiGlowMode.setValue("Flame");BufferedImage flameOff=uiScene();settings.uiGlowMode.setValue("Neon");BufferedImage neonOff=uiScene();
        require(differences(flameOff,neonOff)==0,"Disabled UI/Text glow still exposes mode-dependent pixels");save(neonOff,"ui-glow-off.png");
        settings.glow.setValue(true);settings.textGlow.setValue("On");settings.uiGlowMode.setValue("Standard");
        System.out.println("PASS: Standard/Flame/Neon UI keeps distant background dark and renders visible panel content and bright text");
        System.out.println("PASS: actual UI surfaces/fonts select Flame/Neon, expose no Lines option and obey both glow toggles");
    }
    private static void textGlowColorSources()throws Exception{
        String oldMode=settings.uiGlowMode.getValue(),oldSource=settings.textGlowColor.getValue(),oldGlow=settings.textGlow.getValue();
        double oldStrength=settings.textGlowStrength.getValue(),oldRadius=settings.textGlowRadius.getValue();
        me.youngjibbit.theme.ThemeCatalog catalog=me.youngjibbit.theme.ThemeCatalog.global();
        Color blue=new Color(24,60,255);
        catalog.preview(Theme.custom("custom:00000000-0000-0000-0000-000000000001","Shader blue reference",Arrays.asList(blue,blue),false,false,.18f,
                new Color(8,11,18),Color.WHITE,blue,.75f,9,.8f,1));
        try(TextEffects text=new TextEffects()){
            settings.textGlowStrength.setValue(1.0);settings.textGlowRadius.setValue(3.0);
            for(boolean masked:new boolean[]{false,true}){
                settings.textGlow.setValue("Off");BufferedImage sharp=coloredText(text,masked);
                for(String mode:new String[]{"Flame","Neon"}){
                    settings.uiGlowMode.setValue(mode);settings.textGlow.setValue("On");settings.textGlowColor.setValue("Text");BufferedImage red=coloredText(text,masked);
                    settings.textGlowColor.setValue("Theme");BufferedImage themed=coloredText(text,masked);
                    String path=masked?"mask":"atlas";save(red,"text-"+path+"-"+mode+"-text-color.png");save(themed,"text-"+path+"-"+mode+"-theme-color.png");
                    int switched=0,opaque=0;
                    for(int y=0;y<sharp.getHeight();y++)for(int x=0;x<sharp.getWidth();x++){
                        int base=sharp.getRGB(x,y)&0xffffff,a=red.getRGB(x,y)&0xffffff,b=themed.getRGB(x,y)&0xffffff;
                        if(delta(base)<=1&&(a>>16&255)>(a&255)+4&&(b&255)>(b>>16&255)+4
                                &&(a>>16&255)>(b>>16&255)+3&&(b&255)>(a&255)+3)switched++;
                        if(base==0xff2020){opaque++;require(channelDifference(a,b)<=1,"Text Glow Color recolored opaque "+path+" glyphs in "+mode);}
                    }
                    require(opaque>5,"Text color reference has no opaque red "+path+" glyphs");
                    require(switched>12,"Text/Theme did not visibly switch red/blue exterior aura: "+path+" "+mode+" pixels="+switched);
                }
            }
        }finally{
            catalog.cancelPreview();settings.uiGlowMode.setValue(oldMode);settings.textGlowColor.setValue(oldSource);settings.textGlow.setValue(oldGlow);
            settings.textGlowStrength.setValue(oldStrength);settings.textGlowRadius.setValue(oldRadius);UiEffects.invalidate();
        }
        System.out.println("PASS: atlas and masked Flame/Neon text keep opaque red glyphs and switch exterior aura between Text red and Theme blue");
    }
    private static BufferedImage coloredText(TextEffects text,boolean masked){
        begin();if(masked)text.draw(font,"Neutron glow",210,210,0xffff2020,0xffff2020,0,false,0,true,1,3,false);
        else font.drawString("Neutron glow",210,210,0xffff2020);return capture();
    }
    private static void pulsePaletteSemantics()throws Exception{
        double oldGlow=settings.neonGlow.getValue();settings.neonGlow.setValue(0.0);
        try{
            BufferedImage zero=paletteReference(0,false),quarter=paletteReference(.25f,false),neutral=paletteReference(.25f,true);
            save(zero,"pulse-phase-zero.png");save(quarter,"pulse-phase-quarter.png");save(neutral,"pulse-neutral-reference.png");
            require(differences(zero,quarter)>100,"Pulse phase has no visible palette effect");
            for(int y=0;y<quarter.getHeight();y++)for(int x=0;x<quarter.getWidth();x++)
                require(channelDifference(quarter.getRGB(x,y),neutral.getRGB(x,y))<=1,"Pulse at phase .25 retains a spatial gradient at "+x+","+y);
            require(changed(quarter,(int)X-5,(int)Y-5,(int)W+10,(int)H+10)>100,"Pulse palette reference is empty");
        }finally{settings.neonGlow.setValue(oldGlow);}
        System.out.println("PASS: production contour Pulse at phase .25 is spatially neutral and matches the equal-endpoint-color reference");
    }
    private static BufferedImage paletteReference(float phase,boolean neutral){
        // Obtain all geometry/style uniforms from the production entry point. Only
        // palette inputs are controlled to distinguish Pulse from spatial Flow.
        begin();drawRect("Neon",X,Y,W,H,0,0,0);begin();
        try(RenderState state=new RenderState()){
            glDisable(GL_CULL_FACE);GlStateManager.enableBlend();glEnable(GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            ShaderUtil shader=shaders[1];shader.init();try{
                shader.setUniformf("fxPrimary",neutral?.5f:1,0,neutral?.5f:0);shader.setUniformf("fxSecondary",0,0,1);
                shader.setUniformf("fxGradient",1,neutral?0:4,phase);ShaderUtil.drawQuads();
            }finally{shader.unload();}
        }
        return capture();
    }
    private static int channelDifference(int a,int b){return Math.max(Math.abs((a>>16&255)-(b>>16&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));}
    private static void stateAndClipping()throws Exception{
        fillMask(1);
        try(ShaderUtil sentinel=new ShaderUtil("#version 120\nvoid main(){gl_FragColor=vec4(0.3,0.6,0.9,1.0);}",true)){
            for(boolean masked:new boolean[]{false,true}){begin();try(RenderState restore=new RenderState()){
                glEnable(GL_SCISSOR_TEST);glScissor(220,110,240,180);glEnable(GL_DEPTH_TEST);GlStateManager.enableDepth();glDepthFunc(GL_GREATER);GlStateManager.depthMask(false);glDepthMask(false);
                glEnable(GL_BLEND);GlStateManager.enableBlend();GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);GL14.glBlendFuncSeparate(GL_ONE,GL_ZERO,GL_ZERO,GL_ONE);
                GL13.glActiveTexture(GL13.GL_TEXTURE3);GlStateManager.setActiveTexture(GL13.GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,mask.framebufferTexture);glViewport(3,7,mc.displayWidth-11,mc.displayHeight-15);sentinel.init();State before=new State();
                if(masked)drawMask("Neon",0,0,0);else drawRect("Neon",X,Y,W,H,0,0,0);before.assertSame(new State());BufferedImage image=capture();int top=mc.displayHeight-110-180;
                require(changed(image,220,top,240,180)>30,"Hostile blend/depth state suppresses visible output");
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(x<220||x>=460||y<top||y>=top+180)require(delta(image.getRGB(x,y))<=1,"Contour escaped caller scissor");
                save(image,masked?"clipped-mask.png":"clipped-box.png");
            }}
        }
        require(glGetError()==GL_NO_ERROR,"Contour state contract GL error");System.out.println("PASS: original FBO/program/viewport/blend/depth/scissor/texture restored; clipped output visible");
    }
    private static void resizeAndResources()throws Exception{
        settings.uiGlowMode.setValue("Flame");uiScene();fillMask(1);silhouette("Flame",0,0,0);
        long creates=RenderDiagnostics.framebufferCreates,programs=RenderDiagnostics.programCreates,compiles=RenderDiagnostics.shaderCompiles;
        for(int i=0;i<6;i++){uiScene();silhouette("Flame",0,0,0);}
        require(creates==RenderDiagnostics.framebufferCreates&&programs==RenderDiagnostics.programCreates&&compiles==RenderDiagnostics.shaderCompiles,"Warm UI/mask mode creates GPU resources");
        resize(1101,733,3);begin();drawRect("Flame",70,75,210,120,-.6f,0,.6f);save(capture(),"flame-odd-scale3.png");require(changed(capture(),0,0,mc.displayWidth,mc.displayHeight)>500,"Scaled contour is empty");
        resize(854,480,2);settings.uiGlowMode.setValue("Neon");save(uiScene(),"ui-neon-scale2.png");
        resize(640,480,1);fillMask(1);BufferedImage before=silhouette("Neon",0,0,0);List<Integer> textures=ownedTextures(contours);require(!textures.isEmpty(),"Mask path did not allocate real GPU targets");
        contours.close();for(int texture:textures)require(!glIsTexture(texture),"Contour texture survived close: "+texture);contours=new ContourMaskRenderer();
        require(differences(before,silhouette("Neon",0,0,0))==0,"Recreating mask renderer changes fixed-time pixels");settings.uiGlowMode.setValue("Standard");
        System.out.println("PASS: zero warm GPU allocations, odd GUI scale/resize, actual texture deletion and deterministic recreation");
    }
    private static void performanceEvidence()throws Exception{
        for(String mode:MODES){
            for(int i=0;i<3;i++){fillMask(12);silhouette(mode,0,0,.2f);}long creates=RenderDiagnostics.framebufferCreates,programs=RenderDiagnostics.programCreates;
            fillMask(1);begin();long draws=RenderDiagnostics.shaderDraws;drawMask(mode,0,0,.2f);long one=RenderDiagnostics.shaderDraws-draws;
            fillMask(12);begin();draws=RenderDiagnostics.shaderDraws;drawMask(mode,0,0,.2f);long twelve=RenderDiagnostics.shaderDraws-draws;
            require(one>0&&one==twelve,"Mask effect passes scale with entity count: "+mode+" "+one+"/"+twelve);
            double[] submitted=new double[24],completed=new double[24];
            for(int i=0;i<completed.length;i++){begin();glFinish();long start=System.nanoTime();drawMask(mode,0,0,.2f);long queued=System.nanoTime();glFinish();long end=System.nanoTime();submitted[i]=(queued-start)/1e6;completed[i]=(end-start)/1e6;}
            require(creates==RenderDiagnostics.framebufferCreates&&programs==RenderDiagnostics.programCreates,"Measured mask workload allocates GPU resources");measurement("twelve_silhouettes",mode,submitted,completed,one);
        }
        settings.lineSegments.setValue(2.0);begin();long start=RenderDiagnostics.shaderDraws;drawMask("Lines",0,0,0);long two=RenderDiagnostics.shaderDraws-start;
        settings.lineSegments.setValue(16.0);begin();start=RenderDiagnostics.shaderDraws;drawMask("Lines",0,0,0);require(two==RenderDiagnostics.shaderDraws-start,"Lines count adds framebuffer/draw passes");settings.lineSegments.reset();
        for(String mode:new String[]{"Standard","Flame","Neon"}){settings.uiGlowMode.setValue(mode);for(int i=0;i<3;i++)uiScene();long creates=RenderDiagnostics.framebufferCreates,programs=RenderDiagnostics.programCreates;
            double[] submitted=new double[24],completed=new double[24];for(int i=0;i<completed.length;i++){begin();glFinish();long t0=System.nanoTime();paintUi();long t1=System.nanoTime();glFinish();long t2=System.nanoTime();submitted[i]=(t1-t0)/1e6;completed[i]=(t2-t0)/1e6;}
            require(creates==RenderDiagnostics.framebufferCreates&&programs==RenderDiagnostics.programCreates,"Measured UI workload allocates GPU resources");measurement("ui_surface_text",mode,submitted,completed,-1);
        }
        REPORT.addProperty("timing_scope","24 warm synthetic frames per workload; completed_ms includes glFinish, not GPU timestamps. Mask drawing/backdrop/readback/export excluded. No timing pass/fail threshold or game FPS inference.");
        System.out.println("PASS: fixed mask pass count for 1/12 entities and 2/16 lines; warm resource budgets; raw synthetic timings retained");
    }
    private static BufferedImage rectangle(String mode,float x,float y,float energy){begin();drawRect(mode,X,Y,W,H,x,y,energy);return capture();}
    private static void drawRect(String mode,float x,float y,float width,float height,float flowX,float flowY,float energy){
        EspEffectStyle.drawRectangle(shaders[Arrays.asList(MODES).indexOf(mode)],Theme.getDefaultTheme(),mode,x,y,width,height,Theme.getDefaultTheme().getPrimaryGradientRGB(),Theme.getDefaultTheme().getSecondaryGradientRGB(),flowX,flowY,energy);
    }
    private static BufferedImage silhouette(String mode,float x,float y,float energy){begin();drawMask(mode,x,y,energy);return capture();}
    private static void drawMask(String mode,float x,float y,float energy){contours.draw(Theme.getDefaultTheme(),mode,mask.framebufferTexture,mc.displayWidth,mc.displayHeight,new ScaledResolution(mc).getScaleFactor(),1,true,x,y,energy);}
    private static BufferedImage uiScene(){begin();paintUi();return capture();}
    private static void paintUi(){
        ScaledResolution sr=new ScaledResolution(mc);float width=Math.min(310,sr.getScaledWidth()-60),x=(sr.getScaledWidth()-width)/2f,top=Math.max(45,(sr.getScaledHeight()-190)/2f);
        surface.panel(x,top,width,135,12,Theme.getDefaultTheme(),true,false,true,5,.85f,.8f,()->{
            font.drawString("Neutron",x+18,top+18,0xffeef2ff);font.drawString("Adaptive visuals",x+18,top+47,0xffc3cde2);
            UIUtils.drawRound(x+18,top+90,width-36,5,2.5f,new Color(70,85,112));UIUtils.drawRound(x+18,top+90,width*.52f,5,2.5f,new Color(Theme.getDefaultTheme().getPrimaryGradientRGB()));});
        font.drawString("Glowing UI text",x+21,top+165,0xffeef2ff);GuiShaderBloom.beginBatch();GuiShaderBloom.queueRoundedRect(x+width-43,top+163,24,24,12,0xff8f72ed);GuiShaderBloom.flushBatch(2,2);
    }
    private static void begin(){
        mc.getFramebuffer().bindFramebuffer(true);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL20.glUseProgram(0);
        GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);glDisable(GL_SCISSOR_TEST);glDisable(GL_STENCIL_TEST);GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);GlStateManager.disableBlend();glDisable(GL_BLEND);
        GlStateManager.depthMask(true);glDepthMask(true);GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);GL14.glBlendEquation(GL14.GL_FUNC_ADD);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(8f/255,11f/255,18f/255,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);ScaledResolution sr=new ScaledResolution(mc);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glColor4f(1,1,1,1);UiEffects.beginFrame();
    }
    private static void clearMask(){try(RenderState state=new RenderState()){mask.bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);}}
    private static void fillMask(int count){
        clearMask();try(RenderState state=new RenderState()){mask.bindFramebuffer(true);state.screenSpace();GL20.glUseProgram(0);glDisable(GL_TEXTURE_2D);GlStateManager.disableTexture2D();glDisable(GL_BLEND);glColor4f(.65f,.4f,1,1);
            for(int i=0;i<count;i++){float scale=count==1?1:.44f,centerX=count==1?320:80+(i%6)*96,top=count==1?150:105+(i/6)*190;
                glPushMatrix();glTranslatef(centerX,top,0);glScalef(scale,scale,1);quad(-24,0,48,42);quad(-35,46,70,91);quad(-62,52,25,95);quad(37,52,25,95);quad(-32,137,29,73);quad(3,137,29,73);glPopMatrix();}
        }
    }
    private static void quad(float x,float y,float width,float height){glBegin(GL_QUADS);glVertex2f(x,y);glVertex2f(x+width,y);glVertex2f(x+width,y+height);glVertex2f(x,y+height);glEnd();}
    private static void resize(int width,int height,int scale){mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(width,height);mask.deleteFramebuffer();mask=new Framebuffer(width,height,false);begin();}
    private static void advance(double seconds){int frames=Math.max(1,(int)Math.ceil(seconds/.05));for(int i=0;i<frames;i++){CLOCK.addAndGet(Math.round(seconds*1e9/frames));UiEffects.beginFrame();}}
    private static BufferedImage capture(){
        require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==mc.getFramebuffer().framebufferObject,"Capture is not on the Minecraft FBO");glFinish();ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int p=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-1-y,(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|pixels.get(p+2)&255);}return image;
    }
    private static int delta(int rgb){return Math.max(Math.abs((rgb>>16&255)-8),Math.max(Math.abs((rgb>>8&255)-11),Math.abs((rgb&255)-18)));}
    private static int changed(BufferedImage image,int x,int y,int width,int height){int result=0;for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+height);py++)for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+width);px++)if(delta(image.getRGB(px,py))>5)result++;return result;}
    private static double energy(BufferedImage image,int x,int y,int width,int height){double result=0;for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+height);py++)for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+width);px++)result+=delta(image.getRGB(px,py));return result;}
    private static int whitePixels(BufferedImage image,int x,int y,int width,int height){
        int result=0;for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+height);py++)for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+width);px++){int rgb=image.getRGB(px,py),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(Math.min(r,Math.min(g,b))>=185&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<55)result++;}return result;
    }
    private static int coloredPixels(BufferedImage image,int x,int y,int width,int height){
        int result=0;for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+height);py++)for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+width);px++){int rgb=image.getRGB(px,py),min=Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255)),max=Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255));if(max>40&&max-min>20)result++;}return result;
    }
    private static int differences(BufferedImage a,BufferedImage b){require(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"Image dimensions differ");int result=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))result++;return result;}
    private static List<Integer> contourPeaks(BufferedImage image,int edge){
        double[] profile=new double[110];for(int i=0;i<profile.length;i++)for(int y=208;y<=258;y++)profile[i]+=delta(image.getRGB(edge-4+i,y))/51.0;
        List<Integer> peaks=new ArrayList<>();for(int i=1;i<profile.length-1;i++)if(profile[i]>15&&profile[i]>profile[i-1]&&profile[i]>=profile[i+1]){if(peaks.isEmpty()||i-peaks.get(peaks.size()-1)>=2)peaks.add(i);}return peaks;
    }
    private static int span(List<Integer> values){return values.get(values.size()-1)-values.get(0);}
    private static double centroidX(BufferedImage image,int x,int y,int width,int height){double total=0,weighted=0;for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+height);py++)for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+width);px++){double weight=Math.max(0,delta(image.getRGB(px,py))-5);total+=weight;weighted+=weight*px;}return total==0?Double.NaN:weighted/total;}
    private static List<Integer> ownedTextures(Object owner)throws Exception{
        List<Integer> ids=new ArrayList<>();for(Field field:owner.getClass().getDeclaredFields()){field.setAccessible(true);Object value=field.get(owner);if(value instanceof Framebuffer&&((Framebuffer)value).framebufferTexture>0)ids.add(((Framebuffer)value).framebufferTexture);if(value instanceof Framebuffer[])for(Framebuffer f:(Framebuffer[])value)if(f!=null&&f.framebufferTexture>0)ids.add(f.framebufferTexture);}return ids;
    }
    private static void measurement(String scene,String mode,double[] submission,double[] completed,long draws){JsonObject value=new JsonObject();value.addProperty("scene",scene);value.addProperty("mode",mode);value.addProperty("width",mc.displayWidth);value.addProperty("height",mc.displayHeight);value.addProperty("shared_mask_shader_draws",draws);value.add("submission_ms",distribution(submission));value.add("completed_ms",distribution(completed));SAMPLES.add(value);System.out.println("SHADER_MODE_TIMING "+value);}
    private static JsonObject distribution(double[] values){JsonObject result=new JsonObject();JsonArray samples=new JsonArray();for(double value:values)samples.add(new JsonPrimitive(value));result.add("samples",samples);double[] sorted=values.clone();Arrays.sort(sorted);result.addProperty("p50",sorted[(int)Math.ceil(sorted.length*.5)-1]);result.addProperty("p95",sorted[(int)Math.ceil(sorted.length*.95)-1]);return result;}
    private static void save(BufferedImage image,String name)throws Exception{ImageIO.write(image,"png",new File(OUT,name));}
    private static void gallery(String caption,BufferedImage image){CAPTIONS.add(caption);GALLERY.add(image);}
    private static void contactSheet()throws Exception{
        BufferedImage sheet=new BufferedImage(960,765,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();try{g.setColor(new Color(8,11,18));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());for(int i=0;i<GALLERY.size();i++){int x=(i%3)*320,y=(i/3)*255;g.setColor(new Color(229,234,245));g.drawString(CAPTIONS.get(i),x+8,y+14);g.drawImage(GALLERY.get(i),x,y+15,320,240,null);}}finally{g.dispose();}save(sheet,"modes-overview.png");
    }
    private static void gif(List<BufferedImage> frames,String name)throws Exception{
        ImageWriter writer=ImageIO.getImageWritersBySuffix("gif").next();try(ImageOutputStream output=ImageIO.createImageOutputStream(new File(OUT,name))){writer.setOutput(output);writer.prepareWriteSequence(null);
            for(int i=0;i<frames.size();i++){BufferedImage frame=frames.get(i);IIOMetadata metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),writer.getDefaultWriteParam());String format=metadata.getNativeMetadataFormatName();IIOMetadataNode root=(IIOMetadataNode)metadata.getAsTree(format),control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod","none");control.setAttribute("userInputFlag","FALSE");control.setAttribute("transparentColorFlag","FALSE");control.setAttribute("delayTime","10");control.setAttribute("transparentColorIndex","0");
                if(i==0){IIOMetadataNode extensions=new IIOMetadataNode("ApplicationExtensions"),loop=new IIOMetadataNode("ApplicationExtension");loop.setAttribute("applicationID","NETSCAPE");loop.setAttribute("authenticationCode","2.0");loop.setUserObject(new byte[]{1,0,0});extensions.appendChild(loop);root.appendChild(extensions);}metadata.setFromTree(format,root);writer.writeToSequence(new IIOImage(frame,null,metadata),writer.getDefaultWriteParam());}writer.endWriteSequence();
        }finally{writer.dispose();}
    }
    private static void writeReport()throws Exception{Files.write(new File(OUT,"shader-modes.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT).getBytes(StandardCharsets.UTF_8));}
    private static void release(){if(contours!=null)contours.close();if(surface!=null)surface.close();GuiShaderBloom.releaseResources();ArrayListSurface.releaseResources();TextEffects.releaseResources();UIUtils.releaseResources();GaussianBlur.releaseResources();for(ShaderUtil shader:shaders)if(shader!=null)shader.close();if(fonts!=null)fonts.close();if(mask!=null)mask.deleteFramebuffer();if(mc!=null&&mc.getFramebuffer()!=null)mc.getFramebuffer().deleteFramebuffer();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class State{
        final int target=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),program=glGetInteger(GL20.GL_CURRENT_PROGRAM),active=glGetInteger(GL13.GL_ACTIVE_TEXTURE),texture=glGetInteger(GL_TEXTURE_BINDING_2D),equation=glGetInteger(GL14.GL_BLEND_EQUATION),source=glGetInteger(GL14.GL_BLEND_SRC_RGB),destination=glGetInteger(GL14.GL_BLEND_DST_RGB),depthFunction=glGetInteger(GL_DEPTH_FUNC);
        final boolean blend=glIsEnabled(GL_BLEND),depth=glIsEnabled(GL_DEPTH_TEST),scissor=glIsEnabled(GL_SCISSOR_TEST),depthWrite=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int[] viewport=ints(GL_VIEWPORT),clip=ints(GL_SCISSOR_BOX);
        void assertSame(State other){require(target==other.target&&program==other.program&&active==other.active&&texture==other.texture&&equation==other.equation&&source==other.source&&destination==other.destination&&depthFunction==other.depthFunction,"Contour leaked FBO/program/texture/blend/depth configuration");require(blend==other.blend&&depth==other.depth&&scissor==other.scissor&&depthWrite==other.depthWrite&&Arrays.equals(viewport,other.viewport)&&Arrays.equals(clip,other.clip),"Contour leaked enabled state, viewport or clipping");}
        static int[] ints(int parameter){IntBuffer values=BufferUtils.createIntBuffer(16);glGetInteger(parameter,values);return new int[]{values.get(0),values.get(1),values.get(2),values.get(3)};}
    }
}
