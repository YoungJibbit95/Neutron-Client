package me.youngjibbit;

import lombok.Getter;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.module.ModuleManager;
import me.zero.alpine.bus.EventBus;
import me.zero.alpine.bus.EventManager;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

@Getter
public enum Pulsar implements Subscriber
{
    INSTANCE;

    public static Minecraft mc = Minecraft.getMinecraft();
    public ModuleManager mm = new ModuleManager();
    public static EventBus BUS = EventManager.builder()
            .setName("root/Pulsar")
            .setSuperListeners()
            .build();

    public final String name = "Pulsarware";
    public final String version = "Dev b1.0";
    private final String commandPrefix = ".";
    private final String clientPrefix = "[Pulsar Manager]";
    public final String author = "youngjibbit";

    public void init()
    {
        BUS.subscribe(this);
        Display.setTitle(name + " | " + version + " by " + author);
        mm = new ModuleManager();
    }

    public final void shutdown()
    {
        BUS.unsubscribe(this);
    }

    @Subscribe
    private final Listener<EventKey> listener = new Listener<>(e ->
    {
        if (this.mm != null)
        {
            mm.getModules().values().forEach(m ->
            {
                if (m.getKey() != e.getKey())
                {
                    m.toggle();
                }
            });
        }

    });
}