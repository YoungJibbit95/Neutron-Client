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
        Library(){super(new MainMenu());}
        void search(String q){query(q);}
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
                library.drawScreen(-1,-1,0);save(mc,"library-"+size[0]+".png");
                if(size[0]==960){
                    library.key(org.lwjgl.input.Keyboard.KEY_DOWN);library.drawScreen(-1,-1,0);save(mc,"library-keyboard-list.png");
                    library.key(org.lwjgl.input.Keyboard.KEY_RETURN);library.drawScreen(-1,-1,0);save(mc,"library-keyboard-settings.png");
                }
                library.search("glow");library.drawScreen(-1,-1,0);save(mc,"search-glow-"+size[0]+".png");
                library.search("no result exists");library.drawScreen(-1,-1,0);save(mc,"search-empty-"+size[0]+".png");library.onGuiClosed();
            }
            mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);
            c.animations.setValue(true);c.reducedMotion.setValue(false);
            // Navigation snapshots the main target; resize helpers leave the pbuffer bound.
            mc.getFramebuffer().bindFramebuffer(true);
            glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            GuiTransitions.beforeChange(new MainMenu(),new Library());int texture=(Integer)get(GuiTransitions.class,"texture");require(texture!=0&&glIsTexture(texture)&&(Boolean)get(GuiTransitions.class,"active"),"Transition did not own an active GPU snapshot");
            Field started=GuiTransitions.class.getDeclaredField("started");started.setAccessible(true);long began=started.getLong(null);
            Method drawAt=GuiTransitions.class.getDeclaredMethod("drawAt",long.class);drawAt.setAccessible(true);
            glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glEnable(GL_SCISSOR_TEST);glScissor(3,4,5,6);GL13.glActiveTexture(GL13.GL_TEXTURE1);GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            drawAt.invoke(null,began+90_000_000L);
            require(glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_SCISSOR_TEST)&&glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE1,"Transition leaked caller state");
            require(glGetInteger(GL14.GL_BLEND_EQUATION)==GL14.GL_FUNC_REVERSE_SUBTRACT,"Transition leaked blend equation");
            glDisable(GL_SCISSOR_TEST);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            int pixel=capture(mc).getRGB(480,320);save(mc,"transition-half.png");require((pixel>>16&255)>20&&(pixel&255)>100,"Crossfade did not blend old and new frames: "+Integer.toHexString(pixel));
            started.setLong(null,System.nanoTime()-200_000_000L);GuiTransitions.draw();require(!(Boolean)get(GuiTransitions.class,"active"),"Completed transition retained active work");
            c.reducedMotion.setValue(true);GuiTransitions.beforeChange(new MainMenu(),new Library());require(!(Boolean)get(GuiTransitions.class,"active"),"Reduced motion captured transition");
            c.reducedMotion.setValue(false);GuiTransitions.beforeChange(new MainMenu(),new Library());mc.displayWidth--;GuiTransitions.draw();require(!(Boolean)get(GuiTransitions.class,"active"),"Resize reused an invalid snapshot");mc.displayWidth++;
            GuiTransitions.releaseResources();require(!glIsTexture(texture),"Transition texture survived shutdown");
            require(glGetError()==GL_NO_ERROR,"Neutron UI GL error");
            System.out.println("PASS: SideGUI real search/empty layout at small, odd and GUI scales; transition crossfade pixels, hostile GL state, reduced motion, resize and resource release");
        }finally{GuiTransitions.releaseResources();Pulsar.INSTANCE.fontManager.close();Pulsar.INSTANCE.fontManager=oldFonts;set(Pulsar.INSTANCE,"mm",old);Theme.setDefaultTheme(oldTheme);}
    }
    static Object get(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    static BufferedImage capture(Minecraft mc){glFinish();ByteBuffer bytes=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,bytes);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|(bytes.get(i+2)&255));}return image;}
    static void save(Minecraft mc,String name)throws Exception{ImageIO.write(capture(mc),"png",new File(OUT,name));}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
