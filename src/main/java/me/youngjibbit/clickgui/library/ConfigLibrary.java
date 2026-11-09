package me.youngjibbit.clickgui.library;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.config.LocalConfig;
import java.io.File;
import java.util.*;

/** Named-config actions for the library; confirmation captures its target, never the later selection. */
public final class ConfigLibrary {
    public enum Pending { NONE, OVERWRITE, DELETE }
    private final ConfigManager manager;
    private final List<LocalConfig> entries = new ArrayList<>();
    private String selected = "", status = "", target = "";
    private Pending pending = Pending.NONE;

    public ConfigLibrary(ConfigManager manager) { this.manager = manager; refresh(); }
    public List<LocalConfig> entries() { return Collections.unmodifiableList(entries); }
    public String selected() { return selected; }
    public String status() { return status; }
    public String target() { return target; }
    public Pending pending() { return pending; }
    public boolean available() { return manager != null; }
    public void select(String name) { cancel(); selected = existing(name); }

    public void refresh() {
        cancel();
        if (manager == null) { status = "Config storage is unavailable."; return; }
        if (!manager.collectConfigs()) { status = manager.getLastError(); return; }
        entries.clear();
        entries.addAll(ConfigManager.localConfigs);
        selected = existing(selected);
    }
    private String existing(String name) {
        for (LocalConfig entry : entries) if (entry.getName().equalsIgnoreCase(name)) return entry.getName();
        return "";
    }
    public boolean save(String name) {
        cancel();
        if (manager == null) return false;
        try { new LocalConfig(new File("."), name); }
        catch (IllegalArgumentException ex) { status = ex.getMessage(); return false; }
        // Case-insensitive on Windows: "profile" must not silently overwrite "Profile".
        String existing = existing(name);
        if (!existing.isEmpty()) {
            target = existing; pending = Pending.OVERWRITE;
            status = "Replace \"" + target + "\" with the current client settings?";
            return false;
        }
        return write(name);
    }
    private boolean write(String name) {
        if (!manager.saveConfig(name)) { status = manager.getLastError(); return false; }
        refresh(); selected = name; status = "Saved " + name; return true;
    }
    public boolean load() {
        cancel();
        if (manager == null || selected.isEmpty()) { status = "Select a config to load."; return false; }
        if (!manager.loadConfig(selected)) { status = manager.getLastError(); return false; }
        status = "Loaded " + selected; return true;
    }

    public boolean rename(String newName) {
        cancel();
        if (manager == null || selected.isEmpty()) { status = "Select a config to rename."; return false; }
        try { new LocalConfig(new File("."), newName); }
        catch (IllegalArgumentException ex) { status = ex.getMessage(); return false; }
        String existing = existing(newName);
        if (!existing.isEmpty() && !existing.equalsIgnoreCase(selected)) {
            target = existing; pending = Pending.OVERWRITE;
            status = "Replace \"" + target + "\" with the renamed config?";
            return false;
        }
        if (!manager.delete(selected)) { status = manager.getLastError(); return false; }
        boolean written = write(newName);
        if (!written) status = manager.getLastError();
        return written;
    }
    public void requestDelete() {
        cancel();
        if (manager == null || selected.isEmpty()) { status = "Select a config to delete."; return; }
        target = selected; pending = Pending.DELETE;
        status = "Delete \"" + target + "\"? This removes the saved config.";
    }
    public boolean confirm() {
        Pending action = pending; String name = target; cancel();
        if (manager == null || action == Pending.NONE) return false;
        if (action == Pending.OVERWRITE) return write(name);
        if (!manager.delete(name)) { status = manager.getLastError().isEmpty() ? "Config no longer exists." : manager.getLastError(); refresh(); return false; }
        refresh(); status = "Deleted " + name; return true;
    }
    public void cancel() { if(pending!=Pending.NONE)status=""; pending = Pending.NONE; target = ""; }
}
