package me.youngjibbit.module.impl.render;

import me.youngjibbit.gui.HudSpring;

/** Bounded feedback for observed changes, with no idle animation or per-frame allocation. */
final class CompactHudMotion {
    private final HudSpring spring=new HudSpring();
    private boolean initialized;
    private long previous;

    float step(long observation,float seconds,boolean animations,boolean reduced,String intensity){
        boolean changed=initialized&&previous!=observation;
        previous=observation;initialized=true;
        if(!animations||reduced)return spring.snap(0);
        if(changed)spring.snap(1);
        return Math.max(0,Math.min(1,spring.step(0,seconds,animations,reduced,intensity)));
    }
    void reset(){initialized=false;previous=0;spring.reset();}

    static float fraction(long elapsedNanos,long periodNanos){
        if(elapsedNanos<=0||periodNanos<=0)return 0;
        return (elapsedNanos%periodNanos)/(float)periodNanos;
    }
    static String count(int value){
        long n=Math.max(0,value);
        if(n<1000)return Long.toString(n);
        if(n<1_000_000)return compact(n,1000,"k");
        if(n<1_000_000_000)return compact(n,1_000_000,"m");
        return compact(n,1_000_000_000,"b");
    }
    private static String compact(long value,long divisor,String suffix){
        long whole=value/divisor,tenth=value%divisor*10/divisor;
        return whole+(whole<100&&tenth>0?"."+tenth:"")+suffix;
    }
}
