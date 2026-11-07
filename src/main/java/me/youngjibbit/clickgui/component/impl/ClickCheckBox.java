package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.CheckBoxValue;
import java.awt.Color;

public class ClickCheckBox extends Component.Sub<CheckBoxValue> {
    private final ClickAnimation animation=new ClickAnimation(180,1,Direction.BACKWARDS);
    public ClickCheckBox(CheckBoxValue value,float x,float y,float width,float height){
        super(value,x,y,width,height);setWidth(width);
        if(value.getValue())animation.setDirection(Direction.FORWARDS);
        animation.timerUtil.setTime(System.currentTimeMillis()-180);
    }
    private boolean compact(){return width<160;}
    @Override public void setWidth(float next){width=next;height=compact()?21:24;}
    private float toggleWidth(){return panelStyle?27:compact()?19:25;}
    private float toggleX(){return (panelStyle?x+width-15:compact()?x+width-7:ClickControlStyle.right(x,width))-toggleWidth();}
    @Override public ClipRect presentationBounds(){return new ClipRect(toggleX(),y+5,toggleWidth(),panelStyle?13:compact()?11:14);}
    @Override public void drawScreen(int mouseX,int mouseY){
        animation.setDirection(value.getValue()?Direction.FORWARDS:Direction.BACKWARDS);
        float progress=Math.max(0,Math.min(1,UiEffects.motion()?animation.getOutput().floatValue():value.getValue()?1:0));
        Theme theme=Theme.resolve(Theme.getDefaultTheme());int ink=hoverInk(theme.isCustom()?theme.getTextColor().getRGB():0xffe4e4e8);
        if (panelStyle) {
            float left = toggleX(), top = y + 5;
            float knobX = left + 2 + (toggleWidth() - 14) * progress;
            ClickControlStyle.halo(left, top, toggleWidth(), 13, theme, .12f * progress);
            int neutral = Theme.interpolateRGB(theme.getBackground().getRGB(), theme.getTextColor().getRGB(), .29f);
            UIUtils.drawDecorationGradientVertical(left, top, toggleWidth(), 13, 6.5f,
                    new Color(Theme.interpolateRGB(neutral, theme.getPrimaryGradientRGB(), progress), true),
                    new Color(Theme.interpolateRGB(neutral, theme.getSecondaryGradientRGB(), progress), true));
            UIUtils.drawDecorationRound(knobX, top + 2.5f, 10, 10, 5, new Color(0, 0, 0, 95));
            UIUtils.drawDecorationRound(knobX, top + 1.5f, 10, 10, 5, new Color(hoverInk(0xfff4f6fb), true));
            ClickControlStyle.label(UiEffects.settingLabel(value), x + 17, y + 6, left - x - 23, ink);
            drawFeedback();
            return;
        }
        float th=compact()?11:14,knob=th-2,travel=toggleWidth()-knob-2;
        UIUtils.drawDecorationGradientVertical(toggleX(),y+5,toggleWidth(),th,th/2,
                new Color(Theme.interpolateRGB(0xff464950,theme.getPrimaryGradientRGB(),progress),true),
                new Color(Theme.interpolateRGB(0xff464950,theme.getSecondaryGradientRGB(),progress),true));
        UIUtils.drawDecorationRound(toggleX()+1+travel*progress,y+6,knob,knob,knob/2,
                new Color(hoverInk(Theme.interpolateRGB(0xffd2d6de,theme.getTextColor().getRGB(),progress)),true));
        me.youngjibbit.utils.font.TTFFontRenderer font=compact()?me.youngjibbit.Pulsar.INSTANCE.fontManager.getFont("Poppins-Regular 16"):ClickControlStyle.font();
        font.drawString(ClickControlStyle.fit(font,UiEffects.settingLabel(value),toggleX()-x-(compact()?11:20)),x+(compact()?7:12),y+6,ink);
        if(!compact()&&hit(mouseX,mouseY,toggleX()-3,y+2,31,20))UIUtils.drawDecorationRound(toggleX()+9+11*progress,y+10,3,3,1.5f,new Color(255,255,255,150));
        drawFeedback();
    }
    @Override public void mouseClicked(int mouseX,int mouseY,int button){
        if (panelStyle) {
            if (button == 0 && hit(mouseX, mouseY, x + 10, y, width - 20, height)) value.setValue(!value.getValue());
            return;
        }
        if(button==0&&(compact()?hit(mouseX,mouseY,x+5,y+1,width-10,height-2):hit(mouseX,mouseY,toggleX(),y+5,25,14)))value.setValue(!value.getValue());
    }
    @Override public void keyTyped(char typedChar,int key){
        if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)value.setValue(!value.getValue());
    }
}
