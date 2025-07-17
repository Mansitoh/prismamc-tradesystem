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
    private static final int LANGUAGE_SELECTOR_SLOT = 52;

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
                setupLanguageSelector();
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
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT && i != LANGUAGE_SELECTOR_SLOT) {
                    inventory.setItem(i, borderItem.clone());
                }
            }
        } else {
            GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE).setName("§7 ");
            for (int i = 45; i < 54; i++) {
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT && i != LANGUAGE_SELECTOR_SLOT) {
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

            // Determinar los jugadores en el trade
            boolean isTargetPlayer1 = trade.getPlayer1().equals(targetPlayerData.getUuid());
            UUID player1UUID = trade.getPlayer1();
            UUID player2UUID = trade.getPlayer2();
            String player1Name = plugin.getServer().getOfflinePlayer(player1UUID).getName();
            String player2Name = plugin.getServer().getOfflinePlayer(player2UUID).getName();
            String otherPlayerName = isTargetPlayer1 ? player2Name : player1Name;

            String tradeState = getTradeStateDisplayName(trade.getState());
            String itemsReceived = isTargetPlayer1 ? getItemsReceivedDisplayName(trade.areItemsSentToPlayer1())
                    : getItemsReceivedDisplayName(trade.areItemsSentToPlayer2());

            // Usar ItemManager para mostrar trade con información admin mejorada
            ItemStack tradeItem = plugin.getItemManager().getItemStack(owner, "gui.buttons.admin_trade_display",
                    "trade_id", String.valueOf(trade.getTradeId()),
                    "state", tradeState,
                    "player1", player1Name,
                    "player2", player2Name,
                    "target_player", targetPlayerData.getPlayerName(),
                    "other_player", otherPlayerName,
                    "items_received", itemsReceived);

            if (tradeItem == null) {
                // Fallback con información admin mejorada
                Material material = trade.getState() == TradeState.ACTIVE ? Material.LIME_WOOL
                        : trade.getState() == TradeState.COMPLETED ? Material.BLUE_WOOL : Material.YELLOW_WOOL;
                GUIItem fallbackItem = new GUIItem(material)
                        .setName("§6[ADMIN] §eTrade #" + trade.getTradeId())
                        .setLore(
                                "§7Estado: " + tradeState,
                                "§7Player 1: §f" + player1Name + (isTargetPlayer1 ? " §7(Target)" : ""),
                                "§7Player 2: §f" + player2Name + (!isTargetPlayer1 ? " §7(Target)" : ""),
                                "§7Items recibidos: " + itemsReceived,
                                "",
                                "§e⚡ Admin Controls:",
                                "§a▶ Left Click: §fVer items de " + player1Name,
                                "§c▶ Right Click: §fVer items de " + player2Name,
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
        boolean isLeftClick = event.getClick().isLeftClick();
        boolean isRightClick = event.getClick().isRightClick();

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

        // Click en un trade - DUAL VIEW para administradores
        if (clickedSlot >= 0 && clickedSlot < 45) {
            int tradeIndex = currentPage * ITEMS_PER_PAGE + clickedSlot;
            if (tradeIndex < filteredTrades.size()) {
                TradeDocument trade = filteredTrades.get(tradeIndex);

                if (isLeftClick) {
                    // Left click: Ver items del Player 1
                    handleAdminTradeClick(trade, 1);
                } else if (isRightClick) {
                    // Right click: Ver items del Player 2
                    handleAdminTradeClick(trade, 2);
                }
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

        // Selector de idioma - Slot 52
        if (clickedSlot == LANGUAGE_SELECTOR_SLOT) {
            handleLanguageChange();
            return;
        }
    }

    /**
     * Manejo especial de clicks en trades para administradores con selección de
     * jugador
     * Solo permite vista previa, sin modificar trades
     * 
     * @param trade        El trade document a visualizar
     * @param playerNumber 1 para ver items del Player 1, 2 para ver items del
     *                     Player 2
     */
    private void handleAdminTradeClick(TradeDocument trade, int playerNumber) {
        // Determinar qué jugador se va a mostrar
        UUID selectedPlayerUUID;
        String selectedPlayerName;
        UUID otherPlayerUUID;
        String otherPlayerName;
        String viewingContext;

        if (playerNumber == 1) {
            selectedPlayerUUID = trade.getPlayer1();
            selectedPlayerName = plugin.getServer().getOfflinePlayer(selectedPlayerUUID).getName();
            otherPlayerUUID = trade.getPlayer2();
            otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
            viewingContext = "Player 1 (" + selectedPlayerName + ")";
        } else {
            selectedPlayerUUID = trade.getPlayer2();
            selectedPlayerName = plugin.getServer().getOfflinePlayer(selectedPlayerUUID).getName();
            otherPlayerUUID = trade.getPlayer1();
            otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
            viewingContext = "Player 2 (" + selectedPlayerName + ")";
        }

        // Notificar al admin sobre la acción específica
        plugin.getMessageManager().sendComponentMessage(owner, "admin.viewtrades.viewing_trade",
                "trade_id", String.valueOf(trade.getTradeId()),
                "target_player", viewingContext,
                "other_player", otherPlayerName,
                "state", trade.getState().name());

        // Obtener los items del jugador seleccionado para mostrar
        plugin.getTradeManager().getTradeItems(trade.getTradeId(), selectedPlayerUUID)
                .thenAccept(selectedPlayerItems -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Crear ViewTradeGUI en modo admin (solo lectura) con título descriptivo
                        String adminTitle = "Admin View: " + viewingContext + " - Trade #" + trade.getTradeId();

                        // Usar el constructor estándar de ViewTradeGUI
                        ViewTradeGUI adminViewTradeGUI = new ViewTradeGUI(
                                owner, selectedPlayerName, selectedPlayerUUID, plugin,
                                selectedPlayerItems, trade.getTradeId()) {

                            @Override
                            public String getTitle() {
                                return adminTitle;
                            }

                            @Override
                            protected void initializeItems() {
                                // Configurar como vista de solo lectura admin antes de inicializar
                                setOnlyPreview(true);
                                setAdminView(true);
                                super.initializeItems();
                            }
                        };

                        adminViewTradeGUI.openInventory();
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe(String.format(
                                "Error loading trade items for admin view (Player %d): %s",
                                playerNumber, throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner,
                                "admin.viewtrades.error_viewing_trade",
                                "error", throwable.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Configura el selector de idioma en el slot 52
     * Muestra el idioma actual del administrador y permite cambiarlo
     */
    private void setupLanguageSelector() {
        // Obtener el idioma actual del administrador
        PlayerData adminData = plugin.getPlayerDataManager().getCachedPlayerData(owner.getUniqueId());
        String currentLanguage = adminData != null ? adminData.getLanguage() : "en";

        // Mapeo de códigos de idioma a nombres legibles
        String languageDisplayName = getLanguageDisplayName(currentLanguage);

        // Obtener el item del selector de idioma del ItemManager
        ItemStack languageSelector = plugin.getItemManager().getItemStack(owner, "gui.buttons.language_selector",
                "current_language", languageDisplayName);

        if (languageSelector != null) {
            // Configurar la textura de la cabeza según el idioma
            languageSelector = setPlayerHeadTexture(languageSelector, currentLanguage);
            inventory.setItem(LANGUAGE_SELECTOR_SLOT, languageSelector);
        } else {
            // Fallback si no existe el item en ItemManager
            Material headMaterial = Material.PLAYER_HEAD;
            GUIItem fallbackSelector = new GUIItem(headMaterial)
                    .setName("§6🌍 Selector de Idioma")
                    .setLore(
                            "§7Idioma actual: §f" + languageDisplayName,
                            "",
                            "§7Idiomas disponibles:",
                            "§f• 🇺🇸 English (en)",
                            "§f• 🇪🇸 Español (es)",
                            "",
                            "§e🖱 ¡Haz click para cambiar!");

            ItemStack fallbackItem = fallbackSelector.getItemStack();
            fallbackItem = setPlayerHeadTexture(fallbackItem, currentLanguage);
            inventory.setItem(LANGUAGE_SELECTOR_SLOT, fallbackItem);
        }
    }

    /**
     * Maneja el cambio de idioma cuando se hace click en el selector
     */
    private void handleLanguageChange() {
        // Obtener el idioma actual del administrador
        PlayerData adminData = plugin.getPlayerDataManager().getCachedPlayerData(owner.getUniqueId());
        String currentLanguage = adminData != null ? adminData.getLanguage() : "en";

        // Obtener lista de idiomas disponibles del MessageManager
        String[] availableLanguages = { "en", "es" }; // Basado en los idiomas encontrados en MessageManager

        // Encontrar el siguiente idioma en la lista
        String newLanguage = getNextLanguage(currentLanguage, availableLanguages);

        // Actualizar el idioma del administrador
        plugin.getPlayerDataManager().updateLanguage(owner.getUniqueId(), newLanguage);

        // Enviar mensaje de confirmación
        String newLanguageDisplayName = getLanguageDisplayName(newLanguage);
        plugin.getMessageManager().sendComponentMessage(owner, "general.language_changed",
                "new_language", newLanguageDisplayName);

        // Actualizar toda la GUI con el nuevo idioma
        refreshGUIWithNewLanguage();
    }

    /**
     * Obtiene el siguiente idioma en la lista de idiomas disponibles
     */
    private String getNextLanguage(String currentLanguage, String[] availableLanguages) {
        for (int i = 0; i < availableLanguages.length; i++) {
            if (availableLanguages[i].equals(currentLanguage)) {
                // Retornar el siguiente idioma, o el primero si estamos en el último
                return availableLanguages[(i + 1) % availableLanguages.length];
            }
        }
        // Si no se encuentra el idioma actual, retornar el primero
        return availableLanguages[0];
    }

    /**
     * Obtiene el nombre legible del idioma
     */
    private String getLanguageDisplayName(String languageCode) {
        switch (languageCode) {
            case "en":
                return "English";
            case "es":
                return "Español";
            default:
                return "Unknown";
        }
    }

    /**
     * Configura la textura de la cabeza de jugador según el idioma
     */
    private ItemStack setPlayerHeadTexture(ItemStack item, String languageCode) {
        // Para esta implementación, usaremos diferentes cabezas según el idioma
        // En una implementación completa, podrías usar texturas específicas de banderas

        if (item.getType() == Material.PLAYER_HEAD) {
            // Por ahora, mantenemos la cabeza básica
            // En el futuro se pueden agregar texturas específicas usando SkullMeta
            return item;
        }
        return item;
    }

    /**
     * Refresca toda la GUI con el nuevo idioma
     */
    private void refreshGUIWithNewLanguage() {
        // Actualizar todos los elementos de la GUI con el nuevo idioma
        setupFilterButtons();
        setupInfoSign();
        setupLanguageSelector();
        updatePaginationButtons();
        displayTrades();

        // Mensaje de éxito
        owner.sendMessage("§a✓ Idioma actualizado. La GUI se ha refrescado.");
    }
}