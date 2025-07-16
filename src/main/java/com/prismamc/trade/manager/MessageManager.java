package com.prismamc.trade.manager;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.model.Message;
import org.bson.Document;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import com.prismamc.trade.model.PlayerData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced Message Manager with Component support and easy in-game editing
 * Supports MiniMessage formatting and multi-language messages
 */
public class MessageManager {
    private final Plugin plugin;
    private final MongoCollection<Document> messagesCollection;
    private final Map<String, Message> messageCache;
    private final Map<String, MessageCategory> messageCategories;

    // Modern formatters
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    // Message Categories for organized management
    public enum MessageCategory {
        TRADE_ACTIONS("trade.actions", "Trade Actions"),
        TRADE_ERRORS("trade.errors", "Trade Errors"),
        TRADE_SUCCESS("trade.success", "Trade Success Messages"),
        PRETRADE_GUI("pretrade.gui", "PreTrade GUI Elements"),
        PRETRADE_BUTTONS("pretrade.buttons", "PreTrade Buttons"),
        PRETRADE_INFO("pretrade.info", "PreTrade Information"),
        PRETRADE_NOTIFICATIONS("pretrade.notifications", "PreTrade Notifications"),
        PRETRADE_ERRORS("pretrade.errors", "PreTrade Errors"),
        VIEW_TRADE_GUI("viewtrade.gui", "View Trade GUI"),
        PAGINATION("pagination", "Pagination Controls"),
        GENERAL("general", "General Messages"),
        TRADE_NOTIFICATIONS("trade.notifications", "Trade Notifications");

        private final String prefix;
        private final String displayName;

        MessageCategory(String prefix, String displayName) {
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

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.messagesCollection = plugin.getMongoDBManager().getDatabase().getCollection("messages");
        this.messageCache = new HashMap<>();
        this.messageCategories = new HashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();

        // Initialize message structure
        initializeMessageCategories();
        initializeDefaultMessages();
        loadMessages();
    }

    private void initializeMessageCategories() {
        for (MessageCategory category : MessageCategory.values()) {
            messageCategories.put(category.getPrefix(), category);
        }
    }

    private void initializeDefaultMessages() {
        // ===========================================
        // TRADE ACTIONS - Core trade functionality
        // ===========================================
        addMessage(MessageCategory.TRADE_ACTIONS, "request.sent",
                Map.of(
                        "en", "<green>Trade request sent to <yellow>%player%</yellow>",
                        "es", "<green>Solicitud de trade enviada a <yellow>%player%</yellow>"));

        addMessage(MessageCategory.TRADE_ACTIONS, "request.received",
                Map.of(
                        "en", "<green>Trade request received from <yellow>%player%</yellow>",
                        "es", "<green>Solicitud de trade recibida de <yellow>%player%</yellow>"));

        addMessage(MessageCategory.TRADE_ACTIONS, "accepted",
                Map.of(
                        "en", "<green>Trade accepted with <yellow>%player%</yellow>",
                        "es", "<green>Trade aceptado con <yellow>%player%</yellow>"));

        addMessage(MessageCategory.TRADE_ACTIONS, "declined",
                Map.of(
                        "en", "<red>Trade declined with <yellow>%player%</yellow>",
                        "es", "<red>Trade rechazado con <yellow>%player%</yellow>"));

        addMessage(MessageCategory.TRADE_ACTIONS, "cancelled",
                Map.of(
                        "en", "<red>Trade cancelled with <yellow>%player%</yellow>",
                        "es", "<red>Trade cancelado con <yellow>%player%</yellow>"));

        // ===========================================
        // TRADE ERRORS - Error handling
        // ===========================================
        addMessage(MessageCategory.TRADE_ERRORS, "self_trade",
                Map.of(
                        "en", "<red>You cannot trade with yourself!",
                        "es", "<red>¡No puedes tradear contigo mismo!"));

        addMessage(MessageCategory.TRADE_ERRORS, "already_trading",
                Map.of(
                        "en", "<red>You are already in a trade!",
                        "es", "<red>¡Ya estás en un trade!"));

        addMessage(MessageCategory.TRADE_ERRORS, "player_not_found",
                Map.of(
                        "en", "<red>Player not found!",
                        "es", "<red>¡Jugador no encontrado!"));

        addMessage(MessageCategory.TRADE_ERRORS, "cooldown",
                Map.of(
                        "en", "<red>Please wait <yellow>%seconds%</yellow> seconds before trading again!",
                        "es", "<red>¡Espera <yellow>%seconds%</yellow> segundos antes de tradear de nuevo!"));

        // ===========================================
        // PRETRADE GUI - Interface elements
        // ===========================================
        addMessage(MessageCategory.PRETRADE_GUI, "title.new",
                Map.of(
                        "en", "Select items to trade",
                        "es", "Selecciona items para tradear"));

        addMessage(MessageCategory.PRETRADE_GUI, "title.response",
                Map.of(
                        "en", "Select your items for trade",
                        "es", "Selecciona tus items para el trade"));

        // ===========================================
        // PRETRADE BUTTONS - Interactive elements
        // ===========================================
        addMessage(MessageCategory.PRETRADE_BUTTONS, "confirm.new.lore",
                Map.of(
                        "en",
                        "<gray>Click to send trade request to</gray>\n<white>%player%</white>\n<yellow>• Make sure you selected the right items</yellow>\n<yellow>• This action cannot be undone</yellow>",
                        "es",
                        "<gray>Click para enviar solicitud de trade a</gray>\n<white>%player%</white>\n<yellow>• Asegúrate de haber seleccionado los items correctos</yellow>\n<yellow>• Esta acción no se puede deshacer</yellow>"));

        addMessage(MessageCategory.PRETRADE_BUTTONS, "confirm.response.lore",
                Map.of(
                        "en",
                        "<gray>Click to proceed with the trade and</gray>\n<gray>show your items to</gray>\n<white>%player%</white>\n<green>• Your items will be secured</green>\n<green>• Trade will become active</green>",
                        "es",
                        "<gray>Click para proceder con el trade y</gray>\n<gray>mostrar tus items a</gray>\n<white>%player%</white>\n<green>• Tus items serán asegurados</green>\n<green>• El trade se volverá activo</green>"));

        // ===========================================
        // PRETRADE INFO - Information display
        // ===========================================
        addMessage(MessageCategory.PRETRADE_INFO, "panel.title",
                Map.of(
                        "en", "<yellow><bold>⚡ Trade Information</bold></yellow>",
                        "es", "<yellow><bold>⚡ Información del Trade</bold></yellow>"));

        addMessage(MessageCategory.PRETRADE_INFO, "trading_with",
                Map.of(
                        "en", "<gray>Trading with:</gray> <white><bold>%player%</bold></white>",
                        "es", "<gray>Tradeando con:</gray> <white><bold>%player%</bold></white>"));

        addMessage(MessageCategory.PRETRADE_INFO, "responding_to",
                Map.of(
                        "en", "<gray>Responding to:</gray> <white><bold>%player%</bold></white>",
                        "es", "<gray>Respondiendo a:</gray> <white><bold>%player%</bold></white>"));

        addMessage(MessageCategory.PRETRADE_INFO, "trade_id",
                Map.of(
                        "en", "<gray>Trade ID:</gray> <white><bold>%trade_id%</bold></white>",
                        "es", "<gray>ID del Trade:</gray> <white><bold>%trade_id%</bold></white>"));

        addMessage(MessageCategory.PRETRADE_INFO, "trade_id.pending",
                Map.of(
                        "en", "<gray>Trade ID:</gray> <yellow><bold>Pending...</bold></yellow>",
                        "es", "<gray>ID del Trade:</gray> <yellow><bold>Pendiente...</bold></yellow>"));

        addMessage(MessageCategory.PRETRADE_INFO, "page_info",
                Map.of(
                        "en", "<gray>Page:</gray> <white><bold>%page%</bold></white>",
                        "es", "<gray>Página:</gray> <white><bold>%page%</bold></white>"));

        addMessage(MessageCategory.PRETRADE_INFO, "items_count",
                Map.of(
                        "en", "<gray>Selected Items:</gray> <green><bold>%items%</bold></green>",
                        "es", "<gray>Items Seleccionados:</gray> <green><bold>%items%</bold></green>"));

        // ===========================================
        // PAGINATION - Navigation controls
        // ===========================================
        addMessage(MessageCategory.PAGINATION, "previous.name",
                Map.of(
                        "en", "<yellow><bold>← Previous Page</bold></yellow>",
                        "es", "<yellow><bold>← Página Anterior</bold></yellow>"));

        addMessage(MessageCategory.PAGINATION, "next.name",
                Map.of(
                        "en", "<yellow><bold>Next Page →</bold></yellow>",
                        "es", "<yellow><bold>Página Siguiente →</bold></yellow>"));

        addMessage(MessageCategory.PAGINATION, "navigation.lore",
                Map.of(
                        "en",
                        "<gray>Click to navigate between pages</gray>\n<green>• View more items</green>\n<green>• Organize your selection</green>",
                        "es",
                        "<gray>Click para navegar entre páginas</gray>\n<green>• Ver más items</green>\n<green>• Organiza tu selección</green>"));

        // ===========================================
        // PRETRADE ERRORS - Error handling for GUI
        // ===========================================
        addMessage(MessageCategory.PRETRADE_ERRORS, "no_items",
                Map.of(
                        "en",
                        "<red><bold>⚠ No Items Selected!</bold></red>\n<gray>You must select at least one item to trade</gray>",
                        "es",
                        "<red><bold>⚠ ¡No hay Items Seleccionados!</bold></red>\n<gray>Debes seleccionar al menos un item para tradear</gray>"));

        addMessage(MessageCategory.PRETRADE_ERRORS, "invalid_trade",
                Map.of(
                        "en",
                        "<red><bold>⚠ Invalid Trade!</bold></red>\n<gray>This trade is no longer valid or has expired</gray>",
                        "es",
                        "<red><bold>⚠ ¡Trade Inválido!</bold></red>\n<gray>Este trade ya no es válido o ha expirado</gray>"));

        addMessage(MessageCategory.PRETRADE_ERRORS, "verification_failed",
                Map.of(
                        "en", "<red><bold>⚠ Verification Failed!</bold></red>\n<gray>Error: %error%</gray>",
                        "es", "<red><bold>⚠ ¡Verificación Fallida!</bold></red>\n<gray>Error: %error%</gray>"));

        // ===========================================
        // TRADE SUCCESS - Success messages
        // ===========================================
        addMessage(MessageCategory.TRADE_SUCCESS, "items_sent",
                Map.of(
                        "en",
                        "<green><bold>✓ Items Sent Successfully!</bold></green>\n<gray>Your items have been secured for the trade</gray>",
                        "es",
                        "<green><bold>✓ ¡Items Enviados Exitosamente!</bold></green>\n<gray>Tus items han sido asegurados para el trade</gray>"));

        addMessage(MessageCategory.TRADE_SUCCESS, "items_sent_to",
                Map.of(
                        "en",
                        "<green><bold>✓ Items Sent to %player%!</bold></green>\n<gray>Waiting for their response...</gray>",
                        "es",
                        "<green><bold>✓ ¡Items Enviados a %player%!</bold></green>\n<gray>Esperando su respuesta...</gray>"));

        // ===========================================
        // PRETRADE NOTIFICATIONS - System notifications
        // ===========================================
        addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "items_added",
                Map.of(
                        "en",
                        "<white><bold>%player%</bold></white> <green>has added their items to the trade!</green>\n<yellow>The trade is now ready for confirmation</yellow>",
                        "es",
                        "<white><bold>%player%</bold></white> <green>ha agregado sus items al trade!</green>\n<yellow>El trade está listo para confirmación</yellow>"));

        addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_request_alert",
                Map.of(
                        "en",
                        "<yellow><bold>⚡ NEW TRADE REQUEST!</bold></yellow> <gray>(ID: <white>%trade_id%</white>)</gray>",
                        "es",
                        "<yellow><bold>⚡ ¡NUEVA SOLICITUD DE TRADE!</bold></yellow> <gray>(ID: <white>%trade_id%</white>)</gray>"));

        addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_request_description",
                Map.of(
                        "en",
                        "<white><bold>%player%</bold></white> <gray>wants to trade with you</gray>\n<green>Click the button below to view their items</green>",
                        "es",
                        "<white><bold>%player%</bold></white> <gray>quiere tradear contigo</gray>\n<green>Haz click en el botón para ver sus items</green>"));

        addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_request_sent",
                Map.of(
                        "en",
                        "<green><bold>✓ Trade Request Sent!</bold></green>\n<gray>Sent to:</gray> <white><bold>%player%</bold></white>\n<gray>Trade ID:</gray> <white><bold>%trade_id%</bold></white>",
                        "es",
                        "<green><bold>✓ ¡Solicitud de Trade Enviada!</bold></green>\n<gray>Enviada a:</gray> <white><bold>%player%</bold></white>\n<gray>ID del Trade:</gray> <white><bold>%trade_id%</bold></white>"));

        addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_accepted",
                Map.of(
                        "en",
                        "<green><bold>🎉 %player% accepted your trade!</bold></green>\n<yellow>Click the confirmation button to finalize</yellow>",
                        "es",
                        "<green><bold>🎉 ¡%player% aceptó tu trade!</bold></green>\n<yellow>Haz click en el botón de confirmación para finalizar</yellow>"));

        // ===========================================
        // INTERACTIVE BUTTONS - Clickable elements
        // ===========================================
        addMessage(MessageCategory.PRETRADE_BUTTONS, "view_trade",
                Map.of(
                        "en", "<green><bold>[📦 VIEW TRADE ITEMS]</bold></green>",
                        "es", "<green><bold>[📦 VER ITEMS DEL TRADE]</bold></green>"));

        addMessage(MessageCategory.PRETRADE_BUTTONS, "confirm_trade",
                Map.of(
                        "en", "<yellow><bold>[✅ CLICK TO CONFIRM TRADE]</bold></yellow>",
                        "es", "<yellow><bold>[✅ CLICK PARA CONFIRMAR TRADE]</bold></yellow>"));

        // ===========================================
        // VIEW TRADE GUI - Trade viewing interface
        // ===========================================
        addMessage(MessageCategory.VIEW_TRADE_GUI, "cancel_button.name",
                Map.of(
                        "en", "<red><bold>❌ Cancel Trade</bold></red>",
                        "es", "<red><bold>❌ Cancelar Trade</bold></red>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "cancel_button.lore",
                Map.of(
                        "en",
                        "<gray>Click to cancel this trade</gray>\n<red>• This action cannot be undone</red>\n<red>• All items will be returned</red>\n<gray>Trade ID:</gray> <white>%trade_id%</white>",
                        "es",
                        "<gray>Click para cancelar este trade</gray>\n<red>• Esta acción no se puede deshacer</red>\n<red>• Todos los items serán devueltos</red>\n<gray>ID del Trade:</gray> <white>%trade_id%</white>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "add_items_button.name",
                Map.of(
                        "en", "<green><bold>✚ Add Your Items to Trade</bold></green>",
                        "es", "<green><bold>✚ Agregar tus Items al Trade</bold></green>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "add_items_button.lore",
                Map.of(
                        "en",
                        "<gray>Click to select the items</gray>\n<gray>you want to trade with</gray>\n<white><bold>%player%</bold></white>\n<green>• Secure transaction</green>\n<green>• Easy item selection</green>",
                        "es",
                        "<gray>Click para seleccionar los items</gray>\n<gray>que quieres tradear con</gray>\n<white><bold>%player%</bold></white>\n<green>• Transacción segura</green>\n<green>• Selección fácil de items</green>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "info_panel.title",
                Map.of(
                        "en", "<yellow><bold>📋 Trade Information</bold></yellow>",
                        "es", "<yellow><bold>📋 Información del Trade</bold></yellow>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "info_panel.viewing_items",
                Map.of(
                        "en", "<gray>Viewing items from:</gray> <white><bold>%player%</bold></white>",
                        "es", "<gray>Viendo items de:</gray> <white><bold>%player%</bold></white>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "info_panel.trade_id",
                Map.of(
                        "en", "<gray>Trade ID:</gray> <white><bold>%trade_id%</bold></white>",
                        "es", "<gray>ID del Trade:</gray> <white><bold>%trade_id%</bold></white>"));

        addMessage(MessageCategory.VIEW_TRADE_GUI, "info_panel.page",
                Map.of(
                        "en", "<gray>Page:</gray> <white><bold>%page%</bold></white>",
                        "es", "<gray>Página:</gray> <white><bold>%page%</bold></white>"));

        // ===========================================
        // GENERAL MESSAGES - System-wide messages
        // ===========================================
        addMessage(MessageCategory.GENERAL, "feature_not_implemented",
                Map.of(
                        "en",
                        "<yellow><bold>⚠ Feature Not Available</bold></yellow>\n<gray>This functionality is not yet implemented</gray>\n<gray>Please check back later for updates</gray>",
                        "es",
                        "<yellow><bold>⚠ Funcionalidad No Disponible</bold></yellow>\n<gray>Esta funcionalidad aún no está implementada</gray>\n<gray>Por favor vuelve más tarde para actualizaciones</gray>"));

        addMessage(MessageCategory.GENERAL, "invalid_trade_error",
                Map.of(
                        "en",
                        "<red><bold>⚠ Invalid Trade!</bold></red>\n<gray>This trade is no longer valid or has expired</gray>",
                        "es",
                        "<red><bold>⚠ ¡Trade Inválido!</bold></red>\n<gray>Este trade ya no es válido o ha expirado</gray>"));

        addMessage(MessageCategory.GENERAL, "verification_error",
                Map.of(
                        "en", "<red><bold>⚠ Verification Error!</bold></red>\n<gray>Error: %error%</gray>",
                        "es", "<red><bold>⚠ ¡Error de Verificación!</bold></red>\n<gray>Error: %error%</gray>"));

        // ===========================================
        // TRADE NOTIFICATIONS - Connection alerts
        // ===========================================
        addMessage(MessageCategory.TRADE_NOTIFICATIONS, "active_trades_alert",
                Map.of(
                        "en",
                        "<yellow><bold>🔔 Trade Notifications</bold></yellow>\n<gray>You have <green><bold>%count%</bold></green> active trade(s) waiting for you!\n<yellow>Use <white><bold>/mytrades</bold></white> to review them</yellow>",
                        "es",
                        "<yellow><bold>🔔 Notificaciones de Trade</bold></yellow>\n<gray>¡Tienes <green><bold>%count%</bold></green> trade(s) activo(s) esperándote!\n<yellow>Usa <white><bold>/mytrades</bold></white> para revisarlos</yellow>"));

        addMessage(MessageCategory.TRADE_NOTIFICATIONS, "pending_trades_alert",
                Map.of(
                        "en",
                        "<blue><bold>📬 Pending Trade Requests</bold></blue>\n<gray>You have <cyan><bold>%count%</bold></cyan> pending trade request(s)\n<blue>Use <white><bold>/mytrades</bold></white> to accept or decline them</blue>",
                        "es",
                        "<blue><bold>📬 Solicitudes de Trade Pendientes</bold></blue>\n<gray>Tienes <cyan><bold>%count%</bold></cyan> solicitud(es) de trade pendiente(s)\n<blue>Usa <white><bold>/mytrades</bold></white> para aceptarlas o rechazarlas</blue>"));

        addMessage(MessageCategory.TRADE_NOTIFICATIONS, "mixed_trades_alert",
                Map.of(
                        "en",
                        "<gold><bold>⚡ Trade Status Update</bold></gold>\n<gray>You have:</gray>\n<green>• <bold>%active%</bold> active trade(s)</green>\n<cyan>• <bold>%pending%</bold> pending request(s)</cyan>\n<gold>Use <white><bold>/mytrades</bold></white> to manage them all</gold>",
                        "es",
                        "<gold><bold>⚡ Actualización de Estado de Trades</bold></gold>\n<gray>Tienes:</gray>\n<green>• <bold>%active%</bold> trade(s) activo(s)</green>\n<cyan>• <bold>%pending%</bold> solicitud(es) pendiente(s)</cyan>\n<gold>Usa <white><bold>/mytrades</bold></white> para administrarlos todos</gold>"));

        addMessage(MessageCategory.TRADE_NOTIFICATIONS, "welcome_back_with_trades",
                Map.of(
                        "en",
                        "<rainbow><bold>Welcome back, %player%!</bold></rainbow>\n<gray>While you were away, your trading activity continued...</gray>",
                        "es",
                        "<rainbow><bold>¡Bienvenido de vuelta, %player%!</bold></rainbow>\n<gray>Mientras no estabas, tu actividad de trading continuó...</gray>"));

        addMessage(MessageCategory.TRADE_NOTIFICATIONS, "mytrades_button",
                Map.of(
                        "en", "<yellow><bold>[📋 CLICK TO VIEW MY TRADES]</bold></yellow>",
                        "es", "<yellow><bold>[📋 CLICK PARA VER MIS TRADES]</bold></yellow>"));

        // ===========================================
        // MYTRADES GUI - My Trades interface
        // ===========================================
        addMessage(MessageCategory.GENERAL, "mytrades.title",
                Map.of(
                        "en", "My Trades",
                        "es", "Mis Trades"));

        addMessage(MessageCategory.GENERAL, "mytrades.loading",
                Map.of(
                        "en", "<yellow>Loading your trades...</yellow>",
                        "es", "<yellow>Cargando tus trades...</yellow>"));

        addMessage(MessageCategory.GENERAL, "mytrades.error",
                Map.of(
                        "en", "<red>Error loading trades. Please try again.</red>",
                        "es", "<red>Error cargando trades. Por favor intenta de nuevo.</red>"));

        addMessage(MessageCategory.GENERAL, "mytrades.no_trades",
                Map.of(
                        "en", "<gray>You don't have any trades yet.</gray>",
                        "es", "<gray>No tienes ningún trade aún.</gray>"));

        // Filter names for MyTradesGUI
        addMessage(MessageCategory.GENERAL, "filter.all",
                Map.of(
                        "en", "All Trades",
                        "es", "Todos los Trades"));

        addMessage(MessageCategory.GENERAL, "filter.pending", 
                Map.of(
                        "en", "Pending",
                        "es", "Pendientes"));

        addMessage(MessageCategory.GENERAL, "filter.active",
                Map.of(
                        "en", "Active", 
                        "es", "Activos"));

        addMessage(MessageCategory.GENERAL, "filter.completed",
                Map.of(
                        "en", "Completed",
                        "es", "Completados"));

        addMessage(MessageCategory.GENERAL, "filter.cancelled",
                Map.of(
                        "en", "Cancelled",
                        "es", "Cancelados"));

        // Status translations for trade items
        addMessage(MessageCategory.GENERAL, "status.pending",
                Map.of(
                        "en", "Pending",
                        "es", "Pendiente"));

        addMessage(MessageCategory.GENERAL, "status.active",
                Map.of(
                        "en", "Active",
                        "es", "Activo"));

        addMessage(MessageCategory.GENERAL, "status.completed",
                Map.of(
                        "en", "Completed",
                        "es", "Completado"));

        addMessage(MessageCategory.GENERAL, "status.cancelled",
                Map.of(
                        "en", "Cancelled",
                        "es", "Cancelado"));

        // Action messages for MyTradesGUI
        addMessage(MessageCategory.GENERAL, "action.add_items",
                Map.of(
                        "en", "Click to add your items",
                        "es", "Click para agregar tus items"));

        addMessage(MessageCategory.GENERAL, "action.view_details",
                Map.of(
                        "en", "Click to view details",
                        "es", "Click para ver detalles"));

        addMessage(MessageCategory.GENERAL, "action.collect_items",
                Map.of(
                        "en", "Click to collect items",
                        "es", "Click para recoger items"));

        addMessage(MessageCategory.GENERAL, "action.cancelled_info",
                Map.of(
                        "en", "This trade was cancelled",
                        "es", "Este trade fue cancelado"));

        // Items status messages
        addMessage(MessageCategory.GENERAL, "items.not_added",
                Map.of(
                        "en", "No",
                        "es", "No"));

        addMessage(MessageCategory.GENERAL, "items.added",
                Map.of(
                        "en", "Yes",
                        "es", "Sí"));

        // Count suffixes for items received
        addMessage(MessageCategory.GENERAL, "items.count_suffix",
                Map.of(
                        "en", " items",
                        "es", " items"));

        addMessage(MessageCategory.GENERAL, "items.count_single",
                Map.of(
                        "en", " item",
                        "es", " item"));
    }

    /**
     * Helper method to add messages with category organization
     */
    private void addMessage(MessageCategory category, String key, Map<String, String> translations) {
        String fullKey = category.getPrefix() + "." + key;
        Message message = new Message(fullKey, translations);
        // We'll store this in our initialization map for later database insertion
        messageCache.put(fullKey, message);
    }

    private void loadMessages() {
        CompletableFuture.runAsync(() -> {
            try {
                // Procesar cada mensaje por defecto
                for (Map.Entry<String, Message> entry : messageCache.entrySet()) {
                    String key = entry.getKey();
                    try {
                        // Verificar si el mensaje ya existe
                        Document existingMessage = messagesCollection.find(Filters.eq("key", key)).first();

                        if (existingMessage == null) {
                            Document doc = Document.parse(entry.getValue().toDocument().toJson());
                            messagesCollection.insertOne(doc);
                            plugin.getLogger().info("Added default message: " + key);
                        } else {
                            messageCache.put(key, new Message(existingMessage));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing message " + key + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading messages: " + e.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Fatal error loading messages: " + throwable.getMessage());
            return null;
        });
    }

    public void sendMessage(Player player, String key, Object... replacements) {
        PlayerData playerData = plugin.getPlayerDataManager().getCachedPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        String language = playerData.getLanguage();
        Message message = messageCache.get(key);

        if (message == null) {
            plugin.getLogger().warning("Message key not found: " + key);
            return;
        }

        String text = message.getTranslation(language);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    text = text.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
                }
            }
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }

    /**
     * Send a message using Adventure Components with MiniMessage support
     */
    public void sendComponentMessage(Player player, String key, Object... replacements) {
        PlayerData playerData = plugin.getPlayerDataManager().getCachedPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        Component component = getComponent(key, playerData.getLanguage(), replacements);
        player.sendMessage(component);
    }

    /**
     * Get a Component with full formatting support
     * This is the main method for retrieving formatted messages
     */
    public Component getComponent(String key, String language, Object... replacements) {
        Message message = messageCache.get(key);

        if (message == null) {
            plugin.getLogger().warning("Message key not found: " + key);
            return Component.text("Message not found: " + key);
        }

        String text = message.getTranslation(language);
        text = applyReplacements(text, replacements);

        try {
            // Parse with MiniMessage for modern formatting
            return miniMessage.deserialize(text);
        } catch (Exception e) {
            // Fallback to legacy format
            try {
                return legacySerializer.deserialize(text);
            } catch (Exception e2) {
                // Ultimate fallback
                return Component.text(text);
            }
        }
    }

    /**
     * Get Component for a player using their language preference
     */
    public Component getComponent(Player player, String key, Object... replacements) {
        PlayerData playerData = plugin.getPlayerDataManager().getCachedPlayerData(player.getUniqueId());
        String language = playerData != null ? playerData.getLanguage() : "en";
        return getComponent(key, language, replacements);
    }

    /**
     * Apply replacements to message text
     */
    private String applyReplacements(String text, Object... replacements) {
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    String placeholder = "%" + replacements[i] + "%";
                    String replacement = String.valueOf(replacements[i + 1]);
                    text = text.replace(placeholder, replacement);
                }
            }
        }
        return text;
    }

    /**
     * Get raw message text (for compatibility)
     */
    public String getRawMessage(Player player, String key, Object... replacements) {
        PlayerData playerData = plugin.getPlayerDataManager().getCachedPlayerData(player.getUniqueId());
        String language = playerData != null ? playerData.getLanguage() : "en";
        return getRawMessage(key, language, replacements);
    }

    /**
     * Get raw message text by language
     */
    public String getRawMessage(String key, String language, Object... replacements) {
        Message message = messageCache.get(key);

        if (message == null) {
            plugin.getLogger().warning("Message key not found: " + key);
            return "Message not found: " + key;
        }

        String text = message.getTranslation(language);
        return applyReplacements(text, replacements);
    }

    /**
     * Get all message categories for in-game editing
     */
    public Map<String, MessageCategory> getMessageCategories() {
        return new HashMap<>(messageCategories);
    }

    /**
     * Get messages by category for in-game editing
     */
    public Map<String, Message> getMessagesByCategory(MessageCategory category) {
        Map<String, Message> categoryMessages = new HashMap<>();
        String prefix = category.getPrefix() + ".";

        for (Map.Entry<String, Message> entry : messageCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                categoryMessages.put(entry.getKey(), entry.getValue());
            }
        }

        return categoryMessages;
    }

    /**
     * Update a message (for in-game editing)
     */
    public CompletableFuture<Boolean> updateMessage(String key, String language, String newText) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = messageCache.get(key);
                if (message == null) {
                    return false;
                }

                // Update in cache
                Map<String, String> translations = message.getTranslations();
                translations.put(language, newText);
                Message updatedMessage = new Message(key, translations);
                messageCache.put(key, updatedMessage);

                // Update in database
                Document filter = new Document("key", key);
                Document update = new Document("$set", new Document("translations." + language, newText));
                messagesCollection.updateOne(filter, update);

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error updating message " + key + ": " + e.getMessage());
                return false;
            }
        });
    }

    public void reloadMessages() {
        messageCache.clear();
        loadMessages();
    }
}