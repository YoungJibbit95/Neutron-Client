package me.youngjibbit.gui;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Short read-only HUD details. Resolving a label never advances gameplay or a delay queue. */
public final class ModulePrefixes {
    private ModulePrefixes() { }

    public enum Marker { NONE, IDLE, HOLDING, READY, RELEASING }
    public static final class Detail {
        public static final Detail EMPTY = new Detail("", Marker.NONE);
        public final String text;
        public final Marker marker;
        Detail(String text, Marker marker) { this.text = text; this.marker = marker; }
    }

    /** HUD-owned sampling keeps a live delay label from scanning the held queue on every render frame. */
    public static final class Sampler {
        private final LongSupplier clock;
        private final Function<Module,Detail> resolve;
        private final Map<Module,Sample> samples = new IdentityHashMap<>();
        public Sampler() { clock = System::nanoTime; resolve = ModulePrefixes::observe; }
        Sampler(LongSupplier clock, Function<Module,String> resolve) {
            this.clock=clock; this.resolve=module -> new Detail(resolve.apply(module), Marker.NONE);
        }
        public String detail(Module module) {
            return sample(module).text;
        }
        public Detail sample(Module module) {
            if (!live(module)) return resolve.apply(module);
            long now=clock.getAsLong();
            Sample previous=samples.get(module);
            if(previous==null || previous.enabled!=module.isToggled()
                    || now-previous.at<0 || now-previous.at>=50_000_000L) {
                previous=new Sample(now,module.isToggled(),resolve.apply(module));
                samples.put(module,previous);
            }
            return previous.detail;
        }
        public void clear() { samples.clear(); }
        private static final class Sample {
            final long at; final boolean enabled; final Detail detail;
            Sample(long at,boolean enabled,Detail detail){this.at=at;this.enabled=enabled;this.detail=detail;}
        }
    }

    public static String detail(Module module) {
        return observe(module).text;
    }
    private static boolean live(Module module) {
        return module instanceof Backtrack || module instanceof LagRange || module instanceof Tickbase;
    }
    private static Detail observe(Module module) {
        if (module instanceof Backtrack) {
            return activity(mode(module, "backtrack_mode"), ((Backtrack)module).getActivitySnapshot());
        }
        if (module instanceof LagRange) return activity(mode(module,"delay_mode"),((LagRange)module).getActivitySnapshot());
        if (module instanceof Tickbase) return activity(mode(module,"mode"),((Tickbase)module).getActivitySnapshot());
        if (module instanceof ThemeModule) return plain(clean(Theme.getDefaultTheme().getDisplayName()));
        if ("aura".equals(module.getId())) return plain(mode(module, "mode"));
        if ("scaffold".equals(module.getId())) {
            String approach = mode(module, "scaffold_approach");
            return plain("Neutron".equals(approach) ? mode(module, "rotation_mode") : approach);
        }
        String information = clean(module.getInfo());
        return plain(information.isEmpty() ? mode(module, "mode") : information);
    }

    static String backtrack(String mode, ExploitActivitySnapshot activity) {
        return activity(mode,activity).text;
    }
    static Detail activity(String mode, ExploitActivitySnapshot activity) {
        String text="Idle";
        Marker marker=Marker.IDLE;
        if(activity!=null) {
            switch(activity.phase) {
                case HOLDING: text="Hold "+Math.max(0,activity.ageMillis)+" ms"; marker=Marker.HOLDING; break;
                case CHARGED: text="Ready "+activity.credit+" ticks"; marker=Marker.READY; break;
                case RELEASING:
                    text=activity.extraSteps>0?"Step +"+activity.extraSteps:"Release "+activity.credit+" ticks";
                    marker=Marker.RELEASING; break;
                default: break;
            }
        }
        return new Detail(text+(mode.isEmpty()?"":" \u00b7 "+mode),marker);
    }
    private static Detail plain(String text) { return new Detail(text,Marker.NONE); }
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
