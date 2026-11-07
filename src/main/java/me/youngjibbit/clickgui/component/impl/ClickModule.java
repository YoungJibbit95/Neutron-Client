package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.GuiStyle;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ClickModule extends Component {
    private final Module module;
    private final List<Sub<?>> values = new ArrayList<>();
    private boolean open;
    private final DecelerateAnimation openAnimation = new me.youngjibbit.clickgui.utils.ClickAnimation(220, 1, Direction.BACKWARDS);
    private final DecelerateAnimation toggleAnimation = new me.youngjibbit.clickgui.utils.ClickAnimation(180, 1, Direction.BACKWARDS);
    private final me.youngjibbit.gui.UiSpring panelReveal = new me.youngjibbit.gui.UiSpring();
    private float openProgress, toggleProgress, settingsContentHeight;
    private ClipRect settingsClip = new ClipRect(0, 0, 0, 0);

    public ClickModule(Module module, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.module = module;
        openAnimation.timerUtil.setTime(System.currentTimeMillis() - 220);
        toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        toggleAnimation.timerUtil.setTime(System.currentTimeMillis() - 180);
        for (Value<?> value : module.getValues()) {
            if (value instanceof CheckBoxValue) values.add(new ClickCheckBox((CheckBoxValue) value, 0, 0, width, 24));
            if (value instanceof ComboValue) values.add(new ClickCombo((ComboValue) value, 0, 0, width, 24));
            if (value instanceof SliderValue) values.add(new ClickSlider((SliderValue) value, 0, 0, width, 24));
        }
        updateAnimation();
    }

    public void updateAnimation() {
        openAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);
        toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        openProgress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion()?openAnimation.getOutput().floatValue():open?1:0));
        toggleProgress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion()?toggleAnimation.getOutput().floatValue():module.isToggled()?1:0));
        settingsContentHeight = compactSettings() ? 4 : 24;
        for (Sub<?> value : values) {
            if (!visible(value)) continue;
            if (value instanceof ClickCombo) ((ClickCombo) value).updateAnimation();
            settingsContentHeight += value.getHeight() + 1;
        }
    }

    /** Panel-only reveal; the compact Dropdown retains its existing timing. */
    public void updatePanelAnimation(float seconds) {
        updateAnimation();
        openProgress = Math.max(0, Math.min(1.08f, panelReveal.transition(open ? 1 : 0, seconds,
                me.youngjibbit.module.impl.client.ClientSettings.current())));
    }

    protected boolean compactSettings() { return false; }
    @Override public void setWidth(float next) {
        width=next;
        for(Sub<?> value:values)value.setWidth(compactSettings()?width-4:width);
    }
    @Override public void mouseDragged(int mouseX,int mouseY) {
        if(open)for(Sub<?> value:values)if(visible(value))value.mouseDragged(mouseX,mouseY);
    }

    public void layout(float x, float y, float width, ClipRect parentClip) {
        updateComponent(x, y);
        setWidth(width);
        this.clip = parentClip;
        settingsClip = parentClip.intersect(new ClipRect(x + 1, y + height, width - 2, settingsContentHeight * openProgress));
        float valueY = y + height + (compactSettings() ? 2 : 20);
        for (Sub<?> value : values) {
            if (!visible(value)) {
                value.setClip(new ClipRect(0, 0, 0, 0));
                value.mouseReleased();
                continue;
            }
            value.updateComponent(x + (compactSettings() ? 2 : 0), valueY);
            value.setWidth(compactSettings() ? width - 4 : width);
            value.setClip(settingsClip);
            if (!open || settingsClip.intersect(new ClipRect(x, valueY, width, value.getHeight())).isEmpty()) value.mouseReleased();
            valueY += value.getHeight() + 1;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        boolean hover = isMouseOver(mouseX, mouseY);
        GuiStyle.panel(x, y, width, getHeight(), 4, toggleProgress,
                hover ? new Color(25, 25, 26) : new Color(17, 17, 18));
        if (module.isToggled()) {
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x + 4, y + 6, 2, height - 12, 1,
                    theme.getPrimaryGradientColor());
        }
        drawFeedback();
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(openProgress > 0.5f ? "h" : "i",
                x + width - 17, y + 7, (theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()));
        String binding=module.getKey()==0?"-":org.lwjgl.input.Keyboard.getKeyName(module.getKey());
        if(binding==null)binding="?";
        int conflicts=0;for(Module other:Pulsar.INSTANCE.getMm().getAllModules())if(other!=module&&module.getKey()!=0&&module.getKey()==other.getKey())conflicts++;
        if(conflicts>0)binding+=" !";
        me.youngjibbit.utils.font.TTFFontRenderer small=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");
        binding = ClickControlStyle.fit(small, binding, Math.max(16, Math.min(64, width * .25f)));
        float bindingWidth = Math.max(20, small.getWidth(binding) + 10);
        float bindingX = x + width - 28 - bindingWidth;
        GuiStyle.panel(bindingX, y + 4, bindingWidth, 16, 3, conflicts > 0 ? .5f : .05f, new Color(30, 32, 37));
        small.drawCenteredString(binding, bindingX + bindingWidth / 2, y + 7, conflicts > 0 ? 0xffffb975 : 0xffbec4d0);
        me.youngjibbit.utils.font.TTFFontRenderer title = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 20");
        title.drawString(ClickControlStyle.fit(title, module.getName(), bindingX - x - 20), x + 12, y + 6,
                hoverInk(Theme.interpolateRGB(theme.isCustom()?theme.getTextColor().getRGB():0xfff0f1f5, theme.getPrimaryGradientRGB(), toggleProgress)));
        drawSettings(mouseX, mouseY);
    }

    /** Shared settings renderer for the panel and compact dropdown presentations. */
    protected final void drawSettings(int mouseX, int mouseY) {
        if (!settingsClip.isEmpty()) {
            try (GuiClip ignored = GuiClip.push(settingsClip)) {
                if(compactSettings())me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+1,y+height,width-2,settingsContentHeight,0,new Color(9,12,19,145));
                if(!compactSettings())Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString("Settings", x + 12, y + height + 5,
                        new Color(180, 180, 185).getRGB());
                for (Sub<?> value : values) {
                    if (!visible(value)) continue;
                    if (!settingsClip.intersect(new ClipRect(value.getX(), value.getY(), value.getWidth(), value.getHeight())).isEmpty()) {
                        value.drawScreen(open ? mouseX : -1, open ? mouseY : -1);
                    }
                }
            }
        }
    }

    protected final float getToggleProgress() { return toggleProgress; }

    @Override
    public float getHeight() { return height + settingsContentHeight * openProgress; }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return hit(mouseX, mouseY, x, y, width, height);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            if (button == 1 || (button == 0 && mouseX >= x + width - 24)) {
                setOpen(!open);
            } else if (button == 0) {
                module.toggle();
            }
            return;
        }
        if (open && settingsClip.contains(mouseX, mouseY)) {
            for (Sub<?> value : values) if (visible(value)) value.mouseClicked(mouseX, mouseY, button);
        }
    }

    private boolean visible(Sub<?> value) {
        return ((Value<?>) value.getValue()).isVisible();
    }

    @Override
    public void mouseReleased() { for (Sub<?> value : values) value.mouseReleased(); }

    public Module getModule(){return module;}
    public List<Sub<?>> presentationValues(){return java.util.Collections.unmodifiableList(values);}
    public boolean isOpen(){return open;}
    public void setOpen(boolean next){open=next;mouseReleased();if(!next)cancelInputs();}
    public List<Sub<?>> focusableValues() {
        List<Sub<?>> result = new ArrayList<>();
        if (!open) return result;
        // Use the module reveal, not settingsClip: offscreen settings remain Tab destinations.
        // The inherited header is 24px for Panel and 16px for Dropdown.
        ClipRect reveal = new ClipRect(x + 1, y + height, width - 2, settingsContentHeight * openProgress);
        for (Sub<?> value : values) {
            if (visible(value) && !reveal.intersect(value.presentationBounds()).isEmpty()) result.add(value);
        }
        return result;
    }
    public void cancelInputs(){for(Sub<?> v:values){v.mouseReleased();if(v instanceof ClickSlider)((ClickSlider)v).cancelInput();if(v instanceof ClickCombo)((ClickCombo)v).close();}}
    @Override public void keyTyped(char typedChar,int key){
        if(key==org.lwjgl.input.Keyboard.KEY_RIGHT)setOpen(true);
        else if(key==org.lwjgl.input.Keyboard.KEY_LEFT)setOpen(false);
        else if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)module.toggle();
    }
}
