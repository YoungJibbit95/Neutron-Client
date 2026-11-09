package me.youngjibbit.module.impl.client.island;

import java.util.Locale;

/**
 * Frozen, GL-free content for one Island frame. Rendering never asks a live entity,
 * module or setting for data from inside a deferred shader callback.
 */
public final class IslandPresentation {
    public final IslandStateMachine.State state;
    public final String title, detail, trailing;
    public final String brand,brandVersion;
    public final IslandClockFace clockFace;
    public final IslandStateMachine.Scaffold scaffold;
    public final float healthFraction, absorptionFraction;
    public final boolean hasHealthBar, fixtureOnly;

    private IslandPresentation(IslandStateMachine.State state, String title, String detail,
                               String trailing, float healthFraction, float absorptionFraction,
                               boolean hasHealthBar, boolean fixtureOnly) {
        this(state,title,detail,trailing,healthFraction,absorptionFraction,hasHealthBar,fixtureOnly,"","",null);
    }

    private IslandPresentation(IslandStateMachine.State state,String title,String detail,String trailing,
                               float healthFraction,float absorptionFraction,boolean hasHealthBar,
                               boolean fixtureOnly,String brand,String brandVersion,IslandStateMachine.Scaffold scaffold) {
        this(state,title,detail,trailing,healthFraction,absorptionFraction,hasHealthBar,fixtureOnly,
                brand,brandVersion,scaffold,state==IslandStateMachine.State.CLOCK_REMINDER?IslandClockFace.from(title):null);
    }

    private IslandPresentation(IslandStateMachine.State state,String title,String detail,String trailing,
                               float healthFraction,float absorptionFraction,boolean hasHealthBar,
                               boolean fixtureOnly,String brand,String brandVersion,IslandStateMachine.Scaffold scaffold,
                               IslandClockFace clockFace) {
        this.brand=brand;this.brandVersion=brandVersion;this.scaffold=scaffold;
        this.clockFace=clockFace;
        this.state = state;
        this.title = title;
        this.detail = detail;
        this.trailing = trailing;
        this.healthFraction = healthFraction;
        this.absorptionFraction = absorptionFraction;
        this.hasHealthBar = hasHealthBar;
        this.fixtureOnly = fixtureOnly;
    }

    /** Preserve event identity and duration while freezing the real current hands for this frame. */
    public IslandPresentation withClockFace(IslandClockFace captured){
        if(state!=IslandStateMachine.State.CLOCK_REMINDER||captured==null)return this;
        return new IslandPresentation(state,title,detail,trailing,healthFraction,absorptionFraction,
                hasHealthBar,fixtureOnly,brand,brandVersion,scaffold,captured);
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
            if (scaffold != null && scaffold.valid()) {
                String detail=clean(scaffold.approach);
                if(!scaffold.profile.isEmpty())detail+=" / "+clean(scaffold.profile);
                if(!scaffold.status.isEmpty())detail+="  "+clean(scaffold.status);
                return new IslandPresentation(state,scaffold.blockCount>=0?"Scaffold  "+scaffold.blockCount+" blocks":"Scaffold",
                        detail, "", 0, 0, false, !scaffold.observed,"","",scaffold);
            }
            state = IslandStateMachine.State.IDLE;
        }
        if (state == IslandStateMachine.State.CLOCK_REMINDER) {
            IslandStateMachine.ClockReminder c = selection.clock;
            String t = c != null ? c.time : clean(selection.label);
            return new IslandPresentation(state, t, "", "", 0, 0, false, false);
        }
        if (state == IslandStateMachine.State.INVENTORY_ACTIVITY) {
            IslandStateMachine.InventoryActivity inv = selection.inventory;
            String act = inv != null ? inv.action : clean(selection.label);
            if(inv!=null&&inv.processedCount>=0)act+="  "+inv.processedCount
                    +(inv.totalCount>=0?" / "+inv.totalCount:"");
            return new IslandPresentation(state,inv==null?"Inventory":clean(inv.producer),act,"",0,0,
                    false,inv==null||!inv.observed);
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
                0, 0, false, false, name, build, null);
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
