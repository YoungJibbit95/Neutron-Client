package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import java.awt.Color;

/** Compact presentation; values, visibility, animation and editing belong to ClickModule. */
public final class DropdownModule extends ClickModule {
    public static final float ROW_HEIGHT = 14;
    public DropdownModule(Module module) { super(module, 0, 0, 104, ROW_HEIGHT); setWidth(104); resetCompactMotion(); }
    @Override protected boolean compactSettings() { return true; }

    @Override public void drawScreen(int mouseX, int mouseY) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        float active = getToggleProgress();
        // One vertical accent ramp across the category, as in the reference.
        if (active > .001f) {
            UIUtils.drawDecorationGradientVertical(x, y, width, ROW_HEIGHT, 0,
                    UiEffects.color(theme.getPrimaryGradientRGB(), active * .72f),
                    UiEffects.color(theme.getSecondaryGradientRGB(), active * .72f));
        }
        drawFeedback();
        TTFFontRenderer font = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 16");
        String title = ClickControlStyle.fit(font, getModule().getName(), width - 20);
        font.drawCenteredString(title, x + width / 2, y + (ROW_HEIGHT - font.getHeight(title)) / 2,
                hoverInk(theme.isCustom() ? theme.getTextColor().getRGB() : 0xfff4f0fa));
        // An explicit shape cue supplements color for enabled modules.
        if (getModule().isToggled())
            UIUtils.drawDecorationRound(x + 3, y + ROW_HEIGHT / 2 - 1, 2, 2, 1, new Color(245, 241, 250));
        if (isOpen()) {
            UIUtils.drawDecorationRound(x + width - 8, y + 7, 5, 1, 0, new Color(245, 241, 250));
        }
        drawSettings(mouseX, mouseY);
    }

    @Override public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            if (button == 0) getModule().toggle();
            else if (button == 1) setOpen(!isOpen());
        } else super.mouseClicked(mouseX, mouseY, button);
    }
}
