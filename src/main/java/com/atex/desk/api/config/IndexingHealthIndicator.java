package com.atex.desk.api.config;

import com.atex.desk.api.entity.IndexerState;
import com.atex.desk.api.repository.IndexerStateRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actuator health indicator for the background indexing system.
 * Reports UP if the live indexer has updated recently, DOWN if stale,
 * UNKNOWN if indexing is not configured, or OUT_OF_SERVICE if paused.
 */
@Component
public class IndexingHealthIndicator implements HealthIndicator
{
    private static final Logger LOG = Logger.getLogger(IndexingHealthIndicator.class.getName());
    private static final String LIVE_INDEXER_ID = "solr";

    /**
     * If the live indexer hasn't updated in this long, consider it stale.
     * This should be comfortably above the poll interval (default 2s) to allow
     * for idle periods when no content is being written.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final IndexerStateRepository indexerStateRepository;

    public IndexingHealthIndicator(IndexerStateRepository indexerStateRepository)
    {
        this.indexerStateRepository = indexerStateRepository;
    }

    @Override
    public Health health()
    {
        try
        {
            Optional<IndexerState> opt = indexerStateRepository.findByIndexerId(LIVE_INDEXER_ID);
            if (opt.isEmpty())
            {
                return Health.unknown()
                    .withDetail("reason", "Live indexer state not found")
                    .build();
            }

            IndexerState state = opt.get();
            String status = state.getStatus();

            if ("PAUSED".equals(status))
            {
                return Health.outOfService()
                    .withDetail("status", "PAUSED")
                    .withDetail("cursor", state.getLastCursor())
                    .withDetail("updatedAt", formatInstant(state.getUpdatedAt()))
                    .build();
            }

            Instant updatedAt = state.getUpdatedAt();
            Instant now = Instant.now();
            long staleSeconds = updatedAt != null ? Duration.between(updatedAt, now).getSeconds() : -1;

            var builder = (staleSeconds >= 0 && staleSeconds < STALE_THRESHOLD.getSeconds())
                ? Health.up()
                : Health.down();

            builder.withDetail("status", status)
                .withDetail("cursor", state.getLastCursor())
                .withDetail("updatedAt", formatInstant(updatedAt))
                .withDetail("staleSec", staleSeconds);

            if (state.getLockedBy() != null)
            {
                builder.withDetail("lockedBy", state.getLockedBy());
            }
            if (state.getErrorCount() > 0)
            {
                builder.withDetail("errorCount", state.getErrorCount());
            }

            return builder.build();
        }
        catch (Throwable e)
        {
            LOG.log(Level.WARNING, "Indexing health check failed", e);
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return Health.down()
                .withDetail("error", error)
                .build();
        }
    }

    private static String formatInstant(Instant instant)
    {
        return instant != null ? instant.toString() : "never";
    }
}