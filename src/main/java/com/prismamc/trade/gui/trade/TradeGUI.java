package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TradeGUI extends GUI {
    private final Player trader1;
    private final Player trader2;
    private static final int[] TRADER1_SLOTS = {0,1,2,3,9,10,11,12,18,19,20,21};
    private static final int[] TRADER2_SLOTS = {5,6,7,8,14,15,16,17,23,24,25,26};
    private static final int ACCEPT1_SLOT = 36;
    private static final int ACCEPT2_SLOT = 44;
    private static final int HEAD1_SLOT = 45;  // Left bottom corner
    private static final int HEAD2_SLOT = 53;  // Right bottom corner
    private boolean trader1Accepted = false;
    private boolean trader2Accepted = false;

    public TradeGUI(Player trader1, Player trader2) {
        super(trader1, "Trading with " + trader2.getName(), 54);
        this.trader1 = trader1;
        this.trader2 = trader2;
    }

    public void setInitialItem(Player trader, int index, ItemStack item) {
        if (trader.equals(trader1) && index < TRADER1_SLOTS.length) {
            inventory.setItem(TRADER1_SLOTS[index], item);
        } else if (trader.equals(trader2) && index < TRADER2_SLOTS.length) {
            inventory.setItem(TRADER2_SLOTS[index], item);
        }
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
        inventory.setItem(ACCEPT1_SLOT, new GUIItem(Material.RED_CONCRETE)
            .setName("§cClick to Accept Trade")
            .getItemStack());
        inventory.setItem(ACCEPT2_SLOT, new GUIItem(Material.RED_CONCRETE)
            .setName("§cClick to Accept Trade")
            .getItemStack());

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
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Prevent clicking player heads or bottom glass
        if (slot >= 45) {
            event.setCancelled(true);
            return;
        }

        // Handle accept button clicks
        if(slot == ACCEPT1_SLOT && player.equals(trader1)) {
            handleAcceptButtonClick(true);
        } else if(slot == ACCEPT2_SLOT && player.equals(trader2)) {
            handleAcceptButtonClick(false);
        }

        // Prevent clicking in the other player's slots
        if(player.equals(trader1) && isInSlots(slot, TRADER2_SLOTS)) {
            event.setCancelled(true);
        } else if(player.equals(trader2) && isInSlots(slot, TRADER1_SLOTS)) {
            event.setCancelled(true);
        }

        // Reset accept status when items are moved
        if(isInSlots(slot, TRADER1_SLOTS) || isInSlots(slot, TRADER2_SLOTS)) {
            resetTradeAcceptance();
        }
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

    private void updateAcceptButton(int slot, boolean accepted) {
        inventory.setItem(slot, new GUIItem(accepted ? Material.GREEN_CONCRETE : Material.RED_CONCRETE)
            .setName(accepted ? "§aReady to Trade" : "§cClick to Accept Trade")
            .getItemStack());
    }

    private void checkTradeCompletion() {
        if(trader1Accepted && trader2Accepted) {
            completeTrade();
        }
    }

    private void completeTrade() {
        // Execute the trade
        transferItems(TRADER1_SLOTS, trader2);
        transferItems(TRADER2_SLOTS, trader1);

        // Close inventory for both players
        trader1.closeInventory();
        trader2.closeInventory();

        // Send success messages
        String successMessage = "§aThe trade has been completed successfully!";
        trader1.sendMessage(successMessage);
        trader2.sendMessage(successMessage);
    }

    private void transferItems(int[] slots, Player receiver) {
        for(int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if(item != null && !item.getType().equals(Material.AIR)) {
                receiver.getInventory().addItem(item.clone());
            }
        }
    }

    public void openFor(Player player) {
        player.openInventory(inventory);
    }
}