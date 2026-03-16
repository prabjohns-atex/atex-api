package com.atex.desk.integration.schedule;

import com.atex.desk.api.entity.ChangeListEntry;
import com.atex.desk.api.repository.ChangeListRepository;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.Subject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls the change list for new content events and dispatches to registered handlers.
 * Replaces the legacy Camel {@code onecmschangelist} route and {@code ChangeProcessor}.
 *
 * <p>Handlers can react to content changes for purposes like:
 * <ul>
 *   <li>External system notifications</li>
 *   <li>Cache invalidation (e.g., NGINX image cache warm-up)</li>
 *   <li>Content distribution triggers</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "desk.integration.change-processing.enabled",
                        havingValue = "true", matchIfMissing = false)
public class ChangeProcessor {

    private static final Logger LOG = Logger.getLogger(ChangeProcessor.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ChangeListRepository changeListRepository;
    private final ContentManager contentManager;
    private final List<ChangeHandler> handlers = new CopyOnWriteArrayList<>();

    /** Track the last processed change ID. */
    private int lastProcessedId = 0;

    public ChangeProcessor(ChangeListRepository changeListRepository,
                            ContentManager contentManager) {
        this.changeListRepository = changeListRepository;
        this.contentManager = contentManager;
    }

    /**
     * Register a handler to be notified of content changes.
     */
    public void registerHandler(ChangeHandler handler) {
        handlers.add(handler);
        LOG.info("Registered change handler: " + handler.getClass().getSimpleName());
    }

    @Scheduled(fixedDelayString = "${desk.integration.change-processing.interval-ms:5000}",
               initialDelayString = "${desk.integration.change-processing.initial-delay-ms:10000}")
    public void processChanges() {
        if (handlers.isEmpty()) return;

        try {
            List<ChangeListEntry> entries =
                changeListRepository.findByIdGreaterThanOrderByIdAsc(lastProcessedId);
            if (entries.isEmpty()) return;

            for (ChangeListEntry entry : entries) {
                try {
                    dispatchChange(entry);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error processing change " + entry.getId(), e);
                }
                lastProcessedId = entry.getId();
            }

            LOG.fine(() -> "Processed " + entries.size() + " change(s), last ID: " + lastProcessedId);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error polling change list", e);
        }
    }

    private void dispatchChange(ChangeListEntry entry) {
        ChangeEvent event = new ChangeEvent(
            entry.getContentid(),
            entry.getContenttype(),
            entry.getEventtype(),
            entry.getAttrObjectType(),
            entry.getAttrInputTemplate(),
            entry.getAttrPartition()
        );

        for (ChangeHandler handler : handlers) {
            try {
                if (handler.accepts(event)) {
                    handler.handle(event);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Handler " + handler.getClass().getSimpleName()
                    + " failed for " + entry.getContentid(), e);
            }
        }
    }

    /**
     * A content change event dispatched to handlers.
     */
    public record ChangeEvent(
        String contentId,
        String contentType,
        int eventType,
        String objectType,
        String inputTemplate,
        String partition
    ) {
        /** Event type constants matching adm_changelist.eventtype. */
        public static final int CREATE = 1;
        public static final int UPDATE = 2;
        public static final int DELETE = 3;

        public boolean isCreate() { return eventType == CREATE; }
        public boolean isUpdate() { return eventType == UPDATE; }
        public boolean isDelete() { return eventType == DELETE; }
    }

    /**
     * Interface for components that react to content changes.
     */
    public interface ChangeHandler {
        /** Return true if this handler should process the given event. */
        boolean accepts(ChangeEvent event);

        /** Handle the change event. */
        void handle(ChangeEvent event);
    }
}
