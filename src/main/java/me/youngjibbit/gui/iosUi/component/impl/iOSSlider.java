package me.youngjibbit.gui.iosUi.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.ContinualAnimation;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.SliderValue;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class iOSSlider extends iOSComponent.Sub<SliderValue>
{
    private static final float TRACK_WIDTH = 96.0f;
    private final ContinualAnimation fillAnimation = new ContinualAnimation();
    private boolean dragging;

    Theme currentTheme = applyTheme();

    public iOSSlider(SliderValue value, float x, float y, float width, float height)
    {
        super(value, x, y, width, height);
    }

    public Theme applyTheme() {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY)
    {
        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        Color themeColor1 = currentTheme.getPrimaryGradientColor();
        Color themeColor2 = currentTheme.getSecondaryGradientColor();

        float trackX = x + width - TRACK_WIDTH - 12.0f;
        float trackY = y + 8.0f;
        float trackHeight = Math.max(3.0f, height - 19.0f);
        double range = Math.max(0.001D, value.getMax() - value.getMin());
        float targetWidth = (float) (TRACK_WIDTH * ((value.getValue() - value.getMin()) / range));
        targetWidth = Math.max(0.0f, Math.min(TRACK_WIDTH, targetWidth));
        fillAnimation.animate(targetWidth, 120);
        float renderWidth = Math.max(0.0f, Math.min(TRACK_WIDTH, (UiEffects.motion()?fillAnimation.getOutput():targetWidth)));

        UIUtils.drawRound(trackX, trackY, TRACK_WIDTH, trackHeight, 2, new Color(0, 0, 0, 132));
        UIUtils.drawGradientRound(trackX, trackY, renderWidth, trackHeight, 2, themeColor1, themeColor1, themeColor2, themeColor2);

        Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 20").drawString(value.getName(), x + 31, y + 6, new Color(235, 238, 246, 230).getRGB());

        Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 19").drawString(String.valueOf(value.getValue()), x + 166, y + 6, Color.white.getRGB());

        double difference = Math.min(TRACK_WIDTH, Math.max(0, mouseX - trackX));
        if (dragging)
        {
            if (difference == 0)
            {
                value.setValue(value.getMin());
            } else {
                value.setValue(roundToPlace(((difference / TRACK_WIDTH) * (value.getMax() - value.getMin()) + value.getMin())));
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouse)
    {
        if (mouse == 0 && isMouseOver(mouseX, mouseY)) {
            dragging = true;
        }
    }

    @Override
    public void mouseReleased()
    {
        super.mouseReleased();
        dragging = false;
    }

    @Override
    public void keyTyped(char typedChar, int key)
    {
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        float scrollOffset = Pulsar.INSTANCE.getIOS().getScrollOffset();
        float sliderX = x + width - TRACK_WIDTH - 12.0f;
        float sliderY = y + scrollOffset + 8.0f;
        float sliderHeight = Math.max(3.0f, height - 19.0f);

        return mouseX >= sliderX && mouseX <= sliderX + TRACK_WIDTH && mouseY >= sliderY - 4.0f && mouseY <= sliderY + sliderHeight + 6.0f;
    }

    private double roundToPlace(double value) {
        BigDecimal bigDecimal = new BigDecimal(value);
        return bigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int interpolateTwoColorsSmoothly(int color1, int color2, long currentTime, float cycleDuration) {
        // Calculate the oscillating factor using a sine wave
        float progress = (currentTime % (long) cycleDuration) / cycleDuration; // Normalized cycle [0, 1]
        float factor = (float) (Math.sin(progress * 2 * Math.PI) * 0.5 + 0.5); // Sin wave mapped to [0, 1]

        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;

        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;

        int red = (int) (red1 + (red2 - red1) * factor);
        int green = (int) (green1 + (green2 - green1) * factor);
        int blue = (int) (blue1 + (blue2 - blue1) * factor);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue; // Include alpha
    }

    private int[] getStaticOutlineGradientColors(Theme theme)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        return new int[]{resolvedTheme.getPrimaryGradientRGB(), resolvedTheme.getSecondaryGradientRGB()};
    }

}
