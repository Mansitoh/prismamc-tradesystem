package com.prismamc.trade.manager;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.connection.ConnectionPoolSettings;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

public class MongoDBManager {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> tradesCollection;
    private MongoCollection<Document> playerDataCollection;
    private MongoCollection<Document> messagesCollection;
    private final Logger logger;

    private static final int MIN_CONNECTIONS_PER_HOST = 5;
    private static final int MAX_CONNECTIONS_PER_HOST = 20;
    private static final int MAX_WAIT_TIME = 5000;
    private static final int MAX_CONNECTION_IDLE_TIME = 300000;
    private static final int CONNECTION_TIMEOUT = 5000;

    public MongoDBManager(Logger logger) {
        this.logger = logger;
    }

    public void connect(FileConfiguration config) {
        try {
            setupMongoClient(config);
            initializeDatabase(config);
            initializeCollections();
            setupIndexes();
            logger.info("MongoDB connection and initialization completed successfully!");
        } catch (Exception e) {
            logger.severe("Failed to initialize MongoDB: " + e.getMessage());
            throw new RuntimeException("Error initializing MongoDB", e);
        }
    }

    private void setupMongoClient(FileConfiguration config) {
        String uri = config.getString("mongodb.connection-uri");
        MongoClientSettings settings = createMongoClientSettings(config, uri);
        mongoClient = MongoClients.create(settings);
    }

    private MongoClientSettings createMongoClientSettings(FileConfiguration config, String uri) {
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .minSize(MIN_CONNECTIONS_PER_HOST)
                .maxSize(MAX_CONNECTIONS_PER_HOST)
                .maxWaitTime(MAX_WAIT_TIME, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(MAX_CONNECTION_IDLE_TIME, TimeUnit.MILLISECONDS)
                .build();

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                .applyToSocketSettings(builder -> builder.connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS));

        if (uri != null && !uri.isEmpty()) {
            settingsBuilder.applyConnectionString(new ConnectionString(uri));
        } else {
            String host = config.getString("mongodb.host", "localhost");
            int port = config.getInt("mongodb.port", 27017);
            String username = config.getString("mongodb.username", "");
            String password = config.getString("mongodb.password", "");
            String authDatabase = config.getString("mongodb.auth-database", "admin");

            String connectionString = createConnectionString(host, port, username, password, authDatabase);
            settingsBuilder.applyConnectionString(new ConnectionString(connectionString));
        }

        return settingsBuilder.build();
    }

    private String createConnectionString(String host, int port, String username, String password,
            String authDatabase) {
        if (!username.isEmpty() && !password.isEmpty()) {
            return String.format("mongodb://%s:%s@%s:%d/?authSource=%s",
                    username, password, host, port, authDatabase);
        }
        return String.format("mongodb://%s:%d", host, port);
    }

    private void initializeDatabase(FileConfiguration config) {
        String databaseName = config.getString("mongodb.database", "prismamc_trade");
        database = mongoClient.getDatabase(databaseName);
        verifyConnection();
    }

    private void initializeCollections() {
        String[] collections = { "trades", "player_data", "messages" };

        for (String collectionName : collections) {
            try {
                if (!collectionExists(collectionName)) {
                    database.createCollection(collectionName);
                    logger.info("Created collection: " + collectionName);
                }
            } catch (Exception e) {
                logger.warning("Error creating collection " + collectionName + ": " + e.getMessage());
            }
        }

        tradesCollection = database.getCollection("trades");
        playerDataCollection = database.getCollection("player_data");
        messagesCollection = database.getCollection("messages");
    }

    private void setupIndexes() {
        // Player Data Indexes - Optimizados para búsquedas frecuentes
        setupPlayerDataIndexes();

        // Messages Indexes - Asegurar que el índice sea único
        setupMessagesIndexes();

        // Trade Indexes
        setupTradeIndexes();
    }

    private void setupPlayerDataIndexes() {
        try {
            // Índice único para UUID, que es el identificador principal
            createUniqueIndex(playerDataCollection, "uuid", "player_uuid_index");

            // Índice para búsquedas por nombre de jugador
            createIndex(playerDataCollection, "playerName", "player_name_index");

            // Índice compuesto para búsquedas que incluyen idioma
            Document languageIndex = new Document()
                    .append("language", 1)
                    .append("uuid", 1);
            createIndex(playerDataCollection, languageIndex, "player_language_index");

            // Índice para timestamps de última actualización (si se agrega en el futuro)
            createIndex(playerDataCollection, "lastUpdate", "player_last_update_index", true);
        } catch (Exception e) {
            logger.warning("Error setting up player data indexes: " + e.getMessage());
        }
    }

    private void setupMessagesIndexes() {
        try {
            // Eliminar índice existente si hay uno
            try {
                messagesCollection.dropIndex("message_key_index");
            } catch (Exception ignored) {
                // El índice puede no existir, ignoramos el error
            }

            // Crear índice único para las claves de mensajes
            IndexOptions indexOptions = new IndexOptions()
                    .unique(true)
                    .name("message_key_index");
            messagesCollection.createIndex(new Document("key", 1), indexOptions);
            logger.info("Created unique message key index successfully");
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                logger.warning("Error setting up message indexes: " + e.getMessage());
            }
        }
    }

    private void setupTradeIndexes() {
        try {
            // Índices simples
            createUniqueIndex(tradesCollection, "tradeId", "trade_id_index");
            createIndex(tradesCollection, "player1", "trade_player1_index");
            createIndex(tradesCollection, "player2", "trade_player2_index");
            createIndex(tradesCollection, "state", "trade_state_index");
            createIndex(tradesCollection, "timestamp", "trade_timestamp_index", true);

            // Índices compuestos optimizados
            Document player1StateIndex = new Document()
                    .append("player1", 1)
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, player1StateIndex, "trade_player1_state_index");

            Document player2StateIndex = new Document()
                    .append("player2", 1)
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, player2StateIndex, "trade_player2_state_index");

            // Índice para búsquedas de trades activos
            Document activeTradesIndex = new Document()
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, activeTradesIndex, "trade_active_index");

            // Índice para limpieza de trades expirados
            Document expirationIndex = new Document()
                    .append("state", 1)
                    .append("timestamp", 1);
            createIndex(tradesCollection, expirationIndex, "trade_expiration_index");
        } catch (Exception e) {
            logger.warning("Error setting up trade indexes: " + e.getMessage());
        }
    }

    private void createIndex(MongoCollection<Document> collection, String field, String indexName) {
        createIndex(collection, new Document(field, 1), indexName);
    }

    private void createIndex(MongoCollection<Document> collection, String field, String indexName, boolean descending) {
        createIndex(collection, new Document(field, descending ? -1 : 1), indexName);
    }

    private void createIndex(MongoCollection<Document> collection, Document keys, String indexName) {
        try {
            collection.createIndex(keys, new IndexOptions().name(indexName));
            logger.info("Created index: " + indexName);
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                logger.warning("Error creating index " + indexName + ": " + e.getMessage());
            }
        }
    }

    private void createUniqueIndex(MongoCollection<Document> collection, String field, String indexName) {
        try {
            collection.createIndex(
                    new Document(field, 1),
                    new IndexOptions().unique(true).name(indexName));
            logger.info("Created unique index: " + indexName);
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                logger.warning("Error creating unique index " + indexName + ": " + e.getMessage());
            }
        }
    }

    private void verifyConnection() {
        try {
            database.runCommand(new Document("ping", 1));
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify MongoDB connection: " + e.getMessage(), e);
        }
    }

    private boolean collectionExists(String collectionName) {
        try {
            return database.listCollectionNames()
                    .into(new java.util.ArrayList<>())
                    .contains(collectionName);
        } catch (Exception e) {
            logger.warning("Error checking if collection exists: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                logger.info("MongoDB connection closed successfully");
            } catch (Exception e) {
                logger.warning("Error closing MongoDB connection: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        if (mongoClient == null || database == null)
            return false;
        try {
            verifyConnection();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("MongoDB database not initialized");
        }
        return database;
    }

    public MongoCollection<Document> getTradesCollection() {
        if (tradesCollection == null) {
            throw new IllegalStateException("Trades collection not initialized");
        }
        return tradesCollection;
    }

    public MongoCollection<Document> getPlayerDataCollection() {
        if (playerDataCollection == null) {
            throw new IllegalStateException("Player data collection not initialized");
        }
        return playerDataCollection;
    }

    public MongoCollection<Document> getMessagesCollection() {
        if (messagesCollection == null) {
            throw new IllegalStateException("Messages collection not initialized");
        }
        return messagesCollection;
    }
}