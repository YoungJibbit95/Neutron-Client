package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.combat.ClickTimingSettings;
import me.youngjibbit.core.combat.ClickRandomizer;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.MathUtils;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;


@ModuleInfo(name = "AutoClicker", category = Category.Ghost)
public class AutoClicker2 extends Module {
    private final TimerUtil clickTimer = new TimerUtil();
    private final SliderValue minCps = new SliderValue("Min CPS", this, 10.0, 1.0, 20.0, true);
    private final SliderValue maxCps = new SliderValue("Max CPS", this, 10.0, 1.0, 20.0, true);
    private final CheckBoxValue block = new CheckBoxValue("AutoBlock", this, false);
    private final SliderValue blockFrequency = new SliderValue("Block Frequency", this, 4.0, 0.0, 10.0, true);
    private final ClickTimingSettings clickTiming = new ClickTimingSettings(this, minCps, maxCps, false);
    private ClickRandomizer.Sample pendingClick;

    public AutoClicker2() {
        super.setKey(Keyboard.KEY_R);
    }

    @Override public void onEnable() { pendingClick = null; clickTimer.reset(); super.onEnable(); }
    @Override public void onDisable() { pendingClick = null; super.onDisable(); }
    @Override public void onValueChange() { pendingClick = null; }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(event -> {
        if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK)) return;

        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            if (block.getValue()) {
                mc.gameSettings.keyBindUseItem.pressed = false;
            }
            pendingClick = null;
            return;
        }

        if (pendingClick == null) resolveCps();
        if (!clickTimer.hasTimeElapsed(pendingClick.delayMillis, true)) {
            return;
        }

        if (block.getValue()) {
            mc.gameSettings.keyBindUseItem.pressed = false;
        }

        ClickRandomizer.Sample completedClick = pendingClick;
        pendingClick = null;
        mc.clickMouse();
        if (!isToggled()) return;

        if (clickTiming.debugging.getValue()) {
            Pulsar.INSTANCE.debugMessage("§c[" + clickTiming.mode.getValue() + "]§f : " + MathUtils.round(completedClick.cps, 1) + " cps ");
        }

        if (block.getValue() && ClickRandomizer.blockAtTick(mc.thePlayer.ticksExisted, blockFrequency.getValue())) {
            mc.gameSettings.keyBindUseItem.pressed = true;
        }
    });

    private double resolveCps() {
        pendingClick = clickTiming.sample();
        return pendingClick.cps;
    }
}

