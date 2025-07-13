package com.prismamc.trade.gui.lib;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.gui.trade.PreTradeGUI;

public class GUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof GUI gui) {
            gui.handleClick(event);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof PreTradeGUI) {
            // Return items to player when closing pre-trade GUI
            for (int i = 0; i < event.getInventory().getSize(); i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (item != null && !item.getType().equals(Material.AIR) && !item.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
                    event.getPlayer().getInventory().addItem(item);
                }
            }
        }
    }
}