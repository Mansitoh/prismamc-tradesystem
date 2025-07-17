package com.prismamc.trade.gui.trade;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ViewTradeGUI - Advanced Trade Viewing and Confirmation Interface
 * 
 * This class provides a comprehensive GUI for viewing, managing, and confirming
 * trades
 * in the PrismaMC Trade System. It supports multiple viewing modes including
 * preview,
 * confirmation, and administrative views, with full multi-language support and
 * sophisticated trade state management.
 * 
 * Key Features:
 * - Multi-mode viewing (preview, confirmation, admin)
 * - Paginated item display for large trade inventories
 * - Real-time trade state validation and updates
 * - Offline player support with UUID and name caching
 * - Interactive confirmation system with both-party verification
 * - Intelligent button states based on trade progression
 * - Comprehensive error handling and user feedback
 * - Multi-language support with player-specific preferences
 * 
 * GUI Layout (54 slots):
 * - Slots 0-35: Paginated trade item display (36 items per page)
 * - Slot 36: Previous page navigation
 * - Slot 40: Trade information panel
 * - Slot 44: Next page navigation
 * - Slot 45: Cancel trade button
 * - Slot 49: Add items to trade button
 * - Slot 53: Trade confirmation button
 * - Other slots: Decorative borders
 * 
 * Viewing Modes:
 * - Preview: Read-only view of trade items
 * - Interactive: Full trade management with item addition
 * - Confirmation: Final trade confirmation interface
 * - Admin: Administrative oversight and monitoring
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class ViewTradeGUI extends GUI {

    // Core trade participants
    private final Player tradeInitiator;
    private final Player tradeTarget;
    private final Plugin plugin;
    private final List<ItemStack> initiatorItems;
    private final long tradeId;

    // Offline player support
    private final String initiatorName;
    private final java.util.UUID initiatorUUID;

    // Pagination and state management
    private int currentPage = 0;
    private boolean isOnlyPreview = false;
    private boolean isConfirmationView = false;
    private boolean isAdminView = false;

    // GUI slot configuration for optimal trade item display
    private static final int[] TRADE_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    // Interactive button slot positions
    private static final int ADD_ITEMS_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int CANCEL_TRADE_SLOT = 45;
    private static final int CONFIRM_TRADE_SLOT = 53;
    private static final int ITEMS_PER_PAGE = 36;

    /**
     * Sets the GUI to preview-only mode.
     * In this mode, interactive elements are disabled for read-only viewing.
     * 
     * @param isPreview True to enable preview mode, false for interactive mode
     */
    public void setOnlyPreview(boolean isPreview) {
        this.isOnlyPreview = isPreview;
    }

    /**
     * Sets the GUI to confirmation mode.
     * This mode displays confirmation buttons and enables final trade completion.
     * 
     * @param isConfirmation True to enable confirmation mode, false otherwise
     */
    public void setConfirmationView(boolean isConfirmation) {
        this.isConfirmationView = isConfirmation;
    }

    /**
     * Sets the GUI to administrative view mode.
     * This mode provides additional oversight capabilities for administrators.
     * 
     * @param isAdminView True to enable admin mode, false otherwise
     */
    public void setAdminView(boolean isAdminView) {
        this.isAdminView = isAdminView;
    }

    /**
     * Constructs a ViewTradeGUI for an online trade initiator.
     * This constructor is used when both players are currently online.
     * 
     * @param target         The player who will view the trade
     * @param initiator      The player who initiated the trade
     * @param plugin         The main plugin instance
     * @param initiatorItems List of items being offered by the initiator
     * @param tradeId        Unique identifier for this trade
     */
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
     * Constructs a ViewTradeGUI with offline player support.
     * This constructor handles cases where the trade initiator may be offline,
     * using cached name and UUID information for proper trade display.
     * 
     * @param target         The player who will view the trade
     * @param initiatorName  Cached name of the trade initiator
     * @param initiatorUUID  UUID of the trade initiator
     * @param plugin         The main plugin instance
     * @param initiatorItems List of items being offered by the initiator
     * @param tradeId        Unique identifier for this trade
     */
    public ViewTradeGUI(Player target, String initiatorName, java.util.UUID initiatorUUID, Plugin plugin,
            List<ItemStack> initiatorItems, long tradeId) {
        super(target, "Viewing Trade Items from " + initiatorName, 54);
        this.tradeTarget = target;
        this.tradeInitiator = plugin.getServer().getPlayer(initiatorUUID);
        this.plugin = plugin;
        this.initiatorItems = initiatorItems;
        this.tradeId = tradeId;
        this.initiatorName = initiatorName;
        this.initiatorUUID = initiatorUUID;
    }

    /**
     * Safely retrieves the trade initiator's name.
     * This method handles cases where the initiator might be offline or
     * the name might not be available, providing appropriate fallbacks.
     * 
     * @return The initiator's name or "Unknown Player" if unavailable
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
     * Safely retrieves the trade initiator's UUID.
     * This method provides a reliable way to get the initiator's UUID
     * even when the player object might not be available.
     * 
     * @return The initiator's UUID or null if unavailable
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

    /**
     * Initializes all GUI items with comprehensive trade validation.
     * This method performs the following operations:
     * 
     * 1. Validates trade existence and state
     * 2. Sets up decorative borders and navigation elements
     * 3. Configures interactive buttons based on viewing mode
     * 4. Displays paginated trade items
     * 5. Sets up confirmation buttons when appropriate
     * 
     * All operations are performed asynchronously with proper error handling
     * and user feedback through the message system.
     */
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
                        // Initialize decorative border elements with player language support
                        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

                        if (borderItem != null) {
                            for (int i = 36; i < 54; i++) {
                                if (i != ADD_ITEMS_SLOT && i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT
                                        && i != CANCEL_TRADE_SLOT && i != CONFIRM_TRADE_SLOT) {
                                    inventory.setItem(i, borderItem.clone());
                                }
                            }
                        }

                        // Configure pagination controls
                        updatePaginationButtons();

                        // Setup add items button based on viewing mode
                        ItemStack addItemsButton = plugin.getItemManager().getItemStack(owner, "gui.buttons.add_items",
                                "player", getInitiatorName());
                        if (addItemsButton != null) {
                            if (isOnlyPreview) {
                                inventory.setItem(ADD_ITEMS_SLOT, borderItem);
                            } else {
                                inventory.setItem(ADD_ITEMS_SLOT, addItemsButton);
                            }
                        }

                        // Configure cancel trade button
                        ItemStack cancelTradeButton = plugin.getItemManager().getItemStack(owner,
                                "gui.buttons.cancel_trade",
                                "trade_id", String.valueOf(tradeId));
                        if (cancelTradeButton != null) {
                            inventory.setItem(CANCEL_TRADE_SLOT, cancelTradeButton);
                        }

                        // Setup trade information panel
                        ItemStack infoSign = plugin.getItemManager().getItemStack(owner, "gui.info.view_trade_info",
                                "player", getInitiatorName(),
                                "trade_id", String.valueOf(tradeId),
                                "page", String.valueOf(currentPage + 1));
                        if (infoSign != null) {
                            inventory.setItem(INFO_SLOT, infoSign);
                        }

                        // Display paginated trade items
                        displayCurrentPageItems();

                        // Configure confirmation button based on viewing mode
                        if (isConfirmationView) {
                            setupConfirmationButton();
                        } else {
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
     * Displays the current page of trade items in the designated slots.
     * This method handles pagination logic and ensures proper item placement
     * within the available display area.
     */
    private void displayCurrentPageItems() {
        // Clear all trade item slots
        for (int slot : TRADE_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Calculate and display current page items
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && startIndex + i < initiatorItems.size(); i++) {
            inventory.setItem(TRADE_SLOTS[i], initiatorItems.get(startIndex + i));
        }
    }

    /**
     * Sets up the trade confirmation button with intelligent state detection.
     * This method performs the following operations:
     * 
     * 1. Checks if the current player has already accepted the trade
     * 2. Displays appropriate confirmation or waiting button
     * 3. Handles errors gracefully with fallback mechanisms
     * 4. Provides comprehensive logging for debugging
     */
    private void setupConfirmationButton() {
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
                        // Fallback: assume player hasn't accepted and show confirmation button
                        placeConfirmationButton(false);
                    });
                    return null;
                });
    }

    /**
     * Places the appropriate confirmation button in the GUI.
     * This method selects between confirmation and waiting buttons based
     * on the player's current acceptance status.
     * 
     * @param hasAccepted Whether the player has already accepted the trade
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

        // Fallback to custom button creation if ItemManager fails
        if (confirmationButton == null) {
            plugin.getLogger().warning("Could not get confirmation button from ItemManager, using fallback");
            confirmationButton = createFallbackConfirmButton(hasAccepted);
        }

        // Place the button in the designated slot
        if (confirmationButton != null) {
            inventory.setItem(CONFIRM_TRADE_SLOT, confirmationButton);
            plugin.getLogger().info("Confirmation button placed in slot " + CONFIRM_TRADE_SLOT +
                    " - hasAccepted: " + hasAccepted);
        } else {
            plugin.getLogger()
                    .severe("Could not create any confirmation button, neither from ItemManager nor fallback");
        }
    }

    /**
     * Creates a fallback confirmation button when ItemManager fails.
     * This method ensures that players always have access to trade confirmation
     * even if the item management system encounters errors.
     * 
     * @param hasAccepted Whether the player has already accepted the trade
     * @return A fallback ItemStack for trade confirmation
     */
    private ItemStack createFallbackConfirmButton(boolean hasAccepted) {
        try {
            org.bukkit.Material material = hasAccepted ? org.bukkit.Material.YELLOW_STAINED_GLASS_PANE
                    : org.bukkit.Material.EMERALD;
            ItemStack fallbackButton = new ItemStack(material);
            org.bukkit.inventory.meta.ItemMeta meta = fallbackButton.getItemMeta();

            if (meta != null) {
                if (hasAccepted) {
                    meta.setDisplayName("§e⏳ Waiting for other player...");
                    meta.setLore(java.util.Arrays.asList(
                            "§7You have already confirmed this trade",
                            "§7Waiting for " + getInitiatorName() + " to confirm",
                            "§7Trade ID: §f#" + tradeId));
                } else {
                    meta.setDisplayName("§a✅ CONFIRM TRADE");
                    meta.setLore(java.util.Arrays.asList(
                            "§7Click to confirm and complete",
                            "§7this trade with " + getInitiatorName(),
                            "§7Trade ID: §f#" + tradeId,
                            "",
                            "§c⚠ This action cannot be undone"));
                }
                fallbackButton.setItemMeta(meta);
            }

            return fallbackButton;
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating fallback button: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets up a decorative glass pane when confirmation is not available.
     * This method ensures visual consistency when the confirmation button
     * is not applicable for the current viewing mode.
     */
    private void setupDisabledConfirmationSlot() {
        // Attempt to get decorative glass from ItemManager
        ItemStack disabledGlass = plugin.getItemManager().getItemStack(owner, "gui.decorative.disabled_confirmation");

        // Create fallback if ItemManager item doesn't exist
        if (disabledGlass == null) {
            disabledGlass = createFallbackDisabledGlass();
        }

        // Place the decorative element in the confirmation slot
        if (disabledGlass != null) {
            inventory.setItem(CONFIRM_TRADE_SLOT, disabledGlass);
            plugin.getLogger()
                    .info("Decorative glass placed in slot " + CONFIRM_TRADE_SLOT + " (non-confirmation mode)");
        } else {
            plugin.getLogger().warning("Could not create decorative glass for confirmation slot");
            // Last resort: use border item if available
            ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");
            if (borderItem != null) {
                inventory.setItem(CONFIRM_TRADE_SLOT, borderItem.clone());
            }
        }
    }

    /**
     * Creates a fallback decorative glass pane.
     * This method provides a consistent visual element when the primary
     * decorative item is not available.
     * 
     * @return A decorative ItemStack or null if creation fails
     */
    private ItemStack createFallbackDisabledGlass() {
        try {
            ItemStack glassPane = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta meta = glassPane.getItemMeta();

            if (meta != null) {
                meta.setDisplayName("§7⚫ Disabled Slot");
                meta.setLore(java.util.Arrays.asList(
                        "§7This slot is not available",
                        "§7in the current view mode",
                        "",
                        "§8Trade ID: #" + tradeId));
                glassPane.setItemMeta(meta);
            }

            return glassPane;
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating fallback decorative glass: " + e.getMessage());
            return null;
        }
    }

    /**
     * Updates pagination buttons based on current page state.
     * This method enables or disables navigation buttons depending on
     * available pages and displays appropriate visual indicators.
     */
    private void updatePaginationButtons() {
        // Configure previous page button
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

        // Configure next page button
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

    /**
     * Determines if there are more pages available for navigation.
     * 
     * @return True if additional pages exist, false otherwise
     */
    private boolean hasNextPage() {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        return initiatorItems.size() > startIndex + ITEMS_PER_PAGE;
    }

    /**
     * Handles all inventory click events with comprehensive action routing.
     * This method processes clicks on various GUI elements including:
     * 
     * - Pagination controls (previous/next page)
     * - Add items button (opens PreTradeGUI)
     * - Cancel trade button (initiates trade cancellation)
     * - Confirmation button (processes trade confirmation)
     * 
     * All actions include proper validation and error handling.
     * 
     * @param event The inventory click event to process
     */
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        // Handle pagination controls
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

        // Handle add items action
        if (clickedSlot == ADD_ITEMS_SLOT && player.equals(tradeTarget) && !isOnlyPreview) {
            handleAddItemsClick(player);
        }

        // Handle trade cancellation
        if (clickedSlot == CANCEL_TRADE_SLOT && player.equals(tradeTarget)) {
            handleCancelTrade(player);
        }

        // Handle trade confirmation
        if (clickedSlot == CONFIRM_TRADE_SLOT && player.equals(owner) && isConfirmationView) {
            handleTradeConfirmation(player);
        }
    }

    /**
     * Handles the add items button click with trade validation.
     * This method validates the trade state and opens the PreTradeGUI
     * for the player to select items to add to the trade.
     * 
     * @param player The player who clicked the add items button
     */
    private void handleAddItemsClick(Player player) {
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
                        // Open PreTradeGUI for target to select items using offline-safe constructor
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

    /**
     * Handles trade confirmation with comprehensive validation and state
     * management.
     * This method performs the following operations:
     * 
     * 1. Checks if the player has already accepted the trade
     * 2. Updates player acceptance status if not already accepted
     * 3. Verifies if both players have accepted
     * 4. Completes the trade if both parties have confirmed
     * 5. Provides appropriate feedback and notifications
     * 
     * @param player The player attempting to confirm the trade
     */
    private void handleTradeConfirmation(Player player) {
        // Check if player has already accepted
        plugin.getTradeManager().hasPlayerAccepted(tradeId, player.getUniqueId())
                .thenAccept(hasAccepted -> {
                    if (hasAccepted) {
                        // Player already accepted, show appropriate message
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
                                                    // Update GUI to show waiting state
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
     * Completes the trade when both players have confirmed.
     * This method handles the final trade execution including:
     * 
     * 1. Marking the trade as completed in the database
     * 2. Notifying both players about trade completion
     * 3. Directing both players to use /mytrades to collect items
     * 4. Closing the GUI for the confirming player
     * 
     * @param player The player who triggered the trade completion
     */
    private void completeTrade(Player player) {
        plugin.getTradeManager().completeTrade(tradeId)
                .thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (success) {
                            // Success message for current player - direct them to /mytrades
                            String otherPlayerName = getOtherPlayerName(player.getUniqueId());
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "trade.success.completion.use_mytrades",
                                    "player", otherPlayerName,
                                    "trade_id", String.valueOf(tradeId));

                            // Notify the other player about trade completion
                            notifyOtherPlayerTradeCompleted(player);

                            // Close the current player's GUI
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
     * Determines the UUID of the other player in the trade.
     * This method safely identifies the counterpart player based on
     * the current player's UUID.
     * 
     * @param currentPlayerUUID The UUID of the current player
     * @return The UUID of the other player, or null if not identifiable
     */
    private java.util.UUID getOtherPlayerUUID(java.util.UUID currentPlayerUUID) {
        // If current player is the target, other is the initiator
        if (currentPlayerUUID.equals(tradeTarget.getUniqueId())) {
            return getInitiatorUUID();
        }
        // If current player is the initiator, other is the target
        else if (currentPlayerUUID.equals(getInitiatorUUID())) {
            return tradeTarget.getUniqueId();
        }

        return null; // Could not identify
    }

    /**
     * Determines the name of the other player in the trade.
     * This method safely identifies the counterpart player's name
     * based on the current player's UUID.
     * 
     * @param currentPlayerUUID The UUID of the current player
     * @return The name of the other player, or "Unknown Player" as fallback
     */
    private String getOtherPlayerName(java.util.UUID currentPlayerUUID) {
        // If current player is the target, other is the initiator
        if (currentPlayerUUID.equals(tradeTarget.getUniqueId())) {
            return getInitiatorName();
        }
        // If current player is the initiator, other is the target
        else if (currentPlayerUUID.equals(getInitiatorUUID())) {
            return tradeTarget.getName();
        }

        return "Unknown Player"; // Fallback
    }

    /**
     * Notifies the other player that the trade has been completed.
     * This method handles both online and offline notification scenarios,
     * directing online players to use /mytrades to collect their items.
     * 
     * @param currentPlayer The player who completed the trade
     */
    private void notifyOtherPlayerTradeCompleted(Player currentPlayer) {
        // Identify the other player using UUIDs
        java.util.UUID otherPlayerUUID = getOtherPlayerUUID(currentPlayer.getUniqueId());

        if (otherPlayerUUID == null) {
            plugin.getLogger().warning("Could not identify other player for trade completion notification");
            return;
        }

        // Find the other player if online
        Player otherPlayer = plugin.getServer().getPlayer(otherPlayerUUID);

        if (otherPlayer != null && otherPlayer.isOnline()) {
            // Notify online player to use /mytrades to collect items
            plugin.getMessageManager().sendComponentMessage(otherPlayer,
                    "viewtrade.gui.completion.items_available_mytrades",
                    "player", currentPlayer.getName());

            // Close their GUI if they have it open
            if (otherPlayer.getOpenInventory().getTopInventory().getHolder() instanceof ViewTradeGUI) {
                otherPlayer.closeInventory();
            }

            plugin.getLogger().info("Trade completion notification sent to " + otherPlayer.getName());
        } else {
            plugin.getLogger().info("Other player is offline, will receive notification when they connect");
        }
    }

    /**
     * Handles trade cancellation with comprehensive cleanup.
     * This method performs the following operations:
     * 
     * 1. Validates that the trade can be cancelled
     * 2. Updates the trade state to CANCELLED in the database
     * 3. Returns items to the cancelling player
     * 4. Notifies the other player about the cancellation
     * 5. Closes the GUI and provides appropriate feedback
     * 
     * @param player The player who initiated the cancellation
     */
    private void handleCancelTrade(Player player) {
        // Verify trade validity before cancellation
        plugin.getTradeManager().isTradeValid(tradeId)
                .thenAccept(isValid -> {
                    if (!isValid) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player, "general.invalid_trade_error");
                            player.closeInventory();
                        });
                        return;
                    }

                    // Update trade state to CANCELLED
                    plugin.getTradeManager().updateTradeState(tradeId,
                            com.prismamc.trade.manager.TradeManager.TradeState.CANCELLED)
                            .thenAccept(voidResult -> {
                                // Retrieve and return player's items
                                plugin.getTradeManager().getTradeItems(tradeId, player.getUniqueId())
                                        .thenAccept(playerItems -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                // Return items to cancelling player
                                                if (playerItems != null && !playerItems.isEmpty()) {
                                                    giveItemsToPlayer(player, playerItems);

                                                    // Clear player's items from database
                                                    plugin.getTradeManager().storeTradeItems(tradeId,
                                                            player.getUniqueId(), new java.util.ArrayList<>());
                                                }

                                                // Confirm cancellation to player
                                                plugin.getMessageManager().sendComponentMessage(player,
                                                        "viewtrade.cancellation.trade_cancelled",
                                                        "trade_id", String.valueOf(tradeId));

                                                // Notify the other player
                                                notifyOtherPlayerTradeCancelled(player);

                                                // Close the GUI
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
     * Notifies the other player that the trade has been cancelled.
     * This method handles both online and offline notification scenarios,
     * providing comprehensive information about the cancellation and
     * directing players to /mytrades for item recovery.
     * 
     * @param cancellingPlayer The player who cancelled the trade
     */
    private void notifyOtherPlayerTradeCancelled(Player cancellingPlayer) {
        // Identify the other player using UUIDs
        java.util.UUID otherPlayerUUID = getOtherPlayerUUID(cancellingPlayer.getUniqueId());

        if (otherPlayerUUID == null) {
            plugin.getLogger().warning("Could not identify other player for trade cancellation notification");
            return;
        }

        // Find the other player if online
        Player otherPlayer = plugin.getServer().getPlayer(otherPlayerUUID);

        if (otherPlayer != null && otherPlayer.isOnline()) {
            // Send cancellation notification with /mytrades link
            plugin.getMessageManager().sendComponentMessage(otherPlayer,
                    "viewtrade.cancellation.other_player_cancelled_with_mytrades",
                    "player", cancellingPlayer.getName(),
                    "trade_id", String.valueOf(tradeId));

            // Create clickable /mytrades button
            net.kyori.adventure.text.Component myTradesButton = plugin.getMessageManager()
                    .getComponent(otherPlayer, "viewtrade.cancellation.mytrades_button")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/mytrades"));

            otherPlayer.sendMessage(myTradesButton);

            // Close their GUI if they have it open
            if (otherPlayer.getOpenInventory().getTopInventory().getHolder() instanceof ViewTradeGUI) {
                otherPlayer.closeInventory();
            }

            plugin.getLogger().info("Trade cancellation notification sent to " + otherPlayer.getName());
        } else {
            plugin.getLogger().info("Other player is offline, will receive notification when they connect");
        }
    }

    /**
     * Safely gives items to a player with inventory overflow handling.
     * This method attempts to add items to the player's inventory and
     * drops any overflow items on the ground near the player's location.
     * 
     * @param player The player to receive the items
     * @param items  The list of items to give to the player
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

            // Attempt to add item to inventory
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = inventory.addItem(item);

            // Drop any overflow items on the ground
            if (!leftover.isEmpty()) {
                droppedItems = true;
                for (org.bukkit.inventory.ItemStack leftoverItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                }
            }
        }

        // Notify player if items were dropped
        if (droppedItems) {
            plugin.getMessageManager().sendComponentMessage(player, "mytrades.completed.inventory_full");
        }
    }
}