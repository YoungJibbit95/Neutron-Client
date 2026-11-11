package me.youngjibbit.module.impl.client.island;

/** Fixed, collision-free docks on either side of the capsule. All hit geometry uses these bounds. */
public final class IslandWidgetLayout {
    public static final float HEIGHT=34,INSIDE_WIDTH=34,GAP=5;
    public static final String[] DOCK_NAMES={"Left 1","Left 2","Left 3","Right 1","Right 2","Right 3"};
    private static final int[] DEFAULT_DOCKS={0,3,4};
    public final IslandLayout.Frame frame;
    public final int count;
    public final boolean outside;
    public final float diameter,gap,firstX,y,scale,leftEdge,rightEdge;
    private final float[] widths,positions,dockWidths;
    private final int[] docks;

    private IslandWidgetLayout(IslandLayout.Frame source,int count,boolean outside,float[] expandedWidths,
                               int[] requestedDocks,boolean reserveAllDocks){
        this.count=Math.max(0,Math.min(3,count));this.outside=outside;
        docks=resolveDocks(this.count,requestedDocks);
        widths=new float[this.count];positions=new float[this.count];dockWidths=new float[6];
        float baseDiameter=Math.min(HEIGHT,source.height);
        java.util.Arrays.fill(dockWidths,baseDiameter);
        int leftRank=-1,rightRank=-1;
        for(int i=0;i<this.count;i++){
            float logical=expandedWidths!=null&&i<expandedWidths.length&&Float.isFinite(expandedWidths[i])
                    ?Math.max(HEIGHT,Math.min(220,expandedWidths[i])):HEIGHT;
            widths[i]=baseDiameter*logical/HEIGHT;dockWidths[docks[i]]=widths[i];
            if(docks[i]<3)leftRank=Math.max(leftRank,docks[i]);else rightRank=Math.max(rightRank,docks[i]-3);
        }
        // The editor reserves empty destinations as well: every one of the six targets remains reachable.
        if(outside&&reserveAllDocks&&this.count>0){leftRank=2;rightRank=2;}
        float left=outside?extent(0,leftRank,GAP):0,right=outside?extent(3,rightRank,GAP):0;
        float safe=Math.max(.01f,source.viewportWidth-Math.min(16,source.viewportWidth*.1f));
        scale=outside?Math.min(1,safe/(source.width+left+right)):1;
        diameter=baseDiameter*scale;gap=GAP*scale;
        for(int i=0;i<widths.length;i++)widths[i]*=scale;
        for(int i=0;i<dockWidths.length;i++)dockWidths[i]*=scale;
        frame=outside?source.withGeometry(source.width*scale,source.height*scale,source.radius*scale,source.opacity)
                .shiftHorizontally((left-right)*scale/2):source;
        y=frame.y;
        for(int i=0;i<this.count;i++)positions[i]=outside?dockX(docks[i]):0;
        firstX=this.count==0?frame.x+frame.width+gap:positions[0];
        leftEdge=frame.x-left*scale;rightEdge=frame.x+frame.width+right*scale;
    }
    private float extent(int start,int last,float spacing){
        float sum=0;for(int i=0;i<=last;i++)sum+=dockWidths[start+i]+spacing;return sum;
    }
    /** Resolve stale or duplicate config slots for display without mutating persisted values. */
    public static int[] resolveDocks(int count,int[] requested){
        int[] result=new int[Math.max(0,Math.min(3,count))];int used=0;
        for(int i=0;i<result.length;i++){
            int preferred=requested!=null&&i<requested.length?requested[i]:DEFAULT_DOCKS[i];
            if(preferred<0||preferred>=6)preferred=DEFAULT_DOCKS[i];
            int candidate=preferred;
            for(int retry=0;retry<6&&(used&(1<<candidate))!=0;retry++)candidate=(candidate+1)%6;
            result[i]=candidate;used|=1<<candidate;
        }
        return result;
    }
    public static IslandWidgetLayout capture(IslandLayout.Frame f,int c,boolean o){
        return new IslandWidgetLayout(f,c,o,null,null,false);
    }
    public static IslandWidgetLayout capture(IslandLayout.Frame f,int c,boolean o,float[] w){
        return new IslandWidgetLayout(f,c,o,w,null,false);
    }
    public static IslandWidgetLayout capture(IslandLayout.Frame f,int c,boolean o,float[] w,int[] d){
        return new IslandWidgetLayout(f,c,o,w,d,false);
    }
    public static IslandWidgetLayout capture(IslandLayout.Frame f,int c,boolean o,float[] w,int[] d,boolean editor){
        return new IslandWidgetLayout(f,c,o,w,d,editor);
    }
    public static float idleWidth(boolean modern,int count,boolean outside){
        return(modern?234:245)+(outside?0:Math.max(0,Math.min(3,count))*INSIDE_WIDTH);
    }
    public static boolean visible(IslandStateMachine.State state,boolean outside){
        return outside||state==IslandStateMachine.State.IDLE||IslandAttachmentLayout.attached(state);
    }
    public float x(int i){return positions[i];}
    public float width(int i){return widths[i];}
    public int dock(int i){return docks[i];}
    public boolean left(int i){return docks[i]<3;}
    public float circleX(int i){return x(i)+(left(i)?width(i)-diameter:0);}
    public float dockX(int slot){
        int d=Math.max(0,Math.min(5,slot));
        if(d<3)return frame.x-extent(0,d,gap);
        return frame.x+frame.width+gap+extent(3,d-4,gap);
    }
    public float dockCircleX(int slot){
        int d=Math.max(0,Math.min(5,slot));
        float x=dockX(d)+(d<3?dockWidths[d]-diameter:0);
        return Math.max(0,Math.min(frame.viewportWidth-diameter,x));
    }
    public int nearestDock(float mx){
        int best=0;float distance=Float.POSITIVE_INFINITY;
        for(int s=0;s<6;s++){
            float next=Math.abs(mx-dockCircleX(s)-diameter/2);
            if(next<distance){best=s;distance=next;}
        }
        return best;
    }
}
