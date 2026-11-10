package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.SubcategoryValue;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Quiet section rail and count disclosure: intentionally distinct from a module toggle card. */
public final class ClickSubcategory extends Component.Sub<SubcategoryValue> {
    private final List<Component.Sub<?>> children = new ArrayList<>();
    private final HudSpring reveal = new HudSpring();
    private float progress;
    private ClipRect childReveal = new ClipRect(0,0,0,0);

    public ClickSubcategory(SubcategoryValue value, float width) {
        super(value,0,0,width,25);
        reveal.snap(0);
    }
    public void add(Component.Sub<?> child) { children.add(child); }
    public List<Component.Sub<?>> children() { return Collections.unmodifiableList(children); }
    public boolean isOpen() { return value.getValue(); }
    public float progress() { return progress; }
    public ClipRect childReveal() { return childReveal; }
    public void childReveal(ClipRect bounds) { childReveal = bounds; }
    public void step(float dt) {
        if (!value.isVisible()) { setOpen(false); progress = reveal.snap(0); return; }
        ClientSettings s=ClientSettings.current();
        progress=Math.max(0,Math.min(1,reveal.step(isOpen()?1:0,dt,
                s==null||s.animations.getValue(),s!=null&&s.reducedMotion.getValue(),
                s==null?"Balanced":s.motionIntensity.getValue())));
    }
    public void setOpen(boolean open) {
        value.setValue(open);
        if (!open) for (Component.Sub<?> child : children) cancel(child);
    }
    static void cancel(Component.Sub<?> child) {
        child.mouseReleased();
        if (child instanceof ClickSlider) ((ClickSlider)child).cancelInput();
        if (child instanceof ClickCombo) ((ClickCombo)child).close();
    }
    @Override public void setWidth(float width) { this.width=width; height=width<160?23:25; }
    @Override public ClipRect presentationBounds() { return new ClipRect(x+4,y,width-8,height); }
    @Override public void drawScreen(int mx,int my) {
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        boolean compact=width<160;
        TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont(compact?"Poppins-Medium 16":"Poppins-Medium 18");
        int ink=me.youngjibbit.gui.LegacyUiStyle.ink(theme);
        float over=Math.max(0,Math.min(1,getPresentation().amount()));
        float left=x+(compact?7:12),mid=y+height/2;
        UIUtils.drawDecorationRound(x+4,y+1,width-8,height-2,5,
                UiEffects.color(ink,.025f+.025f*over+.018f*progress));
        // A restrained vertical rail connects disclosure to its settings, never an enable indicator.
        UIUtils.drawDecorationRound(left,mid-4,2,8,1,UiEffects.color(theme.getPrimaryGradientRGB(),.48f+.42f*progress));
        if(childReveal.height>1)UIUtils.drawDecorationRound(x+7,y+height+2,1,
                Math.max(0,childReveal.height-4),.5f,UiEffects.color(theme.getPrimaryGradientRGB(),.13f*progress));
        String count=Integer.toString(value.visibleCount());
        float countWidth=font.getWidth(count);
        float textX=left+8,right=x+width-(compact?18:23);
        font.drawString(ClickControlStyle.fit(font,value.getName(),right-countWidth-textX-13),textX,
                y+(height-font.getHeight(value.getName()))/2,ink);
        font.drawString(count,right-countWidth-8,y+(height-font.getHeight(count))/2,UiEffects.alpha(ink,.48f));
        UIUtils.drawDecorationRound(right,mid,5,1,0,UiEffects.color(ink,.7f));
        if(progress<.995f)UIUtils.drawDecorationRound(right+2,mid-2*(1-progress),1,5*(1-progress),0,UiEffects.color(ink,.7f));
        if(getPresentation().keyboardFocused())UIUtils.drawDecorationRound(x+5,y+height-2,width-10,1,.5f,theme.getPrimaryGradientColor());
    }
    @Override public void mouseClicked(int mx,int my,int button) {
        if ((button==0||button==1)&&hit(mx,my,x+4,y,width-8,height)) setOpen(!isOpen());
    }
    @Override public void keyTyped(char c,int key) {
        if(key==Keyboard.KEY_RIGHT)setOpen(true);
        else if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_ESCAPE)setOpen(false);
        else if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)setOpen(!isOpen());
    }
}
