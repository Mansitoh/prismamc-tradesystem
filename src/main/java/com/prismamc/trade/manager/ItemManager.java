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

                // Confirm trade button for ViewTradeGUI - Accept the trade final confirmation
                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "confirm_trade_final",
                                Material.DIAMOND, 1,
                                Map.of(
                                                "en", "<green><bold>✅ CONFIRM & COMPLETE TRADE</bold></green>",
                                                "es", "<green><bold>✅ CONFIRMAR Y COMPLETAR TRADE</bold></green>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow><bold>⚠ FINAL CONFIRMATION ⚠</bold></yellow>",
                                                                "<gray>You are about to complete this trade:",
                                                                "",
                                                                "<white>• Trading with: <gold>%player%</gold>",
                                                                "<white>• Trade ID: <aqua>#%trade_id%</aqua>",
                                                                "<white>• You will receive their items",
                                                                "<white>• They will receive your items",
                                                                "",
                                                                "<green><bold>✓ This trade is secure and verified</bold></green>",
                                                                "<red><bold>⚠ This action cannot be undone!</bold></red>",
                                                                "",
                                                                "<yellow>🖱 Click to complete the trade!</yellow>",
                                                                "<gray>Both players will receive their items"),
                                                "es", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow><bold>⚠ CONFIRMACIÓN FINAL ⚠</bold></yellow>",
                                                                "<gray>Estás a punto de completar este trade:",
                                                                "",
                                                                "<white>• Trading con: <gold>%player%</gold>",
                                                                "<white>• ID del Trade: <aqua>#%trade_id%</aqua>",
                                                                "<white>• Recibirás sus items",
                                                                "<white>• Recibirá tus items",
                                                                "",
                                                                "<green><bold>✓ Este trade es seguro y verificado</bold></green>",
                                                                "<red><bold>⚠ ¡Esta acción no se puede deshacer!</bold></red>",
                                                                "",
                                                                "<yellow>🖱 ¡Haz click para completar el trade!</yellow>",
                                                                "<gray>Ambos jugadores recibirán sus items")),
                                Map.of(Enchantment.UNBREAKING, 1),
                                Arrays.asList(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES),
                                null, null, null);

                // Alternative confirm button for when trade is ready but waiting for both
                // players
                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "confirm_trade_waiting",
                                Material.GOLD_INGOT, 1,
                                Map.of(
                                                "en", "<gold><bold>⏳ Waiting for Confirmation</bold></gold>",
                                                "es", "<gold><bold>⏳ Esperando Confirmación</bold></gold>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow>Trade Status: <white>Active & Ready</white></yellow>",
                                                                "<gray>Trading with: <white>%player%</white>",
                                                                "<gray>Trade ID: <white>#%trade_id%</white>",
                                                                "",
                                                                "<green>✓ Both players have added items</green>",
                                                                "<green>✓ All items are secured</green>",
                                                                "<gold>⏳ Waiting for final confirmation</gold>",
                                                                "",
                                                                "<yellow>🔄 Current Status:</yellow>",
                                                                "<white>• Your confirmation: <green>Ready</green>",
                                                                "<white>• Their confirmation: <yellow>Pending...</yellow>",
                                                                "",
                                                                "<gray>The trade will complete automatically",
                                                                "<gray>when both players confirm."),
                                                "es", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow>Estado del Trade: <white>Activo y Listo</white></yellow>",
                                                                "<gray>Trading con: <white>%player%</white>",
                                                                "<gray>ID del Trade: <white>#%trade_id%</white>",
                                                                "",
                                                                "<green>✓ Ambos jugadores agregaron items</green>",
                                                                "<green>✓ Todos los items están asegurados</green>",
                                                                "<gold>⏳ Esperando confirmación final</gold>",
                                                                "",
                                                                "<yellow>🔄 Estado Actual:</yellow>",
                                                                "<white>• Tu confirmación: <green>Lista</green>",
                                                                "<white>• Su confirmación: <yellow>Pendiente...</yellow>",
                                                                "",
                                                                "<gray>El trade se completará automáticamente",
                                                                "<gray>cuando ambos jugadores confirmen.")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

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
                // MyTradesGUI specific items
                // ===========================================

                // Info panel específico para MyTradesGUI
                createMultiLanguageItem(ItemCategory.GUI_INFO, "my_trades_info",
                                Material.WRITTEN_BOOK, 1,
                                Map.of(
                                                "en", "<gold><bold>📚 My Trade History</bold></gold>",
                                                "es", "<gold><bold>📚 Mi Historial de Trades</bold></gold>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow>📊 Overview:</yellow>",
                                                                "<white>• Total trades: <green>%total_trades%</green>",
                                                                "<white>• Showing: <yellow>%filter_name%</yellow> <gray>(%filtered_count%)</gray>",
                                                                "<white>• Current page: <aqua>%current_page%</aqua> of <aqua>%total_pages%</aqua>",
                                                                "",
                                                                "<yellow>🔍 Filters available:</yellow>",
                                                                "<white>• <gold>All</gold> - View all your trades",
                                                                "<white>• <yellow>Pending</yellow> - Trades waiting for items",
                                                                "<white>• <green>Active</green> - Ready to complete",
                                                                "<white>• <blue>Completed</blue> - Collect your items",
                                                                "<white>• <red>Cancelled</red> - Returned trades",
                                                                "",
                                                                "<gray>Click on any trade to interact!"),
                                                "es", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<yellow>📊 Resumen:</yellow>",
                                                                "<white>• Total trades: <green>%total_trades%</green>",
                                                                "<white>• Mostrando: <yellow>%filter_name%</yellow> <gray>(%filtered_count%)</gray>",
                                                                "<white>• Página actual: <aqua>%current_page%</aqua> de <aqua>%total_pages%</aqua>",
                                                                "",
                                                                "<yellow>🔍 Filtros disponibles:</yellow>",
                                                                "<white>• <gold>Todos</gold> - Ver todos tus trades",
                                                                "<white>• <yellow>Pendientes</yellow> - Trades esperando items",
                                                                "<white>• <green>Activos</green> - Listos para completar",
                                                                "<white>• <blue>Completados</blue> - Recoge tus items",
                                                                "<white>• <red>Cancelados</red> - Trades devueltos",
                                                                "",
                                                                "<gray>¡Haz click en cualquier trade para interactuar!")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                // Botones de filtro mejorados para MyTradesGUI
                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_all",
                                Material.CHEST, 1,
                                Map.of(
                                                "en", "<gold><bold>📦 All Trades</bold></gold> <gray>(%count%)</gray>",
                                                "es",
                                                "<gold><bold>📦 Todos los Trades</bold></gold> <gray>(%count%)</gray>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<yellow>═══ Filter: All Trades ═══</yellow>",
                                                                "<gray>Shows <white>every trade</white> you've participated in",
                                                                "",
                                                                "<green>✓ Includes:</green>",
                                                                "<white>• Pending trades waiting for items",
                                                                "<white>• Active trades ready to complete",
                                                                "<white>• Completed trades with rewards",
                                                                "<white>• Cancelled trades",
                                                                "",
                                                                "<aqua>📍 Current page: %current_page%</aqua>",
                                                                "<gray>Click to show all trades"),
                                                "es", Arrays.asList(
                                                                "<yellow>═══ Filtro: Todos los Trades ═══</yellow>",
                                                                "<gray>Muestra <white>todos los trades</white> en los que participaste",
                                                                "",
                                                                "<green>✓ Incluye:</green>",
                                                                "<white>• Trades pendientes esperando items",
                                                                "<white>• Trades activos listos para completar",
                                                                "<white>• Trades completados con recompensas",
                                                                "<white>• Trades cancelados",
                                                                "",
                                                                "<aqua>📍 Página actual: %current_page%</aqua>",
                                                                "<gray>Click para mostrar todos los trades")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_pending",
                                Material.YELLOW_WOOL, 1,
                                Map.of(
                                                "en",
                                                "<yellow><bold>⏳ Pending Trades</bold></yellow> <gray>(%count%)</gray>",
                                                "es",
                                                "<yellow><bold>⏳ Trades Pendientes</bold></yellow> <gray>(%count%)</gray>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<yellow>═══ Filter: Pending Trades ═══</yellow>",
                                                                "<gray>Shows trades where <white>you need to add items</white>",
                                                                "",
                                                                "<gold>⚠ Action Required:</gold>",
                                                                "<white>• You haven't added your items yet",
                                                                "<white>• Other player is waiting for you",
                                                                "<white>• Trade will expire if not completed",
                                                                "",
                                                                "<green>💡 What to do:</green>",
                                                                "<white>• Click on a trade to add your items",
                                                                "<white>• Select items from your inventory",
                                                                "<white>• Confirm to make trade active",
                                                                "",
                                                                "<aqua>📍 Current page: %current_page%</aqua>"),
                                                "es", Arrays.asList(
                                                                "<yellow>═══ Filtro: Trades Pendientes ═══</yellow>",
                                                                "<gray>Muestra trades donde <white>necesitas agregar items</white>",
                                                                "",
                                                                "<gold>⚠ Acción Requerida:</gold>",
                                                                "<white>• No has agregado tus items aún",
                                                                "<white>• El otro jugador te está esperando",
                                                                "<white>• El trade expirará si no se completa",
                                                                "",
                                                                "<green>💡 Qué hacer:</green>",
                                                                "<white>• Haz click en un trade para agregar items",
                                                                "<white>• Selecciona items de tu inventario",
                                                                "<white>• Confirma para activar el trade",
                                                                "",
                                                                "<aqua>📍 Página actual: %current_page%</aqua>")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_active",
                                Material.LIME_WOOL, 1,
                                Map.of(
                                                "en",
                                                "<green><bold>🔄 Active Trades</bold></green> <gray>(%count%)</gray>",
                                                "es",
                                                "<green><bold>🔄 Trades Activos</bold></green> <gray>(%count%)</gray>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<green>═══ Filter: Active Trades ═══</green>",
                                                                "<gray>Shows trades that are <white>ready to complete</white>",
                                                                "",
                                                                "<green>✓ Trade Status:</green>",
                                                                "<white>• Both players have added items",
                                                                "<white>• Items are secured and verified",
                                                                "<white>• Waiting for final confirmation",
                                                                "",
                                                                "<gold>🎯 What's next:</gold>",
                                                                "<white>• View the other player's items",
                                                                "<white>• Accept or negotiate the trade",
                                                                "<white>• Complete to receive items",
                                                                "",
                                                                "<aqua>📍 Current page: %current_page%</aqua>",
                                                                "<gray>Click to view active trades"),
                                                "es", Arrays.asList(
                                                                "<green>═══ Filtro: Trades Activos ═══</green>",
                                                                "<gray>Muestra trades que están <white>listos para completar</white>",
                                                                "",
                                                                "<green>✓ Estado del Trade:</green>",
                                                                "<white>• Ambos jugadores agregaron items",
                                                                "<white>• Items asegurados y verificados",
                                                                "<white>• Esperando confirmación final",
                                                                "",
                                                                "<gold>🎯 Qué sigue:</gold>",
                                                                "<white>• Ver los items del otro jugador",
                                                                "<white>• Aceptar o negociar el trade",
                                                                "<white>• Completar para recibir items",
                                                                "",
                                                                "<aqua>📍 Página actual: %current_page%</aqua>",
                                                                "<gray>Click para ver trades activos")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_completed",
                                Material.BLUE_WOOL, 1,
                                Map.of(
                                                "en",
                                                "<blue><bold>✅ Completed Trades</bold></blue> <gray>(%count%)</gray>",
                                                "es",
                                                "<blue><bold>✅ Trades Completados</bold></blue> <gray>(%count%)</gray>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<blue>═══ Filter: Completed Trades ═══</blue>",
                                                                "<gray>Shows <white>successfully finished</white> trades",
                                                                "",
                                                                "<green>🎉 Trade Completed:</green>",
                                                                "<white>• Both players confirmed the exchange",
                                                                "<white>• Your items were given to other player",
                                                                "<white>• You can now collect your rewards",
                                                                "",
                                                                "<gold>💰 Rewards waiting:</gold>",
                                                                "<white>• Click on any completed trade",
                                                                "<white>• Items will be added to inventory",
                                                                "<white>• If inventory full, items drop nearby",
                                                                "",
                                                                "<aqua>📍 Current page: %current_page%</aqua>",
                                                                "<gray>Click to view completed trades"),
                                                "es", Arrays.asList(
                                                                "<blue>═══ Filtro: Trades Completados ═══</blue>",
                                                                "<gray>Muestra trades <white>terminados exitosamente</white>",
                                                                "",
                                                                "<green>🎉 Trade Completado:</green>",
                                                                "<white>• Ambos jugadores confirmaron el intercambio",
                                                                "<white>• Tus items fueron dados al otro jugador",
                                                                "<white>• Ahora puedes recoger tus recompensas",
                                                                "",
                                                                "<gold>💰 Recompensas esperando:</gold>",
                                                                "<white>• Haz click en cualquier trade completado",
                                                                "<white>• Items serán agregados al inventario",
                                                                "<white>• Si inventario lleno, items caen cerca",
                                                                "",
                                                                "<aqua>📍 Página actual: %current_page%</aqua>",
                                                                "<gray>Click para ver trades completados")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "filter_cancelled",
                                Material.RED_WOOL, 1,
                                Map.of(
                                                "en",
                                                "<red><bold>❌ Cancelled Trades</bold></red> <gray>(%count%)</gray>",
                                                "es",
                                                "<red><bold>❌ Trades Cancelados</bold></red> <gray>(%count%)</gray>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<red>═══ Filter: Cancelled Trades ═══</red>",
                                                                "<gray>Shows trades that were <white>cancelled or expired</white>",
                                                                "",
                                                                "<red>❌ Why cancelled:</red>",
                                                                "<white>• Player disconnected for too long",
                                                                "<white>• Trade was manually cancelled",
                                                                "<white>• System timeout or error occurred",
                                                                "",
                                                                "<green>♻ Items returned:</green>",
                                                                "<white>• All your items were returned safely",
                                                                "<white>• No items were lost in the process",
                                                                "<white>• You can start a new trade anytime",
                                                                "",
                                                                "<aqua>📍 Current page: %current_page%</aqua>",
                                                                "<gray>Click to view cancelled trades"),
                                                "es", Arrays.asList(
                                                                "<red>═══ Filtro: Trades Cancelados ═══</red>",
                                                                "<gray>Muestra trades que fueron <white>cancelados o expiraron</white>",
                                                                "",
                                                                "<red>❌ Por qué cancelado:</red>",
                                                                "<white>• Jugador desconectado por mucho tiempo",
                                                                "<white>• Trade fue cancelado manualmente",
                                                                "<white>• Timeout del sistema o error ocurrió",
                                                                "",
                                                                "<green>♻ Items devueltos:</green>",
                                                                "<white>• Todos tus items fueron devueltos",
                                                                "<white>• No se perdieron items en el proceso",
                                                                "<white>• Puedes iniciar un nuevo trade cuando quieras",
                                                                "",
                                                                "<aqua>📍 Página actual: %current_page%</aqua>",
                                                                "<gray>Click para ver trades cancelados")),
                                null, Arrays.asList(ItemFlag.HIDE_ATTRIBUTES), null, null, null);

                // Item para mostrar cada trade individual (mejorado)
                createMultiLanguageItem(ItemCategory.GUI_BUTTONS, "trade_display",
                                Material.CHEST_MINECART, 1,
                                Map.of(
                                                "en", "<yellow><bold>⚡ Trade #%trade_id%</bold></yellow>",
                                                "es", "<yellow><bold>⚡ Trade #%trade_id%</bold></yellow>"),
                                Map.of(
                                                "en", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<white>Status: %state%",
                                                                "<white>Trading with: <gold>%other_player%</gold>",
                                                                "<white>Items received: %items_received%",
                                                                "",
                                                                "<yellow>📋 Trade Details:</yellow>",
                                                                "<white>• Trade ID: <gray>#%trade_id%</gray>",
                                                                "<white>• Created: <gray>%date%</gray>",
                                                                "<white>• Your role: <aqua>%player_role%</aqua>",
                                                                "",
                                                                "<green>🖱 Click to interact!</green>",
                                                                "<gray>View details, add items, or collect rewards"),
                                                "es", Arrays.asList(
                                                                "<gray>═══════════════════════════",
                                                                "<white>Estado: %state%",
                                                                "<white>Trading con: <gold>%other_player%</gold>",
                                                                "<white>Items recibidos: %items_received%",
                                                                "",
                                                                "<yellow>📋 Detalles del Trade:</yellow>",
                                                                "<white>• ID del Trade: <gray>#%trade_id%</gray>",
                                                                "<white>• Creado: <gray>%date%</gray>",
                                                                "<white>• Tu rol: <aqua>%player_role%</aqua>",
                                                                "",
                                                                "<green>🖱 ¡Haz click para interactuar!</green>",
                                                                "<gray>Ver detalles, agregar items, o recoger recompensas")),
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

                                        Document existingItem = itemsCollection.find(Filters.eq("itemId", itemId))
                                                        .first();
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