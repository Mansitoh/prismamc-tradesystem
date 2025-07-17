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
                MYTRADES_GUI("mytrades", "MyTrades GUI Messages"),
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
                                                "es",
                                                "<green>Solicitud de trade recibida de <yellow>%player%</yellow>"));

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

                addMessage(MessageCategory.TRADE_ERRORS, "player_never_connected",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Player Not Found</bold></red>\n<gray>The player <white><bold>%player%</bold></white> has never connected to this server</gray>\n<gray>Please check the spelling and try again</gray>",
                                                "es",
                                                "<red><bold>⚠ Jugador No Encontrado</bold></red>\n<gray>El jugador <white><bold>%player%</bold></white> nunca se conectó a este servidor</gray>\n<gray>Por favor verifica la escritura e intenta de nuevo</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "player_offline",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Player Offline</bold></yellow>\n<gray>The player <white><bold>%player%</bold></white> is currently offline</gray>\n<gray>You can only trade with online players</gray>",
                                                "es",
                                                "<yellow><bold>⚠ Jugador Desconectado</bold></yellow>\n<gray>El jugador <white><bold>%player%</bold></white> está desconectado actualmente</gray>\n<gray>Solo puedes tradear con jugadores conectados</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "cooldown",
                                Map.of(
                                                "en",
                                                "<red>Please wait <yellow>%seconds%</yellow> seconds before trading again!",
                                                "es",
                                                "<red>¡Espera <yellow>%seconds%</yellow> segundos antes de tradear de nuevo!"));

                addMessage(MessageCategory.TRADE_ERRORS, "usage",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Invalid Usage</bold></red>\n<gray>Correct usage:</gray> <white><bold>/trade <player></bold></white>\n<gray>Example:</gray> <yellow>/trade Steve</yellow>",
                                                "es",
                                                "<red><bold>⚠ Uso Incorrecto</bold></red>\n<gray>Uso correcto:</gray> <white><bold>/trade <jugador></bold></white>\n<gray>Ejemplo:</gray> <yellow>/trade Steve</yellow>"));

                addMessage(MessageCategory.TRADE_ERRORS, "verification_error",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ System Error</bold></red>\n<gray>An error occurred while processing your request:</gray>\n<white>%error%</white>\n<gray>Please try again later</gray>",
                                                "es",
                                                "<red><bold>⚠ Error del Sistema</bold></red>\n<gray>Ocurrió un error al procesar tu solicitud:</gray>\n<white>%error%</white>\n<gray>Por favor intenta más tarde</gray>"));

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

                // Mensaje para cuando el jugador recibe items al completar un trade
                addMessage(MessageCategory.TRADE_SUCCESS, "completion.items_received",
                                Map.of(
                                                "en",
                                                "<green><bold>🎉 Items Received Successfully!</bold></green>\n<gray>You have received the items from <white><bold>%player%</bold></white></gray>\n<yellow>Trade <white><bold>#%trade_id%</bold></white> completed successfully!</yellow>",
                                                "es",
                                                "<green><bold>🎉 ¡Items Recibidos Exitosamente!</bold></green>\n<gray>Has recibido los items de <white><bold>%player%</bold></white></gray>\n<yellow>¡Trade <white><bold>#%trade_id%</bold></white> completado exitosamente!</yellow>"));

                // ===========================================
                // PRETRADE NOTIFICATIONS - System notifications
                // ===========================================
                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "items_added",
                                Map.of(
                                                "en",
                                                "<white><bold>%player%</bold></white> <green>has added their items to the trade!</green>\n<yellow>The trade is now ready for confirmation</yellow>",
                                                "es",
                                                "<white><bold>%player%</bold></white> <green>ha agregado sus items al trade!</green>\n<yellow>El trade está listo para confirmación</yellow>"));

                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "items_sent",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ Items Secured!</bold></green>\n<gray>Your items have been secured for the trade</gray>\n<yellow>Waiting for the other player to respond...</yellow>",
                                                "es",
                                                "<green><bold>✅ ¡Items Asegurados!</bold></green>\n<gray>Tus items han sido asegurados para el trade</gray>\n<yellow>Esperando que el otro jugador responda...</yellow>"));

                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "items_sent_to",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ Trade Request Sent to %player%!</bold></green>\n<gray>Your items have been secured</gray>\n<yellow>They will be notified about your trade request</yellow>",
                                                "es",
                                                "<green><bold>✅ ¡Solicitud de Trade Enviada a %player%!</bold></green>\n<gray>Tus items han sido asegurados</gray>\n<yellow>Serán notificados sobre tu solicitud de trade</yellow>"));

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

                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_accepted",
                                Map.of(
                                                "en",
                                                "<green><bold>🎉 %player% accepted your trade!</bold></green>\n<yellow>Click the confirmation button to finalize</yellow>",
                                                "es",
                                                "<green><bold>🎉 ¡%player% aceptó tu trade!</bold></green>\n<yellow>Haz click en el botón de confirmación para finalizar</yellow>"));

                // Mensajes adicionales de notificaciones para PreTrade
                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_request_sent",
                                Map.of(
                                                "en",
                                                "<green><bold>📤 Trade Request Sent!</bold></green>\n<gray>Your trade request has been sent to <white><bold>%player%</bold></white></gray>\n<yellow>Trade ID: <white><bold>#%trade_id%</bold></white></yellow>\n<gray>They will be notified when they come online</gray>",
                                                "es",
                                                "<green><bold>📤 ¡Solicitud de Trade Enviada!</bold></green>\n<gray>Tu solicitud de trade ha sido enviada a <white><bold>%player%</bold></white></gray>\n<yellow>ID del Trade: <white><bold>#%trade_id%</bold></white></yellow>\n<gray>Serán notificados cuando se conecten</gray>"));

                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_sent_offline",
                                Map.of(
                                                "en",
                                                "<yellow><bold>📴 Player Currently Offline</bold></yellow>\n<gray>Your trade request has been sent to <white><bold>%player%</bold></white></gray>\n<gray>They will receive the notification when they come online</gray>\n<green>Your items are secured and the trade is active</green>",
                                                "es",
                                                "<yellow><bold>📴 Jugador Actualmente Desconectado</bold></yellow>\n<gray>Tu solicitud de trade ha sido enviada a <white><bold>%player%</bold></white></gray>\n<gray>Recibirán la notificación cuando se conecten</gray>\n<green>Tus items están asegurados y el trade está activo</green>"));

                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "target_offline",
                                Map.of(
                                                "en",
                                                "<blue><bold>ℹ Player Status Update</bold></blue>\n<gray>The player <white><bold>%player%</bold></white> is currently offline</gray>\n<gray>They will be notified about your trade when they return</gray>",
                                                "es",
                                                "<blue><bold>ℹ Actualización de Estado del Jugador</bold></blue>\n<gray>El jugador <white><bold>%player%</bold></white> está actualmente desconectado</gray>\n<gray>Serán notificados sobre tu trade cuando regresen</gray>"));

                // Mensaje específico para notificación de trade aceptado (diferente sintaxis)
                addMessage(MessageCategory.PRETRADE_NOTIFICATIONS, "trade_accepted",
                                Map.of(
                                                "en",
                                                "<green><bold>🎉 Trade Accepted!</bold></green>\n<gray><white><bold>%player%</bold></white> has accepted your trade request</gray>\n<yellow>Both players can now proceed to confirmation</yellow>",
                                                "es",
                                                "<green><bold>🎉 ¡Trade Aceptado!</bold></green>\n<gray><white><bold>%player%</bold></white> ha aceptado tu solicitud de trade</gray>\n<yellow>Ambos jugadores pueden proceder a la confirmación</yellow>"));

                // ===========================================
                // PRETRADE GUI - PreTrade GUI Elements (titles, labels, etc.)
                // ===========================================
                addMessage(MessageCategory.PRETRADE_GUI, "title.new",
                                Map.of(
                                                "en", "Trade Request - Select Items for %player%",
                                                "es", "Solicitud de Trade - Selecciona Items para %player%"));

                addMessage(MessageCategory.PRETRADE_GUI, "title.response",
                                Map.of(
                                                "en", "Responding to Trade from %player%",
                                                "es", "Respondiendo al Trade de %player%"));

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

                addMessage(MessageCategory.PRETRADE_BUTTONS, "view_trade_with_sender",
                                Map.of(
                                                "en",
                                                "<yellow><bold>📬 %player% sent you a trade!</bold></yellow>\n<gray>They want to exchange some items with you</gray>\n\n<green><bold>[📦 CLICK TO VIEW WHAT THEY'RE OFFERING]</bold></green>\n<gray>Click here to see their items and decide if you want to accept the trade</gray>",
                                                "es",
                                                "<yellow><bold>📬 ¡%player% te envió un trade!</bold></yellow>\n<gray>Quieren intercambiar algunos items contigo</gray>\n\n<green><bold>[📦 CLICK PARA VER LO QUE ESTÁN OFRECIENDO]</bold></green>\n<gray>Haz click aquí para ver sus items y decidir si quieres aceptar el trade</gray>"));

                addMessage(MessageCategory.PRETRADE_BUTTONS, "confirm_trade",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ CONFIRM TRADE</bold></green>\n<gray>Click to confirm and proceed with this trade</gray>\n<yellow>• Final step to complete the exchange</yellow>\n<yellow>• Make sure you're happy with the items</yellow>",
                                                "es",
                                                "<green><bold>✅ CONFIRMAR TRADE</bold></green>\n<gray>Click para confirmar y proceder con este trade</gray>\n<yellow>• Paso final para completar el intercambio</yellow>\n<yellow>• Asegúrate de estar conforme con los items</yellow>"));

                // Mensaje específico para botón confirm_trade (diferente de
                // buttons.confirm_trade)
                addMessage(MessageCategory.PRETRADE_BUTTONS, "button.confirm_trade",
                                Map.of(
                                                "en",
                                                "<green><bold>[🎯 CLICK TO CONFIRM & FINALIZE TRADE]</bold></green>\n<gray>• Review both sides of the trade</gray>\n<gray>• Complete the final confirmation</gray>\n<yellow>Click to proceed to the confirmation screen!</yellow>",
                                                "es",
                                                "<green><bold>[🎯 CLICK PARA CONFIRMAR Y FINALIZAR TRADE]</bold></green>\n<gray>• Revisa ambos lados del trade</gray>\n<gray>• Completa la confirmación final</gray>\n<yellow>¡Haz click para proceder a la pantalla de confirmación!</yellow>"));

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
                                                "en",
                                                "<gray>Viewing items from:</gray> <white><bold>%player%</bold></white>",
                                                "es",
                                                "<gray>Viendo items de:</gray> <white><bold>%player%</bold></white>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "info_panel.trade_id",
                                Map.of(
                                                "en", "<gray>Trade ID:</gray> <white><bold>%trade_id%</bold></white>",
                                                "es",
                                                "<gray>ID del Trade:</gray> <white><bold>%trade_id%</bold></white>"));

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
                                                "en",
                                                "<red><bold>⚠ Verification Error!</bold></red>\n<gray>Error: %error%</gray>",
                                                "es",
                                                "<red><bold>⚠ ¡Error de Verificación!</bold></red>\n<gray>Error: %error%</gray>"));

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
                // MYTRADES GUI - MyTrades interface messages
                // ===========================================
                addMessage(MessageCategory.MYTRADES_GUI, "pending.already_added_items",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Items Already Added</bold></yellow>\n<gray>You have already added your items to this trade.</gray>\n<gray>Waiting for the other player to complete their part.</gray>",
                                                "es",
                                                "<yellow><bold>⚠ Items Ya Agregados</bold></yellow>\n<gray>Ya has agregado tus items a este trade.</gray>\n<gray>Esperando que el otro jugador complete su parte.</gray>"));

                addMessage(MessageCategory.MYTRADES_GUI, "error.loading_trade_items",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Loading Trade</bold></red>\n<gray>Could not load the items for this trade.</gray>\n<gray>Please try again later.</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Cargando Trade</bold></red>\n<gray>No se pudieron cargar los items de este trade.</gray>\n<gray>Por favor intenta más tarde.</gray>"));

                addMessage(MessageCategory.MYTRADES_GUI, "completed.inventory_full",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Inventory Full!</bold></yellow>\n<gray>Your inventory is full! Some items were dropped on the ground.</gray>",
                                                "es",
                                                "<yellow><bold>⚠ ¡Inventario Lleno!</bold></yellow>\n<gray>¡Tu inventario está lleno! Algunos items fueron dropeados al suelo.</gray>"));

                addMessage(MessageCategory.MYTRADES_GUI, "completed.items_received",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ Items Received!</bold></green>\n<gray>You have successfully received the items from trade <white><bold>#%trade_id%</bold></white></gray>\n<yellow>Trade completed successfully!</yellow>",
                                                "es",
                                                "<green><bold>✅ ¡Items Recibidos!</bold></green>\n<gray>Has recibido exitosamente los items del trade <white><bold>#%trade_id%</bold></white></gray>\n<yellow>¡Trade completado exitosamente!</yellow>"));

                addMessage(MessageCategory.MYTRADES_GUI, "completed.already_received",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Items Already Received</bold></yellow>\n<gray>You have already received the items from trade <white><bold>#%trade_id%</bold></white></gray>\n<gray>This trade was completed previously</gray>",
                                                "es",
                                                "<yellow><bold>⚠ Items Ya Recibidos</bold></yellow>\n<gray>Ya has recibido los items del trade <white><bold>#%trade_id%</bold></white></gray>\n<gray>Este trade fue completado anteriormente</gray>"));

                addMessage(MessageCategory.MYTRADES_GUI, "error.giving_trade_items",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Receiving Items</bold></red>\n<gray>There was an error while trying to give you the trade items.</gray>\n<gray>Please contact an administrator.</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Recibiendo Items</bold></red>\n<gray>Hubo un error al intentar darte los items del trade.</gray>\n<gray>Por favor contacta a un administrador.</gray>"));

                // ===========================================
                // VIEWTRADE CONFIRMATION MESSAGES - Mensajes específicos para ViewTradeGUI
                // ===========================================
                addMessage(MessageCategory.VIEW_TRADE_GUI, "confirmation.waiting_for_other",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⏳ Waiting for Other Player</bold></yellow>\n<gray>You have confirmed this trade</gray>\n<gray>Waiting for the other player to confirm</gray>",
                                                "es",
                                                "<yellow><bold>⏳ Esperando al Otro Jugador</bold></yellow>\n<gray>Has confirmado este trade</gray>\n<gray>Esperando que el otro jugador confirme</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "confirmation.already_confirmed",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Already Confirmed</bold></yellow>\n<gray>You have already confirmed this trade</gray>\n<gray>Waiting for the other player</gray>",
                                                "es",
                                                "<yellow><bold>⚠ Ya Confirmado</bold></yellow>\n<gray>Ya has confirmado este trade</gray>\n<gray>Esperando al otro jugador</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "confirmation.player_confirmed",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ Player Confirmed!</bold></green>\n<gray><bold>%player%</bold> has confirmed the trade</gray>\n<yellow>You can now confirm to complete it</yellow>",
                                                "es",
                                                "<green><bold>✅ ¡Jugador Confirmado!</bold></green>\n<gray><bold>%player%</bold> ha confirmado el trade</gray>\n<yellow>Ahora puedes confirmar para completarlo</yellow>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "completion.success",
                                Map.of(
                                                "en",
                                                "<green><bold>🎉 Trade Completed Successfully!</bold></green>\n<gray>Trade <white><bold>#%trade_id%</bold></white> has been completed</gray>\n<yellow>Items have been exchanged</yellow>",
                                                "es",
                                                "<green><bold>🎉 ¡Trade Completado Exitosamente!</bold></green>\n<gray>El trade <white><bold>#%trade_id%</bold></white> ha sido completado</gray>\n<yellow>Los items han sido intercambiados</yellow>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "error.completion_failed",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Trade Completion Failed</bold></red>\n<gray>There was an error completing the trade</gray>\n<gray>Please try again or contact an administrator</gray>",
                                                "es",
                                                "<red><bold>⚠ Error al Completar Trade</bold></red>\n<gray>Hubo un error al completar el trade</gray>\n<gray>Por favor intenta de nuevo o contacta un administrador</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "error.confirmation_failed",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Confirmation Failed</bold></red>\n<gray>Could not confirm your participation in the trade</gray>\n<gray>Please try again</gray>",
                                                "es",
                                                "<red><bold>⚠ Error en Confirmación</bold></red>\n<gray>No se pudo confirmar tu participación en el trade</gray>\n<gray>Por favor intenta de nuevo</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "error.verification_failed",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Verification Failed</bold></red>\n<gray>Could not verify trade status</gray>\n<gray>Please try again later</gray>",
                                                "es",
                                                "<red><bold>⚠ Error de Verificación</bold></red>\n<gray>No se pudo verificar el estado del trade</gray>\n<gray>Por favor intenta más tarde</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "completion.items_available_mytrades",
                                Map.of(
                                                "en",
                                                "<green><bold>🎉 Trade Completed Successfully!</bold></green>\n<gray><white><bold>%player%</bold></white> has confirmed the trade</gray>\n<yellow>Your items are ready for pickup!</yellow>\n<green>Use <white><bold>/mytrades</bold></white> to collect your items</green>",
                                                "es",
                                                "<green><bold>🎉 ¡Trade Completado Exitosamente!</bold></green>\n<gray><white><bold>%player%</bold></white> ha confirmado el trade</gray>\n<yellow>¡Tus items están listos para recoger!</yellow>\n<green>Usa <white><bold>/mytrades</bold></white> para recoger tus items</green>"));

                // ===========================================
                // TRADECONFIRM COMMAND - Mensajes específicos para /tradeconfirm
                // ===========================================
                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.usage",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Invalid Usage</bold></red>\n<gray>Correct usage:</gray> <white><bold>/tradeconfirm <player> <tradeId></bold></white>\n<gray>Example:</gray> <yellow>/tradeconfirm Steve 12345</yellow>",
                                                "es",
                                                "<red><bold>⚠ Uso Incorrecto</bold></red>\n<gray>Uso correcto:</gray> <white><bold>/tradeconfirm <jugador> <tradeId></bold></white>\n<gray>Ejemplo:</gray> <yellow>/tradeconfirm Steve 12345</yellow>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.invalid_trade_id",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Invalid Trade ID</bold></red>\n<gray>The trade ID must be a valid number</gray>\n<gray>Please check the ID and try again</gray>",
                                                "es",
                                                "<red><bold>⚠ ID de Trade Inválido</bold></red>\n<gray>El ID del trade debe ser un número válido</gray>\n<gray>Por favor verifica el ID e intenta de nuevo</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.trade_not_found",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Trade Not Found</bold></red>\n<gray>Trade with ID <white><bold>#%trade_id%</bold></white> was not found</gray>\n<gray>Please verify the trade ID is correct</gray>",
                                                "es",
                                                "<red><bold>⚠ Trade No Encontrado</bold></red>\n<gray>El trade con ID <white><bold>#%trade_id%</bold></white> no fue encontrado</gray>\n<gray>Por favor verifica que el ID del trade sea correcto</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.not_part_of_trade",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Access Denied</bold></red>\n<gray>You are not part of this trade</gray>\n<gray>You can only confirm trades you are involved in</gray>",
                                                "es",
                                                "<red><bold>⚠ Acceso Denegado</bold></red>\n<gray>No eres parte de este trade</gray>\n<gray>Solo puedes confirmar trades en los que estás involucrado</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.other_player_not_part",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Player Mismatch</bold></red>\n<gray>The player <white><bold>%player%</bold></white> is not part of this trade</gray>\n<gray>Please verify the player name and trade ID</gray>",
                                                "es",
                                                "<red><bold>⚠ Jugador Incorrecto</bold></red>\n<gray>El jugador <white><bold>%player%</bold></white> no es parte de este trade</gray>\n<gray>Por favor verifica el nombre del jugador y el ID del trade</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.trade_not_ready",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Trade Not Ready</bold></red>\n<gray>This trade is not ready for confirmation</gray>\n<gray>Current state: <white><bold>%state%</bold></white></gray>\n<yellow>Trade must be in ACTIVE state to confirm</yellow>",
                                                "es",
                                                "<red><bold>⚠ Trade No Listo</bold></red>\n<gray>Este trade no está listo para confirmación</gray>\n<gray>Estado actual: <white><bold>%state%</bold></white></gray>\n<yellow>El trade debe estar en estado ACTIVE para confirmar</yellow>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.no_items_to_view",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ No Items Found</bold></red>\n<gray>Player <white><bold>%player%</bold></white> has no items in this trade</gray>\n<gray>There's nothing to view or confirm</gray>",
                                                "es",
                                                "<red><bold>⚠ No Se Encontraron Items</bold></red>\n<gray>El jugador <white><bold>%player%</bold></white> no tiene items en este trade</gray>\n<gray>No hay nada que ver o confirmar</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.loading_items",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Loading Items</bold></red>\n<gray>Could not load the trade items</gray>\n<gray>Please try again later</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Cargando Items</bold></red>\n<gray>No se pudieron cargar los items del trade</gray>\n<gray>Por favor intenta más tarde</gray>"));

                addMessage(MessageCategory.TRADE_ERRORS, "tradeconfirm.error.loading_trade",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Loading Trade</bold></red>\n<gray>Could not load trade information</gray>\n<gray>Please try again later</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Cargando Trade</bold></red>\n<gray>No se pudo cargar la información del trade</gray>\n<gray>Por favor intenta más tarde</gray>"));

                // ===========================================
                // VIEWTRADE CANCELLATION MESSAGES - Mensajes para cancelación de trades
                // ===========================================
                addMessage(MessageCategory.VIEW_TRADE_GUI, "cancellation.trade_cancelled",
                                Map.of(
                                                "en",
                                                "<green><bold>✅ Trade Cancelled Successfully</bold></green>\n<gray>Trade <white><bold>#%trade_id%</bold></white> has been cancelled</gray>\n<yellow>Your items have been returned to your inventory</yellow>",
                                                "es",
                                                "<green><bold>✅ Trade Cancelado Exitosamente</bold></green>\n<gray>El trade <white><bold>#%trade_id%</bold></white> ha sido cancelado</gray>\n<yellow>Tus items han sido devueltos a tu inventario</yellow>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "cancellation.error_retrieving_items",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Retrieving Items</bold></red>\n<gray>Could not retrieve your items from the cancelled trade</gray>\n<gray>Please contact an administrator</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Recuperando Items</bold></red>\n<gray>No se pudieron recuperar tus items del trade cancelado</gray>\n<gray>Por favor contacta a un administrador</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "cancellation.error_cancelling",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Cancelling Trade</bold></red>\n<gray>Could not cancel the trade</gray>\n<gray>Please try again or contact an administrator</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Cancelando Trade</bold></red>\n<gray>No se pudo cancelar el trade</gray>\n<gray>Por favor intenta de nuevo o contacta a un administrador</gray>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "cancellation.other_player_cancelled_with_mytrades",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Trade Cancelled</bold></yellow>\n<gray>Player <white><bold>%player%</bold></white> has cancelled trade <white><bold>#%trade_id%</bold></white></gray>\n<green>Your items are safe and can be retrieved via /mytrades</green>",
                                                "es",
                                                "<yellow><bold>⚠ Trade Cancelado</bold></yellow>\n<gray>El jugador <white><bold>%player%</bold></white> ha cancelado el trade <white><bold>#%trade_id%</bold></white></gray>\n<green>Tus items están seguros y pueden ser recuperados via /mytrades</green>"));

                addMessage(MessageCategory.VIEW_TRADE_GUI, "cancellation.mytrades_button",
                                Map.of(
                                                "en",
                                                "<yellow><bold>[📋 CLICK TO GO TO /MYTRADES]</bold></yellow>",
                                                "es",
                                                "<yellow><bold>[📋 CLICK PARA IR A /MYTRADES]</bold></yellow>"));

                // ===========================================
                // MENSAJES FALTANTES ESPECÍFICOS - Agregados para resolver errores
                // ===========================================

                // Mensaje para mytrades.error.updating_item_status
                addMessage(MessageCategory.MYTRADES_GUI, "error.updating_item_status",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Updating Item Status</bold></red>\n<gray>Could not update the item received status</gray>\n<gray>Please try again later</gray>",
                                                "es",
                                                "<red><bold>⚠ Error Actualizando Estado de Items</bold></red>\n<gray>No se pudo actualizar el estado de items recibidos</gray>\n<gray>Por favor intenta más tarde</gray>"));

                // Mensaje para pretrade.error.no_items
                addMessage(MessageCategory.PRETRADE_ERRORS, "error.no_items",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ No Items Selected</bold></red>\n<gray>You must select at least one item to trade</gray>\n<yellow>Add items to your trade before confirming</yellow>",
                                                "es",
                                                "<red><bold>⚠ No Hay Items Seleccionados</bold></red>\n<gray>Debes seleccionar al menos un item para tradear</gray>\n<yellow>Agrega items a tu trade antes de confirmar</yellow>"));

                // Mensaje para pretrade.error.verify_trade
                addMessage(MessageCategory.PRETRADE_ERRORS, "error.verify_trade",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Trade Verification Error</bold></red>\n<gray>Error details: <white>%error%</white></gray>\n<gray>Please try again or contact an administrator</gray>",
                                                "es",
                                                "<red><bold>⚠ Error de Verificación de Trade</bold></red>\n<gray>Detalles del error: <white>%error%</white></gray>\n<gray>Por favor intenta de nuevo o contacta a un administrador</gray>"));

                // ===========================================
                // GENERAL COMMANDS - Mensajes para comandos generales
                // ===========================================
                addMessage(MessageCategory.GENERAL, "command_players_only",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Players Only</bold></red>\n<gray>This command can only be used by players</gray>",
                                                "es",
                                                "<red><bold>⚠ Solo Jugadores</bold></red>\n<gray>Este comando solo puede ser usado por jugadores</gray>"));

                // ===========================================
                // ADMIN COMMANDS - Mensajes para comandos de administración
                // ===========================================
                addMessage(MessageCategory.GENERAL, "admin.errors.no_permission",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Insufficient Permissions</bold></red>\n<gray>You do not have permission to use this command</gray>\n<gray>Required permission: <white>prismamc.trade.admin.viewtrades</white></gray>",
                                                "es",
                                                "<red><bold>⚠ Permisos Insuficientes</bold></red>\n<gray>No tienes permisos para usar este comando</gray>\n<gray>Permiso requerido: <white>prismamc.trade.admin.viewtrades</white></gray>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.usage",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Invalid Usage</bold></red>\n<gray>Correct usage:</gray> <white><bold>/viewtrades <player></bold></white>\n<gray>Example:</gray> <yellow>/viewtrades Steve</yellow>\n<gray>Aliases:</gray> <yellow>/vt, /admintrades</yellow>",
                                                "es",
                                                "<red><bold>⚠ Uso Incorrecto</bold></red>\n<gray>Uso correcto:</gray> <white><bold>/viewtrades <jugador></bold></white>\n<gray>Ejemplo:</gray> <yellow>/viewtrades Steve</yellow>\n<gray>Aliases:</gray> <yellow>/vt, /admintrades</yellow>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.player_not_found",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Player Not Found</bold></red>\n<gray>The player <white><bold>%player%</bold></white> was not found in the database</gray>\n<gray>This player may have never joined the server</gray>\n<yellow>Please verify the spelling and try again</yellow>",
                                                "es",
                                                "<red><bold>⚠ Jugador No Encontrado</bold></red>\n<gray>El jugador <white><bold>%player%</bold></white> no fue encontrado en la base de datos</gray>\n<gray>Este jugador puede que nunca se haya unido al servidor</gray>\n<yellow>Por favor verifica la escritura e intenta de nuevo</yellow>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.use_mytrades",
                                Map.of(
                                                "en",
                                                "<yellow><bold>⚠ Use Personal Command</bold></yellow>\n<gray>You are trying to view your own trades</gray>\n<green>Use <white><bold>/mytrades</bold></white> instead for your personal trades</green>\n<gray>The /viewtrades command is for viewing other players' trades</gray>",
                                                "es",
                                                "<yellow><bold>⚠ Usa Comando Personal</bold></yellow>\n<gray>Estás intentando ver tus propios trades</gray>\n<green>Usa <white><bold>/mytrades</bold></white> en su lugar para tus trades personales</green>\n<gray>El comando /viewtrades es para ver trades de otros jugadores</gray>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.opening_view",
                                Map.of(
                                                "en",
                                                "<green><bold>📋 Opening Admin View</bold></green>\n<gray>Loading trades for player: <white><bold>%player%</bold></white></gray>\n<yellow>Please wait while the data is being retrieved...</yellow>",
                                                "es",
                                                "<green><bold>📋 Abriendo Vista de Admin</bold></green>\n<gray>Cargando trades para el jugador: <white><bold>%player%</bold></white></gray>\n<yellow>Por favor espera mientras se recuperan los datos...</yellow>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.error_loading",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Loading Player Data</bold></red>\n<gray>Could not load trade data for the specified player</gray>\n<gray>Error details: <white>%error%</white></gray>\n<yellow>Please try again later or contact a developer</yellow>",
                                                "es",
                                                "<red><bold>⚠ Error Cargando Datos del Jugador</bold></red>\n<gray>No se pudieron cargar los datos de trade del jugador especificado</gray>\n<gray>Detalles del error: <white>%error%</white></gray>\n<yellow>Por favor intenta más tarde o contacta a un desarrollador</yellow>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.viewing_trade",
                                Map.of(
                                                "en",
                                                "<blue><bold>👁️ Viewing Trade Details</bold></blue>\n<gray>Trade ID: <white><bold>#%trade_id%</bold></white></gray>\n<gray>Target Player: <white><bold>%target_player%</bold></white></gray>\n<gray>Other Player: <white><bold>%other_player%</bold></white></gray>\n<gray>Trade State: <white><bold>%state%</bold></white></gray>\n<yellow>Opening detailed view...</yellow>",
                                                "es",
                                                "<blue><bold>👁️ Viendo Detalles del Trade</bold></blue>\n<gray>ID del Trade: <white><bold>#%trade_id%</bold></white></gray>\n<gray>Jugador Objetivo: <white><bold>%target_player%</bold></white></gray>\n<gray>Otro Jugador: <white><bold>%other_player%</bold></white></gray>\n<gray>Estado del Trade: <white><bold>%state%</bold></white></gray>\n<yellow>Abriendo vista detallada...</yellow>"));

                addMessage(MessageCategory.GENERAL, "admin.viewtrades.error_viewing_trade",
                                Map.of(
                                                "en",
                                                "<red><bold>⚠ Error Viewing Trade</bold></red>\n<gray>Could not load the trade details for viewing</gray>\n<gray>Error details: <white>%error%</white></gray>\n<yellow>Please try again or contact a developer</yellow>",
                                                "es",
                                                "<red><bold>⚠ Error Viendo Trade</bold></red>\n<gray>No se pudieron cargar los detalles del trade para visualización</gray>\n<gray>Detalles del error: <white>%error%</white></gray>\n<yellow>Por favor intenta de nuevo o contacta a un desarrollador</yellow>"));
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
                                                Document existingMessage = messagesCollection
                                                                .find(Filters.eq("key", key)).first();

                                                if (existingMessage == null) {
                                                        Document doc = Document
                                                                        .parse(entry.getValue().toDocument().toJson());
                                                        messagesCollection.insertOne(doc);
                                                        plugin.getLogger().info("Added default message: " + key);
                                                } else {
                                                        messageCache.put(key, new Message(existingMessage));
                                                }
                                        } catch (Exception e) {
                                                plugin.getLogger().warning("Error processing message " + key + ": "
                                                                + e.getMessage());
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
                                        text = text.replace("%" + replacements[i] + "%",
                                                        String.valueOf(replacements[i + 1]));
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
                                Document update = new Document("$set",
                                                new Document("translations." + language, newText));
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