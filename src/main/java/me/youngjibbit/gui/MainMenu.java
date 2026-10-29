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

    @Override public void initGui(){buttonList.clear();clearMotion();layout();lastFrame=System.nanoTime();focus=Math.max(0,Math.min(7,focus));}
    private void clearMotion(){pressed=-1;for(int i=0;i<8;i++){hover[i]=0;springs[i].reset();}}
    private void layout(){panelWidth=Math.min(300,Math.max(1,width*.64f));panelHeight=Math.min(310,Math.max(1,height-16));panelX=width>=300?width-panelWidth-16:(width-panelWidth)/2;panelY=(height-panelHeight)/2;header=panelHeight*0.25f;buttonHeight=(panelHeight-header-35-16-16)/5;}
    protected ClipRect actionBounds(int id){
        if(id<5)return new ClipRect(panelX+16,panelY+header+6+id*(buttonHeight+4),panelWidth-32,buttonHeight);
        float w=(panelWidth-40)/3;return new ClipRect(panelX+16+(id-5)*(w+4),panelY+panelHeight-28,w,18);
    }
    private static boolean hit(ClipRect r,float x,float y){if(!r.contains(x,y))return false;float radius=UiEffects.radius(4,r.width,r.height);float dx=Math.max(Math.abs(x-r.x-r.width/2)-(r.width/2-radius),0),dy=Math.max(Math.abs(y-r.y-r.height/2)-(r.height/2-radius),0);return dx*dx+dy*dy<=radius*radius;}
    protected int focusedAction(){return focus;}
    /** One dt for all eight controls; Reduced Motion needs wall dt even when the UI phase is frozen. */
    void advanceMotion(float dt,int mouseX,int mouseY){
        UiEffects.Options options=UiEffects.options();MenuMotion preset=MenuMotion.resolve(ClientSettings.current());
        for(int i=0;i<8;i++){
            float target=i==pressed?-.45f:hit(actionBounds(i),mouseX,mouseY)?1:i==focus?.55f:0;
            hover[i]=springs[i].step(target,dt,options.motion,options.reduced,preset);
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        UiEffects.beginFrame();layout();long now=System.nanoTime();float dt=(now-lastFrame)/1e9f;lastFrame=now;
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        advanceMotion(dt,mouseX,mouseY);
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
        MenuMotion preset=MenuMotion.resolve(ClientSettings.current());
        String name=Pulsar.INSTANCE.getName(),version=Pulsar.INSTANCE.getVersion()+" \u00b7 "+Pulsar.INSTANCE.getAuthor();
        TextEffects.direct(theme,()->logo.drawString(name,panelX+(panelWidth-logo.getWidth(name))/2,panelY+9,-1));
        small.drawString(version,panelX+(panelWidth-small.getWidth(version))/2,panelY+header-13,new Color(165,169,180).getRGB());
        // The base contour never moves. Only its inset and label lift/scale inside it.
        for(int i=0;i<8;i++){
            ClipRect r=actionBounds(i);float lift=preset.lift(hover[i]),scale=preset.scale(hover[i]);
            boolean active=i==focus,held=i==pressed;float amount=Math.max(0,Math.min(1,hover[i]));
            int ink=theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB();
            float halo=preset.glow(hover[i])*(theme.isCustom()?theme.getGlowStrength():1);
            if(halo>0)UiEffects.drawGlow(r.x+3,r.y+3,r.width-6,r.height-6,4,theme.getPrimaryGradientRGB(),halo,2,2);
            UIUtils.drawRound(r.x,r.y,r.width,r.height,4,theme.isCustom()?theme.getBackground():new Color(16,20,29,245));
            float innerW=Math.min(r.width-4,(r.width-10)*scale),innerH=Math.max(1,r.height-8);
            float innerY=Math.max(r.y+2,Math.min(r.y+r.height-2-innerH,r.y+4-lift));
            Color tint=new Color(Theme.interpolateRGB(ink,theme.getPrimaryGradientRGB(),.65f));
            UIUtils.drawRound(r.x+(r.width-innerW)/2,innerY,innerW,innerH,3,
                    new Color(tint.getRed(),tint.getGreen(),tint.getBlue(),held?12:10+(int)(amount*30)));
            UIUtils.drawRoundOutline(r.x,r.y,r.width,r.height,4,active?1.1f:held?.8f:.45f,new Color(0,0,0,0),
                    UiEffects.accent(theme.getPrimaryGradientRGB(),held?.9f:active?.8f:.25f+amount*.35f));
            if(active){
                // An inset bracket survives Global Border/Glow OFF and is not a color-only focus cue.
                UIUtils.drawRound(r.x+5,r.y+r.height/2-4,2,8,1,new Color(ink,true));
                UIUtils.drawRound(r.x+r.width-7,r.y+r.height/2-4,2,8,1,new Color(ink,true));
            }
            if(held)UIUtils.drawRound(r.x+12,r.y+r.height-4,r.width-24,1,0,new Color(ink,true));
            String text;
            if(i<5)text=ACTIONS[i];
            else{
                boolean enabled=i==5?UiEffects.options().motion:i==6?UiEffects.blurEnabled():UiEffects.glowEnabled();
                text=(i==5?"Motion":i==6?"Blur":"Glow")+": "+(enabled?"on":"off");
                if(!enabled)ink=Theme.interpolateRGB(ink,theme.getBackground().getRGB(),.35f);
            }
            drawLabel(i<5?button:small,text,r,lift,scale,ink);
        }
        String hint="Tab / arrows \u00b7 Enter / Space";small.drawString(hint,panelX+(panelWidth-small.getWidth(hint))/2,panelY+panelHeight-43,new Color(140,144,155).getRGB());
    }
    private static void drawLabel(TTFFontRenderer font,String text,ClipRect r,float lift,float scale,int ink){
        float w=font.getWidth(text),h=font.getHeight(text);
        float fit=Math.min(scale,Math.min((r.width-22)/Math.max(1,w),(r.height-6)/Math.max(1,h)));
        fit=Math.max(.1f,fit);
        float y=Math.max(r.y+2,Math.min(r.y+r.height-h*fit-2,r.y+(r.height-h*fit)/2-lift));
        glPushMatrix();try{glTranslatef(r.x+r.width/2,y,0);glScalef(fit,fit,1);font.drawString(text,-w/2,0,ink);}finally{glPopMatrix();}
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{
        if(button!=0)return;layout();for(int i=0;i<8;i++)if(hit(actionBounds(i),mouseX,mouseY)){focus=i;pressed=i;return;}
    }
    @Override protected void mouseReleased(int x,int y,int button){
        if(button==0&&pressed>=0){int action=pressed;pressed=-1;if(hit(actionBounds(action),x,y))try{activate(action);}catch(IOException e){throw new IllegalStateException(e);}}
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        pressed=-1;
        boolean shift=shiftDown();
        if(key==Keyboard.KEY_TAB){focus=(focus+(shift?7:1))%8;return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_LEFT){focus=(focus+7)%8;return;}
        if(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_RIGHT){focus=(focus+1)%8;return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){activate(focus);return;}
        if(key==Keyboard.KEY_ESCAPE){focus=0;return;}
    }
    protected boolean shiftDown(){return Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));}
    protected void activate(int id)throws IOException{
        switch(id){case 0:mc.displayGuiScreen(new GuiSelectWorld(this));break;case 1:mc.displayGuiScreen(new GuiMultiplayer(this));break;
            case 2:mc.displayGuiScreen(new GuiAltManager());break;case 3:mc.displayGuiScreen(new SettingsMenu(this));break;case 4:mc.shutdown();break;
            case 5:if(ClientSettings.current()!=null)ClientSettings.current().animations.setValue(!ClientSettings.current().animations.getValue());break;case 6:if(ClientSettings.current()!=null)ClientSettings.current().blur.setValue(!ClientSettings.current().blur.getValue());break;case 7:if(ClientSettings.current()!=null)ClientSettings.current().glow.setValue(!ClientSettings.current().glow.getValue());break;default:break;}
    }
    @Override public void onGuiClosed(){clearMotion();focus=0;surface.close();super.onGuiClosed();}
    private static TTFFontRenderer font(String name){return Pulsar.INSTANCE.getFontManager().getFont(name);}
    private static void color(ShaderUtil shader,String name,int rgb){shader.setUniformf(name,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);}
}
