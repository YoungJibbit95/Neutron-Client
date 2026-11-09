package me.youngjibbit.utils.render;

/** Arc-length sampling on a rounded rectangle; all coordinates are local GUI pixels. */
public final class PerimeterOrbit {
    private PerimeterOrbit() { }
    public enum Direction { CLOCKWISE, COUNTERCLOCKWISE }

    public static float perimeter(float width,float height,float radius) {
        if(!finite(width)||!finite(height)||width<=0||height<=0)return 0;
        float r=radius(width,height,radius);
        return 2f*(width-2*r)+2f*(height-2*r)+(float)(2*Math.PI*r);
    }

    /** Compatibility helper for callers which need only a wrapped travel phase. */
    public static float position(float width,float height,float radius,float phase,Direction direction) {
        if(perimeter(width,height,radius)<=0||!finite(phase))return 0;
        return wrap(direction==Direction.COUNTERCLOCKWISE?-phase:phase);
    }

    /** Clockwise contour coordinate, starting at the top-left horizontal tangent. */
    public static float contour(float x,float y,float width,float height,float requestedRadius) {
        float perimeter=perimeter(width,height,requestedRadius);
        if(perimeter<=0||!finite(x)||!finite(y))return 0;
        float r=radius(width,height,requestedRadius);
        x=clamp(x,0,width);y=clamp(y,0,height);
        float top=width-2*r,right=height-2*r,arc=(float)(Math.PI*r/2),distance;
        if(r>0&&x>=width-r&&y<=r) {
            float angle=(float)Math.atan2(y-r,x-(width-r));
            distance=top+r*(angle+(float)Math.PI/2);
        } else if(r>0&&x>=width-r&&y>=height-r) {
            float angle=(float)Math.atan2(y-(height-r),x-(width-r));
            distance=top+arc+right+r*angle;
        } else if(r>0&&x<=r&&y>=height-r) {
            float angle=(float)Math.atan2(y-(height-r),x-r);
            distance=2*top+right+2*arc+r*(angle-(float)Math.PI/2);
        } else if(r>0&&x<=r&&y<=r) {
            float angle=(float)Math.atan2(y-r,x-r);
            if(angle<0)angle+=(float)(2*Math.PI);
            distance=2*top+2*right+3*arc+r*(angle-(float)Math.PI);
        } else {
            float nearest=Math.min(Math.min(y,width-x),Math.min(height-y,x));
            if(nearest==y)distance=clamp(x-r,0,top);
            else if(nearest==width-x)distance=top+arc+clamp(y-r,0,right);
            else if(nearest==height-y)distance=top+right+2*arc+clamp(width-r-x,0,top);
            else distance=2*top+right+3*arc+clamp(height-r-y,0,right);
        }
        return wrap(distance/perimeter);
    }

    /** A smooth accent peak travels around the actual contour without a corner jump. */
    public static float sample(float x,float y,float width,float height,float radius,float phase,Direction direction) {
        if(perimeter(width,height,radius)<=0||!finite(phase))return 0;
        float travel=direction==Direction.COUNTERCLOCKWISE?-phase:phase;
        return .5f+.5f*(float)Math.cos(2*Math.PI*(contour(x,y,width,height,radius)-travel));
    }
    private static float radius(float w,float h,float r){return finite(r)?clamp(r,0,Math.min(w,h)/2):0;}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static float wrap(float v){return v-(float)Math.floor(v);}
    private static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
}
