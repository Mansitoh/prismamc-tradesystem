package com.prismamc.trade.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class TradeGUI extends GUI {
    private final Player trader1;
    private final Player trader2;
    private static final int[] TRADER1_SLOTS = {0,1,2,3,9,10,11,12,18,19,20,21};
    private static final int[] TRADER2_SLOTS = {5,6,7,8,14,15,16,17,23,24,25,26};
    private static final int ACCEPT1_SLOT = 36;
    private static final int ACCEPT2_SLOT = 44;
    private boolean trader1Accepted = false;
    private boolean trader2Accepted = false;

    public TradeGUI(Player trader1, Player trader2) {
        super(trader1, "Trading with " + trader2.getName(), 54);
        this.trader1 = trader1;
        this.trader2 = trader2;
    }

    @Override
    protected void initializeItems() {
        // Set glass panes as dividers
        ItemStack divider = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for(int i = 4; i < inventory.getSize(); i += 9) {
            inventory.setItem(i, divider);
        }
        
        // Set accept buttons
        inventory.setItem(ACCEPT1_SLOT, new GUIItem(Material.RED_CONCRETE).setName("§cClick to Accept").getItemStack());
        inventory.setItem(ACCEPT2_SLOT, new GUIItem(Material.RED_CONCRETE).setName("§cClick to Accept").getItemStack());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Handle accept button clicks
        if(slot == ACCEPT1_SLOT && player.equals(trader1)) {
            trader1Accepted = !trader1Accepted;
            updateAcceptButton(ACCEPT1_SLOT, trader1Accepted);
            checkTrade();
        } else if(slot == ACCEPT2_SLOT && player.equals(trader2)) {
            trader2Accepted = !trader2Accepted;
            updateAcceptButton(ACCEPT2_SLOT, trader2Accepted);
            checkTrade();
        }

        // Prevent clicking in the other player's slots
        if(player.equals(trader1) && isInSlots(slot, TRADER2_SLOTS)) {
            event.setCancelled(true);
        } else if(player.equals(trader2) && isInSlots(slot, TRADER1_SLOTS)) {
            event.setCancelled(true);
        }

        // Reset accept status when items are moved
        if(isInSlots(slot, TRADER1_SLOTS) || isInSlots(slot, TRADER2_SLOTS)) {
            trader1Accepted = false;
            trader2Accepted = false;
            updateAcceptButton(ACCEPT1_SLOT, false);
            updateAcceptButton(ACCEPT2_SLOT, false);
        }
    }

    private boolean isInSlots(int slot, int[] slots) {
        for(int s : slots) {
            if(s == slot) return true;
        }
        return false;
    }

    private void updateAcceptButton(int slot, boolean accepted) {
        inventory.setItem(slot, new GUIItem(accepted ? Material.GREEN_CONCRETE : Material.RED_CONCRETE)
            .setName(accepted ? "§aAccepted" : "§cClick to Accept")
            .getItemStack());
    }

    private void checkTrade() {
        if(trader1Accepted && trader2Accepted) {
            completeTrade();
        }
    }

    private void completeTrade() {
        // Execute the trade
        for(int slot : TRADER1_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if(item != null) {
                trader2.getInventory().addItem(item);
            }
        }

        for(int slot : TRADER2_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if(item != null) {
                trader1.getInventory().addItem(item);
            }
        }

        // Close inventory for both players
        trader1.closeInventory();
        trader2.closeInventory();

        // Send success messages
        trader1.sendMessage("§aTrade completed successfully!");
        trader2.sendMessage("§aTrade completed successfully!");
    }
}