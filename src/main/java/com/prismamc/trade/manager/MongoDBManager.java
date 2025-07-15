package com.prismamc.trade.manager;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ConnectionPoolSettings;
import lombok.Getter;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.TimeUnit;

public class MongoDBManager {
    @Getter
    private MongoClient mongoClient;
    @Getter
    private MongoDatabase database;
    @Getter
    private MongoCollection<Document> tradesCollection;
    
    private static final int MIN_CONNECTIONS_PER_HOST = 5;
    private static final int MAX_CONNECTIONS_PER_HOST = 20;
    private static final int MAX_WAIT_TIME = 5000;
    private static final int MAX_CONNECTION_IDLE_TIME = 300000;
    private static final int CONNECTION_TIMEOUT = 5000;

    public void connect(FileConfiguration config) {
        try {
            MongoClientSettings settings;
            String uri = config.getString("mongodb.connection-uri");
            
            ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .minSize(MIN_CONNECTIONS_PER_HOST)
                .maxSize(MAX_CONNECTIONS_PER_HOST)
                .maxWaitTime(MAX_WAIT_TIME, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(MAX_CONNECTION_IDLE_TIME, TimeUnit.MILLISECONDS)
                .build();

            if (uri != null && !uri.isEmpty()) {
                settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                    .applyToSocketSettings(builder -> 
                        builder.connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS))
                    .build();
            } else {
                String host = config.getString("mongodb.host", "localhost");
                int port = config.getInt("mongodb.port", 27017);
                String username = config.getString("mongodb.username", "");
                String password = config.getString("mongodb.password", "");
                String authDatabase = config.getString("mongodb.auth-database", "admin");

                ConnectionString connectionString;
                if (!username.isEmpty() && !password.isEmpty()) {
                    connectionString = new ConnectionString(
                        String.format("mongodb://%s:%s@%s:%d/?authSource=%s",
                            username, password, host, port, authDatabase)
                    );
                } else {
                    connectionString = new ConnectionString(
                        String.format("mongodb://%s:%d", host, port)
                    );
                }

                settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                    .applyToSocketSettings(builder -> 
                        builder.connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS))
                    .build();
            }

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(config.getString("mongodb.database", "prismamc_trade"));
            
            // Inicializar la colección de trades con índices optimizados
            String collectionName = config.getString("mongodb.collection", "trades");
            tradesCollection = database.getCollection(collectionName);
            createIndexes();
        } catch (Exception e) {
            throw new RuntimeException("Error connecting to MongoDB", e);
        }
    }

    private void createIndexes() {
        // Crear índices para mejorar el rendimiento de las consultas más comunes
        tradesCollection.createIndex(new Document("tradeId", 1));
        tradesCollection.createIndex(new Document("player1", 1));
        tradesCollection.createIndex(new Document("player2", 1));
        tradesCollection.createIndex(new Document("state", 1));
        tradesCollection.createIndex(new Document("timestamp", -1));
        
        // Índice compuesto para búsquedas de trades activos por jugador
        tradesCollection.createIndex(new Document()
            .append("player1", 1)
            .append("state", 1)
            .append("timestamp", -1));
        tradesCollection.createIndex(new Document()
            .append("player2", 1)
            .append("state", 1)
            .append("timestamp", -1));
    }

    public void disconnect() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception e) {
                // Log error but don't throw
            }
        }
    }

    public boolean isConnected() {
        if (mongoClient == null || tradesCollection == null) return false;
        try {
            // Ejecutar una operación simple para verificar la conexión
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public MongoCollection<Document> getTradesCollection() {
        if (tradesCollection == null) {
            throw new IllegalStateException("MongoDB connection not initialized");
        }
        return tradesCollection;
    }
}