package com.prismamc.trade.gui.lib;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.HandlerList;

import com.prismamc.trade.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;

import net.kyori.adventure.text.Component;

public abstract class GUI implements InventoryHolder {
    protected Inventory inventory;
    protected final Player owner;
    protected final Plugin plugin;
    protected String title;
    protected int size;
    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isClosed;

    public GUI(Player owner, String title, int size) {
        this.owner = owner;
        this.title = title;
        this.size = size;
        this.plugin = (Plugin) Bukkit.getPluginManager().getPlugin("PrismaMCTradePlugin");
        this.isInitialized = new AtomicBoolean(false);
        this.isClosed = new AtomicBoolean(false);

        // Crear el inventario inmediatamente en el hilo principal
        if (Bukkit.isPrimaryThread()) {
            this.inventory = Bukkit.createInventory(this, size, Component.text(title));
        } else {
            // Si no estamos en el hilo principal, crear el inventario de forma sincronizada
            try {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    this.inventory = Bukkit.createInventory(this, size, Component.text(title));
                    future.complete(null);
                });
                future.get(); // Esperar a que se complete
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating inventory: " + e.getMessage());
            }
        }
    }

    protected abstract void initializeItems();

    public abstract void handleClick(InventoryClickEvent event);

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void openInventory() {
        if (!isInitialized.get()) {
            initializeItems();
            isInitialized.set(true);
        }

        if (!isClosed.get()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                owner.openInventory(inventory);
                // Registrar este GUI en el listener
                GUIListener listener = findGUIListener();
                if (listener != null) {
                    listener.registerGUI(owner, this);
                }
            });
        }
    }

    public void closeInventory() {
        if (!isClosed.get()) {
            isClosed.set(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                owner.closeInventory();
                // Desregistrar este GUI del listener
                GUIListener listener = findGUIListener();
                if (listener != null) {
                    listener.unregisterGUI(owner);
                }
            });
        }
    }

    public void onClose(Player player) {
        isClosed.set(true);
    }

    private GUIListener findGUIListener() {
        return HandlerList.getRegisteredListeners(plugin).stream()
                .filter(handler -> handler.getListener() instanceof GUIListener)
                .map(handler -> (GUIListener) handler.getListener())
                .findFirst()
                .orElse(null);
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Player getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    protected void markDirty() {
        if (!isClosed.get()) {
            plugin.getServer().getScheduler().runTask(plugin, this::initializeItems);
        }
    }
}