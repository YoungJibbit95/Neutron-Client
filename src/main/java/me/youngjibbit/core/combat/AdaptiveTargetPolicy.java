package me.youngjibbit.core.combat;

import java.util.List;

/** Stable Single and timed Switch selection; invalid identities are replaced without waiting. */
public final class AdaptiveTargetPolicy {
    private Object selected;
    private long selectedAt;

    public <T> T select(List<T> sorted, T previous, boolean switching, long delayMillis, long nowMillis) {
        if (sorted.isEmpty()) { reset(); return null; }
        int index = -1;
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i) == previous) { index = i; break; }
        T next = index < 0 ? sorted.get(0) : previous;
        if (selected != previous || index < 0 || nowMillis < selectedAt) {
            selected = next; selectedAt = nowMillis;
        } else if (switching && sorted.size() > 1 && nowMillis - selectedAt >= Math.max(0, delayMillis)) {
            next = sorted.get((index + 1) % sorted.size());
            selected = next; selectedAt = nowMillis;
        }
        return next;
    }

    public void reset() { selected = null; selectedAt = 0; }
}
