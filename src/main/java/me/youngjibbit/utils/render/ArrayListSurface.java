package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Masked HUD surfaces: analytic contours, existing Gaussian shaders, constant effect pass count. */
public final class ArrayListSurface implements AutoCloseable {
    // Bounded contour upload; the 42-module client needs at most 86 corners.
    private static final int MAX_CORNERS = 192;
    private final Framebuffer[] buffers = new Framebuffer[5];
    private final FloatBuffer cornerBuffer = BufferUtils.createFloatBuffer(MAX_CORNERS*4);
    private int bufferWidth, bufferHeight;
    // Mask, scratch and halo are consumed before a content callback: one shared trio suffices even for nested panels.
    private static final Framebuffer[] effects=new Framebuffer[3];private static int effectsWidth,effectsHeight;
    private int capturedClean,capturedSoft;private boolean capturedBlur;private float capturedRadius;private Theme capturedTheme;private UiEffects.SurfacePolicy capturedPolicy;
    private static final Set<ArrayListSurface> owners=Collections.newSetFromMap(new WeakHashMap<ArrayListSurface,Boolean>());
    private static ShaderUtil shapeShader;
    private static boolean shaderFailed;
    private final UiEffects.LocalSurface local;
    public ArrayListSurface(){this(null);}
    public ArrayListSurface(me.youngjibbit.module.Module owner){local=owner==null?null:new UiEffects.LocalSurface(owner);owners.add(this);}
    private static ShaderUtil shader(){
        if(shapeShader==null&&!shaderFailed)try{shapeShader=new ShaderUtil("Flauxy/Shaders/arraylistSurface.frag");}catch(RuntimeException ex){shaderFailed=true;System.err.println("[UI] Surface shader unavailable; tinted contour fallback: "+ex.getMessage());}
        return shapeShader;
    }
    public static void releaseResources(){for(ArrayListSurface owner:new ArrayList<>(owners))owner.close();releaseEffects();if(shapeShader!=null){shapeShader.close();shapeShader=null;}shaderFailed=false;}

    private static void releaseEffects(){for(int i=0;i<effects.length;i++)if(effects[i]!=null){effects[i].deleteFramebuffer();effects[i]=null;}}
    public boolean withCapturedScene(Runnable content){
        if(!UiEffects.effectsSupported()||capturedClean<=0||bufferWidth!=Minecraft.getMinecraft().displayWidth||bufferHeight!=Minecraft.getMinecraft().displayHeight||!glIsTexture(capturedClean))return false;
        try(UiEffects.SceneContext scope=UiEffects.scene(capturedClean,capturedSoft,capturedBlur,capturedTheme,capturedPolicy,false,capturedRadius)){content.run();}return true;
    }
    /** Bands share the left edge and tile the vertical interval without gaps. */
    public static final class Shape {
        public final float left, top, right, bottom;
        final float[] corners;
        public Shape(float left, float[] tops, float[] bottoms, float[] rights, float radius) {
            this(lefts(left,tops.length),tops,bottoms,rights,radius);
        }
        private static float[] lefts(float value,int count){float[] result=new float[count];java.util.Arrays.fill(result,value);return result;}
        public Shape(float[] lefts,float[] tops,float[] bottoms,float[] rights,float radius) {
            if (tops.length == 0 || tops.length != bottoms.length || tops.length != rights.length || tops.length != lefts.length) throw new IllegalArgumentException("Empty/mismatched bands");
            float min=lefts[0];for(float l:lefts)min=Math.min(min,l);this.left=min;top=tops[0];bottom=bottoms[bottoms.length-1];
            float max=min;
            List<float[]> points=new ArrayList<>();
            points.add(new float[]{lefts[0],top});
            points.add(new float[]{rights[0],top});
            for (int i=1;i<rights.length;i++) {
                points.add(new float[]{rights[i-1],tops[i]});
                points.add(new float[]{rights[i],tops[i]});
            }
            points.add(new float[]{rights[rights.length-1],bottom});
            points.add(new float[]{lefts[lefts.length-1],bottom});
            for(int i=lefts.length-1;i>0;i--){points.add(new float[]{lefts[i],tops[i]});points.add(new float[]{lefts[i-1],tops[i]});}
            for (float r:rights) max=Math.max(max,r);
            right=max;
            boolean changed=true;
            while (changed && points.size()>4) {
                changed=false;
                for (int i=0;i<points.size();i++) {
                    float[] a=points.get((i+points.size()-1)%points.size()), b=points.get(i), c=points.get((i+1)%points.size());
                    float cross=(b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]);
                    if (Math.abs(cross)<0.00001f) { points.remove(i); changed=true; break; }
                }
            }
            if (points.size()>MAX_CORNERS) throw new IllegalArgumentException("Arraylist contour exceeds " + MAX_CORNERS + " corners");
            corners=new float[points.size()*4];
            for (int i=0;i<points.size();i++) {
                float[] a=points.get((i+points.size()-1)%points.size()), b=points.get(i), c=points.get((i+1)%points.size());
                float ax=a[0]-b[0], ay=a[1]-b[1], bx=c[0]-b[0], by=c[1]-b[1];
                float al=(float)Math.hypot(ax,ay), bl=(float)Math.hypot(bx,by);
                float r=Math.min(radius,Math.min(al,bl)*0.45f);
                float cx=b[0]+(ax/al+bx/bl)*r, cy=b[1]+(ay/al+by/bl)*r;
                float qx=b[0]-cx, qy=b[1]-cy;
                int q=qx>=0 ? (qy>=0?0:3) : (qy>=0?1:2);
                corners[i*4]=cx; corners[i*4+1]=cy;
                corners[i*4+2]=(-ax*by+ay*bx)>0?r:-r;
                corners[i*4+3]=q;
            }
        }
        private Shape(Shape source,float dx,float dy) {
            left=source.left+dx;right=source.right+dx;top=source.top+dy;bottom=source.bottom+dy;
            corners=source.corners.clone();for(int i=0;i<corners.length;i+=4){corners[i]+=dx;corners[i+1]+=dy;}
        }
        public Shape translated(float dx,float dy){return new Shape(this,dx,dy);}
        /** Same signed nearest-edge/arc distance as arraylistSurface.frag, without the outer halo. */
        public boolean contains(float x,float y) {
            if(x<left||x>right||y<top||y>bottom)return false;
            float best=Float.POSITIVE_INFINITY,side=1;
            for(int i=0;i<corners.length;i+=4){
                int prev=(i+corners.length-4)%corners.length;
                float[] a=tangent(prev,false),b=tangent(i,true);
                float ex=b[0]-a[0],ey=b[1]-a[1],length=ex*ex+ey*ey;
                if(length>0.0000001f){float t=Math.max(0,Math.min(1,((x-a[0])*ex+(y-a[1])*ey)/length));float dx=x-a[0]-ex*t,dy=y-a[1]-ey*t,d=dx*dx+dy*dy;if(d<best){best=d;side=dx*ey-dy*ex;}}
                float cx=corners[i],cy=corners[i+1],r=Math.abs(corners[i+2]),qx=corners[i+3]<.5f||corners[i+3]>2.5f?1:-1,qy=corners[i+3]<1.5f?1:-1;
                float vx=x-cx,vy=y-cy,px,py,n=(float)Math.hypot(vx,vy);
                if(vx*qx>=0&&vy*qy>=0&&n>.00001f){px=cx+vx/n*r;py=cy+vy/n*r;}
                else if((vx-qx*r)*(vx-qx*r)+vy*vy<vx*vx+(vy-qy*r)*(vy-qy*r)){px=cx+qx*r;py=cy;}
                else{px=cx;py=cy+qy*r;}
                float dx=x-px,dy=y-py,d=dx*dx+dy*dy;if(d<best){best=d;side=(dx*(px-cx)+dy*(py-cy))*Math.signum(corners[i+2]);}
            }
            return side<=0;
        }
        private float[] tangent(int i,boolean start){float r=corners[i+2],q=corners[i+3],qx=q<.5f||q>2.5f?1:-1,qy=q<1.5f?1:-1;boolean horizontal=qx*qy*Math.signum(r)>0;if(!start)horizontal=!horizontal;return new float[]{corners[i]+(horizontal?qx*Math.abs(r):0),corners[i+1]+(horizontal?0:qy*Math.abs(r))};}
    }

    public void render(Shape shape, Theme theme, boolean background, boolean blur, boolean glow, float glowAlpha, Runnable text) {
        render(shape,theme,background,blur,glow,glowAlpha,5,116f/255f,background?0.8f:0,false,text);
    }

    /** Rectangular HUDs reuse the same mask/effect compositor without duplicating its framebuffer stack. */
    public void panel(float x,float y,float width,float height,float radius,Theme theme,boolean background,
                      boolean blur,boolean glow,float blurRadius,float fillAlpha,float outlineAlpha,Runnable content) {
        panel(x,y,width,height,radius,theme,background,blur,glow,blurRadius,fillAlpha,outlineAlpha,false,content);
    }
    public void panel(float x,float y,float width,float height,float radius,Theme theme,boolean background,
                      boolean blur,boolean glow,float blurRadius,float fillAlpha,float outlineAlpha,boolean gradientFill,Runnable content) {
        if(width<=0||height<=0)return;
        Shape shape=new Shape(x,new float[]{y},new float[]{y+height},new float[]{x+width},UiEffects.radius(radius,width,height));
        render(shape,theme,background,blur,glow,0.75f,blurRadius,fillAlpha,outlineAlpha,gradientFill,content);
    }

    /** Deliberate named silhouettes (Rise/Rue, flat Basic, Status strip) keep their supplied radius. */
    public void designedPanel(float x,float y,float width,float height,float radius,Theme theme,boolean background,
        boolean blur,boolean glow,float blurRadius,float fillAlpha,float outlineAlpha,boolean gradientFill,Runnable content){
        if(width<=0||height<=0)return;
        render(new Shape(x,new float[]{y},new float[]{y+height},new float[]{x+width},Math.max(0,Math.min(radius,Math.min(width,height)/2))),theme,background,blur,glow,.75f,blurRadius,fillAlpha,outlineAlpha,gradientFill,content);
    }
    private void render(Shape shape,Theme theme,boolean background,boolean blur,boolean glow,float glowAlpha,
                        float blurRadius,float fillAlpha,float outlineAlpha,boolean gradientFill,Runnable text) {
        render(shape,theme,background,blur,glow,glowAlpha,blurRadius,fillAlpha,outlineAlpha,gradientFill,-1,1,text);
    }
    /** Explicit notification design overrides only surface style; global effect masters still win. */
    public void styledPanel(float x,float y,float width,float height,float radius,Theme theme,boolean blur,boolean glow,float fillAlpha,float outlineAlpha,boolean glass,Runnable content){
        styledPanel(x,y,width,height,radius,theme,blur,glow,fillAlpha,outlineAlpha,glass,1,content);
    }
    public void styledPanel(float x,float y,float width,float height,float radius,Theme theme,boolean blur,boolean glow,float fillAlpha,float outlineAlpha,boolean glass,float opacity,Runnable content){
        if(width<=0||height<=0)return;
        render(new Shape(x,new float[]{y},new float[]{y+height},new float[]{x+width},UiEffects.radius(radius,width,height)),theme,true,blur,glow,.65f,5,fillAlpha,outlineAlpha,false,glass?1:0,opacity,content);
    }
    public void inheritedPanel(float x,float y,float width,float height,float radius,Theme theme,float opacity,Runnable content){
        if(width<=0||height<=0)return;
        render(new Shape(x,new float[]{y},new float[]{y+height},new float[]{x+width},UiEffects.radius(radius,width,height)),theme,true,true,true,.65f,5,.6f,.7f,false,-1,opacity,content);
    }
    private void render(Shape shape,Theme theme,boolean background,boolean blur,boolean glow,float glowAlpha,
                        float blurRadius,float fillAlpha,float outlineAlpha,boolean gradientFill,int surfaceStyle,float opacity,Runnable text){
        theme=theme(theme);
        capturedClean=capturedSoft=0;
        UiEffects.SurfacePolicy policy=UiEffects.surface(local,theme,background,blur,glow,outlineAlpha,surfaceStyle);
        background=policy.background;blur=policy.blur;glow=policy.glow&&(!theme.isCustom()||theme.getGlowStrength()>0);outlineAlpha=policy.outline;
        boolean glass=background&&(policy.style==3);
        if(policy.style>=0)gradientFill=policy.style==1;
        Minecraft mc=Minecraft.getMinecraft();
        try (RenderState state=new RenderState()) {
            ScaledResolution sr=new ScaledResolution(mc);
            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);
            glMatrixMode(GL_MODELVIEW); glLoadIdentity();
            prepare();
            ShaderUtil shader=OpenGlHelper.shadersSupported&&OpenGlHelper.isFramebufferEnabled()?shader():null;
            if(shader==null){
                GL20.glUseProgram(0);fallback(shape,theme,background,fillAlpha*opacity,outlineAlpha*opacity);text.run();return;
            }
            shader.init();
            cornerBuffer.clear(); cornerBuffer.put(shape.corners).flip();
            GL20.glUniform4(shader.getUniform("corners"),cornerBuffer);
            shader.setUniformi("cornerCount",shape.corners.length/4);
            shader.setUniformf("resolution",mc.displayWidth,mc.displayHeight);
            shader.setUniformf("guiScale",sr.getScaleFactor());
            shader.unload();
            UiEffects.SceneContext parent=UiEffects.scene();boolean borrow=parent!=null&&parent.clean>0;
            boolean cleanScene=background&&policy.style>=0||glass;
            float effectiveRadius=local!=null&&local.local()?blurRadius:UiEffects.blurRadius(blurRadius);
            boolean computeBlur=blur&&!(borrow&&parent.blurred&&Math.abs(parent.radius-effectiveRadius)<.01f);
            ensureBuffers(mc.displayWidth,mc.displayHeight,glow,computeBlur,cleanScene&&!borrow);
            state.bindTarget();prepare();
            int clean=borrow?parent.clean:0,soft=borrow&&parent.blurred?parent.soft:0;
            if(!borrow&&(blur||cleanScene)){
                int slot=cleanScene?4:0;texture(0,buffers[slot].framebufferTexture);glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,mc.displayWidth,mc.displayHeight);clean=buffers[slot].framebufferTexture;
            }
            if(computeBlur){GaussianBlur.renderScene(buffers[1],buffers[3],clean,effectiveRadius*sr.getScaleFactor());soft=buffers[3].framebufferTexture;}
            if(!blur)soft=clean;
            if (glow) {
                if(RenderDiagnostics.enabled)RenderDiagnostics.maskPasses++;
                target(buffers[0]);
                shader.init(); shader.setUniformi("maskPass",1);
                ShaderUtil.drawQuads(shape.left-1,shape.top-1,shape.right-shape.left+2,shape.bottom-shape.top+2);
                shader.unload();
                GaussianBlur.renderPass(buffers[1],buffers[0].framebufferTexture,1,0,Math.min(120,Math.round(UiEffects.glowRadius(theme.isCustom()?theme.getGlowRadius():9)*sr.getScaleFactor())),true,1.4f);
                GaussianBlur.renderPass(buffers[2],buffers[1].framebufferTexture,0,1,Math.min(120,Math.round(UiEffects.glowRadius(theme.isCustom()?theme.getGlowRadius():9)*sr.getScaleFactor())),true,1.4f);
            }
            state.bindTarget();
            prepare();
            texture(0,soft);texture(2,clean);texture(1,glow?buffers[2].framebufferTexture:0);
            if(RenderDiagnostics.enabled)RenderDiagnostics.composites++;
            shader.init(); shader.setUniformi("maskPass",0);
            shader.setUniformi("scene",0); shader.setUniformi("halo",1);shader.setUniformi("snapshot",2);
            shader.setUniformi("replaceScene",borrow&&parent.replacing&&policy.style>=0?1:0);shader.setUniformi("substance",policy.style);shader.setUniformf("globalOpacity",policy.opacity);shader.setUniformf("gradientStrength",policy.fillStrength);shader.setUniformf("surfaceOpacity",Math.max(0,Math.min(1,opacity)));shader.setUniformi("glass",glass?1:0);UiEffects.Options effects=UiEffects.options();shader.setUniformf("refraction",effects.refraction);shader.setUniformf("tint",effects.tint);shader.setUniformf("edgeLight",effects.edge);shader.setUniformi("glassClear",effects.glassClear?1:0);shader.setUniformf("glassInk",UiEffects.ink(theme));
            UiEffects.upload(shader,theme,policy.gradient);
            shader.setUniformi("gradientFill",gradientFill?1:0);shader.setUniformi("backgroundEnabled",background?1:0);
            shader.setUniformi("blurred",blur?1:0); shader.setUniformi("glowing",glow?1:0);
            shader.setUniformf("fillAlpha",background?(policy.style>=0&&surfaceStyle<0?theme.getBackground().getAlpha()/255f:theme.isCustom()&&surfaceStyle<0?theme.getBackground().getAlpha()/255f:fillAlpha):0);
            shader.setUniformf("outlineAlpha",outlineAlpha*(theme.isCustom()?theme.getOutlineStrength():1));
            shader.setUniformf("outlineWidth",theme.isCustom()?theme.getOutlineWidth():1);
            shader.setUniformf("glowAlpha",UiEffects.glowAlpha(glowAlpha)*(theme.isCustom()?theme.getGlowStrength()/.75f:1));
            shader.setUniformi("customTheme",theme.isCustom()?1:0);shader.setUniformi("verticalGradient",theme.isVertical()?1:0);
            shader.setUniformf("phaseGradient",theme.isAnimated()?theme.phase():-1);
            shader.setUniformf("shapeBounds",shape.left,shape.top,shape.right-shape.left,shape.bottom-shape.top);
            if(theme.isCustom()||policy.style>=0)shader.setUniformf("surfaceColor",theme.getBackground().getRed()/255f,theme.getBackground().getGreen()/255f,theme.getBackground().getBlue()/255f);else shader.setUniformf("surfaceColor",.018f,.018f,.018f);
            color(shader,"outlineColor",theme.getAccentColor().getRGB());
            color(shader,"primary",theme.getPrimaryGradientRGB()); color(shader,"secondary",theme.getSecondaryGradientRGB());
            GlStateManager.enableBlend(); glEnable(GL_BLEND);
            GlStateManager.blendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            float pad=glow?UiEffects.glowRadius(theme.isCustom()?theme.getGlowRadius():9)*2+4:2;
            state.restoreClip();
            ShaderUtil.drawQuads(shape.left-pad,shape.top-pad,shape.right-shape.left+pad*2,shape.bottom-shape.top+pad*2);
            shader.unload();
            texture(0,0);
            // Legacy masks can reuse slot zero for glow; only modern snapshots remain clean for nested fills.
            capturedClean=cleanScene||borrow?clean:0;capturedSoft=soft;capturedBlur=blur;capturedTheme=theme;capturedPolicy=policy;capturedRadius=effectiveRadius;
            try(UiEffects.SceneContext context=UiEffects.scene(capturedClean,soft,blur,theme,policy,background&&policy.style>=0,effectiveRadius)){text.run();}
        }
    }

    /** One read-only scene for disconnected controls or a set of projected labels. No rectangle is drawn here. */
    public boolean inheritsEffects(){return local!=null&&UiEffects.style(local.source.getValue())>=0||UiEffects.options().configured&&(local==null||!local.local());}
    public boolean usesSurface(boolean legacyEnabled){return local==null||!local.local()||legacyEnabled;}
    public Theme theme(Theme requested){return local==null?requested:local.theme(requested);}
    public void withSceneIf(boolean needed,Theme theme,Runnable content){if(needed)withScene(theme,content);else try(RenderState state=new RenderState()){content.run();}}
    public void withScene(Theme theme,Runnable content){withScene(theme,-1,content);}
    /** Explicit local card designs reuse a scene with their selected material, without drawing a group rectangle. */
    public void withScene(Theme theme,int surfaceStyle,Runnable content){
        theme=theme(theme);
        UiEffects.SurfacePolicy policy=UiEffects.surface(local,theme,true,true,false,0,surfaceStyle);UiEffects.SceneContext parent=UiEffects.scene();
        if(parent!=null&&parent.clean>0){try(UiEffects.SceneContext scope=UiEffects.scene(parent.clean,parent.soft,parent.blurred,theme,policy,parent.replacing,parent.radius)){content.run();}return;}
        if(!OpenGlHelper.shadersSupported||!OpenGlHelper.isFramebufferEnabled()){content.run();return;}
        Minecraft mc=Minecraft.getMinecraft();boolean blur=policy.blur;
        try(RenderState state=new RenderState()){
            ensureBuffers(mc.displayWidth,mc.displayHeight,false,blur,true);state.bindTarget();texture(0,buffers[4].framebufferTexture);glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,mc.displayWidth,mc.displayHeight);
            if(blur)GaussianBlur.renderScene(buffers[1],buffers[3],buffers[4].framebufferTexture,UiEffects.blurRadius(5)*new ScaledResolution(mc).getScaleFactor());
            state.bindTarget();prepare();state.restoreClip();try(UiEffects.SceneContext scope=UiEffects.scene(buffers[4].framebufferTexture,blur?buffers[3].framebufferTexture:buffers[4].framebufferTexture,blur,theme,policy,false,UiEffects.blurRadius(5))){content.run();}
        }
    }
    /** Text-only path also owns/restores the matrices and state used by font rendering. */
    public void text(Runnable text) {
        Minecraft mc=Minecraft.getMinecraft();
        try (RenderState state=new RenderState()) {
            ScaledResolution sr=new ScaledResolution(mc);
            glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);
            glMatrixMode(GL_MODELVIEW); glLoadIdentity(); prepare(); text.run();
        }
    }

    private void ensureBuffers(int width,int height,boolean glow,boolean blur,boolean snapshot) {
        if(bufferWidth!=width||bufferHeight!=height){close();bufferWidth=width;bufferHeight=height;}
        if(effectsWidth!=width||effectsHeight!=height){releaseEffects();effectsWidth=width;effectsHeight=height;}
        for(int i=0;i<5;i++)if(i==0&&(glow||blur&&!snapshot)||i==1&&(glow||blur)||i==2&&glow||i==3&&blur||i==4&&snapshot){
            if(i<3){if(effects[i]==null)effects[i]=createBuffer(width,height);buffers[i]=effects[i];}
            else if(buffers[i]==null)buffers[i]=createBuffer(width,height);
        }
    }
    private static Framebuffer createBuffer(int width,int height){
        Framebuffer buffer=new Framebuffer(width,height,false);buffer.setFramebufferFilter(GL_LINEAR);texture(0,buffer.framebufferTexture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);return buffer;
    }
    /** Shader-free fallback: tessellates the same rounded contour, never attempts scene sampling. */
    private static void fallback(Shape shape,Theme theme,boolean background,float fillAlpha,float outlineAlpha){
        java.util.List<float[]> polygon=new java.util.ArrayList<>();
        for(int i=0;i<shape.corners.length;i+=4){float[] start=shape.tangent(i,true);float r=Math.abs(shape.corners[i+2]);double angle=Math.atan2(start[1]-shape.corners[i+1],start[0]-shape.corners[i]);for(int j=0;j<=8;j++){double a=angle+Math.signum(shape.corners[i+2])*Math.PI/2*j/8;polygon.add(new float[]{shape.corners[i]+(float)Math.cos(a)*r,shape.corners[i+1]+(float)Math.sin(a)*r});}}
        java.util.List<float[]> triangles=new java.util.ArrayList<>(polygon);
        glDisable(GL_TEXTURE_2D);GlStateManager.disableTexture2D();glEnable(GL_BLEND);GlStateManager.enableBlend();glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        java.awt.Color fill=theme.isCustom()?theme.getBackground():new java.awt.Color(12,14,20);glColor4f(fill.getRed()/255f,fill.getGreen()/255f,fill.getBlue()/255f,background?fillAlpha:0);
        int guard=triangles.size()*triangles.size();glBegin(GL_TRIANGLES);
        while(triangles.size()>2&&guard-->0){boolean ear=false;for(int i=0;i<triangles.size();i++){float[] a=triangles.get((i+triangles.size()-1)%triangles.size()),b=triangles.get(i),c=triangles.get((i+1)%triangles.size());float cross=cross(a,b,c);if(cross<=.00001f){if(Math.abs(cross)<.00001f){triangles.remove(i);ear=true;break;}continue;}boolean contains=false;for(float[] v:triangles)if(v!=a&&v!=b&&v!=c&&cross(a,b,v)>0&&cross(b,c,v)>0&&cross(c,a,v)>0){contains=true;break;}if(!contains){glVertex2f(a[0],a[1]);glVertex2f(b[0],b[1]);glVertex2f(c[0],c[1]);triangles.remove(i);ear=true;break;}}if(!ear)break;}
        glEnd();if(outlineAlpha>0){java.awt.Color edge=theme.getAccentColor();glColor4f(edge.getRed()/255f,edge.getGreen()/255f,edge.getBlue()/255f,outlineAlpha);glBegin(GL_LINE_LOOP);for(float[] v:polygon)glVertex2f(v[0],v[1]);glEnd();}
        GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
    }
    private static float cross(float[] a,float[] b,float[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    private static void color(ShaderUtil shader,String name,int rgb) { shader.setUniformf(name,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,(rgb>>>24)/255f); }
    private static void texture(int unit,int id) { GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit); GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); GlStateManager.bindTexture(id); glBindTexture(GL_TEXTURE_2D,id); }
    private static void target(Framebuffer target) {
        target.bindFramebuffer(true);
        prepare();
        // Other legacy HUD paths clear through raw GL. Force the value as well as its state cache.
        GlStateManager.clearColor(0,0,0,0);
        glClearColor(0,0,0,0);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    private static void prepare() {
        glDisable(GL_DEPTH_TEST); GlStateManager.disableDepth();
        glDisable(GL_ALPHA_TEST); GlStateManager.disableAlpha();
        glDisable(GL_LIGHTING); GlStateManager.disableLighting();GlStateManager.disableFog();glDisable(GL_FOG);
        glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND); GlStateManager.disableBlend();
        glColorMask(true,true,true,true); GlStateManager.colorMask(true,true,true,true);
        texture(0,0); glEnable(GL_TEXTURE_2D); GlStateManager.enableTexture2D();
        glColor4f(1,1,1,1); GlStateManager.color(1,1,1,1);
    }
    @Override public void close() {
        capturedClean=capturedSoft=0;
        boolean any=false;for(Framebuffer buffer:buffers)any|=buffer!=null;if(!any)return;
        try (RenderState state=new RenderState()) {
            for (int i=0;i<buffers.length;i++) if (buffers[i]!=null) { if(i>=3)buffers[i].deleteFramebuffer(); buffers[i]=null; }
        }
    }

}
