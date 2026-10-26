package me.youngjibbit.theme;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;

/** A live choice list with the same setting ID and preset strings as the former static combo. */
public final class ThemeSelectionValue extends ComboValue {
    public ThemeSelectionValue(Module parent,String reference){super("Theme",parent,ThemeCatalog.canonicalReference(reference),new String[0]);}
    @Override public String[] getOptions(){return ThemeCatalog.global().all().stream().map(Theme::name).toArray(String[]::new);}
    @Override public int getSelectedIndex(){String[] options=getOptions();for(int i=0;i<options.length;i++)if(options[i].equals(getValue()))return i;return 0;}
    @Override public void setSelectedIndex(int index){String[] options=getOptions();if(options.length>0)setValue(options[Math.max(0,Math.min(index,options.length-1))]);}
    @Override public void setValue(String reference){
        // Empty backing options intentionally delegate to Value.setValue, firing its normal callbacks once.
        super.setValue(ThemeCatalog.canonicalReference(reference));
    }
    public static String label(String reference){Theme theme=ThemeCatalog.global().find(reference);return theme==null?"Unavailable custom theme":theme.getDisplayName();}
}
