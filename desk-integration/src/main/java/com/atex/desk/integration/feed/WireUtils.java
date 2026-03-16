package com.atex.desk.integration.feed;

import com.atex.onecms.app.dam.solr.SolrService;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utilities for wire article follow-up threading.
 * Ported from gong/desk WireUtils — queries Solr for related wire articles
 * by normalized headline and concatenates their body text.
 */
@Service
public class WireUtils {

    private static final Logger LOG = Logger.getLogger(WireUtils.class.getName());
    private static final Pattern LEADING_PLUS = Pattern.compile("^\\++");
    private static final Pattern TRAILING_MARKS = Pattern.compile("[+=?]+$");
    private static final Pattern TRAILING_PARENS_DIGITS = Pattern.compile("\\(\\d+\\)$");
    private static final Pattern TRAILING_DASH_DIGITS = Pattern.compile("-\\d+-$");

    private final SolrService solrService;

    public WireUtils(SolrService solrService) {
        this.solrService = solrService;
    }

    /**
     * Normalize a wire headline by stripping follow-up markers.
     * Removes leading '+', trailing '=', '+', '?', numeric suffixes in parens or dashes.
     */
    public static String getBaseHeadline(String headline) {
        if (headline == null || headline.isBlank()) return headline;
        String result = headline.trim();
        result = LEADING_PLUS.matcher(result).replaceAll("");
        result = TRAILING_MARKS.matcher(result).replaceAll("");
        result = TRAILING_PARENS_DIGITS.matcher(result).replaceAll("");
        result = TRAILING_DASH_DIGITS.matcher(result).replaceAll("");
        return result.trim();
    }

    /**
     * Build a Solr query to find follow-up articles for a given headline,
     * source, and creation date.
     */
    public static String getFollowQuery(String headline, String source, String creationDate) {
        String baseHeadline = getBaseHeadline(headline);
        String escaped = StringEscapeUtils.escapeJava(baseHeadline);
        StringBuilder query = new StringBuilder();
        query.append("+inputTemplate_ss:\"com.atex.onecms.app.dam.standard.aspects.OneArticleBean\"");
        query.append(" +name:(\"").append(escaped).append("\")");
        if (source != null && !source.isBlank()) {
            query.append(" +source_atex_desk_s:(\"").append(source).append("\")");
        }
        if (creationDate != null && !creationDate.isBlank()) {
            query.append(" +creationTime_dt:[").append(creationDate).append("/DAY TO ")
                 .append(creationDate).append("/DAY+1DAY]");
        }
        return query.toString();
    }

    /**
     * Find and concatenate follow-up articles for a given headline.
     *
     * @return concatenated body text of follow-up articles separated by {@code <br/>}, or null if none found
     */
    public String getFollows(String contentId, String headline, String source, String creationDate) {
        try {
            String queryStr = getFollowQuery(headline, source, creationDate);
            SolrQuery query = new SolrQuery(queryStr);
            query.setRows(50);
            query.setFields("contentid", "name", "body_t");
            query.setSort("modificationTime_dt", SolrQuery.ORDER.asc);

            var response = solrService.rawQuery(query);
            SolrDocumentList docs = response.getResults();
            if (docs == null || docs.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (SolrDocument doc : docs) {
                String docId = (String) doc.getFieldValue("contentid");
                // Skip the article itself
                if (contentId != null && contentId.equals(docId)) continue;

                String body = (String) doc.getFieldValue("body_t");
                if (body != null && !body.isBlank()) {
                    if (sb.length() > 0) sb.append("<br/>");
                    sb.append(body);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error finding follow-up articles for: " + headline, e);
            return null;
        }
    }
}
