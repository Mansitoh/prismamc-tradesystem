package com.prismamc.trade.gui.trade;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.model.TradeDocument;
import com.prismamc.trade.manager.TradeManager.TradeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MyTradesGUI - Comprehensive Trade Management and History Interface
 * 
 * This class provides an advanced GUI for managing and viewing all trades
 * associated
 * with a player in the PrismaMC Trade System. It features sophisticated
 * filtering,
 * pagination, and state-based interactions to provide a complete trade
 * management
 * experience for players.
 * 
 * Key Features:
 * - Advanced trade filtering by state (All, Pending, Active, Completed,
 * Cancelled)
 * - High-performance paginated trade display (45 trades per page)
 * - Real-time trade state visualization with color-coded indicators
 * - Interactive trade management with context-sensitive actions
 * - Automatic item collection for completed trades
 * - Comprehensive trade history with detailed information
 * - Offline player support for historical trade data
 * - Multi-language support with player-specific preferences
 * - Asynchronous data loading for optimal performance
 * 
 * GUI Layout (54 slots):
 * - Slots 0-44: Paginated trade display grid (45 trades per page)
 * - Slot 45: Previous page navigation
 * - Slot 46: "All Trades" filter button
 * - Slot 47: "Pending Trades" filter button
 * - Slot 48: "Active Trades" filter button
 * - Slot 49: Trade information panel
 * - Slot 50: "Completed Trades" filter button
 * - Slot 51: "Cancelled Trades" filter button
 * - Slot 53: Next page navigation
 * - Other slots: Decorative borders
 * 
 * Trade Interaction Modes:
 * - Pending Trades: Add items or view existing items
 * - Active Trades: View and confirm trades
 * - Completed Trades: Collect items automatically
 * - Cancelled Trades: View historical information
 * 
 * Performance Features:
 * - Asynchronous MongoDB operations
 * - Efficient filter state management
 * - Smart pagination with optimized rendering
 * - Lazy loading of trade item data
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class MyTradesGUI extends GUI {
    private final Plugin plugin;

    // Trade data management with separation of concerns
    private List<TradeDocument> allTrades; // All trades without filtering
    private List<TradeDocument> filteredTrades; // Trades after applying filters

    // Pagination and filtering state
    private int currentPage = 0;
    private TradeFilter currentFilter = TradeFilter.ALL;

    // Optimized layout configuration for maximum trade display
    private static final int ITEMS_PER_PAGE = 45; // Maximum trades per page using main area
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int INFO_SLOT = 49;

    // Strategic filter button placement for intuitive user experience
    private static final int FILTER_ALL_SLOT = 46;
    private static final int FILTER_PENDING_SLOT = 47;
    private static final int FILTER_ACTIVE_SLOT = 48;
    private static final int FILTER_COMPLETED_SLOT = 50;
    private static final int FILTER_CANCELLED_SLOT = 51;

    /**
     * TradeFilter - Enumeration of available trade filtering options
     * 
     * This enum defines all available filter types with their display properties
     * and provides a comprehensive way to categorize and filter trades based
     * on their current state.
     */
    public enum TradeFilter {
        ALL("All Trades", Material.CHEST),
        PENDING("Pending", Material.YELLOW_WOOL),
        ACTIVE("Active", Material.LIME_WOOL),
        COMPLETED("Completed", Material.BLUE_WOOL),
        CANCELLED("Cancelled", Material.RED_WOOL);

        private final String displayName;
        private final Material material;

        /**
         * Constructs a TradeFilter with display properties.
         * 
         * @param displayName The human-readable name for this filter
         * @param material    The material to use for the filter button
         */
        TradeFilter(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        /**
         * Gets the display name for this filter.
         * 
         * @return The human-readable filter name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Gets the material for this filter's button.
         * 
         * @return The Material to use for the filter button
         */
        public Material getMaterial() {
            return material;
        }
    }

    /**
     * Creates a MyTradesGUI for comprehensive trade management.
     * This constructor initializes the GUI with proper player context
     * and prepares the data structures for efficient trade management.
     * 
     * @param owner  The player who owns this trade GUI
     * @param plugin The main plugin instance
     */
    public MyTradesGUI(Player owner, Plugin plugin) {
        super(owner, "My Trades", 54);
        this.plugin = plugin;
        this.allTrades = new ArrayList<>();
        this.filteredTrades = new ArrayList<>();
    }

    /**
     * Initializes all GUI items with asynchronous trade loading.
     * This method coordinates the complete GUI setup process including
     * data loading, filtering, and display initialization.
     */
    @Override
    protected void initializeItems() {
        loadTrades().thenRun(() -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                setupFilterButtons();
                setupBorders();
                updatePaginationButtons();
                setupInfoSign();
                displayTrades();
            });
        });
    }

    /**
     * Asynchronously loads all trades associated with the player.
     * This method performs efficient database queries to retrieve all trade
     * documents where the player is either the initiator or target.
     * 
     * @return CompletableFuture that completes when all trades are loaded
     */
    private CompletableFuture<Void> loadTrades() {
        return CompletableFuture.supplyAsync(() -> {
            List<TradeDocument> loadedTrades = new ArrayList<>();

            try {
                // Retrieve ALL trade documents involving the player using MongoDB aggregation
                plugin.getMongoDBManager().getTradesCollection()
                        .find(new org.bson.Document("$or", List.of(
                                new org.bson.Document("player1", owner.getUniqueId().toString()),
                                new org.bson.Document("player2", owner.getUniqueId().toString()))))
                        .forEach(doc -> {
                            TradeDocument trade = new TradeDocument(doc);
                            loadedTrades.add(trade); // Add ALL trades without filtering
                        });

            } catch (Exception e) {
                plugin.getLogger()
                        .severe(String.format("❌ ERROR loading trades for %s: %s", owner.getName(), e.getMessage()));
                e.printStackTrace();
            }

            this.allTrades = loadedTrades;
            this.filteredTrades = new ArrayList<>(loadedTrades); // Initially show all trades
            return null;
        });
    }

    /**
     * Sets up decorative border elements using the ItemManager system.
     * This method creates visual separation between functional areas
     * while preserving space for interactive elements.
     */
    private void setupBorders() {
        // Use ItemManager like in PreTradeGUI for consistency
        ItemStack borderItem = plugin.getItemManager().getItemStack(owner, "gui.decorative.border");

        if (borderItem != null) {
            for (int i = 45; i < 54; i++) {
                // Exclude slots occupied by other functional elements
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, borderItem.clone());
                }
            }
        } else {
            // Fallback if ItemManager item doesn't exist
            GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                    .setName("§7 ");

            for (int i = 45; i < 54; i++) {
                // Exclude slots occupied by other functional elements
                if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT &&
                        i != FILTER_ALL_SLOT && i != FILTER_PENDING_SLOT && i != FILTER_ACTIVE_SLOT &&
                        i != FILTER_COMPLETED_SLOT && i != FILTER_CANCELLED_SLOT) {
                    inventory.setItem(i, border.getItemStack());
                }
            }
        }
    }

    /**
     * Updates pagination buttons based on current page state.
     * This method configures navigation controls with appropriate enable/disable
     * states using the ItemManager system for consistency.
     */
    private void updatePaginationButtons() {
        // Configure previous page button with ItemManager integration
        if (currentPage > 0) {
            ItemStack prevPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.previous_page");
            if (prevPageItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, prevPageItem);
            } else {
                // Fallback for missing ItemManager configuration
                GUIItem prevPage = new GUIItem(Material.ARROW)
                        .setName("§ePrevious Page")
                        .setLore("§7Click to go to the previous page");
                inventory.setItem(PREV_PAGE_SLOT, prevPage.getItemStack());
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(PREV_PAGE_SLOT, disabledItem);
            } else {
                // Fallback disabled state
                inventory.setItem(PREV_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }

        // Configure next page button with ItemManager integration
        if (hasNextPage()) {
            ItemStack nextPageItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.next_page");
            if (nextPageItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, nextPageItem);
            } else {
                // Fallback for missing ItemManager configuration
                GUIItem nextPage = new GUIItem(Material.ARROW)
                        .setName("§eNext Page")
                        .setLore("§7Click to go to the next page");
                inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());
            }
        } else {
            ItemStack disabledItem = plugin.getItemManager().getItemStack(owner, "gui.navigation.disabled_page");
            if (disabledItem != null) {
                inventory.setItem(NEXT_PAGE_SLOT, disabledItem);
            } else {
                // Fallback disabled state
                inventory.setItem(NEXT_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                        .setName("§7 ").getItemStack());
            }
        }
    }

    /**
     * Sets up the information panel with comprehensive trade statistics.
     * This method creates a detailed information display showing current
     * filter state, trade counts, and pagination information.
     */
    private void setupInfoSign() {
        // Calculate pagination information for display
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredTrades.size() / ITEMS_PER_PAGE));

        // Use specific ItemManager configuration for MyTradesGUI information
        ItemStack infoItem = plugin.getItemManager().getItemStack(owner, "gui.info.my_trades_info",
                "total_trades", String.valueOf(allTrades.size()),
                "filter_name", currentFilter.getDisplayName(),
                "filtered_count", String.valueOf(filteredTrades.size()),
                "current_page", String.valueOf(currentPage + 1),
                "total_pages", String.valueOf(totalPages));

        if (infoItem != null) {
            inventory.setItem(INFO_SLOT, infoItem);
        } else {
            // Fallback if ItemManager item doesn't exist
            GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
                    .setName("§eInformation")
                    .setLore(
                            "§7Total trades: §f" + filteredTrades.size(),
                            "§7Page: §f" + (currentPage + 1));
            inventory.setItem(INFO_SLOT, infoSign.getItemStack());
        }
    }

    /**
     * Displays trades for the current page with optimized rendering.
     * This method efficiently renders trade information with player-specific
     * context and state-based visual indicators.
     */
    private void displayTrades() {
        // Clear previous trade slots for clean rendering
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        // Calculate current page boundaries
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredTrades.size());

        // Render each trade with comprehensive information
        for (int i = startIndex; i < endIndex; i++) {
            TradeDocument trade = filteredTrades.get(i);
            int slot = i - startIndex;

            // Determine player role in the trade (initiator or target)
            boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
            String otherPlayerUUID = isPlayer1 ? trade.getPlayer2().toString() : trade.getPlayer1().toString();
            String otherPlayerName = plugin.getServer().getOfflinePlayer(UUID.fromString(otherPlayerUUID)).getName();

            // Generate display strings with formatting for visual clarity
            String tradeState = getTradeStateDisplayName(trade.getState());
            String itemsReceived = isPlayer1 ? getItemsReceivedDisplayName(trade.areItemsSentToPlayer1())
                    : getItemsReceivedDisplayName(trade.areItemsSentToPlayer2());

            // Attempt to use ItemManager for consistent trade display
            ItemStack tradeItem = plugin.getItemManager().getItemStack(owner, "gui.buttons.trade_display",
                    "trade_id", String.valueOf(trade.getTradeId()),
                    "state", tradeState,
                    "other_player", otherPlayerName,
                    "items_received", itemsReceived);

            if (tradeItem == null) {
                // Fallback using manual GUIItem creation
                Material material = trade.getState() == TradeState.ACTIVE ? Material.LIME_WOOL : Material.YELLOW_WOOL;
                GUIItem fallbackItem = new GUIItem(material)
                        .setName("§eTrade #" + trade.getTradeId())
                        .setLore(
                                "§7State: " + tradeState,
                                "§7Trading with: §f" + otherPlayerName,
                                "§7Items received: " + itemsReceived,
                                "",
                                "§eClick to view details");
                tradeItem = fallbackItem.getItemStack();
            }

            inventory.setItem(slot, tradeItem);
        }
    }

    /**
     * Generates display name for trade state with color formatting.
     * This method provides consistent visual representation of trade states
     * using MiniMessage format for rich text display.
     * 
     * @param state The TradeState to format
     * @return Formatted string with appropriate color coding
     */
    private String getTradeStateDisplayName(TradeState state) {
        switch (state) {
            case ACTIVE:
                return "<green>Active</green>";
            case COMPLETED:
                return "<gold>Completed</gold>";
            case PENDING:
                return "<yellow>Pending</yellow>";
            case CANCELLED:
                return "<red>Cancelled</red>";
            default:
                return "<gray>Unknown</gray>";
        }
    }

    /**
     * Generates display name for item reception status.
     * This method provides clear visual feedback about whether items
     * have been received from completed trades.
     * 
     * @param received Whether items have been received
     * @return Formatted string indicating reception status
     */
    private String getItemsReceivedDisplayName(boolean received) {
        return received ? "<green>Yes</green>" : "<red>No</red>";
    }

    /**
     * Determines if there are more pages available for navigation.
     * This method calculates pagination state for navigation control.
     * 
     * @return True if additional pages exist, false otherwise
     */
    private boolean hasNextPage() {
        return (currentPage + 1) * ITEMS_PER_PAGE < filteredTrades.size();
    }

    /**
     * Handles all inventory click events with comprehensive action routing.
     * This method processes clicks on pagination, filters, and trade items
     * with appropriate context-sensitive actions.
     * 
     * @param event The inventory click event to process
     */
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int clickedSlot = event.getRawSlot();

        // Handle pagination navigation
        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            updatePaginationButtons();
            setupInfoSign();
            displayTrades();
            return;
        }

        if (clickedSlot == NEXT_PAGE_SLOT && hasNextPage()) {
            currentPage++;
            updatePaginationButtons();
            setupInfoSign();
            displayTrades();
            return;
        }

        // Handle trade item clicks with context-sensitive actions
        if (clickedSlot >= 0 && clickedSlot < 45) {
            int tradeIndex = currentPage * ITEMS_PER_PAGE + clickedSlot;
            if (tradeIndex < filteredTrades.size()) {
                TradeDocument trade = filteredTrades.get(tradeIndex);
                handleTradeClick(trade);
            }
        }

        // Handle filter button clicks with state management
        TradeFilter selectedFilter = getFilterFromSlot(clickedSlot);
        if (selectedFilter != null) {
            setCurrentFilter(selectedFilter);
            updateFilterButtons();
            displayTrades();
            return;
        }
    }

    /**
     * Sets the current filter and updates the displayed trades.
     * This method applies the selected filter to the trade list and
     * resets pagination to the first page.
     * 
     * @param filter The filter to apply to the trade list
     */
    private void setCurrentFilter(TradeFilter filter) {
        this.currentFilter = filter;
        this.filteredTrades = new ArrayList<>();

        // Apply filter to all trades
        for (TradeDocument trade : allTrades) {
            if (matchesFilter(trade, filter)) {
                filteredTrades.add(trade);
            }
        }

        this.currentPage = 0; // Reset to first page when changing filter
        setupInfoSign(); // Update trade counter display
    }

    /**
     * Sets up filter buttons with dynamic trade counts.
     * This method creates interactive filter buttons showing the number
     * of trades in each category with visual indicators for the active filter.
     */
    private void setupFilterButtons() {
        for (TradeFilter filter : TradeFilter.values()) {
            // Get the specific slot for each filter
            int slot = getSlotForFilter(filter);
            if (slot == -1)
                continue; // Skip if no slot defined

            // Count trades for this filter category
            int count = getCountForFilter(filter);

            // Attempt to use ItemManager for filter buttons with enhanced parameters
            String filterKey = "gui.buttons.filter_" + filter.name().toLowerCase();
            ItemStack filterItem = plugin.getItemManager().getItemStack(owner, filterKey,
                    "count", String.valueOf(count),
                    "filter_name", filter.getDisplayName(),
                    "current_page", String.valueOf(currentPage + 1));

            if (filterItem == null) {
                // Fallback using manual GUIItem creation
                GUIItem fallbackItem = new GUIItem(filter.getMaterial())
                        .setName("§e" + filter.getDisplayName() + " §7(" + count + ")")
                        .setLore("§7Click to filter by " + filter.getDisplayName().toLowerCase());

                // Mark the current active filter with enhanced visual feedback
                if (filter == currentFilter) {
                    fallbackItem.setName("§a" + filter.getDisplayName() + " §7(" + count + ")");
                    // Add additional lore for active filter indication
                    fallbackItem.setLore("§7Click to filter by " + filter.getDisplayName().toLowerCase(),
                            "§a► Current filter");
                }

                filterItem = fallbackItem.getItemStack();

                // Apply enchantment for visual distinction of active filter
                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            } else {
                // Apply enchantment to ItemManager-provided items for active filter
                if (filter == currentFilter) {
                    filterItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
            }

            inventory.setItem(slot, filterItem);
        }
    }

    /**
     * Maps each filter to its designated slot position.
     * This method provides consistent slot assignment for filter buttons.
     * 
     * @param filter The filter to get the slot for
     * @return The slot number for the filter, or -1 if not defined
     */
    private int getSlotForFilter(TradeFilter filter) {
        switch (filter) {
            case ALL:
                return FILTER_ALL_SLOT;
            case PENDING:
                return FILTER_PENDING_SLOT;
            case ACTIVE:
                return FILTER_ACTIVE_SLOT;
            case COMPLETED:
                return FILTER_COMPLETED_SLOT;
            case CANCELLED:
                return FILTER_CANCELLED_SLOT;
            default:
                return -1;
        }
    }

    /**
     * Maps clicked slot to corresponding filter.
     * This method enables filter selection through GUI interaction.
     * 
     * @param slot The clicked slot number
     * @return The corresponding TradeFilter, or null if not a filter slot
     */
    private TradeFilter getFilterFromSlot(int slot) {
        switch (slot) {
            case FILTER_ALL_SLOT:
                return TradeFilter.ALL;
            case FILTER_PENDING_SLOT:
                return TradeFilter.PENDING;
            case FILTER_ACTIVE_SLOT:
                return TradeFilter.ACTIVE;
            case FILTER_COMPLETED_SLOT:
                return TradeFilter.COMPLETED;
            case FILTER_CANCELLED_SLOT:
                return TradeFilter.CANCELLED;
            default:
                return null;
        }
    }

    /**
     * Updates filter button display states.
     * This method refreshes the filter button appearance to reflect
     * current selection and trade counts.
     */
    private void updateFilterButtons() {
        setupFilterButtons(); // Reuse enhanced filter setup logic
    }

    /**
     * Counts trades matching a specific filter.
     * This method efficiently calculates the number of trades
     * that match the given filter criteria.
     * 
     * @param filter The filter to count trades for
     * @return The number of trades matching the filter
     */
    private int getCountForFilter(TradeFilter filter) {
        if (filter == TradeFilter.ALL) {
            return allTrades.size();
        }

        int count = 0;
        for (TradeDocument trade : allTrades) {
            if (matchesFilter(trade, filter)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Determines if a trade matches the specified filter criteria.
     * This method implements the filtering logic for different trade states.
     * 
     * @param trade  The trade to check
     * @param filter The filter criteria to apply
     * @return True if the trade matches the filter, false otherwise
     */
    private boolean matchesFilter(TradeDocument trade, TradeFilter filter) {
        switch (filter) {
            case ALL:
                return true;
            case PENDING:
                // Show trades in PENDING state regardless of items
                return trade.getState() == TradeState.PENDING;
            case ACTIVE:
                return trade.getState() == TradeState.ACTIVE;
            case COMPLETED:
                return trade.getState() == TradeState.COMPLETED;
            case CANCELLED:
                return trade.getState() == TradeState.CANCELLED;
            default:
                return false;
        }
    }

    /**
     * Handles clicks on individual trade items with context-sensitive actions.
     * This method provides comprehensive trade interaction based on the trade's
     * current state and the player's role in the trade.
     * 
     * @param trade The clicked trade document
     */
    private void handleTradeClick(TradeDocument trade) {
        // Determine player role in the trade (initiator or target)
        boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
        UUID otherPlayerUUID = isPlayer1 ? trade.getPlayer2() : trade.getPlayer1();

        if (trade.getState() == TradeState.PENDING) {
            // Handle pending trades - check if player needs to add items
            boolean needsToAddItems = false;

            if (isPlayer1) {
                // Player is initiator - check if items haven't been added yet
                needsToAddItems = trade.getPlayer1Items().isEmpty();
            } else {
                // Player is target - check if items haven't been added yet
                needsToAddItems = trade.getPlayer2Items().isEmpty();
            }

            if (needsToAddItems) {
                // Open PreTradeGUI in response mode for item addition
                // No need for other player to be online for this operation
                String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();

                // Create PreTradeGUI using only necessary information
                PreTradeGUI preTradeGUI = new PreTradeGUI(owner, otherPlayerName, otherPlayerUUID, plugin, true,
                        trade.getTradeId());
                preTradeGUI.openInventory();
                return;
            } else {
                // Player already added items - show preview of other player's items
                plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                        .thenAccept(items -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                                ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                        owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                                viewTradeGUI.setOnlyPreview(true);
                                viewTradeGUI.openInventory();
                            });
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger()
                                    .severe(String.format("Error loading trade items: %s",
                                            throwable.getMessage()));
                            plugin.getMessageManager().sendComponentMessage(owner,
                                    "mytrades.error.loading_trade_items");
                            return null;
                        });
                return;
            }
        } else if (trade.getState() == TradeState.ACTIVE) {
            // Open active trade GUI for confirmation
            plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                    .thenAccept(items -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                            ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                    owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                            viewTradeGUI.setOnlyPreview(true);
                            viewTradeGUI.setConfirmationView(true);
                            viewTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error loading trade items: %s", throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.loading_trade_items");
                        return null;
                    });
        } else if (trade.getState() == TradeState.COMPLETED) {
            // Handle completed trades with automatic item distribution
            plugin.getTradeManager().getTradeItemsToReceive(trade.getTradeId(), owner.getUniqueId())
                    .thenAccept(itemsToReceive -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (itemsToReceive.isEmpty()) {
                                // Items already received or no items to receive
                                plugin.getMessageManager().sendComponentMessage(owner,
                                        "mytrades.completed.already_received",
                                        "trade_id", String.valueOf(trade.getTradeId()));
                                return;
                            }

                            // Distribute items to player with inventory overflow handling
                            for (ItemStack item : itemsToReceive) {
                                if (item != null && item.getType() != Material.AIR) {
                                    if (owner.getInventory().firstEmpty() != -1) {
                                        owner.getInventory().addItem(item.clone());
                                    } else {
                                        // Inventory full - drop items near player location
                                        owner.getWorld().dropItemNaturally(owner.getLocation(), item.clone());
                                        plugin.getMessageManager().sendComponentMessage(owner,
                                                "mytrades.completed.inventory_full");
                                    }
                                }
                            }

                            // Update item delivery status in database
                            plugin.getTradeManager().updateItemsSentStatus(
                                    trade.getTradeId(),
                                    owner.getUniqueId(),
                                    true).thenRun(() -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            plugin.getMessageManager().sendComponentMessage(owner,
                                                    "mytrades.completed.items_received", "trade_id",
                                                    String.valueOf(trade.getTradeId()));
                                            // Reload GUI to reflect updated state
                                            initializeItems();
                                        });
                                    })
                                    .exceptionally(throwable -> {
                                        plugin.getLogger()
                                                .severe(String.format("Error updating item status: %s",
                                                        throwable.getMessage()));
                                        plugin.getMessageManager().sendComponentMessage(owner,
                                                "mytrades.error.updating_item_status");
                                        return null;
                                    });
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error getting items to receive: %s",
                                        throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.loading_trade_items");
                        return null;
                    });
        } else {
            // Handle cancelled or other state trades - show information only
            plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                    .thenAccept(items -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String otherPlayerName = plugin.getServer().getOfflinePlayer(otherPlayerUUID).getName();
                            ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                                    owner, otherPlayerName, otherPlayerUUID, plugin, items, trade.getTradeId());
                            viewTradeGUI.setOnlyPreview(true);
                            viewTradeGUI.openInventory();
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger()
                                .severe(String.format("Error loading trade items: %s", throwable.getMessage()));
                        plugin.getMessageManager().sendComponentMessage(owner, "mytrades.error.loading_trade_items");
                        return null;
                    });
        }
    }
}