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
    private final float[] presses=new float[7];private long previous;
    private final HudSpring[] modernPresses=new HudSpring[7];
    private Object lastWorld;
    private static final Color MODERN_PLATE=new Color(20,22,32,205);
    private static final Color MODERN_CELL=new Color(34,36,49,230);
    {for(int i=0;i<modernPresses.length;i++)modernPresses[i]=new HudSpring();}
    private boolean downBefore,dragging;private float grabX,grabY;
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{
        if(mc.thePlayer==null||!me.youngjibbit.module.impl.client.HUD.visible()){
            HudDrag.cancel(this);dragging=downBefore=false;return;
        }
        if(lastWorld!=mc.theWorld){
            lastWorld=mc.theWorld;previous=0;
            java.util.Arrays.fill(presses,0);
            for(HudSpring spring:modernPresses)spring.reset();
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
        final boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        Theme theme=modern?Theme.resolve(ThemeCatalog.global().current()):Theme.getDefaultTheme();
        TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Medium 18");
        ClientSettings motion=ClientSettings.current();
        final boolean animated=motion==null||motion.animations.getValue();
        final boolean reduced=motion!=null&&motion.reducedMotion.getValue();
        final String intensity=motion==null?"Balanced":motion.motionIntensity.getValue();
        // One existing surface for the whole modern group; no individual FBO per key.
        Runnable controls=()->{
            for(int i=0;i<(mouse.getValue()?7:5);i++){
                float target=keys[i].isKeyDown()?1:0;
                if(modern)presses[i]=Math.max(0,Math.min(1,modernPresses[i].step(
                        target,dt,animated,reduced,intensity)));
                else presses[i]+=(target-presses[i])*UiEffects.response(dt,24);
                float px=left+(i==0?28:i<4?(i-1)*28:i==6?42:0),
                        py=top+(i==0?0:i<4?28:i==4?56:80);
                float cw=i==4?80:i>=5?38:24,ch=i>=4?17:24;
                int index=i;
                String label=keyLabel(keys[i].getKeyCode());
                final String text=me.youngjibbit.clickgui.utils.ClickControlStyle.fit(font,label,cw-4);
                if(modern){
                    UIUtils.drawRound(px+1,py+1,cw-2,ch-2,5,MODERN_CELL);
                    if(presses[index]>.001f)
                        UIUtils.drawDecorationGradientVertical(px+2,py+2,
                                Math.max(0,cw-4),Math.max(0,ch-4),4,
                                UiEffects.color(theme.getPrimaryGradientRGB(),presses[index]*.38f),
                                UiEffects.color(theme.getSecondaryGradientRGB(),presses[index]*.23f));
                    font.drawCenteredString(text,px+cw/2,py+(ch-font.getHeight(text))/2,
                            0xfff3edf6);
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
        if(modern)surface.panel(left,top,w,h,9,theme,true,
                false,false,3,.83f,.12f,()->{
                    UIUtils.drawRound(left+1,top+1,w-2,h-2,8,MODERN_PLATE);
                    controls.run();
                });
        else surface.withScene(theme,controls);
    });
    static String keyLabel(int key){if(key==-100)return "LMB";if(key==-99)return "RMB";String label=net.minecraft.client.settings.GameSettings.getKeyDisplayString(key);return label==null?"?":label;}
    @Override public void onDisable(){
        surface.close();HudDrag.cancel(this);previous=0;lastWorld=null;
        dragging=downBefore=false;java.util.Arrays.fill(presses,0);
        for(HudSpring spring:modernPresses)spring.reset();
        super.onDisable();
    }
}
