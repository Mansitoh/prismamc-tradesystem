package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.model.PlayerData;
import com.prismamc.trade.model.TradeDocument;
import com.prismamc.trade.manager.TradeManager.TradeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GUI para administradores que permite ver los trades de cualquier jugador
 * Basado en MyTradesGUI pero con funcionalidad de solo lectura
 */
public class AdminViewTradesGUI extends GUI {
    private final Plugin plugin;
    private final PlayerData targetPlayerData; // Datos del jugador cuyos trades se están viendo
    private List<TradeDocument> allTrades;
    private List<TradeDocument> filteredTrades;
    private int currentPage = 0;
    private TradeFilter currentFilter = TradeFilter.ALL;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int INFO_SLOT = 49;

    // Slots para filtros de estado
    private static final int FILTER_ALL_SLOT = 46;
    private static final int FILTER_PENDING_SLOT = 47;
    private static final int FILTER_ACTIVE_SLOT = 48;
    private static final int FILTER_COMPLETED_SLOT = 50;
    private static final int FILTER_CANCELLED_SLOT = 51;

    public enum TradeFilter {
        ALL("Todos", Material.CHEST),
        PENDING("Pendientes", Material.YELLOW_WOOL),
        ACTIVE("Activos", Material.LIME_WOOL),
        COMPLETED("Completados", Material.BLUE_WOOL),
        CANCELLED("Cancelados", Material.RED_WOOL);

        private final String displayName;
        private final Material material;

        TradeFilter(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getMaterial() {
            return material;
        }
    }

    public AdminViewTradesGUI(Player admin, PlayerData targetPlayerData, Plugin plugin) {
        super(admin, "Admin View: " + targetPlayerData.getPlayerName() + "'s Trades", 54);
        this.plugin = plugin;
        this.targetPlayerData = targetPlayerData;
        this.allTrades = new ArrayList<>();
        this.filteredTrades = new ArrayList<>();
    }

    @Override
    protected void initializeItems() {
        loadTrades().thenRun(() -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                setupFilterButtons();
                setupBorders();
                updatePaginationButtons();
                setupInfoSign();
                displayTrades();
            });
        });
    }

    private CompletableFuture<Void> loadTrades() {
        return CompletableFuture.supplyAsync(() -> {
            List<TradeDocument> loadedTrades = new ArrayList<>();

            try {
                // Obtener TODOS los trades del jugador objetivo
                plugin.getMongoDBManager().getTradesCollection()
                        .find(new org.bson.Document("$or", List.of(
                                new org.bson.Document("player1", targetPlayerData.getUuid().toString()),
                                new org.bson.Document("player2", targetPlayerData.getUuid().toString()))))
                        .forEach(doc -> {
                            TradeDocument trade = new TradeDocument(doc);
                            loadedTrades.add(trade);
                        });

            } catch (Exception e) {
                plugin.getLogger().severe(String.format(
                        "❌ ERROR loading trades for %s (Admin: %s): %s",
                        targetPlayerData.getPlayerName(), owner.getName(), e.getMessage()));
                e.printStackTrace();
            }

            this.allTrades = loadedTrades;
            this.filteredTrades = new ArrayList<>(loadedTrades);
            return null;
        });
    }

    private void setupBorders() {
        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

        if (borderItem != null) {
            for (int i = 45; i < 54; i++) {
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, borderItem.clone());
                }
            }
        } else {
            GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE).setName("§7 ");
            for (int i = 45; i < 54; i++) {
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, border.getItemStack());
                }
            }
        }
    }

    private void updatePaginationButtons() {
        // Botón página anterior
        if (currentPage > 0) {
            ItemStack prevPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.previous_page");
            if (prevPageItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, prevPageItem);
            } else {
                GUIItem prevPage = new GUIItem(Material.ARROW)
                        .setName("§ePágina Anterior")
                        .setLore("§7Click para ir a la página anterior");
                inventory.setItem(PREV_PAGE_SLOT, prevPage.getItemStack());
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, disabledItem);
            } else {
                inventory.setItem(PREV_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }

        // Botón página siguiente
        if (hasNextPage()) {
            ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
            if (nextPageItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
            } else {
                GUIItem nextPage = new GUIItem(Material.ARROW)
                        .setName("§eSiguiente Página")
                        .setLore("§7Click para ir a la siguiente página");
                inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, disabledItem);
            } else {
                inventory.setItem(NEXT_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }
    }

    private void setupInfoSign() {
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredTrades.size() / ITEMS_PER_PAGE));

        // Usar ItemManager específico para admin view
        ItemStack infoItem = plugin.getItemManager().getItemStack(owner, "gui.info.admin_view_trades",
                "target_player", targetPlayerData.getPlayerName(),
                "admin_name", owner.getName(),
                "total_trades", String.valueOf(allTrades.size()),
                "filter_name", currentFilter.getDisplayName(),
                "filtered_count", String.valueOf(filteredTrades.size()),
                "current_page", String.valueOf(currentPage + 1),
                "total_pages", String.valueOf(totalPages));

        if (infoItem != null) {
            inventory.setItem(INFO_SLOT, infoItem);
        } else {
            // Fallback específico para admin view
            GUIItem infoSign = new GUIItem(Material.KNOWLEDGE_BOOK)
                    .setName("§6Admin View")
                    .setLore(
                            "§7Viendo trades de: §f" + targetPlayerData.getPlayerName(),
                            "§7Total de trades: §f" + filteredTrades.size(),
                            "§7Página: §f" + (currentPage + 1) + "/" + totalPages,
                            "§7Filtro: §f" + currentFilter.getDisplayName());
            inventory.setItem(INFO_SLOT, infoSign.getItemStack());
        }
    }

    private void displayTrades() {
        // Limpiar slots anteriores
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredTrades.size());

        for (int i = startIndex; i < endIndex; i++) {
            TradeDocument trade = filteredTrades.get(i);
            int slot = i - startIndex;

            // Determinar el otro jugador en el trade
            boolean isTargetPlayer1 = trade.getPlayer1().equals(targetPlayerData.getUuid());
            String otherPlayerUUID = isTargetPlayer1 ? trade.getPlayer2().toString() : trade.getPlayer1().toString();
            String otherPlayerName = plugin.getServer().getOfflinePlayer(UUID.fromString(otherPlayerUUID)).getName();

            String tradeState = getTradeStateDisplayName(trade.getState());
            String itemsReceived = isTargetPlayer1 ? getItemsReceivedDisplayName(trade.areItemsSentToPlayer1())
                    : getItemsReceivedDisplayName(trade.areItemsSentToPlayer2());

            // Usar ItemManager para mostrar trade con información admin
            ItemStack tradeItem = plugin.getItemManager().getItemStack(owner, "gui.buttons.admin_trade_display",
                    "trade_id", String.valueOf(trade.getTradeId()),
                    "state", tradeState,
                    "target_player", targetPlayerData.getPlayerName(),
                    "other_player", otherPlayerName,
                    "items_received", itemsReceived);

            if (tradeItem == null) {
                // Fallback con información admin
                Material material = trade.getState() == TradeState.ACTIVE ? Material.LIME_WOOL
                        : trade.getState() == TradeState.COMPLETED ? Material.BLUE_WOOL : Material.YELLOW_WOOL;
                GUIItem fallbackItem = new GUIItem(material)
                        .setName("§6[ADMIN] §eTrade #" + trade.getTradeId())
                        .setLore(
                                "§7Estado: " + tradeState,
                                "§7" + targetPlayerData.getPlayerName() + " ↔ §f" + otherPlayerName,
                                "§7Items recibidos: " + itemsReceived,
                                "",
                                "§6[Admin View] §eClick para ver detalles");
                tradeItem = fallbackItem.getItemStack();
            }

            inventory.setItem(slot, tradeItem);
        }
    }

    private String getTradeStateDisplayName(TradeState state) {
        switch (state) {
            case ACTIVE:
                return "<green>Activo</green>";
            case COMPLETED:
                return "<gold>Completado</gold>";
            case PENDING:
                return "<yellow>Pendiente</yellow>";
            case CANCELLED:
                return "<red>Cancelado</red>";
            default:
                return "<gray>Desconocido</gray>";
        }
    }

    private String getItemsReceivedDisplayName(boolean received) {
        return received ? "<green>Sí</green>" : "<red>No</red>";
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * ITEMS_PER_PAGE < filteredTrades.size();
    }

    private void setupFilterButtons() {
        for (TradeFilter filter : TradeFilter.values()) {
            int slot = getSlotForFilter(filter);
            if (slot == -1)
                continue;

            int count = getCountForFilter(filter);

            String filterKey = "gui.buttons.filter_" + filter.name().toLowerCase();
            ItemStack filterItem = plugin.getItemManager().getItemStack(owner, filterKey,
                    "count", String.valueOf(count),
                    "filter_name", filter.getDisplayName(),
                    "current_page", String.valueOf(currentPage + 1));

            if (filterItem == null) {
                GUIItem fallbackItem = new GUIItem(filter.getMaterial())
                        .setName("§e" + filter.getDisplayName() + " §7(" + count + ")")
                        .setLore("§7Click para filtrar por " + filter.getDisplayName().toLowerCase());

                if (filter == currentFilter) {
                    fallbackItem.setName("§a" + filter.getDisplayName() + " §7(" + count + ")");
                    fallbackItem.setLore("§7Click para filtrar por " + filter.getDisplayName().toLowerCase(),
                            "§a► Filtro actual");
                }

                filterItem = fallbackItem.getItemStack();

                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            } else {
                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            }

            inventory.setItem(slot, filterItem);
        }
    }

    private int getSlotForFilter(TradeFilter filter) {
        switch (filter) {
            case ALL:
                return FILTER_ALL_SLOT;
            case PENDING:
                return FILTER_PENDING_SLOT;
            case ACTIVE:
                return FILTER_ACTIVE_SLOT;
            case COMPLETED:
                return FILTER_COMPLETED_SLOT;
            case CANCELLED:
                return FILTER_CANCELLED_SLOT;
            default:
                return -1;
        }
    }

    private TradeFilter getFilterFromSlot(int slot) {
        switch (slot) {
            case FILTER_ALL_SLOT:
                return TradeFilter.ALL;
            case FILTER_PENDING_SLOT:
                return TradeFilter.PENDING;
            case FILTER_ACTIVE_SLOT:
                return TradeFilter.ACTIVE;
            case FILTER_COMPLETED_SLOT:
                return TradeFilter.COMPLETED;
            case FILTER_CANCELLED_SLOT:
                return TradeFilter.CANCELLED;
            default:
                return null;
        }
    }

    private int getCountForFilter(TradeFilter filter) {
        if (filter == TradeFilter.ALL) {
            return allTrades.size();
        }

        int count = 0;
        for (TradeDocument trade : allTrades) {
            if (matchesFilter(trade, filter)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesFilter(TradeDocument trade, TradeFilter filter) {
        switch (filter) {
            case ALL:
                return true;
            case PENDING:
                return trade.getState() == TradeState.PENDING;
            case ACTIVE:
                return trade.getState() == TradeState.ACTIVE;
            case COMPLETED:
                return trade.getState() == TradeState.COMPLETED;
            case CANCELLED:
                return trade.getState() == TradeState.CANCELLED;
            default:
                return false;
        }
    }

    private void setCurrentFilter(TradeFilter filter) {
        this.currentFilter = filter;
        this.filteredTrades = new ArrayList<>();

        for (TradeDocument trade : allTrades) {
            if (matchesFilter(trade, filter)) {
                filteredTrades.add(trade);
            }
        }

        this.currentPage = 0;
        setupInfoSign();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();

        // Navegación de páginas
        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            updatePaginationButtons();
            setupInfoSign();
            displayTrades();
            return;
        }

        if (clickedSlot == NEXT_PAGE_SLOT && hasNextPage()) {
            currentPage++;
            updatePaginationButtons();
            setupInfoSign();
            displayTrades();
            return;
        }

        // Click en un trade - SOLO VISTA PREVIA para administradores
        if (clickedSlot >= 0 && clickedSlot < 45) {
            int tradeIndex = currentPage * ITEMS_PER_PAGE + clickedSlot;
            if (tradeIndex < filteredTrades.size()) {
                TradeDocument trade = filteredTrades.get(tradeIndex);
                handleAdminTradeClick(trade);
            }
        }

        // Filtros
        TradeFilter selectedFilter = getFilterFromSlot(clickedSlot);
        if (selectedFilter != null) {
            setCurrentFilter(selectedFilter);
            setupFilterButtons();
            displayTrades();
            return;
        }
    }

    /**
     * Manejo especial de clicks en trades para administradores
     * Solo permite vista previa, sin modificar trades
     */
    private void handleAdminTradeClick(TradeDocument trade) {
        // Determinar el otro jugador
        boolean isTargetPlayer1 = trade.getPlayer1().equals(targetPlayerData.getUuid());
        UUID otherPlayerUUID = isTargetPlayer1 ? trade.getPlayer2() : trade.getPlayer1();
        String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();

        // Notificar al admin sobre la acción
        plugin.getMessageManager().sendComponentMessage(owner, "admin.viewtrades.viewing_trade",
                "trade_id", String.valueOf(trade.getTradeId()),
                "target_player", targetPlayerData.getPlayerName(),
                "other_player", otherPlayerName,
                "state", trade.getState().name());

        // Obtener los items del jugador objetivo para mostrar
        plugin.getTradeManager().getTradeItems(trade.getTradeId(), targetPlayerData.getUuid())
                .thenAccept(targetPlayerItems -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Crear ViewTradeGUI en modo admin (solo lectura)
                        ViewTradeGUI adminViewTradeGUI = new ViewTradeGUI(
                                owner, otherPlayerName, otherPlayerUUID, plugin,
                                targetPlayerItems, trade.getTradeId());

                        // Configurar como vista de solo lectura
                        adminViewTradeGUI.setOnlyPreview(true);
                        adminViewTradeGUI.setAdminView(true); // Nuevo flag para vista admin
                        adminViewTradeGUI.setOnlyPreview(true);

                        adminViewTradeGUI.openInventory();
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe(String.format(
                                "Error loading trade items for admin view: %s", throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner,
                                "admin.viewtrades.error_viewing_trade",
                                "error", throwable.getMessage());
                    });
                    return null;
                });
    }
}