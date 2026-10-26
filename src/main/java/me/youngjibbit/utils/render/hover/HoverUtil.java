package me.youngjibbit.utils.render.hover;

public class HoverUtil
{
    public static boolean rectHovered(float x, float y, float width, float height, float mouseX, float mouseY)
    {
        return mouseX > x && mouseX < x + width && mouseY < y + height && mouseY > y;
    }
}
