package me.youngjibbit.theme;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.utils.Theme;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Bootstrap definitions before module/config restoration; selection remains in the existing setting. */
public final class ThemeRuntime {
    private ThemeRuntime(){}
    public static void initialize(File dataDirectory){
        Path folder=dataDirectory.toPath().resolve("pulsar/theme");ThemeCatalog catalog=new ThemeCatalog(new ThemeStore(folder.resolve("themes.json")));ThemeCatalog.install(catalog);
        Path legacy=folder.resolve("theme_config.txt");
        try{if(Files.isRegularFile(legacy,LinkOption.NOFOLLOW_LINKS)&&Files.size(legacy)<=1024)catalog.select(new String(Files.readAllBytes(legacy),StandardCharsets.UTF_8).trim());}
        catch(IOException ex){System.err.println("Legacy theme was not loaded: "+ex.getMessage());}
        if(catalog.isReadOnly())System.err.println("Theme library is read-only: "+catalog.error());
    }
    public static void select(String reference){
        ThemeModule module=Pulsar.INSTANCE.getMm()==null?null:(ThemeModule)Pulsar.INSTANCE.getMm().getModule(ThemeModule.class);
        if(module!=null)module.selectedTheme.setValue(reference);else ThemeCatalog.global().select(reference);
    }
}
