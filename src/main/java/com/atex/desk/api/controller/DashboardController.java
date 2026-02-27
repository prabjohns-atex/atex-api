package com.atex.desk.api.controller;

import com.atex.desk.api.auth.TokenProperties;
import com.atex.desk.api.config.ConfigProperties;
import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.config.DeskProperties;
import com.atex.desk.api.plugin.PluginProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard API providing system status and endpoint listing.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard")
public class DashboardController
{
    private final ConfigurationService configurationService;
    private final ConfigProperties configProperties;
    private final DeskProperties deskProperties;
    private final TokenProperties tokenProperties;
    private final PluginProperties pluginProperties;
    private final RequestMappingHandlerMapping handlerMapping;

    private final Instant startTime = Instant.now();

    public DashboardController(ConfigurationService configurationService,
                                ConfigProperties configProperties,
                                DeskProperties deskProperties,
                                TokenProperties tokenProperties,
                                PluginProperties pluginProperties,
                                @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping)
    {
        this.configurationService = configurationService;
        this.configProperties = configProperties;
        this.deskProperties = deskProperties;
        this.tokenProperties = tokenProperties;
        this.pluginProperties = pluginProperties;
        this.handlerMapping = handlerMapping;
    }

    /**
     * GET /api/status - System info, config summary, endpoint count.
     */
    @GetMapping("/status")
    @SecurityRequirements
    @Operation(summary = "Get system status")
    public Map<String, Object> getStatus()
    {
        Map<String, Object> result = new LinkedHashMap<>();

        // System info
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("javaVendor", System.getProperty("java.vendor"));
        system.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());
        system.put("startTime", startTime.toString());
        Duration uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        system.put("uptime", formatDuration(uptime));
        system.put("uptimeMs", uptime.toMillis());
        result.put("system", system);

        // Config summary
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("totalConfigs", configurationService.getAllConfigIds().size());
        config.put("flavor", configProperties.getFlavor());
        config.put("configEnabled", configProperties.isEnabled());
        config.put("authEnabled", tokenProperties.isEnabled());
        config.put("instanceId", tokenProperties.getInstanceId());
        config.put("pluginsEnabled", pluginProperties.isEnabled());
        config.put("pluginsDirectory", pluginProperties.getDirectory());
        config.put("solrUrl", deskProperties.getSolrUrl());
        config.put("solrCore", deskProperties.getSolrCore());
        config.put("apiUrl", deskProperties.getApiUrl());
        result.put("config", config);

        // Endpoint count
        int endpointCount = handlerMapping.getHandlerMethods().size();
        result.put("endpointCount", endpointCount);

        return result;
    }

    /**
     * GET /api/endpoints - All registered endpoint mappings.
     */
    @GetMapping("/endpoints")
    @SecurityRequirements
    @Operation(summary = "List all registered endpoints")
    public List<Map<String, Object>> getEndpoints()
    {
        List<Map<String, Object>> endpoints = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Map<String, Object> entry = new LinkedHashMap<>();

            // Patterns
            var patterns = info.getPatternValues();
            entry.put("pattern", patterns.isEmpty() ? "" : patterns.iterator().next());

            // HTTP methods
            var methods = info.getMethodsCondition().getMethods();
            entry.put("methods", methods.stream()
                .map(Enum::name)
                .sorted()
                .toList());

            // Controller
            entry.put("controller", method.getBeanType().getSimpleName());
            entry.put("handler", method.getMethod().getName());

            endpoints.add(entry);
        });

        // Sort by pattern for consistent output
        endpoints.sort((a, b) -> {
            String pa = (String) a.get("pattern");
            String pb = (String) b.get("pattern");
            return pa.compareTo(pb);
        });

        return endpoints;
    }

    private String formatDuration(Duration duration)
    {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0)
        {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        }
        if (hours > 0)
        {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0)
        {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }
}
