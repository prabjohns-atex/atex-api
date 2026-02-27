package com.atex.onecms.app.dam.publish;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UserTokenStorage {

    private static final int MAX_ENTRIES = 50;
    private static final long MAX_AGE_MS = 300_000; // 5 minutes

    private final ConcurrentHashMap<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public Optional<String> getToken(String username) {
        if (username == null) return Optional.empty();
        TokenEntry entry = tokens.get(username);
        if (entry == null) return Optional.empty();
        if (System.currentTimeMillis() - entry.timestamp > MAX_AGE_MS) {
            tokens.remove(username);
            return Optional.empty();
        }
        return Optional.of(entry.token);
    }

    public void putToken(String username, String token) {
        if (username == null || token == null) return;
        // Evict oldest if at capacity
        if (tokens.size() >= MAX_ENTRIES && !tokens.containsKey(username)) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (var entry : tokens.entrySet()) {
                if (entry.getValue().timestamp < oldestTime) {
                    oldestTime = entry.getValue().timestamp;
                    oldest = entry.getKey();
                }
            }
            if (oldest != null) {
                tokens.remove(oldest);
            }
        }
        tokens.put(username, new TokenEntry(token, System.currentTimeMillis()));
    }

    public void invalidate(String username) {
        if (username != null) {
            tokens.remove(username);
        }
    }

    private record TokenEntry(String token, long timestamp) {}
}
