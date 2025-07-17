package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.commands.base.AMyCommand;
import com.prismamc.trade.gui.trade.PreTradeGUI;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class TradeCommand extends AMyCommand<Plugin> {

    private final Plugin plugin;
    private final HashMap<UUID, Long> cooldowns;
    private final int cooldownSeconds;

    public TradeCommand(Plugin plugin) {
        super(plugin, "trade");
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.cooldownSeconds = plugin.getConfig().getInt("commands.trade.cooldown", 5);
        this.setDescription("Open trade selection menu with another player");
        this.setUsage("/trade <player>");
        this.setAliases("t", "tradear");

        if (this.registerCommand()) {
            plugin.getLogger().info("Trade command registered successfully!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .filter(name -> !name.equals(sender.getName())) // Excluir al propio jugador
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check cooldown
        long currentTime = System.currentTimeMillis();
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeElapsed = currentTime - cooldowns.get(player.getUniqueId());
            if (timeElapsed < cooldownSeconds * 1000) {
                int remainingSeconds = (int) ((cooldownSeconds * 1000 - timeElapsed) / 1000);
                player.sendMessage(ChatColor.RED + "Please wait " + remainingSeconds
                        + " seconds before using this command again!");
                return true;
            }
        }

        if (args.length != 1) {
            plugin.getMessageManager().sendComponentMessage(player, "trade.errors.usage");
            return true;
        }

        String targetPlayerName = args[0];

        // Verificar si el jugador objetivo existe en la base de datos
        plugin.getPlayerDataManager().findPlayerByNameIgnoreCase(targetPlayerName)
                .thenAccept(targetPlayerData -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (targetPlayerData == null) {
                            // El jugador nunca se conectó al servidor
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "trade.errors.player_never_connected",
                                    "player", targetPlayerName);
                            return;
                        }

                        // El jugador existe en la base de datos
                        UUID targetUUID = targetPlayerData.getUuid();

                        if (targetUUID.equals(player.getUniqueId())) {
                            plugin.getMessageManager().sendComponentMessage(player, "trade.errors.self_trade");
                            return;
                        }

                        // Update cooldown
                        cooldowns.put(player.getUniqueId(), currentTime);

                        // Asegurarnos de que la apertura del GUI se realice en el hilo principal
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Crear PreTradeGUI pasando PlayerData del target en lugar de Player
                            PreTradeGUI preTradeGUI = new PreTradeGUI(player, targetPlayerData, plugin);
                            preTradeGUI.openInventory();
                        });
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("Error checking player data: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(player, "trade.errors.verification_error",
                                "error", throwable.getMessage());
                    });
                    return null;
                });

        return true;
    }
}