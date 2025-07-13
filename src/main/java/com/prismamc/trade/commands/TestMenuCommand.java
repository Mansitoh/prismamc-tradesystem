package com.prismamc.trade.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.prismamc.trade.Plugin;
import com.prismamc.trade.gui.examples.ExampleMenu;
import com.prismamc.trade.gui.examples.ExamplePaginatedMenu;
import com.prismamc.trade.commands.base.AMyCommand;

public class TestMenuCommand extends AMyCommand<Plugin> {

    public TestMenuCommand(Plugin plugin) {
        super(plugin, "testmenu");
        setDescription("Abre un menú de prueba");
        setUsage("/testmenu [simple|paginated]");
        setPermission("prismamc.testmenu");
        
        // Agregar opciones para el tab complete
        addTabbComplete(0, "simple", "paginated");
        
        boolean registered = registerCommand();
        plugin.getLogger().info("Comando TestMenu " + (registered ? "registrado con éxito!" : "falló al registrar!"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser ejecutado por jugadores.");
            return true;
        }

        Player player = (Player) sender;
        getPlugin().getLogger().info("Ejecutando comando testmenu para " + player.getName());
        
        // Si no se especifica tipo, mostrar el menú simple por defecto
        String type = args.length > 0 ? args[0].toLowerCase() : "simple";
        
        switch (type) {
            case "paginated":
                new ExamplePaginatedMenu(player).openInventory();
                break;
            case "simple":
            default:
                new ExampleMenu(player).openInventory();
                break;
        }
        
        return true;
    }
}