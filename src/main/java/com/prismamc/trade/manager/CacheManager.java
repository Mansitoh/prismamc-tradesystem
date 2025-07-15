package com.prismamc.trade.manager;

import com.prismamc.trade.model.TradeDocument;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    private final ConcurrentHashMap<Long, CacheEntry<TradeDocument>> cache;
    private final ScheduledExecutorService cleanupExecutor;
    private static final long CACHE_EXPIRY_MINUTES = 30;
    private static final int CLEANUP_INTERVAL_MINUTES = 15;

    public CacheManager() {
        this.cache = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        startCleanupTask();
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredEntries,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
    }

    public void put(long tradeId, TradeDocument trade) {
        cache.put(tradeId, new CacheEntry<>(trade));
    }

    public TradeDocument get(long tradeId) {
        CacheEntry<TradeDocument> entry = cache.get(tradeId);
        if (entry != null && !entry.isExpired()) {
            entry.updateAccessTime();
            return entry.getValue();
        }
        if (entry != null && entry.isExpired()) {
            cache.remove(tradeId);
        }
        return null;
    }

    public void remove(long tradeId) {
        cache.remove(tradeId);
    }

    public void cleanupExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class CacheEntry<T> {
        private final T value;
        private long lastAccessTime;

        public CacheEntry(T value) {
            this.value = value;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public T getValue() {
            return value;
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > TimeUnit.MINUTES.toMillis(CACHE_EXPIRY_MINUTES);
        }
    }
}