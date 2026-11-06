package me.youngjibbit.gui.iosUi.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.PanelUtils;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.input.MouseUtil;
import me.youngjibbit.utils.animations.Animation;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.utils.animations.impl.EaseInOutQuad;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class iOSModule extends iOSComponent
{
    private final Module module;
    private final List<iOSComponent.Sub<?>> VALUES = new ArrayList<>();
    private boolean open;
    private final PanelUtils panelUtils = new PanelUtils();
    private final Animation toggleAnimation = new me.youngjibbit.clickgui.utils.ClickAnimation(180,1,Direction.BACKWARDS);
    private final Animation openAnimation = new me.youngjibbit.clickgui.utils.ClickAnimation(220,1,Direction.BACKWARDS);
    Theme currentTheme = applyTheme();
    int[] outlineColors = getStaticOutlineGradientColors(currentTheme);

    public iOSModule(Module module, float x, float y, float width, float height)
    {
        super(x, y, width, height);
        this.module = module;
        this.addValues();
        this.toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        this.openAnimation.setDirection(Direction.BACKWARDS);
        this.openAnimation.timerUtil.setTime(System.currentTimeMillis()-220);
        this.toggleAnimation.timerUtil.setTime(System.currentTimeMillis()-180);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY)
    {
        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        int interpolatedColor1=UiEffects.gradientAt(currentTheme,UiEffects.Channel.BORDER,x,y);
        toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        openAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);

        float toggleProgress = (UiEffects.motion()?toggleAnimation.getOutput().floatValue():module.isToggled()?1:0);
        float openProgress = (UiEffects.motion()?openAnimation.getOutput().floatValue():open?1:0);
        for (Sub<?> value : VALUES) {
            if (!visible(value)) value.mouseReleased();
        }
        int accentColor = module.isToggled() ? interpolatedColor1 : Color.WHITE.getRGB();
        int cardAlpha = (int) (126 + toggleProgress * 42);

        UIUtils.drawRound(x + 1.0f, y + 3.0f, width - 8.0f, getHeight() - 3.0f, 6, true, new Color(8, 10, 14, cardAlpha));
        UIUtils.drawRoundOutline(x + 1.0f, y + 3.0f, width - 8.0f, getHeight() - 3.0f, 6, 0.55f, new Color(0, 0, 0, 1), UiEffects.accent(accentColor, module.isToggled() ? 0.58f : 0.18f));

        if (open || openProgress > 0.05f)
        {
            panelUtils.icon2("h", (int) (x + 214), (int) (y + 8), currentTheme);
        } else {
            panelUtils.icon2("i", (int) (x + 214), (int) (y + 8), currentTheme);
        }

        Pulsar.INSTANCE.getFontManager().getFont("SFProText-Medium 22").drawString(
                me.youngjibbit.clickgui.utils.ClickControlStyle.fit(Pulsar.INSTANCE.fontManager.getFont("SFProText-Medium 22"),module.getName(),width-42),
                x + 6,
                y + (float) mc.fontRendererObj.FONT_HEIGHT / 2 + 2,
                Theme.interpolateRGB(currentTheme.getTextColor().getRGB(),interpolatedColor1,toggleProgress)
        );

        if (openProgress > 0.01f)
        {
            try(GuiClip clip=GuiClip.pushTransformed(new ClipRect(x+1,y+height,width-8,getHeight()-height))) {
            int i = 0;
            String desc = module.getDescription();
            int descAlpha = (int) (120 + 95 * openProgress);
            Pulsar.INSTANCE.getFontManager().getFont("SFProText-Medium 17").drawString(me.youngjibbit.clickgui.utils.ClickControlStyle.fit(Pulsar.INSTANCE.fontManager.getFont("SFProText-Medium 17"),desc,width-24), x + 7, y + (float) mc.fontRendererObj.FONT_HEIGHT / 2 + 18, new Color(188, 195, 210, descAlpha).getRGB());

            for (Sub<?> value : VALUES)
            {
                if (!visible(value)) continue;
                value.updateComponent(x, y + 55 + i);
                value.drawScreen(mouseX, mouseY);
                i += value.getHeight() + 1;
            }
            }
        }
    }

    public Theme applyTheme() {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    @Override
    public float getHeight()
    {
        float openProgress = (UiEffects.motion()?openAnimation.getOutput().floatValue():open?1:0);
        float expandedHeight = (float) (VALUES.stream().filter(this::visible).mapToDouble(value -> value.getHeight() + 1).sum() + 50.0);
        return super.getHeight() + expandedHeight * openProgress;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouse)
    {
        if (isMouseOverModule(mouseX, mouseY)) {
            switch (mouse) {
                case 0:
                    module.toggle();
                    break;
                case 1:
                    open = !open;
                    if (!open) cancelInputs();
                    break;
            }
        }

        if (open && mouseY>=y+height+Pulsar.INSTANCE.getIOS().getScrollOffset() && mouseY<y+getHeight()+Pulsar.INSTANCE.getIOS().getScrollOffset())
        {
            // Newly revealed controls have no layout for this click yet. Do not reuse stale coordinates.
            List<Sub<?>> enabled=new ArrayList<>();
            for(Sub<?> value:VALUES) {
                if(visible(value)) enabled.add(value);
                else value.mouseReleased();
            }
            for(Sub<?> value:enabled) {
                if(visible(value)) value.mouseClicked(mouseX,mouseY,mouse);
                else value.mouseReleased();
            }
        }
    }
    /*private boolean isMouseOverModule(int mouseX, int mouseY)
    {
        float adjustedY = y + Pulsar.INSTANCE.getPanel().getScrollOffset();
        return mouseX >= x && mouseX <= x + width && mouseY >= adjustedY && mouseY <= adjustedY + height;
    }*/

    private boolean isMouseOverModule(int mouseX, int mouseY)
    {
        float adjustedY = y + Pulsar.INSTANCE.getIOS().getScrollOffset();
        return mouseX >= x && mouseX <= x + width && mouseY >= adjustedY && mouseY <= adjustedY + height;
    }

    public boolean isMouseOver(int mouseX, int mouseY)
    {
        return MouseUtil.isMouseOver(mouseX, mouseY, x, y, width, 24);
    }

    private boolean visible(Sub<?> component) {
        return ((Value<?>) component.getValue()).isVisible();
    }

    private void addValues()
    {
        for (Value<?> value : module.getValues())
        {
            if (value instanceof CheckBoxValue)
            {
                VALUES.add(new iOSCheckbox((CheckBoxValue) value, 0, 0, width, 24));
            }
            if (value instanceof ComboValue)
            {
                VALUES.add(new iOSCombo((ComboValue) value, 0, 0, width, 24));
            }
            if (value instanceof SliderValue)
            {
                VALUES.add(new iOSSlider((SliderValue) value, 0, 0, width, 24));
            }
        }
    }

    @Override
    public void cancelInputs() { VALUES.forEach(Sub::cancelInputs); }

    @Override
    public void mouseReleased()
    {
        VALUES.forEach(Sub::mouseReleased);
    }

    @Override
    public void keyTyped(char typedChar, int key)
    {
    }

    private int getColorFromTheme(Theme theme, float offset)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        return resolvedTheme.getGradientRGB(offset);
    }

    private static int interpolateTwoColorsSmoothly(int color1, int color2, long currentTime, float cycleDuration) {

        float progress = (currentTime % (long) cycleDuration) / cycleDuration;
        float factor = (float) (Math.sin(progress * 2 * Math.PI) * 0.5 + 0.5);

        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;

        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;

        int red = (int) (red1 + (red2 - red1) * factor);
        int green = (int) (green1 + (green2 - green1) * factor);
        int blue = (int) (blue1 + (blue2 - blue1) * factor);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }

    private int[] getStaticOutlineGradientColors(Theme currentTheme)
    {
        Theme resolvedTheme = Theme.resolve(currentTheme);
        int color1 = resolvedTheme.getPrimaryGradientRGB();
        int color2 = resolvedTheme.getSecondaryGradientRGB();

        return new int[]
                {
                        color1,
                        color2,
                        color1,
                        color2
                };
    }
}
