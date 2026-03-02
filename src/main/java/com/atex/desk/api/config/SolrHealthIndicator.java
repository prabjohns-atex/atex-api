package com.atex.desk.api.config;

import com.atex.onecms.app.dam.solr.SolrService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actuator health indicator for Solr connectivity.
 * Returns UP if Solr responds to a lightweight ping, DOWN on error,
 * or UNKNOWN if Solr is not configured.
 */
@Component
public class SolrHealthIndicator implements HealthIndicator
{
    private static final Logger LOG = Logger.getLogger(SolrHealthIndicator.class.getName());

    private final SolrService solrService;
    private final DeskProperties deskProperties;

    public SolrHealthIndicator(@Nullable SolrService solrService, DeskProperties deskProperties)
    {
        this.solrService = solrService;
        this.deskProperties = deskProperties;
    }

    @Override
    public Health health()
    {
        if (solrService == null)
        {
            return Health.unknown()
                .withDetail("reason", "Solr not configured")
                .build();
        }

        String url = deskProperties.getSolrUrl() != null ? deskProperties.getSolrUrl() : "unknown";
        String core = deskProperties.getSolrCore() != null ? deskProperties.getSolrCore() : "unknown";

        try
        {
            String status = solrService.ping();
            return Health.up()
                .withDetail("url", url)
                .withDetail("core", core)
                .withDetail("ping", status)
                .build();
        }
        catch (Throwable e)
        {
            LOG.log(Level.WARNING, "Solr health check failed: " + e.getClass().getName() + ": " + e.getMessage(), e);
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return Health.down()
                .withDetail("url", url)
                .withDetail("core", core)
                .withDetail("error", error)
                .build();
        }
    }
}