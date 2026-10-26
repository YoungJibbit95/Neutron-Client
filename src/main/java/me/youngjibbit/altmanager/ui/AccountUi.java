package me.youngjibbit.altmanager.ui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.Account;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import java.awt.Color;
import java.util.Locale;

/** Account-only visual vocabulary built on the client's existing fonts and rounded shaders. */
public final class AccountUi {
    public static final int TEXT=0xffedf0f7, MUTED=0xff9298a8, FAINT=0xff666d7d, GREEN=0xff7dd8b1, RED=0xffec919c;
    private final ArrayListSurface surface = new ArrayListSurface();
    public static TTFFontRenderer font(int size) { return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular "+size); }
    public static TTFFontRenderer medium(int size) { return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium "+size); }
    public static Theme theme() { return Theme.resolve(Theme.getDefaultTheme()); }
    public static int accent() { return theme().getPrimaryGradientRGB(); }
    public static Color color(int rgb) { return new Color(rgb,true); }
    public static void round(ClipRect r,float radius,int rgb) { UIUtils.drawRound(r.x,r.y,r.width,r.height,radius,color(rgb)); }
    public static void decoration(ClipRect r,float radius,int rgb){UIUtils.drawDecorationRound(r.x,r.y,r.width,r.height,radius,color(rgb));}
    public static void line(float x,float y,float w) { UIUtils.drawRound(x,y,w,1,0,color(0xff252832)); }
    public static void gradient(ClipRect r,float radius,float opacity) {
        Theme t=theme();
        if(t.isVertical())UIUtils.drawGradientVertical(r.x,r.y,r.width,r.height,radius,UiEffects.color(t.getPrimaryGradientRGB(),opacity),UiEffects.color(t.getSecondaryGradientRGB(),opacity));
        else UIUtils.drawGradientHorizontal(r.x,r.y,r.width,r.height,radius,UiEffects.color(t.getPrimaryGradientRGB(),opacity),UiEffects.color(t.getSecondaryGradientRGB(),opacity));
    }
    public static String clean(String value) { return value==null?"":value.replaceAll("(?i)[§&][0-9a-fk-or]", "").replaceAll("[\\p{Cntrl}]", " "); }
    public static String fit(TTFFontRenderer font,String text,float width) {
        String result=clean(text);
        if(font.getWidth(result)<=width)return result;
        while(!result.isEmpty()&&font.getWidth(result+"...")>width)result=result.substring(0,result.length()-1);
        return result.isEmpty()?"":result+"...";
    }
    public static void text(String text,float x,float y,int size,int color) { font(size).drawString(clean(text),x,y,color); }
    public static void center(String text,ClipRect r,int size,int color) {
        TTFFontRenderer f=medium(size);String shown=fit(f,text,r.width-12);
        f.drawString(shown,r.x+(r.width-f.getWidth(shown))/2,r.y+(r.height-f.getHeight(shown))/2,color);
    }
    public static void button(ClipRect r,String label,int mouseX,int mouseY,boolean primary,boolean enabled,boolean danger) {
        boolean hover=enabled&&r.contains(mouseX,mouseY);
        int edge=primary?accent():danger?0xff61343f:hover?0xff525765:0xff303440;
        me.youngjibbit.clickgui.utils.GuiStyle.panel(r.x,r.y,r.width,r.height,6,!enabled?.05f:primary?1:hover?.6f:.15f,color(primary?Theme.interpolateRGB(0xff161822,accent(),hover?.36f:.23f):hover?0xff292d38:0xff1b1e27));
        center(label,r,17,!enabled?FAINT:danger?RED:TEXT);
    }
    public static String provider(Account account) {
        switch(account.getProvider()) { case OFFLINE:return "Offline";case MICROSOFT:return "Microsoft";default:return "TheAltening"; }
    }
    public static String name(Account account) { return account.getProvider()==Account.Provider.THEALTENING?"TheAltening account":clean(account.getName()); }
    public static void avatar(Account account,ClipRect r,boolean selected) {
        decoration(r,9,Theme.interpolateRGB(0xff242736,accent(),selected?.26f:.12f));
        String name=name(account).trim();String initials=name.isEmpty()?"?":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT);
        center(initials,r,r.width>40?28:21,Theme.interpolateRGB(TEXT,theme().getSecondaryGradientRGB(),.28f));
    }
    public void window(ClipRect r,Runnable content) {
        try(RenderState state=new RenderState()) {
            state.screenSpace();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
            net.minecraft.client.renderer.GlStateManager.disableBlend();
            me.youngjibbit.gui.MainMenu.drawBackdrop(theme());
            // One shared surface captures blur/glow once; rows and controls remain sharp.
            surface.panel(r.x,r.y,r.width,r.height,11,theme(),true,true,true,7,.88f,.38f,()->{
                round(new ClipRect(r.x+1,r.y+1,r.width-2,r.height-2),10,0xe912141c);
                content.run();
            });
        }
    }
    public void close() { surface.close(); }
}
