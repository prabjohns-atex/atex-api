package com.atex.desk.api.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global object cache with per-type configurable TTL and max size.
 * Uses LRU eviction via access-ordered LinkedHashMap and passive TTL on get().
 * Thread-safe via ConcurrentHashMap of synchronized LRU maps.
 */
@Service
public class ObjectCacheService
{
    private final ConcurrentHashMap<String, CacheConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, CacheEntry>> caches = new ConcurrentHashMap<>();

    private record CacheEntry(Object value, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    /**
     * Pre-register a named cache with TTL and max size.
     * Can be called multiple times; last call wins.
     */
    public void configure(String cacheName, long ttlMs, int maxSize)
    {
        configs.put(cacheName, new CacheConfig(ttlMs, maxSize));
        // Re-create the backing map if max size changed
        caches.put(cacheName, createLruMap(maxSize));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key)
    {
        Map<String, CacheEntry> map = caches.get(cacheName);
        if (map == null) return null;
        synchronized (map)
        {
            CacheEntry entry = map.get(key);
            if (entry == null) return null;
            if (entry.isExpired())
            {
                map.remove(key);
                return null;
            }
            return (T) entry.value();
        }
    }

    public void put(String cacheName, String key, Object value)
    {
        CacheConfig config = configs.get(cacheName);
        if (config == null)
        {
            // Auto-configure with defaults: 5 min TTL, 200 entries
            configure(cacheName, 5 * 60 * 1000L, 200);
            config = configs.get(cacheName);
        }
        final int maxSize = config.maxSize();
        Map<String, CacheEntry> map = caches.computeIfAbsent(cacheName,
            k -> createLruMap(maxSize));
        long expiresAt = System.currentTimeMillis() + config.ttlMs();
        synchronized (map)
        {
            map.put(key, new CacheEntry(value, expiresAt));
        }
    }

    public void evict(String cacheName, String key)
    {
        Map<String, CacheEntry> map = caches.get(cacheName);
        if (map != null)
        {
            synchronized (map)
            {
                map.remove(key);
            }
        }
    }

    public void clear(String cacheName)
    {
        Map<String, CacheEntry> map = caches.get(cacheName);
        if (map != null)
        {
            synchronized (map)
            {
                map.clear();
            }
        }
    }

    public void clearAll()
    {
        for (Map<String, CacheEntry> map : caches.values())
        {
            synchronized (map)
            {
                map.clear();
            }
        }
    }

    /**
     * Returns stats for all registered caches.
     */
    public Map<String, CacheStats> getStats()
    {
        Map<String, CacheStats> stats = new LinkedHashMap<>();
        for (String name : caches.keySet())
        {
            Map<String, CacheEntry> map = caches.get(name);
            CacheConfig config = configs.getOrDefault(name, new CacheConfig(0, 0));
            int size;
            int expired = 0;
            synchronized (map)
            {
                size = map.size();
                for (CacheEntry entry : map.values())
                {
                    if (entry.isExpired()) expired++;
                }
            }
            stats.put(name, new CacheStats(size, size - expired, config.maxSize(), config.ttlMs()));
        }
        return stats;
    }

    public Set<String> getCacheNames()
    {
        return Collections.unmodifiableSet(caches.keySet());
    }

    public record CacheStats(int totalEntries, int activeEntries, int maxSize, long ttlMs) {}
    private record CacheConfig(long ttlMs, int maxSize) {}

    private Map<String, CacheEntry> createLruMap(int maxSize)
    {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest)
            {
                return size() > maxSize;
            }
        });
    }
}
