package com.atex.desk.integration.schedule;

import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.Subject;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduled task for automated web content status transitions.
 * Replaces the legacy Quartz2 routes: webStatusOnlineProcessor, webStatusUnPublishProcessor.
 *
 * <p>Two operations run on a 5-minute schedule:
 * <ul>
 *   <li><b>Embargo release</b>: Content with expired embargo dates gets status changed to "online"</li>
 *   <li><b>Auto-unpublish</b>: Content with expired timeoff dates gets status changed to "offline"</li>
 * </ul>
 *
 * <p>Both query Solr for matching content, then update the WFContentStatus and
 * WebContentStatus aspects via ContentManager.
 */
@Component
@ConditionalOnProperty(name = "desk.integration.web-status.enabled", havingValue = "true")
public class WebStatusScheduler {

    private static final Logger LOG = Logger.getLogger(WebStatusScheduler.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    private static final DateTimeFormatter SOLR_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final ContentManager contentManager;
    private final com.atex.onecms.app.dam.solr.SolrService solrService;

    @Value("${desk.integration.web-status.embargo-status:published}")
    private String embargoTargetStatus;

    @Value("${desk.integration.web-status.timeoff-status:unpublished}")
    private String timeoffTargetStatus;

    @Value("${desk.integration.web-status.embargo-attribute:attr.online}")
    private String embargoAttribute;

    @Value("${desk.integration.web-status.timeoff-attribute:attr.offline}")
    private String timeoffAttribute;

    public WebStatusScheduler(ContentManager contentManager,
                               com.atex.onecms.app.dam.solr.SolrService solrService) {
        this.contentManager = contentManager;
        this.solrService = solrService;
    }

    /**
     * Release embargoed content whose embargo date has passed.
     */
    @Scheduled(cron = "${desk.integration.web-status.schedule:0 */5 * * * *}")
    public void processEmbargoRelease() {
        String now = SOLR_DATE.format(Instant.now());
        String query = "web_content_status_attribute_ss:\"attr.embargo\""
            + " AND web_content_status_embargo_dt:[* TO " + now + "]";
        processStatusChange(query, embargoTargetStatus, embargoAttribute, "embargo release");
    }

    /**
     * Unpublish content whose timeoff date has passed.
     */
    @Scheduled(cron = "${desk.integration.web-status.timeoff-schedule:0 */5 * * * *}")
    public void processTimeoff() {
        String now = SOLR_DATE.format(Instant.now());
        String query = "web_content_status_attribute_ss:\"attr.online\""
            + " AND web_content_status_timeoff_dt:[* TO " + now + "]";
        processStatusChange(query, timeoffTargetStatus, timeoffAttribute, "timeoff");
    }

    private void processStatusChange(String queryStr, String targetStatus,
                                      String targetAttribute, String operationName) {
        try {
            SolrQuery query = new SolrQuery(queryStr);
            query.setRows(500);
            query.setFields("contentid");

            var response = solrService.rawQuery(query);
            var docs = response.getResults();
            if (docs == null || docs.isEmpty()) return;

            LOG.info(operationName + ": found " + docs.size() + " content item(s) to update");

            int updated = 0;
            for (var doc : docs) {
                String contentIdStr = (String) doc.getFieldValue("contentid");
                if (contentIdStr == null) continue;

                try {
                    updateContentStatus(contentIdStr, targetStatus, targetAttribute);
                    updated++;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, operationName + ": failed to update " + contentIdStr, e);
                }
            }

            LOG.info(operationName + ": updated " + updated + " of " + docs.size() + " item(s)");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in " + operationName + " scheduler", e);
        }
    }

    private void updateContentStatus(String contentIdStr, String targetStatus,
                                      String targetAttribute) throws Exception {
        ContentVersionId vid = contentManager.resolve(contentIdStr, SYSTEM_SUBJECT);
        if (vid == null) return;

        ContentResult<Object> cr = contentManager.get(vid, null, Object.class,
            Collections.emptyMap(), SYSTEM_SUBJECT);
        if (!cr.getStatus().isSuccess()) return;

        // Update WFContentStatus
        WFStatusBean statusBean = new WFStatusBean();
        statusBean.setName(targetStatus);
        statusBean.clearAttributes();
        statusBean.addAttribute(targetAttribute);
        WFContentStatusAspectBean wfAspect = new WFContentStatusAspectBean(statusBean, null);

        ContentWriteBuilder<Object> builder = new ContentWriteBuilder<>();
        builder.mainAspectData(cr.getContent().getContentData());
        builder.type(cr.getContent().getContentDataType());
        builder.aspect(WFContentStatusAspectBean.ASPECT_NAME, wfAspect);

        contentManager.update(vid.getContentId(), builder.buildUpdate(), SYSTEM_SUBJECT);
    }
}
