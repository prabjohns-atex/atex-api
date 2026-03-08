package com.atex.desk.api.controller;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentHistoryDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.desk.api.service.ContentService;
import com.atex.desk.api.site.SiteStructureService;
import com.atex.onecms.app.siteengine.SiteStructureBean;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.atex.desk.api.service.ConflictUpdateException;
import com.atex.onecms.content.callback.CallbackException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.atex.desk.api.auth.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.lang.Nullable;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/content")
@Tag(name = "Content")
public class ContentController
{
    private static final Logger LOG = Logger.getLogger(ContentController.class.getName());
    private static final String DEFAULT_USER = "system";
    private static final String STRUCTURE_VARIANT = "atex.onecms.structure";

    private final ContentService contentService;
    private final ContentManager contentManager;
    private final LocalContentManager localContentManager;
    private final SiteStructureService siteStructureService;
    private final ContentResolveController resolveController;

    public ContentController(ContentService contentService,
                              @Nullable ContentManager contentManager,
                              @Nullable LocalContentManager localContentManager,
                              SiteStructureService siteStructureService,
                              ContentResolveController resolveController)
    {
        this.contentService = contentService;
        this.contentManager = contentManager;
        this.localContentManager = localContentManager;
        this.siteStructureService = siteStructureService;
        this.resolveController = resolveController;
    }

    /**
     * GET /content/contentid/{id}
     * If unversioned → 303 redirect to versioned URL with JSON body.
     * If versioned → 200 with content.
     */
    @GetMapping("/contentid/{id}")
    @Operation(summary = "Read a content",
               description = "Versioned ID returns 200 with content and ETag. Unversioned ID returns 303 redirect to versioned URL.")
    @ApiResponse(responseCode = "200", description = "Content found (versioned ID)",
                 headers = @Header(name = "ETag", description = "The versioned content ID"),
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    @ApiResponse(responseCode = "303", description = "Redirect to versioned URL (unversioned ID)")
    @ApiResponse(responseCode = "404", description = "Content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getContent(
        @Parameter(description = "Content ID (versioned or unversioned)", example = "onecms:abc123")
        @PathVariable String id,
        @Parameter(description = "Variant name for content composition (e.g. atex.onecms.structure)")
        @RequestParam(value = "variant", required = false) String variant,
        @Parameter(description = "Comma-separated site IDs to exclude from structure variant")
        @RequestParam(value = "excludedSites", required = false) String excludedSites)
    {
        // Handle encoded external ID: "externalid/X" arrives via %2F decoding in {id}
        if (id.startsWith("externalid/")) {
            String extId = id.substring("externalid/".length());
            return resolveController.getContentByExternalId(extId);
        }

        // If variant is requested and ContentManager is available, use variant-aware path
        if (variant != null && !variant.isEmpty() && contentManager != null) {
            return getContentWithVariant(id, variant, excludedSites);
        }

        if (contentService.isVersionedId(id))
        {
            String[] parts = contentService.parseContentId(id);
            Optional<ContentResultDto> result = contentService.getContent(parts[0], parts[1], parts[2]);
            return result
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok()
                    .eTag(r.getVersion())
                    .body(r))
                .orElseGet(() -> notFound("Content not found"));
        }
        else
        {
            String[] parts = contentService.parseContentId(id);
            Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);

            // Fallback to alias resolution for unknown delegation IDs (e.g. policy:2.184)
            if (versionedId.isEmpty())
            {
                Optional<String> canonical = contentService.resolveWithFallback(id);
                if (canonical.isPresent())
                {
                    String[] canonParts = contentService.parseContentId(canonical.get());
                    versionedId = contentService.resolve(canonParts[0], canonParts[1]);
                }
            }

            return versionedId
                .<ResponseEntity<?>>map(this::redirect)
                .orElseGet(() -> notFound("Content not found"));
        }
    }

    /**
     * Fetch content via ContentManager with variant composition.
     * Handles standard (delegationId:key) and external ID formats.
     */
    private ResponseEntity<?> getContentWithVariant(String id, String variant, String excludedSites) {
        try {
            ContentVersionId vid = resolveToVersionId(id);
            if (vid == null) return notFound("Content not found");

            // Structure variant: build site tree directly from DTOs
            if (STRUCTURE_VARIANT.equals(variant)) {
                return buildStructureResponse(vid, excludedSites);
            }

            // Generic variant: use composer chain
            Map<String, Object> params = null;
            if (excludedSites != null) {
                params = Map.of("excludedSites", excludedSites);
            }
            ContentResult<Object> result = contentManager.get(
                vid, variant, Object.class, params, Subject.NOBODY_CALLER);
            if (result == null || !result.getStatus().isSuccess()) {
                return notFound("Content not found");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Variant fetch failed for id=" + id + " variant=" + variant, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compose variant: " + e.getMessage()));
        }
    }

    /**
     * Resolve an ID string to a ContentVersionId.
     * Supports versioned (a:b:c), unversioned (a:b), and external ID formats.
     */
    private ContentVersionId resolveToVersionId(String id) {
        if (contentService.isVersionedId(id)) {
            return IdUtil.fromVersionedString(id);
        }
        // Try standard delegationId:key format
        try {
            var cid = IdUtil.fromString(id);
            ContentVersionId vid = contentManager.resolve(cid, Subject.NOBODY_CALLER);
            if (vid != null) return vid;
        } catch (IllegalArgumentException ignored) {
            // Not standard format — fall through to external ID
        }
        // Resolve as external ID
        return contentManager.resolve(id, Subject.NOBODY_CALLER);
    }

    /**
     * Build the atex.onecms.structure variant response.
     * Delegates to SiteStructureService for tree building, wraps result in ContentResultDto.
     */
    private ResponseEntity<?> buildStructureResponse(ContentVersionId vid, String excludedSites) {
        SiteStructureBean structure = siteStructureService.getStructure(vid, excludedSites);
        if (structure == null) {
            return notFound("Content not found");
        }

        // Build ContentResultDto wrapping the structure as contentData
        String idStr = IdUtil.toIdString(vid.getContentId());
        String versionStr = IdUtil.toVersionedIdString(vid);

        ContentResultDto dto = new ContentResultDto();
        dto.setId(idStr);
        dto.setVersion(versionStr);

        // Fetch the original content to get aliases and other aspects
        Optional<ContentResultDto> original = contentService.getContent(
            vid.getDelegationId(), vid.getKey(), vid.getVersion());

        Map<String, AspectDto> aspects = new LinkedHashMap<>();
        if (original.isPresent()) {
            ContentResultDto orig = original.get();
            Map<String, AspectDto> origAspects = orig.getAspects();
            if (origAspects != null) {
                origAspects.forEach((k, v) -> {
                    if (!"contentData".equals(k)) aspects.put(k, v);
                });
            }
            // Synthesize atex.Aliases aspect from meta.aliases (mytype-new reads it here)
            if (orig.getMeta() != null && orig.getMeta().getAliases() != null
                    && !orig.getMeta().getAliases().isEmpty()) {
                AspectDto aliasesAspect = new AspectDto();
                aliasesAspect.setName("atex.Aliases");
                aliasesAspect.setData(Map.of("aliases", orig.getMeta().getAliases()));
                aspects.put("atex.Aliases", aliasesAspect);
            }
            dto.setMeta(orig.getMeta());
        }

        // Build contentData aspect with the structure bean
        AspectDto contentDataAspect = new AspectDto();
        contentDataAspect.setName("contentData");
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("_type", "com.atex.onecms.app.siteengine.PageBean");
        contentData.put("name", structure.getName());
        contentData.put("id", structure.getId());
        if (structure.getExternalId() != null) {
            contentData.put("externalId", structure.getExternalId());
        }
        if (structure.getPathSegment() != null) {
            contentData.put("pathSegment", structure.getPathSegment());
        }
        contentData.put("children", structure.getChildren());
        contentDataAspect.setData(contentData);
        aspects.put("contentData", contentDataAspect);

        dto.setAspects(aspects);
        return ResponseEntity.ok(dto);
    }

    /**
     * GET /content/contentid/{id}/history
     */
    @GetMapping("/contentid/{id}/history")
    @Operation(summary = "Read a content history",
               description = "Returns version history for an unversioned content ID")
    @ApiResponse(responseCode = "200", description = "History found",
                 content = @Content(schema = @Schema(implementation = ContentHistoryDto.class)))
    @ApiResponse(responseCode = "400", description = "Versioned ID not allowed",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getHistory(
        @Parameter(description = "Unversioned content ID", example = "onecms:abc123")
        @PathVariable String id)
    {
        if (contentService.isVersionedId(id))
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "History requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        Optional<ContentHistoryDto> history = contentService.getHistory(parts[0], parts[1]);

        // Fallback to alias resolution for unknown delegation IDs
        if (history.isEmpty())
        {
            Optional<String> canonical = contentService.resolveWithFallback(id);
            if (canonical.isPresent())
            {
                String[] canonParts = contentService.parseContentId(canonical.get());
                history = contentService.getHistory(canonParts[0], canonParts[1]);
            }
        }

        return history
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> notFound("Content not found"));
    }

    /**
     * POST /content
     * Create new content.
     */
    @PostMapping
    @Operation(summary = "Create a new content",
               description = "Create a new content entry with the provided aspects")
    @ApiResponse(responseCode = "201", description = "Content created",
                 headers = {
                     @Header(name = "Location", description = "URL of the created content"),
                     @Header(name = "ETag", description = "The versioned content ID")
                 },
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    public ResponseEntity<?> createContent(@RequestBody ContentWriteDto write,
                                            HttpServletRequest request)
    {
        String userId = resolveUserId(request);
        try {
            ContentResultDto result;
            if (localContentManager != null) {
                // Route through LocalContentManager to run pre-store hooks
                result = localContentManager.createContentFromDto(write, userId);
            } else {
                result = contentService.createContent(write, userId);
            }
            return ResponseEntity.created(URI.create("/content/contentid/" + result.getVersion()))
                .eTag(result.getVersion())
                .body(result);
        } catch (CallbackException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Pre-store hook failed: " + e.getMessage()));
        }
    }

    /**
     * PUT /content/contentid/{id}
     * Update existing content. Requires unversioned ID and If-Match header.
     */
    @PutMapping("/contentid/{id}")
    @Operation(summary = "Update a content",
               description = "Update an existing content by creating a new version. Requires If-Match header with current ETag.")
    @ApiResponse(responseCode = "200", description = "Content updated",
                 headers = @Header(name = "ETag", description = "The new versioned content ID"),
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid If-Match header",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "409", description = "If-Match header does not match current version",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> updateContent(
        @Parameter(description = "Unversioned content ID") @PathVariable String id,
        @RequestBody ContentWriteDto write,
        HttpServletRequest request,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch)
    {
        if (contentService.isVersionedId(id))
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "Update requires an unversioned content ID"));
        }

        // Enforce If-Match header
        if (ifMatch == null || ifMatch.isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST,
                    "Tried to update content with an If-Match header not corresponding to this content."));
        }

        String[] parts = contentService.parseContentId(id);
        String delegationId = parts[0];
        String key = parts[1];

        // Fallback to alias resolution for unknown delegation IDs
        if (contentService.resolveIdType(delegationId) == null)
        {
            Optional<String> canonical = contentService.resolveWithFallback(id);
            if (canonical.isPresent())
            {
                String[] canonParts = contentService.parseContentId(canonical.get());
                delegationId = canonParts[0];
                key = canonParts[1];
            }
        }

        String strippedIfMatch = stripETagQuotes(ifMatch);
        String userId = resolveUserId(request);
        try {
            Optional<ContentResultDto> result;
            if (localContentManager != null) {
                // Route through LocalContentManager to run pre-store hooks
                result = localContentManager.updateContentFromDto(
                    delegationId, key, write, userId, strippedIfMatch);
            } else {
                result = contentService.updateContent(
                    delegationId, key, write, userId, strippedIfMatch);
            }
            return result
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok()
                    .eTag(r.getVersion())
                    .location(URI.create("/content/contentid/" + r.getVersion()))
                    .body(r))
                .orElseGet(() -> notFound("Content not found"));
        } catch (ConflictUpdateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto(HttpStatus.CONFLICT,
                    "Tried to update content with an If-Match header not corresponding to this content."));
        } catch (CallbackException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Pre-store hook failed: " + e.getMessage()));
        }
    }

    /**
     * DELETE /content/contentid/{id}
     * Requires If-Match header.
     */
    @DeleteMapping("/contentid/{id}")
    @Operation(summary = "Delete a content",
               description = "Soft delete a content by reassigning its view to p.deleted. Requires If-Match header.")
    @ApiResponse(responseCode = "204", description = "Content deleted")
    @ApiResponse(responseCode = "400", description = "Missing If-Match header",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> deleteContent(
        @Parameter(description = "Unversioned content ID") @PathVariable String id,
        HttpServletRequest request,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch)
    {
        if (contentService.isVersionedId(id))
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "Delete requires an unversioned content ID"));
        }

        // Enforce If-Match header
        if (ifMatch == null || ifMatch.isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST,
                    "Deleting requires an If-Match header"));
        }

        String[] parts = contentService.parseContentId(id);
        String delegationId = parts[0];
        String key = parts[1];

        // Fallback to alias resolution for unknown delegation IDs
        if (contentService.resolveIdType(delegationId) == null)
        {
            Optional<String> canonical = contentService.resolveWithFallback(id);
            if (canonical.isPresent())
            {
                String[] canonParts = contentService.parseContentId(canonical.get());
                delegationId = canonParts[0];
                key = canonParts[1];
            }
        }

        // Validate If-Match against current version at service layer
        String strippedIfMatch = stripETagQuotes(ifMatch);
        Optional<String> currentVersion = contentService.getCurrentVersion(delegationId, key);
        if (currentVersion.isEmpty())
        {
            return notFound("Content not found");
        }
        if (!currentVersion.get().equals(strippedIfMatch))
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST,
                    "Deleting requires an If-Match header"));
        }

        String userId = resolveUserId(request);
        boolean deleted = contentService.deleteContent(delegationId, key, userId);
        if (deleted)
        {
            return ResponseEntity.noContent().build();
        }
        return notFound("Content not found");
    }

    /**
     * DELETE /content/contentid/{id}?purge=true&version={version}
     * Permanently removes a specific version (hard delete).
     */
    @DeleteMapping("/contentid/{id}/version/{version}")
    @Operation(summary = "Purge a content version",
               description = "Permanently remove a specific version of content. If it's the last version, the content ID is also removed.")
    @ApiResponse(responseCode = "204", description = "Version purged")
    @ApiResponse(responseCode = "404", description = "Version not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> purgeVersion(
        @Parameter(description = "Unversioned content ID") @PathVariable String id,
        @Parameter(description = "Version string to purge") @PathVariable String version)
    {
        if (contentService.isVersionedId(id))
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "Purge requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        boolean purged = contentService.purgeVersion(parts[0], parts[1], version);
        if (purged)
        {
            return ResponseEntity.noContent().build();
        }
        return notFound("Version not found");
    }

    // --- Helpers ---

    private String resolveUserId(HttpServletRequest request)
    {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        return user != null ? user.toString() : DEFAULT_USER;
    }

    private ResponseEntity<ErrorResponseDto> notFound(String message)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, message));
    }

    /**
     * Build a 303 See Other redirect response with JSON body matching reference OneCMS format.
     */
    private ResponseEntity<?> redirect(String versionedId)
    {
        String location = "/content/contentid/" + versionedId;
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .location(URI.create(location))
            .body(Map.of("statusCode", "30300",
                          "message", "Symbolic version resolved",
                          "location", location));
    }

    /**
     * Strip surrounding quotes from ETag/If-Match header value.
     * Reference OneCMS expects/sends quoted ETags: "onecms:id:version"
     */
    private static String stripETagQuotes(String etag)
    {
        if (etag != null && etag.startsWith("\"") && etag.endsWith("\""))
        {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }
}
