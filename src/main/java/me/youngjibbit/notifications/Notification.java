package me.youngjibbit.notifications;

/** Immutable producer message. A nonpositive duration inherits the client setting on display. */
public final class Notification {
    public final NotificationType type;
    public final String title,message;
    public final int seconds;
    private final String channel;
    public Notification(NotificationType type,String title,String message,int seconds){
        this(type,title,message,seconds,"");
    }
    private Notification(NotificationType type,String title,String message,int seconds,String channel){
        this.type=type==null?NotificationType.INFO:type;
        this.title=clean(title,96);this.message=clean(message,512);
        this.seconds=seconds<=0?0:Math.min(15,seconds);
        this.channel=channel;
    }
    /** Repeated observations on one channel replace the displayed facts, without adding another card. */
    public static Notification latest(String channel,NotificationType type,String title,String message,int seconds){
        String key=clean(channel,96).trim();
        if(key.isEmpty())throw new IllegalArgumentException("A live notification needs a channel");
        return new Notification(type,title,message,seconds,key);
    }
    private static String clean(String text,int limit){
        if(text==null)return "";
        text=text.replaceAll("[\\p{Cntrl}&&[^\\n]]","");
        return text.length()>limit?text.substring(0,limit-1)+"…":text;
    }
    boolean replaces(){return !channel.isEmpty();}
    String key(){return replaces()?type.name()+"\nlive:"+channel:type.name()+"\n"+title+"\n"+message;}
}
