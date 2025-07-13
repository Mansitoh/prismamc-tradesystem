package com.prismamc.trade.gui.examples;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.prismamc.trade.gui.lib.GUIItem;
import com.prismamc.trade.gui.lib.PaginatedGUI;

import java.util.Arrays;
import java.util.List;

public class ExamplePaginatedMenu extends PaginatedGUI {
    
    private static final List<Material> DEMO_ITEMS = Arrays.asList(
        Material.DIAMOND,
        Material.EMERALD,
        Material.GOLDEN_APPLE,
        Material.ENCHANTED_GOLDEN_APPLE,
        Material.NETHERITE_INGOT,
        Material.ELYTRA,
        Material.DRAGON_EGG,
        Material.BEACON,
        Material.NETHER_STAR,
        Material.TRIDENT,
        Material.TOTEM_OF_UNDYING,
        Material.SHULKER_BOX,
        Material.END_CRYSTAL,
        Material.CONDUIT,
        Material.HEART_OF_THE_SEA,
        Material.MUSIC_DISC_PIGSTEP,
        Material.ENCHANTED_BOOK,
        Material.DRAGON_HEAD,
        Material.COMMAND_BLOCK,
        Material.BARRIER
    );

    public ExamplePaginatedMenu(Player owner) {
        super(owner, "§6Menú de Items Especiales", 6, 5); // 6 filas total, 5 filas para items
    }

    @Override
    protected void setupItems() {
        // Agregar items de demostración
        for (int i = 0; i < DEMO_ITEMS.size(); i++) {
            Material material = DEMO_ITEMS.get(i);
            GUIItem item = new GUIItem(material)
                .setName("§b" + formatName(material.name()))
                .setLore(
                    "§7Item especial #" + (i + 1),
                    "",
                    "§eClick para seleccionar"
                )
                .setClickHandler(ctx -> {
                    owner.sendMessage("§aHas seleccionado: §f" + formatName(material.name()));
                    owner.playSound(owner.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.0f);
                });
            
            addItem(item);
        }

        // Agregar botón de cierre en el centro de la última fila
        GUIItem closeButton = new GUIItem(Material.BARRIER)
            .setName("§cCerrar Menú")
            .setLore("§7Click para cerrar el menú")
            .setClickHandler(ctx -> {
                owner.closeInventory();
                owner.playSound(owner.getLocation(), "block.chest.close", 1.0f, 1.0f);
            });
        
        setStaticItem(49, closeButton); // Slot 49 es el centro de la última fila
    }

    private String formatName(String name) {
        return name.toLowerCase()
                  .replace('_', ' ')
                  .substring(0, 1).toUpperCase() + 
                  name.toLowerCase().replace('_', ' ').substring(1);
    }
}