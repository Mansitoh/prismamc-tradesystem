package com.prismamc.trade.gui.lib;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;

public class GUIItem {
    private ItemStack item;
    private Consumer<ClickContext> clickHandler;

    public GUIItem(Material material) {
        this.item = new ItemStack(material);
    }

    public GUIItem setName(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
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
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return this;
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
        return item.clone();
    }

    public static class ClickContext {
        private final GUI gui;
        private final int slot;
        private final InventoryClickEvent event;

        public ClickContext(GUI gui, int slot, InventoryClickEvent event) {
            this.gui = gui;
            this.slot = slot;
            this.event = event;
        }

        public GUI getGui() { return gui; }
        public int getSlot() { return slot; }
        public InventoryClickEvent getEvent() { return event; }
    }
}