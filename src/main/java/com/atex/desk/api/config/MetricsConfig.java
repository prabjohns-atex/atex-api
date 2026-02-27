package com.atex.desk.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds common tags to all Micrometer metrics.
 */
@Configuration
public class MetricsConfig
{
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags()
    {
        return registry -> registry.config().commonTags("application", "desk-api");
    }
}
