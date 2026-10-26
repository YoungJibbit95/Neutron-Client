package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "Fullbright",
        description = "simple fullbright",
        category = Category.Render
)
public final class FullBright extends Module
{

    private float oldGamma;

    public FullBright()
    {
        setKey(Keyboard.KEY_M);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
        oldGamma = mc.gameSettings.gammaSetting;
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
        mc.gameSettings.gammaSetting = oldGamma;
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e ->
    {
            mc.gameSettings.gammaSetting = 3;
    });
}