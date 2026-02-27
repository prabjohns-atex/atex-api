package com.atex.desk.api.controller;

import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.ContentHistoryDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/content")
@Tag(name = "Content")
public class ContentController
{
    private static final String DEFAULT_USER = "system";

    private final ContentService contentService;
    private final ConfigurationService configurationService;

    public ContentController(ContentService contentService,
                              ConfigurationService configurationService)
    {
        this.contentService = contentService;
        this.configurationService = configurationService;
    }

    /**
     * GET /content/contentid/{id}
     * If unversioned → 302 redirect to versioned URL.
     * If versioned → 200 with content.
     */
    @GetMapping("/contentid/{id}")
    @Operation(summary = "Read a content",
               description = "Versioned ID returns 200 with content and ETag. Unversioned ID returns 302 redirect to versioned URL.")
    @ApiResponse(responseCode = "200", description = "Content found (versioned ID)",
                 headers = @Header(name = "ETag", description = "The versioned content ID"),
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    @ApiResponse(responseCode = "302", description = "Redirect to versioned URL (unversioned ID)")
    @ApiResponse(responseCode = "404", description = "Content not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getContent(
        @Parameter(description = "Content ID (versioned or unversioned)", example = "onecms:abc123")
        @PathVariable String id)
    {
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
            return versionedId
                .<ResponseEntity<?>>map(vid -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/content/contentid/" + vid))
                    .build())
                .orElseGet(() -> notFound("Content not found"));
        }
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
                .body(new ErrorResponseDto("VERSIONED_ID", "History requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        Optional<ContentHistoryDto> history = contentService.getHistory(parts[0], parts[1]);
        return history
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> notFound("Content not found"));
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
    @ApiResponse(responseCode = "302", description = "Redirect to versioned content URL")
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
            .<ResponseEntity<?>>map(vid -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/content/contentid/" + vid))
                .build())
            .orElseGet(() -> notFound("Content not found for external ID: " + id));
    }

    /**
     * GET /content/externalid/{id}/history
     */
    @GetMapping("/externalid/{id}/history")
    @Operation(summary = "Read a content history by external ID",
               description = "Resolves the external ID and redirects to the content history endpoint")
    @ApiResponse(responseCode = "302", description = "Redirect to content history URL")
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

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/content/contentid/" + contentId.get() + "/history"))
            .build();
    }

    /**
     * GET /content/view/{view}/contentid/{id}
     * Resolve content ID in a specific view and redirect to versioned URL.
     */
    @GetMapping("/view/{view}/contentid/{id}")
    @Operation(summary = "Read a content from a view",
               description = "Resolve a content ID in a specific view and redirect to the versioned URL")
    @ApiResponse(responseCode = "302", description = "Redirect to versioned content URL")
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
                .body(new ErrorResponseDto("VERSIONED_ID", "View lookup requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        Optional<String> versionedId = contentService.resolve(parts[0], parts[1], view);
        return versionedId
            .<ResponseEntity<?>>map(vid -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/content/contentid/" + vid))
                .build())
            .orElseGet(() -> notFound("Content not found in view: " + view));
    }

    /**
     * GET /content/view/{view}/externalid/{id}
     */
    @GetMapping("/view/{view}/externalid/{id}")
    @Operation(summary = "Read a content from a view by external ID",
               description = "Resolve an external ID in a specific view and redirect to the versioned URL")
    @ApiResponse(responseCode = "302", description = "Redirect to versioned content URL")
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
            .<ResponseEntity<?>>map(vid -> ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/content/contentid/" + vid))
                .build())
            .orElseGet(() -> notFound("Content not found in view: " + view));
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
        ContentResultDto result = contentService.createContent(write, userId);
        return ResponseEntity.created(URI.create("/content/contentid/" + result.getVersion()))
            .eTag(result.getVersion())
            .body(result);
    }

    /**
     * PUT /content/contentid/{id}
     * Update existing content. Requires unversioned ID.
     */
    @PutMapping("/contentid/{id}")
    @Operation(summary = "Update a content",
               description = "Update an existing content by creating a new version")
    @ApiResponse(responseCode = "200", description = "Content updated",
                 headers = @Header(name = "ETag", description = "The new versioned content ID"),
                 content = @Content(schema = @Schema(implementation = ContentResultDto.class)))
    @ApiResponse(responseCode = "404", description = "Content not found",
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
                .body(new ErrorResponseDto("VERSIONED_ID", "Update requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        String userId = resolveUserId(request);

        Optional<ContentResultDto> result = contentService.updateContent(parts[0], parts[1], write, userId);
        return result
            .<ResponseEntity<?>>map(r -> ResponseEntity.ok()
                .eTag(r.getVersion())
                .location(URI.create("/content/contentid/" + r.getVersion()))
                .body(r))
            .orElseGet(() -> notFound("Content not found"));
    }

    /**
     * DELETE /content/contentid/{id}
     */
    @DeleteMapping("/contentid/{id}")
    @Operation(summary = "Delete a content",
               description = "Soft delete a content by reassigning its view to p.deleted")
    @ApiResponse(responseCode = "204", description = "Content deleted")
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
                .body(new ErrorResponseDto("VERSIONED_ID", "Delete requires an unversioned content ID"));
        }

        String[] parts = contentService.parseContentId(id);
        String userId = resolveUserId(request);

        boolean deleted = contentService.deleteContent(parts[0], parts[1], userId);
        if (deleted)
        {
            return ResponseEntity.noContent().build();
        }
        return notFound("Content not found");
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
            .body(new ErrorResponseDto("NOT_FOUND", message));
    }
}
