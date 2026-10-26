package me.youngjibbit.module.impl.world;

import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.youngjibbit.value.impl.ComboValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "BridgeAssist",
        description = "Aligns you for bridging",
        category = Category.World
)
public class BridgeAssist extends Module
{
    private final CheckBoxValue onSneak = new CheckBoxValue("Work only when sneaking", this, true);
    private final SliderValue waitFor = new SliderValue("Wait time (ms)", this, 500.0, 0, 5000);
    private final SliderValue glideTime = new SliderValue("Align speed", this, 150.0, 1, 201);
    private final SliderValue assistRange = new SliderValue("Assist range", this, 10.0, 1.0, 40.0);
    private final ComboValue assistMode = new ComboValue("Mode", this, new String[] {"Normal", "GodBridge", "Moonwalk", "Breezily"}, 1);

    private boolean waitingForAim;
    private boolean gliding;
    private long startWaitTime;
    private final float[] godbridgeYawPos = {-315, -225, -135, -45, 0, 45, 135, 225, 315};
    private final float[] moonwalkYawPos = {-340, -290, -250, -200, -160, -110, -70, -20, 0, 20, 70, 110, 160, 200, 250, 290, 340};
    private final float[] breezilyYawPos = {-360, -270, -180, -90, 0, 90, 180, 270, 360};
    private final float[] normalYawPos = {-315, -225, -135, -45, 0, 45, 135, 225, 315};
    private final float breezilyPitchPos = 79.9f;
    private final float normalPitchPos = 78f;
    private double speedYaw, speedPitch;
    private float targetYaw, targetPitch;

    public BridgeAssist()
    {
        super.setKey(Keyboard.KEY_0);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
        this.waitingForAim = false;
        this.gliding = false;
    }

    @Override
    public void onDisable()
    {
        this.waitingForAim = false;
        this.gliding = false;
        super.onDisable();
    }

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(event ->
    {
        if (!Minecraft.getMinecraft().thePlayer.onGround)
        {
            return;
        }

        if (onSneak.getValue() && !Minecraft.getMinecraft().thePlayer.isSneaking())
        {
            return;
        }

        if (gliding)
        {
            performGlide();
            return;
        }

        if (!waitingForAim)
        {
            waitingForAim = true;
            startWaitTime = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - startWaitTime < waitFor.getValue())
        {
            return;
        }

        startAiming();
    });

    private void performGlide()
    {
        float playerYaw = Minecraft.getMinecraft().thePlayer.rotationYaw;
        float playerPitch = Minecraft.getMinecraft().thePlayer.rotationPitch;

        if (Math.abs(playerYaw - targetYaw) < speedYaw)
        {
            Minecraft.getMinecraft().thePlayer.rotationYaw = targetYaw;
        } else {
            Minecraft.getMinecraft().thePlayer.rotationYaw += (playerYaw > targetYaw ? -speedYaw : speedYaw);
        }

        if (Math.abs(playerPitch - targetPitch) < speedPitch)
        {
            Minecraft.getMinecraft().thePlayer.rotationPitch = targetPitch;
        } else {
            Minecraft.getMinecraft().thePlayer.rotationPitch += (playerPitch > targetPitch ? -speedPitch : speedPitch);
        }

        if (Minecraft.getMinecraft().thePlayer.rotationYaw == targetYaw && Minecraft.getMinecraft().thePlayer.rotationPitch == targetPitch)
        {
            gliding = false;
            waitingForAim = false;
        }
    }

    private void startAiming()
    {
        float playerYaw = Minecraft.getMinecraft().thePlayer.rotationYaw;
        float playerPitch = Minecraft.getMinecraft().thePlayer.rotationPitch;
        float range = assistRange.getValue().floatValue();

        float[] targetPos = getTargetPosition(playerYaw, range);
        if (targetPos != null) {aimAt(targetPos[1], targetPos[0]);}
    }

    private float[] getTargetPosition(float currentYaw, float range)
    {
        float[] yawPositions;
        float pitchPosition;

        switch (assistMode.getSelectedIndex())
        {
            case 1: //GodBridge
                yawPositions = godbridgeYawPos;
                pitchPosition = normalPitchPos;
                break;
            case 2: // Moonwalk
                yawPositions = moonwalkYawPos;
                pitchPosition = normalPitchPos;
                break;
            case 3: // Breezily
                yawPositions = breezilyYawPos;
                pitchPosition = breezilyPitchPos;
                break;
            default: // Normal
                yawPositions = normalYawPos;
                pitchPosition = normalPitchPos;
                break;
        }

        for (float targetYaw : yawPositions)
        {
            if (targetYaw >= (currentYaw - range) && targetYaw <= (currentYaw + range))
            {
                return new float[]{pitchPosition, targetYaw};
            }
        }
        return null;
    }

    public void aimAt(float yaw, float pitch)
    {
        float glideSpeedFactor = 50.0f / glideTime.getValue().floatValue();
        this.speedYaw = Math.abs((yaw - Minecraft.getMinecraft().thePlayer.rotationYaw) * glideSpeedFactor);
        this.speedPitch = Math.abs((pitch - Minecraft.getMinecraft().thePlayer.rotationPitch) * glideSpeedFactor);
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.gliding = true;
    }
}
