package me.youngjibbit.clickgui.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.TextEffects;

/** Gradient shading applies to the entire string; the font owns all advances and formatting. */
public class PanelUtils {
    public int getColorFromTheme(Theme theme,float offset){return Theme.resolve(theme).getGradientRGB(offset);}
    private void draw(String text,int x,int y,Theme theme,String face){
        TextEffects.direct(Theme.resolve(theme),()->Pulsar.INSTANCE.getFontManager().getFont(face).drawString(text,x,y,-1));
    }
    public void drawClientName(String text,int x,int y,Theme theme){draw(text,x,y,theme,"Product-Sans-Bold 28");}
    public void dropdownModuleString(String text,int x,int y,Theme theme){draw(text,x,y,theme,"Product-Sans-Bold 15");}
    public void categoryString(String text,int x,int y,Theme theme){draw(text,x,y,theme,"Poppins-Medium 18");}
    private void drawIcons(String text,int x,int y,Theme theme,String face){
        TextEffects.direct(Theme.resolve(theme),()->{
            me.youngjibbit.utils.font.TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getLocalFont(face);
            float pen=x;
            for(int at=0;at<text.length();){
                int cp=text.codePointAt(at);at+=Character.charCount(cp);
                String icon=new String(Character.toChars(cp));
                font.drawString(icon,pen,y,-1);pen+=font.getWidth(icon)*.78f;
            }
        });
    }
    public void icon1(String text,int x,int y,Theme theme){drawIcons(text,x,y,theme,"icon 23");}
    public void icon2(String text,int x,int y,Theme theme){drawIcons(text,x,y,theme,"icon1 23");}
}
