package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.gui.HudSpring;
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
    private int focus=0,editingField=-1,drag=-1,pickerField=-1;
    private Color pickerOriginal;
    private String buffer="",originalName="",status="";
    private boolean replaceInput, deleteConfirm, previousRepeat, ownsKeyboardRepeat;
    private int section;
    private final HudSpring sectionMarker=new HudSpring();
    private float sectionPosition;
    private final HudSpring[] sliderMotion=new HudSpring[5];
    private final float[] sliderProgress=new float[5];
    private final me.youngjibbit.gui.UiSpring[] controlMotion=new me.youngjibbit.gui.UiSpring[28];
    private int visualPressed=-1;
    private long lastFrame=System.nanoTime();
    private static final int[][] SECTIONS={{9,10,11,12,13,14},{7,8,20},{21,22,23,24}};
    private static final String[] SECTION_NAMES={"Palette","Motion","Edges & light"};
    public ThemeEditorScreen(GuiScreen parent,ThemeCatalog catalog,Consumer<String> selection){
        this.parent=parent;this.catalog=catalog;draft=new ThemeDraft(catalog,selection);
        for(int i=0;i<controlMotion.length;i++)controlMotion[i]=new me.youngjibbit.gui.UiSpring();
        for(int i=0;i<sliderMotion.length;i++)sliderMotion[i]=new HudSpring();
    }
    public ThemeEditorScreen editTheme(Theme theme,boolean duplicate){
        draft.choose(theme);if(duplicate)draft.duplicate();return this;
    }
    private void resetMotion(){
        visualPressed=-1;for(me.youngjibbit.gui.UiSpring state:controlMotion)state.reset();
        sectionMarker.reset();for(HudSpring motion:sliderMotion)motion.reset();lastFrame=System.nanoTime();
    }
    @Override public void initGui(){
        resetMotion();layout();
        if(!wide())releasePreviewResources();
        if(Keyboard.isCreated()){if(!ownsKeyboardRepeat){previousRepeat=Keyboard.areRepeatEventsEnabled();ownsKeyboardRepeat=true;}Keyboard.enableRepeatEvents(true);}
        super.initGui();
    }
    protected void advanceMotion(float dt,int mx,int my){
        me.youngjibbit.module.impl.client.ClientSettings settings=me.youngjibbit.module.impl.client.ClientSettings.current();
        for(int id:order())controlMotion[id].stepForInput(dt,pickerField<0&&controlVisible(id)&&actionBounds(id).contains(mx,my),id==focus,id==visualPressed,enabled(id),settings);
        sectionPosition=sectionMarker.step(actionBounds(25+section).x,dt,settings==null||settings.animations.getValue(),settings!=null&&settings.reducedMotion.getValue(),settings==null?"Balanced":settings.motionIntensity.getValue());
        SliderValue[] values=sliders();
        for(int i=0;i<values.length;i++){
            float target=(float)((values[i].getValue()-values[i].getMin())/(values[i].getMax()-values[i].getMin()));
            sliderProgress[i]=Math.max(0,Math.min(1,sliderMotion[i].step(target,dt,settings==null||settings.animations.getValue(),settings!=null&&settings.reducedMotion.getValue(),settings==null?"Balanced":settings.motionIntensity.getValue())));
        }
    }
    private boolean compact(){return h<330;}
    private float tabsY(){return oy+(compact()?72:84);}
    private float fieldPitch(){return 38;}
    private int[] fields(){return SECTIONS[section];}
    private float contentHeight(){return fields().length*fieldPitch();}
    private static boolean fieldControl(int id){return id>=7&&id<=24;}
    private void layout(){
        w=Math.min(760,Math.max(1,width-20));h=Math.min(464,Math.max(1,height-20));ox=(width-w)/2;oy=(height-h)/2;
        float max=Math.max(0,contentHeight()-formBounds().height);targetScroll=Math.max(0,Math.min(max,targetScroll));scroll=Math.max(0,Math.min(max,scroll));layoutPicker();
    }
    private boolean wide(){return w>=560&&h>=340;}
    protected ClipRect formBounds(){return new ClipRect(ox+14,tabsY()+36,wide()?Math.min(374,w*.56f):w-28,Math.max(30,oy+h-66-(tabsY()+36)));}
    protected ClipRect actionBounds(int id){
        if(id==0)return new ClipRect(ox+14,oy+(compact()?39:48),26,24);
        if(id==1)return new ClipRect(ox+44,oy+(compact()?39:48),26,24);
        if(id==2)return new ClipRect(ox+w-148,oy+(compact()?39:48),134,24);
        if(id>=3&&id<=6){float bw=(w-46)/4;return new ClipRect(ox+14+(id-3)*(bw+6),oy+h-46,bw,28);}
        if(id>=25&&id<=27){ClipRect f=formBounds();float cell=(f.width-8)/3;return new ClipRect(f.x+(id-25)*(cell+4),tabsY(),cell,26);}
        int row=-1;for(int i=0;i<fields().length;i++)if(fields()[i]==id)row=i;
        if(row<0)return new ClipRect(ox,oy,0,0);
        ClipRect form=formBounds();float label=Math.min(106,form.width*.38f);
        return new ClipRect(form.x+label,form.y+row*fieldPitch()-scroll,form.width-label-4,30);
    }
    protected void revealControl(int id){
        if(!fieldControl(id))return;
        for(int i=0;i<SECTIONS.length;i++)for(int candidate:SECTIONS[i])if(candidate==id&&section!=i){section=i;scroll=targetScroll=0;}
        layout();ClipRect r=actionBounds(id),v=formBounds();
        if(r.y<v.y)scroll=targetScroll=Math.max(0,scroll+r.y-v.y);
        else if(r.y+r.height>v.y+v.height)scroll=targetScroll=scroll+r.y+r.height-v.y-v.height;
    }
    private boolean controlVisible(int id){return !fieldControl(id)||!formBounds().intersect(actionBounds(id)).isEmpty();}
    private TTFFontRenderer font(){return ClickControlStyle.font();}
    private void text(String value,float x,float y,int color){font().drawString(value,x,y,color);}
    private String fit(String value,float width){return ClickControlStyle.fit(font(),value,width);}
    private boolean enabled(int id){
        if(id>=25)return true;
        if(id>=7)return draft.isEditable();if(id==6)return draft.canDelete()&&!catalog.isReadOnly();
        if(id==2)return !catalog.isReadOnly();
        return id!=3||!draft.isEditable()||!catalog.isReadOnly();
    }
    private String fieldLabel(int id){switch(id){case 9:return "Theme name";case 10:return "First color";case 11:return "Second color";case 12:return "Surface";case 13:return "Text";case 14:return "Accent";case 7:return "Direction";case 8:return "Animation";case 20:return "Speed";case 21:return "Glow amount";case 22:return "Glow radius";case 23:return "Border amount";case 24:return "Border width";default:return "";}}
    private void control(int id,String label){
        ClipRect r=GuiStyle.buttonBounds(actionBounds(id),controlMotion[id]);boolean on=enabled(id);Theme theme=Theme.getDefaultTheme();
        me.youngjibbit.clickgui.utils.MenuSurface.card(r.x,r.y,r.width,r.height,theme,id==3||id==25+section,controlMotion[id].amount());
        GuiStyle.feedback(r,controlMotion[id]);
        int ink=UiEffects.alpha(LegacyUiStyle.INK,on?1:.38f);
        String icon=id==0?"back":id==1?"chevron":id==2?"duplicate":id==3?"check":id==4?"back":id==5?"refresh":id==6?"delete":null;
        if(icon!=null){
            if(id==1){org.lwjgl.opengl.GL11.glPushMatrix();try{org.lwjgl.opengl.GL11.glTranslatef(r.x+r.width/2,r.y+r.height/2,0);org.lwjgl.opengl.GL11.glRotatef(-90,0,0,1);LegacyUiStyle.icon(icon,-6,-6,12,ink);}finally{org.lwjgl.opengl.GL11.glPopMatrix();}}
            else LegacyUiStyle.icon(icon,r.x+8,r.y+(r.height-12)/2,12,ink);
            if(id<2)return;
        }
        float left=icon==null?8:26;
        float right=id>=10&&id<=14?30:8;
        try(GuiClip ignored=GuiClip.push(new ClipRect(r.x+left,r.y+1,Math.max(0,r.width-left-right),r.height-2))){
            if(editingField==id)while(label.length()>1&&font().getWidth(label)>r.width-left-right)label=label.substring(label.offsetByCodePoints(0,1));
            text(fit(label,r.width-left-right),r.x+left,r.y+(r.height-font().getLineHeight())/2,ink);
        }
    }
    @Override public void drawScreen(int mx,int my,float partialTicks){
        UiEffects.beginFrame();layout();long now=System.nanoTime();float dt=Math.max(0,Math.min(.05f,(now-lastFrame)/1e9f));lastFrame=now;
        scroll+=(targetScroll-scroll)*UiEffects.response(dt,22);advanceMotion(dt,mx,my);
        Theme theme=Theme.getDefaultTheme();
        try(RenderState state=new RenderState()){
            state.screenSpace();if(mc.theWorld==null)me.youngjibbit.gui.MainMenu.drawBackdrop(theme);
            rootSurface.panel(ox,oy,w,h,9,theme,true,true,true,5,.94f,.55f,()->{
                try(GuiClip clip=GuiClip.push(new ClipRect(ox+2,oy+2,w-4,h-4))){
                    LegacyUiStyle.icon("palette",ox+15,oy+15,16,theme.getPrimaryGradientRGB());
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString("Theme studio",ox+40,oy+13,LegacyUiStyle.INK);
                    if(!compact())text(fit("Color, motion and light in one place",w-54),ox+40,oy+32,LegacyUiStyle.MUTED);
                    control(0,"");control(1,"");control(2,"Duplicate");
                    if(w>360)text(fit(draft.title.getValue(),w-246),ox+82,actionBounds(0).y+8,LegacyUiStyle.MUTED);
                    LegacyUiStyle.rule(ox+14,tabsY()-8,w-28,theme);
                    for(int i=0;i<SECTION_NAMES.length;i++)control(25+i,SECTION_NAMES[i]);
                    ClipRect tab=actionBounds(25+section);
                    UIUtils.drawDecorationRound(Math.max(formBounds().x,Math.min(formBounds().x+formBounds().width-tab.width,sectionPosition))+8,tab.y+tab.height-2,Math.max(1,tab.width-16),2,1,LegacyUiStyle.accent(theme,.9f));
                    try(GuiClip form=GuiClip.push(formBounds())){
                        for(int id:fields()){
                            ClipRect r=actionBounds(id);if(!controlVisible(id))continue;
                            text(fit(fieldLabel(id),r.x-formBounds().x-10),formBounds().x+2,r.y+10,LegacyUiStyle.MUTED);
                            if(id==9)control(id,editingField==id?buffer+"_":draft.title.getValue());
                            else if(id>=10&&id<=14){
                                control(id,editingField==id?buffer+"_":rgba(colors()[id-10].getValue()));
                                Color swatch=colors()[id-10].getValue();
                                UIUtils.drawDecorationRound(r.x+r.width-25,r.y+6,19,18,4,new Color(93,103,119));
                                UIUtils.drawDecorationRound(r.x+r.width-24,r.y+7,17,16,3,swatch);
                            }else if(id==7)control(id,draft.direction.getValue());
                            else if(id==8)control(id,draft.animated.getValue()?"Animated":"Static");
                            else slider(id,sliders()[id-20]);
                        }
                    }
                    ClipRect f=formBounds();if(contentHeight()>f.height){float thumb=Math.max(12,f.height*f.height/contentHeight());UIUtils.drawDecorationRound(f.x+f.width-1,f.y+scroll/(contentHeight()-f.height)*(f.height-thumb),1,thumb,.5f,LegacyUiStyle.alpha(LegacyUiStyle.MUTED,.6f));}
                    if(wide())drawPreview(theme);
                    LegacyUiStyle.rule(ox+14,oy+h-54,w-28,theme);
                    control(3,draft.isEditable()?"Save & use":"Use preset");control(4,"Cancel");control(5,"Reset");control(6,deleteConfirm?"Confirm":"Delete");
                    String message=!status.isEmpty()?status:catalog.isReadOnly()?"Read-only library":draft.isEditable()?"F6: section  ·  Right click: exact value  ·  Save applies":"Duplicate this preset to customize it";
                    text(fit(message,w-28),ox+14,oy+h-12,!status.isEmpty()?0xffffb5a0:LegacyUiStyle.FAINT);
                    if(pickerField>=0)drawPicker(LegacyUiStyle.INK);
                }
            });
        }
    }
    protected ClipRect previewBounds(){
        if(!wide())return new ClipRect(0,0,0,0);
        ClipRect f=formBounds();float left=f.x+f.width+20,top=tabsY()+38;
        return new ClipRect(left,top,ox+w-14-left,Math.min(176,Math.max(68,f.y+f.height-top-56)));
    }
    private void drawPreview(Theme theme){
        ClipRect f=formBounds();float x=f.x+f.width+20,y=tabsY(),pw=ox+w-14-x;
        LegacyUiStyle.card(x,y,pw,28,theme,false,false);
        LegacyUiStyle.icon("palette",x+9,y+8,12,LegacyUiStyle.MUTED);
        text("Live preview",x+28,y+9,LegacyUiStyle.INK);
        ClipRect bounds=previewBounds();float top=bounds.y,ph=bounds.height;
        try(GuiClip clip=GuiClip.push(bounds)){
            // A solid backing also covers antialiased checkerboard seams, so the
            // preview's transparency sample is independent of the moving menu scene.
            UIUtils.drawDecorationRound(x-1,top-1,pw+2,ph+2,0,new Color(0xff25313e,true));
            for(int row=0;row<(int)Math.ceil(ph/10);row++)for(int col=0;col<(int)Math.ceil(pw/10);col++)
                UIUtils.drawDecorationRound(x+col*10,top+row*10,Math.min(10,pw-col*10),Math.min(10,ph-row*10),0,new Color((row+col)%2==0?0xff344252:0xff25313e,true));
            UiEffects.independentScene(()->previewScene.withScene(theme,()->{
                try(UiEffects.GradientSpace gradient=UiEffects.gradientSpace(x,top,pw,ph)){
                    surface.panel(x+8,top+8,pw-16,ph-16,7,theme,true,true,true,5,.8f,.8f,()->{
                        textEffects.draw(Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28"),"Neutron",x+18,top+19,theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),2,theme.isAnimated(),theme.getAnimationSpeed(),true,theme.getGlowStrength(),theme.getGlowRadius(),false);
                        UIUtils.drawDecorationGradientVertical(x+14,top+51,pw-28,24,4,UiEffects.color(theme.getPrimaryGradientRGB(),.22f),UiEffects.color(theme.getSecondaryGradientRGB(),.22f));
                        text(fit("Enabled module",pw-42),x+21,top+59,theme.getTextColor().getRGB());
                        if(ph>=140){
                            text(fit("Sample control",pw-42),x+21,top+98,theme.getTextColor().getRGB());
                            UIUtils.drawDecorationGradientVertical(x+21,top+119,pw-51,3,1,theme.getPrimaryGradientColor(),theme.getSecondaryGradientColor());
                        }
                    });
                }
            }));
        }
        float swatchY=top+ph+12;
        ColorValue[] colors=colors();float cell=(pw-16)/5;
        for(int i=0;i<colors.length;i++)UIUtils.drawDecorationRound(x+i*(cell+4),swatchY,cell,18,4,colors[i].getValue());
        text(fit(draft.isEditable()?"Unsaved draft":"Preset palette",pw),x,swatchY+26,LegacyUiStyle.MUTED);
    }
    private ClipRect pickerBox(){float pw=Math.min(270,w-28),ph=Math.min(232,h-18);return new ClipRect(ox+(w-pw)/2,oy+(h-ph)/2,pw,ph);}
    private ClipRect pickerDone(){ClipRect p=pickerBox();return new ClipRect(p.x+10,p.y+p.height-32,p.width-20,22);}
    private void layoutPicker(){ClipRect p=pickerBox();picker.layout(new ClipRect(p.x+12,p.y+30,p.width-24,Math.max(62,p.height-78)));}
    private void drawPicker(int ink){
        ClipRect p=pickerBox();Theme theme=Theme.getDefaultTheme();
        GuiStyle.panel(p.x,p.y,p.width,p.height,7,1,LegacyUiStyle.panel(theme));
        LegacyUiStyle.icon("palette",p.x+12,p.y+9,12,theme.getPrimaryGradientRGB());
        text(fit(fieldLabel(pickerField)+" / HSV + alpha",p.width-42),p.x+31,p.y+10,ink);
        picker.draw();ClipRect done=pickerDone();me.youngjibbit.clickgui.utils.MenuSurface.card(done.x,done.y,done.width,done.height,theme,true,false);
        LegacyUiStyle.icon("check",done.x+8,done.y+5,12,ink);
        text(fit("Done · Esc restores color",done.width-33),done.x+26,done.y+6,ink);
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
    protected ClipRect sliderTrack(int id){
        ClipRect r=actionBounds(id);float left=Math.round(r.x+8),right=Math.round(r.x+r.width-8);
        return new ClipRect(left,r.y+r.height-5,Math.max(1,right-left),2);
    }
    private void slider(int id,SliderValue value){
        control(id,editingField==id?buffer+"_":String.format(Locale.ROOT,"%.2f",value.getValue()));float f=sliderProgress[id-20];ClipRect track=sliderTrack(id);
        UIUtils.drawDecorationRound(track.x,track.y,track.width,track.height,1,LegacyUiStyle.alpha(LegacyUiStyle.MUTED,.18f));
        UIUtils.drawDecorationRound(track.x,track.y,Math.max(.01f,track.width*f),track.height,1,LegacyUiStyle.accent(Theme.getDefaultTheme(),.9f));
    }
    private void updateSlider(int id,int mx){ClipRect track=sliderTrack(id);SliderValue v=sliders()[id-20];double t=Math.max(0,Math.min(1,(mx-track.x)/track.width));if(v instanceof DemiseSliderValue)((DemiseSliderValue)v).setFromUi(v.getMin()+t*(v.getMax()-v.getMin()));else v.setValue(v.getMin()+t*(v.getMax()-v.getMin()));}
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
        if(button==0)for(int id:order())if(controlVisible(id)&&(!fieldControl(id)||formBounds().contains(mx,my))&&actionBounds(id).contains(mx,my)&&enabled(id)){visualPressed=id;break;}
        if(button!=0&&button!=1)return;
        for(int id:order())if(controlVisible(id)&&(!fieldControl(id)||formBounds().contains(mx,my))&&actionBounds(id).contains(mx,my)&&enabled(id)){
            if(button==1){if(id>=20&&id<=24&&commitField()){focus=id;beginField(id);}return;}
            if(id==4||id==5||id==6){editingField=-1;focus=id;activate(id);return;}
            if(!commitField())return;focus=id;
            if(id==9||id>=10&&id<=14){beginField(id);return;}
            if(id>=20&&id<=24){drag=id;updateSlider(id,mx);return;}activate(id);return;
        }
    }
    private int[] order(){int[] fields=fields(),ids=new int[10+fields.length];int at=0;for(int id:new int[]{0,1,2,25,26,27})ids[at++]=id;for(int id:fields)ids[at++]=id;for(int id:new int[]{3,4,5,6})ids[at++]=id;return ids;}
    /** The tab group has one keyboard stop; F6 changes its section. */
    private int[] focusOrder(){int[] fields=fields(),ids=new int[8+fields.length];int at=0;for(int id:new int[]{0,1,2,25+section})ids[at++]=id;for(int id:fields)ids[at++]=id;for(int id:new int[]{3,4,5,6})ids[at++]=id;return ids;}
    private void beginField(int id){drag=-1;editingField=id;buffer=id>=20?Double.toString(sliders()[id-20].getValue()):id==9?draft.title.getValue():rgba(colors()[id-10].getValue());originalName=draft.title.getValue();replaceInput=true;}
    private boolean commitField(){if(editingField<0)return true;try{if(editingField>=20){SliderValue value=sliders()[editingField-20];double next=Double.parseDouble(buffer);if(!Double.isFinite(next)||next<value.getMin()||next>value.getMax())throw new IllegalArgumentException("Number must be within "+value.getMin()+" - "+value.getMax());if(value instanceof DemiseSliderValue)((DemiseSliderValue)value).setFromUi(next);else value.setValue(next);}else if(editingField==9)draft.title.setValue(Theme.validName(buffer));else{if(!buffer.matches("[0-9a-fA-F]{8}"))throw new IllegalArgumentException("RGBA needs eight hex digits: RRGGBBAA");colors()[editingField-10].setColor(new Color(Integer.rotateRight((int)Long.parseLong(buffer,16),8),true));}editingField=-1;status="";return true;}catch(IllegalArgumentException ex){status=ex.getMessage();return false;}}
    protected void activate(int id)throws IOException{
        if(id>=25&&id<=27){if(commitField()){section=id-25;focus=id;scroll=targetScroll=0;drag=-1;deleteConfirm=false;status="";layout();}return;}
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
            if(key==Keyboard.KEY_BACK){buffer=replaceInput||buffer.isEmpty()?"":buffer.substring(0,buffer.offsetByCodePoints(buffer.length(),-1));replaceInput=false;}
            else if(c>=32&&c!=127&&(replaceInput||buffer.length()<(editingField==9?48:editingField>=20?32:8))){buffer=replaceInput?String.valueOf(c):buffer+c;replaceInput=false;}
            if(editingField==9)draft.title.setValue(buffer);return;
        }
        if(key==Keyboard.KEY_ESCAPE){activate(4);return;}if(key==Keyboard.KEY_F6){activate(25+(section+(shiftDown()?2:1))%3);return;}if(key==Keyboard.KEY_TAB){nextFocus(shiftDown()?-1:1);return;}
        if(focus>=20&&focus<=24&&enabled(focus)&&(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT)){SliderValue value=sliders()[focus-20];if(value instanceof DemiseSliderValue)((DemiseSliderValue)value).stepFromUi(key==Keyboard.KEY_LEFT?-1:1);else value.setValue(value.getValue()+(value.getMax()-value.getMin())/100*(key==Keyboard.KEY_LEFT?-1:1));return;}
        if(focus>=20&&focus<=24&&enabled(focus)&&(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)){beginField(focus);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){ClipRect r=actionBounds(focus);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
    }
    private void nextFocus(int step){int[] ids=focusOrder();int index=0;for(int i=0;i<ids.length;i++)if(ids[i]==focus)index=i;for(int i=0;i<ids.length;i++){index=(index+step+ids.length)%ids.length;if(enabled(ids[index])){focus=ids[index];revealControl(focus);return;}}}
    @Override protected void mouseReleased(int x,int y,int button){visualPressed=-1;drag=-1;picker.release();}
    private void releasePreviewResources(){surface.close();previewScene.close();textEffects.close();}
    @Override public void onGuiClosed(){if(Keyboard.isCreated()&&ownsKeyboardRepeat)Keyboard.enableRepeatEvents(previousRepeat);ownsKeyboardRepeat=false;resetMotion();drag=-1;editingField=-1;picker.release();pickerField=-1;pickerOriginal=null;draft.close();rootSurface.close();releasePreviewResources();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
