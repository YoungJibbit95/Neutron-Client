package me.neutron.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Production entry point. No working-directory or test-source discovery. */
public final class ClientBootstrap {
    private static final Set<String> PATH_OPTIONS = new HashSet<String>(
            Arrays.asList("--gameDir", "--assetsDir", "--nativesDir"));

    private ClientBootstrap() { }

    public static void main(String[] args) {
        try {
            List<String> forwarded = validate(args);
            if (Arrays.asList(args).contains("--validateOnly")) {
                System.out.println("NEUTRON_BOOTSTRAP_VALIDATED");
                return;
            }
            // Resolve the legacy client only after validating the distribution contract.
            net.minecraft.client.main.Main.main(forwarded.toArray(new String[forwarded.size()]));
        } catch (IllegalArgumentException exception) {
            System.err.println("Neutron start rejected: " + exception.getMessage());
            System.exit(2);
        }
    }

    static List<String> validate(String[] args) {
        if (!"1.8".equals(System.getProperty("java.specification.version"))
                || !"64".equals(System.getProperty("sun.arch.data.model"))) {
            throw new IllegalArgumentException("A 64-bit Java 8 runtime is required.");
        }
        List<String> forwarded = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        Path assets = null;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if ("--validateOnly".equals(option)) {
                continue;
            }
            if (PATH_OPTIONS.contains(option) || "--assetIndex".equals(option)
                    || "--version".equals(option)) {
                if (!seen.add(option) || i + 1 == args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing or duplicate value for " + option);
                }
                String value = args[++i];
                if (PATH_OPTIONS.contains(option)) {
                    Path path = Paths.get(value);
                    if (!path.isAbsolute() || !Files.isDirectory(path)) {
                        throw new IllegalArgumentException(option + " must name an existing absolute directory.");
                    }
                    path = path.toAbsolutePath().normalize();
                    if ("--assetsDir".equals(option)) {
                        assets = path;
                    } else if ("--nativesDir".equals(option)) {
                        validateNatives(path);
                        System.setProperty("org.lwjgl.librarypath", path.toString());
                        System.setProperty("net.java.games.input.librarypath", path.toString());
                        continue;
                    } else if (!path.equals(Paths.get("").toAbsolutePath().normalize())) {
                        throw new IllegalArgumentException("Process working directory must equal --gameDir.");
                    }
                } else if (value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Empty value for " + option);
                } else if ("--assetIndex".equals(option) && !"1.8".equals(value)) {
                    throw new IllegalArgumentException("This client requires asset index 1.8.");
                }
                forwarded.add(option);
                forwarded.add(value);
            } else if ("--accessToken".equals(option) || "--userProperties".equals(option)
                    || "--profileProperties".equals(option)) {
                throw new IllegalArgumentException("Authentication is handled inside the client.");
            } else if ("--username".equals(option) || "--width".equals(option) || "--height".equals(option)) {
                if (!seen.add(option) || i + 1 == args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing or duplicate value for " + option);
                }
                forwarded.add(option);
                String value = args[++i];
                if (!"--username".equals(option)) {
                    try {
                        int dimension = Integer.parseInt(value);
                        if (dimension < 240 || dimension > 16384) {
                            throw new IllegalArgumentException("Window size must be between 240 and 16384.");
                        }
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Window size must be an integer.");
                    }
                } else if (!value.matches("[A-Za-z0-9_]{1,16}")) {
                    throw new IllegalArgumentException("Offline username must contain 1-16 letters, digits or underscores.");
                }
                forwarded.add(value);
            } else if ("--fullscreen".equals(option)) {
                forwarded.add(option);
            } else {
                throw new IllegalArgumentException("Unknown launcher option: " + option);
            }
        }
        Set<String> required = new HashSet<String>(PATH_OPTIONS);
        required.add("--version");
        required.add("--assetIndex");
        if (!seen.containsAll(required)) {
            throw new IllegalArgumentException("Explicit gameDir, assetsDir, nativesDir, version and assetIndex are required.");
        }
        if (assets == null || !Files.isRegularFile(assets.resolve("indexes").resolve("1.8.json"))) {
            throw new IllegalArgumentException("Asset index 1.8.json is missing.");
        }
        forwarded.addAll(Arrays.asList("--accessToken", "0", "--userProperties", "{}"));
        return forwarded;
    }

    private static void validateNatives(Path path) {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        if (!"amd64".equals(arch) && !"x86_64".equals(arch)) {
            throw new IllegalArgumentException("V1 requires an x64 Java process, including on Apple Silicon.");
        }
        String[] names;
        if (os.startsWith("Windows")) {
            names = new String[]{"lwjgl64.dll", "OpenAL64.dll", "jinput-raw_64.dll"};
        } else if (os.startsWith("Linux")) {
            names = new String[]{"liblwjgl64.so", "libopenal64.so", "libjinput-linux64.so"};
        } else if (os.startsWith("Mac")) {
            names = new String[]{"liblwjgl.dylib", "openal.dylib", "libjinput-osx.jnilib"};
        } else {
            throw new IllegalArgumentException("Unsupported operating system.");
        }
        for (String name : names) {
            if (!Files.isRegularFile(path.resolve(name))) {
                throw new IllegalArgumentException("Required native library missing: " + name);
            }
        }
    }
}
