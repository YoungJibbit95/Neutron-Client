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
    private final DecelerateAnimation openAnimation = new DecelerateAnimation(220, 1, Direction.BACKWARDS);
    private final DecelerateAnimation toggleAnimation = new DecelerateAnimation(180, 1, Direction.BACKWARDS);
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
        settingsContentHeight = 24;
        for (Sub<?> value : values) {
            if (!visible(value)) continue;
            if (value instanceof ClickCombo) ((ClickCombo) value).updateAnimation();
            settingsContentHeight += value.getHeight() + 1;
        }
    }

    public void layout(float x, float y, float width, ClipRect parentClip) {
        updateComponent(x, y);
        this.width = width;
        this.clip = parentClip;
        settingsClip = parentClip.intersect(new ClipRect(x + 1, y + height, width - 2, settingsContentHeight * openProgress));
        float valueY = y + height + 20;
        for (Sub<?> value : values) {
            if (!visible(value)) {
                value.setClip(new ClipRect(0, 0, 0, 0));
                value.mouseReleased();
                continue;
            }
            value.updateComponent(x, valueY);
            value.setWidth(width);
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
        title.drawString(ClickControlStyle.fit(title, module.getName(), bindingX - x - 18), x + 10, y + 6,
                Theme.interpolateRGB(theme.isCustom()?theme.getTextColor().getRGB():0xfff0f1f5, theme.getPrimaryGradientRGB(), toggleProgress));
        if (!settingsClip.isEmpty()) {
            try (GuiClip ignored = GuiClip.push(settingsClip)) {
                Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString("Settings", x + 12, y + height + 5,
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
    public List<Sub<?>> focusableValues(){List<Sub<?>> result=new ArrayList<>();if(open)for(Sub<?> v:values)if(visible(v))result.add(v);return result;}
    public void cancelInputs(){for(Sub<?> v:values){v.mouseReleased();if(v instanceof ClickSlider)((ClickSlider)v).cancelInput();if(v instanceof ClickCombo)((ClickCombo)v).close();}}
    @Override public void keyTyped(char typedChar,int key){
        if(key==org.lwjgl.input.Keyboard.KEY_RIGHT)setOpen(true);
        else if(key==org.lwjgl.input.Keyboard.KEY_LEFT)setOpen(false);
        else if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)module.toggle();
    }
}
