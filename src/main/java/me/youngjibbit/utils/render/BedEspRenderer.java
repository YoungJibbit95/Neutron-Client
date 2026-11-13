package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL20;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

/** A single reusable mask for all bed pairs. Composites into the caller's actual world-overlay target. */
public final class BedEspRenderer implements AutoCloseable {
    public static final class Entry {
        public final AxisAlignedBB bounds;
        public final int primary,secondary;
        public Entry(AxisAlignedBB bounds,int primary,int secondary){this.bounds=bounds;this.primary=primary;this.secondary=secondary;}
    }
    public static final class Style {
        public String mode="Glow";
        public float opacity=.13f,glow=.8f,radius=5f,lineWidth=1.25f;
        public boolean useTheme=true;
    }
    private Framebuffer mask;
    private final ContourMaskRenderer contour=new ContourMaskRenderer();
    private boolean failed;

    public void render(List<Entry> entries,double cameraX,double cameraY,double cameraZ,Style style){
        if(entries==null||entries.isEmpty()||style==null||!Double.isFinite(cameraX+cameraY+cameraZ))return;
        Theme theme=Theme.getDefaultTheme();
        try(RenderState state=WorldVisuals.begin()){
            prepare();
            for(int i=0;i<Math.min(64,entries.size());i++){
                Entry entry=entries.get(i);if(!valid(entry))continue;
                box(entry,cameraX,cameraY,cameraZ,clamp(style.opacity,0,.7f),false);
                glLineWidth(clamp(style.lineWidth,.5f,4));
                box(entry,cameraX,cameraY,cameraZ,.9f,true);
            }
            if("Outline".equals(style.mode)||!UiEffects.glowEnabled()||!UiEffects.effectsSupported()||failed)return;
            Minecraft mc=Minecraft.getMinecraft();
            try{
                // glClear obeys scissor and color masks, including during framebuffer construction.
                glDisable(GL_SCISSOR_TEST);glDisable(GL_STENCIL_TEST);glColorMask(true,true,true,true);
                ensure(mc.displayWidth,mc.displayHeight);
                mask.bindAndClear();prepare();
                for(int i=0;i<Math.min(64,entries.size());i++){
                    Entry entry=entries.get(i);if(valid(entry))box(entry,cameraX,cameraY,cameraZ,1,false);
                }
                state.bindTarget();
                state.restoreClip();
                state.restoreColorMask();
                String mode="Flame".equals(style.mode)?"Flame":"Lines".equals(style.mode)?"Lines":"Neon";
                contour.draw(theme,mode,mask.framebufferTexture,mc.displayWidth,mc.displayHeight,
                        GuiResolution.current().getScaleFactor(),clamp(style.radius,1,16),
                        UiEffects.glowAlpha(clamp(style.glow,0,2)),style.useTheme,0,0,0);
            }catch(RuntimeException unavailable){
                failed=true;
                System.err.println("BedESP contour unavailable; keeping the geometric outline: "+unavailable.getMessage());
                release();
            }
        }
    }
    private void ensure(int width,int height){
        if(width<=0||height<=0)throw new IllegalArgumentException("Empty BedESP viewport");
        if(mask!=null&&(mask.framebufferWidth!=width||mask.framebufferHeight!=height)){mask.deleteFramebuffer();mask=null;}
        if(mask==null){mask=new Framebuffer(width,height,false);mask.setFramebufferFilter(GL_LINEAR);}
    }
    private static void prepare(){
        GL20.glUseProgram(0);GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);
        GlStateManager.disableLighting();glDisable(GL_LIGHTING);glDisable(GL_FOG);
        GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
        GlStateManager.disableCull();glDisable(GL_CULL_FACE);GlStateManager.depthMask(false);glDepthMask(false);
        GlStateManager.enableBlend();glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glShadeModel(GL_SMOOTH);
    }
    private static boolean valid(Entry entry){
        if(entry==null||entry.bounds==null)return false;
        AxisAlignedBB b=entry.bounds;
        return Double.isFinite(b.minX+b.minY+b.minZ+b.maxX+b.maxY+b.maxZ)
                &&b.maxX>b.minX&&b.maxY>b.minY&&b.maxZ>b.minZ;
    }
    private static final int[] FACES={0,1,3,2,4,6,7,5,0,4,5,1,2,3,7,6,0,2,6,4,1,5,7,3};
    private static final int[] EDGES={0,1,1,3,3,2,2,0,4,5,5,7,7,6,6,4,0,4,1,5,2,6,3,7};
    private static void box(Entry entry,double cx,double cy,double cz,float alpha,boolean outline){
        if(alpha<=0)return;
        AxisAlignedBB b=entry.bounds;int[] vertices=outline?EDGES:FACES;
        glBegin(outline?GL_LINES:GL_QUADS);
        for(int corner:vertices){
            int color=Theme.interpolateRGB(entry.primary,entry.secondary,
                    ((corner&1)==0?0:.4f)+((corner&4)==0?0:.6f));
            glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,alpha);
            glVertex3d(((corner&1)==0?b.minX:b.maxX)-cx,((corner&4)==0?b.minY:b.maxY)-cy,
                    ((corner&2)==0?b.minZ:b.maxZ)-cz);
        }
        glEnd();
    }
    private static float clamp(float value,float low,float high){return Float.isFinite(value)?Math.max(low,Math.min(high,value)):low;}
    private void release(){if(mask!=null){mask.deleteFramebuffer();mask=null;}contour.close();}
    @Override public void close(){
        if(mask!=null){try(RenderState ignored=new RenderState()){release();}}
        else contour.close();
        failed=false;
    }
}
