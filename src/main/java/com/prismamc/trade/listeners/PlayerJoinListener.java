package com.prismamc.trade.listeners;

import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.prismamc.trade.Plugin;

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
                            plugin.getLogger().log(Level.SEVERE, "Error saving player data for {0}: {1}", new Object[]{player.getName(), throwable.getMessage()});
                            return null;
                        });
                }
            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Error loading player data for {0}: {1}", new Object[]{player.getName(), throwable.getMessage()});
                return null;
            });
    }
}