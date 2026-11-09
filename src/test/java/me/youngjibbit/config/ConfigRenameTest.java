package me.youngjibbit.config;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ConfigRenameTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path file(String name) { return temporary.getRoot().toPath().resolve(name); }
    private byte[] bytes() { return "{ saved bytes, not current values }\r\n\u00e4".getBytes(StandardCharsets.UTF_8); }

    @Test public void renamePreservesEveryByteAndRemovesOnlyTheSource() throws Exception {
        Path source=file("Saved.json"), target=file("Renamed.json");
        Files.write(source,bytes()); ConfigRename.rename(source,target);
        assertFalse(Files.exists(source)); assertArrayEquals(bytes(),Files.readAllBytes(target));
    }
    @Test public void unchangedNameIsANonDestructiveNoOp() throws Exception {
        Path source=file("Saved.json"); Files.write(source,bytes());
        ConfigRename.rename(source,source); assertArrayEquals(bytes(),Files.readAllBytes(source));
    }
    @Test public void existingTargetIsNeverOverwritten() throws Exception {
        Path source=file("Saved.json"), target=file("Other.json");
        Files.write(source,bytes()); Files.write(target,new byte[]{7,8,9});
        try { ConfigRename.rename(source,target); fail(); } catch(FileAlreadyExistsException expected) { }
        assertArrayEquals(bytes(),Files.readAllBytes(source)); assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(target));
    }
    @Test public void caseInsensitiveTargetCollisionIsAlsoRejected() throws Exception {
        Path source=file("Saved.json"), target=file("Other.json");
        Files.write(source,bytes()); Files.write(target,new byte[]{42});
        try { ConfigRename.rename(source,file("other.json")); fail(); } catch(FileAlreadyExistsException expected) { }
        assertArrayEquals(bytes(),Files.readAllBytes(source)); assertArrayEquals(new byte[]{42},Files.readAllBytes(target));
    }
    @Test public void caseOnlyRenameUsesTheRequestedSpellingWithoutDeletingContents() throws Exception {
        Path source=file("Saved.json"), target=file("saved.json"); Files.write(source,bytes());
        ConfigRename.rename(source,target); assertArrayEquals(bytes(),Files.readAllBytes(target));
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(temporary.getRoot().toPath())) {
            int count=0; for(Path entry:entries) { assertEquals("saved.json",entry.getFileName().toString()); count++; }
            assertEquals(1,count);
        }
    }
    @Test public void secondMoveFailureRollsBackCaseOnlyRename() throws Exception {
        Path source=file("Saved.json"); Files.write(source,bytes()); AtomicInteger calls=new AtomicInteger();
        try {
            ConfigRename.rename(source,file("saved.json"),(from,to)->{
                if(calls.incrementAndGet()==2)throw new IOException("injected target failure");
                Files.move(from,to);
            }); fail();
        } catch(IOException expected) { assertEquals("injected target failure",expected.getMessage()); }
        assertArrayEquals(bytes(),Files.readAllBytes(source)); assertEquals(3,calls.get());
    }
    @Test public void failedRollbackRetainsTheOriginalInAReportedRecoveryFile() throws Exception {
        Path source=file("Saved.json"); Files.write(source,bytes()); AtomicInteger calls=new AtomicInteger();
        try {
            ConfigRename.rename(source,file("saved.json"),(from,to)->{
                if(calls.incrementAndGet()>1)throw new IOException("injected failure"); Files.move(from,to);
            }); fail();
        } catch(IOException expected) {
            assertTrue(expected.getMessage().contains(".config-rename-")); assertEquals(1,expected.getSuppressed().length);
        }
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(temporary.getRoot().toPath())) {
            int count=0;for(Path entry:entries) {assertArrayEquals(bytes(),Files.readAllBytes(entry));count++;}assertEquals(1,count);
        }
    }
    @Test public void targetCreatedDuringTheMoveCannotBeReplaced() throws Exception {
        Path source=file("Saved.json"),target=file("New.json"); Files.write(source,bytes());
        try {
            ConfigRename.rename(source,target,(from,to)->{ Files.write(to,new byte[]{99}); Files.move(from,to); }); fail();
        } catch(FileAlreadyExistsException expected) { }
        assertArrayEquals(bytes(),Files.readAllBytes(source)); assertArrayEquals(new byte[]{99},Files.readAllBytes(target));
    }
    @Test public void missingSourceAndDirectoryEscapeFailBeforeWriting() throws Exception {
        try { ConfigRename.rename(file("Missing.json"),file("New.json")); fail(); } catch(IOException expected) { }
        Path source=file("Saved.json"); Files.write(source,bytes());
        try { ConfigRename.rename(source,temporary.newFolder().toPath().resolve("New.json"));fail(); }catch(IOException expected) { }
        assertArrayEquals(bytes(),Files.readAllBytes(source));
    }
}
