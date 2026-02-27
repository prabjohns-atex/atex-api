package com.atex.desk.api.indexing;

import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.aspects.Aspect;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composes Solr JSON documents from content results.
 * Combines logic from the original IndexComposer + DamIndexComposer + SystemFieldComposer.
 * <p>
 * Produces fields:
 * <ul>
 *   <li>System fields: id, version, type, modificationTime_dt, creationTime_dt</li>
 *   <li>Aspect fields: serialized from content aspects</li>
 *   <li>Hierarchy fields: page_ss from security parent chain + associated sites</li>
 *   <li>DAM-specific: originalCreationTime_dt from OneContentBean.creationdate</li>
 * </ul>
 */
@Component
public class DamIndexComposer {

    private static final Logger LOG = Logger.getLogger(DamIndexComposer.class.getName());
    private static final DateTimeFormatter SOLR_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Compose a Solr JSON document from a content result.
     */
    public JsonObject compose(ContentResult<Object> contentResult, ContentVersionId versionId) {
        JsonObject doc = new JsonObject();

        try {
            Content<Object> content = contentResult.getContent();
            if (content == null) return doc;

            // --- System fields ---
            doc.addProperty("id", IdUtil.toIdString(versionId.getContentId()));
            doc.addProperty("version", IdUtil.toVersionedIdString(versionId));

            String contentType = content.getContentDataType();
            if (contentType != null) {
                doc.addProperty("inputTemplate", contentType);
                // Short type name
                int lastDot = contentType.lastIndexOf('.');
                doc.addProperty("type", lastDot >= 0 ? contentType.substring(lastDot + 1) : contentType);
            }

            // Timestamps
            long now = System.currentTimeMillis();
            doc.addProperty("modificationTime_dt", formatSolrDate(now));

            if (contentResult.getMeta() != null) {
                long modTime = contentResult.getMeta().getModificationTime();
                if (modTime > 0) {
                    doc.addProperty("modificationTime_dt", formatSolrDate(modTime));
                }
                long createTime = contentResult.getMeta().getOriginalCreationTime();
                if (createTime > 0) {
                    doc.addProperty("creationTime_dt", formatSolrDate(createTime));
                }
            }

            // --- Main aspect data ---
            Object mainData = content.getContentData();
            if (mainData != null) {
                addAspectFields(doc, "contentData", mainData);

                // DAM-specific: creation date from OneContentBean
                if (mainData instanceof OneContentBean bean) {
                    Date creationDate = bean.getCreationdate();
                    if (creationDate != null) {
                        doc.addProperty("originalCreationTime_dt",
                            formatSolrDate(creationDate.getTime()));
                    }

                    // Index searchable text fields
                    addTextField(doc, "name_t", bean.getName());
                    addTextField(doc, "author_t", bean.getAuthor());
                    addTextField(doc, "section_t", bean.getSection());
                    addTextField(doc, "source_t", bean.getSource());
                    addTextField(doc, "subject_t", bean.getSubject());
                }
            }

            // --- Additional aspects ---
            for (Aspect aspect : content.getAspects()) {
                String aspectName = aspect.getName();
                Object aspectData = aspect.getData();
                if (aspectData != null) {
                    addAspectFields(doc, aspectName, aspectData);
                }
            }

            // --- Hierarchy fields (page_ss) ---
            addHierarchyFields(doc, content);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error composing Solr document", e);
        }

        return doc;
    }

    private void addAspectFields(JsonObject doc, String aspectName, Object data) {
        try {
            String json = gson.toJson(data);
            JsonElement element = JsonParser.parseString(json);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    String fieldName = entry.getKey();
                    JsonElement value = entry.getValue();

                    if (value.isJsonNull()) continue;

                    // Create Solr field name: aspectName_fieldName_solrType
                    String solrFieldName = buildSolrFieldName(aspectName, fieldName, value);
                    addSolrField(doc, solrFieldName, value);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot serialize aspect: " + aspectName, e);
        }
    }

    private String buildSolrFieldName(String aspectName, String fieldName, JsonElement value) {
        // Derive Solr type suffix from JSON value type
        String suffix;
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) {
                suffix = "_b";
            } else if (value.getAsJsonPrimitive().isNumber()) {
                suffix = "_l";
            } else {
                String strVal = value.getAsString();
                // Date detection
                if (strVal.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                    suffix = "_dt";
                } else {
                    suffix = "_t";
                }
            }
        } else if (value.isJsonArray()) {
            suffix = "_ss";
        } else {
            suffix = "_t";
        }

        // Flatten to: aspectPrefix_fieldName_suffix
        String prefix = aspectName.replace('.', '_');
        return prefix + "_" + fieldName + suffix;
    }

    private void addSolrField(JsonObject doc, String fieldName, JsonElement value) {
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) {
                doc.addProperty(fieldName, value.getAsBoolean());
            } else if (value.getAsJsonPrimitive().isNumber()) {
                doc.addProperty(fieldName, value.getAsNumber());
            } else {
                doc.addProperty(fieldName, value.getAsString());
            }
        } else if (value.isJsonArray()) {
            doc.add(fieldName, value.getAsJsonArray());
        } else if (value.isJsonObject()) {
            // Serialize complex objects as JSON string
            doc.addProperty(fieldName, gson.toJson(value));
        }
    }

    @SuppressWarnings("unchecked")
    private void addHierarchyFields(JsonObject doc, Content<Object> content) {
        Object iiData = content.getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
        if (iiData == null) return;

        JsonArray pageSs = new JsonArray();

        if (iiData instanceof InsertionInfoAspectBean ii) {
            if (ii.getSecurityParentId() != null) {
                pageSs.add(IdUtil.toIdString(ii.getSecurityParentId()));
            }
            if (ii.getInsertParentId() != null) {
                pageSs.add(ii.getInsertParentId());
            }
            List<String> sites = ii.getAssociatedSites();
            if (sites != null) {
                for (String site : sites) {
                    pageSs.add(site);
                }
            }
        } else if (iiData instanceof Map<?,?> map) {
            // Handle deserialized map form
            Object secParent = map.get("securityParentId");
            if (secParent instanceof String s) {
                pageSs.add(s);
            } else if (secParent instanceof Map<?,?> idMap) {
                String delegationId = (String) idMap.get("delegationId");
                String key = (String) idMap.get("key");
                if (delegationId != null && key != null) {
                    pageSs.add(delegationId + ":" + key);
                }
            }
            Object insertParent = map.get("insertParentId");
            if (insertParent instanceof String s) {
                pageSs.add(s);
            }
            Object sites = map.get("associatedSites");
            if (sites instanceof Collection<?> siteList) {
                for (Object site : siteList) {
                    if (site instanceof String s) {
                        pageSs.add(s);
                    }
                }
            }
        }

        if (!pageSs.isEmpty()) {
            doc.add("page_ss", pageSs);
        }
    }

    private void addTextField(JsonObject doc, String field, String value) {
        if (value != null && !value.isEmpty()) {
            doc.addProperty(field, value);
        }
    }

    private static String formatSolrDate(long millis) {
        return SOLR_DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }
}
