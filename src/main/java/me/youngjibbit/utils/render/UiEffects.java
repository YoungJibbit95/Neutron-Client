package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.impl.*;
import java.util.function.LongSupplier;

import java.awt.Color;

public final class UiEffects {

    public enum Channel { TEXT, BORDER, GLOW }
    private static LongSupplier clock=System::nanoTime;
    private static long previous=-1;
    private static boolean clockStarted;
    private static double elapsed;
    private static final double[] phases=new double[3];
    private static boolean lastSync=true;
    private static ClientSettings cachedOwner;
    private static Options cached;
    public static void invalidate(){cached=null;}
    public static Options options(){ClientSettings c=ClientSettings.current();if(cached==null||cachedOwner!=c){cachedOwner=c;cached=new Options(c);}return cached;}
    public static void beginFrame(){
        Options o=options();long now=clock.getAsLong();double dt=0;
        if(!clockStarted){previous=now;clockStarted=true;}
        else if(now>=previous){dt=Math.max(0,Math.min(.1,(now-previous)/1e9));previous=now;}
        if(lastSync!=o.sync){phases[1]=phases[2]=phases[0];lastSync=o.sync;}
        if(o.motion&&!o.reduced&&o.ambient){elapsed+=dt;Theme theme=Theme.getDefaultTheme();for(Channel c:Channel.values()){int i=c.ordinal();Gradient g=gradient(theme,c);if(g.mode>=2)phases[i]=(phases[i]+dt*g.speed)%1;}}
    }
    static void timeSource(LongSupplier source){clock=source;previous=-1;clockStarted=false;elapsed=0;java.util.Arrays.fill(phases,0);lastSync=true;}
    public static float time(){return (float)elapsed;}
    public static boolean motion(){Options o=options();return o.motion&&!o.reduced;}
    public enum SemanticRole { INFO, WARNING, ERROR, SUCCESS }
    /** Explicit UI roles only; critical errors keep their ink even when decoration follows the theme. */
    public static int semanticColor(SemanticRole role,int decorative,boolean priority){
        int color=role==SemanticRole.ERROR?0xffff667f:role==SemanticRole.WARNING?0xffffc969:role==SemanticRole.SUCCESS?0xff65e0ad:0xff71caff;
        return priority||role==SemanticRole.ERROR?color:Theme.interpolateRGB(color,decorative|0xff000000,.35f);
    }
    public static float response(float dt,float rate){return motion()?1-(float)Math.exp(-Math.max(0,dt)*rate):1;}
    public static float radius(float requested,float w,float h){
        float limit=Math.max(0,Math.min(w,h)/2);if(requested<=0)return 0;
        // Data strips and explicitly circular/capsule controls retain their characteristic shape.
        if(requested>=limit)return Math.min(requested,limit);
        return Math.min(limit,options().configured?options().roundness:requested);
    }
    public static boolean effectsSupported(){return net.minecraft.client.renderer.OpenGlHelper.shadersSupported&&net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled();}
    public static boolean blurEnabled(){return options().blur;}
    public static boolean glowEnabled(){return options().glow;}
    public static float glowAlpha(float local){return local*options().glowIntensity;}
    public static float glowRadius(float local){return options().configured?options().glowRadius:local;}
    public static float blurRadius(float local){return options().configured?options().blurStrength:local;}
    public static boolean glass(){return options().glass;}
    public static Gradient gradient(Theme theme,Channel channel){
        Options o=options();ClientSettings c=cachedOwner;int i=o.sync?0:channel.ordinal();
        String direction=c==null?"Theme":o.sync?c.direction.getValue():channel==Channel.TEXT?c.textDirection.getValue():channel==Channel.BORDER?c.borderDirection.getValue():c.glowDirection.getValue();
        String motion=c==null?"Theme":o.sync?c.gradientMotion.getValue():channel==Channel.TEXT?c.textMotion.getValue():channel==Channel.BORDER?c.borderMotion.getValue():c.glowMotion.getValue();
        float speed=c==null?theme.getAnimationSpeed():o.sync?c.gradientSpeed.getValue().floatValue():channel==Channel.TEXT?c.textSpeed.getValue().floatValue():channel==Channel.BORDER?c.borderSpeed.getValue().floatValue():c.glowSpeed.getValue().floatValue();
        if(motion.equals("Theme"))speed=theme.getAnimationSpeed();
        int axis=direction.equals("Theme")?(theme.isVertical()?2:1):direction.equals("Vertical")?2:direction.equals("Horizontal")?1:direction.equals("Up")?-2:-1;
        int mode=motion.equals("Solid")?0:motion.equals("Animated")||motion.equals("Theme")&&theme.isAnimated()?2:1;
        if(mode==2&&c!=null)mode+=c.gradientMethod.getSelectedIndex();
        float phase=(float)phases[i];
        // A Local custom theme can animate even when the selected Global preset is static.
        if(motion.equals("Theme")&&theme!=Theme.getDefaultTheme())phase=(float)((elapsed*speed)%1);
        return new Gradient(axis,mode,phase,speed);
    }
    /** Shared fragment coordinates are physical viewport coordinates, including transformed GUIs. */
    public static void upload(ShaderUtil shader,Theme theme){upload(shader,theme,scene()==null?null:scene().policy.gradient);}
    public static Theme theme(Theme requested){return scene()==null?Theme.resolve(requested):scene().theme;}
    public static void upload(ShaderUtil shader,Theme theme,Gradient override){
        options();theme=Theme.resolve(theme);
        palette(shader,"uiAccentColor",theme.getAccentColor());
        shader.setUniformi("uiAccentBorder",cachedOwner!=null&&cachedOwner.borderColor.getValue().equals("Accent")?1:0);
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        shader.setUniformi("uiEnabled",options().configured?1:0);
        shader.setUniformf("uiResolution",mc.displayWidth,mc.displayHeight);
        for(Channel channel:Channel.values()){
            Gradient g=override==null?gradient(theme,channel):override;String n=channel==Channel.TEXT?"uiText":channel==Channel.BORDER?"uiBorder":"uiGlow";
            shader.setUniformf(n,g.axis,options().gradients?g.mode:0,g.phase);
        }
    }
    public static int gradientColor(Theme theme,Channel channel,float x,float y){
        theme=theme(theme);
        net.minecraft.client.gui.ScaledResolution sr=new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        Gradient g=scene()!=null&&scene().policy.gradient!=null?scene().policy.gradient:gradient(theme,channel);return Theme.interpolateRGB(theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),options().gradients?g.position(x,y,sr.getScaledWidth(),sr.getScaledHeight()):0);
    }
    public static final class Gradient {
        public final int axis,mode;public final float phase,speed;
        Gradient(int axis,int mode,float phase,float speed){this.axis=axis;this.mode=mode;this.phase=phase;this.speed=speed;}
        public float position(float x,float y,float w,float h){
            float t=Math.abs(axis)==2?y/Math.max(1,h):x/Math.max(1,w);if(axis<0)t=1-t;
            if(mode==0)return 0;
            if(mode==2)return .5f-.5f*(float)Math.cos(2*Math.PI*(t-phase));
            if(mode==3){float travel=.5f-.5f*(float)Math.cos(2*Math.PI*phase);return .5f-.5f*(float)Math.cos(2*Math.PI*(t-travel));}
            if(mode==4)return .5f+(Math.max(0,Math.min(1,t))-.5f)*(float)Math.cos(2*Math.PI*phase);
            return Math.max(0,Math.min(1,t));
        }
    }
    public static final class Options {
        public final boolean configured,blur,kawase,glow,glass,motion,reduced,gradients,sync,border,glassClear;
        public final boolean ambient;
        public final int substance;
        public final float surfaceOpacity,gradientFillStrength,borderOpacity;
        public final float roundness,blurStrength,glowIntensity,glowRadius,refraction,tint,edge;
        public final int quality;
        Options(ClientSettings c){ambient=c==null||c.ambientMotion.getValue();glassClear=c!=null&&c.glassAppearance.getValue().equals("Clear");substance=c==null?-1:style(c.surface.getValue());surfaceOpacity=c==null?1:c.surfaceOpacity.getValue().floatValue();gradientFillStrength=c==null?.28f:c.gradientFillStrength.getValue().floatValue();border=c==null||c.border.getValue();borderOpacity=c==null?.8f:c.borderOpacity.getValue().floatValue();configured=c!=null;blur=c==null||c.blur.getValue();kawase=c!=null&&c.blurMethod.getValue().equals("Kawase");glow=c==null||c.glow.getValue();glass=c!=null&&c.surface.getValue().equals("Liquid Glass");motion=c==null||c.animations.getValue();reduced=c!=null&&c.reducedMotion.getValue();gradients=c==null||c.gradients.getValue();sync=c==null||c.synchronize.getValue();roundness=c==null?5:c.roundness.getValue().floatValue();blurStrength=c==null?5:c.blurStrength.getValue().floatValue();glowIntensity=c==null?1:c.glowIntensity.getValue().floatValue();glowRadius=c==null?9:c.glowRadius.getValue().floatValue();quality=c==null?2:c.blurQuality.getValue().intValue();refraction=c==null?.35f:c.glassRefraction.getValue().floatValue();tint=c==null?.18f:c.glassTint.getValue().floatValue();edge=c==null?.3f:c.glassLight.getValue().floatValue();}
    }

    /** The existing Surface Style ID keeps its legacy value for config compatibility. */
    public static int style(String name){return "Clear".equals(name)?0:"Gradient".equals(name)?1:"Blur".equals(name)?2:"Liquid Glass".equals(name)?3:-1;}
    public static final class LocalSurface {
        public final ComboValue source,substance;
        private final me.youngjibbit.module.Module owner;
        private float phase,lastTime;
        public LocalSurface(me.youngjibbit.module.Module owner){
            this.owner=owner;source=new ComboValue("Effects Source",owner,new String[]{"Global","Local","Clear","Gradient","Blur","Liquid Glass"},0);
            substance=new ComboValue("Local Rectangle Substance",owner,new String[]{"Legacy","Clear","Gradient","Blur","Liquid Glass"},0);substance.setVisibility(m->local());
            for(me.youngjibbit.value.Value<?> value:owner.getValues())if(java.util.Arrays.asList("background","blur","glow","bloom","blur_strength","gradient_speed","theme").contains(value.getId())){
                java.util.function.Predicate<me.youngjibbit.module.Module> before=value.getVisibility();value.setVisibility(m->local()&&(before==null||before.test(m)));
            }
        }
        public boolean local(){return source.getValue().equals("Local");}
        Theme theme(Theme requested){
            if(!local())return Theme.getDefaultTheme();
            me.youngjibbit.value.Value<?> choice=owner.getValueByName("theme");
            if(choice instanceof ComboValue){Theme selected=me.youngjibbit.theme.ThemeCatalog.global().find(((ComboValue)choice).getValue());if(selected!=null)return selected;}
            return Theme.resolve(requested);
        }
        Gradient gradient(Theme theme){
            me.youngjibbit.value.Value<?> speed=owner.getValueByName("gradient_speed");float time=time(),dt=Math.max(0,time-lastTime);lastTime=time;
            if(!local()||!substance.getValue().equals("Legacy")||!(speed instanceof SliderValue))return null;
            float rate=1000/Math.max(1,((SliderValue)speed).getValue().floatValue());if(motion())phase=(phase+dt*rate)%1;
            return new Gradient(UiEffects.gradient(theme,Channel.BORDER).axis,2,phase,rate);
        }
    }
    /** All surface inheritance is resolved here; consumers keep geometry and content only. */
    public static final class SurfacePolicy {
        public final int style;public final boolean background,blur,glow;public final float opacity,outline,fillStrength;public final Gradient gradient;
        SurfacePolicy(SurfacePolicy base,int style){this.style=style;this.background=base.background;this.blur=base.blur;this.glow=base.glow;this.opacity=base.opacity;this.outline=base.outline;this.fillStrength=base.fillStrength;this.gradient=base.gradient;}
        SurfacePolicy(LocalSurface local,Theme theme,boolean background,boolean blur,boolean glow,float outline,int forced){
            Options o=options();boolean global=(o.configured||local!=null&&UiEffects.style(local.source.getValue())>=0)&&forced<0&&(local==null||!local.local());
            int selected=forced>=0?(forced==1?3:0):local!=null&&local.local()?UiEffects.style(local.substance.getValue()):local!=null&&!local.source.getValue().equals("Global")?UiEffects.style(local.source.getValue()):o.substance;
            this.style=selected;
            this.background=global&&local!=null||background;
            this.blur=o.blur&&(selected<0?blur:selected>=2&&(global&&local!=null||blur));
            this.glow=o.glow&&(global&&local!=null||glow);
            this.outline=o.border?(global&&local!=null?o.borderOpacity:outline*o.borderOpacity/.8f):0;
            this.opacity=o.surfaceOpacity;this.fillStrength=o.gradientFillStrength;
            this.gradient=local==null?null:local.gradient(theme);
        }
    }
    public static SurfacePolicy surface(LocalSurface local,Theme theme,boolean background,boolean blur,boolean glow,float outline,int forced){return new SurfacePolicy(local,theme,background,blur,glow,outline,forced);}

    /** Read-only scene sources are inherited through nested controls; child fills never sample prior UI content. */
    private static final java.util.Deque<SceneContext> scenes=new java.util.ArrayDeque<>();
    public static final class SceneContext implements AutoCloseable {
        public final int clean,soft;public final boolean blurred,replacing;public final float radius;public final Theme theme;public final SurfacePolicy policy;
        SceneContext(int clean,int soft,boolean blurred,Theme theme,SurfacePolicy policy,boolean replacing,float radius){this.replacing=replacing;this.radius=radius;this.clean=clean;this.soft=soft;this.blurred=blurred;this.theme=theme;this.policy=policy;scenes.push(this);}
        @Override public void close(){if(scenes.peek()!=this)throw new IllegalStateException("Surface context order");scenes.pop();}
    }
    public static SceneContext scene(){return scenes.peek();}
    public static SceneContext scene(int clean,int soft,boolean blurred,Theme theme,SurfacePolicy policy){return scene(clean,soft,blurred,theme,policy,true,blurRadius(5));}
    public static SceneContext scene(int clean,int soft,boolean blurred,Theme theme,SurfacePolicy policy,boolean replacing,float radius){return new SceneContext(clean,soft,blurred,theme,policy,replacing,radius);}
    public static void independentScene(Runnable draw){
        java.util.Deque<SceneContext> saved=new java.util.ArrayDeque<>(scenes);scenes.clear();try{draw.run();}finally{scenes.clear();scenes.addAll(saved);}
    }
    public static boolean material(){return effectsSupported()&&scene()!=null&&scene().clean>0&&scene().policy.style>=0&&!Blur.isCapturing()&&!GuiShaderBloom.isCapturing();}
    public static void uploadMaterial(ShaderUtil shader,boolean eligible){
        SceneContext c=scene();boolean active=eligible&&material();shader.setUniformi("uiMaterialEnabled",active?1:0);if(!active)return;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        shader.setUniformi("uiCleanScene",3);shader.setUniformi("uiSoftScene",2);shader.setUniformi("uiSubstance",c.policy.style);shader.setUniformf("uiSceneResolution",mc.displayWidth,mc.displayHeight);
        shader.setUniformf("uiSceneScale",new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor());shader.setUniformf("uiOpacity",c.policy.opacity);shader.setUniformf("uiGradientStrength",c.policy.fillStrength);shader.setUniformf("uiGlass",options().refraction,options().tint,options().edge);shader.setUniformi("uiSceneBlurred",c.blurred?1:0);shader.setUniformi("uiGlassClear",options().glassClear?1:0);shader.setUniformf("uiGlassInk",ink(c.theme));
        palette(shader,"uiFillFirst",c.theme.getPrimaryGradientColor());palette(shader,"uiFillSecond",c.theme.getSecondaryGradientColor());palette(shader,"uiFillBackground",c.theme.getBackground());
        bindScene(3,c.clean);bindScene(2,c.soft);net.minecraft.client.renderer.GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
    }
    public static float ink(Theme theme){Color c=theme.getTextColor();return (.2126f*c.getRed()+.7152f*c.getGreen()+.0722f*c.getBlue())/255f;}
    private static void palette(ShaderUtil shader,String name,Color color){shader.setUniformf(name,color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,color.getAlpha()/255f);}
    private static void bindScene(int unit,int texture){net.minecraft.client.renderer.GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);net.minecraft.client.renderer.GlStateManager.bindTexture(texture);org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,texture);}

    private UiEffects() {
    }

    public static int alpha(int color, float alphaFactor) {
        float clamped = clamp01(alphaFactor);
        int alpha = (int) (((color >>> 24) & 0xFF) * clamped);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static String settingLabel(me.youngjibbit.value.Value<?> value){return value.getId().equals("surface_style")?"Rectangle Substance":value.getId().equals("effects_source")?"Rectangle Substance":value.getName();}
    public static String settingOption(me.youngjibbit.value.Value<?> value,String option){if(value.getId().equals("effects_source")){if(option.equals("Global")){ClientSettings c=ClientSettings.current();return "Global: "+(c==null?"Blur":c.surface.getValue());}if(option.equals("Local"))return "Local (advanced)";}if(value instanceof me.youngjibbit.theme.ThemeSelectionValue)return me.youngjibbit.theme.ThemeSelectionValue.label(option);return value.getId().equals("surface_style")&&option.equals("Standard")?"Legacy (retained)":option;}
    public static int gradientAt(Theme theme,Channel channel,float x,float y){float[] p=ScreenRect.project(x,y,1,1);return gradientColor(theme,channel,p[0],p[1]);}
    /** UI accent opacity is independent of the theme stop alpha, applied once by the shared shader. */
    public static Color accent(int color,float opacity){return new Color((color&0xffffff)|(Math.round(Math.max(0,Math.min(1,opacity))*255)<<24),true);}
    public static Color color(int color, float alphaFactor) {
        return new Color(alpha(color, alphaFactor), true);
    }

    public static void drawGlow(float x, float y, float width, float height, int radius, int color, float alphaFactor, int iterations, int offset) {
        if (!effectsSupported() || !glowEnabled() || width <= 0.0f || height <= 0.0f) {
            return;
        }

        int glowColor = accent(color, alphaFactor).getRGB();
        if (GuiShaderBloom.isBatching()) {
            GuiShaderBloom.queueRoundedRect(x, y, width, height, radius, glowColor);
        } else {
            GuiShaderBloom.renderRect(x, y, width, height, radius, glowColor, iterations, offset);
        }
    }

    public static void drawBlurredRect(float x, float y, float width, float height, float radius, Color fill, int blurRadius, int blurPasses) {
        if(!effectsSupported()||width<=0||height<=0)return;
        if(material()){UIUtils.drawSceneRound(x,y,width,height,radius);return;}
        if(!blurEnabled()&&!glass())return;
        SceneContext inherited=scene();
        if(inherited!=null&&inherited.clean>0&&inherited.blurred){try(SceneContext mask=scene(inherited.clean,inherited.soft,true,inherited.theme,new SurfacePolicy(inherited.policy,4),inherited.replacing,inherited.radius)){UIUtils.drawSceneRound(x,y,width,height,radius);}return;}
        Blur.startBlur();
        UIUtils.drawRound(x, y, width, height, radius, fill);
        Blur.endBlur(Math.max(1, blurRadius), Math.max(1, blurPasses));
    }

    public static void drawPanelFill(float x,float y,float width,float height,float radius,Color legacyFill,int legacyRadius){
        if(material())UIUtils.drawSceneRound(x,y,width,height,radius);
        else{drawBlurredRect(x,y,width,height,radius,Color.WHITE,legacyRadius,1);UIUtils.drawRound(x,y,width,height,radius,legacyFill);}
    }
    public static void drawThemedPanel(float x, float y, float width, float height, float radius, Theme theme, int fillAlpha, float glowAlpha, boolean blur) {
        Theme resolvedTheme = Theme.resolve(theme);
        int pulse = gradientAt(resolvedTheme,Channel.BORDER,x,y);


        if (blur) {
            drawBlurredRect(x, y, width, height, radius, new Color(0, 0, 0, Math.min(180, fillAlpha + 45)), 6, 2);
        }

        drawGlow(x - 1.0f, y - 1.0f, width + 2.0f, height + 2.0f, (int) radius, pulse, glowAlpha*(resolvedTheme.isCustom()?resolvedTheme.getGlowStrength():1), 2, resolvedTheme.isCustom()?Math.max(1,Math.round(resolvedTheme.getGlowRadius()/4)):2);

        UIUtils.drawRound(x, y, width, height, radius, true, resolvedTheme.isCustom()?resolvedTheme.getBackground():new Color(0, 0, 0, fillAlpha));
        UIUtils.drawRoundOutline(x, y, width, height, radius, resolvedTheme.isCustom()?resolvedTheme.getOutlineWidth():0.7f, new Color(0, 0, 0, 1), accent(resolvedTheme.isCustom()?resolvedTheme.getAccentColor().getRGB():pulse, resolvedTheme.isCustom()?resolvedTheme.getOutlineStrength():0.68f));
    }

    public static void drawAccentBar(float x, float y, float width, float height, float radius, Theme theme, float alphaFactor) {
        Theme resolvedTheme = Theme.resolve(theme);
        Color first = color(resolvedTheme.getPrimaryGradientRGB(), alphaFactor);
        Color second = color(resolvedTheme.getSecondaryGradientRGB(), alphaFactor);
        UIUtils.drawGradientHorizontal(x, y, width, height, radius, first, second);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
