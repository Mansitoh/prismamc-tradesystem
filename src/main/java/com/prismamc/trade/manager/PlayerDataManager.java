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

public class PlayerDataManager {
    private final Plugin plugin;
    private final MongoCollection<Document> collection;
    private final Map<UUID, PlayerData> cache;

    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoDBManager().getPlayerDataCollection();
        this.cache = new HashMap<>();
    }

    public CompletableFuture<PlayerData> loadPlayerData(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();
            
            // Check cache first
            if (cache.containsKey(uuid)) {
                return cache.get(uuid);
            }

            // Load from database usando el índice uuid
            Document hint = new Document("uuid", 1);
            Document doc = collection.find(Filters.eq("uuid", uuid.toString()))
                                  .hint(hint)
                                  .first();
            
            PlayerData playerData;
            if (doc == null) {
                // Create new player data with default values
                playerData = new PlayerData(uuid, player.getName(), "en"); // Default language English
                savePlayerData(playerData);
            } else {
                playerData = documentToPlayerData(doc);
            }

            // Cache the data
            cache.put(uuid, playerData);
            return playerData;
        });
    }

    public CompletableFuture<Void> savePlayerData(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document()
                    .append("uuid", playerData.getUuid().toString())
                    .append("playerName", playerData.getPlayerName())
                    .append("language", playerData.getLanguage());

            collection.replaceOne(
                    Filters.eq("uuid", playerData.getUuid().toString()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );

            // Update cache
            cache.put(playerData.getUuid(), playerData);
        });
    }

    public CompletableFuture<Void> removeFromCache(UUID uuid) {
        return CompletableFuture.runAsync(() -> cache.remove(uuid));
    }

    private PlayerData documentToPlayerData(Document doc) {
        return new PlayerData(
            UUID.fromString(doc.getString("uuid")),
            doc.getString("playerName"),
            doc.getString("language")
        );
    }

    public PlayerData getCachedPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    public void updateLanguage(UUID uuid, String language) {
        PlayerData playerData = cache.get(uuid);
        if (playerData != null) {
            playerData.setLanguage(language);
            savePlayerData(playerData);
        }
    }

    /**
     * Buscar jugadores por idioma (usando el índice compuesto)
     */
    public CompletableFuture<List<PlayerData>> findPlayersByLanguage(String language) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> players = new ArrayList<>();
            Document hint = new Document("language", 1).append("uuid", 1);
            
            collection.find(Filters.eq("language", language))
                     .hint(hint)
                     .forEach(doc -> {
                         PlayerData playerData = documentToPlayerData(doc);
                         players.add(playerData);
                         // Actualizar caché si el jugador no está en ella
                         cache.putIfAbsent(playerData.getUuid(), playerData);
                     });
            
            return players;
        });
    }

    /**
     * Buscar jugador por nombre (usando el índice de nombre)
     */
    public CompletableFuture<PlayerData> findPlayerByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Document hint = new Document("playerName", 1);
            Document doc = collection.find(Filters.eq("playerName", playerName))
                                  .hint(hint)
                                  .first();
            
            if (doc != null) {
                PlayerData playerData = documentToPlayerData(doc);
                cache.putIfAbsent(playerData.getUuid(), playerData);
                return playerData;
            }
            
            return null;
        });
    }
}