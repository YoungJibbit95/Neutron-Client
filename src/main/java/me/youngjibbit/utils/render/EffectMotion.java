package me.youngjibbit.utils.render;

/**
 * Screen-space inertia for decorative contours. Inputs use physical pixels and a
 * shared frame clock in seconds. The returned flow opposes object movement;
 * positive X points right and positive Y points down. No GL or game state is read.
 */
public final class EffectMotion {
    private static final double RESPONSE = 18.0;
    private static final double MAX_GAP = .25;
    private boolean initialized, active;
    private double lastSeconds, x, y, velocityX, velocityY;
    private float lastX, lastY, lastWidth, lastHeight;
    private int lastViewportWidth, lastViewportHeight;
    private float flowX, flowY, energy;

    public void sample(double seconds, float centerX, float centerY,
                       float width, float height, int viewportWidth, int viewportHeight,
                       boolean enabled) {
        if (!Double.isFinite(seconds) || !Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0
                || viewportWidth <= 0 || viewportHeight <= 0) {
            reset();
            return;
        }
        if (!enabled || !initialized || !active || viewportWidth != lastViewportWidth
                || viewportHeight != lastViewportHeight || seconds < lastSeconds) {
            anchor(seconds, centerX, centerY, width, height, viewportWidth, viewportHeight, enabled);
            return;
        }
        // A surface may composite more than once. The first sample owns this frame.
        double dt = seconds - lastSeconds;
        if (dt < .000001) return;
        double dx = (double) centerX - lastX, dy = (double) centerY - lastY;
        double jumpLimit = Math.max(64, Math.hypot(lastWidth, lastHeight) * .65);
        jumpLimit = Math.max(jumpLimit, Math.max(viewportWidth, viewportHeight) * 4.0 * dt);
        if (dt > MAX_GAP || Math.hypot(dx, dy) > jumpLimit
                || resized(lastWidth, width) || resized(lastHeight, height)) {
            anchor(seconds, centerX, centerY, width, height, viewportWidth, viewportHeight, true);
            return;
        }

        double speedScale = Math.max(96, Math.max(width, height) * 3.0);
        double targetX = -dx / (dt * speedScale), targetY = -dy / (dt * speedScale);
        double magnitude = Math.hypot(targetX, targetY);
        if (magnitude > 1) { targetX /= magnitude; targetY /= magnitude; }

        // Closed-form critically damped oscillator: no Euler substeps or FPS-dependent drag.
        double decay = Math.exp(-RESPONSE * dt);
        double offsetX = x - targetX, offsetY = y - targetY;
        double changeX = (velocityX + RESPONSE * offsetX) * dt;
        double changeY = (velocityY + RESPONSE * offsetY) * dt;
        x = targetX + (offsetX + changeX) * decay;
        y = targetY + (offsetY + changeY) * decay;
        velocityX = (velocityX - RESPONSE * changeX) * decay;
        velocityY = (velocityY - RESPONSE * changeY) * decay;
        magnitude = Math.hypot(x, y);
        double bound = Math.max(1, magnitude);
        flowX = (float) (x / bound);
        flowY = (float) (y / bound);
        energy = (float) Math.min(1, magnitude);
        remember(seconds, centerX, centerY, width, height, viewportWidth, viewportHeight);
    }

    private static boolean resized(float previous, float current) {
        return Math.abs((double) current - previous) > Math.max(24, previous * .45);
    }

    private void anchor(double seconds, float centerX, float centerY, float width, float height,
                        int viewportWidth, int viewportHeight, boolean enabled) {
        x = y = velocityX = velocityY = 0;
        flowX = flowY = energy = 0;
        initialized = true;
        active = enabled;
        remember(seconds, centerX, centerY, width, height, viewportWidth, viewportHeight);
    }

    private void remember(double seconds, float centerX, float centerY, float width, float height,
                          int viewportWidth, int viewportHeight) {
        lastSeconds = seconds;
        lastX = centerX; lastY = centerY; lastWidth = width; lastHeight = height;
        lastViewportWidth = viewportWidth; lastViewportHeight = viewportHeight;
    }

    public float flowX() { return flowX; }
    public float flowY() { return flowY; }
    public float energy() { return energy; }

    public void reset() {
        initialized = active = false;
        lastSeconds = x = y = velocityX = velocityY = 0;
        lastX = lastY = lastWidth = lastHeight = flowX = flowY = energy = 0;
        lastViewportWidth = lastViewportHeight = 0;
    }
}
