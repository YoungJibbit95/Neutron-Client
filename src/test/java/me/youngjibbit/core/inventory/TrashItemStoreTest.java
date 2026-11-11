package me.youngjibbit.core.inventory;

import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class TrashItemStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path file() { return temporary.getRoot().toPath().resolve("inventory-trash.json"); }
    private void text(String value) throws IOException { Files.write(file(), value.getBytes(StandardCharsets.UTF_8)); }
    @Test public void explicitVariantRulesRoundTripAndDoNotExposeMutableCollections() throws Exception {
        TrashItemStore store = new TrashItemStore(file());
        assertTrue(store.keys().isEmpty()); assertFalse(Files.exists(file()));
        store.setBlacklisted("minecraft:wool@14", true); store.setBlacklisted("minecraft:diamond_sword@*", true);
        TrashItemStore reopened = new TrashItemStore(file());
        assertEquals(store.keys(), reopened.keys()); assertFalse(reopened.contains("minecraft:wool@0"));
        reopened.setBlacklisted("minecraft:wool@14", false);
        assertFalse(new TrashItemStore(file()).contains("minecraft:wool@14"));
        try { store.keys().clear(); fail("Mutable policy leaked"); } catch (UnsupportedOperationException expected) { }
        try (java.util.stream.Stream<Path> files = Files.list(temporary.getRoot().toPath())) { assertEquals(1, files.count()); }
    }
    @Test public void corruptFutureDuplicateAndTrailingDataNeverReplaceLastValidPolicy() throws Exception {
        TrashItemStore store = new TrashItemStore(file()); store.setBlacklisted("minecraft:dirt@0", true);
        String valid = new String(Files.readAllBytes(file()), StandardCharsets.UTF_8);
        String[] invalid = {"{", valid.replace("\"version\": 1", "\"version\": 2"),
                valid.replace("\"minecraft:dirt@0\"", "\"minecraft:dirt@0\", \"minecraft:dirt@0\""),
                valid + " {}", valid.replace("minecraft:dirt@0", "minecraft:dirt@65536"),
                valid.replace("\"version\": 1", "\"version\": 1, \"version\": 1")};
        for (String bad : invalid) {
            text(bad); store.reload(); assertFalse(store.lastError().isEmpty()); assertTrue(store.contains("minecraft:dirt@0"));
            try { store.setBlacklisted("minecraft:stone@0", true); fail("Corrupt policy was overwritten"); } catch (IOException expected) { }
            assertEquals(bad, new String(Files.readAllBytes(file()), StandardCharsets.UTF_8));
        }
        text(valid); store.reload(); assertEquals("", store.lastError());
    }
    @Test public void oversizedReadAndFailedWriteLeaveSavedAndActivePolicyUntouched() throws Exception {
        Files.write(file(), new byte[524289]); TrashItemStore tooLarge = new TrashItemStore(file());
        assertFalse(tooLarge.lastError().isEmpty()); assertTrue(tooLarge.keys().isEmpty());
        Files.delete(file()); TrashItemStore store = new TrashItemStore(file()); assertTrue(store.keys().isEmpty());
        Files.createDirectory(file()); Files.write(file().resolve("occupied"), new byte[]{1});
        try { store.setBlacklisted("minecraft:dirt@0", true); fail("Write unexpectedly succeeded"); } catch (IOException expected) { }
        assertTrue(store.keys().isEmpty()); assertTrue(Files.exists(file().resolve("occupied")));
    }
    @Test public void durabilityIsWildcardButFunctionalMetadataIsExact() {
        Bootstrap.register();
        assertEquals(TrashItemStore.keyFor(new ItemStack(Items.diamond_sword)), TrashItemStore.keyFor(new ItemStack(Items.diamond_sword, 1, 100)));
        assertNotEquals(TrashItemStore.keyFor(new ItemStack(Blocks.wool, 1, 0)), TrashItemStore.keyFor(new ItemStack(Blocks.wool, 1, 14)));
        assertNotEquals(TrashItemStore.keyFor(new ItemStack(Items.potionitem, 1, 8197)), TrashItemStore.keyFor(new ItemStack(Items.potionitem, 1, 16389)));
    }
}
