package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.gui.UiSpring;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.Value;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.List;

/** Search and navigation over the live module/config/theme/account services. */
public class SideGuiScreen extends GuiScreen {
    private static final String[] TABS={"Modules","Configs","Themes","Client","Accounts"};
    private final GuiScreen parent;
    private final ArrayListSurface surface=new ArrayListSurface();
    private final UiSpring[] tabs=new UiSpring[6];
    private final List<Module> all=new ArrayList<>(),results=new ArrayList<>();
    private final Map<Module,String> searchIndex=new IdentityHashMap<>();
    private String query="";
    private Module selected;
    private ClickModule editor;
    private Component focus,pressed;
    private boolean searchFocus=true,listFocus,replace,repeatBefore,binding;
    private float x,y,w,h,scroll,targetScroll,detailScroll,targetDetail;
    private int navFocus=-1;
    private long lastFrame;

    public SideGuiScreen(GuiScreen parent){this.parent=parent;for(int i=0;i<tabs.length;i++)tabs[i]=new UiSpring();}
    @Override public void initGui(){
        all.clear();all.addAll(Pulsar.INSTANCE.getMm().getAllModules());all.sort(Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER));
        searchIndex.clear();for(Module m:all){StringBuilder terms=new StringBuilder(m.getName()).append(' ').append(m.getCategory()).append(' ').append(m.getDescription());for(Value<?> v:m.getValues())terms.append(' ').append(v.getName());searchIndex.put(m,terms.toString().toLowerCase(Locale.ROOT));}
        filter();lastFrame=System.nanoTime();
        if(Keyboard.isCreated()){repeatBefore=Keyboard.areRepeatEventsEnabled();Keyboard.enableRepeatEvents(true);}
        layout();
    }
    protected void query(String text){query=text;scroll=targetScroll=0;filter();}
    private void filter(){results.clear();String q=query.trim().toLowerCase(Locale.ROOT);for(Module m:all)if(searchIndex.get(m).contains(q))results.add(m);if(!results.contains(selected))select(results.isEmpty()?null:results.get(0));}
    private void select(Module module){if(editor!=null)editor.cancelInputs();selected=module;editor=module==null?null:new ClickModule(module,0,0,220,24);if(editor!=null)editor.setOpen(true);detailScroll=targetDetail=0;focus=pressed=null;binding=false;}
    private void layout(){
        w=Math.min(550,Math.max(1,width-20));h=Math.min(350,Math.max(1,height-20));
        x=(width-w)/2;y=(height-h)/2;
        float max=Math.max(0,results.size()*30-listBounds().height);targetScroll=clamp(targetScroll,0,max);scroll=clamp(scroll,0,max);
        if(editor!=null){editor.setWidth(detailBounds().width);editor.updateAnimation();float limit=Math.max(0,editor.getHeight()-detailBounds().height);targetDetail=clamp(targetDetail,0,limit);detailScroll=clamp(detailScroll,0,limit);editor.layout(detailBounds().x,detailBounds().y-detailScroll,detailBounds().width,detailBounds());}
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    protected ClipRect window(){return new ClipRect(x,y,w,h);}
    protected ClipRect tabBounds(int id){return id==5?new ClipRect(x+w-32,y+8,22,20):new ClipRect(x+10+id*(w-20)/5,y+38,(w-20)/5-4,22);}
    protected ClipRect searchBounds(){return new ClipRect(x+10,y+66,w-20,24);}
    protected ClipRect listBounds(){return new ClipRect(x+10,y+98,Math.max(72,(w-30)*.36f),Math.max(1,h-122));}
    protected ClipRect detailBounds(){ClipRect l=listBounds();return new ClipRect(l.x+l.width+8,l.y,w-l.width-28,l.height);}
    protected ClipRect resultBounds(int i){ClipRect l=listBounds();return new ClipRect(l.x,l.y+i*30-scroll,l.width-3,28);}
    private TTFFontRenderer font(){return Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18");}
    private void text(String s,float px,float py,float limit,int color){font().drawString(ClickControlStyle.fit(font(),s,limit),px,py,color);}
    @Override public void drawScreen(int mx,int my,float partial){
        UiEffects.beginFrame();
        long now=System.nanoTime();float dt=Math.min(.05f,Math.max(0,(now-lastFrame)/1e9f));lastFrame=now;
        scroll+=(targetScroll-scroll)*UiEffects.response(dt,22);detailScroll+=(targetDetail-detailScroll)*UiEffects.response(dt,22);layout();
        Theme theme=Theme.resolve(Theme.getDefaultTheme());int ink=theme.getTextColor().getRGB();
        try(RenderState frame=new RenderState()){
            frame.screenSpace();if(mc.theWorld==null)MainMenu.drawBackdrop(theme);else UIUtils.drawDecorationRound(0,0,width,height,0,new Color(5,7,12,80));
            surface.panel(x,y,w,h,7,theme,true,true,true,7,.88f,.7f,()->{
                try(GuiClip clip=GuiClip.push(window())){
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString("Neutron",x+12,y+12,ink);
                    if(w>380)text("Library",x+104,y+17,100,UiEffects.alpha(ink,.7f));
                    for (int i = 0; i < tabs.length; i++) {
                        ClipRect b = tabBounds(i);
                        tabs[i].stepForInput(dt,b.contains(mx,my),navFocus==i,false,true,ClientSettings.current());
                        b = GuiStyle.buttonBounds(b,tabs[i]);
                        UIUtils.drawRound(b.x,b.y,b.width,b.height,3,new Color(20,23,31));
                        GuiStyle.feedback(b,tabs[i]);
                        text(i==5?"x":TABS[i],b.x+6,b.y+6,b.width-12,GuiStyle.ink(ink,tabs[i]));
                    }
                    ClipRect search=searchBounds();GuiStyle.panel(search.x,search.y,search.width,search.height,4,searchFocus && showsKeyboardFocus()?.75f:.1f,new Color(15,18,25));
                    text(query.isEmpty()?"Search modules and settings":query+(searchFocus?"_":""),search.x+8,search.y+7,search.width-16,query.isEmpty()?UiEffects.alpha(ink,.65f):ink);
                    // A persistent text backing keeps the moving star from washing out labels.
                    ClipRect listArea=listBounds();int luminance=(ink>>16&255)*299+(ink>>8&255)*587+(ink&255)*114;
                    Color textBacking=luminance>=128000?new Color(7,10,16,185):new Color(240,242,246,220);
                    if(results.isEmpty())UIUtils.drawDecorationRound(listArea.x,listArea.y,listArea.width,listArea.height,4,textBacking);
                    try(GuiClip list=GuiClip.push(listBounds())){
                        if(results.isEmpty())text("No matches",listBounds().x+6,listBounds().y+9,listBounds().width-12,ink);
                        for(int i=0;i<results.size();i++){
                            ClipRect r=resultBounds(i);if(r.intersect(listBounds()).isEmpty())continue;Module m=results.get(i);
                            float active=m==selected?1:r.contains(mx,my)?.4f:.04f;
                            GuiStyle.panel(r.x,r.y,r.width,r.height,3,active,new Color(20,23,30));
                            UIUtils.drawDecorationRound(r.x+1,r.y+1,r.width-2,r.height-2,2,textBacking);
                            if(listFocus&&m==selected&&showsKeyboardFocus()){
                                UIUtils.drawDecorationRound(r.x+3,r.y+4,1,r.height-8,.5f,new Color(ink,true));
                                UIUtils.drawDecorationRound(r.x+r.width-4,r.y+4,1,r.height-8,.5f,new Color(ink,true));
                            }
                            text(m.getName(),r.x+7,r.y+4,r.width-21,ink);text(m.getCategory().toString(),r.x+7,r.y+16,r.width-21,UiEffects.alpha(ink,.66f));
                            if(m.isToggled())UIUtils.drawDecorationRound(r.x+r.width-9,r.y+11,3,6,1,theme.getPrimaryGradientColor());
                        }
                    }
                    if(editor!=null)try(GuiClip detail=GuiClip.push(detailBounds())){
                        editor.advanceMotion(dt,mx,my,focus==editor,pressed==editor,true);
                        for(Component.Sub<?> c:editor.presentationValues())c.advanceMotion(dt,mx,my,focus==c,pressed==c,!c.getClip().intersect(c.presentationBounds()).isEmpty());
                        editor.drawScreen(mx,my);
                    }
                    scrollbar(listBounds(),scroll,results.size()*30);
                    if(editor!=null)scrollbar(detailBounds(),detailScroll,editor.getHeight());
                    text(binding?"Press key Â· Esc cancels Â· Delete removes":"Tab: focus Â· K: bind Â· Ctrl+F: search",x+12,y+h-16,w-24,UiEffects.alpha(ink,.7f));
                }
            });
        }
    }
    private void scrollbar(ClipRect r,float amount,float content){if(content<=r.height)return;float size=Math.max(10,r.height*r.height/content);UIUtils.drawDecorationRound(r.x+r.width-2,r.y+amount/(content-r.height)*(r.height-size),2,size,1,UiEffects.color(Theme.getDefaultTheme().getTextColor().getRGB(),.5f));}
    protected void open(GuiScreen screen){if(editor!=null)editor.cancelInputs();mc.displayGuiScreen(screen);}
    protected void activate(int id){switch(id){case 0:searchFocus=true;navFocus=-1;break;case 1:open(new ClickLibraryScreen(this,false));break;case 2:open(new ClickLibraryScreen(this,true));break;case 3:open(new SettingsMenu(this));break;case 4:open(new GuiAltManager(null,this));break;case 5:open(parent);break;default:break;}}
    private Component componentAt(int mx,int my){if(editor==null||!detailBounds().contains(mx,my))return null;for(Component.Sub<?> c:editor.focusableValues())if(c.getClip().contains(mx,my)&&new ClipRect(c.getX(),c.getY(),c.getWidth(),c.getHeight()).contains(mx,my))return c;return editor.isMouseOver(mx,my)?editor:null;}
    private void focus(Component next){if(focus!=next&&focus instanceof ClickSlider)((ClickSlider)focus).cancelInput();focus=next;searchFocus=listFocus=false;navFocus=-1;}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(binding)return;layout();
        if(button==0){for(int i=0;i<6;i++)if(tabBounds(i).contains(mx,my)){activate(i);return;}if(searchBounds().contains(mx,my)){focus(null);searchFocus=true;replace=false;return;}}
        if(listBounds().contains(mx,my)&&(button==0||button==2))for(int i=0;i<results.size();i++)if(resultBounds(i).contains(mx,my)){select(results.get(i));focus(null);listFocus=true;if(button==2)binding=true;return;}
        Component target=componentAt(mx,my);focus(target);pressed=button==0?target:null;
        if(button==2&&target==editor){binding=true;return;}if(target!=null)target.mouseClicked(mx,my,button);layout();
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){if(button==0&&pressed!=null)pressed.mouseDragged(mx,my);}
    @Override protected void mouseReleased(int mx,int my,int button){pressed=null;if(editor!=null)editor.mouseReleased();}
    protected void scrollAt(int mx,int my,int wheel){if(wheel==0||binding)return;if(editor!=null)editor.mouseReleased();if(listBounds().contains(mx,my))targetScroll-=Math.signum(wheel)*45;else if(detailBounds().contains(mx,my)){if(focus instanceof ClickSlider)((ClickSlider)focus).cancelInput();targetDetail-=Math.signum(wheel)*40;}layout();}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();scrollAt(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,Mouse.getEventDWheel());}
    protected boolean ctrl(){return Keyboard.isCreated()&&isCtrlKeyDown();}
    protected boolean shift(){return Keyboard.isCreated()&&isShiftKeyDown();}
    private void navigate(int direction){List<Component> values=new ArrayList<>();if(editor!=null){values.add(editor);values.addAll(editor.focusableValues());}int count=values.size()+8,index=searchFocus?6:listFocus?7:navFocus>=0?navFocus:values.indexOf(focus)+8;index=Math.floorMod(index+direction,count);focus(null);if(index<6)navFocus=index;else if(index==6)searchFocus=true;else if(index==7)listFocus=true;else{focus(values.get(index-8));ensureFocus();}}
    private void selectRelative(int direction){
        if(results.isEmpty())return;
        int next=Math.max(0,Math.min(results.size()-1,results.indexOf(selected)+direction));
        select(results.get(next));focus(null);listFocus=true;
        ClipRect row=resultBounds(next),view=listBounds();
        if(row.y<view.y)targetScroll=scroll=Math.max(0,scroll+row.y-view.y);
        else if(row.y+row.height>view.y+view.height)targetScroll=scroll=scroll+row.y+row.height-view.y-view.height;
        layout();
    }
    private void ensureFocus(){if(focus==null)return;ClipRect r=focus.presentationBounds(),v=detailBounds();if(r.y<v.y)targetDetail=detailScroll=Math.max(0,detailScroll+r.y-v.y);else if(r.y+r.height>v.y+v.height)targetDetail=detailScroll=detailScroll+r.y+r.height-v.y-v.height;layout();}
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(binding){if(key==Keyboard.KEY_ESCAPE)binding=false;else if(key!=Keyboard.KEY_NONE&&selected!=null){selected.setKey(key==Keyboard.KEY_DELETE||key==Keyboard.KEY_BACK?0:key);binding=false;}return;}
        if(focus instanceof ClickSlider&&((ClickSlider)focus).isEditing()){if(key==Keyboard.KEY_TAB){((ClickSlider)focus).cancelInput();navigate(shift()?-1:1);}else focus.keyTyped(c,key);return;}
        if(ctrl()&&key==Keyboard.KEY_F){focus(null);searchFocus=true;return;}
        if(key==Keyboard.KEY_TAB){navigate(shift()?-1:1);return;}
        if(searchFocus){
            if(key==Keyboard.KEY_ESCAPE){if(query.isEmpty())open(parent);else query("");return;}
            if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_DOWN){focus(null);listFocus=true;selectRelative(0);return;}
            if(ctrl()&&key==Keyboard.KEY_A){replace=true;return;}
            if(key==Keyboard.KEY_BACK){query(replace?"":query.substring(0,Math.max(0,query.length()-1)));replace=false;return;}
            if(c>=32&&c!=127&&(replace||query.length()<96)){query(replace?String.valueOf(c):query+c);replace=false;}return;
        }
        if(key==Keyboard.KEY_ESCAPE){if(focus instanceof ClickCombo&&((ClickCombo)focus).isOpen())((ClickCombo)focus).close();else open(parent);return;}
        if(listFocus){
            if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)selectRelative(key==Keyboard.KEY_UP?-1:1);
            else if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_RIGHT){focus(editor);ensureFocus();}
            else if(key==Keyboard.KEY_SPACE&&selected!=null)selected.toggle();
            else if(key==Keyboard.KEY_K&&selected!=null)binding=true;
            return;
        }
        if(navFocus>=0){if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)activate(navFocus);else if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT)navFocus=Math.floorMod(navFocus+(key==Keyboard.KEY_LEFT?-1:1),6);return;}
        if(key==Keyboard.KEY_K&&selected!=null&&focus==editor){binding=true;return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN){navigate(key==Keyboard.KEY_UP?-1:1);return;}
        if(focus!=null){focus.keyTyped(c,key);layout();ensureFocus();}
    }
    @Override public void onGuiClosed(){if(editor!=null)editor.cancelInputs();surface.close();focus=pressed=null;binding=listFocus=false;for(UiSpring t:tabs)t.reset();if(Keyboard.isCreated())Keyboard.enableRepeatEvents(repeatBefore);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
