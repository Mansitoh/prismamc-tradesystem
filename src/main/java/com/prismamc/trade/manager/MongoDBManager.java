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

/**
 * MongoDBManager - MongoDB Database Connection and Management System
 * 
 * This class provides comprehensive MongoDB database management functionality
 * for the PrismaMC Trade System. It handles connection pooling, collection
 * initialization, index optimization, and provides thread-safe database
 * operations.
 * 
 * Key Features:
 * - Optimized connection pooling for high performance
 * - Automatic collection creation and management
 * - Comprehensive indexing strategy for query optimization
 * - Robust error handling and connection recovery
 * - Configuration-driven setup supporting various MongoDB deployments
 * - Thread-safe operations for concurrent access
 * 
 * Database Schema:
 * - trades: Trade transaction records and item data
 * - player_data: Player preferences and configuration
 * - messages: Localized message templates
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class MongoDBManager {

    // Core MongoDB components
    private MongoClient mongoClient;
    private MongoDatabase database;

    // Collection references for different data types
    private MongoCollection<Document> tradesCollection;
    private MongoCollection<Document> playerDataCollection;
    private MongoCollection<Document> messagesCollection;

    // Logging system
    private final Logger logger;

    // Connection pool configuration constants for optimal performance
    /** Minimum number of connections maintained in the pool */
    private static final int MIN_CONNECTIONS_PER_HOST = 5;

    /** Maximum number of connections allowed in the pool */
    private static final int MAX_CONNECTIONS_PER_HOST = 20;

    /** Maximum time to wait for an available connection (milliseconds) */
    private static final int MAX_WAIT_TIME = 5000;

    /**
     * Maximum time a connection can remain idle before being closed (milliseconds)
     */
    private static final int MAX_CONNECTION_IDLE_TIME = 300000;

    /** Timeout for establishing new connections (milliseconds) */
    private static final int CONNECTION_TIMEOUT = 5000;

    /**
     * Constructs a new MongoDBManager instance.
     * 
     * @param logger Logger instance for database operation logging
     */
    public MongoDBManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Establishes connection to MongoDB and initializes all required database
     * components.
     * This method performs the complete database setup including:
     * - MongoDB client configuration with optimized connection pooling
     * - Database and collection initialization
     * - Index creation for query optimization
     * 
     * @param config FileConfiguration containing MongoDB connection parameters
     * @throws RuntimeException if database initialization fails
     */
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

    /**
     * Configures and creates the MongoDB client with optimized connection settings.
     * Sets up connection pooling, timeouts, and authentication based on
     * configuration.
     * 
     * @param config Configuration containing connection parameters
     */
    private void setupMongoClient(FileConfiguration config) {
        String uri = config.getString("mongodb.connection-uri");
        MongoClientSettings settings = createMongoClientSettings(config, uri);
        mongoClient = MongoClients.create(settings);
    }

    /**
     * Creates optimized MongoDB client settings with connection pooling and timeout
     * configuration.
     * Supports both URI-based and parameter-based connection configuration.
     * 
     * @param config Configuration object containing MongoDB settings
     * @param uri    Optional connection URI (takes precedence over individual
     *               parameters)
     * @return Configured MongoClientSettings instance
     */
    private MongoClientSettings createMongoClientSettings(FileConfiguration config, String uri) {
        // Configure connection pool for optimal performance under load
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .minSize(MIN_CONNECTIONS_PER_HOST)
                .maxSize(MAX_CONNECTIONS_PER_HOST)
                .maxWaitTime(MAX_WAIT_TIME, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(MAX_CONNECTION_IDLE_TIME, TimeUnit.MILLISECONDS)
                .build();

        // Build base client settings with connection pool and socket configuration
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                .applyToSocketSettings(builder -> builder.connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS));

        // Configure connection string - URI takes precedence over individual parameters
        if (uri != null && !uri.isEmpty()) {
            settingsBuilder.applyConnectionString(new ConnectionString(uri));
        } else {
            // Build connection string from individual configuration parameters
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

    /**
     * Constructs a MongoDB connection string from individual parameters.
     * Handles both authenticated and non-authenticated connections.
     * 
     * @param host         MongoDB server hostname or IP address
     * @param port         MongoDB server port number
     * @param username     Database username (empty for no authentication)
     * @param password     Database password (empty for no authentication)
     * @param authDatabase Authentication database name
     * @return Properly formatted MongoDB connection string
     */
    private String createConnectionString(String host, int port, String username, String password,
            String authDatabase) {
        if (!username.isEmpty() && !password.isEmpty()) {
            return String.format("mongodb://%s:%s@%s:%d/?authSource=%s",
                    username, password, host, port, authDatabase);
        }
        return String.format("mongodb://%s:%d", host, port);
    }

    /**
     * Initializes the MongoDB database instance and verifies connectivity.
     * 
     * @param config Configuration containing database name
     */
    private void initializeDatabase(FileConfiguration config) {
        String databaseName = config.getString("mongodb.database", "prismamc_trade");
        database = mongoClient.getDatabase(databaseName);
        verifyConnection();
    }

    /**
     * Initializes all required collections for the trade system.
     * Creates collections if they don't exist and establishes collection
     * references.
     * 
     * Collections created:
     * - trades: Stores trade transaction data and item information
     * - player_data: Stores player preferences, language settings, and
     * configurations
     * - messages: Stores localized message templates and translations
     */
    private void initializeCollections() {
        String[] collections = { "trades", "player_data", "messages" };

        // Create collections if they don't exist
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

        // Establish collection references for application use
        tradesCollection = database.getCollection("trades");
        playerDataCollection = database.getCollection("player_data");
        messagesCollection = database.getCollection("messages");
    }

    /**
     * Sets up comprehensive database indexes for optimal query performance.
     * Creates both simple and compound indexes based on expected query patterns.
     * This method should be called after collection initialization.
     */
    private void setupIndexes() {
        // Player Data Indexes - Optimized for frequent player lookups
        setupPlayerDataIndexes();

        // Messages Indexes - Ensure unique message keys
        setupMessagesIndexes();

        // Trade Indexes - Optimized for trade queries and state management
        setupTradeIndexes();
    }

    /**
     * Creates optimized indexes for the player_data collection.
     * Indexes are designed to support common query patterns:
     * - Player UUID lookups (primary key)
     * - Player name searches
     * - Language-based filtering
     * - Update timestamp tracking
     */
    private void setupPlayerDataIndexes() {
        try {
            // Unique index for UUID - primary identifier for players
            createUniqueIndex(playerDataCollection, "uuid", "player_uuid_index");

            // Index for player name searches and lookups
            createIndex(playerDataCollection, "playerName", "player_name_index");

            // Compound index for language-based queries with player identification
            Document languageIndex = new Document()
                    .append("language", 1)
                    .append("uuid", 1);
            createIndex(playerDataCollection, languageIndex, "player_language_index");

            // Index for timestamp-based queries (for future update tracking)
            createIndex(playerDataCollection, "lastUpdate", "player_last_update_index", true);
        } catch (Exception e) {
            logger.warning("Error setting up player data indexes: " + e.getMessage());
        }
    }

    /**
     * Creates indexes for the messages collection.
     * Ensures unique message keys and optimizes message template lookups.
     */
    private void setupMessagesIndexes() {
        try {
            // Remove existing index if present to ensure clean setup
            try {
                messagesCollection.dropIndex("message_key_index");
            } catch (Exception ignored) {
                // Index may not exist, ignore the error
            }

            // Create unique index for message keys to prevent duplicates
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

    /**
     * Creates comprehensive indexes for the trades collection.
     * Indexes are optimized for common trade system operations:
     * - Trade ID lookups (primary key)
     * - Player-based trade queries
     * - State-based filtering
     * - Timestamp-based sorting and cleanup
     * - Compound queries for performance optimization
     */
    private void setupTradeIndexes() {
        try {
            // Simple indexes for basic queries
            createUniqueIndex(tradesCollection, "tradeId", "trade_id_index");
            createIndex(tradesCollection, "player1", "trade_player1_index");
            createIndex(tradesCollection, "player2", "trade_player2_index");
            createIndex(tradesCollection, "state", "trade_state_index");
            createIndex(tradesCollection, "timestamp", "trade_timestamp_index", true);

            // Compound indexes for optimized complex queries

            // Index for player1 trades filtered by state, sorted by timestamp
            Document player1StateIndex = new Document()
                    .append("player1", 1)
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, player1StateIndex, "trade_player1_state_index");

            // Index for player2 trades filtered by state, sorted by timestamp
            Document player2StateIndex = new Document()
                    .append("player2", 1)
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, player2StateIndex, "trade_player2_state_index");

            // Index for active trades queries with timestamp sorting
            Document activeTradesIndex = new Document()
                    .append("state", 1)
                    .append("timestamp", -1);
            createIndex(tradesCollection, activeTradesIndex, "trade_active_index");

            // Index for expired trade cleanup operations
            Document expirationIndex = new Document()
                    .append("state", 1)
                    .append("timestamp", 1);
            createIndex(tradesCollection, expirationIndex, "trade_expiration_index");
        } catch (Exception e) {
            logger.warning("Error setting up trade indexes: " + e.getMessage());
        }
    }

    /**
     * Creates a simple ascending index on the specified field.
     * 
     * @param collection Target collection for index creation
     * @param field      Field name to index
     * @param indexName  Name for the created index
     */
    private void createIndex(MongoCollection<Document> collection, String field, String indexName) {
        createIndex(collection, new Document(field, 1), indexName);
    }

    /**
     * Creates a simple index on the specified field with configurable sort order.
     * 
     * @param collection Target collection for index creation
     * @param field      Field name to index
     * @param indexName  Name for the created index
     * @param descending True for descending order, false for ascending
     */
    private void createIndex(MongoCollection<Document> collection, String field, String indexName, boolean descending) {
        createIndex(collection, new Document(field, descending ? -1 : 1), indexName);
    }

    /**
     * Creates an index using the provided keys document.
     * Handles index creation errors gracefully and logs appropriate messages.
     * 
     * @param collection Target collection for index creation
     * @param keys       Document specifying the index keys and sort order
     * @param indexName  Name for the created index
     */
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

    /**
     * Creates a unique index on the specified field to enforce data integrity.
     * 
     * @param collection Target collection for index creation
     * @param field      Field name to index with unique constraint
     * @param indexName  Name for the created index
     */
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

    /**
     * Verifies the MongoDB connection by executing a ping command.
     * 
     * @throws RuntimeException if the connection verification fails
     */
    private void verifyConnection() {
        try {
            database.runCommand(new Document("ping", 1));
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify MongoDB connection: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a collection exists in the database.
     * 
     * @param collectionName Name of the collection to check
     * @return True if the collection exists, false otherwise
     */
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

    /**
     * Gracefully closes the MongoDB connection and releases all resources.
     * This method should be called during plugin shutdown to ensure clean
     * disconnection.
     */
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

    /**
     * Checks if the MongoDB connection is active and responsive.
     * 
     * @return True if connected and responsive, false otherwise
     */
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

    /**
     * Retrieves the MongoDB database instance.
     * 
     * @return MongoDatabase instance
     * @throws IllegalStateException if the database is not initialized
     */
    public MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("MongoDB database not initialized");
        }
        return database;
    }

    /**
     * Retrieves the trades collection for trade data operations.
     * 
     * @return MongoCollection for trades data
     * @throws IllegalStateException if the collection is not initialized
     */
    public MongoCollection<Document> getTradesCollection() {
        if (tradesCollection == null) {
            throw new IllegalStateException("Trades collection not initialized");
        }
        return tradesCollection;
    }

    /**
     * Retrieves the player data collection for player preference operations.
     * 
     * @return MongoCollection for player data
     * @throws IllegalStateException if the collection is not initialized
     */
    public MongoCollection<Document> getPlayerDataCollection() {
        if (playerDataCollection == null) {
            throw new IllegalStateException("Player data collection not initialized");
        }
        return playerDataCollection;
    }

    /**
     * Retrieves the messages collection for localization operations.
     * 
     * @return MongoCollection for message templates
     * @throws IllegalStateException if the collection is not initialized
     */
    public MongoCollection<Document> getMessagesCollection() {
        if (messagesCollection == null) {
            throw new IllegalStateException("Messages collection not initialized");
        }
        return messagesCollection;
    }
}