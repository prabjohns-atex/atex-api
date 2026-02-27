package com.atex.desk.api.config;

import com.atex.onecms.app.dam.solr.SolrService;
import com.polopoly.search.solr.SolrServerUrl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a SolrService Spring bean when Solr URL is configured.
 */
@Configuration
public class SolrConfig {

    @Bean
    @ConditionalOnProperty(name = "desk.solr-url", matchIfMissing = false)
    public SolrService solrService(DeskProperties deskProperties) {
        String solrUrl = deskProperties.getSolrUrl();
        if (solrUrl == null || solrUrl.isBlank()) {
            return null;
        }
        return new SolrService(new SolrServerUrl(solrUrl), deskProperties.getSolrCore());
    }
}
