package me.youngjibbit.gui;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.utils.font.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
/** Uses actual init/draw/click/key entry points. Routes are intercepted to avoid launching accounts/worlds/shutdown. */
public final class MainMenuCheck {
    static class Screen extends MainMenu {
        int calls,last=-1;
        @Override protected void activate(int id)throws java.io.IOException{if(id<5){calls++;last=id;}else super.activate(id);}
        void click(int x,int y,int b)throws Exception{mouseClicked(x,y,b);mouseReleased(x,y,b);}void key(int code)throws Exception{keyTyped(' ',code);}
    }
    public static void run(Minecraft mc)throws Exception{
        if(Pulsar.INSTANCE.getMm()==null){Field field=Pulsar.class.getDeclaredField("mm");field.setAccessible(true);field.set(Pulsar.INSTANCE,new me.youngjibbit.module.ModuleManager());}
        Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
        Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"DmSansMedium 44","DmSansMedium 25","Product-Sans-Regular 18"});
        springChecks();
        File out=new File("menu-preview");out.mkdirs();Screen screen=new Screen();
        if(Boolean.getBoolean("pulsar.menu.baseline")){mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);screen.setWorldAndResolution(mc,480,320);screen.drawScreen(-1,-1,0);save(mc,new File(out,"menu-before.png"));return;}
        for(int size=0;size<3;size++){
            mc.displayWidth=size==0?960:size==1?640:1101;mc.displayHeight=size==0?640:size==1?480:733;mc.gameSettings.guiScale=size==2?3:2;
            mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);ScaledResolution sr=new ScaledResolution(mc);screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
            screen.drawScreen(-1,-1,0);
            ClipRect corner=screen.actionBounds(0);int previous=screen.calls;screen.click((int)Math.ceil(corner.x),(int)Math.ceil(corner.y),0);require(screen.calls==previous,"Invisible rounded corner activated action");
            for(int i=0;i<8;i++){ClipRect r=screen.actionBounds(i);require(r.x>=0&&r.y>=0&&r.x+r.width<=sr.getScaledWidth()&&r.y+r.height<=sr.getScaledHeight(),"Unreachable menu button");}
            save(mc,new File(out,"menu-"+size+".png"));
            for(int frame=0;frame<3;frame++){
                Field elapsed=me.youngjibbit.utils.render.UiEffects.class.getDeclaredField("elapsed");elapsed.setAccessible(true);elapsed.setDouble(null,frame*8.0);
                screen.drawScreen(-1,-1,0);save(mc,new File(out,"menu-"+size+"-time-"+frame+".png"));
            }
            for(int i=0;i<5;i++){ClipRect r=screen.actionBounds(i);int before=screen.calls;screen.click((int)(r.x+r.width/2),(int)(r.y+r.height/2),1);require(screen.calls==before,"Right click activated action");screen.click((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);require(screen.calls==before+1&&screen.last==i,"Click routing/duplicate action");}
            screen.key(Keyboard.KEY_ESCAPE);screen.key(Keyboard.KEY_TAB);require(screen.focusedAction()==1,"Tab focus");int before=screen.calls;screen.key(Keyboard.KEY_RETURN);require(screen.calls==before+1&&screen.last==1,"Keyboard action");
            screen.key(Keyboard.KEY_ESCAPE);require(screen.focusedAction()==0,"Root Escape closed screen");
            screen.onGuiClosed();screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());screen.drawScreen(-1,-1,0);
        }
        ClipRect first=screen.actionBounds(0);int cx=(int)(first.x+first.width/2),cy=(int)(first.y+first.height/2);
        for(int i=0;i<20;i++)advance(screen,cx,cy);
        save(mc,new File(out,"menu-hover.png"));int calls=screen.calls;
        screen.mouseClicked(cx,cy,0);require(screen.calls==calls,"Pressed activated before release");
        for(int i=0;i<20;i++)advance(screen,cx,cy);
        save(mc,new File(out,"menu-pressed.png"));
        screen.mouseReleased(-1,-1,0);require(screen.calls==calls,"Release outside activated");
        titleGradientChecks(mc,screen,out);
        qualityChecks(mc,screen,out);
        // Toggle all appearance controls through actual click handlers, including a static-background pixel check.
        for(int i=5;i<8;i++){ClipRect r=screen.actionBounds(i);screen.click((int)r.x+4,(int)r.y+5,0);}
        screen.drawScreen(-1,-1,0);BufferedImage a=capture(mc);Thread.sleep(30);screen.drawScreen(-1,-1,0);BufferedImage b=capture(mc);
        require(java.util.Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth())),"Reduced-motion scene still changes");
        for(int i=5;i<8;i++){ClipRect r=screen.actionBounds(i);screen.click((int)r.x+4,(int)r.y+5,0);}screen.onGuiClosed();
        require(glGetError()==GL_NO_ERROR,"Menu GL error");System.out.println("PASS: menu bounds, small/odd sizes and scales, exact click routing, keyboard focus/activation, return/reopen, reduced motion, effect switches");
    }
    static void titleGradientChecks(Minecraft mc,Screen screen,File out)throws Exception{
        me.youngjibbit.module.impl.client.ClientSettings c=me.youngjibbit.module.impl.client.ClientSettings.current();
        me.youngjibbit.utils.Theme oldTheme=me.youngjibbit.utils.Theme.getDefaultTheme();
        String direction=c.direction.getValue(),mode=c.gradientMotion.getValue();boolean sync=c.synchronize.getValue(),motion=c.animations.getValue(),gradients=c.gradients.getValue();
        Field phasesField=me.youngjibbit.utils.render.UiEffects.class.getDeclaredField("phases");phasesField.setAccessible(true);double[] phases=(double[])phasesField.get(null),saved=phases.clone();
        try{
            me.youngjibbit.utils.Theme.setDefaultTheme(me.youngjibbit.utils.Theme.Cotton_candy);c.synchronize.setValue(true);c.animations.setValue(false);c.gradients.setValue(true);
            mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);screen.setWorldAndResolution(mc,480,320);
            for(String axis:new String[]{"Horizontal","Vertical"}){
                c.direction.setValue(axis);c.gradientMotion.setValue("Static");screen.drawScreen(-1,-1,0);
                verifyTitleGradient(mc,screen,capture(mc),axis);save(mc,new File(out,"title-"+axis+".png"));
            }
            c.direction.setValue("Horizontal");c.gradientMotion.setValue("Animated");
            double[] average=new double[2];
            for(int i=0;i<2;i++){phases[0]=i==0?.15:.65;screen.drawScreen(-1,-1,0);average[i]=verifyTitleGradient(mc,screen,capture(mc),"Animated");save(mc,new File(out,"title-animated-"+i+".png"));}
            require(Math.abs(average[0]-average[1])>.12,"Title gradient phase did not change visible glyph colors");
            System.out.println("PASS: visible menu title glyphs follow spatial horizontal/vertical gradients and two animation phases");
        }finally{
            c.direction.setValue(direction);c.gradientMotion.setValue(mode);c.synchronize.setValue(sync);c.animations.setValue(motion);c.gradients.setValue(gradients);System.arraycopy(saved,0,phases,0,phases.length);me.youngjibbit.utils.Theme.setDefaultTheme(oldTheme);
        }
    }
    static double verifyTitleGradient(Minecraft mc,Screen screen,BufferedImage image,String label)throws Exception{
        float[] panel=new float[3];String[] fields={"panelX","panelY","panelWidth"};for(int i=0;i<3;i++){Field f=MainMenu.class.getDeclaredField(fields[i]);f.setAccessible(true);panel[i]=f.getFloat(screen);}
        me.youngjibbit.utils.font.TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 44");String name=Pulsar.INSTANCE.getName();
        int left=(int)((panel[0]+(panel[2]-font.getWidth(name))/2)*2),top=(int)((panel[1]+9)*2),right=left+(int)(font.getWidth(name)*2),bottom=top+(int)(font.getHeight(name)*2);
        me.youngjibbit.utils.Theme theme=me.youngjibbit.utils.Theme.getDefaultTheme();java.awt.Color a=theme.getPrimaryGradientColor(),b=theme.getSecondaryGradientColor();
        double[] first={a.getRed(),a.getGreen(),a.getBlue()},delta={b.getRed()-a.getRed(),b.getGreen()-a.getGreen(),b.getBlue()-a.getBlue()};double norm=0;for(double d:delta)norm+=d*d;
        int count=0;double min=1,max=0,sum=0,error=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
            int rgb=image.getRGB(x,y);double[] value={rgb>>16&255,rgb>>8&255,rgb&255};double t=0;for(int k=0;k<3;k++)t+=(value[k]-first[k])*delta[k];t/=norm;
            double residual=0;for(int k=0;k<3;k++)residual+=Math.pow(value[k]-first[k]-t*delta[k],2);
            if(residual>9||t<0||t>1)continue; // Opaque glyph interiors, excluding antialias edges and backdrop.
            int expected=me.youngjibbit.utils.render.UiEffects.gradientColor(theme,me.youngjibbit.utils.render.UiEffects.Channel.TEXT,(x+.5f)/2,(y+.5f)/2);
            for(int k=0;k<3;k++)error+=Math.abs(value[k]-(expected>>(16-k*8)&255));count++;sum+=t;min=Math.min(min,t);max=Math.max(max,t);
        }
        require(count>150,"Title missing/black: "+label+" opaque glyph pixels="+count);require(error/(count*3)<2,"Title does not follow shared pixel gradient: "+label);require(max-min>(label.equals("Vertical")?.018:.07),"Title collapsed to one sampled color: "+label+" range="+(max-min));return sum/count;
    }
    static void advance(Screen screen,int x,int y)throws Exception{
        Field frame=MainMenu.class.getDeclaredField("lastFrame");frame.setAccessible(true);frame.setLong(screen,System.nanoTime()-16_666_667L);screen.drawScreen(x,y,0);
    }
    static void qualityChecks(Minecraft mc,Screen screen,File out)throws Exception{
        Field field=MainMenu.class.getDeclaredField("backgroundShader");field.setAccessible(true);
        me.youngjibbit.utils.render.ShaderUtil shader=(me.youngjibbit.utils.render.ShaderUtil)field.get(null);
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            state.screenSpace();glDisable(GL_BLEND);
            for(int quality:new int[]{0,2}){
                shader.init();shader.setUniformf("quality",quality);shader.setUniformf("time",8);
                for(int i=0;i<5;i++)me.youngjibbit.utils.render.ShaderUtil.drawQuads();glFinish();
                long start=System.nanoTime();
                for(int i=0;i<30;i++)me.youngjibbit.utils.render.ShaderUtil.drawQuads();glFinish();
                System.out.println("MENU SHADER quality="+quality+" mean ms="+(System.nanoTime()-start)/30e6);
                shader.unload();save(mc,new File(out,"shader-quality-"+quality+".png"));
            }
        }
    }
    static void springChecks(){
        float reference=0;
        for(int fps:new int[]{30,60,144,360}){
            MenuSpring spring=new MenuSpring();float value=0;
            for(int i=0;i<fps;i++)value=spring.step(1,1f/fps,true);
            if(reference!=0)require(Math.abs(reference-value)<.0001f,"Spring depends on FPS");reference=value;
            float reversed=spring.step(0,.00001f,true);require(Math.abs(reversed-value)<.0001f,"Spring reset on interruption");
            require(spring.step(0,.01f,false)==0,"Disabled motion must snap");
        }
    }
    static BufferedImage capture(Minecraft mc){glFinish();ByteBuffer data=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|(data.get(i+2)&255));}return image;}
    static void save(Minecraft mc,File path)throws Exception{ImageIO.write(capture(mc),"png",path);}static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
