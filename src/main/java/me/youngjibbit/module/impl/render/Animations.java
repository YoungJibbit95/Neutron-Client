package me.youngjibbit.module.impl.render;

import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Animations", category= Category.Render)
public class Animations extends Module
{
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Stella", "Middle", "1.7", "Exhi", "Exhi 2", "Exhi 3", "Exhi 4", "Exhi 5", "Shred", "Smooth", "Sigma", "Custom"}, 1);
    private final SliderValue slowdown = new SliderValue("Speed", this, 1.0, 1.0, 10);
    private final SliderValue x = new SliderValue("X", this, 0.0, -50.0, 50.0, true);
    private final SliderValue y = new SliderValue("Y", this, 0.0, -50.0, 50.0, true);
    private final SliderValue size = new SliderValue("Size", this, 0.0, -30.0, 0.0);
    private final CheckBoxValue smooth = new CheckBoxValue("Smoother", this, true);
    private final CheckBoxValue smallSwing = new CheckBoxValue("Small Swing", this, false);

    public Animations() {
        this.setKey(Keyboard.KEY_0);
    }

    public ComboValue getModeSetting() {
        return mode;
    }

    public SliderValue getSlowdownSetting() {
        return slowdown;
    }

    public SliderValue getXSetting() {
        return x;
    }

    public SliderValue getYSetting() {
        return y;
    }

    public SliderValue getSizeSetting() {
        return size;
    }

    public CheckBoxValue getSmoothSetting() {
        return smooth;
    }

    public CheckBoxValue getSmallSwingSetting() {
        return smallSwing;
    }
}
