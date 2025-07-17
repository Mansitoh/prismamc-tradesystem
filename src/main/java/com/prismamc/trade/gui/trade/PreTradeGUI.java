package com.prismamc.trade.gui.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.manager.TradeManager;
import com.prismamc.trade.model.PlayerData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

/**
 * PreTradeGUI - Advanced Trade Item Selection and Preparation Interface
 * 
 * This class provides a sophisticated GUI for selecting and preparing items for
 * trades
 * in the PrismaMC Trade System. It supports both initial trade creation and
 * response
 * scenarios, with optimized performance through advanced caching, pagination,
 * and
 * asynchronous processing techniques.
 * 
 * Key Features:
 * - Dual-mode operation (new trade creation and trade response)
 * - High-performance paginated item selection (36 items per page)
 * - Real-time item management with intelligent merging algorithms
 * - Offline player support with cached player data
 * - Optimized state management with atomic operations
 * - Asynchronous database operations for zero UI blocking
 * - Advanced error handling with graceful fallbacks
 * - Multi-language support with player-specific preferences
 * 
 * Performance Optimizations:
 * - Static item caching for frequently used GUI elements
 * - Batch inventory operations to minimize UI updates
 * - Lazy updates with dirty state tracking
 * - Concurrent data structures for thread-safe operations
 * - Atomic boolean flags for race condition prevention
 * 
 * GUI Layout (54 slots):
 * - Slots 0-35: Paginated item selection grid (36 items per page)
 * - Slot 36: Previous page navigation
 * - Slot 40: Trade information panel
 * - Slot 44: Next page navigation
 * - Slot 49: Confirm trade button
 * - Other slots: Decorative borders
 * 
 * Operation Modes:
 * - New Trade: Initial trade creation with item selection
 * - Response: Adding items to an existing trade invitation
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class PreTradeGUI extends GUI {

    // Core trade participants and configuration
    private final Player initiator;
    private final Player target;
    private final PlayerData targetPlayerData;
    private final boolean isResponse;
    private long tradeId;
    private int currentPage = 0;

    // Optimized slot configuration for maximum item display efficiency
    private static final int[] TRADE_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    // Strategic button placement for optimal user experience
    private static final int CONFIRM_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int ITEMS_PER_PAGE = 36;

    // High-performance state management with thread-safe operations
    private final Map<Integer, ItemStack> itemSlots;
    private final AtomicBoolean closedByButton;
    private final AtomicBoolean isProcessing;
    private final AtomicBoolean isDirty;

    // Performance-optimized static cache for frequently used GUI elements
    private static final Map<String, ItemStack> CACHED_BORDER_ITEMS = new ConcurrentHashMap<>();

    static {
        // Pre-initialize common GUI elements for instant access
        CACHED_BORDER_ITEMS.put("border", new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName("§7 ").getItemStack());
        CACHED_BORDER_ITEMS.put("prevPage", new GUIItem(Material.ARROW)
                .setName("§ePrevious Page")
                .setLore("§7Click to go to the previous page").getItemStack());
        CACHED_BORDER_ITEMS.put("nextPage", new GUIItem(Material.ARROW)
                .setName("§eNext Page")
                .setLore("§7Click to go to the next page").getItemStack());
        CACHED_BORDER_ITEMS.put("disabledPage", new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName("§7 ").getItemStack());
    }

    /**
     * Creates a PreTradeGUI for a new trade with an online target player.
     * This constructor is used for initial trade creation scenarios.
     * 
     * @param owner  The player creating the trade
     * @param target The target player for the trade
     * @param plugin The main plugin instance
     */
    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        this(owner, target, plugin, false, -1);
    }

    /**
     * Creates a PreTradeGUI for a new trade using PlayerData.
     * This constructor supports offline players through cached player data.
     * 
     * @param owner            The player creating the trade
     * @param targetPlayerData Cached data for the target player
     * @param plugin           The main plugin instance
     */
    public PreTradeGUI(Player owner, PlayerData targetPlayerData, Plugin plugin) {
        this(owner, targetPlayerData, plugin, false, -1);
    }

    /**
     * Creates a PreTradeGUI with full configuration options.
     * This constructor supports both new trades and responses to existing trades.
     * 
     * @param owner      The player using the GUI
     * @param target     The target player (may be null if offline)
     * @param plugin     The main plugin instance
     * @param isResponse Whether this is a response to an existing trade
     * @param tradeId    The trade ID if responding to an existing trade
     */
    public PreTradeGUI(Player owner, Player target, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = target;
        this.targetPlayerData = null;
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    /**
     * Creates a PreTradeGUI with PlayerData support for offline players.
     * This constructor provides full functionality even when the target player is
     * offline.
     * 
     * @param owner            The player using the GUI
     * @param targetPlayerData Cached player data for the target
     * @param plugin           The main plugin instance
     * @param isResponse       Whether this is a response to an existing trade
     * @param tradeId          The trade ID if responding to an existing trade
     */
    public PreTradeGUI(Player owner, PlayerData targetPlayerData, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = Bukkit.getPlayer(targetPlayerData.getUuid());
        this.targetPlayerData = targetPlayerData;
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    /**
     * Creates a PreTradeGUI with offline player support using name and UUID.
     * This constructor handles scenarios where only basic player identification is
     * available.
     * 
     * @param owner            The player using the GUI
     * @param targetPlayerName The name of the target player
     * @param targetPlayerUUID The UUID of the target player
     * @param plugin           The main plugin instance
     * @param isResponse       Whether this is a response to an existing trade
     * @param tradeId          The trade ID if responding to an existing trade
     */
    public PreTradeGUI(Player owner, String targetPlayerName, java.util.UUID targetPlayerUUID, Plugin plugin,
            boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = Bukkit.getPlayer(targetPlayerUUID);
        // Create temporary PlayerData with available information
        this.targetPlayerData = new PlayerData(targetPlayerUUID, targetPlayerName, "Unknown");
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    /**
     * Generates the appropriate GUI title based on operation mode and player
     * language.
     * This method ensures consistent title display across different trade
     * scenarios.
     * 
     * @param plugin     The plugin instance for message access
     * @param player     The player for language-specific title
     * @param isResponse Whether this is a response GUI
     * @return Localized GUI title string
     */
    private static String getGUITitle(Plugin plugin, Player player, boolean isResponse) {
        String key = isResponse ? "pretrade.gui.title.response" : "pretrade.gui.title.new";
        return plugin.getMessageManager().getRawMessage(player, key);
    }

    /**
     * Initializes all GUI items with intelligent mode detection.
     * This method determines the appropriate initialization strategy based on
     * whether this is a new trade or a response to an existing trade.
     */
    @Override
    protected void initializeItems() {
        if (isResponse && tradeId != -1) {
            validateAndInitialize();
        } else {
            setupInventoryItems();
        }
    }

    /**
     * Validates trade existence and initializes the GUI for response mode.
     * This method performs asynchronous trade validation before proceeding
     * with GUI setup to ensure data integrity.
     */
    private void validateAndInitialize() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::validateAndInitialize);
            return;
        }

        plugin.getTradeManager().isTradeValid(tradeId)
                .thenAccept(isValid -> {
                    if (!isValid) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!isClosed()) {
                                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.invalid_trade");
                                owner.closeInventory();
                            }
                        });
                        return;
                    }

                    // Execute setup synchronously if already on main thread
                    if (Bukkit.isPrimaryThread()) {
                        setupInventoryItems();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, this::setupInventoryItems);
                    }
                })
                .exceptionally(throwable -> {
                    if (Bukkit.isPrimaryThread()) {
                        handleError(throwable.getMessage());
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> handleError(throwable.getMessage()));
                    }
                    return null;
                });
    }

    /**
     * Handles initialization errors with appropriate user feedback.
     * This method provides consistent error handling across different failure
     * scenarios.
     * 
     * @param errorMessage The error message to display to the user
     */
    private void handleError(String errorMessage) {
        if (!isClosed()) {
            plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.verify_trade", "error",
                    errorMessage);
            owner.closeInventory();
        }
    }

    /**
     * Sets up all inventory items with optimized batch operations.
     * This method coordinates the initialization of all GUI components
     * in a performance-optimized manner to minimize UI updates.
     */
    private void setupInventoryItems() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::setupInventoryItems);
            return;
        }

        // Batch all inventory operations for optimal performance
        setupBorders();
        updatePaginationButtons();
        setupConfirmButton();
        setupInfoSign();
        updatePageItems();
    }

    /**
     * Sets up decorative border elements using the ItemManager system.
     * This method creates a visually appealing border around the functional areas
     * while preserving interactive elements.
     */
    private void setupBorders() {
        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

        if (borderItem != null) {
            for (int i = 36; i < 54; i++) {
                if (i != INFO_SLOT && i != CONFIRM_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT) {
                    inventory.setItem(i, borderItem.clone());
                }
            }
        }
    }

    /**
     * Sets up the confirmation button with context-appropriate messaging.
     * This method configures the confirm button based on whether this is
     * a new trade creation or a response to an existing trade.
     */
    private void setupConfirmButton() {
        String buttonKey = isResponse ? "gui.buttons.confirm_response" : "gui.buttons.confirm_new";
        String targetPlayer = getTargetPlayerName();

        // Get item from ItemManager with player language support and dynamic
        // replacement
        ItemStack confirmButton = plugin.getItemManager().getItemStack(owner, buttonKey, "player", targetPlayer);

        if (confirmButton != null) {
            inventory.setItem(CONFIRM_SLOT, confirmButton);
        } else {
            // Fallback if item not found
            plugin.getLogger().warning("Confirm button item not found: " + buttonKey);
        }
    }

    /**
     * Sets up the information panel with dynamic trade details.
     * This method creates a comprehensive information display showing
     * current trade status, pagination, and item counts.
     */
    private void setupInfoSign() {
        String tradingWith = isResponse ? initiator.getName() : getTargetPlayerName();
        String tradeIdText = tradeId != -1 ? String.valueOf(tradeId) : "Pending...";

        // Get base info item from ItemManager with player language support
        ItemStack infoItem = plugin.getItemManager().getItemStack(owner, "gui.info.trade_info",
                "player", tradingWith,
                "trade_id", tradeIdText,
                "page", String.valueOf(currentPage + 1),
                "items", String.valueOf(itemSlots.size()));

        if (infoItem != null) {
            inventory.setItem(INFO_SLOT, infoItem);
        } else {
            // Fallback if item not found
            plugin.getLogger().warning("Info sign item not found: gui.info.trade_info");
        }
    }

    /**
     * Safely retrieves the target player's name with fallback handling.
     * This method provides consistent name resolution across different
     * player data sources and offline scenarios.
     * 
     * @return The target player's name or "Unknown Player" as fallback
     */
    private String getTargetPlayerName() {
        if (targetPlayerData != null) {
            return targetPlayerData.getPlayerName();
        } else if (target != null) {
            return target.getName();
        } else {
            return "Unknown Player";
        }
    }

    /**
     * Safely retrieves the target player's UUID.
     * This method provides consistent UUID resolution for database operations
     * and player identification across different scenarios.
     * 
     * @return The target player's UUID or null if unavailable
     */
    private java.util.UUID getTargetPlayerUUID() {
        if (targetPlayerData != null) {
            return targetPlayerData.getUuid();
        } else if (target != null) {
            return target.getUniqueId();
        } else {
            return null;
        }
    }

    /**
     * Updates pagination buttons based on current page state.
     * This method configures navigation controls with appropriate enable/disable
     * states and uses player-specific language preferences.
     */
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

        // Next page button with player language support
        ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
        if (nextPageItem != null) {
            inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
        }
    }

    /**
     * Updates the current page display with optimized item placement.
     * This method efficiently manages the display of items for the current page
     * while preserving item data across page navigation.
     */
    private void updatePageItems() {
        // Clear current page slots efficiently
        for (int slot : TRADE_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Display items for current page with optimized indexing
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = startIndex + ITEMS_PER_PAGE;

        for (Map.Entry<Integer, ItemStack> entry : itemSlots.entrySet()) {
            int slot = entry.getKey();
            if (slot >= startIndex && slot < endIndex) {
                ItemStack item = entry.getValue();
                if (item != null && item.getType() != Material.AIR) {
                    int displaySlot = slot - startIndex;
                    if (displaySlot >= 0 && displaySlot < ITEMS_PER_PAGE) {
                        inventory.setItem(TRADE_SLOTS[displaySlot], item.clone());
                    }
                }
            }
        }
    }

    /**
     * Handles all inventory click events with intelligent routing.
     * This method processes different types of clicks including navigation,
     * confirmation, and item selection with appropriate event handling.
     * 
     * @param event The inventory click event to process
     */
    @Override
    public void handleClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();

        // Handle confirm button with immediate response
        if (clickedSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked().equals(owner)) {
                handleConfirmClick();
            }
            return;
        }

        // Handle navigation controls
        if (handleNavigationClick(event)) {
            return;
        }

        // Handle trade item slots with optimized processing
        if (isValidTradeSlot(clickedSlot)) {
            handleTradeSlotClick(event);
            event.setCancelled(false);
        } else {
            // Cancel clicks outside valid areas
            if (clickedSlot < 54) {
                event.setCancelled(true);
            } else {
                event.setCancelled(false);
            }
        }
    }

    /**
     * Validates whether a slot is a valid trade item slot.
     * This method provides consistent validation for trade item placement.
     * 
     * @param slot The slot number to validate
     * @return True if the slot is valid for trade items, false otherwise
     */
    private boolean isValidTradeSlot(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE;
    }

    /**
     * Handles navigation button clicks with page management.
     * This method processes pagination controls and manages page transitions
     * with proper state preservation.
     * 
     * @param event The inventory click event
     * @return True if the click was handled as navigation, false otherwise
     */
    private boolean handleNavigationClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();
        event.setCancelled(true);

        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            navigateToPage(currentPage - 1);
            return true;
        }

        if (clickedSlot == NEXT_PAGE_SLOT) {
            navigateToPage(currentPage + 1);
            return true;
        }

        return false;
    }

    /**
     * Navigates to a specific page with optimized state management.
     * This method handles page transitions while preserving item data
     * and updating all relevant GUI components.
     * 
     * @param newPage The page number to navigate to
     */
    private void navigateToPage(int newPage) {
        saveCurrentPageItems();
        currentPage = newPage;

        // Batch update all page elements for optimal performance
        updatePaginationButtons();
        updatePageItems();
        setupInfoSign();
    }

    /**
     * Handles trade slot clicks with performance-optimized batching.
     * This method processes item selection clicks while minimizing
     * unnecessary updates through intelligent batching.
     * 
     * @param event The inventory click event
     */
    private void handleTradeSlotClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            // Mark as dirty for lazy updates
            isDirty.set(true);

            // Schedule delayed update to batch multiple rapid clicks
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isDirty.get()) {
                    saveCurrentPageItems();
                    setupInfoSign();
                    isDirty.set(false);
                }
            }, 2L); // 2 ticks delay for optimal batching
        }
    }

    /**
     * Saves the current page items to persistent storage.
     * This method efficiently captures the current state of items
     * on the page for preservation across navigation.
     */
    private void saveCurrentPageItems() {
        int startIndex = currentPage * ITEMS_PER_PAGE;

        // Efficiently save current page items with optimized indexing
        for (int i = 0; i < ITEMS_PER_PAGE && i < TRADE_SLOTS.length; i++) {
            ItemStack item = inventory.getItem(TRADE_SLOTS[i]);
            int realSlot = startIndex + i;

            if (item != null && item.getType() != Material.AIR) {
                itemSlots.put(realSlot, item.clone());
            } else {
                itemSlots.remove(realSlot);
            }
        }
    }

    /**
     * Handles confirmation button clicks with comprehensive processing.
     * This method manages the complete confirmation workflow including
     * validation, UI response, and asynchronous database operations.
     */
    private void handleConfirmClick() {
        if (isProcessing.getAndSet(true)) {
            return; // Prevent double-processing with atomic operation
        }

        try {
            saveCurrentPageItems();

            // Validate items using MessageManager
            if (itemSlots.isEmpty()) {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.no_items");
                isProcessing.set(false);
                return;
            }

            // Immediate UI response - close menu and send confirmation message
            closedByButton.set(true);
            owner.closeInventory();

            // Send appropriate confirmation message based on action type
            if (isResponse) {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.notifications.items_sent");
            } else {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.notifications.items_sent_to", "player",
                        getTargetPlayerName());
            }

            // Process database operations asynchronously to prevent blocking
            if (isResponse) {
                handleResponseConfirmationAsync();
            } else {
                handleInitialConfirmationAsync();
            }

        } catch (Exception e) {
            isProcessing.set(false);
            plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.verify_trade", "error",
                    e.getMessage());
        }
    }

    /**
     * Handles asynchronous confirmation processing for trade responses.
     * This method manages the complete workflow for responding to existing
     * trades including validation, state updates, and GUI transitions.
     */
    private void handleResponseConfirmationAsync() {
        // All database operations happen asynchronously to prevent UI blocking
        CompletableFuture.runAsync(() -> {
            plugin.getTradeManager().isTradeValid(tradeId)
                    .thenCompose(isValid -> {
                        if (!isValid) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.invalid_trade");
                            });
                            return CompletableFuture.completedFuture(null);
                        }

                        return plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.ACTIVE)
                                .thenCompose(v -> plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(),
                                        getAllPagesItems()));
                    })
                    .thenRun(() -> {
                        // Final trade setup - notify players when database operations complete
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            openTradeGUI();
                        });
                    })
                    .exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.verify_trade",
                                    "error", throwable.getMessage());
                            plugin.getLogger()
                                    .severe("Error in handleResponseConfirmationAsync: " + throwable.getMessage());
                        });
                        return null;
                    })
                    .whenComplete((result, ex) -> {
                        isProcessing.set(false);
                    });
        });
    }

    /**
     * Handles asynchronous confirmation processing for initial trade creation.
     * This method manages the complete workflow for creating new trades
     * including database creation, item storage, and notification systems.
     */
    private void handleInitialConfirmationAsync() {
        // All database operations happen asynchronously to prevent UI blocking
        CompletableFuture.runAsync(() -> {
            plugin.getTradeManager().createNewTrade(owner.getUniqueId(), getTargetPlayerUUID())
                    .thenCompose(newTradeId -> {
                        tradeId = newTradeId;
                        return plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(),
                                getAllPagesItems());
                    })
                    .thenRun(() -> {
                        // Send trade request notification when database operations complete
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sendViewTradeRequest();
                        });
                    })
                    .exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.verify_trade",
                                    "error", throwable.getMessage());
                            plugin.getLogger()
                                    .severe("Error in handleInitialConfirmationAsync: " + throwable.getMessage());
                        });
                        return null;
                    })
                    .whenComplete((result, ex) -> {
                        isProcessing.set(false);
                    });
        });
    }

    /**
     * Opens the trade GUI for active trade management.
     * This method coordinates the transition from item selection to active
     * trade management with appropriate notifications for all participants.
     */
    private void openTradeGUI() {
        // Send notifications only if target is online
        if (target != null && target.isOnline()) {
            // Send confirmation messages using MessageManager
            plugin.getMessageManager().sendComponentMessage(target, "pretrade.notifications.trade_accepted", "player",
                    initiator.getName());

            // Create clickable confirmation messages for both participants
            Component confirmMessageInitiator = plugin.getMessageManager()
                    .getComponent(initiator, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + getTargetPlayerName() + " " + tradeId));
            initiator.sendMessage(confirmMessageInitiator);

            Component confirmMessageTarget = plugin.getMessageManager()
                    .getComponent(target, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + initiator.getName() + " " + tradeId));
            target.sendMessage(confirmMessageTarget);
        } else {
            // Target is offline - notify only the initiator
            Component confirmMessageInitiator = plugin.getMessageManager()
                    .getComponent(initiator, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + getTargetPlayerName() + " " + tradeId));
            initiator.sendMessage(confirmMessageInitiator);

            // Notify that the other player is offline
            plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.target_offline",
                    "player", getTargetPlayerName());
        }
    }

    /**
     * Sends trade request notifications to appropriate participants.
     * This method handles both online and offline scenarios for trade
     * request delivery with comprehensive user feedback.
     */
    private void sendViewTradeRequest() {
        // Send immediate success notification to initiator
        plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.trade_request_sent",
                "player", getTargetPlayerName(), "trade_id", tradeId);

        // Send notification to target only if online
        if (target != null && target.isOnline()) {
            // Send comprehensive notification to target
            plugin.getMessageManager().sendComponentMessage(target, "pretrade.notifications.trade_request_alert",
                    "trade_id", tradeId);

            // Create clickable view button for immediate action
            Component viewButton = plugin.getMessageManager()
                    .getComponent(target, "pretrade.buttons.view_trade_with_sender", "player", initiator.getName(),
                            "trade_id", String.valueOf(tradeId))
                    .clickEvent(ClickEvent.runCommand("/tradeaccept " + initiator.getName() + " " + tradeId));
            target.sendMessage(viewButton);
        } else {
            // Target is offline - notify initiator
            plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.trade_sent_offline",
                    "player", getTargetPlayerName());
        }
    }

    /**
     * Retrieves all items from all pages with intelligent merging.
     * This method efficiently combines items across all pages while
     * respecting stack limits and maintaining item integrity.
     * 
     * @return List of merged ItemStacks ready for trade processing
     */
    public List<ItemStack> getAllPagesItems() {
        saveCurrentPageItems();

        // Optimized item merging with enhanced performance
        Map<String, ItemStack> mergedItems = new HashMap<>();

        for (ItemStack item : itemSlots.values()) {
            if (item == null || item.getType() == Material.AIR)
                continue;

            String key = getItemKey(item);
            ItemStack existing = mergedItems.get(key);

            if (existing == null) {
                mergedItems.put(key, item.clone());
            } else {
                int maxStack = item.getMaxStackSize();
                int currentAmount = existing.getAmount();
                int addAmount = item.getAmount();

                if (currentAmount + addAmount <= maxStack) {
                    existing.setAmount(currentAmount + addAmount);
                } else {
                    // Create new stack if exceeds maximum stack size
                    ItemStack newStack = item.clone();
                    newStack.setAmount(addAmount);
                    mergedItems.put(key + "_" + mergedItems.size(), newStack);
                }
            }
        }

        return new ArrayList<>(mergedItems.values());
    }

    /**
     * Generates a unique key for item identification and merging.
     * This method creates comprehensive item signatures for accurate
     * duplicate detection and stack merging operations.
     * 
     * @param item The ItemStack to generate a key for
     * @return Unique string key representing the item's properties
     */
    private String getItemKey(ItemStack item) {
        if (item == null)
            return "null";

        StringBuilder key = new StringBuilder(item.getType().name());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                key.append("_").append(meta.displayName().toString());
            }
            if (meta.hasLore()) {
                key.append("_").append(meta.lore().toString());
            }
            if (meta.hasEnchants()) {
                key.append("_").append(meta.getEnchants().toString());
            }
        }

        return key.toString();
    }

    /**
     * Provides access to the trade slot configuration.
     * This method returns a copy of the trade slots array for external
     * use while maintaining encapsulation.
     * 
     * @return Copy of the trade slots array
     */
    public static int[] getTradeSlots() {
        return TRADE_SLOTS.clone();
    }

    /**
     * Checks if the GUI was closed by a button action.
     * This method provides information about how the GUI was closed,
     * useful for determining appropriate cleanup actions.
     * 
     * @return True if closed by button, false if closed by other means
     */
    public boolean wasClosedByButton() {
        return closedByButton.get();
    }

    /**
     * Handles GUI closure with proper cleanup operations.
     * This method ensures all pending operations are properly cleaned up
     * when the GUI is closed to prevent memory leaks.
     * 
     * @param player The player who closed the GUI
     */
    @Override
    public void onClose(Player player) {
        super.onClose(player);
        // Clean up any pending operations and atomic states
        isProcessing.set(false);
        isDirty.set(false);
    }
}
