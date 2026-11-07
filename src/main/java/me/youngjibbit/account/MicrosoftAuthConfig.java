package me.youngjibbit.account;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Public app configuration shared by IDE, Minecraft Launcher and Neutron Launcher starts. */
public final class MicrosoftAuthConfig {
    private final Properties system;
    private final Map<String, String> environment;
    private final Path file;

    public MicrosoftAuthConfig() {
        this(System.getProperties(), System.getenv(), Paths.get(System.getProperty("user.home"),
                ".neutron", "microsoft-auth.properties"));
    }

    MicrosoftAuthConfig(Properties system, Map<String, String> environment, Path file) {
        this.system = system;
        this.environment = environment;
        this.file = file;
    }

    private String override() {
        return first(system.getProperty("neutron.microsoft.clientId"), system.getProperty("pulsar.microsoft.clientId"),
                environment.get("NEUTRON_MICROSOFT_CLIENT_ID"), environment.get("PULSAR_MICROSOFT_CLIENT_ID"));
    }

    public String clientId() throws IOException {
        String value = override();
        if (!value.isEmpty()) return value;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { value = read(in); }
            if (!value.isEmpty()) return value;
        }
        try (InputStream in = MicrosoftAuthConfig.class.getResourceAsStream("/neutron-microsoft.properties")) {
            return in == null ? "" : read(in);
        }
    }

    private static String read(InputStream in) throws IOException {
        Properties values = new Properties();
        try { values.load(in); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid Microsoft app configuration."); }
        return first(values.getProperty("clientId"));
    }

    public static boolean valid(String value) {
        return value != null && value.trim().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                && !value.trim().equals("00000000-0000-0000-0000-000000000000");
    }

    public void save(String clientId) throws IOException {
        if (!valid(clientId)) throw new IllegalArgumentException("Enter the Application (client) ID from your own app registration.");
        String value = clientId.trim();
        String override = override();
        if (!override.isEmpty() && !override.equalsIgnoreCase(value))
            throw new IllegalArgumentException("A JVM argument or environment variable overrides this ID. Update or remove that override first.");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "microsoft-auth-", ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("clientId", value);
            try (OutputStream out = Files.newOutputStream(temporary)) {
                values.store(out, "Neutron public Microsoft application ID. No account credentials.");
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }
}
