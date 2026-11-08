package me.youngjibbit.module.impl.client.island;

import java.util.Locale;

/**
 * Frozen, GL-free content for one Island frame. Rendering never asks a live entity,
 * module or setting for data from inside a deferred shader callback.
 */
public final class IslandPresentation {
    public final IslandStateMachine.State state;
    public final String title, detail, trailing;
    public final float healthFraction, absorptionFraction;
    public final boolean hasHealthBar, fixtureOnly;

    private IslandPresentation(IslandStateMachine.State state, String title, String detail,
                               String trailing, float healthFraction, float absorptionFraction,
                               boolean hasHealthBar, boolean fixtureOnly) {
        this.state = state;
        this.title = title;
        this.detail = detail;
        this.trailing = trailing;
        this.healthFraction = healthFraction;
        this.absorptionFraction = absorptionFraction;
        this.hasHealthBar = hasHealthBar;
        this.fixtureOnly = fixtureOnly;
    }

    public static IslandPresentation from(IslandStateMachine.Selection selection,
                                          String clientName, String version, String clock,
                                          boolean showName, boolean showVersion, boolean showClock) {
        IslandStateMachine.State state = selection == null ? IslandStateMachine.State.IDLE : selection.state;
        if (state == IslandStateMachine.State.TARGET) {
            IslandStateMachine.Target target = selection.target;
            if (target != null && target.valid()) {
                String health = String.format(Locale.ROOT, "%.1f / %.1f HP",
                        target.health, target.maxHealth);
                if (target.absorption > 0)
                    health += String.format(Locale.ROOT, " +%.1f", target.absorption);
                return new IslandPresentation(state, clean(target.name), health, "",
                        target.fraction(), Math.min(1f, target.absorption / target.maxHealth), true, false);
            }
            state = IslandStateMachine.State.IDLE;
        }
        if (state == IslandStateMachine.State.SCAFFOLD) {
            IslandStateMachine.Scaffold scaffold = selection.scaffold;
            if (scaffold != null && scaffold.valid())
                return new IslandPresentation(state, "Scaffold  " + scaffold.blockCount + " blocks",
                        clean(scaffold.approach), "", 0, 0, false, true);
            state = IslandStateMachine.State.IDLE;
        }
        if (state == IslandStateMachine.State.MODULE_TOAST)
            return new IslandPresentation(state, clean(selection.label), "", "",
                    0, 0, false, true);
        if (state == IslandStateMachine.State.POSSIBLE_SETBACK)
            return new IslandPresentation(state, "Possible setback (local)", "", "",
                    0, 0, false, true);

        String name = showName ? clean(clientName) : "";
        String build = showVersion ? clean(version) : "";
        String title = name.isEmpty() ? build : build.isEmpty() ? name : name + " " + build;
        return new IslandPresentation(IslandStateMachine.State.IDLE,
                IslandLayout.limitText(title, 64), "", showClock ? clean(clock) : "",
                0, 0, false, false);
    }

    private static String clean(String value) {
        if (value == null || value.isEmpty()) return "";
        // Never let a data provider inject Minecraft colour formatting or a line break.
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7' && i < value.length()) {
                int format = Character.toLowerCase(value.codePointAt(i));
                if ("0123456789abcdefklmnor".indexOf(format) >= 0) {
                    i += Character.charCount(format);
                    continue;
                }
            }
            if (Character.isISOControl(cp) || cp == '\u00a7') safe.append(' ');
            else safe.appendCodePoint(cp);
        }
        return IslandLayout.limitText(safe.toString(), 64);
    }
}
