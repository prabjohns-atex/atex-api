package com.atex.desk.api.controller;

import com.atex.desk.api.config.ConfigEntry;
import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.repository.AppGroupMemberRepository;
import com.atex.desk.api.repository.AppGroupRepository;
import com.atex.desk.api.repository.AppUserRepository;
import com.atex.onecms.app.dam.ws.DamUserContext;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ConfigurationDataBean;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reimplements the OneCMS ConfigurationResource at /configuration.
 *
 * Endpoints:
 *   GET  /configuration/profile/{appName}        - Application profile (merged config sections)
 *   GET  /configuration/profile/me/{appName}      - User-specific profile
 *   POST /configuration/profile/me/{appName}      - Save user-specific profile
 *   DELETE /configuration/profile/me/{appName}     - Delete user-specific profile
 *   GET  /configuration/widgets/{widgetName}       - Widget configuration
 *   GET  /configuration/templates/{templateId}     - Template configuration
 *   GET  /configuration/system                     - System configuration
 */
@RestController
@RequestMapping("/configuration")
@Tag(name = "Configuration")
public class ProfileConfigurationController
{
    private static final Logger LOGGER = Logger.getLogger(ProfileConfigurationController.class.getName());
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_PREFIX = "atex.configuration.";
    private static final String CONFIG_COMMON = "common";
    private static final String CONFIG_OVERRIDE = "override";
    private static final String PROFILES_SECTION = "profiles";
    private static final String CONFIGURATIONS_SECTION = "configurations";
    private static final String USER_PROFILE_PREFIX = "atex.onecms.user.profile.";

    private final ConfigurationService configurationService;
    private final ContentManager contentManager;
    private final AppUserRepository appUserRepository;
    private final AppGroupRepository groupRepository;
    private final AppGroupMemberRepository groupMemberRepository;

    public ProfileConfigurationController(ConfigurationService configurationService,
                                          ContentManager contentManager,
                                          AppUserRepository appUserRepository,
                                          AppGroupRepository groupRepository,
                                          AppGroupMemberRepository groupMemberRepository)
    {
        this.configurationService = configurationService;
        this.contentManager = contentManager;
        this.appUserRepository = appUserRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    /**
     * GET /configuration/profile/{appName} — Application profile.
     *
     * Resolves the user's config set from profiles, then merges all sections
     * from configurations into a single JSON response.
     */
    @GetMapping(value = "/profile/{appName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get application profile",
               description = "Returns the merged configuration profile for the given application")
    public ResponseEntity<?> getApplicationProfile(
            @PathVariable String appName,
            @RequestParam(defaultValue = "false") boolean pretty,
            HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        String loginName = userContext.isLoggedIn() && userContext.getCaller() != null
                ? userContext.getCaller().getLoginName() : null;

        // 1. Load profiles config → determine which config set to use
        JsonObject profiles = loadSectionAsObject(appName, PROFILES_SECTION);
        if (profiles == null)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Missing profiles configuration for " + appName));
        }

        String configSetName = resolveConfigSetName(profiles, loginName);
        if (configSetName == null)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Configuration set name not found for " + appName));
        }

        // 2. Load configurations → map section names to config IDs
        JsonArray configurations = loadSectionAsArray(appName, CONFIGURATIONS_SECTION);
        if (configurations == null)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Missing configurations for " + appName));
        }

        Map<String, String> configMap = resolveConfigurationMap(configurations, configSetName);

        // 3. For each section, load the config and merge into result
        JsonObject result = new JsonObject();
        for (Map.Entry<String, String> entry : configMap.entrySet())
        {
            String sectionName = entry.getKey();
            String sectionConfigId = entry.getValue();

            JsonObject sectionConfig = findSectionConfig(appName, sectionName, sectionConfigId);
            if (sectionConfig != null)
            {
                result.add(sectionName, sectionConfig);
            }
        }

        // 4. Add user profile if it exists
        if (loginName != null)
        {
            getUserProfileJson(loginName, appName)
                    .ifPresent(up -> result.add("userProfile", up));
        }

        String json = pretty ? GSON_PRETTY.toJson(result) : GSON.toJson(result);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(json);
    }

    /**
     * GET /configuration/profile/me/{appName} — Get user-specific profile.
     */
    @GetMapping(value = "/profile/me/{appName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get user profile", description = "Returns the user's saved profile for the application")
    public ResponseEntity<?> getUserProfile(
            @PathVariable String appName,
            @RequestParam(defaultValue = "false") boolean pretty,
            HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        if (!userContext.isLoggedIn() || userContext.getCaller() == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponseDto(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }

        String loginName = userContext.getCaller().getLoginName();
        Optional<JsonElement> profile = getUserProfileJson(loginName, appName);
        if (profile.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto(HttpStatus.NOT_FOUND,
                            "User profile " + USER_PROFILE_PREFIX + appName + " has not been found"));
        }

        String json = pretty ? GSON_PRETTY.toJson(profile.get()) : GSON.toJson(profile.get());
        return ResponseEntity.ok()
                .body(json);
    }

    /**
     * POST /configuration/profile/me/{appName} — Save user-specific profile.
     */
    @PostMapping(value = "/profile/me/{appName}",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save user profile", description = "Saves the user's profile for the application")
    public ResponseEntity<?> saveUserProfile(
            @PathVariable String appName,
            @RequestBody String content,
            @RequestParam(defaultValue = "false") boolean pretty,
            HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        if (!userContext.isLoggedIn() || userContext.getCaller() == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponseDto(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }

        // Validate JSON
        JsonElement parsed;
        try
        {
            parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject())
            {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "The given json is not a json object"));
            }
        }
        catch (Exception e)
        {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "Invalid JSON: " + e.getMessage()));
        }

        String loginName = userContext.getCaller().getLoginName();
        String externalId = USER_PROFILE_PREFIX + loginName + "." + appName;

        // Store as a ConfigurationDataBean in the content system
        String userId = loginName;
        Subject subject = new Subject(userId, null);
        boolean existing = false;

        try
        {
            ContentVersionId resolved = contentManager.resolve(externalId, subject);
            existing = resolved != null;

            ConfigurationDataBean bean = new ConfigurationDataBean();
            bean.setName(USER_PROFILE_PREFIX + appName);
            bean.setDataType("json");
            bean.setDataValue(content);
            bean.setJson(content);

            if (existing)
            {
                // Update existing
                var existingResult = contentManager.get(resolved, ConfigurationDataBean.class, subject);
                if (existingResult.getStatus().isSuccess())
                {
                    var cw = new com.atex.onecms.content.ContentWriteBuilder<ConfigurationDataBean>()
                            .origin(existingResult.getContentId())
                            .type(existingResult.getContent().getContentDataType())
                            .mainAspectData(bean)
                            .buildUpdate();
                    contentManager.update(existingResult.getContentId().getContentId(), cw, subject);
                }
            }
            else
            {
                // Create new with alias
                var cw = new com.atex.onecms.content.ContentWriteBuilder<ConfigurationDataBean>()
                        .mainAspectData(bean)
                        .type("p.ConfigurationData")
                        .buildCreate();
                contentManager.create(cw, subject);
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to save user profile: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
        }

        String json = pretty ? GSON_PRETTY.toJson(parsed) : GSON.toJson(parsed);
        return ResponseEntity.status(existing ? HttpStatus.OK : HttpStatus.CREATED)
                .body(json);
    }

    /**
     * DELETE /configuration/profile/me/{appName} — Delete user-specific profile.
     */
    @DeleteMapping(value = "/profile/me/{appName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete user profile", description = "Deletes the user's saved profile for the application")
    public ResponseEntity<?> deleteUserProfile(
            @PathVariable String appName,
            HttpServletRequest request)
    {
        DamUserContext userContext = DamUserContext.from(request);
        if (!userContext.isLoggedIn() || userContext.getCaller() == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponseDto(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }

        String loginName = userContext.getCaller().getLoginName();
        String externalId = USER_PROFILE_PREFIX + loginName + "." + appName;

        try
        {
            Subject subject = new Subject(loginName, null);
            ContentVersionId resolved = contentManager.resolve(externalId, subject);
            if (resolved != null)
            {
                contentManager.delete(resolved.getContentId(), resolved, subject);
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to delete user profile: " + e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * GET /configuration/widgets/{widgetName} — Widget configuration.
     */
    @GetMapping(value = "/widgets/{widgetName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get widget configuration")
    public ResponseEntity<?> getWidgetConfiguration(
            @PathVariable String widgetName,
            @RequestParam(required = false) String templateId)
    {
        // Try widget+template specific config first
        if (templateId != null && !templateId.isBlank())
        {
            String specificId = "configuration.widget." + widgetName + "." + templateId;
            Optional<Map<String, Object>> config = configurationService.getConfiguration(specificId);
            if (config.isPresent())
            {
                return ResponseEntity.ok(config.get());
            }
        }

        // Fall back to widget-only config
        String widgetId = "configuration.widget." + widgetName;
        Optional<Map<String, Object>> config = configurationService.getConfiguration(widgetId);
        if (config.isPresent())
        {
            return ResponseEntity.ok(config.get());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, "Widget configuration not found: " + widgetName));
    }

    /**
     * GET /configuration/templates/{templateId} — Template configuration.
     */
    @GetMapping(value = "/templates/{templateId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get template configuration")
    public ResponseEntity<?> getTemplateConfiguration(@PathVariable String templateId)
    {
        String configId = "atex.onecms.Template-" + templateId;
        Optional<Map<String, Object>> config = configurationService.getConfiguration(configId);
        if (config.isPresent())
        {
            return ResponseEntity.ok(config.get());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, "Template configuration not found: " + templateId));
    }

    /**
     * GET /configuration/system — System configuration (locale, timezone, etc.).
     */
    @GetMapping(value = "/system", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get system configuration")
    public ResponseEntity<?> getSystemConfiguration()
    {
        JsonObject json = new JsonObject();

        // Locale
        java.util.Locale locale = java.util.Locale.getDefault();
        JsonObject lc = new JsonObject();
        lc.addProperty("language", locale.getLanguage());
        lc.addProperty("country", locale.getCountry());
        lc.addProperty("variant", locale.getVariant());
        json.add("locale", lc);

        // Timezone
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        json.addProperty("timezone", tz.getID());

        return ResponseEntity.ok(GSON.toJson(json));
    }

    // --- Internal helpers ---

    /**
     * Resolve which config set to use based on user groups, user name, or default.
     */
    private String resolveConfigSetName(JsonObject profiles, String loginName)
    {
        String chosen = null;

        // Check groups
        if (loginName != null && profiles.has("groups") && profiles.get("groups").isJsonArray())
        {
            JsonArray groups = profiles.getAsJsonArray("groups");
            for (int i = 0; i < groups.size(); i++)
            {
                JsonElement elem = groups.get(i);
                if (!elem.isJsonObject()) continue;
                JsonObject item = elem.getAsJsonObject();
                if (item.has("name") && item.has("config"))
                {
                    String groupName = item.get("name").getAsString();
                    String config = item.get("config").getAsString();
                    if (!config.isBlank() && userIsMemberOfGroup(loginName, groupName))
                    {
                        chosen = config;
                    }
                }
            }
        }

        // Check users (overrides groups)
        if (loginName != null && profiles.has("users") && profiles.get("users").isJsonArray())
        {
            JsonArray users = profiles.getAsJsonArray("users");
            for (int i = 0; i < users.size(); i++)
            {
                JsonElement elem = users.get(i);
                if (!elem.isJsonObject()) continue;
                JsonObject item = elem.getAsJsonObject();
                if (item.has("name") && item.has("config"))
                {
                    String name = item.get("name").getAsString();
                    String config = item.get("config").getAsString();
                    if (!config.isBlank() && name.equals(loginName))
                    {
                        chosen = config;
                    }
                }
            }
        }

        // Default fallback
        if (chosen == null && profiles.has("default") && profiles.get("default").isJsonPrimitive())
        {
            chosen = profiles.getAsJsonPrimitive("default").getAsString();
        }

        return chosen;
    }

    /**
     * Parse configurations array and resolve config set with inheritance (extend).
     */
    private Map<String, String> resolveConfigurationMap(JsonArray configurations, String configSetName)
    {
        // Build map of id → ConfigSet
        Map<String, JsonObject> configSets = new LinkedHashMap<>();
        for (int i = 0; i < configurations.size(); i++)
        {
            JsonElement elem = configurations.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("id"))
            {
                configSets.put(obj.get("id").getAsString(), obj);
            }
        }

        // Resolve the target config set
        Map<String, String> result = new LinkedHashMap<>();
        JsonObject configSet = configSets.get(configSetName);
        if (configSet == null)
        {
            LOGGER.warning("Config set '" + configSetName + "' not found in configurations");
            return result;
        }

        // Collect config map from target + parents via extend chain
        java.util.Set<String> visited = new java.util.HashSet<>();
        JsonObject current = configSet;
        while (current != null)
        {
            String id = current.has("id") ? current.get("id").getAsString() : "";
            if (visited.contains(id)) break;
            visited.add(id);

            if (current.has("config") && current.get("config").isJsonObject())
            {
                JsonObject config = current.getAsJsonObject("config");
                for (Map.Entry<String, JsonElement> e : config.entrySet())
                {
                    if (!result.containsKey(e.getKey()) && e.getValue().isJsonPrimitive())
                    {
                        result.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }

            // Follow extend chain
            if (current.has("extend") && current.get("extend").isJsonPrimitive())
            {
                String parentId = current.get("extend").getAsString();
                current = configSets.get(parentId);
            }
            else
            {
                current = null;
            }
        }

        return result;
    }

    /**
     * Find a section config item by ID within the section's config array.
     *
     * Loads config from: override.{appName}.{sectionName} → {appName}.{sectionName}
     *                  → override.common.{sectionName} → common.{sectionName}
     */
    private JsonObject findSectionConfig(String appName, String sectionName, String sectionConfigId)
    {
        JsonArray sectionArray = loadSectionAsArray(appName, sectionName);
        if (sectionArray == null) return null;

        for (int i = 0; i < sectionArray.size(); i++)
        {
            JsonElement elem = sectionArray.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject item = elem.getAsJsonObject();
            if (!item.has("id") || !item.get("id").isJsonPrimitive()) continue;
            if (!item.has("config") || !item.get("config").isJsonObject()) continue;

            if (sectionConfigId.equals(item.get("id").getAsString()))
            {
                return item.get("config").getAsJsonObject();
            }
        }
        return null;
    }

    /**
     * Load a configuration section as a JsonObject, trying override/app/common fallbacks.
     */
    private JsonObject loadSectionAsObject(String appName, String sectionName)
    {
        JsonElement element = loadSection(appName, sectionName);
        if (element != null && element.isJsonObject())
        {
            return element.getAsJsonObject();
        }
        return null;
    }

    /**
     * Load a configuration section as a JsonArray, trying override/app/common fallbacks.
     */
    private JsonArray loadSectionAsArray(String appName, String sectionName)
    {
        JsonElement element = loadSection(appName, sectionName);
        if (element != null && element.isJsonArray())
        {
            return element.getAsJsonArray();
        }
        return null;
    }

    /**
     * Load a section from config, trying these external IDs in order:
     * 1. atex.configuration.{appName}.override.{sectionName}
     * 2. atex.configuration.{appName}.{sectionName}
     * 3. atex.configuration.common.override.{sectionName}
     * 4. atex.configuration.common.{sectionName}
     */
    private JsonElement loadSection(String appName, String sectionName)
    {
        String[] candidates = {
            CONFIG_PREFIX + appName + "." + CONFIG_OVERRIDE + "." + sectionName,
            CONFIG_PREFIX + appName + "." + sectionName,
            CONFIG_PREFIX + CONFIG_COMMON + "." + CONFIG_OVERRIDE + "." + sectionName,
            CONFIG_PREFIX + CONFIG_COMMON + "." + sectionName,
        };

        for (String externalId : candidates)
        {
            Optional<ConfigEntry> entry = configurationService.getEntry(externalId);
            if (entry.isPresent())
            {
                String jsonStr = GSON.toJson(entry.get().data());
                try
                {
                    JsonElement parsed = JsonParser.parseString(jsonStr);
                    if (parsed.isJsonObject())
                    {
                        JsonObject obj = parsed.getAsJsonObject();
                        // The config JSON wraps the section content under the section name key
                        if (obj.has(sectionName))
                        {
                            return obj.get(sectionName);
                        }
                        // Or it might be the section content directly
                        return obj;
                    }
                }
                catch (Exception e)
                {
                    LOGGER.log(Level.WARNING, "Failed to parse config " + externalId + ": " + e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Get the user's saved profile JSON for a given app.
     */
    private Optional<JsonElement> getUserProfileJson(String loginName, String appName)
    {
        String externalId = USER_PROFILE_PREFIX + loginName + "." + appName;
        try
        {
            Subject subject = new Subject(loginName, null);
            ContentVersionId resolved = contentManager.resolve(externalId, subject);
            if (resolved != null)
            {
                ContentResult<ConfigurationDataBean> result =
                        contentManager.get(resolved, ConfigurationDataBean.class, subject);
                if (result.getStatus().isSuccess())
                {
                    ConfigurationDataBean data = result.getContent().getContentData();
                    if (data != null && data.getJson() != null)
                    {
                        return Optional.of(JsonParser.parseString(data.getJson()));
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.FINE, "No user profile for " + externalId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Check if a user belongs to a group by name.
     */
    private boolean userIsMemberOfGroup(String loginName, String groupName)
    {
        try
        {
            var groups = groupRepository.findAll().stream()
                    .filter(g -> groupName.equals(g.getName()))
                    .toList();
            for (var group : groups)
            {
                var members = groupMemberRepository.findByGroupId(group.getGroupId());
                for (var member : members)
                {
                    if (loginName.equals(member.getPrincipalId()))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.FINE, "Error checking group membership: " + e.getMessage());
        }
        return false;
    }
}
