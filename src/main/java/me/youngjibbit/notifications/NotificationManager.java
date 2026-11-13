package me.youngjibbit.notifications;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import java.util.*;
import java.util.function.Predicate;
import static org.lwjgl.opengl.GL11.*;

/** Producers only touch the bounded inbox. Timeline, layout and GPU resources belong to the client thread. */
public final class NotificationManager {
    private static final Deque<Message> inbox=new ArrayDeque<>();
    private static final Timeline timeline=new Timeline();
    private static final ArrayListSurface[] surfaces=new ArrayListSurface[5];
    private static final ArrayListSurface stackScene=new ArrayListSurface();
    private static long previous;
    private static boolean islandRoute;
    private NotificationManager(){}
    public static void show(Notification notification){
        enqueue(notification,false);
    }
    /** Refresh a visible/pending live card during a producer's cooldown; never start or prolong one. */
    public static void updateIfPresent(Notification notification){
        if(notification!=null&&notification.replaces())enqueue(notification,true);
    }
    private static void enqueue(Notification notification,boolean updateOnly){
        if(notification==null)return;
        synchronized(inbox){
            for(Message message:inbox)if(message.notification.key().equals(notification.key())){
                message.count=Math.min(999,message.count+1);
                if(notification.replaces())message.notification=notification;
                message.updateOnly&=updateOnly;
                return;
            }
            if(inbox.size()==64){if(updateOnly)return;inbox.removeFirst();}
            inbox.addLast(new Message(notification,updateOnly));
        }
    }
    public static void success(String title,String message){show(new Notification(NotificationType.SUCCESS,title,message,0));}
    public static void error(String title,String message){show(new Notification(NotificationType.ERROR,title,message,0));}
    static List<Message> drain(){synchronized(inbox){List<Message> result=new ArrayList<>(inbox);inbox.clear();return result;}}
    static int inboxSize(){synchronized(inbox){return inbox.size();}}
    public static void update(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null||!mc.isCallingFromMinecraftThread())return;
        ClientSettings c=ClientSettings.current();long now=System.nanoTime();float dt=previous==0?0:Math.min(.1f,(now-previous)/1e9f);previous=now;
        if(c!=null&&!c.notifications.getValue()){drain();timeline.clear();islandRoute=false;return;}
        WatermarkHUDModule watermark=Pulsar.INSTANCE.getMm()==null?null:
                (WatermarkHUDModule)Pulsar.INSTANCE.getMm().getModule(WatermarkHUDModule.class);
        islandRoute=watermark!=null&&watermark.routesNotificationsToIsland();
        int visible=1;
        if(!islandRoute){
            ScaledResolution sr=new ScaledResolution(mc);
            visible=Math.min(c==null?3:c.notificationVisible.getValue().intValue(),Math.max(1,(sr.getScaledHeight()-16)/84));
        }
        // The selected destination and its temporary visibility are separate. Hidden Island
        // messages retain their remaining display time instead of leaking into ordinary cards.
        timeline.route(islandRoute,now);
        timeline.pause(islandRoute&&!watermark.canPresentIslandNotifications(),now);
        timeline.advance(drain(),now,dt,visible,c==null?32:c.notificationQueue.getValue().intValue(),c==null?5:c.notificationDuration.getValue().floatValue(),UiEffects.motion(),type->allowed(c,type));
    }
    /** One immutable copy of the ordinary-card timeline: no second inbox or expiry clock. */
    public static IslandNotification islandNotification(){
        update();
        return islandRoute?timeline.islandSnapshot():null;
    }
    private static boolean allowed(ClientSettings c,NotificationType type){
        if(c==null)return true;
        switch(type){case ERROR:return c.errors.getValue();case WARNING:return c.warnings.getValue();case SUCCESS:case ENABLED:return c.success.getValue();default:return c.info.getValue();}
    }
    public static void render(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null||!mc.isCallingFromMinecraftThread()||Pulsar.INSTANCE.fontManager==null)return;
        update();int count=islandRoute?0:timeline.visible.size();
        for(int i=count;i<surfaces.length;i++)if(surfaces[i]!=null){surfaces[i].close();surfaces[i]=null;}
        if(count==0){stackScene.close();return;}
        ClientSettings c=ClientSettings.current();ScaledResolution sr=new ScaledResolution(mc);
        String position=c==null?"Bottom Right":c.notificationPosition.getValue(),design=c==null?"Dark":c.notificationDesign.getValue();
        stackScene.withScene(Theme.getDefaultTheme(),design.equals("Global")?-1:design.equals("Liquid Glass")?1:0,()->drawCards(count,c,sr,position,design));
    }
    private static void drawCards(int count,ClientSettings c,ScaledResolution sr,String position,String design){
        float width=Math.min(sr.getScaledWidth()-16,c==null?260:c.notificationWidth.getValue().floatValue());
        boolean left=position.endsWith("Left"),top=position.startsWith("Top");
        float maxHeight=Math.min(112,(sr.getScaledHeight()-16-(count-1)*7f)/count),offset=8;
        TTFFontRenderer body=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18"),heading=Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 20");
        try(RenderState state=new RenderState()){
            state.screenSpace();
            for(int i=0;i<count;i++){
                Entry entry=timeline.visible.get(i);if(entry.layout==null||entry.layoutWidth!=width||entry.layoutHeight!=maxHeight||entry.layoutCount!=entry.count){entry.layout=new Layout(entry,width,maxHeight,heading,body);entry.layoutWidth=width;entry.layoutHeight=maxHeight;entry.layoutCount=entry.count;}Layout layout=entry.layout;
                float target=top?offset:sr.getScaledHeight()-offset-layout.height;
                if(Float.isNaN(entry.y))entry.y=target;
                entry.y+=(target-entry.y)*UiEffects.response(timeline.dt,16);
                // Resizes and a reduced visible limit cannot leave cards offscreen.
                entry.y=Math.max(8,Math.min(entry.y,sr.getScaledHeight()-layout.height-8));
                float x=(left?8:sr.getScaledWidth()-width-8)+(left?-1:1)*(1-entry.alpha)*22;
                if(surfaces[i]==null)surfaces[i]=new ArrayListSurface();
                final float fx=x,fy=entry.y,opacity=entry.alpha;
                Runnable content=()->{
                    String presentation=c==null?"Follow Global":c.notificationTheme.getValue();
                    if(!presentation.equals("Follow Global"))decorate(presentation,fx,fy,width,layout.height,opacity);
                    icon(entry.notification.type,fx+15,fy+16,opacity,
                            iconReveal(timeline.now,entry.started,UiEffects.motion()));
                    heading.drawString(layout.title,fx+29,fy+8,UiEffects.alpha(0xfff0f3fa,opacity));
                    for(int line=0;line<layout.lines.size();line++)body.drawString(layout.lines.get(line),fx+12,fy+29+line*layout.lineHeight,UiEffects.alpha(0xffd0d8e5,opacity));
                    float remaining=Math.max(0,Math.min(1,(entry.expires-timeline.now)/(float)Math.max(1,entry.expires-entry.started)));
                    UIUtils.drawGradientHorizontal(fx+8,fy+layout.height-4,(width-16)*remaining,1.5f,.75f,UiEffects.color(Theme.getDefaultTheme().getPrimaryGradientRGB(),opacity),UiEffects.color(Theme.getDefaultTheme().getSecondaryGradientRGB(),opacity));
                };
                if(design.equals("Global"))surfaces[i].inheritedPanel(x,entry.y,width,layout.height,5,Theme.getDefaultTheme(),opacity,content);
                else surfaces[i].styledPanel(x,entry.y,width,layout.height,5,Theme.getDefaultTheme(),design.equals("Liquid Glass"),true,design.equals("Outline")?.28f:.91f,design.equals("Outline")?1:.45f,design.equals("Liquid Glass"),opacity,content);
                offset+=layout.height+7;
            }
        }
    }
    /** Additional ink inside the existing material card. No layout, lifetime, status or clock ownership. */
    private static void decorate(String style,float x,float y,float width,float height,float opacity){
        Theme theme=Theme.getDefaultTheme();UiEffects.Options options=UiEffects.options();
        int first=UiEffects.gradientAt(theme,UiEffects.Channel.BORDER,x+5,y+5),second=UiEffects.gradientAt(theme,UiEffects.Channel.BORDER,x+width-5,y+height-5);
        ClientSettings settings=ClientSettings.current();boolean accent=settings!=null&&settings.borderColor.getValue().equals("Accent");
        int edgeFirst=accent?theme.getAccentColor().getRGB():first,edgeSecond=accent?theme.getAccentColor().getRGB():second;
        if(style.equals("Obsidian")){
            UIUtils.drawDecorationRound(x+4,y+4,width-8,height-8,3,UiEffects.color(0xff080a10,.22f*opacity));
            if(options.border)UIUtils.drawDecorationRound(x+6,y+10,2,height-20,1,UiEffects.color(Theme.interpolateRGB(0xff535963,edgeFirst,.22f),.7f*options.borderOpacity*opacity));
        }else if(style.equals("Aurora")){
            UIUtils.drawGradientHorizontal(x+5,y+4,width-10,20,3,UiEffects.color(first,.18f*opacity),UiEffects.color(second,.18f*opacity));
            UIUtils.drawDecorationRound(x+12,y+25,width-24,1,.5f,UiEffects.color(second,.3f*opacity));
        }else if(style.equals("Neon Edge")){
            if(options.border){
                UIUtils.drawDecorationRound(x+4,y+4,width-8,2,1,UiEffects.color(edgeFirst,options.borderOpacity*opacity));
                UIUtils.drawDecorationRound(x+4,y+4,2,18,1,UiEffects.color(edgeFirst,options.borderOpacity*opacity));
                UIUtils.drawDecorationRound(x+width-6,y+height-22,2,18,1,UiEffects.color(edgeSecond,options.borderOpacity*opacity));
                UIUtils.drawDecorationRound(x+width-26,y+height-6,22,2,1,UiEffects.color(edgeSecond,options.borderOpacity*opacity));
            }
        }else if(style.equals("Minimal")){
            // Compact inset rhythm without altering the established card/queue/position geometry.
            UIUtils.drawDecorationRound(x+9,y+6,width-18,height-12,2,UiEffects.color(0xff0c1018,.10f*opacity));
            UIUtils.drawDecorationRound(x+29,y+25,Math.min(42,width-42),1,.5f,UiEffects.color(0xffb2bbc9,.28f*opacity));
        }
    }
    public static void releaseResources(){drain();timeline.clear();stackScene.close();for(int i=0;i<surfaces.length;i++)if(surfaces[i]!=null){surfaces[i].close();surfaces[i]=null;}previous=0;islandRoute=false;}

    /** Display facts only. The timeline owns filtering, coalescing, queue bounds and expiry. */
    public static final class IslandNotification {
        public final long id,startedAtNanos,expiresAtNanos;
        public final String title,message;
        public final NotificationType type;
        public final int count;
        public final float remaining;
        public IslandNotification(long id,Notification notification,int count,long started,long expires,long now){
            this.id=id;title=notification.title;message=notification.message;type=notification.type;
            this.count=Math.max(1,Math.min(999,count));startedAtNanos=started;expiresAtNanos=expires;
            remaining=Math.max(0,Math.min(1,(expires-now)/(float)Math.max(1,expires-started)));
        }
        public boolean isFresh(long now){return now>=startedAtNanos&&now<expiresAtNanos;}
    }

    static final class Message {
        Notification notification;int count=1;boolean updateOnly;
        Message(Notification n){this(n,false);}
        Message(Notification n,boolean updateOnly){notification=n;this.updateOnly=updateOnly;}
    }
    static final class Entry {
        Notification notification;int count;long started,expires;float alpha,y=Float.NaN;Layout layout;float layoutWidth,layoutHeight;int layoutCount;
        long id,parkedRemaining=-1;
        Entry(Message m){notification=m.notification;count=m.count;}
    }
    /** Pure monotonic-time lifecycle, independently testable without Minecraft or OpenGL. */
    static final class Timeline {
        final List<Entry> visible=new ArrayList<>();final Deque<Entry> pending=new ArrayDeque<>();long now;float dt;
        private long nextId;
        private boolean island,paused;
        private long pausedAt;
        void clear(){visible.clear();pending.clear();island=false;paused=false;pausedAt=0;}
        void route(boolean toIsland,long now){
            if(island==toIsland)return;
            island=toIsland;
            if(!island)return;
            for(int i=visible.size()-1;i>=1;i--){
                Entry entry=visible.remove(i);
                entry.parkedRemaining=Math.max(0,entry.expires-now);
                if(entry.parkedRemaining>0)pending.addFirst(entry);
            }
        }
        void pause(boolean value,long now){
            if(paused==value)return;
            if(value){paused=true;pausedAt=now;return;}
            long elapsed=Math.max(0,now-pausedAt);
            for(Entry entry:visible){entry.started+=elapsed;entry.expires+=elapsed;}
            paused=false;pausedAt=0;
        }
        IslandNotification islandSnapshot(){
            if(!island||paused||visible.isEmpty())return null;
            Entry entry=visible.get(0);
            return now>=entry.expires?null:new IslandNotification(entry.id,entry.notification,entry.count,entry.started,entry.expires,now);
        }
        void advance(List<Message> messages,long now,float dt,int maxVisible,int maxPending,float duration,boolean motion,Predicate<NotificationType> allowed){
            final long at=paused?pausedAt:now;
            this.now=at;this.dt=dt;
            for(Message message:messages){
                if(!allowed.test(message.notification.type))continue;
                Entry match=null;for(Entry e:visible)if(e.notification.key().equals(message.notification.key()))match=e;
                if(match==null)for(Entry e:pending)if(e.notification.key().equals(message.notification.key()))match=e;
                if(match!=null){
                    match.count=Math.min(999,match.count+message.count);
                    if(message.notification.replaces()){
                        match.notification=message.notification;
                        match.layout=null;
                    }
                    if(!message.updateOnly&&visible.contains(match))
                        match.expires=Math.min(match.started+30000000000L,at+seconds(match,duration));
                }
                else if(!message.updateOnly){if(pending.size()>=maxPending)pending.removeFirst();Entry entry=new Entry(message);entry.id=++nextId;pending.addLast(entry);}
            }
            pending.removeIf(e->!allowed.test(e.notification.type));while(pending.size()>maxPending)pending.removeFirst();
            if(paused){visible.removeIf(e->!allowed.test(e.notification.type));return;}
            for(int i=0;i<visible.size();i++){Entry e=visible.get(i);if(i>=maxVisible||!allowed.test(e.notification.type))e.expires=Math.min(at,e.expires);boolean leaving=at>=e.expires;float target=leaving?0:1;e.alpha+=(target-e.alpha)*(motion&&!island?1-(float)Math.exp(-dt*16):1);}
            visible.removeIf(e->at>=e.expires&&e.alpha<.01f);
            while(visible.size()<maxVisible&&!pending.isEmpty()){Entry e=pending.removeFirst();e.started=at;e.expires=at+(e.parkedRemaining>=0?e.parkedRemaining:seconds(e,duration));e.parkedRemaining=-1;e.alpha=motion&&!island?0:1;visible.add(e);}
        }
        private long seconds(Entry e,float duration){return (long)((e.notification.seconds>0?e.notification.seconds:duration)*1000000000L);}
    }
    static final class Layout {
        final String title;final List<String> lines;final float height,lineHeight;
        Layout(Entry entry,float width,float maxHeight,TTFFontRenderer heading,TTFFontRenderer body){
            title=fit(entry.notification.title+(entry.count>1?" ×"+entry.count:""),width-42,heading);
            lineHeight=body.getHeight("Ay")+2;
            int maxLines=Math.max(1,(int)((maxHeight-39)/lineHeight));lines=wrap(entry.notification.message,width-24,body,maxLines);height=Math.min(maxHeight,Math.max(52,36+lines.size()*lineHeight));
        }
    }
    private static String fit(String s,float width,TTFFontRenderer font){if(font.getWidth(s)<=width)return s;int end=s.length();while(end>0&&font.getWidth(s.substring(0,end)+"…")>width)end--;return s.substring(0,end)+"…";}
    private static List<String> wrap(String text,float width,TTFFontRenderer font,int maxLines){
        List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();
        outer:for(String paragraph:text.split("\\n",-1)){
            for(String word:paragraph.trim().split("\\s+")){
                if(line.length()>0&&font.getWidth(line+" "+word)>width){lines.add(line.toString());line.setLength(0);if(lines.size()>maxLines)break outer;}
                while(font.getWidth(word)>width){int cut=1;while(cut<word.length()&&font.getWidth(word.substring(0,cut+1))<=width)cut++;lines.add(word.substring(0,cut));word=word.substring(cut);if(lines.size()>maxLines)break outer;}
                if(line.length()>0)line.append(' ');line.append(word);
            }
            lines.add(line.toString());line.setLength(0);if(lines.size()>maxLines)break;
        }
        if(lines.size()>maxLines){lines=new ArrayList<>(lines.subList(0,maxLines));lines.set(maxLines-1,fit(lines.get(maxLines-1)+" …",width,font));}
        return lines;
    }
    static int iconColor(NotificationType type){
        UiEffects.SemanticRole role=type==NotificationType.ERROR?UiEffects.SemanticRole.ERROR:type==NotificationType.WARNING?UiEffects.SemanticRole.WARNING:type==NotificationType.SUCCESS||type==NotificationType.ENABLED?UiEffects.SemanticRole.SUCCESS:UiEffects.SemanticRole.INFO;
        ClientSettings settings=ClientSettings.current();
        return UiEffects.semanticColor(role,Theme.getDefaultTheme().getAccentColor().getRGB(),settings==null||settings.semanticColorPriority.getValue());
    }
    static float iconReveal(long now,long started,boolean motion){
        if(!motion)return 1;
        float t=Math.max(0,Math.min(1,(now-started)/280_000_000f));
        return t*t*(3-2*t);
    }
    private static void icon(NotificationType type,float x,float y,float alpha,float reveal){
        try(RenderState state=new RenderState()){
            glTranslatef(x,y,0);glScalef(.68f,.68f,1);
            int kind=type==NotificationType.WARNING?2:type==NotificationType.ERROR?4
                    :type==NotificationType.SUCCESS||type==NotificationType.ENABLED?1:type==NotificationType.DISABLED?0:3;
            me.youngjibbit.module.impl.client.island.IslandGlyphRenderer.notice(0,0,kind,iconColor(type),alpha,reveal);
        }
    }
}
