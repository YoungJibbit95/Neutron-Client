package me.youngjibbit.gui.iosUi.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.utils.animations.Animation;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.EaseInOutQuad;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.ComboValue;

import java.awt.*;

public class iOSCombo extends iOSComponent.Sub<ComboValue>
{
    private static final float OPTION_HEIGHT = 22.0f;
    private boolean open;
    private final Animation openAnimation = new me.youngjibbit.clickgui.utils.ClickAnimation(170,1,Direction.BACKWARDS);

    public iOSCombo(ComboValue value, float x, float y, float width, float height)
    {
        super(value, x, y, width, height);
        openAnimation.timerUtil.setTime(System.currentTimeMillis()-170);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY)
    {
        openAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);
        float openProgress = openProgress();
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        int accent = UiEffects.gradientAt(theme,UiEffects.Channel.BORDER,x,y);
        float controlWidth = controlWidth();
        float controlX = controlX();
        float controlY = y + 4.0f;
        float controlHeight = height - 7.0f;

        UIUtils.drawRound(controlX, controlY, controlWidth, controlHeight, 5, true, new Color(0, 0, 0, 132));
        UIUtils.drawRoundOutline(controlX, controlY, controlWidth, controlHeight, 5, 0.45f, new Color(0, 0, 0, 1), UiEffects.accent(accent, 0.48f + openProgress * 0.2f));

        Pulsar.INSTANCE.getFontManager()
                .getFont("Poppins-Medium 20").drawString(
                        me.youngjibbit.clickgui.utils.ClickControlStyle.fit(Pulsar.INSTANCE.fontManager.getFont("Poppins-Medium 20"),UiEffects.settingLabel(value),controlX-x-18),
                        x + 10,
                        y + (float) mc.fontRendererObj.FONT_HEIGHT / 2 + 2,
                        theme.getTextColor().getRGB());

        Pulsar.INSTANCE.getFontManager()
                .getFont("Product-Sans-Medium 20").drawCenteredString(
                        label(value.getValue(),controlWidth-8,"Product-Sans-Medium 20"),
                        controlX + controlWidth / 2.0f,
                        y + (float) mc.fontRendererObj.FONT_HEIGHT / 2 + 3,
                        theme.getTextColor().getRGB());

        ClipRect options = optionsBounds(0, openProgress);
        if (!options.isEmpty())
        {
            UIUtils.drawRound(options.x, options.y, options.width, options.height, 5, true,
                    new Color(0, 0, 0, (int) (126 * openProgress)));
            // Preserve the rounded panel's antialias fringe; option ink stays inside its reveal.
            try (GuiClip ignored = GuiClip.pushTransformed(options)) {
                float i = 0.0f;
                for (String option : value.getOptions()) {
                    if (i > options.height + 1.0f) break;
                    Pulsar.INSTANCE.getFontManager()
                            .getFont("Product-Sans-Regular 20").drawCenteredString(
                                    label(option, options.width - 8, "Product-Sans-Regular 20"),
                                    options.x + options.width / 2.0f,
                                    options.y + 6.0f + i,
                                    option.equals(value.getValue()) ? accent : new Color(235, 238, 246, 220).getRGB());
                    i += OPTION_HEIGHT;
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouse)
    {
        if(mouse != 0)return;
        ClipRect options = optionsBounds(Pulsar.INSTANCE.getIOS().getScrollOffset(), openProgress());
        if (open && options.contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - options.y) / OPTION_HEIGHT);
            if (index >= 0 && index < value.getOptions().length) {
                value.setValue(value.getOptions()[index]);
                open = false;
                return;
            }
        }

        if(isMouseOver(mouseX, mouseY))
        {
            open = !open;
        }
    }

    private float controlWidth() {
        return Math.min(72.0f, Math.max(54.0f, width - 168.0f));
    }

    private float controlX() {
        return x + width - controlWidth() - 10.0f;
    }

    private float openProgress() {
        return Math.max(0, Math.min(1, UiEffects.motion() ? openAnimation.getOutput().floatValue() : open ? 1 : 0));
    }

    /** Same local reveal for paint and input; scroll is applied only to pointer-space bounds. */
    private ClipRect optionsBounds(float scrollOffset, float progress) {
        float revealed = progress > 0.01f ? value.getOptions().length * OPTION_HEIGHT * progress : 0;
        return new ClipRect(controlX(), y + height + 1.0f + scrollOffset, controlWidth(), revealed);
    }

    private String label(String reference,float width,String fontKey){
        String text=UiEffects.settingOption(value,reference);me.youngjibbit.utils.font.TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont(fontKey);
        if(font.getWidth(text)<=width)return text;while(text.length()>0&&font.getWidth(text+"...")>width)text=text.substring(0,text.length()-1);return text+"...";
    }
    public boolean isMouseOver(int mouseX, int mouseY)
    {
        float adjustedY = y + Pulsar.INSTANCE.getIOS().getScrollOffset();
        return mouseX >= x && mouseX <= x + width && mouseY >= adjustedY && mouseY <= adjustedY + height;
    }

    @Override
    public void cancelInputs() {
        open = false;
        openAnimation.setDirection(Direction.BACKWARDS);
    }

    @Override
    public void keyTyped(char typedChar, int key) {}

    @Override
    public float getHeight()
    {
        return height + value.getOptions().length * OPTION_HEIGHT * openProgress();
    }
}
