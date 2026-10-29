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
    public SettingsMenu(){this(null);}
    public SettingsMenu(GuiScreen parent){this.parent=parent;for(int i=0;i<actionMotion.length;i++)actionMotion[i]=new me.youngjibbit.gui.UiSpring();}
    private void resetMotion(){pressed=-1;for(me.youngjibbit.gui.UiSpring state:actionMotion)state.reset();lastFrame=System.nanoTime();}
    @Override public void initGui(){resetMotion();if(Keyboard.isCreated()){previousRepeat=Keyboard.areRepeatEventsEnabled();Keyboard.enableRepeatEvents(true);}layout();}
    private Map<String,List<Value<?>>> groups(){
        ClientSettings c=ClientSettings.current();Map<String,List<Value<?>>> g=c==null?new LinkedHashMap<>():new LinkedHashMap<>(c.groups());
        HUD hud=HUD.current();if(hud!=null){List<Value<?>> values=new ArrayList<>(hud.getValues());g.put("HUD behavior",values);}return g;
    }
    private List<String> names(){return new ArrayList<>(groups().keySet());}
    private List<Value<?>> rows(){List<String> names=names();if(names.isEmpty())return Collections.emptyList();group=Math.max(0,Math.min(group,names.size()-1));List<Value<?>> values=new ArrayList<>();for(Value<?> v:groups().get(names.get(group)))if(v.isVisible())values.add(v);return values;}
    private void layout(){w=Math.min(620,Math.max(1,width-16));h=Math.min(430,Math.max(1,height-12));x=(width-w)/2;y=(height-h)/2;int size=rows().size();focus=Math.max(0,Math.min(focus,Math.max(0,size+5)));float max=Math.max(0,size*34-view().height);targetScroll=Math.max(0,Math.min(targetScroll,max));scroll=Math.max(0,Math.min(scroll,max));}
    protected ClipRect view(){return new ClipRect(x+10,y+94,w-20,Math.max(1,h-190));}
    protected ClipRect rowBounds(int row){ClipRect v=view();return new ClipRect(v.x,v.y+row*34-scroll,v.width,30);}
    protected ClipRect controlBounds(int row){ClipRect r=rowBounds(row);float width=Math.min(174,w*.42f);return new ClipRect(r.x+r.width-width-5,r.y+4,width,22);}
    protected ClipRect actionBounds(int action){
        if(action==0)return new ClipRect(x+10,y+39,25,22);if(action==1)return new ClipRect(x+w-35,y+39,25,22);
        float gap=5,width=(w-25)/4;return new ClipRect(x+5+(action-2)*(width+gap),y+h-28,width,21);
    }
    private TTFFontRenderer font(){return Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18");}
    private void text(String value,float x,float y,int color){font().drawString(value,x,y,color);}
    private String fit(String text,float width){if(font().getWidth(text)<=width)return text;while(text.length()>0&&font().getWidth(text+"...")>width)text=text.substring(0,text.length()-1);return text.isEmpty()?"":text+"...";}
    private void button(int id,String title,int mx,int my){ClipRect b=actionBounds(id);me.youngjibbit.gui.UiSpring state=actionMotion[id];float accent=state.pressed()?1:Math.max(Math.max(0,Math.min(1,state.amount())),state.focused()?.75f:.15f);GuiStyle.panel(b.x,b.y,b.width,b.height,4,accent,new Color(24,27,35));GuiStyle.feedback(b,state);text(fit(title,b.width-10),b.x+5,b.y+6,0xffe8edf6);}
    protected void advanceMotion(float dt,int mx,int my){ClientSettings c=ClientSettings.current();int size=rows().size();for(int i=0;i<6;i++)actionMotion[i].step(dt,actionBounds(i).contains(mx,my),focus==size+i,pressed==i,true,c);}
    private String description(){String name=names().isEmpty()?"":names().get(group);switch(name){
        case "Appearance":return "Clear: tint only. Gradient: theme fill. Blur: soft scene. Glass: refracted scene. Border/Glow are independent.";
        case "Blur / Liquid Glass":return "Gaussian/Kawase: scene blur. Regular glass: frosted/adaptive. Clear glass: transparent lens.";
        case "Glow":return "Global off wins. Local HUD switches and explicit strengths remain local overrides.";
        case "Gradients / Motion":return "Theme colors in shared screen coordinates. Separate channels when sync is off.";
        case "HUD behavior":return "Chat dragging only. F7 / HUD module toggle: guides. Grid 0 means free placement.";
        default:return "Notification design overrides surface style. No input capture during gameplay.";
    }}
    @Override public void drawScreen(int mx,int my,float partialTicks){
        UiEffects.beginFrame();layout();long now=System.nanoTime();float dt=Math.max(0,Math.min(.1f,(now-lastFrame)/1e9f));lastFrame=now;scroll+=(targetScroll-scroll)*UiEffects.response(dt,18);
        if(drag>=0&&drag<rows().size())updateSlider(drag,mx);
        advanceMotion(dt,mx,my);
        try(RenderState frameState=new RenderState()){frameState.screenSpace();
        drawDefaultBackground();Theme theme=Theme.getDefaultTheme();
        surface.panel(x,y,w,h,7,theme,true,true,true,5,.9f,.8f,()->{
            text("Client settings",x+12,y+12,0xfff0f3fa);text("Live / saved with your config",x+Math.max(150,w-200),y+14,0xff9ca9bd);
            button(0,"<",mx,my);button(1,">",mx,my);List<String> names=names();text(names.isEmpty()?"Settings unavailable":names.get(group),x+44,y+46,theme.getTextColor().getRGB());
            text(fit(description(),w-24),x+12,y+72,0xffa4adbc);
            try(GuiClip clip=GuiClip.push(view())){
                List<Value<?>> values=rows();
                for(int i=0;i<values.size();i++){
                    ClipRect row=rowBounds(i),control=controlBounds(i);if(!view().intersect(row).isEmpty()){
                        Value<?> value=values.get(i);boolean hover=row.contains(mx,my);
                        if(i==focus||hover)GuiStyle.panel(row.x,row.y,row.width,row.height,4,i==focus?.7f:.2f,new Color(22,25,32));
                        text(fit(UiEffects.settingLabel(value),control.x-row.x-9),row.x+5,row.y+11,0xffdce3f0);
                        GuiStyle.panel(control.x,control.y,control.width,control.height,4,i==focus?.7f:.15f,new Color(16,19,25));
                        String label=value instanceof CheckBoxValue?((CheckBoxValue)value).getValue()?"On":"Off":value instanceof SliderValue?String.format(Locale.ROOT,"%.2f",((SliderValue)value).getValue()):UiEffects.settingOption(value,String.valueOf(value.getValue()));
                        try(GuiClip fieldClip=GuiClip.push(new ClipRect(control.x+4,control.y+2,control.width-8,control.height-4))){String shown=editing==i?input+"_":fit(label,control.width-12);if(editing==i)while(shown.length()>1&&font().getWidth(shown)>control.width-12)shown=shown.substring(1);text(shown,control.x+6,control.y+6,0xfff1f4fb);}
                        if(value instanceof SliderValue){SliderValue v=(SliderValue)value;float f=(float)((v.getValue()-v.getMin())/(v.getMax()-v.getMin()));UIUtils.drawRound(control.x+2,control.y+control.height-3,(control.width-4)*Math.max(.001f,f),1.5f,.75f,theme.getPrimaryGradientColor());}
                    }
                }
            }
            drawPreview(theme);button(2,"Reset group",mx,my);button(3,names.size()>group&&names.get(group).equals("HUD behavior")?"Reset positions":"Preview toast",mx,my);button(4,"Game options",mx,my);button(5,"Done",mx,my);
            text(fit(status.isEmpty()?"Tab / arrows, Enter or right click: exact value. Ctrl+R: reset all.":status,w-24),x+12,y+h-43,status.isEmpty()?0xff9ca8ba:0xffffbc99);
        });
        }
    }
    private void drawPreview(Theme theme){
        float px=x+12,py=y+h-90,pw=w-24;
        try(RenderState state=new RenderState()){
            glDisable(GL_TEXTURE_2D);glDisable(GL_BLEND);glBegin(GL_QUADS);
            for(int i=0;i<pw;i+=8)for(int j=0;j<32;j+=8){boolean light=((i+j)/8)%2==0;glColor3f(light?.4f:.1f,light?.45f:.18f,light?.5f:.25f);glVertex2f(px+i,py+j);glVertex2f(Math.min(px+pw,px+i+8),py+j);glVertex2f(Math.min(px+pw,px+i+8),py+j+8);glVertex2f(px+i,py+j+8);}glEnd();
        }
        UiEffects.independentScene(()->preview.panel(px+8,py+3,pw-16,26,5,theme,true,true,true,5,.45f,.8f,()->text("Live preview: surface / blur / border / glow",px+18,py+12,theme.getTextColor().getRGB())));
    }
    private void updateSlider(int row,int mx){Value<?> value=rows().get(row);if(!(value instanceof SliderValue))return;SliderValue v=(SliderValue)value;ClipRect c=controlBounds(row);double f=Math.max(0,Math.min(1,(mx-c.x)/c.width));v.setValue(v.getMin()+f*(v.getMax()-v.getMin()));}
    private void activateValue(int index,int step){Value<?> value=rows().get(index);if(value instanceof CheckBoxValue){CheckBoxValue v=(CheckBoxValue)value;v.setValue(!v.getValue());}else if(value instanceof ComboValue){ComboValue v=(ComboValue)value;v.setSelectedIndex((v.getSelectedIndex()+step+v.getOptions().length)%v.getOptions().length);}else beginInput(index);}
    private void beginInput(int index){if(!(rows().get(index) instanceof SliderValue))return;editing=index;drag=-1;input=String.valueOf(rows().get(index).getValue());replace=true;status="";}
    private boolean commitInput(){if(editing<0)return true;if(editing>=rows().size()){editing=-1;return true;}if(!(rows().get(editing) instanceof SliderValue)){editing=-1;status="Setting changed; edit cancelled";return true;}SliderValue v=(SliderValue)rows().get(editing);try{double number=Double.parseDouble(input);if(!Double.isFinite(number)||number<v.getMin()||number>v.getMax())throw new IllegalArgumentException();v.setValue(number);editing=-1;status="";return true;}catch(IllegalArgumentException e){status="Enter a finite value between "+v.getMin()+" and "+v.getMax();return false;}}
    protected void activate(int id){
        if(!commitInput())return;drag=-1;
        if(id==0||id==1){group=(group+(id==0?names().size()-1:1))%Math.max(1,names().size());scroll=targetScroll=0;focus=0;}
        else if(id==2){List<String> names=names();if(!names.isEmpty())for(Value<?> value:groups().get(names.get(group)))value.reset();status="Group defaults restored";}
        else if(id==3){if(!names().isEmpty()&&names().get(group).equals("HUD behavior")){HUD.resetPositions();status="HUD positions restored";}else{NotificationManager.show(new Notification(NotificationType.INFO,"Preview notification","Synthetic preview of the selected notification design.",0));status="Preview queued";}}
        else if(id==4)open(new GuiOptions(this,mc.gameSettings));else if(id==5)open(parent);
        layout();
    }
    protected void open(GuiScreen screen){mc.displayGuiScreen(screen);}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button!=0&&button!=1)return;layout();for(int id=0;id<6;id++)if(actionBounds(id).contains(mx,my)){if(button==0){pressed=id;activate(id);}return;}
        if(!view().contains(mx,my)||!commitInput())return;
        for(int i=0;i<rows().size();i++)if(rowBounds(i).intersect(view()).contains(mx,my)){focus=i;ensureFocus();if(rows().get(i) instanceof SliderValue){if(button==1)beginInput(i);else if(controlBounds(i).contains(mx,my)){drag=i;updateSlider(i,mx);}}else if(button==0)activateValue(i,1);return;}
    }
    @Override protected void mouseReleased(int mx,int my,int button){drag=-1;pressed=-1;}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getDWheel();if(wheel!=0){targetScroll-=Math.signum(wheel)*44;layout();}}
    private void ensureFocus(){if(focus>=rows().size())return;ClipRect r=rowBounds(focus),v=view();if(r.y<v.y)scroll=targetScroll=Math.max(0,scroll+r.y-v.y);else if(r.y+r.height>v.y+v.height)scroll=targetScroll=scroll+r.y+r.height-v.y-v.height;layout();}
    protected boolean shift(){return Keyboard.isCreated()&&isShiftKeyDown();}protected boolean ctrl(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException{
        pressed=-1; // presentation only; the existing keyboard and mouse-down actions retain ownership
        if(editing>=0){if(key==Keyboard.KEY_ESCAPE){editing=-1;status="";return;}if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){commitInput();return;}if(key==Keyboard.KEY_A&&ctrl()){replace=true;return;}if(key==Keyboard.KEY_BACK){input=replace?"":input.substring(0,Math.max(0,input.length()-1));replace=false;}else if(c>=32&&c!=127&&(replace||input.length()<32)){input=replace?String.valueOf(c):input+c;replace=false;}return;}
        if(key==Keyboard.KEY_ESCAPE){activate(5);return;}
        if(key==Keyboard.KEY_R&&ctrl()){ClientSettings settings=ClientSettings.current();if(settings!=null)for(Value<?> v:settings.getValues())v.reset();HUD hud=HUD.current();if(hud!=null)for(Value<?> v:hud.getValues())v.reset();status="Client settings restored";layout();return;}
        if(key==Keyboard.KEY_TAB||key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN){int size=rows().size()+6;if(size>0){int step=key==Keyboard.KEY_UP||key==Keyboard.KEY_TAB&&shift()?-1:1;focus=(focus+step+size)%size;ensureFocus();}return;}
        if(key==Keyboard.KEY_PRIOR||key==Keyboard.KEY_NEXT){targetScroll+=(key==Keyboard.KEY_PRIOR?-1:1)*view().height;layout();return;}
        if(key==Keyboard.KEY_HOME||key==Keyboard.KEY_END){focus=key==Keyboard.KEY_HOME?0:Math.max(0,rows().size()-1);ensureFocus();return;}
        if(key==Keyboard.KEY_F6){activate(1);return;}
        if(key==Keyboard.KEY_F7){HUD hud=HUD.current();if(hud!=null){hud.toggle();status="Chat alignment guides: "+(hud.isToggled()?"on":"off");}return;}
        if(focus>=rows().size()){if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)activate(focus-rows().size());return;}
        if(rows().isEmpty())return;
        if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT){Value<?> value=rows().get(focus);int direction=key==Keyboard.KEY_LEFT?-1:1;if(value instanceof SliderValue){SliderValue v=(SliderValue)value;v.setValue(v.getValue()+direction*(v.isInteger()?1:(v.getMax()-v.getMin())/100));}else activateValue(focus,direction);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)activateValue(focus,1);
        if(key==Keyboard.KEY_F6)activate(1);
    }
    @Override public void onGuiClosed(){resetMotion();editing=drag=-1;surface.close();preview.close();if(Keyboard.isCreated())Keyboard.enableRepeatEvents(previousRepeat);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
