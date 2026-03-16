package com.atex.desk.integration.distribution;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates content distribution to external systems.
 * Replaces the legacy CamelEngine + SendContentHandlerFinder pattern.
 *
 * <p>Routes are loaded from configuration (DB or YAML). When content is published,
 * the service finds matching handlers and distributes via the configured routes.
 *
 * <p>Currently supports:
 * <ul>
 *   <li>{@link FileTransferHandler} — FTP/SFTP file delivery</li>
 *   <li>{@link EmailHandler} — SMTP email with attachments</li>
 * </ul>
 *
 * <p>Additional handlers can be registered programmatically or via Spring auto-discovery.
 */
@Service
@ConditionalOnProperty(name = "desk.integration.distribution.enabled", havingValue = "true")
public class DistributionService {

    private static final Logger LOG = Logger.getLogger(DistributionService.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final List<DistributionHandler> handlers;
    private final List<DistributionRoute> routes = new ArrayList<>();

    public DistributionService(ContentManager contentManager,
                                List<DistributionHandler> handlers) {
        this.contentManager = contentManager;
        this.handlers = handlers;
    }

    @PostConstruct
    public void init() {
        LOG.info("Distribution service initialized with " + handlers.size() + " handler(s)");
        // Routes can be loaded from DB config here in future
        // For now, routes are registered programmatically or via ChangeProcessor triggers
    }

    /**
     * Register a distribution route.
     */
    public void addRoute(DistributionRoute route) {
        routes.add(route);
        LOG.info("Added distribution route: " + route.getName()
            + " (" + route.getService() + " via " + route.getProtocol() + ")");
    }

    /**
     * Distribute content via all matching routes.
     *
     * @param contentId the content to distribute
     */
    public void distribute(ContentId contentId) {
        ContentVersionId vid = contentManager.resolve(contentId, SYSTEM_SUBJECT);
        if (vid == null) {
            LOG.warning("Cannot resolve content for distribution: " + contentId);
            return;
        }

        ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
            Collections.emptyMap(), SYSTEM_SUBJECT);
        if (!cr.getStatus().isSuccess()) {
            LOG.warning("Cannot get content for distribution: " + contentId);
            return;
        }

        String contentType = cr.getContent().getContentDataType();

        for (DistributionRoute route : routes) {
            if (!route.isEnabled()) continue;

            for (DistributionHandler handler : handlers) {
                if (matchesContentType(handler, contentType)) {
                    try {
                        handler.distribute(cr, route);
                    } catch (DistributionException e) {
                        LOG.log(Level.WARNING, "Distribution failed: route="
                            + route.getName() + " content=" + contentId, e);
                    }
                }
            }
        }
    }

    private boolean matchesContentType(DistributionHandler handler, String contentType) {
        for (String type : handler.contentTypes()) {
            if ("*".equals(type) || type.equals(contentType)) return true;
        }
        return false;
    }
}
