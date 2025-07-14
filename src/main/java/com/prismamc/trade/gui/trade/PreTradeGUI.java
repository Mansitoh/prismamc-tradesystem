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
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;

public class PreTradeGUI extends GUI {
    private final Player initiator;
    private final Player target;
    private final Plugin plugin;
    private final boolean isResponse;
    private final long tradeId;
    private int currentPage = 0;
    private static final int[] TRADE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int CONFIRM_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int ITEMS_PER_PAGE = 36;
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
        // Verificar validez del trade
        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            owner.sendMessage(Component.text("Este trade ya no es válido!")
                .color(NamedTextColor.RED));
            owner.closeInventory();
            return;
        }

        // Agregar bordes decorativos primero
        GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName(" ");
            
        for (int i = 36; i < 54; i++) {
            if (i != 40 && i != 49 && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT) {
                inventory.setItem(i, border.getItemStack());
            }
        }

        // Botones de paginación
        updatePaginationButtons();

        // Agregar botón de confirmar en la parte inferior
        GUIItem confirmButton = new GUIItem(Material.EMERALD)
            .setName(isResponse ? "§aConfirmar tus items" : "§aConfirmar oferta de trade")
            .setLore(
                isResponse ? new String[]{
                    "§7Click para proceder con el trade",
                    "§7y mostrar los items a",
                    "§f" + initiator.getName()
                } : new String[]{
                    "§7Click para enviar solicitud",
                    "§7de trade a",
                    "§f" + target.getName()
                }
            );
        inventory.setItem(CONFIRM_SLOT, confirmButton.getItemStack());

        // Agregar cartel informativo en el centro
        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eInformación del Trade")
            .setLore(
                "§7" + (isResponse ? "Respondiendo a" : "Tradeando con") + ": §f" + (isResponse ? initiator.getName() : target.getName()),
                "§7ID del Trade: §f" + tradeId,
                "§7Página: §f" + (currentPage + 1),
                "",
                "§7Coloca tus items en los",
                "§7slots disponibles arriba",
                "",
                "§7Click en el botón verde",
                "§7para confirmar"
            );
        inventory.setItem(INFO_SLOT, infoSign.getItemStack());

        // Restaurar items de la página actual
        updatePageItems();
    }

    private void updatePaginationButtons() {
        GUIItem prevPage = new GUIItem(Material.ARROW)
            .setName("§ePágina Anterior")
            .setLore("§7Click para ir a la página anterior");
        
        GUIItem nextPage = new GUIItem(Material.ARROW)
            .setName("§eSiguiente Página")
            .setLore("§7Click para ir a la siguiente página");

        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, prevPage.getItemStack());
        } else {
            inventory.setItem(PREV_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE).setName(" ").getItemStack());
        }

        inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());

    }


    private void updatePageItems() {
        // Limpiar slots de items
        for (int slot : TRADE_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Mostrar items de la página actual
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < selectedItems.size(); i++) {
            ItemStack item = selectedItems.get(startIndex + i);
            if (item != null && !item.getType().equals(Material.AIR)) {
                inventory.setItem(TRADE_SLOTS[i], item.clone());
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

        // Guardar los items de la página actual antes de cambiar de página
        if (clickedSlot == PREV_PAGE_SLOT || clickedSlot == NEXT_PAGE_SLOT) {
            event.setCancelled(true);
            saveCurrentPageItems();
            
            if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
                currentPage--;
            } else if (clickedSlot == NEXT_PAGE_SLOT) {
                currentPage++;
            }
            
            updatePaginationButtons();
            updatePageItems();
            initializeItems();
            return;
        }
        
        if (clickedSlot < inventory.getSize() && !isTradeSlot(clickedSlot)) {
            event.setCancelled(true);
            return;
        }
        
        if (clickedSlot == INFO_SLOT || (clickedSlot >= 45 && clickedSlot < 54)) {
            event.setCancelled(true);
        }

        // Si es un slot válido de trade, actualizar la lista de items
        if (isTradeSlot(clickedSlot)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                saveCurrentPageItems();
                updatePaginationButtons();
            });
        }
    }

    private void saveCurrentPageItems() {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        
        // Asegurarnos que la lista tenga suficiente espacio
        while (selectedItems.size() < startIndex + ITEMS_PER_PAGE) {
            selectedItems.add(null);
        }
        
        // Guardar o actualizar los items de la página actual
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            ItemStack item = inventory.getItem(TRADE_SLOTS[i]);
            selectedItems.set(startIndex + i, item != null ? item.clone() : null);
        }
        
        // Limpiar nulls del final de la lista
        for (int i = selectedItems.size() - 1; i >= 0; i--) {
            if (selectedItems.get(i) == null) {
                selectedItems.remove(i);
            } else {
                break;
            }
        }
    }

    private void handleConfirmClick() {
        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            owner.sendMessage(Component.text("Este trade ya no es válido!")
                .color(NamedTextColor.RED));
            owner.closeInventory();
            return;
        }

        // Guardar los items de la página actual antes de confirmar
        saveCurrentPageItems();
        
        // Verificar si hay items
        if (selectedItems.isEmpty()) {
            owner.sendMessage(Component.text("¡Debes seleccionar al menos un item para tradear!")
                .color(NamedTextColor.RED));
            return;
        }

        closedByButton = true;
        
        if (isResponse) {
            plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.ACTIVE);
            plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(), selectedItems);
            owner.closeInventory();
            
            initiator.sendMessage(Component.text(target.getName())
                .color(NamedTextColor.WHITE)
                .append(Component.text(" ha agregado sus items al trade!")
                .color(NamedTextColor.GREEN)));
                
            TradeGUI tradeGUI = new TradeGUI(initiator, target, tradeId, plugin);
            
            List<ItemStack> initiatorItems = plugin.getTradeManager().getTradeItems(tradeId, initiator.getUniqueId());
            List<ItemStack> targetItems = plugin.getTradeManager().getTradeItems(tradeId, target.getUniqueId());
            
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
            plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(), selectedItems);
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
            .color(NamedTextColor.WHITE))
            .append(Component.text(" (Trade ID: " + tradeId + ")")
            .color(NamedTextColor.GRAY)));
        
        target.sendMessage(Component.empty());
        target.sendMessage(Component.text("⚡ ¡Nueva solicitud de trade! ")
            .color(NamedTextColor.YELLOW)
            .append(Component.text("(ID: " + tradeId + ")")
            .color(NamedTextColor.GRAY)));
        target.sendMessage(Component.text(initiator.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" quiere tradear contigo. Click en el botón para ver sus items.")
            .color(NamedTextColor.GRAY)));
        target.sendMessage(Component.empty());
        
        Component viewButton = Component.text("[Ver Items del Trade]")
            .color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/tradeaccept " + initiator.getName() + " " + tradeId));
            
        target.sendMessage(viewButton);
        target.sendMessage(Component.empty());
    }

    public List<ItemStack> getSelectedItems() {
        // Asegurarse de guardar los items de la página actual antes de retornar la lista
        saveCurrentPageItems();
        return new ArrayList<>(selectedItems);
    }

    public static int[] getTradeSlots() {
        return TRADE_SLOTS.clone();
    }
    
    public boolean wasClosedByButton() {
        return closedByButton;
    }
}