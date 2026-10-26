package me.youngjibbit.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AccountTextImport {
    private AccountTextImport() { }

    /** Parse the entire file before changing the registry. Passwords may contain colons or spaces. */
    public static List<Account> read(Path file) throws IOException {
        List<Account> accounts = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (number == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.trim().isEmpty()) continue;
                try { accounts.add(parse(line)); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid account at line " + number + "."); }
            }
        }
        return accounts;
    }

    public static Account parse(String line) {
        int separator = line.indexOf(':');
        String principal = (separator < 0 ? line : line.substring(0, separator)).trim();
        String password = separator < 0 ? "" : line.substring(separator + 1);
        if (principal.isEmpty()) throw new IllegalArgumentException("Account identity is required.");
        return new Account(principal, password, principal);
    }
}
