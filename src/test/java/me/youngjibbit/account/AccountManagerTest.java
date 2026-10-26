package me.youngjibbit.account;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountManagerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Account offline(String name) { return new Account(name, "", name); }
    private File directory() throws IOException { return temporary.newFolder(); }
    private void write(Path file, String content) throws IOException { Files.write(file, content.getBytes(StandardCharsets.UTF_8)); }

    @Test public void createsParentsAndRoundTripsUtf8AndNullFields() throws Exception {
        File parent = new File(directory(), "nested/accounts");
        AccountManager manager = new AccountManager(parent);
        Account account = new Account("synthetic@example.invalid", "p ä:ss ", "Prüfung");
        manager.addAccount(account);
        assertTrue(manager.save());
        AccountManager reloaded = new AccountManager(parent);
        assertEquals(1, reloaded.getAccounts().size());
        Account restored = reloaded.getAccounts().get(0);
        assertEquals(account.getId(), restored.getId());
        assertEquals("Prüfung", restored.getName());
        assertEquals("p ä:ss ", restored.getPassword());
        assertNull(restored.getMicrosoftAccessToken());
        assertNull(restored.getMicrosoftProfileId());
    }

    @Test public void repeatedLoadsReplaceAndDeduplicateWithFirstCredentialsRetained() throws Exception {
        File parent = directory();
        AccountManager manager = new AccountManager(parent);
        Account first = new Account("Synthetic@example.invalid", "first", "old");
        Account duplicate = new Account("synthetic@example.invalid", "second", "new");
        JsonArray list = new JsonArray();
        list.add(first.toJson()); list.add(duplicate.toJson());
        JsonObject json = new JsonObject(); json.add("accounts", list);
        manager.fromJson(json); manager.fromJson(json);
        assertEquals(1, manager.getAccounts().size());
        assertEquals(1, manager.getDuplicateCount());
        assertEquals("first", manager.getAccounts().get(0).getPassword());
        assertSame(manager.getAccounts().get(0), manager.addAccount(duplicate));
        assertTrue(manager.save());
        assertTrue(manager.load()); assertTrue(manager.load());
        assertEquals(1, manager.getAccounts().size());
        assertEquals(1, manager.getDuplicateCount());
        JsonArray retained = manager.toJson().getAsJsonArray("accounts");
        assertEquals(2, retained.size());
        assertEquals("second", retained.get(1).getAsJsonObject().get("password").getAsString());
        assertTrue(manager.remove(manager.getAccounts().get(0)));
        assertEquals(0, manager.toJson().getAsJsonArray("accounts").size());
    }

    @Test public void identitySurvivesDisplayNameChangesAndSeparatesProviders() throws Exception {
        AccountManager manager = new AccountManager(directory());
        Account account = offline("Synthetic");
        String id = account.getId(); account.setName("Renamed");
        manager.addAccount(account);
        manager.addAccount(new Account("Synthetic", "credential", "other", Account.Provider.MICROSOFT));
        assertEquals(id, account.getId());
        assertEquals(2, manager.getAccounts().size());
        assertNotEquals(id, manager.getAccounts().get(1).getId());
    }

    @Test public void lastAccountSurvivesRestartAndIsClearedOnRemoval() throws Exception {
        File parent = directory(); AccountManager manager = new AccountManager(parent);
        Account account = manager.addAccount(offline("Last")); manager.setLastAlt(account);
        assertTrue(manager.save());
        AccountManager restarted = new AccountManager(parent);
        assertEquals(account.getId(), restarted.getLastAlt().getId());
        restarted.remove(restarted.getLastAlt()); assertTrue(restarted.save()); assertTrue(restarted.load());
        assertNull(restarted.getLastAlt());
    }

    @Test public void removalIsByStableIdAndFilteringReturnsIndependentSnapshots() throws Exception {
        AccountManager manager = new AccountManager(directory());
        Account first = offline("First"), second = offline("Second"), third = offline("Third");
        first.setBanned(true); second.setBanned(true);
        manager.addAccount(first); manager.addAccount(second); manager.addAccount(third);
        assertEquals(Collections.singletonList(third), manager.getNotBannedAccounts());
        manager.getAccounts().clear(); manager.getNotBannedAccounts().clear();
        assertEquals(3, manager.getAccounts().size());
        manager.setLastAlt(first);
        assertTrue(manager.remove(first)); assertNull(manager.getLastAlt());
        second.setName("same"); third.setName("same"); manager.remove("SAME");
        assertTrue(manager.getAccounts().isEmpty());
        assertTrue(manager.save()); assertTrue(manager.load());
        assertTrue(manager.getAccounts().isEmpty());
    }

    @Test public void legacyNullTokensMissingOptionalFieldsAndUnknownFieldsArePreserved() throws Exception {
        AccountManager manager = new AccountManager(directory());
        JsonObject legacy = JsonParser.parseString("{\"email\":\"synthetic@example.invalid\",\"password\":\"old secret\",\"name\":\"\",\"microsoftAccessToken\":null,\"future\":{\"flag\":true}}").getAsJsonObject();
        JsonArray list = new JsonArray(); list.add(legacy);
        JsonObject root = new JsonObject(); root.add("accounts", list); root.addProperty("futureRoot", 42);
        manager.fromJson(root);
        Account account = manager.getAccounts().get(0);
        assertEquals(Account.Provider.MICROSOFT, account.getProvider());
        assertEquals("synthetic@example.invalid", account.getName());
        assertEquals("old secret", account.getPassword());
        assertTrue(account.toJson().getAsJsonObject("future").get("flag").getAsBoolean());
        assertEquals(42, manager.toJson().get("futureRoot").getAsInt());
    }

    @Test public void corruptFileNeverClearsMemoryOrOverwritesDiskAndCanBeRepaired() throws Exception {
        File parent = directory(); Path file = parent.toPath().resolve("alts.json");
        AccountManager manager = new AccountManager(parent); manager.addAccount(offline("Kept")); assertTrue(manager.save());
        byte[] valid = Files.readAllBytes(file);
        write(file, "{broken synthetic-secret"); byte[] corrupt = Files.readAllBytes(file);
        assertFalse(manager.load()); assertTrue(manager.isWriteProtected());
        assertEquals(1, manager.getAccounts().size());
        assertFalse(manager.save()); assertArrayEquals(corrupt, Files.readAllBytes(file));
        assertFalse(manager.getLastError().contains("synthetic-secret"));
        AccountManager startup = new AccountManager(parent);
        assertTrue(startup.isWriteProtected()); assertFalse(startup.save());
        assertArrayEquals(corrupt, Files.readAllBytes(file));
        Files.write(file, valid); assertTrue(manager.load()); assertTrue(manager.save());
    }

    @Test public void invalidDocumentsAndPartiallyValidListsAreTransactional() throws Exception {
        String valid = new Gson().toJson(offline("Valid").toJson());
        for (String content : Arrays.asList("", "null", "[]", "{}", "{\"accounts\":null}",
                "{\"accounts\":[" + valid + ",null]}", "{\"accounts\":[],\"accounts\":[]}",
                "{accounts:[]}", "{\"accounts\":[]} trailing", "{\"accounts\":[{\"email\":9}]}",
                "{\"accounts\":[{\"email\":\"a\",\"provider\":\"UNKNOWN\"}]}")) {
            File parent = directory(); Path file = parent.toPath().resolve("alts.json");
            AccountManager manager = new AccountManager(parent); manager.addAccount(offline("Kept"));
            write(file, content);
            assertFalse(content, manager.load()); assertEquals(1, manager.getAccounts().size());
            assertFalse(manager.save()); assertEquals(content, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
    }

    @Test public void conflictingIdsRejectWholeReplacement() throws Exception {
        AccountManager manager = new AccountManager(directory()); manager.addAccount(offline("Kept"));
        Account one = offline("One"), two = offline("Two");
        JsonObject second = two.toJson(); second.addProperty("id", one.getId());
        JsonArray list = new JsonArray(); list.add(one.toJson()); list.add(second);
        JsonObject root = new JsonObject(); root.add("accounts", list);
        try { manager.fromJson(root); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals("Kept", manager.getAccounts().get(0).getName());
    }

    @Test public void atomicReplacementFailureRetainsPreviousFileAndCleansTemporaryFile() throws Exception {
        File parent = directory();
        AccountManager initial = new AccountManager(parent); initial.addAccount(offline("Kept")); assertTrue(initial.save());
        Path file = parent.toPath().resolve("alts.json"); byte[] original = Files.readAllBytes(file);
        AccountManager failing = new AccountManager(parent) {
            @Override protected void replaceFile(Path source, Path destination) throws IOException { throw new IOException("synthetic secret"); }
        };
        failing.addAccount(offline("Unsaved")); assertFalse(failing.save());
        assertArrayEquals(original, Files.readAllBytes(file)); assertFalse(failing.getLastError().contains("synthetic secret"));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(parent.toPath())) {
            int count = 0; for (Path ignored : files) count++; assertEquals(1, count);
        }
    }

    @Test public void failedParentCreationAndExternalChangesDoNotOverwriteData() throws Exception {
        File parent = directory(); Path obstruction = parent.toPath().resolve("file"); write(obstruction, "retain");
        AccountManager failing = new AccountManager(new File(obstruction.toFile(), "nested"));
        failing.addAccount(offline("Unsaved")); assertFalse(failing.save());
        assertEquals("retain", new String(Files.readAllBytes(obstruction), StandardCharsets.UTF_8));
        AccountManager manager = new AccountManager(parent); manager.addAccount(offline("First")); assertTrue(manager.save());
        Path file = parent.toPath().resolve("alts.json"); write(file, "{\"accounts\":[]}");
        assertFalse(manager.save()); assertTrue(manager.isWriteProtected());
        assertEquals("{\"accounts\":[]}", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test public void textImportPreservesPasswordDelimitersAndDoesNotApplyPartialFiles() throws Exception {
        Path file = directory().toPath().resolve("synthetic.txt");
        write(file, "\uFEFFsynthetic@example.invalid: p:ass \n\nOfflineUser\n");
        List<Account> accounts = AccountTextImport.read(file);
        assertEquals(2, accounts.size()); assertEquals(" p:ass ", accounts.get(0).getPassword());
        assertEquals(Account.Provider.OFFLINE, accounts.get(1).getProvider());
        write(file, "Valid:password\n:secret\n");
        try { AccountTextImport.read(file); fail(); } catch (IOException expected) { assertEquals("Invalid account at line 2.", expected.getMessage()); }
    }
}
