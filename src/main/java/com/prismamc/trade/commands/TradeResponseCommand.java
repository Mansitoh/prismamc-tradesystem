package com.prismamc.trade.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.trade.ViewTradeGUI;
import com.prismamc.trade.commands.base.AMyCommand;
import com.prismamc.trade.manager.TradeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TradeResponseCommand extends AMyCommand<Plugin> {
    
    private final Plugin plugin;
    private final boolean isAccept;
    private static final String PLAYER_ONLY = "§cEste comando solo puede ser usado por jugadores!";
    private static final String PLAYER_OFFLINE = "§cEse jugador ya no está en línea!";
    private static final String SELF_TRADE = "§cNo puedes aceptar/rechazar tu propia solicitud de trade!";
    private static final String EXPIRED = "§cLa solicitud de trade ha expirado!";
    private static final String EXPIRED_REQUESTER = "§cTu solicitud de trade ha expirado. Por favor envía una nueva.";

    public TradeResponseCommand(Plugin plugin, boolean isAccept) {
        super(plugin, isAccept ? "tradeaccept" : "tradedecline");
        this.plugin = plugin;
        this.isAccept = isAccept;
        this.setDescription(isAccept ? "Ver items del trade de un jugador" : "Rechazar una solicitud de trade");
        this.setUsage("/" + (isAccept ? "tradeaccept" : "tradedecline") + " <jugador> <id-trade>");
        
        if (this.registerCommand()) {
            plugin.getLogger().info(String.format("Comando de trade %s registrado exitosamente", isAccept ? "accept" : "decline"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Objects.requireNonNullElse(sender, Bukkit.getConsoleSender()).sendMessage(PLAYER_ONLY);
            return true;
        }

        Player responder = (Player) sender;

        if (args.length != 2) {
            responder.sendMessage(getUsage());
            return true;
        }

        Player requester = Bukkit.getPlayer(args[0]);
        long tradeId;
        
        try {
            tradeId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            responder.sendMessage("§cID de trade inválido!");
            return true;
        }

        if (!validateTradeRequest(responder, requester, tradeId)) {
            return true;
        }

        try {
            if (isAccept) {
                handleTradeView(responder, requester, tradeId);
            } else {
                handleTradeDecline(responder, requester, tradeId);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Error processing trade response command: %s", e.getMessage()));
            responder.sendMessage("§cOcurrió un error al procesar tu solicitud. Por favor intenta de nuevo.");
            return false;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return super.tabComplete(sender, alias, args);
        }
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> {
                    Player target = Bukkit.getPlayer(name);
                    return target != null && 
                           !name.equals(sender.getName()) && 
                           name.toLowerCase().startsWith(input);
                })
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                // Mostrar IDs de trades pendientes entre estos jugadores
                List<Long> pendingTrades = plugin.getTradeManager().getPlayerPendingTrades(target.getUniqueId())
                    .stream()
                    .filter(tradeId -> {
                        TradeManager.TradeInfo info = plugin.getTradeManager().getTradeInfo(tradeId);
                        return info != null && 
                            (info.getPlayer1().equals(player.getUniqueId()) || info.getPlayer2().equals(player.getUniqueId()));
                    })
                    .collect(Collectors.toList());
                return pendingTrades.stream()
                    .map(String::valueOf)
                    .filter(id -> args[1].isEmpty() || id.startsWith(args[1]))
                    .collect(Collectors.toList());
            }
        }
        return super.tabComplete(sender, alias, args);
    }

    private boolean validateTradeRequest(Player responder, Player requester, long tradeId) {
        if (requester == null || !requester.isOnline()) {
            responder.sendMessage(PLAYER_OFFLINE);
            return false;
        }

        if (responder.equals(requester)) {
            responder.sendMessage(SELF_TRADE);
            return false;
        }

        if (!plugin.getTradeManager().isTradeValid(tradeId)) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return false;
        }

        if (!plugin.getTradeManager().hasTradeItems(tradeId, requester.getUniqueId())) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return false;
        }

        return true;
    }

    private void handleTradeView(Player responder, Player requester, long tradeId) {
        List<ItemStack> tradeItems = plugin.getTradeManager().getTradeItems(tradeId, requester.getUniqueId());
        
        if (tradeItems.isEmpty()) {
            responder.sendMessage(EXPIRED);
            requester.sendMessage(EXPIRED_REQUESTER);
            return;
        }

        ViewTradeGUI viewTradeGUI = new ViewTradeGUI(responder, requester, plugin, tradeItems, tradeId);
        viewTradeGUI.openInventory();
        
        requester.sendMessage(Component.text(responder.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" está viendo tus items del trade!")
            .color(NamedTextColor.GREEN)));
    }

    private void handleTradeDecline(Player responder, Player requester, long tradeId) {
        List<ItemStack> tradeItems = plugin.getTradeManager().getAndRemoveTradeItems(tradeId, requester.getUniqueId());
        
        // Devolver items al requester
        for (ItemStack item : tradeItems) {
            if (item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftoverItems = requester.getInventory().addItem(item.clone());
                if (!leftoverItems.isEmpty()) {
                    leftoverItems.values().forEach(leftover -> 
                        requester.getWorld().dropItemNaturally(requester.getLocation(), leftover));
                    requester.sendMessage("§eAlgunos items fueron dropeados al suelo porque tu inventario esta lleno!");
                }
            }
        }
        
        // Actualizar estado del trade
        plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.CANCELLED);
        plugin.getTradeManager().cleanupTrade(tradeId);
        
        sendDeclineMessages(responder, requester);
    }

    private void sendDeclineMessages(Player responder, Player requester) {
        requester.sendMessage(Component.text(responder.getName())
            .color(NamedTextColor.WHITE)
            .append(Component.text(" ha rechazado tu solicitud de trade.")
            .color(NamedTextColor.RED)));
            
        responder.sendMessage(Component.text("Has rechazado la solicitud de trade de ")
            .color(NamedTextColor.RED)
            .append(Component.text(requester.getName())
            .color(NamedTextColor.WHITE)));
    }
}