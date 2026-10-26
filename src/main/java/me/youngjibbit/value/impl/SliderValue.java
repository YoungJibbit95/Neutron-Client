package me.youngjibbit.value.impl;

import lombok.Getter;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;

@Getter
public class SliderValue extends Value<Double>
{
    private final double min, max;
    private final boolean integer;

    public SliderValue(String name, Module parent, Double value, double min, double max)
    {
        super(name, parent, value);
        this.min = min;
        this.max = max;
        this.integer = false;
        setValue(value);
    }

    public SliderValue(String name, Module parent, Double value, double min, double max, boolean integer)
    {
        super(name, parent, value);
        this.min = min;
        this.max = max;
        this.integer = integer;
        setValue(value);
    }

    @Override
    public Double getValue()
    {
        return integer ? (double) Math.round(super.getValue()) : super.getValue();
    }

    @Override
    public void setValue(Double value)
    {
        double clamped = Math.max(min, Math.min(max, value));
        super.setValue(integer ? (double) Math.round(clamped) : clamped);
    }
}
