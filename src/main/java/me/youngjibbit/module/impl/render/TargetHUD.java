package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.gui.HudPlacement;
import me.youngjibbit.gui.HudReveal;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
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
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Neutron Default","Neutron White","Neutron Outline","Rise","Tenacity","Basic","Test"},1);
    public final SliderValue gradientSpeed=new SliderValue("Gradient Speed",this,5000.0,1000,10000,true);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    // Append after the existing values and material controls to preserve saved settings.
    private final SliderValue positionX=new SliderValue("Position X",this,0.0,-16384,16384);
    private final SliderValue positionY=new SliderValue("Position Y",this,0.0,-16384,16384);
    private final HudSpring healthSpring=new HudSpring(),absorptionSpring=new HudSpring(),
            portraitSpring=new HudSpring();
    private final HudReveal reveal=new HudReveal();
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
        lastFrame=lastVisualFrame=0;sceneEpoch++;lastDisplayed=null;
        healthSpring.reset();absorptionSpring.reset();portraitSpring.reset();reveal.reset();
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
    private Frame editorPreview(int sw,int sh){
        String design=mode.getValue();
        boolean test="Test".equals(design),basic="Basic".equals(design),rise="Rise".equals(design);
        TTFFontRenderer nameFont=Pulsar.INSTANCE.fontManager.getFont((basic?"Product-Sans-Bold":"Poppins-Medium")+" 18");
        TTFFontRenderer infoFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 16");
        float w=Math.min(Math.max(1,sw-8),190),h=Math.min(Math.max(1,sh-8),rise||basic?50:44);
        HudPlacement.Point p=position(sw,sh,w,h);
        float rad=basic?.5f:rise?14:"Neutron White".equals(design)?10:UiEffects.radius(5,w,h);
        float textX=test?8:44;
        return new Frame(p.x,p.y,w,h,Math.min(rad,h/2),textX,
                fit("TargetHUD preview",nameFont,w-textX-8),"-- / --","Preview",null,
                1,0,design,nameFont,infoFont,Theme.resolve(Theme.getDefaultTheme()));
    }
    Frame layout(EntityLivingBase target,int sw,int sh){
        if(target==null||target.isDead||!target.isEntityAlive())return null;
        float max=target.getMaxHealth();if(!Float.isFinite(max)||max<=0)max=20;
        float health=target.getHealth();health=Float.isFinite(health)?Math.max(0,Math.min(max,health)):0;
        float absorption=target.getAbsorptionAmount();absorption=Float.isFinite(absorption)?Math.max(0,Math.min(2048,absorption)):0;
        long now=System.nanoTime();
        float dt=lastFrame==0||now<=lastFrame?0:Math.min(.05f,(now-lastFrame)/1_000_000_000f);
        ClientSettings settings=ClientSettings.current();
        boolean enabled=settings==null||settings.animations.getValue();
        boolean reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        if(animatedTarget!=target){
            animatedTarget=target;animatedHealth=healthSpring.snap(health);
            animatedAbsorption=absorptionSpring.snap(absorption);
            portraitSpring.snap(1);
        }else{
            animatedHealth=Math.max(0,Math.min(max,healthSpring.step(health,dt,enabled,reduced,intensity)));
            animatedAbsorption=Math.max(0,Math.min(2048,
                    absorptionSpring.step(absorption,dt,enabled,reduced,intensity)));
        }
        portraitPulse=Math.max(0,Math.min(1,portraitSpring.step(0,dt,enabled,reduced,intensity)));
        lastFrame=now;
        String design=mode.getValue();
        boolean test="Test".equals(design),basic="Basic".equals(design),rise="Rise".equals(design);
        TTFFontRenderer nameFont=Pulsar.INSTANCE.fontManager.getFont((basic?"Product-Sans-Bold":"Poppins-Medium")+" 18");
        TTFFontRenderer infoFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 16");
        String name=net.minecraft.util.StringUtils.stripControlCodes(target.getCommandSenderName());if(name==null||name.trim().isEmpty())name="Living entity";
        String hp=number(animatedHealth)+" / "+number(max)+(animatedAbsorption>0?"  +"+number(animatedAbsorption):"");
        float textX=test?8:44;
        float width=Math.min(Math.max(1,sw-8),Math.max(152,Math.min(240,Math.max(nameFont.getWidth(name),infoFont.getWidth(hp))+textX+9)));
        float height=Math.min(Math.max(1,sh-8),rise||basic?50:44);
        HudPlacement.Point point=position(sw,sh,width,height);float x=point.x,y=point.y;
        float radius=basic?.5f:rise?14:"Neutron White".equals(design)?10:UiEffects.radius(5,width,height);
        ResourceLocation skin=null;
        if(!test&&target instanceof AbstractClientPlayer)skin=((AbstractClientPlayer)target).getLocationSkin();
        String kind=target instanceof AbstractClientPlayer?"Player":target instanceof EntityAnimal?"Animal":"Mob";
        return new Frame(x,y,width,height,Math.min(radius,height/2),textX,fit(name,nameFont,width-textX-8),fit(hp,infoFont,width-textX-8),kind,skin,
                Math.max(0,Math.min(1,animatedHealth/max)),Math.max(0,Math.min(1,animatedAbsorption/max)),
                design,nameFont,infoFont,Theme.resolve(Theme.getDefaultTheme()),portraitPulse);
    }
    private static String number(float value){return String.format(Locale.ROOT,value>=10000?"%.1fk":value==Math.rint(value)?"%.0f":"%.1f",value>=10000?value/1000:value);}
    private static String fit(String text,TTFFontRenderer font,float available){if(font.getWidth(text)<=available)return text;int end=text.length();while(end>0&&font.getWidth(text.substring(0,end)+"...")>available)end--;return end==0?"":text.substring(0,end)+"...";}
    void paint(Frame f){paint(f,1,1,0);}
    void paint(Frame f,float opacity,float scale,float slideY){
        boolean basic="Basic".equals(f.design),outlined="Neutron Outline".equals(f.design),
                tenacity="Tenacity".equals(f.design);
        final float x=f.x+(f.width-f.width*scale)/2;
        final float y=f.y+slideY+(f.height-f.height*scale)/2;
        final float w=f.width*scale,h=f.height*scale;
        final long epoch=sceneEpoch;
        GuiShaderBloom.afterBatch(()->{
            // The compositor can run after a target/world/HUD disable event.
            if(!isToggled()||!HUD.visible()||epoch!=sceneEpoch||mc.gameSettings.showDebugInfo)return;
            surface.designedPanel(x,y,w,h,Math.min(f.radius*scale,h/2),f.theme,true,
                    !basic,!basic,5,.62f*opacity,outlined?.9f*opacity:0,tenacity,()->{
                try(RenderState state=new RenderState()){
                    state.screenSpace();
                    try(GuiClip clip=GuiClip.push(new ClipRect(x+1,y+1,Math.max(1,w-2),Math.max(1,h-2)))){
                        glTranslatef(x-f.x*scale,y-f.y*scale,0);
                        glScalef(scale,scale,1);
                        content(f,opacity);
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
        boolean white="Neutron White".equals(f.design)&&(UiEffects.scene()==null||UiEffects.scene().policy.style<0);
        if(white)UIUtils.drawRound(f.x+1,f.y+1,f.width-2,f.height-2,
                Math.max(.5f,f.radius-1),new Color(244,245,248,Math.round(235*alpha)));
        int text=opacity(white?0xff20232c:f.theme.isCustom()?f.theme.getTextColor().getRGB():0xfff2f3f7,alpha);
        if(!"Test".equals(f.design))portrait(f,alpha);
        if(white||alpha<.999f)f.nameFont.drawString(f.name,f.x+f.textX,f.y+6,text);
        else TextEffects.direct(f.theme,()->f.nameFont.drawString(f.name,f.x+f.textX,f.y+6,-1));
        f.infoFont.drawString(f.healthText,f.x+f.textX,f.y+19,opacity(white?0xff484d58:0xffcdd3df,alpha));
        float bx=f.x+f.textX,by=f.y+f.height-8,bw=Math.max(0,f.width-f.textX-8);
        UIUtils.drawRound(bx,by,bw,3,1,
                tint(new Color(white?0x30323c44:0x66363b49,true),alpha));
        float filled=bw*f.health;
        if(filled>.01f)UIUtils.drawGradientHorizontal(bx,by,filled,3,Math.min(1,filled/2),
                tint(f.theme.getPrimaryGradientColor(),alpha),tint(f.theme.getSecondaryGradientColor(),alpha));
        float absorbed=bw*f.absorption;
        if(absorbed>.01f)UIUtils.drawRound(bx+bw-absorbed,by,absorbed,3,Math.min(1,absorbed/2),
                new Color(255,211,80,Math.round(220*alpha)));
    }
    /** Players use their skin; animals/mobs get an explicit species badge, never a fabricated skin. */
    private void portrait(Frame f,float alpha){
        float px=f.x+7,py=f.y+(f.height-30)/2;
        try(RenderState state=new RenderState()){
            float pulse=1+.09f*Math.max(0,Math.min(1,f.headPulse));
            glTranslatef(px+15,py+15,0);glScalef(pulse,pulse,1);
            glTranslatef(-px-15,-py-15,0);
            UIUtils.drawRound(px,py,30,30,4,
                    new Color((f.theme.getPrimaryGradientRGB()&0xffffff)
                            |(Math.round(85*alpha)<<24),true));
            if(f.skin!=null){
                try(GuiClip clip=GuiClip.push(new ClipRect(px+1,py+1,28,28))){
                    GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                    org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                    GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
                    GlStateManager.enableBlend();glEnable(GL_BLEND);
                    GlStateManager.blendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.color(1,1,1,alpha);glColor4f(1,1,1,alpha);
                    mc.getTextureManager().bindTexture(f.skin);
                    Gui.drawScaledCustomSizeModalRect((int)px+1,(int)py+1,8,8,8,8,28,28,64,64);
                    Gui.drawScaledCustomSizeModalRect((int)px+1,(int)py+1,40,8,8,8,28,28,64,64);
                }
            }else{
                String badge="Preview".equals(f.kind)?"N":"Animal".equals(f.kind)?"A":"Mob".equals(f.kind)?"M":"P";
                f.nameFont.drawString(badge,px+15-f.nameFont.getWidth(badge)/2,py+4,
                        opacity(Color.WHITE.getRGB(),alpha));
                TTFFontRenderer tiny=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 10");
                tiny.drawString(f.kind,px+15-tiny.getWidth(f.kind)/2,py+20,
                        opacity(Color.WHITE.getRGB(),alpha));
            }
        }
    }
    static final class Frame {
        final float x,y,width,height,radius,textX,health,absorption,headPulse;
        final String name,healthText,kind,design;
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
            this.x=x;this.y=y;this.width=width;this.height=height;this.radius=radius;
            this.textX=textX;this.name=name;this.healthText=healthText;this.kind=kind;this.skin=skin;
            this.health=health;this.absorption=absorption;this.design=design;
            this.nameFont=nameFont;this.infoFont=infoFont;this.theme=theme;this.headPulse=headPulse;
        }
        Frame positioned(float x,float y){
            return new Frame(x,y,width,height,radius,textX,name,healthText,kind,skin,
                    health,absorption,design,nameFont,infoFont,theme,headPulse);
        }
    }
}
