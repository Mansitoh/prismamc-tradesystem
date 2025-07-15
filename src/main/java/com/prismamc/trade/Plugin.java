package com.prismamc.trade;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

import com.prismamc.trade.commands.TradeCommand;
import com.prismamc.trade.commands.TradeResponseCommand;
import com.prismamc.trade.gui.lib.GUIListener;
import com.prismamc.trade.manager.TradeManager;
import com.prismamc.trade.manager.MongoDBManager;
import com.prismamc.trade.utils.FileUtil;

public class Plugin extends JavaPlugin {
    
    private TradeManager tradeManager;
    private MongoDBManager mongoDBManager;
    private FileUtil configFile;
    private final TradeCommand tradeCommand;
    private final TradeResponseCommand tradeAcceptCommand;
    private final TradeResponseCommand tradeDeclineCommand;

    public Plugin() {
        this.tradeCommand = null;
        this.tradeAcceptCommand = null;
        this.tradeDeclineCommand = null;
    }

    @Override
    public void onEnable() {
        // Inicializar config.yml usando FileUtil de manera asíncrona
        CompletableFuture.runAsync(() -> {
            try {
                this.configFile = new FileUtil(this, "config.yml");
                
                // Inicializar MongoDB después de cargar la configuración
                this.mongoDBManager = new MongoDBManager();
                initializeMongoDB();
                
                // Inicializar TradeManager después de que MongoDB esté conectado
                this.tradeManager = new TradeManager(this);
                
                // Registrar listeners y comandos en el hilo principal
                getServer().getScheduler().runTask(this, () -> {
                    registerListeners();
                    registerCommands();
                });
                
                getLogger().info("PrismaMCTradePlugin ha sido habilitado exitosamente.");
                getLogger().info("Versión: " + getPluginMeta().getVersion());
            } catch (Exception e) {
                getLogger().severe("Error durante la inicialización del plugin: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
            }
        }).exceptionally(throwable -> {
            getLogger().severe("Error crítico durante la inicialización asíncrona: " + throwable.getMessage());
            getServer().getScheduler().runTask(this, () -> 
                getServer().getPluginManager().disablePlugin(this));
            return null;
        });
    }

    private void initializeMongoDB() {
        try {
            mongoDBManager.connect(configFile.getConfig());
            if (mongoDBManager.isConnected()) {
                getLogger().info("Conexión exitosa a MongoDB!");
            } else {
                getLogger().severe("Fallo al conectar con MongoDB!");
                throw new RuntimeException("No se pudo establecer conexión con MongoDB");
            }
        } catch (Exception e) {
            getLogger().severe("Error al conectar con MongoDB: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void onDisable() {
        // Limpiar recursos y cerrar conexiones
        if (tradeManager != null) {
            tradeManager.shutdown();
        }
        
        if (mongoDBManager != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    mongoDBManager.disconnect();
                    getLogger().info("Desconexión exitosa de MongoDB.");
                } catch (Exception e) {
                    getLogger().warning("Error al desconectar de MongoDB: " + e.getMessage());
                }
            }).join(); // Esperar a que se complete la desconexión
        }
        
        getLogger().info("PrismaMCTradePlugin ha sido deshabilitado.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
    }

    private void registerCommands() {
        try {
            java.lang.reflect.Field f;
            
            f = getClass().getDeclaredField("tradeCommand");
            f.setAccessible(true);
            f.set(this, new TradeCommand(this));

            f = getClass().getDeclaredField("tradeAcceptCommand");
            f.setAccessible(true);
            f.set(this, new TradeResponseCommand(this, true));

            f = getClass().getDeclaredField("tradeDeclineCommand");
            f.setAccessible(true);
            f.set(this, new TradeResponseCommand(this, false));

            getLogger().info("Comandos de trade registrados exitosamente!");
        } catch (Exception e) {
            getLogger().severe("Error al registrar comandos: " + e.getMessage());
            throw new RuntimeException("Fallo al registrar comandos", e);
        }
    }

    public TradeManager getTradeManager() {
        if (tradeManager == null) {
            throw new IllegalStateException("TradeManager no ha sido inicializado");
        }
        return tradeManager;
    }

    public MongoDBManager getMongoDBManager() {
        if (mongoDBManager == null) {
            throw new IllegalStateException("MongoDBManager no ha sido inicializado");
        }
        return mongoDBManager;
    }

    public FileUtil getConfigFile() {
        if (configFile == null) {
            throw new IllegalStateException("ConfigFile no ha sido inicializado");
        }
        return configFile;
    }
}