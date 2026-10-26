package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.MathUtils;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.utils.math.FastNoiseLite;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

import java.security.SecureRandom;
import java.util.Random;

@ModuleInfo(name = "AutoClicker", category = Category.Ghost)
public class AutoClicker2 extends Module {
    private final TimerUtil clickTimer = new TimerUtil();
    private final SliderValue minCps = new SliderValue("Min CPS", this, 10.0, 1.0, 20.0, true);
    private final SliderValue maxCps = new SliderValue("Max CPS", this, 10.0, 1.0, 20.0, true);
    private final CheckBoxValue block = new CheckBoxValue("AutoBlock", this, false);
    private final SliderValue blockFrequency = new SliderValue("Block Frequency", this, 4.0, 0.0, 10.0, true);
    private final CheckBoxValue enableRandomization = new CheckBoxValue("Enable Randomization", this, true);
    private final ComboValue randomizationMode = new ComboValue(
            "Randomization Mode",
            this,
            new String[]{"Basic", "Next", "Secure", "Pi", "Sin & Cos", "Smoothstep", "Perlin", "Test"},
            1
    );
    private final SliderValue randomization = new SliderValue("Randomization", this, 3.0, 0.5, 5.0);
    private final CheckBoxValue debugging = new CheckBoxValue("Debugging", this, false);

    private final FastNoiseLite noise = new FastNoiseLite();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Random random = new Random();

    private double cps;

    public AutoClicker2() {
        super.setKey(Keyboard.KEY_R);
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(event -> {
        cps = resolveCps();
        double debugCps = MathUtils.round(cps, 1);

        long clickDelay = (long) ((1000.0 / cps)
                + (enableRandomization.getValue() ? random.nextDouble() * randomization.getValue() : 0.0));

        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            if (block.getValue()) {
                mc.gameSettings.keyBindUseItem.pressed = false;
            }
            return;
        }

        if (!clickTimer.hasTimeElapsed(Math.max(1L, clickDelay), true)) {
            return;
        }

        if (block.getValue()) {
            mc.gameSettings.keyBindUseItem.pressed = false;
        }

        mc.clickMouse();

        if (debugging.getValue()) {
            Pulsar.INSTANCE.debugMessage("§c[" + randomizationMode.getValue() + "]§f : " + debugCps + " cps ");
        }

        if (block.getValue() && (double) mc.thePlayer.ticksExisted % (10.0 - blockFrequency.getValue()) == 0.0) {
            mc.gameSettings.keyBindUseItem.pressed = true;
        }
    });

    private double resolveCps() {
        double randomPiFactor = random.nextDouble() * Math.PI;
        double time = System.currentTimeMillis() * 0.001;

        switch (randomizationMode.getValue()) {
            case "Basic":
                return MathUtils.getRandomInRange(minCps.getValue(), maxCps.getValue());
            case "Next":
                return MathUtils.nextInt(minCps.getValue().intValue(), maxCps.getValue().intValue());
            case "Secure":
                double min = minCps.getValue(), max = maxCps.getValue();
                return min == max ? min : MathUtils.getRandomInRange(min, max);
            case "Pi":
                return minCps.getValue() + (maxCps.getValue() - minCps.getValue()) * Math.sin(randomPiFactor);
            case "Sin & Cos":
                double angle1 = secureRandom.nextDouble() * Math.PI;
                double angle2 = secureRandom.nextDouble() * Math.PI;
                double factor = (Math.sin(angle1) + Math.cos(angle2)) / 2;
                return minCps.getValue() + (maxCps.getValue() - minCps.getValue()) * factor;
            case "Smoothstep":
                return MathUtils.smoothstep(minCps.getValue(), maxCps.getValue(), randomPiFactor);
            case "Perlin":
                double seed = random.nextDouble() * 1000.0;
                double noiseValue = Math.sin((time + seed) * 0.1) * 0.5 + 0.5;
                return minCps.getValue() + (maxCps.getValue() - minCps.getValue()) * noiseValue;
            case "Test":
                noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
                return noise.GetNoise(minCps.getValue().floatValue(), maxCps.getValue().floatValue());
            default:
                return minCps.getValue();
        }
    }
}

