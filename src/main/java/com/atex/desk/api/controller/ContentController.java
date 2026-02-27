package com.atex.desk.api.controller;

import com.atex.desk.api.dto.ContentHistoryDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
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
public class ContentController
{
    private static final String DEFAULT_USER = "system";

    private final ContentService contentService;

    public ContentController(ContentService contentService)
    {
        this.contentService = contentService;
    }

    /**
     * GET /content/contentid/{id}
     * If unversioned → 302 redirect to versioned URL.
     * If versioned → 200 with content.
     */
    @GetMapping("/contentid/{id}")
    public ResponseEntity<?> getContent(@PathVariable String id)
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
    public ResponseEntity<?> getHistory(@PathVariable String id)
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
    public ResponseEntity<?> getContentByExternalId(@PathVariable String id)
    {
        Optional<String> contentId = contentService.resolveExternalId(id);
        if (contentId.isEmpty())
        {
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
    public ResponseEntity<?> getHistoryByExternalId(@PathVariable String id)
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
    public ResponseEntity<?> getContentFromView(@PathVariable String view,
                                                 @PathVariable String id)
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
    public ResponseEntity<?> getContentFromViewByExternalId(@PathVariable String view,
                                                             @PathVariable String id)
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
    public ResponseEntity<?> updateContent(@PathVariable String id,
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
    public ResponseEntity<?> deleteContent(@PathVariable String id,
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
