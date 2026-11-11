package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.GuiTransitions;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Real SideGUI and GPU transition consumers under the existing isolated pbuffer fixture. */
public final class NeutronUiRenderCheck {
    static final File OUT=new File("neutron-ui-preview");
    static class Library extends SideGuiScreen {
        GuiScreen opened;
        Library(){super(new MainMenu());}
        @Override protected void open(GuiScreen screen){opened=screen;}
        void key(int key)throws Exception{keyTyped(' ',key);}
    }
    public static void run(Minecraft mc)throws Exception{
        OUT.mkdirs();ModuleManager old=Pulsar.INSTANCE.getMm();FontManager oldFonts=Pulsar.INSTANCE.fontManager;
        ModuleManager modules=new ModuleManager();set(Pulsar.INSTANCE,"mm",modules);Pulsar.INSTANCE.fontManager=new FontManager();
        ClientSettings c=ClientSettings.current();c.animations.setValue(false);c.surface.setValue("Liquid Glass");
        Theme oldTheme=Theme.getDefaultTheme();Theme.setDefaultTheme(Theme.Cotton_candy);
        try{
            for(int[] size:new int[][]{{960,640,2},{640,360,2},{1101,733,3}}){
                mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);
                ScaledResolution sr=new ScaledResolution(mc);Library library=new Library();library.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
                // Minecraft's resize helper unbinds the new FBO. A real GUI frame
                // starts on that main target, not on the context's default drawable.
                clear(.05f,.06f,.08f);
                library.drawScreen(-1,-1,0);save(mc,"library-"+size[0]+".png");
                if(size[0]==960){
                    library.key(org.lwjgl.input.Keyboard.KEY_DOWN);library.drawScreen(-1,-1,0);save(mc,"library-keyboard-settings.png");
                    library.key(org.lwjgl.input.Keyboard.KEY_RETURN);
                    require(library.opened instanceof me.youngjibbit.gui.clientsettings.SettingsMenu,"Hub keyboard navigation opens actual Client settings");
                    library.opened.onGuiClosed();
                }
                require(library.tabBounds(0).isEmpty(),"Library must not retain the module browser");
                for(int id=1;id<=6;id++) {
                    me.youngjibbit.clickgui.utils.ClipRect card=library.tabBounds(id),window=library.window();
                    require(card.width==card.intersect(window).width&&card.height==card.intersect(window).height,"Every Library destination stays inside its window");
                }
                library.onGuiClosed();
            }
            mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);
            c.animations.setValue(true);c.reducedMotion.setValue(false);
            // Navigation snapshots the main target; resize helpers leave the pbuffer bound.
            mc.getFramebuffer().bindFramebuffer(true);
            glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            MainMenu root=new MainMenu();Library target=new Library();
            GuiTransitions.beforeChange(root,target);int texture=(Integer)get(GuiTransitions.class,"texture");require(texture!=0&&glIsTexture(texture)&&(Boolean)get(GuiTransitions.class,"active"),"Transition did not own an active GPU snapshot");
            long began=System.nanoTime();
            Method drawAt=GuiTransitions.class.getDeclaredMethod("drawAt",long.class);drawAt.setAccessible(true);
            require(GuiTransitions.blocksPointerInput(target),"Moving destination hit regions must not accept pointer events");
            clear(0,0,1);drawAt.invoke(null,began);
            require((capture(mc).getRGB(480,320)&0xffffff)==0xff0000,"First destination frame must retain outgoing page, including slow initialization");
            glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glEnable(GL_SCISSOR_TEST);glScissor(3,4,5,6);GL13.glActiveTexture(GL13.GL_TEXTURE1);GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            drawAt.invoke(null,began+110_000_000L);
            require(glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_SCISSOR_TEST)&&glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE1,"Transition leaked caller state");
            require(glGetInteger(GL14.GL_BLEND_EQUATION)==GL14.GL_FUNC_REVERSE_SUBTRACT,"Transition leaked blend equation");
            glDisable(GL_SCISSOR_TEST);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            BufferedImage half=capture(mc);save(mc,"transition-half.png");
            require((half.getRGB(240,320)&0xffffff)==0xff0000&&(half.getRGB(720,320)&0xffffff)==0x0000ff,
                    "Mid-transition must show distinct outgoing/incoming pages side by side, not a dissolving overlay");
            clear(0,0,1);drawAt.invoke(null,began+220_000_000L);
            require(!(Boolean)get(GuiTransitions.class,"active")&&!GuiTransitions.blocksPointerInput(target),"Completed transition retained active work/input lock");
            require((capture(mc).getRGB(480,320)&0xffffff)==0x0000ff,"Completed transition must retain live destination");
            // Back navigation reverses both pages, rather than replaying a right-hand entry.
            GuiTransitions.beforeChange(target,root);clear(1,0,0);drawAt.invoke(null,began+300_000_000L);
            clear(1,0,0);drawAt.invoke(null,began+410_000_000L);half=capture(mc);save(mc,"transition-back-half.png");
            require((half.getRGB(240,320)&0xffffff)==0xff0000&&(half.getRGB(720,320)&0xffffff)==0x0000ff,"Back must enter from the left and move the old page right");
            // Interrupting uses the exact displayed composite, not an offscreen old screen.
            GuiTransitions.beforeChange(root,target);clear(0,1,0);drawAt.invoke(null,began+420_000_000L);
            BufferedImage interrupted=capture(mc);
            require(java.util.Arrays.equals(half.getRGB(0,0,960,640,null,0,960),interrupted.getRGB(0,0,960,640,null,0,960)),"Rapid navigation lost the currently visible frame");
            c.reducedMotion.setValue(true);GuiTransitions.draw();require(!(Boolean)get(GuiTransitions.class,"active")&&!GuiTransitions.blocksPointerInput(target),"Reduced motion during a slide must release input");
            c.reducedMotion.setValue(true);GuiTransitions.beforeChange(new MainMenu(),new Library());require(!(Boolean)get(GuiTransitions.class,"active"),"Reduced motion captured transition");
            c.reducedMotion.setValue(false);GuiTransitions.beforeChange(new MainMenu(),new Library());mc.displayWidth--;GuiTransitions.draw();require(!(Boolean)get(GuiTransitions.class,"active"),"Resize reused an invalid snapshot");mc.displayWidth++;
            c.reducedMotion.setValue(false);
            visualNavigation(mc,drawAt);
            checkUndimmedWorldNavigation(mc,drawAt,c);
            int incoming=(Integer)get(GuiTransitions.class,"incomingTexture");
            GuiTransitions.releaseResources();require(!glIsTexture(texture)&&!glIsTexture(incoming),"Transition textures survived shutdown");
            require(glGetError()==GL_NO_ERROR,"Neutron UI GL error");
            System.out.println("PASS: Library hub layout/navigation; continuous forward/back slide pixels, first/end frames, interruption, input guard, hostile GL state, reduced motion, resize, two-texture release, actual Library/Config/Settings composites");
        }finally{GuiTransitions.releaseResources();Pulsar.INSTANCE.fontManager.close();Pulsar.INSTANCE.fontManager=oldFonts;set(Pulsar.INSTANCE,"mm",old);Theme.setDefaultTheme(oldTheme);}
    }
    private static void clear(float r,float g,float b){Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(r,g,b,1);glClear(GL_COLOR_BUFFER_BIT);}
    private static void checkUndimmedWorldNavigation(Minecraft mc,Method drawAt,ClientSettings settings)throws Exception {
        // Only world presence is supplied; no world simulation, networking or player is invoked.
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        net.minecraft.client.multiplayer.WorldClient oldWorld=mc.theWorld;
        String surface=settings.surface.getValue();boolean blur=settings.blur.getValue(),glow=settings.glow.getValue();
        GuiScreen previous=null;
        try {
            mc.theWorld=(net.minecraft.client.multiplayer.WorldClient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);
            settings.surface.setValue("Standard");settings.blur.setValue(false);settings.glow.setValue(false);
            mc.getFramebuffer().bindFramebuffer(true);clear(.25f,.4f,.6f);
            int background=capture(mc).getRGB(2,2);
            ScaledResolution sr=new ScaledResolution(mc);
            for(int navigation=0;navigation<12;navigation++) {
                GuiScreen screen;
                switch(navigation%4) {
                    case 0:screen=new SideGuiScreen(null);break;
                    case 1:screen=new ClickLibraryScreen(null,false);break;
                    case 2:screen=new me.youngjibbit.gui.clientsettings.SettingsMenu(null);break;
                    default:screen=new ThemeEditorScreen(null,me.youngjibbit.theme.ThemeCatalog.global(),me.youngjibbit.theme.ThemeRuntime::select);
                }
                if(previous!=null) { GuiTransitions.beforeChange(previous,screen);previous.onGuiClosed(); }
                screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
                long started=System.nanoTime();
                int frames=navigation%3==2?2:3; // Interrupt every third slide.
                for(int frame=0;frame<frames;frame++) {
                    mc.getFramebuffer().bindFramebuffer(true);clear(.25f,.4f,.6f);
                    screen.drawScreen(-1,-1,0);drawAt.invoke(null,started+frame*110_000_000L);
                    BufferedImage rendered=capture(mc);
                    for(int x:new int[]{2,mc.displayWidth/2,mc.displayWidth-3})for(int y:new int[]{2,mc.displayHeight-3})
                        require(rendered.getRGB(x,y)==background,"Full-screen tint accumulated during navigation "+navigation+", frame "+frame);
                }
                if(navigation==2||navigation==11)save(mc,"world-navigation-"+navigation+".png");
                previous=screen;
            }
            System.out.println("WORLD_BACKDROP: unchanged pixels outside actual Library/Config/Settings/Theme windows through 12 forward/interrupted navigations");
        } finally {
            if(previous!=null)previous.onGuiClosed();mc.theWorld=oldWorld;
            settings.surface.setValue(surface);settings.blur.setValue(blur);settings.glow.setValue(glow);
        }
    }
    private static void visualNavigation(Minecraft mc,Method drawAt)throws Exception{
        Library library=new Library();
        GuiScreen configs=new ClickLibraryScreen(library,false);
        GuiScreen settings=new me.youngjibbit.gui.clientsettings.SettingsMenu(library);
        GuiScreen[] from={library,configs,library,settings},to={configs,library,settings,library};
        String[] names={"library-config","config-library","library-settings","settings-library"};
        for(int i=0;i<from.length;i++){
            ScaledResolution sr=new ScaledResolution(mc);from[i].setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
            clear(.05f,.06f,.08f);from[i].drawScreen(-1,-1,0);GuiTransitions.beforeChange(from[i],to[i]);from[i].onGuiClosed();
            to[i].setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
            long start=System.nanoTime();
            for(int step=0;step<=2;step++){
                clear(.05f,.06f,.08f);to[i].drawScreen(-1,-1,0);drawAt.invoke(null,start+step*110_000_000L);
                save(mc,"navigation-"+names[i]+"-"+step+".png");
            }
            to[i].onGuiClosed();
        }
    }
    static Object get(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    static BufferedImage capture(Minecraft mc){require(glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==mc.getFramebuffer().framebufferObject,"UI capture must read the actual Minecraft framebuffer");glFinish();ByteBuffer bytes=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}return image;}
    static void save(Minecraft mc,String name)throws Exception{ImageIO.write(capture(mc),"png",new File(OUT,name));}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
