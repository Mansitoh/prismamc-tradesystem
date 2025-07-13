package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

public class PreTradeGUI extends GUI {
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final Plugin plugin;
    private static final int[] TRADE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int CONFIRM_SLOT = 44;
    private static final int INFO_SLOT = 40;
    private List<ItemStack> selectedItems = new ArrayList<>();

    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        super(owner, "Select Items to Trade", 54);
        this.tradeInitiator = owner;
        this.tradeTarget = target;
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        GUIItem confirmButton = new GUIItem(Material.EMERALD)
            .setName("§aConfirm Trade Offer")
            .setLore(
                "§7Click to confirm and send",
                "§7trade request to " + tradeTarget.getName()
            );
        inventory.setItem(CONFIRM_SLOT, confirmButton.getItemStack());

        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eTrade Information")
            .setLore(
                "§7Trading with: §f" + tradeTarget.getName(),
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
            if (player.equals(tradeInitiator)) {
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

        plugin.getTradeManager().storePreTradeItems(owner.getUniqueId(), selectedItems);
        owner.closeInventory();
        sendTradeRequest();
    }

    private boolean isTradeSlot(int slot) {
        for (int s : TRADE_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private void sendTradeRequest() {
        tradeInitiator.sendMessage(Component.text("Your trade request has been sent to ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(tradeTarget.getName())
            .color(NamedTextColor.WHITE)));
        
        tradeTarget.sendMessage(Component.empty());
        tradeTarget.sendMessage(Component.text("⚡ New Trade Request!")
            .color(NamedTextColor.YELLOW)
            .decorate(TextDecoration.BOLD));
        tradeTarget.sendMessage(Component.text(tradeInitiator.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" wants to trade with you.")
            .color(NamedTextColor.GRAY)));
        tradeTarget.sendMessage(Component.empty());
        
        Component acceptButton = Component.text("[Accept]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/tradeaccept " + tradeInitiator.getName()));
            
        Component declineButton = Component.text("[Decline]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/tradedecline " + tradeInitiator.getName()));
            
        tradeTarget.sendMessage(acceptButton
            .append(Component.text(" ").color(NamedTextColor.WHITE))
            .append(declineButton));
            
        tradeTarget.sendMessage(Component.empty());
    }

    public List<ItemStack> getSelectedItems() {
        return selectedItems;
    }

    public static int[] getTradeSlots() {
        return TRADE_SLOTS;
    }
}