package com.atex.desk.api.controller;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.WorkspaceInfoDto;
import com.atex.desk.api.onecms.WorkspaceStorage;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.DeleteResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.WorkspaceResult;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.desk.api.onecms.LocalContentManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for workspace (draft) operations.
 * Used by reporter-tool / onecms-lib for preview workflow.
 */
@RestController
@RequestMapping("/content/workspace")
public class WorkspaceController {

    private final LocalContentManager contentManager;
    private final WorkspaceStorage workspaceStorage;

    public WorkspaceController(LocalContentManager contentManager, WorkspaceStorage workspaceStorage) {
        this.contentManager = contentManager;
        this.workspaceStorage = workspaceStorage;
    }

    /**
     * GET /content/workspace/{wsId}/contentid/{id}
     * Get draft content from a workspace. Falls through to real content if no draft exists.
     */
    @GetMapping("/{wsId}/contentid/{id}")
    public ResponseEntity<?> getDraft(@PathVariable String wsId,
                                       @PathVariable String id,
                                       HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            ContentId contentId = IdUtil.fromString(id);
            WorkspaceResult<?> result = contentManager.getFromWorkspace(
                wsId, contentId, Object.class, subject);

            if (result.getStatus().isNotFound()) {
                return notFound("Draft not found in workspace: " + wsId);
            }

            ContentResultDto dto = toResultDto(result);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "Invalid content ID: " + id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * POST /content/workspace/{wsId}
     * Create a draft in a workspace.
     */
    @PostMapping("/{wsId}")
    public ResponseEntity<?> createDraft(@PathVariable String wsId,
                                          @RequestBody ContentWriteDto write,
                                          HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            ContentWrite<Object> contentWrite = dtoToContentWrite(write);
            WorkspaceResult<?> result = contentManager.createOnWorkspace(wsId, contentWrite, subject);

            ContentResultDto dto = toResultDto(result);
            String versionedId = dto.getVersion();
            return ResponseEntity.created(URI.create("/content/workspace/" + wsId + "/contentid/" + dto.getId()))
                .eTag(versionedId)
                .body(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * PUT /content/workspace/{wsId}/contentid/{id}
     * Update a draft in a workspace.
     */
    @PutMapping("/{wsId}/contentid/{id}")
    public ResponseEntity<?> updateDraft(@PathVariable String wsId,
                                          @PathVariable String id,
                                          @RequestBody ContentWriteDto write,
                                          HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            ContentId contentId = IdUtil.fromString(id);
            ContentWrite<Object> contentWrite = dtoToContentWrite(write);
            WorkspaceResult<?> result = contentManager.updateOnWorkspace(wsId, contentId, contentWrite, subject);

            ContentResultDto dto = toResultDto(result);
            return ResponseEntity.ok()
                .eTag(dto.getVersion())
                .body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "Invalid content ID: " + id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * DELETE /content/workspace/{wsId}/contentid/{id}
     * Remove a single draft from a workspace.
     */
    @DeleteMapping("/{wsId}/contentid/{id}")
    public ResponseEntity<?> deleteDraft(@PathVariable String wsId,
                                          @PathVariable String id,
                                          HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            ContentId contentId = IdUtil.fromString(id);
            DeleteResult result = contentManager.deleteFromWorkspace(wsId, contentId, subject);

            if (result.getStatus() == Status.REMOVED) {
                return ResponseEntity.noContent().build();
            }
            return notFound("Draft not found in workspace: " + wsId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "Invalid content ID: " + id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * DELETE /content/workspace/{wsId}
     * Clear all drafts in a workspace.
     */
    @DeleteMapping("/{wsId}")
    public ResponseEntity<?> clearWorkspace(@PathVariable String wsId,
                                             HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            contentManager.clearWorkspace(wsId, subject);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    /**
     * GET /content/workspace/{wsId}
     * Get workspace info (draft count, creation time).
     */
    @GetMapping("/{wsId}")
    public ResponseEntity<?> getWorkspaceInfo(@PathVariable String wsId) {
        WorkspaceInfoDto info = workspaceStorage.getWorkspaceInfo(wsId);
        if (info == null) {
            return notFound("Workspace not found: " + wsId);
        }
        return ResponseEntity.ok(info);
    }

    /**
     * POST /content/workspace/{wsId}/promote
     * Promote all drafts in a workspace to real content.
     */
    @PostMapping("/{wsId}/promote")
    public ResponseEntity<?> promote(@PathVariable String wsId,
                                      HttpServletRequest request) {
        try {
            Subject subject = resolveSubject(request);
            Collection<WorkspaceStorage.DraftEntry> drafts = workspaceStorage.getAllDrafts(wsId);

            if (drafts == null) {
                return notFound("Workspace not found: " + wsId);
            }

            // Collect the content IDs that will be promoted
            List<String> promotedIds = new ArrayList<>();
            for (WorkspaceStorage.DraftEntry draft : drafts) {
                promotedIds.add(draft.contentIdString());
            }

            // Promote — this persists all drafts and deletes the workspace
            contentManager.promote(wsId, null, subject);

            return ResponseEntity.ok(promotedIds);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto("INTERNAL_ERROR", e.getMessage()));
        }
    }

    // --- Helpers ---

    private Subject resolveSubject(HttpServletRequest request) {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        String userId = user != null ? user.toString() : "system";
        return new Subject(userId, null);
    }

    private ResponseEntity<ErrorResponseDto> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDto("NOT_FOUND", message));
    }

    @SuppressWarnings("unchecked")
    private ContentWrite<Object> dtoToContentWrite(ContentWriteDto dto) {
        ContentWriteBuilder<Object> builder = new ContentWriteBuilder<>();

        if (dto.getAspects() != null) {
            var contentDataAspect = dto.getAspects().get("contentData");
            if (contentDataAspect != null && contentDataAspect.getData() != null) {
                Map<String, Object> data = contentDataAspect.getData();
                builder.mainAspectData(data);
                if (data.containsKey("_type")) {
                    builder.type((String) data.get("_type"));
                }
            }

            for (Map.Entry<String, com.atex.desk.api.dto.AspectDto> entry : dto.getAspects().entrySet()) {
                if (!"contentData".equals(entry.getKey()) && entry.getValue().getData() != null) {
                    builder.aspect(new Aspect<>(entry.getKey(), entry.getValue().getData()));
                }
            }
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private ContentResultDto toResultDto(ContentResult<?> result) {
        ContentResultDto dto = new ContentResultDto();

        if (result.getContentId() != null) {
            dto.setId(IdUtil.toIdString(result.getContentId().getContentId()));
            dto.setVersion(IdUtil.toVersionedIdString(result.getContentId()));
        }

        if (result.getContent() != null) {
            Map<String, com.atex.desk.api.dto.AspectDto> aspects = new LinkedHashMap<>();

            // Main aspect
            if (result.getContent().getContentData() != null) {
                com.atex.desk.api.dto.AspectDto mainAspect = new com.atex.desk.api.dto.AspectDto();
                mainAspect.setName("contentData");
                Object data = result.getContent().getContentData();
                if (data instanceof Map) {
                    mainAspect.setData((Map<String, Object>) data);
                } else {
                    mainAspect.setData(Map.of("_data", data));
                }
                aspects.put("contentData", mainAspect);
            }

            // Other aspects
            if (result.getContent().getAspects() != null) {
                for (Aspect<?> aspect : result.getContent().getAspects()) {
                    com.atex.desk.api.dto.AspectDto aspectDto = new com.atex.desk.api.dto.AspectDto();
                    aspectDto.setName(aspect.getName());
                    if (aspect.getData() instanceof Map) {
                        aspectDto.setData((Map<String, Object>) aspect.getData());
                    }
                    aspects.put(aspect.getName(), aspectDto);
                }
            }

            dto.setAspects(aspects);
        }

        return dto;
    }
}
