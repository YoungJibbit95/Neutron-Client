package me.youngjibbit.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import java.awt.Color;
import java.io.IOException;
import static org.lwjgl.opengl.GL11.*;

/** One responsive geometry for paint, focus, hover and activation. No account/auth implementation here. */
public class MainMenu extends GuiScreen {
    private static final String[] ACTIONS={"Singleplayer","Multiplayer","Account Manager","Settings","Exit"};
    private final ArrayListSurface surface=new ArrayListSurface();
    private final float[] hover=new float[8];
    private final MenuSpring[] springs=new MenuSpring[8];
    private int pressed=-1;
    public MainMenu(){for(int i=0;i<8;i++)springs[i]=new MenuSpring();}
    private int focus;
    private float panelX,panelY,panelWidth,panelHeight,header,buttonHeight;
    private long lastFrame=System.nanoTime();
    private static ShaderUtil backgroundShader;
    public static void releaseResources(){if(backgroundShader!=null){backgroundShader.close();backgroundShader=null;}}

    @Override public void initGui(){buttonList.clear();pressed=-1;layout();lastFrame=System.nanoTime();focus=Math.max(0,Math.min(7,focus));}
    private void layout(){panelWidth=Math.min(300,Math.max(1,width*.64f));panelHeight=Math.min(310,Math.max(1,height-16));panelX=width>=300?width-panelWidth-16:(width-panelWidth)/2;panelY=(height-panelHeight)/2;header=panelHeight*0.25f;buttonHeight=(panelHeight-header-35-16-16)/5;}
    protected ClipRect actionBounds(int id){
        if(id<5)return new ClipRect(panelX+16,panelY+header+6+id*(buttonHeight+4),panelWidth-32,buttonHeight);
        float w=(panelWidth-40)/3;return new ClipRect(panelX+16+(id-5)*(w+4),panelY+panelHeight-28,w,18);
    }
    private static boolean hit(ClipRect r,float x,float y){if(!r.contains(x,y))return false;float radius=UiEffects.radius(4,r.width,r.height);float dx=Math.max(Math.abs(x-r.x-r.width/2)-(r.width/2-radius),0),dy=Math.max(Math.abs(y-r.y-r.height/2)-(r.height/2-radius),0);return dx*dx+dy*dy<=radius*radius;}
    protected int focusedAction(){return focus;}
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        UiEffects.beginFrame();layout();float dt=Math.min(0.25f,(System.nanoTime()-lastFrame)/1e9f);lastFrame=System.nanoTime();
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        for(int i=0;i<8;i++){float target=i==pressed?-.45f:hit(actionBounds(i),mouseX,mouseY)?1:i==focus?.55f:0;hover[i]=springs[i].step(target,dt,UiEffects.motion());}
        try(RenderState state=new RenderState()){
            state.screenSpace();glDisable(GL_BLEND);GlStateManager.disableBlend();
            drawBackdrop(theme);
            surface.panel(panelX,panelY,panelWidth,panelHeight,9,theme,true,UiEffects.blurEnabled(),UiEffects.glowEnabled(),7,0.82f,0.6f,()->drawActions(theme));
        }
    }
    /** Shared animated scene backdrop for client menus; one lazy program and the UI clock. */
    public static void drawBackdrop(Theme theme){
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        try(RenderState state=new RenderState()){
            state.screenSpace();glDisable(GL_BLEND);GlStateManager.disableBlend();
            if(backgroundShader==null)backgroundShader=new ShaderUtil("Flauxy/Shaders/menuNeutron.frag");ShaderUtil shader=backgroundShader;shader.init();shader.setUniformf("resolution",mc.displayWidth,mc.displayHeight);
            shader.setUniformf("time",UiEffects.time());
            shader.setUniformf("quality",2);
            color(shader,"primary",theme.getPrimaryGradientRGB());color(shader,"secondary",theme.getSecondaryGradientRGB());ShaderUtil.drawQuads();shader.unload();
        }
    }
    private void drawActions(Theme theme){
        TTFFontRenderer logo=font("DmSansMedium 44"),small=font("Product-Sans-Regular 18"),button=font("DmSansMedium 25");
        String name=Pulsar.INSTANCE.getName(),version=Pulsar.INSTANCE.getVersion()+" \u00b7 "+Pulsar.INSTANCE.getAuthor();
        TextEffects.direct(theme,()->logo.drawString(name,panelX+(panelWidth-logo.getWidth(name))/2,panelY+9,-1));
        small.drawString(version,panelX+(panelWidth-small.getWidth(version))/2,panelY+header-13,new Color(165,169,180).getRGB());
        for(int i=0;i<8;i++){
            ClipRect r=actionBounds(i);float lift=Math.max(-.6f,Math.min(1.15f,hover[i]))*1.2f;
            boolean active=i==focus;float amount=Math.max(0,Math.min(1,hover[i]));
            if(i<5){
                UIUtils.drawRound(r.x,r.y,r.width,r.height,4,new Color(18+(int)(amount*12),18+(int)(amount*12),22+(int)(amount*12),245));
                UIUtils.drawRoundOutline(r.x,r.y,r.width,r.height,4,active?1:0.5f,new Color(0,0,0,0),
                        active?UiEffects.accent(theme.getPrimaryGradientRGB(),1):new Color(65+(int)(amount*40),65,75,170));
                String text=ACTIONS[i];button.drawString(text,r.x+(r.width-button.getWidth(text))/2,r.y+(r.height-button.getHeight(text))/2-lift,(theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()));
                if(active)UIUtils.drawRound(r.x+5,r.y+r.height/2-2,3,4,1.5f,theme.getSecondaryGradientColor());
            }else{
                boolean enabled=i==5?UiEffects.options().motion:i==6?UiEffects.blurEnabled():UiEffects.glowEnabled();String text=(i==5?"Motion":i==6?"Blur":"Glow")+": "+(enabled?"on":"off");
                UIUtils.drawRoundOutline(r.x,r.y,r.width,r.height,4,active?1:0.4f,new Color(16,16,20,220),active?UiEffects.accent(theme.getPrimaryGradientRGB(),1):new Color(55,55,65));
                small.drawString(text,r.x+(r.width-small.getWidth(text))/2,r.y+4,enabled?Theme.interpolateRGB((theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB()),theme.getSecondaryGradientRGB(),0.4f):new Color(155,155,166).getRGB());
            }
        }
        String hint="Tab / arrows \u00b7 Enter";small.drawString(hint,panelX+(panelWidth-small.getWidth(hint))/2,panelY+panelHeight-43,new Color(140,144,155).getRGB());
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{
        if(button!=0)return;layout();for(int i=0;i<8;i++)if(hit(actionBounds(i),mouseX,mouseY)){focus=i;pressed=i;return;}
    }
    @Override protected void mouseReleased(int x,int y,int button){
        if(button==0&&pressed>=0){int action=pressed;pressed=-1;if(hit(actionBounds(action),x,y))try{activate(action);}catch(IOException e){throw new IllegalStateException(e);}}
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        boolean shift=Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        if(key==Keyboard.KEY_TAB){focus=(focus+(shift?7:1))%8;return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_LEFT){focus=(focus+7)%8;return;}
        if(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_RIGHT){focus=(focus+1)%8;return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){activate(focus);return;}
        if(key==Keyboard.KEY_ESCAPE){focus=0;return;}
    }
    protected void activate(int id)throws IOException{
        switch(id){case 0:mc.displayGuiScreen(new GuiSelectWorld(this));break;case 1:mc.displayGuiScreen(new GuiMultiplayer(this));break;
            case 2:mc.displayGuiScreen(new GuiAltManager());break;case 3:mc.displayGuiScreen(new SettingsMenu(this));break;case 4:mc.shutdown();break;
            case 5:if(ClientSettings.current()!=null)ClientSettings.current().animations.setValue(!ClientSettings.current().animations.getValue());break;case 6:if(ClientSettings.current()!=null)ClientSettings.current().blur.setValue(!ClientSettings.current().blur.getValue());break;case 7:if(ClientSettings.current()!=null)ClientSettings.current().glow.setValue(!ClientSettings.current().glow.getValue());break;default:break;}
    }
    @Override public void onGuiClosed(){pressed=-1;surface.close();super.onGuiClosed();}
    private static TTFFontRenderer font(String name){return Pulsar.INSTANCE.getFontManager().getFont(name);}
    private static void color(ShaderUtil shader,String name,int rgb){shader.setUniformf(name,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);}
}
