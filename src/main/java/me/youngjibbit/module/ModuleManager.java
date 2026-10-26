package me.youngjibbit.module;

import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.*;

/** Owns the live modules. Mutations and key dispatch belong to the client thread. */
public final class ModuleManager {

    private final LinkedHashMap<Class<? extends Module>, Module> modulesByClass = new LinkedHashMap<>();
    private final LinkedHashMap<String, Module> modulesById = new LinkedHashMap<>();
    private final LinkedHashMap<String, Module> modulesByName = new LinkedHashMap<>();
    private final EnumMap<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);
    private final EnumMap<Category, Module[]> categoryArrayCache = new EnumMap<>(Category.class);

    public ModuleManager() {
        this(Collections.emptyList());
        register();
    }

    /** Builds a registry from explicit instances without triggering classpath discovery. */
    public ModuleManager(Collection<? extends Module> modules) {
        initializeCategoryIndexes();
        for (Module module : modules) {
            registerModule(module);
        }
    }

    public Map<Class<? extends Module>, Module> getModules() {
        return Collections.unmodifiableMap(modulesByClass);
    }

    public Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modulesByClass.values());
    }

    public Module getModule(Class<? extends Module> mod) {
        return modulesByClass.get(mod);
    }

    public Module getModule(String nameOrId) {
        if (nameOrId == null) {
            return null;
        }

        String normalized = normalize(nameOrId);
        Module byId = modulesById.get(normalized);
        if (byId != null) {
            return byId;
        }

        Module byName = modulesByName.get(normalized);
        return byName;
    }

    public Module[] getModules(Category category) {
        if (category == null) {
            return new Module[0];
        }

        Module[] cached = categoryArrayCache.get(category);
        return cached != null ? cached.clone() : new Module[0];
    }

    public void toggleByKey(int key) {
        if (key == 0) {
            return;
        }

        // Key events are infrequent. Read the authoritative bindings instead of maintaining
        // a second index through a global Pulsar instance (including during construction).
        List<Module> bound = new ArrayList<>();
        for (Module module : modulesByClass.values()) {
            if (module.getKey() == key) {
                bound.add(module);
            }
        }
        for (Module module : bound) {
            // A preceding callback may have removed, replaced or rebound this instance.
            if (modulesByClass.get(module.getClass()) == module && module.getKey() == key) {
                module.toggle();
            }
        }
    }

    public final synchronized void register() {
        clearAllIndexes();

        Reflections reflections = new Reflections("me.youngjibbit.module.impl");
        List<Class<? extends Module>> classes = new ArrayList<>(reflections.getSubTypesOf(Module.class));
        classes.sort(Comparator.comparing(Class::getName));

        for (Class<? extends Module> clazz : classes) {
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }

            if (!clazz.isAnnotationPresent(ModuleInfo.class)) {
                continue;
            }

            Module module = instantiate(clazz);
            if (module == null) {
                continue;
            }

            registerModule(module);
        }
    }

    private Module instantiate(Class<? extends Module> clazz) {
        try {
            Constructor<? extends Module> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            System.err.println("Failed to instantiate module " + clazz.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    public synchronized void registerModule(Module module) {
        if (module == null) {
            return;
        }

        Module previous = modulesByClass.get(module.getClass());
        if (previous == module) {
            return;
        }
        checkLookupAvailable(normalize(module.getId()), previous);
        checkLookupAvailable(normalize(module.getName()), previous);
        if (previous != null) {
            unregisterModule(previous);
        }

        modulesByClass.put(module.getClass(), module);
        modulesById.put(normalize(module.getId()), module);
        modulesByName.put(normalize(module.getName()), module);
        addToCategoryIndex(module);
    }

    public synchronized void unregisterModule(Module module) {
        if (module == null || modulesByClass.get(module.getClass()) != module) {
            return;
        }

        module.setPersistenceListener(null);
        module.setToggled(false);
        removeFromCategoryIndex(module);
        modulesByClass.remove(module.getClass());
        modulesById.remove(normalize(module.getId()));
        modulesByName.remove(normalize(module.getName()));
    }

    private void checkLookupAvailable(String key, Module previous) {
        Module byId = modulesById.get(key);
        Module byName = modulesByName.get(key);
        if ((byId != null && byId != previous) || (byName != null && byName != previous)) {
            throw new IllegalArgumentException("Duplicate module name or id: " + key);
        }
    }

    private String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
    }

    private void initializeCategoryIndexes() {
        modulesByCategory.clear();
        categoryArrayCache.clear();
        for (Category category : Category.values()) {
            modulesByCategory.put(category, new ArrayList<>());
            categoryArrayCache.put(category, new Module[0]);
        }
    }

    private void clearAllIndexes() {
        for (Module module : new ArrayList<>(modulesByClass.values())) {
            unregisterModule(module);
        }
        modulesByClass.clear();
        modulesById.clear();
        modulesByName.clear();
        initializeCategoryIndexes();
    }

    private void addToCategoryIndex(Module module) {
        Category category = module.getCategory();
        List<Module> modules = modulesByCategory.get(category);
        if (modules != null && !modules.contains(module)) {
            modules.add(module);
            refreshCategoryCache(category);
        }
    }

    private void removeFromCategoryIndex(Module module) {
        Category category = module.getCategory();
        List<Module> modules = modulesByCategory.get(category);
        if (modules != null && modules.remove(module)) {
            refreshCategoryCache(category);
        }
    }

    private void refreshCategoryCache(Category category) {
        List<Module> modules = modulesByCategory.get(category);
        categoryArrayCache.put(category, modules == null ? new Module[0] : modules.toArray(new Module[0]));
    }

}
