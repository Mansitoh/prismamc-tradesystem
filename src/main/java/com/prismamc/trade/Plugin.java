package com.prismamc.trade;

import java.util.concurrent.CompletableFuture;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.prismamc.trade.commands.TradeCommand;
import com.prismamc.trade.commands.TradeResponseCommand;
import com.prismamc.trade.commands.MyTradesCommand;
import com.prismamc.trade.gui.lib.GUIListener;
import com.prismamc.trade.listeners.PlayerJoinListener;
import com.prismamc.trade.manager.ItemManager;
import com.prismamc.trade.manager.MessageManager;
import com.prismamc.trade.manager.MongoDBManager;
import com.prismamc.trade.manager.PlayerDataManager;
import com.prismamc.trade.manager.TradeManager;
import com.prismamc.trade.utils.FileUtil;

public class Plugin extends JavaPlugin implements Listener {

    private TradeManager tradeManager;
    private MongoDBManager mongoDBManager;
    private PlayerDataManager playerDataManager;
    private MessageManager messageManager;
    private ItemManager itemManager;
    private FileUtil configFile;
    @SuppressWarnings("unused") // Used via reflection
    private final TradeCommand tradeCommand;
    @SuppressWarnings("unused") // Used via reflection
    private final TradeResponseCommand tradeAcceptCommand;
    @SuppressWarnings("unused") // Used via reflection
    private final TradeResponseCommand tradeDeclineCommand;
    @SuppressWarnings("unused") // Used via reflection
    private final MyTradesCommand myTradesCommand;

    public Plugin() {
        this.tradeCommand = null;
        this.tradeAcceptCommand = null;
        this.tradeDeclineCommand = null;
        this.myTradesCommand = null;
    }

    @Override
    public void onEnable() {
        try {
            this.configFile = new FileUtil(this, "config.yml");
            initializeMongoDB();

            if (mongoDBManager != null && mongoDBManager.isConnected()) {
                this.playerDataManager = new PlayerDataManager(this);
                this.messageManager = new MessageManager(this);
                this.itemManager = new ItemManager(this);
                this.tradeManager = new TradeManager(this);

                registerListeners();
                registerCommands();

                getLogger().info("PrismaMCTradePlugin ha sido habilitado exitosamente.");
                getLogger().info(String.format("Version: %s", getDescription().getVersion()));
            } else {
                getLogger()
                        .severe("No se pudo establecer conexión con MongoDB. El plugin no se iniciará correctamente.");
                getServer().getPluginManager().disablePlugin(this);
            }
        } catch (Exception e) {
            getLogger().severe(String.format("Error durante la inicialización del plugin: %s", e.getMessage()));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeMongoDB() {
        try {
            mongoDBManager = new MongoDBManager(getLogger());
            mongoDBManager.connect(configFile.getConfig());
            if (mongoDBManager.isConnected()) {
                getLogger().info("Conexión exitosa a MongoDB!");
            } else {
                getLogger().severe("Fallo al conectar con MongoDB!");
            }
        } catch (Exception e) {
            getLogger().severe(String.format("Error al conectar con MongoDB: %s", e.getMessage()));
            mongoDBManager = null;
        }
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) {
            tradeManager.shutdown();
        }

        if (mongoDBManager != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    mongoDBManager.disconnect();
                    getLogger().info("Desconexión exitosa de MongoDB.");
                } catch (Exception e) {
                    getLogger().warning(String.format("Error al desconectar de MongoDB: %s", e.getMessage()));
                }
            }).join();
        }

        getLogger().info("PrismaMCTradePlugin ha sido deshabilitado.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
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

            f = getClass().getDeclaredField("myTradesCommand");
            f.setAccessible(true);
            f.set(this, new MyTradesCommand(this));

            getLogger().info("Comandos de trade registrados exitosamente!");
        } catch (Exception e) {
            getLogger().severe(String.format("Error al registrar comandos: %s", e.getMessage()));
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

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerDataManager.loadPlayerData(event.getPlayer())
                .thenAccept(playerData -> getLogger()
                        .info(String.format("Loaded player data for %s", event.getPlayer().getName())))
                .exceptionally(throwable -> {
                    getLogger().severe(String.format("Error loading player data for %s: %s",
                            event.getPlayer().getName(), throwable.getMessage()));
                    return null;
                });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerDataManager.removeFromCache(event.getPlayer().getUniqueId())
                .exceptionally(throwable -> {
                    getLogger().severe(String.format("Error removing player data from cache for %s: %s",
                            event.getPlayer().getName(), throwable.getMessage()));
                    return null;
                });
    }
}