package me.youngjibbit.clickgui;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.clickgui.utils.ClipRect;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
}
