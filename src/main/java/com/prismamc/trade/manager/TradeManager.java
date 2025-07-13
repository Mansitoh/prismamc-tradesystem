package com.prismamc.trade.manager;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class TradeManager {
    private final Map<UUID, List<ItemStack>> preTradeItems;
    private final Map<Long, TradeState> tradeStates;
    private final AtomicLong tradeIdGenerator;
    
    public enum TradeState {
        PENDING,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
    
    private static class TradeInfo {
        private final UUID player1;
        private final UUID player2;
        private TradeState state;
        
        public TradeInfo(UUID player1, UUID player2) {
            this.player1 = player1;
            this.player2 = player2;
            this.state = TradeState.PENDING;
        }
    }
    
    private final Map<Long, TradeInfo> tradeInfos;

    public TradeManager() {
        this.preTradeItems = new HashMap<>();
        this.tradeStates = new HashMap<>();
        this.tradeInfos = new HashMap<>();
        this.tradeIdGenerator = new AtomicLong(1);
    }

    public long createNewTrade(UUID player1, UUID player2) {
        long tradeId = tradeIdGenerator.getAndIncrement();
        tradeStates.put(tradeId, TradeState.PENDING);
        tradeInfos.put(tradeId, new TradeInfo(player1, player2));
        return tradeId;
    }

    public void updateTradeState(long tradeId, TradeState newState) {
        tradeStates.put(tradeId, newState);
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            info.state = newState;
        }
    }

    public boolean isTradeValid(long tradeId) {
        TradeState state = tradeStates.get(tradeId);
        return state != null && state != TradeState.CANCELLED && state != TradeState.COMPLETED;
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

    public TradeState getTradeState(long tradeId) {
        return tradeStates.getOrDefault(tradeId, TradeState.CANCELLED);
    }

    public boolean arePlayersInTrade(UUID player1, UUID player2) {
        return tradeInfos.values().stream()
            .anyMatch(info -> 
                (info.player1.equals(player1) || info.player1.equals(player2)) &&
                (info.player2.equals(player1) || info.player2.equals(player2)) &&
                info.state == TradeState.PENDING || info.state == TradeState.ACTIVE);
    }

    public void cleanupTrade(long tradeId) {
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            clearPreTradeItems(info.player1);
            clearPreTradeItems(info.player2);
        }
        tradeInfos.remove(tradeId);
        tradeStates.remove(tradeId);
    }
}