package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.gui.NeutronVisualTokens;
import me.youngjibbit.clickgui.utils.MenuSurface;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.theme.ThemeMode;
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
    private boolean replace,previousRepeat,ownsKeyboardRepeat;
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
            if(!ownsKeyboardRepeat){previousRepeat=Keyboard.areRepeatEventsEnabled();ownsKeyboardRepeat=true;}
            Keyboard.enableRepeatEvents(true);
        }
        layout();
        // Resize/reinit preserves the logical popup, but starts with an immediately
        // readable disclosure instead of reviving stale progress from an old viewport.
        if(popupValue!=null){
            focus=rows().indexOf(popupValue);
            ensureFocus();
            normalizePopup();
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
    private boolean rail(){return w>=560&&h>=330;}
    private float inset(){return rail()?ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())?176:154:14;}
    private void layout(){
        w=Math.min(two()?700:720,Math.max(1,width-16));h=Math.min(420,Math.max(1,height-12));x=(width-w)/2;y=(height-h)/2;
        List<Value<?>> visible=rows();
        focus=Math.max(0,Math.min(focus,Math.max(0,visible.size()+5)));
        if(editValue!=null){editing=visible.indexOf(editValue);if(editing<0)editValue=null;}
        if(dragValue!=null){drag=visible.indexOf(dragValue);if(drag<0)dragValue=null;}
        if(popupValue!=null&&!visible.contains(popupValue))dismissPopup(false);
        float max=Math.max(0,contentHeight(visible.size())-view().height);
        targetScroll=Math.max(0,Math.min(targetScroll,max));scroll=Math.max(0,Math.min(scroll,max));
    }
    private boolean two(){return ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());}
    private boolean modern(){return two()&&!creative();}
    private boolean creative(){return two()&&creativePresentation();}
    protected boolean creativePresentation(){return ClientSettings.creativeScreenStyle();}
    private boolean compactLegacy(){return !two()&&h<340;}
    private boolean legacyPreview(){return !two()&&!compactLegacy();}
    private float sectionY(){return y+(compactLegacy()?44:58);}
    protected ClipRect view(){
        if(two())return new ClipRect(x+inset(),y+94,w-inset()-14,Math.max(1,h-186));
        float top=compactLegacy()?78:104,bottom=legacyPreview()?108:62;
        return new ClipRect(x+inset(),y+top,w-inset()-14,Math.max(1,h-top-bottom));
    }
    private int columns(){return two()?creative()&&view().width>=500?2:1:view().width>=520&&!compactLegacy()?2:1;}
    private boolean stacked(){return columns()==2||!two()&&view().width<360;}
    private float rowPitch(){return stacked()?52:40;}
    private float contentHeight(int count){return ((count+columns()-1)/columns())*rowPitch();}
    protected ClipRect rowBounds(int row){
        ClipRect v=view();int cols=columns();float cell=(v.width-(cols-1)*8)/cols;
        return new ClipRect(v.x+(row%cols)*(cell+8),v.y+(row/cols)*rowPitch()-scroll,cell,rowPitch()-4);
    }
    protected ClipRect controlBounds(int row){
        ClipRect r=rowBounds(row);
        if(stacked())return new ClipRect(r.x+8,r.y+18,r.width-16,28);
        float cw=Math.min(180,r.width*.45f);return new ClipRect(r.x+r.width-cw-7,r.y+4,cw,30);
    }
    protected ClipRect sliderTrack(int row){
        ClipRect control=controlBounds(row);float left=Math.round(control.x+4),right=Math.round(control.x+control.width-8);
        return new ClipRect(left,control.y+(two()?25:22),Math.max(1,right-left),two()?3:2);
    }
    protected ClipRect categoryBounds(int index){
        if(two()&&rail()){
            float pitch=Math.min(32,(h-126)/Math.max(1,names().size()));
            return new ClipRect(x+15,y+68+index*pitch,148,pitch-4);
        }
        float pitch=Math.min(34,(h-134)/Math.max(1,names().size()));
        return new ClipRect(x+12,y+70+index*pitch,126,pitch-4);
    }
    protected ClipRect actionBounds(int action){
        if(two()){
            if(action==0)return new ClipRect(rail()?x+inset():x+10,y+52,25,22);
            if(action==1)return new ClipRect(x+w-39,y+52,25,22);
            float gap=5,bw=(w-25)/4;return new ClipRect(x+5+(action-2)*(bw+gap),y+h-28,bw,21);
        }
        if(action==0)return new ClipRect(x+inset(),sectionY()-1,26,24);
        if(action==1)return new ClipRect(x+w-40,sectionY()-1,26,24);
        float total=Math.min(472,w-28),gap=6,bw=(total-3*gap)/4;
        return new ClipRect(x+(w-total)/2+(action-2)*(bw+gap),y+h-44,bw,28);
    }
    private TTFFontRenderer font(){return Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18");}
    private void text(String value,float x,float y,int color){font().drawString(value,x,y,color);}
    private String fit(String text,float width){TTFFontRenderer f=font();if(f.getWidth(text)<=width)return text;while(!text.isEmpty()&&f.getWidth(text+"…")>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));return text.isEmpty()?"":text+"…";}
    private void button(int id,String title,int mx,int my){
        me.youngjibbit.gui.UiSpring state=actionMotion[id];
        ClipRect b=GuiStyle.buttonBounds(actionBounds(id),state);
        if(!two()){
            Theme theme=Theme.getDefaultTheme();
            MenuSurface.card(b.x,b.y,b.width,b.height,theme,id==5,state.amount());
            GuiStyle.feedback(b,state);
            String icon=id==0?"back":id==1?"chevron":id==2?"refresh":id==3?"palette":id==4?"settings":"check";
            if(id==1){glPushMatrix();try{glTranslatef(b.x+b.width/2,b.y+b.height/2,0);glRotatef(-90,0,0,1);LegacyUiStyle.icon(icon,-6,-6,12,LegacyUiStyle.INK);}finally{glPopMatrix();}}
            else LegacyUiStyle.icon(icon,b.x+8,b.y+(b.height-12)/2,12,LegacyUiStyle.INK);
            if(id>1){String label=id==2?"Reset":id==3?(names().size()>group&&names().get(group).equals("HUD behavior")?"Positions":"Preview"):id==4?"Options":"Done";text(fit(label,b.width-32),b.x+26,b.y+9,LegacyUiStyle.INK);}
            return;
        }
        MenuSurface.card(b.x,b.y,b.width,b.height,Theme.getDefaultTheme(),id==5,state.amount());
        GuiStyle.feedback(b,state);text(fit(title,b.width-10),b.x+5,b.y+6,GuiStyle.ink(0xffe4ebf6,state));
    }
    protected void advanceMotion(float dt,int mx,int my){ClientSettings c=ClientSettings.current();int size=rows().size();for(int i=0;i<6;i++)actionMotion[i].stepForInput(dt,actionBounds(i).contains(mx,my),focus==size+i,pressed==i,true,c);}
    private String description(){String name=names().isEmpty()?"":names().get(group);switch(name){
        case "Appearance":return "Choose the surface, opacity and border of your UI.";
        case "Blur / Liquid Glass":return "Tune scene softness, refraction, tint and edge light.";
        case "Glow":return "Choose Standard, Flame or Neon for the UI. Global off also disables text glow.";
        case "Gradients / Motion":return "Shared colors and motion, with optional separate channels.";
        case "HUD behavior":return "Arrange HUDs in chat. F7 toggles alignment guides.";
        case "Typography":return ClientSettings.current()!=null&&!ClientSettings.current().glow.getValue()?"Text glow is paused while Global Glow is off.":"UI font and text glow. Icons and explicit HUD fonts stay separate.";
        case "World effects":return "Flame and Neon also tune UI glow. Lines controls affect ESP contours only.";
        default:return "Choose how, where and for how long notifications appear.";}}
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
            if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);
            boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
            boolean creative=this.creative();
            surface.panel(x,y,w,h,two()?14:8,theme,true,true,!two(),
                    two()?4:6,two()?.94f:.76f,two()?.18f:.52f,()->{
                if(two()){
                    if(rail()) UIUtils.drawDecorationRound(x+5,y+58,Math.max(1,inset()-10),h-96,8,
                            new Color(17,23,33,182));
                    UIUtils.drawDecorationRound(x+17,y+51,
                            Math.max(1,w-34),1,.5f,NeutronVisualTokens.HAIRLINE);
                    if(creative&&UiEffects.motion()){
                        // Ambient decoration follows the motion preference.
                        float pulse=UiEffects.time()*1.2f;
                        float ax=x+w-30+(float)Math.sin(pulse)*7f;
                        float ay=y+20+(float)Math.cos(pulse*.8f)*4f;
                        UIUtils.drawRound(ax,ay,8,8,4,
                                NeutronVisualTokens.accent(theme,(int)(140+90*Math.abs((float)Math.sin(pulse*.4f)))));
                    }
                }else{
                    LiquidGlass.divider(x+14,y+(compactLegacy()?35:49),w-28,theme);
                    if(rail()){
                        UIUtils.drawDecorationRound(x+8,y+58,inset()-20,h-114,
                                LiquidGlass.radius(6),LiquidGlass.fill(theme,LiquidGlass.PANEL));
                        LiquidGlass.highlight(x+8,y+58,inset()-20,h-114,LiquidGlass.radius(6));
                        UIUtils.drawRoundOutline(x+8.5f,y+58.5f,inset()-21,h-115,
                                Math.max(0,LiquidGlass.radius(6)-.5f),.6f,new Color(0,0,0,1),
                                LiquidGlass.ink(theme,LiquidGlass.HAIRLINE));
                        LegacyUiStyle.rule(x+inset(),y+h-54,w-inset()-14,theme);
                    }
                    LegacyUiStyle.icon("settings",x+15,y+13,16,theme.getPrimaryGradientRGB());
                }
                TTFFontRenderer heading=Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 26");
                heading.drawString("Client settings",x+(two()?16:39),y+11,
                        two()?NeutronVisualTokens.INK:0xfff1f4fa);
                if(!compactLegacy())text(fit("Appearance, motion and type — changes apply live",w-48),
                        x+(two()?16:39),y+33,two()?NeutronVisualTokens.MUTED:LegacyUiStyle.MUTED);
                // The global screen exit animation stays owned by GuiTransitions.
                // The entry accent uses the same spring policy; text/input remain ready.
                if(entry.opacity>.01f)
                    LiquidGlass.accentLine(x+(two()?16:39),y+(two()?49:50),
                            (w-(two()?32:58))*entry.opacity,theme,1);
                List<String> sections=names();
                if(rail()){
                    float markerY=presentation.category(categoryBounds(group).y+4,dt,motionSettings);
                    UIUtils.drawDecorationGradientVertical(x+(two()?16:12),markerY,2,
                            categoryBounds(group).height-8,1,
                            LiquidGlass.accent(theme,230),
                            NeutronVisualTokens.secondary(theme,190));
                    for(int i=0;i<sections.size();i++){
                        ClipRect r=categoryBounds(i);boolean selected=i==group;
                        me.youngjibbit.gui.UiSpring motion=categoryMotion.computeIfAbsent(sections.get(i),k->new me.youngjibbit.gui.UiSpring());
                        motion.stepForInput(dt,popupValue==null&&r.contains(mx,my),false,false,true,motionSettings);
                        r=GuiStyle.buttonBounds(r,motion);
                        if(two()&&(selected||motion.amount()>.02f))
                            MenuSurface.card(r.x+3,r.y+1,r.width-6,r.height-2,theme,selected,motion.amount());
                        if(!two()){
                            if(selected||motion.amount()>.05f)MenuSurface.card(r.x+3,r.y+1,r.width-6,r.height-2,theme,selected,motion.amount());
                            LegacyUiStyle.icon(sectionIcon(sections.get(i)),r.x+10,r.y+(r.height-12)/2,12,selected?LegacyUiStyle.INK:LegacyUiStyle.FAINT);
                        }
                        text(fit(two()?sections.get(i):sectionLabel(sections.get(i)),r.width-(two()?14:35)),
                                r.x+(two()?14:29),r.y+(r.height-font().getLineHeight())/2,
                                GuiStyle.ink(two()?(selected?NeutronVisualTokens.INK:
                                                NeutronVisualTokens.MUTED):
                                                selected?0xfff1f4fa:0xffa4b0c3,motion));
                        if(creative&&motion.glow()>.03f){
                            UIUtils.drawDecorationRound(r.x-motion.lift()*2f, r.y-motion.lift()*2f,
                                    r.width+motion.lift()*4f, r.height+motion.lift()*4f,
                                    9, me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .05f*motion.glow()));
                        }
                    }
                }
                button(0,"<",mx,my);button(1,">",mx,my);
                text(fit(sections.isEmpty()?"Settings unavailable":sections.get(group),
                        w-inset()-78),x+inset()+34,two()?y+59:sectionY()+7,
                        two()?NeutronVisualTokens.INK:LegacyUiStyle.INK);
                if(!compactLegacy())text(fit(description(),view().width),view().x,two()?y+80:y+86,
                        two()?NeutronVisualTokens.MUTED:LegacyUiStyle.MUTED);
                try(GuiClip clip=GuiClip.push(view())){
                    List<Value<?>> values=rows();
                    for(int i=0;i<values.size();i++)if(!view().intersect(rowBounds(i)).isEmpty())drawValue(i,values.get(i),theme,dt,mx,my);
                }
                drawScrollBar();
                if(two()||legacyPreview())drawPreview(theme);
                button(2,"Reset group",mx,my);
                button(3,sections.size()>group&&sections.get(group).equals("HUD behavior")?"Reset positions":"Preview toast",mx,my);
                button(4,"Game options",mx,my);button(5,"Done",mx,my);
                String hint=status.isEmpty()?(two()?"F6: section   Tab: focus   Right click: exact value   Ctrl+R: reset all":"F6: section  ·  Tab: focus  ·  Right click: exact value"):status;
                text(fit(hint,w-28),x+14,y+h-(two()?43:11),status.isEmpty()?LegacyUiStyle.FAINT:0xffffbc99);
                drawPopup(mx,my,theme,dt,motionSettings);
            });
        }
    }

    private void drawValue(int index,Value<?> value,Theme theme,float dt,int mx,int my){
        ClipRect row=rowBounds(index),control=controlBounds(index);
        me.youngjibbit.gui.UiSpring motion=valueMotion.computeIfAbsent(value,k->new me.youngjibbit.gui.UiSpring());
        motion.stepForInput(dt,popupValue==null&&row.contains(mx,my),index==focus,drag==index,true,ClientSettings.current());
        boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        boolean creative=this.creative();
        if(two()){
            MenuSurface.card(row.x,row.y,row.width,row.height,theme,false,motion.amount());
            if(index==focus&&showsKeyboardFocus())
                UIUtils.drawDecorationRound(row.x+3,row.y+7,2,row.height-14,1,
                        NeutronVisualTokens.accent(theme,210));
            if(creative&&motion.glow()>.03f){
                UIUtils.drawDecorationRound(row.x-motion.lift()*2f, row.y-motion.lift()*2f,
                        row.width+motion.lift()*4f, row.height+motion.lift()*4f,
                        10, me.youngjibbit.utils.render.UiEffects.accent(theme.getSecondaryGradientRGB(), .05f*motion.glow()));
            }
        }
        if(!two())MenuSurface.card(row.x,row.y,row.width,row.height,theme,index==focus&&showsKeyboardFocus(),motion.amount());
        text(fit(UiEffects.settingLabel(value),stacked()?row.width-20:control.x-row.x-18),
                row.x+(two()?12:10),row.y+(stacked()?6:11),
                two()?GuiStyle.ink(NeutronVisualTokens.INK,motion):LegacyUiStyle.INK);
        GuiStyle.feedback(row,motion);
        if(value instanceof CheckBoxValue){
            boolean on=((CheckBoxValue)value).getValue();
            float amount=follow(value,on?1:0,dt),sx=control.x+control.width-38,sy=control.y+6;
            text(on?"On":"Off",sx-25,control.y+9,on?0xffe5ecf7:0xff9da9bb);
            LiquidGlass.plate(sx,sy,34,16,8,theme,on,0);
            if(amount>.006f)UIUtils.drawDecorationRound(sx+1,sy+1,
                    Math.max(1,32*Math.min(1,amount)),14,7,
                    LiquidGlass.accent(theme,Math.round(150+55*Math.min(1,amount))));
            UIUtils.drawDecorationRound(sx+3+18*Math.max(0,Math.min(1,amount)),sy+3,
                    10,10,5,new Color(251,247,252));
        }else if(value instanceof SliderValue){
            SliderValue slider=(SliderValue)value;
            String shown=editing==index?input+"_":number(slider);
            float shownWidth=Math.min(control.width-12,font().getWidth(shown));
            text(fit(shown,control.width-12),two()?control.x+3:control.x+control.width-6-shownWidth,control.y+(two()?2:1),two()?GuiStyle.ink(0xffe5edf9,motion):LegacyUiStyle.INK);
            float fraction=(float)((slider.getValue()-slider.getMin())/Math.max(.0001,slider.getMax()-slider.getMin()));
            float amount=follow(value,fraction,dt);ClipRect track=sliderTrack(index);
            UIUtils.drawDecorationRound(track.x,track.y,track.width,track.height,track.height/2,
                    LiquidGlass.fill(theme,188));
            UIUtils.drawRoundOutline(track.x,track.y,track.width,Math.max(1,track.height),
                    track.height/2,.5f,new Color(0,0,0,1),LiquidGlass.ink(theme,34));
            UIUtils.drawDecorationRound(track.x,track.y,Math.max(.01f,track.width*amount),
                    track.height,track.height/2,LiquidGlass.accent(theme,225));
            UIUtils.drawDecorationRound(track.x-3+track.width*amount,track.y-2,
                    6,6,3,new Color(0xffedf3ff,true));
        }else{
            MenuSurface.card(control.x,control.y+3,control.width,24,theme,popupValue==value,motion.amount());
            text(fit(UiEffects.settingOption(value,String.valueOf(value.getValue())),control.width-28),control.x+7,control.y+10,GuiStyle.ink(0xffe5edf9,motion));
            if(two())text(popupValue==value?"^":"v",control.x+control.width-16,control.y+9,0xffaebbd0);
            else {glPushMatrix();try{glTranslatef(control.x+control.width-14,control.y+15,0);if(popupValue==value)glRotatef(180,0,0,1);LegacyUiStyle.icon("chevron",-5,-5,10,LegacyUiStyle.MUTED);}finally{glPopMatrix();}}
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
        float content=contentHeight(rows().size()),visible=view().height;
        if(content<=visible)return;
        float thumb=Math.max(12,visible*visible/content);
        UIUtils.drawDecorationRound(view().x+view().width-2,
                view().y+(visible-thumb)*scroll/(content-visible),
                2,thumb,1,ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())?
                        (creative()?NeutronVisualTokens.accent(Theme.getDefaultTheme(),180):NeutronVisualTokens.accent(Theme.getDefaultTheme(),154)):
                        new Color(153,168,192,160));
    }
    private void drawPreview(Theme theme){
        float px=view().x,py=y+h-(two()?86:98),pw=view().width;
        boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        boolean creative=this.creative();
        preview.panel(px,py,pw,34,two()?8:5,theme,true,true,false,two()?5:7,
                two()?.92f:.56f,two()?.18f:.4f,()->{
            float radius=LiquidGlass.radius(two()?7:4,pw-2,32);
            UIUtils.drawRound(px+1,py+1,pw-2,32,radius,
                    two()?(creative?NeutronVisualTokens.alpha(NeutronVisualTokens.CARD.getRGB(),240):NeutronVisualTokens.CARD)
                            :LiquidGlass.fill(theme,LiquidGlass.CARD));
            LiquidGlass.highlight(px+1,py+1,pw-2,32,radius);
            LiquidGlass.divider(px+8,py+15,pw-16,theme);
            me.youngjibbit.utils.render.TextEffects.direct(theme,()->text(fit("Neutron  /  HitDelayFix  /  FastPlace",pw-20),px+10,py+5,-1));
            text(fit("Aa Bb  0123456789  ·  Session Info  ·  "+selectedFamily(),pw-20),px+10,py+19,0xffc5cfdf);
            if(creative&&UiEffects.motion()){
                float spark=UiEffects.time()*2f;
                float sx=px+pw-22+(float)Math.sin(spark)*6f;
                UIUtils.drawRound(sx,py+13,6,6,3,NeutronVisualTokens.accent(theme,(int)(160+80*Math.abs((float)Math.sin(spark*.5f)))));
            }
        });
    }
    private String sectionLabel(String name){
        if("Blur / Liquid Glass".equals(name))return "Glass & blur";
        if("Gradients / Motion".equals(name))return "Color & motion";
        if("HUD behavior".equals(name))return "HUD layout";
        return name;
    }
    private static String sectionIcon(String name){
        switch(name){
            case "Appearance":return "palette";
            case "Blur / Liquid Glass":return "eye";
            case "Glow":return "bolt";
            case "Gradients / Motion":return "refresh";
            case "Notifications":return "bell";
            case "Typography":return "rename";
            case "World effects":return "settings";
            case "HUD behavior":return "range";
            default:return "settings";
        }
    }
    private String selectedFamily(){ClientSettings c=ClientSettings.current();return c==null?"Original":c.fontFamily.getValue();}
    private ClipRect popupViewport(){
        float top=y+48,bottom=actionBounds(2).y-6;
        return new ClipRect(x+14,top,Math.max(1,w-28),Math.max(1,bottom-top));
    }
    private int popupRows(){return popupRows(popupValue);}
    private int popupRows(ComboValue value){
        return value==null?0:Math.min(value.getOptions().length,
                Math.max(1,Math.min(7,(int)((popupViewport().height-8)/22))));
    }
    private void normalizePopup(){
        if(popupValue==null)return;
        int total=popupValue.getOptions().length,visible=popupRows();
        popupChoice=Math.max(0,Math.min(popupChoice,total-1));
        popupOffset=Math.max(0,Math.min(popupOffset,Math.max(0,total-visible)));
        if(popupChoice<popupOffset)popupOffset=popupChoice;
        else if(popupChoice>=popupOffset+visible)popupOffset=popupChoice-visible+1;
    }
    protected ClipRect popupBounds(){
        int index=rows().indexOf(popupValue);
        if(index<0)return new ClipRect(0,0,0,0);
        ClipRect field=controlBounds(index),viewport=popupViewport();
        float height=Math.min(viewport.height,popupRows()*22+8),width=Math.min(viewport.width,field.width);
        float top=field.y+field.height+3;
        if(top+height>viewport.y+viewport.height)top=field.y-height-3;
        top=Math.max(viewport.y,Math.min(viewport.y+viewport.height-height,top));
        float left=Math.max(viewport.x,Math.min(viewport.x+viewport.width-width,field.x));
        return new ClipRect(left,top,width,height);
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
        boolean modern=ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());
        boolean creative=this.creative();
        float radius=LiquidGlass.radius(modern?8:6,p.width,Math.max(1,visibleHeight));
        UIUtils.drawDecorationRound(p.x,p.y,p.width,Math.max(1,visibleHeight),radius,
                LiquidGlass.fill(theme,Math.round(224*opacity)));
        UIUtils.drawGradientHorizontal(p.x+radius,p.y+1,
                Math.max(0,p.width-radius*2),1,.5f,
                new Color(255,250,255,Math.round(44*opacity)),new Color(255,250,255,0));
        LiquidGlass.border(p.x,p.y,p.width,Math.max(1,visibleHeight),radius,theme,true);
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
                if(!two()&&highlighted)UIUtils.drawDecorationRound(p.x+4,top+1,p.width-8,20,4,LegacyUiStyle.alpha(LegacyUiStyle.control(theme,true).getRGB(),opacity));
                int ink=two()&&highlighted?Theme.interpolateRGB(NeutronVisualTokens.INK,theme.getPrimaryGradientRGB(),.18f):LegacyUiStyle.INK;
                if(!two()&&option==shown.getSelectedIndex())LegacyUiStyle.icon("check",p.x+p.width-19,top+6,10,fadeInk(theme.getPrimaryGradientRGB(),opacity));
                text(fit(options[option],p.width-30),p.x+10,top+7,fadeInk(ink,opacity));
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
        normalizePopup();
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
    private void updateSlider(int row,int mx){Value<?> value=rows().get(row);if(!(value instanceof SliderValue))return;SliderValue v=(SliderValue)value;ClipRect track=sliderTrack(row);double f=Math.max(0,Math.min(1,(mx-track.x)/track.width));v.setValue(v.getMin()+f*(v.getMax()-v.getMin()));}
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
        }else{targetScroll-=Math.signum(wheel)*rowPitch();layout();}
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
    @Override public void onGuiClosed(){resetMotion();editing=drag=-1;editValue=dragValue=null;popupValue=null;valueMotion.clear();categoryMotion.clear();surface.close();preview.close();if(Keyboard.isCreated()&&ownsKeyboardRepeat)Keyboard.enableRepeatEvents(previousRepeat);ownsKeyboardRepeat=false;}
    @Override public boolean doesGuiPauseGame(){return false;}
}

