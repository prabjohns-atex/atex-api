package com.atex.onecms.content;

import java.util.List;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;

/**
 * Bean for insertion/parent info aspect.
 */
@AspectDefinition("p.InsertionInfo")
public class InsertionInfoAspectBean {
    public static final String ASPECT_NAME = "p.InsertionInfo";

    private ContentId securityParentContentId;
    private String insertParentId;
    private String securityParentId;
    private List<String> associatedSites;

    public InsertionInfoAspectBean() {}

    public InsertionInfoAspectBean(ContentId securityParentId) {
        this.securityParentContentId = securityParentId;
    }

    public ContentId getSecurityParentId() { return securityParentContentId; }
    public void setSecurityParentId(ContentId v) { this.securityParentContentId = v; }
    public String getInsertParentId() { return insertParentId; }
    public void setInsertParentId(String insertParentId) { this.insertParentId = insertParentId; }
    public String getSecurityParentIdString() { return securityParentId; }
    public void setSecurityParentIdString(String securityParentId) { this.securityParentId = securityParentId; }
    public List<String> getAssociatedSites() { return associatedSites; }
    public void setAssociatedSites(List<String> associatedSites) { this.associatedSites = associatedSites; }
}
