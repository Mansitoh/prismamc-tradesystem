package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.manager.TradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

public class PreTradeGUI extends GUI {
    private final Player initiator;
    private final Player target;
    private final Plugin plugin;
    private final boolean isResponse;
    private final long tradeId;
    private static final int[] TRADE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int CONFIRM_SLOT = 44;
    private static final int INFO_SLOT = 40;
    private final List<ItemStack> selectedItems;
    private boolean closedByButton;

    public PreTradeGUI(Player owner, Player target, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, isResponse ? "Selecciona tus items para el trade" : "Selecciona los items a tradear", 54);
        this.initiator = owner;
        this.target = target;
        this.plugin = plugin;
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.selectedItems = new ArrayList<>();
        this.closedByButton = false;
    }

    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        this(owner, target, plugin, false, plugin.getTradeManager().createNewTrade(owner.getUniqueId(), target.getUniqueId()));
    }

    @Override
    protected void initializeItems() {
        GUIItem confirmButton = new GUIItem(Material.EMERALD)
            .setName(isResponse ? "§aConfirmar tus items" : "§aConfirmar oferta de trade")
            .setLore(
                isResponse ? new String[]{
                    "§7Click para confirmar y proceder",
                    "§7con el trade"
                } : new String[]{
                    "§7Click para confirmar y enviar",
                    "§7solicitud de trade a " + target.getName()
                }
            );
        inventory.setItem(CONFIRM_SLOT, confirmButton.getItemStack());

        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eInformación del Trade")
            .setLore(
                "§7Tradeando con: §f" + (isResponse ? initiator.getName() : target.getName()),
                "§7Click en items de tu inventario",
                "§7para agregarlos al trade",
                "",
                "§7Cierra la ventana para",
                "§7cancelar y recuperar tus items"
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
        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            owner.sendMessage(Component.text("Este trade ya no es válido!")
                .color(NamedTextColor.RED));
            owner.closeInventory();
            return;
        }

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
            owner.sendMessage(Component.text("¡Debes seleccionar al menos un item para tradear!")
                .color(NamedTextColor.RED));
            return;
        }

        // Cerrar con éxito el menú
        closedByButton = true;
        
        if (isResponse) {
            plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.ACTIVE);
            plugin.getTradeManager().storePreTradeItems(owner.getUniqueId(), selectedItems);
            owner.closeInventory();
            
            initiator.sendMessage(Component.text(target.getName())
                .color(NamedTextColor.WHITE)
                .append(Component.text(" ha agregado sus items al trade!")
                .color(NamedTextColor.GREEN)));
                
            TradeGUI tradeGUI = new TradeGUI(initiator, target, tradeId, plugin);
            
            List<ItemStack> initiatorItems = plugin.getTradeManager().getAndRemovePreTradeItems(initiator.getUniqueId());
            List<ItemStack> targetItems = plugin.getTradeManager().getAndRemovePreTradeItems(target.getUniqueId());
            
            if (initiatorItems != null) {
                for (int i = 0; i < initiatorItems.size(); i++) {
                    tradeGUI.setInitialItem(initiator, i, initiatorItems.get(i));
                }
            }
            
            if (targetItems != null) {
                for (int i = 0; i < targetItems.size(); i++) {
                    tradeGUI.setInitialItem(target, i, targetItems.get(i));
                }
            }
            
            tradeGUI.openInventory();
            tradeGUI.openFor(target);
            
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
        initiator.sendMessage(Component.text("Tus items han sido enviados a ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(target.getName())
            .color(NamedTextColor.WHITE)));
        
        target.sendMessage(Component.empty());
        target.sendMessage(Component.text("⚡ ¡Nueva solicitud de trade!")
            .color(NamedTextColor.YELLOW));
        target.sendMessage(Component.text(initiator.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" quiere tradear contigo. Click en el botón para ver sus items.")
            .color(NamedTextColor.GRAY)));
        target.sendMessage(Component.empty());
        
        Component viewButton = Component.text("[Ver Items del Trade]")
            .color(NamedTextColor.GREEN)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tradeaccept " + initiator.getName()));
            
        target.sendMessage(viewButton);
        target.sendMessage(Component.empty());
    }

    public List<ItemStack> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    public static int[] getTradeSlots() {
        return TRADE_SLOTS.clone();
    }
    
    public boolean wasClosedByButton() {
        return closedByButton;
    }
}