package me.youngjibbit.command;

import lombok.Getter;
import me.youngjibbit.Pulsar;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.utils.ChatUtil;
import net.minecraft.util.EnumChatFormatting;
import org.reflections.Reflections;

import java.util.*;

@Getter
public final class CommandManager
{
    public static final String COMMAND_PREFIX = Pulsar.INSTANCE.getCommandPrefix();

    private Map<String, Command> commands;

    public CommandManager()
    {
        init();
        try
        {
            register();
        } catch (CommandException e)
        {
            e.printStackTrace();
        }
    }

    private void init()
    {
        this.commands = new HashMap<>();
        try
        {

        } catch (CommandException e)
        {
            throw new CommandException("Failed to register Commands!");
        }
    }

    public boolean handleCommand(final String message)
    {
        if (message.isEmpty()) return false;

        final String[] args = message.substring(1).split("");

        if (message.equalsIgnoreCase(COMMAND_PREFIX))
        {
            ChatUtil.addChatMessage(String
                    .format("Type %shelp for a list of commands and usage. \n Type %s <command> to see information about a specfic command\n", COMMAND_PREFIX, COMMAND_PREFIX), true);
            return true;
        }

        try
        {
            this.getCommand(args[0]).orElseThrow(() ->
                    new CommandException(String
                            .format("ERROR:\n Command \"%s\" not found. \n Use %help to see all Commands!", args[0], COMMAND_PREFIX)))
                    .execute(Arrays.copyOfRange(args, 1, args.length));

        } catch (CommandException ignored) {}
        return true;
    }


    private void register() throws CommandException {
        final Reflections reflections = new Reflections("me.youngjibbit.command.impl");
        final Set<Class<? extends Command>> classes = reflections.getSubTypesOf(Command.class);

        for (Class<? extends Command> command : classes) {
            try {
                final Command comm = command.getDeclaredConstructor().newInstance();
                this.commands.put(comm.getName(), comm);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | java.lang.reflect.InvocationTargetException e) {
                System.err.println("Failed to initialize command: " + command.getName());
            }
        }
    }

    public Optional<Command> getCommand(final String commandName) {
        // First, attempt to get the command directly by name
        Command command = this.commands.get(commandName);
        if (command != null) {
            return Optional.of(command);
        } else {
            // If the command is not found directly, try to find it by alias
            return Optional.ofNullable(this.commands.values()
                    .stream()
                    .filter(cmd -> cmd.isAlias(commandName))
                    .findFirst()
                    .orElseThrow(() ->
                            new CommandException("Command not found: " + commandName)));
        }
    }
}