package com.prismamc.trade;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

import com.prismamc.trade.commands.TestMenuCommand;
import com.prismamc.trade.commands.TradeCommand;
import com.prismamc.trade.commands.TradeResponseCommand;
import com.prismamc.trade.gui.lib.GUIListener;
import com.prismamc.trade.manager.TradeManager;
import org.bukkit.Bukkit;

public class Plugin extends JavaPlugin {
    
    private TradeCommand tradeCommand;
    private TradeResponseCommand tradeAcceptCommand;
    private TradeResponseCommand tradeDeclineCommand;
    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        // Initialize TradeManager
        this.tradeManager = new TradeManager();
        
        // Register the listener for GUIs
        registerListeners();
        
        // Register commands immediately
        registerCommands();
        
        getLogger().info("PrismaMCTradePlugin has been enabled successfully.");
        getLogger().log(Level.INFO, "Version: {0}", getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("PrismaMCTradePlugin has been disabled.");
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
    }

    public void registerCommands() {
        this.tradeCommand = new TradeCommand(this);
        this.tradeAcceptCommand = new TradeResponseCommand(this, true);
        this.tradeDeclineCommand = new TradeResponseCommand(this, false);
        getLogger().info("Trade commands registered successfully!");
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}