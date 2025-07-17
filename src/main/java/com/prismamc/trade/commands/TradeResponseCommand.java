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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
            plugin.getLogger().info(
                    String.format("Comando de trade %s registrado exitosamente", isAccept ? "accept" : "decline"));
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

        // Realizar todas las validaciones de forma asíncrona
        plugin.getTradeManager().isTradeValid(tradeId)
                .thenCompose(isValid -> {
                    if (!isValid) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            responder.sendMessage(EXPIRED);
                            if (requester != null) {
                                requester.sendMessage(EXPIRED_REQUESTER);
                            }
                        });
                        return CompletableFuture.completedFuture(false);
                    }

                    if (requester == null || !requester.isOnline()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            responder.sendMessage(PLAYER_OFFLINE);
                        });
                        return CompletableFuture.completedFuture(false);
                    }

                    if (responder.equals(requester)) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            responder.sendMessage(SELF_TRADE);
                        });
                        return CompletableFuture.completedFuture(false);
                    }

                    return plugin.getTradeManager().hasTradeItems(tradeId, requester.getUniqueId())
                            .thenCompose(hasItems -> {
                                if (!hasItems) {
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        responder.sendMessage(EXPIRED);
                                        requester.sendMessage(EXPIRED_REQUESTER);
                                    });
                                    return CompletableFuture.completedFuture(false);
                                }
                                return CompletableFuture.completedFuture(true);
                            });
                })
                .thenAccept(shouldProceed -> {
                    if (!shouldProceed) {
                        return;
                    }

                    // Si todas las validaciones pasan, proceder con la acción correspondiente
                    if (isAccept) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                handleTradeView(responder, requester, tradeId);
                            } catch (Exception e) {
                                plugin.getLogger().severe("Error processing trade view: " + e.getMessage());
                                responder.sendMessage(
                                        "§cOcurrió un error al procesar tu solicitud. Por favor intenta de nuevo.");
                            }
                        });
                    } else {
                        try {
                            handleTradeDecline(responder, requester, tradeId);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error processing trade decline: " + e.getMessage());
                            responder.sendMessage(
                                    "§cOcurrió un error al procesar tu solicitud. Por favor intenta de nuevo.");
                        }
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Error en validación de trade: " + throwable.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        responder.sendMessage("§cOcurrió un error al validar el trade. Por favor intenta de nuevo.");
                    });
                    return null;
                });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) {
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
                String input = args[1].isEmpty() ? "" : args[1].toLowerCase();
                CompletableFuture<List<String>> pendingTradesFuture = plugin.getTradeManager()
                        .getPlayerPendingTrades(target.getUniqueId())
                        .thenApply(tradeIds -> tradeIds.stream()
                                .map(String::valueOf)
                                .filter(id -> id.startsWith(input))
                                .collect(Collectors.toList()));

                try {
                    return pendingTradesFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    plugin.getLogger()
                            .warning("Error al obtener trades pendientes para autocompletado: " + e.getMessage());
                    return List.of();
                }
            }
        }
        return super.tabComplete(sender, alias, args);
    }

    private void handleTradeView(Player responder, Player requester, long tradeId) {
        plugin.getTradeManager().getTradeItems(tradeId, requester.getUniqueId())
                .thenAccept(tradeItems -> {
                    if (tradeItems.isEmpty()) {
                        responder.sendMessage(EXPIRED);
                        requester.sendMessage(EXPIRED_REQUESTER);
                        return;
                    }

                    // Volvemos al hilo principal para operaciones de inventario
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        ViewTradeGUI viewTradeGUI = new ViewTradeGUI(responder, requester, plugin, tradeItems, tradeId);
                        viewTradeGUI.openInventory();

                        requester.sendMessage(Component.text(responder.getName())
                                .color(NamedTextColor.WHITE)
                                .append(Component.text(" está viendo tus items del trade!")
                                        .color(NamedTextColor.GREEN)));
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Error al obtener items del trade: " + throwable.getMessage());
                    responder.sendMessage(Component.text("Error al cargar los items del trade.")
                            .color(NamedTextColor.RED));
                    return null;
                });
    }

    private void handleTradeDecline(Player responder, Player requester, long tradeId) {
        plugin.getTradeManager().getAndRemoveTradeItems(tradeId, requester.getUniqueId())
                .thenAccept(tradeItems -> {
                    // Volvemos al hilo principal para operaciones de inventario
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Devolver items al requester
                        for (ItemStack item : tradeItems) {
                            if (item.getType() != Material.AIR) {
                                HashMap<Integer, ItemStack> leftoverItems = requester.getInventory()
                                        .addItem(item.clone());
                                if (!leftoverItems.isEmpty()) {
                                    leftoverItems.values().forEach(leftover -> requester.getWorld()
                                            .dropItemNaturally(requester.getLocation(), leftover));
                                    requester.sendMessage(
                                            "§eAlgunos items fueron dropeados al suelo porque tu inventario esta lleno!");
                                }
                            }
                        }

                        // Actualizar estado del trade y limpiarlo
                        CompletableFuture.allOf(
                                plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.CANCELLED),
                                plugin.getTradeManager().cleanupTrade(tradeId))
                                .thenRun(() -> sendDeclineMessages(responder, requester));
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Error al declinar el trade: " + throwable.getMessage());
                    responder.sendMessage(Component.text("Error al declinar el trade.")
                            .color(NamedTextColor.RED));
                    return null;
                });
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