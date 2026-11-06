package me.youngjibbit.utils.font;

import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.TextEffects;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import static org.lwjgl.opengl.GL11.*;

/**
 * Neutron's lazy, Unicode-aware font renderer. Metrics never need a GL context.
 * Glyphs share padded atlases; a warmed string draws one batch per atlas run.
 */
public class TTFFontRenderer implements AutoCloseable {
    private static final int MAX_GLYPHS = 8192, MAX_PAGES = 32, PADDING = 8;
    private static final String CODES = "0123456789abcdefklmnor";
    private static final int[] COLORS = colors();
    private final Thread renderThread;
    private final Font[] faces = new Font[4], fallbacks = new Font[4];
    private final FontRenderContext context;
    private final Glyph[][] latin = new Glyph[4][256];
    private final Map<Integer,Glyph> unicode = new HashMap<>();
    private final List<Page>[] pages = new List[]{new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>()};
    private final Set<Integer> ownedTextures = new LinkedHashSet<>();
    private final float ascent, lineHeight;
    private final int pageSize;
    private int measuredGlyphs;
    private volatile boolean closed;
    private boolean textEffects=true;
    TTFFontRenderer textEffects(boolean enabled){textEffects=enabled;return this;}

    public TTFFontRenderer(Font font) { this(font,Thread.currentThread()); }
    public TTFFontRenderer(Font font,Thread owner) { this(font,owner,true); }
    private TTFFontRenderer(Font font,Thread owner,boolean antialias) {
        if(font==null||owner==null)throw new IllegalArgumentException("Font and owner are required");
        renderThread=owner;
        context=new FontRenderContext(null,antialias,true);
        for(int style=0;style<4;style++){
            faces[style]=font.deriveFont(style);
            fallbacks[style]=new Font(Font.DIALOG,style,font.getSize()).deriveFont(font.getSize2D());
        }
        LineMetrics metrics=faces[0].getLineMetrics("Ag",context);
        ascent=metrics.getAscent()/2;
        lineHeight=metrics.getHeight()/2;
        pageSize=font.getSize2D()>32?512:256;
        if(RenderDiagnostics.enabled)RenderDiagnostics.fontRendererCreates++;
    }

    /** Source-compatible legacy entry points now use the same lazy atlas ownership. */
    public TTFFontRenderer(ExecutorService executor,ConcurrentLinkedQueue<TextureData> queue,Font font){this(font);}
    public TTFFontRenderer(ExecutorService executor,ConcurrentLinkedQueue<TextureData> queue,Font font,int count){this(font);}
    public TTFFontRenderer(ExecutorService executor,ConcurrentLinkedQueue<TextureData> queue,Font font,boolean antialias){this(font,Thread.currentThread(),antialias);}
    public TTFFontRenderer(ExecutorService executor,ConcurrentLinkedQueue<TextureData> queue,Font font,int count,boolean antialias){this(font,Thread.currentThread(),antialias);}

    private void checkOpen(){if(closed)throw new IllegalStateException("Font renderer is closed");}
    private void checkThread(){checkOpen();if(Thread.currentThread()!=renderThread)throw new IllegalStateException("Glyph drawing requires the render thread");}

    private synchronized Glyph glyph(int codepoint,int style) {
        checkOpen();
        if(!Character.isValidCodePoint(codepoint)||codepoint>=0xd800&&codepoint<=0xdfff)codepoint=0xfffd;
        Glyph found=codepoint<256?latin[style][codepoint]:unicode.get((style<<21)|codepoint);
        if(found!=null)return found;
        if(measuredGlyphs>=MAX_GLYPHS&&codepoint!='?')return glyph('?',style);
        Font face=faces[style].canDisplay(codepoint)?faces[style]:fallbacks[style];
        GlyphVector vector=face.createGlyphVector(context,Character.toChars(codepoint));
        Rectangle bounds=vector.getPixelBounds(context,0,0);
        float advance=(float)vector.getGlyphPosition(vector.getNumGlyphs()).getX();
        Glyph result=new Glyph(codepoint,style,face,bounds,advance);
        if(codepoint<256)latin[style][codepoint]=result;else unicode.put((style<<21)|codepoint,result);
        measuredGlyphs++;
        return result;
    }

    private void upload(Glyph glyph) {
        if(glyph.page!=null||glyph.empty)return;
        checkThread();
        // Reserve a visible fallback before filling each style's bounded atlas set.
        if(glyph.codepoint!='?'&&pages[glyph.style].isEmpty())upload(glyph('?',glyph.style));
        Page page=null;
        for(Page candidate:pages[glyph.style])if(candidate.fits(glyph.width,glyph.height)){page=candidate;break;}
        if(page==null){
            if(pages[glyph.style].size()>=MAX_PAGES/4){
                Glyph replacement=glyph('?',glyph.style);
                if(replacement!=glyph){upload(replacement);glyph.copyImage(replacement);}
                return;
            }
            int size=pageSize;
            while(size<glyph.width+4||size<glyph.height+4)size*=2;
            page=new Page(size);
            pages[glyph.style].add(page);
        }
        int[] position=page.reserve(glyph.width,glyph.height);
        BufferedImage raster=new BufferedImage(glyph.width,glyph.height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=raster.createGraphics();
        try{
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,context.isAntiAliased()?RenderingHints.VALUE_TEXT_ANTIALIAS_ON:RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.drawGlyphVector(glyph.face.createGlyphVector(context,Character.toChars(glyph.codepoint)),-glyph.left,-glyph.top);
        }finally{graphics.dispose();}
        ByteBuffer pixels=BufferUtils.createByteBuffer(glyph.width*glyph.height*4);
        for(int y=0;y<glyph.height;y++)for(int x=0;x<glyph.width;x++){
            // White RGB in transparent padding avoids dark filtering fringes.
            pixels.put((byte)255).put((byte)255).put((byte)255).put((byte)(raster.getRGB(x,y)>>>24));
        }
        pixels.flip();
        glBindTexture(GL_TEXTURE_2D,page.texture);
        glTexSubImage2D(GL_TEXTURE_2D,0,position[0],position[1],glyph.width,glyph.height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        glyph.page=page;
        glyph.u=position[0]/(float)page.size;glyph.v=position[1]/(float)page.size;
        glyph.u2=(position[0]+glyph.width)/(float)page.size;glyph.v2=(position[1]+glyph.height)/(float)page.size;
        if(RenderDiagnostics.enabled)RenderDiagnostics.glyphUploads++;
    }

    private void prepare(String text) {
        Format format=new Format(-1,false);
        boolean needsUpload=false;
        for(int index=0;index<text.length();){
            int cp=text.codePointAt(index);index+=Character.charCount(cp);
            if(cp=='\u00a7'&&index<text.length()&&format.accept(text.charAt(index))){index++;continue;}
            if(Character.isISOControl(cp))continue;
            Glyph glyph=glyph(format.obfuscated?obfuscated(cp):cp,format.style);
            if((format.underline||format.strike)&&glyph('_',format.style).page==null){needsUpload=true;break;}
            if(!glyph.empty&&glyph.page==null){needsUpload=true;break;}
        }
        if(!needsUpload)return;
        // Cold uploads may bind textures; the active gradient shader and caller's state survive.
        try(RenderState ignored=new RenderState()){
            format=new Format(-1,false);
            for(int index=0;index<text.length();){
                int cp=text.codePointAt(index);index+=Character.charCount(cp);
                if(cp=='\u00a7'&&index<text.length()&&format.accept(text.charAt(index))){index++;continue;}
                if(Character.isISOControl(cp))continue;
                if(format.underline||format.strike)upload(glyph('_',format.style));
                upload(glyph(format.obfuscated?obfuscated(cp):cp,format.style));
            }
        }
    }

    public int drawString(String text,float x,float y,int color){return render(text,x,y,color,false);}
    public void drawCenteredString(String text,float x,float y,int color){render(text,x-getWidth(text)/2,y,color,false);}
    public void drawStringWithShadow(String text,double x,double y,int color){
        render(text,(float)x+.5f,(float)y+.5f,color,true);
        render(text,(float)x,(float)y,color,false);
    }
    private int render(String text,float x,float y,int color,boolean shadow) {
        if(text==null||text.isEmpty())return (int)x;
        checkThread();
        if((color>>>24)==0)return (int)(x+getWidth(text));
        prepare(text);
        int previousProgram=me.youngjibbit.utils.render.FontInk.begin(textEffects,shadow);
        boolean halo=previousProgram>=0&&textEffects&&!shadow&&!TextEffects.isCapturing()&&me.youngjibbit.utils.render.FontInk.enabled();
        float inset=halo?0:PADDING-2;
        Format format=new Format(color,shadow);
        float pen=x,baseline=y+ascent;
        int bound=-1;
        boolean batch=false;
        // Raw state is scoped without changing GlStateManager's cached entry state.
        // Avoid a full framebuffer/matrix snapshot for every warmed label.
        glPushAttrib(GL_ENABLE_BIT|GL_COLOR_BUFFER_BIT|GL_CURRENT_BIT|GL_TEXTURE_BIT);
        try{
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            glEnable(GL_TEXTURE_2D);glEnable(GL_BLEND);
            glDisable(GL_ALPHA_TEST);
            if(TextEffects.isCapturing())GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            else glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            tint(format.color);
            for(int index=0;index<text.length();){
                int cp=text.codePointAt(index);index+=Character.charCount(cp);
                if(cp=='\u00a7'&&index<text.length()&&format.accept(text.charAt(index))){index++;tint(format.color);continue;}
                if(cp=='\n'){pen=x;baseline+=lineHeight;continue;}
                if(cp=='\t'){pen+=glyph(' ',format.style).advance*2;continue;}
                if(Character.isISOControl(cp))continue;
                Glyph glyph=glyph(format.obfuscated?obfuscated(cp):cp,format.style);
                Page inkPage=glyph.page;
                if(inkPage==null&&(format.underline||format.strike))inkPage=glyph('_',format.style).page;
                if(inkPage!=null){
                    if(bound!=inkPage.texture){
                        if(batch){glEnd();batch=false;}
                        glBindTexture(GL_TEXTURE_2D,inkPage.texture);
                        glBegin(GL_QUADS);batch=true;bound=inkPage.texture;
                        if(RenderDiagnostics.enabled)RenderDiagnostics.fontBatches++;
                    }
                    if(halo){
                        GL13.glMultiTexCoord4f(GL13.GL_TEXTURE1,glyph.u,glyph.v,glyph.u2,glyph.v2);
                        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE2,1f/inkPage.size,1f/inkPage.size);
                    }
                    if(!glyph.empty)quad(pen+(glyph.left+inset)/2f,baseline+(glyph.top+inset)/2f,(glyph.width-inset*2)/2f,(glyph.height-inset*2)/2f,glyph.u+inset/inkPage.size,glyph.v+inset/inkPage.size,glyph.u2-inset/inkPage.size,glyph.v2-inset/inkPage.size);
                    if(format.underline||format.strike){
                        float uv=1.5f/inkPage.size;
                        if(format.underline)quad(pen,baseline+1,glyph.advance/2,.6f,uv,uv,uv,uv);
                        if(format.strike)quad(pen,baseline-ascent*.35f,glyph.advance/2,.6f,uv,uv,uv,uv);
                    }
                }
                pen+=glyph.advance/2;
            }
            return Math.round(pen);
        }finally{if(batch)glEnd();glPopAttrib();me.youngjibbit.utils.render.FontInk.end(previousProgram);}
    }

    /** Width and draw use the same codepoint/style/advance rules, including multiline text. */
    public float getWidth(String text) {
        checkOpen();if(text==null||text.isEmpty())return 0;
        Format format=new Format(-1,false);float line=0,max=0;
        for(int index=0;index<text.length();){
            int cp=text.codePointAt(index);index+=Character.charCount(cp);
            if(cp=='\u00a7'&&index<text.length()&&format.accept(text.charAt(index))){index++;continue;}
            if(cp=='\n'){max=Math.max(max,line);line=0;continue;}
            if(cp=='\t'){line+=glyph(' ',format.style).advance*2;continue;}
            if(!Character.isISOControl(cp))line+=glyph(format.obfuscated?obfuscated(cp):cp,format.style).advance/2;
        }
        return Math.max(max,line);
    }
    public float getHeight(String text) {
        checkOpen();if(text==null||text.isEmpty())return 0;
        int lines=1;for(int i=0;i<text.length();i++)if(text.charAt(i)=='\n')lines++;
        return lineHeight*lines;
    }
    public float getLineHeight(){checkOpen();return lineHeight;}

    @Override public synchronized void close() {
        if(closed)return;checkThread();
        for(int texture:ownedTextures){
            GlStateManager.deleteTexture(texture);
            if(RenderDiagnostics.enabled)RenderDiagnostics.glyphTexturesDeleted++;
        }
        ownedTextures.clear();for(List<Page> style:pages)style.clear();
        for(Glyph[] style:latin)Arrays.fill(style,null);unicode.clear();closed=true;
    }

    private static int obfuscated(int cp){return cp==' '?cp:'A'+Math.floorMod(cp+1,26);}
    private static void tint(int rgba){glColor4f((rgba>>16&255)/255f,(rgba>>8&255)/255f,(rgba&255)/255f,(rgba>>>24)/255f);}
    private static void quad(float x,float y,float w,float h,float u,float v,float u2,float v2){
        glTexCoord2f(u,v);glVertex2f(x,y);
        glTexCoord2f(u,v2);glVertex2f(x,y+h);
        glTexCoord2f(u2,v2);glVertex2f(x+w,y+h);
        glTexCoord2f(u2,v);glVertex2f(x+w,y);
    }
    private static int[] colors(){
        int[] colors=new int[16];
        for(int i=0;i<16;i++){int light=(i>>3&1)*85,r=(i>>2&1)*170+light,g=(i>>1&1)*170+light,b=(i&1)*170+light;if(i==6)r+=85;colors[i]=r<<16|g<<8|b;}
        return colors;
    }
    private static final class Format {
        final int original;final boolean shadow;
        int style,color;boolean underline,strike,obfuscated;
        Format(int color,boolean shadow){original=color;this.shadow=shadow;setColor(color);}
        void setColor(int next){color=shadow?(next&0xff000000)|((next&0xfcfcfc)>>2):next;}
        boolean accept(char code){
            int index=CODES.indexOf(Character.toLowerCase(code));if(index<0)return false;
            if(index<16){style=0;underline=strike=obfuscated=false;setColor((original&0xff000000)|COLORS[index]);}
            else if(index==16)obfuscated=true;
            else if(index==17)style|=Font.BOLD;
            else if(index==18)strike=true;
            else if(index==19)underline=true;
            else if(index==20)style|=Font.ITALIC;
            else{style=0;underline=strike=obfuscated=false;setColor(original);}
            return true;
        }
    }
    private static final class Glyph {
        final int codepoint,style;final Font face;final float advance;final boolean empty;
        int left,top,width,height;Page page;float u,v,u2,v2;
        Glyph(int cp,int style,Font face,Rectangle bounds,float advance){
            codepoint=cp;this.style=style;this.face=face;this.advance=advance;
            empty=bounds.width==0||bounds.height==0;
            left=bounds.x-PADDING;top=bounds.y-PADDING;width=Math.max(1,bounds.width+PADDING*2);height=Math.max(1,bounds.height+PADDING*2);
        }
        void copyImage(Glyph other){page=other.page;left=other.left;top=other.top;width=other.width;height=other.height;u=other.u;v=other.v;u2=other.u2;v2=other.v2;}
    }
    private final class Page {
        final int texture,size;int x=4,y=0,rowHeight=4;
        Page(int size){
            this.size=size;texture=glGenTextures();ownedTextures.add(texture);
            glBindTexture(GL_TEXTURE_2D,texture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            ByteBuffer blank=BufferUtils.createByteBuffer(size*size*4);
            for(int i=0;i<size*size;i++)blank.put((byte)255).put((byte)255).put((byte)255).put((byte)(i/size<3&&i%size<3?255:0));
            blank.flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,size,size,0,GL_RGBA,GL_UNSIGNED_BYTE,blank);
            if(RenderDiagnostics.enabled)RenderDiagnostics.glyphTexturesCreated++;
        }
        boolean fits(int w,int h){return w<=size&&h<=size&&(x+w<=size?y+h<=size:y+rowHeight+h<=size);}
        int[] reserve(int w,int h){if(x+w>size){x=0;y+=rowHeight;rowHeight=0;}int[] at={x,y};x+=w;rowHeight=Math.max(rowHeight,h);return at;}
    }
}
