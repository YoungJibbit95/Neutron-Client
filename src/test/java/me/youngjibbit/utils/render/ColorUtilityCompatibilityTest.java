package me.youngjibbit.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Preserves legacy color contracts, including the deliberate integer-vs-Color alpha difference. */
public class ColorUtilityCompatibilityTest {
    @Test public void interpolationAndOpacityKeepTheirLegacyAlphaRules() {
        Color first = new Color(20, 40, 60, 80), second = new Color(100, 120, 140, 160);
        assertEquals(new Color(60, 80, 100, 120), ColorWheel.interpolateColorC(first, second, .5f));
        assertEquals(first, ColorWheel.interpolateColorC(first, second, -1));
        assertEquals(second, ColorWheel.interpolateColorC(first, second, 2));
        assertEquals(new Color(60, 80, 100).getRGB(), ColorWheel.interpolateColor(first.getRGB(), second.getRGB(), .5f));
        assertEquals(new Color(20, 40, 60, 40), ColorWheel.applyOpacity(first, .5f));
        assertEquals(new Color(20, 40, 60, 127).getRGB(), ColorWheel.applyOpacity(first.getRGB(), .5f));
        assertEquals(new Color(30, 30, 30, 127), ColorWheel.tripleColor(30, .5f));
        assertArrayEquals(new int[]{20, 40, 60, 80}, ColorWheel.createColorArray(first.getRGB()));
        assertEquals(0x50ebd7c3, ColorWheel.getOppositeColor(first.getRGB()));
    }

    @Test public void retainedBlendAndImageSamplingUseTheSameChannelOrder() {
        assertEquals(new Color(.5f, 0, .5f), ColorWheel.blendColors(
                new float[]{0,1}, new Color[]{Color.RED,Color.BLUE}, .5f));
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,new Color(20,40,60,0).getRGB());
        image.setRGB(1,0,new Color(40,60,80,80).getRGB());
        image.setRGB(0,1,new Color(60,80,100,160).getRGB());
        image.setRGB(1,1,new Color(80,100,120,255).getRGB());
        assertEquals(new Color(50,70,90), ColorWheel.averageColor(image,2,2,1));
        assertEquals(new Color(10,20,30,80), ColorWheel.darker(new Color(20,40,60,80),.5f));
        assertEquals(80, ColorWheel.brighter(new Color(20,40,60,80),.5f).getAlpha());
    }
}
