package dev.aurelium.auraskills.bukkit.item;

import com.google.common.collect.Sets;
import dev.aurelium.auraskills.api.item.*;
import dev.aurelium.auraskills.api.loot.Loot;
import dev.aurelium.auraskills.api.loot.LootPool;
import dev.aurelium.auraskills.api.loot.LootTable;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.bukkit.AuraSkills;
import dev.aurelium.auraskills.bukkit.loot.item.BukkitItemSupplier;
import dev.aurelium.auraskills.bukkit.loot.type.ItemLoot;
import dev.aurelium.auraskills.bukkit.user.BukkitUser;
import dev.aurelium.auraskills.bukkit.util.ItemUtils;
import dev.aurelium.auraskills.bukkit.util.PotionUtil;
import dev.aurelium.auraskills.common.item.ItemRegistry;
import dev.aurelium.auraskills.common.item.SourceItem;
import dev.aurelium.auraskills.common.message.type.LevelerMessage;
import dev.aurelium.auraskills.common.user.User;
import dev.aurelium.auraskills.common.util.data.KeyIntPair;
import dev.aurelium.auraskills.common.util.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

import static dev.aurelium.auraskills.bukkit.ref.BukkitItemRef.unwrap;

public class BukkitItemRegistry implements ItemRegistry {

    private final AuraSkills plugin;
    private final Map<NamespacedId, ItemStack> items = new ConcurrentHashMap<>();
    private final BukkitSourceMenuItems sourceMenuItems;
    private final ItemRegistryStorage storage;
    private final Map<String, ExternalItemProvider> externalItemProviders = new ConcurrentHashMap<>();
    // Maps the ID of the unresolved item to a consumer that when called, will try to register the item again wherever it is needed (sources, loot)
    private final Map<NamespacedId, Consumer<ItemStack>> unresolvedExternalItems = new ConcurrentHashMap<>();

    public BukkitItemRegistry(AuraSkills plugin) {
        this.plugin = plugin;
        this.sourceMenuItems = new BukkitSourceMenuItems(plugin);
        this.storage = new ItemRegistryStorage(plugin, this);
    }

    public void register(NamespacedId key, ItemStack item) {
        items.put(key, item.clone());
    }

    public void unregister(NamespacedId key) {
        items.remove(key);
    }

    public Set<NamespacedId> getIds() {
        return items.keySet();
    }

    /**
     * Tries to load unresolved external items from a given namespace again, calling the callback
     * registered when the item load initially failed.
     *
     * @param namespace the namespace to reload
     */
    public void reloadUnresolvedItems(String namespace) {
        unresolvedExternalItems.entrySet().removeIf(entry -> {
            if (!entry.getKey().getNamespace().equals(namespace)) return false;
            ItemStack reloaded = getItem(entry.getKey());
            if (reloaded != null) {
                entry.getValue().accept(reloaded);
                return true; // Remove the entry after it's resolved
            }
            return false; // Keep as unresolved
        });
    }

    @Nullable
    public ItemStack getItem(NamespacedId key) {
        return getItem(key, null);
    }

    @Nullable
    public ItemStack getItem(NamespacedId key, @Nullable Consumer<ItemStack> unresolvedConsumer) {
        ItemStack item = items.get(key);
        if (item != null) {
            return item;
        }

        ExternalItemProvider provider = externalItemProviders.get(key.getNamespace());
        if (provider != null) {
            ItemStack external = provider.getItem(key.getOriginalKey());
            if (external != null) {
                return external.clone();
            } else if (unresolvedConsumer != null) {
                unresolvedExternalItems.put(key, unresolvedConsumer);
            }
        }

        return null;
    }

    public Map<NamespacedId, ItemStack> getItems() {
        return items;
    }

    @Override
    public boolean containsItem(NamespacedId key) {
        return items.containsKey(key);
    }

    @Override
    public void giveItem(User user, NamespacedId key, int amount) {
        Player player = ((BukkitUser) user).getPlayer();
        if (player == null) return;

        ItemStack item = getItem(key);

        if (item == null) {
            return;
        }

        plugin.getScheduler().executeAtEntity(player, (task) -> {
            item.setAmount(amount);

            ItemStack leftoverItem = ItemUtils.addItemToInventory(player, item); // Attempt item give
            // Handle items that could not fit in the inventory
            if (leftoverItem != null) {
                // Add unclaimed item key and amount to player data
                user.getUnclaimedItems().add(new KeyIntPair(key.toString(), leftoverItem.getAmount()));
                // Notify player
                plugin.getScheduler().executeSync(() -> player.sendMessage(plugin.getPrefix(user.getLocale()) + plugin.getMsg(LevelerMessage.UNCLAIMED_ITEM, user.getLocale())));
            }
        });
    }

    @Override
    public int getItemAmount(NamespacedId key) {
        ItemStack item = getItem(key);
        if (item == null) {
            return 1;
        }
        return item.getAmount();
    }

    @Override
    public @Nullable String getEffectiveItemName(NamespacedId key) {
        ItemStack item = getItem(key);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return meta.getDisplayName();
            } else {
                return item.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");
            }
        }
        return null;
    }

    public BukkitSourceMenuItems getSourceMenuItems() {
        return sourceMenuItems;
    }

    public boolean passesFilter(ItemStack item, LootItemFilter filter, Skill skill) {
        String lootPoolName = filter.lootPool();
        if (lootPoolName != null) {
            LootTable lootTable = plugin.getLootManager().getLootTable(skill);
            if (lootTable == null) {
                return false;
            }
            LootPool pool = lootTable.getPool(lootPoolName);
            if (pool == null) {
                return false;
            }
            // Search each loot in pool and check if items match
            for (Loot loot : pool.getLoot()) {
                if (!(loot instanceof ItemLoot itemLoot)) {
                    continue;
                }
                BukkitItemSupplier bukkitItemSupplier = new BukkitItemSupplier(itemLoot.getItem());
                if (item.equals(unwrap(bukkitItemSupplier.supplyItem(plugin, lootTable)))) {
                    return true;
                }
            }
            return false;
        }
        ItemFilter itemFilter = new SourceItem(filter.materials(), filter.excludedMaterials(), filter.category(), filter.meta());
        return passesFilter(item, itemFilter);
    }

    public boolean passesFilter(ItemStack item, ItemFilter filter) {
        // Check materials
        if (item == null) return false;
        String[] materials = filter.materials();
        if (materials != null) {
            if (!TextUtil.contains(materials, item.getType().toString())) {
                return false;
            }
        }
        // Check excluded materials
        String[] excludedMaterials = filter.excludedMaterials();
        if (excludedMaterials != null) {
            for (String excludedMaterial : excludedMaterials) {
                if (excludedMaterial.toUpperCase(Locale.ROOT).equals(item.getType().toString())) {
                    return false;
                }
            }
        }
        // Check ItemCategory
        ItemCategory category = filter.category();
        if (category != null) {
            if (!getItemCategories(item, item.getType()).contains(category)) {
                return false;
            }
        }
        // Check meta
        return passesItemMetaFilter(item, filter);
    }

    public ItemRegistryStorage getStorage() {
        return storage;
    }

    public void registerExternalItemProvider(String namespace, ExternalItemProvider provider) {
        externalItemProviders.put(namespace, provider);
    }

    @SuppressWarnings("deprecation")
    private boolean passesItemMetaFilter(ItemStack item, ItemFilter filter) {
        ItemFilterMeta filterMeta = filter.meta();
        if (filterMeta == null) {
            return true;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return true;
        }
        if (!filterMeta.ignoreCustomModelData()) { // Only check if not ignored
            if (filterMeta.hasCustomModelData()) {
                if (!itemMeta.hasCustomModelData()) {
                    return false;
                }
                if (itemMeta.getCustomModelData() != filterMeta.customModelData()) {
                    return false;
                }
            } else {
                // When filter says it shouldn't have custom model data but the item does
                if (itemMeta.hasCustomModelData()) {
                    return false;
                }
            }
        }
        if (filterMeta.displayName() != null) {
            if (!itemMeta.getDisplayName().equals(filterMeta.displayName())) {
                return false;
            }
        }
        List<String> filterLore = filterMeta.lore();
        if (filterLore != null && !filterLore.isEmpty()) {
            if (!filterLore.equals(itemMeta.getLore())) {
                return false;
            }
        }
        PotionData potionData = filterMeta.potionData();
        if (potionData != null) {
            if (!(itemMeta instanceof PotionMeta potionMeta)) {
                return false;
            }
            String[] types = potionData.types();
            // Backwards compatibility
            BukkitPotionType bukkitPotionType = new BukkitPotionType(potionMeta);

            PotionType basePotionType = bukkitPotionType.getType();
            if (types != null && basePotionType != null) {
                if (!TextUtil.contains(types, basePotionType.toString())) {
                    return false;
                }
            }
            String[] excludedTypes = potionData.excludedTypes();
            if (excludedTypes != null && basePotionType != null) {
                if (TextUtil.contains(excludedTypes, basePotionType.toString())) {
                    return false;
                }
            }
            if (potionData.extended() != bukkitPotionType.isExtended()) {
                return false;
            }
            if (potionData.upgraded() != bukkitPotionType.isUpgraded()) {
                return false;
            }
            if (potionData.excludeNegative()) {
                return basePotionType == null || !PotionUtil.isNegativePotion(basePotionType);
            }
            return true;
        }
        return true;
    }

    private Set<ItemCategory> getItemCategories(ItemStack item, Material mat) {
        Set<ItemCategory> found = Sets.newConcurrentHashSet();
        String name = mat.toString();
        if (name.contains("_SWORD") || mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT) {
            found.add(ItemCategory.WEAPON);
        } else if (name.contains("_HELMET") || name.contains("_CHESTPLATE") || name.contains("_LEGGINGS") || name.contains("_BOOTS") || mat == Material.ELYTRA) {
            found.add(ItemCategory.ARMOR);
        } else if (name.contains("_PICKAXE") || name.contains("_AXE") || name.contains("_SHOVEL") || name.contains("_HOE") ||
                mat == Material.SHEARS || mat == Material.FISHING_ROD || mat == Material.FLINT_AND_STEEL) {
            found.add(ItemCategory.TOOL);
        }
        if (mat == Material.LILY_PAD || mat == Material.BOWL || mat == Material.LEATHER || mat == Material.LEATHER_BOOTS || mat == Material.ROTTEN_FLESH ||
                mat == Material.STICK || mat == Material.STRING || mat == Material.BONE || mat == Material.INK_SAC || mat == Material.TRIPWIRE_HOOK) {
            found.add(ItemCategory.FISHING_JUNK);
        }
        if (mat == Material.FISHING_ROD) {
            if (item.getEnchantments().isEmpty()) {
                found.add(ItemCategory.FISHING_JUNK);
            } else {
                found.add(ItemCategory.FISHING_TREASURE);
            }
        }
        // Check water bottle
        if (mat == Material.POTION) {
            if (item.getItemMeta() instanceof PotionMeta) {
                found.add(ItemCategory.FISHING_JUNK);
            }
        }
        if (mat == Material.BOW || mat == Material.ENCHANTED_BOOK || mat == Material.NAME_TAG || mat == Material.NAUTILUS_SHELL || mat == Material.SADDLE) {
            found.add(ItemCategory.FISHING_TREASURE);
        }
        return found;
    }

}
