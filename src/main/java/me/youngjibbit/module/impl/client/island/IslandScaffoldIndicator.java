package me.youngjibbit.module.impl.client.island;

import java.util.Arrays;

/** Frozen status glyph from an observed Scaffold view and the existing UI motion clock. */
public final class IslandScaffoldIndicator {
    public enum Status { SEARCHING, ALIGNING, READY, PLACING, EDGE_GUARD, RECOVERING }
    public final Status status;
    public final String label;
    public final long sequence;
    public final float opacity;
    private final float[] segments;

    private IslandScaffoldIndicator(Status status,String label,long sequence,float opacity,Lines lines){
        this.status=status;this.label=label;this.sequence=sequence;this.opacity=opacity;
        this.segments=Arrays.copyOf(lines.values,lines.size);
    }
    public int segmentCount(){return segments.length/4;}
    public float x1(int i){return segments[i*4];}
    public float y1(int i){return segments[i*4+1];}
    public float x2(int i){return segments[i*4+2];}
    public float y2(int i){return segments[i*4+3];}
    public boolean warning(){return status==Status.EDGE_GUARD||status==Status.RECOVERING;}

    /** Unknown/fixture statuses never manufacture Ready or a synthetic progress percentage. */
    public static IslandScaffoldIndicator capture(IslandStateMachine.Scaffold source,float seconds,boolean motion){
        if(source==null||!source.observed||!source.valid())return null;
        Status status;String label;
        switch(source.status){
            case "Searching":status=Status.SEARCHING;label="Find";break;
            case "Aligning":status=Status.ALIGNING;label="Align";break;
            case "Ready":status=Status.READY;label="Ready";break;
            case "Placing":status=Status.PLACING;label="Place";break;
            case "Edge guard":status=Status.EDGE_GUARD;label="Guard";break;
            case "Recovering":status=Status.RECOVERING;label="Recover";break;
            default:return null;
        }
        float phase=motion&&Float.isFinite(seconds)?(float)(seconds*.8-Math.floor(seconds*.8)):0;
        float wave=motion?.5f-.5f*(float)Math.cos(phase*Math.PI*2):0;
        float opacity=motion?.78f+.22f*wave:1;
        Lines lines=new Lines();
        switch(status){
            case SEARCHING:
                arc(lines,12,12,7,(float)(-Math.PI/2+phase*Math.PI*2),(float)(Math.PI*1.3),12);
                break;
            case ALIGNING:
                float lo=3+1.5f*wave,hi=24-lo;
                lines.add(lo,lo+4,lo,lo);lines.add(lo,lo,lo+4,lo);
                lines.add(hi-4,lo,hi,lo);lines.add(hi,lo,hi,lo+4);
                lines.add(lo,hi-4,lo,hi);lines.add(lo,hi,lo+4,hi);
                lines.add(hi-4,hi,hi,hi);lines.add(hi,hi,hi,hi-4);
                lines.add(10,12,14,12);lines.add(12,10,12,14);
                break;
            case READY:
                lines.add(6,12,10,16);lines.add(10,16,18,7);
                break;
            case PLACING:
                float lift=motion?1.2f*(float)Math.sin(phase*Math.PI*2):0;
                lines.add(6,8+lift,12,5+lift);lines.add(12,5+lift,18,8+lift);
                lines.add(18,8+lift,18,16+lift);lines.add(18,16+lift,12,20+lift);
                lines.add(12,20+lift,6,16+lift);lines.add(6,16+lift,6,8+lift);
                lines.add(6,8+lift,12,12+lift);lines.add(12,12+lift,18,8+lift);
                lines.add(12,12+lift,12,20+lift);
                break;
            case EDGE_GUARD:
                lines.add(5,4,19,4);lines.add(19,4,18,14);lines.add(18,14,12,20);
                lines.add(12,20,6,14);lines.add(6,14,5,4);lines.add(8,11,16,11);
                break;
            case RECOVERING:
                float start=-1.3f+(motion?.22f*(float)Math.sin(phase*Math.PI*2):0),sweep=4.6f;
                arc(lines,12,12,7,start,sweep,12);
                float end=start+sweep,ex=12+7*(float)Math.cos(end),ey=12+7*(float)Math.sin(end);
                lines.add(ex,ey,ex+3*(float)Math.cos(end-.8f),ey+3*(float)Math.sin(end-.8f));
                lines.add(ex,ey,ex-3*(float)Math.cos(end+.8f),ey-3*(float)Math.sin(end+.8f));
                break;
            default:throw new AssertionError(status);
        }
        return new IslandScaffoldIndicator(status,label,source.sequence,opacity,lines);
    }
    private static void arc(Lines lines,float x,float y,float radius,float start,float sweep,int steps){
        for(int i=0;i<steps;i++){
            float a=start+sweep*i/steps,b=start+sweep*(i+1)/steps;
            lines.add(x+radius*(float)Math.cos(a),y+radius*(float)Math.sin(a),
                    x+radius*(float)Math.cos(b),y+radius*(float)Math.sin(b));
        }
    }
    private static final class Lines {
        final float[] values=new float[16*4];int size;
        void add(float x1,float y1,float x2,float y2){values[size++]=x1;values[size++]=y1;values[size++]=x2;values[size++]=y2;}
    }

    /** Complete item/text/status canvas is fitted as one unit, including during Island morphs. */
    public static final class Layout {
        public static final float WIDTH=224,HEIGHT=46,ITEM_X=9,ITEM_Y=8,ITEM_SIZE=22,
                TEXT_X=40,TEXT_WIDTH=136,STATUS_X=190,STATUS_Y=4,STATUS_SIZE=24,LABEL_Y=30,
                GRAPH_X=40,GRAPH_Y=36,GRAPH_WIDTH=136,GRAPH_HEIGHT=6;
        public final float x,y,scale,width,height;
        public Layout(IslandLayout.Frame frame){
            scale=Math.min(1,Math.min(frame.width/WIDTH,frame.height/HEIGHT));
            width=WIDTH*scale;height=HEIGHT*scale;
            x=frame.x+(frame.width-width)/2;y=frame.y+(frame.height-height)/2;
        }
    }
}
