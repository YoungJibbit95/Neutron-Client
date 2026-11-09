package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.theme.ThemeCatalog;
import java.awt.Color;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@ModuleInfo(name="Keystrokes",description="Live movement keys with shared Liquid Glass surfaces",category=Category.Render)
public final class Keystrokes extends Module {
    private final SliderValue positionX=new SliderValue("Position X",this,16.0,0,16384),positionY=new SliderValue("Position Y",this,80.0,0,16384);
    private final CheckBoxValue mouse=new CheckBoxValue("Mouse Buttons",this,true);
    private final ArrayListSurface surface=new ArrayListSurface(this);
    // Append the cosmetic choice after every pre-existing Value registration.
    private final ComboValue mode=new ComboValue("Mode",this,new String[]{"Basic","Modern","Creative"},0);
    private final float[] presses=new float[7];private long previous;
    private final HudSpring[] modernPresses=new HudSpring[7];
    private final HudSpring[] creativePresses=new HudSpring[7];
    private final HudSpring[] hoverSprings=new HudSpring[7];
    private Object lastWorld;
    {for(int i=0;i<modernPresses.length;i++)modernPresses[i]=new HudSpring();}
    {for(int i=0;i<creativePresses.length;i++)creativePresses[i]=new HudSpring();}
    {for(int i=0;i<hoverSprings.length;i++)hoverSprings[i]=new HudSpring();}
    private boolean downBefore,dragging;private float grabX,grabY;
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{
        if(mc.thePlayer==null||!me.youngjibbit.module.impl.client.HUD.visible()){
            HudDrag.cancel(this);dragging=downBefore=false;return;
        }
        if(lastWorld!=mc.theWorld){
            lastWorld=mc.theWorld;previous=0;
            java.util.Arrays.fill(presses,0);
            for(HudSpring spring:modernPresses)spring.reset();
            for(HudSpring spring:creativePresses)spring.reset();
            for(HudSpring spring:hoverSprings)spring.reset();
            HudDrag.cancel(this);dragging=downBefore=false;
        }
        float w=80,h=mouse.getValue()?101:77,x=Math.max(0,Math.min(positionX.getValue().floatValue(),e.getSr().getScaledWidth()-w)),y=Math.max(0,Math.min(positionY.getValue().floatValue(),e.getSr().getScaledHeight()-h));
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0),chat=mc.currentScreen instanceof GuiChat;
        float mx=Mouse.isCreated()?Mouse.getX()/(float)e.getSr().getScaleFactor():-1,my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/(float)e.getSr().getScaleFactor():-1;
        boolean captured=HudDrag.capture(this,chat,down,down&&!downBefore,HudDrag.roundedContains(mx,my,x,y,w,h,4));
        if(captured&&!dragging){grabX=mx-x;grabY=my-y;}dragging=captured;
        if(dragging){x=Math.max(0,Math.min(mx-grabX,e.getSr().getScaledWidth()-w));y=Math.max(0,Math.min(my-grabY,e.getSr().getScaledHeight()-h));positionX.setValue((double)x);positionY.setValue((double)y);}downBefore=down;
        long now=System.nanoTime();float dt=previous==0?0:Math.min(.1f,(now-previous)/1e9f);previous=now;
        KeyBinding[] keys={mc.gameSettings.keyBindForward,mc.gameSettings.keyBindLeft,mc.gameSettings.keyBindBack,mc.gameSettings.keyBindRight,mc.gameSettings.keyBindJump,mc.gameSettings.keyBindAttack,mc.gameSettings.keyBindUseItem};
        final float left=x,top=y;
        final String design=mode.getValue();
        final boolean modernDesign="Modern".equals(design);
        final boolean creativeDesign="Creative".equals(design);
        final boolean themeModern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        final boolean modern=modernDesign||(themeModern&&!creativeDesign);
        final boolean creative=creativeDesign;
        Theme theme=(modern||creative)?Theme.resolve(ThemeCatalog.global().current()):Theme.getDefaultTheme();
        TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 18");
        ClientSettings motion=ClientSettings.current();
        final boolean animated=motion==null||motion.animations.getValue();
        final boolean reduced=motion!=null&&motion.reducedMotion.getValue();
        final String intensity=motion==null?"Balanced":motion.motionIntensity.getValue();
        // One shared scene, individual keys, and transparent space between them.
        Runnable controls=()->{
            for(int i=0;i<(mouse.getValue()?7:5);i++){
                boolean isDown=keys[i].isKeyDown();
                float target=isDown?1:0;
                float hover=0;
                if(modern||creative){
                    float hx=left+(i==0?28:i<4?(i-1)*28:i==6?42:0),
                            hy=top+(i==0?0:i<4?28:i==4?56:80);
                    float hcw=i==4?80:i>=5?38:24,hch=i>=4?17:24;
                    boolean over=mx>=hx&&mx<hx+hcw&&my>=hy&&my<hy+hch;
                    hover=hoverSprings[i].step(over?1:0,dt,animated,reduced,intensity);
                }
                if(modern){
                    presses[i]=Math.max(0,Math.min(1,modernPresses[i].step(
                            target,dt,animated,reduced,intensity)));
                }else if(creative){
                    presses[i]=Math.max(0,Math.min(1,creativePresses[i].step(
                            target,dt,animated,reduced,intensity)));
                }else{
                    presses[i]+=(target-presses[i])*UiEffects.response(dt,24);
                }
                float px=left+(i==0?28:i<4?(i-1)*28:i==6?42:0),
                        py=top+(i==0?0:i<4?28:i==4?56:80);
                float cw=i==4?80:i>=5?38:24,ch=i>=4?17:24;
                int index=i;
                String label=keyLabel(keys[i].getKeyCode());
                final String text=me.youngjibbit.clickgui.utils.ClickControlStyle.fit(font,label,cw-4);
                if(modern){
                    // Modern: kompakte dunkle Tastenplatte mit subtiler Hover-Animation.
                    float plateAlpha=205+18*hover;
                    UIUtils.drawRound(px+1,py+1,cw-2,ch-2,5,new Color(34,36,49,Math.round(plateAlpha)));
                    if(presses[index]>.001f){
                        UIUtils.drawDecorationGradientVertical(px+2,py+2,
                                Math.max(0,cw-4),Math.max(0,ch-4),4,
                                UiEffects.color(theme.getPrimaryGradientRGB(),presses[index]*.30f),
                                UiEffects.color(theme.getSecondaryGradientRGB(),presses[index]*.20f));
                    }
                    font.drawCenteredString(text,px+cw/2,py+(ch-font.getHeight(text))/2,
                            0xfff3edf6);
                }else if(creative){
                    // Creative: dynamische Taste mit elastischem Press-Effekt (Overshoot).
                    float cs=creativeScale(presses[index]);
                    float cx=px+(cw-cw*cs)/2,cy=py+(ch-ch*cs)/2;
                    float cw2=cw*cs,ch2=ch*cs;
                    UIUtils.drawRound(cx+1,cy+1,Math.max(0,cw2-2),Math.max(0,ch2-2),
                            5*cs,new Color(40,36,52,Math.round(200+35*hover)));
                    if(isDown){
                        UIUtils.drawRoundOutline(cx,cy,Math.max(0,cw2),Math.max(0,ch2),
                                5*cs,.7f,new Color(0,0,0,0),
                                new Color(theme.getPrimaryGradientColor().getRed(),
                                        theme.getPrimaryGradientColor().getGreen(),
                                        theme.getPrimaryGradientColor().getBlue(),
                                        Math.round(255*(.55f+.45f*hover))));
                    }
                    UIUtils.drawDecorationGradientVertical(px+1,py+1,cw-2,ch-2,3,
                            UiEffects.color(theme.getPrimaryGradientRGB(),.12f+.18f*hover),
                            UiEffects.color(theme.getSecondaryGradientRGB(),.08f+.12f*hover));
                    font.drawCenteredString(text,px+cw/2,py+(ch-font.getHeight(text))/2,
                            0xfff7f1fa);
                }else{
                    surface.inheritedPanel(px,py,cw,ch,4,theme,1,()->{
                        if(presses[index]>.001f)
                            UIUtils.drawDecorationGradientVertical(px+1,py+1,cw-2,ch-2,3,
                                    UiEffects.color(theme.getPrimaryGradientRGB(),presses[index]*.6f),
                                    UiEffects.color(theme.getSecondaryGradientRGB(),presses[index]*.6f));
                        font.drawCenteredString(text,px+cw/2,py+(ch-font.getHeight(text))/2,
                                theme.getTextColor().getRGB());
                    });
                }
            }
        };
        surface.withScene(theme,controls);
    });
    /** Press motion stays inside each original key cell, including spring overshoot. */
    static float creativeScale(float press){return 1f-.08f*Math.max(0f,Math.min(1f,press));}
    static String keyLabel(int key){if(key==-100)return "LMB";if(key==-99)return "RMB";String label=net.minecraft.client.settings.GameSettings.getKeyDisplayString(key);return label==null?"?":label;}
    @Override public void onDisable(){
        surface.close();HudDrag.cancel(this);previous=0;lastWorld=null;
        dragging=downBefore=false;java.util.Arrays.fill(presses,0);
        for(HudSpring spring:modernPresses)spring.reset();
        for(HudSpring spring:creativePresses)spring.reset();
        for(HudSpring spring:hoverSprings)spring.reset();
        super.onDisable();
    }
}
