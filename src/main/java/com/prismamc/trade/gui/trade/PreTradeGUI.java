package com.prismamc.trade.gui.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.lib.GUI;
import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.manager.TradeManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public class PreTradeGUI extends GUI {
    private final Player initiator;
    private final Player target;
    private final boolean isResponse;
    private long tradeId;
    private int currentPage = 0;
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
    private final Map<Integer, ItemStack> itemSlots;
    private boolean closedByButton;
    private final Set<Integer> pendingUpdates;
    private long lastUpdateTime;

    public PreTradeGUI(Player owner, Player target, Plugin plugin) {
        this(owner, target, plugin, false, -1);
    }

    public PreTradeGUI(Player owner, Player target, Plugin plugin, boolean isResponse, long tradeId) {
        super(owner, isResponse ? "Selecciona tus items para el trade" : "Selecciona los items a tradear", 54);
        this.initiator = owner;
        this.target = target;
        this.isResponse = isResponse;
        this.tradeId = tradeId;
        this.itemSlots = new ConcurrentHashMap<>();
        this.closedByButton = false;
        this.pendingUpdates = Collections.synchronizedSet(new HashSet<>());
        this.lastUpdateTime = 0;
    }

    @Override
    protected void initializeItems() {
        // Sólo inicializar si el inventario ya está creado
        if (inventory == null) {
            return;
        }
        
        if (isResponse && tradeId != -1) {
            validateAndInitialize();
        } else {
            setupInventoryItems();
        }
    }

    private void validateAndInitialize() {
        plugin.getTradeManager().isTradeValid(tradeId)
            .thenAccept(isValid -> {
                if (!isValid) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        owner.sendMessage(Component.text("Este trade ya no es válido!")
                            .color(NamedTextColor.RED));
                        owner.closeInventory();
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, this::setupInventoryItems);
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    owner.sendMessage(Component.text("Error al verificar el trade: " + throwable.getMessage())
                        .color(NamedTextColor.RED));
                    owner.closeInventory();
                });
                return null;
            });
    }

    private void setupInventoryItems() {
        setupBorders();
        updatePaginationButtons();
        setupConfirmButton();
        setupInfoSign();
        updatePageItems();
    }

    private void setupBorders() {
        GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName(Component.text(" ").toString());
        
        for (int i = 36; i < 54; i++) {
            if (i != INFO_SLOT && i != CONFIRM_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT) {
                inventory.setItem(i, border.getItemStack());
            }
        }
    }

    private void setupConfirmButton() {
        GUIItem confirmButton = new GUIItem(Material.EMERALD)
            .setName(isResponse ? "§aConfirmar tus items" : "§aConfirmar oferta de trade")
            .setLore(
                isResponse ? new String[]{
                    "§7Click para proceder con el trade",
                    "§7y mostrar los items a",
                    "§f" + initiator.getName()
                } : new String[]{
                    "§7Click para enviar solicitud",
                    "§7de trade a",
                    "§f" + target.getName()
                }
            );
        inventory.setItem(CONFIRM_SLOT, confirmButton.getItemStack());
    }

    private void setupInfoSign() {
        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eInformación del Trade")
            .setLore(
                "§7" + (isResponse ? "Respondiendo a" : "Tradeando con") + ": §f" + (isResponse ? initiator.getName() : target.getName()),
                tradeId != -1 ? "§7ID del Trade: §f" + tradeId : "§7ID del Trade: §fPendiente",
                "§7Página: §f" + (currentPage + 1)
            );
        inventory.setItem(INFO_SLOT, infoSign.getItemStack());
    }

    private void updatePaginationButtons() {
        GUIItem prevPage = new GUIItem(Material.ARROW)
            .setName("§ePágina Anterior")
            .setLore("§7Click para ir a la página anterior");

        GUIItem nextPage = new GUIItem(Material.ARROW)
            .setName("§eSiguiente Página")
            .setLore("§7Click para ir a la siguiente página");

        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, prevPage.getItemStack());
        } else {
            inventory.setItem(PREV_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ").getItemStack());
        }

        inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());
    }

    private void updatePageItems() {
        // Limpiar slots de items
        for (int slot : TRADE_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Mostrar items de la página actual
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for(int slot : itemSlots.keySet()) {
            if (slot >= startIndex && slot < startIndex + ITEMS_PER_PAGE) {
                ItemStack item = itemSlots.get(slot);
                if (item != null && !item.getType().equals(Material.AIR)) {
                    inventory.setItem(slot - startIndex, item.clone());
                }
            }
        }
    }

    private void queueInventoryUpdate(int slot) {
        pendingUpdates.add(slot);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastUpdateTime > 200) { // 200ms throttle
            processQueuedUpdates();
        } else {
            // Programar actualización diferida
            new BukkitRunnable() {
                @Override
                public void run() {
                    processQueuedUpdates();
                }
            }.runTaskLater(plugin, 1);
        }
    }

    private void processQueuedUpdates() {
        if (pendingUpdates.isEmpty()) return;
        
        synchronized (pendingUpdates) {
            for (int slot : pendingUpdates) {
                updateSlot(slot);
            }
            pendingUpdates.clear();
        }
        
        lastUpdateTime = System.currentTimeMillis();
        setupInfoSign();
    }

    private void updateSlot(int slot) {
        ItemStack item = itemSlots.get(slot);
        int displaySlot = slot % ITEMS_PER_PAGE;
        
        if (item != null && !item.getType().equals(Material.AIR)) {
            inventory.setItem(displaySlot, item.clone());
        } else {
            inventory.setItem(displaySlot, null);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();
        
        if (clickedSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked().equals(owner)) {
                handleConfirmClick();
            }
            return;
        }

        if (handleNavigationClick(event)) return;

        if (isValidTradeSlot(clickedSlot)) {
            handleTradeSlotClick(event);
        } else {
            event.setCancelled(true);
        }
    }

    private boolean isValidTradeSlot(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE;
    }

    private boolean handleNavigationClick(InventoryClickEvent event) {
        int clickedSlot = event.getRawSlot();
        event.setCancelled(true);

        if (clickedSlot == PREV_PAGE_SLOT && currentPage > 0) {
            saveCurrentPageItems();
            currentPage--;
            updatePaginationButtons();
            updatePageItems();
            return true;
        }

        if (clickedSlot == NEXT_PAGE_SLOT) {
            saveCurrentPageItems();
            currentPage++;
            updatePaginationButtons();
            updatePageItems();
            return true;
        }

        return false;
    }

    private void handleTradeSlotClick(InventoryClickEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            saveCurrentPageItems();
            queueInventoryUpdate(event.getSlot());
        });
    }

    private void saveCurrentPageItems() {
        int startIndex = currentPage * ITEMS_PER_PAGE;


        // Actualizar solo la lista local de items
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            ItemStack item = inventory.getItem(TRADE_SLOTS[i]);
    
            int realSlot = startIndex + i;
            if (item != null && !item.getType().equals(Material.AIR)) {
                itemSlots.put(realSlot, item.clone());
            } else {
                itemSlots.remove(realSlot);
            }
        }

    }

    private void handleConfirmClick() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Asegurar que guardamos todos los items de todas las páginas

            saveCurrentPageItems();

            // Verificar si hay items seleccionados
            if (itemSlots.isEmpty()) {
                owner.sendMessage(Component.text("¡Debes seleccionar al menos un item para tradear!")
                        .color(NamedTextColor.RED));
                return;
            }

            closedByButton = true;

            if (isResponse) {
                // Si es una respuesta, verificar que el trade siga siendo válido
                plugin.getTradeManager().isTradeValid(tradeId)
                        .thenAccept(isValid -> {
                            if (!isValid) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    owner.sendMessage(Component.text("Este trade ya no es válido!")
                                            .color(NamedTextColor.RED));
                                    owner.closeInventory();
                                });
                                return;
                            }
                            handleResponseConfirmation();
                        });
            } else {
                // Si es un nuevo trade, crear el ID y guardar los items
                plugin.getTradeManager().createNewTrade(owner.getUniqueId(), target.getUniqueId())
                    .thenAccept(newTradeId -> {
                        tradeId = newTradeId; // Guardar el nuevo ID
                        handleInitialConfirmation()
                            .exceptionally(throwable -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    owner.sendMessage(Component.text("Error al procesar el trade: " + throwable.getMessage())
                                        .color(NamedTextColor.RED));
                                });
                                return null;
                            });
                    })
                    .exceptionally(throwable -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            owner.sendMessage(Component.text("Error al crear el trade: " + throwable.getMessage())
                                .color(NamedTextColor.RED));
                        });
                        return null;
                    });
            }
        });
    }

    private CompletableFuture<?> handleResponseConfirmation() {
        return plugin.getTradeManager().updateTradeState(tradeId, TradeManager.TradeState.ACTIVE)
                .thenCompose(v -> plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(), getAllPagesItems()))
                .thenRun(() -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        owner.closeInventory();

                        initiator.sendMessage(Component.text(target.getName())
                                .color(NamedTextColor.WHITE)
                                .append(Component.text(" ha agregado sus items al trade!")
                                        .color(NamedTextColor.GREEN)));

                        CompletableFuture.allOf(
                                plugin.getTradeManager().getTradeItems(tradeId, initiator.getUniqueId()),
                                plugin.getTradeManager().getTradeItems(tradeId, target.getUniqueId())
                        ).thenAccept(v -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                openTradeGUI();
                            });
                        });
                    });
                });
    }

    private CompletableFuture<?> handleInitialConfirmation() {
        return plugin.getTradeManager().storeTradeItems(tradeId, owner.getUniqueId(), getAllPagesItems())
                .thenRun(() -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        owner.closeInventory();
                        sendViewTradeRequest();
                    });
                });
    }

    private void openTradeGUI() {
        //enviar mensaje a initiator que target ha aceptado el trade
        target.sendMessage(Component.text("¡" + initiator.getName() + " ha aceptado el trade!")
                .color(NamedTextColor.GREEN));
        //enviar un mensaje para confirmar el trade, el cual ejecuta el comando /tradeconfirm <target> <tradeId> al darle click al mensaje
        Component confirmMessage = Component.text("[Haz click aqui para confirmar el trade]").clickEvent(ClickEvent.runCommand("/tradeconfirm " + target.getName() + " " + tradeId))
                .color(NamedTextColor.YELLOW);
        initiator.sendMessage(confirmMessage);
        
        confirmMessage = Component.text("Haz click aqui para confirmar el trade]")
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/tradeconfirm " + initiator.getName() + " " + tradeId));
        target.sendMessage(confirmMessage);
        
    }



    private void sendViewTradeRequest() {
        initiator.sendMessage(Component.text("Tus items han sido enviados a ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(target.getName())
                        .color(NamedTextColor.WHITE))
                .append(Component.text(" (Trade ID: " + tradeId + ")")
                        .color(NamedTextColor.GRAY)));

        target.sendMessage(Component.empty());
        target.sendMessage(Component.text("⚡ ¡Nueva solicitud de trade! ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("(ID: " + tradeId + ")")
                        .color(NamedTextColor.GRAY)));
        target.sendMessage(Component.text(initiator.getName())
                .color(NamedTextColor.WHITE)
                .append(Component.text(" quiere tradear contigo. Click en el botón para ver sus items.")
                        .color(NamedTextColor.GRAY)));
        target.sendMessage(Component.empty());

        Component viewButton = Component.text("[Ver Items del Trade]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tradeaccept " + initiator.getName() + " " + tradeId));

        target.sendMessage(viewButton);
        target.sendMessage(Component.empty());
    }



    public List<ItemStack> getAllPagesItems() {
        saveCurrentPageItems();
        Map<String, ItemStack> mergedItems = new HashMap<>();
        
        for (ItemStack item : itemSlots.values()) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            String key = getItemKey(item);
            ItemStack existing = mergedItems.get(key);
            
            if (existing == null) {
                mergedItems.put(key, item.clone());
            } else {
                int maxStack = item.getMaxStackSize();
                int currentAmount = existing.getAmount();
                int addAmount = item.getAmount();
                
                // Si la suma excede el máximo, crear un nuevo stack
                if (currentAmount + addAmount > maxStack) {
                    item.setAmount(addAmount);
                    mergedItems.put(key + "_" + mergedItems.size(), item.clone());
                } else {
                    existing.setAmount(currentAmount + addAmount);
                }
            }
        }
        
        return new ArrayList<>(mergedItems.values());
    }
    
    private String getItemKey(ItemStack item) {
        if (item == null) return "null";
        StringBuilder key = new StringBuilder();
        key.append(item.getType().name());
        
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                key.append(meta.displayName());
            }
            if (meta.hasLore()) {
                key.append(meta.lore());
            }
            if (meta.hasEnchants()) {
                key.append(meta.getEnchants());
            }
        }
        
        return key.toString();
    }

    public static int[] getTradeSlots() {
        return TRADE_SLOTS.clone();
    }

    public boolean wasClosedByButton() {
        return closedByButton;
    }
}
