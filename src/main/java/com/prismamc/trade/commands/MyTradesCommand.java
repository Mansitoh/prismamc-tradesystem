package com.prismamc.trade.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.commands.base.AMyCommand;
import com.prismamc.trade.gui.trade.MyTradesGUI;
import java.util.Objects;

public class MyTradesCommand extends AMyCommand<Plugin> {
    
    private final Plugin plugin;

    public MyTradesCommand(Plugin plugin) {
        super(plugin, "mytrades");
        this.plugin = plugin;
        this.setDescription("Ver tus trades activos y pendientes");
        this.setUsage("/mytrades");
        
        if (this.registerCommand()) {
            plugin.getLogger().info("Comando mytrades registrado exitosamente!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Objects.requireNonNull(sender, "CommandSender cannot be null")
                .sendMessage("§cEste comando solo puede ser usado por jugadores!");
            return true;
        }

        Player player = (Player) sender;
        
        // Abrir el GUI de mytrades
        MyTradesGUI myTradesGUI = new MyTradesGUI(player, plugin);
        myTradesGUI.openInventory();
        
        return true;
    }
}