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
import java.util.stream.Collectors;

public class TradeCommand extends AMyCommand<Plugin> {
    
    private final Plugin plugin;

    public TradeCommand(Plugin plugin) {
        super(plugin, "trade");
        this.plugin = plugin;
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

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + this.getUsage());
            return true;
        }

        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        if (target.equals(player)) {
            sender.sendMessage(ChatColor.RED + "You cannot trade with yourself!");
            return true;
        }

        // Open pre-trade GUI for item selection
        PreTradeGUI preTradeGUI = new PreTradeGUI(player, target, plugin);
        preTradeGUI.openInventory();
        
        return true;
    }
}