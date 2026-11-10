package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.theme.ThemeStore;
import me.youngjibbit.theme.ThemeDraft;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** Real editor layout and input with a temporary theme store; no GL or game session. */
public class ThemeEditorLayoutTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private ModuleManager priorManager;
    private ThemeCatalog priorCatalog,catalog;
    private String priorMode;
    private ClientSettings settings;
    private Path file;
    private Screen screen;

    private static final class Screen extends ThemeEditorScreen {
        boolean returned;
        Screen(ThemeCatalog catalog){super(null,catalog,catalog::select);}
        void size(int w,int h){width=w;height=h;initGui();}
        ClipRect control(int id){return actionBounds(id);}
        ClipRect form(){return formBounds();}
        ClipRect track(int id){return sliderTrack(id);}
        void reveal(int id){revealControl(id);}
        void clickAt(float x,float y)throws Exception{mouseClicked(Math.round(x),Math.round(y),0);mouseReleased(0,0,0);}
        void click(int id,int button)throws Exception{
            revealControl(id);ClipRect r=actionBounds(id);
            mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),button);mouseReleased(0,0,0);
        }
        void input(int id,String text)throws Exception{
            click(id,id>=20&&id<=24?1:0);type(text);key(Keyboard.KEY_RETURN);
        }
        void type(String text)throws Exception{for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);}
        void key(int key)throws Exception{keyTyped(' ',key);}
        void motion(float dt){advanceMotion(dt,-1,-1);}
        void action(int id)throws Exception{activate(id);}
        @Override protected void leave(){returned=true;}
        @Override protected boolean ctrlDown(){return false;}
        @Override protected boolean shiftDown(){return false;}
    }

    @Before public void before()throws Exception{
        priorManager=Pulsar.INSTANCE.getMm();priorCatalog=ThemeCatalog.global();priorMode=ThemeMode.selected();
        settings=new ClientSettings();setManager(new ModuleManager(Collections.singletonList(settings)));
        ThemeMode.select(ThemeMode.LEGACY);
        file=temporary.newFolder().toPath().resolve("themes.json");catalog=new ThemeCatalog(new ThemeStore(file));ThemeCatalog.install(catalog);
        screen=new Screen(catalog);screen.editTheme(Theme.PinkLemonade,true);screen.size(800,500);
    }
    @After public void after()throws Exception{
        if(screen!=null)screen.onGuiClosed();ThemeCatalog.install(priorCatalog);ThemeMode.select(priorMode);setManager(priorManager);
    }
    private static void setManager(ModuleManager manager)throws Exception{
        Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,manager);
    }
    private Object field(String name)throws Exception{
        Field f=ThemeEditorScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);
    }
    private static void contained(String label,ClipRect outer,ClipRect inner){
        assertTrue(label+" left",inner.x>=outer.x-.01f);assertTrue(label+" top",inner.y>=outer.y-.01f);
        assertTrue(label+" right",inner.x+inner.width<=outer.x+outer.width+.01f);
        assertTrue(label+" bottom",inner.y+inner.height<=outer.y+outer.height+.01f);
        assertTrue(label+" usable",inner.width>0&&inner.height>=22);
    }

    @Test public void allSectionsRemainReachableAfterCompactResizeWithoutSavingTheDraft()throws Exception{
        screen.input(9,"Responsive draft");screen.input(10,"A020F080");
        String draftId=catalog.current().name();
        for(int[] size:new int[][]{{800,500},{580,350},{334,234},{320,240},{800,360}}){
            screen.size(size[0],size[1]);ClipRect viewport=new ClipRect(0,0,size[0],size[1]);
            for(int id:new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,20,21,22,23,24,25,26,27}){
                screen.reveal(id);ClipRect control=screen.control(id);contained("control "+id,viewport,control);
                if(id>=7&&id<=24)contained("field "+id,screen.form(),control);
            }
            assertEquals(draftId,catalog.current().name());assertEquals("Responsive draft",catalog.current().getDisplayName());
            assertEquals(128,catalog.current().getPrimaryGradientColor().getAlpha());
            assertFalse(Files.exists(file));assertEquals("PinkLemonade",catalog.selectedReference());
        }
        screen.key(Keyboard.KEY_ESCAPE);assertTrue(screen.returned);assertSame(Theme.PinkLemonade,catalog.current());
    }

    @Test public void sectionSwitchCannotCommitInvalidExactInputOrLeaveKeyboardFocusHidden()throws Exception{
        screen.click(26,0);screen.key(Keyboard.KEY_TAB);assertEquals(7,field("focus"));
        screen.input(20,"0.625");float speed=catalog.current().getAnimationSpeed();
        screen.click(20,1);screen.type("-");screen.click(25,0);
        assertEquals(1,field("section"));assertEquals(20,field("editingField"));
        assertEquals(speed,catalog.current().getAnimationSpeed(),0);
        screen.key(Keyboard.KEY_ESCAPE);screen.key(Keyboard.KEY_F6);
        assertEquals(2,field("section"));assertEquals(27,field("focus"));
        screen.key(Keyboard.KEY_TAB);assertEquals(21,field("focus"));
        screen.key(Keyboard.KEY_RETURN);assertEquals(21,field("editingField"));
        assertEquals(speed,catalog.current().getAnimationSpeed(),0);
    }

    @Test public void disabledAndReducedMotionSnapSectionPresentationWithoutChangingTheDraft()throws Exception{
        settings.animations.setValue(false);screen.motion(0);screen.click(27,0);screen.motion(0);
        assertEquals(screen.control(27).x,(Float)field("sectionPosition"),0);
        settings.animations.setValue(true);settings.reducedMotion.setValue(true);screen.click(25,0);screen.motion(0);
        assertEquals(screen.control(25).x,(Float)field("sectionPosition"),0);
        assertEquals("PinkLemonade",catalog.selectedReference());assertFalse(Files.exists(file));
    }

    @Test public void changingSectionDisarmsAnEarlierDeleteConfirmation()throws Exception{
        screen.action(6);assertEquals(Boolean.TRUE,field("deleteConfirm"));
        screen.click(26,0);assertEquals(Boolean.FALSE,field("deleteConfirm"));assertEquals("",field("status"));
        assertFalse(Files.exists(file));assertTrue(catalog.current().isCustom());
    }

    @Test public void visibleSliderEndsMapToExactLimitsAtCompactAndWideSizes()throws Exception{
        ThemeDraft draft=(ThemeDraft)field("draft");SliderValue[] values={draft.speed,draft.glowStrength,draft.glowRadius,draft.outlineStrength,draft.outlineWidth};
        for(int[] size:new int[][]{{334,234},{800,500}}){
            screen.size(size[0],size[1]);
            for(int id=20;id<=24;id++){
                screen.reveal(id);ClipRect track=screen.track(id);SliderValue value=values[id-20];
                screen.clickAt(track.x,track.y+1);assertEquals(value.getMin(),value.getValue(),0);
                screen.clickAt(track.x+track.width,track.y+1);assertEquals(value.getMax(),value.getValue(),0);
            }
        }
    }

    @Test public void closingAColorPickerReleasesItsLogicalInputStateBeforeReopen()throws Exception{
        screen.reveal(10);ClipRect color=screen.control(10);screen.clickAt(color.x+color.width-12,color.y+10);
        assertEquals(10,field("pickerField"));assertNotNull(field("pickerOriginal"));
        screen.onGuiClosed();assertEquals(-1,field("pickerField"));assertNull(field("pickerOriginal"));
        screen.size(334,234);screen.key(Keyboard.KEY_F6);
        assertEquals(1,field("section"));assertEquals(26,field("focus"));assertFalse(catalog.isPreviewing());
    }
}
