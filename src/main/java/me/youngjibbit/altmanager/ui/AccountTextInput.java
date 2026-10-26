package me.youngjibbit.altmanager.ui;

import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.utils.font.TTFFontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Single-line editing with selection, paste, clipping, and a masked password display. */
public final class AccountTextInput {
    private String value="";
    private int cursor,anchor,start;
    public boolean focused;
    private final boolean secret;
    private final int limit;
    private ClipRect bounds=new ClipRect(0,0,0,0);
    public AccountTextInput(boolean secret) { this.secret=secret;limit=secret?2048:320; }
    public String value() { return value; }
    public void value(String value) { this.value=value==null?"":value.substring(0,Math.min(limit,value.length()));cursor=anchor=this.value.length();start=0; }
    public void bounds(ClipRect bounds) { this.bounds=bounds; }
    public ClipRect bounds() { return bounds; }
    public String display() { return secret?String.join("",java.util.Collections.nCopies(value.length(),"*")):value; }
    private void replace(String inserted) {
        int left=Math.min(cursor,anchor),right=Math.max(cursor,anchor);
        String safe=inserted.replaceAll("[\\p{Cntrl}]","");
        safe=safe.substring(0,Math.min(safe.length(),limit-(value.length()-(right-left))));
        value=value.substring(0,left)+safe+value.substring(right);cursor=anchor=left+safe.length();
    }
    public boolean key(char character,int key,boolean ctrl,boolean shift) {
        if(!focused)return false;
        if(ctrl&&key==Keyboard.KEY_A){anchor=0;cursor=value.length();return true;}
        if(ctrl&&key==Keyboard.KEY_V){replace(GuiScreen.getClipboardString());return true;}
        if(ctrl&&(key==Keyboard.KEY_C||key==Keyboard.KEY_X)) {
            if(!secret)GuiScreen.setClipboardString(value.substring(Math.min(anchor,cursor),Math.max(anchor,cursor)));
            if(key==Keyboard.KEY_X)replace("");return true;
        }
        if(key==Keyboard.KEY_BACK||key==Keyboard.KEY_DELETE) {
            if(cursor==anchor)anchor=Math.max(0,Math.min(value.length(),cursor+(key==Keyboard.KEY_BACK?-1:1)));
            replace("");return true;
        }
        int destination=cursor;
        if(key==Keyboard.KEY_HOME)destination=0;
        else if(key==Keyboard.KEY_END)destination=value.length();
        else if(key==Keyboard.KEY_LEFT)destination=ctrl?0:!shift&&cursor!=anchor?Math.min(cursor,anchor):cursor-1;
        else if(key==Keyboard.KEY_RIGHT)destination=ctrl?value.length():!shift&&cursor!=anchor?Math.max(cursor,anchor):cursor+1;
        else if(!ctrl&&character>=32&&character!=127){replace(String.valueOf(character));return true;}
        else return false;
        cursor=Math.max(0,Math.min(value.length(),destination));if(!shift)anchor=cursor;return true;
    }
    public void click(float x,float y) {
        focused=bounds.contains(x,y);if(!focused)return;
        String shown=display();TTFFontRenderer font=AccountUi.font(17);int index=start;
        while(index<shown.length()&&font.getWidth(shown.substring(start,index+1))<x-bounds.x-10)index++;
        cursor=anchor=index;
    }
    public void draw(String placeholder) {
        AccountUi.round(bounds,6,focused?AccountUi.accent():0xff303440);
        AccountUi.round(new ClipRect(bounds.x+1,bounds.y+1,bounds.width-2,bounds.height-2),5,0xff181b24);
        TTFFontRenderer font=AccountUi.font(17);String shown=display();
        start=Math.min(start,cursor);
        while(start<cursor&&font.getWidth(shown.substring(start,cursor))>bounds.width-24)start++;
        try(GuiClip ignored=GuiClip.push(new ClipRect(bounds.x+8,bounds.y+2,bounds.width-16,bounds.height-4))) {
            float y=bounds.y+(bounds.height-font.getHeight("Ag"))/2;
            if(value.isEmpty()&&!focused)font.drawString(placeholder,bounds.x+10,y,AccountUi.FAINT);
            else {
                int left=Math.max(start,Math.min(cursor,anchor)),right=Math.max(start,Math.max(cursor,anchor));
                if(right>left)AccountUi.round(new ClipRect(bounds.x+10+font.getWidth(shown.substring(start,left)),y,
                        font.getWidth(shown.substring(left,right)),font.getHeight("Ag")),2,0x555d74ae);
                font.drawString(shown.substring(start),bounds.x+10,y,AccountUi.TEXT);
            }
            if(focused&&(System.nanoTime()/500_000_000L)%2==0)AccountUi.round(new ClipRect(bounds.x+10+font.getWidth(shown.substring(start,cursor)),y,1,font.getHeight("Ag")),0,AccountUi.TEXT);
        }
    }
}
