package com.atex.desk.api.config;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.MetaDto;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.aspects.Aspect;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 3-tier configuration service: product defaults (classpath) → project overrides (classpath/plugin) → live (DB).
 * <p>
 * Scans {@code classpath:config/defaults/} and {@code classpath:config/project/} at startup,
 * plus plugin classloaders for {@code config/project/} resources. All consumers that call
 * {@code ContentManager.resolve(externalId)} get config from resources when no DB content exists.
 */
@Component
public class ConfigurationService
{
    private static final Logger LOG = Logger.getLogger(ConfigurationService.class.getName());
    private static final String[] DEFAULTS_PATTERNS = {
        "classpath*:config/defaults/**/*.json5",
        "classpath*:config/defaults/**/*.json"
    };
    private static final String[] PROJECT_PATTERNS = {
        "classpath*:config/project/**/*.json5",
        "classpath*:config/project/**/*.json"
    };
    private static final String CONFIG_DELEGATION_ID = "config";
    private static final String CONFIG_VERSION = "1";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final ConfigProperties properties;
    private final PluginManager pluginManager;

    /**
     * Cache of external ID → parsed JSON data.
     * Product defaults loaded first, then overlaid by project configs.
     */
    private final ConcurrentHashMap<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    /**
     * Tracks the source tier of each config for admin/audit.
     * Values: "product", "project", "plugin"
     */
    private final ConcurrentHashMap<String, String> sourceTier = new ConcurrentHashMap<>();

    /**
     * Stores the raw product defaults separately so we can compute diffs against live overrides.
     */
    private final ConcurrentHashMap<String, Map<String, Object>> productDefaults = new ConcurrentHashMap<>();

    /**
     * Stores the raw project overrides separately.
     */
    private final ConcurrentHashMap<String, Map<String, Object>> projectOverrides = new ConcurrentHashMap<>();

    public ConfigurationService(ConfigProperties properties, @Nullable PluginManager pluginManager)
    {
        this.properties = properties;
        this.pluginManager = pluginManager;
    }

    @PostConstruct
    public void init()
    {
        if (!properties.isEnabled())
        {
            LOG.info("Resource-based configuration is disabled");
            return;
        }

        // 1. Load product defaults from classpath (.json5 first, then .json)
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        int defaultsCount = loadFromClasspath(DEFAULTS_PATTERNS, "product", cl);

        // 2. Load flavor overrides (only the files that differ for this flavor)
        int flavorCount = 0;
        String flavor = properties.getFlavor();
        if (flavor != null && !flavor.isBlank())
        {
            String[] flavorPatterns = {
                "classpath*:config/flavor/" + flavor + "/**/*.json5",
                "classpath*:config/flavor/" + flavor + "/**/*.json"
            };
            flavorCount = loadFromClasspath(flavorPatterns, "flavor", cl);
        }

        // 3. Load project overrides from classpath (overwrites defaults + flavor)
        int projectCount = loadFromClasspath(PROJECT_PATTERNS, "project", cl);

        // 4. Load project overrides from plugin classloaders
        int pluginCount = 0;
        if (pluginManager != null)
        {
            String[] pluginPatterns = {
                "classpath*:config/project/**/*.json5",
                "classpath*:config/project/**/*.json"
            };
            for (PluginWrapper plugin : pluginManager.getStartedPlugins())
            {
                ClassLoader pluginCl = plugin.getPluginClassLoader();
                pluginCount += loadFromClasspath(pluginPatterns, "plugin", pluginCl);
            }
        }

        LOG.info("Configuration loaded: " + defaultsCount + " product defaults, "
            + flavorCount + " flavor overrides (" + flavor + "), "
            + projectCount + " project overrides, " + pluginCount + " plugin overrides ("
            + cache.size() + " total configs)");
    }

    /**
     * Returns true if the given external ID is known as a resource-based configuration.
     */
    public boolean isConfigId(String externalId)
    {
        return properties.isEnabled() && cache.containsKey(externalId);
    }

    /**
     * Get the effective configuration data for the given external ID.
     */
    public Optional<Map<String, Object>> getConfiguration(String externalId)
    {
        if (!properties.isEnabled()) return Optional.empty();
        return Optional.ofNullable(cache.get(externalId));
    }

    /**
     * Get the product default data for the given external ID (ignoring project overrides).
     */
    public Optional<Map<String, Object>> getProductDefault(String externalId)
    {
        return Optional.ofNullable(productDefaults.get(externalId));
    }

    /**
     * Get the project override data for the given external ID (ignoring product defaults).
     */
    public Optional<Map<String, Object>> getProjectOverride(String externalId)
    {
        return Optional.ofNullable(projectOverrides.get(externalId));
    }

    /**
     * Get the source tier for the given external ID ("product", "project", or "plugin").
     */
    public String getSourceTier(String externalId)
    {
        return sourceTier.getOrDefault(externalId, "unknown");
    }

    /**
     * Get all known config external IDs.
     */
    public Set<String> getAllConfigIds()
    {
        return Collections.unmodifiableSet(cache.keySet());
    }

    /**
     * Build a synthetic {@link ContentVersionId} for a resource-based configuration.
     */
    public ContentVersionId syntheticVersionId(String externalId)
    {
        return new ContentVersionId(CONFIG_DELEGATION_ID, externalId, CONFIG_VERSION);
    }

    /**
     * Build a synthetic {@link ContentId} for a resource-based configuration.
     */
    public ContentId syntheticContentId(String externalId)
    {
        return new ContentId(CONFIG_DELEGATION_ID, externalId);
    }

    /**
     * Returns true if the given ContentVersionId refers to a resource-based configuration.
     */
    public boolean isSyntheticId(ContentVersionId vid)
    {
        return CONFIG_DELEGATION_ID.equals(vid.getDelegationId());
    }

    /**
     * Wrap config data in a {@link ContentResult} matching the contract all consumers expect.
     */
    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> toContentResult(String externalId)
    {
        Map<String, Object> data = cache.get(externalId);
        if (data == null)
        {
            return ContentResult.of(null, Status.NOT_FOUND);
        }

        ContentVersionId vid = syntheticVersionId(externalId);
        return (ContentResult<T>) new ContentResultBuilder<>()
            .id(vid)
            .status(Status.OK)
            .mainAspectData(data)
            .type("com.atex.standard.content.ContentBean")
            .build();
    }

    /**
     * Wrap config data in a {@link ContentResultDto} for the REST layer.
     */
    public ContentResultDto toContentResultDto(String externalId)
    {
        Map<String, Object> data = cache.get(externalId);
        if (data == null) return null;

        ContentResultDto dto = new ContentResultDto();
        dto.setId(CONFIG_DELEGATION_ID + ":" + externalId);
        dto.setVersion(CONFIG_DELEGATION_ID + ":" + externalId + ":" + CONFIG_VERSION);

        Map<String, AspectDto> aspects = new LinkedHashMap<>();
        AspectDto contentData = new AspectDto();
        contentData.setName("contentData");
        contentData.setData(data);
        aspects.put("contentData", contentData);
        dto.setAspects(aspects);

        MetaDto meta = new MetaDto();
        meta.setModificationTime(String.valueOf(System.currentTimeMillis()));
        meta.setOriginalCreationTime(String.valueOf(System.currentTimeMillis()));
        dto.setMeta(meta);

        return dto;
    }

    /**
     * Invalidate a cached config entry (forces reload from files on next access).
     * Currently reloads immediately from classpath.
     */
    public void invalidate(String externalId)
    {
        cache.remove(externalId);
        sourceTier.remove(externalId);

        // Reload from product defaults and project overrides
        Map<String, Object> defaults = productDefaults.get(externalId);
        Map<String, Object> project = projectOverrides.get(externalId);

        if (project != null)
        {
            cache.put(externalId, project);
            sourceTier.put(externalId, "project");
        }
        else if (defaults != null)
        {
            cache.put(externalId, defaults);
            sourceTier.put(externalId, "product");
        }
    }

    /**
     * Update the effective cache with a live (DB) override value.
     * Called by the admin controller when a config is saved to DB.
     */
    public void setLiveOverride(String externalId, Map<String, Object> data)
    {
        cache.put(externalId, data);
        sourceTier.put(externalId, "live");
    }

    /**
     * Remove a live override, reverting to resource defaults.
     */
    public void removeLiveOverride(String externalId)
    {
        invalidate(externalId);
    }

    // --- Internal ---

    /**
     * Load configs from multiple glob patterns in order. If both .json5 and .json exist
     * for the same external ID within this tier, the first one found wins (.json5 patterns
     * should be listed before .json patterns).
     */
    private int loadFromClasspath(String[] patterns, String tier, ClassLoader classLoader)
    {
        int count = 0;
        for (String pattern : patterns)
        {
            count += loadFromClasspathPattern(pattern, tier, classLoader);
        }
        return count;
    }

    private int loadFromClasspathPattern(String pattern, String tier, ClassLoader classLoader)
    {
        int count = 0;
        try
        {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources)
            {
                try
                {
                    String filename = resource.getFilename();
                    if (filename == null) continue;

                    // Strip .json5 or .json extension to get external ID
                    String externalId;
                    if (filename.endsWith(".json5"))
                    {
                        externalId = filename.substring(0, filename.length() - 6);
                    }
                    else if (filename.endsWith(".json"))
                    {
                        externalId = filename.substring(0, filename.length() - 5);
                    }
                    else
                    {
                        continue;
                    }

                    // Skip if already loaded in this tier (e.g. .json5 already loaded, skip .json)
                    if (cache.containsKey(externalId) && tier.equals(sourceTier.get(externalId)))
                    {
                        continue;
                    }

                    String content = readResource(resource);
                    if (content.isBlank()) continue;

                    Map<String, Object> data = parseJson(content, externalId);
                    if (data == null) continue;

                    // Store in tier-specific maps
                    if ("product".equals(tier))
                    {
                        productDefaults.put(externalId, data);
                    }
                    else
                    {
                        projectOverrides.put(externalId, data);
                    }

                    // Update effective cache (later tiers override earlier)
                    cache.put(externalId, data);
                    sourceTier.put(externalId, tier);
                    count++;
                }
                catch (Exception e)
                {
                    LOG.log(Level.WARNING, "Failed to load config resource: " + resource, e);
                }
            }
        }
        catch (IOException e)
        {
            LOG.log(Level.WARNING, "Failed to scan config resources for pattern: " + pattern, e);
        }
        return count;
    }

    private String readResource(Resource resource) throws IOException
    {
        try (InputStream is = resource.getInputStream())
        {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> parseJson(String content, String externalId)
    {
        try
        {
            // Try standard JSON first
            return GSON.fromJson(content, MAP_TYPE);
        }
        catch (Exception e)
        {
            // Fall back to JSON5 parser
            try
            {
                String cleaned = Json5Reader.toJson(content);
                return GSON.fromJson(cleaned, MAP_TYPE);
            }
            catch (Exception e2)
            {
                LOG.log(Level.WARNING, "Failed to parse config JSON for: " + externalId, e2);
                return null;
            }
        }
    }
}
