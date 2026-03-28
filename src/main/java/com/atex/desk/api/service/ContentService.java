package com.atex.desk.api.service;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ContentHistoryDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.dto.ContentVersionInfoDto;
import com.atex.desk.api.dto.ContentWriteDto;
import com.atex.desk.api.dto.MetaDto;
import com.atex.desk.api.entity.Alias;
import com.atex.desk.api.entity.Aspect;
import com.atex.desk.api.entity.AspectLocation;
import com.atex.desk.api.entity.Content;
import com.atex.desk.api.entity.ContentAlias;
import com.atex.desk.api.entity.ContentId;
import com.atex.desk.api.entity.ContentVersion;
import com.atex.desk.api.entity.ContentView;
import com.atex.desk.api.entity.IdType;
import com.atex.desk.api.entity.View;
import com.atex.desk.api.repository.AliasRepository;
import com.atex.desk.api.repository.AspectLocationRepository;
import com.atex.desk.api.repository.AspectRepository;
import com.atex.desk.api.repository.ContentAliasRepository;
import com.atex.desk.api.repository.ContentIdRepository;
import com.atex.desk.api.repository.ContentRepository;
import com.atex.desk.api.repository.ContentVersionRepository;
import com.atex.desk.api.repository.ContentViewRepository;
import com.atex.desk.api.repository.IdTypeRepository;
import com.atex.desk.api.repository.ViewRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContentService
{
    private static final String DEFAULT_ID_TYPE = "onecms";
    private static final String VIEW_LATEST = "p.latest";
    private static final String VIEW_DELETED = "p.deleted";
    private static final String VIEW_PUBLIC = "p.public";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final IdTypeRepository idTypeRepository;
    private final ContentIdRepository contentIdRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentRepository contentRepository;
    private final AspectRepository aspectRepository;
    private final AspectLocationRepository aspectLocationRepository;
    private final ViewRepository viewRepository;
    private final ContentViewRepository contentViewRepository;
    private final AliasRepository aliasRepository;
    private final ContentAliasRepository contentAliasRepository;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator;

    // Caches for frequently-resolved lookups (Gap 9)
    private final ConcurrentHashMap<String, Integer> idTypeNameToIdCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idTypeIdToNameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> viewNameToIdCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> aliasNameToIdCache = new ConcurrentHashMap<>();

    public ContentService(IdTypeRepository idTypeRepository,
                          ContentIdRepository contentIdRepository,
                          ContentVersionRepository contentVersionRepository,
                          ContentRepository contentRepository,
                          AspectRepository aspectRepository,
                          AspectLocationRepository aspectLocationRepository,
                          ViewRepository viewRepository,
                          ContentViewRepository contentViewRepository,
                          AliasRepository aliasRepository,
                          ContentAliasRepository contentAliasRepository,
                          ObjectMapper objectMapper,
                          IdGenerator idGenerator)
    {
        this.idTypeRepository = idTypeRepository;
        this.contentIdRepository = contentIdRepository;
        this.contentVersionRepository = contentVersionRepository;
        this.contentRepository = contentRepository;
        this.aspectRepository = aspectRepository;
        this.aspectLocationRepository = aspectLocationRepository;
        this.viewRepository = viewRepository;
        this.contentViewRepository = contentViewRepository;
        this.aliasRepository = aliasRepository;
        this.contentAliasRepository = contentAliasRepository;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    // --- ID parsing ---

    /**
     * Parse a content ID string in the format "delegationId:key" or "delegationId:key:version".
     * Returns [delegationId, key] or [delegationId, key, version].
     */
    public String[] parseContentId(String idString)
    {
        if (idString == null || idString.isBlank())
        {
            throw new IllegalArgumentException("Content ID must not be empty");
        }
        String[] parts = idString.split(":", 3);
        if (parts.length < 2)
        {
            throw new IllegalArgumentException("Invalid content ID format: " + idString);
        }
        return parts;
    }

    public boolean isVersionedId(String idString)
    {
        return idString != null && idString.split(":", 4).length >= 3;
    }

    public String formatContentId(String delegationId, String key)
    {
        return delegationId + ":" + key;
    }

    public String formatVersionedId(String delegationId, String key, String version)
    {
        return delegationId + ":" + key + ":" + version;
    }

    // --- Resolve ---

    /**
     * Resolve an unversioned content ID to its latest versioned ID.
     * Returns the versioned ID string, or empty if not found.
     */
    public Optional<String> resolve(String delegationId, String key)
    {
        return resolve(delegationId, key, VIEW_LATEST);
    }

    /**
     * Resolve an unversioned content ID to the version in the given view.
     * p.latest is special: it returns the absolute latest version by versionid DESC,
     * without requiring a view assignment (matching reference ADM Content Service behavior).
     */
    public Optional<String> resolve(String delegationId, String key, String viewName)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        // p.latest is a symbolic pointer — just fetch the absolute latest version
        if (VIEW_LATEST.equals(viewName))
        {
            return contentVersionRepository
                .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key)
                .stream()
                .findFirst()
                .map(cv -> formatVersionedId(delegationId, key, cv.getVersion()));
        }

        // For other views, require an actual view assignment
        Integer viewId = resolveViewId(viewName);
        if (viewId == null) return Optional.empty();

        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);

        for (ContentVersion cv : versions)
        {
            Optional<ContentView> cvw = contentViewRepository
                .findByVersionIdAndViewId(cv.getVersionId(), viewId);
            if (cvw.isPresent())
            {
                return Optional.of(formatVersionedId(delegationId, key, cv.getVersion()));
            }
        }
        return Optional.empty();
    }

    /**
     * Get the current versioned ID string for a content item via its p.latest view.
     * Used for If-Match / ETag validation on PUT and DELETE.
     */
    public Optional<String> getCurrentVersion(String delegationId, String key)
    {
        return resolve(delegationId, key, VIEW_LATEST);
    }

    /**
     * Resolve an external ID to content ID.
     */
    public Optional<String> resolveExternalId(String externalId)
    {
        Optional<ContentAlias> alias = contentAliasRepository
            .findByAliasNameAndValue("externalId", externalId);

        return alias.map(ca -> {
            String delegationId = resolveIdTypeName(ca.getIdtype());
            return formatContentId(delegationId, ca.getId());
        });
    }

    // --- Get content ---

    /**
     * Get content by versioned ID. Returns the full content result with aspects.
     */
    @Transactional(readOnly = true)
    public Optional<ContentResultDto> getContent(String delegationId, String key, String version)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        ContentVersion cv = contentVersionRepository
            .findByIdtypeAndIdAndVersion(idtype, key, version)
            .orElse(null);
        if (cv == null) return Optional.empty();

        return buildContentResult(delegationId, key, cv);
    }

    /**
     * Get content history (all versions) for an unversioned content ID.
     */
    @Transactional(readOnly = true)
    public Optional<ContentHistoryDto> getHistory(String delegationId, String key)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
        if (versions.isEmpty()) return Optional.empty();

        // Build view name lookup
        Map<Integer, String> viewNameMap = new LinkedHashMap<>();
        for (View v : viewRepository.findAll())
        {
            viewNameMap.put(v.getViewId(), v.getName());
        }

        // Batch-load all view assignments for all versions (Gap 8 — avoids N+1)
        List<Integer> versionIds = versions.stream()
            .map(ContentVersion::getVersionId)
            .toList();
        List<ContentView> allViews = contentViewRepository.findByVersionIdIn(versionIds);

        // Group views by versionId
        Map<Integer, List<ContentView>> viewsByVersion = new LinkedHashMap<>();
        for (ContentView cvw : allViews)
        {
            viewsByVersion.computeIfAbsent(cvw.getVersionId(), k -> new ArrayList<>()).add(cvw);
        }

        List<ContentVersionInfoDto> versionInfos = new ArrayList<>();
        for (ContentVersion cv : versions)
        {
            ContentVersionInfoDto info = new ContentVersionInfoDto();
            info.setVersion(formatVersionedId(delegationId, key, cv.getVersion()));
            info.setCreationTime(cv.getCreatedAt().toEpochMilli());
            info.setCreatorId(cv.getCreatedBy());

            List<ContentView> cvws = viewsByVersion.getOrDefault(cv.getVersionId(), List.of());
            List<String> viewNames = cvws.stream()
                .map(cvw -> viewNameMap.getOrDefault(cvw.getViewId(), "unknown"))
                .toList();
            info.setViews(viewNames);

            versionInfos.add(info);
        }

        ContentHistoryDto history = new ContentHistoryDto();
        history.setVersions(versionInfos);
        return Optional.of(history);
    }

    // --- Existence check (Gap 5) ---

    /**
     * Check if a content item exists without doing a full resolve.
     */
    public boolean has(String delegationId, String key)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;
        return contentIdRepository.existsByIdtypeAndId(idtype, key);
    }

    // --- Create ---

    @Transactional
    public ContentResultDto createContent(ContentWriteDto write, String userId)
    {
        // Validate inputs (Gap 6)
        if (userId == null || userId.isBlank())
        {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (write.getAspects() == null || !write.getAspects().containsKey("contentData"))
        {
            throw new IllegalArgumentException("Missing required 'contentData' aspect");
        }

        String delegationId = DEFAULT_ID_TYPE;
        String key;
        Integer idtype = resolveIdType(delegationId);

        // Parse or generate content ID
        if (write.getId() != null && !write.getId().isBlank())
        {
            String[] parts = parseContentId(write.getId());
            delegationId = parts[0];
            key = parts[1];
            idtype = resolveIdType(delegationId);
        }
        else
        {
            key = idGenerator.nextId();
        }

        Instant now = Instant.now();

        // Create the content ID entry
        ContentId contentIdEntity = new ContentId();
        contentIdEntity.setId(key);
        contentIdEntity.setIdtype(idtype);
        contentIdEntity.setCreatedAt(now);
        contentIdEntity.setCreatedBy(userId);
        contentIdRepository.save(contentIdEntity);

        // Determine content type from aspects
        String contentType = determineContentType(write);

        // Create version
        String versionStr = idGenerator.nextVersion();
        ContentVersion cv = new ContentVersion();
        cv.setIdtype(idtype);
        cv.setId(key);
        cv.setVersion(versionStr);
        cv.setCreatedAt(now);
        cv.setCreatedBy(userId);
        contentVersionRepository.save(cv);

        // Create content entry
        Content content = new Content();
        content.setVersionId(cv.getVersionId());
        content.setContentType(contentType);
        content.setCreatedAt(now);
        content.setCreatedBy(userId);
        content.setModifiedAt(now);
        content.setModifiedBy(userId);
        contentRepository.save(content);

        // Create aspects
        if (write.getAspects() != null)
        {
            for (Map.Entry<String, AspectDto> entry : write.getAspects().entrySet())
            {
                createAspectEntry(entry.getKey(), entry.getValue(), cv, content, key, userId, now);
            }
        }

        // Assign p.latest view (exclusive — remove from other versions)
        assignViewExclusive(cv.getVersionId(), VIEW_LATEST, idtype, key, userId, now);

        return buildContentResult(delegationId, key, cv).orElseThrow();
    }

    // --- Update ---

    @Transactional
    public Optional<ContentResultDto> updateContent(String delegationId, String key,
                                                     ContentWriteDto write, String userId)
    {
        return updateContent(delegationId, key, write, userId, null);
    }

    /**
     * Update content with optimistic locking.
     * @param previousVersion if non-null, validates that this matches the current latest version
     * @throws ConflictUpdateException if previousVersion doesn't match current latest
     */
    @Transactional
    public Optional<ContentResultDto> updateContent(String delegationId, String key,
                                                     ContentWriteDto write, String userId,
                                                     String previousVersion)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        // Verify content exists
        if (!contentIdRepository.existsByIdtypeAndId(idtype, key)) return Optional.empty();

        // Optimistic locking check (Gap 2)
        if (previousVersion != null)
        {
            Optional<String> currentVersion = getCurrentVersion(delegationId, key);
            if (currentVersion.isPresent() && !currentVersion.get().equals(previousVersion))
            {
                throw new ConflictUpdateException(currentVersion.get(), previousVersion);
            }
        }

        // Build previous aspect hash map for MD5 reuse (Gap 1)
        Map<String, AspectHash> previousAspectHashes = Map.of();
        ContentVersion prevVersion = contentVersionRepository
            .findFirstByIdtypeAndIdOrderByVersionIdDesc(idtype, key)
            .orElse(null);
        if (prevVersion != null)
        {
            previousAspectHashes = getAspectHashesForVersion(prevVersion.getVersionId());
        }

        Instant now = Instant.now();
        String contentType = determineContentType(write);

        // Create new version
        String versionStr = idGenerator.nextVersion();
        ContentVersion cv = new ContentVersion();
        cv.setIdtype(idtype);
        cv.setId(key);
        cv.setVersion(versionStr);
        cv.setCreatedAt(now);
        cv.setCreatedBy(userId);
        contentVersionRepository.save(cv);

        // Create content entry for new version
        Content content = new Content();
        content.setVersionId(cv.getVersionId());
        content.setContentType(contentType);
        content.setCreatedAt(now);
        content.setCreatedBy(userId);
        content.setModifiedAt(now);
        content.setModifiedBy(userId);
        contentRepository.save(content);

        // Create aspects (with MD5 reuse for unchanged aspects)
        if (write.getAspects() != null)
        {
            for (Map.Entry<String, AspectDto> entry : write.getAspects().entrySet())
            {
                createAspectEntry(entry.getKey(), entry.getValue(), cv, content,
                    key, userId, now, previousAspectHashes);
            }
        }

        // Move p.latest view to new version (exclusive — bulk remove then insert)
        assignViewExclusive(cv.getVersionId(), VIEW_LATEST, idtype, key, userId, now);

        return buildContentResult(delegationId, key, cv);
    }

    // --- Delete ---

    @Transactional
    public boolean deleteContent(String delegationId, String key, String userId)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;

        if (!contentIdRepository.existsByIdtypeAndId(idtype, key)) return false;

        // Find the latest version and move it from p.latest to p.deleted
        ContentVersion cv = contentVersionRepository
            .findFirstByIdtypeAndIdOrderByVersionIdDesc(idtype, key)
            .orElse(null);
        if (cv != null)
        {
            Instant now = Instant.now();
            removeView(cv.getVersionId(), VIEW_LATEST);
            assignViewExclusive(cv.getVersionId(), VIEW_DELETED, idtype, key, userId, now);
        }

        return true;
    }

    // --- Publish / Unpublish ---

    /**
     * Publish content by assigning p.public view to the latest version.
     */
    @Transactional
    public boolean publishContent(String delegationId, String key, String userId)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;

        Optional<String> latestVersionedId = resolve(delegationId, key, VIEW_LATEST);
        if (latestVersionedId.isEmpty()) return false;

        String[] parts = parseContentId(latestVersionedId.get());
        ContentVersion cv = contentVersionRepository
            .findByIdtypeAndIdAndVersion(idtype, key, parts[2])
            .orElse(null);
        if (cv == null) return false;

        Instant now = Instant.now();
        assignViewExclusive(cv.getVersionId(), VIEW_PUBLIC, idtype, key, userId, now);
        return true;
    }

    /**
     * Unpublish content by removing p.public view from all versions.
     */
    @Transactional
    public boolean unpublishContent(String delegationId, String key, String userId)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;

        Integer publicViewId = resolveViewId(VIEW_PUBLIC);
        if (publicViewId == null) return false;

        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
        if (versions.isEmpty()) return false;

        boolean removed = false;
        for (ContentVersion v : versions)
        {
            Optional<ContentView> cvw = contentViewRepository
                .findByVersionIdAndViewId(v.getVersionId(), publicViewId);
            if (cvw.isPresent())
            {
                contentViewRepository.deleteByVersionIdAndViewId(v.getVersionId(), publicViewId);
                removed = true;
            }
        }
        return removed;
    }

    // --- Purge (hard delete) ---

    /**
     * Permanently remove a specific version of content (hard delete).
     * Removes views, content entry, aspects (if not shared), aspect locations, and version record.
     * If this is the last version, also removes the content ID entry and all aliases.
     */
    @Transactional
    public boolean purgeVersion(String delegationId, String key, String version)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;

        ContentVersion cv = contentVersionRepository
            .findByIdtypeAndIdAndVersion(idtype, key, version)
            .orElse(null);
        if (cv == null) return false;

        // 1. Remove all view assignments for this version
        List<ContentView> views = contentViewRepository.findByVersionId(cv.getVersionId());
        for (ContentView v : views)
        {
            contentViewRepository.deleteByVersionIdAndViewId(v.getVersionId(), v.getViewId());
        }

        // 2. Get the content entry for this version
        Content content = contentRepository.findByVersionId(cv.getVersionId()).orElse(null);
        if (content != null)
        {
            // 3. Find all aspect locations for this content entry
            List<AspectLocation> locations = aspectLocationRepository.findByContentId(content.getContentId());

            // 4. For each aspect, check if it's shared with other content entries
            for (AspectLocation loc : locations)
            {
                long refCount = aspectLocationRepository.countByAspectId(loc.getAspectId());
                if (refCount <= 1)
                {
                    // Not shared — safe to delete the aspect row
                    aspectRepository.deleteById(loc.getAspectId());
                }
            }

            // 5. Delete all aspect locations for this content entry
            aspectLocationRepository.deleteByContentId(content.getContentId());

            // 6. Delete the content entry
            contentRepository.deleteByVersionId(cv.getVersionId());
        }

        // 7. Delete the version record
        contentVersionRepository.deleteById(cv.getVersionId());

        // 8. If this was the last version, clean up the content ID and aliases
        List<ContentVersion> remaining = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
        if (remaining.isEmpty())
        {
            deleteAllAliases(delegationId, key);
            contentIdRepository.deleteById(key);
        }

        return true;
    }

    /**
     * Duplicate content — read existing content and create a new copy with the same aspects.
     */
    @Transactional
    public Optional<ContentResultDto> duplicateContent(String delegationId, String key, String userId)
    {
        // Resolve to latest version
        Optional<String> versionedId = resolve(delegationId, key);
        if (versionedId.isEmpty()) return Optional.empty();

        String[] parts = parseContentId(versionedId.get());
        Optional<ContentResultDto> source = getContent(parts[0], parts[1], parts[2]);
        if (source.isEmpty()) return Optional.empty();

        // Build a write DTO from the source
        ContentWriteDto write = new ContentWriteDto();
        write.setAspects(source.get().getAspects());
        return Optional.of(createContent(write, userId));
    }

    /**
     * Update a metadata dimension field on content.
     * Reads existing atex.Metadata aspect, updates the given field in dimensions, saves new version.
     */
    @Transactional
    public Optional<ContentResultDto> markAs(String delegationId, String key,
                                              String field, String value, String userId)
    {
        Optional<String> versionedId = resolve(delegationId, key);
        if (versionedId.isEmpty()) return Optional.empty();

        String[] parts = parseContentId(versionedId.get());
        Optional<ContentResultDto> existing = getContent(parts[0], parts[1], parts[2]);
        if (existing.isEmpty()) return Optional.empty();

        ContentResultDto content = existing.get();
        Map<String, AspectDto> aspects = content.getAspects();
        if (aspects == null) aspects = new LinkedHashMap<>();

        // Get or create atex.Metadata aspect
        AspectDto metadataAspect = aspects.get("atex.Metadata");
        Map<String, Object> metaData;
        if (metadataAspect != null && metadataAspect.getData() != null)
        {
            metaData = new LinkedHashMap<>(metadataAspect.getData());
        }
        else
        {
            metaData = new LinkedHashMap<>();
            metadataAspect = new AspectDto();
            metadataAspect.setName("atex.Metadata");
        }

        // Update dimensions
        @SuppressWarnings("unchecked")
        Map<String, Object> dimensions = metaData.containsKey("dimensions")
            ? new LinkedHashMap<>((Map<String, Object>) metaData.get("dimensions"))
            : new LinkedHashMap<>();
        dimensions.put(field, value);
        metaData.put("dimensions", dimensions);
        metadataAspect.setData(metaData);
        aspects.put("atex.Metadata", metadataAspect);

        ContentWriteDto write = new ContentWriteDto();
        write.setAspects(aspects);
        return updateContent(delegationId, key, write, userId);
    }

    // --- Alias creation ---

    /**
     * Create an alias for a content item in the idaliases table.
     * @param delegationId the idtype name (e.g. "onecms")
     * @param key the content key
     * @param aliasNamespace the alias namespace (e.g. "externalId", "policyId")
     * @param aliasValue the alias value
     */
    /**
     * Create an alias, detecting conflicts with existing aliases pointing to different content.
     * @throws AliasConflictException if alias already points to different content
     */
    @Transactional
    public void createAlias(String delegationId, String key, String aliasNamespace, String aliasValue)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null)
        {
            throw new IllegalArgumentException("Unknown idtype: " + delegationId);
        }

        Integer aliasId = resolveAliasId(aliasNamespace);
        if (aliasId == null)
        {
            throw new IllegalArgumentException("Unknown alias namespace: " + aliasNamespace);
        }

        // Check for conflict — alias already pointing to different content
        Optional<ContentAlias> existing = contentAliasRepository
            .findByAliasNameAndValue(aliasNamespace, aliasValue);
        if (existing.isPresent())
        {
            ContentAlias ea = existing.get();
            if (ea.getIdtype().equals(idtype) && ea.getId().equals(key))
            {
                return; // Already assigned to the same content — no-op
            }
            String conflictId = formatContentId(resolveIdTypeName(ea.getIdtype()), ea.getId());
            throw new AliasConflictException(aliasNamespace, aliasValue, conflictId);
        }

        ContentAlias ca = new ContentAlias();
        ca.setIdtype(idtype);
        ca.setId(key);
        ca.setAliasId(aliasId);
        ca.setValue(aliasValue);
        ca.setCreatedAt(Instant.now());
        ca.setCreatedBy("system");
        contentAliasRepository.save(ca);
    }

    /**
     * Delete a specific alias for a content item.
     */
    @Transactional
    public void deleteAlias(String delegationId, String key, String aliasNamespace)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return;

        Integer aliasId = resolveAliasId(aliasNamespace);
        if (aliasId == null) return;

        contentAliasRepository.deleteByIdtypeAndIdAndAliasId(idtype, key, aliasId);
    }

    /**
     * Delete all aliases for a content item.
     */
    @Transactional
    public void deleteAllAliases(String delegationId, String key)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return;

        contentAliasRepository.deleteByIdtypeAndId(idtype, key);
    }

    /**
     * Get all aliases for a content item.
     * @return Map of alias namespace name → alias value
     */
    public Map<String, String> getAliases(String delegationId, String key)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Map.of();

        List<ContentAlias> aliases = contentAliasRepository.findByIdtypeAndId(idtype, key);
        Map<String, String> result = new LinkedHashMap<>();
        for (ContentAlias ca : aliases)
        {
            String namespace = resolveAliasName(ca.getAliasId());
            if (namespace != null)
            {
                result.put(namespace, ca.getValue());
            }
        }
        return result;
    }

    /**
     * Resolve a content ID by alias lookup. Tries the given alias namespace first,
     * then falls back to other namespaces.
     * @return the canonical unversioned content ID string (e.g. "onecms:key"), or empty
     */
    public Optional<String> resolveByAlias(String aliasNamespace, String aliasValue)
    {
        Optional<ContentAlias> alias = contentAliasRepository.findByAliasNameAndValue(aliasNamespace, aliasValue);
        return alias.map(ca -> {
            String delegationName = resolveIdTypeName(ca.getIdtype());
            return formatContentId(delegationName, ca.getId());
        });
    }

    /**
     * Resolve a content ID string with fallback to alias lookup.
     * 1. Try normal delegationId → idtype resolution
     * 2. If idtype unknown, try policyId alias (for "policy:X.Y" IDs)
     * 3. Try externalId alias as final fallback
     * @return the canonical unversioned content ID string, or empty
     */
    public Optional<String> resolveWithFallback(String idString)
    {
        String[] parts = parseContentId(idString);
        String delegationId = parts[0];
        String key = parts[1];

        // 1. Try normal resolution
        Integer idtype = resolveIdType(delegationId);
        if (idtype != null)
        {
            // Known idtype — resolve normally
            Optional<String> resolved = resolve(delegationId, key);
            if (resolved.isPresent()) return resolved;
            // Content ID known but not found — don't fall through to aliases
            return Optional.empty();
        }

        // 2. Unknown idtype — try policyId alias with the full ID string
        Optional<String> byPolicyId = resolveByAlias("policyId", idString);
        if (byPolicyId.isPresent()) return byPolicyId;

        // 3. Try externalId alias as final fallback
        Optional<String> byExternalId = resolveByAlias("externalId", idString);
        if (byExternalId.isPresent()) return byExternalId;

        return Optional.empty();
    }

    // --- Internal helpers ---

    private Optional<ContentResultDto> buildContentResult(String delegationId, String key,
                                                           ContentVersion cv)
    {
        Content content = contentRepository.findByVersionId(cv.getVersionId()).orElse(null);
        if (content == null) return Optional.empty();

        // Get aspects via aspectslocations
        List<Aspect> aspects = aspectRepository.findByContentEntryId(content.getContentId());

        // Build aspect map
        Map<String, AspectDto> aspectMap = new LinkedHashMap<>();
        for (Aspect a : aspects)
        {
            AspectDto dto = new AspectDto();
            dto.setName(a.getName());
            dto.setVersion(formatVersionedId(delegationId, a.getContentId(), cv.getVersion()));
            dto.setData(parseJsonData(a.getData()));
            aspectMap.put(a.getName(), dto);
        }

        // Get original creation time
        ContentVersion firstVersion = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(cv.getIdtype(), cv.getId())
            .stream().reduce((a, b) -> b).orElse(cv); // last in desc = first created

        // Build meta with aliases (Gap 7)
        MetaDto meta = new MetaDto();
        meta.setModificationTime(String.valueOf(content.getModifiedAt().toEpochMilli()));
        meta.setOriginalCreationTime(String.valueOf(firstVersion.getCreatedAt().toEpochMilli()));
        Map<String, String> aliases = getAliases(delegationId, key);
        if (!aliases.isEmpty())
        {
            meta.setAliases(aliases);
        }

        ContentResultDto result = new ContentResultDto();
        result.setId(formatContentId(delegationId, key));
        result.setVersion(formatVersionedId(delegationId, key, cv.getVersion()));
        result.setAspects(aspectMap);
        result.setMeta(meta);

        return Optional.of(result);
    }

    /**
     * Holds the aspect ID and MD5 hash for a previously stored aspect.
     */
    private record AspectHash(Integer aspectId, String md5) {}

    /**
     * Get aspect hashes for a version (used for MD5 reuse during updates).
     */
    private Map<String, AspectHash> getAspectHashesForVersion(Integer versionId)
    {
        Content content = contentRepository.findByVersionId(versionId).orElse(null);
        if (content == null) return Map.of();

        List<Aspect> aspects = aspectRepository.findByContentEntryId(content.getContentId());
        Map<String, AspectHash> hashes = new LinkedHashMap<>();
        for (Aspect a : aspects)
        {
            hashes.put(a.getName(), new AspectHash(a.getAspectId(), a.getMd5()));
        }
        return hashes;
    }

    private void createAspectEntry(String aspectName, AspectDto aspectDto,
                                    ContentVersion cv, Content content,
                                    String contentKey, String userId, Instant now)
    {
        createAspectEntry(aspectName, aspectDto, cv, content, contentKey, userId, now, Map.of());
    }

    /**
     * Create an aspect entry, reusing the previous version's aspect if the MD5 matches.
     */
    private void createAspectEntry(String aspectName, AspectDto aspectDto,
                                    ContentVersion cv, Content content,
                                    String contentKey, String userId, Instant now,
                                    Map<String, AspectHash> previousHashes)
    {
        String jsonData;
        try
        {
            jsonData = objectMapper.writeValueAsString(aspectDto.getData());
        }
        catch (JacksonException e)
        {
            throw new IllegalArgumentException("Invalid aspect data for: " + aspectName, e);
        }

        String md5Hash = md5(sortJsonKeys(jsonData));

        // Check if we can reuse the previous version's aspect (Gap 1)
        AspectHash previous = previousHashes.get(aspectName);
        if (previous != null && previous.md5().equals(md5Hash))
        {
            // Same data — just link the existing aspect to this content entry
            AspectLocation loc = new AspectLocation();
            loc.setContentId(content.getContentId());
            loc.setAspectId(previous.aspectId());
            aspectLocationRepository.save(loc);
            return;
        }

        // Build versioned content ID string matching reference format: "delegation:key:uniqueId"
        // The reference (CmsAspectInfoMapper) parses this via CmsIdUtil.fromVersionedString()
        // which expects exactly 3 colon-separated parts.  Each aspect row gets its own unique
        // version-like suffix (the DB has a UNIQUE constraint on aspects.contentid).
        String delegationId = idTypeRepository.findById(cv.getIdtype())
            .map(idType -> idType.getName())
            .orElse("onecms");
        String aspectContentId = delegationId + ":" + cv.getId() + ":" + idGenerator.nextId();

        Aspect aspect = new Aspect();
        aspect.setVersionId(cv.getVersionId());
        aspect.setContentId(aspectContentId);
        aspect.setName(aspectName);
        aspect.setData(jsonData);
        aspect.setMd5(md5Hash);
        aspect.setCreatedAt(now);
        aspect.setCreatedBy(userId);
        aspectRepository.save(aspect);

        // Link aspect to content entry
        AspectLocation loc = new AspectLocation();
        loc.setContentId(content.getContentId());
        loc.setAspectId(aspect.getAspectId());
        aspectLocationRepository.save(loc);
    }

    /**
     * Assign a versioned content ID to a named view (public API for ViewController).
     * The versioned ID string must be in format "delegationId:key:version".
     */
    @Transactional
    public void assignToView(String versionedIdStr, String viewName, String userId)
    {
        String[] parts = parseContentId(versionedIdStr);
        if (parts.length < 3)
            throw new IllegalArgumentException("Versioned content ID required: " + versionedIdStr);

        String delegationId = parts[0];
        String key = parts[1];
        String version = parts[2];

        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) throw new IllegalArgumentException("Unknown delegation ID: " + delegationId);

        ContentVersion cv = contentVersionRepository.findByIdtypeAndIdAndVersion(idtype, key, version)
            .orElseThrow(() -> new IllegalArgumentException("Content version not found: " + versionedIdStr));

        assignViewExclusive(cv.getVersionId(), viewName, idtype, key, userId, Instant.now());
    }

    /**
     * Remove a content from a named view (public API for ViewController).
     * The content ID can be versioned or unversioned.
     */
    @Transactional
    public void removeFromView(String contentIdStr, String viewName)
    {
        String[] parts = parseContentId(contentIdStr);
        String delegationId = parts[0];
        String key = parts[1];

        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) throw new IllegalArgumentException("Unknown delegation ID: " + delegationId);

        Integer viewId = resolveViewId(viewName);
        if (viewId == null) return;

        if (parts.length >= 3)
        {
            // Versioned — remove from specific version
            String version = parts[2];
            contentVersionRepository.findByIdtypeAndIdAndVersion(idtype, key, version)
                .ifPresent(cv -> contentViewRepository.deleteByVersionIdAndViewId(cv.getVersionId(), viewId));
        }
        else
        {
            // Unversioned — remove from all versions
            List<ContentVersion> versions = contentVersionRepository.findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
            for (ContentVersion cv : versions)
            {
                contentViewRepository.deleteByVersionIdAndViewId(cv.getVersionId(), viewId);
            }
        }
    }

    /**
     * Assign a view to a version, removing it from all other versions of the same content first.
     * This ensures view exclusivity — a view can only be assigned to one version at a time.
     */
    private void assignViewExclusive(Integer versionId, String viewName,
                                      Integer idtype, String contentKey,
                                      String userId, Instant now)
    {
        Integer viewId = resolveViewId(viewName, true);
        if (viewId == null) return;

        // Bulk remove from all other versions of this content
        contentViewRepository.removeViewFromOtherVersions(viewId, idtype, contentKey, versionId);

        // Also remove from this version if already present (idempotent)
        contentViewRepository.deleteByVersionIdAndViewId(versionId, viewId);

        // Assign to the target version
        ContentView cvw = new ContentView();
        cvw.setVersionId(versionId);
        cvw.setViewId(viewId);
        cvw.setCreatedAt(now);
        cvw.setCreatedBy(userId);
        contentViewRepository.save(cvw);
    }

    private void removeView(Integer versionId, String viewName)
    {
        Integer viewId = resolveViewId(viewName);
        if (viewId == null) return;

        contentViewRepository.deleteByVersionIdAndViewId(versionId, viewId);
    }

    private String determineContentType(ContentWriteDto write)
    {
        if (write.getAspects() != null && write.getAspects().containsKey("contentData"))
        {
            Map<String, Object> data = write.getAspects().get("contentData").getData();
            if (data != null && data.containsKey("_type"))
            {
                return (String) data.get("_type");
            }
        }
        return "com.atex.standard.content.ContentBean";
    }

    public Integer resolveIdType(String delegationId)
    {
        Integer cached = idTypeNameToIdCache.get(delegationId);
        if (cached != null) return cached;

        Integer resolved = idTypeRepository.findByName(delegationId)
            .map(IdType::getId)
            .orElse(null);

        if (resolved != null)
        {
            idTypeNameToIdCache.put(delegationId, resolved);
            idTypeIdToNameCache.put(resolved, delegationId);
        }
        return resolved;
    }

    private String resolveIdTypeName(Integer idtype)
    {
        String cached = idTypeIdToNameCache.get(idtype);
        if (cached != null) return cached;

        String resolved = idTypeRepository.findById(idtype)
            .map(IdType::getName)
            .orElse(DEFAULT_ID_TYPE);

        idTypeIdToNameCache.put(idtype, resolved);
        idTypeNameToIdCache.put(resolved, idtype);
        return resolved;
    }

    private Integer resolveViewId(String viewName)
    {
        return resolveViewId(viewName, false);
    }

    private Integer resolveViewId(String viewName, boolean autoCreate)
    {
        Integer cached = viewNameToIdCache.get(viewName);
        if (cached != null) return cached;

        Integer resolved = viewRepository.findByName(viewName)
            .map(View::getViewId)
            .orElse(null);

        if (resolved == null && autoCreate)
        {
            View v = new View();
            v.setName(viewName);
            v.setCreatedAt(Instant.now());
            v.setCreatedBy("system");
            v = viewRepository.save(v);
            resolved = v.getViewId();
        }

        if (resolved != null)
        {
            viewNameToIdCache.put(viewName, resolved);
        }
        return resolved;
    }

    private final ConcurrentHashMap<Integer, String> aliasIdToNameCache = new ConcurrentHashMap<>();

    private Integer resolveAliasId(String aliasName)
    {
        Integer cached = aliasNameToIdCache.get(aliasName);
        if (cached != null) return cached;

        Integer resolved = aliasRepository.findByName(aliasName)
            .map(Alias::getAliasId)
            .orElse(null);

        if (resolved != null)
        {
            aliasNameToIdCache.put(aliasName, resolved);
            aliasIdToNameCache.put(resolved, aliasName);
        }
        return resolved;
    }

    private String resolveAliasName(Integer aliasId)
    {
        String cached = aliasIdToNameCache.get(aliasId);
        if (cached != null) return cached;

        String resolved = aliasRepository.findById(aliasId)
            .map(Alias::getName)
            .orElse(null);

        if (resolved != null)
        {
            aliasIdToNameCache.put(aliasId, resolved);
            aliasNameToIdCache.put(resolved, aliasId);
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonData(String json)
    {
        try
        {
            return objectMapper.readValue(json, MAP_TYPE);
        }
        catch (JacksonException e)
        {
            return Map.of("_raw", json);
        }
    }

    /**
     * Sort JSON keys recursively for consistent MD5 hashing.
     * Uses Jackson TreeNode to parse, sort, and re-serialize.
     */
    private String sortJsonKeys(String json)
    {
        try
        {
            tools.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject())
            {
                return objectMapper.writeValueAsString(sortNode(node));
            }
        }
        catch (Exception e)
        {
            // If parsing fails, return original — md5 will still work, just won't match reuse
        }
        return json;
    }

    private tools.jackson.databind.JsonNode sortNode(tools.jackson.databind.JsonNode node)
    {
        if (node.isObject())
        {
            tools.jackson.databind.node.ObjectNode objectNode = (tools.jackson.databind.node.ObjectNode) node;
            tools.jackson.databind.node.ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            for (var entry : objectNode.properties())
            {
                fieldNames.add(entry.getKey());
            }
            java.util.Collections.sort(fieldNames);
            for (String field : fieldNames)
            {
                sorted.set(field, sortNode(node.get(field)));
            }
            return sorted;
        }
        else if (node.isArray())
        {
            tools.jackson.databind.node.ArrayNode sortedArray = objectMapper.createArrayNode();
            for (tools.jackson.databind.JsonNode child : node)
            {
                sortedArray.add(sortNode(child));
            }
            return sortedArray;
        }
        return node;
    }

    private static String md5(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
