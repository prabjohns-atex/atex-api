package com.atex.desk.api.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures recent HTTP request/response details in a ring buffer for debugging.
 * Thread-safe; entries auto-evict when buffer exceeds max size.
 */
@Service
public class RequestMetricsService {

    private static final int MAX_ENTRIES = 500;

    private final LinkedList<RequestEntry> entries = new LinkedList<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    // Per-URI aggregate stats
    private final ConcurrentHashMap<String, UriStats> uriStats = new ConcurrentHashMap<>();

    public record RequestEntry(
            long id,
            String timestamp,
            String method,
            String uri,
            String queryString,
            int status,
            long durationMs,
            String user,
            String contentType,
            long responseSize
    ) {}

    public static class UriStats {
        public final AtomicLong count = new AtomicLong(0);
        public final AtomicLong totalMs = new AtomicLong(0);
        public volatile long maxMs = 0;
        public volatile long lastMs = 0;

        void record(long ms) {
            count.incrementAndGet();
            totalMs.addAndGet(ms);
            lastMs = ms;
            long cur = maxMs;
            while (ms > cur) {
                maxMs = ms;
                cur = maxMs;
            }
        }
    }

    public void record(String method, String uri, String queryString, int status,
                       long durationMs, String user, String contentType, long responseSize) {
        RequestEntry entry = new RequestEntry(
                idCounter.incrementAndGet(),
                Instant.now().toString(),
                method, uri, queryString, status, durationMs, user, contentType, responseSize
        );

        synchronized (entries) {
            entries.addFirst(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.removeLast();
            }
        }

        // Aggregate by normalized URI pattern
        String pattern = normalizeUri(method, uri);
        uriStats.computeIfAbsent(pattern, k -> new UriStats()).record(durationMs);
    }

    public List<RequestEntry> getRecent(int limit) {
        synchronized (entries) {
            return new ArrayList<>(entries.subList(0, Math.min(limit, entries.size())));
        }
    }

    public Map<String, UriStats> getUriStats() {
        return Map.copyOf(uriStats);
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
        uriStats.clear();
        idCounter.set(0);
    }

    /**
     * Normalize URI by collapsing path variable segments (UUIDs, numeric IDs)
     * into placeholders for aggregation.
     */
    private String normalizeUri(String method, String uri) {
        // Collapse UUIDs
        String normalized = uri.replaceAll(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                "{id}");
        // Collapse onecms:id:version patterns
        normalized = normalized.replaceAll("onecms:[^/]+", "{contentId}");
        // Collapse pure numeric segments
        normalized = normalized.replaceAll("/\\d+(/|$)", "/{id}$1");
        return method + " " + normalized;
    }
}
