package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.GuiStyle;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.value.impl.ComboValue;
import java.awt.Color;

public class ClickCombo extends Component.Sub<ComboValue> {
    private boolean open;
    private float progress;
    private final DecelerateAnimation animation = new DecelerateAnimation(180, 1, Direction.BACKWARDS);

    public ClickCombo(ComboValue value, float x, float y, float width, float height) {
        super(value, x, y, width, height);
        animation.timerUtil.setTime(System.currentTimeMillis() - 180);
    }

    public void updateAnimation() {
        animation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);
        progress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion()?animation.getOutput().floatValue():open?1:0));
    }

    private float fieldX() { return ClickControlStyle.right(x, width) - fieldWidth(); }
    private float fieldWidth() { return ClickControlStyle.fieldWidth(width); }
    @Override public ClipRect presentationBounds(){return new ClipRect(fieldX(),y+3,fieldWidth(),height-5);}
    private ClipRect optionsClip() {
        ClipRect bounds = new ClipRect(x + 10, y + height, width - 20, value.getOptions().length * 24 * progress);
        return clip == null ? bounds : clip.intersect(bounds);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        ClickControlStyle.label(me.youngjibbit.utils.render.UiEffects.settingLabel(value), x + 12, y + 6, fieldX() - x - 20, theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB());
        GuiStyle.panel(fieldX(), y + 3, fieldWidth(), height - 5, 4, open || isMouseOver(mouseX, mouseY) ? 0.7f : 0.12f, new Color(29, 31, 36));
        drawFeedback();
        try (GuiClip ignored = GuiClip.push(new ClipRect(fieldX() + 3, y + 4, fieldWidth() - 6, height - 7))) {
            Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18").drawString(ClickControlStyle.fit(ClickControlStyle.font(), label(value.getValue()), fieldWidth() - 23),
                    fieldX() + 7, y + 6, theme.isCustom()?theme.getTextColor().getRGB():0xffe4e4e8);
            Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(open ? "h" : "i", fieldX() + fieldWidth() - 13, y + 7, 0xffb8bdc8);
        }
        ClipRect options = optionsClip();
        if (!options.isEmpty()) {
            try (GuiClip ignored = GuiClip.push(options)) {
                GuiStyle.panel(x + 10, y + height, width - 20, value.getOptions().length * 24, 4, 0, new Color(23, 23, 25));
                for (int i = 0; i < value.getOptions().length; i++) {
                    String option = value.getOptions()[i];
                    Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18").drawCenteredString(ClickControlStyle.fit(ClickControlStyle.font(), label(option), width - 36),
                            x + width / 2, y + height + 6 + i * 24,
                            option.equals(value.getValue()) ? theme.getPrimaryGradientRGB() : (theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()));
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        if (hit(mouseX, mouseY, fieldX(), y + 3, fieldWidth(), height - 5)) {
            open = !open;
            return;
        }
        if (open && optionsClip().contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - y - height) / 24);
            if (index >= 0 && index < value.getOptions().length) {
                value.setValue(value.getOptions()[index]);
                open = false;
            }
        }
    }

    private String label(String option){return me.youngjibbit.utils.render.UiEffects.settingOption(value,option);}
    public boolean isMouseOver(int mouseX, int mouseY) {
        return hit(mouseX, mouseY, fieldX(), y + 3, fieldWidth(), height - 5);
    }
    @Override
    public float getHeight() { return height + value.getOptions().length * 24 * progress; }
    public boolean isOpen(){return open;}
    public void close(){open=false;}
    @Override public void keyTyped(char typedChar,int key){
        if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE)close();
        else if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)open=!open;
        else if((key==org.lwjgl.input.Keyboard.KEY_LEFT||key==org.lwjgl.input.Keyboard.KEY_RIGHT)&&value.getOptions().length>0){int n=value.getOptions().length;value.setValue(value.getOptions()[(value.getSelectedIndex()+(key==org.lwjgl.input.Keyboard.KEY_LEFT?n-1:1))%n]);}
    }
}
