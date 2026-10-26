package me.youngjibbit.clickgui.utils;

/** GUI-space bounds shared by rendering and input. Right/bottom edges are exclusive. */
public final class ClipRect {
    public final float x, y, width, height;

    public ClipRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public ClipRect intersect(ClipRect other) {
        float left = Math.max(x, other.x), top = Math.max(y, other.y);
        return new ClipRect(left, top, Math.min(x + width, other.x + other.width) - left,
                Math.min(y + height, other.y + other.height) - top);
    }

    public boolean contains(float mouseX, float mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
