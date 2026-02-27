package com.atex.onecms.search.solr;

import java.util.HashMap;
import java.util.Map;

public class SolrCoreMapper {

    private final Map<String, String> mappers;
    private final String prefix;

    public SolrCoreMapper(Map<String, String> mappers, String prefix) {
        this.mappers = mappers != null ? mappers : new HashMap<>();
        this.prefix = prefix != null ? prefix : "";
    }

    public String core(String logicalName) {
        if (mappers.containsKey(logicalName)) {
            return mappers.get(logicalName);
        }
        return prefix + logicalName;
    }
}
