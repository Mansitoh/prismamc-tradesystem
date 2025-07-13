package com.prismamc.trade;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import com.prismamc.trade.gui.GUIListener;
import com.prismamc.trade.commands.TestMenuCommand;
import com.prismamc.trade.commands.TradeCommand;

public class Plugin extends JavaPlugin {
    
    private TestMenuCommand testMenuCommand;
    private TradeCommand tradeCommand;

    @Override
    public void onEnable() {
        // Registrar el listener de GUIs
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        
        // Register commands
        this.tradeCommand = new TradeCommand(this);
        
        getLogger().info("PrismaMCTradePlugin ha sido activado con éxito (modo testeo).");
        getLogger().log(Level.INFO, "Version: {0}", getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("PrismaMCTradePlugin ha sido desactivado.");
    }
}