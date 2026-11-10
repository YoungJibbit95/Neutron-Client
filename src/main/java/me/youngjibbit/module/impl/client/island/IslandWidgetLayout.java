package me.youngjibbit.module.impl.client.island;

/** Shared inside/outside placement for all optional base-state widgets, including tiny viewports. */
public final class IslandWidgetLayout {
    public static final float HEIGHT = 34, INSIDE_WIDTH = 34, GAP = 5;
    public final IslandLayout.Frame frame;
    public final int count;
    public final boolean outside;
    public final float diameter, gap, firstX, y, scale;
    private final float[] widths;

    private IslandWidgetLayout(IslandLayout.Frame frame, int count, boolean outside) {
        this(frame,count,outside,null);
    }
    private IslandWidgetLayout(IslandLayout.Frame frame,int count,boolean outside,float[] expandedWidths){
        this.count = Math.max(0, Math.min(3, count)); this.outside = outside;
        float diameter = Math.min(HEIGHT, frame.height), gap = GAP;
        float groupScale=1;
        widths=new float[this.count];
        for(int i=0;i<this.count;i++){
            float logical=expandedWidths!=null&&i<expandedWidths.length&&Float.isFinite(expandedWidths[i])
                    ?Math.max(HEIGHT,Math.min(220,expandedWidths[i])):HEIGHT;
            widths[i]=logical==HEIGHT?diameter:diameter*logical/HEIGHT;
        }
        if (outside && this.count > 0) {
            float extra = this.count*gap;for(float width:widths)extra+=width;
            float safe = Math.max(.01f, frame.viewportWidth - Math.min(16, frame.viewportWidth * .1f));
            float scale = Math.min(1, safe / (frame.width + extra));
            groupScale=scale;
            diameter *= scale; gap *= scale;
            for(int i=0;i<widths.length;i++)widths[i]*=scale;
            frame = frame.withGeometry(frame.width * scale, frame.height * scale, frame.radius * scale, frame.opacity)
                    .shiftHorizontally(-extra*scale/2);
        }
        this.frame = frame; this.diameter = diameter; this.gap = gap;
        this.scale=groupScale;
        firstX = outside ? frame.x + frame.width + gap : 0;
        y = frame.y;
    }

    public static IslandWidgetLayout capture(IslandLayout.Frame frame, int count, boolean outside) {
        return new IslandWidgetLayout(frame, count, outside);
    }
    public static IslandWidgetLayout capture(IslandLayout.Frame frame,int count,boolean outside,float[] expandedWidths){
        return new IslandWidgetLayout(frame,count,outside,expandedWidths);
    }

    public static float idleWidth(boolean modern, int count, boolean outside) {
        return (modern ? 234 : 245) + (outside ? 0 : Math.max(0, Math.min(3, count)) * INSIDE_WIDTH);
    }
    public static boolean visible(IslandStateMachine.State state,boolean outside){
        return outside||state==IslandStateMachine.State.IDLE||IslandAttachmentLayout.attached(state);
    }
    public float x(int index) {
        if (index < 0 || index >= count) throw new IllegalArgumentException("widget index");
        float x=firstX;for(int i=0;i<index;i++)x+=widths[i]+gap;return x;
    }
    public float width(int index){return widths[index];}
}
