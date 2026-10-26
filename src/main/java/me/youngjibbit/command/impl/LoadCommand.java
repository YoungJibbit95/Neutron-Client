package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.config.LocalConfig;
import me.youngjibbit.exception.CommandException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@CommandInfo(name = "config", usage = ".config <save/load/list> [name]",
        description = "Save and restore module configs", aliases = {"c", "cfg"})
public final class LoadCommand extends Command {
    private final Supplier<ConfigManager> manager;

    public LoadCommand() { this.manager = () -> Pulsar.INSTANCE.getConfigManager(); }
    public LoadCommand(ConfigManager manager) { this.manager = () -> manager; }

    @Override
    public void execute(String... args) throws CommandException {
        if (args.length == 0) throw new CommandException("Usage: " + getUsage());
        ConfigManager configs = manager.get();
        if (configs == null) throw new CommandException("Configs are not initialized.");
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            if (configs.collectConfigs()) configs.message("Configs: " + ConfigManager.localConfigs.stream()
                    .map(LocalConfig::getName).collect(Collectors.joining(", ")));
        } else if ("load".equals(action) || "save".equals(action)) {
            if (args.length < 2) throw new CommandException("Please specify a config name.");
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if ("load".equals(action)) configs.loadConfig(name);
            else if (configs.saveConfig(name)) configs.message("Config saved: " + name);
            else configs.message(configs.getLastError());
        } else throw new CommandException("Usage: " + getUsage());
    }
}
