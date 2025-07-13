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

import java.util.List;

public class ViewTradeGUI extends GUI {
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final com.prismamc.trade.Plugin plugin;
    private final List<ItemStack> initiatorItems;
    private final long tradeId;
    private static final int ADD_ITEMS_SLOT = 49;

    public ViewTradeGUI(Player target, Player initiator, com.prismamc.trade.Plugin plugin, List<ItemStack> initiatorItems, long tradeId) {
        super(target, "Viewing Trade Items from " + initiator.getName(), 54);
        this.tradeTarget = target;
        this.tradeInitiator = initiator;
        this.plugin = plugin;
        this.initiatorItems = initiatorItems;
        this.tradeId = tradeId;
    }

    @Override
    protected void initializeItems() {
        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            owner.sendMessage(Component.text("Este trade ya no es válido!")
                .color(NamedTextColor.RED));
            owner.closeInventory();
            return;
        }

        // Display initiator's items in the first 4 rows
        for (int i = 0; i < Math.min(initiatorItems.size(), 36); i++) {
            inventory.setItem(i, initiatorItems.get(i));
        }

        // Add decorative border
        ItemStack border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName(" ")
            .getItemStack();
            
        for (int i = 36; i < 54; i++) {
            if (i != ADD_ITEMS_SLOT) {
                inventory.setItem(i, border);
            }
        }

        // Add button to add own items
        GUIItem addItemsButton = new GUIItem(Material.EMERALD)
            .setName("§aAdd Your Items to Trade")
            .setLore(
                "§7Click to select the items",
                "§7you want to trade with",
                "§f" + tradeInitiator.getName()
            );
        inventory.setItem(ADD_ITEMS_SLOT, addItemsButton.getItemStack());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        if (event.getRawSlot() == ADD_ITEMS_SLOT && event.getWhoClicked().equals(tradeTarget)) {
            if (!plugin.getTradeManager().isTradeValid(tradeId)) {
                tradeTarget.sendMessage(Component.text("Este trade ya no es válido!")
                    .color(NamedTextColor.RED));
                tradeTarget.closeInventory();
                return;
            }

            // Open PreTradeGUI for target to select items
            PreTradeGUI preTradeGUI = new PreTradeGUI(tradeTarget, tradeInitiator, plugin, true, tradeId);
            preTradeGUI.openInventory();
        }
    }
}