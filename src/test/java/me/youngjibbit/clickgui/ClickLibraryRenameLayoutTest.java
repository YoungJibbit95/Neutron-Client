package me.youngjibbit.clickgui;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.clickgui.library.ConfigLibrary;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.clickgui.utils.ClipRect;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

public class ClickLibraryRenameLayoutTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void compactRenameIsSeparateFromTheNameFieldAndClickable() throws Exception {
        String previous=ThemeMode.selected();
        try {
            for(String design:new String[]{ThemeMode.LEGACY,ThemeMode.APPROACH_TWO}) {
                ThemeMode.select(design);
                for(int[] size:new int[][]{{320,240},{480,270},{640,360}}) {
                    Path dir=temporary.newFolder().toPath();Files.write(dir.resolve("Saved.json"),new byte[]{123,125});
                    ClickLibraryScreen screen=new ClickLibraryScreen(null,false,
                            new ConfigManager(dir,Collections.emptyList(),()->false,message->{}),new ThemeCatalog(null));
                    screen.width=size[0];screen.height=size[1];
                    Method layout=ClickLibraryScreen.class.getDeclaredMethod("layout");layout.setAccessible(true);layout.invoke(screen);
                    ClipRect rename=screen.actionBounds(4),name=screen.nameBounds();
                    assertTrue("rename must not cover the editable name",rename.intersect(name).isEmpty());
                    assertTrue(rename.intersect(screen.actionBounds(0)).isEmpty());
                    assertTrue(rename.intersect(screen.actionBounds(1)).isEmpty());
                    assertTrue(rename.intersect(screen.actionBounds(2)).isEmpty());
                    ClipRect item=screen.itemBounds(0);
                    screen.mouseClicked((int)(item.x+4),(int)(item.y+4),0);
                    screen.mouseClicked((int)(rename.x+rename.width/2),(int)(rename.y+rename.height/2),0);
                    Field active=ClickLibraryScreen.class.getDeclaredField("renaming");active.setAccessible(true);
                    assertTrue("mouse click must reach Rename",active.getBoolean(screen));
                }
            }
        } finally { ThemeMode.select(previous); }
    }

    @Test public void legacyGridWrapsAndItsBottomToolbarNeverCoversContent()throws Exception {
        String previous=ThemeMode.selected();ThemeMode.select(ThemeMode.LEGACY);
        try {
            Path dir=temporary.newFolder().toPath();
            for(int i=0;i<12;i++)Files.write(dir.resolve(String.format("Profile %02d.json",i)),new byte[]{123,125});
            for(int[] size:new int[][]{{320,224,1},{480,320,2},{854,480,4}}) {
                ClickLibraryScreen screen=screen(dir,size[0],size[1]);
                List<ClipRect> bounds=new ArrayList<>(Arrays.asList(screen.listBounds(),screen.searchBounds(),screen.nameBounds()));
                for(int id:new int[]{0,1,2,3,4,6,7,8})bounds.add(screen.actionBounds(id));
                for(int i=0;i<bounds.size();i++) {
                    ClipRect a=bounds.get(i);
                    assertTrue(a.width>0&&a.height>0&&a.x>=0&&a.y>=0&&a.x+a.width<=screen.width&&a.y+a.height<=screen.height);
                    for(int j=i+1;j<bounds.size();j++)assertTrue("Library fields and actions must be disjoint",a.intersect(bounds.get(j)).isEmpty());
                }
                ClipRect first=screen.itemBounds(0),last=screen.itemBounds(size[2]-1),next=screen.itemBounds(size[2]);
                assertEquals("Cards fill the first row",first.y,last.y,.01f);
                assertTrue("The next row starts below the first",next.y>first.y+first.height);
                assertTrue("Save belongs to the bottom toolbar",screen.actionBounds(0).y>screen.nameBounds().y+screen.nameBounds().height);
                assertEquals(screen.actionBounds(0).y,screen.actionBounds(3).y,.01f);
                screen.onGuiClosed();
            }
        } finally { ThemeMode.select(previous); }
    }

    @Test public void renameReplacesTheSelectedNameAndPreservesBytesUntilConfirmed()throws Exception {
        String previous=ThemeMode.selected();ThemeMode.select(ThemeMode.LEGACY);
        try {
            Path dir=temporary.newFolder().toPath(),original=dir.resolve("Practice.json"),renamed=dir.resolve("New practice.json");
            byte[] bytes="{\"preserve\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(original,bytes);
            ClickLibraryScreen screen=screen(dir,640,360);
            click(screen,screen.itemBounds(0));click(screen,screen.actionBounds(4));type(screen,"New practice");
            screen.keyTyped('\0',Keyboard.KEY_RETURN);
            ConfigLibrary actions=(ConfigLibrary)field(screen,"configs");
            assertEquals(ConfigLibrary.Pending.RENAME,actions.pending());assertEquals("New practice",actions.target());
            assertArrayEquals(bytes,Files.readAllBytes(original));assertFalse(Files.exists(renamed));
            screen.keyTyped('\0',Keyboard.KEY_ESCAPE);assertEquals(ConfigLibrary.Pending.NONE,actions.pending());
            assertArrayEquals(bytes,Files.readAllBytes(original));
            click(screen,screen.actionBounds(4));type(screen,"New practice");
            screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);
            assertFalse(Files.exists(original));assertArrayEquals(bytes,Files.readAllBytes(renamed));
            assertEquals("New practice",actions.selected());assertEquals("New practice",field(screen,"name"));
            screen.onGuiClosed();
        } finally { ThemeMode.select(previous); }
    }

    @Test public void resizeKeepsTheRenameDraftAndStillRequiresConfirmation()throws Exception {
        String previous=ThemeMode.selected();ThemeMode.select(ThemeMode.LEGACY);
        try {
            Path dir=temporary.newFolder().toPath(),original=dir.resolve("Practice.json"),renamed=dir.resolve("Resized draft.json");
            byte[] bytes="{\"preserve\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(original,bytes);
            ClickLibraryScreen screen=screen(dir,854,480);
            try {
                click(screen,screen.itemBounds(0));click(screen,screen.actionBounds(4));type(screen,"Resized draft");
                screen.width=320;screen.height=224;screen.initGui();
                assertEquals(Boolean.TRUE,field(screen,"renaming"));assertEquals("Resized draft",field(screen,"renameBuffer"));
                assertEquals(-4,field(screen,"focus"));assertArrayEquals(bytes,Files.readAllBytes(original));assertFalse(Files.exists(renamed));
                screen.keyTyped('\0',Keyboard.KEY_RETURN);
                ConfigLibrary actions=(ConfigLibrary)field(screen,"configs");
                assertEquals(ConfigLibrary.Pending.RENAME,actions.pending());assertEquals("Resized draft",actions.target());
                assertFalse(Files.exists(renamed));
                screen.keyTyped('\0',Keyboard.KEY_ESCAPE);assertEquals(ConfigLibrary.Pending.NONE,actions.pending());
                assertArrayEquals(bytes,Files.readAllBytes(original));assertFalse(Files.exists(renamed));
            } finally { screen.onGuiClosed(); }
        } finally { ThemeMode.select(previous); }
    }

    @Test public void splitSaveFormFollowsRenameWithoutCoveringHeadingFieldOrFooter()throws Exception {
        String previous=ThemeMode.selected();ThemeMode.select(ThemeMode.APPROACH_TWO);
        try {
            for(boolean creative:new boolean[]{false,true})for(int[] size:new int[][]{{560,320},{640,320},{640,360},{854,480}}){
                Path dir=temporary.newFolder().toPath(),original=dir.resolve("Saved.json");
                byte[] bytes="{\"preserve\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.write(original,bytes);
                ClickLibraryScreen screen=modernScreen(dir,size[0],size[1],creative);
                try {
                    ClipRect rename=screen.actionBounds(4),section=screen.saveSectionBounds(),name=screen.nameBounds(),save=screen.actionBounds(0);
                    ClipRect load=screen.actionBounds(1),delete=screen.actionBounds(2);
                    assertTrue("Load must finish before Delete",load.y+load.height<=delete.y);
                    assertTrue("Delete must finish before Rename",delete.y+delete.height<=rename.y);
                    assertTrue("The save divider must follow Rename",rename.y+rename.height+4<=section.y-6);
                    assertTrue("The full heading must finish before the editable field",section.y+section.height+2<=name.y);
                    assertTrue("Save must follow the field with a visible gap",name.y+name.height+6<=save.y);
                    float windowBottom=(Float)field(screen,"y")+(Float)field(screen,"h");
                    assertTrue("Save must leave the status/footer line uncovered",save.y+save.height+6<=windowBottom-16);
                    for(ClipRect r:new ClipRect[]{load,delete,rename,section,name,save}){
                        assertTrue(r.width>0&&r.height>0&&r.x>=0&&r.y>=0&&r.x+r.width<=screen.width&&r.y+r.height<=screen.height);
                        assertTrue("The detail form must not cover the config list",r.intersect(screen.listBounds()).isEmpty());
                    }
                    assertEquals(section.x,name.x,.001f);assertEquals(name.x,save.x,.001f);
                    assertEquals(section.width,name.width,.001f);assertEquals(name.width,save.width,.001f);
                    click(screen,screen.itemBounds(0));click(screen,rename);
                    assertEquals(Boolean.TRUE,field(screen,"renaming"));
                    type(screen,"Draft name");click(screen,name);
                    assertEquals(-4,field(screen,"focus"));assertEquals("Draft name",field(screen,"renameBuffer"));
                    screen.keyTyped('\0',Keyboard.KEY_ESCAPE);
                    click(screen,save);
                    ConfigLibrary actions=(ConfigLibrary)field(screen,"configs");
                    assertEquals("The moved Save button must receive its click",ConfigLibrary.Pending.OVERWRITE,actions.pending());
                    assertEquals("Saved",actions.target());
                    assertArrayEquals("Layout and draft edits cannot overwrite the source",bytes,Files.readAllBytes(original));
                    screen.keyTyped('\0',Keyboard.KEY_ESCAPE);
                } finally {screen.onGuiClosed();}
            }
        } finally {ThemeMode.select(previous);}
    }

    @Test public void modernRenameDraftAndHitTargetsSurviveTheMinimumSplitHeight()throws Exception {
        String previous=ThemeMode.selected();ThemeMode.select(ThemeMode.APPROACH_TWO);
        try {
            for(boolean creative:new boolean[]{false,true}){
                Path dir=temporary.newFolder().toPath(),original=dir.resolve("Saved.json");
                byte[] bytes="{\"preserve\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(original,bytes);
                ClickLibraryScreen screen=modernScreen(dir,560,360,creative);
                try {
                    click(screen,screen.itemBounds(0));click(screen,screen.actionBounds(4));type(screen,"Resize draft");
                    for(int height:new int[]{319,320,321,240,360}){
                        screen.height=height;screen.initGui();
                        assertEquals(Boolean.TRUE,field(screen,"renaming"));assertEquals("Resize draft",field(screen,"renameBuffer"));
                        ClipRect name=screen.nameBounds(),rename=screen.actionBounds(4),save=screen.actionBounds(0);
                        assertTrue("Rename stays separate across the split breakpoint",rename.intersect(name).isEmpty());
                        assertTrue("Save stays separate across the split breakpoint",save.intersect(name).isEmpty());
                        for(ClipRect r:new ClipRect[]{name,rename,save})
                            assertTrue(r.x>=0&&r.y>=0&&r.x+r.width<=screen.width&&r.y+r.height<=screen.height);
                        click(screen,name);assertEquals(-4,field(screen,"focus"));assertEquals("Resize draft",field(screen,"renameBuffer"));
                        assertArrayEquals(bytes,Files.readAllBytes(original));
                    }
                    screen.keyTyped('\0',Keyboard.KEY_RETURN);
                    ConfigLibrary actions=(ConfigLibrary)field(screen,"configs");
                    assertEquals(ConfigLibrary.Pending.RENAME,actions.pending());assertEquals("Resize draft",actions.target());
                    assertFalse(Files.exists(dir.resolve("Resize draft.json")));
                } finally {screen.onGuiClosed();}
            }
        } finally {ThemeMode.select(previous);}
    }

    private ClickLibraryScreen modernScreen(Path dir,int width,int height,final boolean creative) {
        ClickLibraryScreen screen=new ClickLibraryScreen(null,false,
                new ConfigManager(dir,Collections.emptyList(),()->false,message->{}),new ThemeCatalog(null)){
            @Override protected boolean modernPresentation(){return true;}
            @Override protected boolean creativePresentation(){return creative;}
        };
        screen.width=width;screen.height=height;screen.initGui();return screen;
    }

    private ClickLibraryScreen screen(Path dir,int width,int height) {
        ClickLibraryScreen screen=new ClickLibraryScreen(null,false,
                new ConfigManager(dir,Collections.emptyList(),()->false,message->{}),new ThemeCatalog(null));
        screen.width=width;screen.height=height;screen.initGui();return screen;
    }
    private void click(ClickLibraryScreen screen,ClipRect r)throws Exception{
        screen.mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);screen.mouseReleased(0,0,0);
    }
    private void type(ClickLibraryScreen screen,String text)throws Exception{
        for(char c:text.toCharArray())screen.keyTyped(c,Keyboard.KEY_NONE);
    }
    private Object field(ClickLibraryScreen screen,String name)throws Exception{
        Field f=ClickLibraryScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);
    }
}
