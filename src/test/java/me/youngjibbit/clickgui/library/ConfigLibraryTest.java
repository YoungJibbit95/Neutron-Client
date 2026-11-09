package me.youngjibbit.clickgui.library;

import me.youngjibbit.config.*;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class ConfigLibraryTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @ModuleInfo(name="Library fixture",category=Category.Client)
    public static class Sample extends Module {
        final SliderValue amount=new SliderValue("Amount",this,2.0,0,10);
    }
    private Sample sample;
    private ConfigLibrary library;
    private Path folder;
    @Before public void setup()throws Exception {
        folder=temporary.newFolder().toPath();sample=new Sample();
        library=new ConfigLibrary(new ConfigManager(folder,Collections.singletonList(sample),()->false,message->{}));
    }
    @Test public void saveAndLoadReachTheActualConfigManager()throws Exception {
        sample.amount.setValue(7.25);assertTrue(library.save("Practice"));
        assertTrue(Files.isRegularFile(folder.resolve("Practice.json")));
        sample.amount.setValue(1.0);library.select("Practice");assertTrue(library.load());
        assertEquals(7.25,sample.amount.getValue(),0);
    }
    @Test public void overwriteNeedsConfirmationAndCaseVariantsKeepTheExistingFile()throws Exception {
        assertTrue(library.save("Practice"));byte[] original=Files.readAllBytes(folder.resolve("Practice.json"));
        sample.amount.setValue(9.0);assertFalse(library.save("practice"));
        assertEquals(ConfigLibrary.Pending.OVERWRITE,library.pending());
        assertArrayEquals(original,Files.readAllBytes(folder.resolve("Practice.json")));
        library.cancel();assertArrayEquals(original,Files.readAllBytes(folder.resolve("Practice.json")));
        library.save("practice");assertTrue(library.confirm());
        assertEquals(1,library.entries().size());sample.amount.setValue(1.0);library.load();
        assertEquals(9,sample.amount.getValue(),0);
    }
    @Test public void changingSelectionDisarmsDestructiveAction()throws Exception {
        library.save("One");library.save("Two");library.select("One");library.requestDelete();
        library.select("Two");assertFalse(library.confirm());
        assertTrue(Files.exists(folder.resolve("One.json")));assertTrue(Files.exists(folder.resolve("Two.json")));
        library.requestDelete();assertTrue(library.confirm());
        assertTrue(Files.exists(folder.resolve("One.json")));assertFalse(Files.exists(folder.resolve("Two.json")));
    }
    @Test public void invalidNamesAndCorruptFilesKeepCurrentValues()throws Exception {
        assertFalse(library.save("../escape"));assertFalse(library.save("CON"));
        Files.write(folder.resolve("Broken.json"),"{ invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        library.refresh();library.select("Broken");
        assertFalse(library.load());assertEquals(2,sample.amount.getValue(),0);
        assertFalse(library.status().isEmpty());
    }
    @Test public void absentStorageIsAnExplicitEmptyState() {
        ConfigLibrary absent=new ConfigLibrary(null);
        assertFalse(absent.available());assertFalse(absent.save("One"));
        assertTrue(absent.entries().isEmpty());assertFalse(absent.status().isEmpty());
    }

    @Test public void renamePreservesSavedSourceInsteadOfCurrentClient() throws Exception {
        sample.amount.setValue(7.25);assertTrue(library.save("Saved"));
        byte[] original=Files.readAllBytes(folder.resolve("Saved.json"));
        sample.amount.setValue(1.0);assertFalse(library.rename("Renamed"));
        assertEquals(ConfigLibrary.Pending.RENAME,library.pending());
        assertTrue(library.confirm());assertEquals("Renamed",library.selected());
        assertFalse(Files.exists(folder.resolve("Saved.json")));
        assertArrayEquals(original,Files.readAllBytes(folder.resolve("Renamed.json")));
        assertTrue(library.load());assertEquals(7.25,sample.amount.getValue(),0);
    }
    @Test public void unchangedRenameNeverDeletesSavedConfig() throws Exception {
        library.save("Saved");byte[] bytes=Files.readAllBytes(folder.resolve("Saved.json"));
        assertTrue(library.rename("Saved"));assertFalse(library.confirm());
        assertArrayEquals(bytes,Files.readAllBytes(folder.resolve("Saved.json")));
    }
    @Test public void renameCollisionRefusesToReplaceAnotherConfig() throws Exception {
        library.save("One");library.save("Two");library.select("One");
        byte[] one=Files.readAllBytes(folder.resolve("One.json"));
        byte[] two=Files.readAllBytes(folder.resolve("Two.json"));
        assertFalse(library.rename("two"));assertEquals(ConfigLibrary.Pending.NONE,library.pending());
        assertFalse(library.confirm());assertArrayEquals(one,Files.readAllBytes(folder.resolve("One.json")));
        assertArrayEquals(two,Files.readAllBytes(folder.resolve("Two.json")));
    }
    @Test public void caseOnlyRenameRetainsBytesAndUsesNewSpelling() throws Exception {
        library.save("Saved");byte[] bytes=Files.readAllBytes(folder.resolve("Saved.json"));
        assertFalse(library.rename("saved"));assertTrue(library.confirm());
        assertEquals("saved",library.selected());assertEquals(1,library.entries().size());
        assertArrayEquals(bytes,Files.readAllBytes(folder.resolve("saved.json")));
    }
    @Test public void cancelAndSelectionChangeDisarmCapturedRenameSource() throws Exception {
        library.save("One");library.save("Two");library.select("One");
        library.rename("Renamed");library.cancel();assertFalse(library.confirm());
        library.rename("Renamed");library.select("Two");assertFalse(library.confirm());
        assertTrue(Files.exists(folder.resolve("One.json")));assertTrue(Files.exists(folder.resolve("Two.json")));
        assertFalse(Files.exists(folder.resolve("Renamed.json")));
    }
    @Test public void collisionCreatedAfterDialogDoesNotOverwriteAndPreservesSource() throws Exception {
        library.save("One");assertFalse(library.rename("New"));
        Files.write(folder.resolve("New.json"),new byte[]{12,34});
        assertFalse(library.confirm());assertFalse(library.status().isEmpty());
        assertTrue(Files.exists(folder.resolve("One.json")));
        assertArrayEquals(new byte[]{12,34},Files.readAllBytes(folder.resolve("New.json")));
    }
    @Test public void missingSourceDuringConfirmationReportsIoFailure() throws Exception {
        library.save("One");library.rename("New");Files.delete(folder.resolve("One.json"));
        assertFalse(library.confirm());assertFalse(library.status().isEmpty());
        assertFalse(Files.exists(folder.resolve("New.json")));
    }

}
