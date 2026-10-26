package me.youngjibbit.theme;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.impl.ClickCombo;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.gui.iosUi.component.impl.iOSCombo;
import me.youngjibbit.module.impl.client.ThemeModule;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual choice handlers; the disposable iOS shell supplies only a scroll offset, without a renderer or game. */
public class ThemeUiConsumerTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private ThemeCatalog previous,catalog;
    private ThemeModule module;
    private String id;
    @Before public void setup() throws Exception {
        previous=ThemeCatalog.global();catalog=new ThemeCatalog(new ThemeStore(temporary.newFolder().toPath().resolve("themes.json")));
        ThemeCatalog.install(catalog);module=new ThemeModule();
        try(ThemeDraft draft=new ThemeDraft(catalog,module.selectedTheme::setValue)) {
            draft.duplicate();id=draft.reference();draft.title.setValue("GUI integration theme");draft.save();
        }
        module.selectedTheme.setValue("BlackLight");
    }
    @After public void restore() {ThemeCatalog.install(previous);}
    @Test public void iosMouseSelectionMatchesRenderedRowGeometry() throws Exception {
        Field iosField=Pulsar.class.getDeclaredField("iOS");iosField.setAccessible(true);Object old=iosField.get(Pulsar.INSTANCE);
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        iOS shell=(iOS)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(iOS.class);
        try {
            iosField.set(Pulsar.INSTANCE,shell);
            AtomicInteger callbacks=new AtomicInteger();module.selectedTheme.setOnChange(value->callbacks.incrementAndGet());
            iOSCombo combo=new iOSCombo(module.selectedTheme,10,10,220,24);
            int index=module.selectedTheme.getOptions().length-1;assertEquals(id,module.selectedTheme.getOptions()[index]);
            combo.mouseClicked(190,20,0);
            combo.mouseClicked(190,10+24+1+index*22+11,1);
            assertEquals("BlackLight",module.selectedTheme.getValue());assertEquals(0,callbacks.get());
            combo.mouseClicked(190,10+24+1+index*22+11,0);
            assertEquals(id,module.selectedTheme.getValue());assertEquals(1,callbacks.get());
            assertEquals("GUI integration theme",ThemeSelectionValue.label(module.selectedTheme.getValue()));
            assertTrue(catalog.current().isCustom());assertFalse(module.isToggled());
            callbacks.set(0);combo.mouseClicked(190,20,0);combo.mouseClicked(190,10+24+1+22,0);
            assertEquals("BlackLight",module.selectedTheme.getValue());assertEquals(1,callbacks.get());
        } finally {iosField.set(Pulsar.INSTANCE,old);}
    }
    @Test public void clickGuiKeyboardSelectionUsesStableCustomIdsAndPublicCallbacks() {
        AtomicInteger callbacks=new AtomicInteger();module.selectedTheme.setOnChange(value->callbacks.incrementAndGet());
        ClickCombo combo=new ClickCombo(module.selectedTheme,10,10,220,24);
        int moves=module.selectedTheme.getOptions().length-1;
        for(int i=0;i<moves;i++)combo.keyTyped(' ',Keyboard.KEY_RIGHT);
        assertEquals(id,module.selectedTheme.getValue());assertEquals(moves,callbacks.get());
        assertTrue(catalog.current().isCustom());assertFalse(module.isToggled());
        combo.keyTyped(' ',Keyboard.KEY_LEFT);assertFalse(catalog.current().isCustom());assertFalse(module.isToggled());
    }
    @Test public void rectangularHitTestingKeepsAxesIndependentAndEdgesInclusive() {
        for(int[] origin:new int[][]{{200,10},{10,200},{-30,80}}) {
            int x=origin[0],y=origin[1];
            assertTrue(me.youngjibbit.utils.input.MouseUtil.isMouseOver(x+40,y+11,x,y,80,22));
            assertTrue(me.youngjibbit.utils.input.MouseUtil.isMouseOver(x,y,x,y,80,22));
            assertTrue(me.youngjibbit.utils.input.MouseUtil.isMouseOver(x+80,y+22,x,y,80,22));
            assertFalse(me.youngjibbit.utils.input.MouseUtil.isMouseOver(x+40,y+23,x,y,80,22));
            assertFalse(me.youngjibbit.utils.input.MouseUtil.isMouseOver(x+81,y+11,x,y,80,22));
        }
    }
}
