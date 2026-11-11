package me.youngjibbit.core.diagnostics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/** Headless recording/reanalysis entrypoint. It never initializes Minecraft or executes recorded events. */
public final class NetworkResearchHarness {
    private static final String USAGE = "Usage: NetworkResearchHarness simulate <baseline|jitter|burst|pause> "
            + "<backtrack|lagrange|tickbase> [capacity]\n"
            + "       NetworkResearchHarness <analyze|verify> <recording.json|->\n"
            + "Exit codes: 0 = completed, 2 = invalid input or I/O failure, 3 = verify found recording-quality issues.\n";

    private NetworkResearchHarness() { }

    public static void main(String[] args) throws IOException {
        Writer out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        Writer err = new OutputStreamWriter(System.err, StandardCharsets.UTF_8);
        Reader input = new InputStreamReader(System.in, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
        int status = run(args, input, out, err);
        out.flush();
        // PrintStream records write failures instead of throwing them through the wrapping Writer.
        if (System.out.checkError()) {
            err.write("Research harness: failed to write standard output.\n");
            status = 2;
        }
        err.flush();
        // A failed diagnostic stream must not turn into a successful exit or corrupt JSON stdout.
        if (System.err.checkError()) status = 2;
        if (status != 0) System.exit(status);
    }

    /** Streams remain owned by the caller. Imported files are read locally and never executed. */
    public static int run(String[] args, Reader input, Writer output, Writer error) throws IOException {
        try {
            if (args.length >= 1 && "simulate".equals(args[0])) {
                if (args.length != 3 && args.length != 4) throw new IllegalArgumentException(USAGE);
                NetworkResearchSimulation.Scenario scenario = NetworkResearchSimulation.Scenario.valueOf(
                        args[1].toUpperCase(Locale.ROOT));
                NetworkResearchTrace.Mode mode = NetworkResearchTrace.Mode.valueOf(args[2].toUpperCase(Locale.ROOT));
                int capacity = args.length == 4 ? Integer.parseInt(args[3]) : 512;
                NetworkResearchTrace trace = new NetworkResearchTrace(capacity);
                NetworkResearchSimulation simulation = new NetworkResearchSimulation();
                simulation.run(scenario, mode, trace);
                trace.recording(0, scenario.name(), simulation.getRejected()).write(output);
                output.write('\n');
                return 0;
            }
            if (args.length != 2 || !("analyze".equals(args[0]) || "verify".equals(args[0]))) {
                throw new IllegalArgumentException(USAGE);
            }
            NetworkResearchRecording recording;
            if ("-".equals(args[1])) {
                recording = NetworkResearchRecording.read(input);
            } else {
                try (BufferedReader reader = Files.newBufferedReader(Paths.get(args[1]), StandardCharsets.UTF_8)) {
                    recording = NetworkResearchRecording.read(reader);
                }
            }
            NetworkResearchAnalysis.Report report = NetworkResearchAnalysis.analyze(recording);
            report.write(output);
            return "verify".equals(args[0]) && !report.issues.isEmpty() ? 3 : 0;
        } catch (IllegalArgumentException | IOException invalid) {
            error.write("Research harness: " + invalid.getMessage() + "\n");
            return 2;
        }
    }
}
