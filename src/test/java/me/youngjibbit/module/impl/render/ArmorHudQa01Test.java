package me.youngjibbit.module.impl.render;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import org.junit.Test;
import java.awt.Color;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Information color must describe equipment, not a moving decorative sample. */
public class ArmorHudQa01Test {
    @Test public void durabilityIsIndependentOfPhaseMethodDirectionAndGradientSwitch() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            int[] initial=colors(Theme.SynthWave);
            for(String method:new String[]{"Flow","Ping-Pong","Pulse"})for(String direction:new String[]{"Horizontal","Up","Left"}) {
                f.settings.gradientMethod.setValue(method);f.settings.direction.setValue(direction);f.advance(.31);
                assertArrayEquals("Durability must not drift with decorative phase",initial,colors(Theme.SynthWave));
            }
            f.settings.gradients.setValue(false);
            assertArrayEquals("Disabling decoration must preserve equipment information",initial,colors(Theme.SynthWave));
        }
    }
    @Test public void priorityOnKeepsEmptyHalfAndFullDistinctAcrossLiveThemeChanges() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            for(Theme theme:new Theme[]{Theme.Flare,Theme.Neon,Theme.SynthWave}) {
                Theme.setDefaultTheme(theme);
                assertEquals(0xffff667f,ArmorHUD.durabilityColor(theme,0));
                assertEquals(0xffffc969,ArmorHUD.durabilityColor(theme,.5f));
                assertEquals(0xff65e0ad,ArmorHUD.durabilityColor(theme,1));
            }
        }
    }
    @Test public void priorityOffCanTintButNeverAnimateOrLoseCriticalRed() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.settings.semanticColorPriority.setValue(false);
            int[] initial=colors(Theme.SynthWave);f.advance(.73);
            assertArrayEquals(initial,colors(Theme.SynthWave));
            assertEquals(0xffff667f,initial[0]);
            assertNotEquals("Explicit tint remains available",0xff65e0ad,initial[2]);
        }
    }
    @Test public void localPaletteAlphaAndBoundedRemainingArePreserved() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            Theme local=Theme.custom("custom:00000000-0000-0000-0000-000000000071","QA01 alpha",Arrays.asList(new Color(255,0,0,128),new Color(0,0,255,128)),false,true,.3f,Color.BLACK,Color.WHITE,Color.GREEN,.7f,9,.8f,1);
            for(float ratio:new float[]{0,.5f,1})assertEquals(128,ArmorHUD.durabilityColor(local,ratio)>>>24);
            assertEquals(ArmorHUD.durabilityColor(local,0),ArmorHUD.durabilityColor(local,-1));
            assertEquals(ArmorHUD.durabilityColor(local,1),ArmorHUD.durabilityColor(local,2));
        }
    }
    private static int[] colors(Theme theme){return new int[]{ArmorHUD.durabilityColor(theme,0),ArmorHUD.durabilityColor(theme,.5f),ArmorHUD.durabilityColor(theme,1)};}
}
