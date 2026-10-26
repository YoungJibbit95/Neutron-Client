package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.CheckBoxValue;
import java.awt.Color;

public class ClickCheckBox extends Component.Sub<CheckBoxValue> {
    private final DecelerateAnimation animation = new DecelerateAnimation(180, 1, Direction.BACKWARDS);

    public ClickCheckBox(CheckBoxValue value, float x, float y, float width, float height) {
        super(value, x, y, width, height);
        if (value.getValue()) animation.setDirection(Direction.FORWARDS);
        animation.timerUtil.setTime(System.currentTimeMillis() - 180);
    }

    private float toggleX() { return ClickControlStyle.right(x, width) - 25; }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        animation.setDirection(value.getValue() ? Direction.FORWARDS : Direction.BACKWARDS);
        float progress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion() ? animation.getOutput().floatValue() : value.getValue() ? 1f : 0f));
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        UIUtils.drawGradientHorizontal(toggleX(), y + 5, 25, 14, 7,
                new Color(Theme.interpolateRGB(new Color(70, 73, 80).getRGB(), theme.getPrimaryGradientRGB(), progress), true),
                new Color(Theme.interpolateRGB(new Color(70, 73, 80).getRGB(), theme.getSecondaryGradientRGB(), progress), true));
        UIUtils.drawRound(toggleX() + 1 + 11 * progress, y + 6, 12, 12, 6,
                new Color(Theme.interpolateRGB(new Color(210, 214, 222).getRGB(), (theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()), progress), true));
        ClickControlStyle.label(me.youngjibbit.utils.render.UiEffects.settingLabel(value), x + 12, y + 6, toggleX() - x - 20, theme.isCustom() ? theme.getTextColor().getRGB() : 0xffe4e4e8);
        if (hit(mouseX, mouseY, toggleX() - 3, y + 2, 31, 20)) UIUtils.drawRound(toggleX() + 9 + 11 * progress, y + 10, 3, 3, 1.5f, new Color(255,255,255,150));
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && hit(mouseX, mouseY, toggleX(), y + 5, 25, 14)) value.setValue(!value.getValue());
    }
    @Override
    public void keyTyped(char typedChar,int key){if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)value.setValue(!value.getValue());}
}
