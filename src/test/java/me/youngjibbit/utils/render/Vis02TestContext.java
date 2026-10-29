package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeCatalog;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Disposable real Values/owner; no GL, alternate config or production clock. */
public final class Vis02TestContext implements AutoCloseable {
    public final ClientSettings settings=new ClientSettings();
    public final AtomicLong clock=new AtomicLong();
    private final Field field;
    private final ModuleManager before;
    private final ThemeCatalog themes=ThemeCatalog.global();
    public Vis02TestContext() throws Exception {
        field=Pulsar.class.getDeclaredField("mm");field.setAccessible(true);before=Pulsar.INSTANCE.getMm();
        Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);
        ModuleManager owner=(ModuleManager)((sun.misc.Unsafe)u.get(null)).allocateInstance(ModuleManager.class);
        Field index=ModuleManager.class.getDeclaredField("modulesByClass");index.setAccessible(true);
        LinkedHashMap<Class<?>,Object> values=new LinkedHashMap<>();values.put(ClientSettings.class,settings);index.set(owner,values);
        field.set(Pulsar.INSTANCE,owner);ThemeCatalog.install(new ThemeCatalog(null));
        UiEffects.timeSource(clock::get);UiEffects.invalidate();
        settings.gradientMotion.setValue("Animated");settings.gradientSpeed.setValue(.25);UiEffects.beginFrame();
    }
    public void tick(double seconds){clock.addAndGet(Math.round(seconds*1e9));UiEffects.beginFrame();}
    public void advance(double seconds){int steps=(int)Math.ceil(seconds/.05);for(int i=0;i<steps;i++)tick(seconds/steps);}
    @Override public void close() throws Exception {field.set(Pulsar.INSTANCE,before);ThemeCatalog.install(themes);UiEffects.invalidate();UiEffects.timeSource(System::nanoTime);}
}
