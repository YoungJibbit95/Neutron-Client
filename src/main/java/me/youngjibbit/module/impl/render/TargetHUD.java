package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.gui.HudPlacement;
import me.youngjibbit.gui.HudReveal;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;

/** Freeze living-entity data and geometry before any deferred surface pass is submitted. */
@ModuleInfo(name="TargetHUD", category=Category.Render)
public class TargetHUD extends Module {
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Rue","Tenacity","Basic","Modern","Creative"},3);
    public final SliderValue gradientSpeed=new SliderValue("Gradient Speed",this,5000.0,1000,10000,true);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    // Append after the existing values and material controls to preserve saved settings.
    private final SliderValue positionX=new SliderValue("Position X",this,0.0,-16384,16384);
    private final SliderValue positionY=new SliderValue("Position Y",this,0.0,-16384,16384);
    private final HudSpring healthSpring=new HudSpring(),absorptionSpring=new HudSpring(),
            portraitSpring=new HudSpring(),distanceSpring=new HudSpring();
    private final HudReveal reveal=new HudReveal();
    private final CompactHudMotion healthChange=new CompactHudMotion();
    private EntityLivingBase animatedTarget;
    private float animatedHealth,animatedAbsorption,portraitPulse;
    private long lastFrame,lastVisualFrame,sceneEpoch;
    private Object worldContext;
    private Frame lastDisplayed;
    private boolean dragging,previousButton;
    private float grabX,grabY;

    public TargetHUD(){setKey(Keyboard.KEY_0);}
    @Override public void onDisable(){resetTarget();surface.close();super.onDisable();}
    @Override public void onEnable(){
        resetTarget();previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0);super.onEnable();
    }
    public void resetPosition(){
        positionX.reset();positionY.reset();dragging=false;HudDrag.cancel(this);
    }
    private void resetTarget(){
        animatedTarget=null;animatedHealth=animatedAbsorption=portraitPulse=0;
        lastFrame=lastVisualFrame=0;sceneEpoch++;lastDisplayed=null;worldContext=null;
        healthSpring.reset();absorptionSpring.reset();portraitSpring.reset();distanceSpring.reset();reveal.reset();healthChange.reset();
        dragging=false;HudDrag.cancel(this);
    }
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{if(isToggled())renderTarget(resolveTarget());});
    protected EntityLivingBase resolveTarget(){return CombatTargetTracker.getPrimaryTarget(300L);}

    void renderTarget(EntityLivingBase target){
        if(!HUD.visible()){resetTarget();surface.close();return;}
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0);
        if(mc.gameSettings.showDebugInfo){resetTarget();previousButton=down;return;}
        if(worldContext!=mc.theWorld){resetTarget();worldContext=mc.theWorld;previousButton=down;}
        boolean chat=mc.currentScreen instanceof GuiChat;
        ScaledResolution sr=new ScaledResolution(mc);
        int sw=sr.getScaledWidth(),sh=sr.getScaledHeight();
        long now=System.nanoTime();
        float dt=lastVisualFrame==0||now<=lastVisualFrame?0:
                Math.min(.05f,(now-lastVisualFrame)/1_000_000_000f);
        lastVisualFrame=now;
        if(mc.theWorld==null||mc.thePlayer==null||target!=null&&
                (target==mc.thePlayer||target.isDead||!target.isEntityAlive()||
                mc.theWorld.getEntityByID(target.getEntityId())!=target))target=null;
        Frame live=layout(target,sw,sh);
        if(target==null){
            // Fade the frozen final frame only; discard all live target motion.
            animatedTarget=null;lastFrame=0;
            healthSpring.reset();absorptionSpring.reset();portraitSpring.reset();distanceSpring.reset();healthChange.reset();
        }
        if(live==null&&chat)live=editorPreview(sw,sh);
        if(!chat&&lastDisplayed!=null&&"Preview".equals(lastDisplayed.kind)){
            lastDisplayed=null;reveal.reset();
        }
        if(live!=null){
            float mx=Mouse.isCreated()?Mouse.getX()/(float)sr.getScaleFactor():-1;
            float my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/(float)sr.getScaleFactor():-1;
            interact(chat,down,mx,my,live,sw,sh);
            HudPlacement.Point positioned=position(sw,sh,live.width,live.height);
            live=live.positioned(positioned.x,positioned.y);
            lastDisplayed=live;
        }else{dragging=false;HudDrag.cancel(this);previousButton=down;}
        ClientSettings settings=ClientSettings.current();
        HudReveal.Frame state=reveal.step(live!=null,dt,
                settings==null||settings.animations.getValue(),
                settings!=null&&settings.reducedMotion.getValue(),
                settings==null?"Balanced":settings.motionIntensity.getValue());
        if(!state.drawn()){if(live==null)lastDisplayed=null;return;}
        Frame shown=live!=null?live:lastDisplayed;
        if(shown!=null)paint(shown,state.opacity,state.scale,state.slideY);
    }

    private HudPlacement.Point position(int sw,int sh,float width,float height){
        boolean manual=positionX.getValue()!=0||positionY.getValue()!=0;
        return HudPlacement.location(positionX.getValue().floatValue(),positionY.getValue().floatValue(),
                manual,sw,sh,width,height,HUD.bounded());
    }
    /** Chat input only; a held gameplay click cannot start a new editor drag. */
    void interact(boolean chat,boolean down,float mx,float my,Frame frame,int sw,int sh){
        boolean was=dragging;
        dragging=HudDrag.capture(this,chat,down,!previousButton,
                HudDrag.roundedContains(mx,my,frame.x,frame.y,frame.width,frame.height,frame.radius));
        if(dragging&&!was){grabX=mx-frame.x;grabY=my-frame.y;}
        if(dragging){
            HUD hud=HUD.current();float grid=hud==null?0:hud.snap.getValue().floatValue();
            HudPlacement.Point p=HudPlacement.drag(mx,my,grabX,grabY,grid,sw,sh,
                    frame.width,frame.height,HUD.bounded());
            positionX.setValue((double)p.x);positionY.setValue((double)p.y);
        }
        previousButton=down;
    }
    /** Config aliases are resolved before assignment; paint never changes persisted choices. */
    public static String migrateMode(String saved){
        if(saved==null)return null;
        if("Rise".equalsIgnoreCase(saved)||"Rue (Rise)".equalsIgnoreCase(saved))return "Rue";
        if("Neutron Default".equalsIgnoreCase(saved)||"Test".equalsIgnoreCase(saved))return "Basic";
        if("Neutron White".equalsIgnoreCase(saved)||"Neutron Outline".equalsIgnoreCase(saved)||"Neutron 2.0".equalsIgnoreCase(saved))return "Modern";
        if("Rue".equalsIgnoreCase(saved))return "Rue";
        if("Tenacity".equalsIgnoreCase(saved))return "Tenacity";
        if("Basic".equalsIgnoreCase(saved))return "Basic";
        if("Modern".equalsIgnoreCase(saved))return "Modern";
        if("Creative".equalsIgnoreCase(saved))return "Creative";
        return saved;
    }
    static String effectiveDesign(String saved,boolean modernTheme){return migrateMode(saved);}
    static float modernWidth(int viewport,float content){return Math.min(Math.max(1,viewport-8),142);}
    static float modernHeight(int viewport){return Math.min(Math.max(1,viewport-8),34);}
    static float creativeWidth(int viewport,float content){return modernWidth(viewport,content);}
    static float creativeHeight(int viewport){return modernHeight(viewport);}
    private static boolean compact(String design){return "Modern".equals(design)||"Creative".equals(design);}
    private static Theme targetTheme(String design){
        return compact(design)?Theme.resolve(ThemeCatalog.global().current()):Theme.resolve(Theme.getDefaultTheme());
    }

    private Frame editorPreview(int sw,int sh){
        String design=effectiveDesign(mode.getValue(),ThemeMode.APPROACH_TWO.equals(ThemeMode.selected()));
        boolean small=compact(design),basic="Basic".equals(design),rue="Rue".equals(design);
        TTFFontRenderer nameFont=Pulsar.INSTANCE.fontManager.getFont((basic?"Product-Sans-Bold":"Poppins-Medium")+(small?" 14":" 18"));
        TTFFontRenderer infoFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular "+(small?"12":"16"));
        float w=small?modernWidth(sw,142):Math.min(Math.max(1,sw-8),basic?166:rue?174:190);
        float h=small?modernHeight(sh):Math.min(Math.max(1,sh-8),basic?40:rue?42:44);
        float textX=small?("Creative".equals(design)?44:38):44;
        float radius=small?12:basic?2:rue?12:5;
        HudPlacement.Point p=position(sw,sh,w,h);
        return new Frame(p.x,p.y,w,h,Math.min(radius,h/2),textX,
                fit("Target preview",nameFont,small?142-textX-8:w-textX-8),
                small?"--":"-- / --","Preview",null,1,0,design,nameFont,infoFont,targetTheme(design),0,"-- m");
    }
    Frame layout(EntityLivingBase target,int sw,int sh){
        if(target==null||target.isDead||!target.isEntityAlive())return null;
        float max=target.getMaxHealth();if(!Float.isFinite(max)||max<=0)max=20;
        float health=target.getHealth();health=Float.isFinite(health)?Math.max(0,Math.min(max,health)):0;
        float absorption=target.getAbsorptionAmount();absorption=Float.isFinite(absorption)?Math.max(0,Math.min(2048,absorption)):0;
        long now=System.nanoTime();
        float dt=lastFrame==0||now<=lastFrame?0:Math.min(.05f,(now-lastFrame)/1_000_000_000f);
        ClientSettings settings=ClientSettings.current();
        boolean enabled=settings==null||settings.animations.getValue(),reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        float observedDistance=Float.NaN;
        if(mc.thePlayer!=null){float d=mc.thePlayer.getDistanceToEntity(target);if(Float.isFinite(d)&&d>=0)observedDistance=Math.min(999,d);}
        if(animatedTarget!=target){
            animatedTarget=target;animatedHealth=healthSpring.snap(health);animatedAbsorption=absorptionSpring.snap(absorption);
            distanceSpring.snap(Float.isFinite(observedDistance)?observedDistance:0);portraitSpring.snap(1);healthChange.reset();
        }else{
            animatedHealth=Math.max(0,Math.min(max,healthSpring.step(health,dt,enabled,reduced,intensity)));
            animatedAbsorption=Math.max(0,Math.min(2048,absorptionSpring.step(absorption,dt,enabled,reduced,intensity)));
            if(Float.isFinite(observedDistance))distanceSpring.step(observedDistance,dt,enabled,reduced,intensity);
        }
        float damagePulse=healthChange.step(((long)Float.floatToIntBits(health)<<32)^(Float.floatToIntBits(absorption)&0xffffffffL),
                dt,enabled,reduced,intensity);
        portraitPulse=Math.max(damagePulse,Math.max(0,Math.min(1,portraitSpring.step(0,dt,enabled,reduced,intensity))));
        lastFrame=now;
        String design=effectiveDesign(mode.getValue(),ThemeMode.APPROACH_TWO.equals(ThemeMode.selected()));
        boolean small=compact(design),basic="Basic".equals(design),rue="Rue".equals(design);
        TTFFontRenderer nameFont=Pulsar.INSTANCE.fontManager.getFont((basic?"Product-Sans-Bold":"Poppins-Medium")+(small?" 14":" 18"));
        TTFFontRenderer infoFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular "+(small?"12":"16"));
        String name=net.minecraft.util.StringUtils.stripControlCodes(target.getCommandSenderName());
        if(name==null||name.trim().isEmpty())name="Living entity";
        String hp=small?number(animatedHealth):number(animatedHealth)+" / "+number(max)+(animatedAbsorption>0?"  +"+number(animatedAbsorption):"");
        String distance=Float.isFinite(observedDistance)?number(Math.max(0,distanceSpring.value()))+" m":"";
        float textX=small?("Creative".equals(design)?44:38):44;
        float width=small?modernWidth(sw,142):Math.min(Math.max(1,sw-8),basic?166:rue?174:190);
        float height=small?modernHeight(sh):Math.min(Math.max(1,sh-8),basic?40:rue?42:44);
        HudPlacement.Point point=position(sw,sh,width,height);
        ResourceLocation skin=target instanceof AbstractClientPlayer?((AbstractClientPlayer)target).getLocationSkin():null;
        String kind=target instanceof AbstractClientPlayer?"Player":target instanceof EntityAnimal?"Animal":"Mob";
        return new Frame(point.x,point.y,width,height,Math.min(small?12:basic?2:rue?12:5,height/2),textX,
                fit(name,nameFont,(small?142:width)-textX-8),fit(hp,infoFont,small?35:width-textX-8),kind,skin,
                Math.max(0,Math.min(1,animatedHealth/max)),Math.max(0,Math.min(1,animatedAbsorption/max)),
                design,nameFont,infoFont,targetTheme(design),portraitPulse,fit(distance,infoFont,42));
    }
    private static String number(float value){return String.format(Locale.ROOT,value>=10000?"%.1fk":value==Math.rint(value)?"%.0f":"%.1f",value>=10000?value/1000:value);}
    private static String fit(String text,TTFFontRenderer font,float available){if(font.getWidth(text)<=available)return text;int end=text.length();while(end>0&&font.getWidth(text.substring(0,end)+"...")>available)end=text.offsetByCodePoints(end,-1);return end==0?"":text.substring(0,end)+"...";}
    void paint(Frame f){paint(f,1,1,0);}
    void paint(Frame f,float opacity,float scale,float slideY){
        if(compact(f.design)){paintCompact(f,opacity,scale,slideY);return;}
        boolean basic="Basic".equals(f.design),tenacity="Tenacity".equals(f.design);
        final float x=f.x+(f.width-f.width*scale)/2,y=f.y+slideY+(f.height-f.height*scale)/2;
        final float w=f.width*scale,h=f.height*scale;final long epoch=sceneEpoch;
        GuiShaderBloom.afterBatch(()->{
            if(!isToggled()||!HUD.visible()||epoch!=sceneEpoch||mc.gameSettings.showDebugInfo)return;
            surface.designedPanel(x,y,w,h,Math.min(f.radius*scale,h/2),f.theme,true,!basic,!basic,5,
                    .72f*opacity,0,tenacity,()->{
                try(RenderState state=new RenderState()){
                    state.screenSpace();
                    try(GuiClip clip=GuiClip.push(new ClipRect(x,y,w,h))){
                        glTranslatef(x-f.x*scale,y-f.y*scale,0);glScalef(scale,scale,1);
                        content(f,opacity);
                    }
                }
            });
        });
    }
    /** An inline capsule or detached health-orbit portrait; both fit 142 x 34 logical pixels. */
    private void paintCompact(Frame f,float alpha,float revealScale,float slideY){
        final float x=f.x+(f.width-f.width*revealScale)/2,y=f.y+slideY+(f.height-f.height*revealScale)/2;
        final float w=f.width*revealScale,h=f.height*revealScale;
        final boolean creative="Creative".equals(f.design);final long epoch=sceneEpoch;
        GuiShaderBloom.afterBatch(()->{
            if(!isToggled()||!HUD.visible()||epoch!=sceneEpoch||mc.gameSettings.showDebugInfo)return;
            surface.withScene(f.theme,true,false,3.5f,()->{
                try(RenderState state=new RenderState()){
                    state.screenSpace();
                    try(GuiClip clip=GuiClip.push(new ClipRect(x,y,w,h))){
                        CreativeHudLayout.Fit fit=new CreativeHudLayout.Fit(142,34,w,h);
                        glTranslatef(x+fit.x,y+fit.y,0);glScalef(fit.scale,fit.scale,1);
                        float bx=creative?35:0,bw=creative?107:142;
                        UIUtils.drawDecorationRound(bx,0,bw,34,creative?11:13,new Color(17,23,34,Math.round(232*alpha)));
                        if(creative)UIUtils.drawDecorationRound(1,3,28,28,14,new Color(27,34,47,Math.round(238*alpha)));
                        int accent=f.theme.getPrimaryGradientRGB();
                        CompactHudVisuals.begin(fit.scale);
                        float cx=creative?15:18,cy=17,portrait=creative?23:24;
                        CompactHudVisuals.dot(cx,cy,portrait/2,0xff2b3547,alpha);
                        if(creative){
                            CompactHudVisuals.arc(cx,cy,14,0,1,1.6f,accent,alpha*.18f);
                            CompactHudVisuals.arc(cx,cy,14,0,f.health,2,accent,alpha);
                            if(f.absorption>0)CompactHudVisuals.arc(cx,cy,11.7f,0,f.absorption,1.25f,0xffffd36b,alpha);
                        }
                        float pulse=1-.06f*Math.max(0,Math.min(1,f.headPulse));
                        float size=portrait*pulse;
                        if(f.skin!=null)CompactHudVisuals.skin(f.skin,cx-size/2,cy-size/2,size,creative?size/2:7,alpha);
                        else{
                            String badge="Preview".equals(f.kind)?"N":"Animal".equals(f.kind)?"A":"Mob".equals(f.kind)?"M":"P";
                            f.nameFont.drawString(badge,cx-f.nameFont.getWidth(badge)/2,cy-5,opacity(0xffedf4ff,alpha));
                        }
                        float tx=creative?44:38;
                        f.nameFont.drawString(f.name,tx,4,opacity(0xffedf4ff,alpha));
                        f.infoFont.drawString(f.healthText,tx+11,17,opacity(0xffd7e4ef,alpha));
                        if(!f.distanceText.isEmpty())f.infoFont.drawString(f.distanceText,tx+55,17,opacity(0xffaab9cc,alpha));
                        CompactHudVisuals.begin(fit.scale);
                        CompactHudVisuals.heart(tx,18,7+f.headPulse*.4f,accent,alpha);
                        if(!f.distanceText.isEmpty())CompactHudVisuals.range(tx+44,18,7,0xff8fa5bc,alpha);
                        float track=142-tx-9;
                        CompactHudVisuals.line(tx,29,tx+track,29,2.5f,0xff73859e,alpha*.24f);
                        if(f.health>0)CompactHudVisuals.line(tx,29,tx+track*f.health,29,2.5f,accent,alpha);
                        if(f.absorption>0)CompactHudVisuals.line(tx,32,tx+track*f.absorption,32,1,0xffffd36b,alpha);
                    }
                }
            });
        });
    }

    private static int opacity(int rgba,float amount){
        return (Math.round(((rgba>>>24)&255)*Math.max(0,Math.min(1,amount)))<<24)
                |(rgba&0x00ffffff);
    }
    private static Color tint(Color c,float alpha){
        return new Color(c.getRed(),c.getGreen(),c.getBlue(),
                Math.round(c.getAlpha()*Math.max(0,Math.min(1,alpha))));
    }
    private void content(Frame f,float alpha){
        boolean basic="Basic".equals(f.design),rue="Rue".equals(f.design);
        portrait(f,alpha);
        f.nameFont.drawString(f.name,f.x+f.textX,f.y+5,opacity(0xfff1f4f9,alpha));
        f.infoFont.drawString(f.healthText,f.x+f.textX,f.y+18,opacity(0xffb9c8d9,alpha));
        float bx=f.x+f.textX,by=f.y+f.height-7,bw=Math.max(0,f.width-f.textX-8);
        if(rue){
            float gap=2,pip=(bw-gap*9)/10;
            for(int i=0;i<10;i++){
                UIUtils.drawDecorationRound(bx+i*(pip+gap),by,pip,3,1.5f,new Color(255,255,255,Math.round(30*alpha)));
                float amount=Math.max(0,Math.min(1,f.health*10-i));
                if(amount>0)UIUtils.drawDecorationRound(bx+i*(pip+gap),by,pip*amount,3,Math.min(1.5f,pip*amount/2),tint(f.theme.getPrimaryGradientColor(),alpha));
            }
        }else{
            UIUtils.drawDecorationRound(bx,by,bw,3,basic?.5f:1.5f,new Color(255,255,255,Math.round(30*alpha)));
            if(f.health>0)UIUtils.drawGradientHorizontal(bx,by,bw*f.health,3,Math.min(basic?.5f:1.5f,bw*f.health/2),
                    tint(f.theme.getPrimaryGradientColor(),alpha),tint(f.theme.getSecondaryGradientColor(),alpha));
        }
        if(f.absorption>0)UIUtils.drawDecorationRound(bx,by+4,bw*f.absorption,1,.5f,new Color(255,211,80,Math.round(220*alpha)));
    }
    /** Players use their real head and hat layers; missing skins get a factual species badge. */
    private void portrait(Frame f,float alpha){
        float size=28,px=f.x+7,py=f.y+(f.height-size)/2;
        try(RenderState state=new RenderState()){
            float pulse=1-.05f*Math.max(0,Math.min(1,f.headPulse));
            glTranslatef(px+size/2,py+size/2,0);glScalef(pulse,pulse,1);glTranslatef(-px-size/2,-py-size/2,0);
            UIUtils.drawDecorationRound(px,py,size,size,"Basic".equals(f.design)?2:7,tint(f.theme.getPrimaryGradientColor(),alpha*.25f));
            if(f.skin!=null)CompactHudVisuals.skin(f.skin,px+1,py+1,size-2,"Basic".equals(f.design)?1:6,alpha);
            else{
                String badge="Preview".equals(f.kind)?"N":"Animal".equals(f.kind)?"A":"Mob".equals(f.kind)?"M":"P";
                f.nameFont.drawString(badge,px+size/2-f.nameFont.getWidth(badge)/2,py+4,opacity(0xfff4f7fb,alpha));
                TTFFontRenderer tiny=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 10");
                tiny.drawString(f.kind,px+size/2-tiny.getWidth(f.kind)/2,py+size-9,opacity(0xffb9c8d9,alpha));
            }
        }
    }
    static final class Frame {
        final float x,y,width,height,radius,textX,health,absorption,headPulse;
        final String name,healthText,kind,design,distanceText;
        final ResourceLocation skin;
        final TTFFontRenderer nameFont,infoFont;
        final Theme theme;
        Frame(float x,float y,float width,float height,float radius,float textX,String name,
              String healthText,String kind,ResourceLocation skin,float health,float absorption,
              String design,TTFFontRenderer nameFont,TTFFontRenderer infoFont,Theme theme){
            this(x,y,width,height,radius,textX,name,healthText,kind,skin,health,absorption,
                    design,nameFont,infoFont,theme,0);
        }
        Frame(float x,float y,float width,float height,float radius,float textX,String name,
              String healthText,String kind,ResourceLocation skin,float health,float absorption,
              String design,TTFFontRenderer nameFont,TTFFontRenderer infoFont,Theme theme,float headPulse){
            this(x,y,width,height,radius,textX,name,healthText,kind,skin,health,absorption,design,nameFont,infoFont,theme,headPulse,"");
        }
        Frame(float x,float y,float width,float height,float radius,float textX,String name,
              String healthText,String kind,ResourceLocation skin,float health,float absorption,
              String design,TTFFontRenderer nameFont,TTFFontRenderer infoFont,Theme theme,float headPulse,String distanceText){
            this.distanceText=distanceText;
            this.x=x;this.y=y;this.width=width;this.height=height;this.radius=radius;
            this.textX=textX;this.name=name;this.healthText=healthText;this.kind=kind;this.skin=skin;
            this.health=health;this.absorption=absorption;this.design=design;
            this.nameFont=nameFont;this.infoFont=infoFont;this.theme=theme;this.headPulse=headPulse;
        }
        Frame positioned(float x,float y){
            return new Frame(x,y,width,height,radius,textX,name,healthText,kind,skin,
                    health,absorption,design,nameFont,infoFont,theme,headPulse,distanceText);
        }
    }
}
