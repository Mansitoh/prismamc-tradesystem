package com.prismamc.trade.listeners;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.manager.TradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

public class PlayerJoinListener implements Listener {
    private final Plugin plugin;

    public PlayerJoinListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Load or create player data and update name if needed
        plugin.getPlayerDataManager().loadPlayerData(player)
                .thenAccept(playerData -> {
                    // Check if name has changed
                    if (!playerData.getPlayerName().equals(player.getName())) {
                        playerData.setPlayerName(player.getName());
                        plugin.getPlayerDataManager().savePlayerData(playerData)
                                .exceptionally(throwable -> {
                                    plugin.getLogger().log(Level.SEVERE, "Error saving player data for {0}: {1}",
                                            new Object[] { player.getName(), throwable.getMessage() });
                                    return null;
                                });
                    }

                    // Check for pending/active trades and send notifications
                    checkAndNotifyTrades(player);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Error loading player data for {0}: {1}",
                            new Object[] { player.getName(), throwable.getMessage() });
                    return null;
                });
    }

    /**
     * Check for pending and active trades and send beautiful notifications
     */
    private void checkAndNotifyTrades(Player player) {
        // Small delay to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getTradeManager().getPlayerTradeNotifications(player.getUniqueId())
                    .thenAccept(notifications -> {
                        if (!notifications.hasAnyTrades()) {
                            return; // No trades to notify about
                        }

                        // Send notifications on main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            sendTradeNotifications(player, notifications);
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING, "Error checking trades for {0}: {1}",
                                new Object[] { player.getName(), throwable.getMessage() });
                        return null;
                    });
        }, 40L); // 2 seconds delay
    }

    /**
     * Send beautiful, contextualized trade notifications
     */
    private void sendTradeNotifications(Player player, TradeManager.TradeNotificationData notifications) {
        // Send welcome back message first
        plugin.getMessageManager().sendComponentMessage(player, "trade.notifications.welcome_back_with_trades",
                "player", player.getName());

        // Send empty line for spacing
        player.sendMessage(Component.empty());

        // Send appropriate notification based on trade types
        if (notifications.hasMixed()) {
            // Player has both pending and active trades
            plugin.getMessageManager().sendComponentMessage(player, "trade.notifications.mixed_trades_alert",
                    "active", String.valueOf(notifications.getActiveCount()),
                    "pending", String.valueOf(notifications.getPendingCount()));
        } else if (notifications.hasActiveOnly()) {
            // Player has only active trades
            plugin.getMessageManager().sendComponentMessage(player, "trade.notifications.active_trades_alert",
                    "count", String.valueOf(notifications.getActiveCount()));
        } else if (notifications.hasPendingOnly()) {
            // Player has only pending trades
            plugin.getMessageManager().sendComponentMessage(player, "trade.notifications.pending_trades_alert",
                    "count", String.valueOf(notifications.getPendingCount()));
        }

        // Send clickable button to view trades
        Component myTradesButton = plugin.getMessageManager()
                .getComponent(player, "trade.notifications.mytrades_button")
                .clickEvent(ClickEvent.runCommand("/mytrades"));

        // Send empty line, button, and another empty line for clean spacing
        player.sendMessage(Component.empty());
        player.sendMessage(myTradesButton);
        player.sendMessage(Component.empty());
    }
}