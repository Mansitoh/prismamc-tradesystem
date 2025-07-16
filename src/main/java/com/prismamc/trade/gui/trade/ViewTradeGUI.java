package com.prismamc.trade.gui.trade;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

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
    private static final int CANCEL_TRADE_SLOT = 45;
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
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(owner, "general.invalid_trade_error");
                            owner.closeInventory();
                        });
                        return;
                    }

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Add decorative border first with player language support
                        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

                        if (borderItem != null) {
                            for (int i = 36; i < 54; i++) {
                                if (i != ADD_ITEMS_SLOT && i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT
                                        && i != CANCEL_TRADE_SLOT) {
                                    inventory.setItem(i, borderItem.clone());
                                }
                            }
                        }

                        // Update pagination buttons with player language support
                        updatePaginationButtons();

                        // Add button to add own items using ItemManager with player language
                        ItemStack addItemsButton = plugin.getItemManager().getItemStack(owner, "gui.buttons.add_items",
                                "player", tradeInitiator.getName());
                        if (addItemsButton != null) {
                            inventory.setItem(ADD_ITEMS_SLOT, addItemsButton);
                        }

                        // Add cancel trade button using ItemManager with player language
                        ItemStack cancelTradeButton = plugin.getItemManager().getItemStack(owner,
                                "gui.buttons.cancel_trade",
                                "trade_id", String.valueOf(tradeId));
                        if (cancelTradeButton != null) {
                            inventory.setItem(CANCEL_TRADE_SLOT, cancelTradeButton);
                        }

                        // Add info panel using ItemManager with player language
                        ItemStack infoSign = plugin.getItemManager().getItemStack(owner, "gui.info.view_trade_info",
                                "player", tradeInitiator.getName(),
                                "trade_id", String.valueOf(tradeId),
                                "page", String.valueOf(currentPage + 1));
                        if (infoSign != null) {
                            inventory.setItem(INFO_SLOT, infoSign);
                        }

                        // Clear and display current page items
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
                        plugin.getMessageManager().sendComponentMessage(owner, "general.verification_error", "error",
                                throwable.getMessage());
                        owner.closeInventory();
                    });
                    return null;
                });
    }

    private void updatePaginationButtons() {
        // Previous page button with player language support
        if (currentPage > 0) {
            ItemStack prevPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.previous_page");
            if (prevPageItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, prevPageItem);
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, disabledItem);
            }
        }

        if (hasNextPage()) {
            ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
            if (nextPageItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, disabledItem);
            }
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
                            PreTradeGUI preTradeGUI = new PreTradeGUI(tradeTarget, tradeInitiator, plugin, true,
                                    tradeId);
                            preTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            tradeTarget.sendMessage(
                                    Component.text("Error al verificar el trade: " + throwable.getMessage())
                                            .color(NamedTextColor.RED));
                        });
                        return null;
                    });
        }

        if (clickedSlot == CANCEL_TRADE_SLOT && player.equals(tradeTarget)) {
            // Use structured message system for cancel button feedback
            plugin.getMessageManager().sendComponentMessage(player, "general.feature_not_implemented");
        }
    }
}