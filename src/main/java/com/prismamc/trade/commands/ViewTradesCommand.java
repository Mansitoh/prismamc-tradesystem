package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.commands.base.AMyCommand;
import com.prismamc.trade.gui.trade.AdminViewTradesGUI;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando para administradores que permite ver los trades de cualquier jugador
 * Funciona igual que MyTradesGUI pero para otros jugadores
 */
public class ViewTradesCommand extends AMyCommand<Plugin> {

    private final Plugin plugin;

    public ViewTradesCommand(Plugin plugin) {
        super(plugin, "viewtrades");
        this.plugin = plugin;
        this.setDescription("View trades of any player (Admin only)");
        this.setUsage("/viewtrades <player>");
        this.setAliases("vt", "admintrades");

        if (this.registerCommand()) {
            plugin.getLogger().info("ViewTrades command registered successfully!");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            // Tab complete para nombres de jugadores (incluye offline players)
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return super.tabComplete(sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verificar que sea un jugador
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores!");
            return true;
        }

        Player admin = (Player) sender;

        // Verificar permisos
        if (!admin.hasPermission("prismamc.trade.admin.viewtrades")) {
            plugin.getMessageManager().sendComponentMessage(admin, "admin.errors.no_permission");
            return true;
        }

        // Verificar argumentos
        if (args.length != 1) {
            plugin.getMessageManager().sendComponentMessage(admin, "admin.viewtrades.usage");
            return true;
        }

        String targetPlayerName = args[0];

        // Buscar el jugador objetivo (puede estar offline)
        plugin.getPlayerDataManager().findPlayerByNameIgnoreCase(targetPlayerName)
                .thenAccept(targetPlayerData -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (targetPlayerData == null) {
                            plugin.getMessageManager().sendComponentMessage(admin,
                                    "admin.viewtrades.player_not_found",
                                    "player", targetPlayerName);
                            return;
                        }

                        // Verificar que no sea el mismo jugador (opcional, pero recomendado usar
                        // /mytrades)
                        if (targetPlayerData.getUuid().equals(admin.getUniqueId())) {
                            plugin.getMessageManager().sendComponentMessage(admin,
                                    "admin.viewtrades.use_mytrades");
                            return;
                        }

                        // Notificar al admin que se está abriendo la vista
                        plugin.getMessageManager().sendComponentMessage(admin,
                                "admin.viewtrades.opening_view",
                                "player", targetPlayerData.getPlayerName());

                        // Crear y abrir el GUI de administración
                        AdminViewTradesGUI adminViewTradesGUI = new AdminViewTradesGUI(
                                admin,
                                targetPlayerData,
                                plugin);

                        adminViewTradesGUI.openInventory();

                        // Log de la acción para auditoría
                        plugin.getLogger().info(String.format(
                                "Admin %s is viewing trades of player %s (UUID: %s)",
                                admin.getName(),
                                targetPlayerData.getPlayerName(),
                                targetPlayerData.getUuid()));
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger()
                                .severe("Error finding player for ViewTrades command: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(admin,
                                "admin.viewtrades.error_loading",
                                "error", throwable.getMessage());
                    });
                    return null;
                });

        return true;
    }
}