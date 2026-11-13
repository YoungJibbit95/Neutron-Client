package me.youngjibbit.core.combat;

import me.youngjibbit.core.world.DemiseRotationEngine;

import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Acquisition-local adapter of the pinned Demise frame engine. Frames compute only
 * a candidate; Neutron's normal PRE request and accepted C03 determine authority.
 */
final class AdaptiveDemiseMotion {
    private final DemiseRotationEngine engine;
    private final AdaptiveDemiseSettings.Configuration settings;
    private long sampledSequence = Long.MIN_VALUE, refreshNanos;
    private boolean framePrepared, holding;

    AdaptiveDemiseMotion(LongSupplier nanos, long seed, AdaptiveDemiseSettings.Configuration settings,
                         float yaw, float pitch) {
        this.settings = settings;
        Random random = new Random(seed);
        engine = new DemiseRotationEngine(() -> nanos.getAsLong() / 1_000_000L, nanos, (lo, hi) -> {
            if (lo == hi) return lo;
            return (float) (lo + random.nextDouble() * (hi - lo + (lo < 1 && hi < 1 ? 0 : 1)));
        });
        engine.clear(yaw, pitch);
        engine.observeAcceptedPacket(true, yaw, pitch);
    }

    float[] target(long sequence, long now, float fromYaw, float fromPitch,
                   float yaw, float pitch, double distance, boolean hold) {
        refreshNanos = now;
        if (sampledSequence != sequence) {
            engine.updateSpeeds(settings.rotation);
            sampledSequence = sequence;
        }
        engine.observeMotion(fromYaw, fromPitch);
        holding = hold;
        if (hold) engine.hold(fromYaw, fromPitch);
        engine.setRotation(new float[]{yaw, pitch}, settings.rotation, settings.reduction(distance));
        return hold ? new float[]{fromYaw, fromPitch} : engine.rotation();
    }

    void framePre() { engine.mousePre(); framePrepared = true; }

    void frameUpdate(float cameraYaw, float cameraPitch, float sensitivity) {
        if (!framePrepared) return;
        framePrepared = false;
        if (!holding) engine.mouseUpdate(cameraYaw, cameraPitch, sensitivity);
    }

    boolean fresh(long now) { return now >= refreshNanos && now - refreshNanos <= 150_000_000L; }

    void accepted(float previousYaw, float previousPitch, float yaw, float pitch) {
        boolean rotating = Float.compare(previousYaw, yaw) != 0 || Float.compare(previousPitch, pitch) != 0;
        engine.observeAcceptedPacket(rotating, yaw, pitch);
    }
}
