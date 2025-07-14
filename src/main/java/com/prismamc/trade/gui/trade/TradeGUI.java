package com.prismamc.trade.gui.trade;

import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.manager.TradeManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TradeGUI extends GUI {
    private final Player trader1;
    private final Player trader2;
    private final long tradeId;
    private final Plugin plugin;
    private static final int[] TRADER1_SLOTS = {0,1,2,3,9,10,11,12,18,19,20,21};
    private static final int[] TRADER2_SLOTS = {5,6,7,8,14,15,16,17,23,24,25,26};
    private static final int ACCEPT1_SLOT = 36;
    private static final int ACCEPT2_SLOT = 44;
    private static final int HEAD1_SLOT = 45;  // Left bottom corner
    private static final int HEAD2_SLOT = 53;  // Right bottom corner
    private boolean trader1Accepted = false;
    private boolean trader2Accepted = false;
    private boolean tradeCompleted = false;
    private boolean tradeCancelled = false;
    private boolean itemsReturned = false;

    public TradeGUI(Player trader1, Player trader2, long tradeId, Plugin plugin) {
        super(trader1, "Trading with " + trader2.getName(), 54);
        this.trader1 = trader1;
        this.trader2 = trader2;
        this.tradeId = tradeId;
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Set glass panes as dividers
        ItemStack divider = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName(" ")
            .getItemStack();
            
        for(int i = 4; i < inventory.getSize(); i += 9) {
            inventory.setItem(i, divider);
        }
        
        // Set accept buttons
        updateAcceptButton(ACCEPT1_SLOT, false);
        updateAcceptButton(ACCEPT2_SLOT, false);

        // Set player heads
        inventory.setItem(HEAD1_SLOT, createPlayerHead(trader1));
        inventory.setItem(HEAD2_SLOT, createPlayerHead(trader2));

        // Fill bottom row with glass panes except for heads
        ItemStack bottomGlass = new GUIItem(Material.BLACK_STAINED_GLASS_PANE)
            .setName(" ")
            .getItemStack();
            
        for (int i = 45; i <= 53; i++) {
            if (i != HEAD1_SLOT && i != HEAD2_SLOT) {
                inventory.setItem(i, bottomGlass);
            }
        }
    }

    public void setInitialItem(Player trader, int index, ItemStack item) {
        if (trader.equals(trader1) && index < TRADER1_SLOTS.length) {
            inventory.setItem(TRADER1_SLOTS[index], item);
        } else if (trader.equals(trader2) && index < TRADER2_SLOTS.length) {
            inventory.setItem(TRADER2_SLOTS[index], item);
        }
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(Component.text(player.getName() + "'s Items", NamedTextColor.GOLD));
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true); // Cancel all clicks by default
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Handle accept button clicks
        if(slot == ACCEPT1_SLOT && player.equals(trader1)) {
            handleAcceptButtonClick(true);
            return;
        } else if(slot == ACCEPT2_SLOT && player.equals(trader2)) {
            handleAcceptButtonClick(false);
            return;
        }

        // Allow item placement only in player's own trade slots
        boolean isTrader1Slot = isInSlots(slot, TRADER1_SLOTS);
        boolean isTrader2Slot = isInSlots(slot, TRADER2_SLOTS);
        
        if (isTrader1Slot && player.equals(trader1)) {
            event.setCancelled(false);
        } else if (isTrader2Slot && player.equals(trader2)) {
            event.setCancelled(false);
        }

        // Reset accept status when items are moved
        if(!event.isCancelled() && (isTrader1Slot || isTrader2Slot)) {
            resetTradeAcceptance();
        }
    }

    private void handleAcceptButtonClick(boolean isTrader1) {
        if (isTrader1) {
            trader1Accepted = !trader1Accepted;
            updateAcceptButton(ACCEPT1_SLOT, trader1Accepted);
        } else {
            trader2Accepted = !trader2Accepted;
            updateAcceptButton(ACCEPT2_SLOT, trader2Accepted);
        }
        checkTradeCompletion();
    }

    private void updateAcceptButton(int slot, boolean accepted) {
        inventory.setItem(slot, new GUIItem(accepted ? Material.GREEN_CONCRETE : Material.RED_CONCRETE)
            .setName(accepted ? "§aReady to Trade" : "§cClick to Accept Trade")
            .getItemStack());
    }

    private void resetTradeAcceptance() {
        trader1Accepted = false;
        trader2Accepted = false;
        updateAcceptButton(ACCEPT1_SLOT, false);
        updateAcceptButton(ACCEPT2_SLOT, false);
    }

    private boolean isInSlots(int slot, int[] slots) {
        for(int s : slots) {
            if(s == slot) return true;
        }
        return false;
    }

    private void checkTradeCompletion() {
        if(trader1Accepted && trader2Accepted) {
            completeTrade();
        }
    }

    private void completeTrade() {
        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            cancelTrade(trader1);
            return;
        }

        tradeCompleted = true;
        plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.COMPLETED);
        
        try {
            // Execute the trade
            List<ItemStack> trader1Items = plugin.getTradeManager().getAndRemoveTradeItems(tradeId, trader1.getUniqueId());
            List<ItemStack> trader2Items = plugin.getTradeManager().getAndRemoveTradeItems(tradeId, trader2.getUniqueId());

            // Transferir items
            transferItems(trader2, trader1Items);
            transferItems(trader1, trader2Items);

            // Close inventory for both players
            trader1.closeInventory();
            trader2.closeInventory();

            // Send success messages
            String successMessage = "§a¡El trade se ha completado exitosamente!";
            trader1.sendMessage(successMessage);
            trader2.sendMessage(successMessage);

            // Cleanup the trade
            plugin.getTradeManager().cleanupTrade(tradeId);
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Error completing trade %d: %s", tradeId, e.getMessage()));
            trader1.sendMessage("§c¡Error al completar el trade! Los items han sido devueltos.");
            trader2.sendMessage("§c¡Error al completar el trade! Los items han sido devueltos.");
            cancelTrade(trader1);
        }
    }

    public void cancelTrade(Player canceledBy) {
        if (tradeCompleted || tradeCancelled || itemsReturned) return;
        
        tradeCancelled = true;
        plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.CANCELLED);
        
        try {
            // Get items from trade manager
            List<ItemStack> trader1Items = plugin.getTradeManager().getAndRemoveTradeItems(tradeId, trader1.getUniqueId());
            List<ItemStack> trader2Items = plugin.getTradeManager().getAndRemoveTradeItems(tradeId, trader2.getUniqueId());
            
            // Return items to their owners
            returnItemsToPlayer(trader1, trader1Items);
            returnItemsToPlayer(trader2, trader2Items);
            
            itemsReturned = true;
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Error cancelling trade %d: %s", tradeId, e.getMessage()));
            trader1.sendMessage("§c¡Error al devolver los items! Por favor contacta a un administrador.");
            trader2.sendMessage("§c¡Error al devolver los items! Por favor contacta a un administrador.");
            return;
        }

        // Enviar mensajes personalizados
        if (canceledBy.equals(trader1)) {
            trader1.sendMessage("§cHas cancelado el trade");
            trader2.sendMessage("§c" + trader1.getName() + " ha cancelado el trade");
        } else if (canceledBy.equals(trader2)) {
            trader2.sendMessage("§cHas cancelado el trade");
            trader1.sendMessage("§c" + trader2.getName() + " ha cancelado el trade");
        }

        // Close inventories for both players if they haven't already closed them
        if (trader1.getOpenInventory().getTopInventory().equals(inventory)) {
            trader1.closeInventory();
        }
        if (trader2.getOpenInventory().getTopInventory().equals(inventory)) {
            trader2.closeInventory();
        }

        // Cleanup the trade
        plugin.getTradeManager().cleanupTrade(tradeId);
    }

    private void transferItems(Player receiver, List<ItemStack> items) {
        boolean inventoryFull = false;
        
        for (ItemStack item : items) {
            if (item != null && !item.getType().equals(Material.AIR)) {
                HashMap<Integer, ItemStack> leftover = receiver.getInventory().addItem(item.clone());
                if (!leftover.isEmpty()) {
                    inventoryFull = true;
                    for (ItemStack drop : leftover.values()) {
                        receiver.getWorld().dropItemNaturally(receiver.getLocation(), drop);
                    }
                }
            }
        }
        
        if (inventoryFull) {
            receiver.sendMessage("§eAlgunos items fueron dropeados al suelo porque tu inventario está lleno!");
        }
    }

    private void returnItemsToPlayer(Player player, List<ItemStack> items) {
        boolean inventoryFull = false;
        
        for (ItemStack item : items) {
            if (item != null && !item.getType().equals(Material.AIR)) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                if (!leftover.isEmpty()) {
                    inventoryFull = true;
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }
        
        if (inventoryFull) {
            player.sendMessage("§eAlgunos items fueron dropeados al suelo porque tu inventario está lleno!");
        }
    }

    public boolean isTradeCompleted() {
        return tradeCompleted;
    }

    public boolean isTradeCancelled() {
        return tradeCancelled;
    }

    public boolean areItemsReturned() {
        return itemsReturned;
    }

    public void openFor(Player player) {
        player.openInventory(inventory);
    }
}