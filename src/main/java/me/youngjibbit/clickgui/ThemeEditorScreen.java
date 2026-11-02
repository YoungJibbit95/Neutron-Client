package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Scrollable theme editor; previews are draft-only until Save & use. */
public class ThemeEditorScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ThemeCatalog catalog;
    private final ThemeDraft draft;
    private final ArrayListSurface rootSurface=new ArrayListSurface(),previewScene=new ArrayListSurface(),surface=new ArrayListSurface();
    private final TextEffects textEffects=new TextEffects();
    private final ThemeColorPicker picker=new ThemeColorPicker();
    private float ox,oy,w,h,scroll,targetScroll;
    private final float scale=1;
    private int focus=0,editingField=-1,drag=-1,pickerField=-1;
    private Color pickerOriginal;
    private String buffer="",originalName="",status="";
    private boolean replaceInput, deleteConfirm;
    private final me.youngjibbit.gui.UiSpring[] controlMotion=new me.youngjibbit.gui.UiSpring[25];
    private int visualPressed=-1;
    private long lastFrame=System.nanoTime();
    private static final int[] FIELDS={9,10,11,12,13,14,7,8,20,21,22,23,24};
    private static final String[] LABELS={"Name","First color","Second color","Background","Text","Accent","Direction","Animation","Speed","Glow strength","Glow radius","Outline strength","Outline width"};
    public ThemeEditorScreen(GuiScreen parent,ThemeCatalog catalog,Consumer<String> selection){
        this.parent=parent;this.catalog=catalog;draft=new ThemeDraft(catalog,selection);
        for(int i=0;i<controlMotion.length;i++)controlMotion[i]=new me.youngjibbit.gui.UiSpring();
    }
    public ThemeEditorScreen editTheme(Theme theme,boolean duplicate){
        draft.choose(theme);if(duplicate)draft.duplicate();return this;
    }
    private void resetMotion(){visualPressed=-1;for(me.youngjibbit.gui.UiSpring state:controlMotion)state.reset();lastFrame=System.nanoTime();}
    @Override public void initGui(){resetMotion();layout();super.initGui();}
    protected void advanceMotion(float dt,int mx,int my){
        for(int id:order())controlMotion[id].step(dt,pickerField<0&&controlVisible(id)&&actionBounds(id).contains(mx,my),id==focus,id==visualPressed,enabled(id),me.youngjibbit.module.impl.client.ClientSettings.current());
    }
    private void layout(){
        w=Math.min(620,Math.max(1,width-20));h=Math.min(440,Math.max(1,height-20));ox=(width-w)/2;oy=(height-h)/2;
        float max=Math.max(0,FIELDS.length*30-formBounds().height);targetScroll=Math.max(0,Math.min(max,targetScroll));scroll=Math.max(0,Math.min(max,scroll));layoutPicker();
    }
    private boolean wide(){return w>=540;}
    protected ClipRect formBounds(){return new ClipRect(ox+12,oy+78,wide()?328:w-24,Math.max(24,h-143));}
    protected ClipRect actionBounds(int id){
        if(id==0)return new ClipRect(ox+12,oy+42,24,22);
        if(id==1)return new ClipRect(ox+40,oy+42,24,22);
        if(id==2)return new ClipRect(ox+w-150,oy+42,138,22);
        if(id>=3&&id<=6){float bw=(w-42)/4;return new ClipRect(ox+12+(id-3)*(bw+6),oy+h-46,bw,24);}
        int row=-1;for(int i=0;i<FIELDS.length;i++)if(FIELDS[i]==id)row=i;
        if(row<0)throw new IllegalArgumentException("Unknown editor control");
        ClipRect form=formBounds();float label=Math.min(114,form.width*.42f);
        return new ClipRect(form.x+label,form.y+row*30-scroll,form.width-label-4,22);
    }
    protected void revealControl(int id){
        if(id<7)return;layout();ClipRect r=actionBounds(id),v=formBounds();
        if(r.y<v.y)scroll=targetScroll=Math.max(0,scroll+r.y-v.y);
        else if(r.y+r.height>v.y+v.height)scroll=targetScroll=scroll+r.y+r.height-v.y-v.height;
    }
    private boolean controlVisible(int id){return id<7||!formBounds().intersect(actionBounds(id)).isEmpty();}
    private TTFFontRenderer font(){return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");}
    private void text(String value,float x,float y,int color){font().drawString(value,x,y,color);}
    private String fit(String value,float width){return ClickControlStyle.fit(font(),value,width);}
    private boolean enabled(int id){
        if(id>=7)return draft.isEditable();if(id==6)return draft.canDelete()&&!catalog.isReadOnly();
        if(id==2)return !catalog.isReadOnly();
        return id!=3||!draft.isEditable()||!catalog.isReadOnly();
    }
    private void control(int id,String label){
        ClipRect r=actionBounds(id);boolean on=enabled(id);int ink=Theme.getDefaultTheme().getTextColor().getRGB();
        GuiStyle.panel(r.x,r.y,r.width,r.height,3,on&&id==focus?1:.12f,new Color(20,23,30));GuiStyle.feedback(r,controlMotion[id]);
        try(GuiClip ignored=GuiClip.push(new ClipRect(r.x+3,r.y+1,r.width-6,r.height-2))){
            if(editingField==id)while(label.length()>1&&font().getWidth(label)>r.width-12)label=label.substring(1);
            text(fit(label,r.width-12),r.x+6,r.y+6,UiEffects.alpha(ink,on?1:.42f));
        }
    }
    @Override public void drawScreen(int mx,int my,float partialTicks){
        layout();long now=System.nanoTime();float dt=Math.max(0,Math.min(.05f,(now-lastFrame)/1e9f));lastFrame=now;
        scroll+=(targetScroll-scroll)*UiEffects.response(dt,22);advanceMotion(dt,mx,my);
        Theme theme=Theme.getDefaultTheme();int ink=theme.getTextColor().getRGB();
        try(RenderState state=new RenderState()){
            state.screenSpace();UIUtils.drawDecorationRound(0,0,width,height,0,new Color(5,7,12,115));
            rootSurface.panel(ox,oy,w,h,6,theme,true,true,true,8,.92f,.8f,()->{
                try(GuiClip clip=GuiClip.push(new ClipRect(ox+2,oy+2,w-4,h-4))){
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString("Neutron themes",ox+12,oy+12,ink);
                    control(0,"<");control(1,">");control(2,"Duplicate theme");
                    if(w>420)text(fit(draft.title.getValue(),w-260),ox+76,oy+48,ink);
                    try(GuiClip form=GuiClip.push(formBounds())){
                        for(int i=0;i<FIELDS.length;i++){
                            int id=FIELDS[i];ClipRect r=actionBounds(id);if(!controlVisible(id))continue;
                            text(fit(LABELS[i],r.x-formBounds().x-8),formBounds().x,r.y+6,UiEffects.alpha(ink,.85f));
                            if(id==9)control(id,editingField==id?buffer+"_":draft.title.getValue());
                            else if(id>=10&&id<=14){
                                control(id,editingField==id?buffer+"_":rgba(colors()[id-10].getValue()));
                                UIUtils.drawDecorationRound(r.x+r.width-23,r.y+4,18,14,2,colors()[id-10].getValue());
                            } else if(id==7)control(id,draft.direction.getValue());
                            else if(id==8)control(id,draft.animated.getValue()?"Animated":"Static");
                            else slider(id,sliders()[id-20]);
                        }
                    }
                    if(FIELDS.length*30>formBounds().height){
                        ClipRect f=formBounds();float thumb=Math.max(10,f.height*f.height/(FIELDS.length*30));
                        UIUtils.drawDecorationRound(f.x+f.width-1,f.y+scroll/(FIELDS.length*30-f.height)*(f.height-thumb),1,thumb,0,UiEffects.color(ink,.65f));
                    }
                    if(wide())drawPreview(theme);
                    control(3,draft.isEditable()?"Save & use":"Use preset");control(4,"Cancel");control(5,"Reset draft");control(6,deleteConfirm?"Confirm delete":"Delete");
                    String message=!status.isEmpty()?status:catalog.isReadOnly()?"Read-only theme library":draft.isEditable()?"Swatch: color picker / Right click: exact number / Save applies":"Preset: Use or Duplicate to customize";
                    text(fit(message,w-24),ox+12,oy+h-16,!status.isEmpty()?0xffffb5a0:UiEffects.alpha(ink,.8f));
                    if(pickerField>=0)drawPicker(ink);
                }
            });
        }
    }
    private void drawPreview(Theme theme){
        float x=ox+356,y=oy+80,pw=w-368;
        text("Live preview",x,y,theme.getTextColor().getRGB());
        // Preview uses an independent scene to demonstrate alpha without sampling earlier controls.
        for(int row=0;row<14;row++)for(int col=0;col<(int)Math.ceil(pw/10);col++)
            UIUtils.drawDecorationRound(x+col*10,y+22+row*10,Math.min(10,pw-col*10),10,0,new Color((row+col)%2==0?0xff42515e:0xff25313e,true));
        UiEffects.independentScene(()->previewScene.withScene(theme,()->{
            try(UiEffects.GradientSpace gradient=UiEffects.gradientSpace(x,y+22,pw,140)){
                surface.panel(x+8,y+30,pw-16,122,5,theme,true,true,true,5,.8f,.8f,()->{
                    textEffects.draw(Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28"),"Neutron",x+18,y+41,
                            theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),2,theme.isAnimated(),theme.getAnimationSpeed(),true,theme.getGlowStrength(),theme.getGlowRadius(),false);
                    UIUtils.drawDecorationGradientVertical(x+9,y+73,pw-18,20,0,UiEffects.color(theme.getPrimaryGradientRGB(),.72f),UiEffects.color(theme.getSecondaryGradientRGB(),.72f));
                    text("Enabled module",x+18,y+79,theme.getTextColor().getRGB());
                    text("Module settings",x+18,y+107,theme.getTextColor().getRGB());
                    UIUtils.drawDecorationGradientVertical(x+18,y+130,pw-70,3,1,theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
                });
            }
        }));
        text("Draft preview",x,y+175,theme.getTextColor().getRGB());
        text("Cancel restores your theme.",x,y+192,UiEffects.alpha(theme.getTextColor().getRGB(),.75f));
    }
    private ClipRect pickerBox(){float pw=Math.min(270,w-28),ph=Math.min(232,h-18);return new ClipRect(ox+(w-pw)/2,oy+(h-ph)/2,pw,ph);}
    private ClipRect pickerDone(){ClipRect p=pickerBox();return new ClipRect(p.x+10,p.y+p.height-32,p.width-20,22);}
    private void layoutPicker(){ClipRect p=pickerBox();picker.layout(new ClipRect(p.x+12,p.y+30,p.width-24,Math.max(62,p.height-78)));}
    private void drawPicker(int ink){
        UIUtils.drawDecorationRound(ox,oy,w,h,5,new Color(4,6,10,205));ClipRect p=pickerBox();
        GuiStyle.panel(p.x,p.y,p.width,p.height,5,1,new Color(19,22,29));
        text(colors()[pickerField-10].getName()+" / HSV + alpha",p.x+12,p.y+10,ink);
        picker.draw();ClipRect done=pickerDone();GuiStyle.panel(done.x,done.y,done.width,done.height,3,.6f,new Color(25,29,37));
        text("Done / Esc restores color",done.x+7,done.y+6,ink);
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(button!=0)return;if(pickerField>=0)picker.move(mx,my);else if(drag>=0)updateSlider(drag,mx);
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
        scrollAt(mx,my,Mouse.getEventDWheel());
    }
    protected void scrollAt(int mx,int my,int wheel){
        if(pickerField>=0||wheel==0||!formBounds().contains(mx,my))return;
        drag=-1;editingField=-1;targetScroll-=Math.signum(wheel)*40;layout();
    }
    private ColorValue[] colors(){return new ColorValue[]{draft.first,draft.second,draft.background,draft.text,draft.accent};}
    private SliderValue[] sliders(){return new SliderValue[]{draft.speed,draft.glowStrength,draft.glowRadius,draft.outlineStrength,draft.outlineWidth};}
    private static String rgba(Color c){return String.format(Locale.ROOT,"%08X",Integer.rotateLeft(c.getRGB(),8));}
    private void slider(int id,SliderValue value){ClipRect r=actionBounds(id);control(id,editingField==id?buffer+"_":String.format(Locale.ROOT,"%.2f",value.getValue()));float f=(float)((value.getValue()-value.getMin())/(value.getMax()-value.getMin()));UIUtils.drawDecorationGradientVertical(r.x+3,r.y+r.height-3,(r.width-6)*Math.max(.001f,f),2,1,Theme.getDefaultTheme().getPrimaryGradientColor(),Theme.getDefaultTheme().getSecondaryGradientColor());}
    private void updateSlider(int id,int mx){ClipRect r=actionBounds(id);SliderValue v=sliders()[id-20];double t=Math.max(0,Math.min(1,(mx-r.x)/r.width));v.setValue(v.getMin()+t*(v.getMax()-v.getMin()));}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        visualPressed=-1;
        if(pickerField>=0){
            if(button==0&&pickerDone().contains(mx,my)){pickerField=-1;picker.release();return;}
            if(button==0)picker.click(mx,my);
            return;
        }
        if(button==0&&draft.isEditable())for(int id=10;id<=14;id++){
            ClipRect r=actionBounds(id);
            if(formBounds().contains(mx,my)&&new ClipRect(r.x+r.width-24,r.y,24,r.height).contains(mx,my)){
                if(commitField()){focus=id;pickerField=id;pickerOriginal=colors()[id-10].getValue();picker.setValue(colors()[id-10]);layoutPicker();}
                return;
            }
        }
        if(button==0)for(int id:order())if(controlVisible(id)&&(id<7||formBounds().contains(mx,my))&&actionBounds(id).contains(mx,my)&&enabled(id)){visualPressed=id;break;}
        if(button!=0&&button!=1)return;
        for(int id:order())if(controlVisible(id)&&(id<7||formBounds().contains(mx,my))&&actionBounds(id).contains(mx,my)&&enabled(id)){
            if(button==1){if(id>=20&&commitField()){focus=id;beginField(id);}return;}
            if(id==4||id==5||id==6){editingField=-1;focus=id;activate(id);return;}
            if(!commitField())return;focus=id;
            if(id==9||id>=10&&id<=14){beginField(id);return;}
            if(id>=20){drag=id;updateSlider(id,mx);return;}activate(id);return;
        }
    }
    private int[] order(){return new int[]{0,1,2,9,10,11,12,13,14,7,8,20,21,22,23,24,3,4,5,6};}
    private void beginField(int id){drag=-1;editingField=id;buffer=id>=20?Double.toString(sliders()[id-20].getValue()):id==9?draft.title.getValue():rgba(colors()[id-10].getValue());originalName=draft.title.getValue();replaceInput=true;}
    private boolean commitField(){if(editingField<0)return true;try{if(editingField>=20){SliderValue value=sliders()[editingField-20];double next=Double.parseDouble(buffer);if(!Double.isFinite(next)||next<value.getMin()||next>value.getMax())throw new IllegalArgumentException("Number must be within "+value.getMin()+" - "+value.getMax());value.setValue(next);}else if(editingField==9)draft.title.setValue(Theme.validName(buffer));else{if(!buffer.matches("[0-9a-fA-F]{8}"))throw new IllegalArgumentException("RGBA needs eight hex digits: RRGGBBAA");colors()[editingField-10].setColor(new Color(Integer.rotateRight((int)Long.parseLong(buffer,16),8),true));}editingField=-1;status="";return true;}catch(IllegalArgumentException ex){status=ex.getMessage();return false;}}
    protected void activate(int id)throws IOException{
        if(id==6&&!deleteConfirm){deleteConfirm=true;status="Delete this custom theme? Click Confirm delete or Cancel.";return;}
        if(id!=6)deleteConfirm=false;
        try{
            if(id==0||id==1){java.util.List<Theme> all=catalog.all();int index=0;for(int i=0;i<all.size();i++)if(all.get(i).name().equals(draft.reference()))index=i;draft.choose(all.get((index+(id==0?all.size()-1:1))%all.size()));}
            else if(id==2)draft.duplicate();else if(id==3){draft.save();me.youngjibbit.notifications.NotificationManager.success("Theme saved","Theme changes were saved and selected.");leave();}else if(id==4){draft.cancel();leave();}else if(id==5)draft.reset();else if(id==6){draft.delete();me.youngjibbit.notifications.NotificationManager.success("Theme deleted","The theme was removed successfully.");leave();}else if(id==7)draft.direction.setValue(draft.direction.getSelectedIndex()==0?"Vertical":"Horizontal");else if(id==8)draft.animated.setValue(!draft.animated.getValue());
            status="";
        }catch(IllegalArgumentException|IOException ex){status=ex.getMessage();System.err.println("[Theme] "+status);me.youngjibbit.notifications.NotificationManager.error("Theme action failed","The theme action did not complete. See the theme diagnostics.");}
    }
    protected void leave(){mc.displayGuiScreen(parent);}
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException{
        visualPressed=-1;
        if(pickerField>=0){
            if(key==Keyboard.KEY_ESCAPE){colors()[pickerField-10].setColor(pickerOriginal);pickerField=-1;picker.release();}
            else if(key==Keyboard.KEY_RETURN){pickerField=-1;picker.release();}
            else picker.key(key);
            return;
        }
        if(editingField>=0){
            if(key==Keyboard.KEY_ESCAPE){if(editingField==9)draft.title.setValue(originalName);editingField=-1;status="";return;}
            if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){commitField();return;}
            if(key==Keyboard.KEY_TAB){if(commitField())nextFocus(shiftDown()?-1:1);return;}
            if(key==Keyboard.KEY_A&&ctrlDown()){replaceInput=true;return;}
            if(key==Keyboard.KEY_BACK){buffer=replaceInput?"":buffer.substring(0,Math.max(0,buffer.length()-1));replaceInput=false;}
            else if(c>=32&&c!=127&&(replaceInput||buffer.length()<(editingField==9?48:editingField>=20?32:8))){buffer=replaceInput?String.valueOf(c):buffer+c;replaceInput=false;}
            if(editingField==9)draft.title.setValue(buffer);return;
        }
        if(key==Keyboard.KEY_ESCAPE){activate(4);return;}if(key==Keyboard.KEY_TAB){nextFocus(shiftDown()?-1:1);return;}
        if(focus>=20&&enabled(focus)&&(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT)){SliderValue value=sliders()[focus-20];value.setValue(value.getValue()+(value.getMax()-value.getMin())/100*(key==Keyboard.KEY_LEFT?-1:1));return;}
        if(focus>=20&&enabled(focus)&&(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)){beginField(focus);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){ClipRect r=actionBounds(focus);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
    }
    private void nextFocus(int step){int[] ids=order();int index=0;for(int i=0;i<ids.length;i++)if(ids[i]==focus)index=i;for(int i=0;i<ids.length;i++){index=(index+step+ids.length)%ids.length;if(enabled(ids[index])){focus=ids[index];revealControl(focus);return;}}}
    @Override protected void mouseReleased(int x,int y,int button){visualPressed=-1;drag=-1;picker.release();}
    @Override public void onGuiClosed(){resetMotion();drag=-1;editingField=-1;picker.release();draft.close();surface.close();rootSurface.close();previewScene.close();textEffects.close();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
