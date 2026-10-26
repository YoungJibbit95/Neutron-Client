package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.utils.render.AnimationEngine;
import org.junit.Test;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.EnumMap;
import static org.junit.Assert.*;

public class IosOpeningTest {
    private Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    @Test public void actualIosConstructionAndReopenReachTheInteractiveEndOfTheAnimation() throws Exception {
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        ModuleManager empty=(ModuleManager)unsafe.allocateInstance(ModuleManager.class);
        field(ModuleManager.class,"categoryArrayCache").set(empty,new EnumMap<Category,Module[]>(Category.class));
        Field manager=field(Pulsar.class,"mm");Object previous=manager.get(Pulsar.INSTANCE);
        try {
            manager.set(Pulsar.INSTANCE,empty);iOS screen=new iOS();((net.minecraft.client.gui.GuiScreen)screen).width=640;((net.minecraft.client.gui.GuiScreen)screen).height=480;
            AnimationEngine animation=(AnimationEngine)field(iOS.class,"animationEngine").get(screen);
            for(int reopen=0;reopen<2;reopen++) {
                screen.initGui();assertEquals(0,field(AnimationEngine.class,"animationValue").getFloat(animation),0);
                field(AnimationEngine.class,"prevTime").setLong(animation,System.currentTimeMillis()-1000);
                assertEquals("Real constructor must configure a nonzero opening range",1,animation.getAnimationValue(),0);
                assertTrue(animation.isAnimationDone());assertFalse(animation.getIsDrawAnimation());
            }
        } finally { manager.set(Pulsar.INSTANCE,previous); }
    }
    private BufferedImage image(Color outside,Color accent,Rectangle region) {
        BufferedImage image=new BufferedImage(80,60,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<60;y++)for(int x=0;x<80;x++)image.setRGB(x,y,(region.contains(x,y)?accent:outside).getRGB());
        return image;
    }
    @Test public void themeProbeRejectsAnimatedBackgroundChangesWithoutActualAccentChanges() {
        Rectangle region=new Rectangle(10,20,40,6);
        BufferedImage before=image(Color.RED,Color.GRAY,region),after=image(Color.GREEN,Color.GRAY,region);
        assertThrows(AssertionError.class,()->ThemeGuiRenderCheck.changed(before,after,"negative control",region));
        ThemeGuiRenderCheck.changed(image(Color.BLACK,Color.RED,region),image(Color.BLACK,Color.GREEN,region),"positive control",region);
    }
}
