package com.atex.desk.api.onecms;

import com.atex.desk.api.config.ConfigurationService;
import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentHistoryDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentVersionInfoDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.indexing.ContentIndexer;
import com.atex.desk.api.service.ChangeListService;
import com.atex.desk.api.service.ContentService;
import com.atex.desk.api.service.IdGenerator;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentHistory;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentOperation;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentVersionInfo;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.DeleteResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.SetAliasOperation;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.WorkspaceResult;
import com.atex.onecms.content.WorkspaceResultBuilder;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.content.lifecycle.LifecycleContextPreStore;
import com.atex.onecms.content.lifecycle.LifecyclePreStore;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.content.repository.StorageException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local ContentManager implementation backed by JPA repositories via ContentService.
 * Supports pre-store hooks (wildcard + type-specific) and post-store indexing.
 */
@Component
public class LocalContentManager implements ContentManager {

    private static final Logger LOG = Logger.getLogger(LocalContentManager.class.getName());
    private static final String CONFIG_ID = "desk-api-local";

    /**
     * Wildcard key for hooks that apply to all content types.
     */
    public static final String ALL_TYPES = "*";

    private final ContentService contentService;
    private final ObjectMapper objectMapper;
    private final WorkspaceStorage workspaceStorage;
    private final IdGenerator idGenerator;
    private final FileService fileService;
    private final ContentIndexer contentIndexer;
    private final ConfigurationService configurationService;
    private final ChangeListService changeListService;

    /**
     * Registry of pre-store hooks keyed by content type name.
     * Each content type can have multiple hooks that chain in order.
     * The special key "*" holds hooks that apply to all content types.
     */
    private final Map<String, List<LifecyclePreStore<?, ?>>> preStoreHooks = new ConcurrentHashMap<>();

    public LocalContentManager(ContentService contentService, ObjectMapper objectMapper,
                                WorkspaceStorage workspaceStorage, IdGenerator idGenerator,
                                @Nullable FileService fileService,
                                @Nullable ContentIndexer contentIndexer,
                                @Nullable ConfigurationService configurationService,
                                @Nullable ChangeListService changeListService) {
        this.contentService = contentService;
        this.objectMapper = objectMapper;
        this.workspaceStorage = workspaceStorage;
        this.idGenerator = idGenerator;
        this.fileService = fileService;
        this.contentIndexer = contentIndexer;
        this.configurationService = configurationService;
        this.changeListService = changeListService;
    }

    // --- Pre-store hook registration ---

    /**
     * Register a pre-store hook for a content type.
     * Use "*" for hooks that should run on all content types.
     */
    public <T, C> void registerPreStoreHook(String contentType, LifecyclePreStore<T, C> hook) {
        preStoreHooks.computeIfAbsent(contentType, k -> new ArrayList<>()).add(hook);
    }

    // --- Resolve ---

    @Override
    public ContentVersionId resolve(ContentId id, Subject subject) throws StorageException {
        return resolve(id, SYSTEM_VIEW_LATEST, subject);
    }

    @Override
    public ContentVersionId resolve(ContentId id, String view, Subject subject) throws StorageException {
        try {
            Optional<String> resolved = contentService.resolve(id.getDelegationId(), id.getKey(), view);
            return resolved.map(IdUtil::fromVersionedString).orElse(null);
        } catch (Exception e) {
            throw new StorageException("Failed to resolve " + IdUtil.toIdString(id), e);
        }
    }

    @Override
    public ContentVersionId resolve(String externalId, Subject subject) throws StorageException {
        return resolve(externalId, null, subject);
    }

    @Override
    public ContentVersionId resolve(String externalId, String view, Subject subject)
            throws StorageException {
        try {
            // Try DB resolution first
            Optional<String> contentIdStr = contentService.resolveExternalId(externalId);
            if (contentIdStr.isPresent()) {
                ContentId cid = IdUtil.fromString(contentIdStr.get());
                String viewName = (view != null) ? view : SYSTEM_VIEW_LATEST;
                return resolve(cid, viewName, subject);
            }

            // Fall back to resource-based configuration
            if (configurationService != null && configurationService.isConfigId(externalId)) {
                return configurationService.syntheticVersionId(externalId);
            }

            return null;
        } catch (Exception e) {
            throw new StorageException("Failed to resolve external ID: " + externalId, e);
        }
    }

    // --- Get ---

    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> get(ContentVersionId contentId, String variant,
                                     Class<T> dataClass, Map<String, Object> params,
                                     Subject subject, GetOption... options)
            throws ClassCastException, StorageException {
        try {
            // Intercept synthetic config IDs
            if (configurationService != null && configurationService.isSyntheticId(contentId)) {
                return configurationService.toContentResult(contentId.getKey());
            }

            Optional<ContentResultDto> dto = contentService.getContent(
                contentId.getDelegationId(), contentId.getKey(), contentId.getVersion());

            if (dto.isEmpty()) {
                return ContentResult.of(contentId, Status.NOT_FOUND);
            }

            ContentResultDto result = dto.get();
            return (ContentResult<T>) dtoToContentResult(result, contentId, variant, dataClass);
        } catch (Exception e) {
            throw new StorageException("Failed to get content: " + contentId, e);
        }
    }

    // --- Content History ---

    @Override
    public ContentHistory getContentHistory(ContentId contentId, Subject subject,
                                             GetOption... options) throws StorageException {
        try {
            Optional<ContentHistoryDto> dto = contentService.getHistory(
                contentId.getDelegationId(), contentId.getKey());

            if (dto.isEmpty()) {
                return new ContentHistory(List.of());
            }

            List<ContentVersionInfo> versions = new ArrayList<>();
            for (ContentVersionInfoDto vi : dto.get().getVersions()) {
                ContentVersionId vid = IdUtil.fromVersionedString(vi.getVersion());
                versions.add(new ContentVersionInfo(
                    vid, vi.getCreationTime(), vi.getCreatorId(), vi.getViews()));
            }
            return new ContentHistory(versions);
        } catch (Exception e) {
            throw new StorageException("Failed to get history: " + contentId, e);
        }
    }

    // --- Create ---

    @Override
    @SuppressWarnings("unchecked")
    public <IN, OUT> ContentResult<OUT> create(ContentWrite<IN> content, Subject subject)
            throws IllegalArgumentException, StorageException, CallbackException {
        try {
            // Run pre-store hooks
            ContentWrite<IN> processed = runPreStoreHooks(content, null, subject);

            ContentWriteDto writeDto = contentWriteToDto(processed);
            String userId = subject != null ? subject.getPrincipalId() : "system";

            // Handle SetAliasOperation
            String externalId = extractExternalId(processed);

            ContentResultDto result = contentService.createContent(writeDto, userId);

            ContentVersionId vid = IdUtil.fromVersionedString(result.getVersion());

            // Post-store indexing (fire-and-forget)
            indexAsync(result, vid);

            // Record change event (fire-and-forget)
            recordChange("CREATE", result, vid, userId);

            return (ContentResult<OUT>) dtoToContentResult(result, vid, null, Object.class);
        } catch (CallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to create content", e);
        }
    }

    // --- Update ---

    @Override
    @SuppressWarnings("unchecked")
    public <IN, OUT> ContentResult<OUT> update(ContentId contentId, ContentWrite<IN> data,
                                                Subject subject)
            throws StorageException, ContentModifiedException, CallbackException {
        try {
            // Fetch existing content for pre-store hooks
            Content<IN> existingContent = null;
            ContentVersionId resolved = resolve(contentId, subject);
            if (resolved != null) {
                ContentResult<IN> existing = (ContentResult<IN>) get(
                    resolved, null, Object.class, null, subject);
                if (existing.getStatus().isSuccess()) {
                    existingContent = existing.getContent();
                }
            }

            // Run pre-store hooks
            ContentWrite<IN> processed = runPreStoreHooks(data, existingContent, subject);

            ContentWriteDto writeDto = contentWriteToDto(processed);
            String userId = subject != null ? subject.getPrincipalId() : "system";

            Optional<ContentResultDto> result = contentService.updateContent(
                contentId.getDelegationId(), contentId.getKey(), writeDto, userId);

            if (result.isEmpty()) {
                return ContentResult.of(null, Status.NOT_FOUND);
            }

            ContentVersionId vid = IdUtil.fromVersionedString(result.get().getVersion());

            // Post-store indexing (fire-and-forget)
            indexAsync(result.get(), vid);

            // Record change event (fire-and-forget)
            recordChange("UPDATE", result.get(), vid, userId);

            return (ContentResult<OUT>) dtoToContentResult(result.get(), vid, null, Object.class);
        } catch (CallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to update content: " + contentId, e);
        }
    }

    @Override
    public <IN, OUT> ContentResult<OUT> forceUpdate(ContentId contentId, ContentWrite<IN> data,
                                                     Subject subject)
            throws StorageException, ContentModifiedException, CallbackException {
        // For now, forceUpdate behaves same as update (no conflict checking in ContentService)
        return update(contentId, data, subject);
    }

    // --- Delete ---

    @Override
    public DeleteResult delete(ContentId contentId, ContentVersionId latestVersion,
                                Subject subject, DeleteOption... options)
            throws ContentModifiedException, StorageException, CallbackException {
        try {
            String userId = subject != null ? subject.getPrincipalId() : "system";
            boolean deleted = contentService.deleteContent(
                contentId.getDelegationId(), contentId.getKey(), userId);

            // Post-delete: remove from index
            if (deleted && contentIndexer != null) {
                try {
                    contentIndexer.delete(contentId);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Post-delete indexing failed for " + contentId, e);
                }
            }

            // Record delete event (fire-and-forget)
            if (deleted) {
                recordDelete(contentId, userId);
            }

            return new DeleteResult(deleted ? Status.REMOVED : Status.NOT_FOUND);
        } catch (Exception e) {
            throw new StorageException("Failed to delete content: " + contentId, e);
        }
    }

    // --- Config ---

    @Override
    public String getConfigId() throws StorageException {
        return CONFIG_ID;
    }

    // --- Workspace methods ---

    @Override
    @SuppressWarnings("unchecked")
    public <T> WorkspaceResult<T> getFromWorkspace(String workspaceId, ContentId contentId,
                                                     String variant, Map<String, Object> params,
                                                     Class<T> targetClass, Subject subject,
                                                     GetOption... options)
            throws ClassCastException, StorageException {
        String contentIdStr = IdUtil.toIdString(contentId);
        WorkspaceStorage.DraftEntry draft = workspaceStorage.getDraft(workspaceId, contentIdStr);

        if (draft != null) {
            // Draft found in workspace — convert stored DTO to ContentResult
            ContentVersionId draftVid = draft.draftVersionId();
            ContentResult<T> result = (ContentResult<T>) dtoToContentResult(
                draftDtoToResultDto(draft), draftVid, variant, targetClass);
            return new WorkspaceResultBuilder<>(workspaceId, ContentResultBuilder.copy(result))
                .draftId(draftVid)
                .build();
        }

        // No draft — fall through to real content
        ContentVersionId resolved = resolve(contentId, subject);
        if (resolved == null) {
            return new WorkspaceResult<>(null, workspaceId, null, Status.NOT_FOUND);
        }
        ContentResult<T> result = get(resolved, variant, targetClass, params, subject, options);
        return new WorkspaceResultBuilder<>(workspaceId, ContentResultBuilder.copy(result))
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> WorkspaceResult<T> createOnWorkspace(String workspaceId, ContentWrite<T> content,
                                                      Subject subject)
            throws ContentModifiedException, StorageException {
        try {
            // Generate a draft content ID and version
            String draftKey = idGenerator.nextId();
            String draftVersion = idGenerator.nextVersion();
            ContentId draftContentId = new ContentId("draft", draftKey);
            ContentVersionId draftVid = new ContentVersionId(draftContentId, draftVersion);

            ContentWriteDto writeDto = contentWriteToDto(content);
            String contentIdStr = IdUtil.toIdString(draftContentId);

            workspaceStorage.putDraft(workspaceId, contentIdStr,
                new WorkspaceStorage.DraftEntry(writeDto, contentIdStr, draftVid, Instant.now()));

            // Build result from the stored draft
            ContentResult<T> result = (ContentResult<T>) dtoToContentResult(
                draftDtoToResultDto(new WorkspaceStorage.DraftEntry(writeDto, contentIdStr, draftVid, Instant.now())),
                draftVid, null, Object.class);
            return new WorkspaceResultBuilder<>(workspaceId,
                new ContentResultBuilder<T>().id(draftVid).status(Status.CREATED)
                    .mainAspectData(result.getContent() != null ? result.getContent().getContentData() : null)
                    .type(result.getContent() != null ? result.getContent().getContentDataType() : null)
                    .aspects(result.getContent() != null ? result.getContent().getAspects() : List.of()))
                .draftId(draftVid)
                .build();
        } catch (Exception e) {
            throw new StorageException("Failed to create draft in workspace: " + workspaceId, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> WorkspaceResult<T> updateOnWorkspace(String workspaceId, ContentId contentId,
                                                      ContentWrite<T> data, Subject subject)
            throws ClassCastException, StorageException, ContentModifiedException {
        try {
            String contentIdStr = IdUtil.toIdString(contentId);
            String draftVersion = idGenerator.nextVersion();
            ContentVersionId draftVid = new ContentVersionId(contentId, draftVersion);

            ContentWriteDto writeDto = contentWriteToDto(data);

            workspaceStorage.putDraft(workspaceId, contentIdStr,
                new WorkspaceStorage.DraftEntry(writeDto, contentIdStr, draftVid, Instant.now()));

            ContentResult<T> result = (ContentResult<T>) dtoToContentResult(
                draftDtoToResultDto(new WorkspaceStorage.DraftEntry(writeDto, contentIdStr, draftVid, Instant.now())),
                draftVid, null, Object.class);
            return new WorkspaceResultBuilder<>(workspaceId,
                new ContentResultBuilder<T>().id(draftVid).status(Status.OK)
                    .mainAspectData(result.getContent() != null ? result.getContent().getContentData() : null)
                    .type(result.getContent() != null ? result.getContent().getContentDataType() : null)
                    .aspects(result.getContent() != null ? result.getContent().getAspects() : List.of()))
                .draftId(draftVid)
                .build();
        } catch (Exception e) {
            throw new StorageException("Failed to update draft in workspace: " + workspaceId, e);
        }
    }

    @Override
    public DeleteResult deleteFromWorkspace(String workspaceId, ContentId contentId,
                                             Subject subject, DeleteOption... options)
            throws StorageException, ContentModifiedException {
        String contentIdStr = IdUtil.toIdString(contentId);
        boolean removed = workspaceStorage.removeDraft(workspaceId, contentIdStr);
        return new DeleteResult(removed ? Status.REMOVED : Status.NOT_FOUND);
    }

    @Override
    public Status clearWorkspace(String workspaceId, Subject subject)
            throws StorageException, ContentModifiedException {
        workspaceStorage.clearWorkspace(workspaceId);
        return Status.OK;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentResult<T> promote(String workspaceId, ContentId contentId, Subject subject)
            throws StorageException, ContentModifiedException {
        try {
            Collection<WorkspaceStorage.DraftEntry> drafts = workspaceStorage.getAllDrafts(workspaceId);
            if (drafts == null || drafts.isEmpty()) {
                return ContentResult.of(null, Status.NOT_FOUND);
            }

            String userId = subject != null ? subject.getPrincipalId() : "system";
            ContentResultDto lastResult = null;

            for (WorkspaceStorage.DraftEntry draft : drafts) {
                ContentWriteDto writeDto = draft.contentWrite();
                String idStr = draft.contentIdString();

                if (idStr.startsWith("draft:")) {
                    // New content — create
                    lastResult = contentService.createContent(writeDto, userId);
                } else {
                    // Existing content — update
                    String[] parts = contentService.parseContentId(idStr);
                    Optional<ContentResultDto> updated = contentService.updateContent(
                        parts[0], parts[1], writeDto, userId);
                    if (updated.isPresent()) {
                        lastResult = updated.get();
                    }
                }
            }

            workspaceStorage.deleteWorkspace(workspaceId);

            if (lastResult != null) {
                ContentVersionId vid = IdUtil.fromVersionedString(lastResult.getVersion());
                return (ContentResult<T>) dtoToContentResult(lastResult, vid, null, Object.class);
            }
            return ContentResult.of(null, Status.OK);
        } catch (Exception e) {
            throw new StorageException("Failed to promote workspace: " + workspaceId, e);
        }
    }

    /**
     * Convert a DraftEntry to a ContentResultDto for internal use.
     */
    private ContentResultDto draftDtoToResultDto(WorkspaceStorage.DraftEntry draft) {
        ContentResultDto resultDto = new ContentResultDto();
        resultDto.setId(draft.contentIdString());
        resultDto.setVersion(IdUtil.toVersionedIdString(draft.draftVersionId()));
        resultDto.setAspects(draft.contentWrite().getAspects());
        return resultDto;
    }

    // --- Change feed recording ---

    private void recordChange(String eventType, ContentResultDto result,
                               ContentVersionId vid, String userId) {
        if (changeListService == null) return;
        try {
            changeListService.recordEvent(eventType, result,
                vid.getDelegationId(), vid.getKey(), vid.getVersion(), userId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Change feed recording failed for " + vid, e);
        }
    }

    private void recordDelete(ContentId contentId, String userId) {
        if (changeListService == null) return;
        try {
            changeListService.recordDelete(
                contentId.getDelegationId(), contentId.getKey(), null, null, userId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Change feed recording failed for delete of " + contentId, e);
        }
    }

    // --- Post-store indexing ---

    @SuppressWarnings("unchecked")
    private void indexAsync(ContentResultDto resultDto, ContentVersionId versionId) {
        if (contentIndexer == null) return;
        try {
            ContentResult<Object> result = dtoToContentResult(resultDto, versionId, null, Object.class);
            contentIndexer.index(result, versionId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Post-store indexing failed for " + versionId, e);
        }
    }

    // --- Conversion helpers ---

    @SuppressWarnings("unchecked")
    private <T> ContentResult<T> dtoToContentResult(ContentResultDto dto,
                                                     ContentVersionId vid,
                                                     String variant,
                                                     Class<T> dataClass) {
        // Convert aspects
        List<Aspect> aspects = new ArrayList<>();
        T mainAspectData = null;
        String contentType = null;

        if (dto.getAspects() != null) {
            for (Map.Entry<String, AspectDto> entry : dto.getAspects().entrySet()) {
                String name = entry.getKey();
                AspectDto a = entry.getValue();
                Object data = a.getData();

                // Try to convert to requested class if specific class requested
                if ("contentData".equals(name)) {
                    if (dataClass != null && dataClass != Object.class && data instanceof Map) {
                        try {
                            mainAspectData = objectMapper.convertValue(data, dataClass);
                        } catch (Exception e) {
                            LOG.log(Level.FINE, "Cannot convert to " + dataClass.getName(), e);
                            mainAspectData = (T) data;
                        }
                    } else {
                        mainAspectData = (T) data;
                    }
                    if (data instanceof Map<?,?> map && map.containsKey("_type")) {
                        contentType = (String) map.get("_type");
                    }
                } else {
                    aspects.add(new Aspect<>(name, data));
                }
            }
        }

        Aspect<T> mainAspect = mainAspectData != null
            ? new Aspect<>(contentType, mainAspectData, vid)
            : null;

        ContentResult.Meta meta = null;
        if (dto.getMeta() != null) {
            long modTime = parseLong(dto.getMeta().getModificationTime());
            long createTime = parseLong(dto.getMeta().getOriginalCreationTime());
            meta = new ContentResult.Meta(modTime, createTime);
        }

        return new ContentResultBuilder<T>()
            .id(vid)
            .status(Status.OK)
            .type(contentType)
            .mainAspectData(mainAspectData)
            .aspects(aspects)
            .variant(variant)
            .meta(meta)
            .build();
    }

    @SuppressWarnings("unchecked")
    private <T> ContentWriteDto contentWriteToDto(ContentWrite<T> write) {
        ContentWriteDto dto = new ContentWriteDto();

        // Set ID from origin if present
        if (write.getId() != null) {
            dto.setId(IdUtil.toVersionedIdString(write.getId()));
        }

        Map<String, AspectDto> aspects = new LinkedHashMap<>();

        // Main aspect data
        if (write.getContentData() != null) {
            AspectDto mainAspect = new AspectDto();
            mainAspect.setName("contentData");
            if (write.getContentData() instanceof Map) {
                mainAspect.setData((Map<String, Object>) write.getContentData());
            } else {
                mainAspect.setData(objectMapper.convertValue(write.getContentData(),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
            // Ensure _type is set
            if (write.getContentDataType() != null) {
                mainAspect.getData().put("_type", write.getContentDataType());
            }
            aspects.put("contentData", mainAspect);
        }

        // Additional aspects
        for (Aspect aspect : write.getAspects()) {
            AspectDto aspectDto = new AspectDto();
            aspectDto.setName(aspect.getName());
            if (aspect.getData() instanceof Map) {
                aspectDto.setData((Map<String, Object>) aspect.getData());
            } else if (aspect.getData() != null) {
                aspectDto.setData(objectMapper.convertValue(aspect.getData(),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
            aspects.put(aspect.getName(), aspectDto);
        }

        dto.setAspects(aspects);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private <T> ContentWrite<T> runPreStoreHooks(ContentWrite<T> write, Content<T> existing,
                                                  Subject subject) throws CallbackException {
        String contentType = write.getContentDataType();

        // Merge wildcard hooks with content-type-specific hooks
        List<LifecyclePreStore<?, ?>> mergedHooks = new ArrayList<>();

        // 1. Wildcard hooks first (apply to all types)
        List<LifecyclePreStore<?, ?>> wildcardHooks = preStoreHooks.get(ALL_TYPES);
        if (wildcardHooks != null) {
            mergedHooks.addAll(wildcardHooks);
        }

        // 2. Then type-specific hooks
        if (contentType != null) {
            List<LifecyclePreStore<?, ?>> typeHooks = preStoreHooks.get(contentType);
            if (typeHooks != null) {
                mergedHooks.addAll(typeHooks);
            }
        }

        if (mergedHooks.isEmpty()) return write;

        ContentWrite<T> current = write;
        for (LifecyclePreStore<?, ?> hook : mergedHooks) {
            LifecyclePreStore<T, Object> typedHook = (LifecyclePreStore<T, Object>) hook;
            LifecycleContextPreStore<Object> ctx = new LifecycleContextPreStore<>(
                this, subject, null, fileService);
            current = typedHook.preStore(current, existing, ctx);
        }
        return current;
    }

    private String extractExternalId(ContentWrite<?> write) {
        for (ContentOperation op : write.getOperations()) {
            if (op instanceof SetAliasOperation alias) {
                if (SetAliasOperation.EXTERNAL_ID.equals(alias.getNamespace())) {
                    return alias.getAlias();
                }
            }
        }
        return null;
    }

    private static long parseLong(String s) {
        try {
            return s != null ? Long.parseLong(s) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
