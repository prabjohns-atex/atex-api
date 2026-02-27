package com.atex.onecms.app.dam.util;

import java.util.HashMap;
import java.util.Map;

public class DamMapping {

    public static final HashMap<String, String> fieldMapping = new HashMap<>();

    private static final Map<String, String> FIELD_MAP = new HashMap<>();
    private static final Map<String, String> SOLR_MAP = new HashMap<>();

    static {
        put("inputTemplate", "atex_desk_inputTemplate");
        put("description", "description_atex_desk_ts");
        put("caption", "caption_atex_desk_ts");
        put("headline", "headline_atex_desk_ts");
        put("name", "name_atex_desk_ts");
        put("title", "title_atex_desk_ts");
        put("lead", "subject_atex_desk_tms");
        put("body", "body_atex_desk_ts");
        put("id", "id");
        put("pubdate", "pubdate_atex_desk_dts");
        put("publication", "publication_atex_desk_ss");
        put("edition", "edition_atex_desk_ss");
        put("zone", "zone_atex_desk_ss");
        put("section", "section_atex_desk_ss");
        put("placed", "placed_atex_desk_ss");
        put("linkedstatus", "linked_atex_desk_s");
        put("planstatus", "plan_status_atex_desk_ss");
        put("position", "position_atex_desk_ss");
        put("profile", "profile_atex_desk_ss");
        put("queue", "queue_atex_desk_s");
        put("parent", "parent_name_atex_desk_ss");
        put("childstatus", "child_atex_desk_ss");
        put("objectType", "atex_desk_objectType");
        put("partition", "tag_dimension.partition_ss");
        put("source", "source_atex_desk_s");
        put("pagelevel", "lpagelevel_atex_desk_s");
    }

    private static void put(String field, String solr) {
        fieldMapping.put(field, solr);
        FIELD_MAP.put(solr, field);
        SOLR_MAP.put(field, solr);
    }

    public static String getFieldNameBySolrFieldName(String solrFieldName) {
        return FIELD_MAP.get(solrFieldName);
    }

    public static String getSolrFieldNameByFieldName(String fieldName) {
        return SOLR_MAP.get(fieldName);
    }
}
