package me.youngjibbit.value.impl;

import me.youngjibbit.clickgui.component.impl.ClickSlider;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.*;

/** Pinned Demise SliderValue storage versus SliderComponent-only increment rounding. */
public class DemiseSliderValueTest {
    @Test public void directConfigValuesKeepTheirFloatAndDoNotSnapToIntegerSteps() {
        DemiseSliderValue value = new DemiseSliderValue("Blocks", null, 6D, 0, 15, 1);
        value.setValue(6.25);
        assertEquals(6.25, value.getValue(), 0);
        assertFalse(value.isInteger());
        value.setValue(6.123456789);
        assertEquals((double) (float) 6.123456789, value.getValue(), 0);
        // Source setters do not clamp; input/config validation belongs to their caller.
        value.setValue(20.25);
        assertEquals(20.25, value.getValue(), 0);
        value.reset();
        assertEquals(6, value.getValue(), 0);
    }

    @Test public void mouseOrTypedUiEditsUseTheOriginalIncrementRounding() {
        DemiseSliderValue blocks = new DemiseSliderValue("Blocks", null, 6D, 0, 15, 1);
        blocks.setFromUi(6.49);
        assertEquals(6, blocks.getValue(), 0);
        blocks.setFromUi(6.5);
        assertEquals(7, blocks.getValue(), 0);
        DemiseSliderValue duration = new DemiseSliderValue("Duration", null, 50D, 25, 1000, 25);
        duration.setValue(62.5);
        assertEquals(62.5, duration.getValue(), 0);
        duration.setFromUi(62.5);
        assertEquals(75, duration.getValue(), 0);
        duration.stepFromUi(1);
        assertEquals(100, duration.getValue(), 0);
        duration.setFromUi(0);
        assertEquals(25, duration.getValue(), 0);
    }

    @Test public void hundredthStepsUseFloatIncrementAndDecimalCleanupFromTheSource() {
        DemiseSliderValue factor = new DemiseSliderValue("Smoothing", null, .15D, .01, 1, .01);
        assertEquals(.01f, factor.getIncrement(), 0);
        factor.setFromUi(.1549);
        assertEquals((double) .15f, factor.getValue(), 0);
        factor.setFromUi(.1551);
        assertEquals((double) .16f, factor.getValue(), 0);
        assertEquals("0.16", factor.displayValue());
        factor.stepFromUi(-1);
        assertEquals((double) .15f, factor.getValue(), 0);
    }

    @Test public void clickSliderKeyboardUsesSourceStepsWithoutChangingOrdinarySliders() {
        DemiseSliderValue duration = new DemiseSliderValue("Duration", null, 50D, 25, 1000, 25);
        ClickSlider custom = new ClickSlider(duration, 0, 0, 200, 24);
        custom.keyTyped('\0', Keyboard.KEY_RIGHT);
        assertEquals(75, duration.getValue(), 0);
        custom.keyTyped('\0', Keyboard.KEY_LEFT);
        assertEquals(50, duration.getValue(), 0);
        SliderValue ordinary = new SliderValue("Ordinary", null, 50D, 25, 1000);
        new ClickSlider(ordinary, 0, 0, 200, 24).keyTyped('\0', Keyboard.KEY_RIGHT);
        assertEquals(59.75, ordinary.getValue(), 0);
        ordinary.setValue(2000D);
        assertEquals(1000, ordinary.getValue(), 0);
        SliderValue integer = new SliderValue("Integer", null, 6D, 0, 15, true);
        integer.setValue(6.25);
        assertEquals(6, integer.getValue(), 0);
    }
}
