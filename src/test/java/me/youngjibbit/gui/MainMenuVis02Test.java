package me.youngjibbit.gui;

import me.youngjibbit.utils.render.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class MainMenuVis02Test {
    @Test public void ambientOffLeavesActualHoverBoundsCaptureAndLinearReducedMotion() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.settings.ambientMotion.setValue(false);
            MainMenu menu=new MainMenu();menu.width=480;menu.height=320;menu.initGui();
            ClipRect bounds=menu.actionBounds(1);int x=(int)(bounds.x+bounds.width/2),y=(int)(bounds.y+bounds.height/2);
            menu.advanceMotion(.03f,x,y);Field h=MainMenu.class.getDeclaredField("hover");h.setAccessible(true);
            float[] amount=(float[])h.get(menu);assertTrue(amount[1]>0&&amount[1]<1);
            assertEquals(bounds.x,menu.actionBounds(1).x,0);assertEquals(bounds.y,menu.actionBounds(1).y,0);
            menu.onGuiClosed();menu.initGui();f.settings.reducedMotion.setValue(true);
            menu.advanceMotion(.03f,x,y);assertEquals(.25,amount[1],.00001);menu.advanceMotion(.09f,x,y);assertEquals(1,amount[1],.00001);
            f.settings.animations.setValue(false);menu.mouseClicked(x,y,0);menu.advanceMotion(0,x,y);assertEquals(-.45,amount[1],.00001);
            menu.keyTyped(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);menu.mouseReleased(x,y,0);
            Field press=MainMenu.class.getDeclaredField("pressed");press.setAccessible(true);assertEquals(-1,press.getInt(menu));
            menu.onGuiClosed();
        }
    }
}
