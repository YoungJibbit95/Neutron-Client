package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.GuiStyle;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.module.impl.client.ClientSettings;
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
    private final HudSpring panelToggle=new HudSpring();
    private final HudSpring compactReveal=new HudSpring();
    private float toggleBounce;
    private float openProgress, toggleProgress, settingsContentHeight;
    private ClipRect settingsClip = new ClipRect(0, 0, 0, 0);

    private boolean panelStyle,modernCard;
    private static final float PANEL_TOP = 8, PANEL_BOTTOM = 8, PANEL_GAP = 3;

    public static ClickModule forPanel(Module module,float width) {
        return forPanel(module,width,false);
    }
    public static ClickModule forPanel(Module module,float width,boolean modern) {
        ClickModule row=new ClickModule(module,0,0,width,
                modern?NeutronVisualTokens.CARD_HEIGHT:24);
        row.panelStyle=true;row.modernCard=modern;
        for(Sub<?> value:row.values)value.usePanelStyle();
        row.resetPanelMotion();
        return row;
    }

    private float settingsTop() {
        if (panelStyle) return PANEL_TOP;
        return compactSettings() ? 2 : 20;
    }

    private float valueGap() {
        return panelStyle ? PANEL_GAP : 1;
    }

    public ClickModule(Module module, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.module = module;
        openAnimation.timerUtil.setTime(System.currentTimeMillis() - 220);
        toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        toggleAnimation.timerUtil.setTime(System.currentTimeMillis() - 180);
        for (Value<?> value : me.youngjibbit.clickgui.utils.SettingDisplayOrder.values(module)) {
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
        settingsContentHeight = settingsTop() + (panelStyle ? PANEL_BOTTOM : compactSettings() ? 2 : 4);
        for (Sub<?> value : values) {
            if (!visible(value)) continue;
            if (value instanceof ClickCombo) ((ClickCombo) value).updateAnimation();
            settingsContentHeight += value.getHeight() + valueGap();
        }
    }

    /** Visual state only; never resets a toggled Module or a saved setting. */
    public void resetPanelMotion(){
        panelReveal.reset();
        panelToggle.snap(module.isToggled()?1:0);
        toggleBounce=0;
        for(Sub<?> value:values){
            if(value instanceof ClickCombo)((ClickCombo)value).resetPanelMotion();
            if(value instanceof ClickCheckBox)((ClickCheckBox)value).resetPanelMotion();
            if(value instanceof ClickSlider)((ClickSlider)value).resetPanelMotion();
        }
    }

    /** The compact dropdown Spring is opt-in; legacy ClickAnimation fields stay intact. */
    public void resetCompactMotion(){
        compactReveal.snap(open?1:0);
        panelToggle.snap(module.isToggled()?1:0);
        toggleBounce=0;
        for(Sub<?> value:values)
            if(value instanceof ClickCombo)((ClickCombo)value).resetPanelMotion();
    }

    public void updateCompactMotion(float seconds) {
        updateAnimation(); // Preserve the exact Legacy path when the mode is not active.
        if(!compactSettings() || !me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(
                me.youngjibbit.theme.ThemeMode.selected()))return;
        ClientSettings settings=ClientSettings.current();
        boolean animated=settings==null||settings.animations.getValue();
        boolean reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        float toggle=panelToggle.step(module.isToggled()?1:0,seconds,animated,reduced,intensity);
        toggleProgress=Math.max(0,Math.min(1,toggle));
        toggleBounce=Math.min(.1f,Math.abs(toggle-toggleProgress));
        float openValue=compactReveal.step(open?1:0,seconds,animated,reduced,intensity);
        openProgress=Math.max(0,Math.min(1,openValue));
        settingsContentHeight=settingsTop()+2;
        for(Sub<?> value:values){
            if(!visible(value))continue;
            if(value instanceof ClickCombo)((ClickCombo)value).updatePanelAnimation(seconds);
            settingsContentHeight+=value.getHeight()+valueGap();
        }
    }

    /** Panel-only springs; compact Dropdown retains its original animation path. */
    public void updatePanelAnimation(float seconds) {
        updateAnimation();
        ClientSettings settings=ClientSettings.current();
        float toggle=panelToggle.step(module.isToggled()?1:0,seconds,
                settings==null||settings.animations.getValue(),
                settings!=null&&settings.reducedMotion.getValue(),
                settings==null?"Balanced":settings.motionIntensity.getValue());
        toggleProgress=Math.max(0,Math.min(1,toggle));
        toggleBounce=Math.min(.1f,Math.abs(toggle-toggleProgress));
        openProgress=Math.max(0,Math.min(1.08f,panelReveal.transition(
                open?1:0,seconds,settings)));
        // Combo geometry must be frozen from the same spring sample as the clip.
        settingsContentHeight=settingsTop()+PANEL_BOTTOM;
        for(Sub<?> value:values){
            if(!visible(value))continue;
            if(value instanceof ClickCombo)((ClickCombo)value).updatePanelAnimation(seconds);
            settingsContentHeight+=value.getHeight()+valueGap();
        }
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
        float valueY = y + height + settingsTop();
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
            valueY += value.getHeight() + valueGap();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        boolean hover = isMouseOver(mouseX, mouseY);
        if (panelStyle) {
            drawPanelHeader(theme, hover);
            drawSettings(mouseX, mouseY);
            return;
        }
        GuiStyle.panel(x, y, width, getHeight(), 6, toggleProgress,
                hover ? new Color(29, 29, 32) : new Color(20, 20, 23));
        // A quiet inner edge separates adjacent glass cards without another surface pass.
        me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x + 9, y + 2, Math.max(0, width - 18), 1, .5f,
                new Color(235, 239, 248, hover ? 35 : 20));
        if (module.isToggled()) {
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x + 4, y + 6, 2, height - 12, 1,
                    theme.getPrimaryGradientColor());
        }
        drawFeedback();
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(openProgress > 0.5f ? "h" : "i",
                x + width - 17, y + 7, (theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()));
        String binding=module.getKey()==0?"-":org.lwjgl.input.Keyboard.getKeyName(module.getKey());
        if(binding==null)binding="?";
        boolean conflict = keyConflict();
        if(conflict)binding+=" !";
        me.youngjibbit.utils.font.TTFFontRenderer small=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");
        binding = ClickControlStyle.fit(small, binding, Math.max(16, Math.min(64, width * .25f)));
        float bindingWidth = Math.max(20, small.getWidth(binding) + 10);
        float bindingX = x + width - 28 - bindingWidth;
        GuiStyle.panel(bindingX, y + 4, bindingWidth, 16, 3, conflict ? .5f : .05f, new Color(30, 32, 37));
        small.drawCenteredString(binding, bindingX + bindingWidth / 2, y + 7, conflict ? 0xffffb975 : 0xffbec4d0);
        me.youngjibbit.utils.font.TTFFontRenderer title = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 20");
        title.drawString(ClickControlStyle.fit(title, module.getName(), bindingX - x - 20), x + 12, y + 6,
                hoverInk(Theme.interpolateRGB(theme.isCustom()?theme.getTextColor().getRGB():0xfff0f1f5, theme.getPrimaryGradientRGB(), toggleProgress)));
        drawSettings(mouseX, mouseY);
    }

    private boolean keyConflict() {
        if (module.getKey() == 0) return false;
        for (Module other : Pulsar.INSTANCE.getMm().getAllModules())
            if (other != module && other.getKey() == module.getKey()) return true;
        return false;
    }

    private void drawPanelHeader(Theme theme, boolean hover) {
        if(modernCard){
            drawNeutronTwoHeader(theme,hover);
            return;
        }
        // Module hover stays anchored, even while the settings below grow or scroll.
        int text = theme.getTextColor().getRGB();
        float over = Math.max(0, Math.min(1, getPresentation().amount()));
        int base = theme.isCustom() ? theme.getBackground().getRGB() : 0xff17181d;
        int fill = Theme.interpolateRGB(base, text, over * .045f);
        GuiStyle.panel(x, y, width, getHeight(), 6, .09f + toggleProgress * .17f, new Color(fill, true));
        float dotX = x + 10, dotY = y + height / 2 - 2;
        if (module.isToggled()||toggleProgress>.005f){
            float dot=4+toggleBounce*18;
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(dotX-(dot-4)/2,
                    dotY-(dot-4)/2,dot,dot,dot/2,
                    me.youngjibbit.utils.render.UiEffects.color(
                            theme.getPrimaryGradientRGB(),.24f+.76f*toggleProgress));
        }
        if (getPresentation().keyboardFocused())
            Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString("â€º", x + 3, y + 6, text);

        me.youngjibbit.utils.font.TTFFontRenderer title = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
        float right = x + width - 28;
        if (module.getKey() != 0) {
            String key = org.lwjgl.input.Keyboard.getKeyName(module.getKey());
            if (key == null) key = "?";
            boolean conflict = keyConflict();
            if (conflict) key += " !";
            key = ClickControlStyle.fit(title, key, Math.min(60, width / 4));
            right -= title.getWidth(key);
            title.drawString(key, right, y + 6, conflict ? 0xffffb975 : me.youngjibbit.utils.render.UiEffects.alpha(text, .72f));
        }
        int titleInk = Theme.interpolateRGB(text, theme.getPrimaryGradientRGB(), toggleProgress * .34f);
        title.drawString(ClickControlStyle.fit(title, module.getName(), right - x - 24), x + 19, y + 6, titleInk);
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(openProgress > .5f ? "h" : "i",
                x + width - 17, y + 7, me.youngjibbit.utils.render.UiEffects.alpha(text, hover || open ? .95f : .62f));
    }

    /** Restrained 2.0 cards: dark neutral backing; color is a fine semantic cue. */
    private void drawNeutronTwoHeader(Theme theme,boolean hover){
        float response=Math.max(0,Math.min(1,toggleProgress));
        float energy=Math.max(0,Math.min(1,Math.abs(getPresentation().amount())));
        float top=y,cardHeight=getHeight();
        me.youngjibbit.utils.render.UIUtils.drawRound(
                x,top,width,cardHeight,NeutronVisualTokens.RADIUS_CARD,
                hover?NeutronVisualTokens.CARD_HOVER:NeutronVisualTokens.CARD);
        me.youngjibbit.utils.render.UIUtils.drawRoundOutline(
                x+.5f,top+.5f,Math.max(1,width-1),Math.max(1,cardHeight-1),
                NeutronVisualTokens.RADIUS_CARD,.55f,new Color(0,0,0,1),
                new Color(223,205,228,Math.round(18+15*energy)));
        if(response>.003f){
            float line=Math.min(36,Math.max(0,cardHeight-11));
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(
                    x+2,top+6,2,line,1,
                    NeutronVisualTokens.accent(theme,Math.round(100+140*response)));
        }
        if(getPresentation().keyboardFocused())
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(
                    x+7,top+7,2,cardHeight-14,1,NeutronVisualTokens.secondary(theme,235));
        me.youngjibbit.utils.font.TTFFontRenderer title=
                Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
        me.youngjibbit.utils.font.TTFFontRenderer descFont=
                Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Regular 16");
        String name=ClickControlStyle.fit(title,module.getName(),Math.max(16,width-96));
        title.drawString(name,x+15,y+7,NeutronVisualTokens.INK);
        String description=module.getDescription();
        if(description==null||description.trim().isEmpty())description="Module settings";
        descFont.drawString(ClickControlStyle.fit(descFont,description,Math.max(16,width-110)),
                x+15,y+24,NeutronVisualTokens.MUTED);

        // Retargeted spring travels within a fixed non-interactive switch silhouette.
        // The existing mouse/keyboard handlers alone change module.isToggled().
        float sx=x+width-68,sy=y+13;
        me.youngjibbit.utils.render.UIUtils.drawRound(sx,sy,36,18,9,
                new Color(17,19,27,232));
        if(response>.006f)
            me.youngjibbit.utils.render.UIUtils.drawRound(sx+1,sy+1,
                    Math.max(1,34*response),16,8,
                    NeutronVisualTokens.accent(theme,Math.round(95+145*response)));
        float bounce=Math.min(.9f,Math.abs(toggleBounce)*6);
        me.youngjibbit.utils.render.UIUtils.drawRound(
                sx+2+16*response-bounce/2,sy+3-bounce/2,12+bounce,12+bounce,
                6+bounce/2,new Color(250,246,252,243));
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(
                openProgress>.5f?"h":"i",x+width-23,y+15,NeutronVisualTokens.MUTED);
    }

    /** Shared settings renderer for the panel and compact dropdown presentations. */
    protected final void drawSettings(int mouseX, int mouseY) {
        if (!settingsClip.isEmpty()) {
            try (GuiClip ignored = GuiClip.push(settingsClip)) {
                if(compactSettings())me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+1,y+height,width-2,settingsContentHeight,0,new Color(9,12,19,145));
                if (panelStyle) {
                    if(modernCard){
                        me.youngjibbit.utils.render.UIUtils.drawRound(
                                x+4,y+height+1,Math.max(1,width-8),
                                Math.max(1,settingsContentHeight-1),8,
                                NeutronVisualTokens.PANEL);
                        me.youngjibbit.utils.render.UIUtils.drawDecorationRound(
                                x+14,y+height+2,Math.max(1,width-28),1,.5f,
                                NeutronVisualTokens.HAIRLINE);
                    }else{
                        Theme theme=Theme.resolve(Theme.getDefaultTheme());
                        me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+7,y+height+2,
                                width-14,settingsContentHeight-4,5,
                                me.youngjibbit.utils.render.UiEffects.accent(theme.getBackground().getRGB(),.34f));
                        me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+13,y+height+2,
                                width-26,.7f,0,
                                me.youngjibbit.utils.render.UiEffects.accent(theme.getTextColor().getRGB(),.16f));
                    }
                } else if(!compactSettings())Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString("Settings", x + 12, y + height + 5,
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
