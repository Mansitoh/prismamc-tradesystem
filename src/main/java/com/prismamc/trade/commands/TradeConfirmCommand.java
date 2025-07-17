package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.commands.base.AMyCommand;
import com.prismamc.trade.gui.trade.ViewTradeGUI;
import com.prismamc.trade.manager.TradeManager.TradeState;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TradeConfirmCommand extends AMyCommand<Plugin> {

    private final Plugin plugin;

    public TradeConfirmCommand(Plugin plugin) {
        super(plugin, "tradeconfirm");
        this.plugin = plugin;
        this.setDescription("Open trade confirmation view for a specific trade");
        this.setUsage("/tradeconfirm <player> <tradeId>");
        this.setAliases("tc", "tconfirm");

        if (this.registerCommand()) {
            plugin.getLogger().info("TradeConfirm command registered successfully!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            // Tab complete for player names
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .filter(name -> !name.equals(sender.getName())) // Exclude self
                    .collect(Collectors.toList());
        }
        // No tab complete for trade ID as it's specific
        return super.tabComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            // Para CommandSender que no es Player, usar mensaje directo
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            plugin.getMessageManager().sendComponentMessage(player, "tradeconfirm.error.usage");
            return true;
        }

        String otherPlayerName = args[0];
        String tradeIdStr = args[1];
        long tradeId;

        // Validate trade ID
        try {
            tradeId = Long.parseLong(tradeIdStr);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendComponentMessage(player, "tradeconfirm.error.invalid_trade_id");
            return true;
        }

        // Find the other player's data
        plugin.getPlayerDataManager().findPlayerByNameIgnoreCase(otherPlayerName)
                .thenAccept(otherPlayerData -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (otherPlayerData == null) {
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "trade.errors.player_never_connected",
                                    "player", otherPlayerName);
                            return;
                        }

                        UUID otherPlayerUUID = otherPlayerData.getUuid();

                        // Verify the trade exists and the player is part of it
                        plugin.getTradeManager().getTradeInfo(tradeId)
                                .thenAccept(tradeDocument -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        if (tradeDocument == null) {
                                            plugin.getMessageManager().sendComponentMessage(player,
                                                    "tradeconfirm.error.trade_not_found",
                                                    "trade_id", String.valueOf(tradeId));
                                            return;
                                        }

                                        // Verify the player is part of this trade
                                        boolean isPlayer1 = tradeDocument.getPlayer1().equals(player.getUniqueId());
                                        boolean isPlayer2 = tradeDocument.getPlayer2().equals(player.getUniqueId());

                                        if (!isPlayer1 && !isPlayer2) {
                                            plugin.getMessageManager().sendComponentMessage(player,
                                                    "tradeconfirm.error.not_part_of_trade");
                                            return;
                                        }

                                        // Verify the other player is also part of this trade
                                        boolean otherIsPlayer1 = tradeDocument.getPlayer1().equals(otherPlayerUUID);
                                        boolean otherIsPlayer2 = tradeDocument.getPlayer2().equals(otherPlayerUUID);

                                        if (!otherIsPlayer1 && !otherIsPlayer2) {
                                            plugin.getMessageManager().sendComponentMessage(player,
                                                    "tradeconfirm.error.other_player_not_part",
                                                    "player", otherPlayerName);
                                            return;
                                        }

                                        // Verify trade is in ACTIVE state (ready for confirmation)
                                        if (tradeDocument.getState() != TradeState.ACTIVE) {
                                            plugin.getMessageManager().sendComponentMessage(player,
                                                    "tradeconfirm.error.trade_not_ready",
                                                    "state", tradeDocument.getState().name());
                                            return;
                                        }

                                        // Get the other player's items for viewing
                                        plugin.getTradeManager().getTradeItemsForPlayer(tradeId, player.getUniqueId())
                                                .thenAccept(otherPlayerItems -> {
                                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                        if (otherPlayerItems == null || otherPlayerItems.isEmpty()) {
                                                            plugin.getMessageManager().sendComponentMessage(player,
                                                                    "tradeconfirm.error.no_items_to_view",
                                                                    "player", otherPlayerName);
                                                            return;
                                                        }

                                                        // Create and open ViewTradeGUI in confirmation mode
                                                        ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                                                player,
                                                                otherPlayerData.getPlayerName(),
                                                                otherPlayerUUID,
                                                                plugin,
                                                                otherPlayerItems,
                                                                tradeId);

                                                        // Set confirmation mode flags
                                                        viewTradeGUI.setOnlyPreview(true);
                                                        viewTradeGUI.setConfirmationView(true);

                                                        // Open the GUI
                                                        viewTradeGUI.openInventory();

                                                        plugin.getLogger().info(String.format(
                                                                "Player %s opened trade confirmation view for trade %d with %s",
                                                                player.getName(), tradeId, otherPlayerName));
                                                    });
                                                })
                                                .exceptionally(throwable -> {
                                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                        plugin.getLogger().severe(
                                                                "Error loading trade items: " + throwable.getMessage());
                                                        plugin.getMessageManager().sendComponentMessage(player,
                                                                "tradeconfirm.error.loading_items");
                                                    });
                                                    return null;
                                                });
                                    });
                                })
                                .exceptionally(throwable -> {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        plugin.getLogger().severe("Error loading trade: " + throwable.getMessage());
                                        plugin.getMessageManager().sendComponentMessage(player,
                                                "tradeconfirm.error.loading_trade");
                                    });
                                    return null;
                                });
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("Error finding player: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(player, "trade.errors.verification_error",
                                "error", throwable.getMessage());
                    });
                    return null;
                });

        return true;
    }
}