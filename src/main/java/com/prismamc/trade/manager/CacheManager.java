package com.prismamc.trade.manager;

import com.prismamc.trade.model.TradeDocument;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CacheManager - High-Performance In-Memory Cache System
 * 
 * This class provides a sophisticated caching mechanism for trade documents
 * with
 * automatic expiration, cleanup, and thread-safe operations. It implements a
 * time-based eviction policy to maintain optimal memory usage while providing
 * fast access to frequently used trade data.
 * 
 * Key Features:
 * - Thread-safe concurrent access using ConcurrentHashMap
 * - Automatic expiration based on last access time (LRU-like behavior)
 * - Background cleanup task to remove expired entries
 * - Memory-efficient storage with configurable expiration policies
 * - Graceful shutdown with proper resource cleanup
 * - Access time tracking for intelligent cache management
 * 
 * Performance Characteristics:
 * - O(1) average time complexity for get/put operations
 * - Automatic memory management to prevent memory leaks
 * - Periodic cleanup to maintain cache size
 * - Optimized for high-concurrency trade system environments
 * 
 * Configuration:
 * - Cache expiry: 30 minutes after last access
 * - Cleanup interval: Every 15 minutes
 * - Thread-safe for concurrent plugin operations
 * 
 * @author Mansitoh
 * @version 1.0.0
 * @since 1.0.0
 */
public class CacheManager {

    // Core cache storage with thread-safe concurrent access
    private final ConcurrentHashMap<Long, CacheEntry<TradeDocument>> cache;

    // Background task executor for automatic cache maintenance
    private final ScheduledExecutorService cleanupExecutor;

    /** Duration in minutes after which cache entries expire from last access */
    private static final long CACHE_EXPIRY_MINUTES = 30;

    /** Interval in minutes between automatic cleanup operations */
    private static final int CLEANUP_INTERVAL_MINUTES = 15;

    /**
     * Constructs a new CacheManager instance.
     * Initializes the concurrent cache storage and starts the background
     * cleanup task for automatic memory management.
     */
    public CacheManager() {
        this.cache = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        startCleanupTask();
    }

    /**
     * Starts the background cleanup task that automatically removes expired cache
     * entries.
     * This task runs periodically to maintain optimal memory usage and prevent
     * memory leaks from long-running cache entries.
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredEntries,
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    /**
     * Stores a trade document in the cache with the specified trade ID as the key.
     * Creates a new cache entry with current timestamp for expiration tracking.
     * This operation is thread-safe and can be called concurrently.
     * 
     * @param tradeId Unique identifier for the trade (cache key)
     * @param trade   TradeDocument to store in the cache
     */
    public void put(long tradeId, TradeDocument trade) {
        cache.put(tradeId, new CacheEntry<>(trade));
    }

    /**
     * Retrieves a trade document from the cache by trade ID.
     * This method implements intelligent cache management:
     * - Updates access time for retrieved entries (LRU-like behavior)
     * - Automatically removes expired entries when accessed
     * - Returns null for non-existent or expired entries
     * 
     * @param tradeId Unique identifier for the trade to retrieve
     * @return TradeDocument if found and not expired, null otherwise
     */
    public TradeDocument get(long tradeId) {
        CacheEntry<TradeDocument> entry = cache.get(tradeId);

        if (entry != null && !entry.isExpired()) {
            // Update access time to extend cache lifetime (LRU-like behavior)
            entry.updateAccessTime();
            return entry.getValue();
        }

        // Remove expired entries immediately when accessed
        if (entry != null && entry.isExpired()) {
            cache.remove(tradeId);
        }

        return null;
    }

    /**
     * Manually removes a trade document from the cache.
     * This is typically used when a trade is completed, cancelled,
     * or when immediate cache invalidation is required.
     * 
     * @param tradeId Unique identifier for the trade to remove
     */
    public void remove(long tradeId) {
        cache.remove(tradeId);
    }

    /**
     * Performs cleanup of all expired cache entries.
     * This method is called automatically by the background cleanup task
     * but can also be invoked manually for immediate cleanup.
     * 
     * Removes all entries that have exceeded the expiration time limit
     * to maintain optimal memory usage and cache performance.
     */
    public void cleanupExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Gracefully shuts down the cache manager and releases all resources.
     * This method should be called during plugin shutdown to ensure
     * proper cleanup of background tasks and prevent resource leaks.
     * 
     * Shutdown process:
     * 1. Initiates shutdown of the cleanup executor
     * 2. Waits up to 60 seconds for graceful termination
     * 3. Forces shutdown if graceful termination fails
     * 4. Handles interruption scenarios properly
     */
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

    /**
     * CacheEntry - Internal cache entry wrapper with expiration tracking
     * 
     * This inner class wraps cached values with metadata for expiration management.
     * It tracks access times and provides expiration checking functionality
     * to support the cache's time-based eviction policy.
     * 
     * @param <T> Type of the cached value
     */
    private static class CacheEntry<T> {

        /** The cached value */
        private final T value;

        /** Timestamp of last access for expiration calculation */
        private long lastAccessTime;

        /**
         * Creates a new cache entry with the specified value.
         * Initializes the last access time to the current timestamp.
         * 
         * @param value The value to cache
         */
        public CacheEntry(T value) {
            this.value = value;
            this.lastAccessTime = System.currentTimeMillis();
        }

        /**
         * Retrieves the cached value.
         * 
         * @return The cached value
         */
        public T getValue() {
            return value;
        }

        /**
         * Updates the last access time to the current timestamp.
         * This extends the cache entry's lifetime and implements
         * LRU-like behavior for frequently accessed entries.
         */
        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        /**
         * Checks if this cache entry has expired based on the last access time.
         * An entry is considered expired if it hasn't been accessed within
         * the configured expiration time limit.
         * 
         * @return True if the entry has expired, false otherwise
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > TimeUnit.MINUTES.toMillis(CACHE_EXPIRY_MINUTES);
        }
    }
}