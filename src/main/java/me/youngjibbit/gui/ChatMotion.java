package me.youngjibbit.gui;

/** Bounded chat presentation. Input and message lifetimes remain owned by vanilla. */
public final class ChatMotion {
    public static final int LINE_HEIGHT = 14;
    public static final float TEXT_INSET = 7;
    private final MenuSpring focus = new MenuSpring();
    private long previous = Long.MIN_VALUE, edited = Long.MIN_VALUE;
    private float response, seconds, typing;
    private boolean open;

    public void open(boolean value) { open = value; }
    public void edited(long now) { edited = now; }

    public void advance(long now, boolean animate, String intensity) {
        seconds = previous == Long.MIN_VALUE || now < previous ? 0
                : Math.min(.05f, (now - previous) / 1_000_000_000f);
        previous = now;
        MenuMotion preset = "Calm".equals(intensity) ? MenuMotion.CALM
                : "Expressive".equals(intensity) ? MenuMotion.EXPRESSIVE : MenuMotion.BALANCED;
        response = focus.step(open ? 1 : 0, seconds, animate, false, preset);
        if (!open && response < .002f) response = focus.step(0, 0, false);
        response = Math.max(0, Math.min(1.08f, response));
        float age = edited == Long.MIN_VALUE || now < edited ? 1 : (now - edited) / 1_000_000_000f;
        typing = animate && open && age < .3f
                ? (float) (Math.exp(-19 * age) * Math.cos(29 * age)) : 0;
    }

    public float seconds() { return seconds; }
    public float focusAlpha() { return clamp(response); }
    public float inputSlide() { return (1 - response) * 16; }
    public float textSlideX() { return typing * 1.8f; }
    public float textSlideY() { return -typing * .7f; }
    public float chatSlide() { return (1 - response) * 2; }

    public void reset() {
        focus.reset();
        previous = edited = Long.MIN_VALUE;
        response = seconds = typing = 0;
        open = false;
    }

    /** Vanilla's ten-second lifetime, with a smooth final second. */
    public static float lifetimeAlpha(float ticks) {
        float t = clamp((ticks - 180) / 20);
        return 1 - t * t * (3 - 2 * t);
    }

    /** A small anticipation before the message slides away; no oscillating alpha. */
    public static float exitSlide(float ticks) {
        float t = clamp((ticks - 180) / 20);
        return t * t * (1.65f * t - .65f);
    }

    public static int textWidth(int configuredWidth, float scale, int viewportWidth) {
        float safeScale = Float.isFinite(scale) ? Math.max(.01f, scale) : 1;
        return Math.max(12, (int) Math.floor(Math.min(configuredWidth / safeScale,
                (Math.max(32, viewportWidth) - 8) / safeScale - TEXT_INSET * 2)));
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    /** One small state per retained line, bounded by vanilla's 100-line history. */
    public static final class Line {
        private final long appeared;
        private final HudSpring position = new HudSpring();

        public Line(long appeared) { this.appeared = appeared; }

        public float appearance(long now, boolean animate) {
            if (!animate || appeared == Long.MIN_VALUE) return 1;
            float t = now < appeared ? 0 : clamp((now - appeared) / 280_000_000f);
            float v = t - 1;
            return 1 + 1.7f * v * v * v + .7f * v * v;
        }

        public float y(int row, float seconds, boolean animate, String intensity) {
            float target = -row * LINE_HEIGHT - LINE_HEIGHT + 3;
            return animate ? position.step(target, seconds, true, false, intensity) : position.snap(target);
        }
    }
}
