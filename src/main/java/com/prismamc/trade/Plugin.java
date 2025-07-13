package com.prismamc.trade;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

import com.prismamc.trade.commands.TradeCommand;
import com.prismamc.trade.commands.TradeResponseCommand;
import com.prismamc.trade.gui.lib.GUIListener;
import com.prismamc.trade.manager.TradeManager;

public class Plugin extends JavaPlugin {
    
    private final TradeManager tradeManager;
    // Los comandos son finales para mantener las referencias aunque no se usen directamente
    private final TradeCommand tradeCommand = null;
    private final TradeResponseCommand tradeAcceptCommand = null;
    private final TradeResponseCommand tradeDeclineCommand = null;

    public Plugin() {
        this.tradeManager = new TradeManager();
    }

    @Override
    public void onEnable() {
        // Register the listener for GUIs
        registerListeners();
        
        // Register commands immediately
        registerCommands();
        
        getLogger().info("PrismaMCTradePlugin has been enabled successfully.");
        getLogger().info("Version: " + getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("PrismaMCTradePlugin has been disabled.");
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
    }

    private void registerCommands() {
        // Usamos asignación en campos finales a través del constructor
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

            getLogger().info("Trade commands registered successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
        }
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}