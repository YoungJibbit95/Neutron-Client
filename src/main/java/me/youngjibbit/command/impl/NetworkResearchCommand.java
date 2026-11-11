package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.core.diagnostics.NetworkResearchAnalysis;
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

@CommandInfo(name="research", aliases={"netresearch"}, description="Inspect and export network research recordings",
        usage=".research <on|off|status|report|export|bundle>")
public final class NetworkResearchCommand extends Command {
    @Override public void execute(String... args) throws CommandException {
        NetworkResearch module = (NetworkResearch) Pulsar.INSTANCE.getMm().getModule(NetworkResearch.class);
        if (module == null) throw new CommandException("NetworkResearch is unavailable.");
        if (args.length != 1) throw new CommandException(getUsage());
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "on": module.setToggled(true); break;
            case "off": module.setToggled(false); break;
            case "status": break;
            case "report":
                NetworkResearchAnalysis.Report report = NetworkResearchAnalysis.analyze(module.recording());
                ChatUtil.addChatMessage("Research " + report.origin + " | samples=" + report.retainedSamples
                        + " | tickIntervals=" + report.tickIntervals.samples
                        + " | p95ns=" + report.tickIntervals.p95Nanos
                        + " | quality=" + report.issues + " | serverOutcome=" + report.serverOutcome, true);
                return;
            case "export":
            case "bundle":
                boolean bundle = "bundle".equalsIgnoreCase(args[0]);
                Path directory = mc.mcDataDir.toPath().resolve("pulsar").resolve("research");
                Path destination = directory.resolve("network-" + UUID.randomUUID() + (bundle ? ".json" : ".jsonl"));
                try {
                    Files.createDirectories(directory);
                    try (Writer writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        if (bundle) module.recording().write(writer);
                        else module.writeJsonLines(writer);
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
