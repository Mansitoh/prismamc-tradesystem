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

/**
 * TradeManager - Core trading system manager
 * 
 * This class handles all trading operations including trade creation,
 * validation,
 * item management, state transitions, and database operations. It provides
 * thread-safe operations with caching mechanisms for optimal performance.
 * 
 * Features:
 * - Asynchronous trade operations
 * - Intelligent caching system
 * - Automatic cleanup of expired trades
 * - Bulk database operations for performance
 * - Comprehensive error handling
 * - Player notification system
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class TradeManager {

    // Core dependencies
    private final Plugin plugin;
    private final MongoDBManager mongoDBManager;

    // Trade ID generation and caching
    private final AtomicLong tradeIdGenerator;
    private final CacheManager cacheManager;
    private final Map<UUID, List<Long>> playerTradesCache;

    /**
     * Enumeration representing the various states a trade can be in
     * during its lifecycle.
     */
    public enum TradeState {
        /** Trade has been created but not yet accepted by both parties */
        PENDING,
        /** Trade is active and both parties are configuring items */
        ACTIVE,
        /** Trade has been completed successfully */
        COMPLETED,
        /** Trade has been cancelled by either party or system */
        CANCELLED
    }

    /**
     * Constructs a new TradeManager instance with the specified plugin.
     * Initializes all required components including ID generator, cache manager,
     * and starts background cleanup tasks.
     * 
     * @param plugin The main plugin instance
     */
    public TradeManager(Plugin plugin) {
        this.plugin = plugin;
        this.mongoDBManager = plugin.getMongoDBManager();
        this.tradeIdGenerator = new AtomicLong(1);
        this.cacheManager = new CacheManager();
        this.playerTradesCache = new ConcurrentHashMap<>();

        // Initialize systems
        initializeIdGenerator();
        startPeriodicCleanup();
    }

    /**
     * Initializes the trade ID generator by finding the highest existing trade ID
     * in the database and setting the generator to the next available ID.
     * This ensures no ID conflicts when creating new trades.
     */
    private void initializeIdGenerator() {
        CompletableFuture.runAsync(() -> {
            try {
                // Find the highest trade ID in the database
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

    /**
     * Starts a periodic cleanup task that removes expired trades from the database.
     * Runs every hour and removes trades older than 24 hours that are still
     * in PENDING or ACTIVE state.
     */
    private void startPeriodicCleanup() {
        // Clean up expired trades every hour
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                long expiryTime = System.currentTimeMillis() - (3600000 * 24); // 24 hours ago
                mongoDBManager.getTradesCollection().deleteMany(
                        Filters.and(
                                Filters.lt("timestamp", expiryTime),
                                Filters.in("state", Arrays.asList(
                                        TradeState.PENDING.name(),
                                        TradeState.ACTIVE.name()))));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during periodic cleanup: {0}", e.getMessage());
            }
        }, 20L * 3600, 20L * 3600); // Every hour (3600 seconds)
    }

    /**
     * Gracefully shuts down the TradeManager by cleaning up resources
     * and stopping background tasks.
     */
    public void shutdown() {
        cacheManager.shutdown();
    }

    /**
     * Creates a new trade between two players and stores it in the database.
     * The trade is initialized with PENDING state and empty item lists.
     * 
     * @param player1 UUID of the first player (trade initiator)
     * @param player2 UUID of the second player (trade recipient)
     * @return CompletableFuture containing the generated trade ID
     */
    public CompletableFuture<Long> createNewTrade(UUID player1, UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate unique trade ID
                long tradeId = tradeIdGenerator.getAndIncrement();
                TradeDocument tradeDoc = new TradeDocument(tradeId, player1, player2);

                // Store in database
                mongoDBManager.getTradesCollection().insertOne(tradeDoc.toDocument());

                // Cache the new trade
                cacheManager.put(tradeId, tradeDoc);

                // Update player trades cache for quick lookups
                playerTradesCache.computeIfAbsent(player1, k -> new ArrayList<>()).add(tradeId);
                playerTradesCache.computeIfAbsent(player2, k -> new ArrayList<>()).add(tradeId);

                return tradeId;
            } catch (Exception e) {
                logError("Error creating new trade", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Updates the state of an existing trade in both database and cache.
     * 
     * @param tradeId  The ID of the trade to update
     * @param newState The new state to set for the trade
     * @return CompletableFuture that completes when the update is finished
     */
    public CompletableFuture<Void> updateTradeState(long tradeId, TradeState newState) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Update in database
                mongoDBManager.getTradesCollection().updateOne(
                        Filters.eq("tradeId", tradeId),
                        Updates.set("state", newState.name()));

                // Update cache if present
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

    /**
     * Validates whether a trade is still active and can be modified.
     * A trade is considered valid if it's not CANCELLED or COMPLETED.
     * 
     * @param tradeId The ID of the trade to validate
     * @return CompletableFuture containing true if the trade is valid
     */
    public CompletableFuture<Boolean> isTradeValid(long tradeId) {
        // First try cache for faster response
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(
                    cached.getState() != TradeState.CANCELLED &&
                            cached.getState() != TradeState.COMPLETED);
        }

        // Fallback to database query
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

    /**
     * Performs a batch update operation on multiple trades for improved
     * performance.
     * This method is useful when multiple trades need to be updated simultaneously.
     * 
     * @param trades List of TradeDocument objects to update
     * @return CompletableFuture that completes when all updates are finished
     */
    public CompletableFuture<Void> batchUpdateTrades(List<TradeDocument> trades) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<WriteModel<Document>> writes = new ArrayList<>();

                // Prepare batch write operations
                for (TradeDocument trade : trades) {
                    writes.add(new UpdateOneModel<>(
                            Filters.eq("tradeId", trade.getTradeId()),
                            Updates.combine(
                                    Updates.set("state", trade.getState().name()),
                                    Updates.set("player1Items", trade.serializeItems(trade.getPlayer1Items())),
                                    Updates.set("player2Items", trade.serializeItems(trade.getPlayer2Items())))));

                    // Update cache
                    cacheManager.put(trade.getTradeId(), trade);
                }

                // Execute batch write if there are operations to perform
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

    /**
     * Stores the items that a player wants to trade in the specified trade.
     * This method updates both the database and cache with the new item list.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player storing items
     * @param items      The list of items to store
     * @return CompletableFuture that completes when items are stored
     */
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

                        // Determine which field to update based on player
                        String field = trade.getPlayer1().equals(playerUUID) ? "player1Items" : "player2Items";

                        // Update trade document with new items
                        if (trade.getPlayer1().equals(playerUUID)) {
                            trade.setPlayer1Items(items);
                        } else {
                            trade.setPlayer2Items(items);
                        }

                        // Save to database
                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(field, trade.serializeItems(items)));

                        // Update cache
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

    /**
     * Retrieves the items that a player has placed in a trade.
     * This method first checks the cache before querying the database.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player whose items to retrieve
     * @return CompletableFuture containing the list of items
     */
    public CompletableFuture<List<ItemStack>> getTradeItems(long tradeId, UUID playerUUID) {
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();

        // First try cache for better performance
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            List<ItemStack> items = cached.getPlayer1().equals(playerUUID) ? cached.getPlayer1Items()
                    : cached.getPlayer2Items();
            future.complete(new ArrayList<>(items));
            return future;
        }

        // Fallback to database query
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

    /**
     * Retrieves and removes the items from a trade for a specific player.
     * This is typically used when cancelling a trade to return items to the player.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player whose items to retrieve and remove
     * @return CompletableFuture containing the list of items that were removed
     */
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
                        String field = trade.getPlayer1().equals(playerUUID) ? "player1Items" : "player2Items";
                        List<ItemStack> items = trade.getPlayer1().equals(playerUUID) ? trade.getPlayer1Items()
                                : trade.getPlayer2Items();

                        // Clear items from database
                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(field, trade.serializeItems(new ArrayList<>())));

                        // Clear items from trade document and update cache
                        if (trade.getPlayer1().equals(playerUUID)) {
                            trade.setPlayer1Items(new ArrayList<>());
                        } else {
                            trade.setPlayer2Items(new ArrayList<>());
                        }
                        cacheManager.put(tradeId, trade);

                        future.complete(items);
                    } else {
                        future.complete(new ArrayList<>());
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error retrieving and removing trade items: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Checks whether a player has any items stored in a trade.
     * 
     * @param tradeId    The ID of the trade to check
     * @param playerUUID The UUID of the player to check
     * @return CompletableFuture containing true if the player has items in the
     *         trade
     */
    public CompletableFuture<Boolean> hasTradeItems(long tradeId, UUID playerUUID) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // First try cache
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            List<ItemStack> items = cached.getPlayer1().equals(playerUUID) ? cached.getPlayer1Items()
                    : cached.getPlayer2Items();
            future.complete(!items.isEmpty());
            return future;
        }

        // Fallback to database query
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
                    plugin.getLogger().severe("Error checking trade items: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Retrieves the current state of a trade.
     * 
     * @param tradeId The ID of the trade
     * @return CompletableFuture containing the current TradeState
     */
    public CompletableFuture<TradeState> getTradeState(long tradeId) {
        CompletableFuture<TradeState> future = new CompletableFuture<>();

        // First try cache
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            future.complete(cached.getState());
            return future;
        }

        // Fallback to database query
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
                    plugin.getLogger().severe("Error getting trade state: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Retrieves all pending trades for a specific player.
     * This includes trades where the player is either the initiator or recipient.
     * 
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing a list of trade IDs
     */
    public CompletableFuture<List<Long>> getPlayerPendingTrades(UUID playerUUID) {
        CompletableFuture<List<Long>> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Query for trades where player is involved and trade is pending or active
                    List<Document> docs = mongoDBManager.getTradesCollection()
                            .find(new Document("$or", Arrays.asList(
                                    new Document("player1", playerUUID.toString()),
                                    new Document("player2", playerUUID.toString()))).append("$or", Arrays.asList(
                                            new Document("state", TradeState.PENDING.name()),
                                            new Document("state", TradeState.ACTIVE.name()))))
                            .into(new ArrayList<>());

                    // Extract trade IDs
                    List<Long> tradeIds = docs.stream()
                            .map(doc -> doc.getLong("tradeId"))
                            .collect(Collectors.toList());

                    // Update cache with found trades
                    docs.forEach(doc -> {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(trade.getTradeId(), trade);
                    });

                    future.complete(tradeIds);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error getting pending trades: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Checks if two players are currently involved in an active trade together.
     * 
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return CompletableFuture containing true if players are in a trade together
     */
    public CompletableFuture<Boolean> arePlayersInTrade(UUID player1, UUID player2) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Search for active trades between these two players
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

                    // Cache the trade if found
                    if (doc != null) {
                        TradeDocument trade = new TradeDocument(doc);
                        cacheManager.put(trade.getTradeId(), trade);
                    }

                    future.complete(doc != null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error checking if players are in trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Retrieves complete information about a specific trade.
     * 
     * @param tradeId The ID of the trade
     * @return CompletableFuture containing the TradeDocument or null if not found
     */
    public CompletableFuture<TradeDocument> getTradeInfo(long tradeId) {
        CompletableFuture<TradeDocument> future = new CompletableFuture<>();

        // First try cache
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            future.complete(cached);
            return future;
        }

        // Fallback to database query
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
                    plugin.getLogger().severe("Error getting trade information: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Completely removes a trade from the database and cache.
     * This is typically used for cleaning up cancelled or expired trades.
     * 
     * @param tradeId The ID of the trade to clean up
     * @return CompletableFuture that completes when cleanup is finished
     */
    public CompletableFuture<Void> cleanupTrade(long tradeId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Remove from database
                    mongoDBManager.getTradesCollection().deleteOne(Filters.eq("tradeId", tradeId));

                    // Remove from cache
                    cacheManager.remove(tradeId);

                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    plugin.getLogger().severe("Error cleaning up trade: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Updates the status indicating whether items have been sent to a specific
     * player.
     * This is used to track item delivery during trade completion.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player
     * @param sent       Whether items have been sent to this player
     * @return CompletableFuture that completes when the status is updated
     */
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

                        // Update database
                        mongoDBManager.getTradesCollection().updateOne(
                                Filters.eq("tradeId", tradeId),
                                Updates.set(field, sent));

                        // Update cache
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
                    plugin.getLogger().severe("Error updating items sent status: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Updates the acceptance status of a player for a specific trade.
     * When both players accept, the trade can proceed to completion.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player accepting/rejecting
     * @param accepted   Whether the player has accepted the trade
     * @return CompletableFuture that completes when the acceptance is updated
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

                    // Update database
                    mongoDBManager.getTradesCollection().updateOne(
                            Filters.eq("tradeId", tradeId),
                            Updates.set(field, accepted));

                    // Update cache
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
     * Checks whether a specific player has accepted a trade.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player to check
     * @return CompletableFuture containing true if the player has accepted
     */
    public CompletableFuture<Boolean> hasPlayerAccepted(long tradeId, UUID playerUUID) {
        // First try cache
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            boolean accepted = cached.getPlayer1().equals(playerUUID) ? cached.isPlayer1Accepted()
                    : cached.isPlayer2Accepted();
            return CompletableFuture.completedFuture(accepted);
        }

        // Fallback to database query
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
     * Checks whether both players have accepted a trade.
     * This is typically used to determine if a trade can proceed to completion.
     * 
     * @param tradeId The ID of the trade
     * @return CompletableFuture containing true if both players have accepted
     */
    public CompletableFuture<Boolean> haveBothPlayersAccepted(long tradeId) {
        // First try cache
        TradeDocument cached = cacheManager.get(tradeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(
                    cached.isPlayer1Accepted() && cached.isPlayer2Accepted());
        }

        // Fallback to database query
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
     * Retrieves trade notification data for a player, including counts of
     * pending and active trades. This is typically used for login notifications.
     * 
     * @param playerId The UUID of the player
     * @return CompletableFuture containing TradeNotificationData
     */
    public CompletableFuture<TradeNotificationData> getPlayerTradeNotifications(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int pendingCount = 0;
                int activeCount = 0;

                // Count pending trades where player is involved
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
     * Data class containing trade notification information for a player.
     * Provides various utility methods for checking trade status.
     */
    public static class TradeNotificationData {
        private final int pendingCount;
        private final int activeCount;

        /**
         * Constructs a new TradeNotificationData instance.
         * 
         * @param pendingCount Number of pending trades
         * @param activeCount  Number of active trades
         */
        public TradeNotificationData(int pendingCount, int activeCount) {
            this.pendingCount = pendingCount;
            this.activeCount = activeCount;
        }

        /** @return Number of pending trades */
        public int getPendingCount() {
            return pendingCount;
        }

        /** @return Number of active trades */
        public int getActiveCount() {
            return activeCount;
        }

        /** @return Total number of trades (pending + active) */
        public int getTotalCount() {
            return pendingCount + activeCount;
        }

        /** @return True if player has any trades */
        public boolean hasAnyTrades() {
            return getTotalCount() > 0;
        }

        /** @return True if player has only pending trades */
        public boolean hasPendingOnly() {
            return pendingCount > 0 && activeCount == 0;
        }

        /** @return True if player has only active trades */
        public boolean hasActiveOnly() {
            return activeCount > 0 && pendingCount == 0;
        }

        /** @return True if player has both pending and active trades */
        public boolean hasMixed() {
            return pendingCount > 0 && activeCount > 0;
        }
    }

    /**
     * Completes a trade and automatically distributes items to online players.
     * For offline players, items are kept in the database for later retrieval.
     * This method handles the entire completion process including item distribution
     * and status updates.
     * 
     * @param tradeId The ID of the trade to complete
     * @return CompletableFuture containing true if the trade was successfully
     *         completed
     */
    public CompletableFuture<Boolean> completeTrade(long tradeId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Retrieve trade information
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc == null) {
                    return false;
                }

                TradeDocument trade = new TradeDocument(doc);

                // Verify trade is in ACTIVE state
                if (trade.getState() != TradeState.ACTIVE) {
                    return false;
                }

                // Update trade state to COMPLETED
                trade.setState(TradeState.COMPLETED);

                // Get player UUIDs and their respective items
                UUID player1UUID = trade.getPlayer1();
                UUID player2UUID = trade.getPlayer2();

                // Get items each player will receive (items from the other player)
                List<ItemStack> player1Items = trade.getPlayer1Items(); // Items player2 will receive
                List<ItemStack> player2Items = trade.getPlayer2Items(); // Items player1 will receive

                // Initialize delivery status
                boolean player1ReceivedItems = false;
                boolean player2ReceivedItems = false;

                // Attempt to deliver items to player1 (items from player2)
                Player player1Online = Bukkit.getPlayer(player1UUID);
                if (player1Online != null && player1Online.isOnline()) {
                    player1ReceivedItems = giveItemsToPlayer(player1Online, player2Items);
                    if (player1ReceivedItems) {
                        // Send success notification
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player1Online,
                                    "trade.success.completion.items_received",
                                    "trade_id", String.valueOf(tradeId));
                        });
                    }
                }

                // Attempt to deliver items to player2 (items from player1)
                Player player2Online = Bukkit.getPlayer(player2UUID);
                if (player2Online != null && player2Online.isOnline()) {
                    player2ReceivedItems = giveItemsToPlayer(player2Online, player1Items);
                    if (player2ReceivedItems) {
                        // Send success notification
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player2Online,
                                    "trade.success.completion.items_received",
                                    "trade_id", String.valueOf(tradeId));
                        });
                    }
                }

                // Update trade document with delivery status
                trade.setItemsSentToPlayer1(player1ReceivedItems);
                trade.setItemsSentToPlayer2(player2ReceivedItems);

                // Manage items for players who didn't receive them immediately
                if (!player1ReceivedItems) {
                    // Player1 didn't receive items, keep player2's items for later retrieval
                    trade.setPlayer2Items(player2Items);
                } else {
                    // Player1 received items, clear the list
                    trade.setPlayer2Items(new ArrayList<>());
                }

                if (!player2ReceivedItems) {
                    // Player2 didn't receive items, keep player1's items for later retrieval
                    trade.setPlayer1Items(player1Items);
                } else {
                    // Player2 received items, clear the list
                    trade.setPlayer1Items(new ArrayList<>());
                }

                // Update database with final trade state
                mongoDBManager.getTradesCollection().updateOne(
                        Filters.eq("tradeId", tradeId),
                        Updates.combine(
                                Updates.set("state", trade.getState().name()),
                                Updates.set("itemsSentToPlayer1", trade.areItemsSentToPlayer1()),
                                Updates.set("itemsSentToPlayer2", trade.areItemsSentToPlayer2()),
                                Updates.set("player1Items", trade.serializeItems(trade.getPlayer1Items())),
                                Updates.set("player2Items", trade.serializeItems(trade.getPlayer2Items()))));

                // Update cache
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
     * Safely gives items to a player, handling cases where the inventory is full.
     * If the inventory is full, items are dropped near the player's location.
     * 
     * @param player The player to give items to
     * @param items  The list of items to give
     * @return True if items were successfully given/dropped, false on error
     */
    private boolean giveItemsToPlayer(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return true; // No items to give, consider successful
        }

        try {
            for (ItemStack item : items) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    // Try to add to inventory first
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item.clone());
                    } else {
                        // Inventory full, drop items near player
                        player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                        // Notify player about inventory being full
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendComponentMessage(player,
                                    "mytrades.completed.inventory_full");
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

    /**
     * Logs error messages with consistent formatting.
     * 
     * @param message The error message
     * @param details Additional error details
     */
    private void logError(String message, String details) {
        plugin.getLogger().log(Level.SEVERE, "{0}: {1}", new Object[] { message, details });
    }

    /**
     * Retrieves the items that a player should receive from a completed trade.
     * This method returns the items from the OTHER player (what this player should
     * receive).
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player requesting items
     * @return CompletableFuture containing the list of items the player should
     *         receive
     */
    public CompletableFuture<List<ItemStack>> getTradeItemsForPlayer(long tradeId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First try cache
                TradeDocument cached = cacheManager.get(tradeId);
                if (cached != null) {
                    // Return items from the OTHER player (what this player should receive)
                    List<ItemStack> itemsToReceive = cached.getPlayer1().equals(playerUUID)
                            ? cached.getPlayer2Items() // If requesting player is player1, get player2's items
                            : cached.getPlayer1Items(); // If requesting player is player2, get player1's items
                    return new ArrayList<>(itemsToReceive);
                }

                // Fallback to database query
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);

                    // Return items from the OTHER player
                    List<ItemStack> itemsToReceive = trade.getPlayer1().equals(playerUUID)
                            ? trade.getPlayer2Items() // If requesting player is player1, get player2's items
                            : trade.getPlayer1Items(); // If requesting player is player2, get player1's items

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

    /**
     * Retrieves items that a player should receive from a completed trade WITHOUT
     * removing them.
     * This method is specifically designed for use with the MyTradesGUI to show
     * available items.
     * Only returns items if the trade is completed and the player hasn't received
     * them yet.
     * 
     * @param tradeId    The ID of the trade
     * @param playerUUID The UUID of the player requesting items
     * @return CompletableFuture containing the list of items available for claiming
     */
    public CompletableFuture<List<ItemStack>> getTradeItemsToReceive(long tradeId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First try cache
                TradeDocument cached = cacheManager.get(tradeId);
                if (cached != null) {
                    // Verify trade is completed
                    if (cached.getState() != TradeState.COMPLETED) {
                        return new ArrayList<>();
                    }

                    // Check if player has already received their items
                    boolean hasAlreadyReceived = cached.getPlayer1().equals(playerUUID)
                            ? cached.areItemsSentToPlayer1()
                            : cached.areItemsSentToPlayer2();

                    if (hasAlreadyReceived) {
                        return new ArrayList<>(); // Player already received items
                    }

                    // Return items from the OTHER player (what this player should receive)
                    List<ItemStack> itemsToReceive = cached.getPlayer1().equals(playerUUID)
                            ? cached.getPlayer2Items() // If requesting player is player1, get player2's items
                            : cached.getPlayer1Items(); // If requesting player is player2, get player1's items
                    return new ArrayList<>(itemsToReceive);
                }

                // Fallback to database query
                Document doc = mongoDBManager.getTradesCollection()
                        .find(Filters.eq("tradeId", tradeId))
                        .first();

                if (doc != null) {
                    TradeDocument trade = new TradeDocument(doc);
                    cacheManager.put(tradeId, trade);

                    // Verify trade is completed
                    if (trade.getState() != TradeState.COMPLETED) {
                        return new ArrayList<>();
                    }

                    // Check if player has already received their items
                    boolean hasAlreadyReceived = trade.getPlayer1().equals(playerUUID)
                            ? trade.areItemsSentToPlayer1()
                            : trade.areItemsSentToPlayer2();

                    if (hasAlreadyReceived) {
                        return new ArrayList<>(); // Player already received items
                    }

                    // Return items from the OTHER player
                    List<ItemStack> itemsToReceive = trade.getPlayer1().equals(playerUUID)
                            ? trade.getPlayer2Items() // If requesting player is player1, get player2's items
                            : trade.getPlayer1Items(); // If requesting player is player2, get player1's items

                    return new ArrayList<>(itemsToReceive);
                } else {
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting trade items to receive for player: " + e.getMessage());
                throw new CompletionException(e);
            }
        });
    }
}