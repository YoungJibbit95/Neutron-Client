package me.youngjibbit.core.world;

/** Local successful-placement quota; normal input supplies the resulting sneak. */
public final class ScaffoldSneakInterval {
    private int successes, remaining;
    private long lastInput = Long.MIN_VALUE;
    private boolean inputSneak;

    public void reset() { successes = remaining = 0; lastInput = Long.MIN_VALUE; inputSneak = false; }

    public void placed(int interval) {
        if (interval <= 0) { reset(); return; }
        if (++successes >= interval) { successes = 0; remaining = 2; }
    }

    /** One identity per fresh movement-input read; previews never consume hold time. */
    public boolean input(long sequence) {
        if (lastInput == sequence) return inputSneak;
        lastInput = sequence;
        inputSneak = remaining > 0;
        if (remaining > 0) remaining--;
        return inputSneak;
    }
}
