package me.youngjibbit.gui.iosUi.component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.utils.MC;
import me.youngjibbit.utils.input.MouseUtil;

@Getter
@Setter
@AllArgsConstructor
public abstract class iOSComponent implements MC
{
    protected float x, y, width, height;

    public abstract void drawScreen(int mouseX, int mouseY);
    public abstract void mouseClicked(int mouseX, int mouseY, int mouse);
    public abstract void keyTyped(char typedChar,int key);
    public void mouseReleased() {}
    public void cancelInputs() { mouseReleased(); }

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
    public abstract static class Sub<T> extends me.youngjibbit.gui.iosUi.component.iOSComponent
    {

        protected final T value;

        public Sub(T value, float x, float y, float width, float height)
        {
            super(x, y, width, height);
            this.value = value;
        }
    }
}
