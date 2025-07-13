package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.TradeGUI;
import com.prismamc.trade.commands.base.AMyCommand;

public class TradeCommand extends AMyCommand<Plugin> {
    
    public TradeCommand(Plugin plugin) {
        super(plugin, "trade");
        this.setDescription("Open a trade menu with another player");
        this.setUsage("/trade <player>");
        this.registerCommand();
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

        // Create and open trade GUI
        TradeGUI tradeGUI = new TradeGUI(player, target);
        tradeGUI.openInventory();
        target.openInventory(tradeGUI.getInventory());

        // Send messages
        player.sendMessage(ChatColor.GREEN + "Opening trade with " + target.getName());
        target.sendMessage(ChatColor.GREEN + player.getName() + " wants to trade with you!");

        return true;
    }
}