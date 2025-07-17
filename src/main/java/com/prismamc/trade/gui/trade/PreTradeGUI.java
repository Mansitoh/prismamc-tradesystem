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

public class PreTradeGUI extends GUI {
    private final Player initiator;
    private final Player target;
    private final PlayerData targetPlayerData; // Nueva variable para almacenar PlayerData del target
    private final boolean isResponse;
    private long tradeId;
    private int currentPage = 0;

    // Optimized slot management
    private static final int[] TRADE_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private static final int CONFIRM_SLOT = 49;
    private static final int INFO_SLOT = 40;
    private static final int PREV_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int ITEMS_PER_PAGE = 36;

    // Optimized state management
    private final Map<Integer, ItemStack> itemSlots;
    private final AtomicBoolean closedByButton;
    private final AtomicBoolean isProcessing;
    private final AtomicBoolean isDirty;

    // Static items cache for performance
    private static final Map<String, ItemStack> CACHED_BORDER_ITEMS = new ConcurrentHashMap<>();

    static {
        // Pre-cache common items
        CACHED_BORDER_ITEMS.put("border", new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName("§7 ").getItemStack());
        CACHED_BORDER_ITEMS.put("prevPage", new GUIItem(Material.ARROW)
                .setName("§ePágina Anterior")
                .setLore("§7Click para ir a la página anterior").getItemStack());
        CACHED_BORDER_ITEMS.put("nextPage", new GUIItem(Material.ARROW)
                .setName("§eSiguiente Página")
                .setLore("§7Click para ir a la siguiente página").getItemStack());
        CACHED_BORDER_ITEMS.put("disabledPage", new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName("§7 ").getItemStack());
    }

    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        this(owner, target, plugin, false, -1);
    }

    public PreTradeGUI(Player owner, PlayerData targetPlayerData, Plugin plugin) {
        this(owner, targetPlayerData, plugin, false, -1);
    }

    public PreTradeGUI(Player owner, Player target, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = target;
        this.targetPlayerData = null; // Will be loaded later
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    public PreTradeGUI(Player owner, PlayerData targetPlayerData, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = Bukkit.getPlayer(targetPlayerData.getUuid()); // Puede ser null si está offline
        this.targetPlayerData = targetPlayerData;
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    /**
     * Constructor que acepta nombre y UUID del jugador (para jugadores offline)
     */
    public PreTradeGUI(Player owner, String targetPlayerName, java.util.UUID targetPlayerUUID, Plugin plugin,
            boolean isResponse, long tradeId) {
        super(owner, getGUITitle(plugin, owner, isResponse), 54);
        this.initiator = owner;
        this.target = Bukkit.getPlayer(targetPlayerUUID); // Puede ser null si está offline
        // Crear PlayerData temporal con la información disponible
        this.targetPlayerData = new PlayerData(targetPlayerUUID, targetPlayerName, "Unknown");
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = new AtomicBoolean(false);
        this.isProcessing = new AtomicBoolean(false);
        this.isDirty = new AtomicBoolean(false);
    }

    private static String getGUITitle(Plugin plugin, Player player, boolean isResponse) {
        String key = isResponse ? "pretrade.gui.title.response" : "pretrade.gui.title.new";
        return plugin.getMessageManager().getRawMessage(player, key);
    }

    @Override
    protected void initializeItems() {
        if (isResponse && tradeId != -1) {
            validateAndInitialize();
        } else {
            setupInventoryItems();
        }
    }

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

                    // Execute setup synchronously if we're already on main thread
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

    private void handleError(String errorMessage) {
        if (!isClosed()) {
            plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.verify_trade", "error",
                    errorMessage);
            owner.closeInventory();
        }
    }

    private void setupInventoryItems() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::setupInventoryItems);
            return;
        }

        // Batch all inventory operations together
        setupBorders();
        updatePaginationButtons();
        setupConfirmButton();
        setupInfoSign();
        updatePageItems();
    }

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

    private void setupConfirmButton() {
        String buttonKey = isResponse ? "gui.buttons.confirm_response" : "gui.buttons.confirm_new";
        String targetPlayer = getTargetPlayerName();

        // Get item from ItemManager with player language support and dynamic lore
        // replacement
        ItemStack confirmButton = plugin.getItemManager().getItemStack(owner, buttonKey, "player", targetPlayer);

        if (confirmButton != null) {
            inventory.setItem(CONFIRM_SLOT, confirmButton);
        } else {
            // Fallback if item not found
            plugin.getLogger().warning("Confirm button item not found: " + buttonKey);
        }
    }

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
     * Obtener el nombre del jugador target, usando PlayerData si está disponible
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
     * Obtener el UUID del jugador target
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

    private void updatePageItems() {
        // Clear current page slots efficiently
        for (int slot : TRADE_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Display items for current page
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();

        // Handle confirm button
        if (clickedSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked().equals(owner)) {
                handleConfirmClick();
            }
            return;
        }

        // Handle navigation
        if (handleNavigationClick(event)) {
            return;
        }

        // Handle trade slots
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

    private boolean isValidTradeSlot(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE;
    }

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

    private void navigateToPage(int newPage) {
        saveCurrentPageItems();
        currentPage = newPage;

        // Batch update all page elements
        updatePaginationButtons();
        updatePageItems();
        setupInfoSign();
    }

    private void handleTradeSlotClick(InventoryClickEvent event) {
        // Process immediately since we're already on main thread
        int slot = event.getSlot();
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            // Mark as dirty for lazy updates
            isDirty.set(true);

            // Schedule a delayed update to batch multiple rapid clicks
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isDirty.get()) {
                    saveCurrentPageItems();
                    setupInfoSign();
                    isDirty.set(false);
                }
            }, 2L); // 2 ticks delay for batching
        }
    }

    private void saveCurrentPageItems() {
        int startIndex = currentPage * ITEMS_PER_PAGE;

        // Efficiently save current page items
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

    private void handleConfirmClick() {
        if (isProcessing.getAndSet(true)) {
            return; // Prevent double-processing
        }

        try {
            saveCurrentPageItems();

            // Validate items using MessageManager
            if (itemSlots.isEmpty()) {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.error.no_items");
                isProcessing.set(false);
                return;
            }

            // IMMEDIATE UI RESPONSE - Close menu and send ONE confirmation message
            closedByButton.set(true);
            owner.closeInventory();

            // Send ONE confirmation message based on action type
            if (isResponse) {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.notifications.items_sent");
            } else {
                plugin.getMessageManager().sendComponentMessage(owner, "pretrade.notifications.items_sent_to", "player",
                        getTargetPlayerName());
            }

            // Process database operations asynchronously in background
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

    private void handleResponseConfirmationAsync() {
        // All database operations happen asynchronously
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

    private void handleInitialConfirmationAsync() {
        // All database operations happen asynchronously
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

    private void openTradeGUI() {
        // Solo enviar notificaciones si el target está online
        if (target != null && target.isOnline()) {
            // Send confirmation messages using MessageManager
            plugin.getMessageManager().sendComponentMessage(target, "pretrade.notifications.trade_accepted", "player",
                    initiator.getName());

            // Create clickable confirmation messages
            Component confirmMessageInitiator = plugin.getMessageManager()
                    .getComponent(initiator, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + getTargetPlayerName() + " " + tradeId));
            initiator.sendMessage(confirmMessageInitiator);

            Component confirmMessageTarget = plugin.getMessageManager()
                    .getComponent(target, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + initiator.getName() + " " + tradeId));
            target.sendMessage(confirmMessageTarget);
        } else {
            // Target está offline - solo enviar mensaje al initiator
            Component confirmMessageInitiator = plugin.getMessageManager()
                    .getComponent(initiator, "pretrade.buttons.confirm_trade")
                    .clickEvent(ClickEvent.runCommand("/tradeconfirm " + getTargetPlayerName() + " " + tradeId));
            initiator.sendMessage(confirmMessageInitiator);

            // Notificar que el otro jugador está offline
            plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.target_offline",
                    "player", getTargetPlayerName());
        }
    }

    private void sendViewTradeRequest() {
        // Send notification to initiator using MessageManager - ENVIAR INMEDIATAMENTE
        // EL MENSAJE DE ÉXITO
        plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.trade_request_sent",
                "player", getTargetPlayerName(), "trade_id", tradeId);

        // Solo enviar notificación al target si está online
        if (target != null && target.isOnline()) {
            // Send ONE comprehensive notification to target
            plugin.getMessageManager().sendComponentMessage(target, "pretrade.notifications.trade_request_alert",
                    "trade_id", tradeId);

            // Create clickable view button
            Component viewButton = plugin.getMessageManager()
                    .getComponent(target, "pretrade.buttons.view_trade_with_sender", "player", initiator.getName(),
                            "trade_id", String.valueOf(tradeId))
                    .clickEvent(ClickEvent.runCommand("/tradeaccept " + initiator.getName() + " " + tradeId));
            target.sendMessage(viewButton);
        } else {
            // Target está offline - notificar al initiator
            plugin.getMessageManager().sendComponentMessage(initiator, "pretrade.notifications.trade_sent_offline",
                    "player", getTargetPlayerName());
        }
    }

    public List<ItemStack> getAllPagesItems() {
        saveCurrentPageItems();

        // Optimized item merging with better performance
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
                    // Create new stack if exceeds max
                    ItemStack newStack = item.clone();
                    newStack.setAmount(addAmount);
                    mergedItems.put(key + "_" + mergedItems.size(), newStack);
                }
            }
        }

        return new ArrayList<>(mergedItems.values());
    }

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

    public static int[] getTradeSlots() {
        return TRADE_SLOTS.clone();
    }

    public boolean wasClosedByButton() {
        return closedByButton.get();
    }

    @Override
    public void onClose(Player player) {
        super.onClose(player);
        // Clean up any pending operations
        isProcessing.set(false);
        isDirty.set(false);
    }
}
