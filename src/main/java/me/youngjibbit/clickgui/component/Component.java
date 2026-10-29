package me.youngjibbit.clickgui.component;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.utils.MC;
import me.youngjibbit.utils.input.MouseUtil;
import me.youngjibbit.clickgui.utils.ClipRect;

@Getter
@Setter
public abstract class Component implements MC
{
    protected float x, y, width, height;
    protected ClipRect clip;
    private final me.youngjibbit.gui.UiSpring presentation=new me.youngjibbit.gui.UiSpring();

    public ClipRect presentationBounds(){return new ClipRect(x,y,width,height);}
    public void advanceMotion(float dt,int mx,int my,boolean focused,boolean pressed,boolean visible){
        presentation.step(dt,visible&&acceptsMouse(mx,my)&&presentationBounds().contains(mx,my),focused,pressed,visible,me.youngjibbit.module.impl.client.ClientSettings.current());
    }
    protected void drawFeedback(){me.youngjibbit.clickgui.utils.GuiStyle.feedback(presentationBounds(),presentation);}

    public Component(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    protected boolean acceptsMouse(int mouseX, int mouseY) {
        return clip == null || clip.contains(mouseX, mouseY);
    }

    protected boolean hit(int mouseX, int mouseY, float left, float top, float w, float h) {
        return acceptsMouse(mouseX, mouseY) && new ClipRect(left, top, w, h).contains(mouseX, mouseY);
    }

    public abstract void drawScreen(int mouseX, int mouseY);
    public abstract void mouseClicked(int mouseX, int mouseY, int mouse);
    public abstract void keyTyped(char typedChar,int key);
    public void mouseReleased() {}

    public void updateComponent(float x, float y)
    {
        this.x = x;
        this.y = y;
    }

    public boolean isMouseOver(int mouseX, int mouseY, int scrollHeight)
    {
        return MouseUtil.isMouseOver(mouseX, mouseY, x, y, width, height);
    }

    @Getter
    public abstract static class Sub<T> extends Component
    {

        protected final T value;

        public Sub(T value, float x, float y, float width, float height)
        {
            super(x, y, width, height);
            this.value = value;
        }
    }
}
