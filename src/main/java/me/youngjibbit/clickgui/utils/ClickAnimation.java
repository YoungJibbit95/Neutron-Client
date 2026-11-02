package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;

/** Existing quadratic ease-out, retargeted from its current visible value in either direction. */
public final class ClickAnimation extends DecelerateAnimation {
    public ClickAnimation(int duration, double endpoint, Direction direction) { super(duration, endpoint, direction); }

    @Override public ClickAnimation setDirection(Direction next) {
        if (direction == next) return this;
        double progress = endPoint == 0 ? 0 : Math.max(0, Math.min(1, getOutput() / endPoint));
        direction = next;
        // Invert f(t)=1-(1-t)^2 rather than mirroring elapsed time. Mirroring a non-linear
        // curve jumps when the user reverses an opening/closing transition mid-flight.
        double time = next.forwards() ? 1 - Math.sqrt(1 - progress) : 1 - Math.sqrt(progress);
        timerUtil.setTime(System.currentTimeMillis() - Math.round(duration * time));
        return this;
    }
}
