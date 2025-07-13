package com.prismamc.trade.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;

public abstract class GUI implements InventoryHolder {
    protected final Inventory inventory;
    protected final Player owner;
    protected String title;
    protected int size;

    public GUI(Player owner, String title, int size) {
        this.owner = owner;
        this.title = title;
        this.size = size;
        // Create inventory after all fields are initialized
        Inventory inv = Bukkit.createInventory(this, size, title);
        this.inventory = inv;
    }

    protected abstract void initializeItems();
    
    public abstract void handleClick(InventoryClickEvent event);

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void openInventory() {
        initializeItems(); // Move initialization here
        owner.openInventory(inventory);
    }
}