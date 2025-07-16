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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MyTradesGUI extends GUI {
    private final Plugin plugin;
    private List<TradeDocument> allTrades; // Todos los trades sin filtrar
    private List<TradeDocument> filteredTrades; // Trades después de aplicar filtros
    private int currentPage = 0;
    private TradeFilter currentFilter = TradeFilter.ALL;
    private static final int ITEMS_PER_PAGE = 36; // Reducido para hacer espacio a los filtros
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int INFO_SLOT = 49;
    
    // Slots para filtros de estado (fila superior)
    private static final int FILTER_ALL_SLOT = 1;
    private static final int FILTER_PENDING_SLOT = 2;
    private static final int FILTER_ACTIVE_SLOT = 3;
    private static final int FILTER_COMPLETED_SLOT = 4;
    private static final int FILTER_CANCELLED_SLOT = 5;

    public enum TradeFilter {
        ALL("Todos", Material.CHEST),
        PENDING("Pendientes", Material.YELLOW_WOOL),
        ACTIVE("Activos", Material.LIME_WOOL),
        COMPLETED("Completados", Material.BLUE_WOOL),
        CANCELLED("Cancelados", Material.RED_WOOL);
        
        private final String displayName;
        private final Material material;
        
        TradeFilter(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }
        
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
    }

    public MyTradesGUI(Player owner, Plugin plugin) {
        super(owner, "Mis Trades", 54);
        this.plugin = plugin;
        this.allTrades = new ArrayList<>();
        this.filteredTrades = new ArrayList<>();
    }

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

    private CompletableFuture<Void> loadTrades() {
        return CompletableFuture.supplyAsync(() -> {
            List<TradeDocument> loadedTrades = new ArrayList<>();
            
            try {
                // Obtener TODOS los documentos de trade que involucren al jugador
                plugin.getMongoDBManager().getTradesCollection()
                    .find(new org.bson.Document("$or", List.of(
                        new org.bson.Document("player1", owner.getUniqueId().toString()),
                        new org.bson.Document("player2", owner.getUniqueId().toString())
                    )))
                    .forEach(doc -> {
                        TradeDocument trade = new TradeDocument(doc);
                        loadedTrades.add(trade); // Agregar TODOS los trades sin filtrar
                    });
                
                // Ejecutar debug si está habilitado
                debugTrades(loadedTrades);
                
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("❌ ERROR cargando trades para %s: %s", owner.getName(), e.getMessage()));
                e.printStackTrace();
            }
            
            this.allTrades = loadedTrades;
            this.filteredTrades = new ArrayList<>(loadedTrades); // Inicialmente mostrar todos
            return null;
        });
    }

    /**
     * Método de debug separado - se puede eliminar fácilmente
     */
    private void debugTrades(List<TradeDocument> trades) {
        plugin.getLogger().info("=== DEBUG MyTradesGUI ===");
        plugin.getLogger().info("Jugador: " + owner.getName() + " (UUID: " + owner.getUniqueId() + ")");
        plugin.getLogger().info("Total trades encontrados en DB: " + trades.size());
        
        if (trades.isEmpty()) {
            plugin.getLogger().info("❌ No se encontraron trades para este jugador en la base de datos");
        } else {
            plugin.getLogger().info("✅ Detalles de cada trade:");
            
            for (TradeDocument trade : trades) {
                String player1Name = plugin.getServer().getOfflinePlayer(trade.getPlayer1()).getName();
                String player2Name = plugin.getServer().getOfflinePlayer(trade.getPlayer2()).getName();
                boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
                String otherPlayer = isPlayer1 ? player2Name : player1Name;
                
                plugin.getLogger().info("Trade #" + trade.getTradeId() + ":");
                plugin.getLogger().info("  - Estado: " + trade.getState());
                plugin.getLogger().info("  - Player1: " + player1Name + " (" + trade.getPlayer1() + ")");
                plugin.getLogger().info("  - Player2: " + player2Name + " (" + trade.getPlayer2() + ")");
                plugin.getLogger().info("  - " + owner.getName() + " es: " + (isPlayer1 ? "Player1" : "Player2"));
                plugin.getLogger().info("  - Trading con: " + otherPlayer);
                plugin.getLogger().info("  - Items enviados a Player1: " + trade.areItemsSentToPlayer1());
                plugin.getLogger().info("  - Items enviados a Player2: " + trade.areItemsSentToPlayer2());
                plugin.getLogger().info("  ---");
            }
            
            // Resumen por estados
            plugin.getLogger().info("✅ Trades por estado:");
            trades.stream()
                .collect(java.util.stream.Collectors.groupingBy(TradeDocument::getState))
                .forEach((state, tradeList) -> {
                    plugin.getLogger().info("  - " + state + ": " + tradeList.size() + " trades");
                });
        }
        plugin.getLogger().info("========================");
    }

    private void setupBorders() {
        GUIItem border = new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
            .setName("§7 ");
        
        for (int i = 45; i < 54; i++) {
            if (i != INFO_SLOT && i != PREV_PAGE_SLOT && i != NEXT_PAGE_SLOT) {
                inventory.setItem(i, border.getItemStack());
            }
        }
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
                .setName("§7 ").getItemStack());
        }

        if (hasNextPage()) {
            inventory.setItem(NEXT_PAGE_SLOT, nextPage.getItemStack());
        } else {
            inventory.setItem(NEXT_PAGE_SLOT, new GUIItem(Material.GRAY_STAINED_GLASS_PANE)
                .setName("§7 ").getItemStack());
        }
    }

    private void setupInfoSign() {
        GUIItem infoSign = new GUIItem(Material.OAK_SIGN)
            .setName("§eInformación")
            .setLore(
                "§7Total de trades: §f" + filteredTrades.size(),
                "§7Página: §f" + (currentPage + 1)
            );
        inventory.setItem(INFO_SLOT, infoSign.getItemStack());
    }

    private void displayTrades() {
        // Limpiar slots anteriores
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredTrades.size());

        for (int i = startIndex; i < endIndex; i++) {
            TradeDocument trade = filteredTrades.get(i);
            int slot = i - startIndex;

            // Determinar si el jugador es player1 o player2
            boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
            String otherPlayerUUID = isPlayer1 ? trade.getPlayer2().toString() : trade.getPlayer1().toString();
            String otherPlayerName = plugin.getServer().getOfflinePlayer(UUID.fromString(otherPlayerUUID)).getName();

            Material material = trade.getState() == TradeState.ACTIVE ? Material.LIME_WOOL : Material.YELLOW_WOOL;
            GUIItem tradeItem = new GUIItem(material)
                .setName("§eTrade #" + trade.getTradeId())
                .setLore(
                    "§7Estado: " + (trade.getState() == TradeState.ACTIVE ? "§aActivo" : "§6Completado"),
                    "§7Trading con: §f" + otherPlayerName,
                    "§7Items recibidos: " + (isPlayer1 ? (trade.areItemsSentToPlayer1() ? "§aSí" : "§cNo") 
                                                      : (trade.areItemsSentToPlayer2() ? "§aSí" : "§cNo")),
                    "",
                    "§eClick para ver detalles"
                );
            
            inventory.setItem(slot, tradeItem.getItemStack());
        }
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * ITEMS_PER_PAGE < filteredTrades.size();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int clickedSlot = event.getRawSlot();

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

        // Si el click fue en un trade
        if (clickedSlot >= 0 && clickedSlot < 45) {
            int tradeIndex = currentPage * ITEMS_PER_PAGE + clickedSlot;
            if (tradeIndex < filteredTrades.size()) {
                TradeDocument trade = filteredTrades.get(tradeIndex);
                handleTradeClick(trade);
            }
        }
        
        // Manejo de clicks en los botones de filtro
        if (clickedSlot >= FILTER_ALL_SLOT && clickedSlot <= FILTER_CANCELLED_SLOT) {
            int filterIndex = clickedSlot - FILTER_ALL_SLOT;
            TradeFilter selectedFilter = TradeFilter.values()[filterIndex];
            setCurrentFilter(selectedFilter);
            updateFilterButtons();
            displayTrades();
        }
    }

    private void setCurrentFilter(TradeFilter filter) {
        this.currentFilter = filter;
        this.filteredTrades = new ArrayList<>();
        
        for (TradeDocument trade : allTrades) {
            if (matchesFilter(trade, filter)) {
                filteredTrades.add(trade);
            }
        }
        
        this.currentPage = 0; // Reiniciar a la primera página al cambiar el filtro
        setupInfoSign(); // Actualizar contador
    }

    private void setupFilterButtons() {
        // Limpiar la fila superior de filtros
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, null);
        }
        
        // Configurar botones de filtro
        updateFilterButtons();
    }
    
    private void updateFilterButtons() {
        for (TradeFilter filter : TradeFilter.values()) {
            int slot = FILTER_ALL_SLOT + filter.ordinal();
            
            // Contar trades para este filtro
            int count = getCountForFilter(filter);
            
            GUIItem filterItem = new GUIItem(filter.getMaterial())
                .setName("§e" + filter.getDisplayName() + " §7(" + count + ")")
                .setLore("§7Click para filtrar por " + filter.getDisplayName().toLowerCase());
            
            // Marcar el filtro actual con encantamiento
            if (filter == currentFilter) {
                filterItem.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                filterItem.setName("§a" + filter.getDisplayName() + " §7(" + count + ")");
                filterItem.addLore("§a► Filtro actual");
            }
            
            inventory.setItem(slot, filterItem.getItemStack());
        }
    }
    
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
    
    private boolean matchesFilter(TradeDocument trade, TradeFilter filter) {
        switch (filter) {
            case ALL:
                return true;
            case PENDING:
                // Lógica específica para PENDING: 
                // Solo mostrar si es PENDING y este jugador NO ha puesto items aún
                if (trade.getState() == TradeState.PENDING) {
                    boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
                    if (isPlayer1) {
                        // Soy player1, mostrar si yo NO he puesto items
                        return trade.getPlayer1Items().isEmpty();
                    } else {
                        // Soy player2, mostrar si yo NO he puesto items
                        return trade.getPlayer2Items().isEmpty();
                    }
                }
                return false;
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

    private void handleTradeClick(TradeDocument trade) {
        // Determinar si el jugador es player1 o player2
        boolean isPlayer1 = trade.getPlayer1().equals(owner.getUniqueId());
        UUID otherPlayerUUID = isPlayer1 ? trade.getPlayer2() : trade.getPlayer1();
        
        if (trade.getState() == TradeState.ACTIVE) {
            // Abrir el GUI de trades activos
            plugin.getTradeManager().getTradeItems(trade.getTradeId(), otherPlayerUUID)
                .thenAccept(items -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        ViewTradeGUI viewTradeGUI = new ViewTradeGUI(
                            owner, (Player) plugin.getServer().getOfflinePlayer(otherPlayerUUID),
                            plugin,
                            items,
                            trade.getTradeId()
                        );
                        viewTradeGUI.openInventory();
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe(String.format("Error cargando items del trade: %s", throwable.getMessage()));
                    owner.sendMessage("§cError al cargar los items del trade.");
                    return null;
                });
        } else if (trade.getState() == TradeState.COMPLETED) {
            // Para trades completados, obtener y dar los items
            plugin.getTradeManager().getAndRemoveTradeItems(trade.getTradeId(), otherPlayerUUID)
                .thenAccept(items -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Dar items al jugador
                        for (ItemStack item : items) {
                            if (item != null && item.getType() != Material.AIR) {
                                if (owner.getInventory().firstEmpty() != -1) {
                                    owner.getInventory().addItem(item.clone());
                                } else {
                                    owner.getWorld().dropItemNaturally(owner.getLocation(), item.clone());
                                    owner.sendMessage("§eTu inventario está lleno! Algunos items fueron dropeados al suelo.");
                                }
                            }
                        }
                        
                        // Actualizar el estado de items enviados
                        plugin.getTradeManager().updateItemsSentStatus(
                            trade.getTradeId(),
                            owner.getUniqueId(),
                            true
                        ).thenRun(() -> {
                            owner.sendMessage("§aHas recibido los items del trade #" + trade.getTradeId());
                            // Recargar el GUI
                            initializeItems();
                        });
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe(String.format("Error al dar items del trade: %s", throwable.getMessage()));
                    owner.sendMessage("§cError al recibir los items del trade.");
                    return null;
                });
        }
    }
}