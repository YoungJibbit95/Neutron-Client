package me.youngjibbit.module.impl.client.island;

import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import static org.lwjgl.opengl.GL11.*;

/**
 * Rounded, feathered triangle glyphs. Uses the caller's surface, clock and clip, with no textures,
 * shaders or framebuffer allocations. Every draw restores both actual GL state and matrix mode.
 */
public final class IslandGlyphRenderer {
    private static final float TAU=(float)(Math.PI*2);
    private static final int CAP_STEPS=8;
    private static final float[] CAP_COS=new float[(CAP_STEPS+1)*2],CAP_SIN=new float[(CAP_STEPS+1)*2];
    static {
        for(int i=0;i<CAP_COS.length;i++){
            double angle=-Math.PI/2+(i<=CAP_STEPS?i:i-1)*Math.PI/CAP_STEPS;
            CAP_COS[i]=(float)Math.cos(angle);CAP_SIN[i]=(float)Math.sin(angle);
        }
    }
    private IslandGlyphRenderer(){}

    /** 0 off, 1 success, 2 warning, 3 information, 4 error. Reveal is an existing HudSpring sample. */
    public static void notice(float x,float y,int kind,int rgb,float alpha,float reveal){
        if(alpha<=0)return;
        float drawn=bounded(reveal),scale=.82f+.18f*drawn+.04f*(float)Math.sin(Math.PI*drawn);
        try(GlyphScope ignored=new GlyphScope()){
            prepare();glTranslatef(x,y,0);glScalef(scale,scale,1);glBegin(GL_TRIANGLES);
            arc(0,0,11,0,TAU,.8f,rgb,alpha*.2f);
            if(kind==1){
                progress(-5,0,-1.4f,3.8f,2.25f,rgb,alpha,drawn/.38f);
                progress(-1.4f,3.8f,6,-4.7f,2.25f,rgb,alpha,(drawn-.38f)/.62f);
                float sparkle=(float)Math.sin(Math.PI*drawn)*.7f;
                if(sparkle>.01f){
                    float spread=9+drawn*3;
                    stroke(spread,-7,spread,-7,1.8f,rgb,alpha*sparkle);
                    stroke(-spread,6,-spread,6,1.4f,rgb,alpha*sparkle);
                }
            }else if(kind==2){
                stroke(0,-7,7,5.7f,1.8f,rgb,alpha*drawn);
                stroke(7,5.7f,-7,5.7f,1.8f,rgb,alpha*drawn);
                stroke(-7,5.7f,0,-7,1.8f,rgb,alpha*drawn);
                stroke(0,-2.4f,0,.7f,2,rgb,alpha*drawn);
                stroke(0,3.4f,0,3.4f,2,rgb,alpha*drawn);
            }else if(kind==4){
                progress(-4,-4,4,4,2.15f,rgb,alpha,drawn);
                progress(4,-4,-4,4,2.15f,rgb,alpha,drawn);
            }else if(kind==3){
                stroke(0,-4,0,-4,2.3f,rgb,alpha*drawn);
                progress(0,-1,0,5,2.1f,rgb,alpha,drawn);
            }else progress(-5,0,5,0,2.25f,rgb,alpha,drawn);
            glEnd();
        }
    }

    public static void scaffold(IslandScaffoldIndicator indicator,float x,float y,int rgb,float alpha,float reveal){
        if(indicator==null||alpha<=0)return;
        float scale=.88f+.12f*Math.max(0,Math.min(1.12f,reveal));
        try(GlyphScope ignored=new GlyphScope()){
            prepare();glTranslatef(x+12,y+12,0);glScalef(scale,scale,1);glTranslatef(-12,-12,0);
            glBegin(GL_TRIANGLES);
            arc(12,12,10.6f,0,TAU,1,rgb,alpha*.14f);
            float width=indicator.status==IslandScaffoldIndicator.Status.SEARCHING?2.15f:1.9f;
            for(int i=0;i<indicator.segmentCount();i++)
                stroke(indicator.x1(i),indicator.y1(i),indicator.x2(i),indicator.y2(i),
                        width,rgb,alpha*indicator.opacity);
            glEnd();
        }
    }

    /** A decorative glint is deliberately separate from measured FPS/CPS or provider activity. */
    public static void widgetRim(float x,float y,float diameter,IslandWidget widget,int rgb,float alpha){
        if(widget==null||diameter<=0||alpha<=0)return;
        try(GlyphScope ignored=new GlyphScope()){
            prepare();glTranslatef(x,y,0);glScalef(diameter/34,diameter/34,1);glBegin(GL_TRIANGLES);
            arc(17,17,14.7f,0,TAU,.7f,rgb,alpha*.14f);
            if(widget.active){
                float start=-(float)Math.PI/2+widget.phase*TAU;
                arc(17,17,14.7f,start,.62f,1.15f,rgb,alpha*.55f);
                float tip=start+.62f,tx=17+14.7f*(float)Math.cos(tip),ty=17+14.7f*(float)Math.sin(tip);
                stroke(tx,ty,tx,ty,1.65f,rgb,alpha*widget.pulse);
            }
            glEnd();
        }
    }

    public static void exploit(IslandExploitGlyph glyph,float x,float y,int rgb,float alpha){
        if(glyph==null||alpha<=0)return;
        try(GlyphScope ignored=new GlyphScope()){
            prepare();glTranslatef(x,y,0);glBegin(GL_TRIANGLES);
            for(int i=0;i<glyph.segments();i++)
                stroke(glyph.x1(i),glyph.y1(i),glyph.x2(i),glyph.y2(i),1.15f,rgb,alpha);
            glEnd();
        }
    }

    /** Compact read-only status: idle, holding, ready, releasing. Phase is decorative UI time. */
    public static void activity(float x,float y,int kind,int rgb,float alpha,float phase){
        if(alpha<=0)return;
        try(GlyphScope ignored=new GlyphScope()){
            prepare();glTranslatef(x,y,0);glBegin(GL_TRIANGLES);
            arc(0,0,3.7f,0,TAU,.65f,rgb,alpha*(kind==0?.55f:.22f));
            if(kind!=0)arc(0,0,3.7f,phase*2.4f,1.6f,.85f,rgb,alpha*.8f);
            if(kind==1){
                stroke(-1.2f,-1.6f,-1.2f,1.6f,.85f,rgb,alpha);
                stroke(1.2f,-1.6f,1.2f,1.6f,.85f,rgb,alpha);
            }else if(kind==2){
                stroke(-2,0,-.5f,1.5f,1,rgb,alpha);
                stroke(-.5f,1.5f,2,-1.6f,1,rgb,alpha);
            }else if(kind==3){
                stroke(-2,0,2,0,1,rgb,alpha);
                stroke(.1f,-1.7f,2,0,1,rgb,alpha);
                stroke(2,0,.1f,1.7f,1,rgb,alpha);
            }
            glEnd();
        }
    }

    private static void prepare(){
        GL20.glUseProgram(0);
        glDisable(GL_TEXTURE_2D);glDisable(GL_DEPTH_TEST);glDisable(GL_ALPHA_TEST);
        glDisable(GL_LIGHTING);glDisable(GL_CULL_FACE);glDisable(GL_FOG);
        glEnable(GL_BLEND);glShadeModel(GL_SMOOTH);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);
    }
    private static void progress(float ax,float ay,float bx,float by,float width,int rgb,float alpha,float progress){
        float p=bounded(progress);
        if(p>0)stroke(ax,ay,ax+(bx-ax)*p,ay+(by-ay)*p,width,rgb,alpha);
    }
    private static float bounded(float value){
        return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;
    }
    /** Convex capsule fan plus a subpixel alpha fringe; no driver-dependent wide GL_LINES. */
    private static void stroke(float ax,float ay,float bx,float by,float width,int rgb,float alpha){
        if(alpha<=0||width<=0)return;
        float dx=bx-ax,dy=by-ay,length=(float)Math.sqrt(dx*dx+dy*dy);
        float rx=length==0?1:dx/length,ry=length==0?0:dy/length;
        float radius=width/2,outer=radius+.45f,cx=(ax+bx)/2,cy=(ay+by)/2;
        for(int i=0;i<CAP_COS.length;i++){
            int j=(i+1)%CAP_COS.length;
            float ox=i<=CAP_STEPS?bx:ax,oy=i<=CAP_STEPS?by:ay;
            float nx=j<=CAP_STEPS?bx:ax,ny=j<=CAP_STEPS?by:ay;
            float ca=CAP_COS[i]*rx-CAP_SIN[i]*ry,sa=CAP_COS[i]*ry+CAP_SIN[i]*rx;
            float cb=CAP_COS[j]*rx-CAP_SIN[j]*ry,sb=CAP_COS[j]*ry+CAP_SIN[j]*rx;
            float ix=ox+ca*radius,iy=oy+sa*radius,jx=nx+cb*radius,jy=ny+sb*radius;
            vertex(cx,cy,rgb,alpha);vertex(ix,iy,rgb,alpha);vertex(jx,jy,rgb,alpha);
            vertex(ix,iy,rgb,alpha);vertex(ox+ca*outer,oy+sa*outer,rgb,0);vertex(nx+cb*outer,ny+sb*outer,rgb,0);
            vertex(ix,iy,rgb,alpha);vertex(nx+cb*outer,ny+sb*outer,rgb,0);vertex(jx,jy,rgb,alpha);
        }
    }
    private static void arc(float x,float y,float radius,float start,float sweep,float width,int rgb,float alpha){
        if(alpha<=0||width<=0||sweep==0)return;
        int segments=Math.max(2,(int)Math.ceil(Math.abs(sweep)*8));
        float step=sweep/segments,cs=(float)Math.cos(step),sn=(float)Math.sin(step);
        float ca=(float)Math.cos(start),sa=(float)Math.sin(start),inner=radius-width/2,outer=radius+width/2;
        for(int i=0;i<segments;i++){
            float cb=ca*cs-sa*sn,sb=sa*cs+ca*sn;
            band(x,y,ca,sa,cb,sb,inner,outer,rgb,alpha,alpha);
            band(x,y,ca,sa,cb,sb,inner-.4f,inner,rgb,0,alpha);
            band(x,y,ca,sa,cb,sb,outer,outer+.4f,rgb,alpha,0);
            ca=cb;sa=sb;
        }
    }
    private static void band(float x,float y,float ca,float sa,float cb,float sb,float inner,float outer,
                             int rgb,float innerAlpha,float outerAlpha){
        vertex(x+ca*inner,y+sa*inner,rgb,innerAlpha);vertex(x+ca*outer,y+sa*outer,rgb,outerAlpha);
        vertex(x+cb*outer,y+sb*outer,rgb,outerAlpha);
        vertex(x+ca*inner,y+sa*inner,rgb,innerAlpha);vertex(x+cb*outer,y+sb*outer,rgb,outerAlpha);
        vertex(x+cb*inner,y+sb*inner,rgb,innerAlpha);
    }
    private static void vertex(float x,float y,int rgb,float alpha){
        glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,bounded(alpha));
        glVertex2f(x,y);
    }

    /**
     * Raw GL calls leave Minecraft's cached state unchanged; restoring the corresponding actual
     * state keeps both representations in agreement. Program/matrix mode need explicit restoration.
     */
    private static final class GlyphScope implements AutoCloseable {
        private final int program=glGetInteger(GL20.GL_CURRENT_PROGRAM),matrixMode=glGetInteger(GL_MATRIX_MODE);
        GlyphScope(){
            glPushAttrib(GL_ENABLE_BIT|GL_COLOR_BUFFER_BIT|GL_CURRENT_BIT|GL_LIGHTING_BIT|GL_TEXTURE_BIT|GL_POLYGON_BIT);
            glMatrixMode(GL_MODELVIEW);glPushMatrix();
        }
        @Override public void close(){
            glMatrixMode(GL_MODELVIEW);glPopMatrix();glMatrixMode(matrixMode);glPopAttrib();
            GL20.glUseProgram(program==0||GL20.glIsProgram(program)?program:0);
        }
    }
}
