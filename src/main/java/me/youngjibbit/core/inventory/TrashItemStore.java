package me.youngjibbit.core.inventory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Strictness;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Client-wide explicit vanilla-variant blacklist. Failed reads/writes never replace the saved policy. */
public final class TrashItemStore {
    private static final int MAX_BYTES = 524288, MAX_ITEMS = 4096;
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+@(?:\\*|[0-9]{1,5})");
    private final Path file;
    private Set<String> entries = Collections.emptySet();
    private boolean loaded;
    private String error = "";
    private long revision;
    public TrashItemStore(Path file) { this.file = file == null ? null : file.toAbsolutePath().normalize(); }
    public static TrashItemStore memory() { return new TrashItemStore(null); }

    public static String keyFor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        ResourceLocation id = Item.itemRegistry.getNameForObject(stack.getItem());
        if (id == null) return "";
        Item item = stack.getItem();
        return id + "@" + (item.getMaxDamage() > 0 ? "*" : Integer.toString(item.getHasSubtypes() ? stack.getMetadata() : 0));
    }
    public static boolean validKey(String key) {
        if (key == null || key.length() > 128 || !KEY.matcher(key).matches()) return false;
        String metadata = key.substring(key.lastIndexOf('@') + 1);
        return metadata.equals("*") || Integer.parseInt(metadata) <= 32767;
    }
    public boolean contains(String key) { ensureLoaded(); return entries.contains(key); }
    public Set<String> keys() { ensureLoaded(); return entries; }
    public String lastError() { ensureLoaded(); return error; }
    public long revision() { ensureLoaded(); return revision; }
    public Path path() { return file; }
    private void ensureLoaded() { if (!loaded) reload(); }

    public void reload() {
        loaded = true;
        try {
            Set<String> candidate = new TreeSet<>();
            if (file != null && Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES)
                    throw new IOException("Trash file is a link or exceeds 512 KiB.");
                byte[] bytes = readBounded(file);
                try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8.newDecoder()))) {
                    reader.setStrictness(Strictness.STRICT);
                    read(reader, candidate);
                }
            }
            entries = Collections.unmodifiableSet(candidate); error = ""; revision++;
        } catch (IOException | RuntimeException ex) {
            error = "Cannot read Trash list: " + concise(ex);
        }
    }
    private static byte[] readBounded(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length, total = 0;
            while ((length = in.read(buffer)) >= 0) {
                total += length;
                if (total > MAX_BYTES) throw new IOException("Trash file exceeds 512 KiB.");
                out.write(buffer, 0, length);
            }
            return out.toByteArray();
        }
    }
    private static void read(JsonReader reader, Set<String> candidate) throws IOException {
        Set<String> fields = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!fields.add(name)) throw new IOException("Duplicate field: " + name);
            if (name.equals("format")) {
                if (!"neutron-inventory-trash".equals(reader.nextString())) throw new IOException("Unknown Trash format.");
            } else if (name.equals("version")) {
                if (reader.peek() != JsonToken.NUMBER || !"1".equals(reader.nextString()))
                    throw new IOException("Unsupported Trash version.");
            } else if (name.equals("items")) {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.STRING) throw new IOException("Item key must be text.");
                    String key = reader.nextString();
                    if (!validKey(key) || !candidate.add(key) || candidate.size() > MAX_ITEMS)
                        throw new IOException("Invalid, duplicate or excessive item entry.");
                }
                reader.endArray();
            } else throw new IOException("Unknown field: " + name);
        }
        reader.endObject();
        if (fields.size() != 3 || reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Incomplete Trash document.");
    }

    public void setBlacklisted(String key, boolean blacklist) throws IOException {
        ensureLoaded();
        if (!error.isEmpty()) throw new IOException(error);
        if (!validKey(key)) throw new IOException("Invalid item variant.");
        TreeSet<String> candidate = new TreeSet<>(entries);
        boolean changed = blacklist ? candidate.add(key) : candidate.remove(key);
        if (!changed) return;
        if (candidate.size() > MAX_ITEMS) throw new IOException("Trash list is full.");
        if (file != null) write(candidate);
        entries = Collections.unmodifiableSet(candidate); revision++;
    }
    private void write(Set<String> candidate) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException("Trash file is a link.");
        JsonObject root = new JsonObject(); root.addProperty("format", "neutron-inventory-trash");
        root.addProperty("version", 1); JsonArray items = new JsonArray();
        for (String key : candidate) items.add(key);
        root.add("items", items);
        byte[] bytes = new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Trash list is too large.");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".inventory-trash-", ".tmp");
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static String concise(Exception ex) {
        String message = ex.getMessage(); return message == null ? ex.getClass().getSimpleName() : message;
    }
}
