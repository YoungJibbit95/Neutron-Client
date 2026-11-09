package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import static org.lwjgl.opengl.GL11.*;

/** Shared cached circle geometry and theme phase for cosmetic world effects. */
public final class WorldVisuals {
    public static final int SEGMENTS=64;
    public static final double[] COS=new double[SEGMENTS+1],SIN=new double[SEGMENTS+1];
    private static final int[] palette=new int[SEGMENTS+1];
    static{for(int i=0;i<=SEGMENTS;i++){double a=i*Math.PI*2/SEGMENTS;COS[i]=Math.cos(a);SIN[i]=Math.sin(a);}}
    private WorldVisuals(){}
    public static RenderState begin(){
        RenderState state=new RenderState();Theme theme=Theme.getDefaultTheme();
        UiEffects.Gradient gradient=UiEffects.gradient(theme,UiEffects.Channel.GLOW);
        boolean enabled=UiEffects.options().gradients;
        // Sample the shared phase once in normalized ring coordinates, never project GUI
        // coordinates through the world camera for every vertex. Mirror around the seam.
        for(int i=0;i<=SEGMENTS;i++){float t=2*Math.min(i,SEGMENTS-i)/(float)SEGMENTS;palette[i]=Theme.interpolateRGB(theme.getPrimaryGradientRGB(),theme.getSecondaryGradientRGB(),enabled?gradient.position(t,t,1,1,.5f):0);}
        for(int unit=2;unit>=0;unit--){org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);glDisable(GL_TEXTURE_2D);}
        org.lwjgl.opengl.GL20.glUseProgram(0);glDisable(GL_LIGHTING);glDisable(GL_ALPHA_TEST);glDisable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);glEnable(GL_BLEND);
        org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false);glShadeModel(GL_SMOOTH);return state;
    }
    public static void color(float position,float alpha){int c=palette[Math.max(0,Math.min(SEGMENTS,Math.round(position*SEGMENTS)))];glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,Math.max(0,Math.min(1,alpha)));}
    public static void vertex(double x,double y,double z){net.minecraft.client.renderer.entity.RenderManager r=Minecraft.getMinecraft().getRenderManager();glVertex3d(x-r.viewerPosX,y-r.viewerPosY,z-r.viewerPosZ);}
    public static void ring(double x,double y,double z,double radius,float alpha){
        glBegin(GL_TRIANGLE_STRIP);for(int i=0;i<=SEGMENTS;i++){color(i/(float)SEGMENTS,alpha);vertex(x+COS[i]*radius,y,z+SIN[i]*radius);vertex(x+COS[i]*radius*.91,y,z+SIN[i]*radius*.91);}glEnd();
        if(UiEffects.glowEnabled()){glBegin(GL_TRIANGLE_STRIP);for(int i=0;i<=SEGMENTS;i++){color(i/(float)SEGMENTS,alpha*.25f);vertex(x+COS[i]*radius,y,z+SIN[i]*radius);color(i/(float)SEGMENTS,0);vertex(x+COS[i]*radius*1.15,y,z+SIN[i]*radius*1.15);}glEnd();}
    }
}
