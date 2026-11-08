package me.youngjibbit.clickgui;

/**
 * Approach-2 ClickGUI. Reuses Panel's real focus, mouse capture, category,
 * search, slider, ComboValue, config and GUI-transition paths.
 *
 * Only the presentation/initial dimensions change; no parallel input engine.
 */
public class NeutronTwoGui extends ClickGui {
    @Override protected boolean modernPresentation() { return true; }
}
