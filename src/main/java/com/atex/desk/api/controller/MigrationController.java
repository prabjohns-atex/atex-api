package com.atex.desk.api.controller;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.service.ContentService;
import com.atex.desk.api.auth.AuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Migration endpoint for importing legacy Polopoly content into desk-api.
 * Creates content with aspects and registers aliases (externalId, policyId).
 */
@RestController
@RequestMapping("/admin/migrate")
@Tag(name = "Migration", description = "Legacy content migration endpoints")
public class MigrationController
{
    private static final Logger LOG = Logger.getLogger(MigrationController.class.getName());

    private final ContentService contentService;

    public MigrationController(ContentService contentService)
    {
        this.contentService = contentService;
    }

    /**
     * POST /admin/migrate/content
     *
     * Accepts a migration request containing aspects and aliases.
     * Creates the content in the DB and registers all provided aliases.
     *
     * Request body:
     * {
     *   "aspects": { ... },
     *   "aliases": [
     *     {"namespace": "externalId", "value": "p.onecms.DamTemplateList"},
     *     {"namespace": "policyId", "value": "policy:2.184"}
     *   ]
     * }
     */
    @PostMapping("/content")
    @Operation(summary = "Migrate legacy content",
               description = "Import legacy content with aspects and alias mappings. "
                   + "Creates new OneCMS content and registers aliases for the original external ID and policy ID.")
    @ApiResponse(responseCode = "201", description = "Content migrated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Content already exists (alias conflict)")
    public ResponseEntity<?> migrateContent(@RequestBody MigrateContentRequest request,
                                             HttpServletRequest httpRequest)
    {
        if (request.aspects == null || request.aspects.isEmpty())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(HttpStatus.BAD_REQUEST, "aspects is required"));
        }

        String userId = resolveUserId(httpRequest);

        // Check if any alias already exists — skip if already migrated
        if (request.aliases != null)
        {
            for (AliasEntry alias : request.aliases)
            {
                if ("externalId".equals(alias.namespace))
                {
                    if (contentService.resolveExternalId(alias.value).isPresent())
                    {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Already migrated",
                                          "alias", alias.value));
                    }
                }
                else
                {
                    if (contentService.resolveByAlias(alias.namespace, alias.value).isPresent())
                    {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Already migrated",
                                          "alias", alias.namespace + ":" + alias.value));
                    }
                }
            }
        }

        try
        {
            // Create the content
            ContentWriteDto writeDto = new ContentWriteDto();
            writeDto.setAspects(request.aspects);
            ContentResultDto result = contentService.createContent(writeDto, userId);

            // Register aliases
            String[] idParts = contentService.parseContentId(result.getId());
            String delegationId = idParts[0];
            String key = idParts[1];

            if (request.aliases != null)
            {
                for (AliasEntry alias : request.aliases)
                {
                    try
                    {
                        contentService.createAlias(delegationId, key, alias.namespace, alias.value);
                    }
                    catch (Exception e)
                    {
                        LOG.log(Level.WARNING, "Failed to create alias: " + alias.namespace
                            + "=" + alias.value, e);
                    }
                }
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
        catch (Exception e)
        {
            LOG.log(Level.SEVERE, "Migration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDto(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Migration failed: " + e.getMessage()));
        }
    }

    private String resolveUserId(HttpServletRequest request)
    {
        Object user = request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        return user != null ? user.toString() : "system";
    }

    // --- Request DTOs ---

    public static class MigrateContentRequest
    {
        public Map<String, AspectDto> aspects;
        public List<AliasEntry> aliases;
    }

    public static class AliasEntry
    {
        public String namespace;
        public String value;
    }
}
