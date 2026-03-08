package com.atex.desk.api.controller;

import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Handles content resolution by external ID and view-based lookups.
 * Extracted from ContentController to keep it focused on core CRUD.
 */
@RestController
@RequestMapping("/content")
@Tag(name = "Content")
public class ContentResolveController {

    private final ContentService contentService;
    private final ConfigurationService configurationService;

    public ContentResolveController(ContentService contentService,
                                     ConfigurationService configurationService) {
        this.contentService = contentService;
        this.configurationService = configurationService;
    }

    /**
     * GET /content/contentid/externalid/{externalId}
     * Handles "externalid/X" content ID format used by OneCMS clients.
     * Tomcat decodes %2F to / (via EncodedSolidusHandling.DECODE in WebConfig),
     * so this endpoint catches the resulting two-segment path.
     */
    @GetMapping("/contentid/externalid/{externalId}")
    public ResponseEntity<?> getContentByExternalIdViaContentId(
        @PathVariable String externalId)
    {
        return getContentByExternalId(externalId);
    }

    /**
     * GET /content/externalid/{id}
     * Resolves external ID and redirects to versioned content URL.
     */
    @GetMapping("/externalid/{id}")
    @Operation(summary = "Resolve an externalId and redirect",
               description = "Resolves an external ID to a content ID and redirects to the versioned content URL")
    @ApiResponse(responseCode = "200", description = "Configuration content found (for config external IDs)",
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    @ApiResponse(responseCode = "303", description = "Redirect to versioned content URL")
    @ApiResponse(responseCode = "404", description = "External ID not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getContentByExternalId(
        @Parameter(description = "External content ID") @PathVariable String id)
    {
        Optional<String> contentId = contentService.resolveExternalId(id);
        if (contentId.isEmpty())
        {
            // Fall back to resource-based configuration
            if (configurationService.isConfigId(id))
            {
                ContentResultDto configResult = configurationService.toContentResultDto(id);
                if (configResult != null)
                {
                    return ResponseEntity.ok()
                        .eTag(configResult.getVersion())
                        .body(configResult);
                }
            }
            return notFound("External ID not found: " + id);
        }

        String[] parts = contentService.parseContentId(contentId.get());
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);
        return versionedId
            .<ResponseEntity<?>>map(this::redirect)
            .orElseGet(() -> notFound("Content not found for external ID: " + id));
    }

    /**
     * GET /content/externalid/{id}/history
     */
    @GetMapping("/externalid/{id}/history")
    @Operation(summary = "Read a content history by external ID",
               description = "Resolves the external ID and redirects to the content history endpoint")
    @ApiResponse(responseCode = "303", description = "Redirect to content history URL")
    @ApiResponse(responseCode = "404", description = "External ID not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getHistoryByExternalId(
        @Parameter(description = "External content ID") @PathVariable String id)
    {
        Optional<String> contentId = contentService.resolveExternalId(id);
        if (contentId.isEmpty())
        {
            return notFound("External ID not found: " + id);
        }

        String location = "/content/contentid/" + contentId.get() + "/history";
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .location(URI.create(location))
            .body(Map.of("statusCode", "30300",
                          "message", "Symbolic version resolved",
                          "location", location));
    }

    /**
     * GET /content/view/{view}/contentid/{id}
     * Resolve content ID in a specific view and redirect to versioned URL.
     */
    @GetMapping("/view/{view}/contentid/{id}")
    @Operation(summary = "Read a content from a view",
               description = "Resolve a content ID in a specific view and redirect to the versioned URL")
    @ApiResponse(responseCode = "303", description = "Redirect to versioned content URL")
    @ApiResponse(responseCode = "400", description = "Versioned ID not allowed",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Content not found in view",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getContentFromView(
        @Parameter(description = "View name", example = "p.latest") @PathVariable String view,
        @Parameter(description = "Unversioned content ID") @PathVariable String id)
    {
        if (contentService.isVersionedId(id))
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "View lookup requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1], view);
        return versionedId
            .<ResponseEntity<?>>map(this::redirect)
            .orElseGet(() -> notFound("Content not found in view: " + view));
    }

    /**
     * GET /content/view/{view}/externalid/{id}
     */
    @GetMapping("/view/{view}/externalid/{id}")
    @Operation(summary = "Read a content from a view by external ID",
               description = "Resolve an external ID in a specific view and redirect to the versioned URL")
    @ApiResponse(responseCode = "303", description = "Redirect to versioned content URL")
    @ApiResponse(responseCode = "404", description = "External ID or content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getContentFromViewByExternalId(
        @Parameter(description = "View name", example = "p.latest") @PathVariable String view,
        @Parameter(description = "External content ID") @PathVariable String id)
    {
        Optional<String> contentId = contentService.resolveExternalId(id);
        if (contentId.isEmpty())
        {
            return notFound("External ID not found: " + id);
        }

        String[] parts = contentService.parseContentId(contentId.get());
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1], view);
        return versionedId
            .<ResponseEntity<?>>map(this::redirect)
            .orElseGet(() -> notFound("Content not found in view: " + view));
    }

    // --- Helpers ---

    private ResponseEntity<ErrorResponseDto> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, message));
    }

    private ResponseEntity<?> redirect(String versionedId) {
        String location = "/content/contentid/" + versionedId;
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
            .location(URI.create(location))
            .body(Map.of("statusCode", "30300",
                          "message", "Symbolic version resolved",
                          "location", location));
    }
}
