package com.prismamc.trade.manager;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public class TradeManager {
    private final Map<UUID, List<ItemStack>> preTradeItems;

    public TradeManager() {
        this.preTradeItems = new HashMap<>();
    }

    public void storePreTradeItems(UUID playerUUID, List<ItemStack> items) {
        preTradeItems.put(playerUUID, new ArrayList<>(items));
    }

    public List<ItemStack> getPreTradeItems(UUID playerUUID) {
        return preTradeItems.containsKey(playerUUID) ? 
            new ArrayList<>(preTradeItems.get(playerUUID)) : 
            new ArrayList<>();
    }

    public List<ItemStack> getAndRemovePreTradeItems(UUID playerUUID) {
        return preTradeItems.remove(playerUUID);
    }

    public boolean hasPreTradeItems(UUID playerUUID) {
        return preTradeItems.containsKey(playerUUID);
    }

    public void clearPreTradeItems(UUID playerUUID) {
        preTradeItems.remove(playerUUID);
    }
}