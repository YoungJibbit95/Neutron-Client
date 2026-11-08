package me.youngjibbit.theme;

import me.youngjibbit.utils.Theme;

/**
 * Read-only runtime appearance layer. The theme catalog still owns the saved
 * palette reference; this switch only selects which global presentation uses it.
 * No new renderer, shader, font, theme-library ID or configuration migration.
 */
public final class ThemeMode {
    public static final String LEGACY="Neutron Legacy";
    public static final String APPROACH_TWO="Neutron 2.0";
    private static volatile String selected=LEGACY;
    private ThemeMode() {}
    public static void select(String name){
        selected=APPROACH_TWO.equals(name)?APPROACH_TWO:LEGACY;
    }
    public static String selected(){return selected;}
    public static Theme global(Theme legacy){
        return APPROACH_TWO.equals(selected)?Theme.approachTwo():legacy;
    }
}
