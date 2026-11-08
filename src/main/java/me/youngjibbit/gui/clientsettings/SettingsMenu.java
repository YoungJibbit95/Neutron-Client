package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.notifications.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

/** Direct editors for existing Module Values: no second settings store or duplicate callbacks. */
public class SettingsMenu extends GuiScreen {
    private final GuiScreen parent;
    private final ArrayListSurface surface=new ArrayListSurface(),preview=new ArrayListSurface();
    private int group,focus,drag=-1,editing=-1;
    private float x,y,w,h,scroll,targetScroll;
    private String input="",status="";
    private boolean replace,previousRepeat;
    private final me.youngjibbit.gui.UiSpring[] actionMotion=new me.youngjibbit.gui.UiSpring[6];
    private int pressed=-1;
    private long lastFrame=System.nanoTime();
    private Value<?> editValue,dragValue;
    private ComboValue popupValue;
    private ClientSettings groupOwner;
    private HUD hudOwner;
    private Map<String,List<Value<?>>> groupCache;
    private List<String> sectionNames;
    private int popupChoice,popupOffset;
    private final Map<Value<?>,me.youngjibbit.gui.UiSpring> valueMotion=new IdentityHashMap<>();
    private final Map<String,me.youngjibbit.gui.UiSpring> categoryMotion=new HashMap<>();
    private final Map<Value<?>,me.youngjibbit.gui.HudSpring> controlSprings=new IdentityHashMap<>();
    private final SettingsMenuMotion presentation=new SettingsMenuMotion();
    // A dismissed dropdown remains a short, read-only visual until its spring closes.
    // Logical focus/capture always follows popupValue, never this outgoing snapshot.
    private ComboValue closingPopupValue;
    private ClipRect closingPopupBounds;
    private int closingPopupOffset,closingPopupChoice;
    public SettingsMenu(){this(null);}
    public SettingsMenu(GuiScreen parent){this.parent=parent;for(int i=0;i<actionMotion.length;i++)actionMotion[i]=new me.youngjibbit.gui.UiSpring();}
    private void resetMotion(){
        pressed=-1;
        for(me.youngjibbit.gui.UiSpring state:actionMotion)state.reset();
        controlSprings.clear();presentation.reset();
        closingPopupValue=null;closingPopupBounds=null;
        lastFrame=System.nanoTime();
    }
    @Override public void initGui(){
        resetMotion();
        if(Keyboard.isCreated()){
            previousRepeat=Keyboard.areRepeatEventsEnabled();
            Keyboard.enableRepeatEvents(true);
        }
        layout();
        // Resize/reinit preserves the logical popup, but starts with an immediately
        // readable disclosure instead of reviving stale progress from an old viewport.
        if(popupValue!=null){
            presentation.popup(true);
            presentation.resetCursor(popupBounds().y+4+(popupChoice-popupOffset)*22+5);
        }
    }
    private Map<String,List<Value<?>>> groups(){
        ClientSettings c=ClientSettings.current();HUD hud=HUD.current();
        if(groupCache==null||groupOwner!=c||hudOwner!=hud){
            groupOwner=c;hudOwner=hud;
            groupCache=c==null?new LinkedHashMap<>():new LinkedHashMap<>(c.groups());
            if(hud!=null)groupCache.put("HUD behavior",new ArrayList<>(hud.getValues()));
            sectionNames=new ArrayList<>(groupCache.keySet());
        }
        return groupCache;
    }
    private List<String> names(){groups();return sectionNames;}
    private List<Value<?>> rows(){List<String> names=names();if(names.isEmpty())return Collections.emptyList();group=Math.max(0,Math.min(group,names.size()-1));List<Value<?>> values=new ArrayList<>();for(Value<?> v:groups().get(names.get(group)))if(v.isVisible())values.add(v);return values;}
    private boolean rail(){return w>=560 && h>=330;}
    private float inset(){return rail()?154:14;}
    private void layout(){
        w=Math.min(740,Math.max(1,width-16));h=Math.min(464,Math.max(1,height-12));x=(width-w)/2;y=(height-h)/2;
        List<Value<?>> visible=rows();
        focus=Math.max(0,Math.min(focus,Math.max(0,visible.size()+5)));
        if(editValue!=null){editing=visible.indexOf(editValue);if(editing<0)editValue=null;}
        if(dragValue!=null){drag=visible.indexOf(dragValue);if(drag<0)dragValue=null;}
        if(popupValue!=null&&!visible.contains(popupValue))dismissPopup(false);
        float max=Math.max(0,visible.size()*42-view().height);
        targetScroll=Math.max(0,Math.min(targetScroll,max));scroll=Math.max(0,Math.min(scroll,max));
    }
    protected ClipRect view(){return new ClipRect(x+inset(),y+94,w-inset()-14,Math.max(1,h-186));}
    protected ClipRect rowBounds(int row){ClipRect v=view();return new ClipRect(v.x,v.y+row*42-scroll,v.width,38);}
    protected ClipRect controlBounds(int row){ClipRect r=rowBounds(row);float cw=Math.min(180,r.width*.45f);return new ClipRect(r.x+r.width-cw-7,r.y+4,cw,30);}
    protected ClipRect categoryBounds(int index){return new ClipRect(x+12,y+61+index*28,126,25);}
    protected ClipRect actionBounds(int action){
        if(action==0)return new ClipRect(rail()?x+inset():x+10,y+52,25,22);
        if(action==1)return new ClipRect(x+w-39,y+52,25,22);
        float gap=5,bw=(w-25)/4;return new ClipRect(x+5+(action-2)*(bw+gap),y+h-28,bw,21);
    }
    private TTFFontRenderer font(){return Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18");}
    private void text(String value,float x,float y,int color){font().drawString(value,x,y,color);}
    private String fit(String text,float width){TTFFontRenderer f=font();if(f.getWidth(text)<=width)return text;while(!text.isEmpty()&&f.getWidth(text+"â€¦")>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));return text.isEmpty()?"":text+"â€¦";}
    private void button(int id,String title,int mx,int my){
        me.youngjibbit.gui.UiSpring state=actionMotion[id];
        ClipRect b=GuiStyle.buttonBounds(actionBounds(id),state);
        UIUtils.drawDecorationRound(b.x,b.y,b.width,b.height,4,new Color(31,38,50));
        GuiStyle.feedback(b,state);
        text(fit(title,b.width-10),b.x+5,b.y+6,GuiStyle.ink(0xffe4ebf6,state));
    }
    protected void advanceMotion(float dt,int mx,int my){ClientSettings c=ClientSettings.current();int size=rows().size();for(int i=0;i<6;i++)actionMotion[i].stepForInput(dt,actionBounds(i).contains(mx,my),focus==size+i,pressed==i,true,c);}
    private String description(){String name=names().isEmpty()?"":names().get(group);switch(name){
        case "Appearance":return "Choose the surface, opacity and border of your UI.";
        case "Blur / Liquid Glass":return "Tune scene softness, refraction, tint and edge light.";
        case "Glow":return "Surface glow. Global off also disables text glow.";
        case "Gradients / Motion":return "Shared colors and motion, with optional separate channels.";
        case "HUD behavior":return "Arrange HUDs in chat. F7 toggles alignment guides.";
        case "Typography":return ClientSettings.current()!=null&&!ClientSettings.current().glow.getValue()?"Text glow is paused while Global Glow is off.":"UI font and text glow. Icons and explicit HUD fonts stay separate.";
        default:return "Choose how, where and for how long notifications appear.";
    }}
    @Override public void drawScreen(int mx,int my,float partialTicks){
        UiEffects.beginFrame();layout();
        long now=System.nanoTime();float dt=Math.max(0,Math.min(.1f,(now-lastFrame)/1e9f));lastFrame=now;
        scroll+=(targetScroll-scroll)*UiEffects.response(dt,18);
        // Rendering is presentation-only: slider writes belong to mouseClickMove.
        advanceMotion(dt,mx,my);
        ClientSettings motionSettings=ClientSettings.current();
        me.youngjibbit.gui.HudReveal.Frame entry=presentation.screen(true,dt,motionSettings);
        try(RenderState frameState=new RenderState()){
            frameState.screenSpace();
            Theme theme=Theme.getDefaultTheme();
            if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);else drawDefaultBackground();
            surface.panel(x,y,w,h,8,theme,true,true,true,5,.9f,.8f,()->{
                // A constant contrast layer, independent of hover and of the material preset.
                UIUtils.drawDecorationRound(x+1,y+1,w-2,h-2,7,new Color(13,16,23,175));
                TTFFontRenderer heading=Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 26");
                heading.drawString("Client settings",x+16,y+11,0xfff1f4fa);
                text(fit("Appearance, motion and type â€” changes apply live",w-32),x+16,y+33,0xffa6b2c5);
                // The global screen exit animation stays owned by GuiTransitions.
                // The entry accent uses the same spring policy; text/input remain ready.
                if(me.youngjibbit.theme.ThemeMode.APPROACH_TWO.equals(me.youngjibbit.theme.ThemeMode.selected())
                        &&entry.opacity>.01f)
                    UIUtils.drawDecorationGradientHorizontal(x+16,y+49+Math.max(0,entry.slideY*.22f),
                            Math.max(.01f,(w-32)*entry.opacity),2,1,
                            theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
                List<String> sections=names();
                if(rail()){
                    float markerY=presentation.category(categoryBounds(group).y+4,dt,motionSettings);
                    UIUtils.drawDecorationRound(x+12,markerY,2,categoryBounds(group).height-8,1,
                            theme.getPrimaryGradientColor());
                    for(int i=0;i<sections.size();i++){
                        ClipRect r=categoryBounds(i);boolean selected=i==group;
                        me.youngjibbit.gui.UiSpring motion=categoryMotion.computeIfAbsent(sections.get(i),k->new me.youngjibbit.gui.UiSpring());
                        motion.stepForInput(dt,popupValue==null&&r.contains(mx,my),false,false,true,motionSettings);
                        r = GuiStyle.buttonBounds(r, motion);
                        text(fit(sections.get(i),r.width-14),r.x+10,r.y+8,GuiStyle.ink(selected?0xfff1f4fa:0xffa4b0c3,motion));
                    }
                }
                button(0,"<",mx,my);button(1,">",mx,my);
                text(fit(sections.isEmpty()?"Settings unavailable":sections.get(group),w-inset()-78),x+inset()+33,y+59,0xfff1f4fa);
                text(fit(description(),view().width),view().x,y+80,0xffa7b4c7);
                try(GuiClip clip=GuiClip.push(view())){
                    List<Value<?>> values=rows();
                    for(int i=0;i<values.size();i++)if(!view().intersect(rowBounds(i)).isEmpty())drawValue(i,values.get(i),theme,dt,mx,my);
                }
                drawScrollBar();
                drawPreview(theme);
                button(2,"Reset group",mx,my);
                button(3,sections.size()>group&&sections.get(group).equals("HUD behavior")?"Reset positions":"Preview toast",mx,my);
                button(4,"Game options",mx,my);button(5,"Done",mx,my);
                text(fit(status.isEmpty()?"F6: section   Tab: focus   Right click: exact value   Ctrl+R: reset all":status,w-24),x+12,y+h-43,status.isEmpty()?0xffa7b4c7:0xffffbc99);
                drawPopup(mx,my,theme,dt,motionSettings);
            });
        }
    }

    private void drawValue(int index,Value<?> value,Theme theme,float dt,int mx,int my){
        ClipRect row=rowBounds(index),control=controlBounds(index);
        me.youngjibbit.gui.UiSpring motion=valueMotion.computeIfAbsent(value,k->new me.youngjibbit.gui.UiSpring());
        motion.stepForInput(dt,popupValue==null&&row.contains(mx,my),index==focus,drag==index,true,ClientSettings.current());
        // Only ink, the switch/thumb and the focus marks react; no hover rectangle.
        text(fit(UiEffects.settingLabel(value),control.x-row.x-12),row.x+5,row.y+13,GuiStyle.ink(0xffe1e7f1,motion));
        GuiStyle.feedback(row,motion);
        if(value instanceof CheckBoxValue){
            boolean on=((CheckBoxValue)value).getValue();
            float amount=follow(value,on?1:0,dt),sx=control.x+control.width-36,sy=row.y+11;
            text(on?"On":"Off",sx-25,row.y+13,on?0xffe5ecf7:0xff9da9bb);
            UIUtils.drawDecorationRound(sx,sy,34,16,8,new Color(Theme.interpolateRGB(0xff394251,theme.getPrimaryGradientRGB(),amount),true));
            UIUtils.drawDecorationRound(sx+3+18*amount,sy+3,10,10,5,new Color(GuiStyle.ink(0xfff3f6fc,motion),true));
        }else if(value instanceof SliderValue){
            SliderValue slider=(SliderValue)value;
            String shown=editing==index?input+"_":number(slider);
            text(fit(shown,control.width-8),control.x+3,row.y+6,GuiStyle.ink(0xffe5edf9,motion));
            float fraction=(float)((slider.getValue()-slider.getMin())/Math.max(.0001,slider.getMax()-slider.getMin()));
            float amount=follow(value,fraction,dt),track=control.width-8,cy=row.y+29;
            UIUtils.drawDecorationRound(control.x+4,cy,track,2,1,new Color(68,78,95));
            UIUtils.drawDecorationRound(control.x+4,cy,Math.max(.01f,track*amount),2,1,theme.getPrimaryGradientColor());
            UIUtils.drawDecorationRound(control.x+1+track*amount,cy-2,6,6,3,new Color(0xffedf3ff,true));
        }else{
            UIUtils.drawDecorationRound(control.x,control.y+3,control.width,24,4,new Color(28,35,47));
            text(fit(UiEffects.settingOption(value,String.valueOf(value.getValue())),control.width-28),control.x+7,control.y+10,GuiStyle.ink(0xffe5edf9,motion));
            text(popupValue==value?"^":"v",control.x+control.width-16,control.y+9,0xffaebbd0);
        }
    }
    private float follow(Value<?> value,float target,float dt){
        ClientSettings settings=ClientSettings.current();
        me.youngjibbit.gui.HudSpring spring=controlSprings.computeIfAbsent(value,
                key->new me.youngjibbit.gui.HudSpring());
        float next=spring.step(target,dt,
                settings==null||settings.animations.getValue(),
                settings!=null&&settings.reducedMotion.getValue(),
                settings==null?"Balanced":settings.motionIntensity.getValue());
        // A switch may overshoot a few pixels for bounce; a numeric slider never
        // extends beyond its interaction track. Neither writes a config Value.
        float low=value instanceof CheckBoxValue?-.06f:0;
        float high=value instanceof CheckBoxValue?1.06f:1;
        return Math.max(low,Math.min(high,next));
    }
    private String number(SliderValue v){
        return v.isInteger()?Integer.toString(v.getValue().intValue()):String.format(Locale.ROOT,"%.2f",v.getValue());
    }
    private void drawScrollBar(){
        float content=rows().size()*42,visible=view().height;
        if(content<=visible)return;
        float thumb=Math.max(12,visible*visible/content);
        UIUtils.drawDecorationRound(view().x+view().width-2,view().y+(visible-thumb)*scroll/(content-visible),2,thumb,1,new Color(153,168,192,160));
    }
    private void drawPreview(Theme theme){
        float px=view().x,py=y+h-86,pw=view().width;
        preview.panel(px,py,pw,34,5,theme,true,true,false,5,.45f,.35f,()->{
            UIUtils.drawDecorationRound(px+1,py+1,pw-2,32,4,new Color(11,15,22,145));
            me.youngjibbit.utils.render.TextEffects.direct(theme,()->text(fit("Neutron  /  HitDelayFix  /  FastPlace",pw-20),px+10,py+5,-1));
            text(fit("Aa Bb  0123456789  Â·  Session Info  Â·  "+selectedFamily(),pw-20),px+10,py+19,0xffc5cfdf);
        });
    }
    private String selectedFamily(){ClientSettings c=ClientSettings.current();return c==null?"Original":c.fontFamily.getValue();}
    private int popupRows(){return popupRows(popupValue);}
    private int popupRows(ComboValue value){
        return value==null?0:Math.min(value.getOptions().length,
                Math.max(2,Math.min(7,(int)(h-94)/22)));
    }
    protected ClipRect popupBounds(){
        int index=rows().indexOf(popupValue);
        if(index<0)return new ClipRect(0,0,0,0);
        ClipRect field=controlBounds(index);float height=popupRows()*22+8;
        float top=field.y+field.height+3;
        if(top+height>y+h-33)top=Math.max(y+48,field.y-height-3);
        return new ClipRect(field.x,top,field.width,height);
    }

    private static int fadeInk(int color,float alpha){
        float opacity=Float.isFinite(alpha)?Math.max(0,Math.min(1,alpha)):0;
        return (Math.round(((color>>>24)&255)*opacity)<<24)|(color&0x00ffffff);
    }
    private void drawPopup(int mx,int my,Theme theme,float dt,ClientSettings settings){
        boolean open=popupValue!=null;
        ComboValue shown=open?popupValue:closingPopupValue;
        if(shown==null)return;
        float progress=presentation.popup(dt,settings);
        if(!open&&progress<=.003f){
            closingPopupValue=null;closingPopupBounds=null;
            return;
        }
        ClipRect p=open?popupBounds():closingPopupBounds;
        if(p==null||p.width<=0||p.height<=0)return;
        float opacity=open?Math.max(.72f,progress):progress;
        float visibleHeight=p.height*(open?1:progress);
        // The full logical popup is ready immediately; only its ink, accent and
        // outgoing visual are animated. Its fixed hit rectangles never move.
        UIUtils.drawDecorationRound(p.x-1,p.y-1,p.width+2,Math.max(1,visibleHeight+2),5,
                new Color(69,83,103,Math.round(220*opacity)));
        UIUtils.drawDecorationRound(p.x,p.y,p.width,Math.max(1,visibleHeight),4,
                new Color(24,31,43,Math.round(246*opacity)));
        int count=popupRows(shown),offset=open?popupOffset:closingPopupOffset;
        int choice=open?popupChoice:closingPopupChoice;
        float marker=presentation.cursor(p.y+4+(choice-offset)*22+5,dt,settings);
        try(GuiClip clip=GuiClip.push(new ClipRect(p.x,p.y,p.width,Math.max(1,visibleHeight)))){
            if(count>0&&choice>=offset&&choice<offset+count)
                UIUtils.drawDecorationRound(p.x+3,marker,2,12,1,
                        new Color(fadeInk(theme.getPrimaryGradientRGB(),opacity),true));
            String[] options=shown.getOptions();
            for(int i=0;i<count;i++){
                int option=i+offset;if(option>=options.length)break;
                float top=p.y+4+i*22;
                boolean highlighted=option==choice||(open&&mx>=p.x&&mx<=p.x+p.width
                        &&my>=top&&my<top+22);
                int ink=highlighted?theme.getPrimaryGradientRGB():0xffdce5f2;
                text(fit(options[option],p.width-20),p.x+10,top+7,fadeInk(ink,opacity));
            }
            int total=options.length;
            if(total>count&&count>0){
                float track=p.height-8,thumb=track*count/total;
                UIUtils.drawDecorationRound(p.x+p.width-3,
                        p.y+4+(track-thumb)*offset/(total-count),1,thumb,.5f,
                        new Color(158,173,196,Math.round(180*opacity)));
            }
        }
    }
    private void beginPopup(ComboValue value){
        boolean reopening=closingPopupValue==value;
        if(!reopening)presentation.resetPopup();
        popupValue=value;popupChoice=value.getSelectedIndex();
        int maxOffset=Math.max(0,value.getOptions().length-popupRows());
        int desired=Math.max(0,popupChoice-popupRows()+1);
        popupOffset=reopening?Math.max(0,Math.min(maxOffset,closingPopupOffset)):desired;
        if(popupChoice<popupOffset||popupChoice>=popupOffset+popupRows())popupOffset=desired;
        ClipRect bounds=popupBounds();
        if(!reopening)presentation.resetCursor(bounds.y+4+(popupChoice-popupOffset)*22+5);
        closingPopupValue=null;closingPopupBounds=null;
        presentation.popup(true);
        editing=drag=-1;editValue=dragValue=null;
    }
    /** Drop input ownership at once; keep only a short, non-interactive exit visual. */
    private void dismissPopup(boolean animate){
        if(popupValue!=null&&animate){
            closingPopupBounds=popupBounds();
            closingPopupValue=popupValue;
            closingPopupOffset=popupOffset;
            closingPopupChoice=popupChoice;
            presentation.popup(false);
        }else if(!animate){
            closingPopupValue=null;closingPopupBounds=null;
            presentation.resetPopup();
        }
        popupValue=null;
    }
    private void selectGroup(int next){
        if(!commitInput())return;
        dismissPopup(false);
        group=Math.floorMod(next,Math.max(1,names().size()));scroll=targetScroll=0;focus=0;
        drag=editing=-1;editValue=dragValue=null;status="";layout();
    }
    private void updateSlider(int row,int mx){Value<?> value=rows().get(row);if(!(value instanceof SliderValue))return;SliderValue v=(SliderValue)value;ClipRect c=controlBounds(row);double f=Math.max(0,Math.min(1,(mx-c.x)/c.width));v.setValue(v.getMin()+f*(v.getMax()-v.getMin()));}
    private void activateValue(int index,int step){Value<?> value=rows().get(index);if(value instanceof CheckBoxValue){CheckBoxValue v=(CheckBoxValue)value;v.setValue(!v.getValue());}else if(value instanceof ComboValue){ComboValue v=(ComboValue)value;v.setSelectedIndex((v.getSelectedIndex()+step+v.getOptions().length)%v.getOptions().length);}else beginInput(index);}
    private void beginInput(int index){if(!(rows().get(index) instanceof SliderValue))return;editing=index;editValue=rows().get(index);drag=-1;dragValue=null;dismissPopup(false);input=String.valueOf(rows().get(index).getValue());replace=true;status="";}
    private boolean commitInput(){if(editing<0)return true;if(editing>=rows().size()){editing=-1;return true;}if(!(rows().get(editing) instanceof SliderValue)){editing=-1;status="Setting changed; edit cancelled";return true;}SliderValue v=(SliderValue)rows().get(editing);try{double number=Double.parseDouble(input);if(!Double.isFinite(number)||number<v.getMin()||number>v.getMax())throw new IllegalArgumentException();v.setValue(number);editing=-1;editValue=null;status="";return true;}catch(IllegalArgumentException e){status="Enter a finite value between "+v.getMin()+" and "+v.getMax();return false;}}
    protected void activate(int id){
        if(!commitInput())return;drag=-1;dragValue=null;dismissPopup(false);
        if(id==0||id==1){selectGroup(group+(id==0?-1:1));}
        else if(id==2){List<String> names=names();if(!names.isEmpty())for(Value<?> value:groups().get(names.get(group)))value.reset();status="Group defaults restored";}
        else if(id==3){if(!names().isEmpty()&&names().get(group).equals("HUD behavior")){HUD.resetPositions();status="HUD positions restored";}else{NotificationManager.show(new Notification(NotificationType.INFO,"Preview notification","Synthetic preview of the selected notification design.",0));status="Preview queued";}}
        else if(id==4)open(new GuiOptions(this,mc.gameSettings));else if(id==5)open(parent);
        layout();
    }
    protected void open(GuiScreen screen){mc.displayGuiScreen(screen);}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button!=0&&button!=1)return;layout();
        if(popupValue!=null){
            ClipRect p=popupBounds();
            if(button==0&&p.contains(mx,my)){
                int option=(int)((my-p.y-4)/22)+popupOffset;
                if(my>=p.y+4&&option>=popupOffset&&option<Math.min(popupOffset+popupRows(),popupValue.getOptions().length))popupValue.setSelectedIndex(option);
            }
            dismissPopup(true);return;
        }
        if(rail()&&button==0)for(int i=0;i<names().size();i++)if(categoryBounds(i).contains(mx,my)){selectGroup(i);return;}
        for(int id=0;id<6;id++)if(actionBounds(id).contains(mx,my)){if(button==0){pressed=id;activate(id);}return;}
        if(!view().contains(mx,my)||!commitInput())return;
        List<Value<?>> values=rows();
        for(int i=0;i<values.size();i++)if(rowBounds(i).intersect(view()).contains(mx,my)){
            focus=i;ensureFocus();Value<?> value=values.get(i);
            if(value instanceof SliderValue){
                if(button==1)beginInput(i);
                else if(controlBounds(i).contains(mx,my)){drag=i;dragValue=value;updateSlider(i,mx);}
            }else if(button==0){
                if(value instanceof ComboValue)beginPopup((ComboValue)value);else activateValue(i,1);
            }
            return;
        }
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(button!=0||drag<0||dragValue==null)return;
        List<Value<?>> values=rows();
        if(drag>=values.size()||values.get(drag)!=dragValue){
            drag=-1;dragValue=null;return;
        }
        // Pointer capture owns writes. Drawing and spring updates never persist a Value.
        updateSlider(drag,mx);
    }
    @Override protected void mouseReleased(int mx,int my,int button){
        if(button==0){drag=-1;dragValue=null;pressed=-1;}
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=Mouse.getDWheel();if(wheel==0)return;
        if(popupValue!=null){
            popupOffset=Math.max(0,Math.min(popupValue.getOptions().length-popupRows(),popupOffset-(int)Math.signum(wheel)));
        }else{targetScroll-=Math.signum(wheel)*42;layout();}
    }
    private void ensureFocus(){if(focus>=rows().size())return;ClipRect r=rowBounds(focus),v=view();if(r.y<v.y)scroll=targetScroll=Math.max(0,scroll+r.y-v.y);else if(r.y+r.height>v.y+v.height)scroll=targetScroll=scroll+r.y+r.height-v.y-v.height;layout();}
    protected boolean shift(){return Keyboard.isCreated()&&isShiftKeyDown();}protected boolean ctrl(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException{
        pressed=-1; // presentation only; the existing keyboard and mouse-down actions retain ownership
        if(popupValue!=null){
            if(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_TAB){dismissPopup(true);return;}
            if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN){
                popupChoice=Math.floorMod(popupChoice+(key==Keyboard.KEY_UP?-1:1),popupValue.getOptions().length);
                popupOffset=Math.max(0,Math.min(popupChoice,Math.max(popupOffset,popupChoice-popupRows()+1)));
            }else if(key==Keyboard.KEY_HOME||key==Keyboard.KEY_END){
                popupChoice=key==Keyboard.KEY_HOME?0:popupValue.getOptions().length-1;
                popupOffset=Math.max(0,popupChoice-popupRows()+1);
            }else if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){
                popupValue.setSelectedIndex(popupChoice);dismissPopup(true);
            }
            return;
        }
        if(editing>=0){if(key==Keyboard.KEY_ESCAPE){editing=-1;editValue=null;status="";return;}if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){commitInput();return;}if(key==Keyboard.KEY_A&&ctrl()){replace=true;return;}if(key==Keyboard.KEY_BACK){input=replace?"":input.substring(0,Math.max(0,input.length()-1));replace=false;}else if(c>=32&&c!=127&&(replace||input.length()<32)){input=replace?String.valueOf(c):input+c;replace=false;}return;}
        if(key==Keyboard.KEY_ESCAPE){activate(5);return;}
        if(key==Keyboard.KEY_R&&ctrl()){ClientSettings settings=ClientSettings.current();if(settings!=null)for(Value<?> v:settings.getValues())v.reset();HUD hud=HUD.current();if(hud!=null)for(Value<?> v:hud.getValues())v.reset();status="Client settings restored";layout();return;}
        if(key==Keyboard.KEY_TAB||key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN){int size=rows().size()+6;if(size>0){int step=key==Keyboard.KEY_UP||key==Keyboard.KEY_TAB&&shift()?-1:1;focus=(focus+step+size)%size;ensureFocus();}return;}
        if(key==Keyboard.KEY_PRIOR||key==Keyboard.KEY_NEXT){targetScroll+=(key==Keyboard.KEY_PRIOR?-1:1)*view().height;layout();return;}
        if(key==Keyboard.KEY_HOME||key==Keyboard.KEY_END){focus=key==Keyboard.KEY_HOME?0:Math.max(0,rows().size()-1);ensureFocus();return;}
        if(key==Keyboard.KEY_F6){activate(shift()?0:1);return;}
        if(key==Keyboard.KEY_F7){HUD hud=HUD.current();if(hud!=null){hud.toggle();status="Chat alignment guides: "+(hud.isToggled()?"on":"off");}return;}
        if(focus>=rows().size()){if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)activate(focus-rows().size());return;}
        if(rows().isEmpty())return;
        if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT){Value<?> value=rows().get(focus);int direction=key==Keyboard.KEY_LEFT?-1:1;if(value instanceof SliderValue){SliderValue v=(SliderValue)value;v.setValue(v.getValue()+direction*(v.isInteger()?1:(v.getMax()-v.getMin())/100));}else activateValue(focus,direction);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){Value<?> value=rows().get(focus);if(value instanceof ComboValue)beginPopup((ComboValue)value);else activateValue(focus,1);}
        if(key==Keyboard.KEY_F6)activate(1);
    }
    @Override public void onGuiClosed(){resetMotion();editing=drag=-1;editValue=dragValue=null;popupValue=null;valueMotion.clear();categoryMotion.clear();surface.close();preview.close();if(Keyboard.isCreated())Keyboard.enableRepeatEvents(previousRepeat);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
