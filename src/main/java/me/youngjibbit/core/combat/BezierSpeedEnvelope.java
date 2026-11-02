package me.youngjibbit.core.combat;

/** A bounded cubic velocity multiplier, with constant continuation outside its acquisition. */
public final class BezierSpeedEnvelope {
    private final double v0, v1, v2, v3;
    private final double minimum, maximum;

    public BezierSpeedEnvelope(double v0, double v1, double v2, double v3) {
        control(v0);control(v1);control(v2);control(v3);
        this.v0=v0;this.v1=v1;this.v2=v2;this.v3=v3;
        minimum=Math.min(Math.min(v0,v1),Math.min(v2,v3));
        maximum=Math.max(Math.max(v0,v1),Math.max(v2,v3));
    }
    private static void control(double value) {
        finite(value);
        if(value<0 || value>1)throw new IllegalArgumentException("Speed controls must be between zero and one");
    }
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Speed envelope inputs must be finite");
    }
    public double value(double time) {
        finite(time);
        if(time<=0)return v0;
        if(time>=1)return v3;
        double rest=1-time;
        return bounded(rest*rest*rest*v0+3*time*rest*rest*v1+3*time*time*rest*v2+time*time*time*v3);
    }
    /** Integral over the clamped acquisition parameter, not its constant continuation. */
    public double integral(double time) {
        finite(time);
        if(time<=0)return 0;
        if(time>=1)return (v0+v1+v2+v3)/4;
        double a1=3*(v1-v0),a2=3*(v0-2*v1+v2),a3=-v0+3*v1-3*v2+v3;
        return time*(v0+time*(a1/2+time*(a2/3+time*a3/4)));
    }
    /** Exact cubic interval average. Simpson avoids cancellation for very short intervals. */
    public double average(double from, double to) {
        finite(from);finite(to);
        if(to<from)throw new IllegalArgumentException("Speed envelope interval is reversed");
        if(from==to)return value(from);
        if(to<=0)return v0;
        if(from>=1)return v3;
        double result=0;
        if(from<0)result+=v0*fraction(from,Math.min(to,0),from,to);
        double left=Math.max(0,from),right=Math.min(1,to);
        if(right>left)result+=(value(left)+4*value((left+right)/2)+value(right))/6*fraction(left,right,from,to);
        if(to>1)result+=v3*fraction(Math.max(from,1),to,from,to);
        return bounded(result);
    }
    private double bounded(double value) {return Math.max(minimum,Math.min(maximum,value));}
    private static double fraction(double left,double right,double from,double to) {
        double width=to-from;
        return Double.isFinite(width)?(right-left)/width:(right/2-left/2)/(to/2-from/2);
    }
}
