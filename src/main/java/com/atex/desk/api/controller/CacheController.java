package com.atex.desk.api.controller;

import com.atex.desk.api.service.ObjectCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/cache")
@Tag(name = "Cache Management")
public class CacheController
{
    private final ObjectCacheService cacheService;

    public CacheController(ObjectCacheService cacheService)
    {
        this.cacheService = cacheService;
    }

    @GetMapping
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<?> getCacheStats()
    {
        return ResponseEntity.ok(cacheService.getStats());
    }

    @DeleteMapping
    @Operation(summary = "Clear all caches")
    public ResponseEntity<?> clearAll()
    {
        cacheService.clearAll();
        return ResponseEntity.ok(Map.of("status", "cleared", "caches", "all"));
    }

    @DeleteMapping("/{cacheName}")
    @Operation(summary = "Clear a specific cache")
    public ResponseEntity<?> clearCache(@PathVariable String cacheName)
    {
        cacheService.clear(cacheName);
        return ResponseEntity.ok(Map.of("status", "cleared", "cache", cacheName));
    }
}
