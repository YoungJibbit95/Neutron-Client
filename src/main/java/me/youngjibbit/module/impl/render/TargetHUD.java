package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
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
import java.awt.Color;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;

/** Freeze living-entity data and geometry before any deferred surface pass is submitted. */
@ModuleInfo(name="TargetHUD", category=Category.Render)
public class TargetHUD extends Module {
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Neutron Default","Neutron White","Neutron Outline","Rise","Tenacity","Basic","Test"},1);
    public final SliderValue gradientSpeed=new SliderValue("Gradient Speed",this,5000.0,1000,10000,true);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    private EntityLivingBase animatedTarget;
    private float animatedHealth;
    private long lastFrame;

    public TargetHUD(){setKey(Keyboard.KEY_0);}
    @Override public void onDisable(){resetTarget();surface.close();super.onDisable();}
    @Override public void onEnable(){resetTarget();super.onEnable();}
    private void resetTarget(){animatedTarget=null;animatedHealth=0;lastFrame=0;}
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{if(isToggled())renderTarget(resolveTarget());});
    protected EntityLivingBase resolveTarget(){return CombatTargetTracker.getPrimaryTarget();}

    void renderTarget(EntityLivingBase target){
        if(!me.youngjibbit.module.impl.client.HUD.visible()){resetTarget();surface.close();return;}
        if(mc.gameSettings.showDebugInfo||mc.currentScreen instanceof GuiChat){resetTarget();return;}
        ScaledResolution sr=new ScaledResolution(mc);
        Frame frame=layout(target,sr.getScaledWidth(),sr.getScaledHeight());
        if(frame!=null)paint(frame);
    }
    Frame layout(EntityLivingBase target,int sw,int sh){
        if(target==null||target.isDead||!target.isEntityAlive()){resetTarget();return null;}
        float max=target.getMaxHealth();if(!Float.isFinite(max)||max<=0)max=20;
        float health=target.getHealth();health=Float.isFinite(health)?Math.max(0,Math.min(max,health)):0;
        float absorption=target.getAbsorptionAmount();absorption=Float.isFinite(absorption)?Math.max(0,absorption):0;
        long now=System.nanoTime();
        if(animatedTarget!=target){animatedTarget=target;animatedHealth=health;}
        else {float dt=Math.max(0,Math.min(.1f,(now-lastFrame)/1_000_000_000f));animatedHealth+=(health-animatedHealth)*UiEffects.response(dt,14);}
        lastFrame=now;
        String design=mode.getValue();
        boolean test="Test".equals(design),basic="Basic".equals(design),rise="Rise".equals(design);
        TTFFontRenderer nameFont=Pulsar.INSTANCE.fontManager.getFont((basic?"Product-Sans-Bold":"Poppins-Medium")+" 18");
        TTFFontRenderer infoFont=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 16");
        String name=net.minecraft.util.StringUtils.stripControlCodes(target.getCommandSenderName());if(name==null||name.trim().isEmpty())name="Living entity";
        String hp=number(health)+" / "+number(max)+(absorption>0?"  +"+number(absorption):"");
        float textX=test?8:44;
        float width=Math.min(Math.max(1,sw-8),Math.max(152,Math.min(240,Math.max(nameFont.getWidth(name),infoFont.getWidth(hp))+textX+9)));
        float height=Math.min(Math.max(1,sh-8),rise||basic?50:44);
        float x=Math.max(4,Math.min(sw/2f+25,sw-width-4)),y=Math.max(4,Math.min(sh/2f-35,sh-height-4));
        float radius=basic?.5f:rise?14:"Neutron White".equals(design)?10:UiEffects.radius(5,width,height);
        ResourceLocation skin=null;
        if(!test&&target instanceof AbstractClientPlayer)skin=((AbstractClientPlayer)target).getLocationSkin();
        String kind=target instanceof AbstractClientPlayer?"Player":target instanceof EntityAnimal?"Animal":"Mob";
        return new Frame(x,y,width,height,Math.min(radius,height/2),textX,fit(name,nameFont,width-textX-8),fit(hp,infoFont,width-textX-8),kind,skin,
                Math.max(0,Math.min(1,animatedHealth/max)),Math.max(0,Math.min(1,absorption/max)),design,nameFont,infoFont,Theme.resolve(Theme.getDefaultTheme()));
    }
    private static String number(float value){return String.format(Locale.ROOT,value>=10000?"%.1fk":value==Math.rint(value)?"%.0f":"%.1f",value>=10000?value/1000:value);}
    private static String fit(String text,TTFFontRenderer font,float available){if(font.getWidth(text)<=available)return text;int end=text.length();while(end>0&&font.getWidth(text.substring(0,end)+"...")>available)end--;return end==0?"":text.substring(0,end)+"...";}
    void paint(Frame f){
        boolean basic="Basic".equals(f.design),outlined="Neutron Outline".equals(f.design),tenacity="Tenacity".equals(f.design);
        GuiShaderBloom.afterBatch(()->surface.designedPanel(f.x,f.y,f.width,f.height,f.radius,f.theme,true,!basic,!basic,5,.62f,outlined?.9f:0,tenacity,()->content(f)));
    }
    private void content(Frame f){
        try(GuiClip clip=GuiClip.push(new ClipRect(f.x+1,f.y+1,f.width-2,f.height-2))){
            boolean white="Neutron White".equals(f.design)&&(UiEffects.scene()==null||UiEffects.scene().policy.style<0);
            if(white)UIUtils.drawRound(f.x+1,f.y+1,f.width-2,f.height-2,Math.max(.5f,f.radius-1),new Color(244,245,248,235));
            int text=white?0xff20232c:f.theme.isCustom()?f.theme.getTextColor().getRGB():0xfff2f3f7;
            if(!"Test".equals(f.design))portrait(f);
            if(white)f.nameFont.drawString(f.name,f.x+f.textX,f.y+6,text);else TextEffects.direct(f.theme,()->f.nameFont.drawString(f.name,f.x+f.textX,f.y+6,-1));
            f.infoFont.drawString(f.healthText,f.x+f.textX,f.y+19,white?0xff484d58:0xffcdd3df);
            float bx=f.x+f.textX,by=f.y+f.height-8,bw=Math.max(0,f.width-f.textX-8);
            UIUtils.drawRound(bx,by,bw,3,1,new Color(white?0x30323c44:0x66363b49,true));
            float filled=bw*f.health;
            if(filled>.01f)UIUtils.drawGradientHorizontal(bx,by,filled,3,Math.min(1,filled/2),f.theme.getPrimaryGradientColor(),f.theme.getSecondaryGradientColor());
            float absorbed=bw*f.absorption;
            if(absorbed>.01f)UIUtils.drawRound(bx+bw-absorbed,by,absorbed,3,Math.min(1,absorbed/2),new Color(255,211,80,220));
        }
    }
    /** Players use their skin; animals/mobs get an explicit species badge, never a fabricated skin. */
    private void portrait(Frame f){
        float px=f.x+7,py=f.y+(f.height-30)/2;
        UIUtils.drawRound(px,py,30,30,4,new Color(f.theme.getPrimaryGradientRGB()&0xffffff|0x55000000,true));
        if(f.skin!=null){
            try(RenderState state=new RenderState();GuiClip clip=GuiClip.push(new ClipRect(px+1,py+1,28,28))){
                GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.enableBlend();glEnable(GL_BLEND);GlStateManager.blendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
                mc.getTextureManager().bindTexture(f.skin);Gui.drawScaledCustomSizeModalRect((int)px+1,(int)py+1,8,8,8,8,28,28,64,64);
                Gui.drawScaledCustomSizeModalRect((int)px+1,(int)py+1,40,8,8,8,28,28,64,64);
            }
        }else{
            String badge="Animal".equals(f.kind)?"A":"Mob".equals(f.kind)?"M":"P";
            f.nameFont.drawString(badge,px+15-f.nameFont.getWidth(badge)/2,py+4,Color.WHITE.getRGB());
            TTFFontRenderer tiny=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 10");
            tiny.drawString(f.kind,px+15-tiny.getWidth(f.kind)/2,py+20,Color.WHITE.getRGB());
        }
    }
    static final class Frame {
        final float x,y,width,height,radius,textX,health,absorption;
        final String name,healthText,kind,design;final ResourceLocation skin;
        final TTFFontRenderer nameFont,infoFont;final Theme theme;
        Frame(float x,float y,float width,float height,float radius,float textX,String name,String healthText,String kind,ResourceLocation skin,float health,float absorption,String design,TTFFontRenderer nameFont,TTFFontRenderer infoFont,Theme theme){this.x=x;this.y=y;this.width=width;this.height=height;this.radius=radius;this.textX=textX;this.name=name;this.healthText=healthText;this.kind=kind;this.skin=skin;this.health=health;this.absorption=absorption;this.design=design;this.nameFont=nameFont;this.infoFont=infoFont;this.theme=theme;}
    }
}
