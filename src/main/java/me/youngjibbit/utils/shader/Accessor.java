package me.youngjibbit.utils.shader;

import com.google.gson.Gson;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;

public interface Accessor {
    Minecraft mc = Minecraft.getMinecraft();

    default Pulsar getInstance() {
        return Pulsar.INSTANCE;
    }

    default Theme getTheme() {
        return getInstance().getTheme();
    }

    default <T extends Module> T getModule(final Class<T> clazz) {
        return (T) getInstance().getMm().getModule(clazz);
    }

    default Gson getGSON() {
        return getInstance().getGSON();
    }

    default Minecraft getClient() {
        return Minecraft.getMinecraft();
    }
}
