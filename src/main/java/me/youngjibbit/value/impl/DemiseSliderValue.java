package me.youngjibbit.value.impl;

import me.youngjibbit.module.Module;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Float storage and GUI step rounding from DemisePrivate
 * e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb:
 * features/values/impl/SliderValue.java and gui/click/components/impl/SliderComponent.java.
 * Direct/config setters retain floats without rounding to the GUI increment.
 */
public final class DemiseSliderValue extends SliderValue {
    private final float increment;

    public DemiseSliderValue(String name, Module parent, Double value,
                             double min, double max, double increment) {
        super(name, parent, (double) value.floatValue(), min, max);
        this.increment = (float) increment;
    }

    public float getIncrement() { return increment; }

    @Override public void setValue(Double value) {
        // The original setter is a plain float assignment, including when loading a config.
        storeValue((double) value.floatValue());
    }

    public void setFromUi(double value) {
        double bounded = Math.max(getMin(), Math.min(getMax(), value));
        double rounded = Math.round(bounded / increment) * (double) increment;
        String text = Float.toString(increment);
        int point = text.indexOf('.');
        int decimalPoints = point < 0 ? 0 : text.length() - point - 1;
        setValue((double) BigDecimal.valueOf(rounded).setScale(decimalPoints, RoundingMode.HALF_UP).floatValue());
    }

    public void stepFromUi(int direction) {
        setFromUi(getValue() + direction * (double) increment);
    }

    /** Show the stored float without exposing the widening-to-Double representation. */
    public String displayValue() { return Float.toString(getValue().floatValue()); }
}
