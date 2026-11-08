package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.impl.SliderValue;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ClickSlider extends Component.Sub<SliderValue> {
    private boolean dragging,editing,replaceInput,error;
    private String input="";
    private float displayedFraction = Float.NaN;
    private final HudSpring panelValue=new HudSpring();
    public void resetPanelMotion(){displayedFraction=Float.NaN;panelValue.reset();}

    private float fraction() {
        double range = value.getMax() - value.getMin();
        return range <= 0 ? 0 : (float)Math.max(0, Math.min(1, (value.getValue() - value.getMin()) / range));
    }

    @Override public void advanceMotion(float dt, int mx, int my, boolean focused, boolean pressed, boolean visible) {
        super.advanceMotion(dt, mx, my, focused, pressed, visible);
        boolean compactModern=compact()&&
                me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(
                        me.youngjibbit.theme.ThemeMode.selected());
        if (!panelStyle && !compactModern){
            resetPanelMotion(); // Current Legacy compact slider remains input-direct.
            return;
        }
        if (!visible) {
            resetPanelMotion();
            return;
        }
        float target = fraction();
        if (dragging || Float.isNaN(displayedFraction)) displayedFraction=panelValue.snap(target);
        else {
            ClientSettings settings=ClientSettings.current();
            float response=panelValue.step(target,dt,
                    settings==null||settings.animations.getValue(),
                    settings!=null&&settings.reducedMotion.getValue(),
                    settings==null?"Balanced":settings.motionIntensity.getValue());
            displayedFraction=Math.max(0,Math.min(1,response));
        }
    }
    public boolean isEditing(){return editing;}
    public void cancelInput(){editing=false;dragging=false;error=false;}
    private void beginInput(){dragging=false;editing=true;replaceInput=true;error=false;input=Double.toString(value.getValue());}
    public ClickSlider(SliderValue value,float x,float y,float width,float height){super(value,x,y,width,height);setWidth(width);}
    private boolean compact(){return width<160;}
    @Override public void setWidth(float next) {
        width = next;
        height = panelStyle || compact() ? 34 : 24;
    }
    private float fieldWidth(){return compact()?width-14:ClickControlStyle.fieldWidth(width);}
    private float fieldX(){return compact()?x+7:ClickControlStyle.right(x,width)-fieldWidth();}
    private float valueWidth() {
        if (panelStyle) return Math.min(56, width * .36f);
        return compact() ? Math.min(40, width * .44f) : Math.max(30, Math.min(54, fieldWidth() * .44f));
    }
    private float valueX() {
        if (panelStyle) return x + width - 15 - valueWidth();
        return (compact() ? x + width - 7 : ClickControlStyle.right(x, width)) - valueWidth();
    }
    private float trackX() {
        if (panelStyle) return x + 17;
        return compact() ? x + 8 : fieldX();
    }
    private float trackWidth() {
        if (panelStyle) return Math.max(1, width - 34);
        return compact() ? width - 16 : Math.max(1, fieldWidth() - valueWidth() - 10);
    }
    private float trackY(){return y + (panelStyle || compact() ? 26 : 12);}
    private float badgeY(){return y + (panelStyle || compact() ? 1 : 3);}
    private float badgeHeight(){return panelStyle || compact() ? 16 : 19;}
    @Override public ClipRect presentationBounds() {
        if (panelStyle) return new ClipRect(x + 10, y, width - 20, height);
        return compact() ? new ClipRect(x+5,y,width-10,height) : new ClipRect(fieldX(),y+3,fieldWidth(),height-5);
    }
    @Override public void drawScreen(int mouseX,int mouseY){
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        if (panelStyle) {
            drawPanelSlider(theme, mouseX, mouseY);
            return;
        }
        float range=(float)Math.max(.001,value.getMax()-value.getMin());
        boolean compactModern=compact()&&
                me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(
                        me.youngjibbit.theme.ThemeMode.selected());
        float raw=(float)((value.getValue()-value.getMin())/range);
        float drawn=compactModern&&Float.isFinite(displayedFraction)?displayedFraction:raw;
        float fill=trackWidth()*Math.max(0,Math.min(1,drawn));
        UIUtils.drawDecorationRound(trackX(),trackY()-2,trackWidth(),4,2,new Color(64,68,77));
        if(fill>.5f)UIUtils.drawDecorationGradientVertical(trackX(),trackY()-2,fill,4,Math.min(2,fill/2),theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
        UIUtils.drawDecorationRound(trackX()+fill-3,trackY()-4,6,8,3,new Color(dragging?UiEffects.gradientAt(theme,UiEffects.Channel.BORDER,trackX()+fill,trackY()):0xffe0e4ed,true));
        int ink=hoverInk(theme.isCustom()?theme.getTextColor().getRGB():0xffe4e4e8);
        TTFFontRenderer font=compact()?me.youngjibbit.Pulsar.INSTANCE.fontManager.getFont("Poppins-Regular 16"):ClickControlStyle.font();
        float labelX=x+(compact()?7:12),labelY=y+(compact()?4:6);
        font.drawString(ClickControlStyle.fit(font,value.getName(),(compact()?valueX()-4:fieldX()-8)-labelX),labelX,labelY,ink);
        GuiStyle.panel(valueX(),badgeY(),valueWidth(),badgeHeight(),3,editing?1:isMouseOver(mouseX,mouseY)?.5f:.12f,new Color(29,31,36));
        drawFeedback();
        try(GuiClip ignored=GuiClip.push(new ClipRect(valueX()+3,badgeY()+1,valueWidth()-6,badgeHeight()-2))){
            String text=editing?input+(error?" !":"_"):BigDecimal.valueOf(value.getValue()).stripTrailingZeros().toPlainString();
            if(!editing)text=ClickControlStyle.fit(font,text,valueWidth()-8);
            font.drawString(text,Math.min(valueX()+4,valueX()+valueWidth()-4-font.getWidth(text)),labelY,error?0xffff9c95:ink);
        }
    }
    private void drawPanelSlider(Theme theme, int mouseX, int mouseY) {
        TTFFontRenderer font = ClickControlStyle.font();
        float left = trackX(), center = trackY(), length = trackWidth();
        float fraction = Float.isNaN(displayedFraction) ? fraction() : displayedFraction;
        float fill = length * fraction;
        float energy = UiEffects.motion() ? Math.min(1, Math.abs(getPresentation().amount())) : 0;
        float radius = 3.2f + 1.8f * energy;
        int ink = theme.isCustom() ? theme.getTextColor().getRGB() : 0xffeef0f5;

        UIUtils.drawDecorationRound(left, center - 2, length, 4, 2, UiEffects.accent(ink, .19f));
        if (fill > .5f) {
            UIUtils.drawDecorationGradientVertical(left, center - 2, fill, 4, Math.min(2, fill / 2),
                    theme.getPrimaryGradientColor(), theme.getSecondaryGradientColor());
        }
        float cx = left + fill;
        ClickControlStyle.halo(cx - radius, center - radius, radius * 2, radius * 2, theme, .13f + .25f * energy);
        UIUtils.drawDecorationRound(cx - radius, center - radius + .8f, radius * 2, radius * 2, radius, new Color(0, 0, 0, 100));
        UIUtils.drawDecorationRound(cx - radius, center - radius, radius * 2, radius * 2, radius, new Color(hoverInk(0xfff2f5fb), true));
        if (energy > .01f) {
            float dot = 1.8f * energy;
            UIUtils.drawDecorationRound(cx - dot, center - dot, dot * 2, dot * 2, dot, theme.getPrimaryGradientColor());
        }

        font.drawString(ClickControlStyle.fit(font, UiEffects.settingLabel(value), valueX() - left - 5),
                left, y + 4, ink);
        ClickControlStyle.panelField(valueX(), badgeY(), valueWidth(), badgeHeight(), theme,
                editing || hit(mouseX, mouseY, valueX(), badgeY(), valueWidth(), badgeHeight()));
        drawFeedback();
        try (GuiClip ignored = GuiClip.push(new ClipRect(valueX()+3, badgeY()+1, valueWidth()-6, badgeHeight()-2))) {
            String text = editing ? input + (error ? " !" : "_")
                    : BigDecimal.valueOf(value.getValue()).stripTrailingZeros().toPlainString();
            font.drawString(text, valueX() + valueWidth() - 5 - font.getWidth(text), y + 4, error ? 0xffff9c95 : ink);
        }
    }

    private void updateValue(int mouseX){
        double progress=Math.max(0,Math.min(1,(mouseX-trackX())/trackWidth()));
        double next=value.getMin()+progress*(value.getMax()-value.getMin());
        value.setValue(BigDecimal.valueOf(next).setScale(2,RoundingMode.HALF_UP).doubleValue());
        if (panelStyle || compact()&&
                me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(
                        me.youngjibbit.theme.ThemeMode.selected()))
            displayedFraction=panelValue.snap(fraction());
    }
    @Override public void mouseClicked(int mouseX,int mouseY,int button){
        if(button==0&&hit(mouseX,mouseY,valueX(),badgeY(),valueWidth(),badgeHeight())){beginInput();return;}
        if(button==1&&hit(mouseX,mouseY,x+5,y,width-10,height)){beginInput();return;}
        if(button==0&&hit(mouseX,mouseY,trackX()-2,trackY()-7,trackWidth()+4,14)){cancelInput();dragging=true;updateValue(mouseX);}
    }
    @Override public void mouseDragged(int mouseX,int mouseY){
        if(!dragging)return;
        if(!acceptsMouse(mouseX,mouseY)||!value.isVisible()){dragging=false;return;}
        updateValue(mouseX);
    }
    public boolean isMouseOver(int mouseX,int mouseY){return acceptsMouse(mouseX,mouseY)&&presentationBounds().contains(mouseX,mouseY);}
    @Override public void mouseReleased(){dragging=false;}
    @Override public void keyTyped(char typedChar,int key){
        if(editing){
            if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE){cancelInput();return;}
            if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_NUMPADENTER){
                try{double next=Double.parseDouble(input);if(!Double.isFinite(next)||next<value.getMin()||next>value.getMax())throw new NumberFormatException();value.setValue(next);cancelInput();}catch(NumberFormatException ex){error=true;}return;
            }
            if(key==org.lwjgl.input.Keyboard.KEY_A&&org.lwjgl.input.Keyboard.isCreated()&&net.minecraft.client.gui.GuiScreen.isCtrlKeyDown()){replaceInput=true;return;}
            if(key==org.lwjgl.input.Keyboard.KEY_BACK){input=replaceInput?"":input.substring(0,Math.max(0,input.length()-1));replaceInput=false;error=false;return;}
            if("0123456789.-+eE".indexOf(typedChar)>=0&&(replaceInput||input.length()<32)){input=replaceInput?String.valueOf(typedChar):input+typedChar;replaceInput=false;error=false;}return;
        }
        if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_NUMPADENTER){beginInput();return;}
        if(key==org.lwjgl.input.Keyboard.KEY_LEFT||key==org.lwjgl.input.Keyboard.KEY_RIGHT){double step=value.isInteger()?1:(value.getMax()-value.getMin())/100;value.setValue(value.getValue()+(key==org.lwjgl.input.Keyboard.KEY_LEFT?-step:step));}
    }
}
