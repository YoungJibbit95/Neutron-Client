package me.youngjibbit.gui;

/** Pure editor position policy: logical anchor does not move with its decorative spring. */
public final class HudPlacement {
    public static final class Point {
        public final float x,y;
        Point(float x,float y){this.x=x;this.y=y;}
    }
    private HudPlacement() {}

    public static Point location(float configuredX,float configuredY,boolean custom,
                                 int screenW,int screenH,float width,float height,boolean bounded) {
        float x = custom ? configuredX : screenW/2f+25;
        float y = custom ? configuredY : screenH/2f-35;
        return clamp(x,y,screenW,screenH,width,height,bounded);
    }
    public static Point drag(float mouseX,float mouseY,float grabX,float grabY,
                             float grid,int screenW,int screenH,float width,float height,boolean bounded) {
        float x=mouseX-grabX,y=mouseY-grabY;
        if (Float.isFinite(grid)&&grid>0) {
            x=Math.round(x/grid)*grid;
            y=Math.round(y/grid)*grid;
        }
        return clamp(x,y,screenW,screenH,width,height,bounded);
    }
    public static Point clamp(float x,float y,int screenW,int screenH,float w,float h,boolean bounded) {
        if(!Float.isFinite(x))x=4;
        if(!Float.isFinite(y))y=4;
        if(bounded) {
            float maxX=Math.max(0,screenW-Math.max(0,w));
            float maxY=Math.max(0,screenH-Math.max(0,h));
            float gutterX=maxX>=8?4:0,gutterY=maxY>=8?4:0;
            x=Math.max(gutterX,Math.min(x,maxX-gutterX));
            y=Math.max(gutterY,Math.min(y,maxY-gutterY));
        }
        return new Point(x,y);
    }
}
