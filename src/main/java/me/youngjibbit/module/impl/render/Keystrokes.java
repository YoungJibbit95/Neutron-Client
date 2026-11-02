package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
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
    private boolean downBefore,dragging;private float grabX,grabY;
    @Subscribe private final Listener<Event2D> render=new Listener<>(e->{
        if(mc.thePlayer==null||!me.youngjibbit.module.impl.client.HUD.visible())return;
        float w=80,h=mouse.getValue()?101:77,x=Math.max(0,Math.min(positionX.getValue().floatValue(),e.getSr().getScaledWidth()-w)),y=Math.max(0,Math.min(positionY.getValue().floatValue(),e.getSr().getScaledHeight()-h));
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0),chat=mc.currentScreen instanceof GuiChat;
        float mx=Mouse.isCreated()?Mouse.getX()/(float)e.getSr().getScaleFactor():-1,my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/(float)e.getSr().getScaleFactor():-1;
        boolean captured=HudDrag.capture(this,chat,down,down&&!downBefore,HudDrag.roundedContains(mx,my,x,y,w,h,4));
        if(captured&&!dragging){grabX=mx-x;grabY=my-y;}dragging=captured;
        if(dragging){x=Math.max(0,Math.min(mx-grabX,e.getSr().getScaledWidth()-w));y=Math.max(0,Math.min(my-grabY,e.getSr().getScaledHeight()-h));positionX.setValue((double)x);positionY.setValue((double)y);}downBefore=down;
        long now=System.nanoTime();float dt=previous==0?0:Math.min(.1f,(now-previous)/1e9f);previous=now;
        KeyBinding[] keys={mc.gameSettings.keyBindForward,mc.gameSettings.keyBindLeft,mc.gameSettings.keyBindBack,mc.gameSettings.keyBindRight,mc.gameSettings.keyBindJump,mc.gameSettings.keyBindAttack,mc.gameSettings.keyBindUseItem};
        final float left=x,top=y;Theme theme=Theme.getDefaultTheme();TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Medium 18");
        surface.withScene(theme,()->{for(int i=0;i<(mouse.getValue()?7:5);i++){
            presses[i]+=((keys[i].isKeyDown()?1:0)-presses[i])*UiEffects.response(dt,24);
            float px=left+(i==0?28:i<4?(i-1)*28:i==6?42:0),py=top+(i==0?0:i<4?28:i==4?56:80),cw=i==4?80:i>=5?38:24,ch=i>=4?17:24;
            int index=i;String label=keyLabel(keys[i].getKeyCode());final String text=me.youngjibbit.clickgui.utils.ClickControlStyle.fit(font,label,cw-4);
            surface.inheritedPanel(px,py,cw,ch,4,theme,1,()->{if(presses[index]>.001f)UIUtils.drawDecorationGradientVertical(px+1,py+1,cw-2,ch-2,3,UiEffects.color(theme.getPrimaryGradientRGB(),presses[index]*.6f),UiEffects.color(theme.getSecondaryGradientRGB(),presses[index]*.6f));font.drawCenteredString(text,px+cw/2,py+(ch-font.getHeight(text))/2,theme.getTextColor().getRGB());});
        }});
    });
    static String keyLabel(int key){if(key==-100)return "LMB";if(key==-99)return "RMB";String label=net.minecraft.client.settings.GameSettings.getKeyDisplayString(key);return label==null?"?":label;}
    @Override public void onDisable(){surface.close();HudDrag.cancel(this);previous=0;dragging=downBefore=false;java.util.Arrays.fill(presses,0);super.onDisable();}
}
