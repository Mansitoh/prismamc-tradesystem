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
import java.util.concurrent.CompletableFuture;

public class ViewTradeGUI extends GUI {
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final Plugin plugin;
    private final List<ItemStack> initiatorItems;
    private final long tradeId;
    private int currentPage = 0;
    private static final int[] TRADE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int ADD_ITEMS_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int ITEMS_PER_PAGE = 36;

    public ViewTradeGUI(Player target, Player initiator, Plugin plugin, List<ItemStack> initiatorItems, long tradeId) {
        super(target, "Viewing Trade Items from " + initiator.getName(), 54);
        this.tradeTarget = target;
        this.tradeInitiator = initiator;
        this.plugin = plugin;
        this.initiatorItems = initiatorItems;
        this.tradeId = tradeId;
    }

    @Override
    protected void initializeItems() {
        plugin.getTradeManager().isTradeValid(tradeId)
            .thenAccept(isValid -> {
                if (!isValid) {
                    // Volver al hilo principal para mensajes y operaciones de inventario
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        owner.sendMessage(Component.text("Este trade ya no es válido!")
                            .color(NamedTextColor.RED));
                        owner.closeInventory();
                    });
                    return;
                }

                // Volver al hilo principal para operaciones de inventario
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Add decorative border first
                    ItemStack border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName(" ")
                        .getItemStack();
                        
                    for (int i = 36; i < 54; i++) {
                        if (i != ADD_ITEMS_SLOT && i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT) {
                            inventory.setItem(i, border);
                        }
                    }

                    // Actualizar botones de paginación
                    updatePaginationButtons();
                    
                    // Add button to add own items
                    GUIItem addItemsButton = new GUIItem(Material.EMERALD)
                        .setName("§aAgregar tus Items al Trade")
                        .setLore(
                            "§7Click para seleccionar los items",
                            "§7que quieres tradear con",
                            "§f" + tradeInitiator.getName()
                        );
                    inventory.setItem(ADD_ITEMS_SLOT, addItemsButton.getItemStack());

                    // Agregar cartel informativo en el centro
                    GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
                        .setName("§eInformación del Trade")
                        .setLore(
                            "§7Viendo items de: §f" + tradeInitiator.getName(),
                            "§7ID del Trade: §f" + tradeId,
                            "§7Página: §f" + (currentPage + 1)
                        );
                    inventory.setItem(INFO_SLOT, infoSign.getItemStack());

                    // Limpiar y mostrar items de la página actual
                    for (int slot : TRADE_SLOTS) {
                        inventory.setItem(slot, null);
                    }

                    int startIndex = currentPage * ITEMS_PER_PAGE;
                    for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < initiatorItems.size(); i++) {
                        inventory.setItem(TRADE_SLOTS[i], initiatorItems.get(startIndex + i));
                    }
                });
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    owner.sendMessage(Component.text("Error al verificar el trade: " + throwable.getMessage())
                        .color(NamedTextColor.RED));
                    owner.closeInventory();
                });
                return null;
            });
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

        if (hasNextPage()) {
            inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());
        } else {
            inventory.setItem(NEXT_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE).setName(" ").getItemStack());
        }
    }

    private boolean hasNextPage() {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        return initiatorItems.size() > startIndex + ITEMS_PER_PAGE;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int clickedSlot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            initializeItems();
            return;
        }

        if (clickedSlot == NEXT_PAGE_SLOT && hasNextPage()) {
            currentPage++;
            initializeItems();
            return;
        }
        
        if (clickedSlot == ADD_ITEMS_SLOT && player.equals(tradeTarget)) {
            plugin.getTradeManager().isTradeValid(tradeId)
                .thenAccept(isValid -> {
                    if (!isValid) {
                        // Volver al hilo principal para mensajes y operaciones de inventario
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            tradeTarget.sendMessage(Component.text("Este trade ya no es válido!")
                                .color(NamedTextColor.RED));
                            tradeTarget.closeInventory();
                        });
                        return;
                    }

                    // Volver al hilo principal para operaciones de inventario
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Open PreTradeGUI for target to select items
                        PreTradeGUI preTradeGUI = new PreTradeGUI(tradeTarget, tradeInitiator, plugin, true, tradeId);
                        preTradeGUI.openInventory();
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        tradeTarget.sendMessage(Component.text("Error al verificar el trade: " + throwable.getMessage())
                            .color(NamedTextColor.RED));
                    });
                    return null;
                });
        }
    }
}