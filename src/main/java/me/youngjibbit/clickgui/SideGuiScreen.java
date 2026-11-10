package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.gui.UiSpring;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** A small navigation hub. Module editing belongs exclusively to the ClickGUI. */
public class SideGuiScreen extends GuiScreen {
    private static final String[] TITLES={"Configs","Themes","Client settings","Accounts"};
    private static final String[] DETAILS={"Save and restore your setup","Palettes and visual identity",
            "Appearance, motion and HUD","Manage your local profiles"};
    private static final String[] ICONS={"save","palette","settings","accounts"};
    private final GuiScreen parent;
    private final ArrayListSurface surface=new ArrayListSurface();
    private final UiSpring[] motion=new UiSpring[6];
    private float x,y,w,h;
    private int navFocus=1,pressed=-1;
    private long lastFrame=System.nanoTime();

    public SideGuiScreen(GuiScreen parent) {
        this.parent=parent;
        for(int i=0;i<motion.length;i++)motion[i]=new UiSpring();
    }
    @Override public void initGui() {
        layout();pressed=-1;lastFrame=System.nanoTime();
        for(UiSpring spring:motion)spring.reset();
    }
    private void layout() {
        w=Math.min(550,Math.max(1,width-20));h=Math.min(350,Math.max(1,height-20));
        x=(width-w)/2;y=(height-h)/2;
    }
    protected ClipRect window(){return new ClipRect(x,y,w,h);}
    private int columns(){return w>=280?2:1;}
    protected ClipRect tabBounds(int id) {
        if(id==5)return new ClipRect(x+w-76,y+10,62,24);
        if(id<1||id>4)return new ClipRect(0,0,0,0);
        int cols=columns(),row=(id-1)/cols,column=(id-1)%cols;
        float top=h<240?58:78,gap=8,cell=(w-28-(cols-1)*gap)/cols;
        float pitch=(Math.max(32,h-top-33)+gap)/((4+cols-1)/cols);
        return new ClipRect(x+14+column*(cell+gap),y+top+row*pitch,cell,Math.max(18,pitch-gap));
    }
    private TTFFontRenderer font(){return ClickControlStyle.font();}
    private void text(String value,float px,float py,float limit,int color) {
        font().drawString(ClickControlStyle.fit(font(),value,limit),px,py,color);
    }
    @Override public void drawScreen(int mx,int my,float partial) {
        UiEffects.beginFrame();layout();
        long now=System.nanoTime();float dt=Math.min(.05f,Math.max(0,(now-lastFrame)/1e9f));lastFrame=now;
        Theme theme=Theme.resolve(Theme.getDefaultTheme());int ink=LegacyUiStyle.ink(theme);
        try(RenderState frame=new RenderState()) {
            frame.screenSpace();
            if(mc.theWorld==null)MainMenu.drawBackdrop(theme);
            surface.panel(x,y,w,h,8,theme,true,true,true,7,.94f,.65f,()->{
                try(GuiClip clip=GuiClip.push(window())) {
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString("Library",x+16,y+13,ink);
                    if(h>=240)text("Your client, organized.",x+16,y+38,w-32,LegacyUiStyle.MUTED);
                    LegacyUiStyle.rule(x+16,y+(h<240?44:62),w-32,theme);
                    for(int id=1;id<=5;id++) {
                        ClipRect hit=tabBounds(id);
                        motion[id].stepForInput(dt,hit.contains(mx,my),navFocus==id,pressed==id,true,ClientSettings.current());
                        ClipRect r=GuiStyle.buttonBounds(hit,motion[id]);
                        LegacyUiStyle.card(r.x,r.y,r.width,r.height,theme,motion[id].keyboardFocused(),hit.contains(mx,my));
                        GuiStyle.feedback(r,motion[id]);
                        if(id==5) {
                            LegacyUiStyle.icon("back",r.x+8,r.y+7,10,ink);
                            text("Back",r.x+23,r.y+7,r.width-29,ink);continue;
                        }
                        boolean large=r.height>=60;
                        float iconSize=large?24:18,iconY=large?r.y+13:r.y+(r.height-iconSize)/2;
                        LegacyUiStyle.icon(ICONS[id-1],r.x+12,iconY,iconSize,theme.getPrimaryGradientRGB());
                        float labelX=r.x+12+iconSize+10;
                        text(TITLES[id-1],labelX,large?r.y+16:r.y+(r.height-10)/2,r.width-(labelX-r.x)-12,ink);
                        if(large)text(DETAILS[id-1],r.x+12,r.y+r.height-22,r.width-24,LegacyUiStyle.MUTED);
                    }
                    if(h>=180)text("Module settings are in the ClickGUI",x+16,y+h-18,w-32,LegacyUiStyle.FAINT);
                }
            });
        }
    }
    protected void open(GuiScreen screen){mc.displayGuiScreen(screen);}
    /** Existing destination IDs stay stable; id 0 no longer opens a module browser. */
    protected void activate(int id) {
        switch(id) {
            case 1:open(new ClickLibraryScreen(this,false));break;
            case 2:open(new ClickLibraryScreen(this,true));break;
            case 3:open(new SettingsMenu(this));break;
            case 4:open(new GuiAltManager(null,this));break;
            case 5:open(parent);break;
            default:break;
        }
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException {
        if(button!=0)return;layout();
        for(int id=1;id<=5;id++)if(tabBounds(id).contains(mx,my)) {
            navFocus=id;pressed=id;activate(id);return;
        }
    }
    @Override protected void mouseReleased(int mx,int my,int button){pressed=-1;}
    protected boolean shift(){return Keyboard.isCreated()&&isShiftKeyDown();}
    @Override protected void keyTyped(char c,int key)throws IOException {
        if(key==Keyboard.KEY_ESCAPE){open(parent);return;}
        if(key==Keyboard.KEY_TAB){navFocus=Math.floorMod(navFocus-1+(shift()?-1:1),5)+1;return;}
        if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT) {
            navFocus=Math.floorMod(navFocus-1+(key==Keyboard.KEY_LEFT?-1:1),5)+1;return;
        }
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN) {
            navFocus=Math.floorMod(navFocus-1+(key==Keyboard.KEY_UP?-columns():columns()),5)+1;return;
        }
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)activate(navFocus);
    }
    @Override public void onGuiClosed(){surface.close();pressed=-1;for(UiSpring spring:motion)spring.reset();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
