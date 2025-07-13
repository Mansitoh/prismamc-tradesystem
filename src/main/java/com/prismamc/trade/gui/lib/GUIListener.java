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
        
        if (holder instanceof PreTradeGUI preTradeGui) {
            // Solo devolver items si el GUI no fue cerrado por el botón de confirmar
            if (!preTradeGui.wasClosedByButton()) {
                for (int slot : PreTradeGUI.getTradeSlots()) {
                    ItemStack item = event.getInventory().getItem(slot);
                    if (item != null && !item.getType().equals(Material.AIR)) {
                        player.getInventory().addItem(item);
                    }
                }
            }
        } else if (holder instanceof TradeGUI tradeGui) {
            // Solo cancelar el trade si no está completado y los items no han sido devueltos
            if (!tradeGui.isTradeCompleted() && !tradeGui.isTradeCancelled() && !tradeGui.areItemsReturned()) {
                tradeGui.cancelTrade(player);
            }
        }
    }
}