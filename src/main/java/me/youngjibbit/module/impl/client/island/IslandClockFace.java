package me.youngjibbit.module.impl.client.island;

/** Immutable actual clock hands for one render frame, in GUI coordinates; no scheduler. */
public final class IslandClockFace {
    public final String time;
    public final int hour,minute;
    public final double second;
    public final float hourX,hourY,minuteX,minuteY,secondX,secondY;
    private IslandClockFace(String time,int hour,int minute,double second){
        this.time=time;this.hour=hour;this.minute=minute;this.second=second;
        double actualMinute=minute+second/60;
        double minuteAngle=2*Math.PI*actualMinute/60-Math.PI/2;
        double hourAngle=2*Math.PI*((hour%12)+actualMinute/60)/12-Math.PI/2;
        double secondAngle=2*Math.PI*second/60-Math.PI/2;
        hourX=(float)Math.cos(hourAngle);hourY=(float)Math.sin(hourAngle);
        minuteX=(float)Math.cos(minuteAngle);minuteY=(float)Math.sin(minuteAngle);
        secondX=(float)Math.cos(secondAngle);secondY=(float)Math.sin(secondAngle);
    }
    /** Capture once before deferred painting. Reduced motion uses truthful one-second steps. */
    public static IslandClockFace capture(long epochMillis,java.time.ZoneId zone,boolean smooth){
        java.time.ZonedDateTime current=java.time.Instant.ofEpochMilli(epochMillis).atZone(zone);
        int hour=current.getHour(),minute=current.getMinute();
        double second=current.getSecond()+(smooth?current.getNano()/1_000_000_000.0:0);
        String label=(hour<10?"0":"")+hour+":"+(minute<10?"0":"")+minute;
        return new IslandClockFace(label,hour,minute,second);
    }
    /** Legacy fixture parsing; live rendering supplies capture(...) every frame. */
    public static IslandClockFace from(String time){
        if(time==null||time.length()!=5||time.charAt(2)!=':')return null;
        for(int i:new int[]{0,1,3,4})if(time.charAt(i)<'0'||time.charAt(i)>'9')return null;
        int hour=(time.charAt(0)-'0')*10+time.charAt(1)-'0';
        int minute=(time.charAt(3)-'0')*10+time.charAt(4)-'0';
        return hour<24&&minute<60?new IslandClockFace(time,hour,minute,0):null;
    }
}
