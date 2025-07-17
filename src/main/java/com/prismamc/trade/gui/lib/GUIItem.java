package com.prismamc.trade.gui.lib;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class GUIItem {
    private ItemStack item;
    private Consumer<ClickContext> clickHandler;
    private static final Map<ItemStack, ItemStack> itemCache = new WeakHashMap<>();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public GUIItem(Material material) {
        this.item = createItem(material);
    }

    private static ItemStack createItem(Material material) {
        return new ItemStack(material);
    }

    public GUIItem setName(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return this;
    }

    /**
     * Set display name using Adventure Component
     */
    public GUIItem setName(Component component) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String legacyName = legacySerializer.serialize(component);
            meta.setDisplayName(legacyName);
            item.setItemMeta(meta);
        }
        return this;
    }

    public GUIItem setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public GUIItem setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> coloredLore = lore.stream()
                    .map(line -> legacySerializer.serialize(legacySerializer.deserialize(line)))
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return this;
    }

    /**
     * Set lore using Adventure Components
     */
    public GUIItem setLore(Component... components) {
        List<String> legacyLore = Arrays.stream(components)
                .map(legacySerializer::serialize)
                .collect(Collectors.toList());
        return setLore(legacyLore);
    }

    /**
     * Set lore using a list of Adventure Components
     */
    public GUIItem setLoreComponents(List<Component> components) {
        List<String> legacyLore = components.stream()
                .map(legacySerializer::serialize)
                .collect(Collectors.toList());
        return setLore(legacyLore);
    }

    public GUIItem setClickHandler(Consumer<ClickContext> handler) {
        this.clickHandler = handler;
        return this;
    }

    public void handleClick(ClickContext context) {
        if (clickHandler != null) {
            clickHandler.accept(context);
        }
    }

    public ItemStack getItemStack() {
        ItemStack cachedItem = itemCache.get(item);
        if (cachedItem == null) {
            cachedItem = item.clone();
            itemCache.put(item, cachedItem);
        }
        return cachedItem.clone();
    }

    public static class ClickContext {
        private final GUI gui;
        private final int slot;
        private final InventoryClickEvent event;
        private boolean processed;

        public ClickContext(GUI gui, int slot, InventoryClickEvent event) {
            this.gui = gui;
            this.slot = slot;
            this.event = event;
            this.processed = false;
        }

        public GUI getGui() {
            return gui;
        }

        public int getSlot() {
            return slot;
        }

        public InventoryClickEvent getEvent() {
            return event;
        }

        public void markProcessed() {
            this.processed = true;
        }

        public boolean isProcessed() {
            return processed;
        }

        public boolean isShiftClick() {
            return event.isShiftClick();
        }

        public boolean isRightClick() {
            return event.isRightClick();
        }

        public boolean isLeftClick() {
            return event.isLeftClick();
        }
    }

    // Método para limpiar el caché cuando sea necesario
    public static void clearCache() {
        itemCache.clear();
    }
}