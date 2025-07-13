package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.trade.ViewTradeGUI;
import com.prismamc.trade.commands.base.AMyCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TradeResponseCommand extends AMyCommand<Plugin> {
    
    private final Plugin plugin;
    private final boolean isAccept;
    private static final String PLAYER_ONLY = "§cThis command can only be used by players!";
    private static final String PLAYER_OFFLINE = "§cThat player is no longer online!";
    private static final String SELF_TRADE = "§cYou cannot accept/decline your own trade request!";
    private static final String EXPIRED = "§cThe trade request has expired!";
    private static final String EXPIRED_REQUESTER = "§cYour trade request has expired. Please send a new one.";

    public TradeResponseCommand(Plugin plugin, boolean isAccept) {
        super(plugin, isAccept ? "tradeaccept" : "tradedecline");
        this.plugin = plugin;
        this.isAccept = isAccept;
        this.setDescription(isAccept ? "View trade items from a player" : "Decline a trade request");
        this.setUsage("/" + (isAccept ? "tradeaccept" : "tradedecline") + " <player>");
        
        if (this.registerCommand()) {
            StringBuilder logMsg = new StringBuilder("Trade ")
                .append(isAccept ? "accept" : "decline")
                .append(" command registered successfully!");
            plugin.getLogger().info(logMsg.toString());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Objects.requireNonNullElse(sender, Bukkit.getConsoleSender()).sendMessage(PLAYER_ONLY);
            return true;
        }

        Player responder = (Player) sender;

        if (args.length != 1) {
            responder.sendMessage(getUsage());
            return true;
        }

        Player requester = Bukkit.getPlayer(args[0]);

        if (!validateTradeRequest(responder, requester)) {
            return true;
        }

        try {
            if (isAccept) {
                handleTradeView(responder, requester);
            } else {
                handleTradeDecline(responder, requester);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Error processing trade response command: %s", e.getMessage()));
            responder.sendMessage("§cAn error occurred while processing your request. Please try again.");
            return false;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> {
                    Player player = Bukkit.getPlayer(name);
                    // Solo mostrar jugadores que tienen items de trade pendientes
                    return player != null && 
                           !name.equals(sender.getName()) && 
                           name.toLowerCase().startsWith(input) &&
                           (!isAccept || plugin.getTradeManager().hasPreTradeItems(player.getUniqueId()));
                })
                .collect(Collectors.toList());
        }
        return super.tabComplete(sender, alias, args);
    }

    private boolean validateTradeRequest(Player responder, Player requester) {
        if (requester == null || !requester.isOnline()) {
            responder.sendMessage(PLAYER_OFFLINE);
            return false;
        }

        if (responder.equals(requester)) {
            responder.sendMessage(SELF_TRADE);
            return false;
        }

        if (isAccept && !plugin.getTradeManager().hasPreTradeItems(requester.getUniqueId())) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return false;
        }

        return true;
    }

    private void handleTradeView(Player responder, Player requester) {
        // Verificar si hay un trade válido activo entre estos jugadores
        if (!plugin.getTradeManager().arePlayersInTrade(requester.getUniqueId(), responder.getUniqueId())) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return;
        }

        List<ItemStack> preTradeItems = plugin.getTradeManager().getPreTradeItems(requester.getUniqueId());
        
        if (preTradeItems.isEmpty()) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return;
        }

        // Crear el trade con un nuevo ID
        long tradeId = plugin.getTradeManager().createNewTrade(requester.getUniqueId(), responder.getUniqueId());
        ViewTradeGUI viewTradeGUI = new ViewTradeGUI(responder, requester, plugin, preTradeItems, tradeId);
        viewTradeGUI.openInventory();
        
        requester.sendMessage(Component.text(responder.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" is viewing your trade items!")
            .color(NamedTextColor.GREEN)));
    }

    private void handleTradeDecline(Player responder, Player requester) {
        if (plugin.getTradeManager().hasPreTradeItems(requester.getUniqueId())) {
            returnItemsToRequester(requester);
        }
        
        sendDeclineMessages(responder, requester);
    }

    private void returnItemsToRequester(Player requester) {
        List<ItemStack> preTradeItems = plugin.getTradeManager().getAndRemovePreTradeItems(requester.getUniqueId());
        for (ItemStack item : preTradeItems) {
            if (item != null) {
                requester.getInventory().addItem(item.clone());
            }
        }
    }

    private void sendDeclineMessages(Player responder, Player requester) {
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