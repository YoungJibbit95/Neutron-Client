package me.youngjibbit.gui;

import me.youngjibbit.theme.ThemeMode;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.gui.NeutronVisualTokens;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import java.awt.Color;
import java.io.IOException;
import static org.lwjgl.opengl.GL11.*;

/** One responsive geometry for paint, focus, hover and activation. No account/auth implementation here. */
public class MainMenu extends GuiScreen {
    private static final String[] ACTIONS={"Singleplayer","Multiplayer","Account Manager","Settings","Exit"};
    private static final int[] FOCUS_ORDER={0,1,2,3,8,4,5,6,7};
    private final ArrayListSurface surface=new ArrayListSurface();
    private final float[] hover=new float[9];
    private final MenuSpring[] springs=new MenuSpring[9];
    private int pressed=-1;
    public MainMenu(){for(int i=0;i<9;i++)springs[i]=new MenuSpring();}
    private int focus;
    private boolean keyboardFocus;
    private float panelX,panelY,panelWidth,panelHeight,header,buttonHeight;
    private int paintMouseX,paintMouseY;
    private long lastFrame=System.nanoTime();
    private static ShaderUtil backgroundShader;
    public static void releaseResources(){if(backgroundShader!=null){backgroundShader.close();backgroundShader=null;}}

    @Override public void initGui(){buttonList.clear();clearMotion();layout();lastFrame=System.nanoTime();focus=Math.max(0,Math.min(8,focus));}
    private void clearMotion(){pressed=-1;for(int i=0;i<9;i++){hover[i]=0;springs[i].reset();}}
    private void layout(){panelWidth=Math.min(two()?(creative()?360:336):282,Math.max(1,width-24));panelHeight=Math.min(two()?(creative()?320:360):330,Math.max(1,height-16));panelX=(width-panelWidth)/2;panelY=(height-panelHeight)/2;header=Math.max(54,panelHeight*.27f);buttonHeight=Math.max(8,(panelHeight-header-61)/(creative()?4:6));}
    private boolean two(){return ThemeMode.APPROACH_TWO.equals(ThemeMode.selected());}
    private boolean modern(){return two()&&!creative();}
    private boolean creative(){return two()&&creativePresentation();}
    protected boolean creativePresentation(){return ClientSettings.creativeScreenStyle();}
    protected ClipRect actionBounds(int id){
        if(creative()&&(id<5||id==8)){
            float gap=6,bw=panelWidth-32;
            if(id<4)return new ClipRect(panelX+16+(id%2)*(bw+gap)/2,
                    panelY+header+(id/2)*(buttonHeight+4),(bw-gap)/2,buttonHeight);
            return new ClipRect(panelX+16,panelY+header+(id==8?2:3)*(buttonHeight+4),bw,buttonHeight);
        }
        if(id<5||id==8){int row=id==8?4:id==4?5:id;float bw=Math.min(two()?230:176,panelWidth-32);return new ClipRect(panelX+(panelWidth-bw)/2,panelY+header+row*(buttonHeight+4),bw,buttonHeight);}
        float w=(panelWidth-40)/3;return new ClipRect(panelX+16+(id-5)*(w+4),panelY+panelHeight-28,w,18);
    }
    private static boolean hit(ClipRect r,float x,float y){if(!r.contains(x,y))return false;float radius=UiEffects.radius(4,r.width,r.height);float dx=Math.max(Math.abs(x-r.x-r.width/2)-(r.width/2-radius),0),dy=Math.max(Math.abs(y-r.y-r.height/2)-(r.height/2-radius),0);return dx*dx+dy*dy<=radius*radius;}
    protected int focusedAction(){return focus;}
    /** One dt for all eight controls; Reduced Motion needs wall dt even when the UI phase is frozen. */
    void advanceMotion(float dt,int mouseX,int mouseY){
        UiEffects.Options options=UiEffects.options();MenuMotion preset=MenuMotion.resolve(ClientSettings.current());
        for(int i=0;i<9;i++){
            float target=i==pressed?-.45f:hit(actionBounds(i),mouseX,mouseY)?1:keyboardFocus&&i==focus?.55f:0;
            hover[i]=springs[i].step(target,dt,options.motion,options.reduced,preset);
        }
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        UiEffects.beginFrame();layout();long now=System.nanoTime();float dt=(now-lastFrame)/1e9f;lastFrame=now;
        paintMouseX=mouseX;paintMouseY=mouseY;
        Theme theme=Theme.resolve(Theme.getDefaultTheme());
        advanceMotion(dt,mouseX,mouseY);
        try(RenderState state=new RenderState()){
            state.screenSpace();glDisable(GL_BLEND);GlStateManager.disableBlend();
            drawBackdrop(theme);
            surface.panel(panelX,panelY,panelWidth,panelHeight,two()?NeutronVisualTokens.RADIUS_FRAME:9,theme,true,UiEffects.blurEnabled(),UiEffects.glowEnabled(),two()?4:7,two()?.98f:.82f,two()?.22f:.6f,()->drawActions(theme));
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
        TTFFontRenderer logo=font(panelHeight<260?"DmSansMedium 44":"DmSansMedium 64"),small=font("Product-Sans-Regular 18"),button=font("DmSansMedium 22");
        MenuMotion preset=MenuMotion.resolve(ClientSettings.current());
        String name=Pulsar.INSTANCE.getName(),version=Pulsar.INSTANCE.getVersion()+" \u00b7 "+Pulsar.INSTANCE.getAuthor();
        TextEffects.direct(theme,()->logo.drawString(name,panelX+(panelWidth-logo.getWidth(name))/2,panelY+10,-1));
        small.drawString(version,panelX+(panelWidth-small.getWidth(version))/2,panelY+header-13,two()?NeutronVisualTokens.MUTED:new Color(165,169,180).getRGB());
        if(two()&&creative()){
            // Dynamic expressive accent above the action cards.
            float pulse=UiEffects.time()*1.2f;
            UIUtils.drawDecorationRound(panelX+14,panelY+header-4,Math.max(1,panelWidth-28),1,.5f,
                    NeutronVisualTokens.accent(theme,(int)(120+80*Math.abs((float)Math.sin(pulse*.5f)))));
        }
        // The base contour never moves. Only its inset and label lift/scale inside it.
        for(int i=0;i<9;i++){
            ClipRect r=actionBounds(i);float lift=UiEffects.motion()?preset.lift(hover[i]):0,scale=UiEffects.motion()?preset.scale(hover[i]):1;
            boolean active=keyboardFocus&&i==focus,held=i==pressed;float amount=Math.max(0,Math.min(1,hover[i]));
            int ink=theme.isCustom()?theme.getTextColor().getRGB():Color.WHITE.getRGB();
            float halo=preset.glow(hover[i])*(theme.isCustom()?theme.getGlowStrength():1);
            boolean rowHover=r.contains(paintMouseX,paintMouseY);
            if(two()){
                UIUtils.drawRound(r.x,r.y,r.width,r.height,two()?NeutronVisualTokens.RADIUS_CONTROL:4,
                        creative()?NeutronVisualTokens.alpha(NeutronVisualTokens.CARD.getRGB(),245):NeutronVisualTokens.CARD);
                if(rowHover){
                    UIUtils.drawRound(r.x,r.y,r.width,r.height,two()?NeutronVisualTokens.RADIUS_CONTROL:4,
                            creative()?NeutronVisualTokens.alpha(NeutronVisualTokens.CARD_HOVER.getRGB(),240):NeutronVisualTokens.CARD_HOVER);
                }
                if(active){
                    UIUtils.drawRound(r.x+4,r.y+4,r.width-8,r.height-8,two()?NeutronVisualTokens.RADIUS_CONTROL:2,
                            NeutronVisualTokens.accent(theme,90));
                }
            }else{
                UIUtils.drawRound(r.x,r.y,r.width,r.height,4,theme.isCustom()?theme.getBackground():new Color(16,20,29,245));
            }
            if(halo>0)UiEffects.drawGlow(r.x+3,r.y+3,r.width-6,r.height-6,4,theme.getPrimaryGradientRGB(),halo,2,2);
            if(active){
                // An inset bracket survives Global Border/Glow OFF and is not a color-only focus cue.
                UIUtils.drawRound(r.x+5,r.y+r.height/2-4,2,8,1,new Color(ink,true));
                UIUtils.drawRound(r.x+r.width-7,r.y+r.height/2-4,2,8,1,new Color(ink,true));
            }
            if(held)UIUtils.drawRound(r.x+12,r.y+r.height-4,r.width-24,1,0,new Color(ink,true));
            String text;
            if(i==8)text="Library";
            else if(i<5)text=ACTIONS[i];
            else{
                boolean enabled=i==5?UiEffects.options().motion:i==6?UiEffects.blurEnabled():UiEffects.glowEnabled();
                text=(i==5?"Motion":i==6?"Blur":"Glow")+": "+(enabled?"on":"off");
                if(!enabled)ink=Theme.interpolateRGB(ink,theme.getBackground().getRGB(),.35f);
            }
            drawLabel(i<5||i==8?button:small,text,r,lift,scale,Theme.interpolateRGB(ink,theme.getPrimaryGradientRGB(),amount*.32f));
        }
        if(width>480){String hint="Tab to navigate \u00b7 Enter to select";small.drawString(hint,12,height-18,two()?NeutronVisualTokens.FAINT:new Color(180,185,199).getRGB());}
    }
    private static void drawLabel(TTFFontRenderer font,String text,ClipRect r,float lift,float scale,int ink){
        float w=font.getWidth(text),h=font.getHeight(text);
        float fit=Math.min(scale,Math.min((r.width-22)/Math.max(1,w),(r.height-6)/Math.max(1,h)));
        fit=Math.max(.1f,fit);
        float y=Math.max(r.y+2,Math.min(r.y+r.height-h*fit-2,r.y+(r.height-h*fit)/2-lift));
        glPushMatrix();try{glTranslatef(r.x+r.width/2,y,0);glScalef(fit,fit,1);font.drawString(text,-w/2,0,ink);}finally{glPopMatrix();}
    }
    @Override public void handleMouseInput() throws IOException {
        keyboardFocus = false;
        super.handleMouseInput();
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{
        if(button!=0)return;keyboardFocus=false;layout();for(int i=0;i<9;i++)if(hit(actionBounds(i),mouseX,mouseY)){focus=i;pressed=i;return;}
    }
    @Override protected void mouseReleased(int x,int y,int button){
        if(button==0&&pressed>=0){int action=pressed;pressed=-1;if(hit(actionBounds(action),x,y))try{activate(action);}catch(IOException e){throw new IllegalStateException(e);}}
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        pressed=-1;keyboardFocus=true;
        boolean shift=shiftDown();
        if(key==Keyboard.KEY_TAB){moveFocus(shift?-1:1);return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_LEFT){moveFocus(-1);return;}
        if(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_RIGHT){moveFocus(1);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){activate(focus);return;}
        if(key==Keyboard.KEY_ESCAPE){focus=0;return;}
    }
    protected boolean shiftDown(){return Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));}
    private void moveFocus(int direction){int index=0;for(int i=0;i<FOCUS_ORDER.length;i++)if(FOCUS_ORDER[i]==focus)index=i;focus=FOCUS_ORDER[Math.floorMod(index+direction,FOCUS_ORDER.length)];}
    protected void activate(int id)throws IOException{
        switch(id){case 0:mc.displayGuiScreen(new GuiSelectWorld(this));break;case 1:mc.displayGuiScreen(new GuiMultiplayer(this));break;
            case 2:mc.displayGuiScreen(new GuiAltManager(null,this));break;case 3:mc.displayGuiScreen(new SettingsMenu(this));break;case 4:mc.shutdown();break;case 8:mc.displayGuiScreen(new me.youngjibbit.clickgui.SideGuiScreen(this));break;
            case 5:if(ClientSettings.current()!=null)ClientSettings.current().animations.setValue(!ClientSettings.current().animations.getValue());break;case 6:if(ClientSettings.current()!=null)ClientSettings.current().blur.setValue(!ClientSettings.current().blur.getValue());break;case 7:if(ClientSettings.current()!=null)ClientSettings.current().glow.setValue(!ClientSettings.current().glow.getValue());break;default:break;}
    }
    @Override public void onGuiClosed(){clearMotion();focus=0;keyboardFocus=false;surface.close();super.onGuiClosed();}
    private static TTFFontRenderer font(String name){return Pulsar.INSTANCE.getFontManager().getFont(name);}
    private static void color(ShaderUtil shader,String name,int rgb){shader.setUniformf(name,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);}
}
