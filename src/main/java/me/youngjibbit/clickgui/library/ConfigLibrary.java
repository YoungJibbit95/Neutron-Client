package me.youngjibbit.clickgui.library;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.config.LocalConfig;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Named-config actions for the library; confirmation captures its target, never the later selection. */
public final class ConfigLibrary {
    public enum Pending { NONE, OVERWRITE, DELETE, RENAME }
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

    /** Findet den LocalConfig-Eintrag (falls geladen) für den angegebenen Namen. */
    private LocalConfig findEntry(String name) {
        for (LocalConfig entry : entries) if (entry.getName().equalsIgnoreCase(name)) return entry;
        return null;
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
        boolean sameName = existing.equalsIgnoreCase(selected);
        boolean caseOnly = sameName && !existing.equals(selected);

        // Ein anderer Name, der bereits existiert (unter anderer Schreibweise) → Bestätigung nötig.
        if (!sameName && !existing.isEmpty()) {
            target = newName; pending = Pending.RENAME;
            status = "Rename \"" + selected + "\" zu \"" + target + "\"? Bestehende Datei \"" + existing + "\" wird ersetzt.";
            return false;
        }

        // Original-Inhalt lesen, bevor zerstörerische Operationen erfolgen.
        // Durchsuche direkt ConfigManager.localConfigs, um Inkonsistenzen im
        // entries-Cache zu vermeiden.
        LocalConfig oldConfig = null;
        for (LocalConfig c : ConfigManager.localConfigs) {
            if (c.getName().equalsIgnoreCase(selected)) { oldConfig = c; break; }
        }
        if (oldConfig == null) { status = "Original-Konfiguration nicht gefunden."; return false; }
        String oldContent;
        try {
            oldContent = manager.readConfigData(oldConfig.getFile().toPath());
        } catch (IOException ex) {
            status = "Original-Konfiguration nicht lesbar: " + ex.getMessage();
            return false;
        }

        // Neuen Namen atomar mit dem alten Inhalt schreiben (nicht die aktuellen laufenden Werte!).
        if (!manager.saveConfig(newName, oldContent)) {
            status = manager.getLastError();
            return false;
        }
        System.err.println("DEBUG rename: saveConfig succeeded");

        // Bei Case-only-Rename auf Windows verweisen beide Namen auf dieselbe Datei: nicht löschen.
        if (!caseOnly) {
            if (!manager.delete(selected)) {
                // Neue Datei wurde geschrieben; Löschfehler ist nicht fatal, aber melden.
                status = "Umbenannt, aber Original konnte nicht entfernt werden: " + manager.getLastError();
                refresh(); selected = existing(newName);
                return true;
            }
        }

        refresh(); selected = existing(newName);
        status = "Umbenannt zu \"" + selected + "\".";
        return true;
    }
    public void requestDelete() {
        cancel();
        if (manager == null || selected.isEmpty()) { status = "Select a config to delete."; return; }
        target = selected; pending = Pending.DELETE;
        status = "Delete \"" + target + "\"? This removes the saved config.";
    }
    public boolean confirm() {
        Pending action = pending; String name = target; String oldSelected = selected; cancel();
        if (manager == null || action == Pending.NONE) return false;
        if (action == Pending.OVERWRITE) return write(name);
        if (action == Pending.RENAME) {
            String oldContent;
            try {
                LocalConfig oldConfig = null;
                for (LocalConfig c : ConfigManager.localConfigs) {
                    if (c.getName().equalsIgnoreCase(oldSelected)) { oldConfig = c; break; }
                }
                if (oldConfig == null) { status = "Original-Konfiguration nicht gefunden."; return false; }
                oldContent = manager.readConfigData(oldConfig.getFile().toPath());
            } catch (IOException ex) {
                status = "Original-Konfiguration nicht lesbar: " + ex.getMessage();
                return false;
            }
            if (!manager.saveConfig(name, oldContent)) {
                status = manager.getLastError();
                return false;
            }
            boolean caseOnly = existing(name).equalsIgnoreCase(oldSelected) && !existing(name).equals(oldSelected);
            if (!caseOnly && !manager.delete(oldSelected)) {
                status = "Umbenannt, aber Original konnte nicht entfernt werden: " + manager.getLastError();
                refresh(); selected = existing(name);
                return true;
            }
            refresh(); selected = existing(name);
            status = "Umbenannt zu \"" + selected + "\".";
            return true;
        }
        if (!manager.delete(name)) { status = manager.getLastError().isEmpty() ? "Config no longer exists." : manager.getLastError(); refresh(); return false; }
        refresh(); status = "Deleted " + name; return true;
    }
    public void cancel() { if(pending!=Pending.NONE)status=""; pending = Pending.NONE; target = ""; }
}
