package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.model.TradeDocument;
import com.prismamc.trade.manager.TradeManager.TradeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MyTradesGUI extends GUI {
    private final Plugin plugin;
    private List<TradeDocument> allTrades; // Todos los trades sin filtrar
    private List<TradeDocument> filteredTrades; // Trades después de aplicar filtros
    private int currentPage = 0;
    private TradeFilter currentFilter = TradeFilter.ALL;
    private static final int ITEMS_PER_PAGE = 45; // Ahora podemos usar toda la zona principal
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int INFO_SLOT = 49;

    // Slots para filtros de estado (fila inferior - después de los borders)
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

    public MyTradesGUI(Player owner, Plugin plugin) {
        super(owner, "Mis Trades", 54);
        this.plugin = plugin;
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
                // Obtener TODOS los documentos de trade que involucren al jugador
                plugin.getMongoDBManager().getTradesCollection()
                        .find(new org.bson.Document("$or", List.of(
                                new org.bson.Document("player1", owner.getUniqueId().toString()),
                                new org.bson.Document("player2", owner.getUniqueId().toString()))))
                        .forEach(doc -> {
                            TradeDocument trade = new TradeDocument(doc);
                            loadedTrades.add(trade); // Agregar TODOS los trades sin filtrar
                        });

            } catch (Exception e) {
                plugin.getLogger()
                        .severe(String.format("❌ ERROR cargando trades para %s: %s", owner.getName(), e.getMessage()));
                e.printStackTrace();
            }

            this.allTrades = loadedTrades;
            this.filteredTrades = new ArrayList<>(loadedTrades); // Inicialmente mostrar todos
            return null;
        });
    }

    private void setupBorders() {
        // Usar ItemManager como en PreTradeGUI
        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

        if (borderItem != null) {
            for (int i = 45; i < 54; i++) {
                // Excluir slots ocupados por otros elementos
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, borderItem.clone());
                }
            }
        } else {
            // Fallback si el item no existe
            GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                    .setName("§7 ");

            for (int i = 45; i < 54; i++) {
                // Excluir slots ocupados por otros elementos
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, border.getItemStack());
                }
            }
        }
    }

    private void updatePaginationButtons() {
        // Usar ItemManager como en PreTradeGUI para botón anterior
        if (currentPage > 0) {
            ItemStack prevPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.previous_page");
            if (prevPageItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, prevPageItem);
            } else {
                // Fallback
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
                // Fallback
                inventory.setItem(PREV_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }

        // Usar ItemManager para botón siguiente
        if (hasNextPage()) {
            ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
            if (nextPageItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
            } else {
                // Fallback
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
                // Fallback
                inventory.setItem(NEXT_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }
    }

    private void setupInfoSign() {
        // Usar el nuevo item específico para MyTradesGUI
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredTrades.size() / ITEMS_PER_PAGE));

        ItemStack infoItem = plugin.getItemManager().getItemStack(owner, "gui.info.my_trades_info",
                "total_trades", String.valueOf(allTrades.size()),
                "filter_name", currentFilter.getDisplayName(),
                "filtered_count", String.valueOf(filteredTrades.size()),
                "current_page", String.valueOf(currentPage + 1),
                "total_pages", String.valueOf(totalPages));

        if (infoItem != null) {
            inventory.setItem(INFO_SLOT, infoItem);
        } else {
            // Fallback si el item no existe
            GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
                    .setName("§eInformación")
                    .setLore(
                            "§7Total de trades: §f" + filteredTrades.size(),
                            "§7Página: §f" + (currentPage + 1));
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

            // Determinar si el jugador es player1 o player2
            boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
            String otherPlayerUUID = isPlayer1 ? trade.getPlayer2().toString() : trade.getPlayer1().toString();
            String otherPlayerName = plugin.getServer().getOfflinePlayer(UUID.fromString(otherPlayerUUID)).getName();

            // Intentar usar ItemManager para items de trade
            String tradeState = getTradeStateDisplayName(trade.getState());
            String itemsReceived = isPlayer1 ? getItemsReceivedDisplayName(trade.areItemsSentToPlayer1())
                    : getItemsReceivedDisplayName(trade.areItemsSentToPlayer2());

            ItemStack tradeItem = plugin.getItemManager().getItemStack(owner, "gui.buttons.trade_display",
                    "trade_id", String.valueOf(trade.getTradeId()),
                    "state", tradeState,
                    "other_player", otherPlayerName,
                    "items_received", itemsReceived);

            if (tradeItem == null) {
                // Fallback usando GUIItem manual
                Material material = trade.getState() == TradeState.ACTIVE ? Material.LIME_WOOL : Material.YELLOW_WOOL;
                GUIItem fallbackItem = new GUIItem(material)
                        .setName("§eTrade #" + trade.getTradeId())
                        .setLore(
                                "§7Estado: " + tradeState,
                                "§7Trading con: §f" + otherPlayerName,
                                "§7Items recibidos: " + itemsReceived,
                                "",
                                "§eClick para ver detalles");
                tradeItem = fallbackItem.getItemStack();
            }

            inventory.setItem(slot, tradeItem);
        }
    }

    /**
     * Obtener nombre de display para el estado del trade (en formato MiniMessage)
     */
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

    /**
     * Obtener el estado de items recibidos (en formato MiniMessage)
     */
    private String getItemsReceivedDisplayName(boolean received) {
        return received ? "<green>Sí</green>" : "<red>No</red>";
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * ITEMS_PER_PAGE < filteredTrades.size();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();

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

        // Si el click fue en un trade
        if (clickedSlot >= 0 && clickedSlot < 45) {
            int tradeIndex = currentPage * ITEMS_PER_PAGE + clickedSlot;
            if (tradeIndex < filteredTrades.size()) {
                TradeDocument trade = filteredTrades.get(tradeIndex);
                handleTradeClick(trade);
            }
        }

        // Manejo de clicks en los botones de filtro
        TradeFilter selectedFilter = getFilterFromSlot(clickedSlot);
        if (selectedFilter != null) {
            setCurrentFilter(selectedFilter);
            updateFilterButtons();
            displayTrades();
            return;
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

        this.currentPage = 0; // Reiniciar a la primera página al cambiar el filtro
        setupInfoSign(); // Actualizar contador
    }

    private void setupFilterButtons() {
        for (TradeFilter filter : TradeFilter.values()) {
            // Obtener el slot específico para cada filtro
            int slot = getSlotForFilter(filter);
            if (slot == -1)
                continue; // Skip si no hay slot definido

            // Contar trades para este filtro
            int count = getCountForFilter(filter);

            // Intentar usar ItemManager para botones de filtro con más parámetros
            String filterKey = "gui.buttons.filter_" + filter.name().toLowerCase();
            ItemStack filterItem = plugin.getItemManager().getItemStack(owner, filterKey,
                    "count", String.valueOf(count),
                    "filter_name", filter.getDisplayName(),
                    "current_page", String.valueOf(currentPage + 1));

            if (filterItem == null) {
                // Fallback usando GUIItem manual
                GUIItem fallbackItem = new GUIItem(filter.getMaterial())
                        .setName("§e" + filter.getDisplayName() + " §7(" + count + ")")
                        .setLore("§7Click para filtrar por " + filter.getDisplayName().toLowerCase());

                // Marcar el filtro actual
                if (filter == currentFilter) {
                    fallbackItem.setName("§a" + filter.getDisplayName() + " §7(" + count + ")");
                    // Añadir lore adicional manualmente
                    fallbackItem.setLore("§7Click para filtrar por " + filter.getDisplayName().toLowerCase(),
                            "§a► Filtro actual");
                }

                filterItem = fallbackItem.getItemStack();

                // Aplicar encantamiento después de obtener el ItemStack
                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            } else {
                // Si encontramos el item del ItemManager, aplicar encantamiento para filtro
                // actual
                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            }

            inventory.setItem(slot, filterItem);
        }
    }

    /**
     * Obtener el slot específico para cada filtro
     */
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

    /**
     * Mapear slot clickeado a filtro correspondiente
     */
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

    private void updateFilterButtons() {
        setupFilterButtons(); // Reutilizar la lógica mejorada
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
                // Mostrar trades en estado PENDING sin importar los items
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

    private void handleTradeClick(TradeDocument trade) {
        // Determinar si el jugador es player1 o player2
        boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
        UUID otherPlayerUUID = isPlayer1 ? trade.getPlayer2() : trade.getPlayer1();

        if (trade.getState() == TradeState.PENDING) {
            // Para trades pendientes, verificar si este jugador necesita agregar items
            boolean needsToAddItems = false;

            if (isPlayer1) {
                // Soy player1, verificar si NO he puesto items aún
                needsToAddItems = trade.getPlayer1Items().isEmpty();
            } else {
                // Soy player2, verificar si NO he puesto items aún
                needsToAddItems = trade.getPlayer2Items().isEmpty();
            }

            if (needsToAddItems) {
                // Abrir PreTradeGUI en modo respuesta para que pueda agregar items
                // No necesitamos al otro jugador online para esto
                String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();

                // Crear un PreTradeGUI usando solo la información necesaria
                PreTradeGUI preTradeGUI = new PreTradeGUI(owner, otherPlayerName, otherPlayerUUID, plugin, true,
                        trade.getTradeId());
                preTradeGUI.openInventory();
                return;
            } else {
                // El jugador ya agregó sus items, mostrar vista previa
                plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                        .thenAccept(items -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                                ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                        owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                                viewTradeGUI.setOnlyPreview(true);
                                viewTradeGUI.openInventory();
                            });
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger()
                                    .severe(String.format("Error cargando items del trade: %s",
                                            throwable.getMessage()));
                            plugin.getMessageManager().sendComponentMessage(owner,
                                    "mytrades.error.loading_trade_items");
                            return null;
                        });
                return;
            }
        } else if (trade.getState() == TradeState.ACTIVE) {
            // Abrir el GUI de trades activos
            plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                    .thenAccept(items -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                            ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                    owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                            viewTradeGUI.setOnlyPreview(true);
                            viewTradeGUI.setConfirmationView(true);
                            viewTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error cargando items del trade: %s", throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.loading_trade_items");
                        return null;
                    });
        } else if (trade.getState() == TradeState.COMPLETED) {
            // Para trades completados, verificar si el jugador ya recibió sus items
            boolean hasReceivedItems = isPlayer1 ? trade.areItemsSentToPlayer1() : trade.areItemsSentToPlayer2();

            if (hasReceivedItems) {
                // El jugador ya recibió sus items, mostrar mensaje
                plugin.getMessageManager().sendComponentMessage(owner, "mytrades.completed.already_received",
                        "trade_id", String.valueOf(trade.getTradeId()));
                return;
            }

            // El jugador aún no ha recibido sus items, dárselos ahora
            // Obtener los items que debe recibir este jugador
            List<ItemStack> itemsToReceive;
            if (isPlayer1) {
                // Player1 debe recibir los items de player2
                itemsToReceive = trade.getPlayer2Items();
            } else {
                // Player2 debe recibir los items de player1
                itemsToReceive = trade.getPlayer1Items();
            }

            if (itemsToReceive == null || itemsToReceive.isEmpty()) {
                // No hay items para recibir
                plugin.getMessageManager().sendComponentMessage(owner, "mytrades.completed.no_items_to_receive",
                        "trade_id", String.valueOf(trade.getTradeId()));
                return;
            }

            // Dar items al jugador
            for (ItemStack item : itemsToReceive) {
                if (item != null && item.getType() != Material.AIR) {
                    if (owner.getInventory().firstEmpty() != -1) {
                        owner.getInventory().addItem(item.clone());
                    } else {
                        // Inventario lleno, dropear items cerca del jugador
                        owner.getWorld().dropItemNaturally(owner.getLocation(), item.clone());
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.completed.inventory_full");
                    }
                }
            }

            // Actualizar el estado de items enviados
            plugin.getTradeManager().updateItemsSentStatus(
                    trade.getTradeId(),
                    owner.getUniqueId(),
                    true).thenRun(() -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(owner,
                                    "mytrades.completed.items_received", "trade_id",
                                    String.valueOf(trade.getTradeId()));
                            // Recargar el GUI para actualizar el estado
                            initializeItems();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error al actualizar estado de items: %s",
                                        throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.updating_item_status");
                        return null;
                    });
        } else {
            // Para trades cancelados o en otros estados, solo mostrar información
            plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                    .thenAccept(items -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                            ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                    owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                            viewTradeGUI.setOnlyPreview(true);
                            viewTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error cargando items del trade: %s", throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.loading_trade_items");
                        return null;
                    });
        }
    }
}