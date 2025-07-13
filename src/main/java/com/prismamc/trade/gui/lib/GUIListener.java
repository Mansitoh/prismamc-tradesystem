package com.prismamc.trade.gui.lib;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.gui.trade.PreTradeGUI;
import com.prismamc.trade.gui.trade.TradeGUI;
import org.bukkit.entity.Player;

public class GUIListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof GUI) {
            ((GUI) holder).handleClick(event);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        Player player = (Player) event.getPlayer();
        
        if (holder instanceof PreTradeGUI) {
            // Only return items from trade slots to prevent duplication
            for (int slot : PreTradeGUI.getTradeSlots()) {
                ItemStack item = event.getInventory().getItem(slot);
                if (item != null && !item.getType().equals(Material.AIR)) {
                    player.getInventory().addItem(item);
                }
            }
        } else if (holder instanceof TradeGUI tradeGui) {
            // Handle trade GUI close - do not return items unless the trade is cancelled
            if (!tradeGui.isTradeCompleted()) {
                tradeGui.cancelTrade(player);
            }
        }
    }
}