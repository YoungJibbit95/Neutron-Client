package me.youngjibbit.command.impl;

import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.core.diagnostics.PerformanceCaptureSession;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.utils.ChatUtil;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.util.Locale;

@CommandInfo(name="perf", aliases={"performance"}, description="Capture bounded frame, CPU/GPU and chunk evidence",
        usage=".perf start [1–600 seconds] | .perf stop | .perf status | .perf export <label>")
public final class PerformanceCommand extends Command {
    @Override public void execute(String... args) throws CommandException {
        if (args.length == 0) throw new CommandException(getUsage());
        PerformanceCaptureSession capture = PerformanceCaptureSession.INSTANCE;
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start":
                    if (args.length > 2) break;
                    int seconds = args.length == 2 ? Integer.parseInt(args[1]) : 120;
                    capture.start(Minecraft.getMinecraft(), seconds);
                    ChatUtil.addChatMessage("Performance capture started for " + seconds + " s (bounded). Keep scene/settings fixed; use .perf status and .perf export <label> after it stops.", true);
                    return;
                case "stop":
                    if (args.length != 1) break;
                    capture.stop(); ChatUtil.addChatMessage(capture.status(), true); return;
                case "status":
                    if (args.length != 1) break;
                    ChatUtil.addChatMessage(capture.status(), true); return;
                case "export":
                    if (args.length != 2) break;
                    ChatUtil.addChatMessage("Performance evidence saved to " + capture.export(Minecraft.getMinecraft(), args[1]).getAbsolutePath(), true);
                    return;
                default: break;
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException failure) {
            throw new CommandException(failure.getMessage());
        }
        throw new CommandException(getUsage());
    }
}
