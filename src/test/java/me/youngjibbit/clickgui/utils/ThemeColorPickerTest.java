package me.youngjibbit.clickgui.utils;

import me.youngjibbit.theme.*;
import me.youngjibbit.value.impl.ColorValue;
import org.junit.Test;
import org.lwjgl.input.Keyboard;
import java.awt.Color;
import static org.junit.Assert.*;

public class ThemeColorPickerTest {
    @Test public void hsvAndAlphaUseOneValueAndDoNotMutateUntilInput(){
        ThemeCatalog catalog=new ThemeCatalog(null);
        try(ThemeDraft draft=new ThemeDraft(catalog,catalog::select)){
            draft.duplicate();ColorValue value=draft.first;value.setColor(new Color(255,0,0,128));
            ThemeColorPicker picker=new ThemeColorPicker();picker.setValue(value);
            picker.layout(new ClipRect(10,10,200,140));
            assertEquals(new Color(255,0,0,128),value.getValue());
            ClipRect hue=picker.control(1);
            assertTrue(picker.click((int)(hue.x+hue.width/3),(int)hue.y+3));picker.release();
            assertTrue(value.getValue().getGreen()>240);
            assertEquals(128,value.getValue().getAlpha());
            ClipRect alpha=picker.control(2);
            picker.click((int)(alpha.x+alpha.width*.25f),(int)alpha.y+3);picker.release();
            assertEquals(64,value.getValue().getAlpha());
            Color before=value.getValue();assertFalse(picker.click(-100,-100));picker.move(190,90);
            assertEquals(before,value.getValue());
        }
    }
    @Test public void keyboardAdjustsTheFocusedChannel(){
        ThemeCatalog catalog=new ThemeCatalog(null);
        try(ThemeDraft draft=new ThemeDraft(catalog,catalog::select)){
            draft.duplicate();draft.first.setColor(new Color(255,0,0,100));
            ThemeColorPicker picker=new ThemeColorPicker();picker.setValue(draft.first);
            picker.layout(new ClipRect(0,0,200,140));picker.key(Keyboard.KEY_TAB);picker.key(Keyboard.KEY_TAB);
            picker.key(Keyboard.KEY_RIGHT);assertEquals(101,draft.first.getValue().getAlpha());
            picker.key(Keyboard.KEY_LEFT);assertEquals(100,draft.first.getValue().getAlpha());
        }
    }
}
