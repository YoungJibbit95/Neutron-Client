package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.ColorValue;
import java.awt.Color;
import org.lwjgl.input.Keyboard;

/** HSV + alpha editor. Input owns mutations; painting never modifies the value. */
public final class ThemeColorPicker {
    private ColorValue value;
    private float hue, saturation, brightness, alpha;
    private int drag = -1, focus;
    private ClipRect bounds = new ClipRect(0,0,0,0);
    public void setValue(ColorValue next) {
        value = next; drag = -1;
        Color c = next.getValue();
        float[] hsv = Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null);
        hue=hsv[0]; saturation=hsv[1]; brightness=hsv[2]; alpha=c.getAlpha()/255f;
    }
    public void layout(ClipRect bounds) { this.bounds=bounds; }
    public void release() { drag=-1; }
    public ClipRect control(int id) {
        if(id==0)return new ClipRect(bounds.x,bounds.y,bounds.width,Math.max(20,bounds.height-42));
        return new ClipRect(bounds.x,bounds.y+bounds.height-(id==1?32:12),bounds.width,12);
    }
    public void draw() {
        if(value==null)return;
        ClipRect sv=control(0),h=control(1),a=control(2);
        Color hueColor=Color.getHSBColor(hue,1,1);
        UIUtils.drawLiteralGradient(sv.x,sv.y,sv.width,sv.height,2,Color.WHITE,hueColor);
        UIUtils.drawLiteralGradientVertical(sv.x,sv.y,sv.width,sv.height,2,new Color(0,0,0,0),Color.BLACK);
        for(int i=0;i<6;i++)UIUtils.drawLiteralGradient(h.x+i*h.width/6,h.y,h.width/6+.1f,h.height,0,
                Color.getHSBColor(i/6f,1,1),Color.getHSBColor((i+1)/6f,1,1));
        for(int row=0;row<2;row++)for(int col=0;col<(int)Math.ceil(a.width/6);col++)
            UIUtils.drawDecorationRound(a.x+col*6,a.y+row*6,Math.min(6,a.width-col*6),6,0,new Color((row+col)%2==0?0xffa2a5ad:0xff555963,true));
        Color c=value.getValue();
        UIUtils.drawLiteralGradient(a.x,a.y,a.width,a.height,0,new Color(c.getRed(),c.getGreen(),c.getBlue(),0),new Color(c.getRed(),c.getGreen(),c.getBlue(),255));
        mark(sv.x+saturation*sv.width,sv.y+(1-brightness)*sv.height);
        rail(h,hue);rail(a,alpha);
        ClipRect focused=control(focus);
        UIUtils.drawDecorationRound(focused.x-3,focused.y,1,focused.height,0,Color.WHITE);
    }
    private void mark(float x,float y) {
        UIUtils.drawDecorationRound(x-3,y-3,6,6,3,Color.BLACK);
        UIUtils.drawDecorationRound(x-2,y-2,4,4,2,Color.WHITE);
    }
    private void rail(ClipRect r,float position) {
        float x=r.x+Math.max(1,Math.min(r.width-2,position*r.width));
        UIUtils.drawDecorationRound(x-1,r.y-1,3,r.height+2,1,Color.BLACK);
        UIUtils.drawDecorationRound(x,r.y,1,r.height,0,Color.WHITE);
    }
    public boolean click(int x,int y) {
        if(value==null)return false;
        for(int id=0;id<3;id++)if(control(id).contains(x,y)){drag=focus=id;move(x,y);return true;}
        return false;
    }
    public void move(int x,int y) {
        if(value==null||drag<0)return;
        ClipRect r=control(drag);
        float f=clamp((x-r.x)/r.width);
        if(drag==0){saturation=f;brightness=1-clamp((y-r.y)/r.height);}
        else if(drag==1)hue=f;else alpha=f;
        apply();
    }
    public boolean key(int key) {
        if(value==null)return false;
        if(key==Keyboard.KEY_TAB){focus=(focus+1)%3;return true;}
        int step=key==Keyboard.KEY_LEFT||key==Keyboard.KEY_DOWN?-1:key==Keyboard.KEY_RIGHT||key==Keyboard.KEY_UP?1:0;
        if(step==0)return false;
        if(focus==0){if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)brightness=clamp(brightness+step*.01f);else saturation=clamp(saturation+step*.01f);}
        else if(focus==1)hue=clamp(hue+step/360f);else alpha=clamp(alpha+step/255f);
        apply();return true;
    }
    private void apply() {
        int rgb=Color.HSBtoRGB(hue,saturation,brightness);
        value.setColor(new Color((rgb&0xffffff)|(Math.round(alpha*255)<<24),true));
    }
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
}
