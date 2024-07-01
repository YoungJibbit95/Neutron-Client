package me.youngjibbit.module;

import lombok.Getter;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Set;

@Getter
public final class ModuleManager
{
    private final HashMap<Class<? extends Module>, Module> modules;

    public ModuleManager()
    {
        this.modules = new HashMap<>();
        register();
    }

    public final Module getModule(Class<? extends Module> mod)
    {
        return modules.get(mod);
    }

    public final void register()
    {
        final Reflections refl = new Reflections("me.youngjibbit.module.impl");
        final Set<Class<? extends Module>> classes = refl.getSubTypesOf(Module.class);
        for(Class<? extends Module> aClass : classes)
        { try {
                final Module feat = aClass.newInstance();
                modules.put(aClass, feat);
            } catch (InstantiationException | IllegalAccessException ignored) {
            }
        }
    }

    public final void unregister(Module module)
    {

        modules.remove(module);

    }
}
