package com.prismamc.trade.manager;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class TradeManager {
    private final Map<Long, TradeInfo> tradeInfos;
    private final Map<Long, TradeState> tradeStates;
    private final AtomicLong tradeIdGenerator;
    
    public enum TradeState {
        PENDING,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
    
    public static class TradeInfo {
        private final UUID player1;
        private final UUID player2;
        private TradeState state;
        private final List<ItemStack> player1Items;
        private final List<ItemStack> player2Items;
        
        public TradeInfo(UUID player1, UUID player2) {
            this.player1 = player1;
            this.player2 = player2;
            this.state = TradeState.PENDING;
            this.player1Items = new ArrayList<>();
            this.player2Items = new ArrayList<>();
        }

        public UUID getPlayer1() {
            return player1;
        }

        public UUID getPlayer2() {
            return player2;
        }

        public TradeState getState() {
            return state;
        }
    }

    public TradeManager() {
        this.tradeInfos = new HashMap<>();
        this.tradeStates = new HashMap<>();
        this.tradeIdGenerator = new AtomicLong(1);
    }

    public long createNewTrade(UUID player1, UUID player2) {
        long tradeId = tradeIdGenerator.getAndIncrement();
        TradeInfo tradeInfo = new TradeInfo(player1, player2);
        tradeInfos.put(tradeId, tradeInfo);
        tradeStates.put(tradeId, TradeState.PENDING);
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

    public void storeTradeItems(long tradeId, UUID playerUUID, List<ItemStack> items) {
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            if (playerUUID.equals(info.player1)) {
                info.player1Items.clear();
                info.player1Items.addAll(items);
            } else if (playerUUID.equals(info.player2)) {
                info.player2Items.clear();
                info.player2Items.addAll(items);
            }
        }
    }

    public List<ItemStack> getTradeItems(long tradeId, UUID playerUUID) {
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            if (playerUUID.equals(info.player1)) {
                return new ArrayList<>(info.player1Items);
            } else if (playerUUID.equals(info.player2)) {
                return new ArrayList<>(info.player2Items);
            }
        }
        return new ArrayList<>();
    }

    public List<ItemStack> getAndRemoveTradeItems(long tradeId, UUID playerUUID) {
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            if (playerUUID.equals(info.player1)) {
                List<ItemStack> items = new ArrayList<>(info.player1Items);
                info.player1Items.clear();
                return items;
            } else if (playerUUID.equals(info.player2)) {
                List<ItemStack> items = new ArrayList<>(info.player2Items);
                info.player2Items.clear();
                return items;
            }
        }
        return new ArrayList<>();
    }

    public boolean hasTradeItems(long tradeId, UUID playerUUID) {
        TradeInfo info = tradeInfos.get(tradeId);
        if (info != null) {
            if (playerUUID.equals(info.player1)) {
                return !info.player1Items.isEmpty();
            } else if (playerUUID.equals(info.player2)) {
                return !info.player2Items.isEmpty();
            }
        }
        return false;
    }

    public TradeState getTradeState(long tradeId) {
        return tradeStates.getOrDefault(tradeId, TradeState.CANCELLED);
    }

    public List<Long> getPlayerPendingTrades(UUID playerUUID) {
        List<Long> pendingTrades = new ArrayList<>();
        for (Map.Entry<Long, TradeInfo> entry : tradeInfos.entrySet()) {
            TradeInfo info = entry.getValue();
            if ((info.player1.equals(playerUUID) || info.player2.equals(playerUUID)) 
                && (info.state == TradeState.PENDING || info.state == TradeState.ACTIVE)) {
                pendingTrades.add(entry.getKey());
            }
        }
        return pendingTrades;
    }

    public boolean arePlayersInTrade(UUID player1, UUID player2) {
        return tradeInfos.values().stream()
            .anyMatch(info -> 
                (info.player1.equals(player1) && info.player2.equals(player2) ||
                 info.player1.equals(player2) && info.player2.equals(player1)) &&
                (info.state == TradeState.PENDING || info.state == TradeState.ACTIVE));
    }

    public TradeInfo getTradeInfo(long tradeId) {
        return tradeInfos.get(tradeId);
    }

    public void cleanupTrade(long tradeId) {
        tradeInfos.remove(tradeId);
        tradeStates.remove(tradeId);
    }
}