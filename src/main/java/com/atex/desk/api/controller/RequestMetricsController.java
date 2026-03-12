package com.atex.desk.api.controller;

import com.atex.desk.api.service.RequestMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/requests")
@Tag(name = "Request Metrics")
public class RequestMetricsController {

    private final RequestMetricsService metricsService;

    public RequestMetricsController(RequestMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    @Operation(summary = "Get recent requests", description = "Returns the most recent HTTP requests with timing and status")
    public ResponseEntity<List<RequestMetricsService.RequestEntry>> getRecent(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(metricsService.getRecent(Math.min(limit, 500)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get per-URI aggregate stats", description = "Returns count, avg, max latency per URI pattern")
    public ResponseEntity<List<Map<String, Object>>> getStats() {
        var stats = metricsService.getUriStats();
        List<Map<String, Object>> result = stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().count.get(), a.getValue().count.get()))
                .map(e -> {
                    var s = e.getValue();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("pattern", e.getKey());
                    m.put("count", s.count.get());
                    m.put("avgMs", s.count.get() > 0 ? s.totalMs.get() / s.count.get() : 0);
                    m.put("maxMs", s.maxMs);
                    m.put("lastMs", s.lastMs);
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping
    @Operation(summary = "Clear request metrics")
    public ResponseEntity<Map<String, String>> clear() {
        metricsService.clear();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }
}
