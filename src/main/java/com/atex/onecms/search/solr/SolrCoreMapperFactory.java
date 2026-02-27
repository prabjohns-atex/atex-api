package com.atex.onecms.search.solr;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates SolrCoreMapper. In the original system this reads from connection.properties.
 * Here we use a simple identity mapper since we configure cores via application.properties.
 */
public class SolrCoreMapperFactory {

    public static SolrCoreMapper create() {
        return new SolrCoreMapper(new HashMap<>(), "");
    }

    public static SolrCoreMapper create(Map<String, String> mappings) {
        return new SolrCoreMapper(mappings, "");
    }
}
