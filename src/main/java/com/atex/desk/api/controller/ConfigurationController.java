package com.atex.desk.api.controller;

import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.app.dam.ws.DamUserContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@RestController
@RequestMapping("/admin/config")
public class ConfigurationController
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ConfigurationService configurationService;
    private final ContentService contentService;

    public ConfigurationController(ConfigurationService configurationService,
                                    ContentService contentService)
    {
        this.configurationService = configurationService;
        this.contentService = contentService;
    }

    /**
     * GET /admin/config — List all known config IDs with source tier.
     */
    @GetMapping
    public ResponseEntity<?> listConfigs()
    {
        Map<String, Object> result = new TreeMap<>();
        for (String id : configurationService.getAllConfigIds())
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("source", resolveEffectiveTier(id));
            result.put(id, entry);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /admin/config/{externalId} — Get effective config JSON.
     */
    @GetMapping("/{externalId}")
    public ResponseEntity<?> getConfig(@PathVariable String externalId)
    {
        // Check for live (DB) override first
        Optional<Map<String, Object>> liveData = getLiveConfig(externalId);
        if (liveData.isPresent())
        {
            return ResponseEntity.ok(liveData.get());
        }

        // Fall back to resource-based config
        Optional<Map<String, Object>> data = configurationService.getConfiguration(externalId);
        return data
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto("NOT_FOUND", "Configuration not found: " + externalId)));
    }

    /**
     * GET /admin/config/{externalId}/sources — Get all tiers separately for diff/audit.
     */
    @GetMapping("/{externalId}/sources")
    public ResponseEntity<?> getConfigSources(@PathVariable String externalId)
    {
        Map<String, Object> sources = new LinkedHashMap<>();

        configurationService.getProductDefault(externalId)
            .ifPresent(d -> sources.put("product", d));

        configurationService.getProjectOverride(externalId)
            .ifPresent(d -> sources.put("project", d));

        getLiveConfig(externalId)
            .ifPresent(d -> sources.put("live", d));

        if (sources.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto("NOT_FOUND", "Configuration not found: " + externalId));
        }

        sources.put("effective_source", resolveEffectiveTier(externalId));
        return ResponseEntity.ok(sources);
    }

    /**
     * PUT /admin/config/{externalId} — Save live override to DB.
     */
    @PutMapping("/{externalId}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> saveConfig(@PathVariable String externalId,
                                         @RequestBody Map<String, Object> data,
                                         HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        if (!userContext.isLoggedIn())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication required"));
        }

        String userId = userContext.getCaller() != null
            ? userContext.getCaller().getLoginName() : "system";

        // Create or update content with this external ID
        ContentWriteDto writeDto = new ContentWriteDto();
        Map<String, AspectDto> aspects = new LinkedHashMap<>();
        AspectDto contentData = new AspectDto();
        contentData.setName("contentData");
        contentData.setData(data);
        aspects.put("contentData", contentData);
        writeDto.setAspects(aspects);

        // Check if content already exists in DB
        Optional<String> existing = contentService.resolveExternalId(externalId);
        if (existing.isPresent())
        {
            String[] parts = contentService.parseContentId(existing.get());
            contentService.updateContent(parts[0], parts[1], writeDto, userId);
        }
        else
        {
            contentService.createContent(writeDto, userId);
            // The external ID alias needs to be set — for now the content exists in DB
            // and can be resolved by its content ID
        }

        // Update the config cache
        configurationService.setLiveOverride(externalId, data);

        return ResponseEntity.ok(Map.of("status", "saved", "externalId", externalId));
    }

    /**
     * DELETE /admin/config/{externalId} — Delete live override, revert to resource defaults.
     */
    @DeleteMapping("/{externalId}")
    public ResponseEntity<?> deleteConfig(@PathVariable String externalId,
                                            HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        if (!userContext.isLoggedIn())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication required"));
        }

        // Delete from DB if it exists
        Optional<String> existing = contentService.resolveExternalId(externalId);
        if (existing.isPresent())
        {
            String[] parts = contentService.parseContentId(existing.get());
            String userId = userContext.getCaller() != null
                ? userContext.getCaller().getLoginName() : "system";
            contentService.deleteContent(parts[0], parts[1], userId);
        }

        // Revert cache to resource defaults
        configurationService.removeLiveOverride(externalId);

        return ResponseEntity.ok(Map.of("status", "reverted", "externalId", externalId));
    }

    /**
     * GET /admin/config/export — Export all live (DB) overrides as a JSON bundle.
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportConfigs()
    {
        Map<String, Object> bundle = new TreeMap<>();
        for (String id : configurationService.getAllConfigIds())
        {
            if ("live".equals(resolveEffectiveTier(id)))
            {
                getLiveConfig(id).ifPresent(data -> bundle.put(id, data));
            }
        }
        return ResponseEntity.ok(bundle);
    }

    /**
     * GET /admin/config/patch — Unified diff of DB overrides against resource defaults.
     */
    @GetMapping(value = "/patch", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> patchConfigs()
    {
        StringBuilder patch = new StringBuilder();

        for (String id : new TreeMap<>(Map.copyOf(
                configurationService.getAllConfigIds().stream()
                    .collect(java.util.stream.Collectors.toMap(k -> k, k -> k))))
                .keySet())
        {
            if (!"live".equals(resolveEffectiveTier(id))) continue;

            Optional<Map<String, Object>> liveData = getLiveConfig(id);
            if (liveData.isEmpty()) continue;

            // Get the base (resource default or project override)
            Optional<Map<String, Object>> projectData = configurationService.getProjectOverride(id);
            Optional<Map<String, Object>> productData = configurationService.getProductDefault(id);
            Map<String, Object> baseData = projectData.orElse(productData.orElse(Map.of()));

            String baseJson = GSON.toJson(baseData);
            String liveJson = GSON.toJson(liveData.get());

            if (baseJson.equals(liveJson)) continue;

            String filename = "config/project/" + id + ".json";
            String[] baseLines = baseJson.split("\n");
            String[] liveLines = liveJson.split("\n");

            patch.append("--- a/").append(filename).append("\n");
            patch.append("+++ b/").append(filename).append("\n");
            patch.append("@@ -1,").append(baseLines.length)
                 .append(" +1,").append(liveLines.length).append(" @@\n");

            for (String line : baseLines)
            {
                patch.append("-").append(line).append("\n");
            }
            for (String line : liveLines)
            {
                patch.append("+").append(line).append("\n");
            }
        }

        if (patch.isEmpty())
        {
            return ResponseEntity.ok("# No live overrides differ from resource defaults\n");
        }

        return ResponseEntity.ok(patch.toString());
    }

    // --- Helpers ---

    /**
     * Determine the effective tier for a config ID: "live" if DB has it, otherwise resource tier.
     */
    private String resolveEffectiveTier(String externalId)
    {
        Optional<String> dbContent = contentService.resolveExternalId(externalId);
        if (dbContent.isPresent())
        {
            return "live";
        }
        return configurationService.getSourceTier(externalId);
    }

    /**
     * Get config data from DB (live override) if it exists.
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> getLiveConfig(String externalId)
    {
        Optional<String> contentIdStr = contentService.resolveExternalId(externalId);
        if (contentIdStr.isEmpty()) return Optional.empty();

        String[] parts = contentService.parseContentId(contentIdStr.get());
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);
        if (versionedId.isEmpty()) return Optional.empty();

        String[] vParts = contentService.parseContentId(versionedId.get());
        return contentService.getContent(vParts[0], vParts[1], vParts[2])
            .map(result -> {
                if (result.getAspects() != null && result.getAspects().containsKey("contentData"))
                {
                    return result.getAspects().get("contentData").getData();
                }
                return null;
            });
    }
}
