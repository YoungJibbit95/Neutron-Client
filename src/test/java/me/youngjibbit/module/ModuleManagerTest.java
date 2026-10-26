package me.youngjibbit.module;

import me.youngjibbit.value.Value;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Registry behavior with synthetic modules; no discovery, world or OpenGL. */
public class ModuleManagerTest {
    @ModuleInfo(id = "first-id", name = "First Name", category = Category.Player)
    public static class First extends Module {
        final Value<String> setting = new Value<>("Example", this, "first");
        int enables, disables;
        Runnable onEnabled = () -> { };
        @Override public void onEnable() { enables++; onEnabled.run(); }
        @Override public void onDisable() { disables++; }
    }

    @ModuleInfo(id = "second-id", name = "Second Name", category = Category.Player)
    public static class Second extends First { }

    @ModuleInfo(id = "FIRST ID", name = "Other Name", category = Category.Client)
    public static class DuplicateId extends First { }

    @ModuleInfo(id = "third-id", name = "FIRST-NAME", category = Category.Client)
    public static class DuplicateName extends First { }

    @ModuleInfo(id = "FIRST NAME", name = "Other Name", category = Category.Client)
    public static class CrossNamespace extends First { }

    @Test public void replacingAnEnabledInstanceDisablesAndDetachesIt() {
        First old = new First(), replacement = new First();
        ModuleManager manager = new ModuleManager(Collections.singletonList(old));
        old.setToggled(true);
        manager.registerModule(replacement);
        assertFalse(old.isToggled());
        assertEquals(1, old.disables);
        assertSame(replacement, manager.getModule(First.class));
        assertSame(replacement, manager.getModule("first id"));
        assertSame(replacement, manager.getModule("FIRST-NAME"));
        assertArrayEquals(new Module[]{replacement}, manager.getModules(Category.Player));
        assertEquals(1, manager.getAllModules().size());
    }

    @Test public void unregisteringAStaleInstanceDoesNotRemoveItsReplacement() {
        First old = new First(), replacement = new First();
        ModuleManager manager = new ModuleManager(Collections.singletonList(old));
        manager.registerModule(replacement);
        manager.unregisterModule(old);
        assertSame(replacement, manager.getModule(First.class));
        assertSame(replacement, manager.getModule("first_id"));
    }

    @Test public void unregisteringDisablesTheInstanceAndDetachesPersistence() {
        First module = new First();
        ModuleManager manager = new ModuleManager(Collections.singletonList(module));
        module.setToggled(true);
        manager.unregisterModule(module);
        assertFalse(module.isToggled());
        assertEquals(1, module.disables);
        assertNull(manager.getModule(First.class));
        assertEquals(0, manager.getModules(Category.Player).length);

        First replacement = new First();
        manager.registerModule(replacement);
        AtomicInteger writes = new AtomicInteger();
        replacement.setPersistenceListener(ignored -> writes.incrementAndGet());
        manager.unregisterModule(replacement);
        replacement.setting.setValue("detached");
        assertEquals(0, writes.get());
    }

    @Test public void duplicateIdentifiersAreRejectedBeforeRegistryMutation() {
        First first = new First();
        ModuleManager manager = new ModuleManager(Collections.singletonList(first));
        for (Module conflicting : Arrays.asList(new DuplicateId(), new DuplicateName(), new CrossNamespace())) {
            assertThrows(IllegalArgumentException.class, () -> manager.registerModule(conflicting));
            assertSame(first, manager.getModule("first id"));
            assertEquals(1, manager.getAllModules().size());
            assertEquals(0, manager.getModules(Category.Client).length);
        }
    }

    @Test public void callersCannotCorruptCachedCategoryOrder() {
        First first = new First(); Second second = new Second();
        ModuleManager manager = new ModuleManager(Arrays.asList(first, second));
        Module[] snapshot = manager.getModules(Category.Player);
        snapshot[0] = null;
        assertArrayEquals(new Module[]{first, second}, manager.getModules(Category.Player));
    }

    @Test public void keyDispatchUsesLiveBindingsAndAnEventSnapshot() {
        First first = new First(); Second second = new Second();
        first.setKey(41); second.setKey(41);
        ModuleManager manager = new ModuleManager(Arrays.asList(first, second));
        first.onEnabled = () -> {
            second.setKey(42);
            manager.unregisterModule(second);
        };
        manager.toggleByKey(41);
        assertTrue(first.isToggled());
        assertFalse("Removed instance must not receive the rest of this key event", second.isToggled());
        assertEquals(0, second.enables);
    }

    @Test public void changingBindingsDoesNotRequireASecondGlobalManager() {
        First first = new First(); Second second = new Second();
        first.setKey(41); second.setKey(42);
        ModuleManager manager = new ModuleManager(Arrays.asList(first, second));
        second.setKey(41);
        manager.toggleByKey(41);
        assertTrue(first.isToggled());
        assertTrue(second.isToggled());
        first.setKey(0);
        manager.toggleByKey(0);
        assertTrue(first.isToggled());
    }

    @Test public void registeringTheSameInstanceIsIdempotent() {
        First first = new First();
        ModuleManager manager = new ModuleManager(Collections.singletonList(first));
        first.setToggled(true);
        manager.registerModule(first);
        assertTrue(first.isToggled());
        assertEquals(0, first.disables);
        assertEquals(1, manager.getAllModules().size());
    }

    @Test public void settingsBelongOnlyToTheirOwningInstance() {
        First first = new First(), second = new First();
        first.setting.setValue("changed");
        assertEquals("first", second.setting.getValue());
        assertSame(first.setting, first.getValueByName("example"));
        assertSame(second.setting, second.getValueByName("Example"));
        assertThrows(IllegalArgumentException.class, () -> first.registerValue(second.setting));
        assertThrows(UnsupportedOperationException.class, () -> first.getValues().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.getValueList().clear());
    }
}
