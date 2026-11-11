package me.youngjibbit.core.inventory;

import net.minecraft.init.Bootstrap;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TrashItemCatalogTest {
    @Test public void liveRegistryIncludesEveryItemAndExactColoredPotionVariants() {
        Bootstrap.register(); TrashItemCatalog catalog = new TrashItemCatalog();
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        for (TrashItemCatalog.Entry entry : catalog.entries()) {
            ids.add(entry.registryId); assertTrue(keys.add(entry.key)); assertNotNull(entry.icon());
        }
        for (net.minecraft.util.ResourceLocation id : net.minecraft.item.Item.itemRegistry.getKeys()) assertTrue(id.toString(), ids.contains(id.toString()));
        assertEquals(16, catalog.find("minecraft:wool", false, Collections.emptySet()).size());
        assertTrue("Exact red wool variant", keys.contains("minecraft:wool@14"));
        assertTrue("Drinkable healing omitted by vanilla's creative cache", keys.contains("minecraft:potion@8197"));
        assertTrue("Splash healing must have an independent exact rule", keys.contains("minecraft:potion@16389"));
        assertTrue("Awkward brewing base", keys.contains("minecraft:potion@16"));
        List<TrashItemCatalog.Entry> sword = catalog.find("MINECRAFT:DIAMOND_SWORD", false, Collections.emptySet());
        assertEquals(1, sword.size()); assertEquals("minecraft:diamond_sword@*", sword.get(0).key);
        assertEquals(1, catalog.find("wool", true, Collections.singleton("minecraft:wool@14")).size());
        assertTrue(catalog.find("wool impossibleword", false, Collections.emptySet()).isEmpty());
    }
}
