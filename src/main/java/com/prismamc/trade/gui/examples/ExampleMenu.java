package com.prismamc.trade.gui.examples;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.prismamc.trade.gui.GUI;
import com.prismamc.trade.gui.GUIItem;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ExampleMenu extends GUI {
    
    public ExampleMenu(Player owner) {
        super(owner, "§6Menú de Ejemplo", 27); // 3 filas de inventario
    }

    @Override
    protected void initializeItems() {
        // Crear algunos items de ejemplo
        GUIItem infoItem = new GUIItem(Material.PAPER)
            .setName("§eInformación")
            .setLore(
                "§7Este es un menú de ejemplo",
                "§7usando nuestra biblioteca de GUI"
            )
            .setClickHandler(ctx -> {
                owner.sendMessage("§aClickeaste el item de información!");
            });
            
        GUIItem actionItem = new GUIItem(Material.DIAMOND)
            .setName("§bAcción Especial")
            .setLore(
                "§7Click para ejecutar",
                "§7una acción especial"
            )
            .setClickHandler(ctx -> {
                owner.sendMessage("§b¡Acción especial ejecutada!");
                ctx.getEvent().setCancelled(true);
            });

        // Colocar los items en el inventario
        inventory.setItem(11, infoItem.getItemStack());
        inventory.setItem(15, actionItem.getItemStack());
        
        // Rellenar espacios vacíos con vidrio
        GUIItem filler = new GUIItem(Material.BLACK_STAINED_GLASS_PANE)
            .setName(" ");
            
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler.getItemStack());
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        
        // Obtener el item clickeado
        if (event.getCurrentItem() != null) {
            // Manejar el click según el slot
            switch (slot) {
                case 11: // Info item
                    owner.sendMessage("§aClickeaste el item de información!");
                    break;
                case 15: // Action item
                    owner.sendMessage("§b¡Acción especial ejecutada!");
                    break;
            }
        }
        
        event.setCancelled(true);
    }
}