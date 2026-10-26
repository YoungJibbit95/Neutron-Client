package me.youngjibbit.config;

import net.minecraft.client.Minecraft;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

public final class LocalConfig {
    private final String name;
    private final File file;

    public LocalConfig(String name) {
        this(configDirectory(Minecraft.getMinecraft().mcDataDir), name);
    }

    public LocalConfig(File directory, String name) {
        if (name == null || !name.equals(name.trim()) || name.endsWith(".")
                || !name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,63}")) {
            throw new IllegalArgumentException("Config names must contain 1-64 letters, digits, spaces, '.', '_' or '-'; no leading dot or trailing dot/space.");
        }
        if (name.split("\\.", 2)[0].toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("Reserved config filename.");
        }
        this.name = name;
        Path base = directory.toPath().toAbsolutePath().normalize();
        Path target = base.resolve(name + ".json").normalize();
        if (!base.equals(target.getParent())) throw new IllegalArgumentException("Config must stay inside the config directory.");
        this.file = target.toFile();
    }

    public static File configDirectory(File dataDirectory) {
        if (dataDirectory == null) throw new IllegalStateException("Minecraft data directory is unavailable.");
        return new File(new File(dataDirectory, "pulsar"), "configs");
    }

    public String getName() { return name; }
    public File getFile() { return file; }
}
