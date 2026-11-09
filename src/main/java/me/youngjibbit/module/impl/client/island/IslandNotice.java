package me.youngjibbit.module.impl.client.island;

import java.util.Locale;

/** Immutable display copy of a completed module change or a measured local correction. */
public final class IslandNotice {
    public final IslandStateMachine.State state;
    public final String eventId,title,detail,distanceText,angleText;
    public final long observedAtNanos,sourceRun,sequence;
    public final boolean enabled;
    private IslandNotice(IslandStateMachine.State state,String eventId,String title,String detail,
                         long observedAtNanos,long sourceRun,long sequence,boolean enabled,String distanceText,String angleText){
        this.state=state;this.eventId=eventId;this.title=title;this.detail=detail;
        this.observedAtNanos=observedAtNanos;this.sourceRun=sourceRun;this.sequence=sequence;
        this.enabled=enabled;this.distanceText=distanceText;this.angleText=angleText;
    }
    public static IslandNotice module(String id,String name,boolean enabled,long sequence,long nanos){
        if(id==null||id.isEmpty()||name==null||name.isEmpty()||sequence<1)return null;
        return new IslandNotice(IslandStateMachine.State.MODULE_TOAST,"module:"+id+":"+sequence,name,
                enabled?"Enabled":"Disabled",nanos,0,sequence,enabled,"","");
    }
    public static IslandNotice correction(long run,long sequence,long nanos,double distance,float angle){
        if(run<0||sequence<1||!Double.isFinite(distance)||distance<0||!Float.isFinite(angle)||angle<0)return null;
        return new IslandNotice(IslandStateMachine.State.POSSIBLE_SETBACK,"correction:"+run+":"+sequence,
                "Possible setback","Local correction · cause unknown",nanos,run,sequence,false,
                String.format(Locale.ROOT,"%.2f blocks",distance),String.format(Locale.ROOT,"%.1f°",angle));
    }
    public long lifetimeNanos(){return state==IslandStateMachine.State.POSSIBLE_SETBACK?2_400_000_000L:1_900_000_000L;}
    public boolean isFresh(long now){long age=now-observedAtNanos;return age>=0&&age<lifetimeNanos();}
}
