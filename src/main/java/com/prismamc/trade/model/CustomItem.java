package com.prismamc.trade.model;

import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a customizable item with all possible Minecraft item properties
 * Supports modern Component formatting, legacy compatibility, and
 * multi-language support
 */
public class CustomItem {
    private final String itemId;
    private final Material material;
    private final int amount;
    private final Map<String, String> displayNames; // Multi-language support
    private final Map<String, List<String>> lore; // Multi-language support
    private final Map<Enchantment, Integer> enchantments;
    private final List<ItemFlag> itemFlags;
    private final Integer customModelData;
    private final Boolean unbreakable;
    private final Map<String, Object> customNBT;

    // Serializers for Component conversion
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public CustomItem(String itemId, Material material, int amount, Map<String, String> displayNames,
            Map<String, List<String>> lore, Map<Enchantment, Integer> enchantments,
            List<ItemFlag> itemFlags, Integer customModelData, Boolean unbreakable,
            Map<String, Object> customNBT) {
        this.itemId = itemId;
        this.material = material;
        this.amount = amount;
        this.displayNames = displayNames != null ? new HashMap<>(displayNames) : new HashMap<>();
        this.lore = lore != null ? new HashMap<>(lore) : new HashMap<>();
        this.enchantments = enchantments != null ? new HashMap<>(enchantments) : new HashMap<>();
        this.itemFlags = itemFlags != null ? new ArrayList<>(itemFlags) : new ArrayList<>();
        this.customModelData = customModelData;
        this.unbreakable = unbreakable;
        this.customNBT = customNBT != null ? new HashMap<>(customNBT) : new HashMap<>();
    }

    /**
     * Constructor from MongoDB Document
     */
    public CustomItem(Document doc) {
        this.itemId = doc.getString("itemId");
        this.material = Material.valueOf(doc.getString("material"));
        this.amount = doc.getInteger("amount", 1);

        // Parse multi-language display names
        this.displayNames = new HashMap<>();
        Document displayNamesDoc = doc.get("displayNames", Document.class);
        if (displayNamesDoc != null) {
            for (String lang : displayNamesDoc.keySet()) {
                this.displayNames.put(lang, displayNamesDoc.getString(lang));
            }
        }

        // Parse multi-language lore
        this.lore = new HashMap<>();
        Document loreDoc = doc.get("lore", Document.class);
        if (loreDoc != null) {
            for (String lang : loreDoc.keySet()) {
                List<String> langLore = loreDoc.getList(lang, String.class);
                if (langLore != null) {
                    this.lore.put(lang, new ArrayList<>(langLore));
                }
            }
        }

        // Parse enchantments
        this.enchantments = new HashMap<>();
        Document enchantmentsDoc = doc.get("enchantments", Document.class);
        if (enchantmentsDoc != null) {
            for (String enchantName : enchantmentsDoc.keySet()) {
                try {
                    Enchantment enchant = Enchantment
                            .getByKey(org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase()));
                    if (enchant != null) {
                        this.enchantments.put(enchant, enchantmentsDoc.getInteger(enchantName));
                    }
                } catch (Exception e) {
                    // Skip invalid enchantments
                }
            }
        }

        // Parse item flags
        this.itemFlags = new ArrayList<>();
        List<String> flagStrings = doc.getList("itemFlags", String.class);
        if (flagStrings != null) {
            for (String flagName : flagStrings) {
                try {
                    this.itemFlags.add(ItemFlag.valueOf(flagName));
                } catch (Exception e) {
                    // Skip invalid flags
                }
            }
        }

        this.customModelData = doc.getInteger("customModelData");
        this.unbreakable = doc.getBoolean("unbreakable");

        // Parse custom NBT
        this.customNBT = new HashMap<>();
        Document nbtDoc = doc.get("customNBT", Document.class);
        if (nbtDoc != null) {
            this.customNBT.putAll(nbtDoc);
        }
    }

    /**
     * Convert to MongoDB Document
     */
    public Document toDocument() {
        Document doc = new Document()
                .append("itemId", itemId)
                .append("material", material.name())
                .append("amount", amount);

        // Serialize multi-language display names
        if (!displayNames.isEmpty()) {
            Document displayNamesDoc = new Document();
            displayNames.forEach(displayNamesDoc::append);
            doc.append("displayNames", displayNamesDoc);
        }

        // Serialize multi-language lore
        if (!lore.isEmpty()) {
            Document loreDoc = new Document();
            lore.forEach(loreDoc::append);
            doc.append("lore", loreDoc);
        }

        // Serialize enchantments
        if (!enchantments.isEmpty()) {
            Document enchantmentsDoc = new Document();
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                enchantmentsDoc.append(entry.getKey().getKey().getKey(), entry.getValue());
            }
            doc.append("enchantments", enchantmentsDoc);
        }

        // Serialize item flags
        if (!itemFlags.isEmpty()) {
            List<String> flagStrings = new ArrayList<>();
            for (ItemFlag flag : itemFlags) {
                flagStrings.add(flag.name());
            }
            doc.append("itemFlags", flagStrings);
        }

        if (customModelData != null) {
            doc.append("customModelData", customModelData);
        }

        if (unbreakable != null) {
            doc.append("unbreakable", unbreakable);
        }

        if (!customNBT.isEmpty()) {
            doc.append("customNBT", new Document(customNBT));
        }

        return doc;
    }

    /**
     * Create a Bukkit ItemStack from this CustomItem with language support
     */
    public ItemStack createItemStack(String language, String... replacements) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name with language support
            String displayName = getDisplayName(language);
            if (displayName != null && !displayName.isEmpty()) {
                // Apply replacements
                displayName = applyReplacements(displayName, replacements);
                Component nameComponent = parseComponent(displayName);
                meta.setDisplayName(legacySerializer.serialize(nameComponent));
            }

            // Set lore with language support
            List<String> lorelines = getLore(language);
            if (!lorelines.isEmpty()) {
                List<String> processedLore = new ArrayList<>();
                for (String loreLine : lorelines) {
                    // Apply replacements
                    loreLine = applyReplacements(loreLine, replacements);
                    Component loreComponent = parseComponent(loreLine);
                    processedLore.add(legacySerializer.serialize(loreComponent));
                }
                meta.setLore(processedLore);
            }

            // Set custom model data
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }

            // Set unbreakable
            if (unbreakable != null) {
                meta.setUnbreakable(unbreakable);
            }

            // Add item flags
            if (!itemFlags.isEmpty()) {
                meta.addItemFlags(itemFlags.toArray(new ItemFlag[0]));
            }

            item.setItemMeta(meta);
        }

        // Add enchantments
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }

        return item;
    }

    /**
     * Parse Component from text (tries MiniMessage first, then legacy)
     */
    private static Component parseComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        try {
            return miniMessage.deserialize(text);
        } catch (Exception e) {
            try {
                return legacySerializer.deserialize(text);
            } catch (Exception e2) {
                return Component.text(text);
            }
        }
    }

    /**
     * Serialize Component to MiniMessage format
     */
    private static String serializeComponent(Component component) {
        try {
            return miniMessage.serialize(component);
        } catch (Exception e) {
            return legacySerializer.serialize(component);
        }
    }

    /**
     * Get display name for a specific language with fallback
     */
    public String getDisplayName(String language) {
        String name = displayNames.get(language);
        if (name == null || name.isEmpty()) {
            // Fallback to English
            name = displayNames.get("en");
            if (name == null || name.isEmpty()) {
                // Ultimate fallback to first available language
                return displayNames.values().stream().findFirst().orElse("");
            }
        }
        return name;
    }

    /**
     * Get lore for a specific language with fallback
     */
    public List<String> getLore(String language) {
        List<String> lorelines = lore.get(language);
        if (lorelines == null || lorelines.isEmpty()) {
            // Fallback to English
            lorelines = lore.get("en");
            if (lorelines == null || lorelines.isEmpty()) {
                // Ultimate fallback to first available language
                return lore.values().stream().findFirst().orElse(new ArrayList<>());
            }
        }
        return new ArrayList<>(lorelines);
    }

    /**
     * Apply replacements to text
     */
    private String applyReplacements(String text, String... replacements) {
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    text = text.replace("%" + replacements[i] + "%", replacements[i + 1]);
                }
            }
        }
        return text;
    }

    // Getters
    public String getItemId() {
        return itemId;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public Map<String, String> getDisplayNames() {
        return displayNames;
    }

    public Map<String, List<String>> getLore() {
        return lore;
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return new HashMap<>(enchantments);
    }

    public List<ItemFlag> getItemFlags() {
        return new ArrayList<>(itemFlags);
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public Boolean getUnbreakable() {
        return unbreakable;
    }

    public Map<String, Object> getCustomNBT() {
        return new HashMap<>(customNBT);
    }

    /**
     * Create a copy of this item with a different amount
     */
    public CustomItem withAmount(int newAmount) {
        return new CustomItem(itemId, material, newAmount, displayNames, lore,
                enchantments, itemFlags, customModelData, unbreakable, customNBT);
    }

    /**
     * Create a copy of this item with additional lore
     */
    public CustomItem withAdditionalLore(String language, String... additionalLore) {
        Map<String, List<String>> newLore = new HashMap<>(this.lore);
        List<String> langLore = newLore.computeIfAbsent(language, k -> new ArrayList<>());
        for (String line : additionalLore) {
            langLore.add(line);
        }
        return new CustomItem(itemId, material, amount, displayNames, newLore,
                enchantments, itemFlags, customModelData, unbreakable, customNBT);
    }

    @Override
    public String toString() {
        return "CustomItem{" +
                "itemId='" + itemId + '\'' +
                ", material=" + material +
                ", amount=" + amount +
                ", displayName="
                + (displayNames != null ? serializeComponent(parseComponent(getDisplayName("en"))) : "null") +
                '}';
    }
}