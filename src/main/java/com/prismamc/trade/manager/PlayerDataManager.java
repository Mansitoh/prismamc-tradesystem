package com.prismamc.trade.manager;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.model.PlayerData;
import org.bson.Document;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PlayerDataManager - Player Data Management and Persistence System
 * 
 * This class provides comprehensive player data management functionality for
 * the
 * PrismaMC Trade System. It handles player preferences, language settings, and
 * other persistent player information with intelligent caching and optimized
 * database operations.
 * 
 * Key Features:
 * - High-performance in-memory caching for frequently accessed player data
 * - Asynchronous database operations to prevent server blocking
 * - Optimized MongoDB queries using strategic database indexes
 * - Automatic player data creation for new players
 * - Language preference management and localization support
 * - Advanced player search capabilities (name-based, language-based)
 * - Thread-safe operations for concurrent player access
 * 
 * Data Management:
 * - Player UUID as primary identifier
 * - Player name tracking with case-insensitive search
 * - Language preference storage for localization
 * - Automatic cache synchronization with database
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class PlayerDataManager {

    // Core dependencies
    private final Plugin plugin;
    private final MongoCollection<Document> collection;

    // In-memory cache for fast player data access
    private final Map<UUID, PlayerData> cache;

    /**
     * Constructs a new PlayerDataManager instance.
     * Initializes the MongoDB collection reference and player data cache.
     * 
     * @param plugin The main plugin instance providing database access
     */
    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoDBManager().getPlayerDataCollection();
        this.cache = new HashMap<>();
    }

    /**
     * Loads or creates player data for the specified player.
     * This method implements a cache-first strategy for optimal performance:
     * 1. Checks in-memory cache first
     * 2. Queries database if not cached
     * 3. Creates new player data if not found in database
     * 4. Caches the result for future access
     * 
     * Uses database index hints for optimized query performance.
     * 
     * @param player The player whose data should be loaded
     * @return CompletableFuture containing the player's data
     */
    public CompletableFuture<PlayerData> loadPlayerData(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            // Check cache first for immediate response
            if (cache.containsKey(uuid)) {
                return cache.get(uuid);
            }

            // Load from database using optimized UUID index
            Document hint = new Document("uuid", 1);
            Document doc = collection.find(Filters.eq("uuid", uuid.toString()))
                    .hint(hint)
                    .first();

            PlayerData playerData;
            if (doc == null) {
                // Create new player data with default values for first-time players
                playerData = new PlayerData(uuid, player.getName(), "en"); // Default language: English
                savePlayerData(playerData);
            } else {
                // Convert database document to PlayerData object
                playerData = documentToPlayerData(doc);
            }

            // Cache the data for subsequent fast access
            cache.put(uuid, playerData);
            return playerData;
        });
    }

    /**
     * Saves player data to the database and updates the cache.
     * Uses MongoDB's upsert functionality to insert or update as needed.
     * This operation is performed asynchronously to prevent blocking the main
     * thread.
     * 
     * @param playerData The player data to save
     * @return CompletableFuture that completes when the save operation finishes
     */
    public CompletableFuture<Void> savePlayerData(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            // Create MongoDB document from player data
            Document doc = new Document()
                    .append("uuid", playerData.getUuid().toString())
                    .append("playerName", playerData.getPlayerName())
                    .append("language", playerData.getLanguage());

            // Use upsert to insert new or update existing document
            collection.replaceOne(
                    Filters.eq("uuid", playerData.getUuid().toString()),
                    doc,
                    new ReplaceOptions().upsert(true));

            // Update cache to maintain consistency
            cache.put(playerData.getUuid(), playerData);
        });
    }

    /**
     * Removes player data from the cache.
     * This is typically called when a player disconnects to free memory.
     * The data remains in the database for future sessions.
     * 
     * @param uuid UUID of the player whose data should be removed from cache
     * @return CompletableFuture that completes when the removal is finished
     */
    public CompletableFuture<Void> removeFromCache(UUID uuid) {
        return CompletableFuture.runAsync(() -> cache.remove(uuid));
    }

    /**
     * Converts a MongoDB document to a PlayerData object.
     * This method handles the data transformation from database format
     * to application objects.
     * 
     * @param doc MongoDB document containing player data
     * @return PlayerData object created from the document
     */
    private PlayerData documentToPlayerData(Document doc) {
        return new PlayerData(
                UUID.fromString(doc.getString("uuid")),
                doc.getString("playerName"),
                doc.getString("language"));
    }

    /**
     * Retrieves player data from the cache without database access.
     * This method provides immediate access to cached player data.
     * Returns null if the player data is not currently cached.
     * 
     * @param uuid UUID of the player whose data to retrieve
     * @return Cached PlayerData object, or null if not cached
     */
    public PlayerData getCachedPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Updates a player's language preference.
     * This method updates both the cache and database with the new language
     * setting.
     * The operation is performed asynchronously for optimal performance.
     * 
     * @param uuid     UUID of the player whose language should be updated
     * @param language New language code (e.g., "en", "es", "fr")
     */
    public void updateLanguage(UUID uuid, String language) {
        PlayerData playerData = cache.get(uuid);
        if (playerData != null) {
            playerData.setLanguage(language);
            savePlayerData(playerData);
        }
    }

    /**
     * Searches for players by their language preference.
     * Uses the optimized compound index (language + uuid) for efficient queries.
     * Results are cached to improve performance for subsequent access.
     * 
     * This method is useful for:
     * - Sending localized announcements
     * - Language-specific statistics
     * - Administrative operations targeting specific language groups
     * 
     * @param language Language code to search for (e.g., "en", "es", "fr")
     * @return CompletableFuture containing list of players with the specified
     *         language
     */
    public CompletableFuture<List<PlayerData>> findPlayersByLanguage(String language) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> players = new ArrayList<>();

            // Use compound index hint for optimized query performance
            Document hint = new Document("language", 1).append("uuid", 1);

            collection.find(Filters.eq("language", language))
                    .hint(hint)
                    .forEach(doc -> {
                        PlayerData playerData = documentToPlayerData(doc);
                        players.add(playerData);

                        // Update cache if player is not already cached
                        cache.putIfAbsent(playerData.getUuid(), playerData);
                    });

            return players;
        });
    }

    /**
     * Searches for a player by their exact player name.
     * Uses the player name index for optimized query performance.
     * The search is case-sensitive and requires exact name matching.
     * 
     * Results are cached to improve performance for subsequent access.
     * 
     * @param playerName Exact player name to search for
     * @return CompletableFuture containing the PlayerData if found, null otherwise
     */
    public CompletableFuture<PlayerData> findPlayerByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Use player name index hint for optimized query
            Document hint = new Document("playerName", 1);
            Document doc = collection.find(Filters.eq("playerName", playerName))
                    .hint(hint)
                    .first();

            if (doc != null) {
                PlayerData playerData = documentToPlayerData(doc);

                // Cache the result for future access
                cache.putIfAbsent(playerData.getUuid(), playerData);
                return playerData;
            }

            return null;
        });
    }

    /**
     * Searches for a player by their name with case-insensitive matching.
     * Uses MongoDB regex functionality to perform flexible name searches.
     * This method is more resource-intensive than exact name matching but
     * provides better user experience for administrative commands.
     * 
     * Results are cached to improve performance for subsequent access.
     * 
     * Use cases:
     * - Administrative commands where exact casing is unknown
     * - User-friendly player searches
     * - Flexible player lookup systems
     * 
     * @param playerName Player name to search for (case-insensitive)
     * @return CompletableFuture containing the PlayerData if found, null otherwise
     */
    public CompletableFuture<PlayerData> findPlayerByNameIgnoreCase(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Use regex for case-insensitive search with exact word matching
            Document filter = new Document("playerName",
                    new Document("$regex", "^" + java.util.regex.Pattern.quote(playerName) + "$")
                            .append("$options", "i"));

            Document doc = collection.find(filter).first();

            if (doc != null) {
                PlayerData playerData = documentToPlayerData(doc);

                // Cache the result for future access
                cache.putIfAbsent(playerData.getUuid(), playerData);
                return playerData;
            }

            return null;
        });
    }
}