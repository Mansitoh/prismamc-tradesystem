package com.prismamc.trade.manager;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.bulk.BulkWriteResult;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.model.TradeDocument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import com.mongodb.MongoException;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class TradeManager {
    private final Plugin plugin;
    private final MongoDBManager mongoDBManager;
    private final AtomicLong tradeIdGenerator;
    private final CacheManager cacheManager;
    private final Map<UUID, List<Long>> playerTradesCache;

    public enum TradeState {
        PENDING,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    public TradeManager(Plugin plugin) {
        this.plugin = plugin;
        this.mongoDBManager = plugin.getMongoDBManager();
        this.tradeIdGenerator = new AtomicLong(1);
        this.cacheManager = new CacheManager();
        this.playerTradesCache = new ConcurrentHashMap<>();
        initializeIdGenerator();
        startPeriodicCleanup();
    }

    private void initializeIdGenerator() {
        CompletableFuture.runAsync(() -> {
            try {
                Document lastTrade = mongoDBManager.getTradesCollection()
                        .find()
                        .sort(new Document("tradeId", -1))
                        .limit(1)
                        .first();

                if (lastTrade != null) {
                    tradeIdGenerator.set(lastTrade.getLong("tradeId") + 1);
                }
            } catch (MongoException e) {
                plugin.getLogger().log(Level.SEVERE, "Error initializing ID generator: {0}", e.getMessage());
            }
        });
    }

    private void startPeriodicCleanup() {
        // Limpiar trades expirados cada hora
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                long expiryTime = System.currentTimeMillis() - (3600000 * 24); // 24 horas
                mongoDBManager.getTradesCollection().deleteMany(
                        Filters.and(
                                Filters.lt("timestamp", expiryTime),
                                Filters.in("state", Arrays.asList(
                                        TradeState.PENDING.name(),
                                        TradeState.ACTIVE.name()))));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during periodic cleanup: {0}", e.getMessage());
            }
        }, 20L * 3600, 20L * 3600); // Cada hora
    }

    public void shutdown() {
        cacheManager.shutdown();
    }

    public CompletableFuture<Long> createNewTrade(UUID player1, UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long tradeId = tradeIdGenerator.getAndIncrement();
                TradeDocument tradeDoc = new TradeDocument(tradeId, player1, player2);
                mongoDBManager.getTradesCollection().insertOne(tradeDoc.toDocument());
                cacheManager.put(tradeId, tradeDoc);

                // Actualizar caché de trades por jugador
                playerTradesCache.computeIfAbsent(player1, k -> new ArrayList<>()).add(tradeId);
                playerTradesCache.computeIfAbsent(player2, k -> new ArrayList<>()).add(tradeId);

                return tradeId;
            } catch (Exception e) {
                logError("Error creating new trade", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> updateTradeState(long tradeId, TradeState newState) {
        return CompletableFuture.runAsync(() -> {
            try {
                mongoDBManager.getTradesCollection().updateOne(
                        Filters.eq("tradeId", tradeId),
                        Updates.set("state", newState.name()));

                TradeDocument cached = cacheManager.get(tradeId);
                if (cached != null) {
                    cached.setState(newState);
                    cacheManager.put(tradeId, cached);
                }
            } catch (Exception e) {
                logError("Error updating trade state", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Boolean> isTradeValid(long tradeId) {
        // Primero intentar con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(
                    cached.getState() != TradeState.CANCELLED &&
                            cached.getState() != TradeState.COMPLETED);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);
                    return trade.getState() != TradeState.CANCELLED &&
                            trade.getState() != TradeState.COMPLETED;
                }
                return false;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error validating trade: {0}", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> batchUpdateTrades(List<TradeDocument> trades) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<WriteModel<Document>> writes = new ArrayList<>();
                for (TradeDocument trade : trades) {
                    writes.add(new UpdateOneModel<>(
                            Filters.eq("tradeId", trade.getTradeId()),
                            Updates.combine(
                                    Updates.set("state", trade.getState().name()),
                                    Updates.set("player1Items", trade.serializeItems(trade.getPlayer1Items())),
                                    Updates.set("player2Items", trade.serializeItems(trade.getPlayer2Items())))));
                    cacheManager.put(trade.getTradeId(), trade);
                }

                if (!writes.isEmpty()) {
                    BulkWriteResult result = mongoDBManager.getTradesCollection()
                            .bulkWrite(writes, new BulkWriteOptions().ordered(false));
                    plugin.getLogger().log(Level.INFO, "Batch update completed: {0} documents modified",
                            result.getModifiedCount());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error in batch update: {0}", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> storeTradeItems(long tradeId, UUID playerUUID, List<ItemStack> items) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        String field = trade.getPlayer1().equals(playerUUID) ? "player1Items" : "player2Items";

                        if (trade.getPlayer1().equals(playerUUID)) {
                            trade.setPlayer1Items(items);
                        } else {
                            trade.setPlayer2Items(items);
                        }

                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(field, trade.serializeItems(items)));

                        cacheManager.put(tradeId, trade);
                    }
                    future.complete(null);
                } catch (Exception e) {
                    logError("Error storing trade items", e.getMessage());
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<List<ItemStack>> getTradeItems(long tradeId, UUID playerUUID) {
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();

        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            List<ItemStack> items = cached.getPlayer1().equals(playerUUID) ? cached.getPlayer1Items()
                    : cached.getPlayer2Items();
            future.complete(new ArrayList<>(items));
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(tradeId, trade);
                        List<ItemStack> items = trade.getPlayer1().equals(playerUUID) ? trade.getPlayer1Items()
                                : trade.getPlayer2Items();
                        future.complete(new ArrayList<>(items));
                    } else {
                        future.complete(new ArrayList<>());
                    }
                } catch (Exception e) {
                    logError("Error retrieving trade items", e.getMessage());
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<List<ItemStack>> getAndRemoveTradeItems(long tradeId, UUID playerUUID) {
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        
                        // Obtener los items que debe recibir ESTE jugador (items del OTRO jugador)
                        List<ItemStack> itemsToReceive;
                        String fieldToUpdate;
                        
                        if (trade.getPlayer1().equals(playerUUID)) {
                            // Player1 debe recibir los items de player2
                            itemsToReceive = new ArrayList<>(trade.getPlayer2Items());
                            fieldToUpdate = "player2Items"; // Limpiar los items de player2 porque player1 ya los recibió
                            trade.setPlayer2Items(new ArrayList<>());
                        } else {
                            // Player2 debe recibir los items de player1
                            itemsToReceive = new ArrayList<>(trade.getPlayer1Items());
                            fieldToUpdate = "player1Items"; // Limpiar los items de player1 porque player2 ya los recibió
                            trade.setPlayer1Items(new ArrayList<>());
                        }

                        // Actualizar solo el campo correspondiente en la base de datos
                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(fieldToUpdate, trade.serializeItems(new ArrayList<>())));

                        cacheManager.put(tradeId, trade);
                        future.complete(itemsToReceive);
                    } else {
                        future.complete(new ArrayList<>());
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al obtener y remover items del trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Boolean> hasTradeItems(long tradeId, UUID playerUUID) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            List<ItemStack> items = cached.getPlayer1().equals(playerUUID) ? cached.getPlayer1Items()
                    : cached.getPlayer2Items();
            future.complete(!items.isEmpty());
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(tradeId, trade);
                        List<ItemStack> items = trade.getPlayer1().equals(playerUUID) ? trade.getPlayer1Items()
                                : trade.getPlayer2Items();
                        future.complete(!items.isEmpty());
                    } else {
                        future.complete(false);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al verificar items del trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<TradeState> getTradeState(long tradeId) {
        CompletableFuture<TradeState> future = new CompletableFuture<>();

        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            future.complete(cached.getState());
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(tradeId, trade);
                        future.complete(trade.getState());
                    } else {
                        future.complete(TradeState.CANCELLED);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al obtener estado del trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<List<Long>> getPlayerPendingTrades(UUID playerUUID) {
        CompletableFuture<List<Long>> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<Document> docs = mongoDBManager.getTradesCollection()
                            .find(new Document("$or", Arrays.asList(
                                    new Document("player1", playerUUID.toString()),
                                    new Document("player2", playerUUID.toString()))).append("$or", Arrays.asList(
                                            new Document("state", TradeState.PENDING.name()),
                                            new Document("state", TradeState.ACTIVE.name()))))
                            .into(new ArrayList<>());

                    List<Long> tradeIds = docs.stream()
                            .map(doc -> doc.getLong("tradeId"))
                            .collect(Collectors.toList());

                    // Actualizar caché
                    docs.forEach(doc -> {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(trade.getTradeId(), trade);
                    });

                    future.complete(tradeIds);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al obtener trades pendientes: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Boolean> arePlayersInTrade(UUID player1, UUID player2) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(new Document("$or", Arrays.asList(
                                    new Document()
                                            .append("player1", player1.toString())
                                            .append("player2", player2.toString()),
                                    new Document()
                                            .append("player1", player2.toString())
                                            .append("player2", player1.toString())))
                                    .append("$or", Arrays.asList(
                                            new Document("state", TradeState.PENDING.name()),
                                            new Document("state", TradeState.ACTIVE.name()))))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(trade.getTradeId(), trade);
                    }

                    future.complete(doc != null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al verificar si los jugadores están en trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<TradeDocument> getTradeInfo(long tradeId) {
        CompletableFuture<TradeDocument> future = new CompletableFuture<>();

        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            future.complete(cached);
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(tradeId, trade);
                        future.complete(trade);
                    } else {
                        future.complete(null);
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al obtener información del trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Void> cleanupTrade(long tradeId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    mongoDBManager.getTradesCollection().deleteOne(Filters.eq("tradeId", tradeId));
                    cacheManager.remove(tradeId);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al limpiar el trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    public CompletableFuture<Void> updateItemsSentStatus(long tradeId, UUID playerUUID, boolean sent) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Document doc = mongoDBManager.getTradesCollection()
                            .find(Filters.eq("tradeId", tradeId))
                            .first();

                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        String field = trade.getPlayer1().equals(playerUUID) ? "itemsSentToPlayer1"
                                : "itemsSentToPlayer2";

                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(field, sent));

                        // Actualizar el caché
                        if (trade.getPlayer1().equals(playerUUID)) {
                            trade.setItemsSentToPlayer1(sent);
                        } else {
                            trade.setItemsSentToPlayer2(sent);
                        }
                        cacheManager.put(tradeId, trade);
                    }
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error al actualizar estado de envío de items: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Actualizar el estado de aceptación de un jugador en el trade
     */
    public CompletableFuture<Void> updatePlayerAcceptance(long tradeId, UUID playerUUID, boolean accepted) {
        return CompletableFuture.runAsync(() -> {
            try {
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    String field = trade.getPlayer1().equals(playerUUID) ? "player1Accepted" : "player2Accepted";

                    mongoDBManager.getTradesCollection().updateOne(
                            Filters.eq("tradeId", tradeId),
                            Updates.set(field, accepted));

                    // Actualizar el caché
                    if (trade.getPlayer1().equals(playerUUID)) {
                        trade.setPlayer1Accepted(accepted);
                    } else {
                        trade.setPlayer2Accepted(accepted);
                    }
                    cacheManager.put(tradeId, trade);
                }
            } catch (Exception e) {
                logError("Error updating player acceptance", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Verificar si un jugador ha aceptado el trade
     */
    public CompletableFuture<Boolean> hasPlayerAccepted(long tradeId, UUID playerUUID) {
        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            boolean accepted = cached.getPlayer1().equals(playerUUID) ? cached.isPlayer1Accepted()
                    : cached.isPlayer2Accepted();
            return CompletableFuture.completedFuture(accepted);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);
                    return trade.getPlayer1().equals(playerUUID) ? trade.isPlayer1Accepted()
                            : trade.isPlayer2Accepted();
                }
                return false;
            } catch (Exception e) {
                plugin.getLogger().severe("Error checking player acceptance: " + e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Verificar si ambos jugadores han aceptado el trade
     */
    public CompletableFuture<Boolean> haveBothPlayersAccepted(long tradeId) {
        // Primero intentamos con el caché
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(
                    cached.isPlayer1Accepted() && cached.isPlayer2Accepted());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);
                    return trade.isPlayer1Accepted() && trade.isPlayer2Accepted();
                }
                return false;
            } catch (Exception e) {
                plugin.getLogger().severe("Error checking both players acceptance: " + e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Get trade counts for a player (for login notifications)
     */
    public CompletableFuture<TradeNotificationData> getPlayerTradeNotifications(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int pendingCount = 0;
                int activeCount = 0;

                // Count pending trades (where player is the target)
                Document pendingFilter = new Document("$and", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("player1", playerId.toString()),
                                new Document("player2", playerId.toString()))),
                        new Document("state", TradeState.PENDING.name())));
                pendingCount = (int) mongoDBManager.getTradesCollection().countDocuments(pendingFilter);

                // Count active trades where player is involved
                Document activeFilter = new Document("$and", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("player1", playerId.toString()),
                                new Document("player2", playerId.toString()))),
                        new Document("state", TradeState.ACTIVE.name())));
                activeCount = (int) mongoDBManager.getTradesCollection().countDocuments(activeFilter);

                return new TradeNotificationData(pendingCount, activeCount);

            } catch (Exception e) {
                plugin.getLogger()
                        .severe("Error getting trade notifications for player " + playerId + ": " + e.getMessage());
                return new TradeNotificationData(0, 0);
            }
        });
    }

    /**
     * Data class for trade notification counts
     */
    public static class TradeNotificationData {
        private final int pendingCount;
        private final int activeCount;

        public TradeNotificationData(int pendingCount, int activeCount) {
            this.pendingCount = pendingCount;
            this.activeCount = activeCount;
        }

        public int getPendingCount() {
            return pendingCount;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getTotalCount() {
            return pendingCount + activeCount;
        }

        public boolean hasAnyTrades() {
            return getTotalCount() > 0;
        }

        public boolean hasPendingOnly() {
            return pendingCount > 0 && activeCount == 0;
        }

        public boolean hasActiveOnly() {
            return activeCount > 0 && pendingCount == 0;
        }

        public boolean hasMixed() {
            return pendingCount > 0 && activeCount > 0;
        }
    }

    /**
     * Completar el trade y distribuir items automáticamente a jugadores conectados
     */
    public CompletableFuture<Boolean> completeTrade(long tradeId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Obtener información del trade
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc == null) {
                    return false;
                }

                TradeDocument trade = new TradeDocument(doc);

                // Verificar que el trade esté en estado ACTIVE
                if (trade.getState() != TradeState.ACTIVE) {
                    return false;
                }

                // Cambiar estado a COMPLETED
                trade.setState(TradeState.COMPLETED);

                // Distribuir items automáticamente a jugadores conectados
                UUID player1UUID = trade.getPlayer1();
                UUID player2UUID = trade.getPlayer2();

                // Obtener items de cada jugador
                List<ItemStack> player1Items = trade.getPlayer1Items(); // Items que va a recibir player2
                List<ItemStack> player2Items = trade.getPlayer2Items(); // Items que va a recibir player1

                // Marcar inicialmente como no enviados
                boolean player1ReceivedItems = false;
                boolean player2ReceivedItems = false;

                // Intentar dar items a player1 (items de player2)
                Player player1Online = Bukkit.getPlayer(player1UUID);
                if (player1Online != null && player1Online.isOnline()) {
                    player1ReceivedItems = giveItemsToPlayer(player1Online, player2Items);
                    if (player1ReceivedItems) {
                        // Enviar mensaje de éxito
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player1Online,
                                    "trade.completion.items_received",
                                    "trade_id", String.valueOf(tradeId));
                        });
                    }
                }

                // Intentar dar items a player2 (items de player1)
                Player player2Online = Bukkit.getPlayer(player2UUID);
                if (player2Online != null && player2Online.isOnline()) {
                    player2ReceivedItems = giveItemsToPlayer(player2Online, player1Items);
                    if (player2ReceivedItems) {
                        // Enviar mensaje de éxito
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player2Online,
                                    "trade.completion.items_received",
                                    "trade_id", String.valueOf(tradeId));
                        });
                    }
                }

                // Actualizar el trade document con la información de quién recibió items
                trade.setItemsSentToPlayer1(player1ReceivedItems);
                trade.setItemsSentToPlayer2(player2ReceivedItems);

                // Si un jugador no recibió items, mantener sus items para MyTradesGUI
                if (!player1ReceivedItems) {
                    // Player1 no recibió items, mantener player2Items para él
                    trade.setPlayer2Items(player2Items); // Items que debe recibir player1
                } else {
                    // Player1 recibió items, limpiar
                    trade.setPlayer2Items(new ArrayList<>());
                }

                if (!player2ReceivedItems) {
                    // Player2 no recibió items, mantener player1Items para él
                    trade.setPlayer1Items(player1Items); // Items que debe recibir player2
                } else {
                    // Player2 recibió items, limpiar
                    trade.setPlayer1Items(new ArrayList<>());
                }

                // Actualizar en base de datos
                mongoDBManager.getTradesCollection().updateOne(
                        Filters.eq("tradeId", tradeId),
                        Updates.combine(
                                Updates.set("state", trade.getState().name()),
                                Updates.set("itemsSentToPlayer1", trade.areItemsSentToPlayer1()),
                                Updates.set("itemsSentToPlayer2", trade.areItemsSentToPlayer2()),
                                Updates.set("player1Items", trade.serializeItems(trade.getPlayer1Items())),
                                Updates.set("player2Items", trade.serializeItems(trade.getPlayer2Items()))));

                // Actualizar caché
                cacheManager.put(tradeId, trade);

                plugin.getLogger()
                        .info(String.format(
                                "Trade %d completed - Player1 received items: %b, Player2 received items: %b",
                                tradeId, player1ReceivedItems, player2ReceivedItems));

                return true;

            } catch (Exception e) {
                logError("Error completing trade", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Dar items a un jugador, manejando inventario lleno
     */
    private boolean giveItemsToPlayer(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true; // No hay items que dar, se considera exitoso
        }

        try {
            for (ItemStack item : items) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    // Intentar agregar al inventario
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item.clone());
                    } else {
                        // Inventario lleno, dropear items cerca del jugador
                        player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                        // Notificar que el inventario estaba lleno
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player, "trade.completion.inventory_full");
                        });
                    }
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error giving items to player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void logError(String message, String details) {
        plugin.getLogger().log(Level.SEVERE, "{0}: {1}", new Object[] { message, details });
    }

    /**
     * Obtener los items que un jugador debe recibir del trade (items del otro
     * jugador)
     */
    public CompletableFuture<List<ItemStack>> getTradeItemsForPlayer(long tradeId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Primero intentamos con el caché
                TradeDocument cached = cacheManager.get(tradeId);
                if (cached != null) {
                    // Devolver los items del OTRO jugador (los que debe recibir este jugador)
                    List<ItemStack> itemsToReceive = cached.getPlayer1().equals(playerUUID)
                            ? cached.getPlayer2Items() // Si es player1, recibe los items de player2
                            : cached.getPlayer1Items(); // Si es player2, recibe los items de player1
                    return new ArrayList<>(itemsToReceive);
                }

                // Si no está en caché, buscar en base de datos
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);

                    // Devolver los items del OTRO jugador
                    List<ItemStack> itemsToReceive = trade.getPlayer1().equals(playerUUID)
                            ? trade.getPlayer2Items() // Si es player1, recibe los items de player2
                            : trade.getPlayer1Items(); // Si es player2, recibe los items de player1

                    return new ArrayList<>(itemsToReceive);
                } else {
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting trade items for player: " + e.getMessage());
                throw new CompletionException(e);
            }
        });
    }
}