package com.atex.desk.integration.distribution;

import com.atex.onecms.content.ContentResult;

/**
 * Interface for content distribution handlers.
 * Replaces the legacy {@code SendContentHandler<T>} from gong/desk.
 *
 * <p>Implementations handle delivery of content to external systems
 * (file transfer, email, remote CMS, social media, etc.).
 *
 * <p>Handlers are discovered by {@link DistributionService} and matched
 * to content by type. Custom handlers can be provided by plugins.
 */
public interface DistributionHandler {

    /**
     * Content types this handler supports (e.g., "OneArticleBean", "OneImageBean").
     * Return {@code {"*"}} to handle all types.
     */
    String[] contentTypes();

    /**
     * Distribute content via the configured route.
     *
     * @param content the content to distribute
     * @param route the distribution route configuration
     * @throws DistributionException if distribution fails
     */
    void distribute(ContentResult<Object> content, DistributionRoute route)
        throws DistributionException;
}
