package me.youngjibbit.utils.render;

import me.youngjibbit.core.world.BedObservation;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;

/** Pure projection and bounded label placement; never reads a world or changes an action. */
public final class BedEspLayout {
    private BedEspLayout() { }
    public static final class Point {
        public final float x, y, depth;
        public Point(float x, float y, float depth) { this.x=x;this.y=y;this.depth=depth; }
    }
    public static final class Rect {
        public final float x,y,width,height;
        public Rect(float x,float y,float width,float height){this.x=x;this.y=y;this.width=width;this.height=height;}
        public boolean intersects(Rect other,float padding){
            return x<other.x+other.width+padding&&x+width+padding>other.x
                    &&y<other.y+other.height+padding&&y+height+padding>other.y;
        }
    }
    /** Matrices are captured once for the actual eye, before any screen-space drawing. */
    public static Point project(double x,double y,double z,FloatBuffer model,FloatBuffer projection,
                                IntBuffer viewport,int displayHeight,float scale){
        if(!Double.isFinite(x+y+z)||!Float.isFinite(scale)||scale<=0||displayHeight<=0)return null;
        double a=model.get(0)*x+model.get(4)*y+model.get(8)*z+model.get(12);
        double b=model.get(1)*x+model.get(5)*y+model.get(9)*z+model.get(13);
        double c=model.get(2)*x+model.get(6)*y+model.get(10)*z+model.get(14);
        double d=model.get(3)*x+model.get(7)*y+model.get(11)*z+model.get(15);
        double w=projection.get(3)*a+projection.get(7)*b+projection.get(11)*c+projection.get(15)*d;
        if(!Double.isFinite(w)||w<=.00001)return null;
        double px=(projection.get(0)*a+projection.get(4)*b+projection.get(8)*c+projection.get(12)*d)/w;
        double py=(projection.get(1)*a+projection.get(5)*b+projection.get(9)*c+projection.get(13)*d)/w;
        double pz=(projection.get(2)*a+projection.get(6)*b+projection.get(10)*c+projection.get(14)*d)/w;
        if(!Double.isFinite(px+py+pz)||pz< -1||pz>=1||viewport.get(2)<=0||viewport.get(3)<=0)return null;
        return new Point((float)((viewport.get(0)+(px+1)*viewport.get(2)*.5)/scale),
                (float)((displayHeight-viewport.get(1)-(py+1)*viewport.get(3)*.5)/scale),(float)((pz+1)*.5));
    }
    /** Nearest beds reserve their space first. A crowded label is omitted, never stacked over another. */
    public static Rect place(Point anchor,float width,float height,int screenWidth,int screenHeight,List<Rect> occupied){
        if(anchor==null||!Float.isFinite(anchor.x+anchor.y+width+height)||width<=0||height<=0
                ||width>screenWidth-8||height>screenHeight-8||anchor.x<0||anchor.x>screenWidth
                ||anchor.y<0||anchor.y>screenHeight)return null;
        float x=Math.max(4,Math.min(screenWidth-width-4,anchor.x-width/2));
        float top=anchor.y-height-7;
        for(int attempt=0;attempt<3;attempt++){
            float y=top-attempt*(height+4);
            if(y<4)continue;
            Rect candidate=new Rect(x,Math.min(screenHeight-height-4,y),width,height);
            boolean clear=true;for(Rect prior:occupied)if(candidate.intersects(prior,4)){clear=false;break;}
            if(clear)return candidate;
        }
        return null;
    }
    public static String layer(BedObservation.Layer layer){
        if(layer==null)return "Unknown";
        if(layer.materials.isEmpty())return layer.unknown>0?"Unknown":"Air";
        StringBuilder text=new StringBuilder();
        for(int i=0;i<Math.min(2,layer.materials.size());i++){
            BedObservation.MaterialCount material=layer.materials.get(i);
            if(i>0)text.append(" / ");
            text.append(material.label).append(" x").append(material.count);
        }
        if(layer.materials.size()>2)text.append(" +").append(layer.materials.size()-2);
        if(layer.unknown>0)text.append(" ?");
        return text.toString();
    }
    public static String exposure(BedObservation bed){
        if(!bed.completeBed())return "Partial bed";
        if(!bed.openSides.isEmpty())return "Open: "+bed.openSides.size()+" side"+(bed.openSides.size()==1?"":"s");
        return bed.fullyLoaded?"Covered":"Partly scanned";
    }
    public static String time(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)return "--";
        if(seconds>=600)return "~10m+";
        if(seconds>=60){int total=(int)Math.ceil(seconds);return String.format(Locale.ROOT,"~%d:%02d",total/60,total%60);}
        return String.format(Locale.ROOT,"~%.1f s",Math.max(.1,seconds));
    }
}
