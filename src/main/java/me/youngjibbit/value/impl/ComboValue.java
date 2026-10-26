package me.youngjibbit.value.impl;

import lombok.Getter;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;

@Getter
public class ComboValue extends Value<String>
{
    private final String[] options;
    private int selectedIndex;

    public ComboValue(String name, Module parent, String value, String[] options)
    {
        super(name, parent, value);
        this.options = options.clone();
        this.selectedIndex = resolveIndex(value);
        if (this.selectedIndex == -1 && this.options.length > 0) {
            this.selectedIndex = 0;
            super.setValue(this.options[0]);
        }
    }

    public ComboValue(String name, Module parent, String[] options, int defaultIndex)
    {
        super(name, parent, resolveDefault(options, defaultIndex));
        this.options = options.clone();
        this.selectedIndex = clampIndex(defaultIndex);
        if (this.options.length > 0) {
            super.setValue(this.options[this.selectedIndex]);
        }
    }

    private static String resolveDefault(String[] options, int defaultIndex) {
        if (options == null || options.length == 0) {
            return "";
        }

        int clamped = Math.max(0, Math.min(defaultIndex, options.length - 1));
        return options[clamped];
    }

    private int clampIndex(int index) {
        if (options.length == 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, options.length - 1));
    }

    private int resolveIndex(String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = clampIndex(index);
        if (options.length > 0) {
            super.setValue(options[this.selectedIndex]);
        }
    }

    @Override
    public void setValue(String value)
    {
        int index = resolveIndex(value);
        if (index == -1) {
            if (options.length == 0) {
                super.setValue(value);
                this.selectedIndex = 0;
            } else {
                this.selectedIndex = 0;
                super.setValue(options[0]);
            }
            return;
        }

        this.selectedIndex = index;
        super.setValue(options[index]);
    }
}
