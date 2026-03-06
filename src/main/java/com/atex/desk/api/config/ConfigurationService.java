package com.atex.desk.api.config;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.MetaDto;
import com.atex.onecms.content.ConfigurationDataBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Status;
import com.google.gson.Gson;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 3-tier configuration service: product defaults (classpath) → project overrides (classpath/plugin) → live (DB).
 * <p>
 * Scans {@code classpath:config/defaults/} and {@code classpath:config/project/} at startup,
 * plus plugin classloaders for {@code config/project/} resources. All consumers that call
 * {@code ContentManager.resolve(externalId)} get config from resources when no DB content exists.
 * <p>
 * Config files may contain a {@code _meta} block at the top level with metadata (display name, etc.)
 * that is extracted and stored in the {@link ConfigEntry} but stripped from the config data itself.
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
     * Effective cache: external ID → config entry (data + meta).
     * Product defaults loaded first, then overlaid by project/plugin/live configs.
     */
    private final ConcurrentHashMap<String, ConfigEntry> cache = new ConcurrentHashMap<>();

    /**
     * Tracks the source tier of each config for admin/audit.
     * Values: "product", "project", "plugin", "flavor", "live"
     */
    private final ConcurrentHashMap<String, String> sourceTier = new ConcurrentHashMap<>();

    /**
     * Stores the raw product defaults separately so we can compute diffs against live overrides.
     */
    private final ConcurrentHashMap<String, ConfigEntry> productDefaults = new ConcurrentHashMap<>();

    /**
     * Stores the raw project overrides separately.
     */
    private final ConcurrentHashMap<String, ConfigEntry> projectOverrides = new ConcurrentHashMap<>();

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

        long namedCount = cache.values().stream().filter(e -> e.hasName()).count();
        LOG.info("Configuration loaded: " + defaultsCount + " product defaults, "
            + flavorCount + " flavor overrides (" + flavor + "), "
            + projectCount + " project overrides, " + pluginCount + " plugin overrides ("
            + cache.size() + " total configs, " + namedCount + " with display names)");
    }

    /**
     * Returns true if the given external ID is known as a resource-based configuration.
     */
    public boolean isConfigId(String externalId)
    {
        return properties.isEnabled() && cache.containsKey(externalId);
    }

    /**
     * Get the full config entry (data + meta) for the given external ID.
     */
    public Optional<ConfigEntry> getEntry(String externalId)
    {
        if (!properties.isEnabled()) return Optional.empty();
        return Optional.ofNullable(cache.get(externalId));
    }

    /**
     * Get the effective configuration data for the given external ID.
     */
    public Optional<Map<String, Object>> getConfiguration(String externalId)
    {
        return getEntry(externalId).map(ConfigEntry::data);
    }

    /**
     * Get the product default data for the given external ID (ignoring project overrides).
     */
    public Optional<Map<String, Object>> getProductDefault(String externalId)
    {
        return Optional.ofNullable(productDefaults.get(externalId)).map(ConfigEntry::data);
    }

    /**
     * Get the project override data for the given external ID (ignoring product defaults).
     */
    public Optional<Map<String, Object>> getProjectOverride(String externalId)
    {
        return Optional.ofNullable(projectOverrides.get(externalId)).map(ConfigEntry::data);
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
     * Wrap config data in a {@link ContentResult} deserialized as a specific bean class.
     * Used when callers request a typed dataClass (e.g. LayoutServerConfigurationBean)
     * instead of the default ConfigurationDataBean wrapper.
     */
    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> toContentResultAs(String externalId, Class<T> dataClass)
    {
        ConfigEntry entry = cache.get(externalId);
        if (entry == null)
        {
            return ContentResult.of(null, Status.NOT_FOUND);
        }

        T bean = GSON.fromJson(GSON.toJson(entry.data()), dataClass);
        ContentVersionId vid = syntheticVersionId(externalId);
        return new ContentResultBuilder<T>()
            .id(vid)
            .status(Status.OK)
            .mainAspectData(bean)
            .type(dataClass.getName())
            .build();
    }

    /**
     * Wrap config data in a {@link ContentResult} matching the contract all consumers expect.
     * The main aspect is a {@link ConfigurationDataBean} with the JSON data as a string,
     * matching the reference Polopoly p.ConfigurationData format.
     */
    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> toContentResult(String externalId)
    {
        ConfigEntry entry = cache.get(externalId);
        if (entry == null)
        {
            return ContentResult.of(null, Status.NOT_FOUND);
        }

        ConfigurationDataBean bean = entry.toConfigurationDataBean(externalId);
        ContentVersionId vid = syntheticVersionId(externalId);
        return (ContentResult<T>) new ContentResultBuilder<>()
            .id(vid)
            .status(Status.OK)
            .mainAspectData(bean)
            .type("com.atex.onecms.content.ConfigurationDataBean")
            .build();
    }

    /**
     * Wrap config data in a {@link ContentResultDto} for the REST layer.
     * Matches the reference format: contentData wraps a ConfigurationDataBean
     * with the actual JSON in the "json" field as a string.
     */
    public ContentResultDto toContentResultDto(String externalId)
    {
        ConfigEntry entry = cache.get(externalId);
        if (entry == null) return null;

        ContentResultDto dto = new ContentResultDto();
        dto.setId(CONFIG_DELEGATION_ID + ":" + externalId);
        dto.setVersion(CONFIG_DELEGATION_ID + ":" + externalId + ":" + CONFIG_VERSION);

        Map<String, AspectDto> aspects = new LinkedHashMap<>();
        AspectDto contentData = new AspectDto();
        contentData.setName("contentData");

        // Format depends on _meta.format (matching the original Polopoly input-template):
        //   atex.onecms.Template.it      → OneCMSTemplateBean: {data: "<json string>"}
        //   atex.onecms.TemplateList.it   → OneCMSTemplateListBean: {templateList: [...]}
        //   (default)                     → ConfigurationDataBean: {json: "<json string>", ...}
        ConfigMeta entryMeta = entry.meta();
        if (entryMeta != null && entryMeta.isTemplateFormat())
        {
            // OneCMSTemplateBean format: template JSON as a string in "data" field
            Map<String, Object> beanMap = new LinkedHashMap<>();
            beanMap.put("_type", "com.atex.onecms.app.OneCMSTemplateBean");
            beanMap.put("data", GSON.toJson(entry.data()));
            contentData.setData(beanMap);
        }
        else if (entryMeta != null && entryMeta.isTemplateListFormat())
        {
            // OneCMSTemplateListBean format: list of template external IDs
            Map<String, Object> beanMap = new LinkedHashMap<>();
            beanMap.put("_type", "com.atex.onecms.app.OneCMSTemplateListBean");
            beanMap.put("templateList", entry.contentList() != null ? entry.contentList() : List.of());
            contentData.setData(beanMap);
        }
        else
        {
            ConfigurationDataBean bean = entry.toConfigurationDataBean(externalId);
            Map<String, Object> beanMap = new LinkedHashMap<>();
            beanMap.put("_type", "com.atex.onecms.content.ConfigurationDataBean");
            beanMap.put("json_type", "java.lang.String");
            beanMap.put("json", bean.getJson());
            beanMap.put("dataValue_type", "java.lang.String");
            beanMap.put("dataValue", bean.getDataValue());
            beanMap.put("name_type", "java.lang.String");
            beanMap.put("name", bean.getName());
            beanMap.put("dataType_type", "java.lang.String");
            beanMap.put("dataType", bean.getDataType());
            contentData.setData(beanMap);
        }

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
        ConfigEntry defaults = productDefaults.get(externalId);
        ConfigEntry project = projectOverrides.get(externalId);

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
     * Preserves existing meta from the file-based entry if available.
     */
    public void setLiveOverride(String externalId, Map<String, Object> data)
    {
        ConfigEntry existing = cache.get(externalId);
        ConfigMeta meta = existing != null ? existing.meta() : new ConfigMeta(externalId, null, null);
        cache.put(externalId, new ConfigEntry(data, meta, null));
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

                    ConfigEntry entry = parseJson(content, externalId);
                    if (entry == null) continue;

                    // Store in tier-specific maps
                    if ("product".equals(tier))
                    {
                        productDefaults.put(externalId, entry);
                    }
                    else
                    {
                        projectOverrides.put(externalId, entry);
                    }

                    // Update effective cache (later tiers override earlier)
                    cache.put(externalId, entry);
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

    private ConfigEntry parseJson(String content, String externalId)
    {
        Map<String, Object> data;
        try
        {
            // Try standard JSON first
            data = GSON.fromJson(content, MAP_TYPE);
        }
        catch (Exception e)
        {
            // Fall back to JSON5 parser
            try
            {
                String cleaned = Json5Reader.toJson(content);
                data = GSON.fromJson(cleaned, MAP_TYPE);
            }
            catch (Exception e2)
            {
                LOG.log(Level.WARNING, "Failed to parse config JSON for: " + externalId, e2);
                return null;
            }
        }

        if (data == null) return null;

        // Extract _meta block if present — stores metadata, strips from config data
        ConfigMeta meta = ConfigMeta.fromData(data, externalId);

        // Extract _contentList if present — used for template catalog entries
        List<String> contentList = null;
        Object clObj = data.remove("_contentList");
        if (clObj instanceof java.util.List<?> list)
        {
            contentList = list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        }

        return new ConfigEntry(data, meta, contentList);
    }
}