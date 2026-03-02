package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.preview.PreviewService;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for content preview generation.
 * Dispatches to the appropriate preview adapter based on remotes configuration.
 */
@RestController
@RequestMapping("/preview")
@Tag(name = "Preview")
public class PreviewController {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewController.class);
    private static final Gson GSON = new Gson();

    private final PreviewService previewService;
    private final ContentManager contentManager;

    public PreviewController(PreviewService previewService, ContentManager contentManager) {
        this.previewService = previewService;
        this.contentManager = contentManager;
    }

    /**
     * POST /preview/contentid/{contentId}
     * Generate a preview URL for the given content ID.
     */
    @PostMapping(value = "/contentid/{contentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview content by content ID",
               description = "Generate a preview URL for the given content. Optionally reads from a workspace.")
    @ApiResponse(responseCode = "200", description = "Preview URL generated")
    @ApiResponse(responseCode = "400", description = "Invalid content ID")
    @ApiResponse(responseCode = "404", description = "Content not found")
    public ResponseEntity<String> previewByContentId(
            @Parameter(description = "Content ID (delegationId:key)") @PathVariable String contentId,
            @Parameter(description = "Preview channel (e.g., web)") @RequestParam String channel,
            @Parameter(description = "Workspace name") @RequestParam(required = false) String workspace,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        return doPreview(contentId, channel, workspace, body, request);
    }

    /**
     * POST /preview/externalid/{id}
     * Generate a preview URL for the given external ID.
     */
    @PostMapping(value = "/externalid/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview content by external ID",
               description = "Resolve external ID and generate a preview URL.")
    @ApiResponse(responseCode = "200", description = "Preview URL generated")
    @ApiResponse(responseCode = "400", description = "Invalid external ID")
    @ApiResponse(responseCode = "404", description = "Content not found")
    public ResponseEntity<String> previewByExternalId(
            @Parameter(description = "External ID") @PathVariable String id,
            @Parameter(description = "Preview channel (e.g., web)") @RequestParam String channel,
            @Parameter(description = "Workspace name") @RequestParam(required = false) String workspace,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        Subject subject = resolveSubject(request);
        try {
            ContentVersionId vid = contentManager.resolve(id, subject);
            if (vid == null) {
                return notFound("External ID not found: " + id);
            }
            String resolvedContentId = IdUtil.toIdString(vid.getContentId());
            return doPreview(resolvedContentId, channel, workspace, body, request);
        } catch (Exception e) {
            LOG.error("Error resolving external ID {}: {}", id, e.getMessage());
            return errorResponse("Failed to resolve external ID: " + id, 500);
        }
    }

    private ResponseEntity<String> doPreview(String contentIdStr, String channel,
                                              String workspace, String body,
                                              HttpServletRequest request) {
        Subject subject = resolveSubject(request);
        String userName = resolveUserName(request);

        // Parse content ID
        ContentId contentId;
        try {
            contentId = IdUtil.fromString(contentIdStr);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid content ID: " + contentIdStr);
        }

        // Resolve content version
        ContentVersionId vid;
        try {
            if (workspace != null && !workspace.isEmpty()) {
                // For workspace preview, resolve from workspace if draft exists,
                // otherwise fall through to live
                var wsResult = contentManager.getFromWorkspace(
                    workspace, contentId, Object.class, subject);
                if (wsResult != null && !wsResult.getStatus().isNotFound() && wsResult.getContentId() != null) {
                    vid = wsResult.getContentId();
                } else {
                    vid = contentManager.resolve(contentId, subject);
                }
            } else {
                vid = contentManager.resolve(contentId, subject);
            }
        } catch (Exception e) {
            LOG.error("Error resolving content {}: {}", contentIdStr, e.getMessage());
            return notFound("Content not found: " + contentIdStr);
        }

        if (vid == null) {
            return notFound("Content not found: " + contentIdStr);
        }

        // Parse body
        JsonObject bodyJson;
        try {
            if (body != null && !body.isBlank()) {
                bodyJson = JsonParser.parseString(body).getAsJsonObject();
            } else {
                bodyJson = new JsonObject();
            }
        } catch (Exception e) {
            bodyJson = new JsonObject();
        }

        // Generate preview
        JsonObject result = previewService.preview(vid, channel, workspace, bodyJson, userName, subject);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(GSON.toJson(result));
    }

    private Subject resolveSubject(HttpServletRequest request) {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        String userId = user != null ? user.toString() : "system";
        return new Subject(userId, null);
    }

    private String resolveUserName(HttpServletRequest request) {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        return user != null ? user.toString() : "system";
    }

    private ResponseEntity<String> badRequest(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", 400);
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(GSON.toJson(error));
    }

    private ResponseEntity<String> notFound(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", 404);
        return ResponseEntity.status(404)
            .contentType(MediaType.APPLICATION_JSON)
            .body(GSON.toJson(error));
    }

    private ResponseEntity<String> errorResponse(String message, int status) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", status);
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(GSON.toJson(error));
    }
}
