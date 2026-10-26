package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Reach", category = Category.Ghost)
public class Reach
        extends Module
{
    private final CheckBoxValue randomized = new CheckBoxValue("Randomized", this, true);
    private final SliderValue minReach = new SliderValue("MinReach",this,  3.0, 0.1, 8.0);
    private final SliderValue maxReach = new SliderValue("MaxReach", this, 3.0, 0.1, 8.0);
    private final SliderValue vBlockReach = new SliderValue("BlockReach", this, 3.0, 0.1, 8.0);
    public double hitReach;
    public double blockReach;

    @Override
    public void onEnable()
    {
        super.onEnable();
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
    }

    @Subscribe
    private final Listener<EventUpdate> updateListener = new Listener<> (e->
    {
        if (!e.isPre()) {
            return;
        }

        double min = Math.min(this.minReach.getValue(), this.maxReach.getValue());
        double max = Math.max(this.minReach.getValue(), this.maxReach.getValue());
        this.hitReach = this.randomized.getValue()
                ? min + Math.random() * (max - min)
                : max;
        this.blockReach = this.vBlockReach.getValue();
    });

    public Reach() {
        super.setKey(Keyboard.KEY_0);
    }
}
