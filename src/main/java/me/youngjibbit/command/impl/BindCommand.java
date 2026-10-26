package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.ChatUtil;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

@CommandInfo(
        name = "bind",
        usage = ".bind <module> <key|none> | .bind list | .bind clear",
        description = "Manages module keybinds",
        aliases = {"b", "keybind"}
)
public final class BindCommand extends Command {

    @Override
    public void execute(String... args) throws CommandException {
        if (args.length == 0) {
            throw new CommandException("Usage: " + getUsage());
        }

        String action = args[0].toLowerCase();
        if ("list".equals(action)) {
            listBinds();
            return;
        }
        if ("clear".equals(action)) {
            clearBinds();
            return;
        }

        if (args.length < 2) {
            throw new CommandException("Usage: .bind <module> <key|none>");
        }

        String moduleName = Arrays.stream(args, 0, args.length - 1).collect(Collectors.joining(" "));
        Module module = Pulsar.INSTANCE.getMm().getModule(moduleName);
        if (module == null) {
            throw new CommandException("Unknown module: " + moduleName);
        }

        int key = parseKey(args[args.length - 1]);
        module.setKey(key);

        String keyName = key == Keyboard.KEY_NONE ? "NONE" : Keyboard.getKeyName(key);
        ChatUtil.addChatMessage(module.getName() + " bound to " + keyName + ".", true);
    }

    private int parseKey(String rawKey) throws CommandException {
        if (rawKey == null || rawKey.trim().isEmpty()
                || "none".equalsIgnoreCase(rawKey)
                || "clear".equalsIgnoreCase(rawKey)
                || "unbind".equalsIgnoreCase(rawKey)) {
            return Keyboard.KEY_NONE;
        }

        String normalized = rawKey.toUpperCase();
        if (normalized.startsWith("KEY_")) {
            normalized = normalized.substring("KEY_".length());
        }

        int key = Keyboard.getKeyIndex(normalized);
        if (key == Keyboard.KEY_NONE && !"NONE".equals(normalized)) {
            throw new CommandException("Unknown key: " + rawKey);
        }
        return key;
    }

    private void listBinds() {
        String binds = Pulsar.INSTANCE.getMm().getAllModules().stream()
                .filter(module -> module.getKey() != Keyboard.KEY_NONE)
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .map(module -> module.getName() + "=" + Keyboard.getKeyName(module.getKey()))
                .collect(Collectors.joining(", "));

        ChatUtil.addChatMessage(binds.isEmpty() ? "No module binds set." : "Binds: " + binds, true);
    }

    private void clearBinds() {
        for (Module module : Pulsar.INSTANCE.getMm().getAllModules()) {
            module.setKey(Keyboard.KEY_NONE);
        }
        ChatUtil.addChatMessage("All module binds cleared.", true);
    }
}
