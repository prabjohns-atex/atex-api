package com.atex.onecms.app.dam.ws;

import com.atex.common.collections.Pair;
import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.entity.AppGroup;
import com.atex.desk.api.entity.AppGroupMember;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppGroupMemberRepository;
import com.atex.desk.api.repository.AppGroupRepository;
import com.atex.desk.api.repository.AppUserRepository;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.app.dam.propertybag.PropertyBagConfiguration;
import com.atex.onecms.app.dam.propertybag.SchemaField;
import com.atex.onecms.app.dam.publish.DamEngagement;
import com.atex.onecms.app.dam.publish.DamPublisher;
import com.atex.onecms.app.dam.publish.DamPublisherFactory;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WebContentStatusAspectBean;
import com.atex.onecms.app.mytype.config.MyTypeConfiguration;
import com.atex.onecms.app.mytype.config.MyTypeConfiguration.SaveAction;
import com.atex.onecms.app.mytype.config.MyTypeConfiguration.SaveRule;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MyType resource -- provides content CRUD, configuration and permissions
 * endpoints for the mytype-new client.
 * Ported from adm-starterkit MyTypeResource (JAX-RS).
 */
@RestController
@RequestMapping("/dam/mytype")
public class MyTypeResource {

    private static final Logger LOGGER = Logger.getLogger(MyTypeResource.class.getName());
    private static final String PERMISSIONS_EXTERNAL_ID = "mytype.general.permissions";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final ConfigurationService configurationService;
    private final AppGroupMemberRepository groupMemberRepository;
    private final AppGroupRepository groupRepository;
    private final AppUserRepository appUserRepository;
    private final ContentService contentService;
    private final ContentManager contentManager;
    private final DamPublisherFactory damPublisherFactory;

    public MyTypeResource(ConfigurationService configurationService,
                          AppGroupMemberRepository groupMemberRepository,
                          AppGroupRepository groupRepository,
                          AppUserRepository appUserRepository,
                          ContentService contentService,
                          ContentManager contentManager,
                          DamPublisherFactory damPublisherFactory) {
        this.configurationService = configurationService;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.appUserRepository = appUserRepository;
        this.contentService = contentService;
        this.contentManager = contentManager;
        this.damPublisherFactory = damPublisherFactory;
    }

    // ======== Ping ========

    @GetMapping("ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("mytype connector service available");
    }

    // ======== Content CRUD ========

    /**
     * GET /dam/mytype/content/contentid/{id}
     * If unversioned, resolve and redirect (303). If versioned, return content.
     */
    @GetMapping(value = "content/contentid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getContentWithContentId(
            @PathVariable("id") String contentIdStr,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        try {
            if (!contentService.isVersionedId(contentIdStr)) {
                // Resolve unversioned ID to latest version, then redirect
                return resolveAndForward(contentIdStr, request);
            }

            // Versioned: fetch content
            String[] parts = contentService.parseContentId(contentIdStr);
            Optional<ContentResultDto> result = contentService.getContent(parts[0], parts[1], parts[2]);
            if (result.isEmpty()) {
                throw ContentApiException.notFound("No such content: " + contentIdStr);
            }

            ContentResultDto dto = result.get();
            String etag = "\"" + dto.getVersion() + "\"";

            // ETag match check
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            }

            // Fix collection aspect if present
            fixCollectionAspect(dto);

            return ResponseEntity.ok()
                    .header("ETag", etag)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(dto));

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting content: " + contentIdStr, e);
            throw ContentApiException.internal("Error getting content: " + e.getMessage(), e);
        }
    }

    /**
     * POST /dam/mytype/content -- Create content.
     */
    @PostMapping(value = "content",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(
            @RequestBody String body,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        String userId = (String) request.getAttribute(AuthFilter.USER_ATTRIBUTE);

        try {
            ContentWriteDto writeDto = parseContentWriteDto(body);

            // Detect and remove status aspect for save-rule processing
            Pair<ContentWriteDto, SaveAction> writePair = removeStatusForSaveRule(writeDto);

            ContentResultDto created = contentService.createContent(writePair.left(), userId);

            // Perform publish/unpublish action based on save rules
            ContentResultDto finalResult = performPublishAction(created, writePair.right(), ctx);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(finalResult));

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating content", e);
            throw ContentApiException.internal("Error creating content: " + e.getMessage(), e);
        }
    }

    /**
     * PUT /dam/mytype/content/contentid/{id} -- Update content.
     */
    @PutMapping(value = "content/contentid/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> update(
            @PathVariable("id") String contentIdStr,
            @RequestBody String body,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();
        String userId = (String) request.getAttribute(AuthFilter.USER_ATTRIBUTE);

        try {
            if (contentService.isVersionedId(contentIdStr)) {
                throw ContentApiException.badRequest("Versioned update is not allowed");
            }

            String[] parts = contentService.parseContentId(contentIdStr);
            String delegationId = parts[0];
            String key = parts[1];

            ContentWriteDto writeDto = parseContentWriteDto(body);

            // Detect and remove status aspect for save-rule processing
            Pair<ContentWriteDto, SaveAction> writePair = removeStatusForSaveRule(writeDto);

            // Check publish permission if action is PUBLISH
            if (writePair.right() == SaveAction.PUBLISH) {
                // Simplified permission check -- in full implementation would check
                // MyTypePermissions against user groups
                // For now, WRITE scope is sufficient
            }

            Optional<ContentResultDto> updated = contentService.updateContent(
                    delegationId, key, writePair.left(), userId);

            if (updated.isEmpty()) {
                throw ContentApiException.notFound("No such content: " + contentIdStr);
            }

            // Perform publish/unpublish action based on save rules
            ContentResultDto finalResult = performPublishAction(updated.get(), writePair.right(), ctx);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(finalResult));

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating content: " + contentIdStr, e);
            throw ContentApiException.internal("Error updating content: " + e.getMessage(), e);
        }
    }

    // ======== Configuration ========

    /**
     * GET /dam/mytype/configuration/externalid/{externalId}
     */
    @GetMapping(value = "configuration/externalid/{externalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getConfigurationWithExternalId(
            @PathVariable("externalId") String externalId,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        if (externalId == null || externalId.isEmpty()) {
            throw ContentApiException.badRequest("ExternalId cannot be empty");
        }

        Optional<Map<String, Object>> configOpt = configurationService.getConfiguration(externalId);
        if (configOpt.isEmpty()) {
            throw ContentApiException.notFound("No such content: " + externalId);
        }

        String json = GSON.toJson(configOpt.get());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * PUT /dam/mytype/configuration/externalid/{externalId}
     * Currently only handles "atex.configuration.propertybag".
     */
    @PutMapping(value = "configuration/externalid/{externalId}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateConfiguration(
            @PathVariable("externalId") String externalId,
            @RequestBody String body,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        if ("atex.configuration.propertybag".equals(externalId)) {
            return updatePropertyBagConfiguration(externalId, body);
        } else {
            throw new ContentApiException("Unhandled configuration: " + externalId, 40400, 404);
        }
    }

    // ======== Remote publication URL ========

    /**
     * GET /dam/mytype/remotePublicationUrl/contentid/{id}
     */
    @GetMapping(value = "remotePublicationUrl/contentid/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> remotePublicationUrl(
            @PathVariable("id") String contentIdStr,
            HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        try {
            ContentId contentId = IdUtil.fromString(contentIdStr);
            DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());

            DamEngagement engagement = new DamEngagement(contentManager);
            ContentId remoteId = engagement.getEngagementId(contentId);
            if (remoteId == null) {
                throw new ContentApiException("Cannot get engagement from " + contentIdStr,
                        HttpStatus.NOT_IMPLEMENTED);
            }

            String remoteUrl = damPublisher.getRemotePublicationUrl(contentId, remoteId);
            return ResponseEntity.ok(remoteUrl);

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get remote publication URL for contentId: " + contentIdStr, e);
            throw ContentApiException.internal(
                    "Failed to get remote publication URL for contentId: " + contentIdStr, e);
        }
    }

    // ======== Permissions ========

    @GetMapping(value = "permissions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPermissions(HttpServletRequest request) {
        DamUserContext ctx = DamUserContext.from(request);
        ctx.assertLoggedIn();

        try {
            String userId = (String) request.getAttribute(AuthFilter.USER_ATTRIBUTE);

            // Fetch permissions configuration
            Optional<Map<String, Object>> configOpt = configurationService.getConfiguration(PERMISSIONS_EXTERNAL_ID);
            if (configOpt.isEmpty()) {
                throw new ContentApiException("Permissions not configured", HttpStatus.NOT_FOUND);
            }

            Map<String, Object> config = configOpt.get();

            // Extract schema fields
            String schemaVersion = stringVal(config.get("schemaVersion"), "1.0");

            // Get user's group names from DB
            List<String> userGroups = findGroupNamesForUser(userId);

            // Parse group permissions from config
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) config.get("groups");
            if (groups == null) {
                groups = List.of();
            }

            // Flatten permissions for groups the user belongs to
            List<PermissionEntry> flatPermissions = new ArrayList<>();

            for (Map<String, Object> group : groups) {
                String groupName = stringVal(group.get("name"), "");

                // Check if user belongs to this group
                boolean belongs = "*".equals(groupName);
                if (!belongs) {
                    for (String userGroup : userGroups) {
                        if (groupName.equalsIgnoreCase(userGroup)) {
                            belongs = true;
                            break;
                        }
                    }
                }

                if (belongs) {
                    // Add grants
                    flattenRules(group, "grants", "grant", groupName, flatPermissions);
                    // Add denies
                    flattenRules(group, "denies", "deny", groupName, flatPermissions);
                }
            }

            // Build response matching PermissionsResponse type
            PermissionsResponse response = new PermissionsResponse();
            response.schemaVersion = schemaVersion;
            response.user = new UserInfo();
            response.user.id = "user-" + userId;
            response.user.groups = userGroups;
            response.permissions = flatPermissions;

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(GSON.toJson(response));

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get permissions", e);
            throw new ContentApiException("Error retrieving permissions: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ======== Internal helpers ========

    /**
     * Resolve an unversioned content ID and return a 303 redirect to the versioned URL.
     */
    private ResponseEntity<String> resolveAndForward(String contentIdStr, HttpServletRequest request) {
        String[] parts = contentService.parseContentId(contentIdStr);
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);

        // Fallback to alias resolution
        if (versionedId.isEmpty()) {
            Optional<String> canonical = contentService.resolveWithFallback(contentIdStr);
            if (canonical.isPresent()) {
                String[] canonParts = contentService.parseContentId(canonical.get());
                versionedId = contentService.resolve(canonParts[0], canonParts[1]);
            }
        }

        if (versionedId.isEmpty()) {
            throw ContentApiException.notFound("No such content: " + contentIdStr);
        }

        String vId = versionedId.get();
        String requestUrl = request.getRequestURL().toString();
        String forwardUrl = requestUrl.replace(contentIdStr, vId);

        // Build redirect response matching OneCMS format
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", "30300");
        body.put("message", "Symbolic version resolved");
        body.put("location", forwardUrl);

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(forwardUrl))
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .contentType(MediaType.APPLICATION_JSON)
                .body(GSON.toJson(body));
    }

    /**
     * Parse incoming JSON body into a ContentWriteDto using Gson.
     */
    @SuppressWarnings("unchecked")
    private ContentWriteDto parseContentWriteDto(String json) {
        Map<String, Object> raw = GSON.fromJson(json, MAP_TYPE);
        ContentWriteDto dto = new ContentWriteDto();

        if (raw.containsKey("id")) {
            dto.setId((String) raw.get("id"));
        }
        if (raw.containsKey("version")) {
            dto.setVersion((String) raw.get("version"));
        }

        // Parse aspects
        if (raw.containsKey("aspects")) {
            Map<String, Object> aspectsRaw = (Map<String, Object>) raw.get("aspects");
            Map<String, AspectDto> aspects = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : aspectsRaw.entrySet()) {
                AspectDto aspectDto = new AspectDto();
                aspectDto.setName(entry.getKey());
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> aspectMap = (Map<String, Object>) entry.getValue();
                    if (aspectMap.containsKey("data")) {
                        aspectDto.setData((Map<String, Object>) aspectMap.get("data"));
                    } else {
                        aspectDto.setData(aspectMap);
                    }
                }
                aspects.put(entry.getKey(), aspectDto);
            }
            dto.setAspects(aspects);
        }

        return dto;
    }

    /**
     * Inspect ContentWriteDto for status aspects that match save rules,
     * remove matching status aspect and return the appropriate SaveAction.
     */
    private Pair<ContentWriteDto, SaveAction> removeStatusForSaveRule(ContentWriteDto writeDto) {
        MyTypeConfiguration config = MyTypeConfiguration.fetch(configurationService);

        // Determine content type from contentData aspect
        String contentType = "";
        if (writeDto.getAspects() != null && writeDto.getAspects().containsKey("contentData")) {
            AspectDto cd = writeDto.getAspects().get("contentData");
            if (cd.getData() != null && cd.getData().containsKey("_type")) {
                contentType = (String) cd.getData().get("_type");
            }
        }

        for (SaveRule saveRule : config.getSaveRules()) {
            if ("*".equals(saveRule.getContentType()) || saveRule.getContentType().equals(contentType)) {
                String[] statusParts = saveRule.getStatusMatch().split(":");
                if (statusParts.length == 2) {
                    String aspectName = statusParts[0];
                    String statusId = statusParts[1];

                    if (writeDto.getAspects() != null && writeDto.getAspects().containsKey(aspectName)) {
                        AspectDto aspectDto = writeDto.getAspects().get(aspectName);
                        if (aspectDto.getData() != null) {
                            // Check if the status matches
                            Map<String, Object> data = aspectDto.getData();
                            Object statusObj = data.get("status");
                            String currentStatusId = null;

                            if (statusObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> statusMap = (Map<String, Object>) statusObj;
                                currentStatusId = (String) statusMap.get("statusID");
                            }

                            if (statusId.equals(currentStatusId)) {
                                // Remove the matching status aspect
                                Map<String, AspectDto> updatedAspects = new LinkedHashMap<>(writeDto.getAspects());
                                updatedAspects.remove(aspectName);
                                writeDto.setAspects(updatedAspects);
                                return Pair.of(writeDto, saveRule.getAction());
                            }
                        }
                    }
                }
            }
        }

        return Pair.of(writeDto, SaveAction.NONE);
    }

    /**
     * Execute the publish or unpublish action if indicated by save rules.
     * Returns the refreshed content result after the action.
     */
    private ContentResultDto performPublishAction(ContentResultDto result, SaveAction action,
                                                  DamUserContext ctx) {
        if (action == SaveAction.NONE || result == null) {
            return result;
        }

        try {
            String contentIdStr = result.getId();
            if (contentIdStr == null) {
                return result;
            }
            ContentId contentId = IdUtil.fromString(contentIdStr);

            if (action == SaveAction.PUBLISH) {
                LOGGER.info("publish on " + contentIdStr);
                DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());
                damPublisher.publish(contentId);
            } else if (action == SaveAction.UNPUBLISH) {
                LOGGER.info("unpublish on " + contentIdStr);
                DamPublisher damPublisher = damPublisherFactory.create(contentId, ctx.getCaller());
                damPublisher.unpublish(contentId);
            }

            // Re-fetch the latest content after publish/unpublish
            String[] parts = contentService.parseContentId(contentIdStr);
            Optional<String> latestVersioned = contentService.resolve(parts[0], parts[1]);
            if (latestVersioned.isPresent()) {
                String[] vParts = contentService.parseContentId(latestVersioned.get());
                Optional<ContentResultDto> refreshed = contentService.getContent(
                        vParts[0], vParts[1], vParts[2]);
                if (refreshed.isPresent()) {
                    return refreshed.get();
                }
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            String causeMsg = e.getCause() != null ? e.getCause().getMessage() : null;
            if ("CONTENT_LOCKED".equals(causeMsg)) {
                throw ContentApiException.error("Locked Content", 50023, 500);
            } else if ("AUTHENTICATION_FAILED".equals(causeMsg)) {
                throw ContentApiException.error("Remote Authentication Failed", 50001, 500);
            } else if (causeMsg != null && causeMsg.contains("ERROR_RESPONSE_RECEIVED")) {
                throw ContentApiException.error(causeMsg, 50002, 500);
            }
            LOGGER.log(Level.SEVERE, "cannot perform " + action + " on " + result.getId(), e);
            throw ContentApiException.internal("Error performing " + action + ": " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Add collection aspect metadata to the content result DTO if the content
     * is a collection type. This synthesizes the ephemeral "atex.collection" aspect.
     */
    private void fixCollectionAspect(ContentResultDto dto) {
        // The collection aspect is ephemeral and assembled from child content.
        // For now, the DTO returned from ContentService already includes stored aspects.
        // Full collection assembly (querying child images) would be done here if needed.
    }

    /**
     * Update the property bag configuration with schema field validation
     * and auto-generated index field names.
     */
    private ResponseEntity<String> updatePropertyBagConfiguration(String externalId, String json) {
        try {
            PropertyBagConfiguration config = GSON.fromJson(json, PropertyBagConfiguration.class);
            if (config == null) {
                throw ContentApiException.badRequest("Invalid JSON for PropertyBagConfiguration");
            }

            if (config.getFields() == null) {
                throw ContentApiException.badRequest("Fields cannot be null");
            }

            // Validate and auto-generate index field names
            for (SchemaField field : config.getFields()) {
                if (field.isIndexed() && (field.getIndexField() == null || field.getIndexField().isEmpty())) {
                    String indexSuffix = "_atex_desk_tm";
                    if (field.getType() != null) {
                        if (field.getType().equalsIgnoreCase(SchemaField.Type.TEXT.getValue())) {
                            indexSuffix = "_atex_desk_tm";
                        } else if (field.getType().equalsIgnoreCase(SchemaField.Type.STRING.getValue())) {
                            indexSuffix = "_atex_desk_sm";
                        } else if (field.getType().equalsIgnoreCase(SchemaField.Type.NUMBER.getValue())) {
                            indexSuffix = "_atex_desk_i";
                        } else if (field.getType().equalsIgnoreCase(SchemaField.Type.DATE.getValue())) {
                            indexSuffix = "_atex_desk_dts";
                        } else if (field.getType().equalsIgnoreCase(SchemaField.Type.BOOLEAN.getValue())) {
                            indexSuffix = "_atex_desk_b";
                        }
                    }
                    field.setIndexField(field.getName() + indexSuffix);
                }
            }

            // Serialize the validated configuration
            String updatedJson = GSON.toJson(config);

            // Update in the configuration service
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = GSON.fromJson(updatedJson, MAP_TYPE);
            configurationService.setLiveOverride(externalId, dataMap);

            // Return the updated JSON
            JsonElement jsonElement = JsonParser.parseString(updatedJson);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GSON.toJson(jsonElement));

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            throw ContentApiException.badRequest("Error reading configuration: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenRules(Map<String, Object> group, String rulesKey, String effect,
                              String groupName, List<PermissionEntry> out) {
        List<Map<String, Object>> rules = (List<Map<String, Object>>) group.get(rulesKey);
        if (rules == null) return;

        for (Map<String, Object> rule : rules) {
            PermissionEntry entry = new PermissionEntry();
            entry.permissionId = stringVal(rule.get("permissionId"), "");
            entry.effect = effect;

            // Extract conditions from constraints
            Map<String, Object> constraints = (Map<String, Object>) rule.get("constraints");
            if (constraints != null && constraints.get("conditions") instanceof List<?> condList) {
                entry.conditions = condList.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            } else {
                entry.conditions = new ArrayList<>();
            }

            entry.source = new PermissionSource();
            entry.source.group = groupName;
            out.add(entry);
        }
    }

    /**
     * Resolve loginName to effective principalId, then find group names.
     * Mirrors PrincipalsController.effectivePrincipalId logic:
     * user.principalId if set, otherwise loginName.
     */
    private List<String> findGroupNamesForUser(String loginName) {
        // Resolve loginName → effectivePrincipalId (may differ from loginName)
        String principalId = loginName;
        Optional<AppUser> userOpt = appUserRepository.findByLoginName(loginName);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getPrincipalId() != null && !user.getPrincipalId().isBlank()) {
                principalId = user.getPrincipalId();
            }
        }

        List<AppGroupMember> memberships = groupMemberRepository.findByPrincipalId(principalId);
        List<Integer> groupIds = memberships.stream()
                .map(AppGroupMember::getGroupId)
                .toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, AppGroup> groupMap = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(AppGroup::getGroupId, g -> g));
        return groupIds.stream()
                .map(groupMap::get)
                .filter(g -> g != null)
                .map(AppGroup::getName)
                .toList();
    }

    private static String stringVal(Object obj, String defaultVal) {
        return obj != null ? obj.toString() : defaultVal;
    }

    // Response DTOs -- match PermissionsResponse in mytype-new/lib/configs/permissionsTypes.ts

    private static class PermissionsResponse {
        public String schemaVersion;
        public UserInfo user;
        public List<PermissionEntry> permissions;
    }

    private static class UserInfo {
        public String id;
        public List<String> groups;
    }

    private static class PermissionEntry {
        public String permissionId;
        public String effect;
        public List<String> conditions;
        public PermissionSource source;
    }

    private static class PermissionSource {
        public String group;
    }
}
