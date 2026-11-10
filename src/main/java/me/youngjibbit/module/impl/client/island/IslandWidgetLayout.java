package me.youngjibbit.module.impl.client.island;

/** Shared inside/outside placement for all optional base-state widgets, including tiny viewports. */
public final class IslandWidgetLayout {
    public static final float HEIGHT = 34, INSIDE_WIDTH = 34, GAP = 5;
    public final IslandLayout.Frame frame;
    public final int count;
    public final boolean outside;
    public final float diameter, gap, firstX, y;

    private IslandWidgetLayout(IslandLayout.Frame frame, int count, boolean outside) {
        this.count = Math.max(0, Math.min(3, count)); this.outside = outside;
        float diameter = Math.min(HEIGHT, frame.height), gap = GAP;
        if (outside && this.count > 0) {
            float extra = this.count * (diameter + gap);
            float safe = Math.max(.01f, frame.viewportWidth - Math.min(16, frame.viewportWidth * .1f));
            float scale = Math.min(1, safe / (frame.width + extra));
            diameter *= scale; gap *= scale;
            frame = frame.withGeometry(frame.width * scale, frame.height * scale, frame.radius * scale, frame.opacity)
                    .shiftHorizontally(-this.count * (diameter + gap) / 2);
        }
        this.frame = frame; this.diameter = diameter; this.gap = gap;
        firstX = outside ? frame.x + frame.width + gap : 0;
        y = frame.y + (frame.height - diameter) / 2;
    }

    public static IslandWidgetLayout capture(IslandLayout.Frame frame, int count, boolean outside) {
        return new IslandWidgetLayout(frame, count, outside);
    }

    public static float idleWidth(boolean modern, int count, boolean outside) {
        return (modern ? 234 : 245) + (outside ? 0 : Math.max(0, Math.min(3, count)) * INSIDE_WIDTH);
    }
    public float x(int index) {
        if (index < 0 || index >= count) throw new IllegalArgumentException("widget index");
        return firstX + index * (diameter + gap);
    }
}
