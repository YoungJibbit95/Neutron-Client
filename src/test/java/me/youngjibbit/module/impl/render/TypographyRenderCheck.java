package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Real atlas pixels, global-font switching and bounded glow cost; no game bootstrap or personal data. */
public final class TypographyRenderCheck {
    private static final File OUT=new File("typography-preview");
    private static void require(boolean condition,String message){SessionInfoRenderCheck.require(condition,message);}
    private static void clear(){
        SessionInfoRenderCheck.backdrop();
        glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
        GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
    }
    private static BufferedImage text(TTFFontRenderer font,String label,boolean gradient){
        clear();
        if(gradient)TextEffects.direct(Theme.getDefaultTheme(),()->font.drawString(label,30,40,-1));
        else font.drawString(label,30,40,-1);
        return SessionInfoRenderCheck.capture();
    }
    private static int[] bounds(BufferedImage image){
        int left=image.getWidth(),right=-1,top=image.getHeight(),bottom=-1,count=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int rgb=image.getRGB(x,y);
            if((rgb&0xffffff)!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);count++;}
        }
        return new int[]{left,top,right,bottom,count};
    }
    public static void run()throws Exception{
        OUT.mkdirs();
        ModuleManager previous=Pulsar.INSTANCE.getMm();FontManager previousFonts=Pulsar.INSTANCE.fontManager;
        ClientSettings settings=new ClientSettings();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager(java.util.Collections.singletonList(settings)));
        FontManager fonts=new FontManager();Pulsar.INSTANCE.fontManager=fonts;
        try{
            settings.animations.setValue(false);settings.textGlow.setValue("Off");
            SessionInfoRenderCheck.mc.displayWidth=1100;SessionInfoRenderCheck.mc.displayHeight=720;
            SessionInfoRenderCheck.mc.gameSettings.guiScale=2;
            SessionInfoRenderCheck.mc.getFramebuffer().createBindFramebuffer(1100,720);
            String[] labels={"| HitDelayFix","| FastPlace","| AutoTool","| Theme","| Sprint","Session Info","Time: 15m 56s","Kills: 0   Deaths: 0","§lBold §rplain §oitalic §rÄÖÜ 😀"};
            for(String family:settings.fontFamily.getOptions()){
                settings.fontFamily.setValue(family);
                TTFFontRenderer font=fonts.getFont("Product-Sans-Regular 20");
                for(String label:labels){
                    int[] plain=bounds(text(font,label,false)),gradient=bounds(text(font,label,true));
                    require(plain[4]>0&&gradient[4]>0,"Missing text: "+family+" / "+label);
                    require(Math.abs(plain[0]-gradient[0])<=1&&Math.abs(plain[2]-gradient[2])<=1,"Gradient changed text spacing: "+family+" / "+label);
                }
                clear();float y=20;
                for(String label:labels){final float at=y;TextEffects.direct(Theme.getDefaultTheme(),()->font.drawString(label,24,at,-1));y+=22;}
                ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(OUT,"font-"+family.replace(' ','-')+".png"));
            }
            settings.fontFamily.setValue("Original");
            TTFFontRenderer icon=fonts.getFont("icon 23"),original=fonts.getFont("Poppins-Medium 20");
            settings.fontFamily.setValue("Inter");
            require(icon==fonts.getFont("icon 23"),"Global family replaced icon face");
            require(original!=fonts.getFont("Poppins-Medium 20"),"Global family did not update existing lookup");
            require(original==fonts.getLocalFont("Poppins-Medium 20"),"Explicit local font lost");
            TTFFontRenderer font=fonts.getFont("Poppins-Medium 20");
            String sample="Session Info / FastPlace";
            BufferedImage off=text(font,sample,false);
            settings.textGlow.setValue("On");
            BufferedImage on=text(font,sample,false);
            require(bounds(on)[4]>bounds(off)[4],"Text glow produced no halo");
            for(int y=0;y<off.getHeight();y++)for(int x=0;x<off.getWidth();x++)
                if((off.getRGB(x,y)&0xffffff)==0xffffff)require((on.getRGB(x,y)&0xffffff)==0xffffff,"Halo softened sharp glyph core");
            ImageIO.write(off,"png",new File(OUT,"text-glow-off.png"));ImageIO.write(on,"png",new File(OUT,"text-glow-on.png"));
            settings.glow.setValue(false);require(WatermarkCheck.equal(off,text(font,sample,false)),"Global glow off did not suppress text halo");
            settings.glow.setValue(true);settings.textGlowColor.setValue("Theme");
            text(font,sample,true);
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            for(int i=0;i<12;i++)text(font,sample,true);
            require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.maskPasses==0&&RenderDiagnostics.blurPasses==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.glyphUploads==0,"Atlas glow introduced steady allocation or framebuffer/blur passes");
            RenderDiagnostics.enabled=false;
            try(RenderState state=new RenderState()){
                GL13.glActiveTexture(GL13.GL_TEXTURE3);glDisable(GL_BLEND);int program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
                font.drawString(sample,30,40,-1);
                require(glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE3&&!glIsEnabled(GL_BLEND)&&glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,"Text glow leaked GL state");
            }
            settings.textGlow.setValue("Off");
            TextEffects local=new TextEffects();RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            clear();local.draw(font,sample,20,40,-1,-1,0,false,0,true,.8f,3,false);
            require(RenderDiagnostics.blurPasses==0,"Text Glow Off failed to suppress module text halo");
            local.close();RenderDiagnostics.enabled=false;
            require(glGetError()==GL_NO_ERROR,"Typography GL error");
            System.out.println("PASS: reported labels, seven global families, identical plain/gradient extents, icon/local overrides, sharp halo cores, master Off, zero per-label FBO/blur allocation and GL ownership");
        }finally{
            fonts.close();FontInk.releaseResources();Pulsar.INSTANCE.fontManager=previousFonts;
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",previous);UiEffects.invalidate();RenderDiagnostics.enabled=false;
        }
    }
}
