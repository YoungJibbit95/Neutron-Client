package me.youngjibbit.utils.render;

import me.youngjibbit.module.impl.render.ArmorHUD;
import me.youngjibbit.module.impl.render.SessionInfo;
import me.youngjibbit.utils.Theme;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual owner/policy helpers used by shared scenes; no framebuffer execution is claimed. */
public class CreativeHudScenePolicyTest {
    @Test public void localSessionStrengthIsNotReplacedByTheGlobalSceneRadius()throws Exception {
        try(Vis02TestContext context=new Vis02TestContext()){
            context.settings.blurStrength.setValue(9d);
            SessionInfo session=new SessionInfo();session.getModeSetting("Effects Source").setValue("Local");
            ArrayListSurface surface=surface(session);
            for(double strength:new double[]{1,3,7,10}){
                session.getNumberSetting("Blur Strength").setValue(strength);
                assertEquals((float)strength,surface.sceneBlurRadius(
                        session.getNumberSetting("Blur Strength").getValue().floatValue()),0);
            }
            session.getModeSetting("Effects Source").setValue("Global");
            assertEquals(9,surface.sceneBlurRadius(3),0);
        }
    }
    @Test public void actualArmorOwnerKeepsThreePixelLocalBlurAndSharedGlobalOverride()throws Exception {
        try(Vis02TestContext context=new Vis02TestContext()){
            context.settings.blurStrength.setValue(8d);
            ArmorHUD armor=new ArmorHUD();ArrayListSurface surface=surface(armor);
            armor.getModeSetting("Effects Source").setValue("Local");
            assertEquals(3,surface.sceneBlurRadius(3),0);
            armor.getModeSetting("Effects Source").setValue("Global");
            assertEquals(8,surface.sceneBlurRadius(3),0);
        }
    }
    @Test public void parentSceneCanOnlySupplyTheRequestedBlurWhenItsRadiusMatches()throws Exception {
        try(Vis02TestContext context=new Vis02TestContext()){
            UiEffects.SurfacePolicy policy=UiEffects.surface(null,Theme.Purple,true,true,false,0,-1);
            assertFalse(ArrayListSurface.canBorrowScene(null,true,3));
            try(UiEffects.SceneContext parent=UiEffects.scene(1,2,true,Theme.Purple,policy,false,3)){
                assertTrue(ArrayListSurface.canBorrowScene(parent,true,3));
                assertFalse(ArrayListSurface.canBorrowScene(parent,true,7));
                assertTrue(ArrayListSurface.canBorrowScene(parent,false,7));
            }
            try(UiEffects.SceneContext clear=UiEffects.scene(1,1,false,Theme.Purple,policy,false,3)){
                assertFalse(ArrayListSurface.canBorrowScene(clear,true,3));
                assertTrue(ArrayListSurface.canBorrowScene(clear,false,3));
            }
            try(UiEffects.SceneContext absent=UiEffects.scene(0,0,true,Theme.Purple,policy,false,3)){
                assertFalse(ArrayListSurface.canBorrowScene(absent,false,3));
            }
        }
    }
    @Test public void effectiveBackgroundRetainsGlobalOverrideAndExplicitLocalFlags()throws Exception {
        try(Vis02TestContext context=new Vis02TestContext()){
            for(me.youngjibbit.module.Module owner:new me.youngjibbit.module.Module[]{new ArmorHUD(),new SessionInfo()}){
                ArrayListSurface surface=surface(owner);
                owner.getModeSetting("Effects Source").setValue("Global");
                assertTrue(surface.scenePolicy(Theme.Purple,false,false).background);
                owner.getModeSetting("Effects Source").setValue("Local");
                assertFalse(surface.scenePolicy(Theme.Purple,false,false).background);
                assertTrue(surface.scenePolicy(Theme.Purple,true,false).background);
                context.settings.blur.setValue(false);
                assertFalse(surface.scenePolicy(Theme.Purple,true,true).blur);
            }
        }
    }
    private static ArrayListSurface surface(Object owner)throws Exception {
        Field field=owner.getClass().getDeclaredField("surface");field.setAccessible(true);
        return (ArrayListSurface)field.get(owner);
    }
}
