package com.prismamc.trade.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PaginatedGUI extends GUI {
    protected int currentPage = 0;
    protected final List<GUIItem> items;
    protected final Map<Integer, GUIItem> staticItems;
    protected final int itemsPerPage;
    protected final int navigationRow;

    public PaginatedGUI(Player owner, String title, int rows, int navigationRow) {
        super(owner, title, rows * 9);
        this.navigationRow = navigationRow;
        this.itemsPerPage = (navigationRow * 9);
        this.items = new ArrayList<>();
        this.staticItems = new HashMap<>();
    }

    @Override
    protected void initializeItems() {
        // Let subclasses initialize their items first
        setupItems();
        // Then update the page
        updatePage();
    }

    // New method for subclasses to override instead of initializeItems
    protected abstract void setupItems();

    // Make this method final so it can't be overridden
    protected final void updatePage() {
        // Limpiar inventario
        inventory.clear();

        // Colocar items paginados primero
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            inventory.setItem(i - startIndex, items.get(i).getItemStack());
        }

        // Colocar items estáticos
        staticItems.forEach((slot, item) -> inventory.setItem(slot, item.getItemStack()));

        // Agregar botones de navegación
        if (currentPage > 0) {
            GUIItem previousButton = new GUIItem(Material.ARROW)
                .setName("§aPágina Anterior")
                .setClickHandler(ctx -> {
                    currentPage--;
                    updatePage();
                });
            inventory.setItem(size - 9, previousButton.getItemStack());
        }

        if ((currentPage + 1) * itemsPerPage < items.size()) {
            GUIItem nextButton = new GUIItem(Material.ARROW)
                .setName("§aSiguiente Página")
                .setClickHandler(ctx -> {
                    currentPage++;
                    updatePage();
                });
            inventory.setItem(size - 1, nextButton.getItemStack());
        }
    }

    public void addItem(GUIItem item) {
        items.add(item);
        updatePage();
    }

    public void setStaticItem(int slot, GUIItem item) {
        staticItems.put(slot, item);
        updatePage();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        
        // Manejar items estáticos primero
        if (staticItems.containsKey(slot)) {
            staticItems.get(slot).handleClick(new GUIItem.ClickContext(this, slot, event));
            return;
        }

        // Manejar items paginados
        int index = currentPage * itemsPerPage + slot;
        if (slot < itemsPerPage && index < items.size()) {
            items.get(index).handleClick(new GUIItem.ClickContext(this, slot, event));
        }
    }
}