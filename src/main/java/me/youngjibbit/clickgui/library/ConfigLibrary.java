package me.youngjibbit.clickgui.library;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.config.LocalConfig;
import java.io.File;
import java.util.*;

/** Named-config actions; confirmations capture their targets, never the later selection. */
public final class ConfigLibrary {
    public enum Pending { NONE, OVERWRITE, DELETE, RENAME }
    private final ConfigManager manager;
    private final List<LocalConfig> entries = new ArrayList<>();
    private String selected = "", status = "", target = "", renameSource = "";
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
        refresh(); selected = existing(name); status = "Saved " + selected; return true;
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
        if (newName.equals(selected)) { status = "Name is unchanged."; return true; }
        String collision = existing(newName);
        if (!collision.isEmpty() && !collision.equalsIgnoreCase(selected)) {
            status = "A config named \"" + collision + "\" already exists. Choose another name.";
            return false;
        }
        renameSource = selected;
        target = newName;
        pending = Pending.RENAME;
        status = "Rename \"" + renameSource + "\" to \"" + target + "\"? Saved contents are preserved.";
        return false;
    }
    public void requestDelete() {
        cancel();
        if (manager == null || selected.isEmpty()) { status = "Select a config to delete."; return; }
        target = selected; pending = Pending.DELETE;
        status = "Delete \"" + target + "\"? This removes the saved config.";
    }
    public boolean confirm() {
        Pending action = pending;
        String name = target, source = renameSource;
        cancel();
        if (manager == null || action == Pending.NONE) return false;
        if (action == Pending.OVERWRITE) return write(name);
        if (action == Pending.RENAME) {
            if (!manager.renameConfig(source, name)) { status = manager.getLastError(); return false; }
            refresh(); selected = existing(name); status = "Renamed to \"" + selected + "\".";
            return true;
        }
        if (!manager.delete(name)) {
            String failure = manager.getLastError();
            refresh(); status = failure.isEmpty() ? "Config no longer exists." : failure;
            return false;
        }
        refresh(); status = "Deleted " + name; return true;
    }
    public void cancel() {
        if (pending != Pending.NONE) status = "";
        pending = Pending.NONE; target = ""; renameSource = "";
    }
}