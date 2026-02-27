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
     */
    public Optional<String> resolve(String delegationId, String key, String viewName)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        View view = viewRepository.findByName(viewName).orElse(null);
        if (view == null) return Optional.empty();

        // Find versions for this content that have the requested view assigned
        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);

        for (ContentVersion cv : versions)
        {
            Optional<ContentView> cvw = contentViewRepository
                .findByVersionIdAndViewId(cv.getVersionId(), view.getViewId());
            if (cvw.isPresent())
            {
                return Optional.of(formatVersionedId(delegationId, key, cv.getVersion()));
            }
        }
        return Optional.empty();
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

        List<ContentVersionInfoDto> versionInfos = new ArrayList<>();
        for (ContentVersion cv : versions)
        {
            ContentVersionInfoDto info = new ContentVersionInfoDto();
            info.setVersion(formatVersionedId(delegationId, key, cv.getVersion()));
            info.setCreationTime(cv.getCreatedAt().toEpochMilli());
            info.setCreatorId(cv.getCreatedBy());

            List<ContentView> cvws = contentViewRepository.findByVersionId(cv.getVersionId());
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

    // --- Create ---

    @Transactional
    public ContentResultDto createContent(ContentWriteDto write, String userId)
    {
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

        // Assign p.latest view
        assignView(cv.getVersionId(), VIEW_LATEST, userId, now);

        return buildContentResult(delegationId, key, cv).orElseThrow();
    }

    // --- Update ---

    @Transactional
    public Optional<ContentResultDto> updateContent(String delegationId, String key,
                                                     ContentWriteDto write, String userId)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return Optional.empty();

        // Verify content exists
        if (!contentIdRepository.existsById(key)) return Optional.empty();

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

        // Create aspects
        if (write.getAspects() != null)
        {
            for (Map.Entry<String, AspectDto> entry : write.getAspects().entrySet())
            {
                createAspectEntry(entry.getKey(), entry.getValue(), cv, content, key, userId, now);
            }
        }

        // Move p.latest view to new version
        reassignView(idtype, key, VIEW_LATEST, cv.getVersionId(), userId, now);

        return buildContentResult(delegationId, key, cv);
    }

    // --- Delete ---

    @Transactional
    public boolean deleteContent(String delegationId, String key, String userId)
    {
        Integer idtype = resolveIdType(delegationId);
        if (idtype == null) return false;

        if (!contentIdRepository.existsById(key)) return false;

        // Assign p.deleted view to latest version, remove p.latest
        Optional<String> latestVersionId = resolve(delegationId, key, VIEW_LATEST);
        if (latestVersionId.isPresent())
        {
            String[] parts = parseContentId(latestVersionId.get());
            ContentVersion cv = contentVersionRepository
                .findByIdtypeAndIdAndVersion(idtype, key, parts[2])
                .orElse(null);
            if (cv != null)
            {
                Instant now = Instant.now();
                removeView(cv.getVersionId(), VIEW_LATEST);
                assignView(cv.getVersionId(), VIEW_DELETED, userId, now);
            }
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
        // Remove any existing p.public assignment for this content
        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
        View publicView = viewRepository.findByName(VIEW_PUBLIC).orElse(null);
        if (publicView != null)
        {
            for (ContentVersion v : versions)
            {
                contentViewRepository.deleteByVersionIdAndViewId(v.getVersionId(), publicView.getViewId());
            }
        }

        assignView(cv.getVersionId(), VIEW_PUBLIC, userId, now);
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

        View publicView = viewRepository.findByName(VIEW_PUBLIC).orElse(null);
        if (publicView == null) return false;

        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, key);
        if (versions.isEmpty()) return false;

        boolean removed = false;
        for (ContentVersion v : versions)
        {
            Optional<ContentView> cvw = contentViewRepository
                .findByVersionIdAndViewId(v.getVersionId(), publicView.getViewId());
            if (cvw.isPresent())
            {
                contentViewRepository.deleteByVersionIdAndViewId(v.getVersionId(), publicView.getViewId());
                removed = true;
            }
        }
        return removed;
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

        // Build meta
        MetaDto meta = new MetaDto();
        meta.setModificationTime(String.valueOf(content.getModifiedAt().toEpochMilli()));
        meta.setOriginalCreationTime(String.valueOf(firstVersion.getCreatedAt().toEpochMilli()));

        ContentResultDto result = new ContentResultDto();
        result.setId(formatContentId(delegationId, key));
        result.setVersion(formatVersionedId(delegationId, key, cv.getVersion()));
        result.setAspects(aspectMap);
        result.setMeta(meta);

        return Optional.of(result);
    }

    private void createAspectEntry(String aspectName, AspectDto aspectDto,
                                    ContentVersion cv, Content content,
                                    String contentKey, String userId, Instant now)
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

        String md5Hash = md5(jsonData);
        String aspectContentId = idGenerator.nextId();

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

    private void assignView(Integer versionId, String viewName, String userId, Instant now)
    {
        View view = viewRepository.findByName(viewName).orElse(null);
        if (view == null) return;

        ContentView cvw = new ContentView();
        cvw.setVersionId(versionId);
        cvw.setViewId(view.getViewId());
        cvw.setCreatedAt(now);
        cvw.setCreatedBy(userId);
        contentViewRepository.save(cvw);
    }

    private void removeView(Integer versionId, String viewName)
    {
        View view = viewRepository.findByName(viewName).orElse(null);
        if (view == null) return;

        contentViewRepository.deleteByVersionIdAndViewId(versionId, view.getViewId());
    }

    private void reassignView(Integer idtype, String contentKey, String viewName,
                               Integer newVersionId, String userId, Instant now)
    {
        View view = viewRepository.findByName(viewName).orElse(null);
        if (view == null) return;

        // Remove existing view assignment for any version of this content
        List<ContentVersion> versions = contentVersionRepository
            .findByIdtypeAndIdOrderByVersionIdDesc(idtype, contentKey);
        for (ContentVersion v : versions)
        {
            contentViewRepository.deleteByVersionIdAndViewId(v.getVersionId(), view.getViewId());
        }

        // Assign to new version
        ContentView cvw = new ContentView();
        cvw.setVersionId(newVersionId);
        cvw.setViewId(view.getViewId());
        cvw.setCreatedAt(now);
        cvw.setCreatedBy(userId);
        contentViewRepository.save(cvw);
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

    private Integer resolveIdType(String delegationId)
    {
        return idTypeRepository.findByName(delegationId)
            .map(IdType::getId)
            .orElse(null);
    }

    private String resolveIdTypeName(Integer idtype)
    {
        return idTypeRepository.findById(idtype)
            .map(IdType::getName)
            .orElse(DEFAULT_ID_TYPE);
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
