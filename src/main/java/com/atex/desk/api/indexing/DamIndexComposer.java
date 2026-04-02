package com.atex.desk.api.indexing;

import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.app.dam.standard.aspects.PremiumTypeSupport;
import com.atex.onecms.app.dam.util.DamMapping;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WebContentStatusAspectBean;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.plugins.structured.text.StructuredText;
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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Composes Solr JSON documents from content results.
 * Combines logic from the original IndexComposer + DamIndexComposer + SystemFieldComposer.
 * <p>
 * Uses explicit field-name-to-Solr-field mappings rather than runtime type guessing.
 * Fields not in the mapping are skipped (not indexed) to avoid mistyped field names.
 * <p>
 * Produces fields:
 * <ul>
 *   <li>System fields: id, version, type, modificationTime_dt, creationTime_dt</li>
 *   <li>Content data fields: mapped via DamMapping + ADDITIONAL_FIELD_MAP</li>
 *   <li>Workflow status fields: contentState_s, webStatus_s, web_content_status_attribute_ss</li>
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
     * Additional contentData field mappings not covered by DamMapping.
     * These map bean field names to their Solr field names.
     */
    private static final Map<String, String> ADDITIONAL_FIELD_MAP = Map.ofEntries(
        // OneContentBean fields
        Map.entry("contentType", "contentType_s"),
        Map.entry("creationdate", "creationdate_atex_desk_dt"),
        Map.entry("author", "author_atex_desk_ts"),
        Map.entry("subject", "subject_atex_desk_ts"),
        Map.entry("channel", "channel_atex_desk_s"),
        Map.entry("newsId", "newsId_atex_desk_s"),
        Map.entry("words", "words_l"),
        Map.entry("chars", "chars_l"),
        Map.entry("markForArchive", "markForArchive_b"),

        // OneArchiveBean fields
        Map.entry("lastPublication", "lastPublication_atex_desk_s"),
        Map.entry("lastEdition", "lastEdition_atex_desk_s"),
        Map.entry("lastPage", "lastPage_atex_desk_s"),
        Map.entry("lastPubdate", "lastPubdate_atex_desk_dt"),
        Map.entry("lastPagelevel", "lastPagelevel_atex_desk_s"),
        Map.entry("lastSection", "lastSection_atex_desk_s"),
        Map.entry("domain", "domain_atex_desk_s"),
        Map.entry("legacyid", "legacyid_atex_desk_s"),
        Map.entry("legacyUrl", "legacyUrl_atex_desk_s"),
        Map.entry("related", "related_ss"),

        // OneArticleBean fields
        Map.entry("byline", "byline_atex_desk_ts"),
        Map.entry("publishingTime", "publishingTime_atex_desk_dt"),
        Map.entry("publishingUpdateTime", "publishingUpdateTime_atex_desk_dt"),
        Map.entry("premiumContent", "premiumContent_b"),
        Map.entry("premiumType", "premiumtype_atex_desk_s"),
        Map.entry("priority", "priority_l"),
        Map.entry("publicationDate", "publicationDate_atex_desk_dt"),
        Map.entry("hold", "hold_b"),
        Map.entry("printFirst", "printFirst_b"),
        Map.entry("webArticleType", "webArticleType_atex_desk_s"),
        Map.entry("teaserHeadline", "teaserHeadline_atex_desk_ts"),
        Map.entry("teaserText", "teaserText_atex_desk_ts"),
        Map.entry("levelId", "levelId_atex_desk_s"),
        Map.entry("noIndex", "noIndex_b"),
        Map.entry("editor", "editor_atex_desk_s"),
        Map.entry("desk", "desk_atex_desk_s"),
        Map.entry("budgetHead", "budgetHead_atex_desk_ts"),

        // OneImageBean fields
        Map.entry("title", "title_atex_desk_ts"),
        Map.entry("rights", "rights_atex_desk_s"),
        Map.entry("credit", "credit_atex_desk_ts"),
        Map.entry("location", "location_atex_desk_ts"),
        Map.entry("person", "person_atex_desk_ts"),
        Map.entry("place", "place_atex_desk_ts"),
        Map.entry("reporter", "reporter_atex_desk_ts"),
        Map.entry("instructions", "instructions_atex_desk_ts"),
        Map.entry("alternativeText", "alternativeText_atex_desk_ts"),
        Map.entry("width", "width_l"),
        Map.entry("height", "height_l")
    );

    /**
     * StructuredText fields: field name -> Solr field name.
     * These are extracted by reading the .text subfield from the StructuredText JSON object.
     */
    private static final Map<String, String> STRUCTURED_TEXT_FIELDS = Map.of(
        "headline", "headline_atex_desk_ts",
        "lead", "subject_atex_desk_tms",
        "body", "body_atex_desk_ts",
        "caption", "caption_atex_desk_ts",
        "teaserTitle", "teaserTitle_atex_desk_ts",
        "subTitle", "subTitle_atex_desk_ts"
    );

    /**
     * Fields that should be skipped even if they appear in the JSON.
     * These are internal/structural fields or complex objects that should not be indexed.
     */
    private static final Set<String> SKIP_FIELDS = Set.of(
        "_type", "securityParentId", "propertyBag",
        "aceSlugInfo", "aceMigrationInfo", "timeState", "audioAI",
        "images", "ogImages", "resources", "relatedArticles", "authors",
        "topElement", "teaserImage", "linkPath", "relatedSections",
        "locations", "ogTitle", "ogDescription", "sponsorLogo",
        "archiveComment", "imageContentText", "clippingPath",
        "initialisedFromPrint", "showAuthorOnWeb", "pushNotification",
        "minorChange", "oneTimeUse", "noUsePrint", "noUseWeb",
        "useWatermark", "datePhotographTaken",
        "factsHeading", "factsBody", "mediaEmbed", "allowComments", "rating",
        "topMediaCaption", "topMediaAltText", "publicationLink", "overTitle",
        "teaserSummary", "socialTitle", "socialDescription",
        "seoTitle", "seoDescription", "seoScore", "keyPhrase",
        "editorialNotes", "webStatement", "licensorURL"
    );

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

            // --- Main aspect data (contentData) ---
            Object mainData = content.getContentData();
            if (mainData != null) {
                addContentDataFields(doc, mainData);

                // DAM-specific: creation date from OneContentBean
                if (mainData instanceof OneContentBean bean) {
                    Date creationDate = bean.getCreationdate();
                    if (creationDate != null) {
                        doc.addProperty("originalCreationTime_dt",
                            formatSolrDate(creationDate.getTime()));
                    }

                    // Index searchable text fields directly from bean
                    addTextField(doc, "name_t", bean.getName());
                    addTextField(doc, "name_atex_desk_ss", bean.getName());
                    addTextField(doc, "author_t", bean.getAuthor());
                    addTextField(doc, "section_t", bean.getSection());
                    addTextField(doc, "source_t", bean.getSource());
                    addTextField(doc, "subject_t", bean.getSubject());
                    addTextField(doc, "channel_atex_desk_s", bean.getChannel());

                    // Premium type (from PremiumTypeSupport interface)
                    if (mainData instanceof PremiumTypeSupport pts) {
                        addTextField(doc, "premiumtype_atex_desk_s", pts.getPremiumType());
                    }
                }
            }

            // --- Additional aspects (only known fields) ---
            for (Aspect aspect : content.getAspects()) {
                String aspectName = aspect.getName();
                Object aspectData = aspect.getData();
                if (aspectData == null) continue;

                addKnownAspectFields(doc, aspectName, aspectData);
            }

            // --- Hierarchy fields (page_ss) ---
            addHierarchyFields(doc, content);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error composing Solr document", e);
        }

        return doc;
    }

    /**
     * Index contentData fields using explicit mappings.
     * Serializes the bean to JSON, then maps each known field to its Solr field name.
     * Unknown fields are skipped.
     */
    private void addContentDataFields(JsonObject doc, Object data) {
        try {
            String json = gson.toJson(data);
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) return;

            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String fieldName = entry.getKey();
                JsonElement value = entry.getValue();

                if (value.isJsonNull()) continue;
                if (SKIP_FIELDS.contains(fieldName)) continue;

                // 1. StructuredText fields: extract .text subfield
                if (STRUCTURED_TEXT_FIELDS.containsKey(fieldName)) {
                    String solrName = STRUCTURED_TEXT_FIELDS.get(fieldName);
                    if (value.isJsonObject()) {
                        JsonObject stObj = value.getAsJsonObject();
                        JsonElement textEl = stObj.get("text");
                        if (textEl != null && textEl.isJsonPrimitive()) {
                            String text = textEl.getAsString();
                            if (!text.isEmpty()) {
                                doc.addProperty(solrName, text);
                            }
                        }
                    } else if (value.isJsonPrimitive()) {
                        // In case it was serialized as a plain string
                        String text = value.getAsString();
                        if (!text.isEmpty()) {
                            doc.addProperty(solrName, text);
                        }
                    }
                    continue;
                }

                // 2. DamMapping known fields
                String solrName = DamMapping.getSolrFieldNameByFieldName(fieldName);
                if (solrName != null) {
                    addSolrValue(doc, solrName, value);
                    continue;
                }

                // 3. Additional field map
                solrName = ADDITIONAL_FIELD_MAP.get(fieldName);
                if (solrName != null) {
                    addSolrValue(doc, solrName, value);
                    continue;
                }

                // 4. Unknown field -- skip (don't index)
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Skipping unknown contentData field: " + fieldName);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot serialize contentData", e);
        }
    }

    /**
     * Index only specific known fields from non-contentData aspects.
     */
    private void addKnownAspectFields(JsonObject doc, String aspectName, Object aspectData) {
        switch (aspectName) {
            case WFContentStatusAspectBean.ASPECT_NAME -> {
                // atex.WFContentStatus: index status ID as contentState_s
                if (aspectData instanceof WFContentStatusAspectBean wfBean) {
                    addStatusFields(doc, wfBean.getStatus(), "contentState_s");
                } else {
                    addStatusFieldsFromJson(doc, aspectData, "contentState_s");
                }
            }
            case WebContentStatusAspectBean.ASPECT_NAME -> {
                // atex.WebContentStatus: index status ID as webStatus_s and attributes
                if (aspectData instanceof WebContentStatusAspectBean wcsBean) {
                    addStatusFields(doc, wcsBean.getStatus(), "webStatus_s");
                    addStatusAttributes(doc, wcsBean.getStatus());
                } else {
                    addStatusFieldsFromJson(doc, aspectData, "webStatus_s");
                    addStatusAttributesFromJson(doc, aspectData);
                }
            }
            case InsertionInfoAspectBean.ASPECT_NAME -> {
                // Handled separately in addHierarchyFields
            }
            default -> {
                // Other aspects: skip (don't index arbitrary aspect data)
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Skipping non-indexed aspect: " + aspectName);
                }
            }
        }
    }

    private void addStatusFields(JsonObject doc, WFStatusBean status, String solrFieldName) {
        if (status != null && status.getStatusID() != null && !status.getStatusID().isEmpty()) {
            doc.addProperty(solrFieldName, status.getStatusID());
        }
    }

    private void addStatusAttributes(JsonObject doc, WFStatusBean status) {
        if (status != null && status.getStatusID() != null && !status.getStatusID().isEmpty()) {
            List<String> attrs = status.getAttributes();
            if (attrs != null && !attrs.isEmpty()) {
                JsonArray attrArray = new JsonArray();
                attrs.forEach(attrArray::add);
                doc.add("web_content_status_attribute_ss", attrArray);
            }
        }
    }

    /**
     * Extract status fields from a JSON-serialized status aspect (when not typed).
     */
    private void addStatusFieldsFromJson(JsonObject doc, Object aspectData, String solrFieldName) {
        try {
            String json = gson.toJson(aspectData);
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            JsonElement statusEl = obj.get("status");
            if (statusEl != null && statusEl.isJsonObject()) {
                JsonObject statusObj = statusEl.getAsJsonObject();
                JsonElement statusID = statusObj.get("statusID");
                if (statusID != null && statusID.isJsonPrimitive()) {
                    String id = statusID.getAsString();
                    if (!id.isEmpty()) {
                        doc.addProperty(solrFieldName, id);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot extract status from aspect", e);
        }
    }

    /**
     * Extract status attributes from a JSON-serialized status aspect (when not typed).
     */
    private void addStatusAttributesFromJson(JsonObject doc, Object aspectData) {
        try {
            String json = gson.toJson(aspectData);
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            JsonElement statusEl = obj.get("status");
            if (statusEl != null && statusEl.isJsonObject()) {
                JsonObject statusObj = statusEl.getAsJsonObject();
                JsonElement statusID = statusObj.get("statusID");
                if (statusID == null || !statusID.isJsonPrimitive() || statusID.getAsString().isEmpty()) {
                    return;
                }
                JsonElement attrsEl = statusObj.get("attributes");
                if (attrsEl != null && attrsEl.isJsonArray()) {
                    JsonArray attrs = attrsEl.getAsJsonArray();
                    if (!attrs.isEmpty()) {
                        doc.add("web_content_status_attribute_ss", attrs);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot extract status attributes from aspect", e);
        }
    }

    /**
     * Add a value to the Solr doc, handling primitives and arrays.
     * Date fields (suffix _dt or _dts) are formatted if the value is a number (epoch millis).
     */
    private void addSolrValue(JsonObject doc, String solrFieldName, JsonElement value) {
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) {
                doc.addProperty(solrFieldName, value.getAsBoolean());
            } else if (value.getAsJsonPrimitive().isNumber()) {
                // Date fields stored as epoch millis need conversion
                if (solrFieldName.endsWith("_dt") || solrFieldName.endsWith("_dts")) {
                    long millis = value.getAsLong();
                    if (millis > 0) {
                        doc.addProperty(solrFieldName, formatSolrDate(millis));
                    }
                } else {
                    doc.addProperty(solrFieldName, value.getAsNumber());
                }
            } else {
                String str = value.getAsString();
                if (!str.isEmpty()) {
                    doc.addProperty(solrFieldName, str);
                }
            }
        } else if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            if (!arr.isEmpty()) {
                doc.add(solrFieldName, arr);
            }
        }
        // Complex objects are not indexed as Solr fields
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
