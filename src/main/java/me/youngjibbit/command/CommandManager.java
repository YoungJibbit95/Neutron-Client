package me.youngjibbit.command;

import lombok.Getter;
import me.youngjibbit.Pulsar;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.utils.ChatUtil;
import org.reflections.Reflections;

import java.util.*;

@Getter
public final class CommandManager
{
    public static final String COMMAND_PREFIX = Pulsar.INSTANCE.getCommandPrefix();
    private Map<String, Command> commands;

    public CommandManager()
    {
        this.init();
    }

    private void init()
    {
        this.commands = new LinkedHashMap<>();
        try {
            this.register();
        } catch (CommandException e)
        {
            throw new CommandException("Failed to register commands!");
        }
    }

    public boolean handleCommand(final String message)
    {
        if (message == null || message.trim().isEmpty() || !message.startsWith(COMMAND_PREFIX)) {
            return false;
        }

        final String payload = message.substring(COMMAND_PREFIX.length()).trim();
        if (payload.isEmpty())
        {
            ChatUtil.addChatMessage(String
                            .format("Type %shelp to get a list of commands.", COMMAND_PREFIX)
                    , true);
            return true;
        }

        final String[] args = payload.split("\\s+");
        try {
            this.getCommand(args[0]).orElseThrow(() ->
                            new CommandException(String
                                    .format("Command \"%s\" not found. Use %shelp.", args[0], COMMAND_PREFIX)))
                    .execute(Arrays.copyOfRange(args, 1, args.length));
        } catch (CommandException exception) {
            // CommandException already reports to chat.
        } catch (Exception exception) {
            ChatUtil.addChatMessage("Command failed: " + exception.getMessage(), true);
            exception.printStackTrace();
        }
        return true;
    }

    private void register() throws CommandException
    {
        final Reflections reflections = new Reflections("me.youngjibbit.command.impl");
        final List<Class<? extends Command>> classes = new ArrayList<>(reflections.getSubTypesOf(Command.class));
        classes.sort(Comparator.comparing(Class::getName));
        for (Class<? extends Command> command : classes)
        {
            try {
                final Command comm = command.getDeclaredConstructor().newInstance();
                this.commands.put(normalize(comm.getName()), comm);
            } catch (Exception e)
            {
                System.err.println("couldn't initialize command: " + command.getName() + "!");
            }
        }
    }

    public Optional<Command> getCommand(String commandName)
    {
        final String normalized = normalize(commandName);
        final Command command = this.commands.get(normalized);
        if (command != null)
        {
            return Optional.of(command);
        }

        return this.commands.values()
                .stream()
                .filter(cmd -> cmd.isAlias(normalized))
                .findFirst();
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }
}
