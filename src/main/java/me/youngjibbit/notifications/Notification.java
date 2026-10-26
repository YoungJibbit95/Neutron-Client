package me.youngjibbit.notifications;

/** Immutable producer message. A nonpositive duration inherits the client setting on display. */
public final class Notification {
    public final NotificationType type;
    public final String title,message;
    public final int seconds;
    public Notification(NotificationType type,String title,String message,int seconds){
        this.type=type==null?NotificationType.INFO:type;
        this.title=clean(title,96);this.message=clean(message,512);
        this.seconds=seconds<=0?0:Math.min(15,seconds);
    }
    private static String clean(String text,int limit){
        if(text==null)return "";
        text=text.replaceAll("[\\p{Cntrl}&&[^\\n]]","");
        return text.length()>limit?text.substring(0,limit-1)+"…":text;
    }
    String key(){return type.name()+"\n"+title+"\n"+message;}
}
