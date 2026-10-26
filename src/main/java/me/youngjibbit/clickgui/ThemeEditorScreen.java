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
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import static org.lwjgl.opengl.GL11.*;

/** One geometry for drawing and input; editor values never participate in automatic config saving. */
public class ThemeEditorScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ThemeCatalog catalog;
    private final ThemeDraft draft;
    private final ArrayListSurface rootSurface=new ArrayListSurface(),previewScene=new ArrayListSurface(),surface=new ArrayListSurface();
    private final TextEffects textEffects=new TextEffects();
    private float ox,oy,scale;
    private int focus=0,editingField=-1,drag=-1;
    private String buffer="",originalName="",status="";
    private boolean replaceInput;
    public ThemeEditorScreen(GuiScreen parent,ThemeCatalog catalog,Consumer<String> selection){this.parent=parent;this.catalog=catalog;draft=new ThemeDraft(catalog,selection);}
    private void layout(){scale=Math.min(1,Math.min((width-16)/620f,(height-16)/420f));scale=Math.max(.05f,scale);ox=(width-620*scale)/2;oy=(height-420*scale)/2;}
    private float px(float x){return ox+x*scale;}private float py(float y){return oy+y*scale;}
    protected ClipRect actionBounds(int id){layout();float x,y,w,h=21;
        if(id==0){x=20;y=43;w=25;}else if(id==1){x=334;y=43;w=25;}else if(id==2){x=374;y=43;w=226;}
        else if(id>=3&&id<=6){x=20+(id-3)*100;y=363;w=92;}
        else if(id==7||id==8){x=166;y=id==7?252:280;w=169;}
        else if(id==9){x=166;y=78;w=169;}
        else if(id>=10&&id<=14){x=166;y=110+(id-10)*28;w=169;}
        else if(id==20){x=166;y=309;w=169;}
        else if(id>=21&&id<=24){x=487;y=254+(id-21)*28;w=113;}
        else throw new IllegalArgumentException("Unknown editor control");return new ClipRect(px(x),py(y),w*scale,h*scale);
    }
    private TTFFontRenderer font(){return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18");}
    private void text(String value,float x,float y,int color){text(font(),value,x,y,color);}
    private void text(TTFFontRenderer font,String value,float x,float y,int color){glPushMatrix();glTranslatef(px(x),py(y),0);glScalef(scale,scale,1);font.drawString(value,0,0,color);glPopMatrix();}
    private String fit(String value,float width){while(value.length()>1&&font().getWidth(value)>width)value=value.substring(0,value.length()-1);return value;}
    private boolean enabled(int id){if(id>=7)return draft.isEditable();if(id==6)return draft.canDelete()&&!catalog.isReadOnly();return id!=3||!draft.isEditable()||!catalog.isReadOnly();}
    private void control(int id,String label){ClipRect r=actionBounds(id);boolean on=enabled(id);GuiStyle.panel(r.x,r.y,r.width,r.height,3*scale,on&&id==focus?1:0,new Color(20,20,23));try(GuiClip ignored=GuiClip.push(r)){text(label,(r.x-ox)/scale+6,(r.y-oy)/scale+5,on?0xffe5e8ef:0xff707680);}}
    @Override public void drawScreen(int mx,int my,float partialTicks){
        layout();if(drag>=0)updateSlider(drag,mx);
        Theme theme=Theme.getDefaultTheme();
        try(RenderState state=new RenderState()){
            state.screenSpace();UIUtils.drawRound(0,0,width,height,0,new Color(8,9,13));
            rootSurface.panel(px(0),py(0),620*scale,420*scale,6*scale,theme,true,true,true,5,.8f,.65f,()->{
            try(GuiClip clip=GuiClip.push(new ClipRect(px(3),py(3),614*scale,414*scale))){
                text(Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28"),"Theme studio",20,15,0xfff3f4f8);
                text("Presets and custom themes",374,20,0xff9199a8);
                control(0,"<");control(1,">");text(fit(draft.title.getValue().isEmpty()?"Unnamed draft":draft.title.getValue(),270),54,49,0xffdde3ed);control(2,"Duplicate as custom theme");
                String[] labels={"Name","Stop 1 / RGBA","Stop 2 / RGBA","Background / RGBA","Text / RGBA","Accent / RGBA"};
                for(int i=0;i<6;i++)text(labels[i],20,i==0?84:116+(i-1)*28,0xffaab1bd);
                control(9,editingField==9?buffer+"_":draft.title.getValue());
                ColorValue[] colors=colors();
                for(int i=0;i<colors.length;i++){control(10+i,editingField==10+i?buffer+"_":rgba(colors[i].getValue()));ClipRect r=actionBounds(10+i);UIUtils.drawDecorationRound(r.x+r.width-21*scale,r.y+4*scale,15*scale,13*scale,2*scale,colors[i].getValue());}
                text("Direction",20,258,0xffaab1bd);control(7,draft.direction.getValue());text("Motion",20,286,0xffaab1bd);control(8,draft.animated.getValue()?"Animated":"Static");
                text("Speed",20,315,0xffaab1bd);slider(20,draft.speed);
                String[] effects={"Glow strength","Glow radius","Outline strength","Outline width"};SliderValue[] sliders=sliders();
                for(int i=0;i<4;i++){text(effects[i],374,260+i*28,0xffaab1bd);slider(21+i,sliders[i+1]);}
                control(3,draft.isEditable()?"Save & use":"Use preset");control(4,"Cancel");control(5,"Reset draft");control(6,"Delete");
                drawPreview(theme);
                String message=!status.isEmpty()?status:catalog.isReadOnly()?"Read-only library: "+catalog.error():"Preview only. Save applies; Cancel restores. Enter / right click: exact parameter.";
                text(fit(message,580),20,395,!status.isEmpty()||catalog.isReadOnly()?0xffffb5a0:0xff929baa);
                text("Local module text/effect switches remain active.",374,236,0xff929baa);
            }
            });
        }
    }
    private void drawPreview(Theme theme){
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
        net.minecraft.client.renderer.GlStateManager.disableBlend();glBegin(GL_QUADS);
        for(int row=0;row<10;row++)for(int col=0;col<20;col++){
            if((row+col)%2==0)glColor3f(.20f,.26f,.33f);else glColor3f(.09f,.14f,.19f);
            float x=px(374+col*11.3f),y=py(78+row*15.1f),w=11.3f*scale,h=15.1f*scale;
            glVertex2f(x,y);glVertex2f(x+w,y);glVertex2f(x+w,y+h);glVertex2f(x,y+h);
        }glEnd();net.minecraft.client.renderer.GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
        UiEffects.independentScene(()->previewScene.withScene(theme,()->{
        glPushMatrix();glTranslatef(px(383),py(88),0);glScalef(scale,scale,1);
        textEffects.draw(Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28"),"Neutron",0,0,theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),theme.isVertical()?2:1,theme.isAnimated(),theme.getAnimationSpeed(),true,theme.getGlowStrength(),theme.getGlowRadius()*scale,false);glPopMatrix();
        GuiStyle.panel(px(383),py(124),208*scale,27*scale,4*scale,1,new Color(17,19,25));text("Preview button",394,133,theme.getTextColor().getRGB());
        surface.panel(px(383),py(171),208*scale,48*scale,5*scale,theme,true,true,true,3,.45f,.8f,()->{text("Session Info",394,179,theme.getTextColor().getRGB());text("Time: 1h 02m 03s",394,197,theme.getTextColor().getRGB());});
        }));
    }
    private ColorValue[] colors(){return new ColorValue[]{draft.first,draft.second,draft.background,draft.text,draft.accent};}
    private SliderValue[] sliders(){return new SliderValue[]{draft.speed,draft.glowStrength,draft.glowRadius,draft.outlineStrength,draft.outlineWidth};}
    private static String rgba(Color c){return String.format(Locale.ROOT,"%08X",Integer.rotateLeft(c.getRGB(),8));}
    private void slider(int id,SliderValue value){ClipRect r=actionBounds(id);control(id,editingField==id?buffer+"_":String.format(Locale.ROOT,"%.2f",value.getValue()));float f=(float)((value.getValue()-value.getMin())/(value.getMax()-value.getMin()));UIUtils.drawRound(r.x+3*scale,r.y+r.height-4*scale,(r.width-6*scale)*Math.max(.001f,f),2*scale,scale,new Color(Theme.getDefaultTheme().getAccentColor().getRGB(),true));}
    private void updateSlider(int id,int mx){ClipRect r=actionBounds(id);SliderValue v=sliders()[id-20];double t=Math.max(0,Math.min(1,(mx-r.x)/r.width));v.setValue(v.getMin()+t*(v.getMax()-v.getMin()));}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button!=0&&button!=1)return;
        for(int id:order())if(actionBounds(id).contains(mx,my)&&enabled(id)){
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
        try{
            if(id==0||id==1){java.util.List<Theme> all=catalog.all();int index=0;for(int i=0;i<all.size();i++)if(all.get(i).name().equals(draft.reference()))index=i;draft.choose(all.get((index+(id==0?all.size()-1:1))%all.size()));}
            else if(id==2)draft.duplicate();else if(id==3){draft.save();me.youngjibbit.notifications.NotificationManager.success("Theme saved","Theme changes were saved and selected.");leave();}else if(id==4){draft.cancel();leave();}else if(id==5)draft.reset();else if(id==6){draft.delete();me.youngjibbit.notifications.NotificationManager.success("Theme deleted","The theme was removed successfully.");leave();}else if(id==7)draft.direction.setValue(draft.direction.getSelectedIndex()==0?"Vertical":"Horizontal");else if(id==8)draft.animated.setValue(!draft.animated.getValue());
            status="";
        }catch(IllegalArgumentException|IOException ex){status=ex.getMessage();System.err.println("[Theme] "+status);me.youngjibbit.notifications.NotificationManager.error("Theme action failed","The theme action did not complete. See the theme diagnostics.");}
    }
    protected void leave(){mc.displayGuiScreen(parent);}
    protected boolean ctrlDown(){return Keyboard.isCreated()&&isCtrlKeyDown();}protected boolean shiftDown(){return Keyboard.isCreated()&&isShiftKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException{
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
    private void nextFocus(int step){int[] ids=order();int index=0;for(int i=0;i<ids.length;i++)if(ids[i]==focus)index=i;for(int i=0;i<ids.length;i++){index=(index+step+ids.length)%ids.length;if(enabled(ids[index])){focus=ids[index];return;}}}
    @Override protected void mouseReleased(int x,int y,int button){drag=-1;}
    @Override public void onGuiClosed(){drag=-1;editingField=-1;draft.close();surface.close();rootSurface.close();previewScene.close();textEffects.close();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
