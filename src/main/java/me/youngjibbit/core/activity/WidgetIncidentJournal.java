package me.youngjibbit.core.activity;

import java.util.Arrays;
import java.util.Locale;

/** Bounded scalar observations only; no module, packet, renderer or gameplay mutation. */
public final class WidgetIncidentJournal {
    public static final long INCIDENT_TTL_NANOS=3_000_000_000L;
    public static final long CORRELATION_NANOS=2_000_000_000L;
    private static final long FPS_COOLDOWN_NANOS=4_000_000_000L;
    private static final WidgetIncidentJournal GLOBAL=new WidgetIncidentJournal();
    private final ExploitActivitySnapshot[] activity=new ExploitActivitySnapshot[ExploitActivitySnapshot.Producer.values().length];
    private Object world,connection;
    private long epoch,nextId,lastFrame,frameWindowStart,lastFpsIncident,correctionRun=-1,correctionSequence;
    private int frameSamples,windowFrames;
    private double baselineMillis,baselineFps;
    private WidgetIncident fpsIncident,exploitIncident;

    public static WidgetIncidentJournal global(){return GLOBAL;}

    /** The producer's actual world/connection/packet epoch fences every observation. */
    public synchronized void context(Object world,Object connection,long epoch){
        if(this.world==world&&this.connection==connection&&this.epoch==epoch)return;
        clear();this.world=world;this.connection=connection;this.epoch=epoch;
    }
    public synchronized void clear(){
        world=connection=null;epoch=0;Arrays.fill(activity,null);
        fpsIncident=exploitIncident=null;correctionRun=-1;correctionSequence=0;
        lastFpsIncident=0;suspendFrames();
    }
    /** Call while frames are not being sampled, e.g. an unfocused/hidden HUD. */
    public synchronized void suspendFrames(){
        lastFrame=frameWindowStart=0;frameSamples=windowFrames=0;baselineMillis=baselineFps=0;
    }
    public synchronized void observeFrame(long now){
        if(world==null||connection==null)return;
        if(lastFrame==0){lastFrame=frameWindowStart=now;return;}
        long elapsed=now-lastFrame;lastFrame=now;
        if(elapsed<=0||elapsed>1_000_000_000L){suspendFrames();lastFrame=frameWindowStart=now;return;}
        double millis=elapsed/1_000_000.0;
        boolean available=lastFpsIncident==0||now-lastFpsIncident>=FPS_COOLDOWN_NANOS;
        if(frameSamples>=20&&millis>=50&&millis>baselineMillis*2.2&&available){
            fpsIncident=new WidgetIncident(WidgetIncident.Kind.FPS,++nextId,"Slow client frame",
                    "Observed "+number(millis)+" ms; recent baseline "+number(baselineMillis)+" ms. Cause unconfirmed.",
                    number(millis)+" ms",now,now+INCIDENT_TTL_NANOS);
            lastFpsIncident=now;available=false;
        }
        baselineMillis=frameSamples==0?millis:baselineMillis+(millis-baselineMillis)*.04;
        frameSamples=Math.min(1000,frameSamples+1);windowFrames++;
        long window=now-frameWindowStart;
        if(window>=500_000_000L){
            double currentFps=windowFrames*1_000_000_000.0/window;
            if(baselineFps>0&&baselineFps-currentFps>=10&&currentFps<baselineFps*.7&&available){
                fpsIncident=new WidgetIncident(WidgetIncident.Kind.FPS,++nextId,"FPS drop",
                        "Measured across "+Math.round(window/1_000_000.0)+" ms. Cause unconfirmed.",
                        Math.round(baselineFps)+" → "+Math.round(currentFps)+" FPS",now,now+INCIDENT_TTL_NANOS);
                lastFpsIncident=now;
            }
            baselineFps=baselineFps==0?currentFps:baselineFps+(currentFps-baselineFps)*.15;
            frameWindowStart=now;windowFrames=0;
        }
    }
    /** A toggled module, an empty queue or a stale snapshot cannot create activity evidence. */
    public synchronized void observeActivity(ExploitActivitySnapshot sample,long now){
        if(world==null||connection==null||sample==null||sample.sourceEpoch!=epoch||!sample.isFresh(now))return;
        int index=sample.producer.ordinal();
        if(activity[index]==null||sample.observedAtNanos>=activity[index].observedAtNanos)activity[index]=sample;
    }
    public synchronized void observeCorrection(LocalCorrectionSnapshot correction,long now){
        if(world==null||connection==null||correction==null||!correction.isFresh(now))return;
        if(correction.getRunId()==correctionRun&&correction.getSequence()<=correctionSequence)return;
        correctionRun=correction.getRunId();correctionSequence=correction.getSequence();
        if(correction.getDistance()<.1&&correction.getAngle()<5)return;
        ExploitActivitySnapshot preceding=null;
        long observed=correction.getObservedAtNanos();
        for(ExploitActivitySnapshot sample:activity){
            if(sample==null)continue;
            long age=observed-sample.observedAtNanos;
            if(age>=0&&age<=CORRELATION_NANOS&&(preceding==null||sample.observedAtNanos>preceding.observedAtNanos))preceding=sample;
        }
        if(preceding==null)return;
        long afterMillis=(observed-preceding.observedAtNanos)/1_000_000L;
        exploitIncident=new WidgetIncident(WidgetIncident.Kind.EXPLOIT,++nextId,"Correction observed",
                afterMillis+" ms after "+preceding.name+" "+preceding.state.toLowerCase(Locale.ROOT)
                        +" ("+preceding.value+"). Cause unconfirmed.",
                number(correction.getDistance())+" blocks / "+number(correction.getAngle())+"°",
                observed,observed+INCIDENT_TTL_NANOS);
    }
    /** A successfully applied correction itself advances the packet epoch. Preserve only the
     * resulting display evidence across that exact transition, never across a different world. */
    public synchronized void appliedCorrection(Object world,Object connection,long previousEpoch,long nextEpoch,
                                               LocalCorrectionSnapshot correction,long now){
        if(this.world!=world||this.connection!=connection||epoch!=previousEpoch||nextEpoch<previousEpoch)return;
        observeCorrection(correction,now);
        epoch=nextEpoch;Arrays.fill(activity,null);
    }
    /** Querying does not extend the incident lifetime or consume another widget's observation. */
    public synchronized WidgetIncident latest(WidgetIncident.Kind kind,long now){
        WidgetIncident result=kind==WidgetIncident.Kind.FPS?fpsIncident:kind==WidgetIncident.Kind.EXPLOIT?exploitIncident:null;
        return result!=null&&result.isFresh(now)?result:null;
    }
    private static String number(double value){return String.format(Locale.ROOT,"%.1f",value);}
}
