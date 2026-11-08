package me.youngjibbit.clickgui.component.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.ComboValue;
import java.awt.Color;

public class ClickCombo extends Component.Sub<ComboValue> {
    private boolean open;
    private float progress;
    private final ClickAnimation animation=new ClickAnimation(180,1,Direction.BACKWARDS);
    private final HudSpring panelDisclosure=new HudSpring();
    private float disclosureBounce;
    public ClickCombo(ComboValue value,float x,float y,float width,float height){
        super(value,x,y,width,height);setWidth(width);animation.timerUtil.setTime(System.currentTimeMillis()-180);
    }
    private boolean compact(){return width<160;}
    @Override public void setWidth(float next){width=next;height=compact()?36:24;}
    public void updateAnimation(){
        animation.setDirection(open?Direction.FORWARDS:Direction.BACKWARDS);
        progress=Math.max(0,Math.min(1,UiEffects.motion()?animation.getOutput().floatValue():open?1:0));
    }

    /** Only the Panel screen calls this; Dropdown retains ClickAnimation. */
    public void resetPanelMotion(){
        panelDisclosure.snap(open?1:0);
        disclosureBounce=0;
    }

    public void updatePanelAnimation(float dt){
        if(!panelStyle)return;
        ClientSettings settings=ClientSettings.current();
        float response=panelDisclosure.step(open?1:0,dt,
                settings==null||settings.animations.getValue(),
                settings!=null&&settings.reducedMotion.getValue(),
                settings==null?"Balanced":settings.motionIntensity.getValue());
        progress=Math.max(0,Math.min(1,response));
        disclosureBounce=Math.max(0,Math.min(.1f,response-progress));
    }
    private float fieldX(){return compact()?x+7:ClickControlStyle.right(x,width)-fieldWidth();}
    private float fieldWidth(){return compact()?width-14:ClickControlStyle.fieldWidth(width);}
    private float fieldY(){return y+(compact()?16:3);}
    private float fieldHeight(){return compact()?17:height-5;}
    private float optionHeight(){return compact()?19:24;}
    @Override public ClipRect presentationBounds(){return new ClipRect(fieldX(),fieldY(),fieldWidth(),fieldHeight());}
    private ClipRect optionsClip(){
        ClipRect bounds=new ClipRect(x+(compact()?5:10),y+height,width-(compact()?10:20),value.getOptions().length*optionHeight()*progress);
        return clip==null?bounds:clip.intersect(bounds);
    }
    @Override public void drawScreen(int mouseX,int mouseY){
        me.youngjibbit.utils.font.TTFFontRenderer font=compact()?Pulsar.INSTANCE.fontManager.getFont("Poppins-Regular 16"):ClickControlStyle.font();
        Theme theme=Theme.resolve(Theme.getDefaultTheme());int ink=hoverInk(theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB());
        font.drawString(ClickControlStyle.fit(font,UiEffects.settingLabel(value),compact()?width-14:fieldX()-x-(panelStyle?24:20)),x+(panelStyle?17:compact()?7:12),y+(compact()?2:6),ink);
        if (panelStyle) ClickControlStyle.panelField(fieldX(),fieldY(),fieldWidth(),fieldHeight(),theme,open||isMouseOver(mouseX,mouseY));
        else GuiStyle.panel(fieldX(),fieldY(),fieldWidth(),fieldHeight(),3,open||isMouseOver(mouseX,mouseY)?.7f:.12f,new Color(29,31,36));
        drawFeedback();
        try(GuiClip ignored=GuiClip.push(new ClipRect(fieldX()+3,fieldY()+1,fieldWidth()-6,fieldHeight()-2))){
            font.drawString(ClickControlStyle.fit(font,label(value.getValue()),fieldWidth()-21),
                    fieldX()+5,fieldY()+3,ink);
            // One stroke family, animated disclosure; hit geometry stays fixed.
            float cx=fieldX()+fieldWidth()-10,cy=fieldY()+fieldHeight()/2;
            UIUtils.drawDecorationRound(cx-2,cy,5,1,0,UiEffects.color(ink,.8f));
            if(progress<.99f)UIUtils.drawDecorationRound(cx,cy-2,1,5*(1-progress),0,UiEffects.color(ink,.8f));
            if(panelStyle&&disclosureBounce>.002f)UIUtils.drawDecorationRound(
                    cx-3-disclosureBounce*7,cy-3-disclosureBounce*7,
                    6+disclosureBounce*14,6+disclosureBounce*14,3,
                    UiEffects.color(theme.getPrimaryGradientRGB(),disclosureBounce*1.7f));
        }
        ClipRect options=optionsClip();
        if(!options.isEmpty())try(GuiClip ignored=GuiClip.push(options)){
            UIUtils.drawDecorationRound(options.x,y+height,options.width,value.getOptions().length*optionHeight(),2,new Color(16,19,26,225));
            for(int i=0;i<value.getOptions().length;i++){
                String option=value.getOptions()[i];float rowY=y+height+i*optionHeight();
                boolean selected=option.equals(value.getValue());
                if(selected)UIUtils.drawDecorationGradientVertical(options.x,rowY,options.width,optionHeight(),0,UiEffects.color(theme.getPrimaryGradientRGB(),.25f),UiEffects.color(theme.getSecondaryGradientRGB(),.25f));
                if (selected && panelStyle)
                    UIUtils.drawDecorationRound(options.x + 2, rowY + 5, 2, optionHeight() - 10, 1, theme.getPrimaryGradientColor());
                font.drawString(ClickControlStyle.fit(font,label(option),options.width-12),options.x+6,rowY+(optionHeight()-10)/2,ink);
            }
        }
    }
    @Override public void mouseClicked(int mouseX,int mouseY,int button){
        if(button!=0)return;
        if(hit(mouseX,mouseY,fieldX(),fieldY(),fieldWidth(),fieldHeight())){open=!open;return;}
        if(open&&optionsClip().contains(mouseX,mouseY)){
            int index=(int)((mouseY-y-height)/optionHeight());
            if(index>=0&&index<value.getOptions().length){value.setValue(value.getOptions()[index]);open=false;}
        }
    }
    private String label(String option){return UiEffects.settingOption(value,option);}
    public boolean isMouseOver(int mouseX,int mouseY){return hit(mouseX,mouseY,fieldX(),fieldY(),fieldWidth(),fieldHeight());}
    @Override public float getHeight(){return height+value.getOptions().length*optionHeight()*progress;}
    public boolean isOpen(){return open;}
    public void close(){open=false;}
    @Override public void keyTyped(char typedChar,int key){
        if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE)close();
        else if(key==org.lwjgl.input.Keyboard.KEY_RETURN||key==org.lwjgl.input.Keyboard.KEY_SPACE)open=!open;
        else if((key==org.lwjgl.input.Keyboard.KEY_LEFT||key==org.lwjgl.input.Keyboard.KEY_RIGHT)&&value.getOptions().length>0){
            int n=value.getOptions().length;value.setValue(value.getOptions()[(value.getSelectedIndex()+(key==org.lwjgl.input.Keyboard.KEY_LEFT?n-1:1))%n]);
        }
    }
}
