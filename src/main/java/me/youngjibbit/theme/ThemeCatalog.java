package me.youngjibbit.theme;
import me.youngjibbit.utils.Theme;
import java.io.IOException;
import java.util.*;

/** One runtime selection and one temporary preview; immutable definitions are replaced as a unit. */
public final class ThemeCatalog {
    private static final Theme[] PRESETS=Theme.values();
    private static class Global {static ThemeCatalog instance=new ThemeCatalog(null);}
    public static ThemeCatalog global(){return Global.instance;}
    public static void install(ThemeCatalog catalog){Global.instance=Objects.requireNonNull(catalog);}
    private final ThemeStore store;
    private final LinkedHashMap<String,Theme> custom=new LinkedHashMap<>();
    private String selected="PinkLemonade";
    private Theme selectedTheme=Theme.PinkLemonade;
    private Theme preview;
    public ThemeCatalog(ThemeStore store){this.store=store;if(store!=null)for(Theme t:store.load())custom.put(t.name(),t);}
    public Theme find(String reference){if(reference==null)return null;for(Theme preset:PRESETS)if(preset.name().equalsIgnoreCase(reference))return preset;return custom.get(reference);}
    public static String canonicalReference(String reference){for(Theme preset:PRESETS)if(preset.name().equalsIgnoreCase(reference))return preset.name();if(reference!=null&&reference.startsWith("custom:")&&reference.substring(7).equals(UUID.fromString(reference.substring(7)).toString()))return reference;throw new IllegalArgumentException("Unknown theme reference");}
    /** Drawing reads a resolved immutable theme; only catalog mutations resolve the saved ID. */
    public Theme current(){return preview!=null?preview:selectedTheme;}
    private void resolveSelected(){Theme theme=find(selected);selectedTheme=theme==null?Theme.PinkLemonade:theme;}
    public String selectedReference(){return selected;}
    public void select(String reference){try{selected=canonicalReference(reference);}catch(IllegalArgumentException ex){selected=Theme.PinkLemonade.name();}preview=null;resolveSelected();}
    public void preview(Theme theme){preview=Objects.requireNonNull(theme);}
    public void cancelPreview(){preview=null;}
    public boolean isPreviewing(){return preview!=null;}
    public java.util.List<Theme> all(){java.util.List<Theme> result=new ArrayList<>(Arrays.asList(PRESETS));result.addAll(custom.values());return Collections.unmodifiableList(result);}
    public boolean isReadOnly(){return store==null||store.isReadOnly();}
    public String error(){return store==null?"Theme library not initialized":store.getError();}
    public void save(Theme theme)throws IOException{
        if(store==null)throw new IOException(error());if(!theme.isCustom())throw new IOException("Presets are immutable; duplicate first");
        LinkedHashMap<String,Theme> next=new LinkedHashMap<>(custom);next.put(theme.name(),theme);store.save(next.values());custom.clear();custom.putAll(next);resolveSelected();
    }
    public void delete(String id)throws IOException{
        if(!custom.containsKey(id))throw new IOException("Only saved custom themes can be deleted");
        LinkedHashMap<String,Theme> next=new LinkedHashMap<>(custom);next.remove(id);store.save(next.values());custom.clear();custom.putAll(next);preview=null;if(selected.equals(id))selected=Theme.PinkLemonade.name();resolveSelected();
    }
}
