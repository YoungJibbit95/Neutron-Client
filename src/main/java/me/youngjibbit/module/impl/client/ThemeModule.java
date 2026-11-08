package me.youngjibbit.module.impl.client;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name="Theme",description="Changes the Theme for the whole Client",category=Category.Client)
public class ThemeModule extends Module {
    public final ThemeSelectionValue selectedTheme=new ThemeSelectionValue(this,ThemeCatalog.global().selectedReference());
    // Append after the historical Theme Value: legacy configs and preset ordering stay stable.
    public final ComboValue themeMode=new ComboValue("Theme Mode",this,
            new String[]{ThemeMode.LEGACY,ThemeMode.APPROACH_TWO},0);
    public ThemeModule(){setKey(Keyboard.KEY_0);ThemeMode.select(themeMode.getValue());}
    @Override public void onValueChange(){
        ThemeCatalog.global().select(selectedTheme.getValue());
        ThemeMode.select(themeMode.getValue());
    }
    public static Theme getTheme(){return Theme.getDefaultTheme();}
}
