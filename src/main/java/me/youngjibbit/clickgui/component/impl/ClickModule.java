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
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.clickgui.utils.MenuSurface;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ClickModule extends Component {
    private final Module module;
    private final List<Sub<?>> values = new ArrayList<>();
    private final List<ClickSubcategory> subcategories = new ArrayList<>();
    private final List<Sub<?>> groupedValues = new ArrayList<>();
    private final java.util.Map<Sub<?>, ClickSubcategory> groups = new java.util.IdentityHashMap<>();
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
                modern?36:24);
        row.panelStyle=true;row.modernCard=modern;
        for(Sub<?> value:row.values)value.usePanelStyle();
        row.resetPanelMotion();
        return row;
    }

    private float settingsTop() {
        if (panelStyle) return PANEL_TOP;
        return compactSettings() ? 2 : grouped() ? 5 : 20;
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
        List<Value<?>> supported = new ArrayList<>();
        for (Sub<?> value : values) supported.add((Value<?>) value.getValue());
        for (me.youngjibbit.value.impl.SubcategoryValue category :
                me.youngjibbit.clickgui.utils.SettingSubcategories.create(module, supported)) {
            ClickSubcategory section = new ClickSubcategory(category, width);
            subcategories.add(section);
            groupedValues.add(section);
            for (Value<?> child : category.children()) for (Sub<?> widget : values) {
                if (widget.getValue() == child) {
                    section.add(widget);
                    groupedValues.add(widget);
                    groups.put(widget, section);
                    break;
                }
            }
        }
        // Keep the main controls immediately reachable; other sections remember their UI state.
        if (!subcategories.isEmpty()) subcategories.get(0).setOpen(true);
        updateAnimation();
    }

    private boolean grouped() {
        return !modernCard && !subcategories.isEmpty()
                && !me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(me.youngjibbit.theme.ThemeMode.selected());
    }

    private List<Sub<?>> rows() { return grouped() ? groupedValues : values; }
    public List<ClickSubcategory> subcategories() { return java.util.Collections.unmodifiableList(subcategories); }
    public boolean ownsComponent(Component component) {
        return component == this || values.contains(component) || subcategories.contains(component);
    }
    public Component focusParent(Component component) {
        ClickSubcategory section = groups.get(component);
        return grouped() && section != null && section.getValue().isVisible() && open ? section : this;
    }
    public boolean inputVisible(Sub<?> value) {
        if (!visible(value)) return false;
        ClickSubcategory section = groups.get(value);
        return !grouped() || section == null || section.isOpen() && section.getValue().isVisible();
    }

    private float groupContent(ClickSubcategory section) {
        float content = 0;
        for (Sub<?> child : section.children()) if (visible(child)) content += child.getHeight() + valueGap();
        return content;
    }

    private void measureSettings(float seconds) {
        settingsContentHeight = settingsTop() + (panelStyle ? PANEL_BOTTOM : compactSettings() ? 2 : 4);
        if (grouped()) {
            for (ClickSubcategory section : subcategories) {
                section.step(seconds);
                if (section.getValue().isVisible())
                    settingsContentHeight += section.getHeight() + 3 + groupContent(section) * section.progress();
            }
        } else for (Sub<?> value : values) if (visible(value)) settingsContentHeight += value.getHeight() + valueGap();
    }

    public void updateAnimation() {
        openAnimation.setDirection(open ? Direction.FORWARDS : Direction.BACKWARDS);
        toggleAnimation.setDirection(module.isToggled() ? Direction.FORWARDS : Direction.BACKWARDS);
        openProgress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion()?openAnimation.getOutput().floatValue():open?1:0));
        toggleProgress = Math.max(0, Math.min(1, me.youngjibbit.utils.render.UiEffects.motion()?toggleAnimation.getOutput().floatValue():module.isToggled()?1:0));
        for (Sub<?> value : values) {
            if (!visible(value)) continue;
            if (value instanceof ClickCombo) ((ClickCombo) value).updateAnimation();
        }
        measureSettings(0);
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
        for(Sub<?> value:values){
            if(!visible(value))continue;
            if(value instanceof ClickCombo)((ClickCombo)value).updatePanelAnimation(seconds);
        }
        measureSettings(seconds);
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
        openProgress=Math.max(0,Math.min(1,panelReveal.transition(
                open?1:0,seconds,settings)));
        // Combo geometry must be frozen from the same spring sample as the clip.
        for(Sub<?> value:values){
            if(!visible(value))continue;
            if(value instanceof ClickCombo)((ClickCombo)value).updatePanelAnimation(seconds);
        }
        measureSettings(seconds);
    }

    public void updateLegacyAnimation(float seconds) {
        updateAnimation();
        measureSettings(seconds);
    }

    protected boolean compactSettings() { return false; }
    @Override public void setWidth(float next) {
        width=next;
        for(Sub<?> value:values)value.setWidth(compactSettings()?width-4:width);
        for(ClickSubcategory section:subcategories)section.setWidth(compactSettings()?width-4:width);
    }
    @Override public void mouseDragged(int mouseX,int mouseY) {
        if(open)for(Sub<?> value:values)if(inputVisible(value))value.mouseDragged(mouseX,mouseY);
    }

    public void layout(float x, float y, float width, ClipRect parentClip) {
        updateComponent(x, y);
        setWidth(width);
        this.clip = parentClip;
        settingsClip = parentClip.intersect(new ClipRect(x + 1, y + height, width - 2, settingsContentHeight * openProgress));
        float valueY = y + height + settingsTop();
        if (grouped()) {
            for (ClickSubcategory section : subcategories) {
                if (!section.getValue().isVisible()) {
                    hide(section);
                    for (Sub<?> child : section.children()) hide(child);
                    continue;
                }
                place(section, valueY, settingsClip);
                valueY += section.getHeight();
                float revealedHeight = groupContent(section) * section.progress();
                ClipRect reveal = new ClipRect(x + 1, valueY, width - 2, revealedHeight);
                section.childReveal(reveal);
                ClipRect childClip = settingsClip.intersect(reveal);
                float childY = valueY;
                for (Sub<?> child : section.children()) {
                    if (!visible(child)) { hide(child); continue; }
                    place(child, childY, childClip);
                    if (!section.isOpen()) ClickSubcategory.cancel(child);
                    childY += child.getHeight() + valueGap();
                }
                valueY += revealedHeight + 3;
            }
            return;
        }
        for (Sub<?> value : values) {
            if (!visible(value)) {
                hide(value);
                continue;
            }
            place(value, valueY, settingsClip);
            valueY += value.getHeight() + valueGap();
        }
    }

    private void hide(Sub<?> value) {
        value.setClip(new ClipRect(0, 0, 0, 0));
        ClickSubcategory.cancel(value);
    }

    private void place(Sub<?> value, float valueY, ClipRect valueClip) {
        value.updateComponent(x + (compactSettings() ? 2 : 0), valueY);
        value.setWidth(compactSettings() ? width - 4 : width);
        value.setClip(valueClip);
        if (!open || valueClip.intersect(new ClipRect(x, valueY, width, value.getHeight())).isEmpty())
            value.mouseReleased();
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
        MenuSurface.plate(bindingX, y + 4, bindingWidth, 16, theme, conflict, 0);
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

    /** Assigned bindings stay visible in both Panel presentations. Input remains screen-owned. */
    private float drawPanelBinding(Theme theme,float right,float top){
        if(module.getKey()==0)return right;
        TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 16");
        String key=org.lwjgl.input.Keyboard.getKeyName(module.getKey());
        if(key==null)key="?";
        boolean conflict=keyConflict();
        if(conflict)key+=" !";
        key=ClickControlStyle.fit(font,key,Math.max(8,Math.min(54,width*.25f)-10));
        float chipWidth=Math.max(18,font.getWidth(key)+10),left=right-chipWidth;
        MenuSurface.plate(left,top,chipWidth,16,theme,conflict,0);
        font.drawString(key,left+(chipWidth-font.getWidth(key))/2,
                top+(16-font.getHeight(key))/2,
                conflict?0xffffb975:me.youngjibbit.gui.LegacyUiStyle.ink(theme));
        return left-6;
    }

    private void drawPanelHeader(Theme theme, boolean hover) {
        if(modernCard){
            drawNeutronTwoHeader(theme,hover);
            return;
        }
        // Module hover stays anchored, even while the settings below grow or scroll.
        int text = me.youngjibbit.gui.LegacyUiStyle.ink(theme);
        float over = Math.max(0, Math.min(1, getPresentation().amount()));
        int base = me.youngjibbit.gui.LegacyUiStyle.panel(theme).getRGB();
        int fill = Theme.interpolateRGB(base, text, over * .045f);
        me.youngjibbit.clickgui.utils.MenuSurface.card(x, y, width, getHeight(), theme, false, over);
        float dotX = x + 10, dotY = y + height / 2 - 2;
        if (module.isToggled()||toggleProgress>.005f){
            float dot=4+toggleBounce*18;
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(dotX-(dot-4)/2,
                    dotY-(dot-4)/2,dot,dot,dot/2,
                    me.youngjibbit.utils.render.UiEffects.color(
                            theme.getPrimaryGradientRGB(),.24f+.76f*toggleProgress));
        }
        if (getPresentation().keyboardFocused())
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+2,y+6,1.5f,height-12,.75f,
                    me.youngjibbit.gui.LegacyUiStyle.accent(theme,.9f));

        me.youngjibbit.utils.font.TTFFontRenderer title = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
        float right = drawPanelBinding(theme,x+width-28,y+4);
        int titleInk = text;
        title.drawString(ClickControlStyle.fit(title, module.getName(), right - x - 24), x + 19, y + 6, titleInk);
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(openProgress > .5f ? "h" : "i",
                x + width - 17, y + 7, me.youngjibbit.utils.render.UiEffects.alpha(text, hover || open ? .95f : .62f));
    }

    /** Restrained 2.0 cards: dark neutral backing; color is a fine semantic cue. */
    private void drawNeutronTwoHeader(Theme theme,boolean hover){
        float response=Math.max(0,Math.min(1,toggleProgress));
        float energy=Math.max(0,Math.min(1,Math.abs(getPresentation().amount())));
        float top=y,cardHeight=getHeight();
        me.youngjibbit.clickgui.utils.MenuSurface.card(x,top,width,cardHeight,theme,false,energy);
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
        float titleRight=module.getKey()==0?x+width-83:drawPanelBinding(theme,x+width-70,y+4);
        String name=ClickControlStyle.fit(title,module.getName(),Math.max(0,titleRight-x-13));
        title.drawString(name,x+13,y+5,NeutronVisualTokens.INK);
        String description=module.getDescription();
        if(description==null||description.trim().isEmpty())description="Module settings";
        descFont.drawString(ClickControlStyle.fit(descFont,description,Math.max(16,width-110)),
                x+13,y+20,NeutronVisualTokens.MUTED);

        // Retargeted spring travels within a fixed non-interactive switch silhouette.
        // The existing mouse/keyboard handlers alone change module.isToggled().
        float sx=x+width-62,sy=y+10;
        UIUtils.drawDecorationRound(sx,sy,30,16,8,
                LiquidGlass.fill(theme,206));
        LiquidGlass.highlight(sx,sy,30,16,8);
        LiquidGlass.border(sx,sy,30,16,8,theme,response>.5f);
        if(response>.006f)
            me.youngjibbit.utils.render.UIUtils.drawDecorationRound(sx+1,sy+1,
                    Math.max(1,28*response),14,7,
                    NeutronVisualTokens.accent(theme,Math.round(95+145*response)));
        float bounce=Math.min(.9f,Math.abs(toggleBounce)*6);
        me.youngjibbit.utils.render.UIUtils.drawDecorationRound(
                sx+2+14*response-bounce/2,sy+2-bounce/2,12+bounce,12+bounce,
                6+bounce/2,new Color(250,246,252,243));
        Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(
                openProgress>.5f?"h":"i",x+width-20,y+11,NeutronVisualTokens.MUTED);
    }

    /** Shared settings renderer for the panel and compact dropdown presentations. */
    protected final void drawSettings(int mouseX, int mouseY) {
        if (!settingsClip.isEmpty()) {
            try (GuiClip ignored = GuiClip.push(settingsClip)) {
                if(compactSettings())me.youngjibbit.utils.render.UIUtils.drawDecorationRound(x+1,y+height,width-2,settingsContentHeight,0,
                        LiquidGlass.fill(Theme.resolve(Theme.getDefaultTheme()),150));
                if (panelStyle) {
                    Theme theme=Theme.resolve(Theme.getDefaultTheme());
                    if(modernCard){
                        LiquidGlass.inset(x+4,y+height+1,Math.max(1,width-8),
                                Math.max(1,settingsContentHeight-1),8,theme);
                    }else{
                        LiquidGlass.inset(x+5,y+height+1,Math.max(1,width-10),
                                Math.max(1,settingsContentHeight-2),5,theme);
                    }
                } else if(!compactSettings() && !grouped())Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18").drawString("Settings", x + 12, y + height + 5,
                        new Color(180, 180, 185).getRGB());
                if(grouped())for(ClickSubcategory section:subcategories){
                    if(!section.getValue().isVisible())continue;
                    drawValue(section,mouseX,mouseY);
                    ClipRect sectionClip=settingsClip.intersect(section.childReveal());
                    if(!sectionClip.isEmpty())try(GuiClip childClip=GuiClip.push(sectionClip)){
                        for(Sub<?> child:section.children())drawValue(child,mouseX,mouseY);
                    }
                }
                else for(Sub<?> value:values)drawValue(value,mouseX,mouseY);
            }
        }
    }

    private void drawValue(Sub<?> value,int mouseX,int mouseY){
        if(!visible(value)||value.getClip()==null||value.getClip().intersect(
                new ClipRect(value.getX(),value.getY(),value.getWidth(),value.getHeight())).isEmpty())return;
        boolean interactive=open&&inputVisible(value);
        value.drawScreen(interactive?mouseX:-1,interactive?mouseY:-1);
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
            // Deliver once using the pre-click layout. Disclosure changes must not bleed into children.
            for (Sub<?> value : rows()) if (inputVisible(value) && value.getClip() != null
                    && value.getClip().contains(mouseX, mouseY)
                    && new ClipRect(value.getX(), value.getY(), value.getWidth(), value.getHeight()).contains(mouseX, mouseY)) {
                value.mouseClicked(mouseX, mouseY, button);
                return;
            }
        }
    }

    private boolean visible(Sub<?> value) {
        return ((Value<?>) value.getValue()).isVisible();
    }

    @Override
    public void mouseReleased() { for (Sub<?> value : values) value.mouseReleased(); }

    public Module getModule(){return module;}
    public List<Sub<?>> presentationValues(){return java.util.Collections.unmodifiableList(rows());}
    public boolean isOpen(){return open;}
    public void setOpen(boolean next){open=next;mouseReleased();if(!next)cancelInputs();}
    public List<Sub<?>> focusableValues() {
        List<Sub<?>> result = new ArrayList<>();
        if (!open) return result;
        // Use the module reveal, not settingsClip: offscreen settings remain Tab destinations.
        // The inherited header is 24px for Panel and 16px for Dropdown.
        ClipRect reveal = new ClipRect(x + 1, y + height, width - 2, settingsContentHeight * openProgress);
        for (Sub<?> value : rows()) {
            if (!inputVisible(value) || reveal.intersect(value.presentationBounds()).isEmpty()) continue;
            ClickSubcategory section = groups.get(value);
            if (grouped() && section != null && section.childReveal().intersect(value.presentationBounds()).isEmpty()) continue;
            result.add(value);
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
