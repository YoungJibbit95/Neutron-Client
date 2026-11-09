package me.youngjibbit.config;

import java.io.IOException;
import java.nio.file.*;

/** Byte-preserving, non-overwriting file rename, including Windows case-only names. */
public final class ConfigRename {
    interface Move { void move(Path source, Path target) throws IOException; }
    private static final Move FILE_MOVE = (source, target) -> Files.move(source, target);

    public static void rename(Path source, Path target) throws IOException {
        rename(source, target, FILE_MOVE);
    }

    static void rename(Path source, Path target, Move move) throws IOException {
        source = source.toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        if (!source.getParent().equals(target.getParent()))
            throw new IOException("Config rename must stay in the same directory.");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Original config is missing or is not a regular file.");
        if (Files.isSymbolicLink(target)) throw new IOException("Config links are not supported.");
        if (source.equals(target) && source.toString().equals(target.toString())) return;

        // Match Windows filename rules even on a case-sensitive filesystem. The actual
        // move also refuses replacement, covering a target created after this scan.
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(source.getParent())) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())
                        && !entry.getFileName().toString().equals(source.getFileName().toString()))
                    throw new FileAlreadyExistsException(entry.toString());
            }
        }
        boolean caseOnly = source.getFileName().toString().equalsIgnoreCase(target.getFileName().toString());
        if (!caseOnly) { move.move(source, target); return; }

        // A distinct intermediate name forces Windows to persist the requested spelling.
        Path temporary = Files.createTempFile(source.getParent(), ".config-rename-", ".tmp");
        Files.delete(temporary);
        move.move(source, temporary);
        try {
            move.move(temporary, target);
        } catch (IOException failure) {
            try { move.move(temporary, source); }
            catch (IOException rollback) {
                IOException retained = new IOException("Rename failed; original bytes are retained at " + temporary, failure);
                retained.addSuppressed(rollback);
                throw retained;
            }
            throw failure;
        }
    }

    private ConfigRename() { }
}
