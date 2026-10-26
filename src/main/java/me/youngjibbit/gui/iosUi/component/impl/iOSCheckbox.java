package me.youngjibbit.gui.iosUi.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.ContinualAnimation;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.CheckBoxValue;

import java.awt.*;

public class iOSCheckbox extends iOSComponent.Sub<CheckBoxValue>
{
    private final ContinualAnimation switchAnimation = new ContinualAnimation();

    public iOSCheckbox(CheckBoxValue value, float x, float y, float width, float height)
    {
        super(value, x, y, width, height);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY)
    {
        switchAnimation.animate(value.getValue() ? 1.0f : 0.0f, 130);
        float progress = (UiEffects.motion()?switchAnimation.getOutput():value.getValue()?1:0);
        float switchX = x + width - 36.0f;
        float switchY = y + 5.0f;

        UIUtils.drawRound(switchX, switchY, 27.0f, 14.0f, 7.0f, true, new Color(0, 0, 0, 130));
        if (progress > 0.02f) {
            UIUtils.drawGradientHorizontal(switchX, switchY, 27.0f, 14.0f, 7.0f,
                    UiEffects.color(Theme.resolve(Theme.getDefaultTheme()).getPrimaryGradientRGB(), 0.35f + 0.45f * progress),
                    UiEffects.color(Theme.resolve(Theme.getDefaultTheme()).getSecondaryGradientRGB(), 0.35f + 0.45f * progress));
        }
        UIUtils.drawRound(switchX + 1.5f + progress * 12.5f, switchY + 1.5f, 11.0f, 11.0f, 5.5f, true, new Color(245, 247, 255, 230));

        Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 21").drawString(
                value.getName(),
                x + 52,
                y + 5,
                new Color(235, 238, 246, 230).getRGB()
        );
    }


    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouse)
    {
        if (isMouseOverCheckBox(mouseX, mouseY))
        {
            value.setValue(!value.getValue());
        }
    }

    private boolean isMouseOverCheckBox(int mouseX, int mouseY)
    {
        float checkboxX = x + width - 38.0f;
        float adjustedY = y + Pulsar.INSTANCE.getIOS().getScrollOffset();
        return mouseX >= checkboxX && mouseX <= checkboxX + 32.0f && mouseY >= adjustedY && mouseY <= adjustedY + height;

    }

    @Override
    public void keyTyped(char typedChar, int key)
    {
    }
}
