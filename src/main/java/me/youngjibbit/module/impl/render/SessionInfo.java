package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.ShaderUtil;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Session Info", category = Category.Render)
public class SessionInfo extends Module {
    private final ComboValue mode = new ComboValue("Mode",this,new String[]{"Basic","Outline","Tenacity","Rue (Rise)","Modern","Creative"},1);
    private final CheckBoxValue bg = new CheckBoxValue("Background",this,true);
    private final CheckBoxValue bloom = new CheckBoxValue("Bloom",this,true);
    private final CheckBoxValue blurbg = new CheckBoxValue("Blur",this,false);
    private final SliderValue blurStrength = new SliderValue("Blur Strength",this,3.0,1,10,true);
    private final SliderValue gradientSpeed = new SliderValue("Gradient Speed",this,5000.0,1000,10000,true);
    private static final SessionInfoLifecycle LIFECYCLE = new SessionInfoLifecycle();
    private static final long ANIMATION_EPOCH = System.nanoTime();
    private final SessionStatistics statistics;
    private final ArrayListSurface surface = new ArrayListSurface(this);
    private final SliderValue positionX=new SliderValue("Position X",this,50.0,-16384,16384);
    private final SliderValue positionY=new SliderValue("Position Y",this,40.0,-16384,16384);
    private double lastPositionX=50,lastPositionY=40;
    private final CompactHudMotion killChange=new CompactHudMotion(),deathChange=new CompactHudMotion(),stateChange=new CompactHudMotion();
    private long lastMotionFrame;
    private void resetMotion(){killChange.reset();deathChange.reset();stateChange.reset();lastMotionFrame=0;}
    @Override public void onValueChange(){if(positionX!=null&&positionX.getValue()!=lastPositionX){lastPositionX=positionX.getValue();x=(float)lastPositionX;}if(positionY!=null&&positionY.getValue()!=lastPositionY){lastPositionY=positionY.getValue();y=(float)lastPositionY;}}
    public void resetPosition(){positionX.reset();positionY.reset();x=50;y=40;dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);}

    private float x=50,y=40,width=150,height=70;
    private boolean dragging,previousButton;
    private float grabX,grabY;

    public SessionInfo() { this(LIFECYCLE.statistics); LIFECYCLE.install(); }
    SessionInfo(SessionStatistics statistics) { this.statistics=statistics; }

    @Override public void onEnable() {
        resetMotion();dragging=false;
        previousButton=Mouse.isCreated() && Mouse.isButtonDown(0);
        super.onEnable();
    }
    @Override public void onDisable() {
        resetMotion();dragging=false;
        me.youngjibbit.utils.render.HudDrag.cancel(this);
        surface.close();
        super.onDisable();
    }

    @Subscribe private final Listener<Event2D> render = new Listener<>(event -> {
        if (isToggled()) renderSession();
    });
    @Subscribe private final Listener<TickEvent> editorContext = new Listener<>(event -> {
        boolean button=Mouse.isCreated()&&Mouse.isButtonDown(0);
        if (!(mc.currentScreen instanceof GuiChat) || !button) dragging=false;
        if (!(mc.currentScreen instanceof GuiChat)) previousButton=button;
    });

    private void renderSession() {
        if(!me.youngjibbit.module.impl.client.HUD.visible()){dragging=false;me.youngjibbit.utils.render.HudDrag.cancel(this);surface.close();return;}
        if (mc.gameSettings.showDebugInfo) { dragging=false; previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0); return; }
        ScaledResolution sr=new ScaledResolution(mc);
        Frame frame=layout(statistics.snapshot(),mc.getSession().getUsername(),sr.getScaledWidth(),sr.getScaledHeight());
        int mx=Mouse.isCreated()?Mouse.getX()/sr.getScaleFactor():-1;
        int my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/sr.getScaleFactor():-1;
        interact(mc.currentScreen instanceof GuiChat,Mouse.isCreated()&&Mouse.isButtonDown(0),mx,my,frame,sr.getScaledWidth(),sr.getScaledHeight());
        frame.x=x;frame.y=y;
        paint(frame);
    }

    /** Only the historic default is overlaid by 2.0; explicit saved variants remain. */
    static String modernFor(String saved,boolean globalModern){
        if(globalModern){
            if("Outline".equals(saved))return "Neutron 2.0";
            if("Modern".equals(saved)||"Creative".equals(saved))return saved; // 2.0 modes stay as-is
        }
        return saved;
    }
    Frame layout(SessionStatistics.Snapshot snapshot,String username,int screenWidth,int screenHeight) {
        String design=modernFor(mode.getValue(),ThemeMode.APPROACH_TWO.equals(ThemeMode.selected()));
        if(compact(design))return compactLayout(snapshot,username,screenWidth,screenHeight,design);
        boolean modern="Neutron 2.0".equals(design)||"Modern".equals(design);
        TTFFontRenderer body=font("Product-Sans-Bold 20");
        TTFFontRenderer title=font("Poppins-Bold 23");
        String time="Time: "+snapshot.time(), counts="Kills: "+snapshot.kills+"   Deaths: "+snapshot.deaths;
        boolean tenacity="Tenacity".equals(design), outline="Outline".equals(design), rue="Rue (Rise)".equals(design);
        float desired=Math.max(150,Math.max(body.getWidth(time),
                Math.max(body.getWidth(username),body.getWidth(counts)))+36);
        float radius=modern?13:rue?14:UiEffects.radius(5,width,height);
        height=modern?100:rue?80:tenacity?82:outline?70:76;
        if(modern)desired=Math.max(250,Math.min(330,
                Math.max(body.getWidth(username),body.getWidth(time))+116));
        if (tenacity) desired=Math.max(desired,Math.max(body.getWidth(username),Math.max(body.getWidth(counts),body.getWidth(snapshot.time())))+112);
        width=Math.min(Math.max(1,screenWidth-8),desired);
        height=Math.min(Math.max(1,screenHeight-8),height);
        clamp(screenWidth,screenHeight);
        Frame frame=new Frame(x,y,width,height,radius,design,snapshot,Theme.resolve(Theme.getDefaultTheme()),
                bg.getValue(),blurbg.getValue(),bloom.getValue(),blurStrength.getValue().floatValue(),
                (float)((System.nanoTime()-ANIMATION_EPOCH)/(gradientSpeed.getValue()*1_000_000.0)%1));
        float contentWidth=width-32;
        if(modern){
            float leftWidth=Math.max(0,width-98);
            frame.add("Statistics",title,12,4,false);
            frame.add(fit(username,body,leftWidth),body,14,28,false);
            frame.add(fit("Kills: "+snapshot.kills+"   D: "+snapshot.deaths,body,leftWidth),body,14,49,false);
            frame.add(fit((snapshot.active?"Time: ":"Ended: ")+snapshot.time(),body,leftWidth),body,14,70,false);
            frame.ring=true;
        }else if (tenacity) {
            frame.add("Statistics",title,width/2-title.getWidth("Statistics")/2,4,true);
            float leftWidth=Math.max(0,width-108);
            frame.add(fit(username,body,leftWidth),body,22,26,false);
            frame.add(fit("Kills: "+snapshot.kills+"  D: "+snapshot.deaths,body,leftWidth),body,22,44,false);
            frame.add(fit(snapshot.time(),body,leftWidth+10),body,10,62,false);
            frame.icon("user",7,27); frame.icon("kills",7,45);
            frame.ring=true;
        } else {
            String heading=outline||rue?"Session Info":"Statistics";
            frame.add(heading,title,width/2-title.getWidth(heading)/2,3,true);
            if (outline) {
                frame.icon("time",3,5);
                frame.icon("time",3,27); frame.icon("user",3,40); frame.icon("kills",3,53);
                frame.add(fit(time,body,contentWidth),body,18,25,true);
                frame.add(fit(username,body,contentWidth),body,18,38,true);
                frame.add(fit(counts,body,contentWidth),body,18,51,true);
            } else {
                frame.icon("user",8,27); frame.icon("time",8,43); frame.icon("kills",8,59);
                frame.add(fit(username,body,contentWidth),body,24,25,rue);
                frame.add(fit(time,body,contentWidth),body,24,41,rue);
                frame.add(fit(counts,body,contentWidth),body,24,57,rue);
            }
        }
        return frame;
    }

    private static boolean compact(String design){return "Modern".equals(design)||"Creative".equals(design)||"Neutron 2.0".equals(design);}

    private Frame compactLayout(SessionStatistics.Snapshot snapshot,String username,int sw,int sh,String design){
        boolean creative="Creative".equals(design);
        width=Math.min(Math.max(1,sw-8),154);height=Math.min(Math.max(1,sh-8),creative?40:42);clamp(sw,sh);
        Frame frame=new Frame(x,y,width,height,Math.min(11,Math.min(width,height)/2),design,snapshot,Theme.resolve(me.youngjibbit.theme.ThemeCatalog.global().current()),
                bg.getValue(),blurbg.getValue(),false,blurStrength.getValue().floatValue(),0);
        me.youngjibbit.module.impl.client.ClientSettings settings=me.youngjibbit.module.impl.client.ClientSettings.current();
        boolean animations=settings==null||settings.animations.getValue(),reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        long now=System.nanoTime();float dt=lastMotionFrame==0||now<=lastMotionFrame?0:Math.min(.05f,(now-lastMotionFrame)/1_000_000_000f);
        lastMotionFrame=now;
        frame.killPulse=killChange.step(snapshot.kills,dt,animations,reduced,intensity);
        frame.deathPulse=deathChange.step(snapshot.deaths,dt,animations,reduced,intensity);
        frame.statePulse=stateChange.step(snapshot.active?1:0,dt,animations,reduced,intensity);
        frame.clockProgress=CompactHudMotion.fraction(snapshot.elapsedNanos,3_600_000_000_000L);
        frame.clockTurn=animations&&!reduced?CompactHudMotion.fraction(snapshot.elapsedNanos,60_000_000_000L):
                CompactHudMotion.fraction(snapshot.elapsedNanos/60_000_000_000L*60_000_000_000L,3_600_000_000_000L);
        TTFFontRenderer small=font("Product-Sans-Regular 12"),value=font("Product-Sans-Bold 14");
        if(creative){
            frame.add(fit(snapshot.clock(),value,49),value,7,25,false);
            frame.add(fit(username,small,76),small,71,4,false);
            frame.add(fit(CompactHudMotion.count(snapshot.kills),value,21),value,84,23-frame.killPulse,false);
            frame.add(fit(CompactHudMotion.count(snapshot.deaths),value,21),value,126,23-frame.deathPulse,false);
        }else{
            frame.add(fit(snapshot.clock(),value,62),value,37,5,false);
            frame.add(fit(username,small,44),small,103,5,false);
            frame.add(fit(CompactHudMotion.count(snapshot.kills),value,35),value,54,25-frame.killPulse,false);
            frame.add(fit(CompactHudMotion.count(snapshot.deaths),value,35),value,109,25-frame.deathPulse,false);
        }
        return frame;
    }

    private void compactContent(Frame frame,UiEffects.SurfacePolicy policy){
        boolean creative="Creative".equals(frame.design);
        CreativeHudLayout.Fit fit=new CreativeHudLayout.Fit(154,creative?40:42,frame.width,frame.height);
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            state.screenSpace();
            try(GuiClip clip=GuiClip.push(new ClipRect(frame.x,frame.y,frame.width,frame.height))){
                GL11.glTranslatef(frame.x+fit.x-frame.x*fit.scale,frame.y+fit.y-frame.y*fit.scale,0);GL11.glScalef(fit.scale,fit.scale,1);
                float x=frame.x,y=frame.y;
                if(creative){
                    compactBlock(frame,0,0,61,40,policy);compactBlock(frame,65,0,89,40,policy);
                }else compactBlock(frame,0,0,154,42,policy);
                if(!creative){
                    UIUtils.drawDecorationRound(x+37,y+21,49,17,7,new Color(107,141,179,20));
                    UIUtils.drawDecorationRound(x+92,y+21,55,17,7,new Color(107,141,179,20));
                }
                CompactHudVisuals.begin(fit.scale);
                int accent=frame.theme.getPrimaryGradientRGB();
                CompactHudVisuals.clock(x+(creative?13:18),y+(creative?12:21),creative?8:12,frame.clockProgress,frame.clockTurn,accent,1);
                float liveX=creative?x+28:x+29,liveY=creative?y+11:y+10;
                int live=frame.snapshot.active?0xff8be5bc:0xff94a3b8;
                CompactHudVisuals.dot(liveX,liveY,1.8f+frame.statePulse*.7f,live,1);
                CompactHudVisuals.swords(x+(creative?72:42),y+(creative?24:25)-frame.killPulse,8+frame.killPulse*.4f,accent,1);
                CompactHudVisuals.skull(x+(creative?114:97),y+(creative?24:25)-frame.deathPulse,8+frame.deathPulse*.4f,0xffb3c3d6,1);
                if(creative)font("Product-Sans-Regular 10").drawString(frame.snapshot.active?"LIVE":"END",x+34,y+7,frame.snapshot.active?0xff8be5bc:0xff94a3b8);
                for(TextRun run:frame.text)run.draw(frame);
            }
        }
    }
    private void compactBlock(Frame frame,float x,float y,float w,float h,UiEffects.SurfacePolicy policy){
        if(policy.blur)UiEffects.drawBlurredRect(frame.x+x,frame.y+y,w,h,11,Color.WHITE,Math.max(1,Math.round(frame.blurRadius)),1);
        if(policy.background)UIUtils.drawDecorationRound(frame.x+x,frame.y+y,w,h,11,new Color(17,23,34,232));
    }

    void paint(Frame frame) {
        if(compact(frame.design)){
            GuiShaderBloom.afterBatch(()->surface.withScene(frame.theme,frame.background,frame.blur,frame.blurRadius,
                    policy->compactContent(frame,policy)));
            return;
        }
        final boolean modern="Neutron 2.0".equals(frame.design)||"Modern".equals(frame.design)||"Creative".equals(frame.design);
        GuiShaderBloom.afterBatch(() -> surface.designedPanel(frame.x,frame.y,frame.width,frame.height,frame.radius,frame.theme,
                frame.background,frame.blur,modern?false:frame.glow,frame.blurRadius,
                modern?.82f:"Outline".equals(frame.design)?0.78f:0.48f,
                modern?.16f:"Outline".equals(frame.design)||"Rue (Rise)".equals(frame.design)?0.9f:0,
                "Tenacity".equals(frame.design),()->content(frame)));
    }

    private void content(Frame frame) {
        try (GuiClip ignored=GuiClip.push(new ClipRect(frame.x+2,frame.y+2,frame.width-4,frame.height-4))) {
            boolean modern="Neutron 2.0".equals(frame.design)||"Modern".equals(frame.design)||"Creative".equals(frame.design);
                if(modern&&frame.background){
                UIUtils.drawRound(frame.x+2,frame.y+2,Math.max(1,frame.width-4),
                        Math.max(1,frame.height-4),Math.max(0,frame.radius-2),
                        new Color(20,22,32,182));
            }
            float ruleY=frame.y+(modern?23:18);
            UIUtils.drawGradientHorizontal(frame.x+(modern?11:1),ruleY,
                    Math.max(0,frame.width-(modern?22:2)),modern?1.2f:1,0,
                    frame.theme.getPrimaryGradientColor(),frame.theme.getSecondaryGradientColor());
            if(frame.ring)ring(frame);
            for(TextRun run:frame.text)if(!run.gradient)run.draw(frame);
            me.youngjibbit.utils.render.TextEffects.direct(frame.theme,()->{
                for(TextRun run:frame.text)if(run.gradient)run.glyphs(frame);
            });
        }
    }

    /** Rising-edge capture only in chat. Held clicks from gameplay cannot arm dragging on chat entry. */
    void interact(boolean chat,boolean button,float mouseX,float mouseY,Frame frame,int screenWidth,int screenHeight) {
        boolean was=dragging;
        dragging=me.youngjibbit.utils.render.HudDrag.capture(this,chat,button,!previousButton,frame.contains(mouseX,mouseY));
        if(dragging&&!was){grabX=mouseX-x;grabY=mouseY-y;}
        if (dragging) { x=me.youngjibbit.module.impl.client.HUD.snap(mouseX-grabX); y=me.youngjibbit.module.impl.client.HUD.snap(mouseY-grabY); clamp(screenWidth,screenHeight);positionX.setValue((double)x);positionY.setValue((double)y); }
        previousButton=button;
    }

    private void clamp(int screenWidth,int screenHeight) {
        if(!me.youngjibbit.module.impl.client.HUD.bounded())return;
        float insetX=Math.min(4,Math.max(0,(screenWidth-width)/2));
        float insetY=Math.min(4,Math.max(0,(screenHeight-height)/2));
        x=Math.max(insetX,Math.min(x,screenWidth-width-insetX));
        y=Math.max(insetY,Math.min(y,screenHeight-height-insetY));
    }

    public float getKillDeathRatio() {
        SessionStatistics.Snapshot snapshot=statistics.snapshot();
        return snapshot.deaths==0?snapshot.kills:(float)snapshot.kills/snapshot.deaths;
    }
    public static long getTimeDiff() { return LIFECYCLE.statistics.snapshot().milliseconds(); }
    public static int[] getPlayTime() {
        long seconds=getTimeDiff()/1000;
        return new int[]{(int)Math.min(Integer.MAX_VALUE,seconds/3600),(int)(seconds/60%60),(int)(seconds%60)};
    }
    public String getSessionLengthString() { return statistics.snapshot().time(); }
    public Theme applyTheme() { return Theme.resolve(Theme.getDefaultTheme()); }

    private static TTFFontRenderer font(String key) { return Pulsar.INSTANCE.getFontManager().getFont(key); }
    private static Color alpha(int rgb,int alpha) { return new Color((rgb&0x00FFFFFF)|(alpha<<24),true); }
    private static String fit(String text,TTFFontRenderer font,float available) {
        if (font.getWidth(text)<=available) return text;
        int end=text.length();
        while (end>0 && font.getWidth(text.substring(0,end)+"...")>available) end=text.offsetByCodePoints(end,-1);
        return end==0?"":text.substring(0,end)+"...";
    }

    private static ShaderUtil ringShader;
    public static void releaseRenderResources(){if(ringShader!=null){ringShader.close();ringShader=null;}}
    private static void ring(Frame frame) {
        ScaledResolution sr=new ScaledResolution(mc); float scale=sr.getScaleFactor();
        float cx=frame.x+frame.width-40,cy=frame.y+49,radius=23;
        if(ringShader==null)ringShader=new ShaderUtil("Flauxy/Shaders/sessionRing.frag");ShaderUtil shader=ringShader;
        shader.init();
        shader.setUniformf("center",cx*scale,mc.displayHeight-cy*scale);
        shader.setUniformf("radius",radius*scale); shader.setUniformf("thickness",1.5f*scale);
        shader.setUniformf("progress",(frame.snapshot.elapsedNanos/1_000_000_000L%3600)/3600f);
        Color accent=frame.theme.getSecondaryGradientColor();
        shader.setUniformf("color",accent.getRed()/255f,accent.getGreen()/255f,accent.getBlue()/255f);
        GL11.glEnable(GL11.GL_BLEND); net.minecraft.client.renderer.GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtil.drawQuads(cx-radius-2,cy-radius-2,radius*2+4,radius*2+4);
        shader.unload();
        TTFFontRenderer timer=font("Product-Sans-Bold 16");
        String clock=fit(frame.snapshot.clock(),timer,44);
        timer.drawString(clock,cx-timer.getWidth(clock)/2,cy-timer.getHeight(clock)/2,frame.theme.isCustom()?frame.theme.getTextColor().getRGB():Color.WHITE.getRGB());
    }

    static final class Frame {
        float x,y;
        final float width,height,radius,phase,blurRadius;
        float killPulse,deathPulse,statePulse,clockProgress,clockTurn;
        final String design;
        final SessionStatistics.Snapshot snapshot;
        final Theme theme;
        final boolean background,blur,glow;
        final List<TextRun> text=new ArrayList<>();
        boolean ring;
        Frame(float x,float y,float width,float height,float radius,String design,SessionStatistics.Snapshot snapshot,
              Theme theme,boolean bg,boolean blur,boolean glow,float blurRadius,float phase) {
            this.x=x;this.y=y;this.width=width;this.height=height;this.radius=radius;this.design=design;this.snapshot=snapshot;
            this.theme=theme;this.background=bg;this.blur=blur;this.glow=glow;this.blurRadius=blurRadius;this.phase=phase;
        }
        void add(String text,TTFFontRenderer font,float x,float y,boolean gradient) { this.text.add(new TextRun(text,font,x,y,gradient)); }
        void icon(String kind,float x,float y) { text.add(new IconRun(kind,x,y)); }
        boolean contains(float mx,float my) {
            if (mx<x||my<y||mx>=x+width||my>=y+height) return false;
            float dx=Math.max(Math.abs(mx-x-width/2)-(width/2-radius),0);
            float dy=Math.max(Math.abs(my-y-height/2)-(height/2-radius),0);
            return dx*dx+dy*dy<=radius*radius;
        }
    }

    /** Semantic vector icons do not depend on private character maps or text-font fallbacks. */
    static final class IconRun extends TextRun {
        final String kind;
        IconRun(String kind,float x,float y){super("",SessionInfo.font("Product-Sans-Bold 20"),x,y,false);this.kind=kind;}
        @Override void draw(Frame frame){
            try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
                net.minecraft.client.renderer.GlStateManager.disableTexture2D();GL11.glDisable(GL11.GL_TEXTURE_2D);
                net.minecraft.client.renderer.GlStateManager.enableBlend();GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
                int color=UiEffects.gradientColor(frame.theme,UiEffects.Channel.TEXT,frame.x+x,frame.y+y);GL11.glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
                GL11.glTranslatef(frame.x+x,frame.y+y,0);GL11.glLineWidth(1.4f);
                if("user".equals(kind)){circle(5,2.5f,2);GL11.glBegin(GL11.GL_LINE_STRIP);GL11.glVertex2f(1,10);GL11.glVertex2f(1,7.5f);GL11.glVertex2f(3,6);GL11.glVertex2f(7,6);GL11.glVertex2f(9,7.5f);GL11.glVertex2f(9,10);GL11.glEnd();}
                else if("time".equals(kind)){circle(5,5,4.5f);GL11.glBegin(GL11.GL_LINE_STRIP);GL11.glVertex2f(5,2);GL11.glVertex2f(5,5);GL11.glVertex2f(7.5f,6);GL11.glEnd();}
                else{GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(1,1);GL11.glVertex2f(9,9);GL11.glVertex2f(9,1);GL11.glVertex2f(1,9);GL11.glVertex2f(0,7);GL11.glVertex2f(3,10);GL11.glVertex2f(7,10);GL11.glVertex2f(10,7);GL11.glEnd();}
            }
        }
        private static void circle(float x,float y,float r){GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<24;i++){double a=i*Math.PI/12;GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();}
    }
    static class TextRun {
        final String text;
        final TTFFontRenderer font;
        final float x,y,width;
        final boolean gradient;
        TextRun(String text,TTFFontRenderer font,float x,float y,boolean gradient) {
            this.text=text;this.font=font;this.x=x;this.y=y;this.gradient=gradient;
            width=font.getWidth(text);
        }
        void draw(Frame frame) {
            if (!gradient) {
                boolean modern="Neutron 2.0".equals(frame.design)||"Modern".equals(frame.design)||"Creative".equals(frame.design);
                int ink=modern?(y<13?0xfff7f2fa:0xffd7cbdc):
                        frame.theme.isCustom()?frame.theme.getTextColor().getRGB():Color.WHITE.getRGB();
                font.drawString(text,frame.x+x,frame.y+y,ink);
                return;
            }
            me.youngjibbit.utils.render.TextEffects.direct(frame.theme,()->glyphs(frame));
        }
        void glyphs(Frame frame){
            font.drawString(text,frame.x+x,frame.y+y,-1);
        }
    }
}
