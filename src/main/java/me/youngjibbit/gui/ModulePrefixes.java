package me.youngjibbit.gui;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Short read-only HUD details. Resolving a label never advances gameplay or a delay queue. */
public final class ModulePrefixes {
    private ModulePrefixes() { }

    /** HUD-owned sampling keeps a live delay label from scanning the held queue on every render frame. */
    public static final class Sampler {
        private final LongSupplier clock;
        private final Function<Module,String> resolve;
        private Module sampled;
        private long sampledAt;
        private String text;
        public Sampler() { this(System::nanoTime, ModulePrefixes::detail); }
        Sampler(LongSupplier clock, Function<Module,String> resolve) { this.clock=clock; this.resolve=resolve; }
        public String detail(Module module) {
            if (!(module instanceof Backtrack)) return resolve.apply(module);
            long now=clock.getAsLong(), age=now-sampledAt;
            if(sampled!=module || age<0 || age>=50_000_000L) {
                text=resolve.apply(module); sampled=module; sampledAt=now;
            }
            return text;
        }
        public void clear() { sampled=null; text=null; sampledAt=0; }
    }

    public static String detail(Module module) {
        if (module instanceof Backtrack) {
            return backtrack(mode(module, "backtrack_mode"), ((Backtrack)module).getActivitySnapshot());
        }
        if (module instanceof ThemeModule) return clean(Theme.getDefaultTheme().getDisplayName());
        if ("aura".equals(module.getId())) return mode(module, "mode");
        if ("scaffold".equals(module.getId())) {
            String approach = mode(module, "scaffold_approach");
            return "Neutron".equals(approach) ? mode(module, "rotation_mode") : approach;
        }
        String information = clean(module.getInfo());
        return information.isEmpty() ? mode(module, "mode") : information;
    }

    static String backtrack(String mode, ExploitActivitySnapshot activity) {
        return (activity == null ? "Idle" : Math.max(0, activity.ageMillis) + " ms")
                + (mode.isEmpty() ? "" : " (" + mode + ")");
    }
    private static String mode(Module module, String id) {
        ComboValue value = module.getModeSetting(id);
        return value == null ? "" : clean(value.getValue());
    }
    static String clean(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder result = new StringBuilder(Math.min(80, text.length()));
        for (int i = 0, count = 0; i < text.length() && count < 80;) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (cp == '\u00a7') { if (i < text.length()) i += Character.charCount(text.codePointAt(i)); continue; }
            if (!Character.isISOControl(cp)) { result.appendCodePoint(cp); count++; }
        }
        return result.toString().trim();
    }
}
