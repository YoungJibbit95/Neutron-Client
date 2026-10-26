package me.youngjibbit.account;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class AccountManager {
    private final ArrayList<Account> accounts = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Path altsFile;
    private JsonObject template = new JsonObject();
    private final ArrayList<JsonObject> duplicateRecords = new ArrayList<>();
    private byte[] diskContents;
    private boolean writeProtected;
    private String lastError = "";
    private int duplicateCount;
    private String alteningKey, lastAlteningAlt;
    private Account lastAlt;

    public AccountManager(File parent) {
        altsFile = parent.toPath().toAbsolutePath().normalize().resolve("alts.json");
        load();
    }

    public synchronized boolean save() {
        if (writeProtected) return fail("Account file is protected after a load error. Repair it and reload before saving.");
        Path temporary = null;
        try {
            byte[] current = Files.notExists(altsFile) ? null : Files.readAllBytes(altsFile);
            if (!Arrays.equals(diskContents, current)) {
                writeProtected = true;
                return fail("Account file changed on disk. Reload before saving.");
            }
            Files.createDirectories(altsFile.getParent());
            temporary = Files.createTempFile(altsFile.getParent(), ".alts-", ".tmp");
            byte[] bytes = gson.toJson(toJson()).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            replaceFile(temporary, altsFile);
            diskContents = bytes;
            lastError = "";
            return true;
        } catch (IOException | RuntimeException failure) {
            return fail("Could not save accounts. The previous file was retained.");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    /** Fail closed on filesystems without atomic replacement. Never truncate the destination. */
    protected void replaceFile(Path source, Path destination) throws IOException {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized boolean load() {
        try {
            if (Files.notExists(altsFile)) {
                if (diskContents != null || writeProtected) throw new IOException("Account file disappeared.");
                lastError = "";
                return true;
            }
            byte[] bytes = Files.readAllBytes(altsFile);
            JsonElement parsed;
            try (Reader input = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.newDecoder());
                 JsonReader reader = new JsonReader(input)) {
                reader.setLenient(false);
                parsed = readElement(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing data.");
            }
            if (!parsed.isJsonObject()) throw new IOException("Expected object.");
            fromJson(parsed.getAsJsonObject());
            diskContents = bytes;
            writeProtected = false;
            lastError = "";
            return true;
        } catch (IOException | RuntimeException failure) {
            writeProtected = true;
            return fail("Could not load accounts. Existing entries and the file were retained; saving is blocked until a successful reload.");
        }
    }

    private static JsonElement readElement(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IOException("JSON is too deep.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if (object.has(field)) throw new IOException("Duplicate field.");
                    object.add(field, readElement(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readElement(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("Expected JSON value.");
        }
    }

    public synchronized JsonObject toJson() {
        JsonObject result = template.deepCopy();
        JsonArray list = new JsonArray();
        for (Account account : accounts) list.add(account.toJson());
        // Keep credentials from pre-existing duplicate rows until the user explicitly removes that identity.
        for (JsonObject duplicate : duplicateRecords) list.add(duplicate.deepCopy());
        result.add("accounts", list);
        result.addProperty("lastAccountId", lastAlt == null ? null : lastAlt.getId());
        return result;
    }

    public synchronized void fromJson(JsonObject json) {
        JsonElement list = json.get("accounts");
        if (list == null || !list.isJsonArray()) throw new IllegalArgumentException("Missing accounts array.");
        ArrayList<Account> loaded = new ArrayList<>();
        ArrayList<JsonObject> retainedDuplicates = new ArrayList<>();
        Map<String, Account> identities = new HashMap<>();
        Map<String, Account> ids = new HashMap<>();
        int duplicates = 0;
        for (JsonElement element : list.getAsJsonArray()) {
            Account account = new Account();
            account.fromJson(element.getAsJsonObject());
            Account sameId = ids.get(account.getId());
            if (sameId != null && !sameId.identityKey().equals(account.identityKey())) throw new IllegalArgumentException("Conflicting account ID.");
            ids.put(account.getId(), account);
            if (identities.containsKey(account.identityKey())) {
                duplicates++;
                retainedDuplicates.add(element.getAsJsonObject().deepCopy());
                continue;
            }
            identities.put(account.identityKey(), account);
            loaded.add(account);
        }
        String lastId = lastAlt == null ? null : lastAlt.getId();
        if (json.has("lastAccountId")) {
            JsonElement last = json.get("lastAccountId");
            if (!last.isJsonNull() && (!last.isJsonPrimitive() || !last.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Invalid last account ID.");
            lastId = last.isJsonNull() ? null : last.getAsString();
        }
        accounts.clear();
        accounts.addAll(loaded);
        duplicateRecords.clear();
        duplicateRecords.addAll(retainedDuplicates);
        template = json.deepCopy();
        duplicateCount = duplicates;
        lastAlt = getAccountById(lastId);
    }

    public synchronized Account addAccount(Account account) {
        Account stored = resolveAccount(account);
        if (stored != account) return stored;
        accounts.add(account);
        return account;
    }

    public synchronized Account resolveAccount(Account account) {
        Objects.requireNonNull(account, "account");
        for (Account existing : accounts) {
            if (existing.identityKey().equals(account.identityKey())) return existing;
            if (existing.getId().equals(account.getId())) throw new IllegalArgumentException("Conflicting account ID.");
        }
        return account;
    }

    public synchronized boolean remove(Account account) {
        if (account == null) return false;
        boolean removed = accounts.removeIf(existing -> existing.getId().equals(account.getId()));
        if (removed) duplicateRecords.removeIf(record -> {
            Account duplicate = new Account(); duplicate.fromJson(record);
            return duplicate.identityKey().equals(account.identityKey());
        });
        if (lastAlt != null && lastAlt.getId().equals(account.getId())) lastAlt = null;
        return removed;
    }

    public synchronized void remove(String username) {
        for (Account account : new ArrayList<>(accounts)) if (account.getName().equalsIgnoreCase(username)) remove(account);
    }

    public synchronized void clear() { accounts.clear(); duplicateRecords.clear(); lastAlt = null; }
    public synchronized Account getAccountById(String id) {
        for (Account account : accounts) if (account.getId().equals(id)) return account;
        return null;
    }
    public synchronized Account getAccountByEmail(String email) {
        for (Account account : accounts) if (account.getEmail().equalsIgnoreCase(email)) return account;
        return null;
    }
    public synchronized void addMicrosoftAccount(Account account) { addAccount(account); save(); }
    public synchronized ArrayList<Account> getAccounts() { return new ArrayList<>(accounts); }
    public synchronized ArrayList<Account> getNotBannedAccounts() {
        ArrayList<Account> eligible = new ArrayList<>();
        for (Account account : accounts) if (!account.isBanned()) eligible.add(account);
        return eligible;
    }
    private boolean fail(String message) { lastError = message; return false; }
    public synchronized String getLastError() { return lastError; }
    public synchronized int getDuplicateCount() { return duplicateCount; }
    public synchronized boolean isWriteProtected() { return writeProtected; }
    public synchronized String getLastAlteningAlt() { return lastAlteningAlt; }
    public synchronized void setLastAlteningAlt(String value) { lastAlteningAlt = value; }
    public synchronized String getAlteningKey() { return alteningKey; }
    public synchronized void setAlteningKey(String value) { alteningKey = value; }
    public synchronized Account getLastAlt() { return lastAlt; }
    public synchronized void setLastAlt(Account value) { lastAlt = value; }
}
