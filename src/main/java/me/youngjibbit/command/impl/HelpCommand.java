package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.utils.ChatUtil;

import java.util.Comparator;
import java.util.stream.Collectors;

@CommandInfo(
        name = "help",
        usage = ".help [command]",
        description = "Shows available client commands",
        aliases = {"h", "commands"}
)
public final class HelpCommand extends Command {

    @Override
    public void execute(String... args) throws CommandException {
        if (args.length > 0) {
            Command command = Pulsar.INSTANCE.getCm().getCommand(args[0])
                    .orElseThrow(() -> new CommandException("Unknown command: " + args[0]));
            ChatUtil.addChatMessage(command.getName() + " - " + command.getDescription(), true);
            ChatUtil.addChatMessage("Usage: " + command.getUsage(), true);
            return;
        }

        String commands = Pulsar.INSTANCE.getCm().getCommands().values().stream()
                .sorted(Comparator.comparing(Command::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Command::getName)
                .collect(Collectors.joining(", "));
        ChatUtil.addChatMessage("Commands: " + commands, true);
        ChatUtil.addChatMessage("Use .help <command> for details.", true);
    }
}
