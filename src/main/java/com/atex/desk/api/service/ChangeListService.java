package com.atex.desk.api.service;

import com.atex.desk.api.dto.AspectDto;
import com.atex.desk.api.dto.ChangeEventDto;
import com.atex.desk.api.dto.ChangeFeedDto;
import com.atex.desk.api.dto.ContentResultDto;
import com.atex.desk.api.entity.Attribute;
import com.atex.desk.api.entity.ChangeListAttribute;
import com.atex.desk.api.entity.ChangeListEntry;
import com.atex.desk.api.entity.EventQueueEntry;
import com.atex.desk.api.entity.EventTypeEntity;
import com.atex.desk.api.entity.IdType;
import com.atex.desk.api.repository.AttributeRepository;
import com.atex.desk.api.repository.ChangeListAttributeRepository;
import com.atex.desk.api.repository.ChangeListRepository;
import com.atex.desk.api.repository.EventQueueRepository;
import com.atex.desk.api.repository.EventTypeRepository;
import com.atex.desk.api.repository.IdTypeRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ChangeListService
{
    private static final Logger LOG = Logger.getLogger(ChangeListService.class.getName());

    private final ChangeListRepository changeListRepository;
    private final ChangeListAttributeRepository changeListAttributeRepository;
    private final EventQueueRepository eventQueueRepository;
    private final EventTypeRepository eventTypeRepository;
    private final AttributeRepository attributeRepository;
    private final IdTypeRepository idTypeRepository;
    private final EntityManager entityManager;

    private final AtomicInteger commitIdSequence = new AtomicInteger(0);
    private final Map<String, Integer> eventTypeCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> attributeCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> idTypeCache = new ConcurrentHashMap<>();

    public ChangeListService(ChangeListRepository changeListRepository,
                             ChangeListAttributeRepository changeListAttributeRepository,
                             EventQueueRepository eventQueueRepository,
                             EventTypeRepository eventTypeRepository,
                             AttributeRepository attributeRepository,
                             IdTypeRepository idTypeRepository,
                             EntityManager entityManager) {
        this.changeListRepository = changeListRepository;
        this.changeListAttributeRepository = changeListAttributeRepository;
        this.eventQueueRepository = eventQueueRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.attributeRepository = attributeRepository;
        this.idTypeRepository = idTypeRepository;
        this.entityManager = entityManager;
    }

    @PostConstruct
    void init() {
        // Seed commit ID from max existing
        int maxId = changeListRepository.findMaxId().orElse(0);
        commitIdSequence.set(maxId);
        LOG.info("ChangeListService initialized with commitId sequence at " + maxId);

        // Cache event types
        for (EventTypeEntity et : eventTypeRepository.findAll()) {
            eventTypeCache.put(et.getName(), et.getEventId());
        }

        // Cache attributes
        for (Attribute attr : attributeRepository.findAll()) {
            attributeCache.put(attr.getName(), attr.getAttrId());
        }

        // Cache id types
        for (IdType it : idTypeRepository.findAll()) {
            idTypeCache.put(it.getName(), it.getId());
        }
    }

    /**
     * Record a CREATE or UPDATE event. Runs in its own transaction so content operation
     * succeeds even if recording fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String eventType, ContentResultDto result,
                            String delegationId, String contentKey, String version,
                            String userId) {
        try {
            int commitId = commitIdSequence.incrementAndGet();
            Instant now = Instant.now();

            Integer eventTypeId = eventTypeCache.get(eventType);
            if (eventTypeId == null) {
                LOG.warning("Unknown event type: " + eventType);
                return;
            }

            Integer idtypeId = idTypeCache.get(delegationId);
            if (idtypeId == null) {
                idtypeId = 1; // default to onecms
            }

            String contentType = extractContentType(result);
            String contentIdStr = contentKey;

            // Upsert: delete existing entry for this contentid, then insert new one
            changeListRepository.deleteByContentid(contentIdStr);
            entityManager.flush();

            ChangeListEntry entry = new ChangeListEntry();
            entry.setId(commitId);
            entry.setEventtype(eventTypeId);
            entry.setIdtype(idtypeId);
            entry.setContentid(contentIdStr);
            entry.setVersion(version);
            entry.setContenttype(contentType != null ? contentType : "");
            entry.setCreatedAt(now);
            entry.setCreatedBy(userId);
            entry.setModifiedAt(now);
            entry.setModifiedBy(userId);
            entry.setCommitAt(now);
            changeListRepository.save(entry);

            // Store attributes
            storeAttributes(commitId, result, userId, now);

            // Append-only audit log
            EventQueueEntry queueEntry = new EventQueueEntry();
            queueEntry.setEventtype(eventTypeId);
            queueEntry.setVersionid(commitId);
            queueEntry.setCreatedAt(now);
            queueEntry.setCreatedBy(userId);
            eventQueueRepository.save(queueEntry);

            LOG.fine(() -> "Recorded " + eventType + " event for " + delegationId + ":" + contentKey
                    + " commitId=" + commitId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record " + eventType + " event for "
                    + delegationId + ":" + contentKey, e);
            throw e;
        }
    }

    /**
     * Record a DELETE event with limited metadata.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDelete(String delegationId, String contentKey, String version,
                             String contentType, String userId) {
        try {
            int commitId = commitIdSequence.incrementAndGet();
            Instant now = Instant.now();

            Integer eventTypeId = eventTypeCache.get("DELETE");
            if (eventTypeId == null) {
                LOG.warning("DELETE event type not found in cache");
                return;
            }

            Integer idtypeId = idTypeCache.get(delegationId);
            if (idtypeId == null) {
                idtypeId = 1;
            }

            // Upsert: delete existing entry for this contentid
            changeListRepository.deleteByContentid(contentKey);
            entityManager.flush();

            ChangeListEntry entry = new ChangeListEntry();
            entry.setId(commitId);
            entry.setEventtype(eventTypeId);
            entry.setIdtype(idtypeId);
            entry.setContentid(contentKey);
            entry.setVersion(version != null ? version : "");
            entry.setContenttype(contentType != null ? contentType : "");
            entry.setCreatedAt(now);
            entry.setCreatedBy(userId);
            entry.setModifiedAt(now);
            entry.setModifiedBy(userId);
            entry.setCommitAt(now);
            changeListRepository.save(entry);

            // Store modifier attribute
            Integer modifierAttrId = attributeCache.get("modifier");
            if (modifierAttrId != null) {
                ChangeListAttribute attr = new ChangeListAttribute();
                attr.setId(commitId);
                attr.setAttrId(modifierAttrId);
                attr.setStrValue(userId);
                changeListAttributeRepository.save(attr);
            }

            // Store modificationTime attribute
            Integer modTimeAttrId = attributeCache.get("modificationTime");
            if (modTimeAttrId != null) {
                ChangeListAttribute attr = new ChangeListAttribute();
                attr.setId(commitId);
                attr.setAttrId(modTimeAttrId);
                attr.setStrValue(String.valueOf(now.toEpochMilli()));
                changeListAttributeRepository.save(attr);
            }

            // Audit log
            EventQueueEntry queueEntry = new EventQueueEntry();
            queueEntry.setEventtype(eventTypeId);
            queueEntry.setVersionid(commitId);
            queueEntry.setCreatedAt(now);
            queueEntry.setCreatedBy(userId);
            eventQueueRepository.save(queueEntry);

            LOG.fine(() -> "Recorded DELETE event for " + delegationId + ":" + contentKey
                    + " commitId=" + commitId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record DELETE event for "
                    + delegationId + ":" + contentKey, e);
            throw e;
        }
    }

    /**
     * Query the change feed with optional filters.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ChangeFeedDto queryChanges(Long commitId, Long changedSince,
                                       List<String> contentTypes, List<String> objectTypes,
                                       List<String> partitions, List<String> eventTypes,
                                       int maxRows) {
        long runTime = System.currentTimeMillis();
        int currentMax = commitIdSequence.get();

        // Build dynamic native query
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.eventtype, c.idtype, c.contentid, c.version, c.contenttype, "
            + "c.created_at, c.created_by, c.modified_at, c.modified_by, c.commit_at, "
            + "et.name AS event_name "
            + "FROM changelist c "
            + "JOIN eventtypes et ON et.eventid = c.eventtype ");

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        // Cursor filter
        if (commitId != null) {
            if (commitId > currentMax) {
                throw new InvalidCommitIdException(
                    "commitId " + commitId + " exceeds current max " + currentMax);
            }
            conditions.add("c.id > :commitId");
            params.put("commitId", commitId.intValue());
        } else if (changedSince != null) {
            conditions.add("c.commit_at >= :changedSince");
            params.put("changedSince", Instant.ofEpochMilli(changedSince));
        }

        // Event type filter
        if (eventTypes != null && !eventTypes.isEmpty()) {
            conditions.add("et.name IN :eventTypes");
            params.put("eventTypes", eventTypes);
        }

        // Content type filter
        if (contentTypes != null && !contentTypes.isEmpty()) {
            conditions.add("c.contenttype IN :contentTypes");
            params.put("contentTypes", contentTypes);
        }

        // Object type filter (requires join to attributes)
        Integer objectTypeAttrId = attributeCache.get("objectType");
        if (objectTypes != null && !objectTypes.isEmpty() && objectTypeAttrId != null) {
            sql.append("JOIN changelistattributes ca_ot ON ca_ot.id = c.id AND ca_ot.attrid = :otAttrId ");
            conditions.add("ca_ot.str_value IN :objectTypes");
            params.put("otAttrId", objectTypeAttrId);
            params.put("objectTypes", objectTypes);
        }

        // Partition filter (requires join to attributes)
        Integer partitionAttrId = attributeCache.get("partition");
        if (partitions != null && !partitions.isEmpty() && partitionAttrId != null) {
            sql.append("JOIN changelistattributes ca_pt ON ca_pt.id = c.id AND ca_pt.attrid = :ptAttrId ");
            conditions.add("ca_pt.str_value IN :partitions");
            params.put("ptAttrId", partitionAttrId);
            params.put("partitions", partitions);
        }

        if (!conditions.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", conditions)).append(" ");
        }

        sql.append("ORDER BY c.id ASC LIMIT :maxRows");

        Query query = entityManager.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> p : params.entrySet()) {
            query.setParameter(p.getKey(), p.getValue());
        }
        query.setParameter("maxRows", maxRows);

        List<Object[]> rows = query.getResultList();

        // Collect changelist IDs for bulk attribute loading
        List<Integer> changeIds = new ArrayList<>();
        for (Object[] row : rows) {
            changeIds.add(((Number) row[0]).intValue());
        }

        // Bulk load attributes for all results
        Map<Integer, Map<String, String>> attrMap = loadAttributes(changeIds);

        // Reverse attribute ID → name map
        Map<Integer, String> attrIdToName = new HashMap<>();
        for (Map.Entry<String, Integer> e : attributeCache.entrySet()) {
            attrIdToName.put(e.getValue(), e.getKey());
        }

        // Reverse idtype ID → name map
        Map<Integer, String> idTypeIdToName = new HashMap<>();
        for (Map.Entry<String, Integer> e : idTypeCache.entrySet()) {
            idTypeIdToName.put(e.getValue(), e.getKey());
        }

        // Build DTOs
        List<ChangeEventDto> events = new ArrayList<>();
        for (Object[] row : rows) {
            int id = ((Number) row[0]).intValue();
            int idtypeId = ((Number) row[2]).intValue();
            String contentid = (String) row[3];
            String ver = (String) row[4];
            String ctype = (String) row[5];
            Instant createdAt = toInstant(row[6]);
            String createdBy = (String) row[7];
            Instant modifiedAt = toInstant(row[8]);
            String modifiedBy = (String) row[9];
            Instant commitAt = toInstant(row[10]);
            String eventName = (String) row[11];

            String delegation = idTypeIdToName.getOrDefault(idtypeId, "onecms");
            Map<String, String> attrs = attrMap.getOrDefault(id, Map.of());

            ChangeEventDto dto = new ChangeEventDto();
            dto.setContentId(delegation + ":" + contentid);
            dto.setContentVersionId(delegation + ":" + contentid + ":" + ver);
            dto.setCommitId(id);
            dto.setCommitTime(commitAt != null ? commitAt.toEpochMilli() : 0);
            dto.setEventType(eventName);
            dto.setObjectType(attrs.get("objectType"));
            dto.setContentType(ctype);
            dto.setCreationTime(parseLong(attrs.get("creationTime"), createdAt != null ? createdAt.toEpochMilli() : 0));
            dto.setCreator(attrs.getOrDefault("created_by", createdBy));
            dto.setModificationTime(parseLong(attrs.get("modificationTime"), modifiedAt != null ? modifiedAt.toEpochMilli() : 0));
            dto.setModifier(attrs.getOrDefault("modifier", modifiedBy));
            dto.setSecurityParentId(attrs.get("securityParentId"));
            dto.setInsertionParentId(attrs.get("insertParentId"));

            String partition = attrs.get("partition");
            if (partition != null) {
                dto.setPartitions(List.of(partition));
            } else {
                dto.setPartitions(List.of());
            }

            events.add(dto);
        }

        ChangeFeedDto feed = new ChangeFeedDto();
        feed.setRunTime(runTime);
        feed.setMaxCommitId(currentMax);
        feed.setNumFound(events.size());
        feed.setSize(events.size());
        feed.setEvents(events);
        return feed;
    }

    // --- Private helpers ---

    private void storeAttributes(int commitId, ContentResultDto result, String userId, Instant now) {
        Map<String, AspectDto> aspects = result.getAspects();
        if (aspects == null) return;

        // Object type from contentData._type
        String objectType = extractObjectType(result);
        storeAttribute(commitId, "objectType", objectType);

        // Creator and modifier
        storeAttribute(commitId, "created_by", userId);
        storeAttribute(commitId, "modifier", userId);
        storeAttribute(commitId, "creationTime", String.valueOf(now.toEpochMilli()));
        storeAttribute(commitId, "modificationTime", String.valueOf(now.toEpochMilli()));

        // Insertion info
        AspectDto insertionInfo = aspects.get("atex.InsertionInfo");
        if (insertionInfo != null && insertionInfo.getData() != null) {
            Map<String, Object> data = insertionInfo.getData();
            Object secParent = data.get("securityParentId");
            if (secParent != null) {
                storeAttribute(commitId, "securityParentId", secParent.toString());
            }
            Object insertParent = data.get("insertParentId");
            if (insertParent != null) {
                storeAttribute(commitId, "insertParentId", insertParent.toString());
            }
        }

        // Partition from InsertionInfo or contentData
        AspectDto contentData = aspects.get("contentData");
        if (contentData != null && contentData.getData() != null) {
            Object partition = contentData.getData().get("partition");
            if (partition != null) {
                storeAttribute(commitId, "partition", partition.toString());
            }
        }
    }

    private void storeAttribute(int commitId, String attrName, String value) {
        if (value == null) return;
        Integer attrId = attributeCache.get(attrName);
        if (attrId == null) return;

        ChangeListAttribute attr = new ChangeListAttribute();
        attr.setId(commitId);
        attr.setAttrId(attrId);
        attr.setStrValue(value);
        changeListAttributeRepository.save(attr);
    }

    private Map<Integer, Map<String, String>> loadAttributes(List<Integer> changeIds) {
        Map<Integer, Map<String, String>> result = new HashMap<>();
        if (changeIds.isEmpty()) return result;

        List<ChangeListAttribute> attrs = changeListAttributeRepository.findByIdIn(changeIds);

        // Reverse attribute ID → name map
        Map<Integer, String> attrIdToName = new HashMap<>();
        for (Map.Entry<String, Integer> e : attributeCache.entrySet()) {
            attrIdToName.put(e.getValue(), e.getKey());
        }

        for (ChangeListAttribute a : attrs) {
            String name = attrIdToName.get(a.getAttrId());
            if (name != null) {
                result.computeIfAbsent(a.getId(), k -> new HashMap<>())
                    .put(name, a.getStrValue());
            }
        }
        return result;
    }

    private String extractContentType(ContentResultDto result) {
        if (result.getAspects() == null) return null;
        AspectDto contentData = result.getAspects().get("contentData");
        if (contentData == null || contentData.getData() == null) return null;
        Object type = contentData.getData().get("_type");
        return type != null ? type.toString() : null;
    }

    private String extractObjectType(ContentResultDto result) {
        String contentType = extractContentType(result);
        if (contentType == null) return null;

        // Map common content types to object types
        String lower = contentType.toLowerCase();
        if (lower.contains("image")) return "image";
        if (lower.contains("article")) return "article";
        if (lower.contains("collection")) return "collection";
        if (lower.contains("page")) return "page";
        if (lower.contains("graphic")) return "graphic";
        if (lower.contains("audio")) return "audio";
        if (lower.contains("video")) return "video";

        return contentType;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }

    private static long parseLong(String s, long defaultValue) {
        if (s == null) return defaultValue;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
