package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

public class PreTradeGUI extends GUI {
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final Plugin plugin;
    private final boolean isResponse;
    private static final int[] TRADE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int CONFIRM_SLOT = 44;
    private static final int INFO_SLOT = 40;
    private final List<ItemStack> selectedItems = new ArrayList<>();
    private boolean closedByButton = false;

    public PreTradeGUI(Player owner, Player target, Plugin plugin, boolean isResponse) {
        super(owner, isResponse ? "Select Your Trade Items" : "Select Items to Trade", 54);
        this.tradeInitiator = owner;
        this.tradeTarget = target;
        this.plugin = plugin;
        this.isResponse = isResponse;
    }

    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        this(owner, target, plugin, false);
    }

    @Override
    protected void initializeItems() {
        GUIItem confirmButton = new GUIItem(Material.EMERALD)
            .setName(isResponse ? "§aConfirm Your Items" : "§aConfirm Trade Offer")
            .setLore(
                isResponse ? new String[]{
                    "§7Click to confirm and proceed",
                    "§7with the trade"
                } : new String[]{
                    "§7Click to confirm and send",
                    "§7trade request to " + tradeTarget.getName()
                }
            );
        inventory.setItem(CONFIRM_SLOT, confirmButton.getItemStack());

        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eTrade Information")
            .setLore(
                "§7Trading with: §f" + (isResponse ? tradeInitiator.getName() : tradeTarget.getName()),
                "§7Click items in your inventory",
                "§7to add them to the trade.",
                "",
                "§7Close the window to",
                "§7cancel and get your items back."
            );
        inventory.setItem(INFO_SLOT, infoSign.getItemStack());

        GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName(" ");

        for (int i = 45; i < 54; i++) {
            if (i != CONFIRM_SLOT && i != INFO_SLOT) {
                inventory.setItem(i, border.getItemStack());
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();
        
        if (clickedSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (player.equals(owner)) {
                handleConfirmClick();
            }
            return;
        }
        
        if (clickedSlot < inventory.getSize() && !isTradeSlot(clickedSlot)) {
            event.setCancelled(true);
            return;
        }
        
        if (clickedSlot == INFO_SLOT || (clickedSlot >= 45 && clickedSlot < 54)) {
            event.setCancelled(true);
        }
    }

    private void handleConfirmClick() {
        selectedItems.clear();
        boolean hasItems = false;

        for (int slot : TRADE_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().equals(Material.AIR)) {
                hasItems = true;
                selectedItems.add(item.clone());
            }
        }

        if (!hasItems) {
            owner.sendMessage(Component.text("You must select at least one item to trade!")
                .color(NamedTextColor.RED));
            return;
        }

        closedByButton = true;
        
        if (isResponse) {
            // Open TradeGUI for both players
            plugin.getTradeManager().storePreTradeItems(owner.getUniqueId(), selectedItems);
            owner.closeInventory();
            
            tradeInitiator.sendMessage(Component.text(tradeTarget.getName())
                .color(NamedTextColor.WHITE)
                .append(Component.text(" has added their items to the trade!")
                .color(NamedTextColor.GREEN)));
                
            // Create and open trade GUI
            TradeGUI tradeGUI = new TradeGUI(tradeInitiator, tradeTarget);
            
            // Set initial items for both players
            List<ItemStack> initiatorItems = plugin.getTradeManager().getAndRemovePreTradeItems(tradeInitiator.getUniqueId());
            List<ItemStack> targetItems = plugin.getTradeManager().getAndRemovePreTradeItems(tradeTarget.getUniqueId());
            
            for (int i = 0; i < initiatorItems.size(); i++) {
                tradeGUI.setInitialItem(tradeInitiator, i, initiatorItems.get(i));
            }
            
            for (int i = 0; i < targetItems.size(); i++) {
                tradeGUI.setInitialItem(tradeTarget, i, targetItems.get(i));
            }
            
            tradeGUI.openInventory();
            tradeGUI.openFor(tradeTarget);
            
        } else {
            plugin.getTradeManager().storePreTradeItems(owner.getUniqueId(), selectedItems);
            owner.closeInventory();
            sendViewTradeRequest();
        }
    }

    private boolean isTradeSlot(int slot) {
        for (int s : TRADE_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private void sendViewTradeRequest() {
        tradeInitiator.sendMessage(Component.text("Your trade items have been sent to ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(tradeTarget.getName())
            .color(NamedTextColor.WHITE)));
        
        tradeTarget.sendMessage(Component.empty());
        tradeTarget.sendMessage(Component.text("⚡ New Trade Request!")
            .color(NamedTextColor.YELLOW));
        tradeTarget.sendMessage(Component.text(tradeInitiator.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" wants to trade with you. Click the button below to view their items.")
            .color(NamedTextColor.GRAY)));
        tradeTarget.sendMessage(Component.empty());
        
        Component viewButton = Component.text("[View Trade Items]")
            .color(NamedTextColor.GREEN)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tradeaccept " + tradeInitiator.getName()));
            
        tradeTarget.sendMessage(viewButton);
        tradeTarget.sendMessage(Component.empty());
    }

    public List<ItemStack> getSelectedItems() {
        return selectedItems;
    }

    public static int[] getTradeSlots() {
        return TRADE_SLOTS;
    }
    
    public boolean wasClosedByButton() {
        return closedByButton;
    }
}