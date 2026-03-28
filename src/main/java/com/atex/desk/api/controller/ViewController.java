package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * View management endpoints — assign/remove content versions to/from named views.
 * Ported from polopoly ViewResource (JAX-RS).
 */
@RestController
@RequestMapping("/view")
@Tag(name = "Views", description = "Assign and remove content from views")
public class ViewController {

    private final ContentService contentService;

    public ViewController(ContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * PUT /view/{view} — Assign a versioned content ID to a view.
     * Body: {"contentId": "delegationId:key:version"}
     */
    @PutMapping(value = "{view}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign content to a view")
    public ResponseEntity<?> assignToView(
            @PathVariable("view") String view,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute(AuthFilter.USER_ATTRIBUTE);

        String contentId = body.get("contentId");
        if (contentId == null || contentId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST,
                            "Payload must contain the complete version to assign to view!"));
        }

        // Must be a versioned ID (3 parts: delegationId:key:version)
        if (!contentService.isVersionedId(contentId)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST,
                            "contentId must be a versioned ID (delegationId:key:version)"));
        }

        try {
            contentService.assignToView(contentId, view, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, e.getMessage()));
        }
    }

    /**
     * DELETE /view/{view}/{id} — Remove content from a view.
     * The id can be versioned or unversioned.
     */
    @DeleteMapping(value = "{view}/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Remove content from a view")
    public ResponseEntity<?> removeFromView(
            @PathVariable("view") String view,
            @PathVariable("id") String id,
            HttpServletRequest request) {

        try {
            contentService.removeFromView(id, view);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponseDto(HttpStatus.NOT_FOUND, e.getMessage()));
        }
    }
}
