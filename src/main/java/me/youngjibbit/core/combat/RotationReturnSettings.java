package me.youngjibbit.core.combat;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import java.util.function.BooleanSupplier;

/** Rotation-only handoff; module actions and listeners still stop immediately. */
public final class RotationReturnSettings {
    public final CheckBoxValue enabled;
    public final SliderValue ticks;

    public RotationReturnSettings(Module module) {
        enabled = new CheckBoxValue("Smooth Rotation Stop", module, true);
        ticks = new SliderValue("Return Ticks", module, 6D, 3, 12, true);
        ticks.setVisibility(ignored -> enabled.getValue());
    }

    public boolean begin(RotationManager manager, String source, MoveFixController.Mode movement,
                         BodyRotationController.Mode body, BooleanSupplier valid) {
        return begin(manager, source, movement, body, false, valid);
    }

    public boolean begin(RotationManager manager, String source, MoveFixController.Mode movement,
                         BodyRotationController.Mode body, boolean softArsenic, BooleanSupplier valid) {
        if (!enabled.getValue() && !softArsenic) return false;
        int duration = ticks.getValue().intValue();
        if (softArsenic) duration += java.util.concurrent.ThreadLocalRandom.current().nextInt(-1, 2);
        return manager.beginRotationRelease(source, duration, movement, body, valid);
    }
}
