package me.youngjibbit.theme;
import me.youngjibbit.utils.Theme;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class ThemeModelTest {
 @Rule public TemporaryFolder temporary=new TemporaryFolder();
 private ThemeCatalog previous;
 @Before public void before(){previous=ThemeCatalog.global();}
 @After public void after(){ThemeCatalog.install(previous);}
 private Path file()throws IOException{return temporary.newFolder().toPath().resolve("themes.json");}
 static Theme custom(String id,String name){return Theme.custom(id,name,Arrays.asList(new Color(10,20,30,40),new Color(200,180,160,120)),true,false,.4f,new Color(2,3,4,80),new Color(250,240,230,220),new Color(90,80,70,60),.65f,6,.7f,1.4f);}
 static String id(){return "custom:"+UUID.randomUUID();}
 private String read(Path p)throws IOException{return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
 @Test public void allLegacyPresetsKeepTheirNamesAndExplicitLocalColors(){
  assertEquals(19,Theme.values().length);assertSame(Theme.SynthWave,Theme.valueOf("SynthWave"));assertEquals(0xff0077ff,Theme.SynthWave.getPrimaryGradientRGB());
  assertEquals("Cotton_candy",Theme.themes[1]);Theme[] copy=Theme.values();copy[0]=null;assertNotNull(Theme.values()[0]);
  ThemeCatalog catalog=new ThemeCatalog(null);ThemeCatalog.install(catalog);catalog.select("sYnThWaVe");assertSame(Theme.SynthWave,Theme.getDefaultTheme());assertEquals(0xff0eff00,Theme.Neon.getPrimaryGradientRGB());
 }
 @Test public void rgbaPropertiesRoundTripAndRenameKeepsId()throws Exception{
  Path p=file();ThemeCatalog c=new ThemeCatalog(new ThemeStore(p));Theme t=custom(id(),"Example");c.save(t);c.select(t.name());
  Theme loaded=new ThemeCatalog(new ThemeStore(p)).find(t.name());assertEquals(t.getGradientColors(),loaded.getGradientColors());assertEquals(t.getBackground(),loaded.getBackground());assertEquals(t.getTextColor(),loaded.getTextColor());assertEquals(t.getAccentColor(),loaded.getAccentColor());assertTrue(loaded.isVertical());assertEquals(1.4f,loaded.getOutlineWidth(),.0001);
  Theme renamed=custom(t.name(),"Renamed");c.save(renamed);assertEquals(t.name(),c.selectedReference());assertEquals("Renamed",c.current().getDisplayName());assertFalse(read(p).contains("selected"));
 }
 @Test public void previewAndCancelDoNotWriteOrChangeSelection()throws Exception{
  Path p=file();ThemeCatalog c=new ThemeCatalog(new ThemeStore(p));ThemeCatalog.install(c);c.select("SynthWave");AtomicInteger writes=new AtomicInteger();
  try(ThemeDraft d=new ThemeDraft(c,ref->{writes.incrementAndGet();c.select(ref);})){
   d.duplicate();d.first.setColor(new Color(1,2,3,4));d.speed.setValue(.8);assertTrue(c.isPreviewing());assertEquals(4,c.current().getPrimaryGradientColor().getAlpha());assertEquals("SynthWave",c.selectedReference());assertFalse(Files.exists(p));d.cancel();
  }assertSame(Theme.SynthWave,c.current());assertEquals(0,writes.get());
 }
 @Test public void saveCommitsOnceAndResetRestoresDraftBaseline()throws Exception{
  Path p=file();ThemeCatalog c=new ThemeCatalog(new ThemeStore(p));AtomicInteger selections=new AtomicInteger();String ref;
  try(ThemeDraft d=new ThemeDraft(c,r->{selections.incrementAndGet();c.select(r);})){
   d.duplicate();ref=d.reference();Color baseline=d.first.getValue();d.first.setColor(Color.RED);d.reset();assertEquals(baseline,d.first.getValue());d.title.setValue("Saved");d.save();
  }assertEquals(1,selections.get());assertEquals(ref,c.selectedReference());assertEquals("Saved",c.find(ref).getDisplayName());assertTrue(Files.exists(p));
 }
 @Test public void deletingActiveCustomUsesDefinedFallback()throws Exception{
  ThemeCatalog c=new ThemeCatalog(new ThemeStore(file()));Theme t=custom(id(),"Delete");c.save(t);c.select(t.name());try(ThemeDraft d=new ThemeDraft(c,c::select)){d.delete();}assertSame(Theme.PinkLemonade,c.current());assertEquals("PinkLemonade",c.selectedReference());assertNull(c.find(t.name()));
 }
 @Test public void presetsCannotBeOverwrittenOrDeleted()throws Exception{
  ThemeCatalog c=new ThemeCatalog(new ThemeStore(file()));try{c.save(Theme.Neon);fail();}catch(IOException expected){}try{c.delete("Neon");fail();}catch(IOException expected){}assertSame(Theme.Neon,c.find("Neon"));
 }
 @Test public void malformedFutureAndDuplicateFilesRemainUntouched()throws Exception{
  for(String bytes:new String[]{"{bad","{\"version\":99,\"themes\":[]}","{\"version\":1,\"version\":1,\"themes\":[]}"}){Path p=file();Files.write(p,bytes.getBytes(StandardCharsets.UTF_8));ThemeStore store=new ThemeStore(p);assertTrue(store.load().isEmpty());assertTrue(store.isReadOnly());try{store.save(Collections.singletonList(custom(id(),"No overwrite")));fail();}catch(IOException expected){}assertEquals(bytes,read(p));}
 }
 @Test public void externalChangeIsPreserved()throws Exception{
  Path p=file();ThemeStore store=new ThemeStore(p);store.load();store.save(Collections.emptyList());String bytes="{\"version\":99,\"themes\":[]}";Files.write(p,bytes.getBytes(StandardCharsets.UTF_8));try{store.save(Collections.emptyList());fail();}catch(IOException expected){}assertTrue(store.isReadOnly());assertEquals(bytes,read(p));
 }
 @Test public void failedAtomicReplacementDoesNotPublishDefinition()throws Exception{
  Path p=file();ThemeStore store=new ThemeStore(p){@Override protected void replace(Path from,Path to)throws IOException{throw new IOException("Injected replace failure");}};ThemeCatalog c=new ThemeCatalog(store);Theme t=custom(id(),"Failure");try{c.save(t);fail();}catch(IOException expected){}assertNull(c.find(t.name()));assertFalse(Files.exists(p));try(java.util.stream.Stream<Path> files=Files.list(p.getParent())){assertEquals(0,files.count());}
 }
 @Test public void unknownCustomReferenceIsRetainedWhileDisplayingFallback(){ThemeCatalog c=new ThemeCatalog(null);String id=id();c.select(id);assertEquals(id,c.selectedReference());assertSame(Theme.PinkLemonade,c.current());}
 @Test public void invalidPropertiesCannotEnterModel(){for(String name:new String[]{"","bad\nname"})try{custom(id(),name);fail();}catch(IllegalArgumentException expected){}try{custom("custom:bad","Name");fail();}catch(IllegalArgumentException expected){}try{Theme.custom(id(),"Name",Arrays.asList(Color.RED,Color.BLUE),false,false,Float.NaN,Color.BLACK,Color.WHITE,Color.RED,1,3,1,1);fail();}catch(IllegalArgumentException expected){}}
}
