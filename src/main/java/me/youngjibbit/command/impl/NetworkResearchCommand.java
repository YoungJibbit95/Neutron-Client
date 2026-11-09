package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.module.impl.client.NetworkResearch;
import me.youngjibbit.utils.ChatUtil;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@CommandInfo(name="research", aliases={"netresearch"}, description="Export passive network research observations",
        usage=".research <on|off|status|export>")
public final class NetworkResearchCommand extends Command {
    @Override public void execute(String... args) throws CommandException {
        NetworkResearch module = (NetworkResearch) Pulsar.INSTANCE.getMm().getModule(NetworkResearch.class);
        if (module == null) throw new CommandException("NetworkResearch is unavailable.");
        if (args.length != 1) throw new CommandException(getUsage());
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "on": module.setToggled(true); break;
            case "off": module.setToggled(false); break;
            case "status": break;
            case "export":
                Path directory = mc.mcDataDir.toPath().resolve("pulsar").resolve("research");
                Path destination = directory.resolve("network-" + UUID.randomUUID() + ".jsonl");
                try {
                    Files.createDirectories(directory);
                    try (Writer writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        module.writeJsonLines(writer);
                    }
                } catch (IOException failure) {
                    throw new CommandException("Research export failed: " + failure.getMessage());
                }
                ChatUtil.addChatMessage("Research exported to " + destination.toAbsolutePath(), true);
                return;
            default: throw new CommandException(getUsage());
        }
        ChatUtil.addChatMessage("NetworkResearch " + (module.isToggled() ? "enabled" : "disabled")
                + " | samples=" + module.snapshot().size() + " | overwritten=" + module.getOverwritten()
                + " | captureFailures=" + module.getCaptureFailures(), true);
    }
}
