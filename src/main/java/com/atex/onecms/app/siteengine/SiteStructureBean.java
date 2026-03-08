package com.atex.onecms.app.siteengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.atex.onecms.content.ContentId;

/**
 * Ported from com.atex.gong.site.structure.SiteStructureBean.
 * Represents a node in the site structure tree.
 */
public class SiteStructureBean extends PageBean {

    private ContentId id;
    private String externalId;
    private List<SiteStructureBean> children = new ArrayList<>();

    public List<SiteStructureBean> getChildren() {
        return children;
    }

    public void setChildren(List<SiteStructureBean> children) {
        this.children = Objects.requireNonNull(children);
    }

    public ContentId getId() {
        return id;
    }

    public void setId(ContentId id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
