package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.trade.TradeGUI;
import com.prismamc.trade.commands.base.AMyCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class TradeResponseCommand extends AMyCommand<Plugin> {
    
    private final Plugin plugin;
    private final boolean isAccept;

    public TradeResponseCommand(Plugin plugin, boolean isAccept) {
        super(plugin, isAccept ? "tradeaccept" : "tradedecline");
        this.plugin = plugin;
        this.isAccept = isAccept;
        this.setDescription(isAccept ? "Accept a trade request" : "Decline a trade request");
        this.setUsage("/" + (isAccept ? "tradeaccept" : "tradedecline") + " <player>");
        
        if (this.registerCommand()) {
            plugin.getLogger().info("Trade " + (isAccept ? "accept" : "decline") + " command registered successfully!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (sender != null) {
                sender.sendMessage("This command can only be used by players!");
            }
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Usage: " + this.getUsage());
            return true;
        }

        Player responder = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (!validateTradeRequest(responder, target)) {
            return true;
        }

        if (isAccept) {
            handleTradeAcceptance(responder, target);
        } else {
            handleTradeDecline(responder, target);
        }

        return true;
    }

    private boolean validateTradeRequest(Player responder, Player requester) {
        if (requester == null || !requester.isOnline()) {
            responder.sendMessage("That player is no longer online!");
            return false;
        }

        if (responder.equals(requester)) {
            responder.sendMessage("You cannot accept/decline your own trade request!");
            return false;
        }

        if (isAccept && !plugin.getTradeManager().hasPreTradeItems(requester.getUniqueId())) {
            responder.sendMessage("The trade request has expired!");
            requester.sendMessage("Your trade request has expired. Please send a new one.");
            return false;
        }

        return true;
    }

    private void handleTradeAcceptance(Player responder, Player requester) {
        // Get pre-selected items
        List<ItemStack> preTradeItems = plugin.getTradeManager().getAndRemovePreTradeItems(requester.getUniqueId());

        // Create and open trade GUI with pre-selected items
        TradeGUI tradeGUI = new TradeGUI(requester, responder);
        
        // Pre-fill requester's items
        for (int i = 0; i < preTradeItems.size(); i++) {
            tradeGUI.setInitialItem(requester, i, preTradeItems.get(i));
        }
        
        // Open inventory for both players
        tradeGUI.openInventory();
        tradeGUI.openFor(responder);
        
        // Send acceptance messages
        requester.sendMessage(Component.text(responder.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" accepted your trade request!")
            .color(NamedTextColor.GREEN)));
            
        responder.sendMessage(Component.text("You accepted the trade request from ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(requester.getName())
            .color(NamedTextColor.WHITE)));
    }

    private void handleTradeDecline(Player responder, Player requester) {
        // Return items to requester if they exist
        if (plugin.getTradeManager().hasPreTradeItems(requester.getUniqueId())) {
            List<ItemStack> preTradeItems = plugin.getTradeManager().getAndRemovePreTradeItems(requester.getUniqueId());
            for (ItemStack item : preTradeItems) {
                if (item != null) {
                    requester.getInventory().addItem(item);
                }
            }
        }
        
        // Send decline messages
        requester.sendMessage(Component.text(responder.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" declined your trade request.")
            .color(NamedTextColor.RED)));
            
        responder.sendMessage(Component.text("You declined the trade request from ")
            .color(NamedTextColor.RED)
            .append(Component.text(requester.getName())
            .color(NamedTextColor.WHITE)));
    }
}