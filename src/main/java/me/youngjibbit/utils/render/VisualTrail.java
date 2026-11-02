package me.youngjibbit.utils.render;

/** Fixed-capacity chronological world samples. No per-tick allocation or stale-world trail. */
public final class VisualTrail {
    private final double[] xs,ys,zs;
    private final long[] times;
    private Object world;
    private int first,size;
    public VisualTrail(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");xs=new double[capacity];ys=new double[capacity];zs=new double[capacity];times=new long[capacity];}
    public void world(Object next){if(world!=next){clear();world=next;}}
    public void clear(){first=size=0;world=null;}
    public void add(Object owner,double x,double y,double z,long now){
        world(owner);if(owner==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        if(size>0){int last=index(size-1);double dx=x-xs[last],dy=y-ys[last],dz=z-zs[last];if(dx*dx+dy*dy+dz*dz>1024){first=size=0;}}
        if(size==xs.length){first=(first+1)%xs.length;size--;}
        int at=index(size++);xs[at]=x;ys[at]=y;zs[at]=z;times[at]=now;
    }
    public void expire(long now,long lifetime){while(size>0&&(now<times[first]||now-times[first]>lifetime)){first=(first+1)%xs.length;size--;}}
    private int index(int i){return(first+i)%xs.length;}
    public int size(){return size;}
    public double x(int i){return xs[index(i)];}public double y(int i){return ys[index(i)];}public double z(int i){return zs[index(i)];}public long time(int i){return times[index(i)];}
}
