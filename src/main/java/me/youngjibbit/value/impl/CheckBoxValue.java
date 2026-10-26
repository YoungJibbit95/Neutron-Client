package me.youngjibbit.value.impl;

import me.youngjibbit.module.Module;
import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.value.Value;

public class CheckBoxValue extends Value<Boolean>
{
    public CheckBoxValue(String name, Module parent, Boolean value)
    {
        super(name, parent, value);
    }

    public CheckBoxValue(String name, Module parent)
    {
        super(name, parent, false);
    }

    @Override
    public void setValue(Boolean value)
    {
        super.setValue(value);
    }
}
