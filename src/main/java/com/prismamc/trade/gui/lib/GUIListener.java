package com.prismamc.trade.gui.lib;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.gui.trade.PreTradeGUI;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {
    
    private final Map<UUID, GUI> activeGUIs;
    
    public GUIListener() {
        this.activeGUIs = new ConcurrentHashMap<>();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof GUI) {
            // Prevenir modificaciones concurrentes
            event.setCancelled(true);
            GUI gui = (GUI) holder;
            
            // Ejecutar el manejo del click en el próximo tick para evitar problemas de concurrencia
            gui.getPlugin().getServer().getScheduler().runTask(gui.getPlugin(), () -> {
                try {
                    gui.handleClick(event);
                } catch (Exception e) {
                    gui.getPlugin().getLogger().severe("Error handling GUI click: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof GUI) {
            // Cancelar el evento de arrastre en GUIs personalizadas
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        Player player = (Player) event.getPlayer();
        
        if (holder instanceof GUI) {
            GUI gui = (GUI) holder;
            activeGUIs.remove(player.getUniqueId());
            
            if (holder instanceof PreTradeGUI preTradeGui) {
                handlePreTradeGuiClose(player, preTradeGui);
            }
            
            // Limpiar recursos
            gui.onClose(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GUI gui = activeGUIs.remove(player.getUniqueId());
        
        if (gui != null) {
            // Limpiar recursos y devolver items si es necesario
            gui.onClose(player);
        }
    }
    
    private void handlePreTradeGuiClose(Player player, PreTradeGUI preTradeGui) {
        // Solo devolver items si el GUI no fue cerrado por el botón de confirmar
        if (!preTradeGui.wasClosedByButton()) {
            List<ItemStack> allItems = preTradeGui.getAllPagesItems();
            
            // Devolver items al jugador de forma segura
            for (ItemStack item : allItems) {
                if (item != null && item.getType() != Material.AIR) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                    
                    // Si no hay espacio en el inventario, dropear los items al suelo
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(drop -> 
                            player.getWorld().dropItemNaturally(player.getLocation(), drop));
                        player.sendMessage("§eAlgunos items fueron dropeados al suelo porque tu inventario está lleno!");
                    }
                }
            }
        }
    }
    
    public void registerGUI(Player player, GUI gui) {
        activeGUIs.put(player.getUniqueId(), gui);
    }
    
    public void unregisterGUI(Player player) {
        activeGUIs.remove(player.getUniqueId());
    }
}