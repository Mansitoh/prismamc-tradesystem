package com.prismamc.trade.manager;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.model.CustomItem;
import com.prismamc.trade.model.PlayerData;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Item Manager with full memory caching
 * Eliminates database delays by keeping all items in memory
 */
public class ItemManager {
    private final Plugin plugin;
    private final MongoCollection<Document> itemsCollection;
    private final Map<String, CustomItem> itemCache;

    // Categories for organized item management
    public enum ItemCategory {
        GUI_BUTTONS("gui.buttons", "GUI Interactive Buttons"),
        GUI_DECORATIVE("gui.decorative", "GUI Decorative Elements"),
        GUI_INFO("gui.info", "GUI Information Panels"),
        GUI_NAVIGATION("gui.navigation", "GUI Navigation Controls"),
        TRADE_ITEMS("trade.items", "Trade Related Items"),
        SYSTEM_ITEMS("system.items", "System Items");

        private final String prefix;
        private final String displayName;

        ItemCategory(String prefix, String displayName) {
            this.prefix = prefix;
            this.displayName = displayName;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public ItemManager(Plugin plugin) {
        this.plugin = plugin;
        this.itemsCollection = plugin.getMongoDBManager().getDatabase().getCollection("items");
        this.itemCache = new ConcurrentHashMap<>();

        // Initialize and load all items into memory
        initializeDefaultItems();
        loadAllItems();
    }

    private void initializeDefaultItems() {
        // ===========================================
        // GUI BUTTONS - Interactive elements
        // ===========================================

        // Confirm buttons for PreTradeGUI
        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "confirm_new",
                Material.EMERALD, 1,
                Map.of(
                        "en", "<green><bold>Confirm Trade Offer</bold></green>",
                        "es", "<green><bold>Confirmar Oferta de Trade</bold></green>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to send trade request to <white>%player%</white>",
                                "<yellow>• Make sure you selected the right items",
                                "<yellow>• This action cannot be undone"),
                        "es", Arrays.asList(
                                "<gray>Click para enviar solicitud de trade a <white>%player%</white>",
                                "<yellow>• Asegúrate de haber seleccionado los items correctos",
                                "<yellow>• Esta acción no se puede deshacer")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "confirm_response",
                Material.EMERALD, 1,
                Map.of(
                        "en", "<green><bold>Confirm Your Items</bold></green>",
                        "es", "<green><bold>Confirmar Tus Items</bold></green>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to proceed with the trade",
                                "<green>• Your items will be secured",
                                "<green>• Trade will become active"),
                        "es", Arrays.asList(
                                "<gray>Click para proceder con el trade",
                                "<green>• Tus items serán asegurados",
                                "<green>• El trade se volverá activo")),
                null, null, null, null, null);

        // Add items button for ViewTradeGUI
        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "add_items",
                Material.EMERALD, 1,
                Map.of(
                        "en", "<green><bold>✚ Add Your Items to Trade</bold></green>",
                        "es", "<green><bold>✚ Agregar tus Items al Trade</bold></green>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to select the items",
                                "<gray>you want to trade with <white>%player%</white>",
                                "<green>• Secure transaction",
                                "<green>• Easy item selection"),
                        "es", Arrays.asList(
                                "<gray>Click para seleccionar los items",
                                "<gray>que quieres tradear con <white>%player%</white>",
                                "<green>• Transacción segura",
                                "<green>• Selección fácil de items")),
                null, null, null, null, null);

        // Cancel trade button
        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "cancel_trade",
                Material.BARRIER, 1,
                Map.of(
                        "en", "<red><bold>❌ Cancel Trade</bold></red>",
                        "es", "<red><bold>❌ Cancelar Trade</bold></red>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to cancel this trade",
                                "<red>• This action cannot be undone",
                                "<red>• All items will be returned",
                                "<gray>Trade ID: <white>%trade_id%</white>"),
                        "es", Arrays.asList(
                                "<gray>Click para cancelar este trade",
                                "<red>• Esta acción no se puede deshacer",
                                "<red>• Todos los items serán devueltos",
                                "<gray>ID del Trade: <white>%trade_id%</white>")),
                null, null, null, null, null);

        // ===========================================
        // GUI NAVIGATION - Page controls
        // ===========================================

        createMultiLanguageItem(ItemCategory.GUI_NAVIGATION, "previous_page",
                Material.ARROW, 1,
                Map.of(
                        "en", "<yellow><bold>← Previous Page</bold></yellow>",
                        "es", "<yellow><bold>← Página Anterior</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to navigate between pages",
                                "<green>• View previous items",
                                "<green>• Easy navigation"),
                        "es", Arrays.asList(
                                "<gray>Click para navegar entre páginas",
                                "<green>• Ver items anteriores",
                                "<green>• Navegación fácil")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_NAVIGATION, "next_page",
                Material.ARROW, 1,
                Map.of(
                        "en", "<yellow><bold>Next Page →</bold></yellow>",
                        "es", "<yellow><bold>Página Siguiente →</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to navigate between pages",
                                "<green>• View more items",
                                "<green>• Easy navigation"),
                        "es", Arrays.asList(
                                "<gray>Click para navegar entre páginas",
                                "<green>• Ver más items",
                                "<green>• Navegación fácil")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_NAVIGATION, "disabled_page",
                Material.GRAY_STAINED_GLASS_PANE, 1,
                Map.of(
                        "en", "<gray> ",
                        "es", "<gray> "),
                Map.of(
                        "en", new ArrayList<>(),
                        "es", new ArrayList<>()),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        // ===========================================
        // GUI INFO - Information panels
        // ===========================================

        createMultiLanguageItem(ItemCategory.GUI_INFO, "trade_info",
                Material.OAK_SIGN, 1,
                Map.of(
                        "en", "<yellow><bold>⚡ Trade Information</bold></yellow>",
                        "es", "<yellow><bold>⚡ Información del Trade</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Trading with: <white>%player%</white>",
                                "<gray>Trade ID: <white>%trade_id%</white>",
                                "<gray>Page: <white>%page%</white>",
                                "<gray>Selected Items: <green>%items%</green>"),
                        "es", Arrays.asList(
                                "<gray>Tradeando con: <white>%player%</white>",
                                "<gray>ID del Trade: <white>%trade_id%</white>",
                                "<gray>Página: <white>%page%</white>",
                                "<gray>Items Seleccionados: <green>%items%</green>")),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_INFO, "view_trade_info",
                Material.OAK_SIGN, 1,
                Map.of(
                        "en", "<yellow><bold>📋 Trade Information</bold></yellow>",
                        "es", "<yellow><bold>📋 Información del Trade</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Viewing items from: <white>%player%</white>",
                                "<gray>Trade ID: <white>%trade_id%</white>",
                                "<gray>Page: <white>%page%</white>",
                                "<green>• Safe trading environment",
                                "<green>• Secure item preview"),
                        "es", Arrays.asList(
                                "<gray>Viendo items de: <white>%player%</white>",
                                "<gray>ID del Trade: <white>%trade_id%</white>",
                                "<gray>Página: <white>%page%</white>",
                                "<green>• Entorno de trade seguro",
                                "<green>• Vista previa segura de items")),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        // ===========================================
        // GUI DECORATIVE - Border elements
        // ===========================================

        createMultiLanguageItem(ItemCategory.GUI_DECORATIVE, "border",
                Material.GRAY_STAINED_GLASS_PANE, 1,
                Map.of("en", "<gray> ", "es", "<gray> "),
                Map.of("en", new ArrayList<>(), "es", new ArrayList<>()),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_DECORATIVE, "border_active",
                Material.LIME_STAINED_GLASS_PANE, 1,
                Map.of("en", "<green> ", "es", "<green> "),
                Map.of("en", new ArrayList<>(), "es", new ArrayList<>()),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_DECORATIVE, "border_warning",
                Material.ORANGE_STAINED_GLASS_PANE, 1,
                Map.of("en", "<gold> ", "es", "<gold> "),
                Map.of("en", new ArrayList<>(), "es", new ArrayList<>()),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        // ===========================================
        // SYSTEM ITEMS - Special system items
        // ===========================================

        createMultiLanguageItem(ItemCategory.SYSTEM_ITEMS, "loading",
                Material.CLOCK, 1,
                Map.of(
                        "en", "<yellow><bold>⏰ Loading...</bold></yellow>",
                        "es", "<yellow><bold>⏰ Cargando...</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Please wait while we",
                                "<gray>process your request",
                                "<yellow>This may take a moment"),
                        "es", Arrays.asList(
                                "<gray>Por favor espera mientras",
                                "<gray>procesamos tu solicitud",
                                "<yellow>Esto puede tomar un momento")),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        createMultiLanguageItem(ItemCategory.SYSTEM_ITEMS, "error",
                Material.BARRIER, 1,
                Map.of(
                        "en", "<red><bold>⚠ Error</bold></red>",
                        "es", "<red><bold>⚠ Error</bold></red>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>An error occurred",
                                "<red>Please try again later",
                                "<gray>Contact staff if persists"),
                        "es", Arrays.asList(
                                "<gray>Ocurrió un error",
                                "<red>Por favor intenta más tarde",
                                "<gray>Contacta al staff si persiste")),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

        // ===========================================
        // MyTradesGUI Specific Items
        // ===========================================
        
        // Filter buttons for MyTradesGUI
        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_all",
                Material.CHEST, 1,
                Map.of(
                        "en", "<yellow><bold>All Trades</bold></yellow> <gray>(%count%)</gray>",
                        "es", "<yellow><bold>Todos los Trades</bold></yellow> <gray>(%count%)</gray>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to show all your trades",
                                "<green>• View all trade activity",
                                "<green>• Complete overview"),
                        "es", Arrays.asList(
                                "<gray>Click para mostrar todos tus trades",
                                "<green>• Ver toda la actividad de trades",
                                "<green>• Vista general completa")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_pending",
                Material.YELLOW_WOOL, 1,
                Map.of(
                        "en", "<yellow><bold>Pending Trades</bold></yellow> <gray>(%count%)</gray>",
                        "es", "<yellow><bold>Trades Pendientes</bold></yellow> <gray>(%count%)</gray>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to show pending trades",
                                "<yellow>• Trades waiting for your items",
                                "<yellow>• Action required"),
                        "es", Arrays.asList(
                                "<gray>Click para mostrar trades pendientes",
                                "<yellow>• Trades esperando tus items",
                                "<yellow>• Acción requerida")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_active",
                Material.LIME_WOOL, 1,
                Map.of(
                        "en", "<green><bold>Active Trades</bold></green> <gray>(%count%)</gray>",
                        "es", "<green><bold>Trades Activos</bold></green> <gray>(%count%)</gray>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to show active trades",
                                "<green>• Trades in progress",
                                "<green>• Ready for completion"),
                        "es", Arrays.asList(
                                "<gray>Click para mostrar trades activos",
                                "<green>• Trades en progreso",
                                "<green>• Listos para completar")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_completed",
                Material.BLUE_WOOL, 1,
                Map.of(
                        "en", "<blue><bold>Completed Trades</bold></blue> <gray>(%count%)</gray>",
                        "es", "<blue><bold>Trades Completados</bold></blue> <gray>(%count%)</gray>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to show completed trades",
                                "<blue>• Finished trades",
                                "<blue>• Items ready to collect"),
                        "es", Arrays.asList(
                                "<gray>Click para mostrar trades completados",
                                "<blue>• Trades finalizados",
                                "<blue>• Items listos para recoger")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_cancelled",
                Material.RED_WOOL, 1,
                Map.of(
                        "en", "<red><bold>Cancelled Trades</bold></red> <gray>(%count%)</gray>",
                        "es", "<red><bold>Trades Cancelados</bold></red> <gray>(%count%)</gray>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Click to show cancelled trades",
                                "<red>• Cancelled or failed trades",
                                "<red>• Historical records"),
                        "es", Arrays.asList(
                                "<gray>Click para mostrar trades cancelados",
                                "<red>• Trades cancelados o fallidos",
                                "<red>• Registros históricos")),
                null, null, null, null, null);

        // Trade item representations for MyTradesGUI
        createMultiLanguageItem(ItemCategory.GUI_INFO, "trade_pending",
                Material.YELLOW_WOOL, 1,
                Map.of(
                        "en", "<yellow><bold>Trade #%trade_id%</bold></yellow>",
                        "es", "<yellow><bold>Trade #%trade_id%</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Status: <yellow>Pending</yellow>",
                                "<gray>Trading with: <white>%other_player%</white>",
                                "<gray>Items added: <red>No</red>",
                                "",
                                "<yellow>Click to add your items"),
                        "es", Arrays.asList(
                                "<gray>Estado: <yellow>Pendiente</yellow>",
                                "<gray>Tradeando con: <white>%other_player%</white>",
                                "<gray>Items agregados: <red>No</red>",
                                "",
                                "<yellow>Click para agregar tus items")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_INFO, "trade_active",
                Material.LIME_WOOL, 1,
                Map.of(
                        "en", "<green><bold>Trade #%trade_id%</bold></green>",
                        "es", "<green><bold>Trade #%trade_id%</bold></green>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Status: <green>Active</green>",
                                "<gray>Trading with: <white>%other_player%</white>",
                                "<gray>Items received: %items_received%",
                                "",
                                "<green>Click to view details"),
                        "es", Arrays.asList(
                                "<gray>Estado: <green>Activo</green>",
                                "<gray>Tradeando con: <white>%other_player%</white>",
                                "<gray>Items recibidos: %items_received%",
                                "",
                                "<green>Click para ver detalles")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_INFO, "trade_completed",
                Material.BLUE_WOOL, 1,
                Map.of(
                        "en", "<blue><bold>Trade #%trade_id%</bold></blue>",
                        "es", "<blue><bold>Trade #%trade_id%</bold></blue>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Status: <blue>Completed</blue>",
                                "<gray>Trading with: <white>%other_player%</white>",
                                "<gray>Items received: %items_received%",
                                "",
                                "<blue>Click to collect items"),
                        "es", Arrays.asList(
                                "<gray>Estado: <blue>Completado</blue>",
                                "<gray>Tradeando con: <white>%other_player%</white>",
                                "<gray>Items recibidos: %items_received%",
                                "",
                                "<blue>Click para recoger items")),
                null, null, null, null, null);

        createMultiLanguageItem(ItemCategory.GUI_INFO, "trade_cancelled",
                Material.RED_WOOL, 1,
                Map.of(
                        "en", "<red><bold>Trade #%trade_id%</bold></red>",
                        "es", "<red><bold>Trade #%trade_id%</bold></red>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Status: <red>Cancelled</red>",
                                "<gray>Trading with: <white>%other_player%</white>",
                                "<gray>Reason: <red>%cancel_reason%</red>",
                                "",
                                "<red>This trade was cancelled"),
                        "es", Arrays.asList(
                                "<gray>Estado: <red>Cancelado</red>",
                                "<gray>Tradeando con: <white>%other_player%</white>",
                                "<gray>Razón: <red>%cancel_reason%</red>",
                                "",
                                "<red>Este trade fue cancelado")),
                null, null, null, null, null);

        // MyTradesGUI info sign
        createMultiLanguageItem(ItemCategory.GUI_INFO, "mytrades_info",
                Material.OAK_SIGN, 1,
                Map.of(
                        "en", "<yellow><bold>📋 My Trades</bold></yellow>",
                        "es", "<yellow><bold>📋 Mis Trades</bold></yellow>"),
                Map.of(
                        "en", Arrays.asList(
                                "<gray>Total trades: <white>%total%</white>",
                                "<gray>Current filter: <white>%filter%</white>",
                                "<gray>Page: <white>%page%</white>",
                                "",
                                "<green>• Use filters to organize",
                                "<green>• Click trades to interact"),
                        "es", Arrays.asList(
                                "<gray>Total de trades: <white>%total%</white>",
                                "<gray>Filtro actual: <white>%filter%</white>",
                                "<gray>Página: <white>%page%</white>",
                                "",
                                "<green>• Usa filtros para organizar",
                                "<green>• Click en trades para interactuar")),
                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);
    }

    private void createMultiLanguageItem(ItemCategory category, String itemKey, Material material, int amount,
            Map<String, String> displayNames, Map<String, List<String>> lore,
            Map<Enchantment, Integer> enchantments, List<ItemFlag> itemFlags,
            Integer customModelData, Boolean unbreakable, Map<String, Object> customNBT) {

        String fullKey = category.getPrefix() + "." + itemKey;
        CustomItem item = new CustomItem(fullKey, material, amount, displayNames, lore,
                enchantments, itemFlags, customModelData, unbreakable, customNBT);

        // Store in cache for immediate use
        itemCache.put(fullKey, item);
    }

    /**
     * Load all items from database into memory cache
     */
    private void loadAllItems() {
        CompletableFuture.runAsync(() -> {
            try {
                // First, save any new default items to database
                for (Map.Entry<String, CustomItem> entry : itemCache.entrySet()) {
                    String itemId = entry.getKey();
                    CustomItem item = entry.getValue();

                    Document existingItem = itemsCollection.find(Filters.eq("itemId", itemId)).first();
                    if (existingItem == null) {
                        // Insert new item
                        itemsCollection.insertOne(item.toDocument());
                        plugin.getLogger().info("Added default item: " + itemId);
                    }
                }

                // Then load all items from database (in case of manual edits)
                Map<String, CustomItem> databaseItems = new HashMap<>();
                for (Document doc : itemsCollection.find()) {
                    CustomItem item = new CustomItem(doc);
                    databaseItems.put(item.getItemId(), item);
                }

                // Update cache with database items
                itemCache.putAll(databaseItems);

                plugin.getLogger().info("Loaded " + itemCache.size() + " items into memory cache");

            } catch (Exception e) {
                plugin.getLogger().severe("Error loading items: " + e.getMessage());
                e.printStackTrace();
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Fatal error loading items: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Get an item by ID (instant - from memory)
     */
    public CustomItem getItem(String itemId) {
        return itemCache.get(itemId);
    }

    /**
     * Get an ItemStack by ID (instant - from memory) - DEPRECATED
     * Use getItemStack(Player, String, String...) for language support
     */
    @Deprecated
    public ItemStack getItemStack(String itemId) {
        CustomItem item = itemCache.get(itemId);
        return item != null ? item.createItemStack("en") : null;
    }

    /**
     * Get an ItemStack with custom lore replacements - DEPRECATED
     * Use getItemStack(Player, String, String...) for language support
     */
    @Deprecated
    public ItemStack getItemStack(String itemId, String... loreReplacements) {
        CustomItem item = itemCache.get(itemId);
        if (item == null)
            return null;

        return item.createItemStack("en", loreReplacements);
    }

    /**
     * Get an ItemStack by ID with player language support (instant - from memory)
     */
    public ItemStack getItemStack(Player player, String itemId, String... replacements) {
        CustomItem item = itemCache.get(itemId);
        if (item == null)
            return null;

        // Get player's language preference
        String language = getPlayerLanguage(player);
        return item.createItemStack(language, replacements);
    }

    /**
     * Get an ItemStack by ID with specific language (instant - from memory)
     */
    public ItemStack getItemStack(String itemId, String language, String... replacements) {
        CustomItem item = itemCache.get(itemId);
        if (item == null)
            return null;

        return item.createItemStack(language, replacements);
    }

    /**
     * Get items by category
     */
    public Map<String, CustomItem> getItemsByCategory(ItemCategory category) {
        Map<String, CustomItem> categoryItems = new HashMap<>();
        String prefix = category.getPrefix() + ".";

        for (Map.Entry<String, CustomItem> entry : itemCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                categoryItems.put(entry.getKey(), entry.getValue());
            }
        }

        return categoryItems;
    }

    /**
     * Update an item in memory and database
     */
    public CompletableFuture<Boolean> updateItem(String itemId, CustomItem newItem) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update in cache first (instant)
                itemCache.put(itemId, newItem);

                // Update in database
                Document filter = new Document("itemId", itemId);
                itemsCollection.replaceOne(filter, newItem.toDocument());

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error updating item " + itemId + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Reload all items from database
     */
    public CompletableFuture<Void> reloadItems() {
        return CompletableFuture.runAsync(() -> {
            itemCache.clear();
            loadAllItems();
        });
    }

    /**
     * Get all cached items
     */
    public Map<String, CustomItem> getAllItems() {
        return new HashMap<>(itemCache);
    }

    /**
     * Check if an item exists
     */
    public boolean hasItem(String itemId) {
        return itemCache.containsKey(itemId);
    }

    /**
     * Get total number of cached items
     */
    public int getItemCount() {
        return itemCache.size();
    }

    /**
     * Get item categories
     */
    public ItemCategory[] getCategories() {
        return ItemCategory.values();
    }

    /**
     * Get player's language preference with fallback
     */
    private String getPlayerLanguage(Player player) {
        try {
            PlayerData playerData = plugin.getPlayerDataManager().getCachedPlayerData(player.getUniqueId());
            return playerData != null ? playerData.getLanguage() : "en";
        } catch (Exception e) {
            // Fallback to English if PlayerData is not available
            return "en";
        }
    }
}