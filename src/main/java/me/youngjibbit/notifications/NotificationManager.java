package me.youngjibbit.notifications;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
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
    private NotificationManager(){}
    public static void show(Notification notification){
        if(notification==null)return;
        synchronized(inbox){
            for(Message message:inbox)if(message.notification.key().equals(notification.key())){message.count=Math.min(999,message.count+1);return;}
            if(inbox.size()==64)inbox.removeFirst();
            inbox.addLast(new Message(notification));
        }
    }
    public static void success(String title,String message){show(new Notification(NotificationType.SUCCESS,title,message,0));}
    public static void error(String title,String message){show(new Notification(NotificationType.ERROR,title,message,0));}
    static List<Message> drain(){synchronized(inbox){List<Message> result=new ArrayList<>(inbox);inbox.clear();return result;}}
    static int inboxSize(){synchronized(inbox){return inbox.size();}}
    public static void update(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null||!mc.isCallingFromMinecraftThread())return;
        ClientSettings c=ClientSettings.current();long now=System.nanoTime();float dt=previous==0?0:Math.min(.1f,(now-previous)/1e9f);previous=now;
        if(c!=null&&!c.notifications.getValue()){drain();timeline.clear();return;}
        ScaledResolution sr=new ScaledResolution(mc);
        int visible=Math.min(c==null?3:c.notificationVisible.getValue().intValue(),Math.max(1,(sr.getScaledHeight()-16)/84));
        timeline.advance(drain(),now,dt,visible,c==null?32:c.notificationQueue.getValue().intValue(),c==null?5:c.notificationDuration.getValue().floatValue(),UiEffects.motion(),type->allowed(c,type));
    }
    private static boolean allowed(ClientSettings c,NotificationType type){
        if(c==null)return true;
        switch(type){case ERROR:return c.errors.getValue();case WARNING:return c.warnings.getValue();case SUCCESS:case ENABLED:return c.success.getValue();default:return c.info.getValue();}
    }
    public static void render(){
        Minecraft mc=Minecraft.getMinecraft();if(mc==null||!mc.isCallingFromMinecraftThread()||Pulsar.INSTANCE.fontManager==null)return;
        update();int count=timeline.visible.size();
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
                    icon(entry.notification.type,fx+15,fy+16,opacity);
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
    public static void releaseResources(){drain();timeline.clear();stackScene.close();for(int i=0;i<surfaces.length;i++)if(surfaces[i]!=null){surfaces[i].close();surfaces[i]=null;}previous=0;}

    static final class Message {final Notification notification;int count=1;Message(Notification n){notification=n;}}
    static final class Entry {
        final Notification notification;int count;long started,expires;float alpha,y=Float.NaN;Layout layout;float layoutWidth,layoutHeight;int layoutCount;
        Entry(Message m){notification=m.notification;count=m.count;}
    }
    /** Pure monotonic-time lifecycle, independently testable without Minecraft or OpenGL. */
    static final class Timeline {
        final List<Entry> visible=new ArrayList<>();final Deque<Entry> pending=new ArrayDeque<>();long now;float dt;
        void clear(){visible.clear();pending.clear();}
        void advance(List<Message> messages,long now,float dt,int maxVisible,int maxPending,float duration,boolean motion,Predicate<NotificationType> allowed){
            this.now=now;this.dt=dt;
            for(Message message:messages){
                if(!allowed.test(message.notification.type))continue;
                Entry match=null;for(Entry e:visible)if(e.notification.key().equals(message.notification.key()))match=e;
                if(match==null)for(Entry e:pending)if(e.notification.key().equals(message.notification.key()))match=e;
                if(match!=null){match.count=Math.min(999,match.count+message.count);if(visible.contains(match))match.expires=Math.min(match.started+30000000000L,now+seconds(match,duration));}
                else{if(pending.size()>=maxPending)pending.removeFirst();pending.addLast(new Entry(message));}
            }
            pending.removeIf(e->!allowed.test(e.notification.type));while(pending.size()>maxPending)pending.removeFirst();
            for(int i=0;i<visible.size();i++){Entry e=visible.get(i);if(i>=maxVisible||!allowed.test(e.notification.type))e.expires=Math.min(now,e.expires);boolean leaving=now>=e.expires;float target=leaving?0:1;e.alpha+=(target-e.alpha)*(motion?1-(float)Math.exp(-dt*16):1);}
            visible.removeIf(e->now>=e.expires&&e.alpha<.01f);
            while(visible.size()<maxVisible&&!pending.isEmpty()){Entry e=pending.removeFirst();e.started=now;e.expires=now+seconds(e,duration);e.alpha=motion?0:1;visible.add(e);}
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
    private static void icon(NotificationType type,float x,float y,float alpha){
        try(RenderState state=new RenderState()){
            GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glLineWidth(1.5f);
            int color=iconColor(type);
            glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);glTranslatef(x,y,0);
            if(type==NotificationType.WARNING){glBegin(GL_LINE_LOOP);glVertex2f(0,-7);glVertex2f(7,6);glVertex2f(-7,6);glEnd();glBegin(GL_LINES);glVertex2f(0,-2);glVertex2f(0,1);glVertex2f(0,3);glVertex2f(0,4);glEnd();}
            else{glBegin(GL_LINE_LOOP);for(int i=0;i<32;i++){double a=i*Math.PI/16;glVertex2f((float)Math.cos(a)*7,(float)Math.sin(a)*7);}glEnd();glBegin(GL_LINES);
                if(type==NotificationType.ERROR){glVertex2f(-3,-3);glVertex2f(3,3);glVertex2f(3,-3);glVertex2f(-3,3);}else if(type==NotificationType.SUCCESS||type==NotificationType.ENABLED){glVertex2f(-3,0);glVertex2f(-.5f,3);glVertex2f(-.5f,3);glVertex2f(4,-3);}else{glVertex2f(0,-4);glVertex2f(0,-3);glVertex2f(0,-1);glVertex2f(0,4);}glEnd();}
        }
    }
}
