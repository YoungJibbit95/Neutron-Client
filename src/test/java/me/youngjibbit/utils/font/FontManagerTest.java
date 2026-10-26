package me.youngjibbit.utils.font;
import me.youngjibbit.utils.render.RenderDiagnostics;
import org.junit.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Metadata/file ownership checks deliberately run without an OpenGL context. */
public class FontManagerTest {
 static class Manager extends FontManager {
  final Map<String,Integer> opens=new HashMap<>(),closes=new HashMap<>();boolean corrupt;
  @Override protected InputStream openFont(String file){opens.put(file,opens.getOrDefault(file,0)+1);InputStream raw=corrupt&&file.equals("Poppins-Regular.ttf")?new ByteArrayInputStream(new byte[]{1,2,3}):super.openFont(file);if(raw==null)return null;return new FilterInputStream(raw){@Override public void close()throws IOException{super.close();closes.put(file,closes.getOrDefault(file,0)+1);}};}
 }
 @Before public void before(){RenderDiagnostics.enabled=true;RenderDiagnostics.reset();}
 @After public void after(){RenderDiagnostics.enabled=false;}
 @Test public void constructionAndMetricsNeverTouchGpu(){try(Manager m=new Manager()){assertSame(m,m.getInstance());assertTrue(m.opens.isEmpty());assertEquals(0,RenderDiagnostics.fontRendererCreates);TTFFontRenderer font=m.getFont("Poppins-Regular 18");assertTrue(font.getWidth("CPU \u00a7lbold \u00a7oitalic")>0);assertTrue(font.getHeight("CPU")>0);assertEquals(0,RenderDiagnostics.glyphTexturesCreated);assertEquals(0,RenderDiagnostics.framebufferCreates);}}
 @Test public void sizesShareOneClosedFileAndUnknownKeysKeepFallback(){try(Manager m=new Manager()){TTFFontRenderer a=m.getFont("Poppins-Regular 30");assertSame(a,m.getFont("Poppins-Regular 30"));assertNotSame(a,m.getFont("Poppins-Regular 20"));assertEquals(Integer.valueOf(1),m.opens.get("Poppins-Regular.ttf"));assertSame(m.getFont("unknown"),m.getFont("Poppins-Regular 32"));m.getFont("SFProText-Medium 20");assertEquals(Integer.valueOf(1),m.opens.get("SFProRounded-Medium.ttf"));assertEquals(m.opens,m.closes);}}
 @Test public void allRegisteredFamiliesRemainAvailable(){try(Manager m=new Manager()){TTFFontRenderer fallback=m.getFont("unknown");for(String face:Arrays.asList("Poppins-Regular","Poppins-Medium","Poppins-Bold","MiSansLatinVF","Product-Sans-Regular","Product-Sans-Medium","Product-Sans-Bold","Inter-Regular","Inter-Medium","Inter-Bold","SFProText-Regular","SFProText-Medium","SFProText-Bold","DmSansRegular","DmSansMedium","DmSansBold","TenoriteRegular","TenoriteBold","KhmerOureang","PoeVeticaUi","Serif","VactorySans","Boldena","icon","icon1","iOSIcons"))assertNotSame(face,fallback,m.getFont(face+" 18"));assertEquals(26,m.opens.size());assertEquals(m.opens,m.closes);assertEquals(0,RenderDiagnostics.glyphTexturesCreated);}}
 @Test public void corruptFaceDoesNotDisableOtherFamilies(){try(Manager m=new Manager()){m.corrupt=true;TTFFontRenderer fallback=m.getFont("unknown");assertSame(fallback,m.getFont("Poppins-Regular 18"));assertSame(fallback,m.getFont("Poppins-Regular 20"));assertEquals(Integer.valueOf(1),m.opens.get("Poppins-Regular.ttf"));assertNotSame(fallback,m.getFont("Product-Sans-Regular 20"));assertEquals(m.opens,m.closes);}}
}
