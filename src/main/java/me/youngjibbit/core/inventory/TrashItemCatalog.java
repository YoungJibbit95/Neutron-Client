package me.youngjibbit.core.inventory;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPotion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Registry/variant enumeration is done once when the menu opens, never from a render loop. */
public final class TrashItemCatalog {
    public static final class Entry {
        public final String key, name, registryId, variant, search;
        private final ItemStack icon;
        private Entry(ItemStack stack) {
            key = TrashItemStore.keyFor(stack);
            registryId = Item.itemRegistry.getNameForObject(stack.getItem()).toString();
            name = stack.getDisplayName(); icon = stack.copy();
            variant = stack.getItem().getMaxDamage() > 0 ? "all durability" : "variant " + stack.getMetadata();
            search = (name + " " + registryId + " " + stack.getMetadata()).toLowerCase(Locale.ROOT);
        }
        public ItemStack icon() { return icon; }
    }
    private final List<Entry> entries;
    public TrashItemCatalog() {
        TreeMap<String, Entry> unique = new TreeMap<>();
        for (ResourceLocation id : Item.itemRegistry.getKeys()) {
            Item item = Item.itemRegistry.getObject(id);
            if (item == null) continue;
            List<ItemStack> variants = new ArrayList<>();
            item.getSubItems(item, CreativeTabs.tabAllSearch, variants);
            if (item instanceof ItemPotion) addBrewingVariants((ItemPotion)item, variants);
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (ItemStack stack : variants) {
                if (stack == null || stack.getItem() == null || stack.stackSize < 1) continue;
                Entry entry = new Entry(stack);
                if (TrashItemStore.validKey(entry.key)) unique.put(entry.key, entry);
            }
        }
        List<Entry> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT)).thenComparing(e -> e.key));
        entries = Collections.unmodifiableList(sorted);
    }
    private static void addBrewingVariants(ItemPotion item, List<ItemStack> variants) {
        // Vanilla's creative cache keys by effect list, so equivalent instant
        // effects overwrite other real metadata variants (including healing 8197).
        // Keep exact rule scope while enumerating only the bounded brewing forms.
        for (int metadata : new int[]{0, 16, 32, 64, 8192}) variants.add(new ItemStack(item, 1, metadata));
        for (int base = 0; base < 16; base++) for (int delivery : new int[]{8192, 16384})
            for (int strength : new int[]{0, 32, 64}) {
                ItemStack stack = new ItemStack(item, 1, base | delivery | strength);
                List<PotionEffect> effects = item.getEffects(stack);
                if (effects != null && !effects.isEmpty()) variants.add(stack);
            }
    }
    public List<Entry> entries() { return entries; }
    public List<Entry> find(String query, boolean onlyListed, Set<String> listed) {
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Entry> matches = new ArrayList<>();
        outer: for (Entry entry : entries) {
            if (onlyListed && !listed.contains(entry.key)) continue;
            for (String word : words) if (!entry.search.contains(word)) continue outer;
            matches.add(entry);
        }
        return Collections.unmodifiableList(matches);
    }
}
