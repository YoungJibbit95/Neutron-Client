package me.youngjibbit.module;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.Pulsar;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.apache.commons.lang3.Validate;

@Getter
public abstract class Module implements Subscriber
{
    private boolean toggled;
    private final String name, description;
    private final Category category;

    @Setter
    private int key;
    protected final Minecraft mc = Minecraft.getMinecraft();

    public Module()
    {
        ModuleInfo info = getClass().getAnnotation(ModuleInfo.class);
        Validate.notNull(info, "CONFUSED ANNOTATION EXCEPTION");
        this.name = info.name();
        this.description = info.description();
        this.category = info.category();
    }

    public void onEnable()
    {
        Pulsar.BUS.subscribe(this);
    }

    public void onDisable()
    {
        Pulsar.BUS.unsubscribe(this);
    }

    public void toggle()
    {
        onToggle();
        if (toggled)
        {
            toggled = false;
            onDisable();
        } else {
            toggled = true;
            onEnable();
        }
    }

    public void onToggle()
    {

    }

    public void setToggled()
    {
        if(toggled)
        {
            this.toggled = false;
            onDisable();
        } else {
            this.toggled = true;
            onEnable();
        }
    }

}
