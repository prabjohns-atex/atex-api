package com.atex.onecms.ws.search;

import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.IdUtil;

public class SolrQueryDecorator {

    public static final String FIELD_NAME = "page_ss";

    private SolrQuery solrQuery;

    public SolrQueryDecorator(SolrQuery solrQuery) {
        this.solrQuery = solrQuery;
    }

    public SolrQuery getSolrQuery() {
        return solrQuery;
    }

    public SolrQueryDecorator decorateWithWorkingSites(final List<ContentId> workingSites) {
        if (workingSites != null && !workingSites.isEmpty()) {
            StringBuilder sb = new StringBuilder(FIELD_NAME).append(":");
            String separator = workingSites.size() > 1 ? "(" : "";

            for (ContentId workingSite : workingSites) {
                sb.append(separator);
                sb.append("\"");
                sb.append(IdUtil.toIdString(workingSite));
                sb.append("\"");
                separator = " OR ";
            }

            if (workingSites.size() > 1) {
                sb.append(")");
            }

            solrQuery.addFilterQuery(sb.toString());
        }
        return this;
    }
}
