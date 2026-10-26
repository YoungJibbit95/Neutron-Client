package me.youngjibbit.module.impl.client;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name="Theme",description="Changes the Theme for the whole Client",category=Category.Client)
public class ThemeModule extends Module {
    public final ThemeSelectionValue selectedTheme=new ThemeSelectionValue(this,ThemeCatalog.global().selectedReference());
    public ThemeModule(){setKey(Keyboard.KEY_0);}
    @Override public void onValueChange(){ThemeCatalog.global().select(selectedTheme.getValue());}
    public static Theme getTheme(){return Theme.getDefaultTheme();}
}
