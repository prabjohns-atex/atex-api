package com.atex.desk.api.auth;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory sliding-window rate limiter keyed by client IP.
 * Mirrors the Redis-backed rate limiter in mytype-new (lib/rateLimit.ts).
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.windowMs = window.toMillis();
    }

    /**
     * Returns true if the request is allowed, false if rate limited.
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startTime.get() > windowMs) {
                return new Window(now);
            }
            return existing;
        });
        return w.count.incrementAndGet() <= maxRequests;
    }

    /**
     * Evict expired entries to prevent memory leak. Call periodically.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().startTime.get() > windowMs * 2);
    }

    private static class Window {
        final AtomicLong startTime;
        final AtomicInteger count;

        Window(long startTime) {
            this.startTime = new AtomicLong(startTime);
            this.count = new AtomicInteger(0);
        }
    }
}
