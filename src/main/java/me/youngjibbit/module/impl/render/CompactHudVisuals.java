package me.youngjibbit.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import static org.lwjgl.opengl.GL11.*;

/**
 * Small semantic glyphs drawn in an existing RenderState scope. Geometry is bounded,
 * round-capped, and shares the HUD's pass; it creates no shaders, textures or framebuffers.
 */
final class CompactHudVisuals {
    private static final int SEGMENTS=32;
    private static final float[] COS=new float[SEGMENTS+1],SIN=new float[SEGMENTS+1];
    private static int displayWidth=-1,displayHeight=-1,guiScale=-1,physicalScale=1;
    private static float feather=.65f;
    static {for(int i=0;i<=SEGMENTS;i++){double a=Math.PI*2*i/SEGMENTS;COS[i]=(float)Math.cos(a);SIN[i]=(float)Math.sin(a);}}
    private CompactHudVisuals(){}

    static void begin(float contentScale){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc!=null&&mc.gameSettings!=null&&(displayWidth!=mc.displayWidth||displayHeight!=mc.displayHeight||guiScale!=mc.gameSettings.guiScale)){
            displayWidth=mc.displayWidth;displayHeight=mc.displayHeight;guiScale=mc.gameSettings.guiScale;
            physicalScale=new ScaledResolution(mc).getScaleFactor();
        }
        feather=.65f/Math.max(.05f,physicalScale*Math.max(.01f,contentScale));
        GL20.glUseProgram(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
        GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
        GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
        GlStateManager.disableLighting();glDisable(GL_LIGHTING);
        GlStateManager.disableCull();glDisable(GL_CULL_FACE);
        GlStateManager.disableFog();glDisable(GL_FOG);
        GlStateManager.enableBlend();glEnable(GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GlStateManager.shadeModel(GL_SMOOTH);glShadeModel(GL_SMOOTH);
    }
    static void color(int rgb,float alpha){
        float a=((rgb>>>24)&255)/255f*Math.max(0,Math.min(1,alpha));
        glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,a);
    }
    static void dot(float x,float y,float radius,int rgb,float alpha){
        if(radius<=0||alpha<=0)return;
        float edge=Math.min(feather,radius*.45f),inner=Math.max(0,radius-edge);
        color(rgb,alpha);glBegin(GL_TRIANGLE_FAN);glVertex2f(x,y);
        for(int i=0;i<=SEGMENTS;i++)glVertex2f(x+COS[i]*inner,y+SIN[i]*inner);
        glEnd();glBegin(GL_QUAD_STRIP);
        for(int i=0;i<=SEGMENTS;i++){
            color(rgb,alpha);glVertex2f(x+COS[i]*inner,y+SIN[i]*inner);
            color(rgb,0);glVertex2f(x+COS[i]*(radius+edge),y+SIN[i]*(radius+edge));
        }glEnd();
    }
    static void line(float x1,float y1,float x2,float y2,float width,int rgb,float alpha){
        if(width<=0||alpha<=0)return;
        float dx=x2-x1,dy=y2-y1,len=(float)Math.sqrt(dx*dx+dy*dy),r=width/2;
        if(len<.001f){dot(x1,y1,r,rgb,alpha);return;}
        float nx=-dy/len,ny=dx/len,edge=Math.min(feather,r*.6f),inner=Math.max(0,r-edge);
        color(rgb,alpha);glBegin(GL_QUADS);
        glVertex2f(x1+nx*inner,y1+ny*inner);glVertex2f(x2+nx*inner,y2+ny*inner);
        glVertex2f(x2-nx*inner,y2-ny*inner);glVertex2f(x1-nx*inner,y1-ny*inner);glEnd();
        glBegin(GL_QUAD_STRIP);
        color(rgb,alpha);glVertex2f(x1+nx*inner,y1+ny*inner);
        color(rgb,0);glVertex2f(x1+nx*(r+edge),y1+ny*(r+edge));
        color(rgb,alpha);glVertex2f(x2+nx*inner,y2+ny*inner);
        color(rgb,0);glVertex2f(x2+nx*(r+edge),y2+ny*(r+edge));glEnd();
        glBegin(GL_QUAD_STRIP);
        color(rgb,alpha);glVertex2f(x1-nx*inner,y1-ny*inner);
        color(rgb,0);glVertex2f(x1-nx*(r+edge),y1-ny*(r+edge));
        color(rgb,alpha);glVertex2f(x2-nx*inner,y2-ny*inner);
        color(rgb,0);glVertex2f(x2-nx*(r+edge),y2-ny*(r+edge));glEnd();
        dot(x1,y1,r,rgb,alpha);dot(x2,y2,r,rgb,alpha);
    }
    static void arc(float cx,float cy,float radius,float start,float sweep,float thickness,int rgb,float alpha){
        if(radius<=0||thickness<=0||sweep<=0||alpha<=0)return;
        sweep=Math.min(1,sweep);int steps=Math.max(2,Math.min(64,(int)Math.ceil(sweep*48)));
        float inner=Math.max(0,radius-thickness/2),outer=radius+thickness/2;
        float edge=Math.min(feather,thickness*.3f);
        for(int band=0;band<3;band++){
            float r1=band==0?Math.max(0,inner-edge):band==1?inner+edge:outer-edge;
            float r2=band==0?inner+edge:band==1?outer-edge:outer+edge;
            glBegin(GL_QUAD_STRIP);
            for(int i=0;i<=steps;i++){
                double a=(start+sweep*i/steps)*Math.PI*2-Math.PI/2;
                float co=(float)Math.cos(a),si=(float)Math.sin(a);
                color(rgb,band==0?0:alpha);glVertex2f(cx+co*r1,cy+si*r1);
                color(rgb,band==2?0:alpha);glVertex2f(cx+co*r2,cy+si*r2);
            }glEnd();
        }
        if(sweep<.999f){
            double a=start*Math.PI*2-Math.PI/2,b=(start+sweep)*Math.PI*2-Math.PI/2;
            dot(cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,thickness/2,rgb,alpha);
            dot(cx+(float)Math.cos(b)*radius,cy+(float)Math.sin(b)*radius,thickness/2,rgb,alpha);
        }
    }
    static void heart(float x,float y,float size,int rgb,float alpha){
        float w=size*.16f;
        line(x,y+size*.3f,x+size*.2f,y+size*.1f,w,rgb,alpha);
        line(x+size*.2f,y+size*.1f,x+size*.5f,y+size*.3f,w,rgb,alpha);
        line(x+size*.5f,y+size*.3f,x+size*.8f,y+size*.1f,w,rgb,alpha);
        line(x+size*.8f,y+size*.1f,x+size,y+size*.3f,w,rgb,alpha);
        line(x,y+size*.3f,x+size*.1f,y+size*.58f,w,rgb,alpha);
        line(x+size*.1f,y+size*.58f,x+size*.5f,y+size*.9f,w,rgb,alpha);
        line(x+size,y+size*.3f,x+size*.9f,y+size*.58f,w,rgb,alpha);
        line(x+size*.9f,y+size*.58f,x+size*.5f,y+size*.9f,w,rgb,alpha);
    }
    static void range(float x,float y,float size,int rgb,float alpha){
        line(x,y+size*.5f,x+size,y+size*.5f,size*.12f,rgb,alpha*.65f);
        line(x,y+size*.25f,x,y+size*.75f,size*.15f,rgb,alpha);
        line(x+size,y+size*.25f,x+size,y+size*.75f,size*.15f,rgb,alpha);
        dot(x+size*.5f,y+size*.5f,size*.12f,rgb,alpha);
    }
    static void swords(float x,float y,float size,int rgb,float alpha){
        float w=size*.14f;
        line(x+size*.18f,y+size*.14f,x+size*.8f,y+size*.86f,w,rgb,alpha);
        line(x+size*.82f,y+size*.14f,x+size*.2f,y+size*.86f,w,rgb,alpha);
        line(x+size*.05f,y+size*.65f,x+size*.36f,y+size*.92f,w,rgb,alpha);
        line(x+size*.64f,y+size*.92f,x+size*.95f,y+size*.65f,w,rgb,alpha);
    }
    static void skull(float x,float y,float size,int rgb,float alpha){
        arc(x+size*.5f,y+size*.4f,size*.34f,0,1,size*.13f,rgb,alpha);
        line(x+size*.26f,y+size*.64f,x+size*.26f,y+size*.86f,size*.13f,rgb,alpha);
        line(x+size*.26f,y+size*.86f,x+size*.74f,y+size*.86f,size*.13f,rgb,alpha);
        line(x+size*.74f,y+size*.86f,x+size*.74f,y+size*.64f,size*.13f,rgb,alpha);
        dot(x+size*.37f,y+size*.4f,size*.07f,rgb,alpha);
        dot(x+size*.63f,y+size*.4f,size*.07f,rgb,alpha);
    }
    static void clock(float cx,float cy,float radius,float progress,float turn,int rgb,float alpha){
        arc(cx,cy,radius,0,1,1.6f,0xffa6b7cc,alpha*.22f);
        arc(cx,cy,radius,0,progress,1.9f,rgb,alpha);
        line(cx,cy,cx,cy-radius*.5f,1.35f,rgb,alpha);
        double a=turn*Math.PI*2-Math.PI/2;
        line(cx,cy,cx+(float)Math.cos(a)*radius*.6f,cy+(float)Math.sin(a)*radius*.6f,1.35f,rgb,alpha);
        dot(cx,cy,1.4f,rgb,alpha);
    }
    static void equipment(int slot,float x,float y,float size,int rgb,float alpha){
        float w=size*.12f;
        if(slot==0){
            arc(x+size*.5f,y+size*.55f,size*.35f,.75f,.5f,w,rgb,alpha);
            line(x+size*.15f,y+size*.55f,x+size*.15f,y+size*.8f,w,rgb,alpha);
            line(x+size*.85f,y+size*.55f,x+size*.85f,y+size*.8f,w,rgb,alpha);
            line(x+size*.15f,y+size*.8f,x+size*.36f,y+size*.8f,w,rgb,alpha);
            line(x+size*.64f,y+size*.8f,x+size*.85f,y+size*.8f,w,rgb,alpha);
        }else if(slot==1){
            line(x+size*.35f,y+size*.2f,x+size*.15f,y+size*.35f,w,rgb,alpha);
            line(x+size*.65f,y+size*.2f,x+size*.85f,y+size*.35f,w,rgb,alpha);
            line(x+size*.3f,y+size*.4f,x+size*.3f,y+size*.83f,w,rgb,alpha);
            line(x+size*.7f,y+size*.4f,x+size*.7f,y+size*.83f,w,rgb,alpha);
            line(x+size*.3f,y+size*.83f,x+size*.7f,y+size*.83f,w,rgb,alpha);
            line(x+size*.35f,y+size*.2f,x+size*.65f,y+size*.2f,w,rgb,alpha);
        }else if(slot==2){
            line(x+size*.25f,y+size*.15f,x+size*.75f,y+size*.15f,w,rgb,alpha);
            line(x+size*.25f,y+size*.15f,x+size*.2f,y+size*.85f,w,rgb,alpha);
            line(x+size*.75f,y+size*.15f,x+size*.8f,y+size*.85f,w,rgb,alpha);
            line(x+size*.5f,y+size*.48f,x+size*.42f,y+size*.85f,w,rgb,alpha);
            line(x+size*.5f,y+size*.48f,x+size*.58f,y+size*.85f,w,rgb,alpha);
        }else if(slot==3){
            line(x+size*.25f,y+size*.2f,x+size*.25f,y+size*.8f,w,rgb,alpha);
            line(x+size*.25f,y+size*.8f,x+size*.05f,y+size*.8f,w,rgb,alpha);
            line(x+size*.65f,y+size*.2f,x+size*.65f,y+size*.8f,w,rgb,alpha);
            line(x+size*.65f,y+size*.8f,x+size*.9f,y+size*.8f,w,rgb,alpha);
        }else{
            line(x+size*.2f,y+size*.85f,x+size*.8f,y+size*.15f,w,rgb,alpha);
            line(x+size*.15f,y+size*.55f,x+size*.45f,y+size*.85f,w,rgb,alpha);
        }
    }
    static void skin(ResourceLocation skin,float x,float y,float size,float radius,float alpha){
        if(skin==null||size<=0)return;
        Minecraft mc=Minecraft.getMinecraft();
        GL20.glUseProgram(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
        GlStateManager.enableBlend();glEnable(GL_BLEND);
        GlStateManager.disableLighting();glDisable(GL_LIGHTING);
        GlStateManager.disableCull();glDisable(GL_CULL_FACE);
        GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
        GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.color(1,1,1,alpha);glColor4f(1,1,1,alpha);
        skinLayer(x,y,size,radius,8,8);
        skinLayer(x,y,size,radius,40,8);
    }
    private static void skinLayer(float x,float y,float size,float radius,float u,float v){
        float r=Math.max(0,Math.min(size/2,radius));
        glBegin(GL_TRIANGLE_FAN);glTexCoord2f((u+4)/64,(v+4)/64);glVertex2f(x+size/2,y+size/2);
        for(int i=0;i<=32;i++){
            int corner=(i%32)/8;double a=(i/8d-1)*Math.PI/2;
            float cx=corner==0||corner==1?x+size-r:x+r;
            float cy=corner==0||corner==3?y+r:y+size-r;
            if(i==32){cx=x+size-r;cy=y+r;}
            float px=cx+(float)Math.cos(a)*r,py=cy+(float)Math.sin(a)*r;
            glTexCoord2f((u+(px-x)/size*8)/64,(v+(py-y)/size*8)/64);glVertex2f(px,py);
        }glEnd();
    }
}
