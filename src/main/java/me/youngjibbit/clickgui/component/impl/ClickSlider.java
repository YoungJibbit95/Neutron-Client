package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.SliderValue;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ClickSlider extends Component.Sub<SliderValue> {
    private boolean dragging,editing,replaceInput,error;
    private String input="";
    public boolean isEditing(){return editing;}
    public void cancelInput(){editing=false;dragging=false;error=false;}
    private void beginInput(){dragging=false;editing=true;replaceInput=true;error=false;input=Double.toString(value.getValue());}

    public ClickSlider(SliderValue value, float x, float y, float width, float height) {
        super(value, x, y, width, height);
    }

    private float fieldWidth() { return ClickControlStyle.fieldWidth(width); }
    private float fieldX() { return ClickControlStyle.right(x, width) - fieldWidth(); }
    private float valueWidth() { return Math.max(30, Math.min(54, fieldWidth() * .44f)); }
    private float valueX() { return ClickControlStyle.right(x, width) - valueWidth(); }
    private float trackX() { return fieldX(); }
    private float trackWidth() { return Math.max(1, fieldWidth() - valueWidth() - 10); }
    @Override public me.youngjibbit.clickgui.utils.ClipRect presentationBounds(){return new me.youngjibbit.clickgui.utils.ClipRect(fieldX(),y+3,fieldWidth(),height-5);}

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        if (dragging && !acceptsMouse(mouseX, mouseY)) dragging = false;
        if (dragging) updateValue(mouseX);
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        float range = (float) Math.max(0.001, value.getMax() - value.getMin());
        float fill = trackWidth() * (float) ((value.getValue() - value.getMin()) / range);
        UIUtils.drawRound(trackX(), y + 10, trackWidth(), 4, 2, new Color(64, 68, 77));
        if (fill > 0.5f) UIUtils.drawGradientHorizontal(trackX(), y + 10, fill, 4, Math.min(2, fill / 2),
                theme.getPrimaryGradientColor(), theme.getSecondaryGradientColor());
        UIUtils.drawRound(trackX() + fill - 3, y + 8, 6, 8, 3, new Color(dragging ? theme.getPrimaryGradientRGB() : 0xffe0e4ed, true));
        ClickControlStyle.label(value.getName(), x + 12, y + 6, fieldX() - x - 20, theme.isCustom()?theme.getTextColor().getRGB():0xffe4e4e8);
        me.youngjibbit.clickgui.utils.GuiStyle.panel(valueX(), y + 3, valueWidth(), 19, 4, editing ? 1 : isMouseOver(mouseX, mouseY) ? .5f : .12f, new Color(29,31,36));
        drawFeedback();
        try(me.youngjibbit.clickgui.utils.GuiClip ignored=me.youngjibbit.clickgui.utils.GuiClip.push(new me.youngjibbit.clickgui.utils.ClipRect(valueX()+3,y+3,valueWidth()-6,height-6))){
            String text = editing ? input + (error ? " !" : "_") : String.valueOf(value.getValue());
            me.youngjibbit.utils.font.TTFFontRenderer font = ClickControlStyle.font();
            if (!editing) text = ClickControlStyle.fit(font, text, valueWidth()-8);
            font.drawString(text, Math.min(valueX()+4, valueX()+valueWidth()-4-font.getWidth(text)), y+6, error?0xffff9c95:theme.isCustom()?theme.getTextColor().getRGB():0xffe4e4e8);
        }
    }

    private void updateValue(int mouseX) {
        double progress = Math.max(0, Math.min(1, (mouseX - trackX()) / trackWidth()));
        double next = value.getMin() + progress * (value.getMax() - value.getMin());
        value.setValue(BigDecimal.valueOf(next).setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && hit(mouseX,mouseY,valueX(),y+3,valueWidth(),19)){beginInput();return;}
        if (button == 1 && hit(mouseX,mouseY,x+8,y+2,width-16,height-4)){beginInput();return;}
        if (button == 0 && hit(mouseX, mouseY, trackX(), y + 4, trackWidth(), height - 8)) {
            cancelInput();dragging = true;
            updateValue(mouseX);
        }
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return hit(mouseX, mouseY, fieldX(), y + 3, fieldWidth(), height - 5);
    }

    @Override
    public void mouseReleased() { dragging = false; }
    @Override
    public void keyTyped(char typedChar,int key){
        if(editing){
            if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE){cancelInput();return;}
            if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_NUMPADENTER){
                try{double next=Double.parseDouble(input);if(Double.isNaN(next)||Double.isInfinite(next))throw new NumberFormatException();value.setValue(next);cancelInput();}catch(NumberFormatException ex){error=true;}return;
            }
            if(key==org.lwjgl.input.Keyboard.KEY_A&&org.lwjgl.input.Keyboard.isCreated()&&net.minecraft.client.gui.GuiScreen.isCtrlKeyDown()){replaceInput=true;return;}
            if(key==org.lwjgl.input.Keyboard.KEY_BACK){input=replaceInput?"":input.substring(0,Math.max(0,input.length()-1));replaceInput=false;error=false;return;}
            if("0123456789.-+eE".indexOf(typedChar)>=0&&input.length()<32){input=replaceInput?String.valueOf(typedChar):input+typedChar;replaceInput=false;error=false;}return;
        }
        if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_NUMPADENTER){beginInput();return;}
        if(key==org.lwjgl.input.Keyboard.KEY_LEFT||key==org.lwjgl.input.Keyboard.KEY_RIGHT){double step=value.isInteger()?1:(value.getMax()-value.getMin())/100;value.setValue(value.getValue()+(key==org.lwjgl.input.Keyboard.KEY_LEFT?-step:step));}
    }
}
