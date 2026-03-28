package com.atex.onecms.app.dam.ws;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.onecms.LocalContentManager;
import com.atex.desk.api.service.ContentService;
import com.atex.onecms.app.dam.audioai.CreateOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AudioAI Resource - manages audio AI content linked to articles.
 * Ported from gong/desk DamAudioAIResource (JAX-RS to Spring MVC).
 *
 * @author mnova
 */
@RestController
@RequestMapping("/dam/audioai")
@Tag(name = "AudioAI", description = "Audio AI content operations")
public class DamAudioAIResource {

    private static final Logger LOGGER = Logger.getLogger(DamAudioAIResource.class.getName());

    private static final Gson GSON = new GsonBuilder().create();
    private static final String AUDIOAI_EXT_PREFIX = "audioai-";

    private final ContentService contentService;
    private final LocalContentManager localContentManager;

    public DamAudioAIResource(ContentService contentService,
                              LocalContentManager localContentManager) {
        this.contentService = contentService;
        this.localContentManager = localContentManager;
    }

    /**
     * Search for an AudioAI content linked to the given article.
     * Resolves via external ID alias "audioai-{articleId}".
     */
    @GetMapping(value = "/search/{articleId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find AudioAI content for an article")
    public ResponseEntity<ContentResultDto> findAudioAI(HttpServletRequest request,
                                                        @PathVariable String articleId) {
        DamUserContext userContext = DamUserContext.from(request);
        userContext.assertLoggedIn();

        try {
            String externalId;
            if (articleId.startsWith(AUDIOAI_EXT_PREFIX)) {
                // Already an audioai external ID
                externalId = articleId;
            } else {
                // Build external ID from the article's content ID
                externalId = AUDIOAI_EXT_PREFIX + articleId;
            }

            Optional<String> resolved = contentService.resolveExternalId(externalId);
            if (resolved.isEmpty()) {
                throw ContentApiException.notFound("Audio AI not found for article " + articleId);
            }

            // resolved is an unversioned ID like "onecms:key", resolve to latest version
            String[] parts = contentService.parseContentId(resolved.get());
            Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);
            if (versionedId.isEmpty()) {
                throw ContentApiException.notFound("Audio AI not found for article " + articleId);
            }

            // Get the full content
            String[] vParts = contentService.parseContentId(versionedId.get());
            Optional<ContentResultDto> content = contentService.getContent(vParts[0], vParts[1], vParts[2]);
            if (content.isEmpty()) {
                throw ContentApiException.notFound("Audio AI not found for article " + articleId);
            }

            // Return with no-store cache control (since we resolved from an external ID,
            // the underlying content may change between requests)
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(content.get());

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot find audio " + articleId, e);
            throw ContentApiException.internal("Cannot find audio " + articleId, e);
        }
    }

    /**
     * Create a new AudioAI content linked to the given article.
     * Sets an external ID alias "audioai-{articleId}" on the created content.
     */
    @PostMapping(value = "/create/{articleId}",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create AudioAI content for an article")
    public ResponseEntity<ContentResultDto> createAudioAI(HttpServletRequest request,
                                                          @PathVariable String articleId,
                                                          @RequestBody String body) {
        DamUserContext userContext = DamUserContext.from(request);
        userContext.assertLoggedIn();

        final CreateOptionRequest createOptionRequest;
        try {
            createOptionRequest = GSON.fromJson(body, CreateOptionRequest.class);
        } catch (JsonParseException e) {
            throw ContentApiException.badRequest("Cannot parse request");
        }

        try {
            String contentType = Optional.ofNullable(createOptionRequest.getContentType())
                .orElse("atex.dam.standard.Audio");
            String inputTemplate = Optional.ofNullable(createOptionRequest.getInputTemplate())
                .orElse("p.DamAudioAI");
            String objectType = Optional.ofNullable(createOptionRequest.getObjectType())
                .orElse("audio");

            // Build the contentData aspect
            Map<String, Object> contentData = new LinkedHashMap<>();
            contentData.put("_type", contentType);
            contentData.put("inputTemplate", inputTemplate);
            contentData.put("objectType", objectType);
            if (createOptionRequest.getName() != null) {
                contentData.put("name", createOptionRequest.getName());
                contentData.put("headline", createOptionRequest.getName());
            }

            AspectDto contentDataAspect = new AspectDto();
            contentDataAspect.setName("contentData");
            contentDataAspect.setData(contentData);

            Map<String, AspectDto> aspects = new LinkedHashMap<>();
            aspects.put("contentData", contentDataAspect);

            // Add OneCMS template aspect if templateId is provided
            String templateId = createOptionRequest.getTemplateId();
            if (templateId != null && !templateId.isBlank()) {
                Map<String, Object> onecmsData = new LinkedHashMap<>();
                onecmsData.put("_type", "com.atex.onecms.content.OneCMSAspectBean");
                onecmsData.put("createdWithTemplate", templateId);
                AspectDto onecmsAspect = new AspectDto();
                onecmsAspect.setName("atex.onecms");
                onecmsAspect.setData(onecmsData);
                aspects.put("atex.onecms", onecmsAspect);
            }

            // Add insertion info if parent IDs are provided
            String insertParentId = createOptionRequest.getInsertParentId();
            String securityParentId = createOptionRequest.getSecurityParentId();
            if ((insertParentId != null && !insertParentId.isBlank())
                    || (securityParentId != null && !securityParentId.isBlank())) {
                Map<String, Object> insertionData = new LinkedHashMap<>();
                insertionData.put("_type", "com.atex.onecms.content.InsertionInfoAspectBean");
                if (insertParentId != null && !insertParentId.isBlank()) {
                    insertionData.put("insertParentId", insertParentId);
                } else {
                    // Fall back to article's insertion parent
                    findArticleInsertParentId(articleId).ifPresent(
                        pid -> insertionData.put("insertParentId", pid));
                }
                if (securityParentId != null && !securityParentId.isBlank()) {
                    insertionData.put("securityParentId", securityParentId);
                }
                AspectDto insertionAspect = new AspectDto();
                insertionAspect.setName("p.InsertionInfo");
                insertionAspect.setData(insertionData);
                aspects.put("p.InsertionInfo", insertionAspect);
            }

            // Build the external ID alias operation
            String externalId = AUDIOAI_EXT_PREFIX + articleId;
            ContentWriteDto.OperationDto aliasOp = new ContentWriteDto.OperationDto();
            aliasOp.setType("SetAliasOperation");
            aliasOp.setNamespace("externalId");
            aliasOp.setValue(externalId);

            ContentWriteDto writeDto = new ContentWriteDto();
            writeDto.setAspects(aspects);
            writeDto.setOperations(List.of(aliasOp));

            // Create via LocalContentManager (runs pre-store hooks + persists aliases)
            String userId = userContext.getSubject().getPrincipalId();
            ContentResultDto result = localContentManager.createContentFromDto(writeDto, userId);

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot create audioAI for " + articleId, e);
            throw ContentApiException.internal("Cannot create audioAI for " + articleId, e);
        }
    }

    /**
     * Looks up the article's insertion parent ID to use as a fallback
     * when no insertParentId is explicitly provided.
     */
    private Optional<String> findArticleInsertParentId(String articleId) {
        try {
            String[] parts = contentService.parseContentId(articleId);
            Optional<String> versionedId = contentService.resolve(parts[0], parts[1]);
            if (versionedId.isPresent()) {
                String[] vParts = contentService.parseContentId(versionedId.get());
                Optional<ContentResultDto> articleContent =
                    contentService.getContent(vParts[0], vParts[1], vParts[2]);
                if (articleContent.isPresent()) {
                    Map<String, AspectDto> aspects = articleContent.get().getAspects();
                    if (aspects != null) {
                        AspectDto insertionInfo = aspects.get("p.InsertionInfo");
                        if (insertionInfo != null && insertionInfo.getData() != null) {
                            Object parentId = insertionInfo.getData().get("insertParentId");
                            if (parentId != null) {
                                return Optional.of(parentId.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot find insertion parent for article " + articleId, e);
        }
        return Optional.empty();
    }

    /**
     * Extended CreateOption that also carries the 'name' field from the request body.
     * Matches the inner class in the original gong/desk DamAudioAIResource.
     */
    private static class CreateOptionRequest extends CreateOption {
        private String name;
        private String voiceId;
        private String toneId;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public String getVoiceId() {
            return voiceId;
        }

        public void setVoiceId(final String voiceId) {
            this.voiceId = voiceId;
        }

        public String getToneId() {
            return toneId;
        }

        public void setToneId(final String toneId) {
            this.toneId = toneId;
        }
    }
}
