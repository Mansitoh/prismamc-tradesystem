package com.prismamc.trade.gui.trade;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ViewTradeGUI extends GUI {
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final Plugin plugin;
    private final List<ItemStack> initiatorItems;
    private final long tradeId;
    private final String initiatorName; // Almacenar el nombre para casos offline
    private final java.util.UUID initiatorUUID; // Almacenar el UUID para casos offline
    private int currentPage = 0;
    private static final int[] TRADE_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int ADD_ITEMS_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int CANCEL_TRADE_SLOT = 45;
    private static final int CONFIRM_TRADE_SLOT = 53; // Back to slot 53 as requested
    private static final int ITEMS_PER_PAGE = 36;
    private boolean isOnlyPreview = false;
    private boolean isConfirmationView = false;
    private boolean isAdminView = false; // Nuevo flag para vista admin

    public void setOnlyPreview(boolean b) {
        isOnlyPreview = b;
    }

    public void setConfirmationView(boolean b) {
        isConfirmationView = b;
    }

    public void setAdminView(boolean isAdminView) {
        this.isAdminView = isAdminView;
    }

    public ViewTradeGUI(Player target, Player initiator, Plugin plugin, List<ItemStack> initiatorItems, long tradeId) {
        super(target, "Viewing Trade Items from " + initiator.getName(), 54);
        this.tradeTarget = target;
        this.tradeInitiator = initiator;
        this.plugin = plugin;
        this.initiatorItems = initiatorItems;
        this.tradeId = tradeId;
        this.initiatorName = initiator.getName();
        this.initiatorUUID = initiator.getUniqueId();
    }

    /**
     * Constructor que acepta nombre y UUID del jugador (para jugadores offline)
     */
    public ViewTradeGUI(Player target, String initiatorName, java.util.UUID initiatorUUID, Plugin plugin,
            List<ItemStack> initiatorItems, long tradeId) {
        super(target, "Viewing Trade Items from " + initiatorName, 54);
        this.tradeTarget = target;
        this.tradeInitiator = plugin.getServer().getPlayer(initiatorUUID); // Puede ser null si está offline
        this.plugin = plugin;
        this.initiatorItems = initiatorItems;
        this.tradeId = tradeId;
        this.initiatorName = initiatorName;
        this.initiatorUUID = initiatorUUID;
    }

    /**
     * Obtener el nombre del jugador iniciador de forma segura
     */
    private String getInitiatorName() {
        if (initiatorName != null) {
            return initiatorName;
        } else if (tradeInitiator != null) {
            return tradeInitiator.getName();
        } else {
            return "Unknown Player";
        }
    }

    /**
     * Obtener el UUID del jugador iniciador de forma segura
     */
    private java.util.UUID getInitiatorUUID() {
        if (initiatorUUID != null) {
            return initiatorUUID;
        } else if (tradeInitiator != null) {
            return tradeInitiator.getUniqueId();
        } else {
            return null;
        }
    }

    @Override
    protected void initializeItems() {
        plugin.getTradeManager().isTradeValid(tradeId)
                .thenAccept(isValid -> {
                    if (!isValid) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(owner, "general.invalid_trade_error");
                            owner.closeInventory();
                        });
                        return;
                    }

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Add decorative border first with player language support
                        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

                        if (borderItem != null) {
                            for (int i = 36; i < 54; i++) {
                                if (i != ADD_ITEMS_SLOT && i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT
                                        && i != CANCEL_TRADE_SLOT && i != CONFIRM_TRADE_SLOT) {
                                    inventory.setItem(i, borderItem.clone());
                                }
                            }
                        }

                        // Update pagination buttons with player language support
                        updatePaginationButtons();

                        // Add button to add own items using ItemManager with player language
                        ItemStack addItemsButton = plugin.getItemManager().getItemStack(owner, "gui.buttons.add_items",
                                "player", getInitiatorName());
                        if (addItemsButton != null) {
                            if (isOnlyPreview) {
                                inventory.setItem(ADD_ITEMS_SLOT, borderItem);
                            } else {
                                inventory.setItem(ADD_ITEMS_SLOT, addItemsButton);
                            }
                        }

                        // Add cancel trade button using ItemManager with player language
                        ItemStack cancelTradeButton = plugin.getItemManager().getItemStack(owner,
                                "gui.buttons.cancel_trade",
                                "trade_id", String.valueOf(tradeId));
                        if (cancelTradeButton != null) {
                            inventory.setItem(CANCEL_TRADE_SLOT, cancelTradeButton);
                        }

                        // Add info panel using ItemManager with player language
                        ItemStack infoSign = plugin.getItemManager().getItemStack(owner, "gui.info.view_trade_info",
                                "player", getInitiatorName(),
                                "trade_id", String.valueOf(tradeId),
                                "page", String.valueOf(currentPage + 1));
                        if (infoSign != null) {
                            inventory.setItem(INFO_SLOT, infoSign);
                        }

                        // Clear and display current page items
                        for (int slot : TRADE_SLOTS) {
                            inventory.setItem(slot, null);
                        }

                        int startIndex = currentPage * ITEMS_PER_PAGE;
                        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < initiatorItems.size(); i++) {
                            inventory.setItem(TRADE_SLOTS[i], initiatorItems.get(startIndex + i));
                        }

                        // Siempre colocar algo en el slot de confirmación
                        if (isConfirmationView) {
                            setupConfirmationButton();
                        } else {
                            // Cuando NO está en modo confirmación, colocar cristal decorativo
                            setupDisabledConfirmationSlot();
                        }
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getMessageManager().sendComponentMessage(owner, "general.verification_error", "error",
                                throwable.getMessage());
                        owner.closeInventory();
                    });
                    return null;
                });
    }

    /**
     * Configurar el botón de confirmación con fallbacks robustos
     */
    private void setupConfirmationButton() {
        // Primero intentar verificar si el jugador ya aceptó
        plugin.getTradeManager().hasPlayerAccepted(tradeId, owner.getUniqueId())
                .thenAccept(hasAccepted -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        placeConfirmationButton(hasAccepted);
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().warning(
                                "Error checking player acceptance, using fallback: " + throwable.getMessage());
                        // Fallback: asumir que no ha aceptado y mostrar botón de confirmación
                        placeConfirmationButton(false);
                    });
                    return null;
                });
    }

    /**
     * Colocar el botón de confirmación en el inventario
     */
    private void placeConfirmationButton(boolean hasAccepted) {
        ItemStack confirmationButton = null;

        if (hasAccepted) {
            // Player has already accepted, show waiting button
            confirmationButton = plugin.getItemManager().getItemStack(owner,
                    "gui.buttons.confirm_trade_waiting",
                    "player", getInitiatorName(),
                    "trade_id", String.valueOf(tradeId));
        } else {
            // Player hasn't accepted yet, show confirm button
            confirmationButton = plugin.getItemManager().getItemStack(owner,
                    "gui.buttons.confirm_trade_final",
                    "player", getInitiatorName(),
                    "trade_id", String.valueOf(tradeId));
        }

        // Si no se puede obtener del ItemManager, crear un fallback
        if (confirmationButton == null) {
            plugin.getLogger().warning("No se pudo obtener el botón de confirmación del ItemManager, usando fallback");
            confirmationButton = createFallbackConfirmButton(hasAccepted);
        }

        // Colocar el botón en el slot 53
        if (confirmationButton != null) {
            inventory.setItem(CONFIRM_TRADE_SLOT, confirmationButton);
            plugin.getLogger().info("Botón de confirmación colocado en slot " + CONFIRM_TRADE_SLOT +
                    " - hasAccepted: " + hasAccepted);
        } else {
            plugin.getLogger().severe("No se pudo crear ningún botón de confirmación, ni del ItemManager ni fallback");
        }
    }

    /**
     * Crear un botón de confirmación de fallback si el ItemManager falla
     */
    private ItemStack createFallbackConfirmButton(boolean hasAccepted) {
        try {
            org.bukkit.Material material = hasAccepted ? org.bukkit.Material.YELLOW_STAINED_GLASS_PANE
                    : org.bukkit.Material.EMERALD;
            ItemStack fallbackButton = new ItemStack(material);
            org.bukkit.inventory.meta.ItemMeta meta = fallbackButton.getItemMeta();

            if (meta != null) {
                if (hasAccepted) {
                    meta.setDisplayName("§e⏳ Esperando al otro jugador...");
                    meta.setLore(java.util.Arrays.asList(
                            "§7Ya has confirmado este trade",
                            "§7Esperando a que " + getInitiatorName() + " confirme",
                            "§7Trade ID: §f#" + tradeId));
                } else {
                    meta.setDisplayName("§a✅ CONFIRMAR TRADE");
                    meta.setLore(java.util.Arrays.asList(
                            "§7Click para confirmar y completar",
                            "§7este trade con " + getInitiatorName(),
                            "§7Trade ID: §f#" + tradeId,
                            "",
                            "§c⚠ Esta acción no se puede deshacer"));
                }
                fallbackButton.setItemMeta(meta);
            }

            return fallbackButton;
        } catch (Exception e) {
            plugin.getLogger().severe("Error creando botón de fallback: " + e.getMessage());
            return null;
        }
    }

    /**
     * Configurar un cristal decorativo en el slot de confirmación cuando NO está en
     * modo confirmación
     */
    private void setupDisabledConfirmationSlot() {
        // Intentar obtener el cristal decorativo del ItemManager
        ItemStack disabledGlass = plugin.getItemManager().getItemStack(owner, "gui.decorative.disabled_confirmation");

        // Si no existe en el ItemManager, crear un fallback
        if (disabledGlass == null) {
            disabledGlass = createFallbackDisabledGlass();
        }

        // Colocar el cristal en el slot 53
        if (disabledGlass != null) {
            inventory.setItem(CONFIRM_TRADE_SLOT, disabledGlass);
            plugin.getLogger()
                    .info("Cristal decorativo colocado en slot " + CONFIRM_TRADE_SLOT + " (modo no-confirmación)");
        } else {
            plugin.getLogger().warning("No se pudo crear el cristal decorativo para el slot de confirmación");
            // Como último recurso, usar un border item si está disponible
            ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");
            if (borderItem != null) {
                inventory.setItem(CONFIRM_TRADE_SLOT, borderItem.clone());
            }
        }
    }

    /**
     * Crear un cristal decorativo de fallback
     */
    private ItemStack createFallbackDisabledGlass() {
        try {
            ItemStack glassPane = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta meta = glassPane.getItemMeta();

            if (meta != null) {
                meta.setDisplayName("§7⚫ Slot Deshabilitado");
                meta.setLore(java.util.Arrays.asList(
                        "§7Este slot no está disponible",
                        "§7en el modo de vista actual",
                        "",
                        "§8Trade ID: #" + tradeId));
                glassPane.setItemMeta(meta);
            }

            return glassPane;
        } catch (Exception e) {
            plugin.getLogger().severe("Error creando cristal decorativo de fallback: " + e.getMessage());
            return null;
        }
    }

    private void updatePaginationButtons() {
        // Previous page button with player language support
        if (currentPage > 0) {
            ItemStack prevPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.previous_page");
            if (prevPageItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, prevPageItem);
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, disabledItem);
            }
        }

        if (hasNextPage()) {
            ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
            if (nextPageItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, disabledItem);
            }
        }
    }

    private boolean hasNextPage() {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        return initiatorItems.size() > startIndex + ITEMS_PER_PAGE;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            initializeItems();
            return;
        }

        if (clickedSlot == NEXT_PAGE_SLOT && hasNextPage()) {
            currentPage++;
            initializeItems();
            return;
        }

        if (clickedSlot == ADD_ITEMS_SLOT && player.equals(tradeTarget) && !isOnlyPreview) {
            plugin.getTradeManager().isTradeValid(tradeId)
                    .thenAccept(isValid -> {
                        if (!isValid) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                plugin.getMessageManager().sendComponentMessage(player, "general.invalid_trade_error");
                                player.closeInventory();
                            });
                            return;
                        }

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Open PreTradeGUI for target to select items usando el constructor que acepta
                            // nombre y UUID
                            PreTradeGUI preTradeGUI = new PreTradeGUI(tradeTarget, getInitiatorName(),
                                    getInitiatorUUID(), plugin, true, tradeId);
                            preTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player, "general.verification_error",
                                    "error", throwable.getMessage());
                        });
                        return null;
                    });
        }

        if (clickedSlot == CANCEL_TRADE_SLOT && player.equals(tradeTarget)) {
            handleCancelTrade(player);
        }

        if (clickedSlot == CONFIRM_TRADE_SLOT && player.equals(owner) && isConfirmationView) {
            handleTradeConfirmation(player);
        }
    }

    /**
     * Handle trade confirmation click
     */
    private void handleTradeConfirmation(Player player) {
        // First check if player has already accepted
        plugin.getTradeManager().hasPlayerAccepted(tradeId, player.getUniqueId())
                .thenAccept(hasAccepted -> {
                    if (hasAccepted) {
                        // Player already accepted, show message usando el mensaje correcto
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "viewtrade.gui.confirmation.already_confirmed");
                        });
                        return;
                    }

                    // Player hasn't accepted yet, process confirmation
                    plugin.getTradeManager().updatePlayerAcceptance(tradeId, player.getUniqueId(), true)
                            .thenAccept(voidResult -> {
                                // Check if both players have now accepted
                                plugin.getTradeManager().haveBothPlayersAccepted(tradeId)
                                        .thenAccept(bothAccepted -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                if (bothAccepted) {
                                                    // Complete the trade
                                                    completeTrade(player);
                                                } else {
                                                    // Update GUI to show waiting state - usar mensaje correcto
                                                    plugin.getMessageManager().sendComponentMessage(player,
                                                            "viewtrade.gui.confirmation.waiting_for_other");
                                                    initializeItems(); // Refresh GUI to show waiting button

                                                    // Notify the other player
                                                    Player otherPlayer = player.equals(tradeInitiator) ? tradeTarget
                                                            : tradeInitiator;
                                                    if (otherPlayer != null && otherPlayer.isOnline()) {
                                                        plugin.getMessageManager().sendComponentMessage(otherPlayer,
                                                                "viewtrade.gui.confirmation.player_confirmed",
                                                                "player", player.getName());
                                                    }
                                                }
                                            });
                                        })
                                        .exceptionally(throwable -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                plugin.getLogger().severe("Error checking both players acceptance: "
                                                        + throwable.getMessage());
                                                plugin.getMessageManager().sendComponentMessage(player,
                                                        "viewtrade.gui.error.verification_failed");
                                            });
                                            return null;
                                        });
                            })
                            .exceptionally(throwable -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getLogger()
                                            .severe("Error setting player acceptance: " + throwable.getMessage());
                                    plugin.getMessageManager().sendComponentMessage(player,
                                            "viewtrade.gui.error.confirmation_failed");
                                });
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("Error checking player acceptance: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(player,
                                "viewtrade.gui.error.verification_failed");
                    });
                    return null;
                });
    }

    /**
     * Complete the trade when both players have confirmed
     */
    private void completeTrade(Player player) {
        plugin.getTradeManager().completeTrade(tradeId)
                .thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (success) {
                            // El jugador actual (que tiene el GUI abierto) recibe los items inmediatamente
                            plugin.getTradeManager().getTradeItemsForPlayer(tradeId, player.getUniqueId())
                                    .thenAccept(items -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            if (items != null && !items.isEmpty()) {
                                                // Dar items al jugador actual
                                                giveItemsToPlayer(player, items);

                                                // Mensaje de éxito para el jugador actual
                                                String otherPlayerName = getOtherPlayerName(player.getUniqueId());
                                                plugin.getMessageManager().sendComponentMessage(player,
                                                        "trade.success.completion.items_received",
                                                        "player", otherPlayerName,
                                                        "trade_id", String.valueOf(tradeId));
                                            }
                                        });
                                    });

                            // Notificar al otro jugador - LÓGICA CORREGIDA
                            notifyOtherPlayerTradeCompleted(player);

                            // Cerrar el GUI del jugador actual
                            player.closeInventory();

                        } else {
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "viewtrade.gui.error.completion_failed");
                        }
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("Error completing trade: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(player,
                                "viewtrade.gui.error.completion_failed");
                    });
                    return null;
                });
    }

    /**
     * Obtener el UUID del otro jugador en el trade
     */
    private java.util.UUID getOtherPlayerUUID(java.util.UUID currentPlayerUUID) {
        // Si el jugador actual es el target, el otro es el initiator
        if (currentPlayerUUID.equals(tradeTarget.getUniqueId())) {
            return getInitiatorUUID();
        }
        // Si el jugador actual es el initiator, el otro es el target
        else if (currentPlayerUUID.equals(getInitiatorUUID())) {
            return tradeTarget.getUniqueId();
        }

        return null; // No se pudo identificar
    }

    /**
     * Obtener el nombre del otro jugador en el trade
     */
    private String getOtherPlayerName(java.util.UUID currentPlayerUUID) {
        // Si el jugador actual es el target, el otro es el initiator
        if (currentPlayerUUID.equals(tradeTarget.getUniqueId())) {
            return getInitiatorName();
        }
        // Si el jugador actual es el initiator, el otro es el target
        else if (currentPlayerUUID.equals(getInitiatorUUID())) {
            return tradeTarget.getName();
        }

        return "Unknown Player"; // Fallback
    }

    /**
     * Notificar al otro jugador que el trade fue completado
     */
    private void notifyOtherPlayerTradeCompleted(Player currentPlayer) {
        // Identificar al otro jugador usando UUIDs
        java.util.UUID otherPlayerUUID = getOtherPlayerUUID(currentPlayer.getUniqueId());

        if (otherPlayerUUID == null) {
            plugin.getLogger().warning("No se pudo identificar al otro jugador para notificación de trade completado");
            return;
        }

        // Buscar al otro jugador online
        Player otherPlayer = plugin.getServer().getPlayer(otherPlayerUUID);

        if (otherPlayer != null && otherPlayer.isOnline()) {
            // Al otro jugador se le dice que use /mytrades para obtener sus items
            plugin.getMessageManager().sendComponentMessage(otherPlayer,
                    "viewtrade.gui.completion.items_available_mytrades",
                    "player", currentPlayer.getName());

            // Cerrar su GUI si la tiene abierta
            if (otherPlayer.getOpenInventory().getTopInventory().getHolder() instanceof ViewTradeGUI) {
                otherPlayer.closeInventory();
            }

            plugin.getLogger().info("Notificación de trade completado enviada a " + otherPlayer.getName());
        } else {
            plugin.getLogger().info("El otro jugador está offline, recibirá la notificación al conectarse");
        }
    }

    /**
     * Handle trade cancellation
     */
    private void handleCancelTrade(Player player) {
        // Verificar que el trade sea válido antes de cancelar
        plugin.getTradeManager().isTradeValid(tradeId)
                .thenAccept(isValid -> {
                    if (!isValid) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player, "general.invalid_trade_error");
                            player.closeInventory();
                        });
                        return;
                    }

                    // Cambiar el estado del trade a CANCELLED
                    plugin.getTradeManager().updateTradeState(tradeId,
                            com.prismamc.trade.manager.TradeManager.TradeState.CANCELLED)
                            .thenAccept(voidResult -> {
                                // Obtener los items del jugador que canceló para devolverlos
                                plugin.getTradeManager().getTradeItems(tradeId, player.getUniqueId())
                                        .thenAccept(playerItems -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                // Devolver items al jugador que canceló
                                                if (playerItems != null && !playerItems.isEmpty()) {
                                                    giveItemsToPlayer(player, playerItems);

                                                    // Limpiar los items del jugador en la base de datos
                                                    plugin.getTradeManager().storeTradeItems(tradeId,
                                                            player.getUniqueId(), new java.util.ArrayList<>());
                                                }

                                                // Mensaje de confirmación al jugador que canceló
                                                plugin.getMessageManager().sendComponentMessage(player,
                                                        "viewtrade.cancellation.trade_cancelled",
                                                        "trade_id", String.valueOf(tradeId));

                                                // Notificar al otro jugador
                                                notifyOtherPlayerTradeCancelled(player);

                                                // Cerrar el GUI
                                                player.closeInventory();
                                            });
                                        })
                                        .exceptionally(throwable -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                plugin.getLogger()
                                                        .severe("Error getting player items for cancellation: "
                                                                + throwable.getMessage());
                                                plugin.getMessageManager().sendComponentMessage(player,
                                                        "viewtrade.cancellation.error_retrieving_items");
                                            });
                                            return null;
                                        });
                            })
                            .exceptionally(throwable -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getLogger().severe("Error cancelling trade: " + throwable.getMessage());
                                    plugin.getMessageManager().sendComponentMessage(player,
                                            "viewtrade.cancellation.error_cancelling");
                                });
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("Error verifying trade validity: " + throwable.getMessage());
                        plugin.getMessageManager().sendComponentMessage(player, "general.verification_error",
                                "error", throwable.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Notificar al otro jugador que el trade fue cancelado
     */
    private void notifyOtherPlayerTradeCancelled(Player cancellingPlayer) {
        // Identificar al otro jugador usando UUIDs
        java.util.UUID otherPlayerUUID = getOtherPlayerUUID(cancellingPlayer.getUniqueId());

        if (otherPlayerUUID == null) {
            plugin.getLogger().warning("No se pudo identificar al otro jugador para notificación de trade cancelado");
            return;
        }

        // Buscar al otro jugador online
        Player otherPlayer = plugin.getServer().getPlayer(otherPlayerUUID);

        if (otherPlayer != null && otherPlayer.isOnline()) {
            // Mensaje con información sobre la cancelación y enlace a /mytrades
            plugin.getMessageManager().sendComponentMessage(otherPlayer,
                    "viewtrade.cancellation.other_player_cancelled_with_mytrades",
                    "player", cancellingPlayer.getName(),
                    "trade_id", String.valueOf(tradeId));

            // Crear mensaje clickeable para ir a /mytrades
            net.kyori.adventure.text.Component myTradesButton = plugin.getMessageManager()
                    .getComponent(otherPlayer, "viewtrade.cancellation.mytrades_button")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mytrades"));

            otherPlayer.sendMessage(myTradesButton);

            // Cerrar su GUI si la tiene abierta
            if (otherPlayer.getOpenInventory().getTopInventory().getHolder() instanceof ViewTradeGUI) {
                otherPlayer.closeInventory();
            }

            plugin.getLogger().info("Notificación de trade cancelado enviada a " + otherPlayer.getName());
        } else {
            plugin.getLogger().info("El otro jugador está offline, recibirá la notificación al conectarse");
        }
    }

    /**
     * Dar items al jugador, manejando inventario lleno
     */
    private void giveItemsToPlayer(Player player, List<org.bukkit.inventory.ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        org.bukkit.inventory.Inventory inventory = player.getInventory();
        boolean droppedItems = false;

        for (org.bukkit.inventory.ItemStack item : items) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                continue;
            }

            // Intentar agregar el item al inventario
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = inventory.addItem(item);

            // Si hay items sobrantes, dropearlos al suelo
            if (!leftover.isEmpty()) {
                droppedItems = true;
                for (org.bukkit.inventory.ItemStack leftoverItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                }
            }
        }

        // Notificar si se dropearon items
        if (droppedItems) {
            plugin.getMessageManager().sendComponentMessage(player, "mytrades.completed.inventory_full");
        }
    }
}