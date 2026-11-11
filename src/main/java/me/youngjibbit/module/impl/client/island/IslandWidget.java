package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;

/** One small frozen widget. Labels and values are observations, the pulse is decoration only. */
public final class IslandWidget {
    public enum Kind { CPS, FPS, EXPLOIT }
    public final Kind kind;
    public final String label, value, detail;
    public final boolean active;
    public final float pulse, phase;

    public IslandWidget(Kind kind, String label, String value, String detail,
                        boolean active, float seconds, boolean motion) {
        this.kind = kind;
        this.label = IslandLayout.limitText(label, 12);
        this.value = IslandLayout.limitText(value, 12);
        this.detail = IslandLayout.limitText(detail, 16);
        this.active = active;
        phase = active && motion && Float.isFinite(seconds)
                ? (float) (seconds * .24 - Math.floor(seconds * .24)) : 0;
        pulse = active && motion && Float.isFinite(seconds)
                ? .65f + .35f * (.5f - .5f * (float) Math.cos(seconds * Math.PI * 2)) : 1;
    }

    /** Prefer an observed release; cycle equal-priority active producers without inventing activity. */
    public static IslandWidget exploit(ExploitActivitySnapshot[] observations, long now, float seconds, boolean motion) {
        int priority = -1, count = 0;
        if (observations != null) for (ExploitActivitySnapshot candidate : observations) {
            if (candidate == null || !candidate.isFresh(now)) continue;
            int rank = priority(candidate);
            if (rank > priority) { count = 0; priority = rank; }
            if (rank == priority) count++;
        }
        if (count == 0) return new IslandWidget(Kind.EXPLOIT, "EXP", "--", "Idle", false, seconds, motion);
        int index = (int) Math.floorMod(now / 2_000_000_000L, (long) count);
        ExploitActivitySnapshot item = null;
        for (ExploitActivitySnapshot candidate : observations)
            if (candidate != null && candidate.isFresh(now) && priority(candidate) == priority && index-- == 0) { item = candidate; break; }
        if(item == null) return new IslandWidget(Kind.EXPLOIT, "EXP", "--", "Idle", false, seconds, motion);
        String label = item.producer == ExploitActivitySnapshot.Producer.BACKTRACK ? "BT"
                : item.producer == ExploitActivitySnapshot.Producer.LAGRANGE ? "LR" : "TB";
        String value, state;
        if (item.phase == ExploitActivitySnapshot.Phase.HOLDING) {
            value = item.ageMillis < 1000 ? item.ageMillis + "ms"
                    : String.format(java.util.Locale.ROOT, "%.1fs", item.ageMillis / 1000.0);
            state = "Hold";
        } else if (item.phase == ExploitActivitySnapshot.Phase.RELEASING) {
            value = item.extraSteps > 0 ? "+" + item.extraSteps + "t" : item.credit + "t";
            state = "Fire";
        } else { value = item.credit + "t"; state = "Ready"; }
        return new IslandWidget(Kind.EXPLOIT, label, value, state, true, seconds, motion);
    }
    private static int priority(ExploitActivitySnapshot item) {
        return item.phase == ExploitActivitySnapshot.Phase.RELEASING ? 2
                : item.phase == ExploitActivitySnapshot.Phase.HOLDING ? 1 : 0;
    }

}
