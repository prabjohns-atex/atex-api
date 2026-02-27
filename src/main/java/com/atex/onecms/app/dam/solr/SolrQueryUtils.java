package com.atex.onecms.app.dam.solr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.util.ClientUtils;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;

/**
 * Utility methods to deal with solr queries.
 * Resolves {externalId/xxx} templates in query strings.
 */
public class SolrQueryUtils {

    private final Pattern re = Pattern.compile("\\{?(\\{externalId/[^}]+\\})\\}?", Pattern.CASE_INSENSITIVE);
    private final ContentManager contentManager;

    public SolrQueryUtils(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    public String parseQuery(String query) {
        if (query != null) {
            int startIdx = 0;
            StringBuilder sb = null;
            Matcher matcher = re.matcher(query);
            while (matcher.find()) {
                String contentId = resolve(matcher.group(1));
                if (contentId != null) {
                    int endIdx = matcher.start();
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(query, startIdx, endIdx);
                    sb.append(ClientUtils.escapeQueryChars(contentId));
                    startIdx = matcher.end();
                }
            }
            if (sb != null) {
                if (startIdx > 0) {
                    sb.append(query.substring(startIdx));
                }
                return sb.toString();
            }
        }
        return query;
    }

    private String resolve(String group) {
        if (group != null && group.toLowerCase().startsWith("{externalid/")) {
            String id = group.substring(12, group.length() - 1);
            ContentVersionId contentId = contentManager.resolve(id, Subject.NOBODY_CALLER);
            if (contentId != null) {
                return IdUtil.toIdString(contentId.getContentId());
            }
        }
        return null;
    }
}
